package transpiler.cobol2java.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.Protocol;
import transpiler.cobol2java.protocol.ProtocolException;
import transpiler.cobol2java.session.SessionManager;
import transpiler.cobol2java.translate.Facts;
import transpiler.cobol2java.translate.Handler;

/** The request loop: one JSON request per input line, exactly one response per request. */
public final class Worker {
    public static final String NAME = "cobol-to-java";
    public static final String VERSION = "0.1.0";
    static final List<String> METHODS = List.of("initialize", "translate", "resolve", "finalize", "shutdown");

    private final MessageChannel channel;
    private final SessionManager sessions = new SessionManager();

    public Worker(MessageChannel channel) {
        this.channel = channel;
    }

    /** Runs until shutdown or end of input; returns the process exit status. */
    public int run(BufferedReader in) throws IOException {
        channel.send(event("ready", null, null, readyData()));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            if (!handle(line)) {
                return 0;
            }
        }
        System.err.println("[cobol-to-java] stdin closed without shutdown; exiting");
        return 0;
    }

    private ObjectNode readyData() {
        ObjectNode d = Json.object();
        d.put("translator", NAME);
        d.put("version", VERSION);
        d.put("source_language", "cobol");
        d.put("target_language", "java");
        var methods = d.putArray("methods");
        METHODS.forEach(methods::add);
        var handlers = d.putArray("handlers");
        for (Handler h : sessions.builtins().all()) {
            ObjectNode hn = handlers.addObject();
            hn.put("id", h.id());
            hn.put("operation", h.operation());
            hn.put("description", h.description());
        }
        ObjectNode facts = d.putObject("facts");
        new java.util.TreeMap<>(Facts.KEYS).forEach((op, keys) -> {
            var list = facts.putArray(op);
            new java.util.TreeSet<>(keys).forEach(list::add);
        });
        return d;
    }

    /** Handles one line; returns false when the worker should exit. */
    boolean handle(String line) {
        JsonNode msg;
        try {
            msg = Json.MAPPER.readTree(line);
        } catch (JsonProcessingException e) {
            channel.send(error(null, null, ErrorCode.MALFORMED_JSON, "request is not valid JSON: " + e.getOriginalMessage(), null));
            return true;
        }
        String id = msg != null && msg.path("id").isTextual() ? msg.get("id").asText() : null;
        String session = msg != null && msg.path("session").isTextual() ? msg.get("session").asText() : null;
        try {
            if (msg == null || !msg.isObject()) {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "request must be a JSON object");
            }
            if (!Protocol.VERSION.equals(msg.path("protocol").asText())) {
                throw new ProtocolException(ErrorCode.UNSUPPORTED_PROTOCOL,
                        "expected protocol '" + Protocol.VERSION + "', got '" + msg.path("protocol").asText() + "'");
            }
            if (id == null || id.isEmpty()) {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "'id' must be a non-empty string");
            }
            String method = Json.requireText(msg, "method");
            JsonNode params = msg.has("params") ? msg.get("params") : Json.object();
            if (!params.isObject()) {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "'params' must be an object");
            }
            if (method.equals("shutdown")) {
                channel.send(response(id, session, Protocol.STATUS_OK, Json.object()));
                return false;
            }
            if (!METHODS.contains(method)) {
                throw new ProtocolException(ErrorCode.UNKNOWN_METHOD, "unknown method '" + method + "'");
            }
            if (session == null || session.isEmpty()) {
                throw new ProtocolException(ErrorCode.INVALID_REQUEST, "'session' is required for " + method);
            }
            String sid = session;
            SessionManager.Reply reply = switch (method) {
                case "initialize" -> sessions.initialize(sid, params, p -> channel.send(event("progress", sid, id, p)));
                case "translate" -> sessions.translate(sid, params, p -> channel.send(event("progress", sid, id, p)));
                case "resolve" -> sessions.resolve(sid, params);
                case "finalize" -> sessions.finish(sid, params);
                default -> throw new IllegalStateException(method);
            };
            channel.send(response(id, session, reply.status(), reply.result()));
        } catch (ProtocolException e) {
            channel.send(error(id, session, e.code(), e.getMessage(), e.details()));
        } catch (RuntimeException | StackOverflowError e) {
            e.printStackTrace(System.err);
            channel.send(error(id, session, ErrorCode.INTERNAL_ERROR, e.getClass().getSimpleName() + ": " + e.getMessage(), null));
        }
        return true;
    }

    private static ObjectNode envelope(String id, String session) {
        ObjectNode n = Json.object();
        n.put("protocol", Protocol.VERSION);
        if (id != null) {
            n.put("id", id);
        } else {
            n.putNull("id");
        }
        if (session != null) {
            n.put("session", session);
        }
        return n;
    }

    static ObjectNode response(String id, String session, String status, ObjectNode result) {
        ObjectNode n = envelope(id, session);
        n.put("type", "response");
        n.put("status", status);
        n.set("result", result);
        return n;
    }

    static ObjectNode error(String id, String session, ErrorCode code, String message, JsonNode details) {
        ObjectNode n = envelope(id, session);
        n.put("type", "response");
        n.put("status", Protocol.STATUS_ERROR);
        ObjectNode err = n.putObject("error");
        err.put("code", code.wire());
        err.put("message", message);
        if (details != null) {
            err.set("details", details);
        }
        return n;
    }

    static ObjectNode event(String name, String session, String inReplyTo, ObjectNode data) {
        ObjectNode n = Json.object();
        n.put("protocol", Protocol.VERSION);
        n.put("type", "event");
        n.put("event", name);
        if (session != null) {
            n.put("session", session);
        }
        if (inReplyTo != null) {
            n.put("in_reply_to", inReplyTo);
        }
        n.set("data", data);
        return n;
    }
}
