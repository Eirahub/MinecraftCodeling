package codeling.api;

import codeling.net.ChatCommands;
import codeling.net.SignalHub;
import java.util.UUID;

/**
 * Named signals (issue #249): the hook another mod (a bridge) uses to talk with the programs players run in Codeling.
 * Plain Java with no Minecraft types, so one bridge jar compiles against it for every Minecraft version.
 * <p>
 * In: {@link #post} a signal for one player. Their program gets it only while that player is in the game, their
 * current run is going and it has an on signal handler for that name; otherwise it is dropped and nobody is told. Out:
 * {@link #onSent} hears {@code signals.send(name)} from a program, the name and the player only.
 * <p>
 * A name is one word of letters, digits, - and _, 1 to {@link #MAX_NAME_LENGTH} characters ({@link #nameProblem}).
 * Case matters. The bridge decides which of its own events become signals and for which player: Codeling trusts a
 * signal as much as the mod that posts it.
 */
public final class Signals {
    public static final int MAX_NAME_LENGTH = ChatCommands.MAX_KEYWORD_LENGTH;

    private Signals() {
    }

    /** Hears the signals programs send. Called on the server thread, never for a name that breaks the rules. */
    @FunctionalInterface
    public interface Listener {
        /**
         * {@code player}'s program sent {@code name}. Return true when this listener takes the name (the program's
         * call then answers true), false when it is not one of yours. Return quickly: it runs inside the server tick.
         * An exception is caught and logged (once per listener), and counts as false.
         */
        boolean sent(UUID player, String name);
    }

    /** Why {@code name} cannot be a signal name, or null when it can. */
    public static String nameProblem(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) return "a signal name must be 1 to " + MAX_NAME_LENGTH + " characters long";
        return ChatCommands.isWord(name) ? null : "a signal name is one word of letters, digits, - and _";
    }

    /**
     * Sends signal {@code name} with {@code value} to {@code player}'s program. Any thread; it is delivered on the
     * server thread at the next tick. True when it was taken for delivery, which does not mean a program listens.
     * False, and nothing is sent, when {@code player} is null, the name breaks the rules, the value is not finite or
     * {@link SignalHub#MAX_WAITING} posts already wait for the tick.
     */
    public static boolean post(UUID player, String name, double value) {
        return player != null && nameProblem(name) == null && Double.isFinite(value) && SignalHub.post(player, name, value);
    }

    /**
     * Adds a listener for the signals programs send, from any thread. Returns the way to remove it again: run it once
     * (later runs do nothing).
     */
    public static Runnable onSent(Listener listener) {
        if (listener == null) throw new NullPointerException("listener");
        return SignalHub.add(listener);
    }
}
