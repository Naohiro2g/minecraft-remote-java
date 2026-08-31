package club.code2create.mcremote.client;

/** An origin-relative continuous position or vector. */
public record Vec3(double x, double y, double z) {

    public Vec3 {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
