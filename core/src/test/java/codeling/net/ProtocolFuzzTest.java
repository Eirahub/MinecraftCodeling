package codeling.net;

import codeling.robot.RobotException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fuzz test of the protocol decoder (issue #139). The relay is a trust boundary: whatever a browser (or a modified
 * client) sends, decoding must end in a value or a {@link RobotException} with a code, never another exception or a
 * hang. Fixed seeds, so a failure is reproducible; the failing input is in the assertion message.
 */
class ProtocolFuzzTest {
    private static final int RUNS = 20_000;
    /** The JSON target/sim/api.ts sends for every op (as in ProtocolTest.decodesEveryOpTheSimSends), mutated below. */
    private static final List<String> VALID = List.of(
            cmd("agent.move", "[0,3]"), cmd("agent.turn", "[3]"), cmd("agent.place", "[4]"), cmd("agent.destroy", "[0]"),
            cmd("agent.setAssist", "[0,true]"), cmd("agent.setGravity", "[true]"), cmd("agent.detect", "[1,5]"),
            cmd("agent.inspect", "[0,0]"), cmd("agent.setSlot", "[2]"), cmd("agent.getItemCount", "[1]"),
            cmd("agent.getItemSpace", "[27]"), cmd("agent.setItem", "[65537,64,1]"), cmd("agent.collectAll", "[]"),
            cmd("agent.drop", "[1,3,5]"), cmd("agent.dropAll", "[5]"), cmd("agent.transfer", "[10,1,2]"),
            cmd("agent.getPosition", "[]"), cmd("agent.getOrientation", "[]"), cmd("positions.toWorld", "[1,0,-1,2]"),
            cmd("agent.findNearest", "[17,16]"), cmd("agent.findNearestCategory", "[2,32]"), cmd("agent.say", "[\"Hello!\"]"),
            cmd("agent.storeItems", "[0,0]"), cmd("agent.takeItems", "[4,54,64]"), cmd("agent.takeItem", "[1,4,10]"),
            cmd("agent.containerCount", "[0,1]"), cmd("agent.containerSize", "[5]"), cmd("agent.sortContainer", "[0]"),
            cmd("agent.craft", "[5,4]"), cmd("agent.canCraft", "[5,1]"), cmd("agent.stonecut", "[1,64]"),
            cmd("agent.interact", "[0]"), cmd("agent.isOpen", "[3]"), cmd("agent.redstonePower", "[5]"),
            cmd("agent.moveTo", "[10,-64,3]"), cmd("agent.goToRobot", "[\"Miner\"]"), cmd("agent.measure", "[0,32]"), cmd("agent.measureRun", "[0,3,10]"),
            cmd("agent.measureGap", "[2,32]"), cmd("agent.groundHeight", "[10,-3]"), cmd("agent.heightProfile", "[1,16]"),
            cmd("agent.isAreaFree", "[1,2,3,32,33,-28]"), cmd("agent.countSolid", "[0,64,0,4,60,4]"), cmd("agent.findBounds", "[32]"),
            cmd("agent.setHome", "[]"), cmd("agent.goHome", "[]"), cmd("agent.detectEntity", "[0,4]"), cmd("agent.countNearby", "[3,8]"),
            cmd("agent.radar", "[2,16]"), cmd("agent.detectLiquid", "[5]"), cmd("agent.isSafe", "[0]"), cmd("agent.freeSlots", "[]"),
            cmd("agent.isInventoryFull", "[]"), cmd("agent.lightLevel", "[]"), cmd("agent.biome", "[]"), cmd("agent.temperature", "[]"),
            cmd("gameplay.timeOfDay", "[]"), cmd("gameplay.isRaining", "[]"),
            cmd("blocks.fill", "[1,0,64,0,4,60,4,3]"), cmd("shapes.line", "[0,1,2,3,4,5,6]"), cmd("shapes.circle", "[1,1,2,3,5,1,1]"),
            cmd("shapes.sphere", "[1,1,2,3,5,2]"), cmd("agent.camera", "[2]"), cmd("agent.followPath", "[0]"), cmd("agent.followPath", "[0,3]"),
            cmd("shapes.maze", "[1,2,3,15,9,1,13,42]"),
            cmd("player.say", "[\"Hi!\"]"), cmd("player.position", "[]"), cmd("player.getOrientation", "[]"), cmd("player.name", "[]"),
            cmd("player.teleport", "[1,100,-2]"),
            "{\"v\":2,\"t\":\"control\",\"op\":\"start\"}", "{\"v\":2,\"t\":\"control\",\"op\":\"heartbeat\",\"run\":2}",
            "{\"v\":2,\"t\":\"control\",\"op\":\"stop\",\"run\":2}", "{\"v\":2,\"t\":\"control\",\"op\":\"pause\",\"run\":2}");
    /** The first COMMANDS entries of VALID are cmd messages, the rest control messages. */
    private static final int COMMANDS = (int) VALID.stream().filter(v -> v.contains("\"t\":\"cmd\"")).count();
    private static final String[] NUMBERS = {
            "0", "-0", "1", "-1", "0.5", "1e400", "-1e400", "1e-400", "2147483647", "2147483648", "-2147483649",
            "9007199254740993", "123456789012345678901234567890", "1" + "0".repeat(400), "1E+2", "-0.0e-0", "NaN",
            "Infinity", "-Infinity", "0x10", "01", "1.", ".5", "+1"};
    private static final String[] STRINGS = {
            "", "agent.move", "agent.fly", "AGENT.MOVE", "agent.move ", "positions.toWorld", "control", "cmd", "hello",
            "\\u0000", "\\ud800", "\\udc00\\ud800", "æøå", "🤖", "\\\"", "__proto__", "constructor"};
    private static final String PUNCTUATION = "{}[],:\"\0-e.ntf ";
    private static final String[] FIELDS = {"v", "t", "id", "run", "op", "args", "token", "x", ""};

