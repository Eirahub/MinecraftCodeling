package codeling.gametest;

import codeling.robot.BlockIds;
import codeling.robot.ItemIds;
import codeling.robot.Result;
import codeling.robot.RobotCommand;
import codeling.robot.RobotException;
import codeling.robot.RobotLinks;
import codeling.robot.RobotManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance test with many robots (issue #138): 20 robots search, walk and craft at the same time while every tick's
 * robot cost ({@link RobotManager#lastTick}) is recorded. GameTests share one server with the other tests running
 * beside this one, so the test measures the time spent in the robot executor, not the whole server's MSPT. The shared
 * budgets (#46, #52) bound that time whatever the other tests do, so their robots count too. The 20 robots belong to
 * 5 players with 4 robots each (issue #140): the default robot and three named ones, all in the same shared budgets.
 */
public class RobotPerformanceGameTests {
    private static final Logger LOG = LoggerFactory.getLogger("codeling-gametest");
    private static final int ROBOTS = 20, OWNERS = 5;
    private static final int TICKS = 200;
    /**
     * A server tick has 50 ms for everything (mobs, redstone, chunks, players). The robots of all players together may
     * use 8 percent of it on average and 40 percent in any single tick. A few ticks over the single-tick limit are
     * allowed for garbage collection pauses and the JIT warming up, which the test cannot tell apart from robot work.
     * Measured on the owner's PC (2026-11-09): mean 1.8 ms, max 18 ms; before the crafting fix the mean was 6.5 ms.
     * The margin is for slower machines; shared GitHub runners measured 5.1 ms mean, so CI (env CI=true) gets 2.5 times.
     * Even so a shared runner measured 2, 21 and 5 ms mean on the same tree (issue #229), so in CI the two time limits
     * only warn (a note on the run's summary page); the budgets and the finished work still fail the test everywhere.
     * A real slowdown is caught by running this test without CI=true before each release (CONTRIBUTING.md, "Releases").
     */
    private static final boolean CI = "true".equals(System.getenv("CI"));
    private static final double SLACK = CI ? 2.5 : 1;
    private static final long MAX_MEAN_NANOS = (long) (1000 * SLACK), MAX_TICK_NANOS = (long) (20_000_000 * SLACK);
    private static final int SLOW_TICKS_ALLOWED = 3;

    private record Robot(RobotLinks.Key key, Supplier<RobotCommand> next, List<Result> results) {
    }

    private static RobotCommand command(String op, Object... args) {
        try {
            return RobotCommand.parse(op, List.of(args));
        } catch (RobotException e) {
            throw new IllegalArgumentException(op, e);
        }
    }

    @GameTest(maxTicks = TICKS + 20)
    public void twentyRobotsSearchWalkAndCraft(GameTestHelper helper) {
        // Floor at y 1, a wall two high across most of the area (walkers go round it), crafting tables along z 0.
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        for (int x = 0; x < 6; x++) for (int y = 2; y < 4; y++) helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
        for (int x = 0; x < 7; x++) helper.setBlock(new BlockPos(x, 2, 0), Blocks.CRAFTING_TABLE);
        int emerald = BlockIds.byName("EMERALD_ORE"), stick = ItemIds.byName("stick"), chest = ItemIds.byName("chest");
        List<Robot> robots = new ArrayList<>();
        List<UUID> owners = new ArrayList<>();
        for (int i = 0; i < OWNERS; i++) owners.add(UUID.randomUUID());
        for (int i = 0; i < ROBOTS; i++) {
            RobotLinks.Key owner = new RobotLinks.Key(owners.get(i % OWNERS), i < OWNERS ? "" : "Bot" + i / OWNERS);
            int role = i % 3, x = i / 3;
            BlockPos at = new BlockPos(x, 2, role == 0 ? 1 : role == 1 ? 7 : 2);
            RobotManager.spawn(helper.getLevel(), owner, Vec3.atBottomCenterOf(helper.absolutePos(at)), Direction.SOUTH);
            int[] turn = {0};
            Supplier<RobotCommand> next = switch (role) {
                // Crafting next to a table: 64 sticks (16 crafts), a chest (3x3), and canCraft, from a fresh stack of planks.
                case 0 -> () -> {
                    RobotManager.robot(owner).inventory().clearContent();
                    RobotManager.robot(owner).inventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
                    return switch (turn[0]++ % 3) {
                        case 0 -> command("agent.craft", stick, 64);
                        case 1 -> command("agent.craft", chest, 8);
                        default -> command("agent.canCraft", chest, 64);
                    };
                };
                // Searching for a block that is not there, so every search reads its whole cube.
                case 1 -> () -> command("agent.findNearest", emerald, turn[0]++ % 2 == 0 ? RobotCommand.MAX_FIND_RADIUS : 8);
                // Walking with gravity round the wall and back; every third robot flies to a place it cannot reach,
                // which searches the whole test area.
                default -> () -> {
                    if (x % 3 != 2 && turn[0] == 0) {
                        turn[0]++;
                        return command("agent.setGravity", true);
                    }
                    BlockPos target = x % 3 == 2 ? new BlockPos(3, 3, 30) : new BlockPos(x, 2, turn[0]++ % 2 == 0 ? 6 : 2);
                    BlockPos p = helper.absolutePos(target);
                    return command("agent.moveTo", p.getX(), p.getY(), p.getZ());
                };
            };
            robots.add(new Robot(owner, next, new ArrayList<>()));
        }
        List<RobotManager.TickCost> costs = new ArrayList<>();
        boolean[] over = {false};
        helper.onEachTick(() -> {
            if (over[0]) return; // the test has ended and its robots are being removed
            if (helper.getTick() > 0) costs.add(RobotManager.lastTick());
            for (Robot r : robots) {
                if (RobotManager.queue(r.key()).size() > 0 || RobotManager.robot(r.key()) == null) continue;
                try {
                    RobotManager.submit(r.key(), RobotManager.queue(r.key()).run(), r.next().get(), r.results()::add);
                } catch (RobotException e) {
                    helper.fail("submit refused: " + e.result());
                }
            }
        });
        helper.runAfterDelay(TICKS, () -> {
            over[0] = true;
            owners.forEach(RobotManager::stop);
            int maxPath = RobotManager.PATH_NODES_PER_TICK + 4 * helper.getLevel().getHeight();
            long total = 0, max = 0;
            int slow = 0, scanned = 0, nodes = 0;
            for (RobotManager.TickCost c : costs) {
                helper.assertTrue(c.actions() <= RobotManager.ACTIONS_PER_TICK, "actions per tick " + c);
                helper.assertTrue(c.scanned() <= RobotManager.SCAN_BLOCKS_PER_TICK, "scan budget " + c);
                helper.assertTrue(c.pathNodes() <= maxPath, "path budget " + c);
                total += c.nanos();
                max = Math.max(max, c.nanos());
                if (c.nanos() > MAX_TICK_NANOS) slow++;
                scanned += c.scanned();
                nodes += c.pathNodes();
            }
            long mean = total / Math.max(1, costs.size());
            long done = robots.stream().mapToLong(r -> r.results().stream().filter(res -> res.ok()).count()).sum();
            LOG.info("robot tick cost over {} ticks with {} robots: mean {} us, max {} us, {} ticks over {} ms, {} commands done, {} blocks scanned, {} path nodes",
                    costs.size(), ROBOTS, mean / 1000, max / 1000, slow, MAX_TICK_NANOS / 1_000_000, done, scanned, nodes);
            helper.assertTrue(scanned > 0 && nodes > 0, "searching and walking happened");
            for (int i = 0; i < ROBOTS; i += 3) {
                helper.assertTrue(robots.get(i).results().contains(Result.ok(64)), "robot " + i + " crafted 64 sticks: " + robots.get(i).results());
            }
            timeLimit(helper, mean <= MAX_MEAN_NANOS, "mean robot tick cost " + mean / 1000 + " us is over " + MAX_MEAN_NANOS / 1000 + " us");
            timeLimit(helper, slow <= SLOW_TICKS_ALLOWED, slow + " ticks took over " + MAX_TICK_NANOS / 1_000_000 + " ms (max " + max / 1000 + " us)");
            helper.succeed();
        });
    }

    /** Hard failure outside CI. In CI a slow shared runner is not a code change, so it only adds a line to the step summary. */
    private static void timeLimit(GameTestHelper helper, boolean ok, String message) {
        if (!CI) {
            helper.assertTrue(ok, message);
        } else if (!ok) {
            LOG.warn("{} (slow shared runners cause this, see #229)", message);
            String file = System.getenv("GITHUB_STEP_SUMMARY");
            if (file == null) return;
            try {
                Files.writeString(Path.of(file), "> [!WARNING]\n> Performance GameTest: " + message + ". Slow shared runners cause this (see #229).\n\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOG.warn("could not write the step summary", e);
            }
        }
    }
}
