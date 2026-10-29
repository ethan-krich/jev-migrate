package transpiler.cobol2java.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.ProtocolException;

class DialectTest {
    private static Dialect parse(String json) throws Exception {
        return Dialect.parse(Json.MAPPER.readTree(json));
    }

    @Test
    void absentOptionsAreTheDefaults() throws Exception {
        assertEquals(Dialect.defaults(), Dialect.parse(null));
        assertEquals(Dialect.defaults(), parse("{}"));
        // What cobol_to_java.Dialect().to_options() sends.
        assertEquals(Dialect.defaults(), parse("""
                {"encoding": null, "collating_sequence": null, "out_of_range": null, "binary_truncate": true,
                 "sync_alignment": null, "debugging_lines": false, "source_format": "fixed", "file_assignments": {},
                 "when_compiled": null, "dynamic_calls": null, "source_encoding": "ISO-8859-1"}"""));
    }

    @Test
    void settingsAreParsed() throws Exception {
        Dialect d = parse("""
                {"encoding": "ebcdic", "out_of_range": "fail", "sync_alignment": true, "binary_truncate": false,
                 "file_assignments": {"IN": "/tmp/in"}, "when_compiled": "2024-01-02T03:04:05", "dynamic_calls": "project"}""");
        assertEquals(Optional.of("ebcdic"), d.encoding());
        assertEquals(Optional.of("project"), d.dynamicCalls());
        assertEquals(Optional.of("fail"), d.outOfRange());
        assertEquals(Optional.of(true), d.syncAlignment());
        assertFalse(d.binaryTruncate());
        assertEquals(Map.of("IN", "/tmp/in"), d.fileAssignments());
        assertTrue(d.collatingSequence().isEmpty());
        assertNotEquals(Dialect.defaults().hash(), d.hash());
        assertEquals(d.hash(), parse(Json.MAPPER.writeValueAsString(d.toJson())).hash());
    }

    @Test
    void unknownKeysAndBadValuesAreInvalidParams() {
        for (String bad : new String[] {
            "{\"colour\": \"blue\"}", "{\"encoding\": \"utf-8\"}", "{\"binary_truncate\": \"yes\"}",
            "{\"source_format\": \"free\"}", "{\"when_compiled\": \"today\"}", "{\"file_assignments\": {\"A\": 1}}", "{\"dynamic_calls\": \"all\"}", "[]"}) {
            var e = assertThrows(ProtocolException.class, () -> parse(bad), bad);
            assertEquals(ErrorCode.INVALID_PARAMS, e.code(), bad);
        }
    }
}
