package transpiler.cobol.runtime;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** ACCEPT from standard input (SYSIN / CONSOLE): one line per ACCEPT, bytes as ISO-8859-1. */
public final class Accept {
    private static final InputStream IN = new BufferedInputStream(new FileInputStream(FileDescriptor.in), 8192);

    private Accept() {}

    /**
     * The next input line without its LF terminator; at end of input an empty string (the
     * receiver is then filled with spaces, as GnuCOBOL 3.2 does).
     */
    public static String line() {
        Display.flush();
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        try {
            for (int b = IN.read(); b != -1 && b != '\n'; b = IN.read()) {
                line.write(b);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return line.toString(StandardCharsets.ISO_8859_1);
    }
}
