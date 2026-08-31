package club.code2create.mcremote.examples;

interface TokenBackend {

    char[] load(String key);

    boolean save(String key, char[] token);

    boolean delete(String key);
}
