package club.code2create.mcremote.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Replays the peer side of one client-bootstrap fixture case over loopback TCP.
 *
 * <p>Peer steps are executed in order. Each {@code client_send} must match the next frame the
 * client sends (JSON value equality: member order ignored, numbers compared by value).
 * {@code connect}, {@code client_send}, and {@code client_close} are client turns; while the peer
 * waits on one of them it publishes the step index so the test can drive the client.
 */
final class BootstrapFixturePeer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TIMEOUT_MILLIS = 5_000;

    private final JsonNode steps;
    private final AtomicLong fixtureNanos;
    private final ServerSocket server;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Future<?> future;

    private int blockedAt = -1;
    private boolean done;
    private Throwable failure;

    BootstrapFixturePeer(JsonNode steps, AtomicLong fixtureNanos) throws IOException {
        this.steps = steps;
        this.fixtureNanos = fixtureNanos;
        server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        server.setSoTimeout((int) TIMEOUT_MILLIS);
        future = executor.submit(this::runScript);
    }

    int port() {
        return server.getLocalPort();
    }

    /**
     * Waits until the peer is blocked on a client turn after {@code lastHandled}, and returns its
     * index, or returns -1 when the script has finished.
     */
    synchronized int awaitTurn(int lastHandled) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
        while (failure == null && !done && blockedAt <= lastHandled) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new AssertionError("fixture peer did not reach the next client turn");
            }
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
        if (failure != null) {
            throw new AssertionError("fixture peer failed", failure);
        }
        return done ? -1 : blockedAt;
    }

    /** Waits for the whole peer script and rethrows its failure. */
    void await() throws Exception {
        try {
            future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        } catch (TimeoutException exception) {
            throw new AssertionError("fixture peer did not complete its script", exception);
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (Socket socket : sockets) {
            socket.close();
        }
        executor.shutdownNow();
    }

    private Void runScript() throws Exception {
        try {
            Socket current = null;
            InputStream input = null;
            OutputStream output = null;
            for (int index = 0; index < steps.size(); index++) {
                JsonNode step = steps.get(index);
                String op = step.path("op").asText();
                switch (op) {
                    case "connect" -> {
                        block(index);
                        current = server.accept();
                        running();
                        current.setSoTimeout((int) TIMEOUT_MILLIS);
                        sockets.add(current);
                        input = new BufferedInputStream(current.getInputStream());
                        output = current.getOutputStream();
                    }
                    case "client_send" -> {
                        block(index);
                        byte[] line = readLine(input, index);
                        running();
                        JsonNode actual = MAPPER.readTree(new String(line, UTF_8));
                        JsonNode expected = step.path("message");
                        if (!jsonEquals(expected, actual)) {
                            throw new AssertionError("step " + index + " client_send differs"
                                    + "\n expected: " + expected + "\n actual:   " + actual);
                        }
                    }
                    case "client_close" -> {
                        block(index);
                        int next;
                        try {
                            next = input.read();
                        } catch (SocketException reset) {
                            next = -1;
                        }
                        running();
                        if (next != -1) {
                            throw new AssertionError(
                                    "step " + index + " expected client close, got more data");
                        }
                    }
                    case "peer_send" -> {
                        output.write(MAPPER.writeValueAsBytes(step.path("message")));
                        output.write('\n');
                        output.flush();
                    }
                    case "peer_bytes" -> {
                        output.write(HexFormat.of().parseHex(step.path("bytes_hex").asText()));
                        output.flush();
                    }
                    case "peer_eof" -> current.shutdownOutput();
                    case "advance_time" -> fixtureNanos.addAndGet(
                            TimeUnit.MILLISECONDS.toNanos(step.path("milliseconds").asLong()));
                    case "approve_pairing" -> {
                        // Approval changes only the peer's next poll answer, already in the script.
                    }
                    default -> throw new AssertionError("unsupported peer op " + op);
                }
            }
            // The case ends here: a connection the client opens afterwards is refused, so a flow
            // that continues past the scripted dialog fails fast instead of waiting for a reply.
            server.close();
            finish(null);
            return null;
        } catch (Throwable throwable) {
            finish(throwable);
            for (Socket socket : sockets) {
                socket.close();
            }
            throw throwable;
        }
    }

    private synchronized void block(int index) {
        blockedAt = index;
        notifyAll();
    }

    private synchronized void running() {
        // Cleared before any response is written, so a client that has seen the response never
        // observes this step as still pending.
        blockedAt = -1;
        notifyAll();
    }

    private synchronized void finish(Throwable throwable) {
        failure = throwable;
        done = true;
        notifyAll();
    }

    private static byte[] readLine(InputStream input, int index) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int next = input.read();
            if (next == -1) {
                throw new AssertionError("step " + index + " client closed before sending");
            }
            if (next == '\n') {
                return line.toByteArray();
            }
            line.write(next);
        }
    }

    static boolean jsonEquals(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            return false;
        }
        if (expected.isObject()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode other = actual.get(field.getKey());
                if (other == null || !jsonEquals(field.getValue(), other)) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (int index = 0; index < expected.size(); index++) {
                if (!jsonEquals(expected.get(index), actual.get(index))) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
    }
}
