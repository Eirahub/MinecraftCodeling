package codeling.gametest;

import codeling.Codeling;
import codeling.McCompat;
import codeling.net.Protocol;
import codeling.net.ServerRelay;
import codeling.robot.RobotCamera;
import codeling.robot.RobotManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
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

    private static void command(GameTestHelper helper, String command) {
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static String gameplay(long id, long run, String op) {
        return "{\"v\":2,\"t\":\"cmd\",\"id\":" + id + ",\"run\":" + run + ",\"op\":\"gameplay." + op + "\",\"args\":[]}";
    }

    // Issue #87: the time and the weather of the owner's dimension, set with /time and /weather and read back. One test,
    // so nothing else changes them meanwhile; both are put back at the end. Issue #89: the day start event is checked
    // here too, so this stays the only test that changes the time: 1234 to 23000 is no new day, 23000 to 100 is one.
    @GameTest(maxTicks = 200)
    public void timeOfDayAndRainAreRead(GameTestHelper helper) {
        Browser b = browser(helper);
        long before = McCompat.dayTime(helper.getLevel());
        command(helper, "time set 1234");
        command(helper, "weather clear");
        long run = b.start();
        subscribe(b, run, ",\"dayStart\":true");
        b.send(gameplay(1, run, "timeOfDay"));
        b.send(gameplay(2, run, "isRaining"));
        command(helper, "weather rain");
        helper.runAfterDelay(5, () -> command(helper, "time set 23000"));
        helper.runAfterDelay(8, () -> command(helper, "time set 100"));
        // The rain level rises by 0.01 a tick and counts as rain from 0.2.
        helper.runAfterDelay(40, () -> b.send(gameplay(3, run, "isRaining")));
        helper.succeedWhen(() -> {
            for (int id = 1; id <= 3; id++) helper.assertTrue(b.find("result", id) != null, "result " + id);
            helper.assertValueEqual(events(b).stream().map(e -> e.get("name").getAsString()).toList(), List.of("dayStart"), "one day start");
            long time = b.find("result", 1).get("value").getAsLong();
            // The daylight cycle may run in the test server, so a few ticks may have passed.
            helper.assertTrue(time >= 1234 && time < 1234 + 20, "time of day " + time);
            helper.assertValueEqual(b.find("result", 2).get("value").getAsBoolean(), false, "clear");
            helper.assertValueEqual(b.find("result", 3).get("value").getAsBoolean(), true, "rain");
            command(helper, "weather clear");
            command(helper, "time set " + Math.floorMod(before, 24000L));
            b.leave();
        });
    }

    // ---- Issue #27: chat and death events

    // Every chat line that was broadcast, as "<sender uuid> <text>": CHAT_MESSAGE fires only for lines not held back.
    private static final Set<String> BROADCAST = ConcurrentHashMap.newKeySet();

    static {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> BROADCAST.add(sender.getUUID() + " " + message.signedContent()));
    }

    // A chat line typed by `player`, through the PlayerList call the game's chat handler makes (and Fabric's event).
    private static void chat(ServerPlayer player, String text) {
        player.level().getServer().getPlayerList().broadcastChatMessage(PlayerChatMessage.unsigned(player.getUUID(), text), player, ChatType.bind(ChatType.CHAT, player));
    }

    private static boolean broadcast(ServerPlayer player, String text) {
        return BROADCAST.contains(player.getUUID() + " " + text);
    }

    private static void subscribe(Browser b, long run, String fields) {
        b.send("{\"v\":2,\"t\":\"control\",\"op\":\"subscribe\",\"run\":" + run + fields + "}");
    }

    private static List<JsonObject> events(Browser b) {
        return b.inbox().stream().filter(m -> m.get("t").getAsString().equals("event")).toList();
    }

    @GameTest
    public void chatLineForTheProgramIsHeldBack(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        subscribe(b, run, ",\"chat\":[\"jump\"]");
        chat(b.player(), "jump 3 x -2 9");
        chat(b.player(), "hello jump 4");
        helper.assertTrue(!broadcast(b.player(), "jump 3 x -2 9"), "matching line held back");
        helper.assertTrue(broadcast(b.player(), "hello jump 4"), "other line broadcast");
        helper.assertTrue(events(b).isEmpty(), "events wait for the tick");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(events(b).size(), 1, "one event");
            helper.assertValueEqual(events(b).getFirst().toString(), "{\"v\":2,\"t\":\"event\",\"name\":\"chat\",\"args\":[\"jump\",3.0,0.0,-2.0]}", "event");
            b.leave();
        });
    }

    // Only the sender's own program hears a line: a program listening for every line ("*") and for its owner's death
    // gets nothing when another player chats or dies.
    @GameTest
    public void anotherPlayersChatNeverReachesTheProgram(GameTestHelper helper) {
        Browser b = browser(helper);
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        long run = b.start();
        subscribe(b, run, ",\"chat\":[\"*\"],\"died\":true");
        chat(other, "jump 5");
        other.die(other.damageSources().genericKill());
        helper.assertTrue(broadcast(other, "jump 5"), "other player's line broadcast");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(events(b).isEmpty(), "no event: " + events(b));
            helper.getLevel().getServer().getPlayerList().remove(other);
            b.leave();
            helper.succeed();
        });
    }

    // Stop from the editor or /robot stop: the subscriptions are gone at once and chat is broadcast again; /robot stop
    // also tells the editor on the next tick, since a program with only handlers has no command waiting.
    @GameTest
    public void subscriptionsEndWithTheRun(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        subscribe(b, run, ",\"chat\":[\"jump\"]");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(b.player().createCommandSourceStack(), "robot stop");
        chat(b.player(), "jump 1");
        helper.assertTrue(broadcast(b.player(), "jump 1"), "broadcast after /robot stop");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(b.inbox().stream().anyMatch(m -> m.get("t").getAsString().equals("control") && m.get("op").getAsString().equals("stop")
                    && m.get("run").getAsLong() == run), "editor told about /robot stop");
            long second = b.start();
            subscribe(b, second, ",\"chat\":[\"jump\"]");
            b.send("{\"v\":2,\"t\":\"control\",\"op\":\"stop\",\"run\":" + second + "}");
            chat(b.player(), "jump 2");
            helper.assertTrue(broadcast(b.player(), "jump 2"), "broadcast after Stop");
            subscribe(b, second, ",\"chat\":[\"jump\"]");
            helper.assertValueEqual(b.inbox().getLast().get("code").getAsString(), "STOPPED", "subscribe for an ended run");
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(events(b).isEmpty(), "no events");
                b.leave();
                helper.succeed();
            });
        });
    }

    @GameTest
    public void deathEventForTheOwner(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        subscribe(b, run, ",\"died\":true");
        b.player().die(b.player().damageSources().genericKill());
        helper.succeedWhen(() -> {
            helper.assertValueEqual(events(b).size(), 1, "one event");
            helper.assertValueEqual(events(b).getFirst().get("name").getAsString(), "playerDied", "name");
            b.leave();
        });
    }

    // /codeling chat needs no operator rights; without a listening program, or for another word, nothing happens.
    @GameTest
    public void codelingChatCommand(GameTestHelper helper) {
        Browser b = browser(helper);
        var commands = helper.getLevel().getServer().getCommands();
        commands.performPrefixedCommand(b.player().createCommandSourceStack(), "codeling chat jump 4");
        long run = b.start();
        subscribe(b, run, ",\"chat\":[\"jump\"]");
        commands.performPrefixedCommand(b.player().createCommandSourceStack(), "codeling chat hop 4");
        commands.performPrefixedCommand(b.player().createCommandSourceStack(), "codeling chat jump 7 8");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(events(b).size(), 1, "one event");
            helper.assertValueEqual(events(b).getFirst().get("args").toString(), "[\"jump\",7.0,8.0,0.0]", "args");
            b.leave();
        });
    }

    // At most 16 events wait for one player; the rest are dropped, and their lines are still held back.
    @GameTest(maxTicks = 60)
    public void waitingEventsAreCapped(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        subscribe(b, run, ",\"chat\":[\"n\"]");
        for (int i = 1; i <= 20; i++) chat(b.player(), "n " + i);
        helper.assertTrue(!broadcast(b.player(), "n 20"), "held back");
        helper.runAfterDelay(30, () -> {
            helper.assertValueEqual(events(b).size(), 16, "events");
            helper.assertValueEqual(events(b).getLast().get("args").getAsJsonArray().get(1).getAsDouble(), 16.0, "last kept");
            b.leave();
            helper.succeed();
        });
    }

    // ---- Issue #89: block broken and redstone events (the day start is in timeOfDayAndRainAreRead)

    private static final int STONE = 1;

    // Only the owner's own break of a block the program listens for: another player's stone and the owner's dirt give nothing.
    @GameTest
    public void blockBrokenByTheOwnerOnly(GameTestHelper helper) {
        Browser b = browser(helper);
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        helper.setBlock(START.east(2), Blocks.STONE);
        helper.setBlock(START.east(3), Blocks.STONE);
        helper.setBlock(START.east(4), Blocks.DIRT);
        long run = b.start();
        subscribe(b, run, ",\"broken\":[" + STONE + "]");
        other.gameMode.destroyBlock(helper.absolutePos(START.east(2)));
        b.player().gameMode.destroyBlock(helper.absolutePos(START.east(3)));
        b.player().gameMode.destroyBlock(helper.absolutePos(START.east(4)));
        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(Blocks.AIR, START.east(2));
            helper.assertBlockPresent(Blocks.AIR, START.east(4));
            helper.assertValueEqual(events(b).stream().map(JsonObject::toString).toList(),
                    List.of("{\"v\":2,\"t\":\"event\",\"name\":\"blockBroken\",\"args\":[" + STONE + "]}"), "events");
            helper.getLevel().getServer().getPlayerList().remove(other);
            b.leave();
            helper.succeed();
        });
    }

    // The robot breaks blocks through a FakePlayer with its owner's UUID: that is never the owner's break.
    @GameTest(maxTicks = 100)
    public void robotsOwnBreakIsNoEvent(GameTestHelper helper) {
        Browser b = browser(helper);
        helper.setBlock(START.south(), Blocks.STONE);
        long run = b.start();
        subscribe(b, run, ",\"broken\":[" + STONE + "]");
        b.send("{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":" + run + ",\"op\":\"agent.destroy\",\"args\":[0]}");
        helper.succeedWhen(() -> {
            helper.assertTrue(b.find("result", 1) != null, "destroy answered");
            helper.assertBlockPresent(Blocks.AIR, START.south());
            helper.assertTrue(events(b).isEmpty(), "no event: " + events(b));
            b.leave();
        });
    }

    // Twenty breaks in one tick: 16 events wait and the rest are dropped, as for chat.
    @GameTest(maxTicks = 60)
    public void blockBrokenEventsAreCapped(GameTestHelper helper) {
        Browser b = browser(helper);
        List<BlockPos> stones = new ArrayList<>();
        for (int x = 0; x < 5; x++) for (int y = 2; y < 6; y++) stones.add(new BlockPos(x, y, 5));
        stones.forEach(p -> helper.setBlock(p, Blocks.STONE));
        long run = b.start();
        subscribe(b, run, ",\"broken\":[" + STONE + "]");
        stones.forEach(p -> b.player().gameMode.destroyBlock(helper.absolutePos(p)));
        helper.runAfterDelay(30, () -> {
            helper.assertValueEqual(events(b).size(), 16, "events");
            b.leave();
            helper.succeed();
        });
    }

    // One event each time the robot's block goes from no signal to a signal, none while the signal stays.
    @GameTest(maxTicks = 60)
    public void redstoneAtTheRobotOncePerRisingEdge(GameTestHelper helper) {
        Browser b = browser(helper);
        long run = b.start();
        subscribe(b, run, ",\"redstone\":true");
        helper.runAfterDelay(2, () -> helper.setBlock(START.east(), Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(8, () -> {
            helper.assertValueEqual(events(b).size(), 1, "first signal");
            helper.setBlock(START.east(), Blocks.AIR);
        });
        helper.runAfterDelay(12, () -> helper.setBlock(START.east(), Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(events(b).stream().map(e -> e.get("name").getAsString()).toList(), List.of("redstonePowered", "redstonePowered"), "events");
            b.leave();
            helper.succeed();
        });
    }

    // /robot stop ends these subscriptions too: a break and a signal afterwards give nothing.
    @GameTest
    public void blockAndRedstoneEndWithTheRun(GameTestHelper helper) {
        Browser b = browser(helper);
        helper.setBlock(START.east(3), Blocks.STONE);
        long run = b.start();
        subscribe(b, run, ",\"broken\":[" + STONE + "],\"redstone\":true");
        helper.getLevel().getServer().getCommands().performPrefixedCommand(b.player().createCommandSourceStack(), "robot stop");
        b.player().gameMode.destroyBlock(helper.absolutePos(START.east(3)));
        helper.setBlock(START.east(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(Blocks.AIR, START.east(3));
            helper.assertTrue(events(b).isEmpty(), "no events: " + events(b));
            b.leave();
            helper.succeed();
        });
    }

    private static String camera(long id, long run, int mode) {
        return "{\"v\":2,\"t\":\"cmd\",\"id\":" + id + ",\"run\":" + run + ",\"op\":\"agent.camera\",\"args\":[" + mode + "]}";
    }
}
