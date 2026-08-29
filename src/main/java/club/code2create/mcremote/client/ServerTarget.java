package club.code2create.mcremote.client;

import java.util.Objects;

/** A direct TCP target and the scope key used for its credential. */
public record ServerTarget(String host, int port) {

    public ServerTarget {
        Objects.requireNonNull(host, "host");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    public String credentialKey() {
        return host + ":" + port;
    }
}
