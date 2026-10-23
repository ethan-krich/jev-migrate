package transpiler.cobol2java.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static transpiler.cobol2java.TestSupport.registry;
import static transpiler.cobol2java.TestSupport.rule;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import transpiler.cobol2java.TestSupport;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.ProtocolException;

class MappingRegistryTest {
    private static final Map<String, Object> NUMERIC = Map.of("target_category", "numeric");

    private static ArrayNode rules(com.fasterxml.jackson.databind.node.ObjectNode... rs) {
        ArrayNode a = Json.array();
        for (var r : rs) {
            a.add(r);
        }
        return a;
    }

    @Test
    void higherPriorityWins() throws Exception {
        var reg = registry(rules(rule("low", "MOVE", "move_to_alphanumeric", Map.of(), 1),
                rule("high", "MOVE", "move_to_numeric", Map.of(), 9)));
        assertEquals("high", reg.select("MOVE", NUMERIC).winner().id());
    }

    @Test
    void moreSpecificWinsAtEqualPriority() throws Exception {
        var reg = registry(rules(rule("generic", "MOVE", "move_to_alphanumeric", Map.of(), 5),
                rule("specific", "MOVE", "move_to_numeric", NUMERIC, 5)));
        var s = reg.select("MOVE", NUMERIC);
        assertEquals("specific", s.winner().id());
        assertFalse(s.ambiguous());
    }

    @Test
    void equalPrecedenceWithDifferentEffectIsAmbiguous() throws Exception {
        var reg = registry(rules(rule("a", "MOVE", "move_to_alphanumeric", NUMERIC, 5),
                rule("b", "MOVE", "move_to_numeric", NUMERIC, 5)));
        assertTrue(reg.select("MOVE", NUMERIC).ambiguous());
    }

    @Test
    void equalPrecedenceWithSameEffectUsesRegistryOrder() throws Exception {
        var reg = registry(rules(rule("first", "MOVE", "move_to_numeric", NUMERIC, 5),
                rule("second", "MOVE", "move_to_numeric", NUMERIC, 5)));
        var s = reg.select("MOVE", NUMERIC);
        assertEquals("first", s.winner().id());
        assertFalse(s.ambiguous());
    }

    @Test
    void conditionsMatchScalarsListsAndBooleans() throws Exception {
        var reg = registry(rules(
                rule("list", "MOVE", "move_to_numeric", Map.of("target_category", java.util.List.of("numeric", "x")), 1),
                rule("bool", "ADD", "add", Map.of("rounded", true), 1)));
        assertTrue(reg.select("MOVE", NUMERIC).matched());
        assertFalse(reg.select("MOVE", Map.of("target_category", "alphanumeric")).matched());
        assertTrue(reg.select("ADD", Map.of("rounded", true)).matched());
        assertFalse(reg.select("ADD", Map.of("rounded", false)).matched());
    }

    @Test
    void unresolvedStrategyIsSelectable() throws Exception {
        var reg = registry(rules(rule("u", "CALL", null, Map.of(), 1)));
        assertEquals(Strategy.UNRESOLVED, reg.select("CALL", Map.of()).winner().strategy());
    }

    @Test
    void rejectsUnknownHandlersOperationsAndConditions() {
        var bad = rules(rule("a", "MOVE", "move_string", Map.of(), 1), rule("b", "FROB", null, Map.of(), 1),
                rule("c", "DISPLAY", "add", Map.of(), 1), rule("d", "MOVE", "move_to_numeric", Map.of("colour", "red"), 1));
        var e = assertThrows(ProtocolException.class, () -> registry(bad));
        assertEquals(ErrorCode.INVALID_MAPPING, e.code());
        String problems = e.details().get("problems").toString();
        assertTrue(problems.contains("unknown builtin handler 'move_string'"), problems);
        assertTrue(problems.contains("unknown source operation 'FROB'"), problems);
        assertTrue(problems.contains("implements ADD, not DISPLAY"), problems);
        assertTrue(problems.contains("unknown condition 'colour'"), problems);
    }

    @Test
    void rejectsMalformedRegistry() {
        var cb = rule("cb", "CALL", null, Map.of(), 1).put("strategy", "callback");
        assertThrows(ProtocolException.class, () -> registry(rules(cb)));
        var noVersion = Json.object();
        noVersion.set("rules", Json.array());
        assertThrows(ProtocolException.class, () -> MappingRegistry.parse(noVersion, TestSupport.BUILTINS));
        var extra = rule("x", "MOVE", "move_to_numeric", Map.of(), 1).put("colour", "red");
        assertThrows(ProtocolException.class, () -> registry(rules(extra)));
    }
}
