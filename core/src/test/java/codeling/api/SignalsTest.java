package codeling.api;

import codeling.net.SignalHub;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Issue #249: the hook other mods use. Name rules, the queue to the server thread and its cap, and the listeners.
class SignalsTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @BeforeEach
    void empty() {
        SignalHub.clear();
    }

    private static List<SignalHub.Post> delivered() {
        List<SignalHub.Post> posts = new ArrayList<>();
        SignalHub.deliver(posts::add);
        return posts;
    }

    @Test
    void nameRules() {
        for (String ok : new String[] {"qr-forest", "lamp_2", "7", "løp", "a".repeat(Signals.MAX_NAME_LENGTH)}) assertNull(Signals.nameProblem(ok), ok);
        for (String bad : new String[] {null, "", "*", "two words", "lamp!", "a/b", "§c", "a".repeat(Signals.MAX_NAME_LENGTH + 1)}) {
            assertNotNull(Signals.nameProblem(bad), bad);
        }
    }

    @Test
    void postWaitsForTheServerTickAndChecksItsArguments() {
        assertTrue(Signals.post(PLAYER, "qr-forest", 3));
        assertTrue(Signals.post(PLAYER, "lamp", -0.5));
        assertFalse(Signals.post(null, "qr", 1), "no player");
        assertFalse(Signals.post(PLAYER, "two words", 1), "bad name");
        assertFalse(Signals.post(PLAYER, "qr", Double.NaN), "NaN");
        assertFalse(Signals.post(PLAYER, "qr", Double.POSITIVE_INFINITY), "infinity");
        assertEquals(List.of(new SignalHub.Post(PLAYER, "qr-forest", 3), new SignalHub.Post(PLAYER, "lamp", -0.5)), delivered());
        assertEquals(List.of(), delivered(), "each post once");
    }

    @Test
    void theQueueIsCappedAndFreesUpWhenDelivered() {
        for (int i = 0; i < SignalHub.MAX_WAITING; i++) assertTrue(Signals.post(PLAYER, "n" + i, i));
        assertFalse(Signals.post(PLAYER, "one-too-many", 0), "full");
        assertEquals(SignalHub.MAX_WAITING, delivered().size());
        assertTrue(Signals.post(PLAYER, "again", 0), "room after the tick");
        SignalHub.clear();
        assertEquals(List.of(), delivered(), "cleared when the server stops");
    }

    @Test
    void postFromOtherThreads() throws InterruptedException {
        int threads = 8, each = 20;
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> started = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int n = t;
            Thread thread = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < each; i++) Signals.post(PLAYER, "t" + n, i);
            });
            thread.start();
            started.add(thread);
        }
        go.countDown();
        for (Thread thread : started) thread.join();
        List<SignalHub.Post> posts = delivered();
        assertEquals(threads * each, posts.size());
        for (int t = 0; t < threads; t++) {
            String name = "t" + t;
            assertEquals(List.of(0.0, 1.0, 2.0), posts.stream().filter(p -> p.name().equals(name)).limit(3).map(SignalHub.Post::value).toList(), "one thread's posts in order");
        }
    }

    @Test
    void listenersTakeNamesAndAreRemoved() {
        assertFalse(SignalHub.hasListeners());
        List<String> heard = new ArrayList<>();
        AtomicInteger thrown = new AtomicInteger();
        Runnable lamp = Signals.onSent((player, name) -> heard.add(name) && name.startsWith("lamp"));
        Runnable broken = Signals.onSent((player, name) -> {
            thrown.incrementAndGet();
            throw new IllegalStateException("a broken listener");
        });
        assertTrue(SignalHub.send(PLAYER, "lamp-red"), "taken by one, though another throws");
        assertFalse(SignalHub.send(PLAYER, "door"), "taken by none");
        assertEquals(List.of("lamp-red", "door"), heard);
        assertEquals(2, thrown.get(), "a listener that throws is still asked");
        broken.run();
        lamp.run();
        lamp.run();
        assertFalse(SignalHub.hasListeners());
        assertFalse(SignalHub.send(PLAYER, "lamp-red"));
        assertThrows(NullPointerException.class, () -> Signals.onSent(null));
    }
}
