package transpiler.cobol2java.cobol;

import io.proleap.cobol.CobolPreprocessorBaseListener;
import io.proleap.cobol.CobolPreprocessorLexer;
import io.proleap.cobol.CobolPreprocessorParser;
import io.proleap.cobol.asg.params.CobolParserParams;
import io.proleap.cobol.preprocessor.impl.CobolPreprocessorImpl;
import io.proleap.cobol.preprocessor.sub.copybook.impl.CobolWordCopyBookFinderImpl;
import io.proleap.cobol.preprocessor.sub.copybook.impl.FilenameCopyBookFinderImpl;
import io.proleap.cobol.preprocessor.sub.copybook.impl.LiteralCopyBookFinderImpl;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

/**
 * Maps line numbers of ProLeap's preprocessed source back to the original file.
 *
 * <p>ProLeap replaces each COPY statement with the copybook's expanded text plus a newline, so
 * every later line shifts. This map re-derives those expansions with ProLeap's own normaliser,
 * preprocessor grammar and copybook finders, then checks the prediction against the actual
 * preprocessed line count. If they disagree (e.g. REPLACE changed line structure) the map is
 * marked inexact and reports preprocessed line numbers unchanged.
 */
public final class SourceMap {
    /** A COPY expansion: original lines [start, end] became {@code added} newlines' worth of text. */
    private record Expansion(int start, int end, int added) {}

    private final List<Expansion> expansions;
    private final boolean exact;

    private SourceMap(List<Expansion> expansions, boolean exact) {
        this.expansions = expansions;
        this.exact = exact;
    }

    public static SourceMap identity() {
        return new SourceMap(List.of(), true);
    }

    public boolean exact() {
        return exact;
    }

    /** The original line for a preprocessed line; lines inside a copybook map to its COPY statement. */
    public int originalLine(int preprocessedLine) {
        if (!exact) {
            return preprocessedLine;
        }
        int shift = 0;
        for (Expansion e : expansions) {
            int regionStart = e.start() + shift;
            int regionEnd = regionStart + e.added();
            if (preprocessedLine < regionStart) {
                break;
            }
            if (preprocessedLine <= regionEnd) {
                return e.start();
            }
            shift += e.added() - (e.end() - e.start());
        }
        return preprocessedLine - shift;
    }

    static SourceMap build(String source, CobolParserParams params, int preprocessedLineCount) {
        String normalized = new Normalizer().normalize(source, params);
        var parser = new CobolPreprocessorParser(new CommonTokenStream(new CobolPreprocessorLexer(CharStreams.fromString(normalized))));
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener());
        List<Expansion> expansions = new ArrayList<>();
        boolean[] resolvable = {true};
        new ParseTreeWalker().walk(new CobolPreprocessorBaseListener() {
            @Override
            public void enterCopyStatement(CobolPreprocessorParser.CopyStatementContext ctx) {
                File copybook = find(ctx.copySource(), params);
                if (copybook == null) {
                    resolvable[0] = false;
                    return;
                }
                try {
                    String content = new CobolPreprocessorImpl().process(copybook, params);
                    int added = (int) content.chars().filter(c -> c == '\n').count() + 1;
                    expansions.add(new Expansion(ctx.getStart().getLine(), ctx.getStop().getLine(), added));
                } catch (IOException | RuntimeException e) {
                    resolvable[0] = false;
                }
            }

            @Override
            public void enterReplaceArea(CobolPreprocessorParser.ReplaceAreaContext ctx) {
                // REPLACE may change line structure in ways not modelled here.
                resolvable[0] = false;
            }
        }, parser.startRule());
        int normalizedLines = normalized.split("\n", -1).length;
        int predicted = normalizedLines;
        for (Expansion e : expansions) {
            predicted += e.added() - (e.end() - e.start());
        }
        boolean exact = resolvable[0] && Math.abs(predicted - preprocessedLineCount) <= 1;
        return new SourceMap(List.copyOf(expansions), exact);
    }

    private static File find(CobolPreprocessorParser.CopySourceContext src, CobolParserParams params) {
        if (src.cobolWord() != null) {
            return new CobolWordCopyBookFinderImpl().findCopyBook(params, src.cobolWord());
        }
        if (src.literal() != null) {
            return new LiteralCopyBookFinderImpl().findCopyBook(params, src.literal());
        }
        if (src.filename() != null) {
            return new FilenameCopyBookFinderImpl().findCopyBook(params, src.filename());
        }
        return null;
    }

    /** Exposes ProLeap's line reading and normalisation stages, without COPY expansion. */
    private static final class Normalizer extends CobolPreprocessorImpl {
        String normalize(String code, CobolParserParams params) {
            return createLineWriter().serialize(rewriteLines(readLines(code, params)));
        }
    }
}
