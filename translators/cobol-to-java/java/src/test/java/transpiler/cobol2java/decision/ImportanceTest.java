package transpiler.cobol2java.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.ProtocolException;

class ImportanceTest {
    private static Importance parse(String json) throws Exception {
        return Importance.parse(Json.MAPPER.readTree(json));
    }

    @Test
    void mostSpecificEntryWins() throws Exception {
        Importance table = parse("""
                [{"reason": "*", "operation": "*", "importance": "normal"},
                 {"reason": "missing_target_api", "operation": "*", "importance": "high"},
                 {"reason": "*", "operation": "DISPLAY", "importance": "low"},
                 {"reason": "missing_mapping", "operation": "COMPUTE", "importance": "critical"}]
                """);
        assertEquals("critical", table.of(Reason.MISSING_MAPPING, "COMPUTE"));
        assertEquals("low", table.of(Reason.MISSING_TARGET_API, "DISPLAY"), "(*, operation) beats (reason, *)");
        assertEquals("high", table.of(Reason.MISSING_TARGET_API, "CALL"));
        assertEquals("normal", table.of(Reason.AMBIGUOUS_MAPPING, "MOVE"));
    }

    @Test
    void unmatchedDecisionsAreCritical() throws Exception {
        assertEquals("critical", Importance.empty().of(Reason.MISSING_MAPPING, "DISPLAY"));
        assertEquals("critical", parse("[{\"reason\": \"*\", \"operation\": \"MOVE\", \"importance\": \"low\"}]")
                .of(Reason.MISSING_MAPPING, "DISPLAY"));
        assertEquals("critical", Importance.parse(null).of(Reason.MISSING_MAPPING, "DISPLAY"));
    }

    @Test
    void rejectsInvalidTables() {
        for (String bad : new String[] {
                "{}",
                "[{\"reason\": \"*\", \"operation\": \"*\", \"importance\": \"urgent\"}]",
                "[{\"reason\": \"bogus\", \"operation\": \"*\", \"importance\": \"low\"}]",
                "[{\"reason\": \"*\", \"operation\": \"\", \"importance\": \"low\"}]",
                "[{\"reason\": \"*\", \"operation\": \"*\", \"importance\": \"low\", \"colour\": 1}]",
                "[{\"reason\": \"*\", \"operation\": \"*\", \"importance\": \"low\"}, {\"reason\": \"*\", \"operation\": \"*\", \"importance\": \"high\"}]"}) {
            var e = assertThrows(ProtocolException.class, () -> parse(bad), bad);
            assertEquals(ErrorCode.INVALID_PARAMS, e.code());
            assertTrue(e.getMessage().contains("importance"), e.getMessage());
        }
    }
}
