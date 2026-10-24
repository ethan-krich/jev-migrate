package transpiler.cobol.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * A COBOL zoned-decimal numeric item ({@code PIC [S]9(i)[V9(s)]}, USAGE DISPLAY).
 *
 * <p>The value is held exactly as a {@link BigDecimal} with the field's scale. Two store paths
 * mirror the language:
 *
 * <ul>
 *   <li>{@link #move} – MOVE semantics: align on the decimal point, truncate excess low-order
 *       and high-order digits, drop the sign for an unsigned receiver.
 *   <li>{@link #store} – arithmetic-statement semantics: optional ROUNDED, and a size-error
 *       check that leaves the receiver unchanged when ON SIZE ERROR is specified.
 * </ul>
 */
public final class NumericField {
    private final int digits;
    private final int scale;
    private final boolean signed;
    private final BigInteger modulus;
    private BigDecimal value;

    public NumericField(int digits, int scale, boolean signed) {
        if (digits <= 0 || scale < 0 || scale > digits) {
            throw new IllegalArgumentException("invalid numeric picture: digits=" + digits + " scale=" + scale);
        }
        this.digits = digits;
        this.scale = scale;
        this.signed = signed;
        this.modulus = BigInteger.TEN.pow(digits);
        this.value = BigDecimal.ZERO.setScale(scale);
    }

    /** Creates a field initialised as if by {@code VALUE initial}. */
    public NumericField(int digits, int scale, boolean signed, String initial) {
        this(digits, scale, signed);
        move(new BigDecimal(initial));
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

    public BigDecimal get() {
        return value;
    }

    public int intValue() {
        return value.intValue();
    }

    /** MOVE of a numeric value into this field. */
    public void move(BigDecimal source) {
        BigInteger unscaled = source.setScale(scale, RoundingMode.DOWN).unscaledValue();
        BigInteger kept = unscaled.abs().mod(modulus);
        if (signed && unscaled.signum() < 0) {
            kept = kept.negate();
        }
        value = new BigDecimal(kept, scale);
    }

    public void move(NumericField source) {
        move(source.value);
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
        String raw = value.unscaledValue().abs().toString();
        return "0".repeat(digits - raw.length()) + raw;
    }

    /** GnuCOBOL DISPLAY format: leading sign when signed, decimal point when scaled. */
    public String display() {
        return display('.');
    }

    /** DISPLAY with {@code DECIMAL-POINT IS COMMA}: the decimal point is shown as {@code point}. */
    public String display(char point) {
        String raw = digits();
        StringBuilder sb = new StringBuilder(digits + 2);
        if (signed) {
            sb.append(value.signum() < 0 ? '-' : '+');
        }
        sb.append(raw, 0, digits - scale);
        if (scale > 0) {
            sb.append(point).append(raw, digits - scale, digits);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return display();
    }
}
