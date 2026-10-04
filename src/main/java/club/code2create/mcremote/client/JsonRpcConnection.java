package club.code2create.mcremote.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synchronous, single-stream line-delimited JSON-RPC 2.0 transport. */
final class JsonRpcConnection implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_MAP =
            new TypeReference<>() {};

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private long nextId = 1;

    JsonRpcConnection(ServerTarget target, Duration connectTimeout, Duration readTimeout)
            throws TransportException {
        socket = new Socket();
        try {
            socket.connect(
                    new InetSocketAddress(target.host(), target.port()), millis(connectTimeout));
            socket.setSoTimeout(millis(readTimeout));
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), UTF_8));
        } catch (IOException | RuntimeException exception) {
            closeQuietly(socket);
            throw new TransportException(
                    "Could not connect to " + target.credentialKey(), exception);
        }
    }

    JsonNode request(String method, Object params) throws MinecraftRemoteException {
        long id = nextId++;
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.set("params", MAPPER.valueToTree(params));
        }
        writeFrame(request, method);
        return readResponse(id, method);
    }

    private void writeFrame(ObjectNode request, String method) throws TransportException {
        try {
            writer.write(MAPPER.writeValueAsString(request));
            writer.write('\n');
            writer.flush();
        } catch (IOException exception) {
            throw new TransportException("Could not send " + method, exception);
        }
    }

    private JsonNode readResponse(long expectedId, String method) throws MinecraftRemoteException {
        while (true) {
            String line;
            try {
                line = reader.readLine();
            } catch (SocketTimeoutException exception) {
                throw new TransportException("Timed out waiting for " + method, exception);
            } catch (IOException exception) {
                throw new TransportException("Could not read response for " + method, exception);
            }
            if (line == null) {
                throw new TransportException("Connection closed while waiting for " + method);
            }

            JsonNode response;
            try {
                response = MAPPER.readTree(line);
            } catch (JsonProcessingException ignored) {
                // The protocol explicitly discards non-JSON lines.
                continue;
            }
            if (!response.isObject()) {
                continue;
            }

            JsonNode responseId = response.get("id");
            boolean matchingId = responseId != null
                    && responseId.isIntegralNumber()
                    && responseId.canConvertToLong()
                    && responseId.longValue() == expectedId;
            if (!matchingId) {
                // Unknown response ids have no pending consumer on this synchronous connection.
                continue;
            }
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

    private static int millis(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("timeout must be between 1ms and 2147483647ms");
        }
        return (int) millis;
    }

    @Override
    public void close() {
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing is best-effort; any request failure has already been surfaced.
        }
    }
}
