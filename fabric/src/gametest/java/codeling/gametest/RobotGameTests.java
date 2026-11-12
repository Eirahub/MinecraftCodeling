package codeling.gametest;

import codeling.Codeling;
import codeling.McCompat;
import codeling.robot.BlockIds;
import codeling.robot.ItemIds;
import codeling.robot.RobotCamera;
import codeling.robot.Result;
import codeling.robot.RobotCommand;
import codeling.robot.RobotEntity;
import codeling.robot.RobotException;
import codeling.robot.RobotManager;
import codeling.robot.RobotMenu;
import codeling.robot.RobotShapes;
import codeling.robot.RobotTrail;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
//? if >=26.1 {
import net.minecraft.world.entity.EntityTypes;
//?} else
/*import net.minecraft.world.entity.EntityType;*/
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server GameTests for the robot. Positions are relative to the test structure (empty 8x8 by default). */
public class RobotGameTests {
    private static final BlockPos START = new BlockPos(1, 2, 1);

    //? if <1.21.5 {
    /*// Before 1.21.5 there are no test environments: the free_items tests form a batch, and the rule is set for it.
    @net.minecraft.gametest.framework.BeforeBatch(batch = "codeling-gametest:free_items")
    public void freeItemsOn(ServerLevel level) {
        GameTestCompat.setFreeItems(level.getServer(), true);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = "codeling-gametest:free_items")
    public void freeItemsOff(ServerLevel level) {
        GameTestCompat.setFreeItems(level.getServer(), false);
    }
    *///?}

    /** Spawns a robot for a fresh owner at {@code START}, facing south (+z). */
    private static UUID spawn(GameTestHelper helper) {
        return spawn(helper, UUID.randomUUID());
    }

    private static UUID spawn(GameTestHelper helper, UUID owner) {
        RobotManager.spawn(helper.getLevel(), owner, Vec3.atBottomCenterOf(helper.absolutePos(START)), Direction.SOUTH);
        return owner;
    }

    /** Spawns a robot with {@code count} stone in slot 1 (the selected slot). */
    private static UUID spawnWithStone(GameTestHelper helper, int count) {
        UUID owner = spawn(helper);
        inv(owner).setItem(0, new ItemStack(Items.STONE, count));
        return owner;
    }

    private static SimpleContainer inv(UUID owner) {
        return RobotManager.robot(owner).inventory();
    }

    private static List<Result> send(GameTestHelper helper, UUID owner, String op, Object... args) {
        return send(new ArrayList<>(), helper, owner, op, args);
    }

    /** Sends one command whose result is added to {@code results} when it arrives, so several commands share a list. */
    private static List<Result> send(List<Result> results, GameTestHelper helper, UUID owner, String op, Object... args) {
        try {
            RobotManager.submit(owner, RobotManager.queue(owner).run(), RobotCommand.parse(op, List.of(args)), results::add);
        } catch (RobotException e) {
            helper.fail(op + " rejected: " + e.result());
        }
        return results;
    }

    // Compares absolute positions: GameTestHelper.relativePos in 26.3 inverts the offset for unrotated tests.
    private static BlockPos at(GameTestHelper helper, UUID owner) {
        return RobotManager.robot(owner).blockPosition();
    }

