package club.code2create.mcremote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Line-delimited JSON-RPC 2.0 transport for one direct TCP connection.
 *
 * <p>Request ids start at 1 for each connection. Responses are correlated through a pending table,
 * so several requests may be outstanding and their responses may arrive in any order. Responses
 * whose id is not pending are discarded. This class is not safe for concurrent use.
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
    private final int readTimeoutMillis;
    private final FrameDecoder decoder = new FrameDecoder();
    private final byte[] buffer = new byte[8192];

    private final Map<Long, String> pending = new LinkedHashMap<>();
    private final Map<Long, JsonNode> arrived = new HashMap<>();
    private final Map<Long, String> methods = new HashMap<>();
    private final List<Long> matched = new ArrayList<>();
    private final List<JsonNode> ignored = new ArrayList<>();
    private final List<Long> unanswered = new ArrayList<>();
    private State state = State.OPEN;
    private long nextId = 1;

    JsonRpcConnection(ServerTarget target, Duration connectTimeout, Duration readTimeout)
            throws TransportException {
        socket = new Socket();
        readTimeoutMillis = millis(readTimeout);
        try {
            socket.connect(
                    new InetSocketAddress(target.host(), target.port()), millis(connectTimeout));
            socket.setSoTimeout(readTimeoutMillis);
            input = socket.getInputStream();
            output = socket.getOutputStream();
        } catch (IOException | RuntimeException exception) {
            closeQuietly(socket);
            throw new TransportException(
                    "Could not connect to " + target.credentialKey(), exception);
        }
    }

    /** Sends one request and waits for its response. */
    JsonNode request(String method, Object params) throws MinecraftRemoteException {
        return await(send(method, params));
    }

    /** Sends one request without waiting and returns its connection-scoped id. */
    long send(String method, Object params) throws TransportException {
        if (state == State.CLOSED) {
            throw new TransportException("Connection is closed; could not send " + method);
        }
        long id = nextId++;
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", MAPPER.valueToTree(params));
        }
        pending.put(id, method);
        methods.put(id, method);
        try {
            output.write(MAPPER.writeValueAsBytes(request));
            output.write('\n');
            output.flush();
        } catch (IOException exception) {
            markClosed();
            throw new TransportException("Could not send " + method, exception);
        }
        return id;
    }

    /** Waits for the response to one previously sent request. */
    JsonNode await(long id) throws MinecraftRemoteException {
        String method = methods.getOrDefault(id, "request " + id);
        while (true) {
            JsonNode response = arrived.remove(id);
            if (response != null) {
                return decodeResponse(response);
            }
            if (!pending.containsKey(id)) {
                if (state == State.CLOSED) {
                    throw new TransportException("Connection closed while waiting for " + method);
                }
                throw new IllegalStateException("request " + id + " is not pending");
            }
            int count;
            try {
                count = input.read(buffer);
            } catch (SocketTimeoutException exception) {
                // A late response for an abandoned request has no consumer and is discarded.
                pending.remove(id);
                throw new TransportException("Timed out waiting for " + method, exception);
            } catch (IOException exception) {
                markClosed();
                throw new TransportException("Could not read response for " + method, exception);
            }
            if (count < 0) {
                // An unterminated tail is never turned into a response.
                markClosed();
                throw new TransportException("Connection closed while waiting for " + method);
            }
            accept(decoder.feed(buffer, 0, count));
        }
    }

    /**
     * Reads whatever arrives within {@code wait} without sending a request and reports whether the
     * connection is closed. Used to observe a peer EOF between synchronous requests.
     */
    boolean pollClosed(Duration wait) {
        if (state == State.CLOSED) {
            return true;
        }
        try {
            socket.setSoTimeout(millis(wait));
            while (true) {
                int count = input.read(buffer);
                if (count < 0) {
                    markClosed();
                    return true;
                }
                accept(decoder.feed(buffer, 0, count));
            }
        } catch (SocketTimeoutException exception) {
            return false;
        } catch (IOException exception) {
            markClosed();
            return true;
        } finally {
            if (state == State.OPEN) {
                try {
                    socket.setSoTimeout(readTimeoutMillis);
                } catch (IOException exception) {
                    markClosed();
                }
            }
        }
    }

    State state() {
        return state;
    }

    List<Long> pendingIds() {
        return List.copyOf(pending.keySet());
    }

    /** Ids whose complete response arrived, in arrival order. */
    List<Long> completedIds() {
        return List.copyOf(matched);
    }

    /** Ids that were still pending when the connection closed. */
    List<Long> unansweredIds() {
        return List.copyOf(unanswered);
    }

    /** Response ids that had no pending request and were discarded, in arrival order. */
    List<JsonNode> ignoredIds() {
        return List.copyOf(ignored);
    }

    String methodOf(long id) {
        return methods.get(id);
    }

    private void accept(List<JsonNode> frames) {
        for (JsonNode frame : frames) {
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
                ignored.add(responseId);
                continue;
            }
            long id = responseId.longValue();
            pending.remove(id);
            matched.add(id);
            arrived.put(id, frame);
        }
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

    private void markClosed() {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        unanswered.addAll(pending.keySet());
        pending.clear();
        closeQuietly(socket);
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
        markClosed();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing is best-effort; any request failure has already been surfaced.
        }
    }
}
