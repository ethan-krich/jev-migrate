package transpiler.cobol2java.decision;

/**
 * Where an operation appears. {@code line} is in the preprocessed source; it equals the original
 * file's line unless COPY statements earlier in the file expanded to multiple lines.
 */
public record SourceLocation(String file, int line, int column, String text) {}
