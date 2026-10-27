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

    /**
     * The position of each byte in GnuCOBOL 3.2's EBCDIC collating sequence (-fdefault-colseq=EBCDIC):
     * its ASCII-to-EBCDIC translation, so that bytes compare as their EBCDIC codes.
     */
    private static final int[] EBCDIC = {
            0, 1, 2, 3, 55, 45, 46, 47, 22, 5, 37, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 60, 61, 50, 38, 24, 25, 63, 39, 28, 29, 30, 31,
            64, 90, 127, 123, 91, 108, 80, 125, 77, 93, 92, 78, 107, 96, 75, 97,
            240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 122, 94, 76, 126, 110, 111,
            124, 193, 194, 195, 196, 197, 198, 199, 200, 201, 209, 210, 211, 212, 213, 214,
            215, 216, 217, 226, 227, 228, 229, 230, 231, 232, 233, 173, 224, 189, 95, 109,
            121, 129, 130, 131, 132, 133, 134, 135, 136, 137, 145, 146, 147, 148, 149, 150,
            151, 152, 153, 162, 163, 164, 165, 166, 167, 168, 169, 192, 106, 208, 161, 7,
            104, 220, 81, 66, 67, 68, 71, 72, 82, 83, 84, 87, 86, 88, 99, 103,
            113, 156, 158, 203, 204, 205, 219, 221, 223, 236, 252, 176, 177, 178, 62, 180,
            69, 85, 206, 222, 73, 105, 154, 155, 171, 159, 186, 184, 183, 170, 138, 139,
            182, 181, 98, 79, 100, 101, 102, 32, 33, 34, 112, 35, 114, 115, 116, 190,
            118, 119, 120, 128, 36, 21, 140, 141, 142, 65, 6, 23, 40, 41, 157, 42,
            43, 44, 9, 10, 172, 74, 174, 175, 27, 48, 49, 250, 26, 51, 52, 53,
            54, 89, 8, 56, 188, 57, 160, 191, 202, 58, 254, 59, 4, 207, 218, 20,
            225, 143, 70, 117, 253, 235, 238, 237, 144, 239, 179, 251, 185, 234, 187, 255,
    };

    /** Alphanumeric comparison in the EBCDIC collating sequence (Dialect.collating_sequence = ebcdic). */
    public static int ebcdic(String left, String right) {
        int n = Math.max(left.length(), right.length());
        for (int i = 0; i < n; i++) {
            int a = EBCDIC[(i < left.length() ? left.charAt(i) : ' ') & 0xFF];
            int b = EBCDIC[(i < right.length() ? right.charAt(i) : ' ') & 0xFF];
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }

    /** {@link #figurative(String, char)} in the EBCDIC collating sequence. */
    public static int figurativeEbcdic(String operand, char figurative) {
        return ebcdic(operand, String.valueOf(figurative).repeat(operand.length()));
    }

    /** {@link #figurative(String, String)} in the EBCDIC collating sequence. */
    public static int figurativeEbcdic(String operand, String pattern) {
        return ebcdic(operand, repeat(pattern, operand.length()));
    }

    /** A SORT / MERGE key whose natural order is the EBCDIC collating sequence. */
    public static String ebcdicKey(String value) {
        char[] out = new char[value.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (char) EBCDIC[value.charAt(i) & 0xFF];
        }
        return new String(out);
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
