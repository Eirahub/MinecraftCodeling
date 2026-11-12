package codeling.relay;

import codeling.net.Protocol;
import codeling.robot.Result;
import codeling.robot.RobotCommand;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.JarURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loopback HTTP + WebSocket server: serves the static pxt editor from the classpath and relays
 * robot commands on /relay. Plain class with no Minecraft imports so it runs headless.
 *
 * <p>Only {@code java.base} (sockets, threads), so the same relay runs on every Minecraft version whatever Netty
 * it ships (issue #188: no {@code netty-codec-http} before 1.21.9, Netty 4.1 before 1.21.11), and on Java 17.
 * One thread per connection, and a writer thread per relay session: one local user with a handful of browser connections.
 */
public final class RelayServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 25580;
    static final String RESOURCE_ROOT = "codeling-editor/";
    static final int MAX_MESSAGE_BYTES = Protocol.MAX_MESSAGE_BYTES;
    static final int PROTOCOL = Protocol.VERSION;
    // Standalone only (no game): commands get a fake ack after this delay, so the editor can be worked on without Minecraft.
    static final long STUB_ACK_MS = 300;
    // Request line plus headers. A browser's requests to the editor are well under 4 KiB.
    static final int MAX_HEAD_BYTES = 16 * 1024;
    // Open connections at most (a browser keeps about six per host, plus one relay socket per run); more are closed at once.
    static final int MAX_CONNECTIONS = 64;
    // Frames queued for one browser session and not yet written; a session that does not read is closed past this.
    static final int MAX_PENDING_BYTES = 256 * 1024;
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int TEXT = 0x1, CLOSE = 0x8, PING = 0x9, PONG = 0xA, NO_FRAME = -1;
    private static final byte[] EMPTY = new byte[0];
    // Headers that must come once: a second Host or Origin could pass the check with one and mean the other.
    private static final Set<String> SINGLE_HEADERS = Set.of("host", "origin", "content-length", "sec-websocket-key");
    // Every relay message in both directions, so a run can be followed in the game log. Never the token.
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("codeling/relay");

    // One log line per message; heartbeats are skipped (one every few seconds) and long messages are cut.
    static void logMessage(String direction, String json) {
        if (json.contains("\"heartbeat\"")) return;
        LOG.info("{} {}", direction, json.length() > 300 ? json.substring(0, 300) + "..." : json);
    }

    /** The way to the game server. Without one (standalone) the relay answers with stub acks. */
    public interface Link {
        /** Name shown in {@code welcome}. */
        String playerName();

        /** Forwards one protocol message to the server. Returns null, or why it cannot (for example: not in a world). */
        String send(String json);
    }

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "application/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("webmanifest", "application/manifest+json"),
            Map.entry("manifest", "text/cache-manifest"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("eot", "application/vnd.ms-fontobject"),
            Map.entry("wasm", "application/wasm"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("md", "text/plain; charset=utf-8"),
            Map.entry("xml", "application/xml"));

    // Only a plain locale name (nb, en-GB), so the query can never add a path part.
    private static final Pattern DOCS_LANG = Pattern.compile("[?&]lang=([a-z]{2,3}(?:-[A-Za-z]{2,4})?)(?:&|$)");

    private final int port;
    private final Link link;
    private final String token;
    private final String host;
    private final String origin;
    private final String csp;
    // Slowloris defence: a request head, and the hello on a new relay socket, must arrive within this time, and a response
    // or a frame must be written within it. Tests shorten it.
    int timeoutMs = 10_000;
    private ServerSocket serverSocket;
    private final Semaphore slots = new Semaphore(MAX_CONNECTIONS);
    private final Set<Socket> open = ConcurrentHashMap.newKeySet();
    // Deadlines and the stub acks. Its tasks never block (they close a socket or queue a frame), so a deadline always fires.
    private ScheduledThreadPoolExecutor timers;
    // The one browser session that controls the robot; a newer authenticated session takes over.
    private final AtomicReference<RelaySession> active = new AtomicReference<>();

    /** Standalone relay: stub acks, no game. */
    public RelayServer(int port) {
        this(port, null);
    }

    public RelayServer(int port, Link link) {
        this.port = port;
        this.link = link;
        this.token = newToken();
        this.host = "127.0.0.1:" + port;
        this.origin = "http://" + host;
        // pxt sets no CSP; ours also stops the editor calling makecode.com or other hosts.
        // shortcut: script-src needs 'unsafe-eval' and 'unsafe-inline' for pxt and its sim; tighten when hardening.
        this.csp = "default-src 'self'; connect-src 'self' ws://" + host + "; "
                + "script-src 'self' 'unsafe-inline' 'unsafe-eval' blob:; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' data: blob:; "
                + "worker-src 'self' blob:; frame-src 'self' blob:; frame-ancestors 'self'";
    }

    static String newToken() {
        byte[] bytes = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The URL the "open editor" action opens. Contains the token: never log it. */
    public String editorUrl() {
        return origin + "/#local_token=" + token;
    }

    public int port() {
        return port;
    }

    /** Safe to log: a short prefix only. */
    public String tokenHint() {
        return token.substring(0, 4) + "...";
    }

    /** Binds the port (an IOException when it is busy) and starts accepting. */
    public RelayServer start() throws IOException {
        ServerSocket socket = new ServerSocket();
        // 127.0.0.1 as in editorUrl, not getLoopbackAddress(): that can be ::1 with java.net.preferIPv6Addresses=system,
        // which NeoForge's run configurations set (issue #187), and the editor URL would find nothing. A literal: no lookup.
        socket.bind(new InetSocketAddress("127.0.0.1", port));
        serverSocket = socket;
        timers = new ScheduledThreadPoolExecutor(1, r -> daemon(r, "codeling-relay-timer"));
        timers.setRemoveOnCancelPolicy(true); // most deadlines are cancelled long before they are due
        daemon(this::accept, "codeling-relay").start();
        return this;
    }

    @Override
    public void close() {
        closeQuietly(serverSocket);
        open.forEach(RelayServer::closeQuietly);
        if (timers != null) timers.shutdownNow();
    }

    boolean tokenMatches(String candidate) {
        if (candidate == null) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static Thread daemon(Runnable task, String name) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        return t;
    }

    private static void closeQuietly(java.io.Closeable c) {
        try {
            if (c != null) c.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    // Closes the socket unless cancelled within timeoutMs, so a browser that does not read cannot hold a thread or a slot.
    private Future<?> closeLater(Socket socket) {
        try {
            return timers.schedule(() -> closeQuietly(socket), timeoutMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            closeQuietly(socket); // the server is closing
            return CompletableFuture.completedFuture(null);
        }
    }

    private void accept() {
        int backoffMs = 0;
        while (!serverSocket.isClosed()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
                backoffMs = 0;
            } catch (IOException e) {
                if (serverSocket.isClosed()) return;
                LOG.warn("relay accept failed", e);
                // Out of file handles fails again at once: wait longer each time instead of spinning.
                backoffMs = Math.min(Math.max(2 * backoffMs, 50), 1_000);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException stop) {
                    return;
                }
                continue;
            }
            if (!slots.tryAcquire()) {
                closeQuietly(socket);
                continue;
            }
            open.add(socket);
            try {
                daemon(() -> {
                    try {
                        serve(socket);
                    } finally {
                        open.remove(socket);
                        closeQuietly(socket);
                        slots.release();
                    }
                }, "codeling-relay-connection").start();
            } catch (RuntimeException | OutOfMemoryError e) { // no thread to be had
                LOG.warn("relay connection refused", e);
                open.remove(socket);
                closeQuietly(socket);
                slots.release();
            }
        }
    }

    /** A bad request: answered with this status and message, then the connection closes. */
    private static final class HttpError extends IOException {
        final int status;

        HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    /** One request head. Header names are lower case; a repeated header is joined with ", ". */
    private record Request(String method, String target, boolean http11, Map<String, String> headers) {
        String header(String name, String fallback) {
            return headers.getOrDefault(name, fallback);
        }

        boolean keepAlive() {
            Set<String> connection = tokens(headers.get("connection"));
            return http11 ? !connection.contains("close") : connection.contains("keep-alive");
        }

        boolean hasBody() {
            return headers.containsKey("transfer-encoding") || !header("content-length", "0").equals("0");
        }

        /** The decoded path without the query. */
        String path() throws HttpError {
            String raw = target.split("\\?", 2)[0];
            if (!raw.startsWith("/")) throw new HttpError(400, "bad path"); // no absolute-form or *
            try {
                return URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8); // + stays + in a path
            } catch (IllegalArgumentException e) {
                throw new HttpError(400, "bad path");
            }
        }
    }

    private static Set<String> tokens(String header) {
        if (header == null) return Set.of();
        return Arrays.stream(header.split(",")).map(s -> s.trim().toLowerCase()).collect(Collectors.toSet());
    }

    private void serve(Socket socket) {
        InputStream in = null;
        try {
            socket.setSoTimeout(timeoutMs); // also the idle time of a keep-alive connection
            socket.setTcpNoDelay(true);
            in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            Future<?> deadline = null;
            try {
                Request req;
                while ((req = readRequest(in)) != null) {
                    deadline = closeLater(socket); // for the whole response
                    if (!handle(socket, in, out, req, deadline)) break;
                    deadline.cancel(false); // keep-alive: the next request on the same connection
                }
            } catch (HttpError e) {
                if (deadline == null || deadline.isDone()) deadline = closeLater(socket);
                send(out, null, e.status, "text/plain; charset=utf-8", e.getMessage().getBytes(StandardCharsets.UTF_8), "");
            } finally {
                if (deadline != null) deadline.cancel(false);
            }
        } catch (IOException e) {
            // the browser went away, a slow request timed out, or the server closed
        } catch (RuntimeException e) {
            LOG.warn("relay connection failed", e); // a bug: this connection ends, the relay goes on
        } finally {
            if (in != null) linger(socket, in);
        }
    }

    // Half-closes and reads what the browser still sends (bounded in bytes and time), so a last answer or close frame is
    // not lost to a TCP reset from closing with unread bytes. The caller closes the socket.
    private void linger(Socket socket, InputStream in) {
        try {
            socket.shutdownOutput();
            socket.setSoTimeout(timeoutMs);
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            byte[] buf = new byte[4096];
            for (int total = 0, n; total < 64 * 1024 && System.nanoTime() < end && (n = in.read(buf)) > 0; total += n) {
                // discard
            }
        } catch (IOException ignored) {
            // already closed or reset
        }
    }

    /** Reads a request head, or returns null when the browser closed an idle connection. */
    private Request readRequest(InputStream in) throws IOException {
        int b = in.read(); // waits up to timeoutMs on an idle connection
        if (b < 0) return null;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int last4 = 0;
        while (true) {
            head.write(b);
            last4 = last4 << 8 | b;
            if (last4 == 0x0D0A0D0A) break; // \r\n\r\n
            if (head.size() >= MAX_HEAD_BYTES) throw new HttpError(431, "request too large");
            if (System.nanoTime() > deadline) throw new SocketTimeoutException("request head too slow");
            b = in.read();
            if (b < 0) throw new EOFException();
        }
        String[] lines = head.toString(StandardCharsets.ISO_8859_1).split("\r\n");
        if (lines.length == 0) throw new HttpError(400, "bad request"); // an empty request line
        String[] start = lines[0].split(" ", -1);
        if (start.length != 3 || !start[0].matches("[A-Z]{1,16}") || !start[1].matches("[\\x21-\\x7e]+")
                || !(start[2].equals("HTTP/1.1") || start[2].equals("HTTP/1.0"))) {
            throw new HttpError(400, "bad request");
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            // A token name right before the colon (no folding, no space), and no stray CR or LF in the value.
            if (colon <= 0 || !line.substring(0, colon).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || line.matches("(?s).*[\\r\\n\\x00].*")) {
                throw new HttpError(400, "bad request");
            }
            String name = line.substring(0, colon).toLowerCase();
            String value = line.substring(colon + 1).trim();
            if (headers.containsKey(name) && SINGLE_HEADERS.contains(name)) throw new HttpError(400, "bad request");
            headers.merge(name, value, (a, c) -> a + ", " + c);
        }
        return new Request(start[0], start[1], start[2].equals("HTTP/1.1"), headers);
    }

    /** Answers one request. Returns whether the connection stays open for the next one. */
    private boolean handle(Socket socket, InputStream in, OutputStream out, Request req, Future<?> deadline) throws IOException {
        // Only bodiless GET and HEAD are served. A body is refused first: left unread it would be read as the next request.
        if (req.hasBody()) return text(out, req, 400, "bad request");
        String path = req.path(); // a 400 unless the target is a path, so the redirect below stays on our origin
        boolean read = req.method().equals("GET") || req.method().equals("HEAD");
        String reqHost = req.header("host", "");
        if (read && reqHost.equals("localhost:" + port)) {
            // Keep one origin: projects live in IndexedDB per origin. The target is printable ASCII only (readRequest).
            return send(out, req, 301, null, EMPTY, "Location: " + origin + req.target() + "\r\n");
        }
        if (!reqHost.equals(host)) return text(out, req, 403, "bad host"); // DNS rebinding defence
        if (path.equals("/relay")) {
            upgrade(socket, in, out, req, deadline);
            return false;
        }
        if (!read) return text(out, req, 405, "read only");
        return serveStatic(out, req, path);
    }

    private boolean serveStatic(OutputStream out, Request req, String path) throws IOException {
        String rel = path.equals("/") ? "index.html" : path.substring(1);
        if (rel.contains("..") || rel.contains("\\") || rel.startsWith("/") || rel.indexOf('\0') >= 0) {
            return text(out, req, 400, "bad path");
        }
        URLConnection file = open(rel);
        // A packaged pxt editor asks for docs pages as docs/<page>.md?lang=<locale> and has no translations of its own
        // offline, so a docs/_locales/<locale>/<page>.md shipped with the editor (issue #216) answers in its place.
        Matcher lang = DOCS_LANG.matcher(req.target());
        if (rel.startsWith("docs/") && rel.endsWith(".md") && lang.find()) {
            URLConnection localized = open("docs/_locales/" + lang.group(1) + "/" + rel.substring("docs/".length()));
            if (localized != null) file = localized;
        }
        if (file == null && !rel.contains(".")) {
            rel = rel + ".html"; // pxt static docs routes such as /about
            file = open(rel);
        }
        if (file == null) return text(out, req, 404, "not found");
        String type = TYPES.getOrDefault(rel.substring(rel.lastIndexOf('.') + 1).toLowerCase(), "application/octet-stream");
        // Streamed, so main.js (about 5 MB) is not held in memory once per request. No caching or ETag: one local user.
        try (InputStream body = file.getInputStream()) {
            long length = file.getContentLengthLong();
            // shortcut: a class loader whose URLs give no length gets the file read whole first; stream it if one shows up.
            if (length < 0) return send(out, req, 200, type, body.readAllBytes(), "");
            return send(out, req, 200, type, body, length, "");
        }
    }

    // The editor file at rel, or null when there is none. A directory is none: it would read as a listing or as empty.
    private static URLConnection open(String rel) {
        if (rel.isEmpty() || rel.endsWith("/")) return null;
        URL url = RelayServer.class.getClassLoader().getResource(RESOURCE_ROOT + rel);
        if (url == null) return null;
        try {
            if (url.getProtocol().equals("file") && Files.isDirectory(Path.of(url.toURI()))) return null;
            URLConnection file = url.openConnection();
            if (file instanceof JarURLConnection jar && jar.getJarEntry().isDirectory()) return null;
            return file;
        } catch (IOException | URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private boolean text(OutputStream out, Request req, int status, String msg) throws IOException {
        return send(out, req, status, "text/plain; charset=utf-8", msg.getBytes(StandardCharsets.UTF_8), "");
    }

    private boolean send(OutputStream out, Request req, int status, String type, byte[] body, String extraHeaders) throws IOException {
        return send(out, req, status, type, new ByteArrayInputStream(body), body.length, extraHeaders);
    }

    /** Writes one response (no body for HEAD). Redirects and errors close the connection. Returns whether it stays open. */
    private boolean send(OutputStream out, Request req, int status, String type, InputStream body, long length, String extraHeaders)
            throws IOException {
        boolean keepAlive = req != null && req.keepAlive() && status < 300;
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        if (type != null) head.append("Content-Type: ").append(type).append("\r\n");
        head.append(extraHeaders)
                .append("Content-Security-Policy: ").append(csp).append("\r\n")
                .append("X-Content-Type-Options: nosniff\r\n")
                .append("X-Frame-Options: SAMEORIGIN\r\n") // frame-ancestors for browsers without it
                .append("Referrer-Policy: no-referrer\r\n")
                .append("Cache-Control: no-cache\r\n")
                .append("Content-Length: ").append(length).append("\r\n")
                .append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (req == null || !req.method().equals("HEAD")) {
            byte[] buf = new byte[16 * 1024];
            for (long left = length; left > 0; ) {
                int n = body.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new EOFException("file shorter than its length"); // closes: the browser sees a cut response
                out.write(buf, 0, n);
                left -= n;
            }
        }
        out.flush();
        return keepAlive;
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 301 -> "Moved Permanently";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 426 -> "Upgrade Required";
            case 431 -> "Request Header Fields Too Large";
            default -> "Error";
        };
    }

    /** The WebSocket opening handshake (RFC 6455 section 4.2), then the relay session on this thread. */
    private void upgrade(Socket socket, InputStream in, OutputStream out, Request req, Future<?> deadline) throws IOException {
        if (!origin.equals(req.header("origin", null))) {
            LOG.warn("WebSocket refused: bad Origin {}", req.header("origin", null));
            text(out, req, 403, "bad origin");
            return;
        }
        if (!req.header("sec-websocket-version", "").equals("13")) {
            send(out, req, 426, null, EMPTY, "Sec-WebSocket-Version: 13\r\n");
            return;
        }
        String key = req.header("sec-websocket-key", "");
        boolean keyOk;
        try {
            keyOk = Base64.getDecoder().decode(key).length == 16;
        } catch (IllegalArgumentException e) {
            keyOk = false;
        }
        if (!keyOk || !req.method().equals("GET") || req.hasBody() || !tokens(req.header("upgrade", null)).contains("websocket")
                || !tokens(req.header("connection", null)).contains("upgrade")) {
            text(out, req, 400, "bad handshake");
            return;
        }
        String accept;
        try {
            accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + WEBSOCKET_GUID).getBytes(StandardCharsets.ISO_8859_1)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java has SHA-1
        }
        // No subprotocol and no extensions (permessage-deflate offers are declined by leaving them out).
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                + accept + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        deadline.cancel(false); // the session has its own deadlines
        new RelaySession(socket, new DataInputStream(in), out).run();
    }

    /** True while an editor session is linked: pxt opens one per run, so a program is running. Any thread. */
    public boolean linked() {
        return active.get() != null;
    }

    /**
     * Sends a server message (result, error, control, event) to the active browser session. Any thread.
     * Remembers the run id a {@code control start} carries, so closing the tab can stop that run.
     */
    public void deliver(String json) {
        RelaySession session = active.get();
        if (session == null) return;
        try {
            JsonObject msg = JsonParser.parseString(json).getAsJsonObject();
            if ("control".equals(msg.get("t").getAsString()) && "start".equals(msg.get("op").getAsString())) {
                session.run = msg.get("run").getAsLong();
            }
        } catch (RuntimeException ignored) {
            // only the run bookkeeping needs the fields; the message is forwarded as is
        }
        session.write(json);
    }

    /**
     * One browser session on /relay (architecture section 3). Authenticates with {@code hello}, then forwards
     * {@code control} and {@code cmd} to the server through the {@link Link}. Every pxt run opens its own socket,
     * so a new authenticated session (a restarted program or a second tab) takes over from the old one.
     */
    private final class RelaySession {
        private final Socket socket;
        private final DataInputStream in;
        private final OutputStream out;
        // This session's frames in order, on its own thread: deliver() never blocks the game's thread, and a browser
        // that does not read holds up only its own session.
        private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> daemon(r, "codeling-relay-writer"));
        private final AtomicLong pending = new AtomicLong();
        private volatile boolean authed;
        volatile long run = -1;

        RelaySession(Socket socket, DataInputStream in, OutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        void run() {
            // The hello must come within timeoutMs: the read timeout (still set from serve) until then, and this total
            // deadline for a hello sent a byte at a time.
            Future<?> hello = closeLater(socket);
            try {
                String text;
                while ((text = readMessage()) != null && handle(text, hello)) {
                    // next message
                }
            } catch (IOException e) {
                // the tab closed, or the socket was closed by a takeover, a deadline or close()
            } finally {
                hello.cancel(false);
                writer.shutdown();
                active.compareAndSet(this, null);
                LOG.info("editor session closed (run {})", run);
                // A closed tab stops its run now instead of after the heartbeat timeout. The server ignores an old run id.
                if (link != null && run >= 0) link.send(Protocol.control("stop", run, null));
            }
        }

        /** The next text message, or null when the session ends (after a close frame or a protocol error). */
        private String readMessage() throws IOException {
            while (true) {
                int b0 = in.readUnsignedByte();
                int b1 = in.readUnsignedByte();
                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;
                long length = b1 & 0x7F;
                if (length == 126) length = in.readUnsignedShort();
                else if (length == 127) length = in.readLong();
                // RSV bits need an extension (none agreed); a client frame must be masked (RFC 6455 section 5.1); the
                // most significant bit of a 64-bit length must be 0 (section 5.2).
                if ((b0 & 0x70) != 0 || (b1 & 0x80) == 0 || length < 0) return end(1002);
                if (opcode >= CLOSE && (length > 125 || !fin)) return end(1002);
                if (length > MAX_MESSAGE_BYTES) return end(1009);
                byte[] mask = new byte[4];
                in.readFully(mask);
                byte[] payload = new byte[(int) length];
                in.readFully(payload);
                for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
                switch (opcode) {
                    case CLOSE -> {
                        int code = payload.length >= 2 ? (payload[0] & 0xFF) << 8 | payload[1] & 0xFF : 0;
                        if (payload.length == 1 || payload.length >= 2 && !validCloseCode(code)) return end(1002);
                        finish(CLOSE, payload.length >= 2 ? Arrays.copyOf(payload, 2) : EMPTY); // echo the code
                        return null;
                    }
                    // Before the hello nothing is answered: a socket could ask for pongs and never read them.
                    case PING, PONG -> {
                        if (!authed) return end(1008);
                        if (opcode == PING) queue(PONG, payload, false); // an unasked pong is ignored
                    }
                    case TEXT -> {
                        // shortcut: no fragmented messages; browsers send our small messages as one frame. Add when needed.
                        if (!fin) return end(1003);
                        try {
                            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(payload)).toString();
                        } catch (CharacterCodingException e) {
                            return end(1007);
                        }
                    }
                    default -> {
                        // Binary is not used. A continuation always lacks its start (a text without FIN closed with 1003).
                        return end(opcode == 0x2 ? 1003 : 1002);
                    }
                }
            }
        }

        // Codes a peer may send (RFC 6455 section 7.4 and the IANA registry): not 1004, 1005, 1006 or 1015.
        private static boolean validCloseCode(int code) {
            return code >= 1000 && code <= 1014 && code != 1004 && code != 1005 && code != 1006 || code >= 3000 && code <= 4999;
        }

        // Closes with this status code (RFC 6455 section 7.4) and ends the session.
        private String end(int code) {
            finish(CLOSE, new byte[] {(byte) (code >> 8), (byte) code});
            return null;
        }

        /** Handles one text message (the protocol, unchanged). Returns false when the session ends. */
        private boolean handle(String text, Future<?> hello) throws IOException {
            JsonObject msg;
            try {
                msg = JsonParser.parseString(text).getAsJsonObject();
            } catch (RuntimeException e) {
                finish(NO_FRAME, EMPTY); // decode errors end the relay session only
                return false;
            }
            String type = msg.get("t") instanceof JsonPrimitive t && t.isString() ? t.getAsString() : "";
            Long id = Protocol.id(msg);
            logMessage("browser ->", type.equals("hello") ? "{\"t\":\"hello\"} (token not logged)" : text);
            if (!(msg.get("v") instanceof JsonPrimitive v) || !v.isNumber() || v.getAsDouble() != PROTOCOL) {
                String error = Protocol.error(authed ? id : null, Result.Code.VERSION, "protocol version " + PROTOCOL + " expected, reload the editor");
                if (authed) {
                    write(error);
                    return true;
                }
                finish(TEXT, error.getBytes(StandardCharsets.UTF_8));
                return false;
            }
            if (!authed) {
                if (type.equals("hello") && msg.get("token") instanceof JsonPrimitive token && tokenMatches(token.getAsString())) {
                    authed = true;
                    hello.cancel(false);
                    socket.setSoTimeout(0); // an editor may sit idle between commands
                    RelaySession old = active.getAndSet(this); // atomic: two hellos at once each stop the one before
                    if (old != null) {
                        old.write(Protocol.control("stop", old.run, "Another editor session took over the robot."));
                        old.queue(NO_FRAME, EMPTY, true);
                    }
                    write(welcome().toString());
                    return true;
                }
                LOG.warn("editor session refused: bad or stale token (open the editor with /codeling)");
                finish(TEXT, Protocol.error(null, Result.Code.NO_PERMISSION, "bad token").getBytes(StandardCharsets.UTF_8));
                return false;
            }
            if (!type.equals("cmd") && !type.equals("control")) {
                write(Protocol.error(id, Result.Code.BAD_ARGS, "unknown message type"));
                return true;
            }
            if (active.get() != this) {
                write(Protocol.error(id, Result.Code.STOPPED, "Another editor session took over the robot."));
                return true;
            }
            if (link == null) {
                stub(type, msg, id);
                return true;
            }
            String problem = link.send(text);
            if (problem != null) LOG.warn("not forwarded to the game: {}", problem);
            if (problem != null) write(Protocol.error(id, Result.Code.UNLOADED, problem));
            return true;
        }

        private JsonObject welcome() {
            String player = link == null ? "standalone" : link.playerName();
            JsonObject welcome = Protocol.envelope("welcome");
            welcome.addProperty("player", player);
            welcome.addProperty("robot", player); // one robot per player, known by its owner
            JsonObject limits = new JsonObject();
            limits.addProperty("maxMessageBytes", MAX_MESSAGE_BYTES);
            limits.addProperty("maxMove", RobotCommand.MAX_MOVE);
            limits.addProperty("slots", RobotCommand.SLOTS);
            limits.addProperty("heartbeatTimeoutMs", Protocol.HEARTBEAT_TIMEOUT_MS);
            welcome.add("limits", limits);
            welcome.addProperty("server", PROTOCOL);
            return welcome;
        }

        // Standalone: run 0 for every start, and a delayed ok for every command (a fake value for the position and container queries).
        private void stub(String type, JsonObject msg, Long id) {
            if (type.equals("control")) {
                if (msg.get("op") instanceof JsonPrimitive op && op.getAsString().equals("start")) write(Protocol.control("start", 0, null));
                return;
            }
            if (id == null) {
                write(Protocol.error(null, Result.Code.BAD_ARGS, "cmd needs an id"));
                return;
            }
            // For the editor suite (issue #218): say "refuse <CODE>" is refused with that code, as the game refuses a command.
            String said = msg.get("args") instanceof JsonArray a && a.size() == 1 && a.get(0) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : "";
            Result.Code refusal = java.util.Arrays.stream(Result.Code.values()).filter(c -> said.equals("refuse " + c.name())).findFirst().orElse(null);
            if (refusal != null && msg.get("op") instanceof JsonPrimitive op && op.getAsString().equals("agent.say")) {
                schedule(() -> write(Protocol.error(id, refusal, "refused on purpose by the standalone relay")), STUB_ACK_MS);
                return;
            }
            // The second argument (max or length) as a number from 0 to MAX_MEASURE, for the measuring answers.
            int second = msg.get("args") instanceof JsonArray a && a.size() > 1 && a.get(1) instanceof JsonPrimitive p && p.isNumber()
                    ? (int) Math.max(0, Math.min((long) p.getAsDouble(), RobotCommand.MAX_MEASURE)) : 0;
            Object value = switch (msg.get("op") instanceof JsonPrimitive op ? op.getAsString() : "") {
                case "agent.getPosition", "agent.findNearest", "agent.findNearestCategory" -> java.util.List.of(0, 0, 0);
                case "agent.getOrientation", "agent.containerCount", "agent.takeItem", "agent.craft", "agent.stonecut", "agent.redstonePower" -> 0;
                case "agent.containerSize" -> 27; // a chest
                // measuring: open ground at y 64 as far as the program looks
                case "agent.measure", "agent.measureGap" -> second;
                case "agent.measureRun", "agent.countSolid" -> 0;
                case "agent.groundHeight" -> 64;
                case "agent.heightProfile" -> java.util.Collections.nCopies(second, 64);
                case "agent.findBounds" -> java.util.List.of(0, 0, 0, 0, 0, 0);
                // nobody near the robot
                case "agent.detectEntity" -> false;
                case "agent.countNearby" -> 0;
                case "agent.radar" -> java.util.List.of();
                case "agent.detectLiquid" -> 0; // Liquid.None
                // an empty robot
                case "agent.freeSlots" -> RobotCommand.SLOTS;
                case "agent.isInventoryFull" -> false;
                // followPath: no world, so the path ends at once (PathStop.End)
                case "agent.followPath" -> 0;
                // building: no world, so no block changed
                case "blocks.fill", "shapes.line", "shapes.circle", "shapes.sphere", "shapes.maze" -> 0;
                // as if the player stood at 0 0 0 facing south, where relative and local both equal the offsets
                case "positions.toWorld" -> msg.get("args") instanceof JsonArray a && a.size() == 4 ? java.util.List.of(a.get(1), a.get(2), a.get(3)) : true;
                default -> true;
            };
            schedule(() -> write(Protocol.reply(id, Result.ok(value))), STUB_ACK_MS);
        }

        void write(String json) {
            logMessage("-> browser", json);
            queue(TEXT, json.getBytes(StandardCharsets.UTF_8), false);
        }

        /**
         * Queues one frame (or none, with NO_FRAME) on this session's writer, optionally closing the socket after it.
         * Never blocks: a browser with more than MAX_PENDING_BYTES unwritten is closed instead.
         */
        private Future<?> queue(int opcode, byte[] payload, boolean thenClose) {
            if (pending.addAndGet(payload.length) > MAX_PENDING_BYTES) {
                if (!socket.isClosed()) LOG.warn("editor session closed: the browser does not read its messages");
                closeQuietly(socket);
                return null;
            }
            try {
                return writer.submit(() -> {
                    Future<?> deadline = closeLater(socket);
                    try {
                        if (opcode != NO_FRAME) frame(opcode, payload);
                    } catch (IOException ignored) {
                        // the tab is gone; its reader ends the session
                    } finally {
                        deadline.cancel(false);
                        pending.addAndGet(-payload.length);
                    }
                    if (thenClose) closeQuietly(socket);
                });
            } catch (RejectedExecutionException e) {
                return null; // the session has ended
            }
        }

        // Sends a last frame after everything already queued and waits for it (reader thread only); serve() then closes.
        private void finish(int opcode, byte[] payload) {
            Future<?> done = queue(opcode, payload, false);
            try {
                if (done != null) done.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                closeQuietly(socket);
            }
        }

        private void schedule(Runnable task, long delayMs) {
            try {
                timers.schedule(task, delayMs, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignored) {
                // the server is closing
            }
        }

        // Server frames are never masked (RFC 6455 section 5.1).
        private void frame(int opcode, byte[] payload) throws IOException {
            ByteBuffer head = ByteBuffer.allocate(10).put((byte) (0x80 | opcode));
            int n = payload.length;
            if (n < 126) head.put((byte) n);
            else if (n < 65536) head.put((byte) 126).putShort((short) n);
            else head.put((byte) 127).putLong(n);
            out.write(head.array(), 0, head.position());
            out.write(payload);
            out.flush();
        }
    }
}
