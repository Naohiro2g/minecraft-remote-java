package club.code2create.mcremote.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stores opaque bearer tokens scoped to a direct TCP target.
 *
 * <p>The library deliberately supplies no file-backed default. Applications that persist tokens
 * should use an operating-system credential facility or another appropriately protected store.
 */
public interface CredentialStore {

    Optional<String> load(ServerTarget target) throws MinecraftRemoteException;

    void save(ServerTarget target, String token) throws MinecraftRemoteException;

    void clear(ServerTarget target) throws MinecraftRemoteException;

    static CredentialStore none() {
        return new CredentialStore() {
            @Override
            public Optional<String> load(ServerTarget target) {
                return Optional.empty();
            }

            @Override
            public void save(ServerTarget target, String token) {}

            @Override
            public void clear(ServerTarget target) {}
        };
    }

    static CredentialStore inMemory() {
        return new CredentialStore() {
            private final Map<ServerTarget, String> tokens = new HashMap<>();

            @Override
            public synchronized Optional<String> load(ServerTarget target) {
                return Optional.ofNullable(tokens.get(target));
            }

            @Override
            public synchronized void save(ServerTarget target, String token) {
                tokens.put(target, token);
            }

            @Override
            public synchronized void clear(ServerTarget target) {
                tokens.remove(target);
            }
        };
    }
}
