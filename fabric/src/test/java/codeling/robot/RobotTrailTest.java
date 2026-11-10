package codeling.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class RobotTrailTest {
    /** Path blocks and walls (both solid) in an otherwise empty, loaded world. */
    private static final class World implements RobotPath.Grid {
        final Set<BlockPos> path = new HashSet<>();
        final Set<BlockPos> walls = new HashSet<>();

        public boolean loaded(BlockPos pos) {
            return true;
        }

        public boolean blocked(BlockPos pos) {
            return path.contains(pos) || walls.contains(pos);
        }

        public boolean opens(BlockPos pos) {
            return false;
        }
    }

    private static RobotTrail follow(World world, boolean walking) {
        return follow(world, new BlockPos(0, 1, 0), Direction.EAST, walking, 0);
    }

    private static RobotTrail follow(World world, BlockPos start, Direction facing, boolean walking, int side) {
        RobotTrail trail = new RobotTrail(world, world.path::contains, start, facing, walking, side);
        while (!trail.finished()) trail.plan(RobotTrail.READS_PER_BLOCK * 7);
        return trail;
    }

    private static RobotTrail keep(World world, int side) {
        return follow(world, new BlockPos(0, 1, 0), Direction.EAST, true, side);
    }

    @Test
    void stopsAfterTheLimit() {
        World world = new World();
        for (int x = 0; x <= RobotTrail.MAX_BLOCKS + 10; x++) world.path.add(new BlockPos(x, 0, 0));
        RobotTrail trail = follow(world, true);
        assertEquals(RobotTrail.LIMIT, trail.stop());
        assertEquals(new BlockPos(RobotTrail.MAX_BLOCKS, 1, 0), trail.route().getLast());
    }

    @Test
    void aLoopEndsBackAtTheStart() {
        World world = new World();
        for (int i = 0; i < 4; i++) {
            world.path.add(new BlockPos(i, 0, 0));
            world.path.add(new BlockPos(4, 0, i));
            world.path.add(new BlockPos(4 - i, 0, 4));
            world.path.add(new BlockPos(0, 0, 4 - i));
        }
        RobotTrail trail = follow(world, true);
        assertEquals(RobotTrail.END, trail.stop());
        assertEquals(17, trail.route().size());
        assertEquals(new BlockPos(0, 1, 0), trail.route().getLast());
        // Issue #180: keeping to a side goes round once too, back at the start when it would set off again.
        assertEquals(trail.route(), keep(world, RobotCommand.LEFT_TURN).route());
        assertEquals(RobotTrail.END, keep(world, RobotCommand.RIGHT_TURN).stop());
    }

    // Issue #180: east to a T, the north branch a dead end against a wall, the south branch open at its end.
    private static World tee() {
        World world = new World();
        for (int i = 0; i < 4; i++) {
            world.path.add(new BlockPos(i, 0, 0));
            world.path.add(new BlockPos(3, 0, -i));
            world.path.add(new BlockPos(3, 0, i));
        }
        world.walls.add(new BlockPos(3, 1, -4));
        return world;
    }

    // Keeping left takes the north branch, turns round at the wall, walks back and goes on south to the open end.
    @Test
    void keepLeftTurnsRoundAtADeadEnd() {
        RobotTrail trail = keep(tee(), RobotCommand.LEFT_TURN);
        assertEquals(RobotTrail.END, trail.stop());
        assertEquals(new BlockPos(3, 1, 3), trail.route().getLast());
        assertEquals(new BlockPos(3, 1, -3), trail.route().get(6));
        assertEquals(3 + 3 + 3 + 3 + 1, trail.route().size());
        assertEquals(RobotTrail.JUNCTION, follow(tee(), false).stop());
    }

    // Keeping right takes the south branch at once.
    @Test
    void keepRightTakesTheRightBranch() {
        RobotTrail trail = keep(tee(), RobotCommand.RIGHT_TURN);
        assertEquals(RobotTrail.END, trail.stop());
        assertEquals(List.of(new BlockPos(0, 1, 0), new BlockPos(1, 1, 0), new BlockPos(2, 1, 0), new BlockPos(3, 1, 0),
                new BlockPos(3, 1, 1), new BlockPos(3, 1, 2), new BlockPos(3, 1, 3)), trail.route());
    }

    // A straight path with a wall at the far end: there and back, ending where it started (no wall behind the start).
    @Test
    void aWallAtTheEndTurnsRound() {
        World world = new World();
        for (int x = 0; x < 4; x++) world.path.add(new BlockPos(x, 0, 0));
        world.walls.add(new BlockPos(4, 1, 0));
        RobotTrail trail = keep(world, RobotCommand.LEFT_TURN);
        assertEquals(RobotTrail.END, trail.stop());
        assertEquals(7, trail.route().size());
        assertEquals(new BlockPos(0, 1, 0), trail.route().getLast());
    }

    // The biggest maze shapes.maze builds (31 x 31, a floor of path blocks and walls one block high are enough here):
    // from the entrance a wall follower reaches the exit, following again after each LIMIT like the guide's loop.
    @Test
    void solvesTheBiggestMazeFollowingAgainAfterTheLimit() {
        int longest = 0;
        for (int seed = 0; seed < 10; seed++) {
            boolean[][] open = RobotShapes.maze(32, 32, seed);
            World world = new World();
            int entrance = 0, exit = 0;
            for (int x = 0; x < 31; x++) {
                for (int z = 0; z < 31; z++) world.path.add(new BlockPos(x, 0, z));
                for (int z = 0; z < 31; z++) if (!open[x][z]) world.walls.add(new BlockPos(x, 1, z));
                if (open[x][0]) entrance = x;
                if (open[x][30]) exit = x;
            }
            world.path.removeIf(pos -> !open[pos.getX()][pos.getZ()]);
            for (int side : new int[]{RobotCommand.LEFT_TURN, RobotCommand.RIGHT_TURN}) {
                BlockPos at = new BlockPos(entrance, 1, 0);
                Direction facing = Direction.SOUTH;
                int calls = 0, blocks = 0;
                RobotTrail trail;
                do {
                    trail = follow(world, at, facing, true, side);
                    List<BlockPos> route = trail.route();
                    if (route.size() > 1) facing = RobotPath.direction(route.get(route.size() - 2), route.getLast());
                    at = route.getLast();
                    blocks += route.size() - 1;
                    calls++;
                } while (trail.stop() == RobotTrail.LIMIT && calls < 20);
                assertEquals(RobotTrail.END, trail.stop(), "seed " + seed);
                assertEquals(new BlockPos(exit, 1, 30), at, "seed " + seed);
                longest = Math.max(longest, blocks);
            }
        }
        assertTrue(longest > RobotTrail.MAX_BLOCKS, "some maze needs more than one call: " + longest);
    }

    // Flying, a step up or down is a move of its own: up then over, over then down.
    @Test
    void fliesStepsInTwoMoves() {
        World world = new World();
        world.path.addAll(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 1, 0), new BlockPos(2, 0, 0)));
        RobotTrail trail = follow(world, false);
        assertEquals(RobotTrail.END, trail.stop());
        assertEquals(List.of(new BlockPos(0, 1, 0), new BlockPos(0, 2, 0), new BlockPos(1, 2, 0), new BlockPos(2, 2, 0), new BlockPos(2, 1, 0)),
                trail.route());
        assertEquals(List.of(new BlockPos(0, 1, 0), new BlockPos(1, 2, 0), new BlockPos(2, 1, 0)), follow(world, true).route());
    }
}
