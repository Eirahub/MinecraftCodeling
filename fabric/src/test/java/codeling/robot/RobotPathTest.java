package codeling.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class RobotPathTest {
    /** A small world: solid blocks, closed doors, a floor at y 0 for walking, loaded within 40 blocks of the origin. */
    private static final class World implements RobotPath.Grid {
        final Set<BlockPos> solid = new HashSet<>();
        final Set<BlockPos> doors = new HashSet<>();
        boolean floor;

        public boolean loaded(BlockPos pos) {
            return Math.abs(pos.getX()) <= 40 && Math.abs(pos.getZ()) <= 40 && pos.getY() >= -1 && pos.getY() <= 40;
        }

        public boolean blocked(BlockPos pos) {
            return pos.getY() < -1 || floor && pos.getY() == 0 || solid.contains(pos) || doors.contains(pos);
        }

        public boolean opens(BlockPos pos) {
            return doors.contains(pos);
        }

        World wall(int x, int fromZ, int toZ, int y) {
            for (int z = fromZ; z <= toZ; z++) solid.add(new BlockPos(x, y, z));
            return this;
        }
    }

    private static List<BlockPos> path(World world, BlockPos start, BlockPos target, boolean walking) {
        RobotPath search = new RobotPath(world, start, target, walking);
        while (!search.finished()) search.search(100);
        return search.path();
    }

    private static void walkable(List<BlockPos> path) {
        for (int i = 1; i < path.size(); i++) {
            BlockPos a = path.get(i - 1), b = path.get(i);
            assertEquals(1, Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()), "one block sideways per step: " + a + " " + b);
        }
    }

    @Test
    void fliesStraight() {
        List<BlockPos> path = path(new World(), BlockPos.ZERO, new BlockPos(3, 2, -4), false);
        assertEquals(10, path.size());
        assertEquals(BlockPos.ZERO, path.get(0));
        assertEquals(new BlockPos(3, 2, -4), path.get(9));
    }

    @Test
    void walksAroundAWall() {
        World world = new World().wall(3, -5, 5, 1).wall(3, -5, 5, 2);
        world.floor = true;
        List<BlockPos> path = path(world, new BlockPos(0, 1, 0), new BlockPos(6, 1, 0), true);
        assertEquals(new BlockPos(6, 1, 0), path.get(path.size() - 1));
        walkable(path);
        assertTrue(path.stream().allMatch(p -> p.getY() == 1), "walks on the floor");
        assertTrue(path.stream().anyMatch(p -> Math.abs(p.getZ()) == 6), "goes round the end of the wall");
    }

    @Test
    void stepsUpAndFalls() {
        World world = new World();
        world.floor = true;
        world.wall(1, -40, 40, 1);
        // a step up onto the bump, then a fall off its far side
        assertEquals(List.of(new BlockPos(0, 1, 0), new BlockPos(1, 2, 0), new BlockPos(2, 1, 0)),
                path(world, new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), true));
        // two blocks high is a wall
        world.wall(1, -40, 40, 2);
        assertNull(path(world, new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), true));
    }

    @Test
    void endsNextToASolidTarget() {
        World world = new World();
        BlockPos log = new BlockPos(5, 0, 0);
        world.solid.add(log);
        List<BlockPos> path = path(world, BlockPos.ZERO, log, false);
        assertEquals(new BlockPos(4, 0, 0), path.get(path.size() - 1));
    }

    @Test
    void goesThroughClosedDoors() {
        World world = new World().wall(2, -40, 40, 1).wall(2, -40, 40, 2);
        world.floor = true;
        world.solid.remove(new BlockPos(2, 1, 3));
        world.doors.add(new BlockPos(2, 1, 3));
        List<BlockPos> path = path(world, new BlockPos(0, 1, 0), new BlockPos(4, 1, 0), true);
        assertTrue(path.contains(new BlockPos(2, 1, 3)), "through the door");
        walkable(path);
    }

    @Test
    void noPathAndTheNodeCap() {
        World world = new World();
        BlockPos start = new BlockPos(0, 5, 0);
        for (BlockPos p : List.of(start.above(), start.below(), start.north(), start.south(), start.east(), start.west())) world.solid.add(p);
        RobotPath boxed = new RobotPath(world, start, new BlockPos(10, 5, 0), false);
        assertEquals(1, boxed.search(RobotPath.MAX_NODES));
        assertTrue(boxed.finished());
        assertNull(boxed.path());

        // An enclosed target in an open world: the search spreads over calls and stops at the cap.
        World open = new World();
        BlockPos target = new BlockPos(20, 5, 0);
        for (BlockPos p : List.of(target.above(), target.below(), target.north(), target.south(), target.east(), target.west())) open.solid.add(p);
        RobotPath capped = new RobotPath(open, BlockPos.ZERO, target, false);
        int total = 0;
        for (int i = 0; i < 100 && !capped.finished(); i++) {
            int used = capped.search(1000);
            assertTrue(used <= 1000, "never more than the budget");
            total += used;
        }
        assertTrue(capped.finished());
        assertEquals(RobotPath.MAX_NODES, total);
        assertNull(capped.path());
        assertFalse(new RobotPath(open, BlockPos.ZERO, new BlockPos(99, 0, 0), false).search(10) > 0, "an unloaded target is no path");
    }

    // Issue #138: a walking robot on a bridge 300 blocks up. Every step off the side falls to the ground, and those
    // block checks count against the per-tick budget, so one call reads about as many blocks as its budget allows.
    @Test
    void fallsCountAgainstTheBudget() {
        int[] reads = {0};
        RobotPath.Grid bridge = new RobotPath.Grid() {
            public boolean loaded(BlockPos pos) {
                reads[0]++;
                return Math.abs(pos.getX()) <= 40 && Math.abs(pos.getZ()) <= 40;
            }

            public boolean blocked(BlockPos pos) {
                reads[0]++;
                return pos.getY() <= 0 || pos.getY() == 300 && pos.getZ() == 0;
            }

            public boolean opens(BlockPos pos) {
                return false;
            }
        };
        BlockPos end = new BlockPos(30, 301, 0);
        RobotPath search = new RobotPath(bridge, new BlockPos(0, 301, 0), end, true);
        int used = search.search(100);
        assertTrue(reads[0] <= 4 * (100 + 4 * 300), "reads follow the budget: " + reads[0] + " block reads for a budget of 100");
        assertTrue(used <= 100 + 4 * 300, "at most one node's falls over the budget: " + used);
        while (!search.finished()) search.search(1024);
        assertEquals(end, search.path().get(search.path().size() - 1), "still walks the bridge");
    }
}
