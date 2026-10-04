package club.code2create.mcremote.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonRpcConnectionTest {

    @Test
    void discardsAnErrorResponseForAnUnknownRequestId() throws Exception {
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode request = connection.readRequest();
                        connection.error(999, -32000, "unrelated error", "permission_denied");
                        connection.result(request, "ok");
                    }
                });
                JsonRpcConnection connection = new JsonRpcConnection(
                        new ServerTarget("127.0.0.1", peer.port()),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1))) {
            JsonNode result = connection.request("test.probe", List.of());

            assertEquals("ok", result.textValue());
            peer.await();
        }
    }
}
