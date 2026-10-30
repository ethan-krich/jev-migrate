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
    private final boolean justified;

    /** A view of {@code length} bytes at {@code offset}; the bytes are left as they are. */
    public AlphanumericField(Storage storage, int offset, int length) {
        this(storage, offset, length, false);
    }

    /** A view of a {@code JUSTIFIED RIGHT} item when {@code justified}. */
    public AlphanumericField(Storage storage, int offset, int length, boolean justified) {
        if (length < 0 || offset < 0 || offset + length > storage.length()) {
            throw new IllegalArgumentException("invalid range " + offset + "+" + length + " of " + storage.length() + " bytes");
        }
        this.storage = storage;
        this.offset = offset;
        this.length = length;
        this.justified = justified;
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

    /** Initialises the item as if by {@code VALUE value} (left-aligned even when JUSTIFIED); returns this view. */
    public AlphanumericField initial(String value) {
        storage.put(offset, length, value);
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

    /**
     * MOVE of alphanumeric data into this field: left-justified, space-padded and cut on the
     * right; a JUSTIFIED RIGHT item is right-justified and cut on the left instead.
     */
    public void set(String value) {
        if (justified && value.length() != length) {
            if (value.length() > length) {
                storage.put(offset, length, value.substring(value.length() - length));
            } else {
                storage.fill(offset, length - value.length(), (byte) ' ');
                storage.put(offset + length - value.length(), value.length(), value);
            }
            return;
        }
        storage.put(offset, length, value);
    }

    /** Reference modification {@code (start : length)}, checked as GnuCOBOL checks it at run time. */
    /**
     * MOVE that copies {@code source}'s bytes one at a time from left to right (IBM's MVC,
     * GnuCOBOL's cob_move_ibm): a receiver that starts inside the sender repeats the sender's first
     * bytes. The sender is at least as long as this item.
     */
    public void copyForward(AlphanumericField source) {
        for (int i = 0; i < length; i++) {
            storage.bytes[offset + i] = source.storage.bytes[source.offset + i];
        }
    }

    public AlphanumericField ref(int start, int length, String name) {
        return Bounds.ref(storage, offset, this.length, start, length, name);
    }

    /** Reference modification {@code (start :)}: to the end of the item. */
    public AlphanumericField ref(int start, String name) {
        return Bounds.ref(storage, offset, this.length, start, this.length - start + 1, name);
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
        set(source.alphanumericDigits());
    }

    /** This item's bytes as an alphanumeric view (the argument of CALL ... BY REFERENCE). */
    public AlphanumericField view() {
        return this;
    }

    /** A copy of the item's bytes in storage of its own (CALL ... BY CONTENT). */
    public AlphanumericField copy() {
        AlphanumericField c = new AlphanumericField(new Storage(Math.max(1, length)), 0, length);
        System.arraycopy(storage.bytes, offset, c.storage.bytes, 0, length);
        return c;
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
