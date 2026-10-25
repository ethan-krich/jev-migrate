package transpiler.cobol2java.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transpiler.cobol2java.TestSupport;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.ProtocolException;

class SessionLifecycleTest {
    @TempDir
    Path dir;

    private ObjectNode init(Path... programs) {
        ObjectNode p = Json.object();
        p.put("source_root", dir.resolve("src").toString());
        p.put("output_root", dir.resolve("out").toString());
        p.put("target_language", "java");
        var files = p.putArray("files");
        for (Path f : programs) {
            files.addObject().put("path", f.toString()).put("role", "program");
        }
        p.putObject("layout").put("base_package", "app").putArray("copybook_dirs").add("copy");
        p.set("mappings", TestSupport.mappings(TestSupport.defaultRules()));
        return p;
    }

    private Path write(String rel, String source) throws Exception {
        Path f = dir.resolve("src").resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, source, StandardCharsets.ISO_8859_1);
        return f;
    }

    @Test
    void initializeTranslateFinalize() throws Exception {
        Path f = write("billing/month-end/PAY.cbl", TestSupport.program("COPY REC.", "DISPLAY R-NAME.", "STOP RUN."));
        write("billing/copy/REC.cpy", TestSupport.cobol("01 R-NAME PIC X(4) VALUE 'ABCD'."));
        var m = new SessionManager();
        var init = m.initialize("s", init(f), e -> {});
        var program = init.result().get("programs").get(0);
        assertEquals("parsed", program.get("status").asText(), program::toString);
        assertEquals("app.billing.month_end", program.get("package").asText());
        assertEquals("T", program.get("program_id").asText());
        assertEquals("src/main/java/app/billing/month_end/T.java", program.get("output_path").asText());

        assertThrows(ProtocolException.class, () -> m.finish("nope", Json.object()));
        var translated = m.translate("s", Json.object(), e -> {});
        assertEquals("ok", translated.status());
        var fin = m.finish("s", Json.object());
        assertEquals("ok", fin.status());
        assertTrue(Files.readString(dir.resolve("out/src/main/java/app/billing/month_end/T.java")).contains("rName"));
        assertTrue(Files.readString(dir.resolve("out/src/main/java/app/runtime/NumericField.java"))
                .startsWith("package app.runtime;"));
        var e = assertThrows(ProtocolException.class, () -> m.translate("s", Json.object(), x -> {}));
        assertEquals(ErrorCode.INVALID_STATE, e.code());
    }

    @Test
    void finalizeRefusesUntranslatedOrFailedFiles() throws Exception {
        Path good = write("A.cbl", TestSupport.program("", "STOP RUN."));
        Path bad = write("B.cbl", TestSupport.cobol("IDENTIFICATION DIVISION.", "PROGRAM-ID. B.", "PROCEDURE DIVISION.",
                "    MOVE TO."));
        var m = new SessionManager();
        var init = m.initialize("s", init(good, bad), e -> {});
        assertEquals("failed", init.result().get("programs").get(1).get("status").asText());
        assertEquals("unsupported_dialect", init.result().get("programs").get(1).get("diagnostics").get(0).get("code").asText());
        var fin = m.finish("s", Json.object());
        assertEquals("incomplete", fin.status());
        assertEquals(2, fin.result().get("blocking").size());
        assertTrue(!Files.exists(dir.resolve("out")));
    }

    @Test
    void runtimeSourcesMatchTheRuntimeModule() throws Exception {
        Path runtime = Path.of("runtime/src/main/java/transpiler/cobol/runtime");
        try (var files = Files.list(runtime)) {
            var names = files.map(p -> p.getFileName().toString().replace(".java", "")).sorted().toList();
            assertEquals(names, RuntimeSources.CLASSES.stream().sorted().toList());
        }
        RuntimeSources.forPackage("x.y").values().forEach(src -> assertTrue(src.startsWith("package x.y;")));
    }
}
