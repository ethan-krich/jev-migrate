package transpiler.cobol2java.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Protocol;
import transpiler.cobol2java.protocol.ProtocolException;
import transpiler.cobol2java.translate.Builtins;

/** Routes protocol methods to sessions. A worker may hold several independent sessions. */
public final class SessionManager {
    /** A method's response status and result payload. */
    public record Reply(String status, ObjectNode result) {}

    private final Builtins builtins = new Builtins();
    private final Map<String, Session> sessions = new HashMap<>();

    public Builtins builtins() {
        return builtins;
    }

    public Reply initialize(String sessionId, JsonNode params, Consumer<ObjectNode> progress) throws ProtocolException {
        if (sessions.containsKey(sessionId)) {
            throw new ProtocolException(ErrorCode.SESSION_EXISTS, "session '" + sessionId + "' already exists");
        }
        Session s = Session.initialize(sessionId, params, builtins, progress);
        sessions.put(sessionId, s);
        return new Reply(Protocol.STATUS_OK, s.initializeResult());
    }

    public Reply translate(String sessionId, JsonNode params, Consumer<ObjectNode> progress) throws ProtocolException {
        var o = session(sessionId).translate(params, progress);
        return new Reply(o.status(), o.result());
    }

    public Reply resolve(String sessionId, JsonNode params) throws ProtocolException {
        var o = session(sessionId).resolve(params);
        return new Reply(o.status(), o.result());
    }

    public Reply expandContext(String sessionId, JsonNode params) throws ProtocolException {
        var o = session(sessionId).expandContext(params);
        return new Reply(o.status(), o.result());
    }

    public Reply finish(String sessionId, JsonNode params) throws ProtocolException {
        var o = session(sessionId).finish(params);
        return new Reply(o.status(), o.result());
    }

    public void close(String sessionId) {
        sessions.remove(sessionId);
    }

    private Session session(String id) throws ProtocolException {
        Session s = sessions.get(id);
        if (s == null) {
            throw new ProtocolException(ErrorCode.UNKNOWN_SESSION, "no session '" + id + "'; call initialize first");
        }
        return s;
    }
}
