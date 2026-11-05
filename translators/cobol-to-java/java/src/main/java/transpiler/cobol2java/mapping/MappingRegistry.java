package transpiler.cobol2java.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.ProtocolException;

/**
 * The mapping registry reconstructed from the {@code mappings} object of {@code initialize}.
 *
 * <p>Rules for an operation are tried in a defined order: higher {@code priority} first, then
 * more {@code when} conditions (more specific) first, then registry order. If the best two
 * matching rules tie on priority and specificity but differ in effect, the selection is
 * ambiguous and becomes a decision instead of an arbitrary pick.
 */
public final class MappingRegistry {
    public static final int FORMAT_VERSION = 1;

    /** Static knowledge the registry validates against: known operations, their facts and handlers. */
    public interface Catalog {
        Set<String> operations();

        Set<String> factKeys(String operation);

        /** The operation a builtin handler implements, or null if the handler does not exist. */
        String handlerOperation(String handler);
    }

    private static final Comparator<MappingRule> PRECEDENCE = Comparator
            .comparingInt(MappingRule::priority).reversed()
            .thenComparing(Comparator.comparingInt((MappingRule r) -> r.when().size()).reversed())
            .thenComparingInt(MappingRule::order);

    private final List<MappingRule> rules;

    private MappingRegistry(List<MappingRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<MappingRule> rules() {
        return rules;
    }

    /** The outcome of selecting a rule for one operation instance. */
    public record Selection(MappingRule winner, List<MappingRule> tied) {
        public boolean matched() {
            return winner != null;
        }

        public boolean ambiguous() {
            return tied.size() > 1;
        }
    }

    public Selection select(String operation, Map<String, Object> facts) {
        List<MappingRule> matching = rules.stream()
                .filter(r -> r.source().equals(operation) && r.matches(facts))
                .sorted(PRECEDENCE)
                .toList();
        if (matching.isEmpty()) {
            return new Selection(null, List.of());
        }
        MappingRule best = matching.getFirst();
        List<MappingRule> tied = matching.stream()
                .filter(r -> r.priority() == best.priority() && r.when().size() == best.when().size())
                .toList();
        boolean sameEffect = tied.stream().allMatch(r -> r.strategy() == best.strategy()
                && java.util.Objects.equals(r.handler(), best.handler())
                && java.util.Objects.equals(r.callback(), best.callback()));
        return new Selection(best, sameEffect ? List.of(best) : tied);
    }

    /** Parses and validates; every problem is reported at once in {@code error.details.problems}. */
    public static MappingRegistry parse(JsonNode node, Catalog catalog) throws ProtocolException {
        List<String> problems = new ArrayList<>();
        if (node == null || !node.isObject()) {
            throw invalid(List.of("'mappings' must be an object"));
        }
        if (!node.path("version").isInt() || node.path("version").asInt() != FORMAT_VERSION) {
            problems.add("'mappings.version' must be " + FORMAT_VERSION);
        }
        JsonNode rulesNode = node.get("rules");
        if (rulesNode == null || !rulesNode.isArray()) {
            problems.add("'mappings.rules' must be an array");
            throw invalid(problems);
        }
        List<MappingRule> rules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int order = 0;
        for (JsonNode r : rulesNode) {
            String where = "rule[" + order + "]";
            if (!r.isObject()) {
                problems.add(where + " must be an object");
                order++;
                continue;
            }
            String id = text(r, "id");
            if (id == null || id.isEmpty()) {
                problems.add(where + ": 'id' is required");
            } else {
                where = "rule '" + id + "'";
                if (!ids.add(id)) {
                    problems.add(where + ": duplicate id");
                }
            }
            for (var it = r.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                if (!Set.of("id", "source", "strategy", "handler", "when", "priority", "callback", "description").contains(field)) {
                    problems.add(where + ": unknown field '" + field + "'");
                }
            }
            String source = text(r, "source");
            if (source == null || source.isEmpty()) {
                problems.add(where + ": 'source' is required");
            } else {
                source = source.toUpperCase(Locale.ROOT);
                if (!catalog.operations().contains(source)) {
                    problems.add(where + ": unknown source operation '" + source + "'");
                }
            }
            Strategy strategy = Strategy.parse(text(r, "strategy")).orElse(null);
            if (strategy == null) {
                problems.add(where + ": 'strategy' must be one of builtin, unresolved, callback, library");
            }
            String handler = text(r, "handler");
            MappingRule.CallbackRef callback = null;
            if (strategy == Strategy.BUILTIN) {
                if (handler == null) {
                    problems.add(where + ": builtin strategy requires 'handler'");
                } else {
                    String handlerOp = catalog.handlerOperation(handler);
                    if (handlerOp == null) {
                        problems.add(where + ": unknown builtin handler '" + handler + "'");
                    } else if (source != null && !handlerOp.equals(source)) {
                        problems.add(where + ": handler '" + handler + "' implements " + handlerOp + ", not " + source);
                    }
                }
            } else if (handler != null) {
                problems.add(where + ": 'handler' is only valid with the builtin strategy");
            }
            JsonNode cb = r.get("callback");
            if (strategy == Strategy.CALLBACK) {
                if (cb == null || !cb.isObject() || text(cb, "script") == null || text(cb, "function") == null) {
                    problems.add(where + ": callback strategy requires 'callback': {script, function}");
                } else {
                    callback = new MappingRule.CallbackRef(text(cb, "script"), text(cb, "function"));
                }
            } else if (cb != null && !cb.isNull()) {
                problems.add(where + ": 'callback' is only valid with the callback strategy");
            }
            Map<String, JsonNode> when = new LinkedHashMap<>();
            JsonNode whenNode = r.get("when");
            if (whenNode != null && !whenNode.isNull()) {
                if (!whenNode.isObject()) {
                    problems.add(where + ": 'when' must be an object");
                } else {
                    Set<String> known = source == null ? Set.of() : catalog.factKeys(source);
                    for (var it = whenNode.properties().iterator(); it.hasNext(); ) {
                        var e = it.next();
                        if (!known.contains(e.getKey())) {
                            problems.add(where + ": unknown condition '" + e.getKey() + "' for " + source + " (known: " + new java.util.TreeSet<>(known) + ")");
                        }
                        if (!isConditionValue(e.getValue())) {
                            problems.add(where + ": condition '" + e.getKey() + "' must be a scalar or an array of scalars");
                        }
                        when.put(e.getKey(), e.getValue());
                    }
                }
            }
            JsonNode priorityNode = r.get("priority");
            int priority = 0;
            if (priorityNode != null && !priorityNode.isNull()) {
                if (!priorityNode.canConvertToInt() || !priorityNode.isIntegralNumber()) {
                    problems.add(where + ": 'priority' must be an integer");
                } else {
                    priority = priorityNode.asInt();
                }
            }
            rules.add(new MappingRule(id, source, strategy, handler, when, priority, callback, text(r, "description"), order));
            order++;
        }
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        return new MappingRegistry(rules);
    }

    private static boolean isConditionValue(JsonNode v) {
        if (v.isArray()) {
            for (JsonNode e : v) {
                if (!e.isValueNode()) {
                    return false;
                }
            }
            return !v.isEmpty();
        }
        return v.isValueNode();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private static ProtocolException invalid(List<String> problems) {
        ArrayNode list = Json.array();
        problems.forEach(list::add);
        var details = Json.object();
        details.set("problems", list);
        return new ProtocolException(ErrorCode.INVALID_MAPPING,
                "invalid mapping registry: " + problems.size() + " problem(s); first: " + problems.getFirst(), details);
    }
}
