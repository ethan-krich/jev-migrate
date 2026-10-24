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

    public HandlerException(Kind kind, String message) {
        super(message, null, false, false);
        this.kind = kind;
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
