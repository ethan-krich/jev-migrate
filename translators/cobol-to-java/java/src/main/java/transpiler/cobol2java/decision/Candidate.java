package transpiler.cobol2java.decision;

import java.util.List;

/**
 * A validated way to resolve a decision. Candidates are only offered after the worker has
 * successfully generated their Java, so selecting one cannot produce an invalid AST.
 *
 * @param preview the generated Java statements, for display to a provider (null for a candidate
 *     with slots, which generates nothing until its slots are resolved)
 * @param slots named choices that applying the candidate raises as follow-up decisions
 */
public record Candidate(String id, String handler, String description, String preview, List<Slot> slots) {
    /** A follow-up choice of a candidate: one decision with its own validated candidates. */
    public record Slot(String name, String description) {}

    public Candidate {
        slots = List.copyOf(slots);
    }

    public Candidate(String id, String handler, String description, String preview) {
        this(id, handler, description, preview, List.of());
    }
}
