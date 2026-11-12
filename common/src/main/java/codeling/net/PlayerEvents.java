package codeling.net;

import codeling.Codeling;
import codeling.robot.RobotManager;
import codeling.robot.RobotQueue;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chat and death events (issue #27). A program subscribes at start to its onChat keywords and its onDied handler; then
 * its owner's own chat lines that match a keyword, and the owner's own death, become {@code event} messages to that
 * owner's editor, never anyone else's. Only the keyword and the numbers go, never the line. Everything ends with the
 * run: a stopped run (Stop, /robot stop, heartbeat timeout, leaving, a new run) has no listeners, so its owner's chat is
 * broadcast as usual again. Server thread only.
 */
public final class PlayerEvents {
    private record Listening(long run, Protocol.Subscription subscription, Consumer<String> out, ArrayDeque<String> waiting) {
    }

    private static final Map<UUID, Listening> LISTENING = new HashMap<>();

    private PlayerEvents() {
    }

    /** {@code control subscribe} for the owner's current run; {@code out} reaches their editor. Replaces what it listened for. */
    static void subscribe(UUID owner, String name, long run, Protocol.Subscription subscription, Consumer<String> out) {
        LISTENING.put(owner, new Listening(run, subscription, out, new ArrayDeque<>()));
        Codeling.LOG.info("events: {}'s program (run {}) listens for {} chat command(s){}.", name, run,
                subscription.chat().size(), subscription.died() ? " and their death" : "");
    }

    /** The run ended in a way the editor already knows about (Stop, a new run, timeout, leaving): forget it at once. */
    static void ended(UUID owner) {
        LISTENING.remove(owner);
    }

    static void serverStopped() {
        LISTENING.clear();
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
        queue(player, l, Protocol.event("chat", List.of(command, n[0], n[1], n[2])));
        return true;
    }

    /** {@code player} died; their running program gets the event if it has an onDied handler. */
    public static void died(ServerPlayer player) {
        Listening l = active(player.getUUID());
        if (l != null && l.subscription.died()) queue(player, l, Protocol.event("playerDied", List.of()));
    }

    private static void queue(ServerPlayer player, Listening l, String event) {
        if (l.waiting.size() >= RobotQueue.MAX_LENGTH) {
            Codeling.LOG.info("events: {}'s program has {} events waiting, so one was dropped.", player.getGameProfile().name(), RobotQueue.MAX_LENGTH);
            return;
        }
        l.waiting.add(event);
    }

    /**
     * Each tick: one waiting event per owner. A run stopped in the game (/robot stop, /robot stop all) loses its
     * listeners and its waiting events, and its editor is told now: a program with only handlers has no command
     * waiting that would learn it otherwise before the next heartbeat.
     */
    static void tick() {
        LISTENING.entrySet().removeIf(e -> {
            Listening l = e.getValue();
            if (l.run != RobotManager.queue(e.getKey()).run()) {
                l.out.accept(Protocol.control("stop", l.run, "The robot's run was stopped in the game."));
                return true;
            }
            String event = l.waiting.poll();
            if (event != null) l.out.accept(event);
            return false;
        });
    }
}