    private static String cmd(String op, String args) {
        return "{\"t\":\"cmd\",\"id\":1,\"run\":4,\"op\":\"" + op + "\",\"args\":" + args + ",\"v\":2}";
    }

    /** Runs the whole decode path ServerRelay.handle and the relay take, and fails on anything but RobotException. */
    private static void decode(String json) {
        try {
            JsonObject msg = Protocol.parse(json);
            Protocol.id(msg);
            switch (Protocol.type(msg)) {
                case "cmd" -> assertNotNull(Protocol.cmd(msg).command());
                case "control" -> {
                    Protocol.controlOp(msg);
                    Protocol.run(msg);
                }
                default -> {
                }
            }
        } catch (RobotException e) {
            assertNotNull(e.code, json);
            assertNotNull(e.getMessage(), json);
            // The error reply must encode, whatever the message holds.
            assertNotNull(JsonParser.parseString(Protocol.error(Protocol.MAX_MESSAGE_BYTES * 2L, e.code, e.getMessage())));
        } catch (Throwable t) {
            fail("decoder threw " + t + " for: " + abbreviate(json), t);
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 300 ? s : s.substring(0, 300) + "... (" + s.length() + " chars)";
    }

    @Test
    void randomJsonValues() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            Random random = new Random(139);
            for (int i = 0; i < RUNS; i++) decode(object(random, 0));
        });
    }

    @Test
    void mutatedSimMessages() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            Random random = new Random(1390);
            for (int i = 0; i < RUNS; i++) decode(mutate(random, VALID.get(random.nextInt(VALID.size()))));
        });
    }

    /** Every op with every argument replaced by values of the wrong type or size, one at a time and all at once. */
    @Test
    void wrongArgumentTypes() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            Random random = new Random(13900);
            for (int i = 0; i < RUNS; i++) {
                String op = VALID.get(random.nextInt(COMMANDS)).replaceAll(".*\"op\":\"([^\"]*)\".*", "$1");
                StringBuilder args = new StringBuilder("[");
                for (int n = random.nextInt(6), k = 0; k < n; k++) args.append(k == 0 ? "" : ",").append(value(random, 2));
                decode(cmd(op, args.append(']').toString()));
            }
        });
    }

    @Test
    void extremes() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            for (int depth : new int[]{100, 255, 256, 1000, 4000}) {
                decode("[".repeat(depth) + "]".repeat(depth));
                decode("{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":1,\"op\":\"agent.move\",\"args\":" + "[".repeat(depth) + "]".repeat(depth) + "}");
                decode("{\"a\":".repeat(depth) + "1" + "}".repeat(depth));
                decode("[".repeat(depth)); // unclosed
            }
            int big = Protocol.MAX_MESSAGE_BYTES;
            decode(cmd("agent.say", "[\"" + "x".repeat(big - 80) + "\"]"));
            decode(cmd("agent.say", "[\"" + "🤖".repeat((big - 80) / 4) + "\"]"));
            decode(cmd("agent.say", "[\"" + "\\u0007".repeat((big - 80) / 6) + "\"]"));
            decode(cmd("agent.say", "[\"" + "x".repeat(big) + "\"]"));
            decode(cmd("agent.move", "[0," + "9".repeat(big - 80) + "]"));
            decode(cmd("agent.move", "[0,1e" + "9".repeat(big - 80) + "]"));
            decode(cmd("agent.move", "[0,0." + "0".repeat(big - 80) + "1]"));
            decode(cmd("x".repeat(big - 80), "[]"));
            decode("{\"v\":2,\"t\":\"cmd\",\"id\":" + "1".repeat(big - 80) + "}");
            decode("{" + "\"k\":1,".repeat((big - 40) / 6) + "\"v\":2,\"t\":\"x\"}");
            decode("{\"v\":2,\"v\":1,\"t\":\"cmd\",\"t\":3}"); // duplicate keys
            decode(cmd("agent.move", "[0," + "1,".repeat((big - 80) / 2) + "1]"));
            decode("﻿" + VALID.get(0));
            decode("");
            decode("   ");
            decode("null");
            decode(null);
            decode("{v:1,t:cmd,id:1,run:1,op:agent.move,args:[0,3]}"); // Gson's lenient syntax
            decode("{'v':1,'t':'cmd','id':1,'run':1,'op':'agent.move','args':[0,3]} trailing");
            decode("{\"v\":2,\"t\":\"cmd\"} /* comment */");
        });
    }

    private static String mutate(Random random, String json) {
        StringBuilder s = new StringBuilder(json);
        for (int n = 1 + random.nextInt(4), k = 0; k < n && !s.isEmpty(); k++) {
            int at = random.nextInt(s.length());
            switch (random.nextInt(7)) {
                case 0 -> s.deleteCharAt(at);
                case 1 -> s.insert(at, PUNCTUATION.charAt(random.nextInt(PUNCTUATION.length())));
                case 2 -> s.setCharAt(at, (char) random.nextInt(0x3000));
                case 3 -> s.setLength(at); // truncated
                case 4 -> s.insert(at, value(random, 3));
                case 5 -> s.replace(0, s.length(), s.toString().replaceFirst("\\d+", NUMBERS[random.nextInt(NUMBERS.length)]));
                default -> s.replace(0, s.length(), s.toString().replaceFirst("\"(\\w+)\":", "\"" + FIELDS[random.nextInt(FIELDS.length)] + "\":"));
            }
        }
        return s.toString();
    }

    private static String object(Random random, int depth) {
        StringBuilder s = new StringBuilder("{");
        // Mostly the real fields with random values, so the decoder gets past the version and type checks.
        if (random.nextInt(4) > 0) s.append("\"v\":").append(random.nextInt(4) > 0 ? "1" : value(random, depth + 1)).append(',');
        if (random.nextInt(4) > 0) s.append("\"t\":").append(random.nextBoolean() ? "\"cmd\"" : random.nextBoolean() ? "\"control\"" : value(random, depth + 1)).append(',');
        for (String field : FIELDS) if (random.nextInt(3) == 0) s.append('"').append(field).append("\":").append(value(random, depth + 1)).append(',');
        if (random.nextBoolean()) s.append("\"op\":\"").append(random.nextBoolean() ? STRINGS[random.nextInt(STRINGS.length)]
                : VALID.get(random.nextInt(COMMANDS)).replaceAll(".*\"op\":\"([^\"]*)\".*", "$1")).append("\",");
        if (random.nextBoolean()) s.append("\"args\":").append(array(random, depth + 1)).append(',');
        if (s.charAt(s.length() - 1) == ',') s.setLength(s.length() - 1);
        return s.append('}').toString();
    }

    private static String array(Random random, int depth) {
        StringBuilder s = new StringBuilder("[");
        for (int n = random.nextInt(5), k = 0; k < n; k++) s.append(k == 0 ? "" : ",").append(value(random, depth + 1));
        return s.append(']').toString();
    }

    private static String value(Random random, int depth) {
        int pick = random.nextInt(depth > 4 ? 6 : 9);
        return switch (pick) {
            case 0 -> NUMBERS[random.nextInt(NUMBERS.length)];
            case 1 -> Integer.toString(random.nextInt(200) - 100);
            case 2 -> "\"" + STRINGS[random.nextInt(STRINGS.length)] + "\"";
            case 3 -> random.nextBoolean() ? "true" : "false";
            case 4 -> "null";
            case 5 -> "\"" + "y".repeat(random.nextInt(3000)) + "\"";
            case 6 -> array(random, depth);
            case 7 -> object(random, depth);
            default -> "[".repeat(300) + "]".repeat(300);
        };
    }
}
