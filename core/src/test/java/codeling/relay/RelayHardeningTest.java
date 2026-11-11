package codeling.relay;

import codeling.net.Protocol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The relay's trust boundary on the raw socket (issue #188): HTTP parsing, the handshake, frame rules and timeouts. */
@Timeout(60)
class RelayHardeningTest {
    static final int PORT = 25597;
    // Own servers for the tests that need a fresh one (no session yet, all 64 slots).
    static final int DELIVER_PORT = 25595, SLOTS_PORT = 25596;
    static final String HOST = "127.0.0.1:" + PORT;
    static final String ORIGIN = "http://" + HOST;
    static final String KEY = "dGhlIHNhbXBsZSBub25jZQ=="; // RFC 6455 section 1.3
    static final String INDEX = "<!doctype html><title>test editor</title>\n"; // src/test/resources/codeling-editor/index.html
    static RelayServer server;

    @BeforeAll
    static void start() throws Exception {
        server = new RelayServer(PORT);
        server.timeoutMs = 500;
        server.start();
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    @Test
    void handshakeAnswersTheRfcAcceptKey() throws Exception {
        try (Socket s = upgraded()) {
            assertNotNull(s);
        }
    }

    @Test
    void malformedHandshakesAreRefused() throws Exception {
        String base = "GET /relay HTTP/1.1\r\nHost: " + HOST + "\r\nOrigin: " + ORIGIN + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n";
        assertTrue(raw(base + "Sec-WebSocket-Version: 13\r\n\r\n").startsWith("HTTP/1.1 400"), "no key");
        assertTrue(raw(base + "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: short\r\n\r\n").startsWith("HTTP/1.1 400"), "bad key");
        assertTrue(raw(base + "Sec-WebSocket-Key: " + KEY + "\r\n\r\n").startsWith("HTTP/1.1 426"), "no version");
        assertTrue(raw("POST /relay HTTP/1.1\r\nHost: " + HOST + "\r\nOrigin: " + ORIGIN
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " + KEY + "\r\n\r\n")
                .startsWith("HTTP/1.1 400"), "POST");
        assertTrue(raw("GET /relay\r\n\r\n").startsWith("HTTP/1.1 400"), "HTTP/0.9 request line");
        assertTrue(raw("GET / HTTP/1.1\r\nHost: " + HOST + "\r\n bad: folding\r\n\r\n").startsWith("HTTP/1.1 400"), "folded header");
    }

    @Test
    void badOriginAndHostAreRefused() throws Exception {
        String ws = "Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " + KEY + "\r\n\r\n";
        assertTrue(raw("GET /relay HTTP/1.1\r\nHost: " + HOST + "\r\nOrigin: http://evil.example\r\n" + ws).startsWith("HTTP/1.1 403"));
        assertTrue(raw("GET /relay HTTP/1.1\r\nHost: " + HOST + "\r\n" + ws).startsWith("HTTP/1.1 403"), "no Origin");
        assertTrue(raw("GET /relay HTTP/1.1\r\nHost: evil.example:" + PORT + "\r\nOrigin: " + ORIGIN + "\r\n" + ws).startsWith("HTTP/1.1 403"), "rebinding");
        assertTrue(raw("GET / HTTP/1.1\r\nHost: " + HOST + "\r\nHost: evil.example\r\n\r\n").startsWith("HTTP/1.1 400"), "two Hosts");
        String redirect = raw("GET /x?y=1 HTTP/1.1\r\nHost: localhost:" + PORT + "\r\nConnection: close\r\n\r\n");
        assertTrue(redirect.startsWith("HTTP/1.1 301") && redirect.contains("Location: " + ORIGIN + "/x?y=1\r\n"), redirect);
    }

    @Test
    void hostVariantsAreRefused() throws Exception {
        for (String h : new String[] {"127.0.0.1.:" + PORT, "LOCALHOST:" + PORT, "127.0.0.1", "[::1]:" + PORT, "localhost", ""}) {
            String res = raw("GET / HTTP/1.1\r\nHost: " + h + "\r\n\r\n");
            assertTrue(res.startsWith("HTTP/1.1 403"), h + ": " + res);
        }
        assertTrue(raw("GET / HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 403"), "no Host");
        assertTrue(raw("GET / HTTP/1.0\r\n\r\n").startsWith("HTTP/1.1 403"), "HTTP/1.0 without Host");
    }

    @Test
    void originVariantsAreRefused() throws Exception {
        String ws = "Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " + KEY + "\r\n\r\n";
        String head = "GET /relay HTTP/1.1\r\nHost: " + HOST + "\r\n";
        for (String o : new String[] {"null", "http://localhost:" + PORT, ORIGIN + "/", "https://" + HOST, ""}) {
            String res = raw(head + "Origin: " + o + "\r\n" + ws);
            assertTrue(res.startsWith("HTTP/1.1 403"), o + ": " + res);
        }
        assertTrue(raw(head + "Origin: " + ORIGIN + "\r\nOrigin: " + ORIGIN + "\r\n" + ws).startsWith("HTTP/1.1 400"), "two Origins");
    }

    // Review finding 2: the redirect came before the method and body checks, with keep-alive and the body unread, so a
    // cross-site no-cors POST body became a second request with a Host and Origin of the attacker's choice.
    @Test
    void redirectCannotSmuggleASecondRequest() throws Exception {
        String inner = "GET /relay HTTP/1.1\r\nHost: " + HOST + "\r\nOrigin: " + ORIGIN + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " + KEY + "\r\n\r\n";
        String res = raw("POST /x HTTP/1.1\r\nHost: localhost:" + PORT + "\r\nContent-Type: text/plain\r\nContent-Length: "
                + inner.length() + "\r\n\r\n" + inner);
        assertTrue(res.startsWith("HTTP/1.1 400") && !res.contains("101 Switching"), res);
        // GET with a body, or chunked: refused too (only bodiless GET and HEAD are served).
        assertTrue(raw("GET / HTTP/1.1\r\nHost: localhost:" + PORT + "\r\nContent-Length: 5\r\n\r\nhello").startsWith("HTTP/1.1 400"));
        assertTrue(raw("GET / HTTP/1.1\r\nHost: " + HOST + "\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n").startsWith("HTTP/1.1 400"));
        // Other methods get no redirect.
        assertTrue(raw("PUT / HTTP/1.1\r\nHost: localhost:" + PORT + "\r\n\r\n").startsWith("HTTP/1.1 403"));
        // The redirect closes the connection, and HEAD gets it as well.
        String redirect = raw("HEAD /a HTTP/1.1\r\nHost: localhost:" + PORT + "\r\n\r\n");
        assertTrue(redirect.startsWith("HTTP/1.1 301") && redirect.contains("Connection: close\r\n"), redirect);
        // The Location stays on our origin: a target that is not a path is a 400, not http://127.0.0.1:port@evil.example.
        String userinfo = raw("GET @evil.example/ HTTP/1.1\r\nHost: localhost:" + PORT + "\r\n\r\n");
        assertTrue(userinfo.startsWith("HTTP/1.1 400") && !userinfo.contains("Location"), userinfo);
    }

    // Review finding 3: an empty request line threw ArrayIndexOutOfBoundsException and the connection died unanswered.
    @Test
    void emptyRequestLineIs400() throws Exception {
        assertTrue(raw("\r\n\r\n").startsWith("HTTP/1.1 400"));
    }

    @Test
    void pathTraversalIsRefused() throws Exception {
        for (String path : new String[] {"/../secret", "/%2e%2e/secret", "/a/..%2f..%2fsecret", "/a%5c..%5csecret", "//etc/passwd", "/a%00.html"}) {
            String res = raw("GET " + path + " HTTP/1.1\r\nHost: " + HOST + "\r\n\r\n");
            assertTrue(res.startsWith("HTTP/1.1 400"), path + ": " + res);
        }
        assertTrue(raw("GET /%zz HTTP/1.1\r\nHost: " + HOST + "\r\n\r\n").startsWith("HTTP/1.1 400"), "bad escape");
        assertTrue(raw("PUT /index.html HTTP/1.1\r\nHost: " + HOST + "\r\n\r\n").startsWith("HTTP/1.1 405"), "read only");
    }

    @Test
    void directoryIsNotFound() throws Exception {
        for (String path : new String[] {"/sub", "/sub/"}) {
            String res = raw("GET " + path + " HTTP/1.1\r\nHost: " + HOST + "\r\n\r\n");
            assertTrue(res.startsWith("HTTP/1.1 404"), path + ": " + res);
        }
        assertTrue(raw("GET /sub/page.txt HTTP/1.1\r\nHost: " + HOST + "\r\nConnection: close\r\n\r\n").endsWith("\r\n\r\npage\n"));
    }

    @Test
    void staticFileHasLengthAndFrameHeaders() throws Exception {
        try (Socket s = socket()) {
            s.getOutputStream().write(get("/", false));
            String res = readResponse(s.getInputStream());
            assertTrue(res.startsWith("HTTP/1.1 200") && res.endsWith("\r\n\r\n" + INDEX), res);
            assertTrue(res.contains("Content-Length: " + INDEX.length() + "\r\n") && res.contains("Content-Type: text/html; charset=utf-8\r\n"), res);
            // Review finding 5: no framing by other sites (pxt runs a postMessage controller when framed with ?controller=1).
            assertTrue(res.contains("frame-ancestors 'self'") && res.contains("X-Frame-Options: SAMEORIGIN\r\n"), res);
            assertTrue(res.contains("Referrer-Policy: no-referrer\r\n"), res);
            assertTrue(res.contains("Connection: close\r\n"), res);
        }
    }

    @Test
    void pipelinedKeepAliveRequestsAreAnsweredInOrder() throws Exception {
        try (Socket s = socket()) {
            ByteArrayOutputStream two = new ByteArrayOutputStream();
            two.write(get("/index.html", true));
            two.write(get("/sub/page.txt", false));
            s.getOutputStream().write(two.toByteArray());
            InputStream in = s.getInputStream();
            String first = readResponse(in);
            assertTrue(first.contains("Connection: keep-alive\r\n") && first.endsWith(INDEX), first);
            String second = readResponse(in);
            assertTrue(second.contains("Connection: close\r\n") && second.endsWith("page\n"), second);
            assertEquals(-1, in.read());
        }
    }

    @Test
    void idleKeepAliveConnectionIsClosed() throws Exception {
        try (Socket s = socket()) {
            s.getOutputStream().write(get("/", true));
            readResponse(s.getInputStream());
            long t0 = System.nanoTime();
            assertEquals(-1, s.getInputStream().read());
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(ms > 300 && ms < 3_000, ms + " ms");
        }
    }

    @Test
    void oversizedHeadIsRefused() throws Exception {
        try (Socket s = socket()) {
            s.getOutputStream().write(("GET / HTTP/1.1\r\nHost: " + HOST + "\r\nX: " + "a".repeat(RelayServer.MAX_HEAD_BYTES) + "\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            String res = readAll(s.getInputStream());
            // The relay answers 431 and closes; with the rest of the head unread the OS may reset the connection first.
            assertTrue(res.isEmpty() || res.startsWith("HTTP/1.1 431"), res);
        } catch (IOException reset) {
            // connection reset: refused as well
        }
    }

    @Test
    void slowHeadIsCutOff() throws Exception {
        try (Socket s = socket()) {
            s.getOutputStream().write("GET / HTTP/1.1\r\nHost: ".getBytes(StandardCharsets.ISO_8859_1));
            long t0 = System.nanoTime();
            assertEquals(-1, s.getInputStream().read(), "closed without an answer");
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000, "closed after the timeout");
        }
    }

    // Review finding 4: a response the browser never reads held its thread and slot for good (no write timeout).
    @Test
    void responseThatIsNeverReadIsCutOff() throws Exception {
        try (Socket s = new Socket()) {
            s.setReceiveBufferSize(1024);
            s.connect(new InetSocketAddress("127.0.0.1", PORT));
            byte[] request = get("/index.html", true);
            AtomicReference<IOException> ended = new AtomicReference<>();
            Thread flood = new Thread(() -> {
                try {
                    OutputStream out = s.getOutputStream();
                    long end = System.nanoTime() + 20_000_000_000L;
                    while (System.nanoTime() < end) out.write(request); // and never read the answers
                } catch (IOException e) {
                    ended.set(e);
                }
            });
            flood.setDaemon(true);
            flood.start();
            flood.join(8_000);
            assertNotNull(ended.get(), "the relay closed the connection whose answers were not read");
        }
    }

    // Review finding 4: the half-close after a response read the browser's leftovers with no total deadline.
    @Test
    void lingerHasATotalDeadline() throws Exception {
        try (Socket s = socket()) {
            s.setSoTimeout(4_000);
            OutputStream out = s.getOutputStream();
            out.write(("GET / HTTP/1.1\r\nHost: evil.example\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            assertTrue(readAll(s.getInputStream()).startsWith("HTTP/1.1 403")); // ends at the relay's half-close
            AtomicReference<IOException> ended = new AtomicReference<>();
            Thread drip = new Thread(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        Thread.sleep(150);
                        out.write('x'); // fails once the relay has closed for good
                    }
                } catch (IOException e) {
                    ended.set(e);
                } catch (InterruptedException e) {
                    // test over
                }
            });
            drip.setDaemon(true);
            drip.start();
            drip.join(3_000);
            assertNotNull(ended.get(), "still reading after 3 s of a byte every 150 ms");
        }
    }

    @Test
    void connectionsBeyond64AreClosedAndSlotsReleased() throws Exception {
        RelayServer other = new RelayServer(SLOTS_PORT);
        other.timeoutMs = 10_000;
        other.start();
        List<Socket> held = new ArrayList<>();
        try {
            for (int i = 0; i < RelayServer.MAX_CONNECTIONS; i++) {
                Socket s = socket(SLOTS_PORT);
                held.add(s);
                s.getOutputStream().write(get(SLOTS_PORT, "/", true));
                assertTrue(readResponse(s.getInputStream()).startsWith("HTTP/1.1 200")); // accepted, and kept alive
            }
            try (Socket extra = socket(SLOTS_PORT)) {
                long t0 = System.nanoTime();
                try {
                    assertEquals(-1, extra.getInputStream().read());
                } catch (SocketTimeoutException e) {
                    fail("the 65th connection was not closed");
                } catch (IOException reset) {
                    // closed as well
                }
                assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_000);
            }
            for (Socket s : held) s.close();
            long end = System.nanoTime() + 5_000_000_000L;
            String res = "";
            while (System.nanoTime() < end && !res.startsWith("HTTP/1.1 200")) {
                try (Socket s = socket(SLOTS_PORT)) {
                    s.getOutputStream().write(get(SLOTS_PORT, "/", false));
                    res = readAll(s.getInputStream());
                } catch (IOException e) {
                    res = "";
                }
            }
            assertTrue(res.startsWith("HTTP/1.1 200"), "slots released: " + res);
        } finally {
            for (Socket s : held) s.close();
            other.close();
        }
    }

    @Test
    void relaySocketWithoutHelloIsClosed() throws Exception {
        try (Socket s = upgraded()) {
            long t0 = System.nanoTime();
            assertEquals(-1, s.getInputStream().read());
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000);
        }
    }

