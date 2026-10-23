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
    INTERNAL_ERROR;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
