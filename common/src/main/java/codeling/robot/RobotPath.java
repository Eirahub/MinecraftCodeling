package codeling.robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Bounded A* for agent.moveTo (issues #44 to #46). The search runs over several ticks: {@link #search} expands at
 * most the nodes it is given and keeps its state, and gives up after {@link #MAX_NODES}. It follows the executor's
 * move rules exactly, so the robot can then walk the path one normal move at a time:
 * <ul>
 * <li>flying (gravity off): one block in any of the six directions;</li>
 * <li>walking (gravity on): one block sideways, up a 1-block bump when there is head room, then falling until the
 * robot stands on something.</li>
 * </ul>
 * A closed wooden door or fence gate counts as free: the robot opens it on the way (issue #109). When the target
 * itself is solid (a log from findNearest), the path ends next to it, and so it does when told to stop beside the target
 * (agent.goToRobot, issue #243: the target is the block another robot stands in). Unloaded blocks are never entered.
 */
public final class RobotPath {
    // shortcut: proposals (issue #46), like SCAN_BLOCKS_PER_TICK; move to server config with the other limits in step 4.
    public static final int MAX_NODES = 8192;

    /** The world as the search sees it; RobotManager backs it with the level, JUnit with a few blocks. */
    public interface Grid {
        boolean loaded(BlockPos pos);

        /** The robot cannot be in this block (it has a collision shape, or it is outside the build height). */
        boolean blocked(BlockPos pos);

        /** A closed door or gate the robot can open by hand. */
        boolean opens(BlockPos pos);
    }

    private record Node(BlockPos pos, int cost, int estimate) {
    }

    private final Grid grid;
    private final BlockPos target;
    private final boolean walking;
    private final boolean solidTarget;
    // Lowest estimate first; on a tie the node furthest along, so open ground is crossed in a straight line.
    private final PriorityQueue<Node> open = new PriorityQueue<>(
            Comparator.comparingInt(Node::estimate).thenComparing(Comparator.comparingInt(Node::cost).reversed()));
    private final Map<BlockPos, Integer> cost = new HashMap<>();
    private final Map<BlockPos, BlockPos> from = new HashMap<>();
    private int expanded;
    /** Blocks checked while falling in {@link #step}: they count against the per-tick budget (issue #138), not the node cap, so a high bridge can still be walked. */
    private int fallen;
    private boolean finished;
    private List<BlockPos> path;

    public RobotPath(Grid grid, BlockPos start, BlockPos target, boolean walking) {
        this(grid, start, target, walking, false);
    }

    /** With {@code beside} the path ends on a block next to the target, as for a solid target, even when it is free. */
    public RobotPath(Grid grid, BlockPos start, BlockPos target, boolean walking, boolean beside) {
        this.grid = grid;
        this.target = target;
        this.walking = walking;
        this.solidTarget = beside || grid.blocked(target) && !grid.opens(target);
        if (!grid.loaded(target)) {
            finished = true;
            return;
        }
        cost.put(start, 0);
        open.add(new Node(start, 0, estimate(start)));
    }

    /**
     * Expands nodes while {@code budget} lasts and answers how much it used: one per node plus one per block checked
     * while walking off an edge, so a search along a deep drop costs what it reads. The last node may go over the
     * budget by its falls (at most four times the world height).
     */
    public int search(int budget) {
        int used = 0;
        while (!finished && used < budget) {
            Node node = open.poll();
            if (node == null || expanded >= MAX_NODES) {
                finished = true;
                break;
            }
            if (node.cost() > cost.get(node.pos())) continue; // a cheaper way here was found later
            used++;
            expanded++;
            if (goal(node.pos())) {
                finished = true;
                path = new ArrayList<>();
                for (BlockPos p = node.pos(); p != null; p = from.get(p)) path.add(p);
                Collections.reverse(path);
                break;
            }
            fallen = 0;
            for (Direction dir : walking ? Direction.Plane.HORIZONTAL : List.of(Direction.values())) {
                BlockPos next = step(node.pos(), dir);
                if (next == null) continue;
                int c = node.cost() + next.distManhattan(node.pos());
                Integer known = cost.get(next);
                if (known != null && known <= c) continue;
                cost.put(next, c);
                from.put(next, node.pos());
                open.add(new Node(next, c, c + estimate(next)));
            }
            used += fallen;
        }
        return used;
    }

    public boolean finished() {
        return finished;
    }

    /** True when the search stopped at {@link #MAX_NODES} without a path, rather than running out of places to try. */
    public boolean capped() {
        return finished && path == null && expanded >= MAX_NODES;
    }

    /** The blocks from the start to the end, both included; null when there is no path or the cap was hit. */
    public List<BlockPos> path() {
        return path;
    }

    /** True when the robot at pos has arrived: on the target, or next to it when the target is solid. */
    private boolean goal(BlockPos pos) {
        return solidTarget ? pos.distManhattan(target) == 1 : pos.equals(target);
    }

    // Manhattan distance never overestimates: every move costs the blocks it covers.
    private int estimate(BlockPos pos) {
        return Math.max(0, pos.distManhattan(target) - (solidTarget ? 1 : 0));
    }

    private boolean enter(BlockPos pos) {
        return grid.loaded(pos) && (!grid.blocked(pos) || grid.opens(pos));
    }

    // Where one move in dir ends, by the executor's rules (RobotManager.moveOne), or null when it is blocked.
    private BlockPos step(BlockPos pos, Direction dir) {
        BlockPos next = pos.relative(dir);
        if (!walking) return enter(next) ? next : null;
        if (!grid.loaded(next)) return null;
        if (!enter(next)) {
            BlockPos up = next.above();
            return !grid.blocked(pos.above()) && grid.loaded(up) && !grid.blocked(up) ? up : null;
        }
        while (grid.loaded(next.below()) && !grid.blocked(next.below())) {
            next = next.below();
            fallen++;
        }
        return next;
    }

    /** The direction of one path step: sideways when it is a walk (with a step up or a fall), else up or down. */
    public static Direction direction(BlockPos from, BlockPos to) {
        int dx = Integer.signum(to.getX() - from.getX()), dz = Integer.signum(to.getZ() - from.getZ());
        if (dx != 0) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (dz != 0) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return to.getY() > from.getY() ? Direction.UP : Direction.DOWN;
    }
}
