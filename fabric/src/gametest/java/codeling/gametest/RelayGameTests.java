package codeling.gametest;

import codeling.Codeling;
import codeling.net.Protocol;
import codeling.net.ServerRelay;
import codeling.robot.RobotCamera;
import codeling.robot.RobotManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server end of the relay. Calls {@link ServerRelay#handle} directly with a mock player and collects the replies,
 * instead of real payload delivery (the mock player's connection is an embedded channel with no client).
 */
public class RelayGameTests {
    private static final BlockPos START = new BlockPos(1, 2, 1);

    private record Browser(GameTestHelper helper, ServerPlayer player, List<JsonObject> inbox) {
        void send(String json) {
            ServerRelay.handle(player, json, reply -> inbox.add(JsonParser.parseString(reply).getAsJsonObject()));
        }

        long start() {
            send("{\"v\":2,\"t\":\"control\",\"op\":\"start\"}");
            JsonObject reply = inbox.removeLast();
            helper.assertValueEqual(reply.get("op").getAsString(), "start", "start reply");
            return reply.get("run").getAsLong();
        }

        JsonObject find(String type, long id) {
            return inbox.stream().filter(m -> m.get("t").getAsString().equals(type) && m.has("id") && m.get("id").getAsLong() == id)
                    .findFirst().orElse(null);
        }

        void leave() {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    /** A mock player out of reach of other tests, with a robot at START facing south (+z). */
    private static Browser browser(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(START).above(100)));
        RobotManager.spawn(helper.getLevel(), player.getUUID(), Vec3.atBottomCenterOf(helper.absolutePos(START)), Direction.SOUTH);
        return new Browser(helper, player, new ArrayList<>());
    }

    private static String move(long id, long run, int blocks) {
        return "{\"v\":2,\"t\":\"cmd\",\"id\":" + id + ",\"run\":" + run + ",\"op\":\"agent.move\",\"args\":[0," + blocks + "]}";
    }

    @GameTest(maxTicks = 100)
    public void moveAnswersAfterTheMoveFinishes(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        b.send(move(1, run, 3));
        helper.assertTrue(b.find("result", 1) == null, "no result before the move ran");
        helper.succeedWhen(() -> {
            JsonObject result = b.find("result", 1);
            helper.assertTrue(result != null, "result for cmd 1");
            helper.assertTrue(result.get("value").getAsBoolean(), "move value");
            helper.assertValueEqual(RobotManager.robot(b.player().getUUID()).blockPosition(), helper.absolutePos(START.south(3)), "position");
            b.leave();
        });
    }

    @GameTest
    public void staleRunIsStopped(GameTestHelper helper) {
        Browser b = browser(helper);
        long old = b.start();
        long run = b.start();
        helper.assertTrue(run != old, "new run id");
        b.send(move(1, old, 1));
        JsonObject error = b.find("error", 1);
        helper.assertTrue(error != null, "error for the old run");
        helper.assertValueEqual(error.get("code").getAsString(), "STOPPED", "code");
        b.leave();
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void stopDropsTheQueue(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        b.send(move(1, run, 10));
        b.send(move(2, run, 10));
        helper.runAfterDelay(6, () -> {
            b.send("{\"v\":2,\"t\":\"control\",\"op\":\"stop\",\"run\":" + run + "}");
            helper.assertValueEqual(b.find("error", 1).get("code").getAsString(), "STOPPED", "cmd 1");
            helper.assertValueEqual(b.find("error", 2).get("code").getAsString(), "STOPPED", "cmd 2");
            BlockPos stoppedAt = RobotManager.robot(b.player().getUUID()).blockPosition();
            helper.runAfterDelay(20, () -> {
                helper.assertValueEqual(RobotManager.robot(b.player().getUUID()).blockPosition(), stoppedAt, "robot stays put");
                b.send(move(3, run, 1));
                helper.assertValueEqual(b.find("error", 3).get("code").getAsString(), "STOPPED", "cmd after stop");
                b.leave();
                helper.succeed();
            });
        });
    }

    // Issue #5: /robot stop typed in game during a long browser move halts the robot and answers the pending cmd.
    @GameTest(maxTicks = 100)
    public void robotStopCommandHaltsABrowserMove(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        b.send(move(1, run, 30));
        helper.runAfterDelay(6, () -> {
            helper.getLevel().getServer().getCommands().performPrefixedCommand(b.player().createCommandSourceStack(), "robot stop");
            JsonObject error = b.find("error", 1);
            helper.assertTrue(error != null, "pending move answered at once");
            helper.assertValueEqual(error.get("code").getAsString(), "STOPPED", "code");
            helper.assertValueEqual(error.get("message").getAsString(), "stopped with /robot stop", "message");
            BlockPos stoppedAt = RobotManager.robot(b.player().getUUID()).blockPosition();
            helper.assertTrue(!stoppedAt.equals(helper.absolutePos(START.south(30))), "stopped before the end");
            helper.runAfterDelay(20, () -> {
                helper.assertValueEqual(RobotManager.robot(b.player().getUUID()).blockPosition(), stoppedAt, "robot stays put");
                // The sim's next heartbeat learns the run is over.
                b.send("{\"v\":2,\"t\":\"control\",\"op\":\"heartbeat\",\"run\":" + run + "}");
                JsonObject stop = b.inbox().getLast();
                helper.assertValueEqual(stop.get("op").getAsString(), "stop", "heartbeat answer");
                b.leave();
                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 100)
    public void pauseHoldsTheQueue(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        b.send("{\"v\":2,\"t\":\"control\",\"op\":\"pause\",\"run\":" + run + "}");
        b.send(move(1, run, 2));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(b.find("result", 1) == null, "no result while paused");
            helper.assertValueEqual(RobotManager.robot(b.player().getUUID()).blockPosition(), helper.absolutePos(START), "not moved while paused");
            b.send("{\"v\":2,\"t\":\"control\",\"op\":\"resume\",\"run\":" + run + "}");
            helper.succeedWhen(() -> {
                helper.assertTrue(b.find("result", 1) != null, "result after resume");
                b.leave();
            });
        });
    }

    // Issue #25: pos is relative to the owner's feet, posLocal also turns with the owner's facing; answered at once.
    @GameTest
    public void positionsResolveAgainstTheOwner(GameTestHelper helper) {
        Browser b = browser(helper);
        b.player().setYRot(-90); // facing east: left is north (-z)
        BlockPos feet = b.player().blockPosition();
        long run = b.start();
        b.send("{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":" + run + ",\"op\":\"positions.toWorld\",\"args\":[1,0,0,1]}");
        b.send("{\"v\":2,\"t\":\"cmd\",\"id\":2,\"run\":" + run + ",\"op\":\"positions.toWorld\",\"args\":[2,1,2,3]}");
        helper.assertValueEqual(b.find("result", 1).get("value").toString(), "[" + feet.getX() + "," + feet.getY() + "," + (feet.getZ() + 1) + "]", "pos(0, 0, 1)");
        helper.assertValueEqual(b.find("result", 2).get("value").toString(), "[" + (feet.getX() + 3) + "," + (feet.getY() + 2) + "," + (feet.getZ() - 1) + "]", "posLocal(1, 2, 3)");
        b.send("{\"v\":2,\"t\":\"cmd\",\"id\":3,\"run\":" + (run - 1) + ",\"op\":\"positions.toWorld\",\"args\":[1,0,0,1]}");
        helper.assertValueEqual(b.find("error", 3).get("code").getAsString(), "STOPPED", "old run");
        b.leave();
        helper.succeed();
    }

    @GameTest
    public void badMessagesGetErrorsAndKeepThePlayer(GameTestHelper helper) {
        Browser b = browser(helper);
        b.send("not json");
        b.send("{\"v\":1,\"t\":\"cmd\",\"id\":5}");
        b.send("{\"v\":2,\"t\":\"cmd\",\"id\":6,\"run\":1,\"op\":\"agent.fly\",\"args\":[]}");
        helper.assertValueEqual(b.inbox().get(0).get("code").getAsString(), "BAD_ARGS", "not json");
        helper.assertValueEqual(b.inbox().get(1).get("code").getAsString(), "VERSION", "version");
        helper.assertValueEqual(b.find("error", 6).get("code").getAsString(), "BAD_ARGS", "unknown op");
        helper.assertTrue(!b.player().hasDisconnected(), "player still connected");
        b.leave();
        helper.succeed();
    }

    @GameTest(maxTicks = Protocol.HEARTBEAT_TIMEOUT_TICKS + 60)
    public void missingHeartbeatStopsTheRun(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        helper.succeedWhen(() -> {
            JsonObject stop = b.inbox().stream().filter(m -> m.get("t").getAsString().equals("control")).findFirst().orElse(null);
            helper.assertTrue(stop != null, "control stop after the timeout");
            helper.assertValueEqual(stop.get("op").getAsString(), "stop", "op");
            helper.assertValueEqual(stop.get("run").getAsLong(), run, "run");
            helper.assertTrue(RobotManager.queue(b.player().getUUID()).run() != run, "run ended");
            b.leave();
        });
    }

    // Issue #177: agent.camera is answered at once with the /robot camera rules; refused with NO_PERMISSION when off.
    // The rule is changed and put back within this one call, so camera tests running at the same time never see it.
    @GameTest
    public void cameraFromProgram(GameTestHelper helper) {
        Browser b = browser(helper);
        b.player().setChunkTrackingView(ChunkTrackingView.of(b.player().chunkPosition(), 2));
        var server = helper.getLevel().getServer();
        long run = b.start();
        try {
            GameTestCompat.setCamera(server, RobotCamera.Mode.off);
            b.send(camera(1, run, RobotCamera.FOLLOW));
        } finally {
            GameTestCompat.setCamera(server, RobotCamera.Mode.owner);
        }
        helper.assertValueEqual(b.find("error", 1).get("code").getAsString(), "NO_PERMISSION", "rule off");
        helper.assertTrue(RobotCamera.viewing(b.player()) == null, "no view when refused");
        b.send(camera(2, run, RobotCamera.FOLLOW));
        helper.assertTrue(b.find("result", 2) != null, "owner allowed");
        helper.assertTrue(RobotCamera.following(b.player()), "following");
        b.send(camera(3, run, RobotCamera.OFF));
        helper.assertTrue(b.find("result", 3) != null, "off answered");
        helper.assertTrue(RobotCamera.viewing(b.player()) == null, "off");
        b.leave();
        helper.succeed();
    }

    // The error code for a command, or "none" yet, so a check inside succeedWhen fails softly and is tried again.
    private static String errorCode(Browser b, long id) {
        JsonObject error = b.find("error", id);
        return error == null ? "none" : error.get("code").getAsString();
    }

    private static String player(long id, long run, String op, String args) {
        return "{\"v\":2,\"t\":\"cmd\",\"id\":" + id + ",\"run\":" + run + ",\"op\":\"player." + op + "\",\"args\":" + args + "}";
    }

    // Issue #26: the player calls answer about the owner, one per tick, without needing the robot.
    @GameTest
    public void playerCallsAnswerAboutTheOwner(GameTestHelper helper) {
        Browser b = browser(helper);
        b.player().setYRot(-90); // east
        BlockPos feet = b.player().blockPosition();
        long run = b.start();
        b.send(player(1, run, "position", "[]"));
        b.send(player(2, run, "getOrientation", "[]"));
        b.send(player(3, run, "name", "[]"));
        b.send(player(4, run, "say", "[\"Hi §cthere\"]"));
        b.send(player(5, run - 1, "name", "[]"));
        helper.assertValueEqual(errorCode(b, 5), "STOPPED", "old run");
        helper.assertTrue(b.find("result", 1) == null, "answered on the tick, not at once");
        helper.succeedWhen(() -> {
            for (int id = 1; id <= 4; id++) helper.assertTrue(b.find("result", id) != null, "result " + id);
            helper.assertValueEqual(b.find("result", 1).get("value").toString(), "[" + feet.getX() + "," + feet.getY() + "," + feet.getZ() + "]", "position");
            helper.assertValueEqual(b.find("result", 2).get("value").getAsDouble(), -90.0, "orientation");
            helper.assertValueEqual(b.find("result", 3).get("value").getAsString(), b.player().getGameProfile().name(), "name");
            b.leave();
        });
    }

    // Issue #26: more than a robot queue's worth of waiting player calls is refused, as a full robot queue is.
    @GameTest
    public void playerCallsAreCapped(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        for (int i = 1; i <= 17; i++) b.send(player(i, run, "name", "[]"));
        helper.assertValueEqual(errorCode(b, 17), "RATE_LIMITED", "17th call");
        b.leave();
        helper.succeed();
    }

    // Issue #26: a player who is neither the singleplayer host nor an operator may not be teleported by their program.
    @GameTest
    public void playerTeleportNeedsOperator(GameTestHelper helper) {
        Browser b = browser(helper);
        BlockPos before = b.player().blockPosition();
        BlockPos to = helper.absolutePos(START.above(3));
        long run = b.start();
        b.send(player(1, run, "teleport", "[" + to.getX() + "," + to.getY() + "," + to.getZ() + "]"));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(errorCode(b, 1), "NO_PERMISSION", "not an op");
            helper.assertValueEqual(b.player().blockPosition(), before, "not moved");
            b.leave();
        });
    }

    // Issue #26: an operator is teleported to the middle of the block; outside the build height is refused.
    @GameTest
    public void playerTeleportForAnOperator(GameTestHelper helper) {
        Browser b = browser(helper);
        GameTestCompat.op(b.player());
        BlockPos to = helper.absolutePos(START.above(3));
        int tooHigh = 20000; // above every build height
        long run = b.start();
        b.send(player(1, run, "teleport", "[" + to.getX() + "," + to.getY() + "," + to.getZ() + "]"));
        b.send(player(2, run, "teleport", "[" + to.getX() + "," + tooHigh + "," + to.getZ() + "]"));
        helper.succeedWhen(() -> {
            helper.assertTrue(b.find("result", 1) != null, "teleport answered");
            helper.assertValueEqual(b.player().blockPosition(), to, "teleported");
            helper.assertValueEqual(errorCode(b, 2), "OUT_OF_RANGE", "above the build height");
            helper.assertValueEqual(b.player().blockPosition(), to, "not moved by the refused call");
            GameTestCompat.deop(b.player());
            b.leave();
        });
    }

    private static String camera(long id, long run, int mode) {
        return "{\"v\":2,\"t\":\"cmd\",\"id\":" + id + ",\"run\":" + run + ",\"op\":\"agent.camera\",\"args\":[" + mode + "]}";
    }
}
