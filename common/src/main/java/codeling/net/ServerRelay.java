package codeling.net;

import codeling.platform.Platform;
import codeling.Codeling;
import codeling.robot.Positions;
import codeling.robot.Result;
import codeling.robot.RobotCamera;
import codeling.robot.RobotCommand;
import codeling.robot.RobotException;
import codeling.robot.RobotLinks;
import codeling.robot.RobotManager;
import codeling.robot.RobotQueue;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server end of the relay: handles protocol messages from a player's browser (through their client) for that
 * player's own robots only (a robot name is looked up among the sender's robots, never anyone else's). The payload handler runs on the server thread, as does everything here.
 * Bad messages get an {@code error} reply; the game connection is never closed.
 */
public final class ServerRelay {
    private static final Sessions SESSIONS = new Sessions(Protocol.HEARTBEAT_TIMEOUT_TICKS);

    private ServerRelay() {
    }

    /** A {@code codeling:to_server} payload from {@code player}; the loader calls this on the server thread. */
    public static void receive(ServerPlayer player, String json) {
        MinecraftServer server = player.level().getServer();
        UUID owner = player.getUUID();
        handle(player, json, reply -> send(server, owner, reply));
    }

    public static void playerLeft(ServerPlayer player) {
        UUID owner = player.getUUID();
        SESSIONS.remove(owner);
        PlayerApi.playerLeft(owner);
        RobotManager.stop(owner, "the player left the game");
    }

    public static void serverStopped() {
        SESSIONS.clear();
        PlayerApi.serverStopped();
    }

