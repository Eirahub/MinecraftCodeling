package codeling.net;

import codeling.robot.Result;
import codeling.robot.RobotCommand;
import codeling.robot.RobotException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    private static Result.Code code(String json) {
        RobotException e = assertThrows(RobotException.class, () -> Protocol.cmd(Protocol.parse(json)));
        return e.code;
    }

    @Test
    void decodesMoveThroughTheValidator() throws RobotException {
        Protocol.Cmd cmd = Protocol.cmd(Protocol.parse("{\"v\":2,\"t\":\"cmd\",\"id\":3,\"run\":9,\"op\":\"agent.move\",\"args\":[0,3]}"));
        assertEquals(3, cmd.id());
        assertEquals(9, cmd.run());
        assertEquals(new RobotCommand(RobotCommand.Op.MOVE, RobotCommand.FORWARD, 3), cmd.command());
        Protocol.Cmd turn = Protocol.cmd(Protocol.parse("{\"v\":2,\"t\":\"cmd\",\"id\":4,\"run\":9,\"op\":\"agent.turn\",\"args\":[3]}"));
        assertEquals(RobotCommand.Op.TURN, turn.command().op());
        Protocol.Cmd assist = Protocol.cmd(Protocol.parse("{\"v\":2,\"t\":\"cmd\",\"id\":5,\"run\":9,\"op\":\"agent.setAssist\",\"args\":[0,true]}"));
        assertEquals(1, assist.command().b());
    }

    // Issue #141: version 2 names one of the player's robots; without a name the command is for the default robot.
    @Test
    void robotNameInCmdAndControl() throws RobotException {
        String move = "\"t\":\"cmd\",\"id\":1,\"run\":2,\"op\":\"agent.move\",\"args\":[0,1]";
        assertEquals("", Protocol.cmd(Protocol.parse("{\"v\":2," + move + "}")).robot());
        assertEquals("", Protocol.cmd(Protocol.parse("{\"v\":2," + move + ",\"robot\":\"\"}")).robot());
        assertEquals("Miner", Protocol.cmd(Protocol.parse("{\"v\":2," + move + ",\"robot\":\"Miner\"}")).robot());
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2," + move + ",\"robot\":\"no spaces\"}"));
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2," + move + ",\"robot\":7}"));
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2," + move + ",\"robot\":\"" + "a".repeat(RobotCommand.MAX_NAME + 1) + "\"}"));
        assertEquals(Result.Code.VERSION, code("{\"v\":1," + move + ",\"robot\":\"Miner\"}"));
        assertEquals("Digger", Protocol.robot(Protocol.parse("{\"v\":2,\"t\":\"control\",\"op\":\"pause\",\"run\":2,\"robot\":\"Digger\"}")));
    }

    @Test
    void robotNameRules() {
        assertNull(RobotCommand.nameProblem("Miner"));
        assertNull(RobotCommand.nameProblem("bot-2_b"));
        assertNull(RobotCommand.nameProblem("a".repeat(RobotCommand.MAX_NAME)));
        for (String bad : new String[] {null, "", "2bots", "_x", "two words", "Mïner", "a.b", "a".repeat(RobotCommand.MAX_NAME + 1), "all", "Off", "follow", "on"}) {
            assertNotNull(RobotCommand.nameProblem(bad), "accepted " + bad);
        }
    }

    @Test
    void rejectsBadCommands() {
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":1,\"op\":\"agent.fly\",\"args\":[]}"));
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":1,\"op\":\"agent.move\",\"args\":{\"direction\":0}}"));
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":1,\"op\":\"agent.move\",\"args\":[[0],3]}"));
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2,\"t\":\"cmd\",\"id\":1,\"op\":\"agent.move\",\"args\":[0,3]}"));
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2,\"t\":\"cmd\",\"run\":1,\"op\":\"agent.move\",\"args\":[0,3]}"));
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":1,\"op\":\"agent.move\",\"args\":[0,1.5]}"));
        assertEquals(Result.Code.OUT_OF_RANGE, code("{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":1,\"op\":\"agent.move\",\"args\":[0,65]}"));
        assertEquals(Result.Code.BAD_ARGS, code("not json"));
        assertEquals(Result.Code.BAD_ARGS, code("[1,2]"));
        assertEquals(Result.Code.BAD_ARGS, code("{\"v\":2}"));
    }

    @Test
    void versionMismatchIsVersion() {
        assertEquals(Result.Code.VERSION, code("{\"v\":1,\"t\":\"cmd\"}"));
        assertEquals(Result.Code.VERSION, code("{\"t\":\"cmd\"}"));
    }

    @Test
    void sizeCap() throws RobotException {
        String pad = "x".repeat(Protocol.MAX_MESSAGE_BYTES);
        RobotException e = assertThrows(RobotException.class, () -> Protocol.parse("{\"v\":2,\"t\":\"cmd\",\"pad\":\"" + pad + "\"}"));
        assertEquals(Result.Code.BAD_ARGS, e.code);
        String fits = "x".repeat(Protocol.MAX_MESSAGE_BYTES - 40);
        assertNotNull(Protocol.parse("{\"v\":2,\"t\":\"cmd\",\"pad\":\"" + fits + "\"}"));
    }

    @Test
    void controlOps() throws RobotException {
        assertEquals("heartbeat", Protocol.controlOp(Protocol.parse("{\"v\":2,\"t\":\"control\",\"op\":\"heartbeat\",\"run\":2}")));
        assertThrows(RobotException.class, () -> Protocol.controlOp(Protocol.parse("{\"v\":2,\"t\":\"control\",\"op\":\"explode\"}")));
    }

    @Test
    void encodesReplies() {
        JsonObject ok = JsonParser.parseString(Protocol.reply(7, Result.ok(false))).getAsJsonObject();
        assertEquals("result", ok.get("t").getAsString());
        assertEquals(7, ok.get("id").getAsLong());
        assertFalse(ok.get("value").getAsBoolean());
        assertFalse(ok.has("message"));
        // Why a build did less than asked goes along with the count.
        JsonObject told = JsonParser.parseString(Protocol.reply(10, Result.ok(0, "I have no stone in my inventory."))).getAsJsonObject();
        assertEquals(0, told.get("value").getAsInt());
        assertEquals("I have no stone in my inventory.", told.get("message").getAsString());
        JsonObject err = JsonParser.parseString(Protocol.reply(8, Result.error(Result.Code.STOPPED, "stopped"))).getAsJsonObject();
        assertEquals("error", err.get("t").getAsString());
        assertEquals("STOPPED", err.get("code").getAsString());
        assertEquals(8, err.get("id").getAsLong());
        // getPosition: the sim reads value as [x, y, z]
        assertEquals("[10,-64,3]", JsonParser.parseString(Protocol.reply(9, Result.ok(java.util.List.of(10, -64, 3)))).getAsJsonObject().get("value").toString());
        JsonObject stop = JsonParser.parseString(Protocol.control("stop", 5, "why")).getAsJsonObject();
        assertEquals(Protocol.VERSION, stop.get("v").getAsInt());
        assertEquals(5, stop.get("run").getAsLong());
    }

    @Test
    void heartbeatTimeout() {
        Sessions sessions = new Sessions(200);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<String> out = new ArrayList<>();
        sessions.start(a, 10, 0, out::add);
        sessions.start(b, 11, 0, out::add);
        assertTrue(sessions.touch(a, 10, 150));
        assertFalse(sessions.touch(a, 9, 150), "old run id does not keep the run alive");
        assertFalse(sessions.touch(UUID.randomUUID(), 10, 150));
        assertTrue(sessions.expire(199).isEmpty());
        List<Map.Entry<UUID, Sessions.Session>> expired = sessions.expire(200);
        assertEquals(1, expired.size());
        assertEquals(b, expired.get(0).getKey());
        assertEquals(11, expired.get(0).getValue().run);
        assertNull(sessions.get(b), "expired sessions are removed");
        assertEquals(1, sessions.expire(350).size());
        assertTrue(sessions.expire(10_000).isEmpty());
    }
    /** The exact cmd JSON target/sim/api.ts sends for each op (JSON.stringify keeps this key order). */
    private static RobotCommand sim(String op, String args) throws RobotException {
        return Protocol.cmd(Protocol.parse("{\"t\":\"cmd\",\"id\":1,\"run\":4,\"op\":\"" + op + "\",\"args\":" + args + ",\"v\":2}")).command();
    }

    @Test
    void decodesEveryOpTheSimSends() throws RobotException {
        assertEquals(new RobotCommand(RobotCommand.Op.MOVE, 0, 3), sim("agent.move", "[0,3]"));
        assertEquals(new RobotCommand(RobotCommand.Op.TURN, 3, 0), sim("agent.turn", "[3]"));
        assertEquals(new RobotCommand(RobotCommand.Op.PLACE, 4, 0), sim("agent.place", "[4]"));
        assertEquals(new RobotCommand(RobotCommand.Op.DESTROY, 0, 0), sim("agent.destroy", "[0]"));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_ASSIST, 0, 1), sim("agent.setAssist", "[0,true]"));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_GRAVITY, 1, 0), sim("agent.setGravity", "[true]"));
        assertEquals(new RobotCommand(RobotCommand.Op.DETECT, 5, 1), sim("agent.detect", "[1,5]"));
        assertEquals(new RobotCommand(RobotCommand.Op.INSPECT, 0, 0), sim("agent.inspect", "[0,0]"));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_SLOT, 2, 0), sim("agent.setSlot", "[2]"));
        assertEquals(new RobotCommand(RobotCommand.Op.GET_ITEM_COUNT, 1, 0), sim("agent.getItemCount", "[1]"));
        assertEquals(new RobotCommand(RobotCommand.Op.GET_ITEM_SPACE, 27, 0), sim("agent.getItemSpace", "[27]"));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_ITEM, 65537, 64, 1), sim("agent.setItem", "[65537,64,1]"));
        assertEquals(new RobotCommand(RobotCommand.Op.COLLECT_ALL, 0, 0), sim("agent.collectAll", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.DROP, 1, 3, 5), sim("agent.drop", "[1,3,5]"));
        assertEquals(new RobotCommand(RobotCommand.Op.DROP_ALL, 5, 0), sim("agent.dropAll", "[5]"));
        assertEquals(new RobotCommand(RobotCommand.Op.TRANSFER, 10, 1, 2), sim("agent.transfer", "[10,1,2]"));
        assertEquals(new RobotCommand(RobotCommand.Op.GET_POSITION, 0, 0), sim("agent.getPosition", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.GET_ORIENTATION, 0, 0), sim("agent.getOrientation", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.RELATIVE_TO_WORLD, 0, -1, 2), sim("positions.toWorld", "[1,0,-1,2]"));
        assertEquals(new RobotCommand(RobotCommand.Op.LOCAL_TO_WORLD, 1, 0, 3), sim("positions.toWorld", "[2,1,0,3]"));
        assertEquals(new RobotCommand(RobotCommand.Op.FIND_BLOCK, 17, 16), sim("agent.findNearest", "[17,16]"));
        assertEquals(new RobotCommand(RobotCommand.Op.FIND_CATEGORY, 2, 32), sim("agent.findNearestCategory", "[2,32]"));
        assertEquals(new RobotCommand(RobotCommand.Op.SAY, 0, 0, 0, "Hello!"), sim("agent.say", "[\"Hello!\"]"));
        assertEquals(new RobotCommand(RobotCommand.Op.PLAYER_SAY, 0, 0, 0, "Hi!"), sim("player.say", "[\"Hi!\"]"));
        assertEquals(new RobotCommand(RobotCommand.Op.PLAYER_POSITION, 0, 0), sim("player.position", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.PLAYER_ORIENTATION, 0, 0), sim("player.getOrientation", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.PLAYER_NAME, 0, 0), sim("player.name", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.PLAYER_TELEPORT, 1, 100, -2), sim("player.teleport", "[1,100,-2]"));
        assertEquals(new RobotCommand(RobotCommand.Op.STORE_ITEMS, 0, 0), sim("agent.storeItems", "[0,0]"));
        assertEquals(new RobotCommand(RobotCommand.Op.TAKE_ITEMS, 4, 54, 64), sim("agent.takeItems", "[4,54,64]"));
        assertEquals(new RobotCommand(RobotCommand.Op.TAKE_ITEM, 1, 4, 10), sim("agent.takeItem", "[1,4,10]"));
        assertEquals(new RobotCommand(RobotCommand.Op.CONTAINER_COUNT, 0, 1), sim("agent.containerCount", "[0,1]"));
        assertEquals(new RobotCommand(RobotCommand.Op.CONTAINER_SIZE, 5, 0), sim("agent.containerSize", "[5]"));
        assertEquals(new RobotCommand(RobotCommand.Op.SORT_CONTAINER, 0, 0), sim("agent.sortContainer", "[0]"));
        assertEquals(new RobotCommand(RobotCommand.Op.CRAFT, 5, 4), sim("agent.craft", "[5,4]"));
        assertEquals(new RobotCommand(RobotCommand.Op.CAN_CRAFT, 5, 1), sim("agent.canCraft", "[5,1]"));
        assertEquals(new RobotCommand(RobotCommand.Op.STONECUT, 1, 64), sim("agent.stonecut", "[1,64]"));
        assertEquals(new RobotCommand(RobotCommand.Op.INTERACT, 0, 0), sim("agent.interact", "[0]"));
        assertEquals(new RobotCommand(RobotCommand.Op.IS_OPEN, 3, 0), sim("agent.isOpen", "[3]"));
        assertEquals(new RobotCommand(RobotCommand.Op.REDSTONE_POWER, 5, 0), sim("agent.redstonePower", "[5]"));
        assertEquals(new RobotCommand(RobotCommand.Op.MOVE_TO, 10, -64, 3), sim("agent.moveTo", "[10,-64,3]"));
        assertEquals(new RobotCommand(RobotCommand.Op.GO_TO_ROBOT, 0, 0, 0, "Miner"), sim("agent.goToRobot", "[\"Miner\"]"));
        assertEquals(new RobotCommand(RobotCommand.Op.GO_TO_ROBOT, 0, 0, 0, ""), sim("agent.goToRobot", "[\"\"]"));
        assertEquals(new RobotCommand(RobotCommand.Op.FOLLOW_PATH, 0, 0), sim("agent.followPath", "[0]"));
        assertEquals(new RobotCommand(RobotCommand.Op.FOLLOW_PATH, 0, RobotCommand.LEFT_TURN), sim("agent.followPath", "[0,2]"));
        assertEquals(new RobotCommand(RobotCommand.Op.MEASURE, 0, 32), sim("agent.measure", "[0,32]"));
        assertEquals(new RobotCommand(RobotCommand.Op.MEASURE_RUN, 0, 3, 10), sim("agent.measureRun", "[0,3,10]"));
        assertEquals(new RobotCommand(RobotCommand.Op.MEASURE_GAP, 2, 32), sim("agent.measureGap", "[2,32]"));
        assertEquals(new RobotCommand(RobotCommand.Op.GROUND_HEIGHT, 10, -3), sim("agent.groundHeight", "[10,-3]"));
        assertEquals(new RobotCommand(RobotCommand.Op.HEIGHT_PROFILE, 1, 16), sim("agent.heightProfile", "[1,16]"));
        assertEquals(new RobotCommand(RobotCommand.Op.IS_AREA_FREE, 1, 2, 3, 32, 33, -28, null), sim("agent.isAreaFree", "[1,2,3,32,33,-28]"));
        assertEquals(new RobotCommand(RobotCommand.Op.COUNT_SOLID, 0, 64, 0, 4, 60, 4, null), sim("agent.countSolid", "[0,64,0,4,60,4]"));
        assertEquals(new RobotCommand(RobotCommand.Op.FIND_BOUNDS, 0, 32), sim("agent.findBounds", "[32]"));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_HOME, 0, 0), sim("agent.setHome", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.GO_HOME, 0, 0), sim("agent.goHome", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.DETECT_ENTITY, 4, 0), sim("agent.detectEntity", "[0,4]"));
        assertEquals(new RobotCommand(RobotCommand.Op.COUNT_NEARBY, 3, 8), sim("agent.countNearby", "[3,8]"));
        assertEquals(new RobotCommand(RobotCommand.Op.RADAR, 2, 16), sim("agent.radar", "[2,16]"));
        assertEquals(new RobotCommand(RobotCommand.Op.DETECT_LIQUID, 5, 0), sim("agent.detectLiquid", "[5]"));
        assertEquals(new RobotCommand(RobotCommand.Op.IS_SAFE, 0, 0), sim("agent.isSafe", "[0]"));
        assertEquals(new RobotCommand(RobotCommand.Op.FREE_SLOTS, 0, 0), sim("agent.freeSlots", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.IS_INVENTORY_FULL, 0, 0), sim("agent.isInventoryFull", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.LIGHT_LEVEL, 0, 0), sim("agent.lightLevel", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.BIOME, 0, 0), sim("agent.biome", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.TEMPERATURE, 0, 0), sim("agent.temperature", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.TIME_OF_DAY, 0, 0), sim("gameplay.timeOfDay", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.IS_RAINING, 0, 0), sim("gameplay.isRaining", "[]"));
        assertEquals(new RobotCommand(RobotCommand.Op.SIGNAL_SEND, 0, 0, 0, "lamp-red"), sim("signals.send", "[\"lamp-red\"]"));
        assertEquals(Result.Code.BAD_ARGS, assertThrows(RobotException.class, () -> sim("signals.send", "[\"lamp red\"]")).code);
        assertEquals(Result.Code.BAD_ARGS, assertThrows(RobotException.class, () -> sim("signals.send", "[7]")).code);
        int stone = codeling.robot.BlockIds.byName("stone");
        assertEquals(new RobotCommand(RobotCommand.Op.FILL, 0, 64, 0, 4, 60, 4, stone, 3, null), sim("blocks.fill", "[" + stone + ",0,64,0,4,60,4,3]"));
        assertEquals(new RobotCommand(RobotCommand.Op.LINE, 1, 2, 3, 4, 5, 6, 0, 0, null), sim("shapes.line", "[0,1,2,3,4,5,6]"));
        assertEquals(new RobotCommand(RobotCommand.Op.CIRCLE, 1, 2, 3, 5, 1, 0, stone, 1, null), sim("shapes.circle", "[" + stone + ",1,2,3,5,1,1]"));
        assertEquals(new RobotCommand(RobotCommand.Op.SPHERE, 1, 2, 3, 5, 0, 0, stone, 2, null), sim("shapes.sphere", "[" + stone + ",1,2,3,5,2]"));
        assertEquals(new RobotCommand(RobotCommand.Op.MAZE, 1, 2, 3, 15, 9, 42, stone, stone, null), sim("shapes.maze", "[1,2,3,15,9," + stone + "," + stone + ",42]"));
        // Hidden AgentAssist members still compile in JS; the server answers BAD_ARGS.
        assertEquals(Result.Code.BAD_ARGS, assertThrows(RobotException.class, () -> sim("agent.setAssist", "[2,true]")).code);
    }
}
