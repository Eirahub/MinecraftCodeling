package codeling;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
//? if >=1.21.11 {
import net.minecraft.server.permissions.Permissions;
//?}
//? if >=26.3
import net.minecraft.util.Prediction;

/**
 * Small calls whose Minecraft API differs between the versions Codeling builds for (issue #189), so the robot code
 * reads the same on every version. Each body is a Stonecutter condition; the 26.x line is what src/ shows.
 */
public final class McCompat {
    private McCompat() {
    }

    /** Op level 2 (game master), what /robot list all and the robot camera's ops mode ask for. */
    public static boolean isGamemaster(ServerPlayer player) {
        //? if >=1.21.11 {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        //?} else
        /*return player.hasPermissions(2);*/
    }

    /** The host of a singleplayer world (also once it is opened to LAN); false on a dedicated server. */
    public static boolean isSingleplayerOwner(ServerPlayer player) {
        //? if >=1.21.9 {
        return player.level().getServer().isSingleplayerOwner(player.nameAndId());
        //?} else
        /*return player.level().getServer().isSingleplayerOwner(player.getGameProfile());*/
    }

    /** The player's level as a ServerLevel (26.x types ServerPlayer.level() so; 1.21.1 has serverLevel()). */
    public static ServerLevel level(ServerPlayer player) {
        //? if >=26.1 {
        return player.level();
        //?} else
        /*return player.serverLevel();*/
    }

    /** The block with this id, or air when the id is not a block in this Minecraft version. */
    public static Block block(Identifier id) {
        //? if >=1.21.2 {
        return BuiltInRegistries.BLOCK.getValue(id);
        //?} else
        /*return BuiltInRegistries.BLOCK.get(id);*/
    }

    /** The item with this id, or air when the id is not an item in this Minecraft version. */
    public static Item item(Identifier id) {
        //? if >=1.21.2 {
        return BuiltInRegistries.ITEM.getValue(id);
        //?} else
        /*return BuiltInRegistries.ITEM.get(id);*/
    }

    /** This version's id for an item id from ItemIds (26.3's names): before 1.21.9 the iron chain was minecraft:chain. */
    public static Identifier itemId(String modernId) {
        //? if <1.21.9
        /*if (modernId.equals("minecraft:iron_chain")) modernId = "minecraft:chain";*/
        return Identifier.parse(modernId);
    }

    /** The dimension's id, e.g. minecraft:overworld. */
    public static Identifier dimension(Level level) {
        //? if >=1.21.11 {
        return level.dimension().identifier();
        //?} else
        /*return level.dimension().location();*/
    }

    /** Whether the player can reach the entity, with {@code buffer} blocks of slack. */
    public static boolean inReach(Player player, Entity entity, double buffer) {
        //? if >=26.1 {
        return player.isWithinEntityInteractionRange(entity, buffer);
        //?} else
        /*return player.canInteractWithEntity(entity, buffer);*/
    }

    public static int chunkX(ChunkPos chunk) {
        //? if >=26.1 {
        return chunk.x();
        //?} else
        /*return chunk.x;*/
    }

    public static int chunkZ(ChunkPos chunk) {
        //? if >=26.1 {
        return chunk.z();
        //?} else
        /*return chunk.z;*/
    }

    /** Gives the stack back to the player (the server decides, the client is told), dropping what does not fit. */
    public static void giveBack(Player player, ItemStack stack) {
        //? if >=26.3 {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        //?} else
        /*player.getInventory().placeItemBackInInventory(stack);*/
    }
}
