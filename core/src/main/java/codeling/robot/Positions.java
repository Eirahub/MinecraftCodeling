package codeling.robot;

import java.util.List;

/**
 * Resolves editor positions to world block positions (architecture section 5, issues #24 and #25).
 * Plain Java so the math is unit-tested without Minecraft.
 */
public final class Positions {
    /** Position kinds as the editor's {@code Position} holds them: world {@code world()}, relative {@code pos()} (~), local {@code posLocal()} (^). */
    public static final int WORLD = 0, RELATIVE = 1, LOCAL = 2;
    /** Minecraft's world border; also keeps every sum below inside an int. */
    public static final int MAX_COORDINATE = 30_000_000;

    private Positions() {
    }

    /**
     * World block position of {@code (x, y, z)} for an owner whose feet are in block {@code (ox, oy, oz)} and who faces
     * the horizontal unit step {@code (fx, fz)} (south is {@code 0, 1}). Relative keeps the world axes; local turns
     * with the owner: x to the left, y up, z forward.
     */
    /** Plain words for a RELATIVE or LOCAL offset, for the log: "2 east, 0 up, 2 south" or "1 left, 0 up, 3 forward". */
    public static String describe(int kind, int x, int y, int z) {
        boolean local = kind == LOCAL;
        return part(x, local ? "left" : "east", local ? "right" : "west") + ", " + part(y, "up", "down") + ", "
                + part(z, local ? "forward" : "south", local ? "back" : "north");
    }

    private static String part(int v, String positive, String negative) {
        return Math.abs(v) + " " + (v >= 0 ? positive : negative);
    }

    public static List<Integer> toWorld(int kind, int x, int y, int z, int ox, int oy, int oz, int fx, int fz) {
        return switch (kind) {
            case RELATIVE -> List.of(ox + x, oy + y, oz + z);
            // left of forward (fx, fz) is (fz, -fx): facing south (+z) the left hand points east (+x)
            case LOCAL -> List.of(ox + x * fz + z * fx, oy + y, oz - x * fx + z * fz);
            default -> List.of(x, y, z);
        };
    }
}
