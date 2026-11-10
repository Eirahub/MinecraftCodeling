package codeling.relay;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Headless checks of the relay's trust boundary and the stub ack. No Minecraft needed. */
class RelayServerTest {
    static final int PORT = 25599;
    static RelayServer server;
    static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        server = new RelayServer(PORT).start();
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Test
    void tokenIsRandomAndLong() {
        assertNotEquals(RelayServer.newToken(), RelayServer.newToken());
        assertEquals(43, RelayServer.newToken().length());
        assertFalse(server.tokenMatches("nope"));
        assertFalse(server.tokenMatches(null));
    }

    @Test
    void rejectsPathTraversal() throws Exception {
        var res = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORT + "/a/..%2f..%2fsecret")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(res.statusCode() == 400 || res.statusCode() == 404, "status " + res.statusCode());
        assertTrue(res.headers().firstValue("content-security-policy").orElse("").contains("connect-src 'self' ws://127.0.0.1:" + PORT));
    }

    @Test
    void badOriginIsRefused() {
        var ex = assertThrows(Exception.class, () -> connect("http://evil.example", new LinkedBlockingQueue<>()));
        assertNotNull(ex);
    }

    @Test
    void badTokenGetsErrorAndNoAck() throws Exception {
        var inbox = new LinkedBlockingQueue<String>();
        WebSocket ws = connect("http://127.0.0.1:" + PORT, inbox);
        ws.sendText("{\"v\":2,\"t\":\"hello\",\"token\":\"wrong\"}", true).join();
        String reply = inbox.poll(2, TimeUnit.SECONDS);
        assertNotNull(reply);
        assertTrue(reply.contains("\"t\":\"error\""), reply);
    }

    @Test
    void commandIsAckedAfterDelay() throws Exception {
        var inbox = new LinkedBlockingQueue<String>();
        WebSocket ws = connect("http://127.0.0.1:" + PORT, inbox);
        String token = server.editorUrl().substring(server.editorUrl().indexOf("=") + 1);
        ws.sendText("{\"v\":2,\"t\":\"hello\",\"token\":\"" + token + "\"}", true).join();
        assertTrue(inbox.poll(2, TimeUnit.SECONDS).contains("welcome"));
        assertTrue(server.linked(), "linked after the hello");
        long t0 = System.nanoTime();
        ws.sendText("{\"v\":2,\"t\":\"cmd\",\"id\":7,\"run\":\"r\",\"op\":\"agent.move\",\"args\":{}}", true).join();
        String result = inbox.poll(2, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertNotNull(result);
        assertTrue(result.contains("\"t\":\"result\"") && result.contains("\"id\":7"), result);
        assertTrue(ms >= RelayServer.STUB_ACK_MS - 20, "acked after " + ms + " ms");
    }

    private WebSocket connect(String origin, LinkedBlockingQueue<String> inbox) {
        return http.newWebSocketBuilder()
                .header("Origin", origin)
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
