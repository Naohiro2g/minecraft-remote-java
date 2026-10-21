package club.code2create.mcremote.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.CredentialStore;
import club.code2create.mcremote.client.ServerTarget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Runs the real example bodies over TCP against a deterministic protocol 23.2.1 peer. */
@Timeout(60)
class ExampleProtocolCompatibilityTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void helloExamplesUseAcknowledgedChat() throws Exception {
        try (Peer peer = new Peer(); var minecraft = peer.connect()) {
            MyWorld.run(minecraft);
            Demo00Hello.run(minecraft);
            assertEquals(2, peer.chat.size());
            assertTrue(peer.chat.getFirst().contains("MyWorld"));
        }
    }

    @Test
    void blockExampleWritesReadsAndCleansUpAtAbsoluteY96() throws Exception {
        try (Peer peer = new Peer(); var minecraft = peer.connect()) {
            Demo01SetAndReadBlock.run(minecraft);
            assertEquals("minecraft:gold_block", peer.lastBlock.path("block_id").asText());
            assertEquals(96, peer.lastY + peer.origin.get(1).intValue());
            Demo01SetAndReadBlock.cleanup(minecraft);
            assertEquals("minecraft:air", peer.lastBlock.path("block_id").asText());
        }
    }

    @Test
    void tourUsesCompatibleSignParticleAndEventShapes() throws Exception {
        try (Peer peer = new Peer(); var minecraft = peer.connect()) {
            Demo02Protocol23Tour.run(minecraft);
            assertEquals(30, peer.particles);
            assertEquals(List.of("front", 2), peer.updatedLine);
            assertEquals(3, peer.lastPollLimit);
            assertTrue(peer.methods.containsAll(List.of("catalog.get", "player.getPose",
                    "world.setSign", "world.updateSignLine", "world.getSign", "events.poll")));
            Demo02Protocol23Tour.cleanup(minecraft);
            assertEquals("minecraft:air", peer.lastBlock.path("block_id").asText());
        }
    }

    @Test
    void inspectIsReadOnlyAndDiscoversResources() throws Exception {
        try (Peer peer = new Peer(); var minecraft = peer.connect()) {
            Demo03InspectWorld.run(minecraft);
            assertEquals(List.of("hello", "player.getPose", "catalog.get"), peer.methods);
        }
    }

    @Test
    void eventsAreFiniteAndAdvanceTheServerCursor() throws Exception {
        try (Peer peer = new Peer(); var minecraft = peer.connect()) {
            Demo04Events.run(minecraft);
            assertEquals(20, peer.cursors.size());
            assertEquals(0L, peer.cursors.getFirst());
            assertTrue(peer.cursors.subList(1, 20).stream().allMatch(cursor -> cursor == 3));
        }
    }

    @Test
    void axisFlatKeepsRequestsWithinCurrentBlockLimit() throws Exception {
        try (Peer peer = new Peer(); var minecraft = peer.connect()) {
            AxisFlat.prepare(minecraft);
            assertEquals(65L * 97 * 97 + 97L * 97, peer.filledBlocks);
            assertEquals(62, peer.minimumAbsoluteY);
            assertEquals(127, peer.maximumAbsoluteY);
            assertTrue(peer.maxRequestBlocks <= 4096);
            AxisFlat.clearAxes(minecraft);
        }
    }

    @Test
    void menuRetainsTheConnectionAfterARejectedBlockEdit() throws Exception {
        try (Peer peer = new Peer(); var minecraft = peer.connect()) {
            peer.rejectBlock = true;
            Main.runMenu(minecraft, new Scanner("01\nhello\nx\n"));
            assertEquals(1, peer.chat.size());
            assertTrue(peer.chat.getFirst().contains("Minecraft Remote for Java"));
        }
    }

    @Test
    void menuReconnectsWithTheSameTokenWithoutPairingOrReplayingAnOperation() throws Exception {
        try (Peer peer = new Peer(2)) {
            var target = new ServerTarget("127.0.0.1", peer.server.getLocalPort());
            var credentials = CredentialStore.inMemory();
            credentials.save(target, "fixture-session-token");
            Main.runConnected(target, credentials, "menu", new Scanner("r\nhello\nx\n"));
            assertEquals(List.of("hello", "hello", "chat.post"), peer.methods);
            assertEquals(List.of("fixture-session-token", "fixture-session-token"), peer.helloTokens);
        }
    }

    private static final class Peer implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<?> task;
        final List<String> methods = new ArrayList<>();
        final List<String> chat = new ArrayList<>();
        final List<Long> cursors = new ArrayList<>();
        final List<String> helloTokens = new ArrayList<>();
        JsonNode origin = JSON.valueToTree(List.of(200, 0, 200));
        JsonNode lastBlock;
        int lastY;
        int particles;
        int lastPollLimit;
        List<Object> updatedLine;
        long filledBlocks;
        long maxRequestBlocks;
        int minimumAbsoluteY = Integer.MAX_VALUE;
        int maximumAbsoluteY = Integer.MIN_VALUE;
        boolean rejectBlock;

        Peer() throws Exception {
            this(1);
        }

        Peer(int connections) throws Exception {
            task = executor.submit(() -> {
                for (int session = 0; session < connections; session++) {
                    try (var socket = server.accept();
                            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                            var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
                        socket.setSoTimeout(30_000);
                        String line;
                        while ((line = reader.readLine()) != null) {
                            JsonNode request = JSON.readTree(line);
                            assertEquals("2.0", request.path("jsonrpc").asText());
                            assertTrue(request.path("id").isIntegralNumber());
                            String method = request.path("method").asText();
                            methods.add(method);
                            ObjectNode response = JSON.createObjectNode();
                            response.put("jsonrpc", "2.0");
                            response.set("id", request.get("id"));
                            if (rejectBlock && method.equals("world.setBlock")) {
                                response.set("error", JSON.valueToTree(Map.of("code", -32000,
                                        "message", "build_denied", "data", Map.of("reason", "build_denied"))));
                            } else {
                                response.set("result", JSON.valueToTree(result(method, request.get("params"))));
                            }
                            writer.write(JSON.writeValueAsString(response));
                            writer.newLine();
                            writer.flush();
                        }
                    }
                }
                return null;
            });
        }

        MinecraftRemote connect() throws Exception {
            return MinecraftRemote.builder("127.0.0.1", server.getLocalPort())
                    .readTimeout(Duration.ofSeconds(5)).connect();
        }

        private Object result(String method, JsonNode params) throws Exception {
            return switch (method) {
                case "hello" -> {
                    assertEquals("23.0.0", params.path("protocol").asText());
                    if (params.has("auth")) helloTokens.add(params.path("auth").path("token").asText());
                    yield Map.of("protocol", "23.2.1", "mc_version", "1.21.11",
                            "supported_mc_versions", List.of("1.21.11", "26.3"),
                            "world_constants", Map.of("y_sea", 62), "dimension", "minecraft:overworld",
                            "origin", origin, "permissions", Map.of("online", true, "offline", true, "buildRange", 500));
                }
                case "build.setOrigin" -> {
                    assertEquals(3, params.size());
                    origin = params.deepCopy();
                    yield Map.of("dimension", "minecraft:overworld", "origin", origin);
                }
                case "chat.post" -> { assertEquals(1, params.size()); chat.add(params.get(0).asText()); yield null; }
                case "world.setBlock" -> {
                    assertEquals(4, params.size());
                    lastBlock = params.get(3).deepCopy();
                    assertTrue(lastBlock.path("state").isObject());
                    lastY = params.get(1).intValue();
                    yield null;
                }
                case "world.getBlock" -> { assertEquals(3, params.size()); yield lastBlock; }
                case "world.setBlocks" -> {
                    assertEquals(7, params.size());
                    long volume = 1;
                    for (int axis = 0; axis < 3; axis++) {
                        volume *= Math.abs((long) params.get(axis).intValue() - params.get(axis + 3).intValue()) + 1;
                    }
                    assertTrue(volume <= 32768, "current per-request policy");
                    maxRequestBlocks = Math.max(maxRequestBlocks, volume);
                    filledBlocks += volume;
                    int firstY = params.get(1).intValue() + origin.get(1).intValue();
                    int secondY = params.get(4).intValue() + origin.get(1).intValue();
                    minimumAbsoluteY = Math.min(minimumAbsoluteY, Math.min(firstY, secondY));
                    maximumAbsoluteY = Math.max(maximumAbsoluteY, Math.max(firstY, secondY));
                    yield null;
                }
                case "catalog.get" -> Map.of("catalogHash", "fixture-catalog", "block",
                        Map.of("minecraft:sea_lantern", Map.of("states", Map.of(), "default_state", Map.of())),
                        "entity", Map.of("minecraft:allay", Map.of()), "particle", Map.of("minecraft:flame", Map.of()));
                case "player.getPose" -> Map.of("dimension", "minecraft:overworld", "pos", List.of(2, 96, 2), "yaw", 0, "pitch", 0);
                case "world.setSign" -> { assertEquals(4, params.size()); assertEquals(4, params.get(3).path("front").size()); yield null; }
                case "world.updateSignLine" -> {
                    assertEquals(6, params.size());
                    updatedLine = List.of(params.get(3).asText(), params.get(4).intValue());
                    yield null;
                }
                case "world.getSign" -> fixture("sign-v23.json").path("get_sign").path("B6-S03").path("result");
                case "world.spawnParticle" -> {
                    assertEquals(9, params.size());
                    assertEquals("minecraft:flame", params.get(6).asText());
                    particles = params.get(8).intValue();
                    yield particles;
                }
                case "events.poll" -> {
                    assertEquals(2, params.size());
                    long cursor = params.get(0).longValue();
                    cursors.add(cursor);
                    lastPollLimit = params.get(1).path("max_events").intValue();
                    ObjectNode result = (ObjectNode) fixture("events-v23.json").path("poll_result").deepCopy();
                    if (cursor == 3) result.set("events", JSON.createArrayNode());
                    yield result;
                }
                default -> throw new AssertionError("Unexpected sample method: " + method);
            };
        }

        private static JsonNode fixture(String name) throws Exception {
            try (var stream = ExampleProtocolCompatibilityTest.class.getResourceAsStream("/protocol-fixtures/" + name)) {
                return JSON.readTree(stream);
            }
        }

        @Override
        public void close() throws Exception {
            try {
                server.close();
                task.get(5, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
