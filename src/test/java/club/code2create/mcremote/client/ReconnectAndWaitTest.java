package club.code2create.mcremote.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the E2 waiting and reconnecting decisions (2026-11-11-01 (3)(5),
 * 2026-11-12-05) that the shared F1 cases do not cover.
 */
class ReconnectAndWaitTest {

    private static final String TOKEN = "mcrs_unit_test_only_not_a_real_credential";
    private static final String PAIRING_ID = "unit-pairing";

    @Test
    void continuesPairingOnANewConnectionAfterThePollResponseWasLost() throws Exception {
        // Beyond F1-AU-12: the poll whose response was lost ends as completion unknown, and the
        // next poll for the same pairing_id goes on a new connection; no second pairBegin.
        CredentialStore store = CredentialStore.inMemory();
        AtomicInteger pairBegins = new AtomicInteger();
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.error(hello, -32000, "auth required", "auth_required");
                    }
                    try (ScriptedPeer.PeerConnection pairing = server.accept()) {
                        ObjectNode begin = pairing.readRequest();
                        assertEquals("auth.pairBegin", begin.path("method").asText());
                        pairBegins.incrementAndGet();
                        pairing.result(begin, Map.of(
                                "pairing_id", PAIRING_ID, "pair_code", "123456", "expires_in", 120));
                        ObjectNode poll = pairing.readRequest();
                        pairing.result(poll, Map.of("status", "pending"));
                        ObjectNode lost = pairing.readRequest();
                        assertEquals(3, lost.path("id").asInt());
                        pairing.eof();
                        pairing.expectClientClose();
                    }
                    try (ScriptedPeer.PeerConnection continued = server.accept()) {
                        ObjectNode poll = continued.readRequest();
                        assertEquals("auth.pairPoll", poll.path("method").asText());
                        assertEquals(1, poll.path("id").asInt());
                        assertEquals(PAIRING_ID, poll.path("params").path("pairing_id").asText());
                        continued.result(poll, Map.of("status", "pending"));
                        ObjectNode approved = continued.readRequest();
                        assertEquals(PAIRING_ID, approved.path("params").path("pairing_id").asText());
                        continued.result(approved, Map.of("status", "ok", "token", TOKEN));
                        continued.expectClientClose();
                    }
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        assertEquals(TOKEN, hello.path("params").path("auth").path("token").asText());
                        connection.result(hello, helloResult("server-a", "session-a", null));
                    }
                })) {
            ServerTarget target = new ServerTarget("127.0.0.1", peer.port());
            try (MinecraftRemote client = MinecraftRemote.builder(target.host(), target.port())
                    .credentialStore(store)
                    .pairingListener(challenge -> {})
                    .pairPollInterval(Duration.ofMillis(1))
                    .connect()) {
                assertEquals(Optional.of("session-a"), client.helloResult().session());
            }
            assertEquals(Optional.of(TOKEN), store.load(target));
            assertEquals(1, pairBegins.get());
            peer.await();
        }
    }

    @Test
    void doesNotPairAgainWhenTheContinuedPollFails() throws Exception {
        // 2026-11-12-05: a continued poll that fails with pair_not_found ends the pairing.
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.error(hello, -32000, "auth required", "auth_required");
                    }
                    try (ScriptedPeer.PeerConnection pairing = server.accept()) {
                        ObjectNode begin = pairing.readRequest();
                        pairing.result(begin, Map.of(
                                "pairing_id", PAIRING_ID, "pair_code", "123456", "expires_in", 120));
                        pairing.readRequest();
                        pairing.eof();
                        pairing.expectClientClose();
                    }
                    try (ScriptedPeer.PeerConnection continued = server.accept()) {
                        ObjectNode poll = continued.readRequest();
                        assertEquals("auth.pairPoll", poll.path("method").asText());
                        continued.error(poll, -32000, "pairing not found", "pair_not_found");
                        continued.expectClientClose();
                    }
                })) {
            RemoteCallException exception = assertThrows(RemoteCallException.class,
                    () -> MinecraftRemote.builder("127.0.0.1", peer.port())
                            .pairingListener(challenge -> {})
                            .pairPollInterval(Duration.ofMillis(1))
                            .connect());
            assertEquals(Optional.of("pair_not_found"), exception.reason());
            peer.await();
        }
    }

    @Test
    void threadsThatNoticeTheCloseTogetherReconnectOnce() throws Exception {
        // 2026-11-11-01 (5) E with real threads: one reconnect, every call sent on it once.
        int threads = 4;
        List<JsonRpcConnection> connections = new CopyOnWriteArrayList<>();
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.result(hello, helloResult("server-a", "session-a", null));
                    }
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.result(hello, helloResult("server-a", "session-b", null));
                        for (int index = 0; index < threads; index++) {
                            ObjectNode getBlock = connection.readRequest();
                            assertEquals("world.getBlock", getBlock.path("method").asText());
                            assertEquals(index + 2, getBlock.path("id").asInt());
                            connection.result(getBlock, Map.of(
                                    "block_id", "minecraft:stone", "state", Map.of()));
                        }
                        connection.expectClientClose();
                    }
                })) {
            List<ReconnectEvent> events = new CopyOnWriteArrayList<>();
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port())
                    .connectionObserver(connections::add)
                    .reconnectListener(events::add)
                    .connect()) {
                assertTrue(connections.get(0).pollClosed(Duration.ofSeconds(2)));
                assertFalse(client.isConnected());
                CyclicBarrier start = new CyclicBarrier(threads);
                List<Future<BlockValue>> results = new ArrayList<>();
                for (int index = 0; index < threads; index++) {
                    results.add(executor.submit(() -> {
                        start.await();
                        return client.getBlock(0, 0, 0);
                    }));
                }
                for (Future<BlockValue> result : results) {
                    assertEquals("minecraft:stone", result.get(5, TimeUnit.SECONDS).blockId());
                }
                assertEquals(2, connections.size());
                assertEquals(1, events.size());
                assertTrue(client.isConnected());
            } finally {
                executor.shutdownNow();
            }
            peer.await();
        }
    }

    @Test
    void reconnectWaitsGrowWithJitterUntilTheWindowRunsOut() throws Exception {
        // 2026-11-11-01 (5) F: waits double from the initial delay up to the maximum, each between
        // half and all of it, and the call fails as not sent when the window runs out.
        for (double random : new double[] {1.0, 0.0}) {
            VirtualTicker ticker = new VirtualTicker(true);
            try (ScriptedPeer peer = new ScriptedPeer(
                    server -> {
                        try (ScriptedPeer.PeerConnection connection = server.accept()) {
                            ObjectNode hello = connection.readRequest();
                            connection.result(hello, helloResult("server-a", "session-a", null));
                            server.closeListener();
                        }
                    })) {
                try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port())
                        .ticker(ticker)
                        .random(() -> random)
                        .connect()) {
                    peer.await();
                    awaitDisconnected(client);
                    NotSentException exception =
                            assertThrows(NotSentException.class, () -> client.getBlock(0, 0, 0));
                    assertEquals(NotSentException.Reason.CONNECTION_FAILED, exception.reason());
                    assertInstanceOf(TransportException.class, exception.getCause());
                }
            }
            List<Long> expected = new ArrayList<>();
            long delay = Duration.ofMillis(500).toNanos();
            long elapsed = 0;
            while (true) {
                long wait = delay / 2 + (long) (delay / 2 * random);
                if (elapsed + wait > Duration.ofMinutes(5).toNanos()) {
                    break;
                }
                expected.add(wait);
                elapsed += wait;
                delay = Math.min(delay * 2, Duration.ofSeconds(30).toNanos());
            }
            assertEquals(expected, ticker.sleeps(), "random=" + random);
        }
    }

    @Test
    void failsAsNotSentWhenAutomaticReconnectingIsDisabled() throws Exception {
        List<JsonRpcConnection> connections = new CopyOnWriteArrayList<>();
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.result(hello, helloResult("server-a", "session-a", null));
                    }
                })) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port())
                    .autoReconnect(false)
                    .connectionObserver(connections::add)
                    .connect()) {
                peer.await();
                awaitDisconnected(client);
                NotSentException exception =
                        assertThrows(NotSentException.class, () -> client.getBlock(0, 0, 0));
                assertEquals(NotSentException.Reason.DISCONNECTED, exception.reason());
                assertEquals(1, connections.size());
            }
        }
    }

    @Test
    void reportsWhatChangedWhenReconnecting() throws Exception {
        // 2026-11-11-01 (5) C: hello is checked again; server, session, and catalogHash changes are
        // reported, and the restored build is the last confirmed one.
        List<ReconnectEvent> events = new CopyOnWriteArrayList<>();
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.result(hello, helloResult("server-a", "session-a", "a".repeat(64)));
                        ObjectNode dimension = connection.readRequest();
                        assertEquals("build.setDimension", dimension.path("method").asText());
                        assertEquals("the_nether", dimension.path("params").get(0).asText());
                        connection.result(dimension, Map.of(
                                "dimension", "minecraft:the_nether", "origin", List.of(200, 0, 200)));
                    }
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        assertEquals("minecraft:the_nether",
                                hello.path("params").path("build").path("dimension").asText());
                        Map<String, Object> result =
                                helloResult("server-b", "session-b", "b".repeat(64));
                        result.put("dimension", "minecraft:the_nether");
                        connection.result(hello, result);
                        ObjectNode getBlock = connection.readRequest();
                        connection.result(getBlock, Map.of(
                                "block_id", "minecraft:netherrack", "state", Map.of()));
                    }
                })) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port())
                    .reconnectListener(events::add)
                    .connect()) {
                client.setDimension("the_nether");
                awaitDisconnected(client);
                assertEquals("minecraft:netherrack", client.getBlock(0, 0, 0).blockId());
            }
            peer.await();
        }
        assertEquals(1, events.size());
        ReconnectEvent event = events.get(0);
        assertTrue(event.serverChanged());
        assertTrue(event.sessionChanged());
        assertTrue(event.catalogHashChanged());
        assertTrue(event.eventsMayHaveBeenLost());
        assertEquals(new BuildContext("minecraft:the_nether", new BlockPosition(200, 0, 200)),
                event.restoredBuild());
        assertEquals(Optional.of("server-b"), event.current().server());
        assertEquals(Optional.of("server-a"), event.previous().server());
    }

    @Test
    void aLongerResponseTimeoutAppliesToRequestsAlreadyWaiting() throws Exception {
        // 2026-11-11-01 (3): the wait can be extended for a connection while a request is pending.
        VirtualTicker ticker = new VirtualTicker();
        try (ScriptedPeer peer = new ScriptedPeer(
                server -> {
                    try (ScriptedPeer.PeerConnection connection = server.accept()) {
                        ObjectNode hello = connection.readRequest();
                        connection.result(hello, helloResult("server-a", "session-a", null));
                        ObjectNode getBlock = connection.readRequest();
                        ticker.advance(Duration.ofSeconds(50).toNanos());
                        ObjectNode longer = connection.readRequest();
                        assertEquals("ready", longer.path("method").asText());
                        connection.result(getBlock, Map.of(
                                "block_id", "minecraft:stone", "state", Map.of()));
                    }
                })) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port())
                    .responseTimeout(Duration.ofMinutes(1))
                    .ticker(ticker)
                    .connect()) {
                Future<BlockValue> pending = executor.submit(() -> client.getBlock(0, 0, 0));
                while (ticker.nanoTime() == 0) {
                    Thread.sleep(1);
                }
                client.setResponseTimeout(Duration.ofMinutes(2));
                assertEquals(Duration.ofMinutes(2), client.responseTimeout());
                ticker.advance(Duration.ofSeconds(50).toNanos());
                client.checkTimers();
                assertTrue(client.isConnected(), "100 s is within the extended two minutes");
                // Tell the peer to answer; this request itself is never answered.
                client.currentConnection().send("ready", null);
                assertEquals("minecraft:stone", pending.get(5, TimeUnit.SECONDS).blockId());
            } finally {
                executor.shutdownNow();
            }
            peer.await();
        }
    }

    @Test
    void enablesTcpKeepAliveByDefaultAndCanTurnItOff() throws Exception {
        // 2026-11-11-01 (3)(5) G: transport liveness is the operating system's TCP keepalive.
        for (boolean enabled : new boolean[] {true, false}) {
            List<JsonRpcConnection> connections = new CopyOnWriteArrayList<>();
            try (ScriptedPeer peer = new ScriptedPeer(
                    server -> {
                        try (ScriptedPeer.PeerConnection connection = server.accept()) {
                            ObjectNode hello = connection.readRequest();
                            connection.result(hello, helloResult("server-a", "session-a", null));
                            connection.expectClientClose();
                        }
                    })) {
                MinecraftRemote.Builder builder = MinecraftRemote.builder("127.0.0.1", peer.port())
                        .connectionObserver(connections::add);
                if (!enabled) {
                    builder.disableKeepAlive();
                }
                try (MinecraftRemote client = builder.connect()) {
                    JsonRpcConnection connection = connections.get(0);
                    assertEquals(enabled, connection.keepAliveEnabled());
                    if (enabled) {
                        assertOptionalOption(connection, "TCP_KEEPIDLE", 30);
                        assertOptionalOption(connection, "TCP_KEEPINTERVAL", 15);
                        assertOptionalOption(connection, "TCP_KEEPCOUNT", 8);
                    }
                }
                peer.await();
            }
        }
        assertEquals(Duration.ofSeconds(150), KeepAlive.DEFAULT.tolerance());
    }

    private static void assertOptionalOption(JsonRpcConnection connection, String name, int value) {
        Integer actual = connection.keepAliveOption(name);
        if (actual != null) {
            assertEquals(value, actual, name);
        }
    }

    private static void awaitDisconnected(MinecraftRemote client) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (client.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertFalse(client.isConnected(), "the client did not notice the close");
    }

    private static Map<String, Object> helloResult(String server, String session, String catalogHash) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol", "23.0.0");
        result.put("mc_version", "1.21.11");
        result.put("supported_mc_versions", List.of("1.21.11"));
        result.put("catalogHash", catalogHash);
        result.put("world_constants", Map.of());
        result.put("session", session);
        result.put("player", "player-id");
        result.put("dimension", "minecraft:overworld");
        result.put("origin", List.of(200, 0, 200));
        result.put("server", server);
        return result;
    }
}
