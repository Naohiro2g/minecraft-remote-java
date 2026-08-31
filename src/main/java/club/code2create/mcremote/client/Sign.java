package club.code2create.mcremote.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Types used by the protocol 23 sign commands. */
public final class Sign {

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final Set<String> NAMED_COLORS = Set.of(
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gold",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "light_purple",
            "yellow",
            "white");

    private Sign() {}

    public enum Face {
        FRONT("front"),
        BACK("back");

        private final String wireName;

        Face(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        static Face fromWire(String value) {
            return switch (value) {
                case "front" -> FRONT;
                case "back" -> BACK;
                default -> throw new IllegalArgumentException("unknown sign face: " + value);
            };
        }
    }

    /** Declaration order is the protocol's canonical output order. */
    public enum Decoration {
        BOLD("bold"),
        ITALIC("italic"),
        OBFUSCATED("obfuscated"),
        STRIKETHROUGH("strikethrough"),
        UNDERLINED("underlined");

        private final String wireName;

        Decoration(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        static Decoration fromWire(String value) {
            for (Decoration decoration : values()) {
                if (decoration.wireName.equals(value)) {
                    return decoration;
                }
            }
            throw new IllegalArgumentException("unknown sign decoration: " + value);
        }
    }

    /** One writable sign line. Plain lines are encoded using the protocol string shorthand. */
    public record LineSpec(
            String text, Optional<String> color, List<Decoration> decorations) {

        public LineSpec {
            Objects.requireNonNull(text, "text");
            color = Objects.requireNonNull(color, "color").map(Sign::canonicalColor);
            decorations = canonicalDecorations(decorations);
        }

        public static LineSpec plain(String text) {
            return new LineSpec(text, Optional.empty(), List.of());
        }

        public static LineSpec styled(
                String text, String color, Decoration... decorations) {
            return new LineSpec(text, Optional.of(color), List.of(decorations));
        }

        public static LineSpec decorated(String text, Decoration... decorations) {
            return new LineSpec(text, Optional.empty(), List.of(decorations));
        }

        public boolean isPlain() {
            return color.isEmpty() && decorations.isEmpty();
        }
    }

    /** One server-canonical sign line returned by {@code world.getSign}. */
    public record LineValue(String text, String color, List<Decoration> decorations) {

        public LineValue {
            Objects.requireNonNull(text, "text");
            color = canonicalColor(color);
            decorations = canonicalDecorations(decorations);
        }
    }

    /** A partial or complete two-sided sign write. */
    public record Content(
            Optional<List<LineSpec>> front, Optional<List<LineSpec>> back) {

        public Content {
            front = copyLines(Objects.requireNonNull(front, "front"), "front");
            back = copyLines(Objects.requireNonNull(back, "back"), "back");
            if (front.isEmpty() && back.isEmpty()) {
                throw new IllegalArgumentException("at least one sign face must be present");
            }
        }

        public static Content front(List<LineSpec> lines) {
            return new Content(Optional.of(lines), Optional.empty());
        }

        public static Content back(List<LineSpec> lines) {
            return new Content(Optional.empty(), Optional.of(lines));
        }

        public static Content both(List<LineSpec> front, List<LineSpec> back) {
            return new Content(Optional.of(front), Optional.of(back));
        }
    }

    /** The complete server-canonical value returned by {@code world.getSign}. */
    public record Value(List<LineValue> front, List<LineValue> back, boolean waxed) {

        public Value {
            front = copyFour(front, "front");
            back = copyFour(back, "back");
        }
    }

    private static <T> Optional<List<T>> copyLines(Optional<List<T>> lines, String face) {
        return lines.map(value -> copyFour(value, face));
    }

    private static <T> List<T> copyFour(List<T> lines, String face) {
        Objects.requireNonNull(lines, face);
        if (lines.size() != 4) {
            throw new IllegalArgumentException(face + " must contain exactly four lines");
        }
        return List.copyOf(lines);
    }

    private static List<Decoration> canonicalDecorations(List<Decoration> values) {
        Objects.requireNonNull(values, "decorations");
        List<Decoration> copy = new ArrayList<>(values);
        copy.forEach(value -> Objects.requireNonNull(value, "decoration"));
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("decorations must not contain duplicates");
        }
        copy.sort(Comparator.comparingInt(Enum::ordinal));
        return List.copyOf(copy);
    }

    private static String canonicalColor(String value) {
        Objects.requireNonNull(value, "color");
        String color = value.toLowerCase(Locale.ROOT);
        if (NAMED_COLORS.contains(color)) {
            return color;
        }
        if (HEX_COLOR.matcher(value).matches()) {
            return value;
        }
        throw new IllegalArgumentException("invalid sign color: " + value);
    }
}
