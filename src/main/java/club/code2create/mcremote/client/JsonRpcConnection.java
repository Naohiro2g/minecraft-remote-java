package club.code2create.mcremote.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Line-delimited JSON-RPC 2.0 transport for one direct TCP connection.
 *
 * <p>Request ids start at 1 for each connection. A reader thread correlates responses through a
 * pending table, so several requests, from several threads, may be outstanding and their responses
 * may arrive in any order. Responses whose id is not pending are discarded.
 *
 * <p>While a request is pending, the connection is collected when nothing has been received for the
 * response timeout, counted from the last bytes received (or from when the first pending request
 * was sent). When the connection closes for any reason, every pending request fails with
 * {@link CompletionUnknownException} (2026-11-11-01 (2)(3)).
 */
final class JsonRpcConnection implements AutoCloseable {

    enum State {
        OPEN,
        CLOSED
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_MAP =
            new TypeReference<>() {};

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final Ticker ticker;
    private final FrameDecoder decoder = new FrameDecoder();
    private final Object writeLock = new Object();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();

    private final Map<Long, String> pending = new LinkedHashMap<>();
    private final Map<Long, JsonNode> arrived = new HashMap<>();
    private final Map<Long, String> methods = new HashMap<>();
    private final Set<Long> abandoned = new HashSet<>();
    private final List<Long> matched = new ArrayList<>();
    private final List<JsonNode> ignored = new ArrayList<>();
    private final List<Long> unanswered = new ArrayList<>();
    private State state = State.OPEN;
    private String closeReason;
    private long nextId = 1;
    private long framesReceived;
    private long idleSince;
    private long responseTimeoutNanos;
    private volatile Boolean compatible;

    /** Opens a connection with the default keepalive and the system clock. */
    JsonRpcConnection(ServerTarget target, Duration connectTimeout, Duration responseTimeout)
            throws TransportException {
        this(target, connectTimeout, responseTimeout, KeepAlive.DEFAULT, Ticker.SYSTEM);
    }

