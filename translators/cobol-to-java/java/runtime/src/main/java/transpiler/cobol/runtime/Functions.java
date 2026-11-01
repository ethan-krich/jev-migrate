package transpiler.cobol.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * The exact intrinsic functions, as GnuCOBOL 3.2 computes them. Invalid arguments give the
 * value GnuCOBOL returns for them (zero), not an exception.
 */
public final class Functions {
    /** Day 1 of the integer date functions is 1601-01-01. */
    private static final LocalDate EPOCH = LocalDate.of(1600, 12, 31);

    private Functions() {}

    // ------------------------------------------------------------- strings

    /** UPPER-CASE: ASCII letters only (the C locale). */
    public static String upperCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= 'a' && c <= 'z' ? (char) (c - 32) : c);
        }
        return sb.toString();
    }

    public static String lowerCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c);
        }
        return sb.toString();
    }

    public static String reverse(String s) {
        return new StringBuilder(s).reverse().toString();
    }

    /** TRIM: {@code mode} 0 both ends, 1 LEADING, 2 TRAILING; only spaces are removed. */
    public static String trim(String s, int mode) {
        int from = 0;
        int to = s.length();
        if (mode != 2) {
            while (from < to && s.charAt(from) == ' ') {
                from++;
            }
        }
        if (mode != 1) {
            while (to > from && s.charAt(to - 1) == ' ') {
                to--;
            }
        }
        return s.substring(from, to);
    }

    /** CHAR(n): the character with ordinal n (code n - 1). */
    public static String charOf(int n) {
        return n >= 1 && n <= 256 ? String.valueOf((char) (n - 1)) : " ";
    }

    /** ORD(c): the ordinal of the first character (its code + 1). */
    public static int ord(String s) {
        return s.isEmpty() ? 0 : (s.charAt(0) & 0xFF) + 1;
    }

    /** {@code (start : length)} of a function result. */
    public static String substring(String s, int start, int length) {
        if (start < 1 || length < 0 || start - 1 + length > s.length()) {
            Bounds.fail("reference modification of a function result out of bounds: " + start + ":" + length);
        }
        return s.substring(start - 1, start - 1 + length);
    }

    /** CURRENT-DATE: YYYYMMDDhhmmsscc followed by the UTC offset (+hhmm), from {@link Clock}. */
    public static String currentDate() {
        LocalDateTime t = Clock.now();
        int seconds = ZoneId.systemDefault().getRules().getOffset(t).getTotalSeconds();
        int minutes = Math.abs(seconds) / 60;
        String digits = String.format("%04d%02d%02d%02d%02d%02d%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(),
                t.getMinute(), t.getSecond(), System.getenv(Clock.VARIABLE) == null ? t.getNano() / 10_000_000 : 0);
        return digits + (seconds < 0 ? "-" : "+") + String.format("%02d%02d", minutes / 60, minutes % 60);
    }

    // ------------------------------------------------------------- numbers

    /** MOD: {@code a - b * FLOOR(a / b)}; zero for a zero divisor. */
    public static BigDecimal mod(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal q = a.divide(b, 0, RoundingMode.FLOOR);
        return a.subtract(b.multiply(q));
    }

    /** REM: {@code a - b * INTEGER-PART(a / b)}; zero for a zero divisor. */
    public static BigDecimal rem(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return a.subtract(b.multiply(a.divideToIntegralValue(b)));
    }

    /** INTEGER: the greatest integer not greater than the argument. */
    public static BigDecimal integer(BigDecimal a) {
        return a.setScale(0, RoundingMode.FLOOR);
    }

    /** INTEGER-PART: the integer part (towards zero). */
    public static BigDecimal integerPart(BigDecimal a) {
        return a.setScale(0, RoundingMode.DOWN);
    }

    public static BigDecimal abs(BigDecimal a) {
        return a.abs();
    }

    public static BigDecimal max(BigDecimal... values) {
        return values[ordMax(values) - 1];
    }

    public static BigDecimal min(BigDecimal... values) {
        return values[ordMin(values) - 1];
    }

    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            total = total.add(v);
        }
        return total;
    }

    /** ORD-MAX: the position of the first largest argument. */
    public static int ordMax(BigDecimal... values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i].compareTo(values[best]) > 0) {
                best = i;
            }
        }
        return best + 1;
    }

    public static int ordMin(BigDecimal... values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i].compareTo(values[best]) < 0) {
                best = i;
            }
        }
        return best + 1;
    }

    /** NUMVAL: {@code [sp][+|-][sp]digits[.digits][sp][+|-|CR|DB][sp]}; zero when the text is not of that form. */
    public static BigDecimal numval(String s) {
        return parse(s, false);
    }

    /** NUMVAL-C: as NUMVAL, also allowing a currency sign ($) and comma separators in the integer part. */
    public static BigDecimal numvalC(String s) {
        return parse(s, true);
    }

    private static BigDecimal parse(String s, boolean currency) {
        String t = s.trim();
        boolean neg = false;
        if (t.startsWith("+") || t.startsWith("-")) {
            neg = t.startsWith("-");
            t = t.substring(1).trim();
        } else if (t.endsWith("CR") || t.endsWith("DB") || t.endsWith("cr") || t.endsWith("db")) {
            neg = true;
            t = t.substring(0, t.length() - 2).trim();
        } else if (t.endsWith("+") || t.endsWith("-")) {
            neg = t.endsWith("-");
            t = t.substring(0, t.length() - 1).trim();
        }
        if (currency && t.startsWith("$")) {
            t = t.substring(1).trim();
        }
        if (currency) {
            t = t.replace(",", "");
        }
        if (!t.matches("[0-9]*(\\.[0-9]*)?") || t.replace(".", "").isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = new BigDecimal(t.startsWith(".") ? "0" + t : t.endsWith(".") ? t + "0" : t);
        return neg ? v.negate() : v;
    }

    // ------------------------------------------------------------- dates

    /** INTEGER-OF-DATE(YYYYMMDD): days since 1600-12-31; zero for an invalid date. */
    public static BigDecimal integerOfDate(BigDecimal yyyymmdd) {
        try {
            int v = yyyymmdd.intValueExact();
            LocalDate d = LocalDate.of(v / 10000, v / 100 % 100, v % 100);
            return d.getYear() < 1601 ? BigDecimal.ZERO : BigDecimal.valueOf(ChronoUnit.DAYS.between(EPOCH, d));
        } catch (ArithmeticException | DateTimeException e) {
            return BigDecimal.ZERO;
        }
    }

    /** INTEGER-OF-DAY(YYYYDDD). */
    public static BigDecimal integerOfDay(BigDecimal yyyyddd) {
        try {
            int v = yyyyddd.intValueExact();
            LocalDate d = LocalDate.ofYearDay(v / 1000, v % 1000);
            return d.getYear() < 1601 ? BigDecimal.ZERO : BigDecimal.valueOf(ChronoUnit.DAYS.between(EPOCH, d));
        } catch (ArithmeticException | DateTimeException e) {
            return BigDecimal.ZERO;
        }
    }

    /** DATE-OF-INTEGER(n): YYYYMMDD; zero when n is out of range. */
    public static BigDecimal dateOfInteger(BigDecimal n) {
        LocalDate d = fromInteger(n);
        return d == null ? BigDecimal.ZERO : BigDecimal.valueOf(d.getYear() * 10000L + d.getMonthValue() * 100L + d.getDayOfMonth());
    }

    /** DAY-OF-INTEGER(n): YYYYDDD. */
    public static BigDecimal dayOfInteger(BigDecimal n) {
        LocalDate d = fromInteger(n);
        return d == null ? BigDecimal.ZERO : BigDecimal.valueOf(d.getYear() * 1000L + d.getDayOfYear());
    }

    private static LocalDate fromInteger(BigDecimal n) {
        try {
            long days = n.longValueExact();
            // 3067671 is 9999-12-31.
            return days < 1 || days > 3067671 ? null : EPOCH.plusDays(days);
        } catch (ArithmeticException e) {
            return null;
        }
    }

    // ------------------------------------------------------------- DISPLAY

    /**
     * DISPLAY of an integer function result (MOD, ORD, INTEGER, ...): the field GnuCOBOL allocates
     * for it, as {@link #decimalDisplay} (nine digits up to 32 bits, a minus sign only when negative).
     */
    public static String integerDisplay(BigDecimal value) {
        return decimalDisplay(new BigDecimal(value.toBigIntegerExact()), '.');
    }

    /** ABS of an item or literal: a copy in its own picture and usage holding the absolute value (cob_intr_abs). */
    public static NumericField abs(NumericField f) {
        NumericField r = f.like();
        r.move(f.get().abs());
        return r;
    }

    /** MAX of items and literals: the first largest one, in its own picture (cob_intr_max). */
    public static NumericField maxField(NumericField... fields) {
        NumericField best = fields[0];
        for (NumericField f : fields) {
            if (f.get().compareTo(best.get()) > 0) {
                best = f;
            }
        }
        return best;
    }

    /** MIN of items and literals: the first smallest one, in its own picture (cob_intr_min). */
    public static NumericField minField(NumericField... fields) {
        NumericField best = fields[0];
        for (NumericField f : fields) {
            if (f.get().compareTo(best.get()) < 0) {
                best = f;
            }
        }
        return best;
    }

    /**
     * DISPLAY of a result that GnuCOBOL 3.2 puts in a field sized from its value (cob_alloc_field:
     * SUM, NUMVAL, NUMVAL-C, MOD, REM). Trailing fractional zeros are dropped; a value that fits 32
     * bits (31 when negative) with fewer than 10 decimals gets nine digits, one that fits 64 bits
     * (63) with fewer than 19 decimals twenty, anything else as many as GMP's size estimate gives.
     * The sign is shown only when negative; higher digits than the field holds are cut, as a MOVE.
     */
    public static String decimalDisplay(BigDecimal value, char point) {
        BigDecimal v = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (v.scale() < 0) {
            v = v.setScale(0);
        }
        BigInteger unscaled = v.unscaledValue();
        int scale = v.scale();
        boolean negative = unscaled.signum() < 0;
        int bits = unscaled.signum() == 0 ? 1 : unscaled.abs().bitLength();
        int digits;
        if (bits < 33 - (negative ? 1 : 0) && scale < 10) {
            digits = 9;
        } else if (bits < 65 - (negative ? 1 : 0) && scale < 19) {
            digits = 20;
        } else {
            // mpz_sizeinbase (value, 10): the digits of the largest number with as many bits.
            digits = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE).toString().length();
        }
        digits = Math.max(digits, scale);
        String raw = unscaled.abs().toString();
        raw = raw.length() >= digits ? raw.substring(raw.length() - digits) : "0".repeat(digits - raw.length()) + raw;
        StringBuilder sb = new StringBuilder(digits + 2);
        if (negative) {
            sb.append('-');
        }
        sb.append(raw, 0, digits - scale);
        if (scale > 0) {
            sb.append(point).append(raw, digits - scale, digits);
        }
        return sb.toString();
    }

    /** DISPLAY of a fixed-width unsigned result (DATE-OF-INTEGER: 8, DAY-OF-INTEGER: 7). */
    public static String digits(BigDecimal value, int width) {
        String digits = value.toBigInteger().abs().toString();
        return digits.length() >= width ? digits.substring(digits.length() - width) : "0".repeat(width - digits.length()) + digits;
    }
}
