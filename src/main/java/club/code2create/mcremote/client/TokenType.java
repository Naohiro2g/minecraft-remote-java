package club.code2create.mcremote.client;

/** The one token kind requested by an initial pairing operation. */
public enum TokenType {
    SESSION("session"),
    LONG_LIVED("long_lived");

    private final String wireName;

    TokenType(String wireName) {
        this.wireName = wireName;
    }

    String wireName() {
        return wireName;
    }
}
