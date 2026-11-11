package codeling.robot;

import codeling.McCompat;
import codeling.Codeling;
import codeling.platform.Platform;
import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side owner of all robots: per player a default robot and, up to the game rule codeling:max_robots, named
 * robots (issue #140); per-robot queues, and the tick executor. Server thread only.
 */
public final class RobotManager {
    // shortcut: proposals from architecture section 8, move to server config when step 4 measures them.
    public static final int ACTIONS_PER_TICK = 64;
    public static final int TICKS_PER_BLOCK = 4;
    // shortcut: proposal (issues #40 and #46), shared by all robots; a radius-32 search that finds nothing takes about 34 ticks.
    public static final int SCAN_BLOCKS_PER_TICK = 8192;
    // shortcut: proposal (issue #46), path nodes per tick for all robots; a search that hits RobotPath.MAX_NODES takes 8 ticks.
    public static final int PATH_NODES_PER_TICK = 1024;
    // shortcut: proposal (issue #90), blocks set or cleared per tick for all robots; a full 32768-block fill takes 128 ticks.
    public static final int BUILD_BLOCKS_PER_TICK = 256;
    /** BlockCategory in the editor, in order (RobotCommand.CATEGORIES). c:ores is the convention tag of Fabric and NeoForge, so modded ores match too. */
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
    private static final List<TagKey<Block>> CATEGORIES = List.of(BlockTags.LOGS, BlockTags.LEAVES, ORES,
            BlockTags.PLANKS, BlockTags.WOOL, BlockTags.FLOWERS);
    /** Liquid in the editor: None 0, Water 1, Lava 2. */
    private static final int WATER = 1, LAVA = 2;
    /** EntityKind.Mob in the editor. */
    private static final int MOB = 0;
    /** agent.isSafe (issue #161): a fall onto the ground over at most this many free blocks is safe. */
    public static final int SAFE_DROP = 3;

    private static final Map<RobotLinks.Key, RobotEntity> LOADED = new LinkedHashMap<>();
    private static final Map<RobotLinks.Key, RobotQueue> QUEUES = new HashMap<>();
    /** The run id each owner's robots share (issue #141). */
    private static final Map<UUID, RobotQueue.Run> RUNS = new HashMap<>();
    private static final List<RobotEntity> STALE = new ArrayList<>();
    private static int roundRobin;
    private static int scanLeft;
    private static int pathLeft;
    private static int buildLeft;
    private static TickCost lastTick = new TickCost(0, 0, 0, 0);

    /**
     * What the last server tick cost the robots (issues #52 and #138): the time spent in the executor and how much of
     * each shared budget it used. The performance GameTest reads it; a server config or command could show it later.
     */
    public record TickCost(long nanos, int actions, int scanned, int pathNodes) {
    }

    public static TickCost lastTick() {
        return lastTick;
    }

    private RobotManager() {
    }

    /** Common hook for the loader's entity load event. */
    public static void entityLoaded(Entity entity, ServerLevel level) {
        if (entity instanceof RobotEntity robot) onLoad(robot, level);
    }

    /** Common hook for the loader's entity unload event. */
    public static void entityUnloaded(Entity entity) {
        if (entity instanceof RobotEntity robot && robot.key() != null && LOADED.remove(robot.key(), robot)) {
            queue(robot.key()).clear(Result.Code.UNLOADED, "robot unloaded");
        }
    }

    public static void serverStopped() {
        for (UUID owner : new HashSet<>(RUN_INFO.keySet())) endRun(owner, "the world was closed");
        lastTickNanos = 0;
        LOADED.clear();
        QUEUES.clear();
        RUNS.clear();
        STALE.clear();
    }

    private static void onLoad(RobotEntity robot, ServerLevel level) {
        UUID owner = robot.owner();
        // Which robot this is comes from robots.dat, so a rename while unloaded is no problem. A robot the owner has
        // replaced (respawned while this one was unloaded) or removed is no one's robot any more and goes.
        RobotLinks.Key key = owner == null ? null : RobotLinks.get(level.getServer()).key(robot.getUUID());
        if (key == null || !key.owner().equals(owner)) {
            STALE.add(robot);
            return;
        }
        robot.setKey(key);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) robot.setOwnerName(player.getGameProfile().name());
        LOADED.put(key, robot);
        Codeling.LOG.info("spawn: {} was loaded into the world at {} in {}.", label(level.getServer(), key), fmt(robot.position()), dim(level));
    }

    // "Player303's robot "Miner"" or "Player303's default robot".
    static String label(MinecraftServer server, RobotLinks.Key key) {
        ServerPlayer p = server.getPlayerList().getPlayer(key.owner());
        return (p != null ? p.getGameProfile().name() : key.owner().toString()) + "'s "
                + (key.name().isEmpty() ? "default robot" : "robot \"" + key.name() + "\"");
    }

    static String label(RobotEntity robot) {
        return robot.key() == null ? "a robot" : label(robot.level().getServer(), robot.key());
    }

    private static String at(BlockPos p) {
        return "x=" + p.getX() + " y=" + p.getY() + " z=" + p.getZ();
    }

    private static String name(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private static String fmt(Vec3 v) {
        return String.format(java.util.Locale.ROOT, "x=%.2f y=%.2f z=%.2f", v.x, v.y, v.z);
    }

    private static String dim(ServerLevel level) {
        return McCompat.dimension(level).getPath();
    }

    private static RobotLinks.Key key(UUID owner) {
        return new RobotLinks.Key(owner, "");
    }

    /** The queue of the owner's default robot. */
    public static RobotQueue queue(UUID owner) {
        return queue(key(owner));
    }

    public static RobotQueue queue(RobotLinks.Key key) {
        return QUEUES.computeIfAbsent(key, k -> new RobotQueue(RUNS.computeIfAbsent(k.owner(), o -> new RobotQueue.Run())));
    }

    /** The owner's default robot, or null when it is not loaded. */
    public static RobotEntity robot(UUID owner) {
        return LOADED.get(key(owner));
    }

    public static RobotEntity robot(RobotLinks.Key key) {
        return LOADED.get(key);
    }

    /** The owner's loaded robots, the default robot first if it is loaded. */
    public static List<RobotEntity> robots(UUID owner) {
        List<RobotEntity> robots = new ArrayList<>();
        for (Map.Entry<RobotLinks.Key, RobotEntity> e : LOADED.entrySet()) {
            if (e.getKey().owner().equals(owner)) robots.add(e.getKey().name().isEmpty() ? 0 : robots.size(), e.getValue());
        }
        return robots;
    }

    /** Spawns the owner's default robot, or moves the existing one, and ends any running program. */
    public static RobotEntity spawn(ServerLevel level, UUID owner, Vec3 pos, Direction facing) {
        return spawn(level, key(owner), pos, facing);
    }

    /**
     * Spawns one of the owner's robots, or moves it if it exists, and ends any running program of that owner (the
     * owner's robots share one run). The limit (codeling:max_robots) is checked by {@link #spawnRefusal} first.
     */
    public static RobotEntity spawn(ServerLevel level, RobotLinks.Key key, Vec3 pos, Direction facing) {
        stop(key.owner(), "/robot spawn put a robot in a new place");
        RobotEntity robot = LOADED.get(key);
        if (robot != null && robot.level() == level) {
            String before = fmt(robot.position());
            robot.snapTo(pos, facing.toYRot(), 0);
            Codeling.LOG.info("spawn: Moved {} from {} to {}, facing {}, in {}. It now stands at {}.{}", label(level.getServer(), key), before, fmt(pos),
                    facing.getName(), dim(level), fmt(robot.position()), robot.position().distanceTo(pos) > 0.5 ? " This is NOT where it was asked to go." : "");
            return robot;
        }
        RobotEntity old = robot;
        robot = new RobotEntity(Codeling.ROBOT.get(), level);
        robot.setOwner(key.owner());
        robot.setKey(key);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(key.owner());
        if (player != null) robot.setOwnerName(player.getGameProfile().name());
        robot.snapTo(pos, facing.toYRot(), 0);
        RobotLinks links = RobotLinks.get(level.getServer());
        if (old != null) {
            // Another dimension: the items move to the new robot, so the old one drops nothing when it is discarded.
            for (int i = 0; i < RobotCommand.SLOTS; i++) robot.inventory().setItem(i, old.inventory().removeItemNoUpdate(i));
            robot.setCustomName(old.getCustomName());
            robot.setCustomNameVisible(old.isCustomNameVisible());
            old.discard();
        } else if (links.robot(key) != null) {
            // The old robot is unloaded and keeps the only copy of its items until its chunk loads again (see tick).
            links.orphan(links.robot(key));
        }
        links.set(key, robot.getUUID());
        level.addFreshEntity(robot);
        Codeling.LOG.info("spawn: Created {} at {}, facing {}, in {}. It now stands at {}.{}", label(level.getServer(), key), fmt(pos), facing.getName(),
                dim(level), fmt(robot.position()), robot.position().distanceTo(pos) > 0.5 ? " This is NOT where it was asked to go." : "");
        return robot;
    }

    /**
     * Why the owner may not spawn a robot called {@code name} now, or null. The default robot (empty name) and a robot
     * the owner already has are always allowed (spawn moves it). A new named robot needs a free name and room under
     * codeling:max_robots, which counts the default robot as one. Lowering the rule never removes robots.
     */
    public static String spawnRefusal(MinecraftServer server, UUID owner, String name) {
        RobotLinks links = RobotLinks.get(server);
        if (name.isEmpty() || links.robot(new RobotLinks.Key(owner, name)) != null) return null;
        String problem = RobotCommand.nameProblem(name);
        if (problem != null) return problem;
        String same = links.sameName(owner, name);
        if (same != null) return "You already have a robot called " + same;
        int max = Codeling.maxRobots(server);
        if (links.names(owner).size() + 1 < max) return null;
        return max == 1
                ? "This world allows one robot per player, so robots cannot have names here (game rule codeling:max_robots is 1). "
                        + "Use /robot spawn without a name, or ask an operator for /gamerule codeling:max_robots 4"
                : "This world allows " + max + " robots per player: your default robot and " + (max - 1) + " with a name (game rule "
                        + "codeling:max_robots is " + max + "). Remove one with /robot remove <name>, or ask an operator to raise the rule";
    }

    /** Queues a validated command for the owner's default robot; {@code done} gets the result on a later tick. */
    public static void submit(UUID owner, long run, RobotCommand command, Consumer<Result> done) throws RobotException {
        submit(key(owner), run, command, done);
    }

    /** Queues a validated command for one of the owner's robots; {@code done} gets the result on a later tick. */
    public static void submit(RobotLinks.Key key, long run, RobotCommand command, Consumer<Result> done) throws RobotException {
        if (!LOADED.containsKey(key)) {
            throw new RobotException(Result.Code.UNLOADED, key.name().isEmpty() ? "robot is not loaded (or not spawned: /robot spawn)"
                    : "robot " + key.name() + " is not loaded (or not spawned: /robot spawn " + key.name() + ", see /robot list)");
        }
        queue(key).submit(run, command, done);
    }

    /** New editor run for all of the owner's robots: their queues are dropped and the shared run id is new. */
    public static long start(UUID owner) {
        for (RobotQueue queue : queues(owner)) {
            queue.clear(Result.Code.STOPPED, "a new run started");
            queue.resume();
        }
        return queue(owner).start();
    }

    /** Stops all of the owner's robots and ends the run they share, so its later commands get {@code STOPPED}. */
    public static void stop(UUID owner) {
        stop(owner, "stopped");
    }

    /** As {@link #stop(UUID)}; {@code why} goes to the log. */
    public static void stop(UUID owner, String why) {
        endRun(owner, why);
        for (RobotQueue queue : queues(owner)) queue.stop();
        queue(owner).stop();
    }

    /** The queues of the owner's robots that have had commands. */
    public static List<RobotQueue> queues(UUID owner) {
        List<RobotQueue> queues = new ArrayList<>();
        QUEUES.forEach((key, queue) -> {
            if (key.owner().equals(owner)) queues.add(queue);
        });
        return queues;
    }

    /**
     * Renames one of the owner's robots (issue #151), from /robot rename or a name tag. A named robot changes its
     * name everywhere (robots.get, the commands, the tag) and its queued commands stop; the default robot stays the
     * default robot (agent in programs) and only shows the new name. Answers why not, or null. Names are unique per owner.
     */
    public static String rename(MinecraftServer server, RobotLinks.Key key, String name) {
        RobotLinks links = RobotLinks.get(server);
        UUID id = links.robot(key);
        if (id == null) return key.name().isEmpty() ? "No robot. Use /robot spawn" : "You have no robot called " + key.name();
        String problem = RobotCommand.nameProblem(name);
        if (problem != null) return problem;
        String same = links.sameName(key.owner(), name);
        if (same != null && !same.equals(key.name())) return "You already have a robot called " + same;
        RobotEntity robot = LOADED.get(key);
        if (key.name().isEmpty()) {
            if (robot == null) return "Robot is not loaded";
            robot.setCustomName(Component.literal(name));
            robot.setCustomNameVisible(true);
            return null;
        }
        RobotLinks.Key renamed = new RobotLinks.Key(key.owner(), name);
        RobotQueue queue = QUEUES.remove(key);
        if (queue != null) queue.clear(Result.Code.STOPPED, "robot " + key.name() + " was renamed to " + name);
        links.remove(key);
        links.set(renamed, id);
        if (robot != null) {
            LOADED.remove(key);
            LOADED.put(renamed, robot);
            robot.setKey(renamed);
        }
        return null;
    }

    /** A vanilla name tag used on a robot (issue #151): the owner or an op renames it, and the tag is used up. */
    static void nameTag(ServerPlayer player, RobotEntity robot, ItemStack stack) {
        if (!mayOpen(player, robot)) {
            player.sendSystemMessage(Component.literal("This is not your robot."));
            return;
        }
        if (robot.key() == null) return; // a stale copy, removed this tick
        String refusal = rename(player.level().getServer(), robot.key(), stack.get(DataComponents.CUSTOM_NAME).getString());
        if (refusal != null) {
            player.sendSystemMessage(Component.literal(refusal));
            return;
        }
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
    }

    /**
     * /robot remove (issue #140): one of the owner's named robots goes, and every item it holds moves to the player,
     * or drops at the player's feet when the inventory is full (the way a closed crafting grid gives items back), so
     * nothing is lost. Only a loaded robot: an unloaded one holds the only copy of its items. Answers why not, or null.
     */
    public static String remove(ServerPlayer player, String name) {
        RobotLinks.Key key = new RobotLinks.Key(player.getUUID(), name);
        RobotLinks links = RobotLinks.get(player.level().getServer());
        if (name.isEmpty() || links.robot(key) == null) return "You have no robot called " + name;
        RobotEntity robot = LOADED.get(key);
        if (robot == null) return "Robot " + name + " is not loaded. Go near it, or bring it to you with /robot spawn " + name + ", then remove it";
        RobotQueue queue = QUEUES.remove(key);
        if (queue != null) queue.clear(Result.Code.STOPPED, "robot " + name + " was removed");
        for (int i = 0; i < robot.inventory().getContainerSize(); i++) {
            ItemStack stack = robot.inventory().removeItemNoUpdate(i);
            if (!stack.isEmpty()) McCompat.giveBack(player, stack);
        }
        links.remove(key);
        LOADED.remove(key);
        robot.discard(); // empty now, so it drops nothing
        return null;
    }

    /**
     * Opens the robot's inventory screen for its owner or an op; anyone else gets "not your robot". The screen works
     * on the robot's own container, so a running program and the screen share the same items.
     */
    public static boolean openInventory(ServerPlayer player, RobotEntity robot) {
        if (!mayOpen(player, robot)) {
            player.sendSystemMessage(Component.literal("This is not your robot."));
            return false;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RobotMenu(id, inventory, robot), robot.getDisplayName()));
        return true;
    }

    /** The owner or an op (game master level). Never a FakePlayer: ours carries the owner's UUID. */
    static boolean mayOpen(Player player, RobotEntity robot) {
        return !Platform.INSTANCE.isFakePlayer(player) && player instanceof ServerPlayer p
                && (p.getUUID().equals(robot.owner()) || McCompat.isGamemaster(p));
    }

    public static void stopAll() {
        for (UUID owner : new HashSet<>(RUN_INFO.keySet())) endRun(owner, "/robot stop all");
        QUEUES.values().forEach(RobotQueue::stop);
    }

    private record RunInfo(String name, long nanos) {
    }

    private static final Map<UUID, RunInfo> RUN_INFO = new HashMap<>();
    private static long lastTickNanos;

    /** Log only: the editor started a program for the owner's robots. */
    public static void runStarted(UUID owner, String name) {
        endRun(owner, "a new run started");
        RUN_INFO.put(owner, new RunInfo(name, System.nanoTime()));
        List<String> names = new ArrayList<>();
        for (RobotEntity r : robots(owner)) names.add(r.key() == null || r.key().name().isEmpty() ? "default robot" : "robot \"" + r.key().name() + "\"");
        Codeling.LOG.info("run: {}'s program started (run {}) with {}.", name, queue(owner).run(), names.isEmpty() ? "no robot loaded" : String.join(", ", names));
    }

    private static void endRun(UUID owner, String why) {
        RunInfo info = RUN_INFO.remove(owner);
        if (info != null) Codeling.LOG.info("run: {}'s program ended after {} s: {}.", info.name(),
                String.format(java.util.Locale.ROOT, "%.1f", (System.nanoTime() - info.nanos()) / 1e9), why);
    }

    public static void tick(MinecraftServer server) {
        long start = System.nanoTime();
        // The integrated server runs no tick while the pause menu is open or the window has lost focus, so a long gap
        // between two ticks is the pause; it is logged when the game goes on.
        if (lastTickNanos != 0 && start - lastTickNanos > 2_000_000_000L) pauseEnded((start - lastTickNanos) / 1e9);
        lastTickNanos = start;
        int actions = execute(server);
        lastTick = new TickCost(System.nanoTime() - start, actions, SCAN_BLOCKS_PER_TICK - scanLeft, PATH_NODES_PER_TICK - pathLeft);
    }

    private static void pauseEnded(double seconds) {
        for (RobotEntity robot : LOADED.values()) {
            RobotQueue queue = QUEUES.get(robot.key());
            RobotQueue.Entry entry = queue == null ? null : queue.head();
            if (entry != null) Codeling.LOG.info("pause: Minecraft was paused (or stalled) for {} s; {} was waiting to run {}. It goes on now.",
                    String.format(java.util.Locale.ROOT, "%.1f", seconds), label(robot), entry.command.op().name());
        }
    }

    // Runs one tick of every robot's queue and answers the number of actions used.
    private static int execute(MinecraftServer server) {
        scanLeft = SCAN_BLOCKS_PER_TICK;
        pathLeft = PATH_NODES_PER_TICK;
        buildLeft = BUILD_BLOCKS_PER_TICK;
        for (RobotEntity robot : STALE) {
            // Only an orphan still owns its items and drops them. Any other stale copy (an older save of a robot that
            // moved on, a structure-block copy) holds items that exist elsewhere, so dropping them would duplicate.
            if (!RobotLinks.get(server).takeOrphan(robot.getUUID())) robot.inventory().clearContent();
            robot.discard();
        }
        STALE.clear();
        List<RobotEntity> robots = new ArrayList<>(LOADED.values());
        int n = robots.size();
        if (n == 0) return 0;
        int budget = ACTIONS_PER_TICK;
        int first = Math.floorMod(roundRobin++, n);
        for (int i = 0; i < n; i++) {
            RobotEntity robot = robots.get((first + i) % n);
            RobotQueue queue = QUEUES.get(robot.key());
            RobotQueue.Entry entry = queue == null || queue.paused() ? null : queue.head();
            if (entry == null) continue;
            if (entry.wait > 0) {
                entry.wait--;
                continue;
            }
            if (budget == 0) continue;
            budget--;
            step(robot, queue, entry);
        }
        return ACTIONS_PER_TICK - budget;
    }

    // One action step: one block of a move, or a whole other action.
    private static void step(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry) {
        RobotCommand command = entry.command;
        // shortcut: a stop mid-fall leaves the robot in the air until its next sideways move; fine while falls are short.
        if (entry.falling) {
            if (fall(robot)) return;
            entry.falling = false;
            if (entry.landed != null) {
                queue.finish(entry.landed);
                return;
            }
        }
        if (command.op() == RobotCommand.Op.FIND_BLOCK || command.op() == RobotCommand.Op.FIND_CATEGORY) {
            find(robot, queue, entry);
            return;
        }
        if (command.op() == RobotCommand.Op.MOVE_TO || command.op() == RobotCommand.Op.COME) {
            navigate(robot, queue, entry, new BlockPos(command.a(), command.b(), command.c()));
            return;
        }
        if (command.op() == RobotCommand.Op.FOLLOW_PATH) {
            follow(robot, queue, entry);
            return;
        }
        if (command.op() == RobotCommand.Op.GO_HOME) {
            // Issue #78: walks only, never across dimensions; no home or a home elsewhere answers false.
            GlobalPos home = robot.home();
            if (home == null || !home.dimension().equals(robot.level().dimension())) queue.finish(Result.ok(false));
            else navigate(robot, queue, entry, home.pos());
            return;
        }
        if (command.op().compareTo(RobotCommand.Op.MEASURE) >= 0 && command.op().compareTo(RobotCommand.Op.FIND_BOUNDS) <= 0) {
            try {
                measure(robot, queue, entry);
            } catch (RobotException e) {
                queue.finish(e.result());
            }
            return;
        }
        if (command.op().compareTo(RobotCommand.Op.FILL) >= 0) {
            try {
                build(robot, queue, entry);
            } catch (RobotException e) {
                buildEnd(robot, queue, entry, e.getMessage(), e.result());
            }
            return;
        }
        if (command.op() != RobotCommand.Op.MOVE) {
            Result result = act(robot, command);
            // Gravity mode: the robot drops into the hole it dug below itself.
            if (command.op() == RobotCommand.Op.DESTROY && command.direction() == RobotCommand.DOWN
                    && robot.gravityMode() && canFall(robot)) {
                entry.falling = true;
                entry.landed = result;
            } else {
                queue.finish(result);
            }
            return;
        }
        moveOne(robot, queue, entry, relative(robot.getDirection(), command.direction()), entry.step + 1 >= command.blocks(), Result.ok(false));
    }

    private static boolean isPath(RobotQueue.Entry entry) {
        return switch (entry.command.op()) {
            case MOVE_TO, COME, GO_HOME, FOLLOW_PATH -> true;
            default -> false;
        };
    }

    private static void pathStart(RobotEntity robot, RobotQueue.Entry entry, String to) {
        entry.from = robot.blockPosition();
        Codeling.LOG.info("path: {} starts {} at {} (standing on {}){}.", label(robot), entry.command.op().name(), at(entry.from),
                name(robot.level().getBlockState(entry.from.below()).getBlock()), to);
    }

    private static void pathEnd(RobotEntity robot, RobotQueue.Entry entry, String why) {
        BlockPos now = robot.blockPosition();
        Codeling.LOG.info("path: {} {} ended after {} steps at {}, {} blocks from where it started: {}.", label(robot), entry.command.op().name(), entry.step,
                at(now), entry.from == null ? 0 : entry.from.distManhattan(now), why);
    }

    /**
     * One block of a move in dir, the same for agent.move and each step of agent.moveTo and followPath: answers
     * {@code blocked} when blocked, true after the last block, else waits {@link #TICKS_PER_BLOCK} ticks (and falls
     * first in gravity mode).
     */
    private static void moveOne(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry, Direction dir, boolean last, Result blocked) {
        Level level = robot.level();
        boolean sideways = dir.getAxis().isHorizontal();
        BlockPos from = robot.blockPosition();
        BlockPos target = from.relative(dir);
        if (!level.isLoaded(target)) {
            if (isPath(entry)) pathEnd(robot, entry, "the next block is in an unloaded chunk");
            queue.finish(Result.error(Result.Code.UNLOADED, "next block is in an unloaded chunk"));
            return;
        }
        // Gravity mode steps up a 1-block bump when there is head room above both blocks; a higher wall stops it.
        if (robot.gravityMode() && sideways && blocked(level, target) && !blocked(level, from.above()) && !blocked(level, target.above())) {
            target = target.above();
        }
        if (blocked(level, target)) {
            if (isPath(entry)) pathEnd(robot, entry, "the next block " + at(target) + " is blocked by " + name(level.getBlockState(target).getBlock()));
            queue.finish(blocked);
            return;
        }
        robot.setPos(Vec3.atBottomCenterOf(target));
        // PLACE_ON_MOVE: an empty slot or a failed place does not stop the move.
        if (robot.placeOnMove()) place(robot, from, dir.getOpposite());
        entry.step++;
        // Gravity mode falls after a sideways step, never after up, so "move up, place down" pillars still work.
        if (robot.gravityMode() && sideways && canFall(robot)) {
            entry.falling = true;
            if (last) entry.landed = Result.ok(true);
        } else if (last) {
            queue.finish(Result.ok(true));
        } else {
            entry.wait = TICKS_PER_BLOCK - 1;
        }
    }

    /**
     * Checks up to the tick's scan budget of blocks in the cube around the robot, nearest first by block steps
     * (Manhattan distance), and answers [x, y, z] for the first match or no value when the cube holds none.
     * Unloaded chunks are skipped, never loaded. Stop or unload drops the entry, which ends the scan.
     */
    private static void find(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry) {
        RobotCommand command = entry.command;
        int r = command.b();
        //? if >=26.1 {
        if (entry.scan == null) entry.scan = BlockPos.withinBoxByManhattanDistance(robot.blockPosition(), r, r, r).iterator();
        //?} else
        /*if (entry.scan == null) entry.scan = BlockPos.withinManhattan(robot.blockPosition(), r, r, r).iterator();*/
        Iterator<BlockPos> scan = entry.scan;
        Level level = robot.level();
        Block block = command.op() == RobotCommand.Op.FIND_BLOCK
                ? McCompat.block(Identifier.parse(BlockIds.toModern(command.a()))) : null;
        TagKey<Block> tag = block == null ? CATEGORIES.get(command.a()) : null;
        for (; scanLeft > 0 && scan.hasNext(); scanLeft--) {
            BlockPos pos = scan.next();
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (block != null ? state.is(block) : state.is(tag)) {
                queue.finish(Result.ok(List.of(pos.getX(), pos.getY(), pos.getZ())));
                return;
            }
        }
        if (!scan.hasNext()) queue.finish(Result.ok(null));
    }

    /**
     * Measuring (issues #127 to #132): reads blocks without moving the robot, in loaded chunks only (an unloaded block
     * answers {@code UNLOADED}), costed against the shared scan budget like findNearest. A line read (at most 128
     * blocks) waits for a tick with enough budget left, then runs at once; a box read goes on over later ticks.
     * measure, measureRun and measureGap use the move rule (a block with a collision shape stops them); the box reads
     * and findBounds count every block that is not air or replaceable (grass, snow layer, water). Reads at a given
     * position (groundHeight, isAreaFree, countSolid) must lie within {@link RobotCommand#MAX_FIND_RADIUS} of the robot
     * on each axis, else {@code OUT_OF_RANGE}, so a program on a server cannot probe hidden blocks far away. findBounds
     * stays within that range by its own cap: its box is under {@link RobotCommand#MAX_AREA} wide around the block in front.
     */
    private static void measure(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry) throws RobotException {
        RobotCommand c = entry.command;
        Level level = robot.level();
        BlockPos at = robot.blockPosition();
        Direction facing = robot.getDirection();
        switch (c.op()) {
            case MEASURE -> {
                if (reserve(c.b())) queue.finish(Result.ok(free(level, at, relative(facing, c.a()), c.b())));
            }
            case MEASURE_RUN -> {
                if (!reserve(c.c())) return;
                BlockPos start = at.relative(relative(facing, c.a()));
                Direction along = relative(facing, c.b());
                int n = 0;
                while (n < c.c() && blocked(level, loaded(level, start.relative(along, n)))) n++;
                queue.finish(Result.ok(n));
            }
            case MEASURE_GAP -> {
                if (!reserve(2 * c.b())) return;
                Direction.Axis axis = Direction.Axis.values()[c.a()];
                int plus = free(level, at, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), c.b());
                int minus = free(level, at, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE), c.b());
                queue.finish(Result.ok(plus == c.b() || minus == c.b() ? c.b() : Math.min(c.b(), plus + minus + 1)));
            }
            case GROUND_HEIGHT -> {
                near(at, c.a(), at.getY(), c.b());
                if (reserve(1)) queue.finish(Result.ok(ground(level, c.a(), c.b())));
            }
            case HEIGHT_PROFILE -> {
                if (!reserve(c.b())) return;
                Direction dir = relative(facing, c.a());
                List<Integer> heights = new ArrayList<>();
                for (int i = 1; i <= c.b(); i++) heights.add(ground(level, at.getX() + dir.getStepX() * i, at.getZ() + dir.getStepZ() * i));
                queue.finish(Result.ok(heights));
            }
            case IS_AREA_FREE, COUNT_SOLID -> {
                if (entry.scan == null) {
                    near(at, c.a(), c.b(), c.c());
                    near(at, c.d(), c.e(), c.f());
                    entry.scan = BlockPos.betweenClosed(c.a(), c.b(), c.c(), c.d(), c.e(), c.f()).iterator();
                }
                boolean free = c.op() == RobotCommand.Op.IS_AREA_FREE;
                for (; scanLeft > 0 && entry.scan.hasNext(); scanLeft--) {
                    if (!solid(level, loaded(level, entry.scan.next()))) continue;
                    if (free) {
                        queue.finish(Result.ok(false));
                        return;
                    }
                    entry.step++;
                }
                if (!entry.scan.hasNext()) queue.finish(Result.ok(free ? (Object) true : entry.step));
            }
            case FIND_BOUNDS -> bounds(level, queue, entry, at.relative(facing), c.b());
            default -> throw new IllegalStateException("not a measuring op " + c.op());
        }
    }

    // The owner's rule for reads at a given position: within findNearest's radius of the robot on every axis.
    private static void near(BlockPos robot, int x, int y, int z) throws RobotException {
        int r = RobotCommand.MAX_FIND_RADIUS;
        if (Math.abs(x - robot.getX()) > r || Math.abs(y - robot.getY()) > r || Math.abs(z - robot.getZ()) > r) {
            throw new RobotException(Result.Code.OUT_OF_RANGE, "position " + x + " " + y + " " + z + " is more than " + r + " blocks from the robot");
        }
    }

    // Takes n blocks from this tick's scan budget, or answers false so the command waits for the next tick.
    private static boolean reserve(int n) {
        if (scanLeft < n) return false;
        scanLeft -= n;
        return true;
    }

    private static BlockPos loaded(Level level, BlockPos pos) throws RobotException {
        if (!level.isLoaded(pos) && !level.isOutsideBuildHeight(pos)) {
            throw new RobotException(Result.Code.UNLOADED, "block " + pos.toShortString() + " is in an unloaded chunk");
        }
        return pos;
    }

    // Free blocks from the robot in dir before the first one that would stop a move, or max when none does.
    private static int free(Level level, BlockPos from, Direction dir, int max) throws RobotException {
        for (int i = 1; i <= max; i++) if (blocked(level, loaded(level, from.relative(dir, i)))) return i - 1;
        return max;
    }

    // Not air and not replaceable: what a builder would have to clear first.
    private static boolean solid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced();
    }

    // The vanilla heightmap without leaves: the y of the highest block that blocks motion (water counts), below the world when there is none.
    private static int ground(Level level, int x, int z) throws RobotException {
        loaded(level, new BlockPos(x, level.getMinY(), z));
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /**
     * findBounds (issue #132): a flood fill over the solid blocks touching the block in front (sides, not edges), as
     * long as the box stays at most max blocks on each side. Six reads per block, from the scan budget, over ticks.
     * Answers [min x, y, z, max x, y, z], or no value when the block in front is not solid. Unloaded blocks are an edge.
     */
    private static void bounds(Level level, RobotQueue queue, RobotQueue.Entry entry, BlockPos start, int max) throws RobotException {
        if (entry.fill == null) {
            if (!solid(level, loaded(level, start))) {
                queue.finish(Result.ok(null));
                return;
            }
            entry.fill = new ArrayDeque<>(List.of(start.immutable()));
            entry.seen = new HashSet<>(entry.fill);
            entry.bounds = new int[]{start.getX(), start.getY(), start.getZ(), start.getX(), start.getY(), start.getZ()};
        }
        int[] b = entry.bounds;
        while (scanLeft >= 6 && !entry.fill.isEmpty()) {
            BlockPos pos = entry.fill.poll();
            scanLeft -= 6;
            for (Direction d : Direction.values()) {
                BlockPos next = pos.relative(d);
                if (Math.max(b[3], next.getX()) - Math.min(b[0], next.getX()) >= max || Math.max(b[4], next.getY()) - Math.min(b[1], next.getY()) >= max
                        || Math.max(b[5], next.getZ()) - Math.min(b[2], next.getZ()) >= max) continue;
                if (!level.isLoaded(next) || !solid(level, next) || !entry.seen.add(next)) continue;
                entry.fill.add(next);
                b[0] = Math.min(b[0], next.getX());
                b[1] = Math.min(b[1], next.getY());
                b[2] = Math.min(b[2], next.getZ());
                b[3] = Math.max(b[3], next.getX());
                b[4] = Math.max(b[4], next.getY());
                b[5] = Math.max(b[5], next.getZ());
            }
        }
        if (entry.fill.isEmpty()) queue.finish(Result.ok(List.of(b[0], b[1], b[2], b[3], b[4], b[5])));
    }

    /**
     * blocks.fill, shapes.line, shapes.circle, shapes.sphere (issue #90) and shapes.maze (#182). Every block of the shape must be within
     * {@link RobotCommand#MAX_FIND_RADIUS} of the robot on each axis (the measuring rule), else {@code OUT_OF_RANGE}.
     * Each block is set through the FakePlayer like agent.place (and cleared like agent.destroy, drops go into the
     * robot), so claim and protection mods apply. The blocks come from the robot's inventory, any slot, unless the
     * owner gets items free (setItem's rule, see {@link #freeItems}); with no block left the command ends there.
     * Keep only fills air and replaceable blocks. Works over ticks: at most {@link #BUILD_BLOCKS_PER_TICK} blocks set or
     * cleared and the scan budget for the blocks looked at. Answers the number of blocks changed.
     */
    private static void build(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry) throws RobotException {
        RobotCommand c = entry.command;
        Level level = robot.level();
        boolean fresh = entry.scan == null;
        if (entry.scan == null) {
            int[] b = RobotShapes.box(c);
            near(robot.blockPosition(), b[0], b[1], b[2]);
            near(robot.blockPosition(), b[3], b[4], b[5]);
            entry.scan = c.op() == RobotCommand.Op.LINE
                    ? RobotShapes.line(c).stream().map(p -> new BlockPos(p[0], p[1], p[2])).iterator()
                    : BlockPos.betweenClosed(b[0], b[1], b[2], b[3], b[4], b[5]).iterator();
            if (c.op() == RobotCommand.Op.MAZE) entry.maze = RobotShapes.maze(c.d(), c.e(), c.f());
        }
        Block block = c.g() == 0 ? Blocks.AIR : McCompat.block(Identifier.parse(BlockIds.toModern(c.g())));
        Block floor = c.op() == RobotCommand.Op.MAZE ? McCompat.block(Identifier.parse(BlockIds.toModern(c.h()))) : block;
        boolean free = freeItems(robot);
        if (fresh) Codeling.LOG.info("build: {} starts {} with {}; {}.", label(robot), c.op().name().toLowerCase(java.util.Locale.ROOT), name(block),
                !free ? "it builds from the robot's inventory (not creative, and game rule codeling:free_items is off)"
                        : Codeling.freeItems(robot.level().getServer()) ? "it builds free of charge because game rule codeling:free_items is on"
                        : "it builds free of charge because the owner is in creative mode");
        boolean overwrite = c.op() != RobotCommand.Op.FILL || c.h() != RobotShapes.KEEP;
        for (; buildLeft > 0 && scanLeft > 0 && entry.scan.hasNext(); scanLeft--) {
            BlockPos pos = entry.scan.next().immutable();
            entry.looked++;
            int action = switch (c.op()) {
                case LINE -> RobotShapes.SET;
                case MAZE -> RobotShapes.mazeAction(entry.maze, pos.getX() - c.a(), pos.getY() - (c.b() - 1), pos.getZ() - c.c());
                default -> RobotShapes.action(c, pos.getX(), pos.getY(), pos.getZ());
            };
            if (action == RobotShapes.SKIP) continue;
            BlockState state = level.getBlockState(loaded(level, pos));
            if (action == RobotShapes.CLEAR || block == Blocks.AIR) {
                if (!state.isAir() && !state.canBeReplaced()) {
                    buildLeft--;
                    if (destroy(robot, pos)) entry.step++;
                    else entry.refused++;
                }
                continue;
            }
            Block target = action == RobotShapes.FLOOR ? floor : block;
            if (state.is(target) || !state.canBeReplaced() && !overwrite) continue;
            buildLeft--;
            if (!state.canBeReplaced() && !destroy(robot, pos)) {
                entry.refused++;
                continue;
            }
            int slot = free ? -1 : slotWith(robot.inventory(), target.asItem());
            if (!free && slot < 0) {
                // In survival the robot gathers its own blocks: say why it stopped, not only how many it set.
                String missing = name(target).replace('_', ' ');
                String why = entry.step == 0 ? "I have no " + missing + " in my inventory."
                        : "I ran out of " + missing + " after " + entry.step + (entry.step == 1 ? " block." : " blocks.");
                robot.say(why);
                buildEnd(robot, queue, entry, "the robot has no " + name(target) + " in its inventory (survival, and game rule codeling:free_items is off)", Result.ok(entry.step, why));
                return;
            }
            ItemStack stack = free ? new ItemStack(target.asItem()) : robot.inventory().getItem(slot);
            if (place(robot, stack, pos, Direction.UP, rest -> {
                if (slot >= 0) robot.inventory().setItem(slot, rest);
            })) entry.step++;
            else entry.refused++;
        }
        if (!entry.scan.hasNext()) buildEnd(robot, queue, entry, null, Result.ok(entry.step));
    }

    private static void buildEnd(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry, String why, Result result) {
        Codeling.LOG.info("build: {} finished {}: changed {} of {} blocks looked at.{}{}", label(robot), entry.command.op().name().toLowerCase(java.util.Locale.ROOT),
                entry.step, entry.looked, why == null ? "" : " It stopped early: " + why + ".",
                entry.refused == 0 ? "" : " " + entry.refused + " blocks were refused (a claim or protection, or nothing to place against).");
        queue.finish(result);
    }

    private static int slotWith(SimpleContainer inv, Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return i;
        return -1;
    }

    /**
     * agent.moveTo: plans the path first (over several ticks, at most {@link #PATH_NODES_PER_TICK} nodes per tick for
     * all robots together), then walks it with {@link #moveOne}, one normal move per step, opening closed wooden doors
     * and gates on the way through the FakePlayer. Answers false when there is no path, or when the world changed so
     * the robot is not where the path says. agent.goHome walks to the home the same way; /robot come teleports instead
     * of answering false.
     */
    private static void navigate(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry, BlockPos target) {
        RobotCommand command = entry.command;
        Level level = robot.level();
        if (entry.path == null) {
            entry.path = new RobotPath(grid(level), robot.blockPosition(), target, robot.gravityMode());
            pathStart(robot, entry, " to " + at(target));
        }
        if (!entry.path.finished()) {
            pathLeft -= entry.path.search(pathLeft);
            if (!entry.path.finished()) return;
        }
        List<BlockPos> path = entry.path.path();
        if (path == null || !robot.blockPosition().equals(path.get(entry.step))) {
            pathEnd(robot, entry, path == null ? "no route was found" : "the robot is not where the planned route says (the world changed)");
            if (command.op() == RobotCommand.Op.COME) teleport(robot, queue, entry, target);
            else queue.finish(Result.ok(false));
            return;
        }
        BlockPos at = path.get(entry.step);
        if (entry.step == path.size() - 1) {
            // Next to a solid target: face it, so destroy forward or interact forward reaches it.
            Direction toTarget = RobotPath.direction(at, target);
            if (!at.equals(target) && toTarget.getAxis().isHorizontal()) robot.setYRot(toTarget.toYRot());
            pathEnd(robot, entry, "reached the target");
            queue.finish(Result.ok(command.op() == RobotCommand.Op.COME ? "walked" : true));
            return;
        }
        walk(robot, queue, entry, path, Result.ok(false));
    }

    // One move along a planned path, from the block at entry.step to the next; the robot turns to each sideways step.
    private static void walk(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry, List<BlockPos> path, Result blocked) {
        BlockPos at = path.get(entry.step);
        Direction dir = RobotPath.direction(at, path.get(entry.step + 1));
        if (dir.getAxis().isHorizontal()) robot.setYRot(dir.toYRot());
        BlockPos next = at.relative(dir);
        Codeling.LOG.debug("path: {} step {} to {}", label(robot), entry.step + 1, at(next));
        // Issue #109: a closed door on the path; a claim that refuses the click leaves it shut and the move blocked.
        if (opens(robot.level(), next)) interact(robot, next, dir.getOpposite());
        moveOne(robot, queue, entry, dir, false, blocked);
    }

    /**
     * agent.followPath (issue #179): plans the route with {@link RobotTrail} (from the shared scan budget, over ticks
     * when it runs out), then walks it like moveTo and answers the PathStop. The path block is the given block, or the
     * block under the robot; air is never a path. With a side (#180) it keeps to that side like a wall follower. A robot that is not where the route says (the world changed) or a
     * blocked move answers BLOCKED, where it stands. A robot not on the given block steps onto it when it is right next
     * to it (as the route starts any way the path goes); with none next to it, it answers END with the message "I am
     * not on the path" and says it.
     */
    private static void follow(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry) {
        Level level = robot.level();
        if (entry.trail == null) {
            BlockPos at = robot.blockPosition();
            int code = entry.command.a();
            Block block = code == 0 ? level.getBlockState(at.below()).getBlock()
                    : McCompat.block(Identifier.parse(BlockIds.toModern(code)));
            Block under = level.getBlockState(at.below()).getBlock();
            if (code != 0 && !under.equals(block)) entry.note = "the robot stands on " + name(under) + ", not " + name(block);
            pathStart(robot, entry, "");
            entry.trail = new RobotTrail(grid(level), pos -> {
                BlockState state = level.getBlockState(pos);
                return !state.isAir() && state.is(block);
            }, at, robot.getDirection(), robot.gravityMode(), entry.command.b());
        }
        if (!entry.trail.finished()) {
            scanLeft -= entry.trail.plan(scanLeft);
            if (!entry.trail.finished()) return;
        }
        List<BlockPos> route = entry.trail.route();
        Result blocked = Result.ok(RobotTrail.BLOCKED);
        if (!robot.blockPosition().equals(route.get(entry.step))) {
            pathEnd(robot, entry, "the robot is not where the route says (the world changed)");
            queue.finish(blocked);
        } else if (entry.step == route.size() - 1) {
            int stop = entry.trail.stop();
            String why = switch (stop) {
                case RobotTrail.JUNCTION -> "it came to a junction";
                case RobotTrail.BLOCKED -> "the way ahead is blocked";
                case RobotTrail.LIMIT -> "it reached the " + RobotTrail.MAX_BLOCKS + " block limit (follow again to go on)";
                default -> "the path ends here";
            };
            pathEnd(robot, entry, entry.step == 0 ? "stopped at once: " + (entry.note != null ? entry.note : why) : why);
            if (entry.step == 0 && entry.note != null) {
                // Not on the path and none next to it: say so instead of a bare End.
                Block block = McCompat.block(Identifier.parse(BlockIds.toModern(entry.command.a())));
                String message = "I am not on the path. Put me on the " + name(block).replace('_', ' ') + " first.";
                robot.say(message);
                queue.finish(Result.ok(stop, message));
            } else {
                queue.finish(Result.ok(stop));
            }
        }
        else walk(robot, queue, entry, route, blocked);
    }

    /**
     * /robot come without a path (issue #77): the robot appears in the player's block, as /robot spawn puts it. The
     * rules of teleportToPlayer (architecture section 5): same dimension (RobotCommands checks it) and loaded chunks.
     */
    private static void teleport(RobotEntity robot, RobotQueue queue, RobotQueue.Entry entry, BlockPos target) {
        if (!robot.level().isLoaded(target)) {
            queue.finish(Result.error(Result.Code.UNLOADED, "your block is in an unloaded chunk"));
            return;
        }
        robot.setPos(Vec3.atBottomCenterOf(target));
        Result result = Result.ok("teleported");
        if (robot.gravityMode() && canFall(robot)) {
            entry.falling = true;
            entry.landed = result;
        } else {
            queue.finish(result);
        }
    }

    private static RobotPath.Grid grid(Level level) {
        return new RobotPath.Grid() {
            public boolean loaded(BlockPos pos) {
                return level.isLoaded(pos);
            }

            public boolean blocked(BlockPos pos) {
                return RobotManager.blocked(level, pos);
            }

            public boolean opens(BlockPos pos) {
                return RobotManager.opens(level, pos);
            }
        };
    }

    // A closed wooden door or fence gate (iron doors need redstone; trapdoors would let the robot fall).
    private static boolean opens(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return (DoorBlock.isWoodenDoor(state) || state.getBlock() instanceof FenceGateBlock) && !state.getValue(BlockStateProperties.OPEN);
    }

    /**
     * Outside the build height, or a block with a collision shape (water, lava and grass tufts let the robot through).
     * An open door or trapdoor lets the robot through too, as it lets a player through; an open gate has no shape.
     */
    private static boolean blocked(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return true;
        BlockState state = level.getBlockState(pos);
        if ((state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock) && state.getValue(BlockStateProperties.OPEN)) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean canFall(RobotEntity robot) {
        BlockPos below = robot.blockPosition().below();
        return robot.level().isLoaded(below) && !blocked(robot.level(), below);
    }

    // One block of a fall; false once the robot stands on a block, the world bottom or an unloaded chunk.
    private static boolean fall(RobotEntity robot) {
        if (!canFall(robot)) return false;
        robot.setPos(Vec3.atBottomCenterOf(robot.blockPosition().below()));
        return true;
    }

    private static Result act(RobotEntity robot, RobotCommand command) {
        SimpleContainer inv = robot.inventory();
        Level level = robot.level();
        Direction facing = robot.getDirection();
        switch (command.op()) {
            case TURN -> {
                robot.setYRot((command.direction() == RobotCommand.LEFT_TURN ? facing.getCounterClockWise() : facing.getClockWise()).toYRot());
                return Result.ok(null);
            }
            case SET_SLOT -> {
                robot.setSelectedSlot(command.a());
                return Result.ok(null);
            }
            case GET_ITEM_COUNT -> {
                return Result.ok(inv.getItem(command.a() - 1).getCount());
            }
            case GET_ITEM_SPACE -> {
                ItemStack stack = inv.getItem(command.a() - 1);
                // shortcut: an empty slot reports 64 although some items stack to 16 or 1; fine until getItemDetail exists.
                return Result.ok(stack.isEmpty() ? 64 : stack.getMaxStackSize() - stack.getCount());
            }
            case SET_ITEM -> {
                if (!freeItems(robot)) return Result.error(Result.Code.NO_PERMISSION, "setItem needs creative mode or the gamerule codeling:free_items");
                ItemStack stack = new ItemStack(command.a() == 0 ? Items.AIR : item(command.a()));
                stack.setCount(Math.min(command.b(), stack.getMaxStackSize()));
                inv.setItem(command.c() - 1, stack);
                return Result.ok(null);
            }
            case SET_ASSIST -> {
                robot.setPlaceOnMove(command.b() == 1);
                return Result.ok(null);
            }
            case SET_GRAVITY -> {
                robot.setGravityMode(command.a() == 1);
                return Result.ok(null);
            }
            case COLLECT_ALL -> {
                collect(robot, robot.getBoundingBox().inflate(1));
                return Result.ok(null);
            }
            case TRANSFER -> {
                return Result.ok(transfer(inv, command.a(), command.b() - 1, command.c() - 1));
            }
            case GET_POSITION -> {
                BlockPos pos = robot.blockPosition();
                return Result.ok(List.of(pos.getX(), pos.getY(), pos.getZ()));
            }
            case GET_ORIENTATION -> {
                return Result.ok(orientation(facing));
            }
            case SAY -> {
                robot.say(command.text());
                return Result.ok(null);
            }
            case CRAFT, STONECUT -> {
                return Result.ok(RobotCrafting.craft(robot, item(command.a()), command.b(), true, command.op() == RobotCommand.Op.STONECUT));
            }
            case CAN_CRAFT -> {
                return Result.ok(RobotCrafting.craft(robot, item(command.a()), command.b(), false, false) >= command.b());
            }
            case SET_HOME -> {
                robot.setHome(GlobalPos.of(level.dimension(), robot.blockPosition()));
                return Result.ok(null);
            }
            case COUNT_NEARBY -> {
                return Result.ok(entities(level, new AABB(robot.blockPosition()).inflate(command.b()), command.a()).size());
            }
            case RADAR -> {
                return Result.ok(radar(robot, command.a(), command.b()));
            }
            case FREE_SLOTS -> {
                return Result.ok(freeSlots(inv));
            }
            case IS_INVENTORY_FULL -> {
                return Result.ok(freeSlots(inv) == 0);
            }
            default -> {
            }
        }
        // The rest act on the neighbouring block in a SixDirection.
        Direction dir = relative(facing, command.direction());
        BlockPos target = robot.blockPosition().relative(dir);
        if (!level.isLoaded(target)) return Result.error(Result.Code.UNLOADED, "block is in an unloaded chunk");
        BlockState state = level.getBlockState(target);
        return switch (command.op()) {
            case PLACE -> {
                boolean placed = place(robot, target, dir.getOpposite());
                Codeling.LOG.info("build: {} {} {} with agent.place{}.", label(robot), placed ? "placed a block at" : "could NOT place a block at", at(target),
                        placed ? "" : !(robot.inventory().getItem(robot.selectedSlot() - 1).getItem() instanceof BlockItem) ? ": the selected slot holds no block"
                                : !state.canBeReplaced() ? ": the target is " + name(state.getBlock()) + ", not empty" : ": a claim or protection refused it");
                yield Result.ok(placed);
            }
            case DESTROY -> Result.ok(destroy(robot, target));
            // Block: true where move would be blocked (non-empty collision shape), so water and grass tufts are false.
            case DETECT -> Result.ok(command.b() == RobotCommand.KIND_BLOCK
                    ? !state.getCollisionShape(level, target).isEmpty()
                    : level.hasNeighborSignal(target));
            case INSPECT -> {
                int code = BlockIds.fromModern(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                yield Result.ok(command.b() == RobotCommand.KIND_BLOCK ? code : BlockIds.data(code));
            }
            case STORE_ITEMS, TAKE_ITEMS, TAKE_ITEM, CONTAINER_COUNT, CONTAINER_SIZE, SORT_CONTAINER ->
                    container(robot, command, target, dir.getOpposite());
            case INTERACT -> Result.ok(interact(robot, target, dir.getOpposite()));
            case IS_OPEN -> Result.ok(state.getOptionalValue(BlockStateProperties.OPEN).orElse(false));
            case DETECT_ENTITY -> Result.ok(!entities(level, new AABB(target), command.b()).isEmpty());
            case DETECT_LIQUID -> Result.ok(liquid(level, target));
            case IS_SAFE -> Result.ok(safe(level, target));
            // F3's value: what a source gives out (redstone dust its power, a lever 15), otherwise what the block receives.
            //? if >=26.1 {
            case REDSTONE_POWER -> Result.ok(state.isSignalSource() ? state.getOwnSignal(level, target) : level.getBestNeighborSignal(target));
            //?} else {
            /*// shortcut: 1.21.1 has no getOwnSignal, so a source's power is its strongest side; matches F3 for dust,
            // levers, buttons and torches, may differ for a block that powers one side only. Keep while 1.21.1 is built.
            case REDSTONE_POWER -> Result.ok(state.isSignalSource() ? ownSignal(level, target, state) : level.getBestNeighborSignal(target));
            *///?}
            case DROP -> Result.ok(drop(level, target, inv.removeItem(command.b() - 1, command.c())));
            case DROP_ALL -> {
                boolean any = false;
                for (int i = 0; i < inv.getContainerSize(); i++) any |= drop(level, target, inv.removeItemNoUpdate(i));
                yield Result.ok(any);
            }
            default -> throw new IllegalStateException("unhandled op " + command.op());
        };
    }

    /**
     * The robot's FakePlayer: the owner's UUID with its own name, so claim and protection mods treat robot edits as
     * the owner's (Fabric's FakePlayer Javadoc recommends this; Platform.fakePlayer). Fetched per action, never kept: a strong reference
     * would keep the level loaded. No instabuild, so breaking drops items and placing uses up the stack.
     */
    private static ServerPlayer fakePlayer(RobotEntity robot) {
        ServerLevel level = (ServerLevel) robot.level();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(robot.owner());
        String name = owner != null ? owner.getGameProfile().name() : robot.owner().toString().substring(0, 8);
        ServerPlayer player = Platform.INSTANCE.fakePlayer(level, new GameProfile(robot.owner(), "Codeling[" + name + "]"));
        // Survival rules without setGameMode, which would broadcast a tab-list update for a player clients never saw.
        player.getAbilities().instabuild = false;
        player.snapTo(robot.position(), robot.getYRot(), 0);
        return player;
    }

    // Places the selected slot's block at pos, as the FakePlayer clicking that (replaceable) position on face.
    private static boolean place(RobotEntity robot, BlockPos pos, Direction face) {
        int slot = robot.selectedSlot() - 1;
        ItemStack stack = robot.inventory().getItem(slot);
        if (!(stack.getItem() instanceof BlockItem) || !robot.level().getBlockState(pos).canBeReplaced()) return false;
        return place(robot, stack, pos, face, rest -> robot.inventory().setItem(slot, rest));
    }

    // The FakePlayer holds stack and clicks pos on face; rest gets what is left in its hand afterwards.
    private static boolean place(RobotEntity robot, ItemStack stack, BlockPos pos, Direction face, Consumer<ItemStack> rest) {
        ServerPlayer player = fakePlayer(robot);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        boolean placed = player.gameMode.useItemOn(player, robot.level(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)).consumesAction();
        rest.accept(player.getMainHandItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return placed;
    }

    // Breaks the block through the normal player path (Fabric break events fire, so protection applies).
    private static boolean destroy(RobotEntity robot, BlockPos pos) {
        Level level = robot.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, pos) < 0) return false;
        ServerPlayer player = fakePlayer(robot);
        // shortcut: always a diamond pickaxe so stone and ores drop like a player with the right tool; no silk touch or tool choice.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        boolean broken = player.gameMode.destroyBlock(pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // Drops pop out inside the broken block's space; what does not fit stays in the world.
        if (broken) collect(robot, new AABB(pos).inflate(0.25));
        return broken;
    }

    /**
     * agent.interact: the FakePlayer uses the block with an empty hand, so claim mods and block events apply. Only
     * doors, trapdoors, fence gates, buttons and levers: an empty-handed click on a bed, a cake or a respawn anchor
     * would make the FakePlayer sleep, eat or blow up. True when the block changed (a pressed button does not).
     */
    static boolean interact(RobotEntity robot, BlockPos pos, Direction face) {
        Level level = robot.level();
        BlockState before = level.getBlockState(pos);
        Block block = before.getBlock();
        if (!(block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock)) return false;
        ServerPlayer player = fakePlayer(robot);
        return player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)).consumesAction() && level.getBlockState(pos) != before;
    }

    /**
     * The container ops. A refused or missing container answers false or 0, as an empty hand would find nothing.
     * Items only ever move: what does not fit stays where it was.
     */
    private static Result container(RobotEntity robot, RobotCommand command, BlockPos pos, Direction face) {
        Item item = command.op() == RobotCommand.Op.TAKE_ITEM || command.op() == RobotCommand.Op.CONTAINER_COUNT ? item(command.b()) : null;
        Container c = openContainer(robot, pos, face);
        SimpleContainer inv = robot.inventory();
        return switch (command.op()) {
            case CONTAINER_SIZE -> Result.ok(c == null ? 0 : c.getContainerSize());
            case CONTAINER_COUNT -> Result.ok(c == null ? 0 : c.countItem(item));
            case STORE_ITEMS -> Result.ok(c != null && store(c, inv, face, command.b()));
            case TAKE_ITEMS -> Result.ok(c != null && take(c, inv, face, command.b(), null, command.c()) > 0);
            case TAKE_ITEM -> Result.ok(c == null ? 0 : take(c, inv, face, 0, item, command.c()));
            case SORT_CONTAINER -> Result.ok(c != null && sortable(robot.level().getBlockEntity(pos)) && sort(c));
            default -> throw new IllegalStateException("unhandled op " + command.op());
        };
    }

    /**
     * The container at pos, opened as the owner would open it with an empty hand, or null. The click goes through the
     * FakePlayer's normal player path, so claim and protection mods can refuse it (a FakePlayer opens no screen); a
     * locked container needs its key and a chest under a solid block stays shut, as for a player. Only containers a
     * player opens as a screen (chests, barrels, hoppers, shulker boxes, furnaces, ...): never jukeboxes or shelves,
     * where an empty-handed click would take something out.
     */
    private static Container openContainer(RobotEntity robot, BlockPos pos, Direction face) {
        Level level = robot.level();
        if (!(level.getBlockEntity(pos) instanceof BaseContainerBlockEntity entity)) return null;
        ServerPlayer player = fakePlayer(robot);
        if (!entity.canOpen(player)) return null;
        if (!player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)).consumesAction()) return null;
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock chest ? ChestBlock.getContainer(chest, state, level, pos, false) : entity;
    }

    // storeItems: one robot slot or all (0), each stack put in by vanilla hopper rules for that face (a furnace takes
    // fuel from the side and what to smelt from above). True when anything moved.
    private static boolean store(Container c, SimpleContainer inv, Direction face, int slot) {
        boolean moved = false;
        for (int i = slot == 0 ? 0 : slot - 1; i < (slot == 0 ? inv.getContainerSize() : slot); i++) {
            int before = inv.getItem(i).getCount();
            if (before == 0) continue;
            ItemStack rest = HopperBlockEntity.addItem(null, c, inv.removeItemNoUpdate(i), face);
            inv.setItem(i, rest);
            moved |= rest.getCount() < before;
        }
        return moved;
    }

    /**
     * Moves up to count items into the robot from the slots a hopper on that face could take from (a furnace gives
     * its output downwards). With an item: from any slot until count or the robot is full. Without: from one stack,
     * the given 1-based slot or the first one (0). Answers how many moved.
     */
    private static int take(Container c, SimpleContainer inv, Direction face, int slot, Item item, int count) {
        int[] slots = c instanceof WorldlyContainer w ? w.getSlotsForFace(face) : IntStream.range(0, c.getContainerSize()).toArray();
        int taken = 0;
        for (int i : slots) {
            ItemStack stack = c.getItem(i);
            if (slot != 0 && i != slot - 1 || stack.isEmpty() || item != null && !stack.is(item) || !c.canTakeItem(inv, i, stack)
                    || c instanceof WorldlyContainer w && !w.canTakeItemThroughFace(i, stack, face)) continue;
            int n = Math.min(count - taken, stack.getCount());
            int moved = n - inv.addItem(stack.copyWithCount(n)).getCount();
            if (moved > 0) c.removeItem(i, moved);
            taken += moved;
            if (item == null || moved < n || taken == count) break;
        }
        if (taken > 0) c.setChanged();
        return taken;
    }

    // Plain storage only: sorting a furnace, brewing stand or crafter would mix up slots that mean something.
    private static boolean sortable(BlockEntity entity) {
        return entity instanceof RandomizableContainerBlockEntity && !(entity instanceof CrafterBlockEntity);
    }

    // Merges equal stacks and orders them by item id (minecraft:cobblestone before minecraft:dirt); empty slots go last.
    private static boolean sort(Container c) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack stack = c.removeItemNoUpdate(i);
            for (ItemStack into : stacks) {
                if (stack.isEmpty()) break;
                if (!ItemStack.isSameItemSameComponents(into, stack)) continue;
                int n = Math.min(stack.getCount(), c.getMaxStackSize(into) - into.getCount());
                into.grow(n);
                stack.shrink(n);
            }
            if (!stack.isEmpty()) stacks.add(stack);
        }
        stacks.sort(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
        c.setChanged();
        return true;
    }

    /**
     * Issue #86: living entities of an EntityKind in the box. Mob: the hostile ones (mob category monster). Animal: the
     * other natural mobs (animals, fish, bats, axolotls); villagers and golems are neither. Player: not in spectator
     * mode and not invisible (issue #158), so no program can find a hiding player. Item: dropped items. The robot
     * itself is none of these.
     */
    private static List<Entity> entities(Level level, AABB box, int kind) {
        return level.getEntities((Entity) null, box, e -> e.isAlive() && !e.isSpectator() && switch (kind) {
            case MOB -> e.getType().getCategory() == MobCategory.MONSTER;
            case 1 -> e.getType().getCategory() != MobCategory.MONSTER && e.getType().getCategory() != MobCategory.MISC;
            case 2 -> e instanceof Player && !e.isInvisible();
            default -> e instanceof ItemEntity;
        });
    }

    /**
     * agent.radar (issue #159): the entities of a kind in the cube of that radius around the robot, as countNearby
     * finds them, nearest first (straight-line distance from the robot), at most {@link RobotCommand#MAX_RADAR}. Answers
     * their block positions in one flat list, [x1, y1, z1, x2, y2, z2, ...], empty when there is none.
     */
    private static List<Integer> radar(RobotEntity robot, int kind, int radius) {
        List<Integer> found = new ArrayList<>();
        entities(robot.level(), new AABB(robot.blockPosition()).inflate(radius), kind).stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(robot)))
                .limit(RobotCommand.MAX_RADAR)
                .forEach(e -> found.addAll(List.of(e.getBlockX(), e.getBlockY(), e.getBlockZ())));
        return found;
    }

    // agent.detectLiquid (issue #161): the liquid in the block, waterlogged blocks count as water.
    private static int liquid(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.LAVA) ? LAVA : fluid.is(FluidTags.WATER) ? WATER : 0;
    }

    /**
     * agent.isSafe (issue #161): false when the block holds lava; when the block is free and lava or more than
     * {@link #SAFE_DROP} free blocks lie under it before the ground (a fall of 4 or more); or when a hostile mob is in
     * the block or touches one of the 26 blocks around it. A block that would stop a move is checked for lava and mobs only.
     */
    // shortcut: water at the bottom of a deep drop still counts as a fall; add it when players ask for diving.
    private static boolean safe(Level level, BlockPos pos) {
        if (liquid(level, pos) == LAVA) return false;
        if (!blocked(level, pos)) {
            for (int i = 1; ; i++) {
                BlockPos below = pos.below(i);
                if (liquid(level, below) == LAVA) return false;
                if (blocked(level, below)) break;
                if (i > SAFE_DROP) return false;
            }
        }
        return entities(level, new AABB(pos).inflate(1), MOB).isEmpty();
    }

    //? if <26.1 {
    /*private static int ownSignal(Level level, BlockPos pos, BlockState state) {
        int power = 0;
        for (Direction d : Direction.values()) power = Math.max(power, state.getSignal(level, pos, d));
        return power;
    }

    *///?}
    private static int freeSlots(SimpleContainer inv) {
        int free = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) free++;
        return free;
    }

    // An item code the parser has validated (ItemIds), so the registry lookup always finds it.
    private static Item item(int code) {
        return McCompat.item(McCompat.itemId(ItemIds.toModern(code)));
    }

    private static void collect(RobotEntity robot, AABB box) {
        for (ItemEntity item : robot.level().getEntitiesOfClass(ItemEntity.class, box, Entity::isAlive)) {
            ItemStack rest = robot.inventory().addItem(item.getItem());
            if (rest.isEmpty()) item.discard();
            else item.setItem(rest);
        }
    }

    private static boolean drop(Level level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return false;
        Vec3 at = Vec3.atCenterOf(pos);
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        return true;
    }

    private static boolean transfer(SimpleContainer inv, int quantity, int from, int to) {
        ItemStack source = inv.getItem(from);
        ItemStack dest = inv.getItem(to);
        if (from == to || source.isEmpty() || !dest.isEmpty() && !ItemStack.isSameItemSameComponents(source, dest)) return false;
        int room = dest.isEmpty() ? source.getMaxStackSize() : dest.getMaxStackSize() - dest.getCount();
        int n = Math.min(quantity, Math.min(source.getCount(), room));
        if (n <= 0) return false;
        if (dest.isEmpty()) {
            inv.setItem(to, source.split(n));
        } else {
            dest.grow(n);
            source.shrink(n);
        }
        inv.setChanged();
        return true;
    }

    /**
     * In survival the robot gathers its own resources. setItem and building without items are allowed only when the
     * owner is online and in creative, or when the world's gamerule codeling:free_items is true.
     */
    private static boolean freeItems(RobotEntity robot) {
        MinecraftServer server = robot.level().getServer();
        if (Codeling.freeItems(server)) return true;
        ServerPlayer owner = server.getPlayerList().getPlayer(robot.owner());
        return owner != null && owner.isCreative();
    }

    /**
     * MakeCode's yaw convention (Bedrock agent): south 0, west 90, north 180, east -90. Java's yaw is the same
     * except that it says 270 for east, so only east is folded. shortcut: unconfirmed against MakeCode in game (issue #20).
     */
    public static int orientation(Direction facing) {
        int yaw = (int) facing.toYRot();
        return yaw > 180 ? yaw - 360 : yaw;
    }

    static Direction relative(Direction facing, int sixDirection) {
        return switch (sixDirection) {
            case RobotCommand.FORWARD -> facing;
            case RobotCommand.BACK -> facing.getOpposite();
            case RobotCommand.LEFT -> facing.getCounterClockWise();
            case RobotCommand.RIGHT -> facing.getClockWise();
            case RobotCommand.UP -> Direction.UP;
            case RobotCommand.DOWN -> Direction.DOWN;
            default -> throw new IllegalArgumentException("SixDirection " + sixDirection);
        };
    }
}
