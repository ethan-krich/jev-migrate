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

    public record Options(CobolSourceFormatEnum format, Charset charset) {
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

        /** {@code missing_copybook} or {@code syntax_error}. */
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
        Program program;
        try {
            program = new CobolParserRunnerImpl().analyzeFile(file.toFile(), params);
        } catch (RuntimeException | java.io.IOException e) {
            String message = rootMessage(e);
            String kind = message != null && message.startsWith("Could not find copy book") ? "missing_copybook" : "syntax_error";
            throw new ParseFailure(kind, message == null ? e.getClass().getSimpleName() : message, e);
        }
        SourceMap map;
        try {
            int lines = program.getCompilationUnits().getFirst().getLines().size();
            map = SourceMap.build(java.nio.file.Files.readString(file, options.charset()), params, lines);
        } catch (java.io.IOException | RuntimeException e) {
            map = SourceMap.identity();
        }
        return new Parsed(program, map);
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
