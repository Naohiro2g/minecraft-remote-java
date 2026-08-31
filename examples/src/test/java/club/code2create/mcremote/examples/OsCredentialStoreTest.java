package club.code2create.mcremote.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import club.code2create.mcremote.client.MinecraftRemoteException;
import club.code2create.mcremote.client.ServerTarget;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OsCredentialStoreTest {

    @Test
    void reloadsAcrossInstancesAndSeparatesTargetsWithoutExposingThemInKeys() throws Exception {
        FakeTokenBackend backend = new FakeTokenBackend();
        ServerTarget first = new ServerTarget("private-one.example", 25575);
        ServerTarget second = new ServerTarget("private-two.example", 25575);

        new OsCredentialStore(backend).save(first, "mcrs_first_secret");
        OsCredentialStore restarted = new OsCredentialStore(backend);

        assertEquals("mcrs_first_secret", restarted.load(first).orElseThrow());
        assertTrue(restarted.load(second).isEmpty());
        assertEquals(1, backend.values.size());
        String physicalKey = backend.values.keySet().iterator().next();
        assertTrue(physicalKey.startsWith("mcremote-java-examples/session/tcp/v1/"));
        assertFalse(physicalKey.contains(first.host()));
        assertFalse(physicalKey.contains(Integer.toString(first.port())));
    }

    @Test
    void clearIsIdempotentAndOnlyDeletesTheSelectedTarget() throws Exception {
        FakeTokenBackend backend = new FakeTokenBackend();
        OsCredentialStore store = new OsCredentialStore(backend);
        ServerTarget first = new ServerTarget("first.example", 25575);
        ServerTarget second = new ServerTarget("second.example", 25575);
        store.save(first, "mcrs_first");
        store.save(second, "mcrs_second");

        store.clear(first);
        store.clear(first);

        assertTrue(store.load(first).isEmpty());
        assertEquals("mcrs_second", store.load(second).orElseThrow());
    }

    @Test
    void backendFailureDoesNotLeakTokenOrTarget() {
        FakeTokenBackend backend = new FakeTokenBackend();
        backend.saveSucceeds = false;
        OsCredentialStore store = new OsCredentialStore(backend);
        ServerTarget target = new ServerTarget("private.example", 25575);

        MinecraftRemoteException exception = assertThrows(
                MinecraftRemoteException.class,
                () -> store.save(target, "mcrs_do_not_log"));

        assertFalse(exception.getMessage().contains("mcrs_do_not_log"));
        assertFalse(exception.getMessage().contains("private.example"));
        assertFalse(exception.getMessage().contains("25575"));
    }

    private static final class FakeTokenBackend implements TokenBackend {

        private final Map<String, char[]> values = new HashMap<>();
        private boolean saveSucceeds = true;

        @Override
        public char[] load(String key) {
            char[] value = values.get(key);
            return value == null ? null : Arrays.copyOf(value, value.length);
        }

        @Override
        public boolean save(String key, char[] token) {
            if (!saveSucceeds) {
                return false;
            }
            values.put(key, Arrays.copyOf(token, token.length));
            return true;
        }

        @Override
        public boolean delete(String key) {
            return values.remove(key) != null;
        }
    }
}
