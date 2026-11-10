package codeling.robot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * agent.followPath (issue #179): plans the walk along a path of one block type before the robot walks it, over
 * several ticks like {@link RobotPath}, so RobotManager walks the route with the same moves as moveTo. The path block
 * is the block under the robot's feet. The next one is in front, left or right (never back the way the robot came),
 * at the same height or one step up or down. At the start the robot goes forward when it can, else any way the path
 * goes, so a program can turn at a junction and follow the path again. The plan stops at the end of the path, at a
 * junction (two ways or more), at a next block the robot cannot enter, after {@link #MAX_BLOCKS} blocks, or back at
 * the start of a loop (answered as the end). The route follows the executor's move rules: walking, one block sideways
 * per step (moveOne climbs the step or falls); flying, the step up or down is a move of its own.
 *
 * <p>Keeping to a side (issue #180, {@link RobotCommand#LEFT_TURN} or {@link RobotCommand#RIGHT_TURN}): a wall
 * follower. The robot takes the left-most (right-most) way of left, forward and right, so it never stops at a
 * junction. At a dead end with a wall in front it turns round and walks back; where the path ends without a wall in
 * front (the way out of a maze) it stops (END). It also stops back at its start when it would set off the same way
 * again (END, it has been round everything), and at {@link #MAX_BLOCKS} (LIMIT: follow again to go on).
 */
public final class RobotTrail {
    // shortcut: proposal from issue #179, move to server config with the other limits in step 4.
    public static final int MAX_BLOCKS = 256;
    /** PathStop in the editor. */
    public static final int END = 0, JUNCTION = 1, BLOCKED = 2, LIMIT = 3;
    /** Blocks read for one block of the plan at most: three heights in four directions, the wall at a dead end, three to check the way is free. */
    public static final int READS_PER_BLOCK = 16;

    private final RobotPath.Grid grid;
    private final Predicate<BlockPos> path;
    private final boolean walking;
    private final int side;
    private final List<BlockPos> route = new ArrayList<>();
    private Direction facing;
    private Direction firstWay;
    private int blocks;
    private int stop = -1;

    public RobotTrail(RobotPath.Grid grid, Predicate<BlockPos> path, BlockPos start, Direction facing, boolean walking, int side) {
        this.grid = grid;
        this.path = path;
        this.walking = walking;
        this.side = side;
        this.facing = facing;
        route.add(start);
    }

    /** Plans blocks while {@code budget} lasts and answers how much it used ({@link #READS_PER_BLOCK} per block). */
    public int plan(int budget) {
        int used = 0;
        for (; stop < 0 && used + READS_PER_BLOCK <= budget; used += READS_PER_BLOCK) stop = next();
        return used;
    }

    public boolean finished() {
        return stop >= 0;
    }

    /** The blocks the robot stands in from the start to where it stops, both included. */
    public List<BlockPos> route() {
        return route;
    }

    /** Why the route ends: {@link #END}, {@link #JUNCTION}, {@link #BLOCKED} or {@link #LIMIT}. */
    public int stop() {
        return stop;
    }

    // Plans one more block: answers why the route stops here, or -1 when it goes on.
    private int next() {
        BlockPos at = route.getLast();
        Direction way = null;
        BlockPos to = null;
        if (side == 0) {
            boolean ahead = blocks == 0 && onPath(at, facing) != null;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (ahead ? dir != facing : blocks > 0 && dir == facing.getOpposite()) continue;
                BlockPos next = onPath(at, dir);
                if (next == null) continue;
                if (to != null) return JUNCTION;
                way = dir;
                to = next;
            }
        } else {
            Direction first = side == RobotCommand.LEFT_TURN ? facing.getCounterClockWise() : facing.getClockWise();
            for (Direction dir : List.of(first, facing, first.getOpposite())) {
                to = onPath(at, dir);
                way = dir;
                if (to != null) break;
            }
            // A dead end with a wall in front: turn round. Without a wall the path just ends: the way out.
            if (to == null && grid.blocked(at.relative(facing))) to = onPath(at, way = facing.getOpposite());
            // Setting off from the start the same way as the first time would walk the same round again.
            if (to != null && blocks > 0 && way == firstWay && at.equals(route.getFirst())) return END;
        }
        if (to == null) return END;
        if (!free(at, to, way)) return BLOCKED;
        if (blocks == MAX_BLOCKS) return LIMIT;
        if (!walking && to.getY() != at.getY()) route.add(to.getY() > at.getY() ? at.above() : at.relative(way));
        route.add(to);
        if (blocks++ == 0) firstWay = way;
        facing = way;
        return side == 0 && to.equals(route.getFirst()) ? END : -1;
    }

    // Where the robot stands on the path block next to it in dir: one step up, level or one step down; null for none.
    // shortcut: an unloaded block reads as no path (End) and a path block without a collision shape (carpet, rail) as
    // ground, so a walking robot steps or falls wrongly there; answer UNLOADED and check the shape if players hit it.
    private BlockPos onPath(BlockPos at, Direction dir) {
        BlockPos side = at.relative(dir);
        for (int down = 0; down < 3; down++) {
            BlockPos block = side.below(down);
            if (grid.loaded(block) && path.test(block)) return block.above();
        }
        return null;
    }

    // RobotPath's move rules: head room for a step up, room to go over the edge for a step down, doors and gates open.
    private boolean free(BlockPos at, BlockPos to, Direction dir) {
        if (to.getY() > at.getY()) return !grid.blocked(at.above()) && !grid.blocked(to);
        if (to.getY() < at.getY()) return enter(at.relative(dir)) && !grid.blocked(to);
        return enter(to);
    }

    private boolean enter(BlockPos pos) {
        return !grid.blocked(pos) || grid.opens(pos);
    }
}
