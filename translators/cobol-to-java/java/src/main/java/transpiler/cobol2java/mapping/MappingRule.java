package transpiler.cobol2java.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * One rule reconstructed from the host-supplied registry.
 *
 * @param order position in the registry, the final tie-breaker for deterministic ordering
 */
public record MappingRule(
        String id,
        String source,
        Strategy strategy,
        String handler,
        Map<String, JsonNode> when,
        int priority,
        CallbackRef callback,
        String description,
        int order) {

    /** A reference to a user callback; the worker only carries it back to the host. */
    public record CallbackRef(String script, String function) {}

    boolean matches(Map<String, Object> facts) {
        for (Map.Entry<String, JsonNode> condition : when.entrySet()) {
            Object actual = facts.get(condition.getKey());
            if (!matchesValue(condition.getValue(), actual)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesValue(JsonNode expected, Object actual) {
        if (expected.isArray()) {
            for (JsonNode option : expected) {
                if (matchesValue(option, actual)) {
                    return true;
                }
            }
            return false;
        }
        if (actual == null) {
            return expected.isNull();
        }
        if (actual instanceof Boolean b) {
            return expected.isBoolean() && expected.booleanValue() == b;
        }
        if (actual instanceof Number n) {
            return expected.isNumber() && expected.decimalValue().compareTo(new java.math.BigDecimal(n.toString())) == 0;
        }
        return expected.isTextual() && expected.asText().equals(actual.toString());
    }
}
