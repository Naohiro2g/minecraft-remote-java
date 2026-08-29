package club.code2create.mcremote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A synchronous Java client for the Minecraft Remote Protocol 23.0.0 Phase B slice.
 *
 * <p>One instance owns one TCP connection and its connection-scoped build context. Public methods
 * are not safe for concurrent use.
 */
public final class MinecraftRemote implements AutoCloseable {

    public static final String PROTOCOL = "23.0.0";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT_MAP =
            new TypeReference<>() {};
    private static final Set<String> AUTH_DISCARD_REASONS = Set.of(
            "auth_required",
            "token_expired",
            "token_revoked",
            "token_not_found",
            "token_invalid");

    private final ServerTarget target;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration pairPollInterval;
    private final CredentialStore credentialStore;
    private final PairingListener pairingListener;
    private final TokenType tokenType;
    private final ClientIdentity clientIdentity;

    private JsonRpcConnection connection;
    private HelloResult helloResult;
    private BuildContext buildContext;
    private boolean closed;

    private MinecraftRemote(Builder builder) {
        target = builder.target;
        connectTimeout = builder.connectTimeout;
        readTimeout = builder.readTimeout;
        pairPollInterval = builder.pairPollInterval;
        credentialStore = builder.credentialStore;
        pairingListener = builder.pairingListener;
        tokenType = builder.tokenType;
        clientIdentity = builder.clientIdentity;
    }

    public static Builder builder(String host, int port) {
        return new Builder(new ServerTarget(host, port));
    }

    /** Returns the server-authoritative handshake result. */
    public HelloResult helloResult() {
        ensureOpen();
        return helloResult;
    }

    /** Returns the current server-canonical build context. */
    public BuildContext buildContext() {
        ensureOpen();
        return buildContext;
    }

    /** Changes the origin for subsequent origin-relative commands. */
    public BuildContext setOrigin(int x, int y, int z) throws MinecraftRemoteException {
        BuildContext updated = decodeBuildContext(
                request("build.setOrigin", List.of(x, y, z)), "build.setOrigin result");
        buildContext = updated;
        return updated;
    }

    /** Posts one message to Minecraft chat and waits for the server response. */
    public void postToChat(String message) throws MinecraftRemoteException {
        Objects.requireNonNull(message, "message");
        request("chat.post", List.of(message));
    }

    /** Sets one origin-relative block and waits for the server's success or error response. */
    public void setBlock(int x, int y, int z, BlockSpec block) throws MinecraftRemoteException {
        Objects.requireNonNull(block, "block");
        JsonNode result = request(
                "world.setBlock", List.of(x, y, z, encodeBlock(block.blockId(), block.state())));
        if (!result.isNull()) {
            throw new ProtocolException("world.setBlock success result must be null");
        }
    }

    /** Fills one origin-relative cuboid and waits for the server's success or error response. */
    public void setBlocks(
            int x1,
            int y1,
            int z1,
            int x2,
            int y2,
            int z2,
            BlockSpec block)
            throws MinecraftRemoteException {
        Objects.requireNonNull(block, "block");
        JsonNode result = request(
                "world.setBlocks",
                List.of(
                        x1,
                        y1,
                        z1,
                        x2,
                        y2,
                        z2,
                        encodeBlock(block.blockId(), block.state())));
        if (!result.isNull()) {
            throw new ProtocolException("world.setBlocks success result must be null");
        }
    }

