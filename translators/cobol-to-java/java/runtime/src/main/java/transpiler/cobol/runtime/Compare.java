package transpiler.cobol.runtime;

import java.math.BigDecimal;

/** COBOL relation-condition comparisons. */
public final class Compare {
    private Compare() {}

    /** Algebraic comparison of numeric operands. */
    public static int numeric(BigDecimal left, BigDecimal right) {
        return left.compareTo(right);
    }

    /**
     * Alphanumeric comparison: the shorter operand is padded with spaces, then characters are
     * compared in the native (ASCII) collating sequence.
     */
    public static int alphanumeric(String left, String right) {
        int n = Math.max(left.length(), right.length());
        for (int i = 0; i < n; i++) {
            char a = i < left.length() ? left.charAt(i) : ' ';
            char b = i < right.length() ? right.charAt(i) : ' ';
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }

    /** Comparison against a figurative constant repeated to the operand's length. */
    public static int figurative(String operand, char figurative) {
        return alphanumeric(operand, String.valueOf(figurative).repeat(operand.length()));
    }

    /** Comparison against {@code ALL literal}: the pattern repeated to the operand's length. */
    public static int figurative(String operand, String pattern) {
        return alphanumeric(operand, repeat(pattern, operand.length()));
    }

    /** {@code pattern} repeated and cut to {@code length} characters. */
    public static String repeat(String pattern, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(pattern.charAt(i % pattern.length()));
        }
        return sb.toString();
    }

    /** Class condition NUMERIC on alphanumeric data: every character is a digit 0-9. */
    public static boolean isNumeric(String value) {
        return value.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** Class condition ALPHABETIC: letters A-Z, a-z and space. */
    public static boolean isAlphabetic(String value) {
        return value.chars().allMatch(c -> c == ' ' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
    }

    public static boolean isAlphabeticUpper(String value) {
        return value.chars().allMatch(c -> c == ' ' || (c >= 'A' && c <= 'Z'));
    }

    public static boolean isAlphabeticLower(String value) {
        return value.chars().allMatch(c -> c == ' ' || (c >= 'a' && c <= 'z'));
    }

    /** A user CLASS condition: every character is one of {@code members}. */
    public static boolean inClass(String value, String members) {
        return value.chars().allMatch(c -> members.indexOf(c) >= 0);
    }
}
