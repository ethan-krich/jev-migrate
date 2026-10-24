package transpiler.cobol.runtime;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * DISPLAY UPON SYSOUT. Output is written byte-for-byte (one byte per character, ISO-8859-1) with
 * a single LF terminator, independent of the platform line separator.
 */
public final class Display {
    private static final OutputStream OUT = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 8192);

    private Display() {}

    public static void line(String... operands) {
        write(operands);
        write("\n");
    }

    /** DISPLAY ... WITH NO ADVANCING. */
    public static void noAdvancing(String... operands) {
        write(operands);
    }

    public static void flush() {
        try {
            OUT.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(String... parts) {
        try {
            for (String part : parts) {
                OUT.write(part.getBytes(StandardCharsets.ISO_8859_1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