    /** Gets one origin-relative block as a canonical structured value. */
    public BlockValue getBlock(int x, int y, int z) throws MinecraftRemoteException {
        JsonNode result = request("world.getBlock", List.of(x, y, z));
        return decodeBlockValue(result);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private void connect() throws MinecraftRemoteException {
        String token = credentialStore.load(target).orElse(null);
        openConnection();
        try {
            acceptHello(hello(token));
            return;
        } catch (RemoteCallException exception) {
            Optional<String> reason = exception.reason();
            if (reason.isEmpty() || !AUTH_DISCARD_REASONS.contains(reason.get())) {
                close();
                throw exception;
            }
            credentialStore.clear(target);
            if (pairingListener == null) {
                close();
                throw new PairingRequiredException(reason.get());
            }
        } catch (MinecraftRemoteException exception) {
            close();
            throw exception;
        }

        try {
            reconnect();
            String pairedToken = pair();
            credentialStore.save(target, pairedToken);
            reconnect();
            try {
                acceptHello(hello(pairedToken));
            } catch (RemoteCallException exception) {
                if (exception.reason().filter(AUTH_DISCARD_REASONS::contains).isPresent()) {
                    credentialStore.clear(target);
                }
                throw exception;
            }
        } catch (MinecraftRemoteException exception) {
            close();
            throw exception;
        }
    }

    private HelloResult hello(String token) throws MinecraftRemoteException {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocol", PROTOCOL);
        if (token != null && !token.isBlank()) {
            params.put("auth", Map.of("token", token));
        }
        JsonNode result = request("hello", params);
        if (!result.isObject()) {
            throw new ProtocolException("hello result must be an object");
        }

        String serverProtocol = requiredText(result, "protocol", "hello result");
        ProtocolVersion required = ProtocolVersion.parse(PROTOCOL);
        ProtocolVersion advertised = ProtocolVersion.parse(serverProtocol);
        if (!advertised.satisfies(required)) {
            throw new ProtocolException(
                    "incompatible protocol: server=" + serverProtocol + ", client=" + PROTOCOL);
        }

        String minecraftVersion = requiredText(result, "mc_version", "hello result");
        List<String> supportedVersions = stringList(
                result.get("supported_mc_versions"), "hello result.supported_mc_versions");
        Optional<String> catalogHash = optionalText(result.get("catalogHash"), "catalogHash");
        Map<String, Object> worldConstants = objectMap(
                result.get("world_constants"), "hello result.world_constants");
        String dimension = requiredText(result, "dimension", "hello result");
        if (!dimension.contains(":")) {
            throw new ProtocolException("hello result.dimension must be fully qualified");
        }
        BlockPosition origin = blockPosition(result.get("origin"), "hello result.origin");
        Map<String, Object> permissions = optionalObjectMap(
                result.get("permissions"), "hello result.permissions");

        return new HelloResult(
                serverProtocol,
                minecraftVersion,
                supportedVersions,
                catalogHash,
                worldConstants,
                optionalText(result.get("session"), "session"),
                optionalText(result.get("player"), "player"),
                dimension,
                origin,
                permissions,
                optionalText(result.get("server"), "server"));
    }

    private void acceptHello(HelloResult result) {
        helloResult = result;
        buildContext = new BuildContext(result.dimension(), result.origin());
    }

    private String pair() throws MinecraftRemoteException {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("name", clientIdentity.name());
        client.put("version", clientIdentity.version());
        client.put("locale", clientIdentity.locale());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("token_type", tokenType.wireName());
        params.put("client", client);

        JsonNode begin = request("auth.pairBegin", params);
        if (!begin.isObject()) {
            throw new ProtocolException("auth.pairBegin result must be an object");
        }
        String pairingId = requiredText(begin, "pairing_id", "auth.pairBegin result");
        String pairCode = requiredText(begin, "pair_code", "auth.pairBegin result");
        long expiresSeconds = requiredPositiveLong(begin, "expires_in", "auth.pairBegin result");
        PairingChallenge challenge = new PairingChallenge(
                pairCode, displayPairCode(pairCode), Duration.ofSeconds(expiresSeconds));
        pairingListener.pairingRequired(challenge);

        long deadline = System.nanoTime() + challenge.expiresIn().toNanos();
        while (true) {
            JsonNode poll = request("auth.pairPoll", Map.of("pairing_id", pairingId));
            if (!poll.isObject()) {
                throw new ProtocolException("auth.pairPoll result must be an object");
            }
            String status = requiredText(poll, "status", "auth.pairPoll result");
            if ("ok".equals(status)) {
                return requiredText(poll, "token", "auth.pairPoll result");
            }
            if (!"pending".equals(status)) {
                throw new ProtocolException("unknown auth.pairPoll status: " + status);
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new RemoteCallException(
                        -32000, "pairing code expired", Map.of("reason", "pair_expired"));
            }
            sleep(Duration.ofNanos(Math.min(remaining, pairPollInterval.toNanos())));
        }
    }

    private JsonNode request(String method, Object params) throws MinecraftRemoteException {
        ensureOpen();
        return connection.request(method, params);
    }

    private void openConnection() throws TransportException {
        connection = new JsonRpcConnection(target, connectTimeout, readTimeout);
    }

    private void reconnect() throws TransportException {
        if (connection != null) {
            connection.close();
        }
        openConnection();
    }

    private void ensureOpen() {
        if (closed || connection == null) {
            throw new IllegalStateException("MinecraftRemote is closed");
        }
    }

    private static Map<String, Object> encodeBlock(String blockId, Map<String, Object> state) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("block_id", blockId);
        block.put("state", state);
        return block;
    }

