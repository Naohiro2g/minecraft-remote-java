package club.code2create.mcremote.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class SharedCompatibilityFixtureTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> stableChatAndEventCases() throws IOException {
        JsonNode fixture;
        try (var input = getClass().getResourceAsStream(
                "/protocol-fixtures/chat-event-compat-v23.2.1.json")) {
            if (input == null) {
                throw new IOException("missing shared compatibility fixture");
            }
            fixture = MAPPER.readTree(input);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode testCase : fixture.path("chat_post").path("cases")) {
            tests.add(DynamicTest.dynamicTest(testCase.path("id").asText(),
                    () -> checkChat(fixture, testCase)));
        }
        for (String group : List.of("cases", "stateful_rejections")) {
            for (JsonNode testCase : fixture.path("event_batches").path(group)) {
                tests.add(DynamicTest.dynamicTest(testCase.path("id").asText(),
                        () -> checkEvents(fixture, testCase)));
            }
        }
        return tests.stream();
    }

    private static void checkChat(JsonNode fixture, JsonNode testCase) throws Exception {
        JsonNode chat = fixture.path("chat_post");
        try (ScriptedPeer peer = peer(fixture, chat.path("method").asText(),
                chat.path("params"), testCase.path("result"))) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port()).connect()) {
                if (testCase.path("accept").asBoolean()) {
                    client.postToChat(chat.path("params").get(0).asText());
                } else {
                    assertThrows(ProtocolException.class,
                            () -> client.postToChat(chat.path("params").get(0).asText()));
                }
            }
            peer.await();
        }
    }

    private static void checkEvents(JsonNode fixture, JsonNode testCase) throws Exception {
        JsonNode prior = testCase.path("previous_status");
        EventPollResult previous = new EventPollResult(List.of(),
                prior.path("cursor").asLong(), prior.path("latestSequence").asLong(), 0,
                prior.path("overflowDroppedTotal").asLong(),
                prior.path("capacityDroppedTotal").asLong(),
                prior.path("explicitlyDiscardedTotal").asLong());
        boolean bounded = Integer.parseInt(testCase.path("id").asText().substring(4)) % 2 == 0;
        var params = MAPPER.createArrayNode().add(testCase.path("after_sequence"));
        if (bounded) {
            params.add(MAPPER.createObjectNode().put("max_events", 16));
        }
        try (ScriptedPeer peer = peer(fixture, fixture.path("event_batches").path("method").asText(),
                params, testCase.path("result"))) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port()).connect()) {
                if (!testCase.path("accept").asBoolean()) {
                    assertThrows(ProtocolException.class, () -> {
                        if (bounded) client.pollEvents(previous, 16);
                        else client.pollEvents(previous);
                    });
                } else {
                    EventPollResult current = bounded ? client.pollEvents(previous, 16) : client.pollEvents(previous);
                    JsonNode expected = testCase.path("expected_client");
                    JsonNode status = expected.path("status");
                    assertEquals(expected.path("cursor").asLong(), current.throughSequence());
                    assertEquals(status.path("latestSequence").asLong(), current.latestSequence());
                    assertEquals(status.path("overflowDroppedTotal").asLong(), current.overflowDroppedTotal());
                    assertEquals(status.path("capacityDroppedTotal").asLong(), current.capacityDroppedTotal());
                    assertEquals(status.path("explicitlyDiscardedTotal").asLong(), current.explicitlyDiscardedTotal());
                    assertEquals(testCase.path("result").path("filtered_out").asLong(), current.filteredOut());
                    long lossDelta = current.overflowDroppedTotal() - previous.overflowDroppedTotal()
                            + current.capacityDroppedTotal() - previous.capacityDroppedTotal()
                            + current.explicitlyDiscardedTotal() - previous.explicitlyDiscardedTotal();
                    assertEquals(expected.path("lossDelta").asLong(), lossDelta);
                    assertEquals(expected.path("events").size(), current.events().size());
                    for (int i = 0; i < current.events().size(); i++) {
                        assertEvent(expected.path("events").get(i), current.events().get(i));
                    }
                }
            }
            peer.await();
        }
    }

    private static void assertEvent(JsonNode expected, MinecraftEvent event) {
        assertEquals(expected.path("sequence").asLong(), event.sequence());
        assertEquals(expected.path("dimension").asText(), event.dimension());
        JsonNode origin = expected.path("origin");
        assertEquals(new BlockPosition(origin.get(0).asInt(), origin.get(1).asInt(), origin.get(2).asInt()),
                event.origin());
        switch (expected.path("type").asText()) {
            case "chat_posted" -> assertEquals(expected.path("message").asText(),
                    assertInstanceOf(MinecraftEvent.ChatPosted.class, event).message());
            case "pickaxe_poke" -> {
                var poke = assertInstanceOf(MinecraftEvent.PickaxePoke.class, event);
                JsonNode pos = expected.path("pos");
                assertEquals(new BlockPosition(pos.get(0).asInt(), pos.get(1).asInt(), pos.get(2).asInt()),
                        poke.position());
                assertEquals(expected.path("face").asText(), poke.face());
                assertEquals(expected.path("block").path("block_id").asText(), poke.block().blockId());
                assertEquals(MAPPER.convertValue(expected.path("block").path("state"), Map.class),
                        poke.block().state());
                assertEquals(expected.path("hand").asText(), poke.hand().wireName());
                assertEquals(expected.path("item").asText(), poke.item());
            }
            default -> throw new AssertionError("unhandled expected fixture event");
        }
    }

    private static ScriptedPeer peer(JsonNode fixture, String method, JsonNode params, JsonNode result)
            throws IOException {
        return new ScriptedPeer(server -> {
            try (var connection = server.accept()) {
                ObjectNode hello = connection.readRequest();
                assertEquals("hello", hello.path("method").asText());
                assertEquals(MinecraftRemote.PROTOCOL, hello.path("params").path("protocol").asText());
                connection.result(hello, Map.of(
                        "protocol", fixture.path("protocol").asText(),
                        "mc_version", "1.21.11",
                        "supported_mc_versions", List.of("1.21.11"),
                        "catalogHash", "sha256:shared-fixture",
                        "world_constants", Map.of("min_y", -64, "max_y", 319),
                        "dimension", "minecraft:overworld", "origin", List.of(200, 0, 200)));
                ObjectNode request = connection.readRequest();
                assertEquals("2.0", request.path("jsonrpc").asText());
                assertEquals(2, request.path("id").asInt());
                assertEquals(method, request.path("method").asText());
                assertEquals(params, request.path("params"));
                connection.result(request, result);
            }
        });
    }
}
