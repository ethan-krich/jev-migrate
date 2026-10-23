package transpiler.cobol2java.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import transpiler.cobol2java.protocol.Json;

class WorkerTest {
    private static List<JsonNode> run(String input) throws Exception {
        var out = new ByteArrayOutputStream();
        new Worker(new MessageChannel(out)).run(new BufferedReader(new StringReader(input)));
        return out.toString(StandardCharsets.UTF_8).lines().map(l -> {
            try {
                return Json.MAPPER.readTree(l);
            } catch (Exception e) {
                throw new AssertionError("stdout line is not JSON: " + l, e);
            }
        }).toList();
    }

    @Test
    void announcesReadyAndAnswersEveryLine() throws Exception {
        var msgs = run("""
                garbage
                {"protocol":"transpiler-worker/1","id":"1","method":"translate","session":"none"}

                {"protocol":"transpiler-worker/1","id":"2","method":"shutdown"}
                {"protocol":"transpiler-worker/1","id":"3","method":"translate","session":"none"}
                """);
        assertEquals("ready", msgs.get(0).get("event").asText());
        assertEquals("malformed_json", msgs.get(1).get("error").get("code").asText());
        assertTrue(msgs.get(1).get("id").isNull());
        assertEquals("unknown_session", msgs.get(2).get("error").get("code").asText());
        assertEquals("ok", msgs.get(3).get("status").asText());
        assertEquals(4, msgs.size(), "nothing is processed after shutdown");
    }

    @Test
    void readyListsHandlersAndFacts() throws Exception {
        JsonNode ready = run("").getFirst().get("data");
        assertEquals("cobol-to-java", ready.get("translator").asText());
        assertTrue(ready.get("handlers").size() >= 15);
        assertTrue(ready.get("facts").has("MOVE"));
        assertFalse(ready.get("facts").has("GO_TO"));
    }
}
