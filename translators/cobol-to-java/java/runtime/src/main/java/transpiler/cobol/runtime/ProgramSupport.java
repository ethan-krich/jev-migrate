package transpiler.cobol.runtime;

import java.math.BigDecimal;
import java.util.Set;
import java.util.function.Function;

/**
 * Run-unit control flow shared by generated programs.
 *
 * <p>Matches GnuCOBOL 3.2: STOP RUN ends the run unit from any depth; GOBACK returns to the caller
 * (or ends the run unit in the main program); EXIT PROGRAM is ignored in the main program;
 * after a CALL the caller's RETURN-CODE is the callee's; a CALL of a program that is active (not
 * returned from) is an exception, and CANCEL of one ends the run unit.
 */
public final class ProgramSupport {
    private static int depth;
    /** Programs with invocations in progress, and how many (GnuCOBOL's module_active). */
    private static final java.util.Map<Class<?>, Integer> active = new java.util.HashMap<>();
    /** GnuCOBOL 3.2's system routines, which a CALL by name reaches without a program. */
    private static final Set<String> SYSTEM_ROUTINES = Set.of("SYSTEM", "CBL_AND", "CBL_ALARM_SOUND", "CBL_BELL_SOUND",
            "CBL_CHANGE_DIR", "CBL_CHECK_FILE_EXIST", "CBL_CLOSE_FILE", "CBL_COPY_FILE", "CBL_CREATE_DIR", "CBL_CREATE_FILE",
            "CBL_DELETE_DIR", "CBL_DELETE_FILE", "CBL_EQ", "CBL_ERROR_PROC", "CBL_EXIT_PROC", "CBL_RUNTIME_ERROR", "CBL_FLUSH_FILE",
            "CBL_GET_CSR_POS", "CBL_GET_CURRENT_DIR", "CBL_GET_SCR_SIZE", "CBL_IMP", "CBL_NIMP", "CBL_NOR", "CBL_NOT", "CBL_OPEN_FILE",
            "CBL_OR", "CBL_READ_FILE", "CBL_READ_KBD_CHAR", "CBL_RENAME_FILE", "CBL_SET_CSR_POS", "CBL_TOLOWER", "CBL_TOUPPER",
            "CBL_WRITE_FILE", "CBL_XOR", "CBL_GC_FORK", "CBL_GC_GETOPT", "CBL_GC_HOSTED", "CBL_GC_NANOSLEEP", "CBL_GC_PRINTABLE",
            "CBL_GC_SET_SCR_SIZE", "CBL_GC_WAITPID", "CBL_OC_GETOPT", "CBL_OC_HOSTED", "CBL_OC_NANOSLEEP", "C$CALLEDBY", "C$CHDIR",
            "C$COPY", "C$DELETE", "C$FILEINFO", "C$GETPID", "C$JUSTIFY", "C$MAKEDIR", "C$NARG", "C$PARAMSIZE", "C$PRINTABLE",
            "C$SLEEP", "C$TOLOWER", "C$TOUPPER", "EXTFH", "\u0091", "\u00e4", "\u00e5", "\u00f4", "\u00f5");

    private ProgramSupport() {}

    private static final java.util.Map<String, Storage> externalStorage = new java.util.HashMap<>();
    private static final java.util.Map<String, Object[]> externalFiles = new java.util.HashMap<>();

    /**
     * The storage of an EXTERNAL record, shared by every program of the run unit that declares
     * it; zero bytes until a program sets it, as in GnuCOBOL.
     */
    public static Storage external(String name, int size) {
        Storage s = externalStorage.computeIfAbsent(name, n -> Storage.zeros(size));
        if (s.length() != size) {
            Bounds.fail("EXTERNAL item '" + name + "' has " + size + " bytes here but " + s.length() + " where it was first declared");
        }
        return s;
    }

    /**
     * An EXTERNAL file: the connector of the first program that declares it, shared by the others;
     * {@code signature} (path, organisation, record, keys, FILE STATUS) must be the same in each.
     */
    @SuppressWarnings("unchecked")
    public static <T> T externalFile(String name, String signature, java.util.function.Supplier<T> create) {
        Object[] entry = externalFiles.computeIfAbsent(name, n -> new Object[] {signature, create.get()});
        if (!entry[0].equals(signature)) {
            Bounds.fail("EXTERNAL file '" + name + "' is declared differently in two programs (" + entry[0] + " / " + signature + ")");
        }
        return (T) entry[1];
    }

    /** Runs {@code program} as the main program and returns RETURN-CODE as the process exit status. */
    public static int runMain(CobolProgram program, String... args) {
        Environment.arguments(args);
        active.merge(program.getClass(), 1, Integer::sum);
        program.enterFrame();
        try {
            program.procedureDivision();
            return program.returnCode().intValue();
        } catch (StopRun stop) {
            return stop.status();
        } catch (Goback end) {
            return program.returnCode().intValue();
        } finally {
            FileBase.closeAll();
            Display.flush();
        }
    }

