package codeling.gametest;

import codeling.client.CodelingClient;
import codeling.relay.RelayServer;
import codeling.robot.RobotEntity;
import codeling.robot.RobotManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Issue #135: the editor in headless Chromium (e2e/editor-run.mjs, Playwright) talks to the relay the client started,
 * which forwards to the singleplayer server, which moves the robot. The editor URL holds the session token: it is read
 * from the relay in this process and handed to the script on stdin only, and never logged.
 */
public class EditorEndToEndClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("codeling/e2e");
    private static final long SCRIPT_TIMEOUT_MS = 120_000;

    @Override
    public void runTest(ClientGameTestContext context) {
        RelayServer relay = CodelingClient.relay();
        if (relay == null) throw new AssertionError("the relay did not start with the client (port 25580 busy?)");
        String url = relay.editorUrl();

        // No world yet (title screen): the relay cannot forward the run, the editor says so.
        editor(context, url, "agent.move(SixDirection.Forward, 1)", "Not in a world", () -> { });

        // A token from an earlier game: refused before anything reaches the game.
        String stale = url.substring(0, url.indexOf("#local_token=") + "#local_token=".length()) + "staleTokenFromAnEarlierGame";
        editor(context, stale, "agent.move(SixDirection.Forward, 1)", "Editor link expired", () -> { });

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            TestServerContext server = world.getServer();
            UUID owner = context.computeOnClient(mc -> mc.player.getUUID());
            context.runOnClient(mc -> mc.getConnection().sendCommand("robot spawn"));
            world.getConnection().waitForServerboundPackets();
            BlockPos start = server.computeOnServer(s -> robot(owner).blockPosition());
            Direction facing = server.computeOnServer(s -> robot(owner).getDirection());

            // #3: agent.move from the editor moves the robot, and the result comes back to the editor.
            editor(context, url, "agent.move(SixDirection.Forward, 3)", "move 0 3: done", () -> { });
            BlockPos moved = start.relative(facing, 3);
            assertEquals(moved, server.computeOnServer(s -> robot(owner).blockPosition()), "robot after agent.move 3");

            // /robot stop typed in the game during a long move: the robot halts and the editor shows why.
            boolean[] stopped = {false};
            editor(context, url, "agent.move(SixDirection.Forward, 30)", "stopped with /robot stop", () -> {
                if (!stopped[0] && server.computeOnServer(s -> robot(owner).blockPosition().distManhattan(moved)) >= 2) {
                    context.runOnClient(mc -> mc.getConnection().sendCommand("robot stop"));
                    stopped[0] = true;
                }
            });
            if (!stopped[0]) throw new AssertionError("the long move never started, so /robot stop was not sent");
            BlockPos haltedAt = server.computeOnServer(s -> robot(owner).blockPosition());
            int went = haltedAt.distManhattan(moved);
            if (went < 2 || went >= 30) throw new AssertionError("robot should stop part of the way, went " + went + " blocks");
            context.waitTicks(20);
            assertEquals(haltedAt, server.computeOnServer(s -> robot(owner).blockPosition()), "robot after /robot stop");
        }
    }

    /**
     * Runs e2e/editor-run.mjs with this program until the robot log in the simulator contains {@code expect}. The game
     * keeps ticking meanwhile (client GameTests only tick when the test waits); {@code everyTick} runs after each tick.
     */
    private static void editor(ClientGameTestContext context, String url, String program, String expect, Runnable everyTick) {
        Path dir = Path.of(System.getProperty("codeling.e2eDir")); // set by the clientGameTest run in fabric/build.gradle.kts
        Path shots = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        Process node;
        try {
            Files.createDirectories(shots);
            ProcessBuilder builder = new ProcessBuilder("node", dir.resolve("editor-run.mjs").toString()).directory(dir.toFile()).redirectErrorStream(true);
            builder.environment().put("CODELING_PROGRAM", program);
            builder.environment().put("CODELING_EXPECT", expect);
            builder.environment().put("CODELING_TIMEOUT_MS", Long.toString(SCRIPT_TIMEOUT_MS - 30_000));
            builder.environment().put("CODELING_SHOTS", shots.toString());
            node = builder.start();
            try (Writer stdin = node.outputWriter(StandardCharsets.UTF_8)) {
                stdin.write(url + "\n");
            }
        } catch (IOException e) {
            throw new AssertionError("cannot start node with e2e/editor-run.mjs (is Node.js on PATH and e2e installed?)", e);
        }
        List<String> lines = new CopyOnWriteArrayList<>();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader out = new BufferedReader(new InputStreamReader(node.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = out.readLine()) != null; ) {
                    lines.add(line);
                    LOG.info("editor: {}", line.replaceAll("local_token=[A-Za-z0-9_-]+", "local_token=<hidden>"));
                }
            } catch (IOException ignored) {
                // the process ended
            }
        });
        long deadline = System.currentTimeMillis() + SCRIPT_TIMEOUT_MS;
        try {
            while (node.isAlive()) {
                if (System.currentTimeMillis() > deadline) throw new AssertionError("editor script timed out: " + program);
                context.waitTick();
                everyTick.run();
                // Real-time pace: the server counts the 10 s heartbeat timeout in ticks, the sim sends one every 3 s.
                Thread.sleep(50);
            }
            reader.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } finally {
            node.destroyForcibly();
        }
        String last = lines.isEmpty() ? "" : lines.getLast();
        if (!last.startsWith("{")) throw new AssertionError("editor script gave no result for " + program + ": " + last);
        JsonObject result = JsonParser.parseString(last).getAsJsonObject();
        if (!result.get("ok").getAsBoolean()) {
            throw new AssertionError("editor run of " + program + " expected \"" + expect + "\": " + result);
        }
    }

    private static RobotEntity robot(UUID owner) {
        RobotEntity robot = RobotManager.robot(owner);
        if (robot == null) throw new AssertionError("robot is not loaded");
        return robot;
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }
}
