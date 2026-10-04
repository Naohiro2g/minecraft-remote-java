package club.code2create.mcremote.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ScriptedPeer implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ServerSocket serverSocket;
    private final ExecutorService executor;
    private final Future<?> future;

    ScriptedPeer(Script script) throws IOException {
        serverSocket = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        executor = Executors.newSingleThreadExecutor();
        future = executor.submit(
                () -> {
                    script.run(this);
                    return null;
                });
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    PeerConnection accept() throws IOException {
        return new PeerConnection(serverSocket.accept());
    }

    void await() throws Exception {
        try {
            future.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
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
            throw new AssertionError("scripted peer did not complete", exception);
        }
    }

    @Override
    public void close() throws Exception {
        try {
            serverSocket.close();
            await();
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    interface Script {
        void run(ScriptedPeer peer) throws Exception;
    }

    static final class PeerConnection implements AutoCloseable {

        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        PeerConnection(Socket socket) throws IOException {
            this.socket = socket;
            socket.setSoTimeout(5_000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), UTF_8));
        }

        ObjectNode readRequest() throws IOException {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("client closed before the next request");
            }
            JsonNode request = MAPPER.readTree(line);
            if (!request.isObject()) {
                throw new IOException("request was not a JSON object");
            }
            return (ObjectNode) request;
        }

        void result(ObjectNode request, Object result) throws IOException {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", request.get("id"));
            response.set("result", MAPPER.valueToTree(result));
            write(response);
        }

        void error(ObjectNode request, int code, String message, String reason) throws IOException {
            error(request.path("id").longValue(), code, message, reason);
        }

        void error(long id, int code, String message, String reason) throws IOException {
            ObjectNode error = MAPPER.createObjectNode();
            error.put("code", code);
            error.put("message", message);
            error.set("data", MAPPER.valueToTree(Map.of("reason", reason)));
            ObjectNode response = MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.set("error", error);
            write(response);
        }

        private void write(ObjectNode response) throws IOException {
            writer.write(MAPPER.writeValueAsString(response));
            writer.write('\n');
            writer.flush();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