    // Looks the player up by UUID on every send, so a respawned player (new ServerPlayer object) still gets replies.
    private static void send(MinecraftServer server, UUID owner, String json) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null && !player.hasDisconnected()) Platform.INSTANCE.send(player, new RelayPayloads.ToClient(json));
    }

    /** Handles one message from {@code player}'s browser; {@code out} sends a reply to that browser. */
    public static void handle(ServerPlayer player, String json, Consumer<String> out) {
        UUID owner = player.getUUID();
        long now = player.level().getServer().getTickCount();
        Long id = null;
        try {
            JsonObject msg = Protocol.parse(json);
            id = Protocol.id(msg);
            switch (Protocol.type(msg)) {
                case "control" -> control(owner, player.getGameProfile().name(), msg, now, out);
                case "cmd" -> {
                    Protocol.Cmd cmd = Protocol.cmd(msg);
                    SESSIONS.touch(owner, cmd.run(), now);
                    RobotLinks.Key key = new RobotLinks.Key(owner, cmd.robot());
                    if (toWorld(player, cmd, out) || camera(player, key, cmd, out) || PlayerApi.submit(player, cmd, out)) return;
                    RobotManager.submit(key, cmd.run(), cmd.command(), result -> out.accept(Protocol.reply(cmd.id(), result)));
                }
                default -> throw new RobotException(Result.Code.BAD_ARGS, "unknown message type " + Protocol.type(msg));
            }
        } catch (RobotException e) {
            out.accept(Protocol.error(id, e.code, e.getMessage()));
        }
    }

    /**
     * positions.toWorld resolves against the owner at once instead of going through the robot's queue: it needs the
     * player, not the robot, and the sim sends one command at a time, so nothing queued can come before it.
     * Feet block and horizontal facing (pitch ignored), so local positions stay on the block grid.
     */
    private static boolean toWorld(ServerPlayer player, Protocol.Cmd cmd, Consumer<String> out) throws RobotException {
        RobotCommand c = cmd.command();
        if (c.op() != RobotCommand.Op.RELATIVE_TO_WORLD && c.op() != RobotCommand.Op.LOCAL_TO_WORLD) return false;
        if (cmd.run() != RobotManager.queue(player.getUUID()).run()) throw new RobotException(Result.Code.STOPPED, "run " + cmd.run() + " is no longer active");
        BlockPos feet = player.blockPosition();
        Direction facing = player.getDirection();
        int kind = c.op() == RobotCommand.Op.LOCAL_TO_WORLD ? Positions.LOCAL : Positions.RELATIVE;
        java.util.List<Integer> world = Positions.toWorld(kind, c.a(), c.b(), c.c(), feet.getX(), feet.getY(), feet.getZ(), facing.getStepX(), facing.getStepZ());
        Codeling.LOG.info("pos: {}'s {} position ({}, {}, {}) is {} of the player's feet block x={} y={} z={} (looking {}), which is x={} y={} z={} in the world.",
                player.getGameProfile().name(), kind == Positions.LOCAL ? "local (^)" : "relative (~)", c.a(), c.b(), c.c(), Positions.describe(kind, c.a(), c.b(), c.c()),
                feet.getX(), feet.getY(), feet.getZ(), facing.getName(), world.get(0), world.get(1), world.get(2));
        out.accept(Protocol.reply(cmd.id(), Result.ok(world)));
        return true;
    }

    // agent.camera (issue #177) also needs the player, not the robot; RobotCamera applies the same rules as /robot camera.
    private static boolean camera(ServerPlayer player, RobotLinks.Key key, Protocol.Cmd cmd, Consumer<String> out) throws RobotException {
        if (cmd.command().op() != RobotCommand.Op.CAMERA) return false;
        if (cmd.run() != RobotManager.queue(player.getUUID()).run()) throw new RobotException(Result.Code.STOPPED, "run " + cmd.run() + " is no longer active");
        String refusal = RobotCamera.set(player, RobotManager.robot(key), cmd.command().a());
        out.accept(Protocol.reply(cmd.id(), refusal == null ? Result.ok(null) : Result.error(Result.Code.NO_PERMISSION, refusal)));
        return true;
    }

    // The owner's robots share one run (issue #141): start, heartbeat and a stop without a robot are for all of them;
    // stop, pause and resume with a robot name are for that robot only (a stop then only empties its queue).
    private static void control(UUID owner, String name, JsonObject msg, long now, Consumer<String> out) throws RobotException {
        String op = Protocol.controlOp(msg);
        RobotQueue queue = RobotManager.queue(owner);
        String robot = Protocol.robot(msg);
        if (op.equals("start")) {
            long run = RobotManager.start(owner);
            RobotManager.runStarted(owner, name);
            SESSIONS.start(owner, run, now, out);
            out.accept(Protocol.control("start", run, null));
            return;
        }
        // shortcut: no events until step 3 (chat, playerDied), so subscribe is accepted and ignored.
        if (op.equals("subscribe")) return;
        Long run = Protocol.run(msg);
        boolean current = run != null && run == queue.run();
        switch (op) {
            case "stop" -> {
                // Only the run the message names: a closing old tab must not stop the run a newer one started.
                if (current && !robot.isEmpty()) {
                    RobotManager.queue(new RobotLinks.Key(owner, robot)).clear(Result.Code.STOPPED, "stopped");
                } else if (current) {
                    RobotManager.stop(owner, "the editor sent stop (Stop button, or the editor was closed)");
                    SESSIONS.remove(owner);
                }
            }
            case "heartbeat" -> {
                if (!current || !SESSIONS.touch(owner, run, now)) {
                    out.accept(Protocol.control("stop", run == null ? -1 : run, "The robot's run was stopped in the game."));
                }
            }
            default -> { // pause, resume
                if (!current) throw new RobotException(Result.Code.STOPPED, "run " + run + " is no longer active");
                SESSIONS.touch(owner, run, now);
                for (RobotQueue q : robot.isEmpty() ? RobotManager.queues(owner) : java.util.List.of(RobotManager.queue(new RobotLinks.Key(owner, robot)))) {
                    if (op.equals("pause")) q.pause();
                    else q.resume();
                }
            }
        }
    }

    public static void tick(MinecraftServer server) {
        PlayerApi.tick();
        for (Map.Entry<UUID, Sessions.Session> e : SESSIONS.expire(server.getTickCount())) {
            if (RobotManager.queue(e.getKey()).run() == e.getValue().run) RobotManager.stop(e.getKey(), "no heartbeat from the editor (it was closed or lost contact)");
            e.getValue().out.accept(Protocol.control("stop", e.getValue().run,
                    "No heartbeat from the editor for " + Protocol.HEARTBEAT_TIMEOUT_MS / 1000 + " s, so the game stopped the robot."));
        }
    }
}
