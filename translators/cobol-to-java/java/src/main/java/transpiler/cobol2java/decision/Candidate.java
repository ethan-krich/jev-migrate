package transpiler.cobol2java.decision;

import java.util.List;

/**
 * A validated way to resolve a decision. Candidates are only offered after the worker has
 * successfully generated their Java, so selecting one cannot produce an invalid AST.
 *
 * @param preview the generated Java statements, for display to a provider (null for a candidate
 *     with slots, which generates nothing until its slots are resolved)
 * @param slots named choices that applying the candidate raises as follow-up decisions
 * @param docs the Java methods the preview calls, with the first paragraph of their Javadoc
 *     (advisory, for a provider; not part of the fingerprint)
 * @param uses on a {@code runtime_choice} decision, what the preview is built on: {@code runtime}
 *     (the translator's runtime library, GnuCOBOL's exact semantics) or {@code jdk} (plain JDK
 *     calls, approximate); null otherwise
 */
public record Candidate(String id, String handler, String description, String preview, List<Slot> slots, List<Doc> docs,
        String uses) {
    /** A follow-up choice of a candidate: one decision with its own validated candidates. */
    public record Slot(String name, String description) {}

    /** A method's signature ({@code int Compare.compare(String a, String b)}) and Javadoc summary. */
    public record Doc(String signature, String summary) {}

    public Candidate {
        slots = List.copyOf(slots);
        docs = List.copyOf(docs);
    }

    public Candidate(String id, String handler, String description, String preview, List<Slot> slots, List<Doc> docs) {
        this(id, handler, description, preview, slots, docs, null);
    }

    public Candidate(String id, String handler, String description, String preview, List<Slot> slots) {
        this(id, handler, description, preview, slots, List.of());
    }

    public Candidate(String id, String handler, String description, String preview) {
        this(id, handler, description, preview, List.of());
    }

    public Candidate withDocs(List<Doc> docs) {
        return new Candidate(id, handler, description, preview, slots, docs, uses);
    }

    public Candidate withUses(String uses) {
        return new Candidate(id, handler, description, preview, slots, docs, uses);
    }
}