    private static BlockValue decodeBlockValue(JsonNode value) throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException("world.getBlock result must be a BlockValue object");
        }
        if (value.size() != 2 || !value.has("block_id") || !value.has("state")) {
            throw new ProtocolException(
                    "world.getBlock result must contain exactly block_id and state");
        }
        String blockId = requiredText(value, "block_id", "world.getBlock result");
        Map<String, Object> state = objectMap(value.get("state"), "world.getBlock result.state");
        try {
            return new BlockValue(blockId, state);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid world.getBlock BlockValue", exception);
        }
    }

    private static String requiredText(JsonNode object, String field, String where)
            throws ProtocolException {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new ProtocolException(where + "." + field + " must be a non-empty string");
        }
        return value.textValue();
    }

    private static Optional<String> optionalText(JsonNode value, String field)
            throws ProtocolException {
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (!value.isTextual()) {
            throw new ProtocolException("hello result." + field + " must be a string or null");
        }
        return Optional.of(value.textValue());
    }

    private static List<String> stringList(JsonNode value, String where) throws ProtocolException {
        if (value == null || !value.isArray()) {
            throw new ProtocolException(where + " must be an array");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw new ProtocolException(where + " must contain only strings");
            }
            values.add(item.textValue());
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> objectMap(JsonNode value, String where)
            throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException(where + " must be an object");
        }
        return MAPPER.convertValue(value, OBJECT_MAP);
    }

    private static Map<String, Object> optionalObjectMap(JsonNode value, String where)
            throws ProtocolException {
        if (value == null || value.isNull()) {
            return Map.of();
        }
        return objectMap(value, where);
    }

    private static BlockPosition blockPosition(JsonNode value, String where)
            throws ProtocolException {
        if (value == null || !value.isArray() || value.size() != 3) {
            throw new ProtocolException(where + " must be a three-integer array");
        }
        int[] coordinates = new int[3];
        for (int index = 0; index < coordinates.length; index++) {
            JsonNode coordinate = value.get(index);
            if (!coordinate.isIntegralNumber() || !coordinate.canConvertToInt()) {
                throw new ProtocolException(where + " must be a three-integer array");
            }
            coordinates[index] = coordinate.intValue();
        }
        return new BlockPosition(coordinates[0], coordinates[1], coordinates[2]);
    }

    private static BuildContext decodeBuildContext(JsonNode value, String where)
            throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException(where + " must be an object");
        }
        if (value.size() != 2 || !value.has("dimension") || !value.has("origin")) {
            throw new ProtocolException(where + " must contain exactly dimension and origin");
        }
        String dimension = requiredText(value, "dimension", where);
        BlockPosition origin = blockPosition(value.get("origin"), where + ".origin");
        try {
            return new BuildContext(dimension, origin);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid " + where, exception);
        }
    }

    private static long requiredPositiveLong(JsonNode value, String field, String where)
            throws ProtocolException {
        JsonNode number = value.get(field);
        if (number == null || !number.isIntegralNumber() || !number.canConvertToLong()) {
            throw new ProtocolException(where + "." + field + " must be a positive integer");
        }
        long result = number.longValue();
        if (result <= 0) {
            throw new ProtocolException(where + "." + field + " must be a positive integer");
        }
        return result;
    }

    private static String displayPairCode(String pairCode) {
        if (pairCode.length() == 6 && pairCode.chars().allMatch(character -> character >= '0'
                && character <= '9')) {
            return pairCode.substring(0, 3) + "-" + pairCode.substring(3);
        }
        return pairCode;
    }

    private static void sleep(Duration duration) throws MinecraftRemoteException {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MinecraftRemoteException("Interrupted while waiting for pairing", exception);
        }
    }

    /** Builder for one direct TCP connection. */
    public static final class Builder {

        private final ServerTarget target;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(10);
        private Duration pairPollInterval = Duration.ofMillis(1500);
        private CredentialStore credentialStore = CredentialStore.none();
        private PairingListener pairingListener;
        private TokenType tokenType = TokenType.SESSION;
        private ClientIdentity clientIdentity = ClientIdentity.defaultIdentity();

        private Builder(ServerTarget target) {
            this.target = target;
        }

        public Builder connectTimeout(Duration timeout) {
            connectTimeout = positive(timeout, "connectTimeout");
            return this;
        }

        public Builder readTimeout(Duration timeout) {
            readTimeout = positive(timeout, "readTimeout");
            return this;
        }

        public Builder pairPollInterval(Duration interval) {
            pairPollInterval = positive(interval, "pairPollInterval");
            return this;
        }

        public Builder credentialStore(CredentialStore store) {
            credentialStore = Objects.requireNonNull(store, "store");
            return this;
        }

        public Builder pairingListener(PairingListener listener) {
            pairingListener = Objects.requireNonNull(listener, "listener");
            return this;
        }

        public Builder tokenType(TokenType type) {
            tokenType = Objects.requireNonNull(type, "type");
            return this;
        }

        public Builder clientIdentity(ClientIdentity identity) {
            clientIdentity = Objects.requireNonNull(identity, "identity");
            return this;
        }

        public MinecraftRemote connect() throws MinecraftRemoteException {
            MinecraftRemote client = new MinecraftRemote(this);
            try {
                client.connect();
                return client;
            } catch (MinecraftRemoteException | RuntimeException exception) {
                client.close();
                throw exception;
            }
        }

        private static Duration positive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }
}
