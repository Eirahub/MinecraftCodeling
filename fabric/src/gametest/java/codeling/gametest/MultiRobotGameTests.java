package codeling.gametest;

import codeling.Codeling;
import codeling.net.ServerRelay;
import codeling.robot.Result;
import codeling.robot.RobotCommand;
import codeling.robot.RobotEntity;
import codeling.robot.RobotException;
import codeling.robot.RobotLinks;
import codeling.robot.RobotManager;
import codeling.robot.RobotPath;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Several robots per player (issues #140, #141 and #151). Tests that need more than one robot per player run in the
 * test environment codeling-gametest:max_robots (the game rule at 3); the rest run with the default rule, 1.
 */
public class MultiRobotGameTests {
    private static final BlockPos START = new BlockPos(1, 2, 1);
    private static final String MORE = "codeling-gametest:max_robots";

    //? if <1.21.5 {
    /*// Before 1.21.5 there are no test environments: MORE is a batch, and the rule is set for it and put back after.
    @net.minecraft.gametest.framework.BeforeBatch(batch = MORE)
    public void raiseRule(net.minecraft.server.level.ServerLevel level) {
        GameTestCompat.setMaxRobots(level.getServer(), 3);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = MORE)
    public void resetRule(net.minecraft.server.level.ServerLevel level) {
        GameTestCompat.setMaxRobots(level.getServer(), 1);
    }
    *///?}

    /** A mock player (creative, not op) standing next to START. */
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(START.east())));
        return player;
    }

    /** Runs a /robot command as the player and returns what it printed. */
    private static List<String> run(ServerPlayer player, String command) {
        List<String> out = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                out.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSource(capture), command);
        return out;
    }

    private static RobotLinks.Key key(ServerPlayer player, String name) {
        return new RobotLinks.Key(player.getUUID(), name);
    }

    private static RobotEntity spawn(GameTestHelper helper, ServerPlayer player, String name, BlockPos at) {
        return RobotManager.spawn(helper.getLevel(), key(player, name), Vec3.atBottomCenterOf(helper.absolutePos(at)), Direction.SOUTH);
    }

    private static void leave(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) helper.getLevel().getServer().getPlayerList().remove(p);
    }

    // With the default rule (1) a player has the default robot only: a named robot is refused with the rule's name.
    @GameTest(maxTicks = 100)
    public void defaultRuleRefusesANamedRobot(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.assertValueEqual(Codeling.maxRobots(helper.getLevel().getServer()), 1, "default rule");
        run(player, "robot spawn");
        helper.assertTrue(RobotManager.robot(player.getUUID()) != null, "the default robot spawns as before");
        List<String> out = run(player, "robot spawn Miner");
        helper.assertTrue(RobotManager.robot(key(player, "Miner")) == null, "no second robot");
        helper.assertTrue(out.toString().contains("codeling:max_robots"), "the refusal names the game rule: " + out);
        leave(helper, player);
        helper.succeed();
    }

    // Rule 3: the default robot and two named ones; a fourth is refused; names are checked and unique per player.
    @GameTest(maxTicks = 100, environment = MORE)
    public void raisedRuleAllowsNamedRobotsUpToTheLimit(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        run(player, "robot spawn");
        run(player, "robot spawn Miner");
        run(player, "robot spawn Digger");
        helper.assertTrue(RobotManager.robot(key(player, "Miner")) != null && RobotManager.robot(key(player, "Digger")) != null, "two named robots");
        helper.assertTrue(run(player, "robot spawn Third").toString().contains("codeling:max_robots"), "a fourth robot is refused");
        helper.assertTrue(RobotManager.robot(key(player, "Third")) == null, "no fourth robot");
        helper.assertTrue(run(player, "robot spawn miner").toString().contains("already have a robot called Miner"), "Miner and miner are one name");
        helper.assertTrue(run(player, "robot spawn all").toString().contains("choose another name"), "reserved word");
        // Spawning a robot the player has moves it, at the limit too.
        RobotEntity miner = RobotManager.robot(key(player, "Miner"));
        run(player, "robot spawn Miner");
        helper.assertTrue(RobotManager.robot(key(player, "Miner")) == miner, "the same robot, moved");
        // Each robot has its own inventory.
        miner.inventory().setItem(0, new ItemStack(Items.STONE, 3));
        helper.assertTrue(RobotManager.robot(player.getUUID()).inventory().isEmpty(), "the default robot's inventory is its own");
        List<String> list = run(player, "robot list");
        helper.assertTrue(list.toString().contains("default robot (agent)") && list.toString().contains("Miner") && list.toString().contains("Digger"), "list: " + list);
        miner.inventory().clearContent();
        RobotManager.robot(key(player, "Digger")).discard();
        leave(helper, player);
        helper.succeed();
    }

    // Lowering the rule only stops new robots: the ones a player has stay and keep working.
    @GameTest(maxTicks = 100)
    public void loweredRuleKeepsExistingRobots(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        RobotEntity miner = spawn(helper, player, "Miner", START); // as if the rule was higher before
        helper.assertTrue(run(player, "robot spawn Digger").toString().contains("codeling:max_robots"), "no new robot at rule 1");
        helper.assertTrue(RobotManager.robot(key(player, "Miner")) == miner && !miner.isRemoved(), "Miner stays");
        run(player, "robot move forward 2 Miner");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(miner.blockPosition(), helper.absolutePos(START.south(2)), "Miner moved");
            leave(helper, player);
        });
    }

    // /robot remove: every item moves to the player; what does not fit drops at the player's feet; nothing is lost.
    @GameTest(maxTicks = 100, environment = MORE)
    public void removeGivesTheItemsToThePlayer(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        RobotEntity miner = spawn(helper, player, "Miner", START);
        miner.inventory().setItem(0, new ItemStack(Items.STONE, 10));
        miner.inventory().setItem(5, new ItemStack(Items.DIRT, 64));
        RobotEntity digger = spawn(helper, player, "Digger", START.south(2));
        digger.inventory().setItem(0, new ItemStack(Items.COBBLESTONE, 7));
        List<String> out = run(player, "robot remove Miner");
        helper.assertTrue(miner.isRemoved() && RobotManager.robot(key(player, "Miner")) == null, "Miner is gone: " + out);
        helper.assertValueEqual(player.getInventory().countItem(Items.STONE), 10, "stone in the player's inventory");
        helper.assertValueEqual(player.getInventory().countItem(Items.DIRT), 64, "dirt in the player's inventory");
        helper.assertTrue(run(player, "robot remove Miner").toString().contains("no robot called Miner"), "removed for good");
        // A full inventory: the rest drops where the player stands.
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) player.getInventory().setItem(i, new ItemStack(Items.GLASS, 64));
        run(player, "robot remove Digger");
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(3),
                e -> e.isAlive() && e.getItem().is(Items.COBBLESTONE)).stream().mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertValueEqual(dropped, 7, "cobblestone dropped at the player");
        helper.assertTrue(digger.isRemoved(), "Digger is gone");
        leave(helper, player);
        helper.succeed();
    }

    // Issue #141: two robots of one player, each with its own queue, move at the same time in one run.
    @GameTest(maxTicks = 100)
    public void twoRobotsRunInParallel(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(START).above(100)));
        RobotEntity agent = spawn(helper, player, "", START);
        RobotEntity miner = spawn(helper, player, "Miner", START.east(3));
        List<JsonObject> inbox = new ArrayList<>();
        ServerRelay.handle(player, "{\"v\":2,\"t\":\"control\",\"op\":\"start\"}", r -> inbox.add(JsonParser.parseString(r).getAsJsonObject()));
        long run = inbox.getLast().get("run").getAsLong();
        String move = ",\"run\":" + run + ",\"op\":\"agent.move\",\"args\":[0,5]";
        ServerRelay.handle(player, "{\"v\":2,\"t\":\"cmd\",\"id\":1" + move + "}", r -> inbox.add(JsonParser.parseString(r).getAsJsonObject()));
        ServerRelay.handle(player, "{\"v\":2,\"t\":\"cmd\",\"id\":2" + move + ",\"robot\":\"Miner\"}", r -> inbox.add(JsonParser.parseString(r).getAsJsonObject()));
        // Five blocks take 20 ticks; one after the other would take 40.
        helper.runAfterDelay(5 * RobotManager.TICKS_PER_BLOCK + 6, () -> {
            helper.assertValueEqual(agent.blockPosition(), helper.absolutePos(START.south(5)), "the default robot moved");
            helper.assertValueEqual(miner.blockPosition(), helper.absolutePos(START.east(3).south(5)), "Miner moved at the same time");
            helper.assertValueEqual(inbox.stream().filter(m -> m.get("t").getAsString().equals("result")).count(), 2L, "both answered: " + inbox);
            leave(helper, player);
            helper.succeed();
        });
    }

    // Another player can name the robot but never reach it: names are looked up among the sender's own robots.
    @GameTest(maxTicks = 100)
    public void othersCannotUseSomeonesNamedRobot(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        ServerPlayer other = player(helper);
        RobotEntity miner = spawn(helper, owner, "Miner", START);
        List<String> replies = new ArrayList<>();
        ServerRelay.handle(other, "{\"v\":2,\"t\":\"control\",\"op\":\"start\"}", replies::add);
        long run = JsonParser.parseString(replies.getLast()).getAsJsonObject().get("run").getAsLong();
        ServerRelay.handle(other, "{\"v\":2,\"t\":\"cmd\",\"id\":1,\"run\":" + run + ",\"op\":\"agent.move\",\"args\":[0,2],\"robot\":\"Miner\"}", replies::add);
        helper.assertTrue(replies.getLast().contains("UNLOADED"), "the stranger has no Miner: " + replies.getLast());
        run(other, "robot move forward 2 Miner");
        run(other, "robot remove Miner");
        run(other, "robot rename Miner Thief");
        // A name tag from a stranger does nothing either.
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal("Thief"));
        other.setItemInHand(InteractionHand.MAIN_HAND, tag);
        GameTestCompat.interact(miner, other);
        helper.runAfterDelay(20, () -> {
            helper.assertValueEqual(miner.blockPosition(), helper.absolutePos(START), "Miner did not move");
            helper.assertTrue(!miner.isRemoved() && RobotManager.robot(key(owner, "Miner")) == miner, "Miner is still the owner's");
            helper.assertValueEqual(miner.getCustomName().getString(), "Miner", "name unchanged");
            leave(helper, owner, other);
            helper.succeed();
        });
    }

    // Issue #151: /robot rename and a name tag rename a robot; the default robot only shows a name and stays agent.
    @GameTest(maxTicks = 100, environment = MORE)
    public void renameByCommandAndNameTag(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        RobotEntity agent = spawn(helper, player, "", START);
        RobotEntity miner = spawn(helper, player, "Miner", START.south(2));
        spawn(helper, player, "Digger", START.south(4));
        helper.assertTrue(miner.isCustomNameVisible() && miner.getCustomName().getString().equals("Miner"), "a named robot shows its name");
        run(player, "robot rename Miner Builder");
        helper.assertTrue(RobotManager.robot(key(player, "Builder")) == miner && RobotManager.robot(key(player, "Miner")) == null, "renamed");
        helper.assertValueEqual(miner.getCustomName().getString(), "Builder", "tag after rename");
        helper.assertTrue(run(player, "robot rename Builder digger").toString().contains("already have a robot called Digger"), "names stay unique");
        run(player, "robot rename Bob");
        helper.assertTrue(RobotManager.robot(player.getUUID()) == agent, "the default robot stays the default");
        helper.assertValueEqual(agent.getCustomName().getString(), "Bob", "the default robot's tag");
        // A name tag item, used by the owner.
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal("Tagged"));
        player.setItemInHand(InteractionHand.MAIN_HAND, tag);
        GameTestCompat.interact(miner, player);
        helper.assertTrue(RobotManager.robot(key(player, "Tagged")) == miner, "renamed by the name tag");
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "a name tag does not open the inventory");
        // Name tags off and on again.
        run(player, "robot nametag off");
        helper.assertTrue(!miner.isCustomNameVisible() && !agent.isCustomNameVisible(), "tags hidden");
        run(player, "robot nametag on Tagged");
        helper.assertTrue(miner.isCustomNameVisible() && !agent.isCustomNameVisible(), "one tag shown");
        RobotManager.robot(key(player, "Digger")).discard();
        leave(helper, player);
        helper.succeed();
    }

    // /robot stop with a name stops that robot only; without a name all of the player's robots.
    @GameTest(maxTicks = 100, environment = MORE)
    public void stopOneOrAll(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(START).above(100)));
        RobotEntity agent = spawn(helper, player, "", START);
        RobotEntity miner = spawn(helper, player, "Miner", START.east(3));
        run(player, "robot move forward 6");
        run(player, "robot move forward 6 Miner");
        helper.runAfterDelay(2, () -> run(player, "robot stop Miner"));
        helper.runAfterDelay(40, () -> {
            helper.assertValueEqual(agent.blockPosition(), helper.absolutePos(START.south(6)), "the default robot went on");
            helper.assertTrue(miner.blockPosition().getZ() - helper.absolutePos(START).getZ() < 3, "Miner stopped early");
            run(player, "robot move forward 6");
            run(player, "robot move forward 6 Miner");
            run(player, "robot stop");
            helper.assertValueEqual(RobotManager.queue(key(player, "")).size() + RobotManager.queue(key(player, "Miner")).size(), 0, "both queues empty");
            leave(helper, player);
            helper.succeed();
        });
    }

    // A robot saved before named robots (no OwnerName, only the default link) loads as the player's default robot.
    @GameTest(maxTicks = 100)
    public void oldSaveLoadsAsTheDefaultRobot(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        RobotEntity first = RobotManager.spawn(helper.getLevel(), owner, Vec3.atBottomCenterOf(helper.absolutePos(START)), Direction.SOUTH);
        first.inventory().setItem(0, new ItemStack(Items.STONE, 4));
        CompoundTag saved = GameTestCompat.save(first, helper.getLevel().registryAccess());
        saved.remove("OwnerName");
        first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        RobotEntity back = new RobotEntity(Codeling.ROBOT.get(), helper.getLevel());
        GameTestCompat.load(back, saved, helper.getLevel().registryAccess());
        helper.getLevel().addFreshEntity(back);
        helper.assertTrue(RobotManager.robot(owner) == back, "the old robot is the default robot");
        helper.assertValueEqual(back.name(), "", "no name");
        helper.assertValueEqual(back.inventory().getItem(0).getCount(), 4, "its items");
        helper.assertTrue(!back.hasCustomName(), "no name tag");
        try {
            RobotManager.submit(owner, RobotManager.queue(owner).run(), RobotCommand.parse("agent.move", List.of(0, 1)), r -> { });
        } catch (Exception e) {
            helper.fail("a command for the old robot: " + e);
        }
        helper.succeedWhen(() -> {
            helper.assertValueEqual(back.blockPosition(), helper.absolutePos(START.south()), "it still takes commands");
            back.inventory().clearContent();
            back.discard();
        });
    }

    /** Queues agent.goToRobot(target) for the player's robot {@code robot} ("" for the default robot); the result goes in the list. */
    private static List<Result> goTo(GameTestHelper helper, ServerPlayer player, String robot, String target) {
        return goTo(new ArrayList<>(), helper, player, robot, target);
    }

    private static List<Result> goTo(List<Result> results, GameTestHelper helper, ServerPlayer player, String robot, String target) {
        RobotLinks.Key key = key(player, robot);
        try {
            RobotManager.submit(key, RobotManager.queue(key).run(), RobotCommand.parse("agent.goToRobot", List.of(target)), results::add);
        } catch (RobotException e) {
            helper.fail("goToRobot rejected: " + e.result());
        }
        return results;
    }

    // Issue #243: the default robot walks to the player's Miner, past another player's Miner that is nearer, and ends
    // level with it, next to it and facing it. Then Miner, already next to it, goes to agent: it only turns. Going to
    // itself answers true at once.
    @GameTest(maxTicks = 200)
    public void goToAnotherRobotEndsNextToItFacingIt(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ServerPlayer stranger = player(helper);
        RobotEntity agent = spawn(helper, player, "", START);
        RobotEntity miner = spawn(helper, player, "Miner", START.south(4).east(2));
        RobotEntity decoy = spawn(helper, stranger, "Miner", START.east(2));
        List<Result> there = goTo(helper, player, "", "Miner");
        List<Result> beside = new ArrayList<>();
        List<Result> self = new ArrayList<>();
        BlockPos minerAt = miner.blockPosition();
        helper.runAfterDelay(80, () -> {
            helper.assertValueEqual(there, List.of(Result.ok(true)), "arrived");
            BlockPos at = agent.blockPosition();
            helper.assertValueEqual(at.distManhattan(minerAt), 1, "next to the player's Miner");
            helper.assertValueEqual(at.getY(), minerAt.getY(), "level with it");
            helper.assertValueEqual(agent.getDirection(), RobotPath.direction(at, minerAt), "facing it");
            helper.assertTrue(at.distManhattan(decoy.blockPosition()) > 1, "not at the other player's Miner");
            goTo(beside, helper, player, "Miner", "");
            goTo(self, helper, player, "", "");
        });
        helper.runAfterDelay(100, () -> {
            helper.assertValueEqual(beside, List.of(Result.ok(true)), "Miner was already next to agent");
            helper.assertValueEqual(miner.blockPosition(), minerAt, "Miner did not move");
            helper.assertValueEqual(miner.getDirection(), RobotPath.direction(minerAt, agent.blockPosition()), "Miner faces agent");
            helper.assertValueEqual(self, List.of(Result.ok(true, "agent is this robot itself, so it is already there")), "to itself");
            decoy.discard();
            leave(helper, player, stranger);
            helper.succeed();
        });
    }

    // Issue #243: a target with no free block next to it answers false at once; an unknown name says which robots the
    // player has (never another player's); a robot that is not loaded is UNLOADED; another player's robot of the
    // same name is never found.
    @GameTest(maxTicks = 100)
    public void goToRobotRefusals(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ServerPlayer stranger = player(helper);
        RobotEntity agent = spawn(helper, player, "", START);
        BlockPos shut = START.south(4).above(2);
        for (Direction d : Direction.values()) helper.setBlock(shut.relative(d), Blocks.STONE);
        spawn(helper, player, "Miner", shut);
        spawn(helper, player, "Digger", START.east(4)).remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        RobotEntity secret = spawn(helper, stranger, "Secret", START.east(2));
        List<Result> results = goTo(helper, player, "", "Miner");
        List<Result> nobody = goTo(helper, player, "", "Nobody");
        List<Result> digger = goTo(helper, player, "", "Digger");
        List<Result> strangers = goTo(helper, stranger, "Secret", "Miner");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(results, List.of(Result.ok(false, "there is no free block next to robot Miner")), "boxed in");
            helper.assertValueEqual(nobody, List.of(Result.error(Result.Code.BAD_ARGS,
                    "you have no robot called Nobody; your robots are agent (the default robot), Digger, Miner")), "unknown name: the player's robots");
            helper.assertValueEqual(digger, List.of(Result.error(Result.Code.UNLOADED, "robot Digger is not loaded (see /robot list)")), "not loaded");
            helper.assertValueEqual(strangers, List.of(Result.error(Result.Code.BAD_ARGS,
                    "you have no robot called Miner; your robots are Secret")), "the stranger has no Miner, and sees only their own names");
            helper.assertValueEqual(agent.blockPosition(), helper.absolutePos(START), "agent did not move");
            helper.assertValueEqual(secret.blockPosition(), helper.absolutePos(START.east(2)), "Secret did not move");
            secret.discard();
            leave(helper, player, stranger);
        });
    }
}
