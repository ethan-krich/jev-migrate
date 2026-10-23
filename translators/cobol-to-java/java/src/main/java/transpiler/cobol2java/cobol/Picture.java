package transpiler.cobol2java.cobol;

import java.util.Locale;
import java.util.Optional;

/**
 * The supported subset of PICTURE strings: {@code X(n)} alphanumeric and {@code [S]9(i)[V9(s)]}
 * zoned numeric. Edited, alphabetic, national and scaled ({@code P}) pictures are reported as
 * unsupported rather than approximated.
 */
public sealed interface Picture {
    record Alphanumeric(int length) implements Picture {}

    record Numeric(int digits, int scale, boolean signed) implements Picture {}

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
        if (s.chars().allMatch(c -> c == 'X')) {
            return Result.of(new Alphanumeric(s.length()));
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

    record Result(Optional<Picture> picture, String unsupportedReason) {
        static Result of(Picture p) {
            return new Result(Optional.of(p), null);
        }

        static Result unsupported(String reason) {
            return new Result(Optional.empty(), reason);
        }
    }
}
