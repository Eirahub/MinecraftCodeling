package codeling.net;

import codeling.Codeling;
import codeling.McCompat;
import codeling.robot.Result;
import codeling.robot.RobotCommand;
import codeling.robot.RobotException;
import codeling.robot.RobotManager;
import codeling.robot.RobotQueue;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The player API (issue #26): calls about the program's owner, never about another player. They need no robot, so
 * they skip the robot queues, but they get the same pace and cap: one call per owner per tick, at most
 * {@link RobotQueue#MAX_LENGTH} waiting, else {@code RATE_LIMITED}. Game mode does not matter: the calls only read
 * the owner, tell the owner something, or move the owner as /tp would, so spectator and adventure owners may use them
 * all (teleport still needs the rights below). Server thread only.
 */
final class PlayerApi {
    private static final Map<UUID, ArrayDeque<Runnable>> WAITING = new HashMap<>();

    private PlayerApi() {
    }

    /** Queues a player call from {@code player}'s browser; false when {@code cmd} is not one. */
    static boolean submit(ServerPlayer player, Protocol.Cmd cmd, Consumer<String> out) throws RobotException {
        RobotCommand c = cmd.command();
        if (c.op().compareTo(RobotCommand.Op.PLAYER_SAY) < 0 || c.op().compareTo(RobotCommand.Op.PLAYER_TELEPORT) > 0) return false;
        UUID owner = player.getUUID();
        MinecraftServer server = player.level().getServer();
        if (cmd.run() != RobotManager.queue(owner).run()) throw new RobotException(Result.Code.STOPPED, "run " + cmd.run() + " is no longer active");
        ArrayDeque<Runnable> waiting = WAITING.computeIfAbsent(owner, o -> new ArrayDeque<>());
        if (waiting.size() >= RobotQueue.MAX_LENGTH) throw new RobotException(Result.Code.RATE_LIMITED, "too many player calls waiting (" + RobotQueue.MAX_LENGTH + ")");
        waiting.add(() -> {
            Result result;
            ServerPlayer now = server.getPlayerList().getPlayer(owner);
            if (cmd.run() != RobotManager.queue(owner).run()) result = Result.error(Result.Code.STOPPED, "run " + cmd.run() + " is no longer active");
            else if (now == null || now.hasDisconnected()) result = Result.error(Result.Code.UNLOADED, "you are not in the game");
            else result = run(now, c);
            out.accept(Protocol.reply(cmd.id(), result));
        });
        return true;
    }

    static void tick() {
        WAITING.values().removeIf(waiting -> {
            waiting.poll().run();
            return waiting.isEmpty();
        });
    }

    static void playerLeft(UUID owner) {
        WAITING.remove(owner);
    }

    static void serverStopped() {
        WAITING.clear();
    }

    private static Result run(ServerPlayer player, RobotCommand c) {
        return switch (c.op()) {
            case PLAYER_SAY -> {
                // Literal text to the owner only: no formatting, click or hover events (RobotCommand.sayText strips the rest).
                player.sendSystemMessage(Component.literal(c.text()));
                yield Result.ok(null);
            }
            case PLAYER_POSITION -> {
                BlockPos pos = player.blockPosition();
                yield Result.ok(List.of(pos.getX(), pos.getY(), pos.getZ()));
            }
            // MakeCode's convention, as agent.getOrientation: south 0, west 90, north -180 (or 180), east -90; one decimal.
            case PLAYER_ORIENTATION -> Result.ok(Math.round(Mth.wrapDegrees(player.getYRot()) * 10) / 10.0);
            case PLAYER_NAME -> Result.ok(player.getGameProfile().name());
            case PLAYER_TELEPORT -> teleport(player, new BlockPos(c.a(), c.b(), c.c()));
            default -> throw new IllegalStateException("not a player call: " + c.op());
        };
    }

    // As /tp would allow it: the host of a singleplayer world or an op (game master), in the owner's own dimension,
    // inside the world border and the build height. To the middle of the block, keeping the owner's facing.
    private static Result teleport(ServerPlayer player, BlockPos pos) {
        String name = player.getGameProfile().name();
        if (!McCompat.isSingleplayerOwner(player) && !McCompat.isGamemaster(player)) {
            Codeling.LOG.info("player: {}'s program may not teleport them: not an operator.", name);
            return Result.error(Result.Code.NO_PERMISSION, "player.teleport needs singleplayer or operator rights");
        }
        ServerLevel level = McCompat.level(player);
        if (!Level.isInSpawnableBounds(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return Result.error(Result.Code.OUT_OF_RANGE, "x=" + pos.getX() + " y=" + pos.getY() + " z=" + pos.getZ() + " is outside the world border or the build height");
        }
        player.stopRiding();
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Codeling.LOG.info("player: {}'s program teleported them to x={} y={} z={} in {}.", name, pos.getX(), pos.getY(), pos.getZ(), McCompat.dimension(level).getPath());
        return Result.ok(null);
    }
}
