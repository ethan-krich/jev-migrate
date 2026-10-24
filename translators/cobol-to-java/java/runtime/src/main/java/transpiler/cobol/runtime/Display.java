package transpiler.cobol.runtime;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * DISPLAY UPON SYSOUT / CONSOLE (standard output) and UPON SYSERR (standard error). Output is
 * written byte-for-byte (one byte per character, ISO-8859-1) with a single LF terminator,
 * independent of the platform line separator.
 */
public final class Display {
    private static final OutputStream OUT = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 8192);
    private static final OutputStream ERR = new BufferedOutputStream(new FileOutputStream(FileDescriptor.err), 8192);

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
