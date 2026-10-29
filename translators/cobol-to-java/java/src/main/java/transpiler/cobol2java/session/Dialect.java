package transpiler.cobol2java.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.ProtocolException;

/**
 * Project settings the COBOL source does not determine (mirrors {@code cobol_to_java.Dialect}).
 *
 * <p>Optional settings are empty when the project left them unset; a program that needs one
 * gets a single {@code dialect:<field>} decision instead of a guessed value.
 */
public record Dialect(
        Optional<String> encoding,
        Optional<String> collatingSequence,
        Optional<String> outOfRange,
        boolean binaryTruncate,
        Optional<Boolean> syncAlignment,
        boolean debuggingLines,
        String sourceFormat,
        Map<String, String> fileAssignments,
        Optional<String> whenCompiled,
        Optional<String> dynamicCalls) {

    /** Option keys of {@code initialize} that are not dialect settings. */
    static final Set<String> OTHER_OPTIONS = Set.of("source_encoding");

    static final Set<String> KEYS = Set.of("encoding", "collating_sequence", "out_of_range", "binary_truncate",
            "sync_alignment", "debugging_lines", "source_format", "file_assignments", "when_compiled",
            "dynamic_calls");

    private static final Pattern WHEN_COMPILED = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");

    public static Dialect defaults() {
        return new Dialect(Optional.empty(), Optional.empty(), Optional.empty(), true, Optional.empty(), false, "fixed",
                Map.of(), Optional.empty(), Optional.empty());
    }

    /** Parses {@code initialize.params.options}; unknown keys and malformed values are INVALID_PARAMS. */
    public static Dialect parse(JsonNode options) throws ProtocolException {
        if (options == null || options.isNull() || options.isMissingNode()) {
            return defaults();
        }
        if (!options.isObject()) {
            throw invalid("'options' must be an object");
        }
        for (Iterator<String> it = options.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if (!KEYS.contains(key) && !OTHER_OPTIONS.contains(key)) {
                throw invalid("unknown option '" + key + "' (known: "
                        + KEYS.stream().sorted().toList() + " and " + OTHER_OPTIONS + ")");
            }
        }
        Map<String, String> files = new TreeMap<>();
        JsonNode fa = options.get("file_assignments");
        if (fa != null && !fa.isNull()) {
            if (!fa.isObject()) {
                throw invalid("options.file_assignments must be an object");
            }
            for (var e : fa.properties()) {
                if (!e.getValue().isTextual() || e.getKey().isEmpty() || e.getValue().asText().isEmpty()) {
                    throw invalid("options.file_assignments must map names to non-empty paths");
                }
                files.put(e.getKey(), e.getValue().asText());
            }
        }
        Optional<String> whenCompiled = text(options, "when_compiled", null);
        if (whenCompiled.isPresent() && !WHEN_COMPILED.matcher(whenCompiled.get()).matches()) {
            throw invalid("options.when_compiled must be 'YYYY-MM-DDTHH:MM:SS'");
        }
        return new Dialect(
                text(options, "encoding", Set.of("ascii", "ebcdic")),
                text(options, "collating_sequence", Set.of("native", "ascii", "ebcdic")),
                text(options, "out_of_range", Set.of("fail", "decision")),
                bool(options, "binary_truncate").orElse(true),
                bool(options, "sync_alignment"),
                bool(options, "debugging_lines").orElse(false),
                text(options, "source_format", Set.of("fixed", "variable", "tandem")).orElse("fixed"),
                Map.copyOf(files),
                whenCompiled,
                text(options, "dynamic_calls", Set.of("project")));
    }

    private static Optional<String> text(JsonNode options, String key, Set<String> allowed) throws ProtocolException {
        JsonNode v = options.get(key);
        if (v == null || v.isNull()) {
            return Optional.empty();
        }
        if (!v.isTextual() || (allowed != null && !allowed.contains(v.asText()))) {
            throw invalid("options." + key + " must be " + (allowed == null ? "a string" : "one of " + allowed.stream().sorted().toList())
                    + " or null, not " + v);
        }
        return Optional.of(v.asText());
    }

    private static Optional<Boolean> bool(JsonNode options, String key) throws ProtocolException {
        JsonNode v = options.get(key);
        if (v == null || v.isNull()) {
            return Optional.empty();
        }
        if (!v.isBoolean()) {
            throw invalid("options." + key + " must be a boolean, not " + v);
        }
        return Optional.of(v.asBoolean());
    }

    private static ProtocolException invalid(String message) {
        return new ProtocolException(ErrorCode.INVALID_PARAMS, "invalid options: " + message);
    }

    /** The settings as written to the run report; unset ones are null. */
    public ObjectNode toJson() {
        ObjectNode n = Json.object();
        n.put("encoding", encoding.orElse(null));
        n.put("collating_sequence", collatingSequence.orElse(null));
        n.put("out_of_range", outOfRange.orElse(null));
        n.put("binary_truncate", binaryTruncate);
        if (syncAlignment.isPresent()) {
            n.put("sync_alignment", syncAlignment.get());
        } else {
            n.putNull("sync_alignment");
        }
        n.put("debugging_lines", debuggingLines);
        n.put("source_format", sourceFormat);
        ObjectNode files = n.putObject("file_assignments");
        new TreeMap<>(fileAssignments).forEach(files::put);
        n.put("when_compiled", whenCompiled.orElse(null));
        n.put("dynamic_calls", dynamicCalls.orElse(null));
        return n;
    }

    /** A stable hash of every setting; part of each decision's fingerprint. */
    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Json.MAPPER.writeValueAsString(toJson()).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
