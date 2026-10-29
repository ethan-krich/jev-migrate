package transpiler.cobol2java.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.proleap.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transpiler.cobol2java.TestSupport;

class SourceMapTest {
    @TempDir
    Path dir;

    @Test
    void mapsLinesAfterCopyExpansionsBackToTheOriginalFile() throws Exception {
        Files.writeString(dir.resolve("A.cpy"), TestSupport.cobol("01 A1 PIC X.", "01 A2 PIC X.", "01 A3 PIC X."));
        Files.writeString(dir.resolve("B.cpy"), TestSupport.cobol("01 B1 PIC 9.", "COPY A."));
        String src = TestSupport.cobol(
                "IDENTIFICATION DIVISION.",   // 1
                "PROGRAM-ID. M.",             // 2
                "DATA DIVISION.",             // 3
                "WORKING-STORAGE SECTION.",   // 4
                "COPY A.",                    // 5
                "01 X PIC 9.",                // 6
                "COPY B.",                    // 7
                "PROCEDURE DIVISION.",        // 8
                "    DISPLAY X.");            // 9
        Path file = dir.resolve("M.cbl");
        Files.writeString(file, src, StandardCharsets.ISO_8859_1);
        var parsed = new CobolFrontend(new CobolFrontend.Options(CobolSourceFormatEnum.FIXED, StandardCharsets.ISO_8859_1, false))
                .parse(file, List.of(dir), List.of(".cpy"));
        SourceMap map = parsed.sourceMap();
        assertTrue(map.exact());
        List<String> pre = parsed.program().getCompilationUnits().getFirst().getLines();
        int displayLine = indexOf(pre, "DISPLAY X") + 1;
        int xLine = indexOf(pre, "01 X PIC 9") + 1;
        int b1Line = indexOf(pre, "01 B1") + 1;
        int a3Nested = lastIndexOf(pre, "01 A3") + 1;
        assertEquals(9, map.originalLine(displayLine));
        assertEquals(6, map.originalLine(xLine));
        assertEquals(7, map.originalLine(b1Line), "copybook lines map to their COPY statement");
        assertEquals(7, map.originalLine(a3Nested), "nested copybooks too");
        assertEquals(2, map.originalLine(2));
    }

    private static int indexOf(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        throw new AssertionError(needle + " not in " + lines);
    }

    private static int lastIndexOf(List<String> lines, String needle) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        throw new AssertionError(needle);
    }
}
