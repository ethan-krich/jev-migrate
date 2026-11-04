package transpiler.cobol2java.decision;

import java.util.Locale;

/** Why an operation could not be translated deterministically. */
public enum Reason {
    /** No rule matched, or the matching rule uses the {@code unresolved} strategy. */
    MISSING_MAPPING,
    /** The construct is outside what any builtin handler can translate with full semantics. */
    UNSUPPORTED_CONSTRUCT,
    /** Several rules tie at the highest precedence with different effects. */
    AMBIGUOUS_MAPPING,
    /** The operation depends on a target-side API or program that is not available. */
    MISSING_TARGET_API,
    /** The selected user rule's handler does not apply to this instance of the operation. */
    INVALID_MAPPING,
    /** The selected rule delegates to a user callback. */
    CALLBACK_REQUIRED,
    /**
     * The operation uses a part of the COBOL program that a library claims: the candidates call the
     * library (not equivalence-checked) or translate the COBOL exactly ({@code cobol:translate}).
     */
    LIBRARY_CALL,
    /**
     * A builtin handler has a JDK twin and the run asks for the choice
     * ({@code options.jdk_alternatives}): the runtime candidate reproduces GnuCOBOL exactly, the
     * {@code jdk} one uses plain JDK calls and is approximate.
     */
    RUNTIME_CHOICE;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
