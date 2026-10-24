package transpiler.cobol.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * A COBOL zoned-decimal numeric item ({@code PIC [S]9(i)[V9(s)]}, USAGE DISPLAY): a view of
 * its bytes in a {@link Storage}, encoded as GnuCOBOL 3.2 encodes them on an ASCII machine.
 *
 * <p>One ASCII digit per byte. A signed item without SIGN SEPARATE carries the sign in its last
 * (or, with SIGN LEADING, first) digit: {@code '0'..'9'} positive, {@code 'p'..'y'} negative. With
 * SIGN SEPARATE the sign is an extra {@code '+'} or {@code '-'} byte.
 *
 * <p>Two store paths mirror the language:
 *
 * <ul>
 *   <li>{@link #move} – MOVE semantics: align on the decimal point, truncate excess low-order
 *       and high-order digits, drop the sign for an unsigned receiver.
 *   <li>{@link #store} – arithmetic-statement semantics: optional ROUNDED, and a size-error
 *       check that leaves the receiver unchanged when ON SIZE ERROR is specified.
 * </ul>
 *
 * <p>Bytes that are not valid digits (put there through a group or REDEFINES) are read as
 * GnuCOBOL reads them: a digit position contributes its low four bits, an embedded sign
 * position that is neither a digit nor {@code 'p'..'y'} reads as a positive zero, and MOVE and
 * DISPLAY copy digit positions unchanged.
 */
public final class NumericField {
    private final Storage storage;
    private final int offset;
    private final int digits;
    private final int scale;
    private final boolean signed;
    private final boolean signLeading;
    private final boolean signSeparate;
    private final BigInteger modulus;

    /** A view at {@code offset}; the sign, when signed, is embedded in the last digit. */
    public NumericField(Storage storage, int offset, int digits, int scale, boolean signed) {
        this(storage, offset, digits, scale, signed, false, false);
    }

    /** A view with an explicit SIGN clause ({@code leading}, {@code separate}). */
    public NumericField(Storage storage, int offset, int digits, int scale, boolean signed, boolean leading, boolean separate) {
        if (digits <= 0 || scale < 0 || scale > digits) {
            throw new IllegalArgumentException("invalid numeric picture: digits=" + digits + " scale=" + scale);
        }
        if (!signed && (leading || separate)) {
            throw new IllegalArgumentException("SIGN clause on an unsigned item");
        }
        this.storage = storage;
        this.offset = offset;
        this.digits = digits;
        this.scale = scale;
        this.signed = signed;
        this.signLeading = leading;
        this.signSeparate = separate;
        this.modulus = BigInteger.TEN.pow(digits);
        if (offset < 0 || offset + size() > storage.length()) {
            throw new IllegalArgumentException("invalid range " + offset + "+" + size() + " of " + storage.length() + " bytes");
        }
    }

    /** A standalone item (its own record), initialised to zero. */
    public NumericField(int digits, int scale, boolean signed) {
        this(new Storage(digits), 0, digits, scale, signed);
        initial();
    }

    /** A standalone item initialised as if by {@code VALUE initial}. */
    public NumericField(int digits, int scale, boolean signed, String initial) {
        this(digits, scale, signed);
        move(new BigDecimal(initial));
    }

    /** Initialises the item to zero (WORKING-STORAGE without VALUE); returns this view. */
    public NumericField initial() {
        move(BigDecimal.ZERO);
        return this;
    }

    /** Initialises the item as if by {@code VALUE value}; returns this view. */
    public NumericField initial(String value) {
        move(new BigDecimal(value));
        return this;
    }

    public int digitCount() {
        return digits;
    }

    public int scale() {
        return scale;
    }

    public boolean isSigned() {
        return signed;
    }

    /** The item's size in bytes. */
    public int size() {
        return digits + (signSeparate ? 1 : 0);
    }

    public Storage storage() {
        return storage;
    }

    public int offset() {
        return offset;
    }

    public BigDecimal get() {
        BigInteger unscaled;
        if (digits <= 18) {
            long acc = 0;
            for (int i = 0; i < digits; i++) {
                acc = acc * 10 + digitValue(i);
            }
            unscaled = BigInteger.valueOf(acc);
        } else {
            unscaled = BigInteger.ZERO;
            for (int i = 0; i < digits; i++) {
                unscaled = unscaled.multiply(BigInteger.TEN).add(BigInteger.valueOf(digitValue(i)));
            }
        }
        return new BigDecimal(negative() ? unscaled.negate() : unscaled, scale);
    }

    public int intValue() {
        return get().intValue();
    }

    /** MOVE of a numeric value into this field. */
    public void move(BigDecimal source) {
        BigInteger unscaled = source.setScale(scale, RoundingMode.DOWN).unscaledValue();
        String kept = unscaled.abs().mod(modulus).toString();
        for (int i = 0, pad = digits - kept.length(); i < digits; i++) {
            storage.bytes[digitIndex(i)] = (byte) (i < pad ? '0' : kept.charAt(i - pad));
        }
        putSign(unscaled.signum() < 0);
    }

    /**
     * MOVE between numeric DISPLAY items: digit positions are aligned on the decimal point and
     * copied as they are, except that a space becomes a zero.
     */
    public void move(NumericField source) {
        if (source == this) {
            return;
        }
        boolean negative = source.negative();
        int shift = (source.digits - source.scale) - (digits - scale);
        byte[] moved = new byte[digits];
        for (int i = 0; i < digits; i++) {
            int j = i + shift;
            char d = j >= 0 && j < source.digits ? source.digitChar(j) : '0';
            moved[i] = (byte) (d == ' ' ? '0' : d);
        }
        for (int i = 0; i < digits; i++) {
            storage.bytes[digitIndex(i)] = moved[i];
        }
        putSign(negative);
    }

    /**
     * Stores an arithmetic result.
     *
     * @param result the exact result, or {@code null} when it is undefined (division by zero)
     * @param rounded whether the ROUNDED phrase was specified
     * @param sizeErrorPhrase whether ON SIZE ERROR / NOT ON SIZE ERROR was specified; when true a
     *     size error leaves this field unchanged, otherwise high-order digits are truncated
     * @return {@code true} if a size error occurred
     */
    public boolean store(BigDecimal result, boolean rounded, boolean sizeErrorPhrase) {
        if (result == null) {
            return true;
        }
        BigDecimal aligned = result.setScale(scale, rounded ? RoundingMode.HALF_UP : RoundingMode.DOWN);
        boolean overflow = aligned.unscaledValue().abs().compareTo(modulus) >= 0;
        if (overflow && sizeErrorPhrase) {
            return true;
        }
        move(aligned);
        return overflow;
    }

    /** The unsigned digit string with leading zeros and no decimal point, as moved to alphanumeric. */
    public String digits() {
        char[] out = new char[digits];
        for (int i = 0; i < digits; i++) {
            out[i] = digitChar(i);
        }
        return new String(out);
    }

    /** The item's bytes as characters (a group move or reference modification sees these). */
    public String bytes() {
        return storage.text(offset, size());
    }

    /** Reference modification of the item's bytes. */
    public AlphanumericField ref(int start, int length, String name) {
        return Bounds.ref(storage, offset, size(), start, length, name);
    }

    public AlphanumericField ref(int start, String name) {
        return Bounds.ref(storage, offset, size(), start, size() - start + 1, name);
    }

    /**
     * Class condition NUMERIC: every digit position holds {@code '0'..'9'}; an embedded sign
     * position may also hold {@code 'p'..'y'} and a separate sign is {@code '+'} or {@code '-'}.
     */
    public boolean isNumeric() {
        for (int i = 0; i < digits; i++) {
            int b = storage.bytes[digitIndex(i)] & 0xFF;
            boolean ok = (b >= '0' && b <= '9') || (isSignDigit(i) && b >= 'p' && b <= 'y');
            if (!ok) {
                return false;
            }
        }
        if (signSeparate) {
            byte sign = storage.bytes[signLeading ? offset : offset + digits];
            return sign == '+' || sign == '-';
        }
        return true;
    }

    /** GnuCOBOL DISPLAY format: leading sign when signed, decimal point when scaled. */
    public String display() {
        return display('.');
    }

    /** DISPLAY with {@code DECIMAL-POINT IS COMMA}: the decimal point is shown as {@code point}. */
    public String display(char point) {
        String raw = digits();
        StringBuilder sb = new StringBuilder(digits + 2);
        // A separate sign is shown where it is stored, as it is stored; an embedded one leads.
        char separate = signSeparate ? (char) (storage.bytes[signLeading ? offset : offset + digits] & 0xFF) : 0;
        if (signSeparate && signLeading) {
            sb.append(separate);
        } else if (signed && !signSeparate) {
            sb.append(get().signum() < 0 ? '-' : '+');
        }
        sb.append(raw, 0, digits - scale);
        if (scale > 0) {
            sb.append(point).append(raw, digits - scale, digits);
        }
        if (signSeparate && !signLeading) {
            sb.append(separate);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return display();
    }

    private int digitIndex(int i) {
        return offset + (signSeparate && signLeading ? 1 : 0) + i;
    }

    private boolean isSignDigit(int i) {
        return signed && !signSeparate && i == (signLeading ? 0 : digits - 1);
    }

    /**
     * The character of digit position {@code i} with an embedded sign removed: {@code 'p'..'y'}
     * read as their digit, a space stays a space, anything else that is not a digit reads as zero.
     */
    private char digitChar(int i) {
        int b = storage.bytes[digitIndex(i)] & 0xFF;
        if (isSignDigit(i)) {
            if (b >= 'p' && b <= 'y') {
                return (char) (b - 0x40);
            }
            return (b >= '0' && b <= '9') || b == ' ' ? (char) b : '0';
        }
        return (char) b;
    }

    private int digitValue(int i) {
        return digitChar(i) & 0x0F;
    }

    private boolean negative() {
        if (!signed) {
            return false;
        }
        if (signSeparate) {
            return storage.bytes[signLeading ? offset : offset + digits] == '-';
        }
        int b = storage.bytes[digitIndex(signLeading ? 0 : digits - 1)] & 0xFF;
        return b >= 'p' && b <= 'y';
    }

    private void putSign(boolean negative) {
        if (!signed) {
            return;
        }
        if (signSeparate) {
            storage.bytes[signLeading ? offset : offset + digits] = (byte) (negative ? '-' : '+');
            return;
        }
        if (negative) {
            int at = digitIndex(signLeading ? 0 : digits - 1);
            storage.bytes[at] = (byte) (storage.bytes[at] + 0x40);
        }
    }
}
