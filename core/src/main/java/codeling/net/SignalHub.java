package codeling.net;

import codeling.api.Signals;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Codeling's side of {@link Signals} (issue #249): the posts waiting for the server thread and the listeners of other
 * mods. Other mods use {@link Signals}; only Codeling's server calls {@link #deliver}, {@link #send} and {@link #clear}.
 */
public final class SignalHub {
    // shortcut: 256 posts per tick for all players together is a guess at "far more than a bridge needs"; raise it if
    // bridges see post answer false.
    public static final int MAX_WAITING = 256;
    private static final Logger LOG = LoggerFactory.getLogger("codeling/signals");

    /** A posted signal waiting for the server thread. */
    public record Post(UUID player, String name, double value) {
    }

    private static final ConcurrentLinkedQueue<Post> WAITING = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger COUNT = new AtomicInteger();

    // One per onSent call, so the same listener added twice is two registrations, each removed by its own Runnable.
    private static final class Registration {
        final Signals.Listener listener;
        volatile boolean failed;

        Registration(Signals.Listener listener) {
            this.listener = listener;
        }
    }

    private static final List<Registration> LISTENERS = new CopyOnWriteArrayList<>();

    private SignalHub() {
    }

    /** Any thread. False when {@link #MAX_WAITING} posts already wait; the caller has checked the arguments. */
    public static boolean post(UUID player, String name, double value) {
        if (COUNT.incrementAndGet() > MAX_WAITING) {
            COUNT.decrementAndGet();
            return false;
        }
        WAITING.add(new Post(player, name, value));
        return true;
    }

    public static Runnable add(Signals.Listener listener) {
        Registration r = new Registration(listener);
        LISTENERS.add(r);
        return () -> LISTENERS.remove(r);
    }

    /** Server thread, each tick: hands the waiting posts to {@code to}, at most {@link #MAX_WAITING} of them. */
    public static void deliver(java.util.function.Consumer<Post> to) {
        for (int i = 0; i < MAX_WAITING; i++) {
            Post p = WAITING.poll();
            if (p == null) return;
            COUNT.decrementAndGet();
            to.accept(p);
        }
    }

    /** What signals.send answers (with false) when no mod listens for signals at all. */
    public static String nobodyListens(String name) {
        return "No mod on this server listens for signals, so \"" + name + "\" went nowhere.";
    }

    public static boolean hasListeners() {
        return !LISTENERS.isEmpty();
    }

    /**
     * Server thread: {@code player}'s program sent {@code name} (already checked). Every listener hears it; true when
     * at least one took it. A listener that throws counts as false and is logged once, so a broken mod never stops
     * the tick or the program.
     */
    public static boolean send(UUID player, String name) {
        boolean taken = false;
        for (Registration r : LISTENERS) {
            try {
                taken |= r.listener.sent(player, name);
            } catch (RuntimeException | LinkageError e) {
                if (!r.failed) LOG.warn("A signal listener ({}) failed on signal \"{}\"; further failures of it are not logged.", r.listener.getClass().getName(), name, e);
                r.failed = true;
            }
        }
        return taken;
    }

    /** The server stopped: posts for it are dropped. Listeners stay, as their mods do. */
    public static void clear() {
        while (WAITING.poll() != null) COUNT.decrementAndGet();
    }
}
