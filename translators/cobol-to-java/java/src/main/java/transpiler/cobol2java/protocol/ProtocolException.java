package transpiler.cobol2java.protocol;

import com.fasterxml.jackson.databind.JsonNode;

/** A request-level failure reported to the host as an {@code error} response. */
public class ProtocolException extends Exception {
    private final ErrorCode code;
    private final JsonNode details;

    public ProtocolException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ProtocolException(ErrorCode code, String message, JsonNode details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public JsonNode details() {
        return details;
    }
}
