package codeling.relay;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Issue #188: proves the relay on an old Java with no Netty on the classpath (run by {@code :core:relayCompatTest}).
 * Serves the packaged editor, then does the hello, welcome, cmd and result round trip over WebSocket. Exits 1 on failure.
 * The editor URL holds the token: it stays in this process and is never printed.
 */
public final class RelayCompatCheck {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        String java = System.getProperty("java.version");
        check(!onClasspath("io.netty.channel.Channel"), "Netty is on the classpath, so this proves nothing");
        RelayServer server = new RelayServer(port).start();
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String base = "http://127.0.0.1:" + port;
            HttpResponse<String> index = http.send(HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString());
            check(index.statusCode() == 200 && index.body().contains("<html"), "editor index: HTTP " + index.statusCode());
            check(index.headers().firstValue("content-type").orElse("").equals("text/html; charset=utf-8"), "index content type");
            check(index.headers().firstValue("cache-control").orElse("").equals("no-cache"), "index cache control");
            HttpResponse<Void> script = http.send(HttpRequest.newBuilder(URI.create(base + "/main.js")).build(), HttpResponse.BodyHandlers.discarding());
            check(script.statusCode() == 200, "main.js: HTTP " + script.statusCode());
            check(script.headers().firstValue("content-type").orElse("").startsWith("application/javascript"), "main.js content type");

            LinkedBlockingQueue<String> inbox = new LinkedBlockingQueue<>();
            WebSocket ws = http.newWebSocketBuilder()
                    .header("Origin", base)
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/relay"), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            inbox.add(data.toString());
                            webSocket.request(1);
                            return CompletableFuture.completedFuture(null);
                        }
                    })
                    .get(5, TimeUnit.SECONDS);
            String token = server.editorUrl().substring(server.editorUrl().indexOf('=') + 1);
            ws.sendText("{\"v\":2,\"t\":\"hello\",\"token\":\"" + token + "\"}", true).get(5, TimeUnit.SECONDS);
            String welcome = inbox.poll(5, TimeUnit.SECONDS);
            check(welcome != null && welcome.contains("\"t\":\"welcome\""), "welcome: " + welcome);
            ws.sendText("{\"v\":2,\"t\":\"cmd\",\"id\":7,\"run\":0,\"op\":\"agent.move\",\"args\":[0,1]}", true).get(5, TimeUnit.SECONDS);
            String result = inbox.poll(5, TimeUnit.SECONDS);
            check(result != null && result.contains("\"t\":\"result\"") && result.contains("\"id\":7"), "result: " + result);
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get(5, TimeUnit.SECONDS);
        } finally {
            server.close();
        }
        System.out.println("Relay compat OK on Java " + java + " without Netty: editor served, WebSocket round trip done");
    }

    private static boolean onClasspath(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void check(boolean ok, String what) {
        if (ok) return;
        System.err.println("Relay compat FAILED on Java " + System.getProperty("java.version") + ": " + what);
        System.exit(1);
    }
}
