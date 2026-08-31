package club.code2create.mcremote.examples;

import com.microsoft.credentialstorage.SecretStore;
import com.microsoft.credentialstorage.model.StoredToken;
import com.microsoft.credentialstorage.model.StoredTokenType;
import java.util.Arrays;

final class MicrosoftTokenBackend implements TokenBackend {

    private final SecretStore<StoredToken> store;

    MicrosoftTokenBackend(SecretStore<StoredToken> store) {
        this.store = store;
    }

    @Override
    public char[] load(String key) {
        StoredToken stored = store.get(key);
        if (stored == null) {
            return null;
        }
        try {
            char[] value = stored.getValue();
            return Arrays.copyOf(value, value.length);
        } finally {
            stored.clear();
        }
    }

    @Override
    public boolean save(String key, char[] token) {
        StoredToken stored = new StoredToken(token, StoredTokenType.UNKNOWN);
        try {
            return store.add(key, stored);
        } finally {
            stored.clear();
        }
    }

    @Override
    public boolean delete(String key) {
        return store.delete(key);
    }
}
