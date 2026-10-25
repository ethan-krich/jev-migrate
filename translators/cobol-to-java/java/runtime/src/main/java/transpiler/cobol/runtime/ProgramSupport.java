package transpiler.cobol.runtime;

/**
 * Run-unit control flow shared by generated programs.
 *
 * <p>Matches GnuCOBOL 3.2: STOP RUN ends the run unit from any depth; GOBACK returns to the caller
 * (or ends the run unit in the main program); EXIT PROGRAM is ignored in the main program;
 * after a CALL the caller's RETURN-CODE is the callee's.
 */
public final class ProgramSupport {
    private static int depth;

    private ProgramSupport() {}

    /** Runs {@code program} as the main program and returns RETURN-CODE as the process exit status. */
    public static int runMain(CobolProgram program) {
        try {
            program.procedureDivision();
            return program.returnCode().intValue();
        } catch (StopRun stop) {
            return stop.status();
        } catch (Goback end) {
            return program.returnCode().intValue();
        } finally {
            Display.flush();
        }
    }

    /** Static CALL of a program without parameters. */
    public static void call(CobolProgram caller, CobolProgram callee) {
        call(caller, callee, new AlphanumericField[0]);
    }

    /** Static CALL ... USING: the callee's LINKAGE records see {@code arguments}. */
    public static void call(CobolProgram caller, CobolProgram callee, AlphanumericField... arguments) {
        callee.using(arguments);
        depth++;
        try {
            callee.procedureDivision();
        } catch (Goback back) {
            // Returned to the caller.
        } finally {
            depth--;
        }
        caller.returnCode().move(callee.returnCode());
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
