package codeling.robot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RobotShapesTest {
    private static RobotCommand shape(RobotCommand.Op op, int d, int e, int operation) {
        return new RobotCommand(op, 0, 0, 0, d, e, 0, 1, operation, null);
    }

    // How many blocks of the command's box get each action (SKIP, SET, CLEAR).
    private static int[] count(RobotCommand c) {
        int[] b = RobotShapes.box(c);
        int[] n = new int[3];
        for (int x = b[0]; x <= b[3]; x++) for (int y = b[1]; y <= b[4]; y++) for (int z = b[2]; z <= b[5]; z++) n[RobotShapes.action(c, x, y, z)]++;
        return n;
    }

    @Test
    void fillOperations() {
        RobotCommand box = new RobotCommand(RobotCommand.Op.FILL, 0, 0, 0, 2, 2, 2, 1, RobotShapes.REPLACE, null);
        assertArrayEquals(new int[]{0, 27, 0}, count(box));
        assertArrayEquals(new int[]{0, 26, 1}, count(new RobotCommand(RobotCommand.Op.FILL, 2, 2, 2, 0, 0, 0, 1, RobotShapes.HOLLOW, null)));
        assertArrayEquals(new int[]{1, 26, 0}, count(new RobotCommand(RobotCommand.Op.FILL, 0, 0, 0, 2, 2, 2, 1, RobotShapes.OUTLINE, null)));
        assertEquals(27, RobotShapes.volume(RobotShapes.box(box)));
    }

    @Test
    void circleOnTheGround() {
        // Radius 2 around y: the 5 x 5 square without its 4 corners; the ring is what touches the outside.
        assertArrayEquals(new int[]{4, 21, 0}, count(shape(RobotCommand.Op.CIRCLE, 2, 1, RobotShapes.SHAPE_REPLACE)));
        assertArrayEquals(new int[]{13, 12, 0}, count(shape(RobotCommand.Op.CIRCLE, 2, 1, RobotShapes.SHAPE_OUTLINE)));
        assertArrayEquals(new int[]{4, 12, 9}, count(shape(RobotCommand.Op.CIRCLE, 2, 1, RobotShapes.SHAPE_HOLLOW)));
        // Flat across its axis.
        assertArrayEquals(new int[]{-2, 0, -2, 2, 0, 2}, RobotShapes.box(shape(RobotCommand.Op.CIRCLE, 2, 1, 0)));
        assertArrayEquals(new int[]{0, -3, -3, 0, 3, 3}, RobotShapes.box(shape(RobotCommand.Op.CIRCLE, 3, 0, 0)));
    }

    @Test
    void sphere() {
        // Radius 1: the centre, its 6 faces and 12 edges, not the 8 corners; only the centre is inside the shell.
        assertArrayEquals(new int[]{8, 19, 0}, count(shape(RobotCommand.Op.SPHERE, 1, 0, RobotShapes.SHAPE_REPLACE)));
        assertArrayEquals(new int[]{8, 18, 1}, count(shape(RobotCommand.Op.SPHERE, 1, 0, RobotShapes.SHAPE_HOLLOW)));
    }

    @Test
    void lineIncludesBothEnds() {
        List<int[]> line = RobotShapes.line(new RobotCommand(RobotCommand.Op.LINE, 0, 0, 0, 5, -2, 3, 1, 0, null));
        assertEquals(6, line.size());
        assertArrayEquals(new int[]{0, 0, 0}, line.getFirst());
        assertArrayEquals(new int[]{5, -2, 3}, line.getLast());
        for (int i = 1; i < line.size(); i++) {
            for (int k = 0; k < 3; k++) assertEquals(true, Math.abs(line.get(i)[k] - line.get(i - 1)[k]) <= 1, "one step at a time");
        }
        assertEquals(1, RobotShapes.line(new RobotCommand(RobotCommand.Op.LINE, 4, 4, 4, 4, 4, 4, 1, 0, null)).size());
    }

    // Issue #182: a perfect maze. Cells at odd x and z; every cell reached from the first, the corridors a tree
    // (cells - 1 openings between them), the border closed but for the entrance (north) and the exit (south).
    @Test
    void mazeIsPerfect() {
        for (int[] size : new int[][]{{5, 5}, {7, 11}, {6, 10}, {32, 32}, {31, 5}}) {
            for (long seed = 0; seed < 20; seed++) {
                boolean[][] open = RobotShapes.maze(size[0], size[1], seed);
                int w = open.length, l = open[0].length;
                assertEquals(size[0] % 2 == 0 ? size[0] - 1 : size[0], w, "an even width builds one block less");
                assertEquals(size[1] % 2 == 0 ? size[1] - 1 : size[1], l, "an even length builds one block less");
                int cells = (w / 2) * (l / 2), openings = 0, north = 0, south = 0;
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < l; z++) {
                        boolean border = x == 0 || z == 0 || x == w - 1 || z == l - 1;
                        if (x % 2 == 1 && z % 2 == 1) assertTrue(open[x][z], "every cell is corridor");
                        else if (x % 2 == 0 && z % 2 == 0) assertFalse(open[x][z], "every pillar is wall");
                        else if (!border && open[x][z]) openings++;
                        if (border && open[x][z]) {
                            assertTrue(x % 2 == 1 && (z == 0 || z == l - 1), "only the entrance and exit, in front of a cell");
                            if (z == 0) north++;
                            else south++;
                        }
                    }
                }
                assertEquals(cells - 1, openings, "a tree: exactly one route between two cells");
                assertEquals(1, north, "one entrance");
                assertEquals(1, south, "one exit");
                assertEquals(cells, reached(open), "every cell reachable");
            }
        }
    }

    @Test
    void mazeSameSeedSameLayout() {
        assertTrue(Arrays.deepEquals(RobotShapes.maze(21, 15, 42), RobotShapes.maze(21, 15, 42)));
        assertFalse(Arrays.deepEquals(RobotShapes.maze(21, 15, 42), RobotShapes.maze(21, 15, 43)));
        // Pinned: a change to the generator would change every shared maze number.
        boolean[][] small = RobotShapes.maze(5, 5, 42);
        StringBuilder rows = new StringBuilder();
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 5; x++) rows.append(small[x][z] ? '.' : '#');
            rows.append('/');
        }
        assertEquals(MAZE_42, rows.toString());
    }

    private static final String MAZE_42 = "#.###/#.#.#/#.#.#/#...#/#.###/";

    @Test
    void mazeActions() {
        boolean[][] open = RobotShapes.maze(5, 5, 1);
        assertEquals(RobotShapes.FLOOR, RobotShapes.mazeAction(open, 1, 0, 1));
        assertEquals(RobotShapes.CLEAR, RobotShapes.mazeAction(open, 1, 2, 1));
        assertEquals(RobotShapes.SET, RobotShapes.mazeAction(open, 0, 0, 0));
        assertEquals(RobotShapes.SET, RobotShapes.mazeAction(open, 2, 1, 2));
        // The corner is at foot height: the floor replaces the ground layer under it (y 63), walls at 64 and 65.
        assertArrayEquals(new int[]{10, 63, -3, 14, 65, 1},
                RobotShapes.box(new RobotCommand(RobotCommand.Op.MAZE, 10, 64, -3, 5, 6, 0, 1, 2, null)));
    }

    // Cells reached from the first cell through corridors inside the border.
    private static int reached(boolean[][] open) {
        int w = open.length, l = open[0].length, cells = 0;
        boolean[][] seen = new boolean[w][l];
        ArrayDeque<int[]> todo = new ArrayDeque<>(List.of(new int[]{1, 1}));
        seen[1][1] = true;
        while (!todo.isEmpty()) {
            int[] at = todo.pop();
            if (at[0] % 2 == 1 && at[1] % 2 == 1) cells++;
            for (int[] s : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = at[0] + s[0], z = at[1] + s[1];
                if (x > 0 && z > 0 && x < w - 1 && z < l - 1 && open[x][z] && !seen[x][z]) {
                    seen[x][z] = true;
                    todo.push(new int[]{x, z});
                }
            }
        }
        return cells;
    }
}
