package transpiler.cobol.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
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

    // GnuCOBOL 3.2 cob_decimal semantics for COMPUTE with / and **. A null operand or result is
    // GnuCOBOL's NaN (division by zero): it propagates, and storing it is a size error.

    /** Extra decimal digits GnuCOBOL keeps in a quotient (COB_MAX_DIGITS). */
    private static final int QUOTIENT_DIGITS = 38;

    public static BigDecimal plus(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.add(b);
    }

    public static BigDecimal minus(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.subtract(b);
    }

    public static BigDecimal times(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.multiply(b);
    }

    public static BigDecimal negate(BigDecimal a) {
        return a == null ? null : a.negate();
    }

    /**
     * cob_decimal_div: the scale becomes {@code scale(a) - scale(b)}, the dividend is shifted
     * left by 38 digits (more if that scale is negative) and the quotient truncated.
     */
    public static BigDecimal over(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.signum() == 0) {
            return null;
        }
        if (a.signum() == 0) {
            return BigDecimal.ZERO;
        }
        int scale = a.scale() - b.scale();
        int shift = QUOTIENT_DIGITS + (scale < 0 ? -scale : 0);
        BigInteger q = a.unscaledValue().multiply(BigInteger.TEN.pow(shift)).divide(b.unscaledValue());
        return new BigDecimal(q, scale + shift);
    }

    /**
     * cob_decimal_pow with an integer exponent: exact for {@code n >= 0}; for {@code n < 0},
     * 1 / a^|n| by {@link #over}. 0 ** n is 0 for n != 0, and a ** 0 is 1.
     */
    public static BigDecimal power(BigDecimal a, BigDecimal n) {
        if (a == null || n == null) {
            return null;
        }
        if (n.signum() == 0) {
            return BigDecimal.ONE;
        }
        if (a.signum() == 0) {
            return BigDecimal.ZERO;
        }
        int e = n.intValueExact();
        BigDecimal base = trim(a);
        if (e == 1) {
            return base;
        }
        BigDecimal p = trim(base.pow(Math.abs(e)));
        return e > 0 ? p : trim(over(BigDecimal.ONE, p));
    }

    /** cob_trim_decimal: drops trailing fractional zeros. */
    private static BigDecimal trim(BigDecimal d) {
        BigDecimal r = d;
        while (r.scale() > 0) {
            BigInteger[] qr = r.unscaledValue().divideAndRemainder(BigInteger.TEN);
            if (qr[1].signum() != 0) {
                break;
            }
            r = new BigDecimal(qr[0], r.scale() - 1);
        }
        return r;
    }
}