    /**
     * CALL [USING]: the callee's LINKAGE records see {@code arguments}. True when the CALL raised
     * an exception instead: the callee is active, which with ON EXCEPTION ({@code exceptionPhrase})
     * sets RETURN-CODE to -1, and without it ends the run unit.
     */
    public static boolean call(CobolProgram caller, CobolProgram callee, boolean exceptionPhrase, AlphanumericField... arguments) {
        return callEntry(caller, callee, 0, exceptionPhrase, arguments);
    }

    /** A program and one of its entry points (0: the PROCEDURE DIVISION, n: its n-th ENTRY), as a CALL name denotes them. */
    public record Entry(CobolProgram program, int entry) {}

    /** CALL of ENTRY number {@code entry} of {@code callee} (0: the program itself); see {@link #call}. */
    public static boolean callEntry(CobolProgram caller, CobolProgram callee, int entry, boolean exceptionPhrase,
            AlphanumericField... arguments) {
        if (active.containsKey(callee.getClass()) && !callee.recursive()) {
            if (exceptionPhrase) {
                caller.returnCode().move(BigDecimal.ONE.negate());
                return true;
            }
            Bounds.fail("recursive CALL from '" + caller.getClass().getSimpleName() + "' to '" + callee.getClass().getSimpleName()
                    + "' which is NOT RECURSIVE");
        }
        Object frame = callee.enterFrame();
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = callee.rebase(frame, arguments[i]);
        }
        callee.using(entry, arguments);
        active.merge(callee.getClass(), 1, Integer::sum);
        depth++;
        try {
            callee.enter(entry);
        } catch (Goback back) {
            // Returned to the caller.
        } finally {
            depth--;
            active.computeIfPresent(callee.getClass(), (k, n) -> n == 1 ? null : n - 1);
            callee.leaveFrame(frame);
        }
        caller.returnCode().move(callee.returnCode());
        return false;
    }

    /**
     * CALL identifier: {@code programs} gives the program a name denotes (null for none). A name
     * no program has is an exception (ON EXCEPTION), or ends the run unit; so does an argument
     * shorter than the callee's LINKAGE item, which GnuCOBOL would read past.
     */
    public static boolean callNamed(CobolProgram caller, String value, Function<String, Entry> programs, boolean exceptionPhrase,
            AlphanumericField... arguments) {
        String name = programName(value);
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || SYSTEM_ROUTINES.contains(name)) {
            Bounds.fail("CALL '" + name + "': a module path or GnuCOBOL system routine has no target");
        }
        Entry target = name.isEmpty() ? null : programs.apply(name);
        if (target == null) {
            if (exceptionPhrase) {
                return true;
            }
            Bounds.fail("module '" + name + "' not found");
        }
        CobolProgram callee = target.program();
        int[] sizes = callee.parameterSizes(target.entry());
        for (int i = 0; i < Math.min(sizes.length, arguments.length); i++) {
            if (arguments[i].length() < sizes[i]) {
                Bounds.fail("CALL '" + name + "' USING: argument " + (i + 1) + " has " + arguments[i].length()
                        + " bytes but the program's LINKAGE item has " + sizes[i]);
            }
        }
        return callEntry(caller, callee, target.entry(), exceptionPhrase, arguments);
    }

    /**
     * The program name a CALL identifier's value gives, as GnuCOBOL takes it: trailing spaces and
     * NULs are dropped, then leading spaces, and the name ends at a NUL.
     */
    public static String programName(String value) {
        String name = trimmed(value);
        int start = 0;
        while (start < name.length() && name.charAt(start) == ' ') {
            start++;
        }
        return name.substring(start);
    }

    /** The name a CANCEL identifier's value gives: leading spaces stay, a directory part is ignored. */
    public static String cancelName(String value) {
        String name = trimmed(value);
        return name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    }

    private static String trimmed(String value) {
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == 0)) {
            end--;
        }
        String name = value.substring(0, end);
        int nul = name.indexOf(0);
        return nul >= 0 ? name.substring(0, nul) : name;
    }

    /** CANCEL of a program (its WORKING-STORAGE is initial at the next CALL); cancelling an active program ends the run unit. */
    public static void cancel(Class<?> program) {
        if (active.containsKey(program)) {
            Bounds.fail("attempt to CANCEL active program");
        }
    }

    /** A LINKAGE record's binding; using one the caller did not pass ends the run unit. */
    public static AlphanumericField parameter(AlphanumericField binding, String name) {
        if (binding == null) {
            Bounds.fail("LINKAGE item '" + name + "' was not passed by the caller");
        }
        return binding;
    }

    public static void goback(CobolProgram self) {
        if (depth == 0) {
            throw new StopRun(self.returnCode().intValue());
        }
        throw new Goback();
    }

    public static void exitProgram() {
        if (depth > 0) {
            throw new Goback();
        }
    }

    public static void stopRun(CobolProgram self) {
        throw new StopRun(self.returnCode().intValue());
    }
}
