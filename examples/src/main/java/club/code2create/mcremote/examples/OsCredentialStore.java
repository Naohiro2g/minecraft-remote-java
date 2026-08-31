package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.CredentialStore;
import club.code2create.mcremote.client.MinecraftRemoteException;
import club.code2create.mcremote.client.ServerTarget;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

/** CredentialStore adapter for an operating-system protected secret backend. */
final class OsCredentialStore implements CredentialStore {

    private static final String APPLICATION_ID = "club.code2create.mcremote.examples";
    private static final String CREDENTIAL_TYPE = "session";
    private static final String TRANSPORT = "tcp";
    private static final String KEY_PREFIX = "mcremote-java-examples/session/tcp/v1/";

    private final TokenBackend backend;

    OsCredentialStore(TokenBackend backend) {
        this.backend = backend;
    }

    @Override
    public Optional<String> load(ServerTarget target) throws MinecraftRemoteException {
        char[] token = null;
        try {
            token = backend.load(keyFor(target));
            return token == null ? Optional.empty() : Optional.of(new String(token));
        } catch (RuntimeException | LinkageError exception) {
            throw failure("read", exception);
        } finally {
            clear(token);
        }
    }

    @Override
    public void save(ServerTarget target, String token) throws MinecraftRemoteException {
        char[] value = token.toCharArray();
        try {
            if (!backend.save(keyFor(target), value)) {
                throw new MinecraftRemoteException("OS credential store could not save the session token");
            }
        } catch (MinecraftRemoteException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            throw failure("save", exception);
        } finally {
            clear(value);
        }
    }

    @Override
    public void clear(ServerTarget target) throws MinecraftRemoteException {
        String key = keyFor(target);
        char[] existing = null;
        try {
            existing = backend.load(key);
            if (existing != null && !backend.delete(key)) {
                throw new MinecraftRemoteException("OS credential store could not delete the session token");
            }
        } catch (MinecraftRemoteException exception) {
            throw exception;
        } catch (RuntimeException | LinkageError exception) {
            throw failure("delete", exception);
        } finally {
            clear(existing);
        }
    }

    static String keyFor(ServerTarget target) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, APPLICATION_ID);
            update(digest, CREDENTIAL_TYPE);
            update(digest, TRANSPORT);
            update(digest, target.host());
            update(digest, Integer.toString(target.port()));
            return KEY_PREFIX + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("Java runtime does not provide SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
        digest.update(encoded);
    }

    private static void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private static MinecraftRemoteException failure(String operation, Throwable cause) {
        return new MinecraftRemoteException(
                "OS credential store failed during " + operation, cause);
    }
}
