package club.code2create.mcremote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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

    /** Gets the server-authoritative block, entity, and particle catalog. */
    public Catalog getCatalog() throws MinecraftRemoteException {
        return decodeCatalog(request("catalog.get", List.of()));
    }

    /** Gets the player's origin-relative position and orientation. */
    public PlayerPose getPlayerPose() throws MinecraftRemoteException {
        return decodePlayerPose(request("player.getPose", List.of()), "player.getPose result");
    }

    /** Moves the player and returns the server-canonical pose. */
    public PlayerPose setPlayerPose(
            String dimension,
            double x,
            double y,
            double z,
            double yaw,
            double pitch)
            throws MinecraftRemoteException {
        PlayerPose requested = new PlayerPose(dimension, new Vec3(x, y, z), yaw, pitch);
        JsonNode result = request(
                "player.setPose",
                List.of(
                        requested.dimension(),
                        wireNumber(requested.position().x()),
                        wireNumber(requested.position().y()),
                        wireNumber(requested.position().z()),
                        wireNumber(requested.yaw()),
                        wireNumber(requested.pitch())));
        return decodePlayerPose(result, "player.setPose result");
    }

    /** Spawns a data-free particle; omitted {@code force} means the protocol default, true. */
    public int spawnParticle(
            double x,
            double y,
            double z,
            double offsetX,
            double offsetY,
            double offsetZ,
            String particle,
            double speed,
            int count)
            throws MinecraftRemoteException {
        return spawnParticle(
                x, y, z, offsetX, offsetY, offsetZ, particle, speed, count, Optional.empty());
    }

    /** Spawns a data-free particle with an explicit force flag. */
    public int spawnParticle(
            double x,
            double y,
            double z,
            double offsetX,
            double offsetY,
            double offsetZ,
            String particle,
            double speed,
            int count,
            boolean force)
            throws MinecraftRemoteException {
        return spawnParticle(
                x, y, z, offsetX, offsetY, offsetZ, particle, speed, count, Optional.of(force));
    }

    /** Gets both faces and the wax state of one origin-relative sign. */
    public Sign.Value getSign(int x, int y, int z) throws MinecraftRemoteException {
        return decodeSign(request("world.getSign", List.of(x, y, z)));
    }

    /** Replaces one or both faces of an origin-relative sign. */
    public void setSign(int x, int y, int z, Sign.Content content)
            throws MinecraftRemoteException {
        Objects.requireNonNull(content, "content");
        JsonNode result = request("world.setSign", List.of(x, y, z, encodeSignContent(content)));
        requireNullResult(result, "world.setSign");
    }

    /** Replaces one line on one face of an origin-relative sign. */
    public void updateSignLine(
            int x, int y, int z, Sign.Face face, int lineIndex, Sign.LineSpec line)
            throws MinecraftRemoteException {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(line, "line");
        if (lineIndex < 0 || lineIndex > 3) {
            throw new IllegalArgumentException("lineIndex must be between 0 and 3");
        }
        JsonNode result = request(
                "world.updateSignLine",
                List.of(x, y, z, face.wireName(), lineIndex, encodeSignLine(line)));
        requireNullResult(result, "world.updateSignLine");
    }

    /** Polls all currently available events after one sequence cursor. */
    public EventPollResult pollEvents(long afterSequence) throws MinecraftRemoteException {
        requireNonNegative(afterSequence, "afterSequence");
        return decodeEventPollResult(request("events.poll", List.of(afterSequence)));
    }

    /** Polls at most {@code maxEvents} events after one sequence cursor. */
    public EventPollResult pollEvents(long afterSequence, int maxEvents)
            throws MinecraftRemoteException {
        requireNonNegative(afterSequence, "afterSequence");
        if (maxEvents <= 0) {
            throw new IllegalArgumentException("maxEvents must be positive");
        }
        return decodeEventPollResult(request(
                "events.poll", List.of(afterSequence, Map.of("max_events", maxEvents))));
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

    private int spawnParticle(
            double x,
            double y,
            double z,
            double offsetX,
            double offsetY,
            double offsetZ,
            String particle,
            double speed,
            int count,
            Optional<Boolean> force)
            throws MinecraftRemoteException {
        new Vec3(x, y, z);
        requireNonNegativeFinite(offsetX, "offsetX");
        requireNonNegativeFinite(offsetY, "offsetY");
        requireNonNegativeFinite(offsetZ, "offsetZ");
        requireQualifiedId(particle, "particle");
        requireNonNegativeFinite(speed, "speed");
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        List<Object> params = new ArrayList<>(10);
        params.addAll(List.of(
                wireNumber(x),
                wireNumber(y),
                wireNumber(z),
                wireNumber(offsetX),
                wireNumber(offsetY),
                wireNumber(offsetZ),
                particle,
                wireNumber(speed),
                count));
        force.ifPresent(params::add);
        JsonNode result = request("world.spawnParticle", params);
        if (!result.isIntegralNumber() || !result.canConvertToInt() || result.intValue() < 0) {
            throw new ProtocolException(
                    "world.spawnParticle result must be a non-negative integer");
        }
        return result.intValue();
    }

    private static Catalog decodeCatalog(JsonNode value) throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException("catalog.get result must be an object");
        }
        String hash = requiredText(value, "catalogHash", "catalog.get result");
        JsonNode blocksNode = requiredObject(value.get("block"), "catalog.get result.block");
        JsonNode entitiesNode = requiredObject(value.get("entity"), "catalog.get result.entity");
        JsonNode particlesNode = requiredObject(value.get("particle"), "catalog.get result.particle");

        Map<String, Catalog.BlockEntry> blocks = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : blocksNode.properties()) {
            blocks.put(entry.getKey(), decodeCatalogBlock(entry.getValue(), entry.getKey()));
        }
        Set<String> entityIds = catalogIds(entitiesNode, "catalog.get result.entity");
        Set<String> particleIds = catalogIds(particlesNode, "catalog.get result.particle");
        try {
            return new Catalog(hash, blocks, entityIds, particleIds);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid catalog.get result", exception);
        }
    }

    private static Catalog.BlockEntry decodeCatalogBlock(JsonNode value, String blockId)
            throws ProtocolException {
        String where = "catalog.get result.block." + blockId;
        JsonNode object = requiredObject(value, where);
        JsonNode statesNode = requiredObject(object.get("states"), where + ".states");
        JsonNode defaultNode = requiredObject(object.get("default_state"), where + ".default_state");
        Map<String, List<Object>> states = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : statesNode.properties()) {
            JsonNode choices = entry.getValue();
            if (!choices.isArray() || choices.isEmpty()) {
                throw new ProtocolException(
                        where + ".states." + entry.getKey() + " must be a non-empty array");
            }
            List<Object> values = new ArrayList<>();
            Class<?> valueType = null;
            for (JsonNode choice : choices) {
                Object scalar = scalar(choice, where + ".states." + entry.getKey());
                Class<?> currentType = scalar instanceof Number ? Number.class : scalar.getClass();
                if (valueType == null) {
                    valueType = currentType;
                } else if (valueType != currentType) {
                    throw new ProtocolException(
                            where + ".states." + entry.getKey()
                                    + " must contain one JSON scalar type");
                }
                values.add(scalar);
            }
            states.put(entry.getKey(), values);
        }
        Map<String, Object> defaultState = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : defaultNode.properties()) {
            defaultState.put(entry.getKey(), scalar(entry.getValue(), where + ".default_state"));
        }
        try {
            return new Catalog.BlockEntry(states, defaultState);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid " + where, exception);
        }
    }

    private static Set<String> catalogIds(JsonNode object, String where)
            throws ProtocolException {
        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<String, JsonNode> entry : object.properties()) {
            if (!entry.getValue().isObject()) {
                throw new ProtocolException(where + "." + entry.getKey() + " must be an object");
            }
            names.add(entry.getKey());
        }
        return names;
    }

    private static Object scalar(JsonNode value, String where) throws ProtocolException {
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isIntegralNumber()) {
            return value.canConvertToInt() ? value.intValue() : value.longValue();
        }
        if (value.isFloatingPointNumber() && Double.isFinite(value.doubleValue())) {
            return value.doubleValue();
        }
        throw new ProtocolException(where + " must contain only JSON scalar values");
    }

    private static PlayerPose decodePlayerPose(JsonNode value, String where)
            throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException(where + " must be an object");
        }
        String dimension = requiredText(value, "dimension", where);
        Vec3 position = vec3(value.get("pos"), where + ".pos");
        double yaw = requiredFiniteDouble(value, "yaw", where);
        double pitch = requiredFiniteDouble(value, "pitch", where);
        if (yaw < -180.0 || yaw >= 180.0) {
            throw new ProtocolException(where + ".yaw must be in [-180, 180)");
        }
        if (pitch < -90.0 || pitch > 90.0) {
            throw new ProtocolException(where + ".pitch must be in [-90, 90]");
        }
        try {
            return new PlayerPose(dimension, position, yaw, pitch);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid " + where, exception);
        }
    }

    private static Map<String, Object> encodeSignContent(Sign.Content content) {
        Map<String, Object> value = new LinkedHashMap<>();
        content.front().ifPresent(lines -> value.put("front", encodeSignLines(lines)));
        content.back().ifPresent(lines -> value.put("back", encodeSignLines(lines)));
        return value;
    }

    private static List<Object> encodeSignLines(List<Sign.LineSpec> lines) {
        return lines.stream().map(MinecraftRemote::encodeSignLine).toList();
    }

    private static Object encodeSignLine(Sign.LineSpec line) {
        if (line.isPlain()) {
            return line.text();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("text", line.text());
        line.color().ifPresent(color -> value.put("color", color));
        if (!line.decorations().isEmpty()) {
            value.put(
                    "decorations",
                    line.decorations().stream().map(Sign.Decoration::wireName).toList());
        }
        return value;
    }

    private static Sign.Value decodeSign(JsonNode value) throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException("world.getSign result must be an object");
        }
        List<Sign.LineValue> front = decodeSignLines(value.get("front"), "front");
        List<Sign.LineValue> back = decodeSignLines(value.get("back"), "back");
        JsonNode waxed = value.get("waxed");
        if (waxed == null || !waxed.isBoolean()) {
            throw new ProtocolException("world.getSign result.waxed must be a boolean");
        }
        try {
            return new Sign.Value(front, back, waxed.booleanValue());
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid world.getSign result", exception);
        }
    }

    private static List<Sign.LineValue> decodeSignLines(JsonNode value, String face)
            throws ProtocolException {
        String where = "world.getSign result." + face;
        if (value == null || !value.isArray() || value.size() != 4) {
            throw new ProtocolException(where + " must be a four-element array");
        }
        List<Sign.LineValue> result = new ArrayList<>(4);
        for (JsonNode line : value) {
            if (!line.isObject()) {
                throw new ProtocolException(where + " lines must be objects");
            }
            String text = requiredTextAllowEmpty(line, "text", where);
            String color = requiredText(line, "color", where);
            List<String> wireDecorations = stringList(line.get("decorations"), where + ".decorations");
            List<Sign.Decoration> decorations = new ArrayList<>();
            try {
                wireDecorations.forEach(
                        decoration -> decorations.add(Sign.Decoration.fromWire(decoration)));
                Sign.LineValue decoded = new Sign.LineValue(text, color, decorations);
                List<String> canonical = decoded.decorations().stream()
                        .map(Sign.Decoration::wireName)
                        .toList();
                if (!canonical.equals(wireDecorations)) {
                    throw new ProtocolException(where + ".decorations must use canonical order");
                }
                result.add(decoded);
            } catch (IllegalArgumentException exception) {
                throw new ProtocolException("invalid " + where + " line", exception);
            }
        }
        return List.copyOf(result);
    }

    private static EventPollResult decodeEventPollResult(JsonNode value)
            throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException("events.poll result must be an object");
        }
        JsonNode eventsNode = value.get("events");
        if (eventsNode == null || !eventsNode.isArray()) {
            throw new ProtocolException("events.poll result.events must be an array");
        }
        List<MinecraftEvent> events = new ArrayList<>();
        for (JsonNode event : eventsNode) {
            events.add(decodeEvent(event));
        }
        try {
            return new EventPollResult(
                    events,
                    requiredNonNegativeLong(value, "through_sequence", "events.poll result"),
                    requiredNonNegativeLong(value, "latest_sequence", "events.poll result"),
                    requiredNonNegativeLong(value, "filtered_out", "events.poll result"),
                    requiredNonNegativeLong(
                            value, "overflow_dropped_total", "events.poll result"),
                    requiredNonNegativeLong(
                            value, "capacity_dropped_total", "events.poll result"),
                    requiredNonNegativeLong(
                            value, "explicitly_discarded_total", "events.poll result"));
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid events.poll result", exception);
        }
    }

    private static MinecraftEvent decodeEvent(JsonNode value) throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException("events.poll event must be an object");
        }
        String type = requiredText(value, "type", "events.poll event");
        long sequence = requiredPositiveLong(value, "sequence", "events.poll event");
        String dimension = requiredText(value, "dimension", "events.poll event");
        BlockPosition origin = blockPosition(value.get("origin"), "events.poll event.origin");
        try {
            return switch (type) {
                case "pickaxe_poke" -> new MinecraftEvent.PickaxePoke(
                        sequence,
                        dimension,
                        origin,
                        blockPosition(value.get("pos"), "pickaxe_poke.pos"),
                        requiredText(value, "face", "pickaxe_poke"),
                        decodeBlockValue(value.get("block"), "pickaxe_poke.block"),
                        MinecraftEvent.Hand.fromWire(requiredText(value, "hand", "pickaxe_poke")),
                        requiredText(value, "item", "pickaxe_poke"));
                case "chat_posted" -> new MinecraftEvent.ChatPosted(
                        sequence,
                        dimension,
                        origin,
                        requiredTextAllowEmpty(value, "message", "chat_posted"));
                case "projectile_hit" -> new MinecraftEvent.ProjectileHit(
                        sequence,
                        dimension,
                        origin,
                        requiredText(value, "projectile", "projectile_hit"),
                        vec3(value.get("pos"), "projectile_hit.pos"),
                        decodeProjectileTarget(value.get("target")));
                default -> throw new ProtocolException("unknown protocol 23 event type: " + type);
            };
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid events.poll " + type + " event", exception);
        }
    }

    private static MinecraftEvent.ProjectileTarget decodeProjectileTarget(JsonNode value)
            throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException("projectile_hit.target must be an object");
        }
        String kind = requiredText(value, "kind", "projectile_hit.target");
        return switch (kind) {
            case "player" -> new MinecraftEvent.PlayerTarget();
            case "entity" -> new MinecraftEvent.EntityTarget(
                    requiredText(value, "handle", "projectile_hit.target"));
            case "block" -> new MinecraftEvent.BlockTarget(
                    decodeBlockValue(value.get("block"), "projectile_hit.target.block"),
                    blockPosition(value.get("pos"), "projectile_hit.target.pos"),
                    optionalNonEmptyText(value.get("face"), "projectile_hit.target.face"));
            default -> throw new ProtocolException("unknown projectile target kind: " + kind);
        };
    }

    private static BlockValue decodeBlockValue(JsonNode value) throws ProtocolException {
        return decodeBlockValue(value, "world.getBlock result");
    }

    private static BlockValue decodeBlockValue(JsonNode value, String where)
            throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException(where + " must be a BlockValue object");
        }
        if (value.size() != 2 || !value.has("block_id") || !value.has("state")) {
            throw new ProtocolException(where + " must contain exactly block_id and state");
        }
        String blockId = requiredText(value, "block_id", where);
        Map<String, Object> state = objectMap(value.get("state"), where + ".state");
        try {
            return new BlockValue(blockId, state);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("invalid " + where + " BlockValue", exception);
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

    private static String requiredTextAllowEmpty(JsonNode object, String field, String where)
            throws ProtocolException {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw new ProtocolException(where + "." + field + " must be a string");
        }
        return value.textValue();
    }

    private static Optional<String> optionalNonEmptyText(JsonNode value, String where)
            throws ProtocolException {
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new ProtocolException(where + " must be a non-empty string or null");
        }
        return Optional.of(value.textValue());
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

    private static Vec3 vec3(JsonNode value, String where) throws ProtocolException {
        if (value == null || !value.isArray() || value.size() != 3) {
            throw new ProtocolException(where + " must be a three-number array");
        }
        double[] coordinates = new double[3];
        for (int index = 0; index < coordinates.length; index++) {
            JsonNode coordinate = value.get(index);
            if (!coordinate.isNumber() || !Double.isFinite(coordinate.doubleValue())) {
                throw new ProtocolException(where + " must be a three-finite-number array");
            }
            coordinates[index] = coordinate.doubleValue();
        }
        return new Vec3(coordinates[0], coordinates[1], coordinates[2]);
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

    private static long requiredNonNegativeLong(JsonNode value, String field, String where)
            throws ProtocolException {
        JsonNode number = value.get(field);
        if (number == null || !number.isIntegralNumber() || !number.canConvertToLong()) {
            throw new ProtocolException(where + "." + field + " must be a non-negative integer");
        }
        long result = number.longValue();
        if (result < 0) {
            throw new ProtocolException(where + "." + field + " must be a non-negative integer");
        }
        return result;
    }

    private static double requiredFiniteDouble(JsonNode value, String field, String where)
            throws ProtocolException {
        JsonNode number = value.get(field);
        if (number == null || !number.isNumber() || !Double.isFinite(number.doubleValue())) {
            throw new ProtocolException(where + "." + field + " must be a finite number");
        }
        return number.doubleValue();
    }

    private static JsonNode requiredObject(JsonNode value, String where)
            throws ProtocolException {
        if (value == null || !value.isObject()) {
            throw new ProtocolException(where + " must be an object");
        }
        return value;
    }

    private static void requireNullResult(JsonNode result, String method)
            throws ProtocolException {
        if (!result.isNull()) {
            throw new ProtocolException(method + " success result must be null");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireQualifiedId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || !value.contains(":")) {
            throw new IllegalArgumentException(name + " must be fully qualified");
        }
    }

    private static Number wireNumber(double value) {
        if (value == Math.rint(value) && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
            long integral = (long) value;
            if (integral >= Integer.MIN_VALUE && integral <= Integer.MAX_VALUE) {
                return (int) integral;
            }
            return integral;
        }
        return value;
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
