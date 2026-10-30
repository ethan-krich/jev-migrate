package transpiler.cobol2java.translate;

/** Raised by a handler that cannot translate an operation instance with full semantics. */
public final class HandlerException extends Exception {
    public enum Kind {
        /** No builtin translation preserves the semantics of this construct. */
        UNSUPPORTED,
        /** This handler does not cover this shape of the operation; another handler might. */
        NOT_APPLICABLE,
        /** A required target-side program or API is not available. */
        MISSING_TARGET,
        /** An unset dialect setting (named by the message) decides the semantics. */
        DIALECT
    }

    private final Kind kind;
    private final boolean offerCandidates;
    private final String missingProgram;

    public HandlerException(Kind kind, String message) {
        this(kind, message, false, null);
    }

    private HandlerException(Kind kind, String message, boolean offerCandidates, String missingProgram) {
        super(message, null, false, false);
        this.kind = kind;
        this.offerCandidates = offerCandidates;
        this.missingProgram = missingProgram;
    }

    /** MISSING_TARGET: a CALL names a program (as written) that is not part of the project. */
    public static HandlerException missingProgram(String program, String message) {
        return new HandlerException(Kind.MISSING_TARGET, message, false, program);
    }

    /**
     * MISSING_TARGET: the target system decides what replaces a device or console; with
     * {@code offerCandidates} the operation's other builtin handlers are offered, each exact for
     * one answer.
     */
    public static HandlerException missingTarget(String message, boolean offerCandidates) {
        return new HandlerException(Kind.MISSING_TARGET, message, offerCandidates, null);
    }

    /** The program a CALL names that is not in the project, or null. */
    public String missingProgram() {
        return missingProgram;
    }

    /**
     * UNSUPPORTED, but the operation's other builtin handlers are offered as candidates for
     * review (they are correct only under conditions the translator cannot prove).
     */
    public static HandlerException unprovable(String message) {
        return new HandlerException(Kind.UNSUPPORTED, message, true, null);
    }

    public boolean offerCandidates() {
        return offerCandidates;
    }

    public static HandlerException unsupported(String message) {
        return new HandlerException(Kind.UNSUPPORTED, message);
    }

    public static HandlerException notApplicable(String message) {
        return new HandlerException(Kind.NOT_APPLICABLE, message);
    }

    public Kind kind() {
        return kind;
    }
}
