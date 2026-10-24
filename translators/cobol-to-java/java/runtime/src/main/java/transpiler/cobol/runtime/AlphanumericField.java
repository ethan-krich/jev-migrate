package transpiler.cobol.runtime;

/**
 * A fixed-width COBOL alphanumeric item ({@code PIC X(n)}, USAGE DISPLAY) or a group item: a
 * view of {@code length} bytes of a {@link Storage}.
 *
 * <p>Receiving semantics follow the standard alphanumeric MOVE: data is left-justified, padded
 * on the right with spaces and truncated on the right when longer than the field.
 */
public final class AlphanumericField {
    private final Storage storage;
    private final int offset;
    private final int length;

    /** A view of {@code length} bytes at {@code offset}; the bytes are left as they are. */
    public AlphanumericField(Storage storage, int offset, int length) {
        if (length <= 0 || offset < 0 || offset + length > storage.length()) {
            throw new IllegalArgumentException("invalid range " + offset + "+" + length + " of " + storage.length() + " bytes");
        }
        this.storage = storage;
        this.offset = offset;
        this.length = length;
    }

    /** A standalone item (its own record), initialised to spaces. */
    public AlphanumericField(int length) {
        this(new Storage(length), 0, length);
    }

    /** A standalone item initialised as if by {@code VALUE initial}. */
    public AlphanumericField(int length, String initial) {
        this(length);
        set(initial);
    }

    /** Initialises the item to spaces (WORKING-STORAGE without VALUE); returns this view. */
    public AlphanumericField initial() {
        fill(' ');
        return this;
    }

    /** Initialises the item as if by {@code VALUE value}; returns this view. */
    public AlphanumericField initial(String value) {
        set(value);
        return this;
    }

    public Storage storage() {
        return storage;
    }

    public int offset() {
        return offset;
    }

    public int length() {
        return length;
    }

    /** MOVE of alphanumeric data into this field. */
    public void set(String value) {
        storage.put(offset, length, value);
    }

    /** MOVE of a figurative constant (SPACES, ZEROS) into this field. */
    public void fill(char c) {
        storage.fill(offset, length, (byte) c);
    }

    /** MOVE of {@code ALL literal}: the pattern repeated, the last repetition cut at the field's end. */
    public void fill(String pattern) {
        for (int i = 0; i < length; i++) {
            storage.bytes[offset + i] = (byte) pattern.charAt(i % pattern.length());
        }
    }

    /** MOVE of an integer numeric item: its unsigned digit string is moved as alphanumeric data. */
    public void set(NumericField source) {
        set(source.digits());
    }

    public String get() {
        return storage.text(offset, length);
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
