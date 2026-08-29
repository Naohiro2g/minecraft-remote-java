package club.code2create.mcremote.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-authoritative identity, compatibility, and build context from {@code hello}.
 * Authentication-related optional values are empty when the server does not advertise them.
 */
public record HelloResult(
        String protocol,
        String minecraftVersion,
        List<String> supportedMinecraftVersions,
        Optional<String> catalogHash,
        Map<String, Object> worldConstants,
        Optional<String> session,
        Optional<String> player,
        String dimension,
        BlockPosition origin,
        Map<String, Object> permissions,
        Optional<String> server) {

    public HelloResult {
        supportedMinecraftVersions = List.copyOf(supportedMinecraftVersions);
        catalogHash = catalogHash == null ? Optional.empty() : catalogHash;
        worldConstants = immutableNullableMap(worldConstants);
        session = session == null ? Optional.empty() : session;
        player = player == null ? Optional.empty() : player;
        permissions = immutableNullableMap(permissions);
        server = server == null ? Optional.empty() : server;
    }

    private static Map<String, Object> immutableNullableMap(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
