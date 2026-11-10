package codeling.relay;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The relay with a fake game link: welcome, forwarding both ways, no-world errors, the second-session rule. */
class RelayForwardingTest {
    static final int PORT = 25598;
    static final LinkedBlockingQueue<String> toServer = new LinkedBlockingQueue<>();
    static volatile String problem;
    static RelayServer server;
    static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        server = new RelayServer(PORT, new RelayServer.Link() {
            @Override
            public String playerName() {
                return "Steve";
            }

            @Override
            public String send(String json) {
                if (problem != null) return problem;
                toServer.add(json);
                return null;
            }
        }).start();
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @BeforeEach
    void reset() {
        toServer.clear();
        problem = null;
    }

    @Test
    void welcomeHasPlayerAndLimits() throws Exception {
        var inbox = new LinkedBlockingQueue<String>();
        hello(inbox);
        String welcome = inbox.poll(2, TimeUnit.SECONDS);
        assertNotNull(welcome);
        assertTrue(welcome.contains("\"t\":\"welcome\"") && welcome.contains("\"player\":\"Steve\""), welcome);
        assertTrue(welcome.contains("\"maxMessageBytes\":8192") && welcome.contains("\"server\":" + codeling.net.Protocol.VERSION), welcome);
    }

    @Test
    void forwardsBothWays() throws Exception {
        var inbox = new LinkedBlockingQueue<String>();
        WebSocket ws = hello(inbox);
        inbox.poll(2, TimeUnit.SECONDS); // welcome
        String cmd = "{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":4,\"op\":\"agent.move\",\"args\":[0,3]}";
        ws.sendText(cmd, true).join();
        assertEquals(cmd, toServer.poll(2, TimeUnit.SECONDS));
        server.deliver("{\"v\":2,\"t\":\"result\",\"id\":1,\"ok\":true,\"value\":true}");
        String result = inbox.poll(2, TimeUnit.SECONDS);
        assertNotNull(result);
        assertTrue(result.contains("\"t\":\"result\""), result);
    }

    @Test
    void notInWorldAnswersError() throws Exception {
        problem = "Not in a world.";
        var inbox = new LinkedBlockingQueue<String>();
        WebSocket ws = hello(inbox);
        inbox.poll(2, TimeUnit.SECONDS);
        ws.sendText("{\"v\":2,\"t\":\"cmd\",\"id\":2,\"run\":4,\"op\":\"agent.move\",\"args\":[0,3]}", true).join();
        String err = inbox.poll(2, TimeUnit.SECONDS);
        assertNotNull(err);
        assertTrue(err.contains("\"t\":\"error\"") && err.contains("\"id\":2") && err.contains("Not in a world."), err);
    }

    // Issue #4: an old tab after a game restart holds a dead token; the sim turns NO_PERMISSION before welcome into "open with /codeling".
    @Test
    void staleTokenIsRefused() throws Exception {
        var inbox = new LinkedBlockingQueue<String>();
        WebSocket ws = connect(inbox);
        ws.sendText("{\"v\":2,\"t\":\"hello\",\"token\":\"from-the-last-game-session\"}", true).join();
        String err = inbox.poll(2, TimeUnit.SECONDS);
        assertNotNull(err);
        assertTrue(err.contains("\"code\":\"NO_PERMISSION\"") && !err.contains("welcome"), err);
        ws.sendText("{\"v\":2,\"t\":\"control\",\"op\":\"start\"}", true);
        assertNull(toServer.poll(300, TimeUnit.MILLISECONDS), "nothing forwarded without a valid token");
    }

    // Issue #5: closing the tab stops its run at once, without waiting for the heartbeat timeout.
    @Test
    void closingTheTabStopsTheRun() throws Exception {
        var inbox = new LinkedBlockingQueue<String>();
        WebSocket ws = hello(inbox);
        inbox.poll(2, TimeUnit.SECONDS);
        ws.sendText("{\"v\":2,\"t\":\"control\",\"op\":\"start\"}", true).join();
        assertTrue(toServer.poll(2, TimeUnit.SECONDS).contains("start"));
        server.deliver("{\"v\":2,\"t\":\"control\",\"op\":\"start\",\"run\":7}");
        assertTrue(inbox.poll(2, TimeUnit.SECONDS).contains("\"run\":7"));
        ws.abort();
        String stop = toServer.poll(2, TimeUnit.SECONDS);
        assertNotNull(stop);
        assertTrue(stop.contains("\"op\":\"stop\"") && stop.contains("\"run\":7"), stop);
    }

    @Test
    void versionMismatchIsNotForwarded()throws Exception {
        var inbox = new LinkedBlockingQueue<String>();
        WebSocket ws = hello(inbox);
        inbox.poll(2, TimeUnit.SECONDS);
        ws.sendText("{\"v\":1,\"t\":\"cmd\",\"id\":3}", true).join();
        String err = inbox.poll(2, TimeUnit.SECONDS);
        assertNotNull(err);
        assertTrue(err.contains("\"code\":\"VERSION\""), err);
        assertNull(toServer.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void newSessionTakesOverAndOldRunIsStopped() throws Exception {
        var first = new LinkedBlockingQueue<String>();
        WebSocket a = hello(first);
        first.poll(2, TimeUnit.SECONDS);
        a.sendText("{\"v\":2,\"t\":\"control\",\"op\":\"start\"}", true).join();
        assertTrue(toServer.poll(2, TimeUnit.SECONDS).contains("start"));
        server.deliver("{\"v\":2,\"t\":\"control\",\"op\":\"start\",\"run\":42}");
        assertTrue(first.poll(2, TimeUnit.SECONDS).contains("42"));

        var second = new LinkedBlockingQueue<String>();
        hello(second);
        assertTrue(second.poll(2, TimeUnit.SECONDS).contains("welcome"));
        String stop = first.poll(2, TimeUnit.SECONDS);
        assertNotNull(stop);
        assertTrue(stop.contains("\"op\":\"stop\"") && stop.contains("took over"), stop);
        // The old session's run is stopped on the server when its socket closes.
        String forwarded = toServer.poll(2, TimeUnit.SECONDS);
        assertNotNull(forwarded);
        assertTrue(forwarded.contains("\"op\":\"stop\"") && forwarded.contains("\"run\":42"), forwarded);
        // Server messages now go to the new session only.
        server.deliver("{\"v\":2,\"t\":\"result\",\"id\":1,\"ok\":true}");
        assertNotNull(second.poll(2, TimeUnit.SECONDS));
        assertNull(first.poll(200, TimeUnit.MILLISECONDS));
    }

    private WebSocket hello(LinkedBlockingQueue<String> inbox) {
        WebSocket ws = connect(inbox);
        String token = server.editorUrl().substring(server.editorUrl().indexOf("=") + 1);
        ws.sendText("{\"v\":2,\"t\":\"hello\",\"token\":\"" + token + "\"}", true).join();
        return ws;
    }

    private WebSocket connect(LinkedBlockingQueue<String> inbox) {
        return http.newWebSocketBuilder()
                .header("Origin", "http://127.0.0.1:" + PORT)
                .buildAsync(URI.create("ws://127.0.0.1:" + PORT + "/relay"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        inbox.add(data.toString());
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .join();
    }
}
