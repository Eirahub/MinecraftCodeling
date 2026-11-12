package codeling.gametest;

import codeling.Codeling;
import codeling.net.RelayPayloads;
import codeling.net.ServerRelay;
import codeling.platform.Platform;
import codeling.robot.Result;
import codeling.robot.RobotCamera;
import codeling.robot.RobotCommand;
import codeling.robot.RobotException;
import codeling.robot.RobotManager;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
//? if >=1.21.5 {
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
//?} else {
/*import java.io.File;
import java.util.Collection;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;
*///?}
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
//? if >=1.21.5
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
//? if >=1.21.11 {
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRules;
//?} else
/*import net.minecraft.world.level.GameRules;*/
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
//? if >=1.21.5
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
//? if >=1.21.5
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Smoke GameTests for the NeoForge glue (issue #187): spawn, move, place, game rules (incl. the enum rule), payloads and
 * the FakePlayer inside a claim. The full suite (fabric/src/gametest) stays on Fabric; these bodies use the same
 * vanilla GameTestHelper. Run with ./gradlew :neoforge:runGameTestServer (part of `build`). The structure
 * codeling:gametest_empty is 8x8x8 air, like Fabric's fabric-gametest-api-v1:empty. Before 1.21.5 (issue #189) the
 * tests come from a @GameTestGenerator in a @GameTestHolder instead of the test registries.
 */
//? if <1.21.5
/*@GameTestHolder(Codeling.MOD_ID)*/
@EventBusSubscriber(modid = Codeling.MOD_ID)
public final class NeoForgeSmokeGameTests {
    private static final BlockPos START = new BlockPos(1, 2, 1);
    private static final Identifier STRUCTURE = id("gametest_empty");
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("robot_spawns", NeoForgeSmokeGameTests::robotSpawns);
        TESTS.put("robot_moves", NeoForgeSmokeGameTests::robotMoves);
        TESTS.put("robot_places_block", NeoForgeSmokeGameTests::robotPlacesBlock);
        TESTS.put("game_rules_registered", NeoForgeSmokeGameTests::gameRulesRegistered);
        TESTS.put("payload_types_registered", NeoForgeSmokeGameTests::payloadTypesRegistered);
        TESTS.put("fake_player_respects_claims", NeoForgeSmokeGameTests::fakePlayerRespectsClaims);
        TESTS.put("adventure_owner_cannot_build", NeoForgeSmokeGameTests::adventureOwnerCannotBuild);
        TESTS.put("chat_line_for_the_program", NeoForgeSmokeGameTests::chatLineForTheProgram);
        TESTS.put("block_broken_for_the_program", NeoForgeSmokeGameTests::blockBrokenForTheProgram);
    }

    private NeoForgeSmokeGameTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Codeling.MOD_ID, path);
    }

    //? if >=1.21.5 {
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        TESTS.forEach((name, test) -> event.register(Registries.TEST_FUNCTION, id(name), () -> test));
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("smoke"));
        TESTS.keySet().forEach(name -> event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)), new TestData<>(environment, STRUCTURE, 100, 0, true))));
    }
    //?} else {
    /*@GameTestGenerator
    public static Collection<TestFunction> tests() {
        // No --report argument before 1.21.5: the build passes the report file as a system property.
        String report = System.getProperty("codeling.gameTestReport");
        if (report != null) {
            try {
                new File(report).getParentFile().mkdirs();
                GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(new File(report)));
            } catch (Exception e) {
                throw new IllegalStateException("GameTest report " + report, e);
            }
        }
        List<TestFunction> tests = new ArrayList<>();
        TESTS.forEach((name, test) -> tests.add(new TestFunction("codeling", "codeling." + name, STRUCTURE.toString(), 100, 0, true, test)));
        return tests;
    }
    *///?}

    // ---- helpers, as in fabric/src/gametest/java/codeling/gametest/RobotGameTests.java

    private static UUID spawn(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        RobotManager.spawn(helper.getLevel(), owner, Vec3.atBottomCenterOf(helper.absolutePos(START)), Direction.SOUTH);
        return owner;
    }

    private static List<Result> send(GameTestHelper helper, UUID owner, String op, Object... args) {
        List<Result> results = new ArrayList<>();
        try {
            RobotManager.submit(owner, RobotManager.queue(owner).run(), RobotCommand.parse(op, List.of(args)), results::add);
        } catch (RobotException e) {
            helper.fail(op + " rejected: " + e.result());
        }
        return results;
    }

    // ---- tests

    private static void robotSpawns(GameTestHelper helper) {
        UUID owner = spawn(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(RobotManager.robot(owner) != null, "robot loaded (EntityJoinLevelEvent reached RobotManager)");
            helper.assertValueEqual(RobotManager.robot(owner).blockPosition(), helper.absolutePos(START), "position");
            helper.assertValueEqual(RobotManager.robot(owner).getType(), Codeling.ROBOT.get(), "entity type from the DeferredRegister");
        });
    }

    private static void robotMoves(GameTestHelper helper) {
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "agent.move", RobotCommand.FORWARD, 5);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "results");
            helper.assertValueEqual(RobotManager.robot(owner).blockPosition(), helper.absolutePos(START.south(5)), "position");
        });
    }

    private static void robotPlacesBlock(GameTestHelper helper) {
        UUID owner = spawn(helper);
        RobotManager.robot(owner).inventory().setItem(0, new ItemStack(Items.STONE, 5));
        List<Result> results = send(helper, owner, "agent.place", RobotCommand.FORWARD);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "results");
            helper.assertBlockPresent(Blocks.STONE, START.south());
            helper.assertValueEqual(RobotManager.robot(owner).inventory().getItem(0).getCount(), 4, "stone left");
        });
    }

    private static void gameRulesRegistered(GameTestHelper helper) {
        //? if >=1.21.11 {
        GameRules rules = helper.getLevel().getGameRules();
        helper.assertValueEqual(BuiltInRegistries.GAME_RULE.getKey(Codeling.FREE_ITEMS.get()), id("free_items"), "free_items id");
        helper.assertValueEqual(BuiltInRegistries.GAME_RULE.getKey(Codeling.MAX_ROBOTS.get()), id("max_robots"), "max_robots id");
        helper.assertValueEqual(BuiltInRegistries.GAME_RULE.getKey(Codeling.ROBOT_CAMERA.get()), id("robot_camera"), "robot_camera id");
        helper.assertValueEqual(rules.get(Codeling.FREE_ITEMS.get()), false, "free_items default");
        helper.assertValueEqual(rules.get(Codeling.MAX_ROBOTS.get()), 1, "max_robots default");
        GameRule<RobotCamera.Mode> camera = Codeling.ROBOT_CAMERA.get();
        helper.assertValueEqual(rules.get(camera), RobotCamera.Mode.owner, "robot_camera default");
        helper.assertValueEqual(camera.gameRuleType(), GameRuleType.valueOf("CODELING_ENUM"), "enum rule type from enumextensions.json");
        // Saved as the constant name, as on Fabric, so a world keeps its value when it moves between loaders.
        helper.assertValueEqual(camera.valueCodec().encodeStart(JsonOps.INSTANCE, RobotCamera.Mode.ops).getOrThrow().getAsString(), "ops", "saved form");
        // /gamerule parses the value with NeoForge's EnumArgument; out-of-range ints are refused by the int rule.
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule codeling:robot_camera ops");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule codeling:max_robots 99");
        try {
            helper.assertValueEqual(rules.get(camera), RobotCamera.Mode.ops, "robot_camera after /gamerule");
            helper.assertValueEqual(rules.get(Codeling.MAX_ROBOTS.get()), 1, "max_robots keeps its range");
        } finally {
            rules.set(camera, RobotCamera.Mode.owner, server);
        }
        //?} else {
        /*// Before 1.21.11 a rule is a GameRules key named by the id (NeoForgePlatform).
        MinecraftServer server = helper.getLevel().getServer();
        GameRules rules = helper.getLevel().getGameRules();
        helper.assertValueEqual(Codeling.FREE_ITEMS.get().getId(), "codeling:free_items", "free_items name");
        helper.assertValueEqual(Codeling.MAX_ROBOTS.get().getId(), "codeling:max_robots", "max_robots name");
        helper.assertValueEqual(Codeling.ROBOT_CAMERA.get().getId(), "codeling:robot_camera", "robot_camera name");
        helper.assertValueEqual(Codeling.freeItems(server), false, "free_items default");
        helper.assertValueEqual(Codeling.maxRobots(server), 1, "max_robots default");
        helper.assertValueEqual(Codeling.robotCamera(server), RobotCamera.Mode.owner, "robot_camera default");
        // Saved as the constant name, as on 26.x and on Fabric.
        helper.assertValueEqual(rules.getRule(Codeling.ROBOT_CAMERA.get()).serialize(), "owner", "saved form");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule codeling:robot_camera ops");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule codeling:max_robots 99");
        try {
            helper.assertValueEqual(Codeling.robotCamera(server), RobotCamera.Mode.ops, "robot_camera after /gamerule");
            helper.assertValueEqual(Codeling.maxRobots(server), 1, "max_robots keeps its range");
        } finally {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule codeling:robot_camera owner");
        }
        *///?}
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static void payloadTypesRegistered(GameTestHelper helper) {
        roundTrip(helper, new RelayPayloads.ToServer("{\"t\":\"hello\"}"), PacketFlow.SERVERBOUND);
        roundTrip(helper, new RelayPayloads.ToClient("{\"t\":\"ack\"}"), PacketFlow.CLIENTBOUND);
        roundTrip(helper, new RelayPayloads.Camera(42, true), PacketFlow.CLIENTBOUND);
        // A FakePlayer has no client, so the common code never sends it a payload.
        ServerPlayer fake = Platform.INSTANCE.fakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Codeling[smoke]"));
        helper.assertFalse(Platform.INSTANCE.canSend(fake, RelayPayloads.ToClient.TYPE), "canSend to a FakePlayer");
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static void roundTrip(GameTestHelper helper, CustomPacketPayload payload, PacketFlow flow) {
        var codec = (StreamCodec<FriendlyByteBuf, CustomPacketPayload>) NetworkRegistry.getCodec(payload.type().id(), ConnectionProtocol.PLAY, flow);
        helper.assertTrue(codec != null, payload.type().id() + " registered for " + flow);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, payload);
        helper.assertValueEqual(codec.decode(buf), payload, payload.type().id() + " round trip");
    }

    /** Absolute positions a claim mod protects: it refuses the FakePlayer's click there, as claim mods do. */
    private static final Set<BlockPos> CLAIMED = ConcurrentHashMap.newKeySet();
    /** Who the claim mod saw clicking; the robot's FakePlayer carries the owner's UUID. */
    private static final Set<UUID> CLICKERS = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void claimMod(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof FakePlayer player && CLAIMED.contains(event.getPos())) {
            CLICKERS.add(player.getUUID());
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private static void fakePlayerRespectsClaims(GameTestHelper helper) {
        BlockPos claimed = helper.absolutePos(START.south());
        CLAIMED.add(claimed);
        UUID owner = spawn(helper);
        RobotManager.robot(owner).inventory().setItem(0, new ItemStack(Items.STONE, 5));
        List<Result> results = send(helper, owner, "agent.place", RobotCommand.FORWARD);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(false)), "results");
            CLAIMED.remove(claimed);
            helper.assertBlockPresent(Blocks.AIR, START.south());
            helper.assertValueEqual(RobotManager.robot(owner).inventory().getItem(0).getCount(), 5, "stone kept");
            helper.assertTrue(CLICKERS.contains(owner), "the claim mod saw the owner's UUID");
        });
    }

    // Issue #53: the FakePlayer from FakePlayerFactory follows the owner's adventure rule: no placing, no breaking.
    private static void adventureOwnerCannotBuild(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(START).above(100)));
        player.setGameMode(GameType.ADVENTURE);
        helper.setBlock(START.west(), Blocks.STONE);
        UUID owner = player.getUUID();
        RobotManager.spawn(helper.getLevel(), owner, Vec3.atBottomCenterOf(helper.absolutePos(START)), Direction.SOUTH);
        RobotManager.robot(owner).inventory().setItem(0, new ItemStack(Items.STONE, 5));
        List<Result> place = send(helper, owner, "agent.place", RobotCommand.FORWARD);
        List<Result> destroy = send(helper, owner, "agent.destroy", RobotCommand.RIGHT);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(place, List.of(Result.ok(false)), "place");
            helper.assertValueEqual(destroy, List.of(Result.ok(false)), "destroy");
            helper.assertBlockPresent(Blocks.AIR, START.south());
            helper.assertBlockPresent(Blocks.STONE, START.west());
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // Issue #27: ServerChatEvent reaches the common hook; a line the running program listens for is cancelled (not
    // broadcast) and becomes an event, any other line is left alone.
    private static void chatLineForTheProgram(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<String> inbox = new ArrayList<>();
        ServerRelay.handle(player, "{\"v\":2,\"t\":\"control\",\"op\":\"start\"}", inbox::add);
        long run = RobotManager.queue(player.getUUID()).run();
        ServerRelay.handle(player, "{\"v\":2,\"t\":\"control\",\"op\":\"subscribe\",\"run\":" + run + ",\"chat\":[\"jump\"]}", inbox::add);
        ServerChatEvent jump = NeoForge.EVENT_BUS.post(new ServerChatEvent(player, "jump 3", Component.literal("jump 3")));
        ServerChatEvent hello = NeoForge.EVENT_BUS.post(new ServerChatEvent(player, "hello 3", Component.literal("hello 3")));
        helper.assertTrue(jump.isCanceled(), "jump held back");
        helper.assertTrue(!hello.isCanceled(), "hello broadcast");
        helper.succeedWhen(() -> {
            helper.assertTrue(inbox.contains("{\"v\":2,\"t\":\"event\",\"name\":\"chat\",\"args\":[\"jump\",3.0,0.0,0.0]}"), "event: " + inbox);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // Issue #89: the block break event reaches the common hook through a real break (NeoForge fires it inside
    // destroyBlock); the stone the program listens for becomes an event and the break is not changed.
    private static void blockBrokenForTheProgram(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.setBlock(START.east(2), Blocks.STONE);
        List<String> inbox = new ArrayList<>();
        ServerRelay.handle(player, "{\"v\":2,\"t\":\"control\",\"op\":\"start\"}", inbox::add);
        long run = RobotManager.queue(player.getUUID()).run();
        ServerRelay.handle(player, "{\"v\":2,\"t\":\"control\",\"op\":\"subscribe\",\"run\":" + run + ",\"broken\":[1]}", inbox::add);
        helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(START.east(2))), "broken");
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, START.east(2));
            helper.assertTrue(inbox.contains("{\"v\":2,\"t\":\"event\",\"name\":\"blockBroken\",\"args\":[1]}"), "event: " + inbox);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }
}
