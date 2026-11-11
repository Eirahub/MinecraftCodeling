package codeling;

import codeling.net.ServerRelay;
import codeling.platform.Platform;
import codeling.robot.ItemIds;
import codeling.robot.RobotCamera;
import codeling.robot.RobotEntity;
import codeling.robot.RobotManager;
import codeling.robot.RobotMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
//? if >=1.21.11 {
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
//?} else {
/*import net.minecraft.world.level.GameRules;
*///?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The loader-independent half of the mod (issue #186): registered content and the hooks each loader's entrypoint
 * calls from its own events. The values are suppliers so NeoForge can register them later; call get() in game only.
 */
public final class Codeling {
    public static final String MOD_ID = "codeling";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    private static final Platform PLATFORM = Platform.INSTANCE;

    private static final ResourceKey<EntityType<?>> ROBOT_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "robot"));
    // noSummon: a robot always needs an owner, so it is only created through /robot spawn. The name tag sits 1.7 up
    // (vanilla adds 0.5 to the attachment), clear of the 0.8 block body even seen from above, as from the follow camera.
    //? if >=1.21.2 {
    public static final Supplier<EntityType<RobotEntity>> ROBOT = PLATFORM.register(BuiltInRegistries.ENTITY_TYPE, ROBOT_KEY.identifier(),
            () -> EntityType.Builder.of(RobotEntity::new, MobCategory.MISC).noLootTable().noSummon().sized(0.8F, 0.8F).nameTagOffset(1.2F)
                    .clientTrackingRange(10).build(ROBOT_KEY));
    //?} else {
    /*// Before 1.21.2 there is no noLootTable (entity loot tables came later), and the type is built from the plain id.
    public static final Supplier<EntityType<RobotEntity>> ROBOT = PLATFORM.register(BuiltInRegistries.ENTITY_TYPE, ROBOT_KEY.location(),
            () -> EntityType.Builder.of(RobotEntity::new, MobCategory.MISC).noSummon().sized(0.8F, 0.8F).nameTagOffset(1.2F)
                    .clientTrackingRange(10).build(ROBOT_KEY.location().toString()));
    *///?}
    /** The robot's inventory screen (issue #67); the client registers RobotScreen for it. */
    public static final Supplier<MenuType<RobotMenu>> ROBOT_MENU = PLATFORM.register(BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(MOD_ID, "robot"), () -> new MenuType<>(RobotMenu::new, FeatureFlags.VANILLA_SET));

    //? if >=1.21.11 {
    /** Survival robots gather their own items; true lets every robot in the world get items free (setItem, building). */
    public static final Supplier<GameRule<Boolean>> FREE_ITEMS = PLATFORM.booleanRule(
            Identifier.fromNamespaceAndPath(MOD_ID, "free_items"), GameRuleCategory.MISC, false);
    /** Who may look through a robot's eyes (issue #175): off, owner (the owner and ops) or ops (ops only). */
    public static final Supplier<GameRule<RobotCamera.Mode>> ROBOT_CAMERA = PLATFORM.enumRule(
            Identifier.fromNamespaceAndPath(MOD_ID, "robot_camera"), GameRuleCategory.MISC, RobotCamera.Mode.owner);
    /**
     * How many robots each player may have (issue #140): the default robot counts as one, so 1 (the default) means no
     * named robots. Lowering it never removes robots; it only stops new ones.
     */
    public static final Supplier<GameRule<Integer>> MAX_ROBOTS = PLATFORM.intRule(
            Identifier.fromNamespaceAndPath(MOD_ID, "max_robots"), GameRuleCategory.MISC, 1, 1, 16);
    //?} else {
    /*// Before 1.21.11 game rules are GameRules keys named by a plain string; Codeling keeps the 26.x names
    // (/gamerule codeling:free_items true). The enum rule's value type is the loader's (Fabric's EnumRule, Codeling's
    // own value on NeoForge), so common reads it as its saved text (the constant name, as on 26.x).
    public static final Supplier<GameRules.Key<GameRules.BooleanValue>> FREE_ITEMS = PLATFORM.booleanRule(
            Identifier.fromNamespaceAndPath(MOD_ID, "free_items"), GameRules.Category.MISC, false);
    public static final Supplier<? extends GameRules.Key<?>> ROBOT_CAMERA = PLATFORM.enumRule(
            Identifier.fromNamespaceAndPath(MOD_ID, "robot_camera"), GameRules.Category.MISC, RobotCamera.Mode.owner);
    public static final Supplier<GameRules.Key<GameRules.IntegerValue>> MAX_ROBOTS = PLATFORM.intRule(
            Identifier.fromNamespaceAndPath(MOD_ID, "max_robots"), GameRules.Category.MISC, 1, 1, 16);
    *///?}

    /** The free_items game rule (issue #189: one read for every Minecraft version). */
    public static boolean freeItems(MinecraftServer server) {
        //? if >=26.1 {
        return server.getGameRules().get(FREE_ITEMS.get());
        //?} else if >=1.21.11 {
        /*return server.overworld().getGameRules().get(FREE_ITEMS.get());
        *///?} else {
        /*return server.getGameRules().getBoolean(FREE_ITEMS.get());
        *///?}
    }

    /** The max_robots game rule. */
    public static int maxRobots(MinecraftServer server) {
        //? if >=26.1 {
        return server.getGameRules().get(MAX_ROBOTS.get());
        //?} else if >=1.21.11 {
        /*return server.overworld().getGameRules().get(MAX_ROBOTS.get());
        *///?} else {
        /*return server.getGameRules().getInt(MAX_ROBOTS.get());
        *///?}
    }

    /** The robot_camera game rule. */
    public static RobotCamera.Mode robotCamera(MinecraftServer server) {
        //? if >=26.1 {
        return server.getGameRules().get(ROBOT_CAMERA.get());
        //?} else if >=1.21.11 {
        /*return server.overworld().getGameRules().get(ROBOT_CAMERA.get());
        *///?} else {
        /*return RobotCamera.Mode.valueOf(server.getGameRules().getRule(ROBOT_CAMERA.get()).serialize());
        *///?}
    }

    private Codeling() {
    }

    /** Called first by each loader's entrypoint: loading this class registers the content above. */
    public static void init() {
        ItemIds.setInThisVersion(id -> BuiltInRegistries.ITEM.containsKey(McCompat.itemId(id)));
        LOG.debug("Codeling content registered");
    }

    // The hooks below run in the order the separate Fabric listeners ran before issue #186: robots, relay, camera.

    /** End of every server tick. */
    public static void serverTick(MinecraftServer server) {
        RobotManager.tick(server);
        ServerRelay.tick(server);
        RobotCamera.tick(server);
    }

    public static void serverStopped() {
        RobotManager.serverStopped();
        ServerRelay.serverStopped();
        RobotCamera.serverStopped();
    }

    public static void playerLeft(ServerPlayer player) {
        ServerRelay.playerLeft(player);
        RobotCamera.playerLeft(player);
    }

    public static void entityLoaded(Entity entity, ServerLevel level) {
        RobotManager.entityLoaded(entity, level);
    }

    public static void entityUnloaded(Entity entity) {
        RobotManager.entityUnloaded(entity);
    }
}
