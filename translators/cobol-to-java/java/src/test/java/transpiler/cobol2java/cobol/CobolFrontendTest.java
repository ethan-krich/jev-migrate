package transpiler.cobol2java.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.proleap.cobol.asg.metamodel.procedure.Statement;
import io.proleap.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CobolFrontendTest {
    @TempDir
    Path dir;

    private static String fixed(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append(l.startsWith("D") ? "      " + l : "       " + l).append('\n');
        }
        return sb.toString();
    }

    private List<Statement> statements(String source, boolean debuggingLines) throws Exception {
        Path file = dir.resolve("M.cbl");
        Files.writeString(file, source, StandardCharsets.ISO_8859_1);
        var parsed = new CobolFrontend(new CobolFrontend.Options(CobolSourceFormatEnum.FIXED, StandardCharsets.ISO_8859_1, debuggingLines))
                .parse(file, List.of(dir), List.of(".cpy"));
        return parsed.program().getCompilationUnits().getFirst().getProgramUnit().getProcedureDivision().getStatements();
    }

    private static final String[] PROGRAM = {
        "IDENTIFICATION DIVISION.", "PROGRAM-ID. M.", "PROCEDURE DIVISION.",
        "    DISPLAY 'a'.", "D    DISPLAY 'debug'.", "    STOP RUN."};

    @Test
    void debuggingLinesAreCommentsUnlessEnabled() throws Exception {
        assertEquals(2, statements(fixed(PROGRAM), false).size());
        assertEquals(3, statements(fixed(PROGRAM), true).size());
    }

    @Test
    void debuggingModeInSourceComputerEnablesThem() throws Exception {
        String src = fixed("IDENTIFICATION DIVISION.", "PROGRAM-ID. M.", "ENVIRONMENT DIVISION.", "CONFIGURATION SECTION.",
                "SOURCE-COMPUTER. X WITH DEBUGGING MODE.", "PROCEDURE DIVISION.", "    DISPLAY 'a'.", "D    DISPLAY 'debug'.");
        assertEquals(2, statements(src, false).size());
    }

    @Test
    void debuggingLinesInCopybooksAreRefusedRatherThanCompiled() throws Exception {
        Files.writeString(dir.resolve("C.cpy"), fixed("D    DISPLAY 'debug'."));
        String src = fixed("IDENTIFICATION DIVISION.", "PROGRAM-ID. M.", "PROCEDURE DIVISION.", "    COPY C.", "    STOP RUN.");
        var failure = assertThrows(CobolFrontend.ParseFailure.class, () -> statements(src, false));
        assertEquals("unsupported_dialect", failure.kind());
        assertTrue(failure.getMessage().contains("C.cpy"), failure.getMessage());
        assertEquals(2, statements(src, true).size());
    }
}
