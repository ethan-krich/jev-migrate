package transpiler.cobol.runtime;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * DISPLAY UPON SYSOUT / CONSOLE (standard output), UPON SYSERR (standard error) and UPON PRINTER
 * (GnuCOBOL's print file or standard output). Output is
 * written byte-for-byte (one byte per character, ISO-8859-1) with a single LF terminator,
 * independent of the platform line separator.
 *
 * <p>{@code System.out} and {@code System.err} write into the same buffers once this class is
 * loaded (ISO-8859-1; a println flushes them, as the JDK's own streams do), so a statement
 * translated with plain JDK calls ({@code System.out.println}) stays in order with the DISPLAYs
 * around it.
 */
public final class Display {
    private static final OutputStream OUT = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 8192);
    private static final OutputStream ERR = new BufferedOutputStream(new FileOutputStream(FileDescriptor.err), 8192);

    static {
        System.setOut(new java.io.PrintStream(OUT, true, StandardCharsets.ISO_8859_1));
        System.setErr(new java.io.PrintStream(ERR, true, StandardCharsets.ISO_8859_1));
    }

    private Display() {}

    public static void line(String... operands) {
        write(operands);
        write("\n");
    }

    /** DISPLAY ... WITH NO ADVANCING. */
    public static void noAdvancing(String... operands) {
        write(operands);
    }

    /** DISPLAY ... UPON SYSERR. */
    public static void err(String... operands) {
        write(ERR, operands);
        write(ERR, "\n");
    }

    /** DISPLAY ... UPON SYSERR WITH NO ADVANCING. */
    public static void errNoAdvancing(String... operands) {
        write(ERR, operands);
    }

    /**
     * DISPLAY ... UPON PRINTER as GnuCOBOL 3.2 does it: appended to the file COB_DISPLAY_PRINT_FILE
     * names (opened and closed for each DISPLAY; standard error when it cannot be opened), else
     * standard output. A print command (COB_DISPLAY_PRINT_PIPE) is not reproduced: the run unit ends.
     */
    public static void printer(boolean advancing, String... operands) {
        String file = System.getenv("COB_DISPLAY_PRINT_FILE");
        if (file != null) {
            try (OutputStream out = new FileOutputStream(file, true)) {
                write(out, operands);
                if (advancing) {
                    write(out, "\n");
                }
                return;
            } catch (IOException e) {
                if (advancing) {
                    err(operands);
                } else {
                    errNoAdvancing(operands);
                }
                return;
            }
        }
        if (System.getenv("COB_DISPLAY_PRINT_PIPE") != null || System.getenv("COBPRINTER") != null) {
            Bounds.fail("DISPLAY UPON PRINTER to a print command (COB_DISPLAY_PRINT_PIPE) is not supported");
        }
        if (advancing) {
            line(operands);
        } else {
            noAdvancing(operands);
        }
    }

    public static void flush() {
        try {
            OUT.flush();
            ERR.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(String... parts) {
        write(OUT, parts);
    }

    private static void write(OutputStream out, String... parts) {
        try {
            for (String part : parts) {
                out.write(part.getBytes(StandardCharsets.ISO_8859_1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
