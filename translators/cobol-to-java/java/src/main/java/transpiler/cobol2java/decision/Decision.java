package transpiler.cobol2java.decision;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import transpiler.cobol2java.context.ContextGraph;
import transpiler.cobol2java.mapping.MappingRule;
import transpiler.cobol2java.protocol.Json;

/**
 * A structured decision request plus the worker-side state needed to apply a resolution.
 *
 * <p>The protocol view ({@link #toJson}) never exposes ProLeap or JavaParser objects; the
 * application plan stays private to the session.
 */
public final class Decision {
    public enum State { PENDING, RESOLVED }

    /** Applies a selected candidate to the session's partially built AST. */
    public interface Plan {
        /** Replaces the placeholder; returns decisions raised by nested statements of the candidate. */
        List<Decision> apply(String candidateId);
    }

    private final Reason reason;
    private final String operation;
    private final String message;
    private final SourceLocation location;
    private final String programId;
    private final Map<String, Object> facts;
    private final String ruleId;
    private final List<Candidate> candidates;
    private final MappingRule.CallbackRef callback;
    private final Plan plan;
    private String id;
    private String fingerprint;
    private String importance;
    private ContextGraph context;
    private ObjectNode renderedContext;
    /** The group of a slot decision (raised by a candidate with slots), with the slot it fills. */
    private SlotGroup slotGroup;
    private String slotName;
    /** For a setting decision: the Dialect field and the value of each candidate. */
    private String settingField;
    private Map<String, Object> settingValues;
    private State state = State.PENDING;
    private String selectedCandidate;

    public Decision(Reason reason, String operation, String message, SourceLocation location, String programId,
            Map<String, Object> facts, String ruleId, List<Candidate> candidates, MappingRule.CallbackRef callback,
            Plan plan) {
        this.reason = reason;
        this.operation = operation;
        this.message = message;
        this.location = location;
        this.programId = programId;
        this.facts = new TreeMap<>(facts);
        this.ruleId = ruleId;
        this.candidates = List.copyOf(candidates);
        this.callback = callback;
        this.plan = plan;
    }

    /**
     * A decision filling slot {@code name} of a candidate: its plan records the choice in
     * {@code group}, and the last slot resolved generates the statement.
     */
    public static Decision slot(Reason reason, String operation, String message, SourceLocation location, String programId,
            Map<String, Object> facts, String ruleId, List<Candidate> candidates, SlotGroup group, String name) {
        Decision d = new Decision(reason, operation, message, location, programId, facts, ruleId, candidates, null,
                candidateId -> group.fill(name, candidateId));
        d.slotGroup = group;
        d.slotName = name;
        return d;
    }

    /**
     * Makes this a setting decision: selecting candidate {@code id} means translating again with
     * {@code field} set to {@code values.get(id)} (the orchestrator does that; resolve refuses it).
     */
    public Decision withSetting(String field, Map<String, Object> values) {
        this.settingField = field;
        this.settingValues = new java.util.LinkedHashMap<>(values);
        return this;
    }

    /** The setting field of a setting decision, or null. */
    public String setting() {
        return settingField;
    }

    /** Adds or replaces a fact; only before {@link #register}, which fingerprints the facts. */
    public void putFact(String key, Object value) {
        if (fingerprint != null) {
            throw new IllegalStateException("facts of " + id + " are fingerprinted already");
        }
        facts.put(key, value);
    }

    /** Attaches the source graph that {@code context.refs} is rendered from. */
    public Decision withContext(ContextGraph graph) {
        this.context = graph;
        return this;
    }

    public ContextGraph context() {
        return context;
    }

    /**
     * Assigns the session-unique id, the importance tier and the content fingerprint used for
     * caching (which includes the tier), and renders the context within {@code budget}.
     */
    public void register(String assignedId, String targetLanguage, String dialectHash, Importance table,
            ContextGraph.Budget budget) {
        this.id = assignedId;
        this.importance = table.of(reason, operation);
        this.fingerprint = computeFingerprint(targetLanguage, dialectHash);
        this.renderedContext = context == null ? null : context.render(budget);
    }

    public String importance() {
        return importance;
    }

