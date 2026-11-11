package codeling.client;

import codeling.Codeling;
import codeling.net.Protocol;
import codeling.net.RelayPayloads;
import codeling.relay.RelayServer;
import codeling.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
import com.mojang.blaze3d.Blaze3D;
//?} else
/*import net.minecraft.Util;*/
import java.net.URI;

/**
 * The client half of the mod: the editor and relay server. Each loader's client entrypoint registers the renderer,
 * screen, keys and HUD and calls these hooks from its own events (issue #186).
 */
public final class CodelingClient {
    private static RelayServer relay;
    /** Issue #192: the linked editor was told that the game is paused. */
    private static boolean toldPaused;

    private CodelingClient() {
    }

    /** The running relay, or null. For the end-to-end client GameTest: its editor URL holds the token, never log it. */
    public static RelayServer relay() {
        return relay;
    }

    /** The game has started: start the editor and relay server. */
    public static void started(Minecraft client) {
        int port = Integer.getInteger("codeling.port", RelayServer.DEFAULT_PORT);
        try {
            relay = new RelayServer(port, new GameLink(client)).start();
            Codeling.LOG.info("Codeling editor on http://127.0.0.1:{}/ (token {})", port, relay.tokenHint());
        } catch (Exception e) {
            // shortcut: log only; show an in-game error when the port is busy (not done in step 2).
            Codeling.LOG.error("Codeling could not start the editor server on port {}", port, e);
        }
    }

    public static void stopping() {
        if (relay != null) relay.close();
    }

    /** A {@code codeling:to_client} payload goes to the browser session. Runs on the render thread; deliver is thread-safe. */
    public static void receive(String json) {
        if (relay != null) relay.deliver(json);
    }

    /** True while a program runs from the editor: losing focus to the browser must not pause the game (issue #192). */
    public static boolean keepRunning() {
        return relay != null && relay.linked();
    }

    /**
     * Each client tick (issue #192): when singleplayer is paused (the pause menu is open) while a program runs, the
     * integrated server stops and the robot's commands wait. Tell the editor once, and again when the game goes on;
     * the waiting commands then run.
     */
    public static void tick(Minecraft client) {
        boolean paused = keepRunning() && client.isPaused();
        if (paused == toldPaused) return;
        toldPaused = paused;
        relay.deliver(Protocol.control("notice", -1, paused ? "Minecraft is paused. Go back to the game and close the menu."
                : "Minecraft is running again."));
    }

    /** Leaving the world ends the run on the server; tell the browser so the sim does not wait forever. */
    public static void disconnected() {
        if (relay != null) relay.deliver(Protocol.control("stop", -1, "You left the world, so the program stopped."));
    }

    // shortcut: /codeling client command instead of a key binding; add a key when the HUD comes.
    /** The {@code /codeling} client command: opens the editor in the browser. */
    public static int openEditor() {
        if (relay == null) return 0;
        // shortcut: on Linux and macOS openUri passes the URL with the token to xdg-open or open in argv, which other
        // local users can read with ps; hand the token over another way when the security review issue is taken up.
        //? if >=26.1 {
        Blaze3D.openUri(URI.create(relay.editorUrl()));
        //?} else
        /*Util.getPlatform().openUri(URI.create(relay.editorUrl()));*/
        return 1;
    }

    /** Sends browser messages to the logical server as payloads (singleplayer or a server with Codeling). */
    private record GameLink(Minecraft client) implements RelayServer.Link {
        @Override
        public String playerName() {
            return client.getUser().getName();
        }

        // Called on the relay's Netty thread: checks here are best effort, the send itself runs on the render thread.
        @Override
        public String send(String json) {
            if (client.getConnection() == null) return "Not in a world. Open a world in Minecraft, then run the program again.";
            try {
                if (!ClientPlatform.INSTANCE.canSend(RelayPayloads.ToServer.TYPE)) return "This server does not have Codeling installed.";
            } catch (IllegalStateException e) {
                return "Not in a world. Open a world in Minecraft, then run the program again.";
            }
            client.execute(() -> {
                if (client.getConnection() != null) ClientPlatform.INSTANCE.send(new RelayPayloads.ToServer(json));
            });
            return null;
        }
    }
}
