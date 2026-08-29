package club.code2create.mcremote.client;

import java.util.Locale;
import java.util.Objects;

/** Display metadata sent when a pairing is begun. */
public record ClientIdentity(String name, String version, String locale) {

    public ClientIdentity {
        requireText(name, "name");
        requireText(version, "version");
        requireText(locale, "locale");
    }

    public static ClientIdentity defaultIdentity() {
        String implementationVersion = MinecraftRemote.class.getPackage().getImplementationVersion();
        String version = implementationVersion == null ? "development" : implementationVersion;
        String locale = Locale.getDefault().toLanguageTag();
        return new ClientIdentity("minecraft-remote-java", version, locale);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
