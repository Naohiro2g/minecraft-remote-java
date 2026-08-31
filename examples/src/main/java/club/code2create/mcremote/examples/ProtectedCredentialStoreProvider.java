package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.CredentialStore;
import java.util.Optional;

@FunctionalInterface
interface ProtectedCredentialStoreProvider {

    Optional<CredentialStore> open();
}
