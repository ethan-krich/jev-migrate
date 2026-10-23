package transpiler.cobol2java.worker;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import transpiler.cobol2java.protocol.Json;

/** Writes one JSON object per line to the protocol stream. */
public final class MessageChannel {
    private final OutputStream out;

    public MessageChannel(OutputStream out) {
        this.out = out;
    }

    public synchronized void send(JsonNode message) {
        try {
            out.write(Json.MAPPER.writeValueAsString(message).getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
