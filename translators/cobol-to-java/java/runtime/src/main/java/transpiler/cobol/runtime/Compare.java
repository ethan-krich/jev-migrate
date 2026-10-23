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
}
