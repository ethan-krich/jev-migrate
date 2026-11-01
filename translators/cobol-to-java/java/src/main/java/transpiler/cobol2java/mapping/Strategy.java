package transpiler.cobol2java.mapping;

import java.util.Locale;
import java.util.Optional;

/** How a mapping rule resolves a source operation. */
public enum Strategy {
    /** Apply a deterministic transformation implemented by this worker. */
    BUILTIN,
    /** Report the operation as an unresolved decision. */
    UNRESOLVED,
    /** Defer to a user Python function (routed by the host; never executed in this worker). */
    CALLBACK,
    /** The operation uses a part a library claims: a library_call decision offering library calls and the exact translation. */
    LIBRARY;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<Strategy> parse(String value) {
        for (Strategy s : values()) {
            if (s.wire().equals(value)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
