package transpiler.cobol2java.protocol;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared Jackson configuration and small accessors that raise protocol errors. */
public final class Json {
    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private Json() {}

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    public static String requireText(JsonNode node, String field) throws ProtocolException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isEmpty()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'" + field + "' must be a non-empty string");
        }
        return value.asText();
    }

    public static String optionalText(JsonNode node, String field, String fallback) throws ProtocolException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isTextual()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'" + field + "' must be a string");
        }
        return value.asText();
    }

    public static JsonNode requireObject(JsonNode node, String field) throws ProtocolException {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'" + field + "' must be an object");
        }
        return value;
    }

    public static ArrayNode requireArray(JsonNode node, String field) throws ProtocolException {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'" + field + "' must be an array");
        }
        return (ArrayNode) value;
    }
}
