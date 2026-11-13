package club.code2create.mcremote.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the F1 v2 operation cases (waiting, reconnecting, local failures) against the Java client.
 *
 * <p>Each {@code app_connect}/{@code app_call} starts on its own thread and is not awaited; the
 * runner then lets the client settle before the next step. The client uses a virtual clock, so
 * {@code advance_clock} moves its time without sleeping. {@code socket_error} and
 * {@code liveness_lost} reset the peer's socket: when TCP keepalive finds the peer gone, the Java
 * client's read fails the same way. A connection whose next step is {@code connect_error} is
 * refused by closing the listening socket before the call that would open it.
 *
 * <p>Cases that need features the Java client does not have yet are reported as todo (skipped),
 * never as passed; see {@link #TODO}.
 */
class ClientBootstrapOperationsFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DIR = "/protocol-fixtures/";
    private static final String OPERATIONS = "client-bootstrap-operations-v23.2.1.json";
    private static final String OPERATIONS_DIGEST =
            "8309f5847ad69aa2d82fcbcb1726918c3a69ba875e04e8f38678352b06b7f7a1";
    private static final String SCHEMA = "client-bootstrap-v2.schema.json";
    private static final String SCHEMA_DIGEST =
            "8ae1c7313d01fa0fa24f4f2079ba453129dc16b678342df6f7ef66b9255d1381";
    private static final long WAIT_MILLIS = 5_000;

    /** Cases that need a feature the Java client does not have; not counted as passed. */
    static final Map<String, String> TODO = Map.of(
            "F1-OP-06", "FAST mode and connection.flush are not in the Java client yet",
            "F1-OP-11", "entity handles, epoch-scoped event cursors, and a catalog cache are not in"
                    + " the Java client yet, so seed_epoch_state has nothing to seed");

    @Test
    void pinnedFixtureBytesMatch() throws Exception {
        assertEquals(OPERATIONS_DIGEST, sha256(OPERATIONS));
        assertEquals(SCHEMA_DIGEST, sha256(SCHEMA));
    }

    @Test
    void fixtureFollowsTheSchemaShape() throws Exception {
        JsonNode schema = load(SCHEMA);
        JsonNode fixture = load(OPERATIONS);
        JsonNode properties = schema.path("properties");
        assertEquals(fieldNames(properties), fieldNames(fixture));
        assertEquals(properties.path("schema").path("const"), fixture.path("schema"));
        assertEquals(properties.path("protocol").path("const"), fixture.path("protocol"));
        assertEquals(properties.path("suite").path("const"), fixture.path("suite"));
        JsonNode contract = properties.path("knowledge_contract").path("properties");
        assertTrue(textSet(contract.path("commit").path("enum"))
                .contains(fixture.path("knowledge_contract").path("commit").asText()));
        assertEquals(contract.path("instructions").path("const"),
                fixture.path("knowledge_contract").path("instructions"));
        JsonNode caseSchema = properties.path("cases").path("items");
        Set<String> ops = new HashSet<>();
        caseSchema.path("properties").path("steps").path("items").path("oneOf")
                .forEach(option -> ops.add(option.path("properties").path("op").path("const").asText()));
        Set<String> expectedKeys =
                fieldNames(schema.path("definitions").path("expected").path("properties"));
        Set<String> ids = new HashSet<>();
        for (JsonNode testCase : fixture.path("cases")) {
            String id = testCase.path("id").asText();
            assertTrue(id.matches(caseSchema.path("properties").path("id").path("pattern").asText()), id);
            assertTrue(ids.add(id), "duplicate case id " + id);
            assertEquals(textSet(caseSchema.path("required")), fieldNames(testCase), id);
            assertTrue(expectedKeys.containsAll(fieldNames(testCase.path("expected"))), id);
            for (JsonNode step : testCase.path("steps")) {
                assertTrue(ops.contains(step.path("op").asText()), id);
                if (step.has("expected")) {
                    assertTrue(expectedKeys.containsAll(fieldNames(step.path("expected"))), id);
                }
            }
        }
        assertEquals(12, ids.size());
    }

    @Test
    void todoCasesAreExactlyTheOnesNeedingMissingFeatures() throws Exception {
        Set<String> needing = new HashSet<>();
        for (JsonNode testCase : load(OPERATIONS).path("cases")) {
            for (JsonNode step : testCase.path("steps")) {
                boolean fast = "FAST".equals(step.path("mode").asText());
                boolean flush = "connection.flush".equals(step.path("method").asText());
                boolean epoch = "seed_epoch_state".equals(step.path("op").asText());
                if (fast || flush || epoch) {
                    needing.add(testCase.path("id").asText());
                }
            }
        }
        assertEquals(TODO.keySet(), needing);
    }

    @TestFactory
    Stream<DynamicTest> operationCases() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode testCase : load(OPERATIONS).path("cases")) {
            String id = testCase.path("id").asText();
            tests.add(DynamicTest.dynamicTest(id + " " + testCase.path("title").asText(), () -> {
                Assumptions.assumeFalse(TODO.containsKey(id), () -> "todo: " + TODO.get(id));
                try (Run run = new Run(testCase)) {
                    run.execute();
                }
            }));
        }
        return tests.stream();
    }

    /** One application call started by the runner. */
    private static final class Call {
        final Thread thread;
        final boolean bootstrap;
        volatile boolean succeeded;
        volatile Throwable failure;
        volatile JsonRpcConnection used;

        Call(Thread thread, boolean bootstrap) {
            this.thread = thread;
            this.bootstrap = bootstrap;
        }
    }

    /** One server-side socket. */
    private static final class Peer {
        final Socket socket;
        final InputStream input;
        final OutputStream output;
        int framesSent;

        Peer(Socket socket) throws IOException {
            this.socket = socket;
            socket.setSoTimeout((int) WAIT_MILLIS);
            input = new BufferedInputStream(socket.getInputStream());
            output = socket.getOutputStream();
        }
    }

    private record Notice(String kind, String connection, String previous) {}

    /** Executes one case. */
    private static final class Run implements AutoCloseable {
        private final JsonNode testCase;
        private final JsonNode steps;
        private final ServerSocket server;
        private final VirtualTicker ticker = new VirtualTicker();
        private final CredentialStore store = CredentialStore.inMemory();
        private final Map<String, Call> calls = new LinkedHashMap<>();
        private final Map<String, Peer> peers = new LinkedHashMap<>();
        private final List<String> accepted = new ArrayList<>();
        private final Set<String> refused = new HashSet<>();
        private final List<JsonRpcConnection> opened = new CopyOnWriteArrayList<>();
        private final Map<Thread, JsonRpcConnection> lastUsed = new ConcurrentHashMap<>();
        private final List<Notice> notices = new CopyOnWriteArrayList<>();
        private final MinecraftRemote.Builder builder;
        private volatile MinecraftRemote remote;
        private volatile JsonRpcConnection established;
        private long pairingTtlNanos;

        Run(JsonNode testCase) throws IOException, MinecraftRemoteException {
            this.testCase = testCase;
            steps = testCase.path("steps");
            server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
            server.setSoTimeout((int) WAIT_MILLIS);
            findRefused();
            JsonNode hello = firstHello();
            ServerTarget target = new ServerTarget("127.0.0.1", server.getLocalPort());
            if (hello.has("auth")) {
                store.save(target, hello.path("auth").path("token").asText());
            }
            JsonNode client = hello.path("client");
            JsonNode build = hello.path("build");
            JsonNode origin = build.path("origin");
            builder = MinecraftRemote.builder(target.host(), target.port())
                    .connectTimeout(Duration.ofSeconds(2))
                    .credentialStore(store)
                    .protocol(hello.path("protocol").asText())
                    .clientIdentity(new ClientIdentity(client.path("name").asText(),
                            client.path("version").asText(), client.path("locale").asText()))
                    .buildContext(build.path("dimension").asText(), origin.get(0).intValue(),
                            origin.get(1).intValue(), origin.get(2).intValue())
                    .tokenType(TokenType.SESSION)
                    .pairingListener(challenge -> {})
                    .pairPollInterval(Duration.ofSeconds(1))
                    .ticker(ticker)
                    .random(() -> 0.5)
                    .connectionObserver(opened::add)
                    .requestObserver((connection, id) -> lastUsed.put(Thread.currentThread(), connection))
                    .reconnectListener(this::reconnected);
        }

        void execute() throws Exception {
            for (int index = 0; index < steps.size(); index++) {
                JsonNode step = steps.get(index);
                String op = step.path("op").asText();
                String name = step.path("connection").asText(null);
                switch (op) {
                    case "app_connect" -> {
                        refuseAhead(index);
                        start(step.path("call").asText(), true, () -> {
                            remote = builder.connect();
                            established = remote.currentConnection();
                        });
                    }
                    case "app_call" -> {
                        refuseAhead(index);
                        start(step.path("call").asText(), false, () -> call(step));
                    }
                    case "expect_connect" -> {
                        if (!refused.contains(name)) {
                            Socket socket = server.accept();
                            peers.put(name, new Peer(socket));
                            accepted.add(name);
                        }
                    }
                    case "expect_send" -> {
                        JsonNode actual = MAPPER.readTree(readLine(peer(name), index));
                        JsonNode expected = step.path("message");
                        assertTrue(BootstrapFixturePeer.jsonEquals(expected, actual), "step " + index
                                + " expect_send differs\n expected: " + expected
                                + "\n actual:   " + actual);
                    }
                    case "peer_send" -> {
                        JsonNode message = step.path("message");
                        if (message.path("result").has("expires_in")) {
                            pairingTtlNanos = Duration.ofSeconds(
                                    message.path("result").path("expires_in").asLong()).toNanos();
                        }
                        Peer peer = peer(name);
                        ByteArrayOutputStream frame = new ByteArrayOutputStream();
                        frame.writeBytes(MAPPER.writeValueAsBytes(message));
                        frame.write('\n');
                        peer.output.write(frame.toByteArray());
                        peer.output.flush();
                        peer.framesSent++;
                        awaitReceived(name, peer.framesSent);
                        settle();
                    }
                    case "peer_eof" -> {
                        try {
                            peer(name).socket.shutdownOutput();
                        } catch (IOException alreadyClosed) {
                            // The client closed first; the peer's EOF changes nothing.
                        }
                        awaitClosed(name);
                        settle();
                    }
                    case "socket_error", "liveness_lost" -> {
                        Socket socket = peer(name).socket;
                        socket.setSoLinger(true, 0);
                        socket.close();
                        awaitClosed(name);
                        settle();
                    }
                    case "connect_error" -> {
                        assertTrue(refused.contains(name), "step " + index);
                        // The reconnect policy waits on the virtual clock; run it to its end.
                        for (int turn = 0; turn < 10_000 && anyRunning(); turn++) {
                            ticker.advance(Duration.ofSeconds(10).toNanos());
                            Thread.sleep(1);
                        }
                        assertFalse(anyRunning(), "the reconnect policy did not give up");
                        settle();
                    }
                    case "advance_clock" -> {
                        long timer = switch (step.path("timer").asText()) {
                            case "receive_idle" -> MinecraftRemote.DEFAULT_RESPONSE_TIMEOUT.toNanos();
                            case "pairing_ttl" -> pairingTtlNanos;
                            default -> throw new AssertionError("unknown timer " + step);
                        };
                        assertTrue(timer > 0, "timer length unknown at step " + index);
                        JsonNode fraction = step.path("fraction");
                        ticker.advance(Math.multiplyExact(timer, fraction.path("numerator").asLong())
                                / fraction.path("denominator").asLong());
                        MinecraftRemote current = remote;
                        if (current != null) {
                            current.checkTimers();
                        }
                        settle();
                    }
                    case "observe" -> assertEventually(step.path("expected"), "step " + index);
                    default -> fail("unsupported op " + op + " at step " + index);
                }
            }
            settle();
            assertEventually(testCase.path("expected"), "final");
        }

        private void call(JsonNode step) throws MinecraftRemoteException {
            assertEquals("DEBUG", step.path("mode").asText(), "FAST is not in the Java client");
            JsonNode params = step.path("params");
            MinecraftRemote client = remote;
            switch (step.path("method").asText()) {
                case "world.getBlock" -> client.getBlock(
                        params.get(0).intValue(), params.get(1).intValue(), params.get(2).intValue());
                case "world.setBlock" -> {
                    JsonNode block = params.get(3);
                    assertTrue(block.path("state").isEmpty(), "only stateless blocks are mapped");
                    client.setBlock(params.get(0).intValue(), params.get(1).intValue(),
                            params.get(2).intValue(), BlockSpec.of(block.path("block_id").asText()));
                }
                case "build.setDimension" -> client.setDimension(params.get(0).asText());
                case "build.setOrigin" -> client.setOrigin(
                        params.get(0).intValue(), params.get(1).intValue(), params.get(2).intValue());
                default -> throw new AssertionError("unmapped method " + step);
            }
        }

        private void start(String id, boolean bootstrap, ThrowingRunnable body)
                throws InterruptedException {
            Call[] holder = new Call[1];
            Thread thread = new Thread(() -> {
                Call call = holder[0];
                try {
                    body.run();
                    call.succeeded = true;
                } catch (Throwable throwable) {
                    call.failure = throwable;
                } finally {
                    call.used = lastUsed.get(Thread.currentThread());
                }
            }, "fixture-" + id);
            thread.setDaemon(true);
            holder[0] = new Call(thread, bootstrap);
            calls.put(id, holder[0]);
            thread.start();
            settle();
        }

        private void reconnected(ReconnectEvent event) {
            String previous = nameOf(established);
            JsonRpcConnection current = remote.currentConnection();
            String now = nameOf(current);
            established = current;
            notices.add(new Notice("reconnected", now, previous));
            if (event.eventsMayHaveBeenLost()) {
                notices.add(new Notice("events_lost", now, previous));
            }
            if (event.serverChanged()) {
                notices.add(new Notice("server_changed", now, previous));
            }
            if (event.sessionChanged()) {
                notices.add(new Notice("session_changed", now, previous));
            }
        }

        /** Connections whose first step after expect_connect is connect_error. */
        private void findRefused() {
            for (int index = 0; index < steps.size(); index++) {
                JsonNode step = steps.get(index);
                if (!"expect_connect".equals(step.path("op").asText())) {
                    continue;
                }
                String name = step.path("connection").asText();
                for (int next = index + 1; next < steps.size(); next++) {
                    JsonNode later = steps.get(next);
                    if (name.equals(later.path("connection").asText(null))) {
                        if ("connect_error".equals(later.path("op").asText())) {
                            refused.add(name);
                        }
                        break;
                    }
                }
            }
        }

        /** Closes the listener before the input whose connection the peer must refuse. */
        private void refuseAhead(int index) throws IOException {
            for (int next = index + 1; next < steps.size(); next++) {
                JsonNode later = steps.get(next);
                if ("expect_connect".equals(later.path("op").asText())) {
                    if (refused.contains(later.path("connection").asText())) {
                        server.close();
                    }
                    return;
                }
                if (later.path("op").asText().startsWith("app_")) {
                    return;
                }
            }
        }

        private JsonNode firstHello() {
            for (JsonNode step : steps) {
                if ("expect_send".equals(step.path("op").asText())
                        && "hello".equals(step.path("message").path("method").asText())) {
                    return step.path("message").path("params");
                }
            }
            throw new AssertionError("case without hello");
        }

        private Peer peer(String name) {
            Peer peer = peers.get(name);
            assertTrue(peer != null, "no accepted connection " + name);
            return peer;
        }

        private JsonRpcConnection clientConnection(String name) throws InterruptedException {
            int index = accepted.indexOf(name);
            assertTrue(index >= 0, "no accepted connection " + name);
            long deadline = System.nanoTime() + Duration.ofMillis(WAIT_MILLIS).toNanos();
            while (opened.size() <= index && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            return opened.get(index);
        }

        private String nameOf(JsonRpcConnection connection) {
            int index = opened.indexOf(connection);
            return index >= 0 && index < accepted.size() ? accepted.get(index) : "<unnamed>";
        }

        private void awaitReceived(String name, int frames) throws InterruptedException {
            JsonRpcConnection connection = clientConnection(name);
            long deadline = System.nanoTime() + Duration.ofMillis(WAIT_MILLIS).toNanos();
            while (connection.framesReceived() < frames && connection.isOpen()
                    && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
        }

        private void awaitClosed(String name) throws InterruptedException {
            clientConnection(name).pollClosed(Duration.ofMillis(WAIT_MILLIS));
        }

        private boolean anyRunning() {
            return calls.values().stream().anyMatch(call -> call.thread.isAlive());
        }

        /** Waits until every running call is parked: waiting for a response, a lock, or the clock. */
        private void settle() throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofMillis(WAIT_MILLIS).toNanos();
            int quiet = 0;
            while (quiet < 3 && System.nanoTime() < deadline) {
                boolean parked = calls.values().stream().allMatch(call -> switch (call.thread.getState()) {
                    case WAITING, TIMED_WAITING, BLOCKED, TERMINATED -> true;
                    default -> false;
                });
                quiet = parked ? quiet + 1 : 0;
                Thread.sleep(2);
            }
        }

        private void assertEventually(JsonNode expected, String where) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            List<String> mismatches = compare(expected);
            while (!mismatches.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(10);
                mismatches = compare(expected);
            }
            assertTrue(mismatches.isEmpty(), where + ": " + String.join("\n  ", mismatches));
        }

        private List<String> compare(JsonNode expected) throws InterruptedException {
            List<String> mismatches = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                switch (field.getKey()) {
                    case "calls" -> {
                        for (JsonNode row : value) {
                            compareRow("call " + row.path("call").asText(), row,
                                    outcome(row.path("call").asText()), Set.of("call"), mismatches);
                        }
                    }
                    case "connections" -> {
                        for (JsonNode row : value) {
                            compareRow("connection " + row.path("connection").asText(), row,
                                    connectionState(row.path("connection").asText()),
                                    Set.of("connection"), mismatches);
                        }
                    }
                    case "connect_attempts" -> {
                        if (value.asInt() != opened.size()) {
                            mismatches.add("connect_attempts expected " + value + " but was "
                                    + opened.size());
                        }
                    }
                    case "events" -> {
                        List<Notice> unused = new ArrayList<>(notices);
                        for (JsonNode row : value) {
                            Notice wanted = new Notice(row.path("kind").asText(),
                                    row.path("connection").asText(),
                                    row.path("previous_connection").asText());
                            if (!unused.remove(wanted)) {
                                mismatches.add("event " + row + " not among " + notices);
                            }
                        }
                    }
                    case "negotiations" -> {
                        for (JsonNode row : value) {
                            Boolean compatible =
                                    clientConnection(row.path("connection").asText()).compatible();
                            if (compatible == null
                                    || compatible != row.path("compatible").asBoolean()) {
                                mismatches.add("negotiation " + row + " but was " + compatible);
                            }
                        }
                    }
                    default -> mismatches.add("unsupported expected key " + field.getKey());
                }
            }
            return mismatches;
        }

        private ObjectNode outcome(String id) {
            ObjectNode node = MAPPER.createObjectNode();
            Call call = calls.get(id);
            if (call == null) {
                node.put("outcome", "<not started>");
                return node;
            }
            if (call.thread.isAlive()) {
                node.put("outcome", "pending");
                node.put("source", "client");
                return node;
            }
            Throwable failure = call.failure;
            if (call.succeeded) {
                node.put("outcome", "success");
                if (call.bootstrap) {
                    node.put("source", "client");
                } else {
                    node.put("source", "server");
                    node.put("connection", nameOf(call.used));
                }
            } else if (failure instanceof RemoteCallException remoteError) {
                node.put("outcome", "server_error");
                node.put("source", "server");
                ObjectNode error = node.putObject("error");
                error.put("code", remoteError.code());
                error.put("message", remoteError.remoteMessage());
                error.set("data", MAPPER.valueToTree(remoteError.data()));
            } else if (failure instanceof CompletionUnknownException) {
                node.put("outcome", "completion_unknown");
                node.put("source", "client");
            } else if (failure instanceof NotSentException notSent) {
                node.put("outcome", "not_sent");
                node.put("source", "client");
                node.put("cause", notSent.reason().name().toLowerCase(java.util.Locale.ROOT));
            } else if (failure instanceof PairingExpiredException) {
                node.put("outcome", "pairing_expired");
                node.put("source", "client");
                node.put("equivalent_to", PairingExpiredException.REASON);
            } else {
                node.put("outcome", "unexpected " + failure);
            }
            return node;
        }

        private ObjectNode connectionState(String name) throws InterruptedException {
            ObjectNode node = MAPPER.createObjectNode();
            if (refused.contains(name)) {
                // The peer refused it, and the client made no connection object for it.
                node.put("state", "failed");
                node.put("sent_count", 0);
                node.set("pending_ids", MAPPER.createArrayNode());
                return node;
            }
            JsonRpcConnection connection = clientConnection(name);
            node.put("state", connection.isOpen() ? "open" : "closed");
            node.put("sent_count", connection.sentCount());
            node.set("pending_ids", MAPPER.valueToTree(connection.pendingIds()));
            return node;
        }

        private static void compareRow(String what, JsonNode expected, ObjectNode actual,
                Set<String> keys, List<String> mismatches) {
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (keys.contains(field.getKey())) {
                    continue;
                }
                JsonNode got = actual.get(field.getKey());
                boolean same = got != null && ("pending_ids".equals(field.getKey())
                        ? sameSet(field.getValue(), got)
                        : BootstrapFixturePeer.jsonEquals(field.getValue(), got));
                if (!same) {
                    mismatches.add(what + "." + field.getKey() + " expected " + field.getValue()
                            + " but was " + got + " (" + actual + ")");
                }
            }
        }

        private static boolean sameSet(JsonNode expected, JsonNode actual) {
            Set<Long> left = new HashSet<>();
            Set<Long> right = new HashSet<>();
            expected.forEach(value -> left.add(value.asLong()));
            actual.forEach(value -> right.add(value.asLong()));
            return expected.size() == actual.size() && left.equals(right);
        }

        private static String readLine(Peer peer, int index) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (true) {
                int next = peer.input.read();
                if (next == -1) {
                    throw new AssertionError("step " + index + " client closed before sending");
                }
                if (next == '\n') {
                    return line.toString(UTF_8);
                }
                line.write(next);
            }
        }

        @Override
        public void close() throws IOException {
            MinecraftRemote current = remote;
            if (current != null) {
                current.close();
            }
            server.close();
            for (Peer peer : peers.values()) {
                peer.socket.close();
            }
            for (Call call : calls.values()) {
                call.thread.interrupt();
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static JsonNode load(String name) throws IOException {
        try (InputStream input =
                ClientBootstrapOperationsFixtureTest.class.getResourceAsStream(DIR + name)) {
            if (input == null) {
                throw new IOException("missing fixture " + name);
            }
            return MAPPER.readTree(input.readAllBytes());
        }
    }

    private static String sha256(String name) throws Exception {
        try (InputStream input =
                ClientBootstrapOperationsFixtureTest.class.getResourceAsStream(DIR + name)) {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }
}
