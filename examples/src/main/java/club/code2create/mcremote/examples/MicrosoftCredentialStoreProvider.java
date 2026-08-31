package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.CredentialStore;
import com.microsoft.credentialstorage.SecretStore;
import com.microsoft.credentialstorage.StorageProvider;
import com.microsoft.credentialstorage.model.StoredToken;
import java.util.Optional;

final class MicrosoftCredentialStoreProvider implements ProtectedCredentialStoreProvider {

    @Override
    public Optional<CredentialStore> open() {
        try {
            SecretStore<StoredToken> backend = StorageProvider.getTokenStorage(
                    true, StorageProvider.SecureOption.REQUIRED);
            if (backend == null || !backend.isSecure()) {
                return Optional.empty();
            }
            return Optional.of(new OsCredentialStore(new MicrosoftTokenBackend(backend)));
        } catch (RuntimeException | LinkageError exception) {
            return Optional.empty();
        }
    }
}
