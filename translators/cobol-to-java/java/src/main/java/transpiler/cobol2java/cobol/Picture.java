package transpiler.cobol2java.cobol;

import java.util.Locale;
import java.util.Optional;

/**
 * The supported subset of PICTURE strings: {@code X(n)} / {@code A(n)} alphanumeric,
 * {@code [S]9(i)[V9(s)]} numeric, numeric-edited ({@code Z * $ + - CR DB . , B 0 /} with
 * {@code 9} and {@code V}) and alphanumeric-edited ({@code X A 9} with {@code B 0 /}). National
 * and scaled ({@code P}) pictures are reported as unsupported rather than approximated.
 */
public sealed interface Picture {
    record Alphanumeric(int length) implements Picture {}

    record Numeric(int digits, int scale, boolean signed) implements Picture {}

    /** An edited picture with repetitions expanded; {@code length} is its size in bytes. */
    record Edited(String picture, boolean numeric, int length) implements Picture {}

    /** Parses a picture string, or explains why it is outside the supported subset. */
    static Result parse(String raw) {
        String pic = raw.toUpperCase(Locale.ROOT);
        StringBuilder expanded = new StringBuilder();
        for (int i = 0; i < pic.length(); i++) {
            char c = pic.charAt(i);
            if (c == '(') {
                int close = pic.indexOf(')', i);
                if (close < 0 || expanded.isEmpty()) {
                    return Result.unsupported("malformed repetition in PICTURE " + raw);
                }
                int count;
                try {
                    count = Integer.parseInt(pic.substring(i + 1, close).trim());
                } catch (NumberFormatException e) {
                    return Result.unsupported("malformed repetition in PICTURE " + raw);
                }
                if (count <= 0) {
                    return Result.unsupported("non-positive repetition in PICTURE " + raw);
                }
                char last = expanded.charAt(expanded.length() - 1);
                expanded.append(String.valueOf(last).repeat(count - 1));
                i = close;
            } else {
                expanded.append(c);
            }
        }
        String s = expanded.toString();
        if (s.chars().allMatch(c -> c == 'X' || c == 'A')) {
            return Result.of(new Alphanumeric(s.length()));
        }
        if (!s.matches("S?[9V]*")) {
            return edited(raw, s);
        }
        boolean signed = false;
        int start = 0;
        if (s.startsWith("S")) {
            signed = true;
            start = 1;
        }
        int intDigits = 0;
        int scale = 0;
        boolean seenV = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '9') {
                if (seenV) {
                    scale++;
                } else {
                    intDigits++;
                }
            } else if (c == 'V' && !seenV) {
                seenV = true;
            } else {
                return Result.unsupported("PICTURE " + raw + " uses '" + c + "', which is outside the supported subset (X, 9, S, V)");
            }
        }
        int digits = intDigits + scale;
        if (digits == 0) {
            return Result.unsupported("PICTURE " + raw + " has no digit positions");
        }
        if (digits > 38) {
            return Result.unsupported("PICTURE " + raw + " exceeds 38 digits");
        }
        return Result.of(new Numeric(digits, scale, signed));
    }

    private static Result edited(String raw, String s) {
        if (s.contains("P")) {
            return Result.unsupported("PICTURE " + raw + " uses P (scaling), which is not supported");
        }
        if (s.contains("N") || s.contains("E") || s.contains("S")) {
            return Result.unsupported("PICTURE " + raw + " is outside the supported subset");
        }
        boolean alpha = s.contains("X") || s.contains("A");
        String allowed = alpha ? "XA9B0/" : "9VZ*$+-CRDB.,0/";
        for (char c : s.toCharArray()) {
            if (allowed.indexOf(c) < 0) {
                return Result.unsupported("PICTURE " + raw + " uses '" + c + "', which is outside the supported subset");
            }
        }
        String noCrDb = s.replace("CR", "").replace("DB", "");
        if (!alpha && (noCrDb.contains("C") || noCrDb.contains("R") || noCrDb.contains("D"))) {
            return Result.unsupported("PICTURE " + raw + " is not a valid edited picture");
        }
        if (!alpha && s.matches("[+-]\\$\\$.*")) {
            // GnuCOBOL 3.2 shows such a fixed sign inconsistently with the value.
            return Result.unsupported("PICTURE " + raw + ": a fixed sign before a floating currency string is not supported");
        }
        if (!alpha && s.chars().filter(c -> c == '9' || c == 'Z' || c == '*' || c == '$' || c == '+' || c == '-').count() > 38) {
            return Result.unsupported("PICTURE " + raw + " exceeds 38 digits");
        }
        int length = s.length() - (int) s.chars().filter(c -> c == 'V').count();
        return Result.of(new Edited(s, !alpha, length));
    }

    record Result(Optional<Picture> picture, String unsupportedReason) {
        static Result of(Picture p) {
            return new Result(Optional.of(p), null);
        }

        static Result unsupported(String reason) {
            return new Result(Optional.empty(), reason);
        }
    }
}
