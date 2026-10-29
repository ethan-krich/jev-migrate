package transpiler.cobol.runtime;

/**
 * Run-time range checks for subscripts and reference modification. A failed check ends the run
 * unit with status 1 after a libcob-style message on standard error, as GnuCOBOL 3.2 does when
 * compiled with {@code -fec=EC-BOUND-SUBSCRIPT -fec=EC-BOUND-REF-MOD}.
 */
public final class Bounds {
    private Bounds() {}

    /** The view {@code (start : length)} of the {@code size} bytes at {@code offset}. */
    static AlphanumericField ref(Storage storage, int offset, int size, int start, int length, String name) {
        if (start < 1 || start > size) {
            fail("offset of '" + name + "' out of bounds: " + start + ", maximum: " + size);
        }
        if (length < 1 || length > size - start + 1) {
            fail("length of '" + name + "' out of bounds: " + length + ", maximum: " + (size - start + 1));
        }
        return new AlphanumericField(storage, offset + start - 1, length);
    }

    /** {@code value} as a subscript of a table with {@code max} occurrences (1-based). */
    public static int subscript(int value, int max, String name) {
        if (value < 1 || value > max) {
            fail("subscript of '" + name + "' out of bounds: " + value + "\nnote: maximum subscript for '" + name + "': " + max);
        }
        return value;
    }

    /**
     * A subscript of an OCCURS DEPENDING ON table, checked against the occurrences in use
     * ({@code current}, after checking it against {@code min..max}).
     */
    public static int subscript(int value, int current, int min, int max, String name, String dependingName) {
        depending(current, min, max, dependingName);
        if (value < 1 || value > current) {
            fail("subscript of '" + name + "' out of bounds: " + value + "\nnote: current maximum subscript for '" + name + "': " + current);
        }
        return value;
    }

    /** The value of an OCCURS DEPENDING ON item, which must lie in {@code min..max}. */
    public static int depending(int value, int min, int max, String name) {
        if (value < min || value > max) {
            fail("OCCURS DEPENDING ON '" + name + "' out of bounds: " + value
                    + "\nnote: " + (value < min ? "minimum" : "maximum") + " subscript for '" + name + "': " + (value < min ? min : max));
        }
        return value;
    }

    static void fail(String message) {
        Display.err("libcob: error: " + message);
        Display.flush();
        throw new StopRun(1);
    }
}
