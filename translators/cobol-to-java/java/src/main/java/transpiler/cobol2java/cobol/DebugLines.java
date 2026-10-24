package transpiler.cobol2java.cobol;

import io.proleap.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum;

/**
 * Debugging lines ({@code D} or {@code d} in the indicator area). This inspects the reference
 * format's indicator column only; it does not interpret COBOL text.
 */
final class DebugLines {
    private DebugLines() {}

    /** Column of the indicator area (0-based) in a source format. */
    private static int indicator(CobolSourceFormatEnum format) {
        return format == CobolSourceFormatEnum.TANDEM ? 0 : 6;
    }

    static boolean contains(String source, CobolSourceFormatEnum format) {
        return !toComments(source, format).equals(source);
    }

    /** The source with every debugging line turned into a comment line; line structure is kept. */
    static String toComments(String source, CobolSourceFormatEnum format) {
        int col = indicator(format);
        StringBuilder out = new StringBuilder(source.length());
        int start = 0;
        while (start <= source.length()) {
            int end = source.indexOf('\n', start);
            String line = end < 0 ? source.substring(start) : source.substring(start, end);
            if (line.length() > col && (line.charAt(col) == 'D' || line.charAt(col) == 'd')) {
                line = line.substring(0, col) + '*' + line.substring(col + 1);
            }
            out.append(line);
            if (end < 0) {
                break;
            }
            out.append('\n');
            start = end + 1;
        }
        return out.toString();
    }
}