    // The hello deadline is a total one: a hello sent a byte at a time (each within the read timeout) is cut off too.
    @Test
    void helloSentAByteAtATimeIsCutOff() throws Exception {
        try (Socket s = upgraded()) {
            byte[] frame = masked(0x81, hello(server).getBytes(StandardCharsets.UTF_8));
            Thread drip = new Thread(() -> {
                try {
                    for (byte b : frame) {
                        Thread.sleep(200);
                        s.getOutputStream().write(b);
                    }
                } catch (IOException | InterruptedException e) {
                    // closed by the relay
                }
            });
            drip.setDaemon(true);
            drip.start();
            long t0 = System.nanoTime();
            try {
                assertEquals(-1, s.getInputStream().read());
            } catch (SocketTimeoutException e) {
                fail("no deadline");
            } catch (IOException reset) {
                // closed as well
            }
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000);
        }
    }

    // Review finding 1: pings before the hello were answered, so a socket that never reads stalled the shared writer.
    @Test
    void controlFramesBeforeHelloCloseWith1008() throws Exception {
        try (Socket s = upgraded()) {
            sendMasked(s, 0x89, "hi");
            assertEquals(1008, closeCode(s));
        }
        try (Socket s = upgraded()) {
            sendMasked(s, 0x8A, "");
            assertEquals(1008, closeCode(s));
        }
    }

    // Review finding 1 as the probe ran it: one unauthenticated socket floods pings and never reads.
    @Test
    void socketThatNeverReadsDoesNotBlockOthers() throws Exception {
        Socket flood = new Socket();
        flood.setReceiveBufferSize(1024);
        flood.connect(new InetSocketAddress("127.0.0.1", PORT));
        flood.setSoTimeout(5_000);
        upgrade(flood, PORT);
        byte[] ping = new byte[6 + 125];
        ping[0] = (byte) 0x89;
        ping[1] = (byte) (0x80 | 125);
        Thread t = new Thread(() -> {
            try {
                OutputStream o = flood.getOutputStream();
                for (int i = 0; i < 400_000; i++) o.write(ping);
            } catch (IOException e) {
                // closed by the relay
            }
        });
        t.setDaemon(true);
        t.start();
        try {
            Thread.sleep(1_500);
            try (Socket s = authed(server)) {
                assertNotNull(s); // welcome arrived
            }
            try (Socket s = upgraded()) {
                long t0 = System.nanoTime();
                assertEquals(-1, s.getInputStream().read(), "hello deadline");
                assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000);
            }
        } finally {
            flood.close();
        }
    }

    // Review finding 1: the outbound queue had no bound, so a session that does not read could fill the game's memory.
    @Test
    void sessionThatDoesNotReadIsClosedAndDeliverNeverBlocks() throws Exception {
        try (Socket s = new Socket()) {
            s.setReceiveBufferSize(1024);
            s.connect(new InetSocketAddress("127.0.0.1", PORT));
            s.setSoTimeout(5_000);
            upgrade(s, PORT);
            sendMasked(s, 0x81, hello(server));
            assertTrue(text(readFrame(s.getInputStream())).contains("welcome"));
            String big = "{\"v\":" + Protocol.VERSION + ",\"t\":\"event\",\"pad\":\"" + "x".repeat(8000) + "\"}";
            int count = 300;
            long t0 = System.nanoTime();
            for (int i = 0; i < count; i++) server.deliver(big);
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 1_000, "deliver does not wait for the socket");
            Thread.sleep(1_000);
            long read = 0;
            try {
                InputStream in = s.getInputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) read += n;
            } catch (SocketTimeoutException e) {
                fail("the session was not closed");
            } catch (IOException reset) {
                // closed as well
            }
            assertTrue(read < (long) count * big.length(), "closed before everything was sent: " + read);
        }
        try (Socket s = authed(server)) {
            assertNotNull(s); // the relay still serves new sessions
        }
    }

    @Test
    void helloWithTokenGetsWelcomeAndCommandsAreAcked() throws Exception {
        try (Socket s = authed(server)) {
            sendMasked(s, 0x81, "{\"v\":" + Protocol.VERSION + ",\"t\":\"cmd\",\"id\":5,\"run\":1,\"op\":\"agent.move\",\"args\":[0,1]}");
            String result = text(readFrame(s.getInputStream()));
            assertTrue(result.contains("\"t\":\"result\"") && result.contains("\"id\":5"), result);
            sendMasked(s, 0x89, "hi"); // after the hello a ping gets its pong
            Frame pong = readFrame(s.getInputStream());
            assertEquals(0x8A, pong.b0);
            assertEquals("hi", text(pong));
        }
    }

    @Test
    void secondSessionTakesOverAndTheFirstIsClosed() throws Exception {
        try (Socket first = authed(server); Socket second = authed(server)) {
            String stop = text(readFrame(first.getInputStream()));
            assertTrue(stop.contains("\"op\":\"stop\"") && stop.contains("took over"), stop);
            assertEquals(-1, first.getInputStream().read());
            assertNotNull(second);
        }
    }

    @Test
    void commandBeforeHelloAndBadTokenAreRefused() throws Exception {
        try (Socket s = upgraded()) {
            sendMasked(s, 0x81, "{\"v\":" + Protocol.VERSION + ",\"t\":\"cmd\",\"id\":1,\"op\":\"agent.move\",\"args\":[0,1]}");
            String error = text(readFrame(s.getInputStream()));
            assertTrue(error.contains("\"code\":\"NO_PERMISSION\""), error);
            assertEquals(-1, s.getInputStream().read(), "closed");
        }
        try (Socket s = upgraded()) {
            sendMasked(s, 0x81, "{\"v\":" + Protocol.VERSION + ",\"t\":\"hello\",\"token\":\"wrong\"}");
            String error = text(readFrame(s.getInputStream()));
            assertTrue(error.contains("\"code\":\"NO_PERMISSION\""), error);
            assertEquals(-1, s.getInputStream().read(), "closed after a bad token");
        }
    }

    @Test
    void tokenIsNeverLogged() throws Exception {
        PrintStream saved = System.err; // slf4j-simple (test runtime) logs to the current System.err
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        System.setErr(new PrintStream(log, true, StandardCharsets.UTF_8));
        try (Socket s = authed(server)) {
            sendMasked(s, 0x81, "{\"v\":" + Protocol.VERSION + ",\"t\":\"cmd\",\"id\":6,\"run\":1,\"op\":\"agent.move\",\"args\":[0,1]}");
            readFrame(s.getInputStream());
            sendMasked(s, 0x81, hello(server)); // a second hello is logged as well
            readFrame(s.getInputStream());
        } finally {
            System.setErr(saved);
        }
        String text = log.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("token not logged") && text.contains("welcome"), "log captured: " + text);
        assertFalse(text.contains(token(server)), text);
    }

    // Review finding 6: two hellos at once both read the same old session, and one of them was never stopped.
    @Test
    void concurrentHellosLeaveOneSession() throws Exception {
        for (int round = 0; round < 3; round++) {
            List<Socket> sockets = new ArrayList<>();
            try {
                for (int i = 0; i < 8; i++) sockets.add(upgraded());
                CountDownLatch go = new CountDownLatch(1);
                List<Thread> threads = new ArrayList<>();
                for (Socket s : sockets) {
                    Thread t = new Thread(() -> {
                        try {
                            go.await();
                            sendMasked(s, 0x81, hello(server));
                        } catch (IOException | InterruptedException ignored) {
                            // checked below
                        }
                    });
                    t.start();
                    threads.add(t);
                }
                go.countDown();
                for (Thread t : threads) t.join();
                int open = 0;
                for (Socket s : sockets) {
                    s.setSoTimeout(1_000);
                    try {
                        while (readFrame(s.getInputStream()) != null) {
                            // welcome, then a stop for every session taken over
                        }
                    } catch (SocketTimeoutException e) {
                        open++;
                    } catch (IOException closed) {
                        // taken over and closed
                    }
                }
                assertEquals(1, open, "sessions left open in round " + round);
            } finally {
                for (Socket s : sockets) s.close();
            }
        }
    }

    @Test
    void deliverWithoutSessionWithBadJsonAndAfterClose() throws Exception {
        RelayServer other = new RelayServer(DELIVER_PORT);
        other.timeoutMs = 500;
        other.start();
        try {
            other.deliver("{\"v\":2,\"t\":\"result\",\"id\":1}"); // no session: dropped
            try (Socket s = authed(other)) {
                for (String json : new String[] {"not json", "{}", "[1]", "{\"t\":\"control\",\"op\":\"start\",\"run\":\"x\"}"}) {
                    other.deliver(json); // forwarded as is; only the run bookkeeping reads it
                    assertEquals(json, text(readFrame(s.getInputStream())));
                }
            }
            other.deliver("{\"v\":2,\"t\":\"result\",\"id\":2}"); // the session is closing or gone
        } finally {
            other.close();
        }
        other.deliver("{\"v\":2,\"t\":\"result\",\"id\":3}"); // the relay is closed
    }

    @Test
    void unmaskedClientFrameClosesWith1002() throws Exception {
        try (Socket s = upgraded()) {
            byte[] text = "{}".getBytes(StandardCharsets.UTF_8);
            OutputStream out = s.getOutputStream();
            out.write(new byte[] {(byte) 0x81, (byte) text.length});
            out.write(text);
            assertEquals(1002, closeCode(s));
        }
    }

    @Test
    void oversizedFrameClosesWith1009() throws Exception {
        try (Socket s = upgraded()) {
            int n = RelayServer.MAX_MESSAGE_BYTES + 1;
            s.getOutputStream().write(new byte[] {(byte) 0x81, (byte) (0x80 | 126), (byte) (n >> 8), (byte) n, 1, 2, 3, 4});
            assertEquals(1009, closeCode(s));
        }
    }

    @Test
    void fragmentedAndBinaryMessagesCloseWith1003() throws Exception {
        try (Socket s = upgraded()) {
            sendMasked(s, 0x01, "{\"v\":2,"); // text without FIN
            assertEquals(1003, closeCode(s));
        }
        try (Socket s = upgraded()) {
            sendMasked(s, 0x82, "abc");
            assertEquals(1003, closeCode(s));
        }
    }

    @Test
    void invalidUtf8ClosesWith1007() throws Exception {
        try (Socket s = upgraded()) {
            OutputStream out = s.getOutputStream();
            out.write(new byte[] {(byte) 0x81, (byte) 0x82, 0, 0, 0, 0, (byte) 0xC3, (byte) 0x28});
            assertEquals(1007, closeCode(s));
        }
    }

    // RFC 6455 sections 5.2 and 5.5, checked after the hello so the frame rule and not the hello rule closes.
    @Test
    void badFramesAfterHelloCloseWith1002() throws Exception {
        assertEquals(1002, afterHello(0x89, new byte[126]), "ping over 125 bytes");
        assertEquals(1002, afterHello(0x09, new byte[0]), "ping without FIN");
        assertEquals(1002, afterHello(0xC1, "{}".getBytes(StandardCharsets.UTF_8)), "RSV1");
        assertEquals(1002, afterHello(0xA1, "{}".getBytes(StandardCharsets.UTF_8)), "RSV2");
        assertEquals(1002, afterHello(0x83, new byte[0]), "reserved data opcode");
        assertEquals(1002, afterHello(0x8B, new byte[0]), "reserved control opcode");
        assertEquals(1002, afterHello(0x80, "x".getBytes(StandardCharsets.UTF_8)), "continuation without a start");
        try (Socket s = authed(server)) {
            s.getOutputStream().write(new byte[] {(byte) 0x81, (byte) (0x80 | 127), (byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4});
            assertEquals(1002, closeCode(s), "64-bit length with the top bit");
        }
    }

    // Review finding 9: the close code a client sends is checked, and 1005 or 1006 is never echoed.
    @Test
    void closeFrameCodesAreChecked() throws Exception {
        try (Socket s = upgraded()) {
            sendFrame(s, 0x88, new byte[] {3});
            assertEquals(1002, closeCode(s), "one byte payload");
        }
        for (int bad : new int[] {999, 1004, 1005, 1006, 1015, 2999, 5000}) {
            try (Socket s = upgraded()) {
                sendFrame(s, 0x88, new byte[] {(byte) (bad >> 8), (byte) bad});
                assertEquals(1002, closeCode(s), "code " + bad);
            }
        }
        for (int good : new int[] {1000, 1001, 1011, 3000, 4999}) {
            try (Socket s = upgraded()) {
                sendFrame(s, 0x88, new byte[] {(byte) (good >> 8), (byte) good, 'o', 'k'});
                assertEquals(good, closeCode(s), "code " + good);
            }
        }
        try (Socket s = upgraded()) {
            sendFrame(s, 0x88, new byte[0]);
            DataInputStream in = new DataInputStream(s.getInputStream());
            assertEquals(0x88, in.readUnsignedByte());
            assertEquals(0, in.readUnsignedByte(), "an empty close is answered empty");
            assertEquals(-1, in.read());
        }
    }

    @Test
    void headSendsHeadersWithoutBody() throws Exception {
        try (Socket s = socket()) {
            OutputStream out = s.getOutputStream();
            out.write(("HEAD /missing HTTP/1.1\r\nHost: " + HOST + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            String res = readAll(s.getInputStream());
            assertTrue(res.startsWith("HTTP/1.1 404") && res.contains("Content-Length: 9\r\n") && res.endsWith("\r\n\r\n"), res);
            assertTrue(res.contains("X-Content-Type-Options: nosniff\r\n") && res.contains("Cache-Control: no-cache\r\n"), res);
        }
    }

    // Authenticates, sends one frame and returns the close code it gets.
    private static int afterHello(int b0, byte[] payload) throws IOException {
        try (Socket s = authed(server)) {
            sendFrame(s, b0, payload);
            return closeCode(s);
        }
    }

    private static Socket socket() throws IOException {
        return socket(PORT);
    }

    private static Socket socket(int port) throws IOException {
        Socket s = new Socket("127.0.0.1", port);
        s.setSoTimeout(5_000);
        return s;
    }

    private static byte[] get(String path, boolean keepAlive) {
        return get(PORT, path, keepAlive);
    }

    private static byte[] get(int port, String path, boolean keepAlive) {
        return ("GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n" + (keepAlive ? "" : "Connection: close\r\n") + "\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String raw(String request) throws IOException {
        try (Socket s = socket()) {
            s.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            return readAll(s.getInputStream());
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bytes.write(buf, 0, n);
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    // One response: the head, then Content-Length bytes of body.
    private static String readResponse(InputStream in) throws IOException {
        String head = readHead(in);
        int at = head.indexOf("Content-Length: ");
        int length = Integer.parseInt(head.substring(at + 16, head.indexOf("\r\n", at)));
        return head + new String(in.readNBytes(length), StandardCharsets.ISO_8859_1);
    }

    private static String readHead(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) throw new EOFException("closed in the head: " + head);
            head.append((char) b);
        }
        return head.toString();
    }

    /** A socket past the WebSocket handshake; checks the 101 and the RFC 6455 example accept key. */
    private static Socket upgraded() throws IOException {
        return upgraded(PORT);
    }

    private static Socket upgraded(int port) throws IOException {
        Socket s = socket(port);
        upgrade(s, port);
        return s;
    }

    private static void upgrade(Socket s, int port) throws IOException {
        String host = "127.0.0.1:" + port;
        s.getOutputStream().write(("GET /relay HTTP/1.1\r\nHost: " + host + "\r\nOrigin: http://" + host + "\r\nUpgrade: websocket\r\n"
                + "Connection: keep-alive, Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: " + KEY + "\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        String head = readHead(s.getInputStream());
        assertTrue(head.startsWith("HTTP/1.1 101"), head);
        assertTrue(head.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n"), head);
    }

    /** A relay session that got its welcome. */
    private static Socket authed(RelayServer relay) throws IOException {
        Socket s = upgraded(relay.port());
        sendMasked(s, 0x81, hello(relay));
        String welcome = text(readFrame(s.getInputStream()));
        assertTrue(welcome.contains("\"t\":\"welcome\""), welcome);
        return s;
    }

    private static String token(RelayServer relay) {
        return relay.editorUrl().substring(relay.editorUrl().indexOf('=') + 1);
    }

    private static String hello(RelayServer relay) {
        return "{\"v\":" + Protocol.VERSION + ",\"t\":\"hello\",\"token\":\"" + token(relay) + "\"}";
    }

    private static byte[] masked(int b0, byte[] p) {
        byte[] mask = {9, 8, 7, 6};
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(b0);
        if (p.length < 126) {
            frame.write(0x80 | p.length);
        } else {
            frame.write(0x80 | 126);
            frame.write(p.length >> 8);
            frame.write(p.length);
        }
        frame.writeBytes(mask);
        for (int i = 0; i < p.length; i++) frame.write(p[i] ^ mask[i & 3]);
        return frame.toByteArray();
    }

    private static void sendFrame(Socket s, int b0, byte[] payload) throws IOException {
        s.getOutputStream().write(masked(b0, payload));
    }

    private static void sendMasked(Socket s, int b0, String text) throws IOException {
        sendFrame(s, b0, text.getBytes(StandardCharsets.UTF_8));
    }

    private record Frame(int b0, byte[] payload) {
    }

    // One server frame (never masked); EOFException when the socket closed.
    private static Frame readFrame(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        int b0 = in.readUnsignedByte();
        long length = in.readUnsignedByte();
        if (length == 126) length = in.readUnsignedShort();
        else if (length == 127) length = in.readLong();
        byte[] payload = new byte[(int) length];
        in.readFully(payload);
        return new Frame(b0, payload);
    }

    private static String text(Frame frame) {
        return new String(frame.payload, StandardCharsets.UTF_8);
    }

    // Reads the server's close frame and returns its status code; the socket must close after it.
    private static int closeCode(Socket s) throws IOException {
        DataInputStream in = new DataInputStream(s.getInputStream());
        assertEquals(0x88, in.readUnsignedByte());
        assertEquals(2, in.readUnsignedByte());
        int code = in.readUnsignedShort();
        assertEquals(-1, in.read(), "closed after the close frame");
        return code;
    }
}
