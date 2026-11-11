package codeling.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Heartbeat bookkeeping for browser runs: per player the run the browser started, when it was last heard from
 * (server tick), and where to send messages. Plain Java so the timeout rules are unit-testable. Server thread only.
 */
public final class Sessions {
    /** One browser run. {@code out} sends a protocol message to the player's relay. */
    public static final class Session {
        public final long run;
        public final Consumer<String> out;
        long lastSeen;

        Session(long run, long now, Consumer<String> out) {
            this.run = run;
            this.lastSeen = now;
            this.out = out;
        }
    }

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final long timeout;

    public Sessions(long timeout) {
        this.timeout = timeout;
    }

    public void start(UUID player, long run, long now, Consumer<String> out) {
        sessions.put(player, new Session(run, now, out));
    }

    /** Marks the run as alive. False if {@code run} is not the player's current browser run. */
    public boolean touch(UUID player, long run, long now) {
        Session s = sessions.get(player);
        if (s == null || s.run != run) return false;
        s.lastSeen = now;
        return true;
    }

    public Session get(UUID player) {
        return sessions.get(player);
    }

    public Session remove(UUID player) {
        return sessions.remove(player);
    }

    public void clear() {
        sessions.clear();
    }

    /** Removes and returns every session not heard from for {@code timeout} or longer. */
    public List<Map.Entry<UUID, Session>> expire(long now) {
        List<Map.Entry<UUID, Session>> expired = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> e = it.next();
            if (now - e.getValue().lastSeen >= timeout) {
                expired.add(Map.entry(e.getKey(), e.getValue()));
                it.remove();
            }
        }
        return expired;
    }
}
