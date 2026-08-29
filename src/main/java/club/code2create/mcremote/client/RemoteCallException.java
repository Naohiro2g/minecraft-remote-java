package club.code2create.mcremote.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A JSON-RPC error returned by Minecraft Remote. */
public final class RemoteCallException extends MinecraftRemoteException {

    private final int code;
    private final String remoteMessage;
    private final Map<String, Object> data;

    public RemoteCallException(int code, String remoteMessage, Map<String, Object> data) {
        super(formatMessage(code, remoteMessage, data));
        this.code = code;
        this.remoteMessage = remoteMessage;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public int code() {
        return code;
    }

    public String remoteMessage() {
        return remoteMessage;
    }

    public Map<String, Object> data() {
        return data;
    }

    public Optional<String> reason() {
        Object reason = data.get("reason");
        return reason instanceof String value ? Optional.of(value) : Optional.empty();
    }

    private static String formatMessage(int code, String message, Map<String, Object> data) {
        Object reason = data.get("reason");
        String suffix = reason instanceof String value ? " (reason=" + value + ")" : "";
        return "Minecraft Remote call failed: " + code + " " + message + suffix;
    }
}
