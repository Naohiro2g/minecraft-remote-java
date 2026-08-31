package club.code2create.mcremote.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Protocol23CapabilitiesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void projectsCatalogAndPlayerPoseIntoJavaTypes() throws Exception {
        try (ScriptedPeer peer = new ScriptedPeer(server -> {
            try (ScriptedPeer.PeerConnection connection = server.accept()) {
                ObjectNode hello = connection.readRequest();
                connection.result(hello, helloResult());

                ObjectNode catalog = connection.readRequest();
                assertRequest(catalog, 2, "catalog.get");
                assertEquals(0, catalog.path("params").size());
                connection.result(
                        catalog,
                        Map.of(
                                "catalogHash", "sha256:fixture-catalog",
                                "block",
                                        Map.of(
                                                "minecraft:oak_log",
                                                Map.of(
                                                        "states",
                                                                Map.of(
                                                                        "axis",
                                                                                List.of(
                                                                                        "x",
                                                                                        "y",
                                                                                        "z"),
                                                                        "waterlogged",
                                                                                List.of(false, true)),
                                                        "default_state",
                                                                Map.of(
                                                                        "axis", "y",
                                                                        "waterlogged", false),
                                                        "future_entry_field", "ignored")),
                                "entity", Map.of("minecraft:allay", Map.of()),
                                "particle", Map.of("minecraft:flame", Map.of())));

                ObjectNode getPose = connection.readRequest();
                assertRequest(getPose, 3, "player.getPose");
                assertEquals(0, getPose.path("params").size());
                connection.result(
                        getPose,
                        Map.of(
                                "dimension", "minecraft:overworld",
                                "pos", List.of(1.235, 64.0, -1.235),
                                "yaw", -179.25,
                                "pitch", 12.5));

                ObjectNode setPose = connection.readRequest();
                assertRequest(setPose, 4, "player.setPose");
                assertEquals(
                        MAPPER.valueToTree(
                                List.of("minecraft:overworld", 4, 65, -3, 181, -10)),
                        setPose.get("params"));
                connection.result(
                        setPose,
                        Map.of(
                                "dimension", "minecraft:overworld",
                                "pos", List.of(4.0, 65.0, -3.0),
                                "yaw", -179.0,
                                "pitch", -10.0));
            }
        })) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port()).connect()) {
                Catalog catalog = client.getCatalog();
                assertEquals("sha256:fixture-catalog", catalog.catalogHash());
                assertEquals(
                        Map.of("axis", "y", "waterlogged", false),
                        catalog.blocks().get("minecraft:oak_log").defaultState());
                assertEquals(java.util.Set.of("minecraft:allay"), catalog.entityIds());
                assertEquals(java.util.Set.of("minecraft:flame"), catalog.particleIds());

                assertEquals(
                        new PlayerPose(
                                "minecraft:overworld",
                                new Vec3(1.235, 64.0, -1.235),
                                -179.25,
                                12.5),
                        client.getPlayerPose());
                assertEquals(
                        -179.0,
                        client.setPlayerPose(
                                        "minecraft:overworld",
                                        4.0,
                                        65.0,
                                        -3.0,
                                        181.0,
                                        -10.0)
                                .yaw());
            }
            peer.await();
        }
    }

    @Test
    void consumesTheOwnerEventsSignAndParticleFixtures() throws Exception {
        JsonNode eventsFixture = fixture("events-v23.json");
        JsonNode signFixture = fixture("sign-v23.json");
        JsonNode spawnFixture = fixture("spawn-v22.json");

        try (ScriptedPeer peer = new ScriptedPeer(server -> {
            try (ScriptedPeer.PeerConnection connection = server.accept()) {
                ObjectNode hello = connection.readRequest();
                connection.result(hello, helloResult());

                ObjectNode events = connection.readRequest();
                assertRequest(events, 2, eventsFixture.path("method").asText());
                assertEquals(eventsFixture.path("poll_requests").path("bounded"), events.get("params"));
                connection.result(events, eventsFixture.path("poll_result"));

                ObjectNode defaultParticle = connection.readRequest();
                assertRequest(defaultParticle, 3, "world.spawnParticle");
                JsonNode defaultParticleCase =
                        spawnFixture.path("spawn_particle").path("default_force");
                assertEquals(defaultParticleCase.path("params"), defaultParticle.get("params"));
                connection.result(defaultParticle, defaultParticleCase.path("result"));

                ObjectNode explicitParticle = connection.readRequest();
                assertRequest(explicitParticle, 4, "world.spawnParticle");
                JsonNode explicitParticleCase =
                        spawnFixture.path("spawn_particle").path("explicit_false");
                assertEquals(explicitParticleCase.path("params"), explicitParticle.get("params"));
                connection.result(explicitParticle, explicitParticleCase.path("result"));

                ObjectNode getSign = connection.readRequest();
                assertRequest(
                        getSign, 5, signFixture.path("methods").path("get").asText());
                JsonNode getSignCase = signFixture.path("get_sign").path("B6-S03");
                assertEquals(getSignCase.path("params"), getSign.get("params"));
                connection.result(getSign, getSignCase.path("result"));

                ObjectNode setSign = connection.readRequest();
                assertRequest(
                        setSign, 6, signFixture.path("methods").path("set").asText());
                JsonNode setSignCase = signFixture.path("set_sign").path("B6-S04");
                assertEquals(setSignCase.path("params"), setSign.get("params"));
                connection.result(setSign, null);

                ObjectNode updateSign = connection.readRequest();
                assertRequest(
                        updateSign,
                        7,
                        signFixture.path("methods").path("update_line").asText());
                JsonNode updateCase = signFixture.path("update_sign_line").path("B6-S05");
                assertEquals(updateCase.path("params"), updateSign.get("params"));
                connection.result(updateSign, null);
            }
        })) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port()).connect()) {
                EventPollResult poll = client.pollEvents(0, 3);
                assertEquals(3, poll.events().size());
                MinecraftEvent.PickaxePoke poke =
                        assertInstanceOf(MinecraftEvent.PickaxePoke.class, poll.events().get(0));
                assertEquals(MinecraftEvent.Hand.MAIN, poke.hand());
                assertEquals("minecraft:diamond_pickaxe", poke.item());
                assertInstanceOf(MinecraftEvent.ChatPosted.class, poll.events().get(1));
                MinecraftEvent.ProjectileHit hit =
                        assertInstanceOf(MinecraftEvent.ProjectileHit.class, poll.events().get(2));
                MinecraftEvent.BlockTarget target =
                        assertInstanceOf(MinecraftEvent.BlockTarget.class, hit.target());
                assertEquals("north", target.face().orElseThrow());
                assertEquals(3, poll.throughSequence());

                assertEquals(
                        4,
                        client.spawnParticle(
                                1.25,
                                2.5,
                                3.75,
                                0,
                                0.5,
                                0,
                                "minecraft:flame",
                                0.1,
                                4));
                assertEquals(
                        4,
                        client.spawnParticle(
                                1.25,
                                2.5,
                                3.75,
                                0,
                                0.5,
                                0,
                                "minecraft:flame",
                                0.1,
                                4,
                                false));

                Sign.Value sign = client.getSign(1, 2, 3);
                assertTrue(sign.waxed());
                assertEquals("Front 2", sign.front().get(1).text());
                assertEquals(List.of(Sign.Decoration.BOLD), sign.front().get(1).decorations());

                client.setSign(
                        1,
                        2,
                        3,
                        Sign.Content.both(
                                List.of(
                                        Sign.LineSpec.plain("Front 1"),
                                        Sign.LineSpec.styled(
                                                "Front 2", "gold", Sign.Decoration.BOLD),
                                        Sign.LineSpec.plain(""),
                                        Sign.LineSpec.plain("")),
                                List.of(
                                        Sign.LineSpec.plain("Back 1"),
                                        Sign.LineSpec.plain(""),
                                        Sign.LineSpec.plain(""),
                                        Sign.LineSpec.plain(""))));
                client.updateSignLine(
                        1,
                        2,
                        3,
                        Sign.Face.FRONT,
                        0,
                        Sign.LineSpec.plain("Updated line"));
            }
            peer.await();
        }
    }

    @Test
    void rejectsLegacyProtocol22EventAndInvalidTypedInputs() throws Exception {
        JsonNode fixture = fixture("events-v23.json");
        ObjectNode result = fixture.path("poll_result").deepCopy();
        result.set(
                "events",
                MAPPER.createArrayNode()
                        .add(fixture.path("legacy_rejected_events").path("block_right_click")));

        try (ScriptedPeer peer = new ScriptedPeer(server -> {
            try (ScriptedPeer.PeerConnection connection = server.accept()) {
                ObjectNode hello = connection.readRequest();
                connection.result(hello, helloResult());
                ObjectNode poll = connection.readRequest();
                connection.result(poll, result);
            }
        })) {
            try (MinecraftRemote client = MinecraftRemote.builder("127.0.0.1", peer.port()).connect()) {
                assertThrows(ProtocolException.class, () -> client.pollEvents(0));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> client.pollEvents(0, 0));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Sign.LineSpec.styled("x", "chartreuse"));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> client.updateSignLine(
                                1,
                                2,
                                3,
                                Sign.Face.FRONT,
                                4,
                                Sign.LineSpec.plain("x")));
            }
            peer.await();
        }
    }

    @Test
    void vendoredFixturesMatchThePinnedOwnerBytes() throws Exception {
        assertFixtureDigest(
                "events-v23.json",
                "31760d267f3c2641042fbe8595fda9c259134a1c05423271a99cb74da1efa9aa");
        assertFixtureDigest(
                "sign-v23.json",
                "7ffb63c264602cba56117eefff1f9604b955df04c5cc655e877772b8ff7cd30e");
        assertFixtureDigest(
                "spawn-v22.json",
                "1120e6c8d41b05b65c916fa96f496b02884123ec7ef59b0a226eea48bebf3abd");
    }

    @Test
    void catalogBlockEntryEnforcesTheSharedSchemaInvariants() {
        Catalog.BlockEntry numeric = new Catalog.BlockEntry(
                Map.of("level", List.of(1, 2L)), Map.of("level", 1.0));
        assertEquals(1.0, numeric.defaultState().get("level"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new Catalog.BlockEntry(
                        Map.of("mixed", List.of(true, "true")), Map.of("mixed", true)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Catalog.BlockEntry(
                        Map.of("duplicate", List.of(1, 1.0)), Map.of("duplicate", 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Catalog.BlockEntry(
                        Map.of("axis", List.of("x", "y")), Map.of("axis", "z")));
    }

    private static void assertFixtureDigest(String name, String expected) throws Exception {
        byte[] bytes;
        try (InputStream stream = resource(name)) {
            bytes = stream.readAllBytes();
        }
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertEquals(expected, actual);
    }

    private static JsonNode fixture(String name) throws IOException {
        try (InputStream stream = resource(name)) {
            return MAPPER.readTree(stream);
        }
    }

    private static InputStream resource(String name) {
        InputStream stream = Protocol23CapabilitiesTest.class.getResourceAsStream(
                "/protocol-fixtures/" + name);
        if (stream == null) {
            throw new IllegalArgumentException("missing fixture: " + name);
        }
        return stream;
    }

    private static void assertRequest(ObjectNode request, int id, String method) {
        assertEquals("2.0", request.path("jsonrpc").asText());
        assertEquals(id, request.path("id").asInt());
        assertEquals(method, request.path("method").asText());
    }

    private static Map<String, Object> helloResult() {
        return Map.ofEntries(
                Map.entry("protocol", "23.0.0"),
                Map.entry("mc_version", "1.21.8"),
                Map.entry("supported_mc_versions", List.of("1.21.8")),
                Map.entry("catalogHash", "sha256:fixture-catalog"),
                Map.entry("world_constants", Map.of("min_y", -64, "max_y", 319)),
                Map.entry("session", "fixture-session"),
                Map.entry("player", "FixturePlayer"),
                Map.entry("dimension", "minecraft:overworld"),
                Map.entry("origin", List.of(200, 0, 200)),
                Map.entry("permissions", Map.of("build", true)),
                Map.entry("server", "fixture-server"));
    }
}
