package club.code2create.mcremote.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import club.code2create.mcremote.client.CredentialStore;
import club.code2create.mcremote.client.ServerTarget;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CredentialStoreSelectorTest {

    @Test
    void noSaveNeverOpensThePersistentStore() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        Harness harness = new Harness(() -> {
            opens.incrementAndGet();
            return Optional.of(CredentialStore.inMemory());
        });
        ServerTarget target = new ServerTarget("private.example", 25575);

        CredentialStore selected = harness.selector.select(
                CredentialMode.NO_SAVE, new Scanner(""));
        selected.save(target, "mcrs_secret");

        assertEquals(0, opens.get());
        assertEquals("mcrs_secret", selected.load(target).orElseThrow());
    }

    @Test
    void promptHasNoDefaultAndUsesExplicitSaveChoice() {
        CredentialStore persistent = CredentialStore.inMemory();
        Harness harness = new Harness(() -> Optional.of(persistent));

        CredentialStore selected = harness.selector.select(
                CredentialMode.PROMPT, new Scanner("\nsave\n"));

        assertEquals(persistent, selected);
        assertTrue(harness.output().contains("既定値はありません"));
    }

    @Test
    void unavailableProtectedStoreFallsBackToFreshMemoryWithoutSecretsInWarning()
            throws Exception {
        Harness harness = new Harness(Optional::empty);
        ServerTarget target = new ServerTarget("private.example", 25575);
        CredentialStore first = harness.selector.select(CredentialMode.SAVE, new Scanner(""));
        first.save(target, "mcrs_secret");
        CredentialStore second = harness.selector.select(CredentialMode.SAVE, new Scanner(""));

        assertTrue(second.load(target).isEmpty());
        assertFalse(harness.output().contains("private.example"));
        assertFalse(harness.output().contains("mcrs_secret"));
        assertTrue(harness.output().contains("次回もpairingが必要"));
    }

    @Test
    void forgetClearsOnlyLocalTokenAndExplainsItsBoundary() throws Exception {
        CredentialStore persistent = CredentialStore.inMemory();
        ServerTarget target = new ServerTarget("private.example", 25575);
        persistent.save(target, "mcrs_secret");
        Harness harness = new Harness(() -> Optional.of(persistent));

        harness.selector.forget(target);

        assertTrue(persistent.load(target).isEmpty());
        assertTrue(harness.output().contains("local session tokenを削除"));
        assertTrue(harness.output().contains("revokeまたはlogoutした操作ではありません"));
    }

    private static final class Harness {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CredentialStoreSelector selector;

        private Harness(ProtectedCredentialStoreProvider provider) {
            selector = new CredentialStoreSelector(
                    provider, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        }

        private String output() {
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
