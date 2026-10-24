package transpiler.cobol2java.translate;

import java.util.Locale;
import java.util.Set;

/** Deterministic COBOL-name to Java-identifier conversion. */
public final class Names {
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var",
            "record", "yield", "sealed", "permits", "non-sealed", "when", "module", "open", "exports", "requires",
            "_");

    private Names() {}

    /** PAY-CALC -> PayCalc. */
    public static String className(String cobolName) {
        String pascal = pascal(cobolName);
        return Character.isDigit(pascal.charAt(0)) ? "_" + pascal : pascal;
    }

    /** WS-TOTAL-AMT -> wsTotalAmt. */
    public static String fieldName(String cobolName) {
        String pascal = pascal(cobolName);
        String camel = Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
        if (Character.isDigit(camel.charAt(0))) {
            camel = "_" + camel;
        }
        return KEYWORDS.contains(camel) ? camel + "_" : camel;
    }

    /** 100-MAIN-LOGIC -> para100MainLogic (prefix keeps paragraphs that start with digits valid). */
    public static String methodName(String prefix, String cobolName) {
        return prefix + pascal(cobolName);
    }

    /** A directory name as a package segment: lower case, '-' to '_', keywords suffixed, digits prefixed. */
    public static String packageSegment(String directory) {
        StringBuilder sb = new StringBuilder();
        for (char c : directory.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
        }
        String segment = sb.toString();
        if (segment.isEmpty() || Character.isDigit(segment.charAt(0))) {
            segment = "_" + segment;
        }
        return KEYWORDS.contains(segment) ? segment + "_" : segment;
    }

    public static boolean isValidPackage(String pkg) {
        if (pkg.isEmpty()) {
            return false;
        }
        for (String part : pkg.split("\\.", -1)) {
            if (part.isEmpty() || KEYWORDS.contains(part) || !Character.isJavaIdentifierStart(part.charAt(0))
                    || !part.chars().allMatch(Character::isJavaIdentifierPart)) {
                return false;
            }
        }
        return true;
    }

    private static String pascal(String cobolName) {
        StringBuilder sb = new StringBuilder();
        for (String part : cobolName.split("[-_]+")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        if (sb.isEmpty()) {
            throw new IllegalArgumentException("empty COBOL name: " + cobolName);
        }
        return sb.toString();
    }
}
