package codeling.gametest;

import codeling.Codeling;
import codeling.client.CodelingClient;
import codeling.relay.RelayServer;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

/**
 * Client smoke check for Fabric versions without the client GameTest API (issue #189; that API came with 1.21.5), run
 * by ./gradlew :fabric:1.21.1:runClientSmoke (system property codeling.clientSmoke). Same checks as
 * NeoForgeClientSmoke: once the client has loaded its resources it checks that the camera mixins were merged and that
 * the relay serves the editor from the mod jar, logs the result and quits; a failure exits with status 1 so the Gradle
 * task fails. Never logs the editor URL: it holds the token. Without the property (every other run) it does nothing.
 */
public final class FabricClientSmoke implements ClientModInitializer {
    private boolean done;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("codeling.clientSmoke")) return;
        // Ticks come after CLIENT_STARTED, where Codeling starts the relay. Waits for the resource load to end: quitting
        // during it trips vanilla's TextureManager.close.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (done) return;
            //? if >=26.1 {
            if (client.gui.overlay() != null) return;
            //?} else
            /*if (client.getOverlay() != null) return;*/
            done = true;
            check(client);
        });
    }

    private static void check(Minecraft client) {
        List<String> failures = new ArrayList<>();
        if (!merged(Camera.class, "codeling$robotCamera")) failures.add("CameraMixin not applied");
        if (!merged(MouseHandler.class, "codeling$orbit") || !merged(MouseHandler.class, "codeling$zoom")) failures.add("MouseHandlerMixin not applied");
        //? if >=26.1 {
        if (!merged(net.minecraft.client.Minecraft.class, "codeling$keepRunning")) failures.add("PauseOnLostFocusMixin not applied");
        //?} else
        /*if (!merged(net.minecraft.client.renderer.GameRenderer.class, "codeling$keepRunning")) failures.add("PauseOnLostFocusMixin not applied");*/
        //? if <1.21.2
        /*if (!merged(net.minecraft.client.renderer.GameRenderer.class, "codeling$noHandOnRobot")) failures.add("GameRendererMixin not applied");*/
        RelayServer relay = CodelingClient.relay();
        if (relay == null) {
            failures.add("relay not started");
        } else {
            try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                HttpResponse<String> index = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + relay.port() + "/")).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (index.statusCode() != 200 || !index.body().contains("<html")) failures.add("editor index: HTTP " + index.statusCode());
            } catch (Exception e) {
                failures.add("editor index: " + e);
            }
        }
        if (failures.isEmpty()) {
            Codeling.LOG.info("Codeling client smoke: OK (mixins merged, relay on port {} serves the editor)", relay.port());
            client.stop();
        } else {
            Codeling.LOG.error("Codeling client smoke: FAILED {}", failures);
            Runtime.getRuntime().halt(1);
        }
    }

    // Mixin merges a handler into the target under a name that keeps the handler's own name.
    private static boolean merged(Class<?> target, String handler) {
        return Arrays.stream(target.getDeclaredMethods()).map(Method::getName).anyMatch(name -> name.contains(handler));
    }
}
