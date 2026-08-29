package club.code2create.mcremote.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MinecraftRemoteTest {

    @Test
    void connectsAndReadsAndWritesOneStructuredBlock() throws Exception {
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        assertRequest(hello, 1, "hello");
                        assertEquals("23.0.0", hello.path("params").path("protocol").asText());
                        assertEquals(1, hello.path("params").size());
                        Map<String, Object> enforcementOffHello = helloResult("23.0.0");
                        enforcementOffHello.remove("session");
                        enforcementOffHello.remove("player");
                        enforcementOffHello.remove("permissions");
                        enforcementOffHello.remove("server");
                        connection.result(hello, enforcementOffHello);

                        ObjectNode setBlock = connection.readRequest();
                        assertRequest(setBlock, 2, "world.setBlock");
                        assertEquals(4, setBlock.path("params").size());
                        assertEquals(1, setBlock.path("params").get(0).asInt());
                        assertEquals(2, setBlock.path("params").get(1).asInt());
                        assertEquals(3, setBlock.path("params").get(2).asInt());
                        assertEquals(
                                "minecraft:oak_log",
                                setBlock.path("params").get(3).path("block_id").asText());
                        assertEquals(
                                "y",
                                setBlock.path("params").get(3).path("state").path("axis").asText());
                        connection.result(setBlock, null);

                        ObjectNode getBlock = connection.readRequest();
                        assertRequest(getBlock, 3, "world.getBlock");
                        connection.result(
                                getBlock,
                                Map.of(
                                        "block_id",
                                        "minecraft:oak_log",
                                        "state",
                                        Map.of("axis", "y", "waterlogged", false)));
                    }
                })) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port()).connect()) {
                assertEquals("23.0.0", client.helloResult().protocol());
                assertEquals(new BlockPosition(200, 0, 200), client.helloResult().origin());
                assertTrue(client.helloResult().session().isEmpty());
                assertTrue(client.helloResult().player().isEmpty());
                assertTrue(client.helloResult().permissions().isEmpty());
                assertTrue(client.helloResult().server().isEmpty());

                client.setBlock(
                        1,
                        2,
                        3,
                        BlockSpec.of("minecraft:oak_log", Map.of("axis", "y")));
                BlockValue block = client.getBlock(1, 2, 3);

                assertEquals("minecraft:oak_log", block.blockId());
                assertEquals(Map.of("axis", "y", "waterlogged", false), block.state());
            }
            peer.await();
        }
    }

    @Test
    void postsOneChatMessageAndFillsOneStructuredBlockRegion() throws Exception {
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.result(hello, helloResult("23.0.0"));

                        ObjectNode postToChat = connection.readRequest();
                        assertRequest(postToChat, 2, "chat.post");
                        assertEquals(1, postToChat.path("params").size());
                        assertEquals(
                                "Hello from Java!", postToChat.path("params").get(0).asText());
                        connection.result(postToChat, Map.of("delivered", true));

                        ObjectNode setBlocks = connection.readRequest();
                        assertRequest(setBlocks, 3, "world.setBlocks");
                        assertEquals(7, setBlocks.path("params").size());
                        assertEquals(-2, setBlocks.path("params").get(0).asInt());
                        assertEquals(10, setBlocks.path("params").get(1).asInt());
                        assertEquals(-3, setBlocks.path("params").get(2).asInt());
                        assertEquals(4, setBlocks.path("params").get(3).asInt());
                        assertEquals(11, setBlocks.path("params").get(4).asInt());
                        assertEquals(5, setBlocks.path("params").get(5).asInt());
                        assertEquals(
                                "minecraft:stone",
                                setBlocks.path("params").get(6).path("block_id").asText());
                        assertEquals(0, setBlocks.path("params").get(6).path("state").size());
                        connection.result(setBlocks, null);
                    }
                })) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port()).connect()) {
                client.postToChat("Hello from Java!");
                client.setBlocks(
                        -2, 10, -3, 4, 11, 5, BlockSpec.of("minecraft:stone"));
            }
            peer.await();
        }
    }

    @Test
    void changesAndRetainsTheServerCanonicalBuildOrigin() throws Exception {
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.result(hello, helloResult("23.0.0"));

                        ObjectNode setOrigin = connection.readRequest();
                        assertRequest(setOrigin, 2, "build.setOrigin");
                        assertEquals(
                                List.of(300, 0, 300),
                                List.of(
                                        setOrigin.path("params").get(0).asInt(),
                                        setOrigin.path("params").get(1).asInt(),
                                        setOrigin.path("params").get(2).asInt()));
                        connection.result(
                                setOrigin,
                                Map.of(
                                        "dimension", "minecraft:overworld",
                                        "origin", List.of(300, 0, 300)));
                    }
                })) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port()).connect()) {
                assertEquals(
                        new BuildContext(
                                "minecraft:overworld", new BlockPosition(200, 0, 200)),
                        client.buildContext());

                BuildContext updated = client.setOrigin(300, 0, 300);

                assertEquals(
                        new BuildContext(
                                "minecraft:overworld", new BlockPosition(300, 0, 300)),
                        updated);
                assertEquals(updated, client.buildContext());
                assertEquals(
                        new BlockPosition(200, 0, 200), client.helloResult().origin());
            }
            peer.await();
        }
    }

    @Test
    void pairsOnlyAfterAnAuthenticationReasonAndRetriesHelloWithTheToken() throws Exception {
        CredentialStore store = CredentialStore.inMemory();
        AtomicReference<PairingChallenge> observed = new AtomicReference<>();

        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection first = server.accept()) {
                        ObjectNode hello = first.readRequest();
                        assertRequest(hello, 1, "hello");
                        first.error(hello, -32000, "authentication required", "auth_required");
                    }
                    try (ScriptedPeer.PeerConnection pairing = server.accept()) {
                        ObjectNode begin = pairing.readRequest();
                        assertRequest(begin, 1, "auth.pairBegin");
                        assertEquals(
                                "session", begin.path("params").path("token_type").asText());
                        assertEquals(
                                "minecraft-remote-java",
                                begin.path("params").path("client").path("name").asText());
                        assertTrue(begin.path("params").path("client").has("version"));
                        assertTrue(begin.path("params").path("client").has("locale"));
                        assertTrue(!begin.path("params").has("protocol"));
                        pairing.result(
                                begin,
                                Map.of(
                                        "pairing_id", "private-pairing-id",
                                        "pair_code", "827419",
                                        "expires_in", 120));

                        ObjectNode pending = pairing.readRequest();
                        assertRequest(pending, 2, "auth.pairPoll");
                        assertEquals(
                                "private-pairing-id",
                                pending.path("params").path("pairing_id").asText());
                        pairing.result(pending, Map.of("status", "pending"));

                        ObjectNode approved = pairing.readRequest();
                        assertRequest(approved, 3, "auth.pairPoll");
                        pairing.result(approved, Map.of("status", "ok", "token", "mcrs_secret"));
                    }
                    try (ScriptedPeer.PeerConnection authenticated = server.accept()) {
                        ObjectNode hello = authenticated.readRequest();
                        assertRequest(hello, 1, "hello");
                        assertEquals(
                                "mcrs_secret",
                                hello.path("params").path("auth").path("token").asText());
                        authenticated.result(hello, helloResult("23.0.0"));
                    }
                })) {
            ServerTarget target = new ServerTarget("127.0.0.1", peer.port());
            try (MinecraftRemote ignored = MinecraftRemote.builder(target.host(), target.port())
                    .credentialStore(store)
                    .pairPollInterval(Duration.ofMillis(1))
                    .pairingListener(observed::set)
                    .connect()) {
                assertEquals("mcrs_secret", store.load(target).orElseThrow());
                assertEquals("827419", observed.get().pairCode());
                assertEquals("827-419", observed.get().displayCode());
                assertEquals("/mcremote pair 827-419", observed.get().command());
            }
            peer.await();
        }
    }

    @Test
    void reportsPairingRequiredWithoutStartingAnImplicitUi() throws Exception {
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.error(hello, -32000, "authentication required", "auth_required");
                    }
                })) {
            PairingRequiredException exception = assertThrows(
                    PairingRequiredException.class,
                    () -> MinecraftRemote.builder("127.0.0.1", peer.port()).connect());
            assertEquals("auth_required", exception.reason());
            peer.await();
        }
    }

    @Test
    void rejectsAnIncompatibleProtocolAdvertisedByHello() throws Exception {
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.result(hello, helloResult("24.0.0"));
                    }
                })) {
            ProtocolException exception = assertThrows(
                    ProtocolException.class,
                    () -> MinecraftRemote.builder("127.0.0.1", peer.port()).connect());
            assertTrue(exception.getMessage().contains("server=24.0.0"));
            peer.await();
        }
    }

    @Test
    void surfacesStableRemoteErrorReason() throws Exception {
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.result(hello, helloResult("23.0.0"));
                        ObjectNode setBlock = connection.readRequest();
                        connection.error(setBlock, -32602, "unknown block", "unknown_block");
                    }
                })) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port()).connect()) {
                RemoteCallException exception = assertThrows(
                        RemoteCallException.class,
                        () -> client.setBlock(0, 0, 0, BlockSpec.of("missing")));
                assertEquals(-32602, exception.code());
                assertEquals("unknown_block", exception.reason().orElseThrow());
            }
            peer.await();
        }
    }

    private static void assertRequest(ObjectNode request, int id, String method) {
        assertEquals("2.0", request.path("jsonrpc").asText());
        assertEquals(id, request.path("id").asInt());
        assertEquals(method, request.path("method").asText());
    }

    private static Map<String, Object> helloResult(String protocol) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol", protocol);
        result.put("mc_version", "1.21.11");
        result.put("supported_mc_versions", List.of("1.21.11"));
        result.put("catalogHash", null);
        result.put("world_constants", nullableMap("y_sea", null));
        result.put("session", "session-id");
        result.put("player", "player-id");
        result.put("dimension", "minecraft:overworld");
        result.put("origin", List.of(200, 0, 200));
        result.put(
                "permissions", Map.of("online", true, "offline", false, "buildRange", 100));
        result.put("server", "scripted-peer");
        return result;
    }

    private static Map<String, Object> nullableMap(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

}
