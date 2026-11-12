package codeling.net;

import codeling.Codeling;
import codeling.McCompat;
import codeling.robot.BlockIds;
import codeling.robot.RobotEntity;
import codeling.robot.RobotManager;
import codeling.robot.RobotQueue;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chat and death events (issue #27). A program subscribes at start to its onChat keywords and its onDied handler; then
 * its owner's own chat lines that match a keyword, and the owner's own death, become {@code event} messages to that
 * owner's editor, never anyone else's. Only the keyword and the numbers go, never the line. Everything ends with the
 * run: a stopped run (Stop, /robot stop, heartbeat timeout, leaving, a new run) has no listeners, so its owner's chat is
 * broadcast as usual again. Server thread only.
 * <p>
 * Issue #89: the blocks the owner breaks by hand (never the robot's FakePlayer), a redstone signal reaching the owner's
 * default robot (a rising edge, looked at each tick only while it is loaded) and the start of a new day in the
 * owner's dimension (the time of day wrapping round, looked at each tick). The last two cost a lookup per tick only
 * for a run that listens for them.
 * <p>
 * Issue #249: a signal another mod posted through {@link codeling.api.Signals} (handed over at the start of each tick)
 * or an operator sent with /codeling signal, for the named player's current run only, when it listens for that name.
 */
public final class PlayerEvents {
    private static final class Listening {
        final String name;
        final long run;
        final Protocol.Subscription subscription;
        final Consumer<String> out;
        final ArrayDeque<String> waiting = new ArrayDeque<>();
        // What the last tick saw: whether the robot's block had a signal, and the time of day (-1 before the first look).
        boolean powered;
        long timeOfDay = -1;

        Listening(String name, long run, Protocol.Subscription subscription, Consumer<String> out) {
            this.name = name;
            this.run = run;
            this.subscription = subscription;
            this.out = out;
        }
    }

    private static final Map<UUID, Listening> LISTENING = new HashMap<>();

    private PlayerEvents() {
    }

    /** {@code control subscribe} for the owner's current run; {@code out} reaches their editor. Replaces what it listened for. */
    static void subscribe(UUID owner, String name, long run, Protocol.Subscription subscription, Consumer<String> out) {
        Listening l = new Listening(name, run, subscription, out);
        // A robot that already has a signal when the program starts gives no event: only a new signal counts.
        RobotEntity robot = RobotManager.robot(owner);
        l.powered = subscription.redstone() && robot != null && RobotManager.redstonePower(robot.level(), robot.blockPosition()) > 0;
        LISTENING.put(owner, l);
        Codeling.LOG.info("events: {}'s program (run {}) listens for {} chat command(s){}, {} broken block(s){}{}, {} signal(s).", name, run,
                subscription.chat().size(), subscription.died() ? ", their death" : "", subscription.broken().size(),
                subscription.redstone() ? ", redstone at the robot" : "", subscription.dayStart() ? ", the day start" : "", subscription.signals().size());
    }

    /** The run ended in a way the editor already knows about (Stop, a new run, timeout, leaving): forget it at once. */
    static void ended(UUID owner) {
        LISTENING.remove(owner);
    }

    static void serverStopped() {
        LISTENING.clear();
        SignalHub.clear();
    }

    // Only while the run that subscribed is still the owner's current run: /robot stop ends it at once.
    private static Listening active(UUID owner) {
        Listening l = LISTENING.get(owner);
        return l != null && l.run == RobotManager.queue(owner).run() ? l : null;
    }

    /** True while the owner has a running program with at least one onChat handler (for /codeling chat's answer). */
    public static boolean listensForChat(UUID owner) {
        Listening l = active(owner);
        return l != null && !l.subscription.chat().isEmpty();
    }

    /**
     * A chat line {@code player} sent (a chat message or /codeling chat). True when their running program listens for
     * its first word (or "*"): the caller must then not broadcast it. The event waits for the next tick.
     */
    public static boolean chat(ServerPlayer player, String text) {
        Listening l = active(player.getUUID());
        String command = l == null ? null : ChatCommands.match(l.subscription.chat(), text);
        if (command == null) return false;
        double[] n = ChatCommands.numbers(text);
        queue(l, Protocol.event("chat", List.of(command, n[0], n[1], n[2])));
        return true;
    }

    /** {@code player} died; their running program gets the event if it has an onDied handler. */
    public static void died(ServerPlayer player) {
        Listening l = active(player.getUUID());
        if (l != null && l.subscription.died()) queue(l, Protocol.event("playerDied", List.of()));
    }

    /**
     * {@code player} broke a block of {@code state} by hand (the loader calls this only for a break that happened). Their
     * running program gets the event if it listens for that block. Never the robot's breaks: its FakePlayer carries the
     * owner's UUID, so the caller leaves FakePlayers out.
     */
    public static void blockBroken(ServerPlayer player, BlockState state) {
        Listening l = active(player.getUUID());
        if (l == null || l.subscription.broken().isEmpty()) return;
        int code = BlockIds.fromModern(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (l.subscription.broken().contains(code)) queue(l, Protocol.event("blockBroken", List.of(code)));
    }

    /**
     * Signal {@code name} with {@code value} for {@code owner} (issue #249). True when their running program listens
     * for that name and the event waits for a tick; false when it does not, or {@link RobotQueue#MAX_LENGTH} wait already.
     */
    public static boolean signal(UUID owner, String name, double value) {
        Listening l = active(owner);
        return l != null && l.subscription.signals().contains(name) && queue(l, Protocol.event("signal", List.of(name, value)));
    }

    private static boolean queue(Listening l, String event) {
        if (l.waiting.size() >= RobotQueue.MAX_LENGTH) {
            Codeling.LOG.info("events: {}'s program has {} events waiting, so one was dropped.", l.name, RobotQueue.MAX_LENGTH);
            return false;
        }
        l.waiting.add(event);
        return true;
    }

    // Issue #89: a new signal at the owner's default robot, and the time of day wrapping round in the owner's
    // dimension. Only a robot that is loaded is looked at, so no chunk is ever loaded for it.
    private static void watch(MinecraftServer server, UUID owner, Listening l) {
        if (l.subscription.redstone()) {
            RobotEntity robot = RobotManager.robot(owner);
            if (robot != null && robot.level().isLoaded(robot.blockPosition())) {
                boolean powered = RobotManager.redstonePower(robot.level(), robot.blockPosition()) > 0;
                if (powered && !l.powered) queue(l, Protocol.event("redstonePowered", List.of()));
                l.powered = powered;
            }
        }
        ServerPlayer player = l.subscription.dayStart() ? server.getPlayerList().getPlayer(owner) : null;
        if (player != null) {
            long time = Math.floorMod(McCompat.dayTime(player.level()), 24000L);
            if (l.timeOfDay >= 0 && time < l.timeOfDay) queue(l, Protocol.event("dayStart", List.of()));
            l.timeOfDay = time;
        }
    }

    /**
     * Each tick: one waiting event per owner. A run stopped in the game (/robot stop, /robot stop all) loses its
     * listeners and its waiting events, and its editor is told now: a program with only handlers has no command
     * waiting that would learn it otherwise before the next heartbeat.
     */
    static void tick(MinecraftServer server) {
        SignalHub.deliver(p -> signal(p.player(), p.name(), p.value()));
        LISTENING.entrySet().removeIf(e -> {
            Listening l = e.getValue();
            if (l.run != RobotManager.queue(e.getKey()).run()) {
                l.out.accept(Protocol.control("stop", l.run, "The robot's run was stopped in the game."));
                return true;
            }
            watch(server, e.getKey(), l);
            String event = l.waiting.poll();
            if (event != null) l.out.accept(event);
            return false;
        });
    }
}
