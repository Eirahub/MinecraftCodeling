package codeling.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RobotCommandTest {
    private static Result.Code rejected(String op, Object... args) {
        return assertThrows(RobotException.class, () -> RobotCommand.parse(op, Arrays.asList(args))).code;
    }

    @Test
    void acceptsValidMoveAndTurn() throws RobotException {
        assertEquals(new RobotCommand(RobotCommand.Op.MOVE, RobotCommand.UP, 64), RobotCommand.parse("agent.move", List.of(4, 64)));
        // JSON numbers may arrive as doubles or longs
        assertEquals(new RobotCommand(RobotCommand.Op.MOVE, 0, 3), RobotCommand.parse("agent.move", List.of(0.0, 3L)));
        assertEquals(new RobotCommand(RobotCommand.Op.TURN, RobotCommand.LEFT_TURN, 0), RobotCommand.parse("agent.turn", List.of(2)));
    }

    // Issue #189: on an older Minecraft the mod limits ItemIds to its registry; the 26.3 code then answers a clear error.
    @Test
    void refusesItemsMissingInThisMinecraftVersion() throws RobotException {
        int pale = ItemIds.byName("pale_oak_planks");
        try {
            ItemIds.setInThisVersion(id -> !id.equals("minecraft:pale_oak_planks"));
            RobotException e = assertThrows(RobotException.class, () -> RobotCommand.parse("agent.craft", List.of(pale, 1)));
            assertEquals(Result.Code.BAD_ARGS, e.code);
            assertEquals("minecraft:pale_oak_planks is not in this Minecraft version", e.getMessage());
            assertEquals(Result.Code.BAD_ARGS, rejected("agent.setItem", pale, 1, 1));
            RobotCommand.parse("agent.craft", List.of(ItemIds.byName("oak_planks"), 1));
        } finally {
            ItemIds.setInThisVersion(id -> true);
        }
    }

    @Test
    void rejectsBadArgs() {
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.fly", 1));
        assertEquals(Result.Code.BAD_ARGS, rejected(null));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.move", 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.move", 0, 1, 2));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.move", "0", 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.move", 0, 1.5));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.move", 0, null));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.move", 6, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.move", -1, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.turn", 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.turn", 4));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.interact", 6));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.redstonePower"));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.moveTo", 0, 64, -30_000_001));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.moveTo", 0, 64));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.move", 0, 1e12));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.findNearest", 17, 33));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.findNearestCategory", 0, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.findNearest", 9999, 8));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.findNearestCategory", RobotCommand.CATEGORIES, 8));
        // world positions are resolved in the editor, never sent
        assertEquals(Result.Code.BAD_ARGS, rejected("positions.toWorld", 0, 1, 2, 3));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("positions.toWorld", 1, 30_000_001, 0, 0));
    }

    // Issue #71: slot 0 means all slots (storeItems) or the first stack (takeItems); items are block codes, never air.
    @Test
    void containerArgs() throws RobotException {
        assertEquals(new RobotCommand(RobotCommand.Op.STORE_ITEMS, 0, 27), RobotCommand.parse("agent.storeItems", List.of(0, 27)));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.storeItems", 0, 28));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.storeItems", 0, -1));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.takeItems", 0, RobotCommand.MAX_CONTAINER_SLOT + 1, 1));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.takeItems", 0, 0, 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.takeItem", 0, 1, 65));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.takeItem", 0, 0, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.containerCount", 0, 9999));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.containerSize", 6));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.sortContainer"));
    }

    // Issue #125: item calls take Item codes and Block codes with an item form; air empties a slot in setItem only.
    @Test
    void itemArgs() throws RobotException {
        int coal = ItemIds.byName("coal");
        assertEquals(new RobotCommand(RobotCommand.Op.TAKE_ITEM, 0, coal, 1), RobotCommand.parse("agent.takeItem", List.of(0, coal, 1)));
        assertEquals(new RobotCommand(RobotCommand.Op.CONTAINER_COUNT, 0, 4), RobotCommand.parse("agent.containerCount", List.of(0, 4)));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_ITEM, coal, 64, 1), RobotCommand.parse("agent.setItem", List.of(coal, 64, 1)));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_ITEM, 0, 1, 1), RobotCommand.parse("agent.setItem", List.of(0, 1, 1)));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.containerCount", 0, BlockIds.byName("WATER")));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.setItem", BlockIds.byName("LAVA"), 1, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.takeItem", 0, ItemIds.ITEM_BASE + 99_999, 1));
    }

    // Issues #93 and #115: an item and a count of 1 to 64.
    @Test
    void craftArgs() throws RobotException {
        int stick = ItemIds.byName("stick");
        assertEquals(new RobotCommand(RobotCommand.Op.CRAFT, stick, 64), RobotCommand.parse("agent.craft", List.of(stick, 64)));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.craft", stick, 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.canCraft", stick, 65));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.stonecut", 0, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.craft", stick));
    }

    // Issues #127 to #132: caps of 32 blocks in a line and on each side of a box.
    @Test
    void measureArgs() throws RobotException {
        assertEquals(new RobotCommand(RobotCommand.Op.MEASURE, RobotCommand.UP, 32), RobotCommand.parse("agent.measure", List.of(4.0, 32.0)));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.heightProfile", 0, 33));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.measure", 0, RobotCommand.MAX_MEASURE + 1));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.measure", 0, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.measureRun", 0, 6, 10));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.measureGap", 3, 10));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.heightProfile", RobotCommand.UP, 10));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.groundHeight", 30_000_001, 0));
        // 32 blocks on a side is corners 31 apart
        assertEquals(RobotCommand.Op.IS_AREA_FREE, RobotCommand.parse("agent.isAreaFree", List.of(0, 0, 0, 31, -31, 31)).op());
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.isAreaFree", 0, 0, 0, 32, 0, 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.countSolid", 0, 0, 0, 0, 0, -32));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.countSolid", 0, 0, 0, 0, 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.findBounds", RobotCommand.MAX_AREA + 1));
    }

    // Issues #78 and #86: no arguments for home; entity kinds 0 to 3 and a radius of 1 to 8. come is never parsed.
    @Test
    void homeAndEntityArgs() throws RobotException {
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.goHome", 1));
        assertEquals(new RobotCommand(RobotCommand.Op.DETECT_ENTITY, RobotCommand.DOWN, 2), RobotCommand.parse("agent.detectEntity", List.of(2.0, 5.0)));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.detectEntity", RobotCommand.ENTITY_KINDS, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.detectEntity", 0, 6));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.countNearby", 0, RobotCommand.MAX_NEARBY_RADIUS + 1));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.countNearby", 0, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.countNearby", -1, 4));
        // Sensors (issues #159, #161, #162): the radar radius is 1 to 16, the kind and direction the shared checks.
        assertEquals(new RobotCommand(RobotCommand.Op.RADAR, 0, RobotCommand.MAX_RADAR_RADIUS), RobotCommand.parse("agent.radar", List.of(0.0, 16.0)));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.radar", 0, RobotCommand.MAX_RADAR_RADIUS + 1));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.radar", 0, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.radar", RobotCommand.ENTITY_KINDS, 4));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.detectLiquid", 6));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.isSafe", -1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.freeSlots", 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.isInventoryFull", 1));
        // World queries (issue #87): no arguments.
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.lightLevel", 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.biome", 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.temperature", 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("gameplay.timeOfDay", 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("gameplay.isRaining", true));
        assertEquals(Result.Code.BAD_ARGS, rejected("come"));
    }

    // Issue #179: one block code, 0 (air) for the block under the robot; #180: an optional side, 0, left 2 or right 3.
    @Test
    void followPathArgs() throws RobotException {
        int gravel = BlockIds.byName("gravel");
        assertEquals(new RobotCommand(RobotCommand.Op.FOLLOW_PATH, gravel, 0), RobotCommand.parse("agent.followPath", List.of((double) gravel)));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.followPath", -1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.followPath"));
        assertEquals(new RobotCommand(RobotCommand.Op.FOLLOW_PATH, gravel, RobotCommand.LEFT_TURN), RobotCommand.parse("agent.followPath", List.of((double) gravel, 2.0)));
        assertEquals(new RobotCommand(RobotCommand.Op.FOLLOW_PATH, 0, RobotCommand.RIGHT_TURN), RobotCommand.parse("agent.followPath", List.of(0.0, 3.0)));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.followPath", 0, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.followPath", 0, 0, 0));
    }

    // Issue #177: CameraMode Off 0, Eyes 1, Follow 2.
    @Test
    void cameraArgs() throws RobotException {
        assertEquals(new RobotCommand(RobotCommand.Op.CAMERA, 2, 0), RobotCommand.parse("agent.camera", List.of(2.0)));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.camera", RobotCommand.CAMERA_MODES));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.camera", -1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.camera"));
    }

    // Issue #80: one line, capped, and only a string (the sim sends "" + text).
    @Test
    void sayTextIsOneCappedLine() throws RobotException {
        assertEquals(new RobotCommand(RobotCommand.Op.SAY, 0, 0, 0, "Hi there"), RobotCommand.parse("agent.say", List.of("Hi\nthere")));
        String said = RobotCommand.parse("agent.say", List.of("x".repeat(500))).text();
        assertEquals(RobotCommand.MAX_SAY, said.length());
        assertEquals("x".repeat(RobotCommand.MAX_SAY - 3) + "...", said);
        assertEquals("", RobotCommand.parse("agent.say", List.of("")).text());
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.say", 5));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.say"));
    }

    // Issue #26: player.say is plain text only, one line of at most 256 characters; no section-sign format codes.
    @Test
    void playerSayIsPlainCappedText() throws RobotException {
        assertEquals(new RobotCommand(RobotCommand.Op.PLAYER_SAY, 0, 0, 0, "Hi  cred there"), RobotCommand.parse("player.say", List.of("Hi §cred\nthere")));
        String said = RobotCommand.parse("player.say", List.of("x".repeat(1000))).text();
        assertEquals("x".repeat(RobotCommand.MAX_PLAYER_SAY - 3) + "...", said);
        assertEquals("a b", RobotCommand.parse("agent.say", List.of("a§b")).text());
        assertEquals(Result.Code.BAD_ARGS, rejected("player.say", 5));
        assertEquals(Result.Code.BAD_ARGS, rejected("player.say"));
    }

    @Test
    void playerQueriesAndTeleport() throws RobotException {
        assertEquals(RobotCommand.Op.PLAYER_POSITION, RobotCommand.parse("player.position", List.of()).op());
        assertEquals(RobotCommand.Op.PLAYER_ORIENTATION, RobotCommand.parse("player.getOrientation", List.of()).op());
        assertEquals(RobotCommand.Op.PLAYER_NAME, RobotCommand.parse("player.name", List.of()).op());
        assertEquals(Result.Code.BAD_ARGS, rejected("player.name", 1));
        assertEquals(new RobotCommand(RobotCommand.Op.PLAYER_TELEPORT, 10, -60, 3), RobotCommand.parse("player.teleport", List.of(10, -60.0, 3L)));
        assertEquals(Result.Code.BAD_ARGS, rejected("player.teleport", Double.NaN, 0, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("player.teleport", 0, Double.POSITIVE_INFINITY, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("player.teleport", 0.5, 0, 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("player.teleport", 0, 0, Positions.MAX_COORDINATE + 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("player.teleport", 0, 0));
    }

    @Test
    void rejectsOutOfRangeDistance() {
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.move", 0, 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.move", 0, -3));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.move", 0, RobotCommand.MAX_MOVE + 1));
    }

    @Test
    void queueIsCapped() throws RobotException {
        RobotQueue queue = new RobotQueue();
        RobotCommand move = new RobotCommand(RobotCommand.Op.MOVE, 0, 1);
        for (int i = 0; i < RobotQueue.MAX_LENGTH; i++) queue.submit(queue.run(), move, r -> {});
        RobotException e = assertThrows(RobotException.class, () -> queue.submit(queue.run(), move, r -> {}));
        assertEquals(Result.Code.RATE_LIMITED, e.code);
        assertEquals(RobotQueue.MAX_LENGTH, queue.size());
    }

    @Test
    void startClearsQueueAndOldRunIsStopped() throws RobotException {
        RobotQueue queue = new RobotQueue();
        List<Result> results = new ArrayList<>();
        long oldRun = queue.run();
        queue.submit(oldRun, new RobotCommand(RobotCommand.Op.TURN, 3, 0), results::add);

        long newRun = queue.start();
        assertNotEquals(oldRun, newRun);
        assertEquals(0, queue.size());
        assertEquals(List.of(Result.error(Result.Code.STOPPED, "a new run started")), results);

        RobotException e = assertThrows(RobotException.class,
                () -> queue.submit(oldRun, new RobotCommand(RobotCommand.Op.TURN, 3, 0), r -> {}));
        assertEquals(Result.Code.STOPPED, e.code);
        queue.submit(newRun, new RobotCommand(RobotCommand.Op.TURN, 3, 0), results::add);
        assertEquals(1, queue.size());
    }

    @Test
    void stopInvalidatesRunAndRunIdsAreUnique() throws RobotException {
        RobotQueue a = new RobotQueue();
        RobotQueue b = new RobotQueue();
        assertNotEquals(a.run(), b.run());
        long run = a.run();
        a.submit(run, new RobotCommand(RobotCommand.Op.MOVE, 0, 5), r -> {});
        a.stop();
        assertEquals(0, a.size());
        assertEquals(Result.Code.STOPPED,
                assertThrows(RobotException.class, () -> a.submit(run, new RobotCommand(RobotCommand.Op.MOVE, 0, 1), r -> {})).code);
    }

    @Test
    void finishReportsInOrder() throws RobotException {
        RobotQueue queue = new RobotQueue();
        List<Object> done = new ArrayList<>();
        queue.submit(queue.run(), new RobotCommand(RobotCommand.Op.MOVE, 0, 1), r -> done.add("first " + r.value()));
        queue.submit(queue.run(), new RobotCommand(RobotCommand.Op.MOVE, 0, 2), r -> done.add("second " + r.value()));
        assertEquals(1, queue.head().command.blocks());
        queue.finish(Result.ok(true));
        assertEquals(2, queue.head().command.blocks());
        queue.finish(Result.ok(false));
        assertEquals(List.of("first true", "second false"), done);
    }

    @Test
    void acceptsInventoryAndBlockOps() throws RobotException {
        assertEquals(new RobotCommand(RobotCommand.Op.PLACE, RobotCommand.DOWN, 0), RobotCommand.parse("agent.place", List.of(5)));
        assertEquals(new RobotCommand(RobotCommand.Op.INSPECT, RobotCommand.FORWARD, RobotCommand.KIND_DATA), RobotCommand.parse("agent.inspect", List.of(1, 0)));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_ITEM, 1, 64, 27), RobotCommand.parse("agent.setItem", List.of(1, 64, 27)));
        assertEquals(new RobotCommand(RobotCommand.Op.TRANSFER, 5, 1, 2), RobotCommand.parse("agent.transfer", List.of(5, 1, 2)));
        assertEquals(new RobotCommand(RobotCommand.Op.DROP, RobotCommand.BACK, 3, 1), RobotCommand.parse("agent.drop", List.of(1, 3, 1)));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_ASSIST, 0, 1), RobotCommand.parse("agent.setAssist", List.of(0, true)));
        assertEquals(new RobotCommand(RobotCommand.Op.COLLECT_ALL, 0, 0), RobotCommand.parse("agent.collectAll", List.of()));
        assertEquals(new RobotCommand(RobotCommand.Op.SET_GRAVITY, 0, 0), RobotCommand.parse("agent.setGravity", List.of(false)));
    }

    @Test
    void rejectsBadInventoryAndBlockArgs() {
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.setSlot", 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.getItemCount", 28));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.setItem", 1, 65, 1));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.setItem", 1, 0, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.setItem", 9999, 1, 1));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.transfer", 1, 1, 28));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("agent.drop", 0, 1, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.place", 6));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.detect", 2, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.inspect", 0, -1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.setAssist", 1, true));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.setAssist", 0, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.collectAll", 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("agent.setGravity", 1));
    }

    // Issue #90: a placeable block or air, an operation the editor has, and at most MAX_FILL_VOLUME blocks in the box.
    @Test
    void buildingArgs() throws RobotException {
        int stone = BlockIds.byName("stone");
        assertEquals(new RobotCommand(RobotCommand.Op.FILL, 1, 2, 3, 4, 5, 6, stone, RobotShapes.HOLLOW, null),
                RobotCommand.parse("blocks.fill", List.of(stone, 1, 2, 3, 4, 5, 6, 1)));
        assertEquals(new RobotCommand(RobotCommand.Op.LINE, 0, 0, 0, 9, 9, 9, 0, 0, null),
                RobotCommand.parse("shapes.line", List.of(0, 0, 0, 0, 9, 9, 9)));
        assertEquals(new RobotCommand(RobotCommand.Op.CIRCLE, 5, 64, 5, 32, 2, 0, stone, RobotShapes.SHAPE_HOLLOW, null),
                RobotCommand.parse("shapes.circle", List.of(stone, 5, 64, 5, 32, 2, 2)));
        assertEquals(new RobotCommand(RobotCommand.Op.SPHERE, 0, 0, 0, 15, 0, 0, stone, 0, null),
                RobotCommand.parse("shapes.sphere", List.of(stone, 0, 0, 0, 15, 0)));
        // 32 x 32 x 32 is the cap; one more layer, or a sphere of radius 16 (33 wide), is too much.
        RobotCommand.parse("blocks.fill", List.of(stone, 0, 0, 0, 31, 31, 31, 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("blocks.fill", stone, 0, 0, 0, 31, 32, 31, 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("shapes.sphere", stone, 0, 0, 0, 16, 0));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("shapes.circle", stone, 0, 0, 0, 33, 1, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("blocks.fill", stone, 0, 0, 0, 1, 1, 1, 5));
        assertEquals(Result.Code.BAD_ARGS, rejected("shapes.sphere", stone, 0, 0, 0, 2, 3));
        assertEquals(Result.Code.BAD_ARGS, rejected("shapes.circle", stone, 0, 0, 0, 2, 3, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("blocks.fill", BlockIds.byName("water"), 0, 0, 0, 1, 1, 1, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("blocks.fill", 12345678, 0, 0, 0, 1, 1, 1, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("shapes.line", stone, 0, 0, 0, 1, 1));
    }

    // Issue #182: shapes.maze with or without a seed, 5 to 32 blocks each way, real blocks for walls and floor.
    @Test
    void mazeArgs() throws RobotException {
        int stone = BlockIds.byName("stone"), gravel = BlockIds.byName("gravel");
        assertEquals(new RobotCommand(RobotCommand.Op.MAZE, 1, 64, -3, 5, 32, 42, stone, gravel, null),
                RobotCommand.parse("shapes.maze", List.of(1, 64, -3, 5, 32, stone, gravel, 42)));
        RobotCommand random = RobotCommand.parse("shapes.maze", List.of(1, 64, -3, 9, 9, stone, gravel));
        assertEquals(new RobotCommand(RobotCommand.Op.MAZE, 1, 64, -3, 9, 9, random.f(), stone, gravel, null), random);
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("shapes.maze", 0, 0, 0, 4, 9, stone, gravel, 1));
        assertEquals(Result.Code.OUT_OF_RANGE, rejected("shapes.maze", 0, 0, 0, 9, 33, stone, gravel, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("shapes.maze", 0, 0, 0, 9, 9, 0, gravel, 1));
        assertEquals(Result.Code.BAD_ARGS, rejected("shapes.maze", 0, 0, 0, 9, 9, stone, 0));
        assertEquals(Result.Code.BAD_ARGS, rejected("shapes.maze", 0, 0, 0, 9, 9, stone, gravel, 1.5));
        assertEquals(Result.Code.BAD_ARGS, rejected("shapes.maze", 0, 0, 0, 9, 9, stone));
    }
}
