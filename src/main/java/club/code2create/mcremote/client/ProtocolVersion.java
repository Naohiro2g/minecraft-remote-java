package club.code2create.mcremote.client;

record ProtocolVersion(int major, int minor, int patch) {

    static ProtocolVersion parse(String value) throws ProtocolException {
        if (value == null) {
            throw new ProtocolException("protocol version is missing");
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3) {
            throw new ProtocolException("invalid protocol version: " + value);
        }
        try {
            return new ProtocolVersion(
                    parsePart(parts[0]), parsePart(parts[1]), parsePart(parts[2]));
        } catch (NumberFormatException exception) {
            throw new ProtocolException("invalid protocol version: " + value, exception);
        }
    }

    boolean satisfies(ProtocolVersion required) {
        return major == required.major && minor >= required.minor;
    }

    private static int parsePart(String part) {
        if (part.isEmpty() || (part.length() > 1 && part.charAt(0) == '0')) {
            throw new NumberFormatException("non-canonical semver part");
        }
        int parsed = Integer.parseInt(part);
        if (parsed < 0) {
            throw new NumberFormatException("negative semver part");
        }
        return parsed;
    }
}