    /** The slot this decision fills, or null. */
    public String slotName() {
        return slotName;
    }

    public String id() {
        return id;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public Reason reason() {
        return reason;
    }

    public String message() {
        return message;
    }

    public String operation() {
        return operation;
    }

    public SourceLocation location() {
        return location;
    }

    public List<Candidate> candidates() {
        return candidates;
    }

    public State state() {
        return state;
    }

    public String selectedCandidate() {
        return selectedCandidate;
    }

    public boolean hasCandidate(String candidateId) {
        return candidates.stream().anyMatch(c -> c.id().equals(candidateId));
    }

    /** Applies the candidate; the caller has validated it. Returns newly raised nested decisions. */
    public List<Decision> resolve(String candidateId) {
        List<Decision> nested = plan.apply(candidateId);
        state = State.RESOLVED;
        selectedCandidate = candidateId;
        return nested;
    }

    /**
     * Whether resolving this decision completed its statement: false for a candidate with slots
     * and for a slot decision whose group still has open slots.
     */
    public boolean statementTranslated() {
        boolean slotted = candidates.stream().anyMatch(c -> c.id().equals(selectedCandidate) && !c.slots().isEmpty());
        return !slotted && (slotGroup == null || slotGroup.complete());
    }

    public ObjectNode toJson(String targetLanguage) {
        ObjectNode n = Json.object();
        n.put("id", id);
        n.put("fingerprint", fingerprint);
        n.put("reason", reason.wire());
        n.put("operation", operation);
        n.put("message", message);
        n.put("required", true);
        n.put("state", state.name().toLowerCase(java.util.Locale.ROOT));
        n.put("importance", importance);
        if (selectedCandidate != null) {
            n.put("selected_candidate", selectedCandidate);
        }
        ObjectNode src = n.putObject("source");
        src.put("file", location.file());
        src.put("line", location.line());
        src.put("column", location.column());
        src.put("text", location.text());
        ObjectNode ctx = n.putObject("context");
        ctx.put("program_id", programId);
        ctx.put("source_operation", operation);
        ctx.put("target_language", targetLanguage);
        if (ruleId != null) {
            ctx.put("rule_id", ruleId);
        }
        ctx.set("facts", Json.MAPPER.valueToTree(facts));
        if (renderedContext != null) {
            ctx.set("refs", renderedContext.get("refs"));
            ctx.set("budget", renderedContext.get("budget"));
        }
        var cands = n.putArray("candidates");
        for (Candidate c : candidates) {
            ObjectNode cn = cands.addObject();
            cn.put("id", c.id());
            cn.put("handler", c.handler());
            cn.put("description", c.description());
            if (c.preview() != null) {
                cn.put("preview", c.preview());
            }
            if (!c.slots().isEmpty()) {
                var slots = cn.putArray("slots");
                for (Candidate.Slot s : c.slots()) {
                    slots.addObject().put("name", s.name()).put("description", s.description());
                }
            }
        }
        if (settingField != null) {
            ObjectNode st = n.putObject("setting");
            st.put("field", settingField);
            st.set("values", Json.MAPPER.valueToTree(settingValues));
        }
        if (callback != null) {
            ObjectNode cb = n.putObject("callback");
            cb.put("script", callback.script());
            cb.put("function", callback.function());
        }
        return n;
    }

    private String computeFingerprint(String targetLanguage, String dialectHash) {
        ObjectNode basis = Json.object();
        basis.put("reason", reason.wire());
        basis.put("operation", operation);
        basis.put("file", location.file());
        basis.put("line", location.line());
        basis.put("column", location.column());
        basis.put("text", location.text());
        basis.put("program_id", programId);
        basis.put("target_language", targetLanguage);
        // The same source means something else under other project settings.
        basis.put("dialect", dialectHash);
        // The tier selects the provider's confidence floor, so an answer cached under one tier is not reused under another.
        basis.put("importance", importance);
        basis.set("facts", Json.MAPPER.valueToTree(facts));
        var ids = basis.putArray("candidates");
        candidates.forEach(c -> ids.add(c.id()));
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(Json.MAPPER.writeValueAsString(basis).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
