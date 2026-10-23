package transpiler.cobol2java.decision;

/**
 * A validated way to resolve a decision. Candidates are only offered after the worker has
 * successfully generated their Java, so selecting one cannot produce an invalid AST.
 *
 * @param preview the generated Java statements, for display to a provider
 */
public record Candidate(String id, String handler, String description, String preview) {}
