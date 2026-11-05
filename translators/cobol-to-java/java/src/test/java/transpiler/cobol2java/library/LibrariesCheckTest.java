package transpiler.cobol2java.library;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.ProtocolException;

/** Two libraries must not claim the same part of the source, however the selectors name it. */
class LibrariesCheckTest {
    @TempDir
    Path dir;

    private static final Map<String, String> PROGRAMS = Map.of("common/dates/DATEADD.cbl", "DATEADD", "MAIN.cbl", "MAIN");
    private static final Map<String, Set<String>> PARAGRAPHS = Map.of("DATEADD", Set.of("CALC", "SHOW"), "MAIN", Set.of("CALC"));

    private ObjectNode selector(String kind, String... names) {
        ObjectNode s = Json.object();
        s.put("kind", kind);
        ArrayNode ns = s.putArray("names");
        List.of(names).forEach(ns::add);
        return s;
    }

    private ObjectNode path(String pattern) {
        ObjectNode s = Json.object();
        s.put("kind", "path");
        s.put("pattern", pattern);
        return s;
    }

    private Libraries libraries(ObjectNode first, ObjectNode second) throws Exception {
        Path src = dir.resolve("lib");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Ops.java"), "public class Ops { public static int one() { return 1; } }");
        ObjectNode root = Json.object();
        root.put("version", 1);
        ArrayNode libs = root.putArray("libraries");
        int n = 0;
        for (ObjectNode s : List.of(first, second)) {
            ObjectNode lib = libs.addObject();
            lib.put("id", "lib" + ++n);
            lib.putArray("source").add(s);
            lib.putObject("target").put("kind", "path").put("path", src.toString());
        }
        return Libraries.parse(root, dir);
    }

    private String problem(ObjectNode first, ObjectNode second) throws Exception {
        Libraries libs = libraries(first, second);
        return assertThrows(ProtocolException.class, () -> libs.check(PROGRAMS, Set.of(), PARAGRAPHS)).getMessage();
    }

    @Test
    void aPathAndAProgramNameTheSameProgram() throws Exception {
        String message = problem(path("common/dates/"), selector("program", "DATEADD"));
        assertTrue(message.contains("program DATEADD") && message.contains("already claimed by library 'lib1'"), message);
    }

    @Test
    void aGlobalParagraphAndAProgramScopedOneOverlap() throws Exception {
        ObjectNode scoped = selector("paragraph", "CALC");
        scoped.put("program", "MAIN");
        String message = problem(selector("paragraph", "CALC"), scoped);
        assertTrue(message.contains("paragraph CALC of program MAIN") && message.contains("already claimed by library 'lib1'"), message);
    }

    @Test
    void disjointPartsAreAccepted() throws Exception {
        ObjectNode scoped = selector("paragraph", "SHOW");
        scoped.put("program", "DATEADD");
        assertDoesNotThrow(() -> libraries(selector("paragraph", "CALC"), scoped).check(PROGRAMS, Set.of(), PARAGRAPHS));
        assertDoesNotThrow(() -> libraries(path("common/dates/"), selector("program", "MAIN")).check(PROGRAMS, Set.of(), PARAGRAPHS));
    }
}
