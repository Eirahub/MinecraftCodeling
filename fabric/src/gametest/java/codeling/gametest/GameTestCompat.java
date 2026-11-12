package codeling.gametest;

import codeling.Codeling;
import codeling.robot.RobotCamera;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
//? if >=1.21.11 {
import net.minecraft.server.permissions.LevelBasedPermissionSet;
//?} else {
/*import net.fabricmc.fabric.api.gamerule.v1.rule.EnumRule;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.GameRules;
*///?}
//? if >=1.21.6 {
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//?}

/**
 * Test-side calls whose Minecraft API differs between the versions the GameTests run on (issue #189), next to
 * {@link codeling.McCompat} on the mod side. The 26.x body is what src/ shows.
 */
final class GameTestCompat {
    private GameTestCompat() {
    }

    /** Makes the player an op at game master level (the GameTest server's own op level is 0). */
    static void op(ServerPlayer player) {
        var players = player.level().getServer().getPlayerList();
        //? if >=1.21.11 {
        players.op(player.nameAndId(), java.util.Optional.of(LevelBasedPermissionSet.GAMEMASTER), java.util.Optional.empty());
        //?} else if >=1.21.9 {
        /*players.getOps().add(new ServerOpListEntry(player.nameAndId(), 2, false));
        players.sendPlayerPermissionLevel(player);
        *///?} else {
        /*players.getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
        players.sendPlayerPermissionLevel(player);
        *///?}
    }

    static void deop(ServerPlayer player) {
        var players = player.level().getServer().getPlayerList();
        // The op list keys on NameAndId from 1.21.9.
        //? if >=1.21.9 {
        players.deop(player.nameAndId());
        //?} else
        /*players.deop(player.getGameProfile());*/
    }

    static void setCamera(MinecraftServer server, RobotCamera.Mode mode) {
        //? if >=26.1 {
        server.getGameRules().set(Codeling.ROBOT_CAMERA.get(), mode, server);
        //?} else if >=1.21.11 {
        /*server.overworld().getGameRules().set(Codeling.ROBOT_CAMERA.get(), mode, server);
        *///?} else {
        /*@SuppressWarnings("unchecked")
        var key = (GameRules.Key<EnumRule<RobotCamera.Mode>>) Codeling.ROBOT_CAMERA.get();
        server.getGameRules().getRule(key).set(mode, server);
        *///?}
    }

    //? if <1.21.5 {
    /*// For the @BeforeBatch methods that stand in for test environments before 1.21.5.
    static void setFreeItems(MinecraftServer server, boolean value) {
        server.getGameRules().getRule(Codeling.FREE_ITEMS.get()).set(value, server);
    }

    static void setMaxRobots(MinecraftServer server, int value) {
        server.getGameRules().getRule(Codeling.MAX_ROBOTS.get()).set(value, server);
    }
    *///?}

    /** The entity's saved data, as a chunk save writes it. */
    static CompoundTag save(Entity entity, RegistryAccess registries) {
        //? if >=1.21.6 {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        entity.saveWithoutId(out);
        return out.buildResult();
        //?} else
        /*return entity.saveWithoutId(new CompoundTag());*/
    }

    static void load(Entity entity, CompoundTag tag, RegistryAccess registries) {
        //? if >=1.21.6 {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        //?} else
        /*entity.load(tag);*/
    }

    /** A right click on the entity with the item in the main hand. */
    static void interact(Entity entity, Player player) {
        //? if >=26.1 {
        entity.interact(player, InteractionHand.MAIN_HAND, entity.position());
        //?} else
        /*entity.interact(player, InteractionHand.MAIN_HAND);*/
    }
}