    @GameTest(maxTicks = 100)
    public void moveForwardInOpenAir(GameTestHelper helper) {
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "agent.move", RobotCommand.FORWARD, 5);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "results");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.south(5)), "position");
        });
    }

    @GameTest(maxTicks = 100)
    public void moveIntoWallStopsEarly(GameTestHelper helper) {
        helper.setBlock(START.south(3), Blocks.STONE);
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "agent.move", RobotCommand.FORWARD, 5);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(false)), "results");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.south(2)), "position");
        });
    }

    @GameTest(maxTicks = 100)
    public void turnChangesFacing(GameTestHelper helper) {
        UUID owner = spawn(helper);
        List<Result> right = send(helper, owner, "agent.turn", RobotCommand.RIGHT_TURN);
        List<Result> move = send(helper, owner, "agent.move", RobotCommand.FORWARD, 1);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(right, List.of(Result.ok(null)), "turn result");
            helper.assertValueEqual(move, List.of(Result.ok(true)), "move result");
            helper.assertValueEqual(RobotManager.robot(owner).getDirection(), Direction.WEST, "facing");
            // facing west, forward is -x
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.west()), "position");
        });
    }

    // Issues #21 and #22: world block position, and MakeCode yaw (south 0, west 90, north 180, east -90).
    @GameTest(maxTicks = 100)
    public void positionAndOrientation(GameTestHelper helper) {
        UUID owner = spawn(helper);
        BlockPos start = helper.absolutePos(START);
        List<Result> position = send(helper, owner, "agent.getPosition");
        List<List<Result>> yaw = new ArrayList<>();
        yaw.add(send(helper, owner, "agent.getOrientation"));
        for (int i = 0; i < 3; i++) {
            send(helper, owner, "agent.turn", RobotCommand.RIGHT_TURN);
            yaw.add(send(helper, owner, "agent.getOrientation"));
        }
        send(helper, owner, "agent.move", RobotCommand.FORWARD, 2);
        List<Result> moved = send(helper, owner, "agent.getPosition");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(position, List.of(Result.ok(List.of(start.getX(), start.getY(), start.getZ()))), "position");
            helper.assertValueEqual(yaw, List.of(List.of(Result.ok(0)), List.of(Result.ok(90)), List.of(Result.ok(180)), List.of(Result.ok(-90))),
                    "south, west, north, east");
            // facing east, forward is +x
            helper.assertValueEqual(moved, List.of(Result.ok(List.of(start.getX() + 2, start.getY(), start.getZ()))), "position after move");
        });
    }

    private static Result found(BlockPos pos) {
        return Result.ok(List.of(pos.getX(), pos.getY(), pos.getZ()));
    }

    // Issue #40: the nearest match by block steps, and only the asked-for block (birch is not oak).
    @GameTest(maxTicks = 100)
    public void findNearestBlock(GameTestHelper helper) {
        UUID owner = spawn(helper);
        helper.setBlock(START.south(), Blocks.BIRCH_LOG);
        helper.setBlock(START.south(2), Blocks.OAK_LOG);
        helper.setBlock(START.east(4), Blocks.OAK_LOG);
        List<Result> results = send(helper, owner, "agent.findNearest", BlockIds.byName("OAK_LOG"), 4);
        helper.succeedWhen(() -> helper.assertValueEqual(results, List.of(found(helper.absolutePos(START.south(2)))), "nearest oak log"));
    }

    // Issue #41: categories are block tags, so modern woods and ores without a legacy id match too.
    @GameTest(maxTicks = 100)
    public void findNearestCategory(GameTestHelper helper) {
        UUID owner = spawn(helper);
        helper.setBlock(START.above(2), Blocks.CHERRY_LOG);
        //? if >=1.21.4 {
        helper.setBlock(START.east(), Blocks.PALE_OAK_LEAVES);
        //?} else
        /*helper.setBlock(START.east(), Blocks.CHERRY_LEAVES);*/
        helper.setBlock(START.south(3), Blocks.DEEPSLATE_DIAMOND_ORE);
        List<Result> logs = send(helper, owner, "agent.findNearestCategory", 0, 3);
        List<Result> leaves = send(helper, owner, "agent.findNearestCategory", 1, 3);
        List<Result> ores = send(helper, owner, "agent.findNearestCategory", 2, 3);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(logs, List.of(found(helper.absolutePos(START.above(2)))), "logs");
            helper.assertValueEqual(leaves, List.of(found(helper.absolutePos(START.east()))), "leaves");
            helper.assertValueEqual(ores, List.of(found(helper.absolutePos(START.south(3)))), "ores");
        });
    }

    // Issues #40 and #46: outside the radius is not found (no value); a full radius-32 search spreads over ticks; stop cancels.
    // The scan budget is shared, so while twentyRobotsSearchWalkAndCraft runs beside it this search takes about 270 ticks.
    @GameTest(maxTicks = 600)
    public void findNearestNotFoundOutOfRangeAndStop(GameTestHelper helper) {
        UUID owner = spawn(helper);
        helper.setBlock(START.south(5), Blocks.GOLD_BLOCK);
        int gold = BlockIds.byName("GOLD_BLOCK");
        List<Result> outOfRange = send(helper, owner, "agent.findNearest", gold, 4);
        List<Result> inRange = send(helper, owner, "agent.findNearest", gold, 5);
        List<Result> nowhere = send(helper, owner, "agent.findNearest", BlockIds.byName("EMERALD_ORE"), RobotCommand.MAX_FIND_RADIUS);
        helper.runAfterDelay(3, () -> helper.assertTrue(nowhere.isEmpty(), "a radius-32 search takes more than one tick"));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(outOfRange, List.of(Result.ok(null)), "out of range");
            helper.assertValueEqual(inRange, List.of(found(helper.absolutePos(START.south(5)))), "in range");
            helper.assertValueEqual(nowhere, List.of(Result.ok(null)), "not found");
        });
    }

    @GameTest(maxTicks = 100)
    public void stopCancelsAFind(GameTestHelper helper) {
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "agent.findNearest", BlockIds.byName("EMERALD_ORE"), RobotCommand.MAX_FIND_RADIUS);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(results.isEmpty(), "still searching " + results);
            RobotManager.queue(owner).stop();
            helper.assertValueEqual(results.size() == 1 ? results.get(0).code() : null, Result.Code.STOPPED, "stopped");
            helper.succeed();
        });
    }

    // Issue #80: the text shows over the robot (synced entity data the renderer draws) and clears after SAY_TICKS.
    @GameTest(maxTicks = RobotEntity.SAY_TICKS + 40)
    public void saySetsTheBubbleThenClearsIt(GameTestHelper helper) {
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "agent.say", "Hello!");
        helper.runAfterDelay(3, () -> {
            helper.assertValueEqual(results, List.of(Result.ok(null)), "result");
            helper.assertValueEqual(RobotManager.robot(owner).said(), "Hello!", "bubble");
        });
        helper.runAfterDelay(RobotEntity.SAY_TICKS + 10, () -> {
            helper.assertValueEqual(RobotManager.robot(owner).said(), "", "bubble after the time");
            helper.succeed();
        });
    }

    /** A mock player (creative, not op) standing next to the robot's start position. */
    private static ServerPlayer playerAtStart(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(START.east())));
        return player;
    }

    private static int itemsOnGround(GameTestHelper helper, net.minecraft.world.item.Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(START)).inflate(3), e -> e.isAlive()
                && e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    // Issue #70: only the owner and ops open the screen; anyone else gets "not your robot" and no menu.
    @GameTest(maxTicks = 100)
    public void inventoryOnlyForOwnerAndOps(GameTestHelper helper) {
        ServerPlayer owner = playerAtStart(helper);
        ServerPlayer other = playerAtStart(helper);
        UUID id = spawn(helper, owner.getUUID());
        RobotEntity robot = RobotManager.robot(id);
        var players = helper.getLevel().getServer().getPlayerList();
        helper.assertFalse(RobotManager.openInventory(other, robot), "a stranger may not open it");
        helper.assertTrue(other.containerMenu == other.inventoryMenu, "no screen for the stranger");
        helper.assertTrue(RobotManager.openInventory(owner, robot), "the owner may open it");
        helper.assertTrue(owner.containerMenu instanceof RobotMenu, "the owner's screen");
        // The GameTest server's default op level is 0, so give the level a dedicated server's op gets (game master).
        GameTestCompat.op(other);
        helper.assertTrue(RobotManager.openInventory(other, robot), "an op may open any robot");
        helper.assertTrue(other.containerMenu instanceof RobotMenu, "the op's screen");
        GameTestCompat.deop(other);
        helper.assertFalse(other.containerMenu.stillValid(other), "the screen closes when the op status goes");
        players.remove(owner);
        players.remove(other);
        helper.succeed();
    }

    // Issue #68: the screen is the robot's own inventory, so items moved in it are what the program sees; the
    // selected slot is synced for the highlight.
    @GameTest(maxTicks = 100)
    public void inventoryScreenIsTheRobotsInventory(GameTestHelper helper) {
        ServerPlayer player = playerAtStart(helper);
        UUID owner = spawn(helper, player.getUUID());
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 10)); // hotbar slot 0 is menu slot 27 + 27
        RobotManager.openInventory(player, RobotManager.robot(owner));
        RobotMenu menu = (RobotMenu) player.containerMenu;
        menu.clicked(RobotCommand.SLOTS + 27, 0, ContainerInput.QUICK_MOVE, player);
        List<Result> setSlot = send(helper, owner, "agent.setSlot", 5);
        List<Result> count = send(helper, owner, "agent.getItemCount", 1);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(count, List.of(Result.ok(10)), "getItemCount after the move in the screen");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "moved, not copied");
            helper.assertValueEqual(setSlot, List.of(Result.ok(null)), "setSlot");
            helper.assertValueEqual(menu.selectedSlot(), 5, "selected slot in the menu");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // Items never double or vanish: a removed robot drops its items and its screen stops being valid (the server
    // refuses clicks on an invalid menu); logging out with an item on the cursor drops that item, as with a chest.
    @GameTest(maxTicks = 100)
    public void inventoryScreenKeepsItemsWhenRobotOrPlayerGoes(GameTestHelper helper) {
        ServerPlayer player = playerAtStart(helper);
        UUID owner = spawn(helper, player.getUUID());
        RobotEntity robot = RobotManager.robot(owner);
        robot.inventory().setItem(0, new ItemStack(Items.STONE, 10));
        RobotManager.openInventory(player, robot);
        RobotMenu menu = (RobotMenu) player.containerMenu;
        menu.setCarried(new ItemStack(Items.DIRT, 4));
        robot.discard();
        helper.assertFalse(menu.stillValid(player), "the screen of a removed robot is invalid");
        helper.assertTrue(robot.inventory().isEmpty(), "the removed robot's container is empty");
        helper.assertValueEqual(itemsOnGround(helper, Items.STONE), 10, "stone dropped once");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "logout closes the screen");
        helper.assertValueEqual(itemsOnGround(helper, Items.DIRT), 4, "the cursor item dropped at logout");
        helper.succeed();
    }

    // Issue #114: /robot spawn in another dimension moves every item to the new robot and drops nothing.
    @GameTest(maxTicks = 100)
    public void spawnInAnotherDimensionMovesTheInventory(GameTestHelper helper) {
        UUID owner = spawnWithStone(helper, 10);
        RobotEntity old = RobotManager.robot(owner);
        old.inventory().setItem(RobotCommand.SLOTS - 1, new ItemStack(Items.DIRT, 64));
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the test server has a nether");
        RobotEntity moved = RobotManager.spawn(nether, owner, new Vec3(0.5, 64, 0.5), Direction.SOUTH);
        helper.assertTrue(moved != old && moved.level() == nether, "a new robot in the nether");
        helper.assertTrue(old.isRemoved() && old.inventory().isEmpty(), "the old robot is gone and empty");
        helper.assertValueEqual(moved.inventory().getItem(0).getCount(), 10, "stone arrived");
        helper.assertValueEqual(moved.inventory().getItem(RobotCommand.SLOTS - 1).getCount(), 64, "dirt arrived");
        helper.assertValueEqual(itemsOnGround(helper, Items.STONE) + itemsOnGround(helper, Items.DIRT), 0, "nothing dropped");
        moved.inventory().clearContent();
        moved.discard();
        helper.succeed();
    }

    private static CompoundTag save(GameTestHelper helper, RobotEntity robot) {
        return GameTestCompat.save(robot, helper.getLevel().registryAccess());
    }

    private static RobotEntity load(GameTestHelper helper, CompoundTag tag) {
        RobotEntity robot = new RobotEntity(Codeling.ROBOT.get(), helper.getLevel());
        GameTestCompat.load(robot, tag, helper.getLevel().registryAccess());
        return robot;
    }

    // Issue #114: a robot replaced while unloaded still owns its items and drops them when its chunk loads again,
    // once. Any other stale copy (a second load of that save, a copy with a new UUID) is removed without dropping.
    @GameTest(maxTicks = 100)
    public void staleCopiesDropOnlyItemsTheyStillOwn(GameTestHelper helper) {
        UUID owner = spawnWithStone(helper, 7);
        RobotEntity first = RobotManager.robot(owner);
        CompoundTag saved = save(helper, first);
        first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // its chunk unloads with the stone inside
        spawn(helper, owner); // replaced while unloaded
        RobotEntity back = load(helper, saved);
        helper.getLevel().addFreshEntity(back);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(back.isRemoved(), "the stale robot is removed"))
                .thenExecute(() -> {
                    helper.assertValueEqual(itemsOnGround(helper, Items.STONE), 7, "the orphan dropped its own stone");
                    RobotEntity again = load(helper, saved);
                    RobotEntity copy = load(helper, saved);
                    copy.setUUID(UUID.randomUUID());
                    helper.getLevel().addFreshEntity(again);
                    helper.getLevel().addFreshEntity(copy);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertValueEqual(itemsOnGround(helper, Items.STONE), 7, "no stale copy duplicated the stone");
                    helper.assertTrue(RobotManager.robot(owner).inventory().isEmpty(), "the new robot got nothing");
                })
                .thenSucceed();
    }

    /** Absolute positions a claim mod protects: it refuses the FakePlayer's click there, as claim mods do. */
    private static final Set<BlockPos> CLAIMED = ConcurrentHashMap.newKeySet();

    static {
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                player instanceof FakePlayer && CLAIMED.contains(hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    private static Container container(GameTestHelper helper, BlockPos pos) {
        return HopperBlockEntity.getContainerAt(helper.getLevel(), helper.absolutePos(pos));
    }

    private static int stone() {
        return BlockIds.byName("STONE");
    }

    // Issue #72: storeItems moves one slot or all slots into a chest, barrel or hopper and answers true when
    // something moved; with nothing left it answers false.
    @GameTest(maxTicks = 100)
    public void storeItemsIntoChestBarrelAndHopper(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.CHEST);
        helper.setBlock(START.east(), Blocks.BARREL);
        helper.setBlock(START.below(), Blocks.HOPPER);
        UUID owner = spawnWithStone(helper, 10);
        inv(owner).setItem(1, new ItemStack(Items.DIRT, 5));
        inv(owner).setItem(2, new ItemStack(Items.COBBLESTONE, 3));
        inv(owner).setItem(3, new ItemStack(Items.OAK_PLANKS, 2));
        List<Result> hopper = send(helper, owner, "agent.storeItems", RobotCommand.DOWN, 4);
        List<Result> chest = send(helper, owner, "agent.storeItems", RobotCommand.FORWARD, 1);
        List<Result> barrel = send(helper, owner, "agent.storeItems", RobotCommand.LEFT, 0);
        List<Result> nothing = send(helper, owner, "agent.storeItems", RobotCommand.LEFT, 0);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(List.of(hopper, chest, barrel, nothing), List.of(List.of(Result.ok(true)), List.of(Result.ok(true)),
                    List.of(Result.ok(true)), List.of(Result.ok(false))), "results");
            helper.assertValueEqual(container(helper, START.below()).countItem(Items.OAK_PLANKS), 2, "planks in the hopper");
            helper.assertValueEqual(container(helper, START.south()).countItem(Items.STONE), 10, "stone in the chest");
            helper.assertValueEqual(container(helper, START.east()).countItem(Items.DIRT), 5, "dirt in the barrel");
            helper.assertValueEqual(container(helper, START.east()).countItem(Items.COBBLESTONE), 3, "cobblestone in the barrel");
            helper.assertTrue(inv(owner).isEmpty(), "the robot is empty");
        });
    }

    // Issue #72: a full container answers false and a partial move leaves the rest in the robot.
    @GameTest(maxTicks = 100)
    public void storeItemsIntoAFullContainerKeepsTheRest(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.HOPPER);
        Container hopper = container(helper, START.south());
        for (int i = 0; i < 4; i++) hopper.setItem(i, new ItemStack(Items.DIRT, 64));
        hopper.setItem(4, new ItemStack(Items.STONE, 60));
        UUID owner = spawnWithStone(helper, 10);
        List<Result> partial = send(helper, owner, "agent.storeItems", RobotCommand.FORWARD, 1);
        List<Result> full = send(helper, owner, "agent.storeItems", RobotCommand.FORWARD, 1);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(partial, List.of(Result.ok(true)), "partial");
            helper.assertValueEqual(full, List.of(Result.ok(false)), "full");
            helper.assertValueEqual(inv(owner).getItem(0).getCount(), 6, "stone left in the robot");
            helper.assertValueEqual(hopper.countItem(Items.STONE), 64, "stone in the hopper");
        });
    }

    // Issues #73, #111 and #112: takeItems takes from the first or a given stack, takeItem takes one item from any
    // slot, containerCount and containerSize look inside; no container answers 0 or false.
    @GameTest(maxTicks = 100)
    public void takeItemsAndLookInside(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.CHEST);
        Container chest = container(helper, START.south());
        chest.setItem(0, new ItemStack(Items.DIRT, 5));
        chest.setItem(2, new ItemStack(Items.STONE, 40));
        chest.setItem(5, new ItemStack(Items.STONE, 30));
        UUID owner = spawn(helper);
        List<Result> count = send(helper, owner, "agent.containerCount", RobotCommand.FORWARD, stone());
        List<Result> size = send(helper, owner, "agent.containerSize", RobotCommand.FORWARD);
        List<Result> noSize = send(helper, owner, "agent.containerSize", RobotCommand.BACK);
        List<Result> noTake = send(helper, owner, "agent.takeItems", RobotCommand.BACK, 0, 1);
        List<Result> first = send(helper, owner, "agent.takeItems", RobotCommand.FORWARD, 0, 3);
        List<Result> slot6 = send(helper, owner, "agent.takeItems", RobotCommand.FORWARD, 6, 64);
        List<Result> byItem = send(helper, owner, "agent.takeItem", RobotCommand.FORWARD, stone(), 64);
        List<Result> after = send(helper, owner, "agent.containerCount", RobotCommand.FORWARD, stone());
        helper.succeedWhen(() -> {
            helper.assertValueEqual(List.of(count, size, noSize, noTake, first, slot6, byItem, after), List.of(List.of(Result.ok(70)),
                    List.of(Result.ok(27)), List.of(Result.ok(0)), List.of(Result.ok(false)), List.of(Result.ok(true)),
                    List.of(Result.ok(true)), List.of(Result.ok(40)), List.of(Result.ok(0))), "results");
            helper.assertValueEqual(chest.countItem(Items.DIRT), 2, "dirt left in the chest");
            helper.assertValueEqual(inv(owner).countItem(Items.DIRT), 3, "dirt in the robot");
            helper.assertValueEqual(inv(owner).countItem(Items.STONE), 70, "stone in the robot");
        });
    }

    // Issue #112: takeItem stops when the robot is full and leaves the rest in the chest.
    @GameTest(maxTicks = 100)
    public void takeItemStopsWhenTheRobotIsFull(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.CHEST);
        Container chest = container(helper, START.south());
        chest.setItem(0, new ItemStack(Items.STONE, 10));
        UUID owner = spawn(helper);
        for (int i = 0; i < RobotCommand.SLOTS - 1; i++) inv(owner).setItem(i, new ItemStack(Items.DIRT, 64));
        inv(owner).setItem(RobotCommand.SLOTS - 1, new ItemStack(Items.STONE, 60));
        List<Result> taken = send(helper, owner, "agent.takeItem", RobotCommand.FORWARD, stone(), 64);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(taken, List.of(Result.ok(4)), "taken");
            helper.assertValueEqual(chest.countItem(Items.STONE), 6, "stone left in the chest");
            helper.assertValueEqual(inv(owner).countItem(Items.STONE), 64, "stone in the robot");
        });
    }

    // Issues #111 and #113: a double chest has 54 slots; sortContainer merges stacks and orders them by item id.
    // A furnace is not sorted.
    @GameTest(maxTicks = 100)
    public void doubleChestSizeAndSort(GameTestHelper helper) {
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        helper.setBlock(START.south(), chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(START.south().east(), chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        helper.setBlock(START.west(), Blocks.FURNACE);
        Container both = container(helper, START.south());
        both.setItem(0, new ItemStack(Items.STONE, 5));
        both.setItem(30, new ItemStack(Items.DIRT, 10));
        both.setItem(40, new ItemStack(Items.DIRT, 20));
        both.setItem(50, new ItemStack(Items.COBBLESTONE, 64));
        both.setItem(51, new ItemStack(Items.COBBLESTONE, 1));
        UUID owner = spawn(helper);
        List<Result> size = send(helper, owner, "agent.containerSize", RobotCommand.FORWARD);
        List<Result> sorted = send(helper, owner, "agent.sortContainer", RobotCommand.FORWARD);
        List<Result> furnace = send(helper, owner, "agent.sortContainer", RobotCommand.RIGHT);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(List.of(size, sorted, furnace), List.of(List.of(Result.ok(54)), List.of(Result.ok(true)),
                    List.of(Result.ok(false))), "results");
            List<String> slots = new ArrayList<>();
            for (int i = 0; i < 5; i++) slots.add(both.getItem(i).getCount() + " " + both.getItem(i).getItem());
            helper.assertValueEqual(slots, List.of("64 minecraft:cobblestone", "1 minecraft:cobblestone", "30 minecraft:dirt",
                    "5 minecraft:stone", "0 minecraft:air"), "sorted slots");
        });
    }

    // Issues #73 and #111: a protected container (a claim mod refuses the click) is refused like a player would be.
    @GameTest(maxTicks = 100)
    public void protectedContainerIsRefused(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.CHEST);
        Container chest = container(helper, START.south());
        chest.setItem(0, new ItemStack(Items.STONE, 10));
        CLAIMED.add(helper.absolutePos(START.south()));
        UUID owner = spawn(helper);
        inv(owner).setItem(1, new ItemStack(Items.DIRT, 5));
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.containerSize", RobotCommand.FORWARD);
        send(results, helper, owner, "agent.containerCount", RobotCommand.FORWARD, stone());
        send(results, helper, owner, "agent.takeItems", RobotCommand.FORWARD, 0, 64);
        send(results, helper, owner, "agent.takeItem", RobotCommand.FORWARD, stone(), 64);
        send(results, helper, owner, "agent.storeItems", RobotCommand.FORWARD, 0);
        send(results, helper, owner, "agent.sortContainer", RobotCommand.FORWARD);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results.size(), 6, "all answered");
            CLAIMED.remove(helper.absolutePos(START.south()));
            helper.assertValueEqual(results, List.of(Result.ok(0), Result.ok(0), Result.ok(false), Result.ok(0), Result.ok(false),
                    Result.ok(false)), "results");
            helper.assertValueEqual(chest.countItem(Items.STONE), 10, "the chest keeps its stone");
            helper.assertValueEqual(inv(owner).countItem(Items.DIRT), 5, "the robot keeps its dirt");
        });
    }

    /** Absolute positions under spawn protection; SpawnProtectionMixin answers true there (a GameTest server has none). */
    public static final Set<BlockPos> SPAWN_PROTECTED = ConcurrentHashMap.newKeySet();

    /**
     * Issue #53 layout, robot at START facing south: an oak door forward, a chest with 3 stone left, a stone block
     * right, air above, stone in the selected slot. Answers place up, destroy right, interact forward and the chest's
     * stone count, in that order, as one list.
     */
    private static List<Result> protectionOps(GameTestHelper helper, UUID owner) {
        door(helper, START.south(), Blocks.OAK_DOOR);
        helper.setBlock(START.east(), Blocks.CHEST);
        container(helper, START.east()).setItem(0, new ItemStack(Items.STONE, 3));
        helper.setBlock(START.west(), Blocks.STONE);
        spawn(helper, owner);
        inv(owner).setItem(0, new ItemStack(Items.STONE, 5));
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.place", RobotCommand.UP);
        send(results, helper, owner, "agent.destroy", RobotCommand.RIGHT);
        send(results, helper, owner, "agent.interact", RobotCommand.FORWARD);
        send(results, helper, owner, "agent.containerCount", RobotCommand.LEFT, stone());
        return results;
    }

    /** An online owner in the given game mode, out of the robot's way. */
    private static ServerPlayer ownerIn(GameTestHelper helper, GameType mode) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(START).above(100)));
        player.setGameMode(mode);
        return player;
    }

    // Issue #53: the robot of an adventure owner opens doors and containers but places and breaks nothing.
    @GameTest(maxTicks = 100)
    public void adventureOwnerUsesBlocksButCannotBuild(GameTestHelper helper) {
        ServerPlayer player = ownerIn(helper, GameType.ADVENTURE);
        List<Result> results = protectionOps(helper, player.getUUID());
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(false), Result.ok(false), Result.ok(true), Result.ok(3)), "results");
            helper.assertBlockPresent(Blocks.AIR, START.above());
            helper.assertBlockPresent(Blocks.STONE, START.west());
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // Issue #53: the robot of a spectator owner acts on nothing.
    @GameTest(maxTicks = 100)
    public void spectatorOwnerDoesNothing(GameTestHelper helper) {
        ServerPlayer player = ownerIn(helper, GameType.SPECTATOR);
        List<Result> results = protectionOps(helper, player.getUUID());
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(false), Result.ok(false), Result.ok(false), Result.ok(0)), "results");
            helper.assertBlockPresent(Blocks.AIR, START.above());
            helper.assertBlockPresent(Blocks.STONE, START.west());
            helper.assertFalse(open(helper, START.south()), "door shut");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // Issue #53: an online survival owner's robot does all four, as before.
    @GameTest(maxTicks = 100)
    public void survivalOwnerIsUnchanged(GameTestHelper helper) {
        ServerPlayer player = ownerIn(helper, GameType.SURVIVAL);
        List<Result> results = protectionOps(helper, player.getUUID());
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true), Result.ok(true), Result.ok(true), Result.ok(3)), "results");
            helper.assertBlockPresent(Blocks.STONE, START.above());
            helper.assertBlockPresent(Blocks.AIR, START.west());
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // Issue #53: inside spawn protection the robot places, breaks, uses and opens nothing, also in a fill.
    @GameTest(maxTicks = 100)
    public void spawnProtectionRefusesEveryEdit(GameTestHelper helper) {
        List<BlockPos> spots = List.of(START.above(), START.west(), START.south(), START.east(), START.north());
        spots.forEach(p -> SPAWN_PROTECTED.add(helper.absolutePos(p)));
        UUID owner = UUID.randomUUID();
        List<Result> results = protectionOps(helper, owner);
        helper.setBlock(START.north(), Blocks.DIRT);
        send(results, helper, owner, "blocks.fill", fill(helper, stone(), START.north(), START.north(), RobotShapes.REPLACE));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(false), Result.ok(false), Result.ok(false), Result.ok(0), Result.ok(0)), "results");
            spots.forEach(p -> SPAWN_PROTECTED.remove(helper.absolutePos(p)));
            helper.assertBlockPresent(Blocks.AIR, START.above());
            helper.assertBlockPresent(Blocks.STONE, START.west());
            helper.assertBlockPresent(Blocks.DIRT, START.north());
            helper.assertFalse(open(helper, START.south()), "door shut");
        });
    }

    // Issue #74: from above the robot fills the input, from the side the fuel, from below it takes the output.
    @GameTest(maxTicks = 400)
    public void furnaceSmeltsCobblestoneWithCoal(GameTestHelper helper) {
        BlockPos furnace = START.above();
        helper.setBlock(furnace, Blocks.FURNACE);
        UUID owner = UUID.randomUUID();
        RobotManager.spawn(helper.getLevel(), owner, Vec3.atBottomCenterOf(helper.absolutePos(furnace.above())), Direction.SOUTH);
        inv(owner).setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        inv(owner).setItem(1, new ItemStack(Items.COAL, 2));
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.storeItems", RobotCommand.DOWN, 1);
        send(results, helper, owner, "agent.move", RobotCommand.BACK, 1);
        send(results, helper, owner, "agent.move", RobotCommand.DOWN, 1);
        send(results, helper, owner, "agent.storeItems", RobotCommand.FORWARD, 2);
        send(results, helper, owner, "agent.move", RobotCommand.DOWN, 1);
        send(results, helper, owner, "agent.move", RobotCommand.FORWARD, 1);
        Container c = container(helper, furnace);
        List<Result> taken = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(c.getItem(2).getItem(), Items.STONE, "smelted"))
                .thenExecute(() -> {
                    helper.assertValueEqual(results, java.util.Collections.nCopies(6, Result.ok(true)), "store, move, store, move");
                    helper.assertValueEqual(at(helper, owner), helper.absolutePos(START), "below the furnace");
                    send(taken, helper, owner, "agent.takeItems", RobotCommand.UP, 0, 64);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(taken, List.of(Result.ok(true)), "taken"))
                .thenExecute(() -> {
                    helper.assertValueEqual(inv(owner).countItem(Items.STONE), 1, "stone in the robot");
                    helper.assertValueEqual(inv(owner).countItem(Items.COAL), 0, "the fuel stays in the furnace");
                    helper.assertValueEqual(c.countItem(Items.COAL), 1, "one coal burns, one is left");
                })
                .thenSucceed();
    }

    // Issue #74: smoker, blast furnace and brewing stand follow the furnace's side rules.
    @GameTest(maxTicks = 400)
    public void smokerCooksBeef(GameTestHelper helper) {
        useFromThreeSides(helper, Blocks.SMOKER, new ItemStack(Items.BEEF), List.of(new ItemStack(Items.COAL)), Items.COOKED_BEEF, 1,
                c -> c.getItem(2).is(Items.COOKED_BEEF));
    }

    @GameTest(maxTicks = 400)
    public void blastFurnaceSmeltsRawIron(GameTestHelper helper) {
        useFromThreeSides(helper, Blocks.BLAST_FURNACE, new ItemStack(Items.RAW_IRON), List.of(new ItemStack(Items.COAL)), Items.IRON_INGOT, 1,
                c -> c.getItem(2).is(Items.IRON_INGOT));
    }

    // From above the nether wart, from the side three water bottles and the blaze powder, from below the potions.
    @GameTest(maxTicks = 700)
    public void brewingStandBrewsAwkwardPotions(GameTestHelper helper) {
        ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        useFromThreeSides(helper, Blocks.BREWING_STAND, new ItemStack(Items.NETHER_WART),
                List.of(water.copy(), water.copy(), water.copy(), new ItemStack(Items.BLAZE_POWDER)), Items.POTION, 3,
                c -> c.getItem(0).getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD));
    }

    /**
     * The robot stands on the block and stores slot 1 downwards (the input), steps back and down and stores the rest
     * from the side (fuel, bottles), goes below and, once {@code done}, takes the output upwards with takeItem.
     */
    private static void useFromThreeSides(GameTestHelper helper, Block block, ItemStack top, List<ItemStack> side, Item output, int count,
            java.util.function.Predicate<Container> done) {
        BlockPos station = START.above();
        helper.setBlock(station, block);
        UUID owner = UUID.randomUUID();
        RobotManager.spawn(helper.getLevel(), owner, Vec3.atBottomCenterOf(helper.absolutePos(station.above())), Direction.SOUTH);
        inv(owner).setItem(0, top);
        for (int i = 0; i < side.size(); i++) inv(owner).setItem(i + 1, side.get(i));
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.storeItems", RobotCommand.DOWN, 1);
        send(results, helper, owner, "agent.move", RobotCommand.BACK, 1);
        send(results, helper, owner, "agent.move", RobotCommand.DOWN, 1);
        send(results, helper, owner, "agent.storeItems", RobotCommand.FORWARD, 0);
        send(results, helper, owner, "agent.move", RobotCommand.DOWN, 1);
        send(results, helper, owner, "agent.move", RobotCommand.FORWARD, 1);
        Container c = container(helper, station);
        List<Result> taken = new ArrayList<>();
        int code = ItemIds.fromModern(BuiltInRegistries.ITEM.getKey(output).toString());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(done.test(c), "done"))
                .thenExecute(() -> {
                    helper.assertValueEqual(results, java.util.Collections.nCopies(6, Result.ok(true)), "store, move, store, move");
                    helper.assertTrue(inv(owner).isEmpty(), "everything went in");
                    send(taken, helper, owner, "agent.takeItem", RobotCommand.UP, code, 64);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(taken, List.of(Result.ok(count)), "taken"))
                .thenExecute(() -> helper.assertValueEqual(inv(owner).countItem(output), count, "output in the robot"))
                .thenSucceed();
    }

    // Issue #93: planks from a log and sticks from planks in the robot's own 2x2 grid; the stick recipe takes any
    // planks (a tag), here spruce and oak together.
    @GameTest(maxTicks = 100)
    public void craftPlanksAndSticks(GameTestHelper helper) {
        UUID owner = spawn(helper);
        inv(owner).setItem(0, new ItemStack(Items.OAK_LOG, 1));
        inv(owner).setItem(5, new ItemStack(Items.SPRUCE_PLANKS, 1));
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.craft", ItemIds.byName("oak_planks"), 4);
        send(results, helper, owner, "agent.craft", ItemIds.byName("stick"), 8);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(4), Result.ok(8)), "made");
            helper.assertValueEqual(inv(owner).countItem(Items.OAK_LOG), 0, "log used");
            helper.assertValueEqual(inv(owner).countItem(Items.STICK), 8, "sticks");
            helper.assertValueEqual(inv(owner).countItem(Items.OAK_PLANKS) + inv(owner).countItem(Items.SPRUCE_PLANKS), 1, "planks left");
        });
    }

    // Issue #93: a furnace (3x3) needs a crafting table next to the robot; missing ingredients consume nothing.
    @GameTest(maxTicks = 100)
    public void threeByThreeNeedsACraftingTable(GameTestHelper helper) {
        UUID owner = spawn(helper);
        inv(owner).setItem(0, new ItemStack(Items.COBBLESTONE, 12));
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.canCraft", ItemIds.byName("furnace"), 1);
        send(results, helper, owner, "agent.craft", ItemIds.byName("furnace"), 1);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(results, List.of(Result.ok(false), Result.ok(0)), "without a table"))
                .thenExecute(() -> {
                    helper.assertValueEqual(inv(owner).countItem(Items.COBBLESTONE), 12, "nothing used");
                    helper.setBlock(START.west(), Blocks.CRAFTING_TABLE);
                    send(results, helper, owner, "agent.canCraft", ItemIds.byName("furnace"), 1);
                    send(results, helper, owner, "agent.canCraft", ItemIds.byName("furnace"), 2);
                    send(results, helper, owner, "agent.craft", ItemIds.byName("furnace"), 2);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(results.size(), 5, "answered"))
                .thenExecute(() -> {
                    helper.assertValueEqual(results.subList(2, 5), List.of(Result.ok(true), Result.ok(false), Result.ok(1)), "with a table");
                    helper.assertValueEqual(inv(owner).countItem(Items.FURNACE), 1, "one furnace");
                    helper.assertValueEqual(inv(owner).countItem(Items.COBBLESTONE), 4, "four left, too few for a second");
                })
                .thenSucceed();
    }

    // Issue #93: missing ingredients answer 0; a full robot makes nothing rather than dropping the output.
    @GameTest(maxTicks = 100)
    public void craftConsumesNothingWhenItCannot(GameTestHelper helper) {
        helper.setBlock(START.east(), Blocks.CRAFTING_TABLE);
        UUID owner = spawn(helper);
        inv(owner).setItem(0, new ItemStack(Items.COBBLESTONE, 7));
        List<Result> missing = send(helper, owner, "agent.craft", ItemIds.byName("furnace"), 1);
        UUID full = spawn(helper, UUID.randomUUID());
        inv(full).setItem(0, new ItemStack(Items.OAK_LOG, 2));
        for (int i = 1; i < RobotCommand.SLOTS; i++) inv(full).setItem(i, new ItemStack(Items.DIRT, 64));
        List<Result> noRoom = send(helper, full, "agent.craft", ItemIds.byName("oak_planks"), 8);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(List.of(missing, noRoom), List.of(List.of(Result.ok(0)), List.of(Result.ok(0))), "made");
            helper.assertValueEqual(inv(owner).countItem(Items.COBBLESTONE), 7, "cobblestone kept");
            helper.assertValueEqual(inv(full).countItem(Items.OAK_LOG), 2, "logs kept");
            helper.assertValueEqual(inv(full).countItem(Items.OAK_PLANKS), 0, "no planks");
        });
    }

    // Issue #115: the stonecutter next to the robot makes stone bricks from stone, one for one; without it nothing.
    @GameTest(maxTicks = 100)
    public void stonecutterCutsStone(GameTestHelper helper) {
        UUID owner = spawnWithStone(helper, 3);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.stonecut", ItemIds.byName("stone_bricks"), 2);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(results, List.of(Result.ok(0)), "without a stonecutter"))
                .thenExecute(() -> {
                    helper.setBlock(START.below(), Blocks.STONECUTTER);
                    send(results, helper, owner, "agent.stonecut", ItemIds.byName("stone_bricks"), 2);
                    send(results, helper, owner, "agent.stonecut", ItemIds.byName("oak_planks"), 1);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(results, List.of(Result.ok(0), Result.ok(2), Result.ok(0)), "with one"))
                .thenExecute(() -> {
                    helper.assertValueEqual(inv(owner).countItem(Items.STONE_BRICKS), 2, "bricks");
                    helper.assertValueEqual(inv(owner).countItem(Items.STONE), 1, "stone left");
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 100)
    public void moveUpAndDownAndSideways(GameTestHelper helper) {
        UUID owner = spawn(helper);
        List<Result> up = send(helper, owner, "agent.move", RobotCommand.UP, 3);
        List<Result> down = send(helper, owner, "agent.move", RobotCommand.DOWN, 2);
        List<Result> left = send(helper, owner, "agent.move", RobotCommand.LEFT, 2);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(up, List.of(Result.ok(true)), "up result");
            helper.assertValueEqual(down, List.of(Result.ok(true)), "down result");
            helper.assertValueEqual(left, List.of(Result.ok(true)), "left result");
            // facing south, left is east (+x)
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.above(1).east(2)), "position");
        });
    }

    @GameTest(maxTicks = 100)
    public void placeUsesSelectedSlot(GameTestHelper helper) {
        UUID owner = spawnWithStone(helper, 5);
        List<Result> results = send(helper, owner, "agent.place", RobotCommand.FORWARD);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "results");
            helper.assertBlockPresent(Blocks.STONE, START.south());
            helper.assertValueEqual(inv(owner).getItem(0).getCount(), 4, "stone left");
        });
    }

    @GameTest(maxTicks = 100)
    public void destroyPutsDropInInventory(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.STONE);
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "agent.destroy", RobotCommand.FORWARD);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "results");
            helper.assertBlockPresent(Blocks.AIR, START.south());
            helper.assertValueEqual(inv(owner).getItem(0).getItem(), Items.COBBLESTONE, "drop");
        });
    }

    @GameTest(maxTicks = 100)
    public void destroyBedrockFails(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.BEDROCK);
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "agent.destroy", RobotCommand.FORWARD);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(false)), "results");
            helper.assertBlockPresent(Blocks.BEDROCK, START.south());
        });
    }

    @GameTest(maxTicks = 100)
    public void inspectReturnsEncodedIds(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.STONE);
        helper.setBlock(START.north(), Blocks.SCULK);
        helper.setBlock(START.above(), Blocks.GRANITE);
        UUID owner = spawn(helper);
        List<Result> stone = send(helper, owner, "agent.inspect", RobotCommand.KIND_BLOCK, RobotCommand.FORWARD);
        List<Result> unknown = send(helper, owner, "agent.inspect", RobotCommand.KIND_BLOCK, RobotCommand.BACK);
        List<Result> granite = send(helper, owner, "agent.inspect", RobotCommand.KIND_BLOCK, RobotCommand.UP);
        List<Result> data = send(helper, owner, "agent.inspect", RobotCommand.KIND_DATA, RobotCommand.UP);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(stone, List.of(Result.ok(1)), "stone");
            helper.assertValueEqual(unknown, List.of(Result.ok(BlockIds.UNKNOWN)), "sculk");
            helper.assertValueEqual(granite, List.of(Result.ok(1 | 1 << 16)), "granite");
            helper.assertValueEqual(data, List.of(Result.ok(1)), "granite data");
        });
    }

    @GameTest(maxTicks = 100)
    public void detectBlockAndRedstone(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.STONE);
        helper.setBlock(START.south(2), Blocks.REDSTONE_BLOCK);
        UUID owner = spawn(helper);
        List<Result> block = send(helper, owner, "agent.detect", RobotCommand.KIND_BLOCK, RobotCommand.FORWARD);
        List<Result> air = send(helper, owner, "agent.detect", RobotCommand.KIND_BLOCK, RobotCommand.BACK);
        List<Result> powered = send(helper, owner, "agent.detect", RobotCommand.KIND_REDSTONE, RobotCommand.FORWARD);
        List<Result> unpowered = send(helper, owner, "agent.detect", RobotCommand.KIND_REDSTONE, RobotCommand.BACK);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(block, List.of(Result.ok(true)), "block");
            helper.assertValueEqual(air, List.of(Result.ok(false)), "air");
            helper.assertValueEqual(powered, List.of(Result.ok(true)), "powered");
            helper.assertValueEqual(unpowered, List.of(Result.ok(false)), "unpowered");
        });
    }

    @GameTest(maxTicks = 100)
    public void setItemForCreativeOwner(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel(); // creative
        // Out of reach so the mock player picks up no items from other tests.
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(START).above(100)));
        UUID owner = spawn(helper, player.getUUID());
        List<Result> set = send(helper, owner, "agent.setItem", BlockIds.byName("oak_planks"), 20, 3);
        List<Result> count = send(helper, owner, "agent.getItemCount", 3);
        List<Result> space = send(helper, owner, "agent.getItemSpace", 3);
        List<Result> diamonds = send(helper, owner, "agent.setItem", ItemIds.byName("diamond"), 3, 5); // issue #125
        helper.succeedWhen(() -> {
            helper.assertValueEqual(set, List.of(Result.ok(null)), "setItem");
            helper.assertValueEqual(diamonds, List.of(Result.ok(null)), "setItem diamond");
            helper.assertValueEqual(inv(owner).getItem(4).getCount() + " " + inv(owner).getItem(4).getItem(), "3 minecraft:diamond", "diamonds");
            helper.assertValueEqual(count, List.of(Result.ok(20)), "count");
            helper.assertValueEqual(space, List.of(Result.ok(44)), "space");
            helper.assertValueEqual(inv(owner).getItem(2).getItem(), Items.OAK_PLANKS, "item");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    @GameTest(maxTicks = 100)
    public void setItemDeniedForOfflineOwner(GameTestHelper helper) {
        UUID owner = spawn(helper); // random owner: not online, and the gamerule is off
        List<Result> set = send(helper, owner, "agent.setItem", 1, 1, 1);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(set.size(), 1, "answered");
            helper.assertValueEqual(set.getFirst().code(), Result.Code.NO_PERMISSION, "code");
            helper.assertTrue(inv(owner).isEmpty(), "inventory stays empty");
        });
    }

    @GameTest(maxTicks = 100)
    public void transferMovesBetweenSlots(GameTestHelper helper) {
        UUID owner = spawnWithStone(helper, 10);
        List<Result> results = send(helper, owner, "agent.transfer", 4, 1, 2);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "results");
            helper.assertValueEqual(inv(owner).getItem(0).getCount(), 6, "source");
            helper.assertValueEqual(inv(owner).getItem(1).getCount(), 4, "destination");
        });
    }

    @GameTest(maxTicks = 100)
    public void dropSpawnsItemInFront(GameTestHelper helper) {
        UUID owner = spawnWithStone(helper, 10);
        List<Result> results = send(helper, owner, "agent.drop", RobotCommand.FORWARD, 1, 3);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "results");
            helper.assertValueEqual(inv(owner).getItem(0).getCount(), 7, "left");
            List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(START.south())).inflate(1));
            helper.assertValueEqual(items.stream().mapToInt(i -> i.getItem().getCount()).sum(), 3, "dropped");
        });
    }

    @GameTest(maxTicks = 100)
    public void placeOnMoveFillsVacatedBlocks(GameTestHelper helper) {
        UUID owner = spawnWithStone(helper, 10);
        List<Result> assist = send(helper, owner, "agent.setAssist", RobotCommand.PLACE_ON_MOVE, true);
        List<Result> move = send(helper, owner, "agent.move", RobotCommand.FORWARD, 3);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(assist, List.of(Result.ok(null)), "assist");
            helper.assertValueEqual(move, List.of(Result.ok(true)), "move");
            helper.assertBlockPresent(Blocks.STONE, START);
            helper.assertBlockPresent(Blocks.STONE, START.south());
            helper.assertBlockPresent(Blocks.STONE, START.south(2));
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.south(3)), "position");
            helper.assertValueEqual(inv(owner).getItem(0).getCount(), 7, "stone left");
        });
    }

    @GameTest
    public void blockTableUsesRealBlockIds(GameTestHelper helper) {
        for (String id : BlockIds.modernIds()) {
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id)), "no block " + id);
        }
        helper.succeed();
    }

    // Issue #125: the Item table holds every vanilla item (except air), and nothing else.
    @GameTest
    public void itemTableCoversEveryItem(GameTestHelper helper) {
        Set<String> registry = new java.util.TreeSet<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) registry.add(id.toString());
        registry.remove("minecraft:air");
        //? if >=26.3 {
        helper.assertValueEqual(new java.util.TreeSet<>(ItemIds.modernIds()), registry, "items");
        //?} else {
        /*// Issue #189: the table stays 26.3's. The entries this version has (renamed ones included, see McCompat.itemId)
        // are as many as its registry holds, so every item here has a code; the newer ones are refused.
        int present = 0;
        for (String id : ItemIds.modernIds()) if (ItemIds.inThisVersion(id)) present++;
        helper.assertValueEqual(present, registry.size(), "items this version has");
        *///?}
        helper.succeed();
    }

    // Issue #125: items without a block form are counted and taken by their Item code; a Block code still works.
    @GameTest(maxTicks = 100)
    public void containerCallsTakeItems(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.CHEST);
        Container chest = container(helper, START.south());
        chest.setItem(0, new ItemStack(Items.COAL, 12));
        chest.setItem(1, new ItemStack(Items.IRON_INGOT, 5));
        chest.setItem(2, new ItemStack(Items.COBBLESTONE, 7));
        UUID owner = spawn(helper);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.containerCount", RobotCommand.FORWARD, ItemIds.byName("coal"));
        send(results, helper, owner, "agent.takeItem", RobotCommand.FORWARD, ItemIds.byName("iron_ingot"), 64);
        send(results, helper, owner, "agent.containerCount", RobotCommand.FORWARD, BlockIds.byName("COBBLESTONE"));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(12), Result.ok(5), Result.ok(7)), "results");
            helper.assertValueEqual(inv(owner).countItem(Items.IRON_INGOT), 5, "iron in the robot");
        });
    }

    @GameTest(maxTicks = 100)
    public void robotSurvivesSaveAndLoad(GameTestHelper helper) {
        UUID owner = spawnWithStone(helper, 12);
        RobotEntity robot = RobotManager.robot(owner);
        robot.setYRot(Direction.EAST.toYRot());
        send(helper, owner, "agent.setSlot", 5);
        send(helper, owner, "agent.setAssist", RobotCommand.PLACE_ON_MOVE, true);
        send(helper, owner, "agent.setGravity", true);
        List<Result> home = send(helper, owner, "agent.setHome");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(home.size(), 1, "setSlot, setAssist, setGravity and setHome done");
            CompoundTag tag = GameTestCompat.save(robot, helper.getLevel().registryAccess());

            RobotEntity copy = new RobotEntity(Codeling.ROBOT.get(), helper.getLevel());
            GameTestCompat.load(copy, tag, helper.getLevel().registryAccess());
            helper.assertValueEqual(copy.owner(), owner, "owner");
            helper.assertValueEqual(copy.getDirection(), Direction.EAST, "facing");
            helper.assertValueEqual(copy.blockPosition(), robot.blockPosition(), "position");
            helper.assertValueEqual(copy.getUUID(), robot.getUUID(), "uuid");
            helper.assertValueEqual(copy.inventory().getItem(0).getCount(), 12, "inventory");
            helper.assertValueEqual(copy.selectedSlot(), 5, "selected slot");
            helper.assertTrue(copy.placeOnMove(), "place on move");
            helper.assertTrue(copy.gravityMode(), "gravity mode");
            helper.assertValueEqual(copy.home(), robot.home(), "home");
            helper.assertValueEqual(copy.home().pos(), helper.absolutePos(START), "home position");
        });
    }

    @GameTest(maxTicks = 100)
    public void gravityFallsAfterSidewaysStep(GameTestHelper helper) {
        helper.setBlock(START.south().below(2), Blocks.STONE);
        UUID owner = spawn(helper);
        List<Result> gravity = send(helper, owner, "agent.setGravity", true);
        List<Result> move = send(helper, owner, "agent.move", RobotCommand.FORWARD, 1);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(gravity, List.of(Result.ok(null)), "gravity");
            helper.assertValueEqual(move, List.of(Result.ok(true)), "move");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.south().below()), "position");
        });
    }

    @GameTest(maxTicks = 100)
    public void gravityDoesNotFallAfterUp(GameTestHelper helper) {
        UUID owner = spawn(helper);
        send(helper, owner, "agent.setGravity", true);
        List<Result> up = send(helper, owner, "agent.move", RobotCommand.UP, 2);
        helper.runAfterDelay(30, () -> {
            helper.assertValueEqual(up, List.of(Result.ok(true)), "up");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.above(2)), "position");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void gravityFallsAfterDestroyDown(GameTestHelper helper) {
        helper.setBlock(START.below(), Blocks.STONE);
        helper.setBlock(START.below(2), Blocks.STONE);
        UUID owner = spawn(helper);
        send(helper, owner, "agent.setGravity", true);
        List<Result> destroy = send(helper, owner, "agent.destroy", RobotCommand.DOWN);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(destroy, List.of(Result.ok(true)), "destroy");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.below()), "position");
        });
    }

    @GameTest(maxTicks = 100)
    public void gravityStopsAtWorldBottom(GameTestHelper helper) {
        // Clears a pocket in the bottom layer below the test area, so the fall ends at the build limit, not on a block.
        BlockPos top = helper.absolutePos(START).atY(helper.getLevel().getMinY() + 1);
        for (BlockPos pos : List.of(top, top.south(), top.south().below())) helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        UUID owner = UUID.randomUUID();
        RobotManager.spawn(helper.getLevel(), owner, Vec3.atBottomCenterOf(top), Direction.SOUTH);
        send(helper, owner, "agent.setGravity", true);
        List<Result> move = send(helper, owner, "agent.move", RobotCommand.FORWARD, 1);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(move, List.of(Result.ok(true)), "move");
            helper.assertValueEqual(at(helper, owner), top.south().below(), "position");
        });
    }

    @GameTest(maxTicks = 100)
    public void gravityStepsUpOneBlockBump(GameTestHelper helper) {
        for (int i = 0; i <= 3; i++) helper.setBlock(START.south(i).below(), Blocks.STONE);
        helper.setBlock(START.south(2), Blocks.STONE);
        UUID owner = spawn(helper);
        send(helper, owner, "agent.setGravity", true);
        List<Result> move = send(helper, owner, "agent.move", RobotCommand.FORWARD, 3);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(move, List.of(Result.ok(true)), "move");
            // Up onto the bump, then down again on the far side.
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.south(3)), "position");
        });
    }

    @GameTest(maxTicks = 100)
    public void gravityStopsAtTwoBlockWall(GameTestHelper helper) {
        helper.setBlock(START.below(), Blocks.STONE);
        helper.setBlock(START.south(), Blocks.STONE);
        helper.setBlock(START.south().above(), Blocks.STONE);
        UUID owner = spawn(helper);
        send(helper, owner, "agent.setGravity", true);
        List<Result> move = send(helper, owner, "agent.move", RobotCommand.FORWARD, 2);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(move, List.of(Result.ok(false)), "move");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START), "position");
        });
    }

    private static void door(GameTestHelper helper, BlockPos pos, Block door) {
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, door);
        helper.setBlock(pos.above(), door.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    private static boolean open(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(BlockStateProperties.OPEN);
    }

    // Issues #102 and #106: wooden doors, gates and trapdoors open and close by hand; iron doors need redstone.
    @GameTest(maxTicks = 100)
    public void interactOpensDoorsGatesAndTrapdoors(GameTestHelper helper) {
        // Facing south: forward is south, left east, right west, back north.
        door(helper, START.south(), Blocks.OAK_DOOR);
        door(helper, START.north(), Blocks.IRON_DOOR);
        helper.setBlock(START.east(), Blocks.OAK_FENCE_GATE);
        helper.setBlock(START.west(), Blocks.OAK_TRAPDOOR);
        UUID owner = spawn(helper);
        List<Result> door = new ArrayList<>();
        send(door, helper, owner, "agent.interact", RobotCommand.FORWARD);
        send(door, helper, owner, "agent.isOpen", RobotCommand.FORWARD);
        List<Result> gate = send(helper, owner, "agent.interact", RobotCommand.LEFT);
        List<Result> trapdoor = send(helper, owner, "agent.interact", RobotCommand.RIGHT);
        List<Result> iron = new ArrayList<>();
        send(iron, helper, owner, "agent.interact", RobotCommand.BACK);
        send(iron, helper, owner, "agent.isOpen", RobotCommand.BACK);
        List<Result> notOpenable = send(helper, owner, "agent.isOpen", RobotCommand.UP);
        List<Result> nothing = send(helper, owner, "agent.interact", RobotCommand.UP);
        List<Result> closed = new ArrayList<>();
        send(closed, helper, owner, "agent.interact", RobotCommand.FORWARD);
        send(closed, helper, owner, "agent.isOpen", RobotCommand.FORWARD);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(door, List.of(Result.ok(true), Result.ok(true)), "door opens");
            helper.assertValueEqual(gate, List.of(Result.ok(true)), "gate");
            helper.assertTrue(open(helper, START.east()), "gate open");
            helper.assertValueEqual(trapdoor, List.of(Result.ok(true)), "trapdoor");
            helper.assertTrue(open(helper, START.west()), "trapdoor open");
            helper.assertValueEqual(iron, List.of(Result.ok(false), Result.ok(false)), "iron door");
            helper.assertValueEqual(notOpenable, List.of(Result.ok(false)), "air is never open");
            helper.assertValueEqual(nothing, List.of(Result.ok(false)), "nothing to use");
            helper.assertValueEqual(closed, List.of(Result.ok(true), Result.ok(false)), "door closes");
            helper.assertTrue(!open(helper, START.south().above()), "both halves closed");
        });
    }

    // Issues #102 and #107: a button and a lever react and give out 15; a pressed button does not react again.
    @GameTest(maxTicks = 100)
    public void interactPressesButtonsAndFlipsLevers(GameTestHelper helper) {
        helper.setBlock(START.south().below(), Blocks.STONE);
        helper.setBlock(START.north().below(), Blocks.STONE);
        helper.setBlock(START.south(), Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
        helper.setBlock(START.north(), Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
        UUID owner = spawn(helper);
        List<Result> button = new ArrayList<>();
        send(button, helper, owner, "agent.redstonePower", RobotCommand.FORWARD);
        send(button, helper, owner, "agent.interact", RobotCommand.FORWARD);
        send(button, helper, owner, "agent.redstonePower", RobotCommand.FORWARD);
        send(button, helper, owner, "agent.interact", RobotCommand.FORWARD);
        List<Result> lever = new ArrayList<>();
        send(lever, helper, owner, "agent.interact", RobotCommand.BACK);
        send(lever, helper, owner, "agent.redstonePower", RobotCommand.BACK);
        send(lever, helper, owner, "agent.interact", RobotCommand.BACK);
        send(lever, helper, owner, "agent.redstonePower", RobotCommand.BACK);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(button, List.of(Result.ok(0), Result.ok(true), Result.ok(15), Result.ok(false)), "button");
            helper.assertValueEqual(lever, List.of(Result.ok(true), Result.ok(15), Result.ok(true), Result.ok(0)), "lever on, then off");
        });
    }

    // Issue #107: redstone dust reads its own power as F3 shows it; a plain block reads how strongly it is powered.
    @GameTest(maxTicks = 100)
    public void redstonePowerOfDustAndPoweredBlocks(GameTestHelper helper) {
        helper.setBlock(START.south().below(), Blocks.STONE);
        helper.setBlock(START.south(2).below(), Blocks.STONE);
        helper.setBlock(START.south(3), Blocks.REDSTONE_BLOCK);
        helper.setBlock(START.south(2), Blocks.REDSTONE_WIRE);
        helper.setBlock(START.south(), Blocks.REDSTONE_WIRE);
        helper.setBlock(START.east(2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(START.east(), Blocks.STONE);
        UUID owner = spawn(helper);
        List<Result> dust = send(helper, owner, "agent.redstonePower", RobotCommand.FORWARD);
        List<Result> powered = send(helper, owner, "agent.redstonePower", RobotCommand.LEFT);
        List<Result> air = send(helper, owner, "agent.redstonePower", RobotCommand.UP);
        List<Result> detect = send(helper, owner, "agent.detect", RobotCommand.KIND_REDSTONE, RobotCommand.LEFT);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(dust, List.of(Result.ok(14)), "dust one block from the source");
            helper.assertValueEqual(powered, List.of(Result.ok(15)), "stone next to a redstone block");
            helper.assertValueEqual(air, List.of(Result.ok(0)), "air");
            helper.assertValueEqual(detect, List.of(Result.ok(true)), "detect Redstone is unchanged");
        });
    }

    private static List<Result> moveTo(GameTestHelper helper, UUID owner, BlockPos relative) {
        BlockPos p = helper.absolutePos(relative);
        return send(helper, owner, "agent.moveTo", p.getX(), p.getY(), p.getZ());
    }

    // A stone floor under the test area (y of START minus one).
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, START.getY() - 1, z), Blocks.STONE);
    }

    // Issue #45: a straight path, flying (gravity off), at the normal move speed.
    @GameTest(maxTicks = 100)
    public void moveToStraightPath(GameTestHelper helper) {
        UUID owner = spawn(helper);
        BlockPos target = START.south(4).east(2).above(2);
        List<Result> results = moveTo(helper, owner, target);
        helper.runAfterDelay(4, () -> helper.assertTrue(results.isEmpty(), "walks visibly, not in one tick"));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "arrived");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(target), "position");
        });
    }

    // Issue #45: walking (gravity on) around a wall two blocks high.
    @GameTest(maxTicks = 200)
    public void moveToDetourAroundAWall(GameTestHelper helper) {
        floor(helper);
        for (int x = 0; x < 6; x++) for (int y = 0; y < 2; y++) helper.setBlock(new BlockPos(x, START.getY() + y, 3), Blocks.STONE);
        UUID owner = spawn(helper);
        send(helper, owner, "agent.setGravity", true);
        BlockPos target = START.south(4);
        List<Result> results = moveTo(helper, owner, target);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "arrived");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(target), "position");
        });
    }

    // Issue #44: a solid target (a log from findNearest) ends next to it, facing it.
    @GameTest(maxTicks = 100)
    public void moveToASolidBlockStopsNextToIt(GameTestHelper helper) {
        helper.setBlock(START.east(4), Blocks.OAK_LOG);
        UUID owner = spawn(helper);
        List<Result> results = moveTo(helper, owner, START.east(4));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "arrived");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.east(3)), "position");
            helper.assertValueEqual(RobotManager.robot(owner).getDirection(), Direction.EAST, "facing the log");
        });
    }

    private static void box(GameTestHelper helper, BlockPos pos) {
        for (Direction d : Direction.values()) helper.setBlock(pos.relative(d), Blocks.STONE);
    }

    // Issue #45: no path, from a robot shut in and to a target shut in. GameTests are encased in barriers, so these searches
    // end when the test area is used up; RobotPathTest covers the node cap and the per-tick budget.
    @GameTest(maxTicks = 200)
    public void moveToNoPath(GameTestHelper helper) {
        box(helper, START);
        UUID boxed = spawn(helper);
        List<Result> fromTheBox = moveTo(helper, boxed, START.south(5));
        BlockPos shut = START.south(4).east(4).above(2);
        box(helper, shut);
        UUID free = spawn(helper, UUID.randomUUID());
        RobotManager.robot(free).snapTo(Vec3.atBottomCenterOf(helper.absolutePos(START.above(3))), 0, 0);
        List<Result> intoTheBox = moveTo(helper, free, shut);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(fromTheBox, List.of(Result.ok(false)), "robot shut in");
            helper.assertValueEqual(intoTheBox, List.of(Result.ok(false)), "target shut in");
            helper.assertValueEqual(at(helper, free), helper.absolutePos(START.above(3)), "did not move");
        });
    }

    // Issue #46: stop cancels a moveTo (the search or the walk: both live on the queue entry that stop drops).
    @GameTest(maxTicks = 100)
    public void stopCancelsAMoveTo(GameTestHelper helper) {
        UUID owner = spawn(helper);
        List<Result> results = moveTo(helper, owner, START.south(6));
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(results.isEmpty(), "still on the way");
            RobotManager.queue(owner).stop();
            helper.assertValueEqual(results.size() == 1 ? results.get(0).code() : null, Result.Code.STOPPED, "stopped");
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(at(helper, owner).getZ() < helper.absolutePos(START.south(6)).getZ(), "stopped part way");
            helper.succeed();
        });
    }

    // Issue #109: walking through a closed wooden door, which the robot opens on the way; the way round would mean a fall.
    @GameTest(maxTicks = 200)
    public void moveToOpensADoorOnTheWay(GameTestHelper helper) {
        floor(helper);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 2; y++) helper.setBlock(new BlockPos(x, START.getY() + y, 3), Blocks.STONE);
        door(helper, START.south(2), Blocks.OAK_DOOR);
        UUID owner = spawn(helper);
        send(helper, owner, "agent.setGravity", true);
        BlockPos target = START.south(4);
        List<Result> results = moveTo(helper, owner, target);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(true)), "arrived");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(target), "position");
            helper.assertTrue(open(helper, START.south(2)), "door opened");
        });
    }

    // A line of path blocks at y 1 (under the robot's feet at START), from one corner to the other, both included.
    private static void path(GameTestHelper helper, Block block, int x1, int z1, int x2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) helper.setBlock(new BlockPos(x, 1, z), block);
        }
    }

    private static List<Result> followPath(GameTestHelper helper, UUID owner) {
        return send(helper, owner, "agent.followPath", 0);
    }

    // Issue #179: a straight path to its end, at the normal move speed, facing the way it went.
    @GameTest(maxTicks = 100)
    public void followPathStraightToTheEnd(GameTestHelper helper) {
        path(helper, Blocks.STONE_BRICKS, 1, 1, 1, 6);
        UUID owner = spawn(helper);
        List<Result> results = followPath(helper, owner);
        helper.runAfterDelay(4, () -> helper.assertTrue(results.isEmpty(), "walks visibly, not in one tick"));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(RobotTrail.END)), "end of the path");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.south(5)), "position");
            helper.assertValueEqual(RobotManager.robot(owner).getDirection(), Direction.SOUTH, "facing");
        });
    }

    // Issue #179: round a corner.
    @GameTest(maxTicks = 100)
    public void followPathRoundACorner(GameTestHelper helper) {
        path(helper, Blocks.STONE_BRICKS, 1, 1, 1, 4);
        path(helper, Blocks.STONE_BRICKS, 2, 4, 5, 4);
        UUID owner = spawn(helper);
        List<Result> results = followPath(helper, owner);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(RobotTrail.END)), "end of the path");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(new BlockPos(5, 2, 4)), "position");
            helper.assertValueEqual(RobotManager.robot(owner).getDirection(), Direction.EAST, "facing");
        });
    }

    // Issue #179: a T junction stops on the junction block facing the way the robot came; turn and follow on.
    @GameTest(maxTicks = 150)
    public void followPathStopsAtAJunction(GameTestHelper helper) {
        path(helper, Blocks.STONE_BRICKS, 1, 1, 1, 4);
        path(helper, Blocks.STONE_BRICKS, 0, 5, 4, 5);
        UUID owner = spawn(helper);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.followPath", 0);
        // Facing south on the junction block, left is east: only then does the second call end at the east branch's end.
        send(results, helper, owner, "agent.turn", RobotCommand.LEFT_TURN);
        send(results, helper, owner, "agent.followPath", 0);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(RobotTrail.JUNCTION), Result.ok(null), Result.ok(RobotTrail.END)), "results");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(new BlockPos(4, 2, 5)), "position");
        });
    }

    // Issue #179: one step up and one step down, walking (gravity on) and flying.
    private static void followPathSteps(GameTestHelper helper, boolean gravity) {
        path(helper, Blocks.STONE_BRICKS, 1, 1, 1, 2);
        helper.setBlock(new BlockPos(1, 2, 3), Blocks.STONE_BRICKS);
        helper.setBlock(new BlockPos(1, 2, 4), Blocks.STONE_BRICKS);
        path(helper, Blocks.STONE_BRICKS, 1, 5, 1, 6);
        UUID owner = spawn(helper);
        send(helper, owner, "agent.setGravity", gravity);
        List<Result> results = followPath(helper, owner);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(RobotTrail.END)), "end of the path");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.south(5)), "position");
        });
    }

    @GameTest(maxTicks = 100)
    public void followPathStepsUpAndDownWalking(GameTestHelper helper) {
        followPathSteps(helper, true);
    }

    @GameTest(maxTicks = 100)
    public void followPathStepsUpAndDownFlying(GameTestHelper helper) {
        followPathSteps(helper, false);
    }

    // Issue #179: a ring stops when the robot is back where it started.
    @GameTest(maxTicks = 150)
    public void followPathRoundALoop(GameTestHelper helper) {
        path(helper, Blocks.STONE_BRICKS, 1, 1, 5, 1);
        path(helper, Blocks.STONE_BRICKS, 5, 1, 5, 5);
        path(helper, Blocks.STONE_BRICKS, 1, 5, 5, 5);
        path(helper, Blocks.STONE_BRICKS, 1, 1, 1, 5);
        UUID owner = spawn(helper);
        List<Result> results = followPath(helper, owner);
        helper.runAfterDelay(30, () -> helper.assertTrue(results.isEmpty(), "still going round"));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(RobotTrail.END)), "back at the start");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START), "position");
            helper.assertValueEqual(RobotManager.robot(owner).getDirection(), Direction.WEST, "facing the way it came");
        });
    }

    // Issue #179: a block on the path stops the robot in front of it.
    @GameTest(maxTicks = 100)
    public void followPathBlocked(GameTestHelper helper) {
        path(helper, Blocks.STONE_BRICKS, 1, 1, 1, 6);
        helper.setBlock(START.south(3), Blocks.STONE);
        UUID owner = spawn(helper);
        List<Result> results = followPath(helper, owner);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(RobotTrail.BLOCKED)), "blocked");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START.south(2)), "position");
        });
    }

    // Issue #179: followPath(block) follows that block from a robot standing on another one, and ignores other paths.
    @GameTest(maxTicks = 100)
    public void followPathOfAGivenBlock(GameTestHelper helper) {
        helper.setBlock(START.below(), Blocks.STONE);
        path(helper, Blocks.COBBLESTONE, 1, 2, 1, 5);
        path(helper, Blocks.STONE_BRICKS, 2, 1, 5, 1);
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "agent.followPath", BlockIds.byName("STONE_BRICKS"));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(RobotTrail.END)), "end of the stone bricks");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(new BlockPos(5, 2, 1)), "position");
            helper.assertValueEqual(RobotManager.robot(owner).getDirection(), Direction.EAST, "facing");
        });
    }

    // followPath(block) with no such block under or next to the robot: End, and it says it is not on the path.
    @GameTest(maxTicks = 100)
    public void followPathNotOnThePathSaysSo(GameTestHelper helper) {
        helper.setBlock(START.below(), Blocks.STONE);
        path(helper, Blocks.STONE_BRICKS, 4, 1, 6, 1);
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "agent.followPath", BlockIds.byName("STONE_BRICKS"));
        String message = "I am not on the path. Put me on the stone bricks first.";
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(RobotTrail.END, message)), "not on the path");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START), "position");
            helper.assertValueEqual(RobotManager.robot(owner).said(), message, "said");
        });
    }

    // Issue #127: free blocks before the first solid one, or max when nothing is hit. Measuring never moves the robot.
    @GameTest(maxTicks = 100)
    public void measureFreeBlocks(GameTestHelper helper) {
        helper.setBlock(START.south(4), Blocks.STONE);
        UUID owner = spawn(helper);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.measure", RobotCommand.FORWARD, 32);
        send(results, helper, owner, "agent.measure", RobotCommand.FORWARD, 2);
        send(results, helper, owner, "agent.measure", RobotCommand.UP, 3);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(3), Result.ok(2), Result.ok(3)), "measured");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START), "did not move");
        });
    }

    // Issue #128: a wall in front, 4 wide to the left (east, facing south), 3 high at the robot's column.
    @GameTest(maxTicks = 100)
    public void measureRunOfAWall(GameTestHelper helper) {
        for (int x = 1; x <= 4; x++) helper.setBlock(new BlockPos(x, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 3, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 4, 2), Blocks.STONE);
        UUID owner = spawn(helper);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.measureRun", RobotCommand.FORWARD, RobotCommand.LEFT, 32);
        send(results, helper, owner, "agent.measureRun", RobotCommand.FORWARD, RobotCommand.UP, 32);
        send(results, helper, owner, "agent.measureRun", RobotCommand.FORWARD, RobotCommand.RIGHT, 32);
        send(results, helper, owner, "agent.measureRun", RobotCommand.FORWARD, RobotCommand.LEFT, 2);
        send(results, helper, owner, "agent.measureRun", RobotCommand.BACK, RobotCommand.UP, 10);
        helper.succeedWhen(() -> helper.assertValueEqual(results,
                List.of(Result.ok(4), Result.ok(3), Result.ok(1), Result.ok(2), Result.ok(0)), "wall lengths"));
    }

    // Issue #129: the free width across the robot, its own block included; max when one side is open.
    @GameTest(maxTicks = 100)
    public void measureGapAcrossTheRobot(GameTestHelper helper) {
        helper.setBlock(START.west(), Blocks.STONE);
        helper.setBlock(START.east(3), Blocks.STONE);
        helper.setBlock(START.below(), Blocks.STONE);
        helper.setBlock(START.above(3), Blocks.STONE);
        UUID owner = spawn(helper);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.measureGap", 0, 32);
        send(results, helper, owner, "agent.measureGap", 1, 32);
        send(results, helper, owner, "agent.measureGap", 2, 3);
        helper.succeedWhen(() -> helper.assertValueEqual(results, List.of(Result.ok(3), Result.ok(3), Result.ok(3)), "gaps"));
    }

    // Issue #130: the heightmap without leaves (no barrier ceiling, so the sky is open), and a profile along a line.
    @GameTest(maxTicks = 100, skyAccess = true)
    public void groundHeightAndProfile(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(START.south(2), Blocks.STONE);
        helper.setBlock(START.south(2).above(), Blocks.STONE);
        helper.setBlock(START.south(3).above(2), Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
        UUID owner = spawn(helper);
        BlockPos pillar = helper.absolutePos(START.south(2));
        int ground = helper.absolutePos(START.below()).getY();
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.groundHeight", pillar.getX(), pillar.getZ());
        send(results, helper, owner, "agent.heightProfile", RobotCommand.FORWARD, 3);
        helper.succeedWhen(() -> helper.assertValueEqual(results,
                List.of(Result.ok(pillar.getY() + 1), Result.ok(List.of(ground, pillar.getY() + 1, ground))), "heights"));
    }

    // Issue #131: air and replaceable blocks (a snow layer) are free, stone is not. A 32-high box takes more than one tick.
    @GameTest(maxTicks = 100)
    public void areaFreeAndCountSolid(GameTestHelper helper) {
        helper.setBlock(START.south(2), Blocks.STONE);
        helper.setBlock(START.south(2).above(), Blocks.SNOW);
        helper.setBlock(START.south(4).east(2).above(2), Blocks.STONE);
        UUID owner = spawn(helper);
        BlockPos a = helper.absolutePos(START.south(2)), b = helper.absolutePos(START.south(4).east(2).above(2));
        BlockPos c = helper.absolutePos(START.south(2).above()), d = helper.absolutePos(START.south(3).above(2));
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.isAreaFree", a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        send(results, helper, owner, "agent.countSolid", a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        send(results, helper, owner, "agent.isAreaFree", c.getX(), c.getY(), c.getZ(), d.getX(), d.getY(), d.getZ());
        helper.succeedWhen(() -> helper.assertValueEqual(results, List.of(Result.ok(false), Result.ok(2), Result.ok(true)), "box"));
    }

    // Owner's rule: reads at a given position stay within findNearest's radius of the robot on every axis.
    @GameTest(maxTicks = 100)
    public void measuringFarFromTheRobotIsOutOfRange(GameTestHelper helper) {
        UUID owner = spawn(helper);
        BlockPos at = helper.absolutePos(START);
        int far = RobotCommand.MAX_FIND_RADIUS + 1;
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.groundHeight", at.getX() + far, at.getZ());
        send(results, helper, owner, "agent.countSolid", at.getX(), at.getY() + far, at.getZ(), at.getX(), at.getY() + far - 1, at.getZ());
        send(results, helper, owner, "agent.isAreaFree", at.getX(), at.getY(), at.getZ() - far, at.getX(), at.getY(), at.getZ() - far + 1);
        send(results, helper, owner, "agent.isAreaFree", at.getX(), at.getY(), at.getZ(), at.getX(), at.getY(), at.getZ());
        helper.succeedWhen(() -> helper.assertValueEqual(results.stream().map(r -> r.ok() ? r.toString() : r.code().name()).toList(),
                List.of("OUT_OF_RANGE", "OUT_OF_RANGE", "OUT_OF_RANGE", "ok true"), "answers"));
    }

    // Shares the scan budget with twentyRobotsSearchWalkAndCraft when they run together, hence the long timeout.
    @GameTest(maxTicks = 400)
    public void countSolidOfABigBoxSpreadsOverTicks(GameTestHelper helper) {
        UUID owner = spawn(helper);
        BlockPos a = helper.absolutePos(new BlockPos(0, -12, 0)), b = helper.absolutePos(new BlockPos(7, 19, 7));
        List<Result> results = send(helper, owner, "agent.countSolid", a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        BlockPos e = helper.absolutePos(new BlockPos(0, 0, 0)), f = helper.absolutePos(new BlockPos(31, 31, 31));
        List<Result> big = send(helper, owner, "agent.countSolid", e.getX(), e.getY(), e.getZ(), f.getX(), f.getY(), f.getZ());
        helper.runAfterDelay(3, () -> helper.assertTrue(big.isEmpty(), "32 x 32 x 32 blocks take more than one tick"));
        helper.succeedWhen(() -> {
            helper.assertTrue(results.size() == 1 && results.get(0).ok() && (int) results.get(0).value() > 0, "the barriers below count: " + results);
            helper.assertTrue(big.size() == 1 && big.get(0).ok(), "counted: " + big);
        });
    }

    // Issue #132: the corners of the blocks connected to the block in front, capped by max; null when nothing is in front.
    @GameTest(maxTicks = 100)
    public void findBoundsOfABuilding(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) for (int y = 2; y <= 4; y++) for (int z = 2; z <= 3; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
        UUID owner = spawn(helper);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.findBounds", 32);
        send(results, helper, owner, "agent.findBounds", 2);
        send(results, helper, owner, "agent.turn", RobotCommand.LEFT_TURN);
        send(results, helper, owner, "agent.findBounds", 32);
        BlockPos min = helper.absolutePos(new BlockPos(1, 2, 2)), max = helper.absolutePos(new BlockPos(3, 4, 3)), capped = helper.absolutePos(new BlockPos(2, 3, 3));
        helper.succeedWhen(() -> helper.assertValueEqual(results, List.of(
                Result.ok(List.of(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ())),
                Result.ok(List.of(min.getX(), min.getY(), min.getZ(), capped.getX(), capped.getY(), capped.getZ())),
                Result.ok(null), Result.ok(null)), "bounds"));
    }

    // Issue #78: goHome without a home answers false; with one it walks back like moveTo.
    @GameTest(maxTicks = 200)
    public void setHomeThenGoHome(GameTestHelper helper) {
        UUID owner = spawn(helper);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.goHome");
        send(results, helper, owner, "agent.setHome");
        send(results, helper, owner, "agent.move", RobotCommand.FORWARD, 3);
        send(results, helper, owner, "agent.turn", RobotCommand.LEFT_TURN);
        send(results, helper, owner, "agent.move", RobotCommand.FORWARD, 2);
        send(results, helper, owner, "agent.goHome");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(false), Result.ok(null), Result.ok(true), Result.ok(null), Result.ok(true), Result.ok(true)), "results");
            helper.assertValueEqual(at(helper, owner), helper.absolutePos(START), "home");
        });
    }

    // Issue #77: /robot come walks to the player's block when there is a path, else teleports there.
    @GameTest(maxTicks = 300)
    public void comeWalksOrTeleports(GameTestHelper helper) {
        UUID walker = spawn(helper);
        List<Result> walked = come(helper, walker, START.south(4));
        BlockPos shut = START.south(4).east(4).above(2);
        box(helper, shut);
        UUID boxed = spawn(helper, UUID.randomUUID());
        List<Result> teleported = come(helper, boxed, shut);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(walked, List.of(Result.ok("walked")), "walked");
            helper.assertValueEqual(at(helper, walker), helper.absolutePos(START.south(4)), "walker");
            helper.assertValueEqual(teleported, List.of(Result.ok("teleported")), "teleported");
            helper.assertValueEqual(at(helper, boxed), helper.absolutePos(shut), "boxed");
        });
    }

    // What RobotCommands sends for /robot come, with the player's block as the target.
    private static List<Result> come(GameTestHelper helper, UUID owner, BlockPos relative) {
        BlockPos p = helper.absolutePos(relative);
        List<Result> results = new ArrayList<>();
        try {
            RobotManager.submit(owner, RobotManager.queue(owner).run(), new RobotCommand(RobotCommand.Op.COME, p.getX(), p.getY(), p.getZ()), results::add);
        } catch (RobotException e) {
            helper.fail("come rejected: " + e.result());
        }
        return results;
    }

    // Issue #86: entity kinds in the block next to the robot and in the cube around it. Robot faces south at START.
    @GameTest(maxTicks = 100)
    public void detectEntityAndCountNearby(GameTestHelper helper) {
        helper.spawnWithNoFreeWill(EntityTypes.PIG, START.south()).setNoGravity(true);
        helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, START.north()).setNoGravity(true);
        ItemEntity item = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.STONE), 0, 0, 0);
        item.setPos(Vec3.atBottomCenterOf(helper.absolutePos(START.east(2))));
        item.setNoGravity(true);
        helper.getLevel().addFreshEntity(item);
        UUID owner = spawn(helper);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.detectEntity", 1, RobotCommand.FORWARD);
        send(results, helper, owner, "agent.detectEntity", 0, RobotCommand.FORWARD);
        send(results, helper, owner, "agent.detectEntity", 0, RobotCommand.BACK);
        send(results, helper, owner, "agent.detectEntity", 3, RobotCommand.LEFT);
        send(results, helper, owner, "agent.countNearby", 3, 1);
        send(results, helper, owner, "agent.countNearby", 3, 2);
        send(results, helper, owner, "agent.countNearby", 1, 8);
        send(results, helper, owner, "agent.countNearby", 2, 8);
        helper.succeedWhen(() -> helper.assertValueEqual(results, List.of(Result.ok(true), Result.ok(false), Result.ok(true), Result.ok(false),
                Result.ok(0), Result.ok(1), Result.ok(1), Result.ok(0)), "entities"));
    }

    // Block positions as agent.radar answers them: one flat list, x y z for each entity.
    private static List<Integer> flat(GameTestHelper helper, BlockPos... relative) {
        List<Integer> list = new ArrayList<>();
        for (BlockPos r : relative) {
            BlockPos p = helper.absolutePos(r);
            list.addAll(List.of(p.getX(), p.getY(), p.getZ()));
        }
        return list;
    }

    // A real player (not the mock, which always reports creative) standing in a block of the test.
    private static ServerPlayer playerAt(GameTestHelper helper, BlockPos relative, GameType mode) {
        ServerPlayer player = survivalOwner(helper);
        player.setGameMode(mode);
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(relative)));
        return player;
    }

    // Issue #159: radar finds a kind nearest first within the radius cube; invisible players and spectators never show.
    @GameTest(maxTicks = 100)
    public void radarSortsByDistanceAndHidesInvisiblePlayers(GameTestHelper helper) {
        for (BlockPos pig : List.of(START.south(5), START.east(3), START.south(2))) {
            helper.spawnWithNoFreeWill(EntityTypes.PIG, pig).setNoGravity(true);
        }
        ServerPlayer visible = playerAt(helper, START.south(3), GameType.SURVIVAL);
        ServerPlayer invisible = playerAt(helper, START.east(1), GameType.SURVIVAL);
        invisible.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 1000));
        invisible.setInvisible(true);
        ServerPlayer spectator = playerAt(helper, START.east(2), GameType.SPECTATOR);
        UUID owner = spawn(helper);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.radar", 1, 4);
        send(results, helper, owner, "agent.radar", 1, 8);
        send(results, helper, owner, "agent.radar", 2, RobotCommand.MAX_RADAR_RADIUS);
        send(results, helper, owner, "agent.radar", 0, 8);
        send(results, helper, owner, "agent.countNearby", 2, 8);
        Result.Code tooFar = null;
        try {
            RobotCommand.parse("agent.radar", List.of(1, RobotCommand.MAX_RADAR_RADIUS + 1));
        } catch (RobotException e) {
            tooFar = e.code;
        }
        helper.assertValueEqual(tooFar, Result.Code.OUT_OF_RANGE, "radius 17");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(
                    Result.ok(flat(helper, START.south(2), START.east(3))),
                    Result.ok(flat(helper, START.south(2), START.east(3), START.south(5))),
                    Result.ok(flat(helper, START.south(3))),
                    Result.ok(List.of()),
                    Result.ok(1)), "radar");
            var players = helper.getLevel().getServer().getPlayerList();
            players.remove(visible);
            players.remove(invisible);
            players.remove(spectator);
        });
    }

    // Issue #161: water (a waterlogged slab), lava and none; lava in the block makes it unsafe.
    @GameTest(maxTicks = 100)
    public void detectLiquid(GameTestHelper helper) {
        helper.setBlock(START.south(), Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        // Lava under the robot, walled in so it cannot flow.
        for (BlockPos wall : List.of(START.below(2), START.below().north(), START.below().south(), START.below().east(), START.below().west())) {
            helper.setBlock(wall, Blocks.STONE);
        }
        helper.setBlock(START.below(), Blocks.LAVA);
        UUID owner = spawn(helper);
        // Lava first: the slab's water reaches the robot's block after 5 ticks and would then turn the lava to obsidian.
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "agent.detectLiquid", RobotCommand.DOWN);
        send(results, helper, owner, "agent.isSafe", RobotCommand.DOWN);
        send(results, helper, owner, "agent.detectLiquid", RobotCommand.FORWARD);
        send(results, helper, owner, "agent.detectLiquid", RobotCommand.BACK);
        helper.succeedWhen(() -> helper.assertValueEqual(results, List.of(Result.ok(2), Result.ok(false), Result.ok(1), Result.ok(0)), "liquids"));
    }

    /**
     * Issue #161: the robot stands high at (3, 6, 3) facing south. Forward has 3 free blocks under it (safe), back 4
     * (unsafe), left (east) has lava under it, right (west) has a zombie next to it, down is the stone it stands on.
     */
    @GameTest(maxTicks = 100)
    public void isSafeChecksDropLavaAndMobs(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 6, 3);
        for (int y = 2; y <= 5; y++) {
            helper.setBlock(new BlockPos(3, y, 4), Blocks.AIR);
            helper.setBlock(new BlockPos(3, y, 2), Blocks.AIR);
        }
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.STONE); // forward: free at y 5, 4, 3
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE); // back: free at y 5, 4, 3, 2
        BlockPos lava = at.east().below();
        for (BlockPos wall : List.of(lava.below(), lava.east(), lava.north(), lava.south(), at.below())) helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(lava, Blocks.LAVA);
        helper.setBlock(at.west().below(), Blocks.STONE);
        helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, at.west(2)).setNoGravity(true);
        UUID owner = UUID.randomUUID();
        RobotManager.spawn(helper.getLevel(), owner, Vec3.atBottomCenterOf(helper.absolutePos(at)), Direction.SOUTH);
        List<Result> results = new ArrayList<>();
        for (int direction : new int[]{RobotCommand.FORWARD, RobotCommand.BACK, RobotCommand.LEFT, RobotCommand.RIGHT, RobotCommand.DOWN}) {
            send(results, helper, owner, "agent.isSafe", direction);
        }
        helper.succeedWhen(() -> helper.assertValueEqual(results,
                List.of(Result.ok(true), Result.ok(false), Result.ok(false), Result.ok(false), Result.ok(true)), "forward, back, left, right, down"));
    }

    // Issue #162: full means no empty slot, even when a stack could take more.
    @GameTest(maxTicks = 100)
    public void inventoryFullAndFreeSlots(GameTestHelper helper) {
        UUID almost = spawn(helper);
        UUID full = spawn(helper);
        for (int i = 0; i < RobotCommand.SLOTS; i++) {
            if (i > 0) inv(almost).setItem(i, new ItemStack(Items.DIRT));
            inv(full).setItem(i, new ItemStack(Items.DIRT));
        }
        List<Result> one = send(helper, almost, "agent.freeSlots");
        send(one, helper, almost, "agent.isInventoryFull");
        List<Result> none = send(helper, full, "agent.freeSlots");
        send(none, helper, full, "agent.isInventoryFull");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(one, List.of(Result.ok(1), Result.ok(false)), "one slot empty");
            helper.assertValueEqual(none, List.of(Result.ok(0), Result.ok(true)), "every slot used");
        });
    }

    // Issue #90: building with blocks.fill and shapes through the FakePlayer.

    private static int count(GameTestHelper helper, BlockPos from, BlockPos to, Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(from), helper.absolutePos(to))) {
            if (helper.getLevel().getBlockState(pos).is(block)) n++;
        }
        return n;
    }

    private static Object[] fill(GameTestHelper helper, int block, BlockPos from, BlockPos to, int operation) {
        BlockPos a = helper.absolutePos(from), b = helper.absolutePos(to);
        return new Object[]{block, a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), operation};
    }

    // An owner who may not give items builds from the robot's own blocks, any slot, and stops when they run out,
    // saying why (in the answer and over the robot). With none at all it says it has none.
    @GameTest(maxTicks = 100)
    public void fillUsesTheRobotsBlocksAndStopsWhenOut(GameTestHelper helper) {
        UUID owner = spawn(helper); // offline, and the gamerule is off
        inv(owner).setItem(4, new ItemStack(Items.STONE, 5));
        BlockPos from = new BlockPos(2, 2, 3), to = new BlockPos(4, 2, 5);
        List<Result> results = send(helper, owner, "blocks.fill", fill(helper, stone(), from, to, RobotShapes.REPLACE));
        send(results, helper, owner, "blocks.fill", fill(helper, stone(), from, to, RobotShapes.REPLACE));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(5, "I ran out of stone after 5 blocks."),
                    Result.ok(0, "I have no stone in my inventory.")), "placed");
            helper.assertValueEqual(RobotManager.robot(owner).said(), "I have no stone in my inventory.", "said");
            helper.assertValueEqual(count(helper, from, to, Blocks.STONE), 5, "stone in the box");
            helper.assertTrue(inv(owner).isEmpty(), "every stone used");
        });
    }

    /**
     * An online owner in survival, out of reach so it picks up no items from other tests. Built like
     * makeMockServerPlayerInLevel, whose player always reports creative.
     */
    private static ServerPlayer survivalOwner(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-survival-player"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(START).above(100)));
        return player;
    }

    // In survival the robot gathers its own resources: without the gamerule, setItem is refused and building uses the
    // robot's blocks, even for an online owner.
    @GameTest(maxTicks = 100)
    public void survivalOwnerGetsNothingFree(GameTestHelper helper) {
        ServerPlayer player = survivalOwner(helper);
        UUID owner = spawn(helper, player.getUUID());
        inv(owner).setItem(4, new ItemStack(Items.STONE, 2));
        List<Result> set = send(helper, owner, "agent.setItem", BlockIds.byName("oak_planks"), 20, 1);
        BlockPos from = new BlockPos(2, 2, 3), to = new BlockPos(4, 2, 3);
        List<Result> fill = send(helper, owner, "blocks.fill", fill(helper, stone(), from, to, RobotShapes.REPLACE));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(set.size(), 1, "setItem answered");
            helper.assertValueEqual(set.getFirst().code(), Result.Code.NO_PERMISSION, "setItem refused");
            helper.assertValueEqual(fill, List.of(Result.ok(2, "I ran out of stone after 2 blocks.")), "placed");
            helper.assertValueEqual(count(helper, from, to, Blocks.STONE), 2, "stone in the line");
            helper.assertTrue(inv(owner).isEmpty(), "every stone used, no planks given");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // With /gamerule codeling:free_items true (set by the test environment) a survival owner gets items free.
    @GameTest(maxTicks = 100, environment = "codeling-gametest:free_items")
    public void freeItemsGameruleGivesItemsInSurvival(GameTestHelper helper) {
        ServerPlayer player = survivalOwner(helper);
        UUID owner = spawn(helper, player.getUUID());
        List<Result> set = send(helper, owner, "agent.setItem", BlockIds.byName("oak_planks"), 20, 1);
        BlockPos from = new BlockPos(2, 2, 3), to = new BlockPos(4, 2, 3);
        List<Result> fill = send(helper, owner, "blocks.fill", fill(helper, stone(), from, to, RobotShapes.REPLACE));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(set, List.of(Result.ok(null)), "setItem");
            helper.assertValueEqual(fill, List.of(Result.ok(3)), "placed");
            helper.assertValueEqual(count(helper, from, to, Blocks.STONE), 3, "stone in the line");
            helper.assertValueEqual(inv(owner).getItem(0).getCount(), 20, "planks given");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // A creative owner builds without items. Hollow: the shell is set (dirt on it is broken first, its drop goes into
    // the robot), the inside is cleared, and a block a claim mod protects stays as it was.
    @GameTest(maxTicks = 100)
    public void hollowFillForACreativeOwnerRespectsClaims(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel(); // creative
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(START).above(100)));
        UUID owner = spawn(helper, player.getUUID());
        BlockPos from = new BlockPos(2, 2, 3), to = new BlockPos(4, 4, 5), centre = new BlockPos(3, 3, 4), claimed = new BlockPos(4, 4, 5);
        helper.setBlock(centre, Blocks.DIRT);
        helper.setBlock(from, Blocks.DIRT);
        CLAIMED.add(helper.absolutePos(claimed));
        List<Result> results = send(helper, owner, "blocks.fill", fill(helper, stone(), from, to, RobotShapes.HOLLOW));
        helper.succeedWhen(() -> {
            // 24 air and 1 dirt block of the shell set (the claimed one refused), and the dirt inside cleared.
            helper.assertValueEqual(results, List.of(Result.ok(26)), "changed");
            helper.assertValueEqual(count(helper, from, to, Blocks.STONE), 25, "stone shell");
            helper.assertBlockPresent(Blocks.AIR, centre);
            helper.assertBlockPresent(Blocks.AIR, claimed);
            helper.assertValueEqual(inv(owner).countItem(Items.DIRT), 2, "dirt collected");
            helper.assertValueEqual(inv(owner).countItem(Items.STONE), 0, "no stone given or used");
            CLAIMED.remove(helper.absolutePos(claimed));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // Keep fills only air; air as the block clears what is there.
    @GameTest(maxTicks = 100)
    public void keepFillsOnlyAirAndAirClears(GameTestHelper helper) {
        UUID owner = spawn(helper);
        inv(owner).setItem(0, new ItemStack(Items.GLASS, 64));
        BlockPos from = new BlockPos(2, 2, 3), to = new BlockPos(3, 2, 4);
        helper.setBlock(from, Blocks.DIRT);
        BlockPos wallFrom = new BlockPos(5, 2, 3), wallTo = new BlockPos(5, 3, 3);
        helper.setBlock(wallFrom, Blocks.STONE);
        helper.setBlock(wallTo, Blocks.STONE);
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "blocks.fill", fill(helper, BlockIds.byName("glass"), from, to, RobotShapes.KEEP));
        send(results, helper, owner, "blocks.fill", fill(helper, 0, wallFrom, wallTo, RobotShapes.REPLACE));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(3), Result.ok(2)), "changed");
            helper.assertBlockPresent(Blocks.DIRT, from);
            helper.assertValueEqual(count(helper, from, to, Blocks.GLASS), 3, "glass");
            helper.assertValueEqual(count(helper, wallFrom, wallTo, Blocks.AIR), 2, "cleared");
            helper.assertValueEqual(inv(owner).countItem(Items.COBBLESTONE), 2, "the wall's drops");
        });
    }

    // shapes.line and shapes.sphere answer how many blocks they set.
    @GameTest(maxTicks = 100)
    public void lineAndSphere(GameTestHelper helper) {
        UUID owner = spawn(helper);
        inv(owner).setItem(0, new ItemStack(Items.STONE, 64));
        BlockPos a = helper.absolutePos(new BlockPos(2, 2, 3)), b = helper.absolutePos(new BlockPos(6, 2, 3));
        BlockPos c = helper.absolutePos(new BlockPos(4, 4, 5));
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "shapes.line", stone(), a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        send(results, helper, owner, "shapes.sphere", stone(), c.getX(), c.getY(), c.getZ(), 1, RobotShapes.SHAPE_REPLACE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(5), Result.ok(19)), "set");
            helper.assertValueEqual(inv(owner).countItem(Items.STONE), 64 - 24, "stone used");
        });
    }

    // The measuring rule: every block of the shape within 32 blocks of the robot on each axis.
    @GameTest(maxTicks = 100)
    public void buildingFarFromTheRobotIsOutOfRange(GameTestHelper helper) {
        UUID owner = spawn(helper);
        inv(owner).setItem(0, new ItemStack(Items.STONE, 64));
        BlockPos at = helper.absolutePos(START);
        int far = RobotCommand.MAX_FIND_RADIUS + 1;
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "blocks.fill", stone(), at.getX(), at.getY() + 2, at.getZ(), at.getX(), at.getY() + far, at.getZ(), 0);
        send(results, helper, owner, "shapes.sphere", stone(), at.getX() + far - 1, at.getY(), at.getZ(), 2, 0);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results.stream().map(Result::code).toList(), List.of(Result.Code.OUT_OF_RANGE, Result.Code.OUT_OF_RANGE), "codes");
            helper.assertValueEqual(inv(owner).countItem(Items.STONE), 64, "nothing placed");
        });
    }

    // Issue #182: a 7 x 7 maze of stone bricks on oak planks with its north-west floor corner at y 1, seed 42. The
    // command's corner is at foot height, one above: the floor replaces the ground layer under it.
    private static final BlockPos MAZE = new BlockPos(0, 1, 0);

    private static Object[] maze(GameTestHelper helper, int seed) {
        BlockPos a = helper.absolutePos(MAZE.above());
        return new Object[]{a.getX(), a.getY(), a.getZ(), 7, 7, BlockIds.byName("stone_bricks"), BlockIds.byName("oak_planks"), seed};
    }

    // Built as the generator says (walls 2 high, planks under the corridors), then followPath on the planks walks in
    // from the entrance and stops at a junction or a dead end.
    @GameTest(maxTicks = 300, environment = "codeling-gametest:free_items")
    public void mazeBuildsAndFollowPathWalksIn(GameTestHelper helper) {
        ServerPlayer player = survivalOwner(helper);
        boolean[][] open = RobotShapes.maze(7, 7, 42);
        int entrance = 0;
        while (!open[entrance][0]) entrance++;
        BlockPos start = MAZE.offset(entrance, 1, 0);
        RobotManager.spawn(helper.getLevel(), player.getUUID(), Vec3.atBottomCenterOf(helper.absolutePos(start)), Direction.SOUTH);
        UUID owner = player.getUUID();
        int corridors = 0;
        for (boolean[] column : open) for (boolean o : column) if (o) corridors++;
        List<Result> results = new ArrayList<>();
        send(results, helper, owner, "shapes.maze", maze(helper, 42));
        send(results, helper, owner, "agent.followPath", BlockIds.byName("oak_planks"));
        int set = 3 * (49 - corridors) + corridors;
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results.size(), 2, "answers");
            helper.assertValueEqual(results.getFirst(), Result.ok(set), "blocks set");
            Object stop = results.get(1).value();
            helper.assertTrue(stop.equals(RobotTrail.END) || stop.equals(RobotTrail.JUNCTION), "stopped at a dead end or junction: " + stop);
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    helper.assertBlockPresent(open[x][z] ? Blocks.OAK_PLANKS : Blocks.STONE_BRICKS, MAZE.offset(x, 0, z));
                    for (int y = 1; y <= 2; y++) helper.assertBlockPresent(open[x][z] ? Blocks.AIR : Blocks.STONE_BRICKS, MAZE.offset(x, y, z));
                }
            }
            BlockPos end = at(helper, owner);
            helper.assertTrue(!end.equals(helper.absolutePos(start)), "the robot moved");
            helper.assertTrue(helper.getLevel().getBlockState(end.below()).is(Blocks.OAK_PLANKS), "on the corridor floor");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    // Issue #180: keeping to one side walks a shapes.maze from the entrance to the exit, following again after LIMIT
    // as the guide's loop does.
    private static void solveMaze(GameTestHelper helper, int side) {
        ServerPlayer player = survivalOwner(helper);
        boolean[][] open = RobotShapes.maze(7, 7, 42);
        int entrance = 0, exit = 0;
        while (!open[entrance][0]) entrance++;
        while (!open[exit][6]) exit++;
        RobotManager.spawn(helper.getLevel(), player.getUUID(), Vec3.atBottomCenterOf(helper.absolutePos(MAZE.offset(entrance, 1, 0))), Direction.SOUTH);
        UUID owner = player.getUUID();
        List<Result> built = send(helper, owner, "shapes.maze", maze(helper, 42));
        List<Result> stops = new ArrayList<>();
        followUntilNotLimit(helper, owner, side, stops);
        BlockPos goal = helper.absolutePos(MAZE.offset(exit, 1, 6));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(built.size(), 1, "maze built");
            helper.assertTrue(!stops.isEmpty() && stops.getLast().equals(Result.ok(RobotTrail.END)), "out of the maze: " + stops);
            helper.assertValueEqual(at(helper, owner), goal, "at the exit");
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static void followUntilNotLimit(GameTestHelper helper, UUID owner, int side, List<Result> stops) {
        try {
            RobotCommand follow = RobotCommand.parse("agent.followPath", List.of(BlockIds.byName("oak_planks"), side));
            RobotManager.submit(owner, RobotManager.queue(owner).run(), follow, result -> {
                stops.add(result);
                if (result.equals(Result.ok(RobotTrail.LIMIT))) followUntilNotLimit(helper, owner, side, stops);
            });
        } catch (RobotException e) {
            helper.fail("agent.followPath rejected: " + e.result());
        }
    }

    @GameTest(maxTicks = 500, environment = "codeling-gametest:free_items")
    public void mazeSolvedKeepingLeft(GameTestHelper helper) {
        solveMaze(helper, RobotCommand.LEFT_TURN);
    }

    @GameTest(maxTicks = 500, environment = "codeling-gametest:free_items")
    public void mazeSolvedKeepingRight(GameTestHelper helper) {
        solveMaze(helper, RobotCommand.RIGHT_TURN);
    }

    // An offline owner in survival without blocks: the maze stops at the first block it cannot place, like every build.
    @GameTest(maxTicks = 100)
    public void mazeWithoutBlocksStops(GameTestHelper helper) {
        UUID owner = spawn(helper);
        List<Result> results = send(helper, owner, "shapes.maze", maze(helper, 42));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(0, "I have no stone bricks in my inventory.")), "nothing placed");
            helper.assertValueEqual(count(helper, MAZE, MAZE.offset(6, 2, 6), Blocks.STONE_BRICKS) + count(helper, MAZE, MAZE.offset(6, 2, 6), Blocks.OAK_PLANKS), 0, "no maze");
        });
    }

    /** A player next to the robot whose loaded chunks (view distance 2) are around it, as a real client's would be. */
    private static ServerPlayer cameraPlayer(GameTestHelper helper) {
        ServerPlayer player = playerAtStart(helper);
        player.setChunkTrackingView(ChunkTrackingView.of(player.chunkPosition(), 2));
        return player;
    }

    private static void allowed(GameTestHelper helper, String refusal, String what) {
        helper.assertTrue(refusal == null, what + " refused: " + refusal);
    }

    // Issue #175: the owner may look through the robot, another player may not; an op may in the default mode.
    @GameTest(maxTicks = 100)
    public void cameraForOwnerNotOthers(GameTestHelper helper) {
        ServerPlayer owner = cameraPlayer(helper);
        ServerPlayer other = cameraPlayer(helper);
        RobotEntity robot = RobotManager.robot(spawn(helper, owner.getUUID()));
        var players = helper.getLevel().getServer().getPlayerList();
        helper.assertValueEqual(RobotCamera.start(other, robot), "This is not your robot.", "a stranger");
        helper.assertTrue(RobotCamera.viewing(other) == null, "the stranger sees nothing");
        allowed(helper, RobotCamera.start(owner, robot), "the owner");
        helper.assertTrue(RobotCamera.viewing(owner) == robot, "the owner looks through the robot");
        GameTestCompat.op(other);
        allowed(helper, RobotCamera.refusal(other, robot), "an op");
        GameTestCompat.deop(other);
        helper.assertTrue(RobotCamera.stop(owner), "stop");
        helper.assertTrue(RobotCamera.viewing(owner) == null, "stopped");
        players.remove(owner);
        players.remove(other);
        helper.succeed();
    }

    // The rule is changed and put back within this one call, so camera tests running at the same time never see it.
    @GameTest(maxTicks = 100)
    public void cameraRuleOffAndOps(GameTestHelper helper) {
        ServerPlayer owner = cameraPlayer(helper);
        RobotEntity robot = RobotManager.robot(spawn(helper, owner.getUUID()));
        var server = helper.getLevel().getServer();
        try {
            GameTestCompat.setCamera(server, RobotCamera.Mode.off);
            helper.assertValueEqual(RobotCamera.start(owner, robot), "The robot camera is turned off on this server.", "off");
            GameTestCompat.setCamera(server, RobotCamera.Mode.ops);
            helper.assertValueEqual(RobotCamera.start(owner, robot), "Only operators may use the robot camera on this server.", "ops, owner not op");
            GameTestCompat.op(owner);
            allowed(helper, RobotCamera.start(owner, robot), "ops, owner is op");
            // Permission taken away: the next tick ends the view.
            GameTestCompat.setCamera(server, RobotCamera.Mode.off);
            RobotCamera.tick(server);
            helper.assertTrue(RobotCamera.viewing(owner) == null, "ended when the rule turned off");
        } finally {
            GameTestCompat.setCamera(server, RobotCamera.Mode.owner);
            GameTestCompat.deop(owner);
            server.getPlayerList().remove(owner);
        }
        helper.succeed();
    }

    // Only a robot in chunks the player has loaded, so the camera never shows more than the client has anyway.
    @GameTest(maxTicks = 100)
    public void cameraOutOfRangeRefused(GameTestHelper helper) {
        ServerPlayer owner = cameraPlayer(helper);
        RobotEntity robot = RobotManager.robot(spawn(helper, owner.getUUID()));
        ChunkPos far = new ChunkPos(McCompat.chunkX(robot.chunkPosition()) + 10, McCompat.chunkZ(robot.chunkPosition()));
        owner.setChunkTrackingView(ChunkTrackingView.of(far, 2));
        helper.assertValueEqual(RobotCamera.start(owner, robot), "Your robot is too far away for the camera.", "out of range");
        helper.getLevel().getServer().getPlayerList().remove(owner);
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void cameraEndsWhenRobotRemoved(GameTestHelper helper) {
        ServerPlayer owner = cameraPlayer(helper);
        RobotEntity robot = RobotManager.robot(spawn(helper, owner.getUUID()));
        allowed(helper, RobotCamera.start(owner, robot), "start");
        robot.discard();
        helper.succeedWhen(() -> {
            helper.assertTrue(RobotCamera.viewing(owner) == null, "ended after the robot was removed");
            helper.getLevel().getServer().getPlayerList().remove(owner);
        });
    }

    // Issue #177: follow mode goes through the same checks; agent.camera's modes switch and end the view.
    @GameTest(maxTicks = 100)
    public void cameraFollowForOwnerNotOthers(GameTestHelper helper) {
        ServerPlayer owner = cameraPlayer(helper);
        ServerPlayer other = cameraPlayer(helper);
        RobotEntity robot = RobotManager.robot(spawn(helper, owner.getUUID()));
        helper.assertValueEqual(RobotCamera.start(other, robot, true), "This is not your robot.", "a stranger");
        helper.assertTrue(RobotCamera.viewing(other) == null, "the stranger sees nothing");
        allowed(helper, RobotCamera.start(owner, robot, true), "the owner");
        helper.assertTrue(RobotCamera.viewing(owner) == robot && RobotCamera.following(owner), "the owner follows the robot");
        allowed(helper, RobotCamera.set(owner, RobotCamera.EYES), "agent.camera Eyes");
        helper.assertTrue(RobotCamera.viewing(owner) == robot && !RobotCamera.following(owner), "eyes after follow");
        allowed(helper, RobotCamera.set(owner, RobotCamera.OFF), "agent.camera Off");
        helper.assertTrue(RobotCamera.viewing(owner) == null, "off");
        var players = helper.getLevel().getServer().getPlayerList();
        players.remove(owner);
        players.remove(other);
        helper.succeed();
    }
}
