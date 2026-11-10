package club.code2create.mcremote.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs every F1 client-bootstrap case from the pinned tooling fixtures against the Java client.
 *
 * <p>Dialog cases drive the public API against {@link BootstrapFixturePeer}. A client message that
 * the typed API refuses to build (for example a block value without a state object, an unknown
 * token type, or a pairing id the server did not issue) is sent through the client's own JSON-RPC
 * connection instead, so the transport, id correlation, and error decoding remain the client's.
 */
class ClientBootstrapFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DIR = "/protocol-fixtures/";
    private static final Map<String, String> DIGESTS = new LinkedHashMap<>();

    static {
        DIGESTS.put("client-bootstrap-framing-v23.2.1.json",
                "be063b519b5d653a4bb4ec3da6f67e5d481a28dcf5913961c848b2b909d4b26d");
        DIGESTS.put("client-bootstrap-hello-v23.2.1.json",
                "78297f05eb4352b0dabffab3242e933c1e68cb192bbb6d242019c7956c809d57");
        DIGESTS.put("client-bootstrap-pairing-v23.2.1.json",
                "cdb2209d8a1d46fede158db50c3672936dad152c7fd2bec7ef5a068ed57a1fe1");
        DIGESTS.put("client-bootstrap-blocks-v23.2.1.json",
                "664babdf3131a74e4aaf6d9e5dfb5e4cfc6e197df0a0d48142688820f1be813d");
        DIGESTS.put("client-bootstrap-errors-v23.2.1.json",
                "c458d3ffbae846cfb33c032eba14d8f0005c6a1fbe871290b53fcfec8ec41aaa");
        DIGESTS.put("client-bootstrap-close-v23.2.1.json",
                "44e9a4276bbd204671db0385f4f801cd3b152d0853ac943876ede73db6bd8dd1");
    }

    private static final String SCHEMA = "client-bootstrap-v1.schema.json";
    private static final String SCHEMA_DIGEST =
            "e848bd3a61d4c0a737f7f3a8963baf2fcbb83247486f3d8aa69f022bb55a7ede";
    private static final String BLOCK_VALUES = "block-value-v22.json";
    private static final String BLOCK_VALUES_DIGEST =
            "e8e108d59c40751d67a432543d75cb4bf9146ef9781721012085c80545b854f3";
    private static final String LICENSE_DIGEST =
            "aa329a0bafc839b95124306141db13a381de45818bf629e983c44ea55ab5a7fb";

    @Test
    void pinnedFixtureBytesMatch() throws Exception {
        for (Map.Entry<String, String> entry : DIGESTS.entrySet()) {
            assertEquals(entry.getValue(), sha256(entry.getKey()), entry.getKey());
        }
        assertEquals(SCHEMA_DIGEST, sha256(SCHEMA));
        assertEquals(BLOCK_VALUES_DIGEST, sha256(BLOCK_VALUES));
        assertEquals(LICENSE_DIGEST, sha256("LICENSE"));
        assertEquals(1_070, bytes("LICENSE").length);
    }

    @Test
    void fixturesFollowTheSchemaShape() throws Exception {
        JsonNode schema = load(SCHEMA);
        JsonNode properties = schema.path("properties");
        JsonNode caseSchema = properties.path("cases").path("items");
        Set<String> suites = textSet(properties.path("suite").path("enum"));
        Set<String> ops = textSet(caseSchema.path("properties").path("steps").path("items")
                .path("properties").path("op").path("enum"));
        Set<String> expectedKeys = fieldNames(
                caseSchema.path("properties").path("expected").path("properties"));
        Set<String> caseKeys = fieldNames(caseSchema.path("properties"));
        Pattern idPattern = Pattern.compile(
                caseSchema.path("properties").path("id").path("pattern").asText());
        Set<String> ids = new HashSet<>();
        int total = 0;
        for (String name : DIGESTS.keySet()) {
            JsonNode fixture = load(name);
            assertEquals(fieldNames(properties), fieldNames(fixture), name);
            assertEquals(properties.path("schema").path("const"), fixture.path("schema"), name);
            assertEquals(properties.path("protocol").path("const"), fixture.path("protocol"), name);
            assertTrue(allowedValues(properties.path("knowledge_contract").path("properties")
                    .path("commit")).contains(fixture.path("knowledge_contract").path("commit")), name);
            assertTrue(suites.contains(fixture.path("suite").asText()), name);
            for (JsonNode testCase : fixture.path("cases")) {
                String id = testCase.path("id").asText();
                assertTrue(idPattern.matcher(id).matches(), id);
                assertTrue(ids.add(id), "duplicate case id " + id);
                assertTrue(caseKeys.containsAll(fieldNames(testCase)), id);
                for (String required : textSet(caseSchema.path("required"))) {
                    assertTrue(testCase.has(required), id + " lacks " + required);
                }
                assertTrue(expectedKeys.containsAll(fieldNames(testCase.path("expected"))), id);
                for (JsonNode step : testCase.path("steps")) {
                    assertTrue(ops.contains(step.path("op").asText()), id);
                }
                total++;
            }
        }
        assertEquals(45, total);
    }

    @Test
    void blockValueReferencesResolve() throws Exception {
        JsonNode blocks = load("client-bootstrap-blocks-v23.2.1.json");
        JsonNode values = load(BLOCK_VALUES);
        for (JsonNode testCase : blocks.path("cases")) {
            for (JsonNode reference : testCase.path("value_refs")) {
                assertEquals(BLOCK_VALUES, reference.path("file").asText());
                JsonNode target = values.at(reference.path("pointer").asText());
                assertFalse(target.isMissingNode(), testCase.path("id").asText());
                checkBlockValueAgainstCatalog(target, testCase.path("expected").path("result"),
                        testCase.path("id").asText());
            }
        }
    }

    @TestFactory
    Stream<DynamicNode> sharedClientBootstrapCases() throws IOException {
        List<DynamicNode> files = new ArrayList<>();
        for (String name : DIGESTS.keySet()) {
            JsonNode fixture = load(name);
            String suite = fixture.path("suite").asText();
            List<DynamicTest> tests = new ArrayList<>();
            for (JsonNode testCase : fixture.path("cases")) {
                tests.add(DynamicTest.dynamicTest(
                        testCase.path("id").asText() + " " + testCase.path("title").asText(),
                        () -> runCase(suite, testCase)));
            }
            files.add(DynamicContainer.dynamicContainer(name, tests));
        }
        return files.stream();
    }

    private static void runCase(String suite, JsonNode testCase) throws Exception {
        JsonNode steps = testCase.path("steps");
        if (allOps(steps, "peer_bytes")) {
            runDecoderCase(testCase);
        } else if (allOps(steps, "connect_error")) {
            runConnectErrorCase(testCase);
        } else {
            runDialogCase(suite, testCase);
        }
    }

    private static void runDecoderCase(JsonNode testCase) {
        FrameDecoder decoder = new FrameDecoder();
        ArrayNode frames = MAPPER.createArrayNode();
        for (JsonNode step : testCase.path("steps")) {
            decoder.feed(HexFormat.of().parseHex(step.path("bytes_hex").asText()))
                    .forEach(frames::add);
        }
        JsonNode expected = testCase.path("expected");
        assertKeys(expected, "frames", "tail_hex");
        assertJson(expected.path("frames"), frames, "frames");
        assertEquals(expected.path("tail_hex").asText(), HexFormat.of().formatHex(decoder.tail()));
    }

    private static void runConnectErrorCase(JsonNode testCase) throws Exception {
        int port;
        try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = closed.getLocalPort();
        }
        List<JsonRpcConnection> connections = new ArrayList<>();
        MinecraftRemoteException failure = null;
        try (MinecraftRemote ignored = MinecraftRemote.builder("127.0.0.1", port)
                .connectTimeout(Duration.ofSeconds(2))
                .connectionObserver(connections::add)
                .connect()) {
            fail("connection to a closed port succeeded");
        } catch (MinecraftRemoteException exception) {
            failure = exception;
        }
        assertInstanceOf(TransportException.class, failure);
        JsonNode expected = testCase.path("expected");
        assertKeys(expected, "connection", "completed_ids");
        assertEquals("failed", expected.path("connection").asText());
        assertTrue(connections.isEmpty());
        assertJson(expected.path("completed_ids"), MAPPER.createArrayNode(), "completed_ids");
    }

    private static void runDialogCase(String suite, JsonNode testCase) throws Exception {
        JsonNode steps = testCase.path("steps");
        boolean raw = !typedFlowReproducible(steps);
        AtomicLong clock = new AtomicLong();
        Dialog dialog = new Dialog();
        try (BootstrapFixturePeer peer = new BootstrapFixturePeer(steps, clock)) {
            try {
                drive(steps, raw, peer, clock, dialog);
                peer.await();
                if (raw) {
                    dialog.awaitRaw();
                }
                check(suite, testCase.path("expected"), dialog);
            } finally {
                dialog.close();
            }
        }
    }

    private static void drive(
            JsonNode steps, boolean raw, BootstrapFixturePeer peer, AtomicLong clock, Dialog dialog)
            throws Exception {
        int last = -1;
        while (true) {
            int turn = peer.awaitTurn(last);
            if (turn < 0) {
                return;
            }
            JsonNode step = steps.get(turn);
            switch (step.path("op").asText()) {
                case "connect" -> {
                    if (raw) {
                        dialog.openRaw(peer.port());
                    } else {
                        JsonNode hello = nextClientMessage(steps, turn);
                        assertEquals("hello", hello.path("method").asText(),
                                "typed connections start with hello");
                        dialog.connectTyped(peer.port(), hello.path("params"), steps, clock);
                    }
                }
                case "client_send" -> {
                    JsonNode message = step.path("message");
                    if (raw) {
                        dialog.sendRaw(message);
                    } else {
                        dialog.callTyped(message);
                    }
                }
                case "client_close" -> dialog.closeCurrent();
                default -> throw new AssertionError("unexpected client turn " + step);
            }
            last = turn;
        }
    }

    private static void check(String suite, JsonNode expected, Dialog dialog) throws Exception {
        JsonRpcConnection connection = dialog.lastConnection();
        Iterator<String> keys = expected.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            JsonNode value = expected.get(key);
            switch (key) {
                case "compatible" -> assertEquals(value.asBoolean(), dialog.helloAccepted,
                        "compatible; errors=" + dialog.errors);
                case "reason" -> assertEquals(value.asText(), dialog.lastReason(),
                        "reason; errors=" + dialog.errors);
                case "result" -> assertJson(value, "blocks".equals(suite)
                        ? blockJson(dialog.lastBlock) : helloJson(dialog.lastHello), "result");
                case "error" -> assertJson(value, errorJson(dialog.lastRemoteError()), "error");
                case "token" -> assertEquals(Optional.of(value.asText()),
                        dialog.store.load(dialog.target()), "token");
                case "discard_token" -> {
                    Optional<String> token = dialog.store.load(dialog.target());
                    if (value.asBoolean()) {
                        assertTrue(token.isEmpty(), "token should be discarded");
                    } else {
                        assertEquals(Optional.ofNullable(dialog.initialToken), token,
                                "token should be kept");
                    }
                }
                case "pairing_required" -> assertEquals(value.asBoolean(),
                        dialog.pairingPrompts > 0 || dialog.errors.stream()
                                .anyMatch(PairingRequiredException.class::isInstance),
                        "pairing_required");
                case "connection" -> {
                    assertNotNull(connection, "no connection was opened");
                    boolean closed = connection.pollClosed(Duration.ofMillis(100));
                    assertEquals(value.asText(), closed ? "closed" : "open", "connection");
                }
                case "pending_ids" -> assertJson(value, ids(connection.pendingIds()), key);
                case "unanswered_ids" -> assertJson(value, ids(connection.unansweredIds()), key);
                case "completed_ids" -> assertJson(value, ids(connection.completedIds()), key);
                case "matched_ids" -> assertJson(value, ids(connection.completedIds().stream()
                        .filter(id -> !"hello".equals(connection.methodOf(id))).toList()), key);
                case "ignored_ids" -> assertJson(value,
                        MAPPER.valueToTree(connection.ignoredIds()), key);
                default -> fail("unhandled expected key " + key);
            }
        }
    }

    /** Whether the hello and pairing messages are exactly what the typed connect flow sends. */
    private static boolean typedFlowReproducible(JsonNode steps) {
        JsonNode helloClient = null;
        boolean sawHello = false;
        String issuedPairingId = null;
        String lastMethod = null;
        boolean connectionStarted = false;
        for (JsonNode step : steps) {
            if ("connect".equals(step.path("op").asText())) {
                connectionStarted = true;
            }
            if ("peer_send".equals(step.path("op").asText())
                    && "auth.pairBegin".equals(lastMethod)) {
                issuedPairingId = step.path("message").path("result").path("pairing_id").asText();
            }
            if (!"client_send".equals(step.path("op").asText())) {
                continue;
            }
            JsonNode message = step.path("message");
            JsonNode params = message.path("params");
            lastMethod = message.path("method").asText();
            if (connectionStarted && !"hello".equals(lastMethod) && !"auth.pairBegin".equals(lastMethod)
                    && !sawHello) {
                // A connection that begins without hello is an envelope-only replay.
                return false;
            }
            connectionStarted = false;
            switch (lastMethod) {
                case "hello" -> {
                    if (!Set.of("protocol", "client", "auth", "build")
                            .containsAll(fieldNames(params))
                            || (params.has("auth") && !Set.of("token")
                                    .equals(fieldNames(params.path("auth"))))) {
                        return false;
                    }
                    sawHello = true;
                    helloClient = params.get("client");
                }
                case "auth.pairBegin" -> {
                    if (!sawHello
                            || !Set.of("session", "long_lived")
                                    .contains(params.path("token_type").asText())
                            || helloClient == null
                            || !helloClient.equals(params.path("client"))) {
                        return false;
                    }
                }
                case "auth.pairPoll" -> {
                    if (!params.path("pairing_id").asText().equals(issuedPairingId)) {
                        return false;
                    }
                }
                default -> {
                    // Ordinary methods are dispatched one message at a time in callTyped.
                }
            }
        }
        return true;
    }

    private static JsonNode nextClientMessage(JsonNode steps, int from) {
        for (int index = from + 1; index < steps.size(); index++) {
            if ("client_send".equals(steps.get(index).path("op").asText())) {
                return steps.get(index).path("message");
            }
        }
        throw new AssertionError("connect without a following client message");
    }

    /** Client-side state and observations for one dialog case. */
    private static final class Dialog implements AutoCloseable {
        final CredentialStore store = CredentialStore.inMemory();
        final List<MinecraftRemoteException> errors = new ArrayList<>();
        final List<JsonRpcConnection> connections = new ArrayList<>();
        final List<MinecraftRemote> remotes = new ArrayList<>();
        final List<long[]> rawSent = new ArrayList<>();
        ServerTarget target;
        String initialToken;
        boolean tokenSeeded;
        MinecraftRemote remote;
        JsonRpcConnection raw;
        HelloResult lastHello;
        BlockValue lastBlock;
        boolean helloAccepted;
        int pairingPrompts;

        ServerTarget target() {
            return target;
        }

        void connectTyped(int port, JsonNode hello, JsonNode steps, AtomicLong clock)
                throws Exception {
            target = new ServerTarget("127.0.0.1", port);
            if (!tokenSeeded) {
                tokenSeeded = true;
                if (hello.has("auth")) {
                    initialToken = hello.path("auth").path("token").asText();
                    store.save(target, initialToken);
                }
            }
            MinecraftRemote.Builder builder = MinecraftRemote.builder("127.0.0.1", port)
                    .connectTimeout(Duration.ofSeconds(2))
                    .readTimeout(Duration.ofSeconds(2))
                    .pairPollInterval(Duration.ofMillis(1))
                    .credentialStore(store)
                    .protocol(hello.path("protocol").asText())
                    .nanoClock(clock::get)
                    .connectionObserver(connections::add);
            if (hello.has("client")) {
                JsonNode client = hello.path("client");
                builder.clientIdentity(new ClientIdentity(client.path("name").asText(),
                        client.path("version").asText(), client.path("locale").asText()));
            }
            configureBuild(builder, hello);
            JsonNode pairBegin = firstParams(steps, "auth.pairBegin");
            if (pairBegin != null) {
                builder.tokenType("long_lived".equals(pairBegin.path("token_type").asText())
                        ? TokenType.LONG_LIVED : TokenType.SESSION);
                builder.pairingListener(challenge -> pairingPrompts++);
            }
            remote = null;
            try {
                remote = builder.connect();
                remotes.add(remote);
                lastHello = remote.helloResult();
                helloAccepted = true;
            } catch (MinecraftRemoteException exception) {
                helloAccepted = false;
                errors.add(exception);
            }
        }

        void callTyped(JsonNode message) throws Exception {
            assertNotNull(remote, "no open client for " + message);
            String method = message.path("method").asText();
            JsonNode params = message.path("params");
            try {
                switch (method) {
                    case "world.setBlock" -> {
                        Optional<BlockSpec> block = typedBlock(params);
                        if (block.isPresent()) {
                            remote.setBlock(params.get(0).intValue(), params.get(1).intValue(),
                                    params.get(2).intValue(), block.get());
                        } else {
                            remote.request(method, params);
                        }
                    }
                    case "world.getBlock" -> lastBlock = remote.getBlock(
                            params.get(0).intValue(), params.get(1).intValue(),
                            params.get(2).intValue());
                    case "chat.post" -> remote.postToChat(params.get(0).textValue());
                    default -> remote.request(method, params);
                }
            } catch (MinecraftRemoteException exception) {
                errors.add(exception);
            }
        }

        void openRaw(int port) throws Exception {
            target = new ServerTarget("127.0.0.1", port);
            raw = new JsonRpcConnection(target, Duration.ofSeconds(2), Duration.ofSeconds(2));
            connections.add(raw);
        }

        void sendRaw(JsonNode message) throws Exception {
            long id = raw.send(message.path("method").asText(), message.get("params"));
            rawSent.add(new long[] {connections.indexOf(raw), id});
        }

        void awaitRaw() {
            for (long[] sent : rawSent) {
                try {
                    connections.get((int) sent[0]).await(sent[1]);
                } catch (MinecraftRemoteException exception) {
                    errors.add(exception);
                }
            }
        }

        void closeCurrent() {
            if (raw != null) {
                raw.close();
            } else {
                assertNotNull(remote, "client_close without an open client");
                remote.close();
            }
        }

        JsonRpcConnection lastConnection() {
            return connections.isEmpty() ? null : connections.get(connections.size() - 1);
        }

        String lastReason() {
            if (errors.isEmpty()) {
                return null;
            }
            MinecraftRemoteException last = errors.get(errors.size() - 1);
            return reasonOf(last).orElse("<no reason: " + last + ">");
        }

        RemoteCallException lastRemoteError() {
            for (int index = errors.size() - 1; index >= 0; index--) {
                if (errors.get(index) instanceof RemoteCallException remoteError) {
                    return remoteError;
                }
            }
            throw new AssertionError("no remote error; errors=" + errors);
        }

        @Override
        public void close() {
            remotes.forEach(MinecraftRemote::close);
            connections.forEach(JsonRpcConnection::close);
        }
    }

    static Optional<String> reasonOf(MinecraftRemoteException exception) {
        if (exception instanceof RemoteCallException remoteError) {
            return remoteError.reason();
        }
        if (exception instanceof PairingRequiredException pairing) {
            return Optional.of(pairing.reason());
        }
        if (exception instanceof ProtocolMismatchException mismatch) {
            return Optional.of(mismatch.reason());
        }
        return Optional.empty();
    }

    private static void configureBuild(MinecraftRemote.Builder builder, JsonNode hello) {
        if (hello.has("build")) {
            JsonNode build = hello.path("build");
            JsonNode origin = build.path("origin");
            builder.buildContext(build.path("dimension").asText(),
                    origin.get(0).intValue(), origin.get(1).intValue(), origin.get(2).intValue());
        }
    }

    private static Optional<BlockSpec> typedBlock(JsonNode params) {
        if (!params.isArray() || params.size() != 4) {
            return Optional.empty();
        }
        for (int index = 0; index < 3; index++) {
            if (!params.get(index).isInt()) {
                return Optional.empty();
            }
        }
        JsonNode block = params.get(3);
        if (!block.isObject() || !Set.of("block_id", "state").equals(fieldNames(block))
                || !block.path("block_id").isTextual() || !block.path("state").isObject()) {
            return Optional.empty();
        }
        Map<String, Object> state = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = block.path("state").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (value.isTextual()) {
                state.put(field.getKey(), value.textValue());
            } else if (value.isBoolean()) {
                state.put(field.getKey(), value.booleanValue());
            } else if (value.isIntegralNumber()) {
                state.put(field.getKey(), value.longValue());
            } else if (value.isNumber()) {
                state.put(field.getKey(), value.doubleValue());
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(BlockSpec.of(block.path("block_id").textValue(), state));
    }

    private static JsonNode firstParams(JsonNode steps, String method) {
        for (JsonNode step : steps) {
            if ("client_send".equals(step.path("op").asText())
                    && method.equals(step.path("message").path("method").asText())) {
                return step.path("message").path("params");
            }
        }
        return null;
    }

    private static void checkBlockValueAgainstCatalog(JsonNode catalog, JsonNode value, String id) {
        String blockId = value.path("block_id").asText();
        JsonNode entry = catalog.path(blockId);
        assertFalse(entry.isMissingNode(), id + " result block is outside the fixture catalog");
        assertEquals(fieldNames(entry.path("states")), fieldNames(value.path("state")),
                id + " result must carry the full state");
        Iterator<Map.Entry<String, JsonNode>> fields = value.path("state").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            boolean allowed = false;
            for (JsonNode choice : entry.path("states").path(field.getKey())) {
                allowed |= BootstrapFixturePeer.jsonEquals(choice, field.getValue());
            }
            assertTrue(allowed, id + " state value outside the fixture catalog: " + field);
        }
    }

    private static JsonNode helloJson(HelloResult hello) {
        assertNotNull(hello, "no hello result");
        ObjectNode node = MAPPER.createObjectNode();
        node.put("protocol", hello.protocol());
        node.put("mc_version", hello.minecraftVersion());
        node.set("supported_mc_versions", MAPPER.valueToTree(hello.supportedMinecraftVersions()));
        node.set("catalogHash", hello.catalogHash()
                .<JsonNode>map(JsonNodeFactory.instance::textNode)
                .orElse(JsonNodeFactory.instance.nullNode()));
        node.set("world_constants", MAPPER.valueToTree(hello.worldConstants()));
        hello.session().ifPresent(value -> node.put("session", value));
        hello.player().ifPresent(value -> node.put("player", value));
        node.put("dimension", hello.dimension());
        node.set("origin", MAPPER.valueToTree(
                List.of(hello.origin().x(), hello.origin().y(), hello.origin().z())));
        if (!hello.permissions().isEmpty()) {
            node.set("permissions", MAPPER.valueToTree(hello.permissions()));
        }
        hello.server().ifPresent(value -> node.put("server", value));
        return node;
    }

    private static JsonNode blockJson(BlockValue block) {
        assertNotNull(block, "no block value");
        ObjectNode node = MAPPER.createObjectNode();
        node.put("block_id", block.blockId());
        node.set("state", MAPPER.valueToTree(block.state()));
        return node;
    }

    private static JsonNode errorJson(RemoteCallException error) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("code", error.code());
        node.put("message", error.remoteMessage());
        node.set("data", MAPPER.valueToTree(error.data()));
        return node;
    }

    private static JsonNode ids(List<Long> ids) {
        return MAPPER.valueToTree(ids);
    }

    private static void assertJson(JsonNode expected, JsonNode actual, String what) {
        assertTrue(BootstrapFixturePeer.jsonEquals(expected, actual),
                what + "\n expected: " + expected + "\n actual:   " + actual);
    }

    private static void assertKeys(JsonNode expected, String... keys) {
        assertEquals(Set.of(keys), fieldNames(expected), "expected keys");
    }

    private static boolean allOps(JsonNode steps, String op) {
        for (JsonNode step : steps) {
            if (!op.equals(step.path("op").asText())) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    /** Returns the values a schema property admits through {@code const} or {@code enum}. */
    private static List<JsonNode> allowedValues(JsonNode property) {
        List<JsonNode> values = new ArrayList<>();
        if (property.has("const")) {
            values.add(property.get("const"));
        }
        property.path("enum").forEach(values::add);
        assertFalse(values.isEmpty(), "schema property admits no value");
        return values;
    }

    private static Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static JsonNode load(String name) throws IOException {
        return MAPPER.readTree(bytes(name));
    }

    private static byte[] bytes(String name) throws IOException {
        try (InputStream input = ClientBootstrapFixtureTest.class.getResourceAsStream(DIR + name)) {
            if (input == null) {
                throw new IOException("missing fixture " + name);
            }
            return input.readAllBytes();
        }
    }

    private static String sha256(String name) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(name)));
    }
}
