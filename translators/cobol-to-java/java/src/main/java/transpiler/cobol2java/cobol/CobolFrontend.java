package transpiler.cobol2java.cobol;

import io.proleap.cobol.asg.metamodel.Program;
import io.proleap.cobol.asg.params.CobolParserParams;
import io.proleap.cobol.asg.params.impl.CobolParserParamsImpl;
import io.proleap.cobol.asg.runner.impl.CobolParserRunnerImpl;
import io.proleap.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Parses COBOL source into ProLeap's abstract semantic graph. ProLeap types stay inside this module. */
public final class CobolFrontend {

    /** @param debuggingLines compile {@code D} lines (otherwise they are comments, as in GnuCOBOL) */
    public record Options(CobolSourceFormatEnum format, Charset charset, boolean debuggingLines) {
        public static CobolSourceFormatEnum parseFormat(String value) {
            return CobolSourceFormatEnum.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    /** A parse that failed; the message is ProLeap's (preprocessor, lexer or parser). */
    public static final class ParseFailure extends Exception {
        private final String kind;

        ParseFailure(String kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        /** {@code missing_copybook}, {@code syntax_error} or {@code unsupported_dialect}. */
        public String kind() {
            return kind;
        }
    }

    private final Options options;

    public CobolFrontend(Options options) {
        this.options = options;
    }

    public java.nio.charset.Charset charset() {
        return options.charset();
    }

    /** A parsed program and the map from preprocessed to original line numbers. */
    public record Parsed(Program program, SourceMap sourceMap) {}

    public Parsed parse(Path file, List<Path> copybookDirectories, List<String> copybookExtensions) throws ParseFailure {
        CobolParserParams params = new CobolParserParamsImpl();
        params.setFormat(options.format());
        params.setCharset(options.charset());
        params.setCopyBookDirectories(copybookDirectories.stream().map(Path::toFile).toList());
        // ProLeap expects extensions without the leading dot; "" also matches members without one.
        List<String> extensions = new java.util.ArrayList<>(
                copybookExtensions.stream().map(e -> e.startsWith(".") ? e.substring(1) : e).toList());
        extensions.add("");
        params.setCopyBookExtensions(extensions);
        params.setIgnoreSyntaxErrors(false);
        String source;
        try {
            source = java.nio.file.Files.readString(file, options.charset());
        } catch (java.io.IOException e) {
            throw new ParseFailure("syntax_error", "cannot read " + file + ": " + e.getMessage(), e);
        }
        String unitName = unitName(file);
        // ProLeap always compiles debugging lines; GnuCOBOL treats them as comments unless asked.
        Program program = analyze(source, unitName, params);
        if (!options.debuggingLines() && !debuggingMode(program)) {
            String withoutDebug = DebugLines.toComments(source, options.format());
            if (!withoutDebug.equals(source)) {
                program = analyze(withoutDebug, unitName, params);
            }
            for (java.io.File copybook : SourceMap.copybooks(withoutDebug, params)) {
                if (DebugLines.contains(readCopybook(copybook), options.format())) {
                    throw new ParseFailure("unsupported_dialect", "copybook " + copybook.getName()
                            + " has debugging lines (D in the indicator area), which cannot be excluded inside copybooks;"
                            + " set Dialect(debugging_lines=True) or move them to the program", null);
                }
            }
        }
        SourceMap map;
        try {
            int lines = program.getCompilationUnits().getFirst().getLines().size();
            map = SourceMap.build(source, params, lines);
        } catch (RuntimeException e) {
            map = SourceMap.identity();
        }
        return new Parsed(program, map);
    }

    private String readCopybook(java.io.File copybook) throws ParseFailure {
        try {
            return java.nio.file.Files.readString(copybook.toPath(), options.charset());
        } catch (java.io.IOException e) {
            throw new ParseFailure("missing_copybook", "cannot read copybook " + copybook + ": " + e.getMessage(), e);
        }
    }

    private static Program analyze(String source, String unitName, CobolParserParams params) throws ParseFailure {
        try {
            return new CobolParserRunnerImpl().analyzeCode(source, unitName, params);
        } catch (RuntimeException | java.io.IOException e) {
            String message = rootMessage(e);
            if (message != null && message.startsWith("Could not find copy book")) {
                throw new ParseFailure("missing_copybook", message, e);
            }
            // ProLeap cannot tell invalid COBOL from a dialect its grammar does not cover; either way
            // the file cannot be translated. Name the construct when the failing line shows it.
            String detail = message == null ? e.getClass().getSimpleName() : message;
            if (detail.length() > 300) {
                detail = detail.substring(0, 300) + "...";
            }
            int line = failingLine(detail);
            String construct = line > 0 ? construct(source, line) : null;
            throw new ParseFailure("unsupported_dialect", (construct == null ? "a construct outside ProLeap's COBOL grammar (or invalid COBOL)"
                    : construct + ", which ProLeap's COBOL grammar does not cover") + (line > 0 ? " at line " + line : "") + ": " + detail, e);
        }
    }

    /** The line of a ProLeap "syntax error in line L:C" message, or 0. */
    static int failingLine(String message) {
        int at = message.indexOf("line ");
        if (at < 0) {
            return 0;
        }
        int i = at + 5;
        int start = i;
        while (i < message.length() && Character.isDigit(message.charAt(i))) {
            i++;
        }
        return i > start ? Integer.parseInt(message.substring(start, i)) : 0;
    }

    /** Dialect constructs ProLeap does not parse, recognised by their words on the failing line (for the message only). */
    static String construct(String source, int line) {
        String[] lines = source.split("\n", -1);
        if (line > lines.length) {
            return null;
        }
        List<String> words = new java.util.ArrayList<>();
        for (String w : lines[line - 1].toUpperCase(java.util.Locale.ROOT).split("[\\s.]+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            String next = i + 1 < words.size() ? words.get(i + 1) : "";
            if ((w.equals("XML") || w.equals("JSON")) && (next.equals("GENERATE") || next.equals("PARSE"))) {
                return w + " " + next;
            }
            switch (w) {
                case "ALLOCATE", "FREE" -> {
                    return "ALLOCATE / FREE";
                }
                case "INVOKE", "CLASS-ID", "METHOD-ID", "FACTORY", "INTERFACE-ID" -> {
                    return "object-oriented COBOL (" + w + ")";
                }
                default -> {
                    if (w.startsWith(">>") || w.startsWith("$SET")) {
                        return "the compiler directive " + w;
                    }
                }
            }
        }
        return null;
    }

    /** ProLeap's compilation-unit name for a file (capitalised base name). */
    private static String unitName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.isEmpty() ? base : Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    /** SOURCE-COMPUTER ... WITH DEBUGGING MODE compiles debugging lines regardless of the dialect. */
    private static boolean debuggingMode(Program program) {
        for (var cu : program.getCompilationUnits()) {
            for (var unit : cu.getProgramUnits()) {
                var env = unit.getEnvironmentDivision();
                var config = env == null ? null : env.getConfigurationSection();
                var sc = config == null ? null : config.getSourceComputerParagraph();
                if (sc != null && sc.isDebuggingMode()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        String message = t.getMessage();
        for (Throwable c = t.getCause(); c != null; c = c.getCause()) {
            if (c.getMessage() != null) {
                message = c.getMessage();
            }
        }
        return message;
    }

}
