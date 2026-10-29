package transpiler.cobol2java.decision;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.ProtocolException;

/**
 * The importance table of {@code initialize.importance} (declared in Python as
 * {@code cobol_to_java.DEFAULT_IMPORTANCE}): {@code (reason, operation) -> tier}, with {@code *}
 * matching anything. The most specific entry wins: (reason, operation), (*, operation),
 * (reason, *), (*, *). A decision no entry matches is {@code critical}, so a missing table can
 * only make a provider stricter.
 */
public final class Importance {
    public static final List<String> TIERS = List.of("low", "normal", "high", "critical");
    public static final String WILDCARD = "*";
    private static final String UNMATCHED = "critical";
    private static final Set<String> KEYS = Set.of("reason", "operation", "importance");

    private final Map<String, String> table;

    private Importance(Map<String, String> table) {
        this.table = table;
    }

    public static Importance empty() {
        return new Importance(Map.of());
    }

    /** Parses the table; a malformed entry, an unknown reason or tier, or a duplicate key is INVALID_PARAMS. */
    public static Importance parse(JsonNode node) throws ProtocolException {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return empty();
        }
        if (!node.isArray()) {
            throw invalid("'importance' must be an array of {reason, operation, importance}");
        }
        List<String> problems = new ArrayList<>();
        Map<String, String> table = new HashMap<>();
        int index = 0;
        for (JsonNode e : node) {
            String where = "importance[" + index++ + "]";
            if (!e.isObject()) {
                problems.add(where + " must be an object");
                continue;
            }
            for (Iterator<String> it = e.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                if (!KEYS.contains(key)) {
                    problems.add(where + ": unknown key '" + key + "'");
                }
            }
            String reason = e.path("reason").asText("");
            String operation = e.path("operation").asText("");
            String tier = e.path("importance").asText("");
            if (!e.path("reason").isTextual() || !(reason.equals(WILDCARD) || isReason(reason))) {
                problems.add(where + ": reason must be a decision reason or '*', not " + e.path("reason"));
            }
            if (!e.path("operation").isTextual() || operation.isEmpty()) {
                problems.add(where + ": operation must be a non-empty string");
            }
            if (!TIERS.contains(tier)) {
                problems.add(where + ": importance must be one of " + TIERS + ", not " + e.path("importance"));
            }
            if (table.put(key(reason, operation), tier) != null) {
                problems.add(where + ": duplicate entry for (" + reason + ", " + operation + ")");
            }
        }
        if (!problems.isEmpty()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "invalid importance table: " + String.join("; ", problems));
        }
        return new Importance(Map.copyOf(table));
    }

    /** The tier of a decision with this reason and operation. */
    public String of(Reason reason, String operation) {
        for (String key : List.of(key(reason.wire(), operation), key(WILDCARD, operation), key(reason.wire(), WILDCARD),
                key(WILDCARD, WILDCARD))) {
            String tier = table.get(key);
            if (tier != null) {
                return tier;
            }
        }
        return UNMATCHED;
    }

    private static boolean isReason(String text) {
        for (Reason r : Reason.values()) {
            if (r.wire().equals(text)) {
                return true;
            }
        }
        return false;
    }

    private static String key(String reason, String operation) {
        return reason + "\u0000" + operation;
    }

    private static ProtocolException invalid(String message) {
        return new ProtocolException(ErrorCode.INVALID_PARAMS, message);
    }
}
