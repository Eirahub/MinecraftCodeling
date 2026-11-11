package codeling.robot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bulk building (issue #90): which blocks blocks.fill, shapes.line, shapes.circle and shapes.sphere change, and how.
 * Plain Java so the shapes are unit-testable without Minecraft; RobotManager walks the box and places through the
 * FakePlayer. The coordinates are in the command: a box (fill, line) {@code a, b, c} to {@code d, e, f}; a circle or
 * sphere has its centre in {@code a, b, c}, the radius in {@code d} and the circle's axis in {@code e}. A maze (issue
 * #182) has its north-west corner in {@code a, b, c}, the width (x) in {@code d}, the length (z) in {@code e} and
 * the seed in {@code f}; {@code g} is the wall block and {@code h} the floor block. The corner is at foot height, as a
 * fill's corner is: the walls stand from {@code b} up and the floor replaces the ground layer below it.
 */
public final class RobotShapes {
    /** What happens at one block: nothing, set it to the block, or clear it (break it to air). */
    public static final int SKIP = 0, SET = 1, CLEAR = 2;
    /** The maze's floor: set it to the second block ({@code h}) instead of the first. */
    public static final int FLOOR = 3;
    /** FillOperation in the editor (MakeCode's names): Replace, Hollow, Outline, Keep, Destroy. */
    public static final int REPLACE = 0, HOLLOW = 1, OUTLINE = 2, KEEP = 3, DESTROY = 4;
    /** ShapeOperation in the editor (MakeCode's names): Replace (filled), Outline (shell only), Hollow (shell, inside cleared). */
    public static final int SHAPE_REPLACE = 0, SHAPE_OUTLINE = 1, SHAPE_HOLLOW = 2;

    private RobotShapes() {
    }

    /** The box the shape lies in: min x, y, z, max x, y, z. Every block it changes is inside. */
    public static int[] box(RobotCommand c) {
        return switch (c.op()) {
            case FILL, LINE -> new int[]{Math.min(c.a(), c.d()), Math.min(c.b(), c.e()), Math.min(c.c(), c.f()),
                    Math.max(c.a(), c.d()), Math.max(c.b(), c.e()), Math.max(c.c(), c.f())};
            case CIRCLE -> {
                int r = c.d();
                // Flat on the circle's axis (0 x, 1 y, 2 z).
                yield new int[]{c.a() - (c.e() == 0 ? 0 : r), c.b() - (c.e() == 1 ? 0 : r), c.c() - (c.e() == 2 ? 0 : r),
                        c.a() + (c.e() == 0 ? 0 : r), c.b() + (c.e() == 1 ? 0 : r), c.c() + (c.e() == 2 ? 0 : r)};
            }
            case SPHERE -> new int[]{c.a() - c.d(), c.b() - c.d(), c.c() - c.d(), c.a() + c.d(), c.b() + c.d(), c.c() + c.d()};
            // The floor layer (the ground under the corner) and two layers of walls above it.
            case MAZE -> new int[]{c.a(), c.b() - 1, c.c(), c.a() + odd(c.d()) - 1, c.b() + 1, c.c() + odd(c.e()) - 1};
            default -> throw new IllegalArgumentException("not a building op " + c.op());
        };
    }

    public static long volume(int[] box) {
        return (long) (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
    }

    /** What the command does at x, y, z inside its {@link #box}. Not for LINE, whose blocks come from {@link #line}. */
    public static int action(RobotCommand c, int x, int y, int z) {
        return switch (c.op()) {
            case FILL -> {
                int[] b = box(c);
                boolean shell = x == b[0] || x == b[3] || y == b[1] || y == b[4] || z == b[2] || z == b[5];
                yield switch (c.h()) {
                    case HOLLOW -> shell ? SET : CLEAR;
                    case OUTLINE -> shell ? SET : SKIP;
                    default -> SET;
                };
            }
            case CIRCLE, SPHERE -> {
                int dx = x - c.a(), dy = y - c.b(), dz = z - c.c();
                if (!inside(c, dx, dy, dz)) yield SKIP;
                // On the shell when a neighbour (in the circle's plane, or any of six for a sphere) is outside.
                boolean shell = false;
                for (int[] n : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                    if (c.op() == RobotCommand.Op.CIRCLE && n[c.e()] != 0) continue;
                    shell |= !inside(c, dx + n[0], dy + n[1], dz + n[2]);
                }
                yield shell || c.h() == SHAPE_REPLACE ? SET : c.h() == SHAPE_HOLLOW ? CLEAR : SKIP;
            }
            default -> throw new IllegalArgumentException("no action for " + c.op());
        };
    }

    // Within radius + 0.5 of the centre (rounds like a drawn circle), and on the circle's plane for a circle.
    private static boolean inside(RobotCommand c, int dx, int dy, int dz) {
        if (c.op() == RobotCommand.Op.CIRCLE && new int[]{dx, dy, dz}[c.e()] != 0) return false;
        double r = c.d() + 0.5;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /** The blocks of a straight line from one corner to the other (3D Bresenham), both ends included. */
    public static List<int[]> line(RobotCommand c) {
        int[] p = {c.a(), c.b(), c.c()};
        int[] d = {Math.abs(c.d() - c.a()), Math.abs(c.e() - c.b()), Math.abs(c.f() - c.c())};
        int[] s = {Integer.signum(c.d() - c.a()), Integer.signum(c.e() - c.b()), Integer.signum(c.f() - c.c())};
        int main = d[0] >= d[1] && d[0] >= d[2] ? 0 : d[1] >= d[2] ? 1 : 2;
        int[] err = new int[3];
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i <= d[main]; i++) {
            points.add(p.clone());
            for (int k = 0; k < 3; k++) {
                if (k == main) continue;
                err[k] += 2 * d[k];
                if (err[k] > d[main]) {
                    p[k] += s[k];
                    err[k] -= 2 * d[main];
                }
            }
            p[main] += s[main];
        }
        return points;
    }

    // A maze needs an odd size (walls on both edges, cells between): an even width or length builds one block less.
    private static int odd(int n) {
        return n % 2 == 0 ? n - 1 : n;
    }

    /**
     * A perfect maze (issue #182): exactly one route between any two cells. {@code open[x][z]} is true for corridor,
     * false for wall, with x across the width and z along the length, both made odd. Cells are at odd x and z, the
     * walls between them; a recursive backtracker (with its own stack) opens one wall to each new cell. The entrance
     * is a gap in the north edge (z 0) and the exit in the south edge (last z), each in front of a cell. The same seed
     * always gives the same maze: java.util.Random's algorithm is fixed by its specification.
     */
    public static boolean[][] maze(int width, int length, long seed) {
        int w = odd(width), l = odd(length);
        boolean[][] open = new boolean[w][l];
        Random random = new Random(seed);
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        open[1][1] = true;
        stack.push(new int[]{1, 1});
        while (!stack.isEmpty()) {
            int[] at = stack.peek();
            List<int[]> ways = new ArrayList<>();
            for (int[] s : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                int x = at[0] + s[0], z = at[1] + s[1];
                if (x > 0 && x < w && z > 0 && z < l && !open[x][z]) ways.add(new int[]{x, z});
            }
            if (ways.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] next = ways.get(random.nextInt(ways.size()));
            open[(at[0] + next[0]) / 2][(at[1] + next[1]) / 2] = true;
            open[next[0]][next[1]] = true;
            stack.push(next);
        }
        open[1 + 2 * random.nextInt(w / 2)][0] = true;
        open[1 + 2 * random.nextInt(w / 2)][l - 1] = true;
        return open;
    }

    /** What a maze does {@code dx, dy, dz} from the floor's corner ({@link #box}'s minimum): walls of the wall block, corridors of floor with air above. */
    public static int mazeAction(boolean[][] open, int dx, int dy, int dz) {
        if (!open[dx][dz]) return SET;
        return dy == 0 ? FLOOR : CLEAR;
    }
}