    JsonRpcConnection(
            ServerTarget target,
            Duration connectTimeout,
            Duration responseTimeout,
            KeepAlive keepAlive,
            Ticker ticker)
            throws TransportException {
        this.ticker = ticker;
        responseTimeoutNanos = positiveNanos(responseTimeout);
        socket = new Socket();
        try {
            socket.connect(
                    new InetSocketAddress(target.host(), target.port()), millis(connectTimeout));
            if (keepAlive != null) {
                enableKeepAlive(socket, keepAlive);
            }
            input = socket.getInputStream();
            output = socket.getOutputStream();
        } catch (IOException | RuntimeException exception) {
            closeQuietly(socket);
            throw new TransportException(
                    "Could not connect to " + target.credentialKey(), exception);
        }
        idleSince = ticker.nanoTime();
        Thread reader = new Thread(this::readLoop, "mcremote-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Sends one request and waits for its response. */
    JsonNode request(String method, Object params) throws MinecraftRemoteException {
        return await(send(method, params));
    }

    /**
     * Sends one request without waiting and returns its connection-scoped id.
     *
     * @throws NotSentException if the connection was already closed; nothing was written
     * @throws CompletionUnknownException if writing failed; some bytes may have been sent
     */
    long send(String method, Object params) throws TransportException {
        synchronized (writeLock) {
            long id;
            lock.lock();
            try {
                if (state == State.CLOSED) {
                    throw new NotSentException(NotSentException.Reason.DISCONNECTED,
                            "Connection is closed; did not send " + method, null);
                }
                id = nextId++;
                if (pending.isEmpty()) {
                    // The wait for a response starts now, not at the last receive before it.
                    idleSince = ticker.nanoTime();
                }
                pending.put(id, method);
                methods.put(id, method);
            } finally {
                lock.unlock();
            }
            try {
                output.write(encode(id, method, params));
                output.flush();
            } catch (IOException exception) {
                closeWith("could not send " + method);
                throw new CompletionUnknownException("Could not send " + method, exception);
            }
            return id;
        }
    }

    /**
     * Waits for the response to one previously sent request.
     *
     * @throws CompletionUnknownException if the connection closed or was collected first
     */
    JsonNode await(long id) throws MinecraftRemoteException {
        JsonNode response;
        lock.lock();
        try {
            String method = methods.getOrDefault(id, "request " + id);
            while (true) {
                response = arrived.remove(id);
                if (response != null) {
                    break;
                }
                if (!pending.containsKey(id)) {
                    if (unanswered.contains(id)) {
                        throw new CompletionUnknownException(
                                "Connection closed (" + closeReason + ") while waiting for "
                                        + method);
                    }
                    throw new IllegalStateException("request " + id + " is not pending");
                }
                long remaining = idleSince + responseTimeoutNanos - ticker.nanoTime();
                if (remaining <= 0) {
                    closeLocked("no response within " + Duration.ofNanos(responseTimeoutNanos)
                            + " of the last receive");
                    continue;
                }
                try {
                    changed.awaitNanos(remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    pending.remove(id);
                    abandoned.add(id);
                    throw new CompletionUnknownException(
                            "Interrupted while waiting for " + method, exception);
                }
            }
        } finally {
            lock.unlock();
        }
        return decodeResponse(response);
    }

    /** Collects the connection if its response wait has run out on the current clock. */
    void checkTimeout() {
        lock.lock();
        try {
            if (state == State.OPEN && !pending.isEmpty()
                    && ticker.nanoTime() - idleSince >= responseTimeoutNanos) {
                closeLocked("no response within " + Duration.ofNanos(responseTimeoutNanos)
                        + " of the last receive");
            }
        } finally {
            lock.unlock();
        }
    }

    /** Changes how long pending requests wait after the last receive. */
    void responseTimeout(Duration timeout) {
        long nanos = positiveNanos(timeout);
        lock.lock();
        try {
            responseTimeoutNanos = nanos;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Waits up to {@code wait} of real time for the connection to close; returns whether it did. */
    boolean pollClosed(Duration wait) {
        lock.lock();
        try {
            long remaining = wait.toNanos();
            while (state == State.OPEN && remaining > 0) {
                remaining = changed.awaitNanos(remaining);
            }
            return state == State.CLOSED;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return state == State.CLOSED;
        } finally {
            lock.unlock();
        }
    }

    State state() {
        lock.lock();
        try {
            return state;
        } finally {
            lock.unlock();
        }
    }

    boolean isOpen() {
        return state() == State.OPEN;
    }

    List<Long> pendingIds() {
        lock.lock();
        try {
            return List.copyOf(pending.keySet());
        } finally {
            lock.unlock();
        }
    }

    /** Ids whose complete response arrived, in arrival order. */
    List<Long> completedIds() {
        lock.lock();
        try {
            return List.copyOf(matched);
        } finally {
            lock.unlock();
        }
    }

    /** Ids that were still pending when the connection closed. */
    List<Long> unansweredIds() {
        lock.lock();
        try {
            return List.copyOf(unanswered);
        } finally {
            lock.unlock();
        }
    }

    /** Response ids that had no pending request and were discarded, in arrival order. */
    List<JsonNode> ignoredIds() {
        lock.lock();
        try {
            return List.copyOf(ignored);
        } finally {
            lock.unlock();
        }
    }

    String methodOf(long id) {
        lock.lock();
        try {
            return methods.get(id);
        } finally {
            lock.unlock();
        }
    }

    /** The number of requests written, counted from the first. */
    long sentCount() {
        lock.lock();
        try {
            return nextId - 1;
        } finally {
            lock.unlock();
        }
    }

    /** The number of complete JSON frames received. */
    long framesReceived() {
        lock.lock();
        try {
            return framesReceived;
        } finally {
            lock.unlock();
        }
    }

    /** Whether SO_KEEPALIVE is on (package-private for tests). */
    boolean keepAliveEnabled() throws IOException {
        return socket.getKeepAlive();
    }

    /**
     * Reads one {@code jdk.net.ExtendedSocketOptions} keepalive option, or null where it is not
     * supported (package-private for tests).
     */
    @SuppressWarnings("unchecked")
    Integer keepAliveOption(String name) {
        try {
            Class<?> options = Class.forName("jdk.net.ExtendedSocketOptions");
            SocketOption<Integer> option =
                    (SocketOption<Integer>) options.getField(name).get(null);
            return socket.supportedOptions().contains(option) ? socket.getOption(option) : null;
        } catch (ReflectiveOperationException | IOException | RuntimeException unsupported) {
            return null;
        }
    }

    /** The client's version judgment of this connection's hello, or null before it was made. */
    Boolean compatible() {
        return compatible;
    }

    void compatible(boolean value) {
        compatible = value;
    }

    private void readLoop() {
        byte[] buffer = new byte[8192];
        while (true) {
            int count;
            try {
                count = input.read(buffer);
            } catch (IOException exception) {
                // A reset, or keepalive probes that went unanswered.
                closeWith("socket error: " + exception.getMessage());
                return;
            }
            if (count < 0) {
                // An unterminated tail is never turned into a response.
                closeWith("peer EOF");
                return;
            }
            List<JsonNode> frames = decoder.feed(buffer, 0, count);
            lock.lock();
            try {
                if (state == State.CLOSED) {
                    return;
                }
                idleSince = ticker.nanoTime();
                accept(frames);
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private void accept(List<JsonNode> frames) {
        for (JsonNode frame : frames) {
            framesReceived++;
            if (!frame.isObject()) {
                continue;
            }
            JsonNode responseId = frame.get("id");
            if (responseId == null) {
                continue;
            }
            boolean pendingId = responseId.isIntegralNumber()
                    && responseId.canConvertToLong()
                    && pending.containsKey(responseId.longValue());
            if (!pendingId) {
                if (!(responseId.canConvertToLong() && abandoned.remove(responseId.longValue()))) {
                    ignored.add(responseId);
                }
                continue;
            }
            long id = responseId.longValue();
            pending.remove(id);
            matched.add(id);
            arrived.put(id, frame);
        }
    }

    private static byte[] encode(long id, String method, Object params)
            throws JsonProcessingException {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", MAPPER.valueToTree(params));
        }
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.writeBytes(MAPPER.writeValueAsBytes(request));
        frame.write('\n');
        return frame.toByteArray();
    }

    private static JsonNode decodeResponse(JsonNode response) throws MinecraftRemoteException {
        if (!"2.0".equals(response.path("jsonrpc").asText())) {
            throw new ProtocolException("response jsonrpc must be 2.0");
        }
        boolean hasResult = response.has("result");
        boolean hasError = response.has("error");
        if (hasResult == hasError) {
            throw new ProtocolException("response must contain exactly one of result or error");
        }
        if (hasError) {
            throw decodeRemoteError(response.get("error"));
        }
        return response.get("result");
    }

    private static RemoteCallException decodeRemoteError(JsonNode error)
            throws ProtocolException {
        if (error == null
                || !error.isObject()
                || !error.path("code").isIntegralNumber()
                || !error.path("code").canConvertToInt()) {
            throw new ProtocolException("invalid JSON-RPC error object");
        }
        int code = error.path("code").intValue();
        String message = error.path("message").isTextual()
                ? error.path("message").textValue()
                : "remote error";
        JsonNode dataNode = error.get("data");
        Map<String, Object> data = dataNode != null && dataNode.isObject()
                ? MAPPER.convertValue(dataNode, OBJECT_MAP)
                : Map.of();
        return new RemoteCallException(code, message, data);
    }

    private void closeWith(String reason) {
        lock.lock();
        try {
            closeLocked(reason);
        } finally {
            lock.unlock();
        }
    }

    private void closeLocked(String reason) {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        closeReason = reason;
        unanswered.addAll(pending.keySet());
        pending.clear();
        changed.signalAll();
        closeQuietly(socket);
    }

    private static void enableKeepAlive(Socket socket, KeepAlive keepAlive) throws IOException {
        socket.setKeepAlive(true);
        setIfSupported(socket, "TCP_KEEPIDLE", (int) keepAlive.idle().toSeconds());
        setIfSupported(socket, "TCP_KEEPINTERVAL", (int) keepAlive.interval().toSeconds());
        setIfSupported(socket, "TCP_KEEPCOUNT", keepAlive.probes());
    }

    @SuppressWarnings("unchecked")
    private static void setIfSupported(Socket socket, String name, int value) {
        // Read reflectively so the client still runs where jdk.net is not available.
        try {
            Class<?> options = Class.forName("jdk.net.ExtendedSocketOptions");
            SocketOption<Integer> option =
                    (SocketOption<Integer>) options.getField(name).get(null);
            if (socket.supportedOptions().contains(option)) {
                socket.setOption(option, value);
            }
        } catch (ReflectiveOperationException | IOException | RuntimeException ignored) {
            // The operating system's keepalive defaults apply.
        }
    }

    private static long positiveNanos(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE / 4;
        }
    }

    private static int millis(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("timeout must be between 1ms and 2147483647ms");
        }
        return (int) millis;
    }

    @Override
    public void close() {
        closeWith("closed by the client");
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing is best-effort; any request failure has already been surfaced.
        }
    }
}
