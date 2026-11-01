package transpiler.cobol2java.cobol;

import io.proleap.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The comments of a source file by original line, which ProLeap's preprocessor drops: comment
 * lines ({@code *} or {@code /} in the indicator area) and {@code *>} comments outside literals.
 * This reads the reference format only; it does not interpret COBOL. Comments are advisory
 * context for a provider and never affect what is generated.
 *
 * <p>Text is stripped of the comment marker and of framing ({@code *}, {@code -}, {@code =});
 * banner lines with no letter or digit are left out.
 */
public final class SourceComments {
    public static final SourceComments NONE = new SourceComments(new TreeMap<>(), new TreeSet<>());

    private final TreeMap<Integer, String> comments;
    /** Lines holding code (anything other than blanks and comments). */
    private final TreeSet<Integer> code;

    private SourceComments(TreeMap<Integer, String> comments, TreeSet<Integer> code) {
        this.comments = comments;
        this.code = code;
    }

    /** The comments of {@code source}; {@code free} is GnuCOBOL's free format (before its rewrite). */
    static SourceComments of(String source, CobolSourceFormatEnum format, boolean free) {
        TreeMap<Integer, String> comments = new TreeMap<>();
        TreeSet<Integer> code = new TreeSet<>();
        int indicator = format == CobolSourceFormatEnum.TANDEM ? 0 : 6;
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace("\r", "");
            String body;
            if (free) {
                body = line;
            } else {
                if (format == CobolSourceFormatEnum.FIXED && line.length() > 72) {
                    line = line.substring(0, 72);
                }
                if (line.length() <= indicator) {
                    continue;
                }
                char c = line.charAt(indicator);
                if (c == '*' || c == '/') {
                    put(comments, i + 1, line.substring(indicator + 1));
                    continue;
                }
                body = line.substring(indicator + 1);
            }
            int at = floating(body);
            String before = at < 0 ? body : body.substring(0, at);
            if (!before.isBlank()) {
                code.add(i + 1);
            }
            if (at >= 0) {
                put(comments, i + 1, body.substring(at + 2));
            }
        }
        return new SourceComments(comments, code);
    }

    /** The comments on lines {@code from} to {@code to}, in order. */
    public List<String> between(int from, int to) {
        return new ArrayList<>(comments.subMap(from, true, to, true).values());
    }

    /** The comments between the last code line before {@code line} and {@code line} itself. */
    public List<String> above(int line) {
        Integer previous = code.lower(line);
        return new ArrayList<>(comments.subMap(previous == null ? 0 : previous, false, line, false).values());
    }

    /** Whether the source has no comments. */
    public boolean isEmpty() {
        return comments.isEmpty();
    }

    private static void put(TreeMap<Integer, String> comments, int line, String text) {
        String t = text.strip().replaceAll("^[*=\\-\\s]+|[*=\\-\\s]+$", "");
        if (t.chars().anyMatch(Character::isLetterOrDigit)) {
            comments.put(line, t);
        }
    }

    /** The column of a {@code *>} outside a literal, or -1. */
    private static int floating(String body) {
        char quote = 0;
        for (int i = 0; i + 1 < body.length(); i++) {
            char c = body.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '*' && body.charAt(i + 1) == '>') {
                return i;
            }
        }
        return -1;
    }
}
