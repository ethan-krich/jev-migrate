package transpiler.cobol.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The bytes of one level-01 / level-77 record. Data items are views over a range of a record,
 * so group moves, REDEFINES and reference modification see exactly the bytes GnuCOBOL would.
 *
 * <p>Characters are bytes: Java strings carry one character per byte (ISO-8859-1).
 */
public final class Storage {
    final byte[] bytes;

    /** A record of {@code length} bytes, all spaces until its items are initialised. */
    public Storage(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive: " + length);
        }
        this.bytes = new byte[length];
        Arrays.fill(bytes, (byte) ' ');
    }

    /** A FILE SECTION record area: GnuCOBOL leaves it zero bytes until a statement fills it. */
    public static Storage zeros(int length) {
        Storage s = new Storage(length);
        Arrays.fill(s.bytes, (byte) 0);
        return s;
    }

    public int length() {
        return bytes.length;
    }

    String text(int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.ISO_8859_1);
    }

    /** Writes {@code value} left-justified into the range, space-padded or truncated on the right. */
    void put(int offset, int length, String value) {
        int n = Math.min(value.length(), length);
        for (int i = 0; i < n; i++) {
            bytes[offset + i] = (byte) value.charAt(i);
        }
        Arrays.fill(bytes, offset + n, offset + length, (byte) ' ');
    }

    void fill(int offset, int length, byte b) {
        Arrays.fill(bytes, offset, offset + length, b);
    }
}
