package transpiler.cobol2java.cobol;

import java.util.Locale;

/**
 * Free-format source (GnuCOBOL's {@code -free}) rewritten line for line as VARIABLE format, which
 * ProLeap reads: each line's text moves to column 8, so line numbers stay the same. This handles
 * the reference format only; it does not interpret COBOL.
 *
 * <ul>
 *   <li>{@code *>} starts a comment anywhere outside a literal;
 *   <li>{@code >>D} marks a debugging line;
 *   <li>{@code >>SOURCE [FORMAT] [IS] FREE} lines become comments.
 * </ul>
 *
 * Anything this cannot carry over exactly is refused: other compiler directives ({@code >>} or
 * {@code $}), a literal that is not closed on its line, and {@code >>SOURCE} switching to another
 * format.
 */
final class FreeFormat {
    private FreeFormat() {}

    /** A free-format construct that cannot be rewritten exactly. */
    static final class Unsupported extends Exception {
        final int line;

        Unsupported(int line, String message) {
            super(message);
            this.line = line;
        }
    }

    static String toVariable(String source) throws Unsupported {
        StringBuilder out = new StringBuilder(source.length() + source.length() / 8);
        int start = 0;
        int number = 1;
        while (start <= source.length()) {
            int end = source.indexOf('\n', start);
            String line = end < 0 ? source.substring(start) : source.substring(start, end);
            boolean cr = line.endsWith("\r");
            if (cr) {
                line = line.substring(0, line.length() - 1);
            }
            out.append(convert(line, number));
            if (cr) {
                out.append('\r');
            }
            if (end < 0) {
                break;
            }
            out.append('\n');
            start = end + 1;
            number++;
        }
        return out.toString();
    }

    private static String convert(String line, int number) throws Unsupported {
        String trimmed = line.stripLeading();
        if (trimmed.isEmpty()) {
            return "";
        }
        char indicator = ' ';
        String text = line;
        if (trimmed.startsWith(">>")) {
            String directive = trimmed.substring(2).toUpperCase(Locale.ROOT);
            if (directive.startsWith("D ") || directive.equals("D")) {
                indicator = 'D';
                text = trimmed.substring(3);
            } else if (directive.matches("SOURCE\\s+(FORMAT\\s+)?(IS\\s+)?FREE\\s*(\\*>.*)?")) {
                return "      *" + line;
            } else {
                throw new Unsupported(number, "compiler directive " + trimmed.split("\\s+")[0] + " in free-format source");
            }
        } else if (trimmed.startsWith("$")) {
            throw new Unsupported(number, "compiler directive " + trimmed.split("\\s+")[0] + " in free-format source");
        }
        // Remove a *> comment, outside literals; a literal must close on its line.
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '>') {
                String code = text.substring(0, i);
                return code.isBlank() && indicator == ' ' ? "      *" + text.substring(i) : "      " + indicator + code;
            }
        }
        if (quote != 0) {
            throw new Unsupported(number, "a literal continued on the next line in free-format source");
        }
        return "      " + indicator + text;
    }
}
