package transpiler.cobol.runtime;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact intermediate arithmetic for ADD, SUBTRACT, MULTIPLY, DIVIDE and COMPUTE. */
public final class Arithmetic {
    private Arithmetic() {}

    public static BigDecimal sum(BigDecimal... operands) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal operand : operands) {
            total = total.add(operand);
        }
        return total;
    }

    /**
     * Quotient for storing into a receiver with {@code receiverScale} decimal places.
     *
     * <p>Without ROUNDED the result is truncated at the receiver's scale; with ROUNDED one extra
     * digit is kept so {@link NumericField#store} can round half away from zero. Both are exact
     * for those purposes. Returns {@code null} on division by zero (a size error condition).
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor, int receiverScale, boolean rounded) {
        if (divisor.signum() == 0) {
            return null;
        }
        return dividend.divide(divisor, receiverScale + (rounded ? 1 : 0), RoundingMode.DOWN);
    }

    /**
     * DIVIDE ... REMAINDER: {@code dividend - quotient * divisor}, where the quotient is truncated
     * to the quotient receiver's scale. As in GnuCOBOL 3.2 the quotient is never rounded here
     * (even with ROUNDED) and is not cut to the receiver's integer digits. {@code null} on
     * division by zero.
     */
    public static BigDecimal remainder(BigDecimal dividend, BigDecimal divisor, int quotientScale) {
        if (divisor.signum() == 0) {
            return null;
        }
        return dividend.subtract(dividend.divide(divisor, quotientScale, RoundingMode.DOWN).multiply(divisor));
    }
}
