package transpiler.cobol2java.protocol;

import java.util.Locale;

/** Error codes carried in {@code error.code} of a response. */
public enum ErrorCode {
    MALFORMED_JSON,
    INVALID_REQUEST,
    UNSUPPORTED_PROTOCOL,
    UNKNOWN_METHOD,
    UNKNOWN_SESSION,
    SESSION_EXISTS,
    INVALID_STATE,
    INVALID_PARAMS,
    INVALID_MAPPING,
    /** initialize.libraries names COBOL parts or Java members that do not exist (details.problems lists them all). */
    INVALID_LIBRARY,
    /** A library's Java source cannot be read: not installed in the local caches, or without its sources jar. */
    LIBRARY_UNAVAILABLE,
    INTERNAL_ERROR;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
