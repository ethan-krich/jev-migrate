package transpiler.cobol.runtime;

import java.util.Arrays;

/**
 * A fixed-width COBOL alphanumeric item ({@code PIC X(n)}, USAGE DISPLAY).
 *
 * <p>Receiving semantics follow the standard alphanumeric MOVE: data is left-justified, padded
 * on the right with spaces and truncated on the right when longer than the field.
 */
public final class AlphanumericField {
    private final char[] data;

    public AlphanumericField(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive: " + length);
        }
        this.data = new char[length];
        Arrays.fill(data, ' ');
    }

    /** Creates a field initialised as if by {@code VALUE initial}. */
    public AlphanumericField(int length, String initial) {
        this(length);
        set(initial);
    }

    public int length() {
        return data.length;
    }

    /** MOVE of alphanumeric data into this field. */
    public void set(String value) {
        int n = Math.min(value.length(), data.length);
        value.getChars(0, n, data, 0);
        Arrays.fill(data, n, data.length, ' ');
    }

    /** MOVE of a figurative constant (SPACES, ZEROS) into this field. */
    public void fill(char c) {
        Arrays.fill(data, c);
    }

    /** MOVE of {@code ALL literal}: the pattern repeated, the last repetition cut at the field's end. */
    public void fill(String pattern) {
        for (int i = 0; i < data.length; i++) {
            data[i] = pattern.charAt(i % pattern.length());
        }
    }

    /** MOVE of an integer numeric item: its unsigned digit string is moved as alphanumeric data. */
    public void set(NumericField source) {
        set(source.digits());
    }

    public String get() {
        return new String(data);
    }

    /** The DISPLAY representation is the stored characters, trailing spaces included. */
    public String display() {
        return get();
    }

    @Override
    public String toString() {
        return get();
    }
}
