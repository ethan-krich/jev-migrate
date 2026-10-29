package transpiler.cobol2java.decision;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    /** Assigns the session-unique id and the content fingerprint used for caching. */
    public void register(String assignedId, String targetLanguage, String dialectHash) {
        this.id = assignedId;
        this.fingerprint = computeFingerprint(targetLanguage, dialectHash);
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

    public ObjectNode toJson(String targetLanguage) {
        ObjectNode n = Json.object();
        n.put("id", id);
        n.put("fingerprint", fingerprint);
        n.put("reason", reason.wire());
        n.put("operation", operation);
        n.put("message", message);
        n.put("required", true);
        n.put("state", state.name().toLowerCase(java.util.Locale.ROOT));
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
        var cands = n.putArray("candidates");
        for (Candidate c : candidates) {
            ObjectNode cn = cands.addObject();
            cn.put("id", c.id());
            cn.put("handler", c.handler());
            cn.put("description", c.description());
            cn.put("preview", c.preview());
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
