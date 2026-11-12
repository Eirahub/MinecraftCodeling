package codeling.net;

import codeling.robot.BlockIds;
import codeling.robot.Result;
import codeling.robot.RobotCommand;
import codeling.robot.RobotException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Protocol messages (architecture section 3): decoding untrusted JSON and encoding replies.
 * Plain Java (Gson only) so it is unit-testable without Minecraft. Shared by the relay and the server.
 * Version 2 (issue #141): {@code cmd} and {@code control} may name one of the player's robots in {@code robot}; without
 * it (or with "") they are for the default robot, as in version 1. An editor that still speaks version 1 gets the
 * VERSION error and is asked to reload.
 */
public final class Protocol {
    public static final int VERSION = 2;
    // shortcut: 8 KiB proposal from architecture section 7, raise when an op needs bigger arguments.
    public static final int MAX_MESSAGE_BYTES = 8 * 1024;
    // shortcut: 10 s proposal (section 3), counted in server ticks (20 per second) so a paused singleplayer
    // game never times out a run; make it a server config value in step 4.
    public static final int HEARTBEAT_TIMEOUT_TICKS = 200;
    public static final int HEARTBEAT_TIMEOUT_MS = HEARTBEAT_TIMEOUT_TICKS * 50;

    private Protocol() {
    }

    /** A validated command: {@code id} is the per-run counter the reply must carry; {@code robot} is "" for the default robot. */
    public record Cmd(long id, long run, RobotCommand command, String robot) {
    }

    /** Parses one message and checks size, shape, protocol version and type field. */
    public static JsonObject parse(String json) throws RobotException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            throw new RobotException(Result.Code.BAD_ARGS, "message is larger than " + MAX_MESSAGE_BYTES + " bytes");
        }
        JsonObject msg;
        try {
            msg = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new RobotException(Result.Code.BAD_ARGS, "message is not a JSON object");
        }
        if (!(msg.get("v") instanceof JsonPrimitive v) || !v.isNumber() || v.getAsDouble() != VERSION) {
            throw new RobotException(Result.Code.VERSION, "protocol version " + VERSION + " expected");
        }
        string(msg, "t");
        return msg;
    }

    /** The message's {@code id} if it has a whole-number one, else null (for error replies). */
    public static Long id(JsonObject msg) {
        try {
            return number(msg, "id");
        } catch (RobotException e) {
            return null;
        }
    }

    public static String type(JsonObject msg) {
        return msg.get("t").getAsString();
    }

    /** The {@code run} field, or null if absent. */
    public static Long run(JsonObject msg) throws RobotException {
        return msg.has("run") ? number(msg, "run") : null;
    }

    /** Decodes a {@code cmd} and validates its op and arguments with {@link RobotCommand#parse}. */
    public static Cmd cmd(JsonObject msg) throws RobotException {
        long id = number(msg, "id");
        Long run = run(msg);
        if (run == null) throw new RobotException(Result.Code.BAD_ARGS, "cmd needs a run id (send control start first)");
        if (!(msg.get("args") instanceof JsonArray array)) throw new RobotException(Result.Code.BAD_ARGS, "args must be an array");
        List<Object> args = new ArrayList<>();
        for (JsonElement e : array) {
            if (!(e instanceof JsonPrimitive p)) throw new RobotException(Result.Code.BAD_ARGS, "args must be numbers, booleans or strings");
            args.add(p.isNumber() ? (Object) p.getAsDouble() : p.isBoolean() ? (Object) p.getAsBoolean() : p.getAsString());
        }
        return new Cmd(id, run, RobotCommand.parse(string(msg, "op"), args), robot(msg));
    }

    /** At most this many blocks in a program's on block broken handlers (issue #89), as many as chat keywords. */
    public static final int MAX_BROKEN_BLOCKS = 16;

    /**
     * What a program listens for (issue #27): its onChat keywords and whether it has an onDied handler; issue #89 adds
     * the blocks of its onBlockBroken handlers ({@link BlockIds} codes) and whether it has onRedstonePowered and
     * onDayStart handlers.
     */
    public record Subscription(Set<String> chat, boolean died, Set<Integer> broken, boolean redstone, boolean dayStart) {
    }

    /**
     * Decodes {@code control subscribe}: {@code chat}, an array of onChat keywords (optional), and {@code died}, true
     * when the program has an onDied handler (optional). Each keyword is checked by {@link ChatCommands#keywordProblem}.
     * Issue #89, all optional so an older editor's message still fits: {@code broken}, an array of known block codes
     * (not air), and {@code redstone} and {@code dayStart}, true or false. Unknown fields are ignored, as an older
     * server ignores these.
     */
    public static Subscription subscription(JsonObject msg) throws RobotException {
        Set<String> chat = new LinkedHashSet<>();
        if (msg.has("chat")) {
            if (!(msg.get("chat") instanceof JsonArray array)) throw new RobotException(Result.Code.BAD_ARGS, "chat must be an array of keywords");
            for (JsonElement e : array) {
                if (!(e instanceof JsonPrimitive p) || !p.isString()) throw new RobotException(Result.Code.BAD_ARGS, "chat must be an array of keywords");
                String problem = ChatCommands.keywordProblem(p.getAsString());
                if (problem != null) throw new RobotException(Result.Code.BAD_ARGS, problem);
                chat.add(p.getAsString());
            }
            if (chat.size() > ChatCommands.MAX_KEYWORDS) {
                throw new RobotException(Result.Code.BAD_ARGS, "a program may listen for at most " + ChatCommands.MAX_KEYWORDS + " chat commands");
            }
        }
        Set<Integer> broken = new LinkedHashSet<>();
        if (msg.has("broken")) {
            if (!(msg.get("broken") instanceof JsonArray array)) throw new RobotException(Result.Code.BAD_ARGS, "broken must be an array of blocks");
            for (JsonElement e : array) {
                if (!(e instanceof JsonPrimitive p) || !p.isNumber() || p.getAsDouble() != (int) p.getAsDouble()
                        || p.getAsInt() == 0 || BlockIds.toModern(p.getAsInt()) == null) {
                    throw new RobotException(Result.Code.BAD_ARGS, "broken must be an array of blocks, and " + e + " is not one");
                }
                broken.add(p.getAsInt());
            }
            if (broken.size() > MAX_BROKEN_BLOCKS) {
                throw new RobotException(Result.Code.BAD_ARGS, "a program may listen for at most " + MAX_BROKEN_BLOCKS + " broken blocks");
            }
        }
        return new Subscription(Set.copyOf(chat), flag(msg, "died"), Set.copyOf(broken), flag(msg, "redstone"), flag(msg, "dayStart"));
    }

    private static boolean flag(JsonObject msg, String name) throws RobotException {
        if (!msg.has(name)) return false;
        if (!(msg.get(name) instanceof JsonPrimitive p) || !p.isBoolean()) throw new RobotException(Result.Code.BAD_ARGS, name + " must be true or false");
        return p.getAsBoolean();
    }

    /**
     * An {@code event} for a handler the program subscribed to: {@code chat} with [keyword, num1, num2, num3],
     * {@code playerDied} with [], and (issue #89) {@code blockBroken} with [block code], {@code redstonePowered} and
     * {@code dayStart} with [].
     */
    public static String event(String name, List<?> args) {
        JsonObject o = envelope("event");
        o.addProperty("name", name);
        o.add("args", new Gson().toJsonTree(args));
        return o.toString();
    }

    /** The {@code robot} field: which of the player's robots, "" (or absent) for the default robot. */
    public static String robot(JsonObject msg) throws RobotException {
        if (!msg.has("robot")) return "";
        String name = string(msg, "robot");
        String problem = name.isEmpty() ? null : RobotCommand.nameProblem(name);
        if (problem != null) throw new RobotException(Result.Code.BAD_ARGS, problem);
        return name;
    }

    /** The {@code op} of a {@code control} message. */
    public static String controlOp(JsonObject msg) throws RobotException {
        String op = string(msg, "op");
        return switch (op) {
            case "start", "stop", "pause", "resume", "heartbeat", "subscribe" -> op;
            default -> throw new RobotException(Result.Code.BAD_ARGS, "unknown control op " + op);
        };
    }

    public static JsonObject envelope(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("t", type);
        return o;
    }

    /** {@code result} for an ok result, {@code error} otherwise. */
    public static String reply(long id, Result result) {
        if (!result.ok()) return error(id, result.code(), result.message());
        JsonObject o = envelope("result");
        o.addProperty("id", id);
        o.addProperty("ok", true);
        Object value = result.value();
        if (value instanceof Boolean b) o.addProperty("value", b);
        else if (value instanceof Number n) o.addProperty("value", n);
        else if (value instanceof List<?> list) o.add("value", new Gson().toJsonTree(list)); // getPosition: [x, y, z]
        else if (value != null) o.addProperty("value", value.toString());
        if (result.message() != null) o.addProperty("message", result.message());
        return o.toString();
    }

    public static String error(Long id, Result.Code code, String message) {
        JsonObject o = envelope("error");
        if (id != null) o.addProperty("id", id);
        o.addProperty("code", code.name());
        o.addProperty("message", message);
        return o.toString();
    }

    /** A {@code control} message; {@code message} (may be null) tells the player why. */
    public static String control(String op, long run, String message) {
        JsonObject o = envelope("control");
        o.addProperty("op", op);
        o.addProperty("run", run);
        if (message != null) o.addProperty("message", message);
        return o.toString();
    }

    private static String string(JsonObject msg, String field) throws RobotException {
        if (msg.get(field) instanceof JsonPrimitive p && p.isString()) return p.getAsString();
        throw new RobotException(Result.Code.BAD_ARGS, field + " must be a string");
    }

    private static long number(JsonObject msg, String field) throws RobotException {
        if (msg.get(field) instanceof JsonPrimitive p && p.isNumber()) {
            double d = p.getAsDouble();
            if (d == Math.rint(d) && Math.abs(d) < 0x1p53) return (long) d;
        }
        throw new RobotException(Result.Code.BAD_ARGS, field + " must be a whole number");
    }
}
