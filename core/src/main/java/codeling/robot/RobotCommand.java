package codeling.robot;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One validated robot command. Plain Java so the validator is unit-testable without Minecraft.
 * Enum values match the MakeCode API (architecture section 5).
 *
 * <p>Arguments by op: {@code a} is the SixDirection for every op that has one ({@code b} is then the kind for
 * detect/inspect, the slot for drop, the block count for move). Otherwise: setSlot/getItemCount/getItemSpace
 * {@code a} = slot; setItem {@code a} = item code (0 empties the slot), {@code b} = count, {@code c} = slot; drop {@code c} = quantity;
 * transfer {@code a} = quantity, {@code b} = source slot, {@code c} = destination slot; setAssist {@code a} = assist,
 * {@code b} = 1 for on; setGravity {@code a} = 1 for on; say {@code text} = the text. Slots are 1-based as in MakeCode.
 * Container ops (after the direction): storeItems {@code b} = robot slot or 0 for all; takeItems {@code b} = container
 * slot or 0 for the first stack, {@code c} = count; takeItem {@code b} = item ({@link ItemIds} code), {@code c} = count;
 * containerCount {@code b} = item.
 * Measuring (issue #126): measure {@code a} = direction, {@code b} = max; measureRun {@code b} = along, {@code c} = max;
 * measureGap {@code a} = axis, {@code b} = max; groundHeight {@code a, b} = world x, z; heightProfile {@code a} =
 * direction, {@code b} = length; isAreaFree/countSolid {@code a, b, c} and {@code d, e, f} = the two world corners;
 * findBounds {@code b} = max.
 * Home and entities (issues #76 and #86): come {@code a, b, c} = the player's block; detectEntity {@code a} = direction,
 * {@code b} = kind; countNearby {@code a} = kind, {@code b} = radius.
 * Sensors (issues #159, #161, #162): radar {@code a} = kind, {@code b} = radius; detectLiquid and isSafe {@code a} =
 * direction; freeSlots and isInventoryFull take nothing.
 * World queries (issue #87): gameplay.timeOfDay, gameplay.isRaining, lightLevel, biome and temperature take nothing.
 * Building (issue #90, {@link RobotShapes}): {@code g} = the block ({@link BlockIds} code, 0 for air), {@code h} = the
 * FillOperation or ShapeOperation; fill and line {@code a, b, c} to {@code d, e, f} = the two world corners; circle and
 * sphere {@code a, b, c} = the world centre, {@code d} = radius, circle {@code e} = axis. maze (issue #182)
 * {@code a, b, c} = the north-west floor corner, {@code d} = width (x), {@code e} = length (z), {@code f} = seed,
 * {@code g} = wall block, {@code h} = floor block.
 */
public record RobotCommand(Op op, int a, int b, int c, int d, int e, int f, int g, int h, String text) {
    public enum Op {
        MOVE, TURN, PLACE, DESTROY, DETECT, INSPECT, SET_SLOT, GET_ITEM_COUNT, GET_ITEM_SPACE, SET_ITEM,
        COLLECT_ALL, DROP, DROP_ALL, TRANSFER, SET_ASSIST, SET_GRAVITY, GET_POSITION, GET_ORIENTATION,
        /** positions.toWorld: {@code a, b, c} = x, y, z; answered by ServerRelay against the owner, never queued. */
        RELATIVE_TO_WORLD, LOCAL_TO_WORLD,
        /** agent.findNearest / findNearestCategory: {@code a} = block code or category, {@code b} = radius. */
        FIND_BLOCK, FIND_CATEGORY,
        /** agent.say: {@code text}, capped at {@link #MAX_SAY} characters, shown over the robot. */
        SAY,
        /**
         * The player API (issue #26), for the program's owner only and never another player: say {@code text}, capped at
         * {@link #MAX_PLAYER_SAY}; teleport {@code a, b, c} = world x, y, z. Answered by PlayerApi, never queued on a robot.
         */
        PLAYER_SAY, PLAYER_POSITION, PLAYER_ORIENTATION, PLAYER_NAME, PLAYER_TELEPORT,
        /** gameplay.timeOfDay / isRaining (issue #87): the owner's dimension, answered by PlayerApi like the player calls. */
        TIME_OF_DAY, IS_RAINING,
        /** Chests, furnaces and other containers next to the robot (issue #71). */
        STORE_ITEMS, TAKE_ITEMS, TAKE_ITEM, CONTAINER_COUNT, CONTAINER_SIZE, SORT_CONTAINER,
        /** agent.craft / canCraft (issue #93) and agent.stonecut (issue #115): {@code a} = item, {@code b} = count. */
        CRAFT, CAN_CRAFT, STONECUT,
        /** agent.interact (issue #102), agent.isOpen (#106), agent.redstonePower (#107): {@code a} = direction. */
        INTERACT, IS_OPEN, REDSTONE_POWER,
        /** agent.moveTo (issue #44): {@code a, b, c} = world x, y, z; the editor resolves ~ and ^ positions first. */
        MOVE_TO,
        /**
         * agent.goToRobot (issue #243): {@code text} = the name of another of the owner's robots, "" for the default
         * robot. Walks like MOVE_TO to a free block next to that robot; the server looks the robot up when it starts.
         */
        GO_TO_ROBOT,
        /** agent.followPath (issue #179): {@code a} = the path block ({@link BlockIds} code), 0 (air) for the block under the robot; {@code b} = the side to keep to (#180), 0 or LEFT_TURN / RIGHT_TURN. */
        FOLLOW_PATH,
        /** Measuring (issues #127 to #132): reads only, the robot does not move. */
        MEASURE, MEASURE_RUN, MEASURE_GAP, GROUND_HEIGHT, HEIGHT_PROFILE, IS_AREA_FREE, COUNT_SOLID, FIND_BOUNDS,
        /** agent.setHome / goHome (issue #78): the home is a saved position on the robot; goHome walks there like moveTo. */
        SET_HOME, GO_HOME,
        /** /robot come only, never from the relay (issue #77): walk to the player's block, else teleport there. */
        COME,
        /** agent.detectEntity / countNearby (issue #86). */
        DETECT_ENTITY, COUNT_NEARBY,
        /** agent.radar (issue #159), detectLiquid and isSafe (#161), freeSlots and isInventoryFull (#162): reads only. */
        RADAR, DETECT_LIQUID, IS_SAFE, FREE_SLOTS, IS_INVENTORY_FULL,
        /** agent.lightLevel, biome and temperature (issue #87): reads of the robot's own block only. */
        LIGHT_LEVEL, BIOME, TEMPERATURE,
        /** blocks.fill, shapes.line, shapes.circle, shapes.sphere (issue #90): bulk placement through the FakePlayer. */
        FILL, LINE, CIRCLE, SPHERE,
        /** shapes.maze (issue #182): built by the same loop as the shapes. */
        MAZE,
        /** agent.camera (issue #177): {@code a} = CameraMode; answered by ServerRelay against the owner, never queued. */
        CAMERA
    }

    // SixDirection
    public static final int FORWARD = 0, BACK = 1, LEFT = 2, RIGHT = 3, UP = 4, DOWN = 5;
    // TurnDirection
    public static final int LEFT_TURN = 2, RIGHT_TURN = 3;
    // AgentDetection and AgentInspection share the values: Block 0, Redstone/Data 1
    public static final int KIND_BLOCK = 0, KIND_REDSTONE = 1, KIND_DATA = 1;
    // AgentAssist: only PLACE_ON_MOVE in v1
    public static final int PLACE_ON_MOVE = 0;

    // shortcut: proposal from architecture section 5, move to server config when step 4 adds limits.
    public static final int MAX_MOVE = 64;
    public static final int SLOTS = 27;
    public static final int MAX_QUANTITY = 64;
    // shortcut: proposal from issue #40, move to server config with the other limits in step 4.
    public static final int MAX_FIND_RADIUS = 32;
    /** BlockCategory members in the editor (Logs, Leaves, Ores, Planks, Wool, Flowers); RobotManager maps them to tags. */
    public static final int CATEGORIES = 6;
    // shortcut: proposal (issue #80), long enough for a sentence, short enough to stay readable over the robot.
    public static final int MAX_SAY = 80;
    /** player.say: vanilla's own limit for a chat line. */
    public static final int MAX_PLAYER_SAY = 256;
    /** A double chest; modded containers with more slots are reached with slot 0 (the first stack). */
    public static final int MAX_CONTAINER_SLOT = 54;
    // shortcut: proposals from issues #127 to #132, move to server config with the other limits in step 4.
    /** measure, measureRun, measureGap and heightProfile read at most this many blocks in a line (owner: the same 32 as findNearest). */
    public static final int MAX_MEASURE = MAX_FIND_RADIUS;
    /** isAreaFree, countSolid and findBounds work in a box of at most this many blocks on each side. */
    public static final int MAX_AREA = 32;
    /** EntityKind in the editor: Mob 0, Animal 1, Player 2, Item 3. */
    public static final int ENTITY_KINDS = 4;
    // shortcut: proposal for issue #86's "small radius cap", move to server config with the other limits in step 4.
    public static final int MAX_NEARBY_RADIUS = 8;
    /** agent.radar (issue #158): less than any view distance, so it shows only what a player nearby could see anyway. */
    public static final int MAX_RADAR_RADIUS = 16;
    // shortcut: keeps a radar reply far under Protocol.MAX_MESSAGE_BYTES; raise with the message size if players need more.
    /** agent.radar answers at most this many entities, the nearest ones. */
    public static final int MAX_RADAR = 64;
    // shortcut: proposal (issue #90), the vanilla /fill limit; move to server config with the other limits in step 4.
    /** blocks.fill and the shapes change at most this many blocks: the box around the shape may hold no more. */
    public static final int MAX_FILL_VOLUME = 32768;
    /** FillOperation in the editor: Replace, Hollow, Outline, Keep, Destroy. ShapeOperation: Replace, Outline, Hollow. */
    public static final int FILL_OPERATIONS = 5, SHAPE_OPERATIONS = 3;
    /** shapes.maze: width and length from 5 (two cells) to MAX_AREA blocks, so the robot can stand in the middle and reach it all. */
    public static final int MIN_MAZE = 5;
    /** CameraMode in the editor: Off 0, Eyes 1, Follow 2 (RobotCamera.OFF, EYES, FOLLOW). */
    public static final int CAMERA_MODES = 3;
    // shortcut: 16 fits a name tag and a chat line; raise it if players ask for longer names.
    /** Robot names (issue #140): a letter, then letters, digits, _ or -, this many at most. The default robot has the empty name. */
    public static final int MAX_NAME = 16;
    /** Words the /robot commands use themselves (stop all, camera follow, camera off, nametag on), so no robot is called that. */
    private static final List<String> RESERVED_NAMES = List.of("all", "follow", "off", "on");

    /** Why {@code name} cannot be a robot's name, or null when it can. Plain rules, so programs and commands agree. */
    public static String nameProblem(String name) {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_-]{0," + (MAX_NAME - 1) + "}")) {
            return "A robot name starts with a letter and has at most " + MAX_NAME + " letters, digits, _ or - (no spaces)";
        }
        if (RESERVED_NAMES.contains(name.toLowerCase(java.util.Locale.ROOT))) return "\"" + name + "\" is a word the /robot commands use, choose another name";
        return null;
    }

    public RobotCommand(Op op, int a, int b, int c, int d, int e, int f, String text) {
        this(op, a, b, c, d, e, f, 0, 0, text);
    }

    public RobotCommand(Op op, int a, int b, int c, String text) {
        this(op, a, b, c, 0, 0, 0, text);
    }

    public RobotCommand(Op op, int a, int b, int c) {
        this(op, a, b, c, null);
    }

    public RobotCommand(Op op, int a, int b) {
        this(op, a, b, 0);
    }

    public int direction() {
        return a;
    }

    public int blocks() {
        return b;
    }

    /** Validates an untrusted {@code op} and argument list, as they will arrive from the relay in step 2. */
    public static RobotCommand parse(String op, List<?> args) throws RobotException {
        if (op == null || args == null) throw new RobotException(Result.Code.BAD_ARGS, "missing op or args");
        switch (op) {
            case "agent.move" -> {
                arity(op, args, 2);
                int direction = direction(args.get(0));
                int blocks = integer(args.get(1), "blocks");
                if (blocks < 1 || blocks > MAX_MOVE) throw new RobotException(Result.Code.OUT_OF_RANGE, "blocks must be 1 to " + MAX_MOVE);
                return new RobotCommand(Op.MOVE, direction, blocks);
            }
            case "agent.turn" -> {
                arity(op, args, 1);
                int direction = integer(args.get(0), "direction");
                if (direction != LEFT_TURN && direction != RIGHT_TURN) throw new RobotException(Result.Code.BAD_ARGS, "turn direction must be 2 (left) or 3 (right)");
                return new RobotCommand(Op.TURN, direction, 0);
            }
            case "agent.place", "agent.destroy", "agent.dropAll" -> {
                arity(op, args, 1);
                Op o = op.equals("agent.place") ? Op.PLACE : op.equals("agent.destroy") ? Op.DESTROY : Op.DROP_ALL;
                return new RobotCommand(o, direction(args.get(0)), 0);
            }
            case "agent.interact", "agent.isOpen", "agent.redstonePower" -> {
                arity(op, args, 1);
                Op o = op.equals("agent.interact") ? Op.INTERACT : op.equals("agent.isOpen") ? Op.IS_OPEN : Op.REDSTONE_POWER;
                return new RobotCommand(o, direction(args.get(0)), 0);
            }
            case "agent.detect", "agent.inspect" -> {
                arity(op, args, 2);
                int kind = integer(args.get(0), "kind");
                if (kind != KIND_BLOCK && kind != KIND_REDSTONE) throw new RobotException(Result.Code.BAD_ARGS, "kind must be 0 or 1");
                return new RobotCommand(op.equals("agent.detect") ? Op.DETECT : Op.INSPECT, direction(args.get(1)), kind);
            }
            case "agent.setSlot", "agent.getItemCount", "agent.getItemSpace" -> {
                arity(op, args, 1);
                Op o = op.equals("agent.setSlot") ? Op.SET_SLOT : op.equals("agent.getItemCount") ? Op.GET_ITEM_COUNT : Op.GET_ITEM_SPACE;
                return new RobotCommand(o, slot(args.get(0), "slot"), 0);
            }
            case "agent.setItem" -> {
                arity(op, args, 3);
                int item = integer(args.get(0), "blockOrItem");
                if (item != 0) item(item);
                return new RobotCommand(Op.SET_ITEM, item, quantity(args.get(1), "count"), slot(args.get(2), "slot"));
            }
            case "agent.collectAll", "agent.getPosition", "agent.getOrientation" -> {
                arity(op, args, 0);
                Op o = op.equals("agent.collectAll") ? Op.COLLECT_ALL : op.equals("agent.getPosition") ? Op.GET_POSITION : Op.GET_ORIENTATION;
                return new RobotCommand(o, 0, 0);
            }
            case "agent.drop" -> {
                arity(op, args, 3);
                return new RobotCommand(Op.DROP, direction(args.get(0)), slot(args.get(1), "slot"), quantity(args.get(2), "quantity"));
            }
            case "agent.transfer" -> {
                arity(op, args, 3);
                return new RobotCommand(Op.TRANSFER, quantity(args.get(0), "quantity"), slot(args.get(1), "sourceSlot"), slot(args.get(2), "destinationSlot"));
            }
            case "agent.setAssist" -> {
                arity(op, args, 2);
                if (integer(args.get(0), "assist") != PLACE_ON_MOVE) throw new RobotException(Result.Code.BAD_ARGS, "only PLACE_ON_MOVE (0) is supported");
                if (!(args.get(1) instanceof Boolean on)) throw new RobotException(Result.Code.BAD_ARGS, "on must be true or false");
                return new RobotCommand(Op.SET_ASSIST, PLACE_ON_MOVE, on ? 1 : 0);
            }
            case "agent.setGravity" -> {
                arity(op, args, 1);
                if (!(args.get(0) instanceof Boolean on)) throw new RobotException(Result.Code.BAD_ARGS, "on must be true or false");
                return new RobotCommand(Op.SET_GRAVITY, on ? 1 : 0, 0);
            }
            case "agent.findNearest", "agent.findNearestCategory" -> {
                arity(op, args, 2);
                int what = integer(args.get(0), "block");
                boolean category = op.equals("agent.findNearestCategory");
                if (category ? what < 0 || what >= CATEGORIES : BlockIds.toModern(what) == null) {
                    throw new RobotException(Result.Code.BAD_ARGS, (category ? "unknown category " : "unknown block ") + what);
                }
                int radius = integer(args.get(1), "radius");
                if (radius < 1 || radius > MAX_FIND_RADIUS) throw new RobotException(Result.Code.OUT_OF_RANGE, "radius must be 1 to " + MAX_FIND_RADIUS);
                return new RobotCommand(category ? Op.FIND_CATEGORY : Op.FIND_BLOCK, what, radius);
            }
            case "agent.say" -> {
                arity(op, args, 1);
                if (!(args.get(0) instanceof String text)) throw new RobotException(Result.Code.BAD_ARGS, "text must be a string");
                return new RobotCommand(Op.SAY, 0, 0, 0, sayText(text, MAX_SAY));
            }
            case "player.say" -> {
                arity(op, args, 1);
                if (!(args.get(0) instanceof String text)) throw new RobotException(Result.Code.BAD_ARGS, "message must be a string");
                return new RobotCommand(Op.PLAYER_SAY, 0, 0, 0, sayText(text, MAX_PLAYER_SAY));
            }
            case "player.position", "player.getOrientation", "player.name" -> {
                arity(op, args, 0);
                return new RobotCommand(op.equals("player.position") ? Op.PLAYER_POSITION : op.equals("player.name") ? Op.PLAYER_NAME : Op.PLAYER_ORIENTATION, 0, 0);
            }
            case "gameplay.timeOfDay", "gameplay.isRaining" -> {
                arity(op, args, 0);
                return new RobotCommand(op.equals("gameplay.timeOfDay") ? Op.TIME_OF_DAY : Op.IS_RAINING, 0, 0);
            }
            case "player.teleport" -> {
                arity(op, args, 3);
                return new RobotCommand(Op.PLAYER_TELEPORT, coordinate(args.get(0), "x"), coordinate(args.get(1), "y"), coordinate(args.get(2), "z"));
            }
            case "agent.storeItems" -> {
                arity(op, args, 2);
                return new RobotCommand(Op.STORE_ITEMS, direction(args.get(0)), slotOrZero(args.get(1), "slot", SLOTS), 0);
            }
            case "agent.takeItems" -> {
                arity(op, args, 3);
                return new RobotCommand(Op.TAKE_ITEMS, direction(args.get(0)), slotOrZero(args.get(1), "slot", MAX_CONTAINER_SLOT),
                        quantity(args.get(2), "count"));
            }
            case "agent.takeItem" -> {
                arity(op, args, 3);
                return new RobotCommand(Op.TAKE_ITEM, direction(args.get(0)), item(args.get(1)), quantity(args.get(2), "count"));
            }
            case "agent.containerCount" -> {
                arity(op, args, 2);
                return new RobotCommand(Op.CONTAINER_COUNT, direction(args.get(0)), item(args.get(1)));
            }
            case "agent.containerSize", "agent.sortContainer" -> {
                arity(op, args, 1);
                return new RobotCommand(op.equals("agent.containerSize") ? Op.CONTAINER_SIZE : Op.SORT_CONTAINER, direction(args.get(0)), 0);
            }
            case "agent.craft", "agent.canCraft", "agent.stonecut" -> {
                arity(op, args, 2);
                Op o = op.equals("agent.craft") ? Op.CRAFT : op.equals("agent.canCraft") ? Op.CAN_CRAFT : Op.STONECUT;
                return new RobotCommand(o, item(args.get(0)), quantity(args.get(1), "count"));
            }
            case "agent.moveTo" -> {
                arity(op, args, 3);
                return new RobotCommand(Op.MOVE_TO, coordinate(args.get(0), "x"), coordinate(args.get(1), "y"), coordinate(args.get(2), "z"));
            }
            case "agent.goToRobot" -> {
                arity(op, args, 1);
                if (!(args.get(0) instanceof String name)) throw new RobotException(Result.Code.BAD_ARGS, "robot must be a robot's name");
                String problem = name.isEmpty() ? null : nameProblem(name);
                if (problem != null) throw new RobotException(Result.Code.BAD_ARGS, problem);
                return new RobotCommand(Op.GO_TO_ROBOT, 0, 0, 0, name);
            }
            case "agent.followPath" -> {
                // The side is optional (issue #180), so a client that sends only the block still works.
                if (args.size() != 1) arity(op, args, 2);
                int block = integer(args.get(0), "block");
                if (BlockIds.toModern(block) == null) throw new RobotException(Result.Code.BAD_ARGS, "unknown block " + block);
                int side = args.size() == 2 ? integer(args.get(1), "side") : 0;
                if (side != 0 && side != LEFT_TURN && side != RIGHT_TURN) throw new RobotException(Result.Code.BAD_ARGS, "side must be 0 (none), 2 (left) or 3 (right)");
                return new RobotCommand(Op.FOLLOW_PATH, block, side);
            }
            case "agent.measure" -> {
                arity(op, args, 2);
                return new RobotCommand(Op.MEASURE, direction(args.get(0)), upTo(args.get(1), "max", MAX_MEASURE));
            }
            case "agent.measureRun" -> {
                arity(op, args, 3);
                return new RobotCommand(Op.MEASURE_RUN, direction(args.get(0)), direction(args.get(1)), upTo(args.get(2), "max", MAX_MEASURE));
            }
            case "agent.measureGap" -> {
                arity(op, args, 2);
                int axis = integer(args.get(0), "axis");
                if (axis < 0 || axis > 2) throw new RobotException(Result.Code.BAD_ARGS, "axis must be 0 (x), 1 (y) or 2 (z)");
                return new RobotCommand(Op.MEASURE_GAP, axis, upTo(args.get(1), "max", MAX_MEASURE));
            }
            case "agent.groundHeight" -> {
                arity(op, args, 2);
                return new RobotCommand(Op.GROUND_HEIGHT, coordinate(args.get(0), "x"), coordinate(args.get(1), "z"));
            }
            case "agent.heightProfile" -> {
                arity(op, args, 2);
                int direction = direction(args.get(0));
                if (direction > RIGHT) throw new RobotException(Result.Code.BAD_ARGS, "direction must be forward, back, left or right (0 to 3)");
                return new RobotCommand(Op.HEIGHT_PROFILE, direction, upTo(args.get(1), "length", MAX_MEASURE));
            }
            case "agent.isAreaFree", "agent.countSolid" -> {
                arity(op, args, 6);
                int[] c = new int[6];
                for (int i = 0; i < 6; i++) c[i] = coordinate(args.get(i), "xyz".charAt(i % 3) + (i < 3 ? "1" : "2"));
                for (int i = 0; i < 3; i++) {
                    if (Math.abs(c[i] - c[i + 3]) >= MAX_AREA) throw new RobotException(Result.Code.OUT_OF_RANGE, "the box may be at most " + MAX_AREA + " blocks on each side");
                }
                return new RobotCommand(op.equals("agent.isAreaFree") ? Op.IS_AREA_FREE : Op.COUNT_SOLID, c[0], c[1], c[2], c[3], c[4], c[5], null);
            }
            case "agent.findBounds" -> {
                arity(op, args, 1);
                return new RobotCommand(Op.FIND_BOUNDS, FORWARD, upTo(args.get(0), "max", MAX_AREA));
            }
            case "agent.setHome", "agent.goHome" -> {
                arity(op, args, 0);
                return new RobotCommand(op.equals("agent.setHome") ? Op.SET_HOME : Op.GO_HOME, 0, 0);
            }
            case "agent.detectEntity" -> {
                arity(op, args, 2);
                int kind = entityKind(args.get(0));
                return new RobotCommand(Op.DETECT_ENTITY, direction(args.get(1)), kind);
            }
            case "agent.countNearby" -> {
                arity(op, args, 2);
                return new RobotCommand(Op.COUNT_NEARBY, entityKind(args.get(0)), upTo(args.get(1), "radius", MAX_NEARBY_RADIUS));
            }
            case "agent.radar" -> {
                arity(op, args, 2);
                return new RobotCommand(Op.RADAR, entityKind(args.get(0)), upTo(args.get(1), "radius", MAX_RADAR_RADIUS));
            }
            case "agent.detectLiquid", "agent.isSafe" -> {
                arity(op, args, 1);
                return new RobotCommand(op.equals("agent.isSafe") ? Op.IS_SAFE : Op.DETECT_LIQUID, direction(args.get(0)), 0);
            }
            case "agent.lightLevel", "agent.biome", "agent.temperature" -> {
                arity(op, args, 0);
                return new RobotCommand(op.equals("agent.lightLevel") ? Op.LIGHT_LEVEL : op.equals("agent.biome") ? Op.BIOME : Op.TEMPERATURE, 0, 0);
            }
            case "agent.freeSlots", "agent.isInventoryFull" -> {
                arity(op, args, 0);
                return new RobotCommand(op.equals("agent.freeSlots") ? Op.FREE_SLOTS : Op.IS_INVENTORY_FULL, 0, 0);
            }
            case "blocks.fill", "shapes.line" -> {
                boolean fill = op.equals("blocks.fill");
                arity(op, args, fill ? 8 : 7);
                int block = block(args.get(0));
                int[] c = new int[6];
                for (int i = 0; i < 6; i++) c[i] = coordinate(args.get(i + 1), "xyz".charAt(i % 3) + (i < 3 ? "1" : "2"));
                int operation = fill ? operation(args.get(7), FILL_OPERATIONS) : RobotShapes.REPLACE;
                return building(new RobotCommand(fill ? Op.FILL : Op.LINE, c[0], c[1], c[2], c[3], c[4], c[5], block, operation, null));
            }
            case "shapes.circle", "shapes.sphere" -> {
                boolean circle = op.equals("shapes.circle");
                arity(op, args, circle ? 7 : 6);
                int block = block(args.get(0));
                int x = coordinate(args.get(1), "x"), y = coordinate(args.get(2), "y"), z = coordinate(args.get(3), "z");
                int radius = upTo(args.get(4), "radius", MAX_FIND_RADIUS);
                int axis = circle ? integer(args.get(5), "axis") : 0;
                if (axis < 0 || axis > 2) throw new RobotException(Result.Code.BAD_ARGS, "axis must be 0 (x), 1 (y) or 2 (z)");
                int operation = operation(args.get(circle ? 6 : 5), SHAPE_OPERATIONS);
                return building(new RobotCommand(circle ? Op.CIRCLE : Op.SPHERE, x, y, z, radius, axis, 0, block, operation, null));
            }
            case "shapes.maze" -> {
                // The seed is optional: without it the server picks one, so every maze is new.
                if (args.size() != 7) arity(op, args, 8);
                int x = coordinate(args.get(0), "x"), y = coordinate(args.get(1), "y"), z = coordinate(args.get(2), "z");
                int width = integer(args.get(3), "width"), length = integer(args.get(4), "length");
                if (Math.min(width, length) < MIN_MAZE || Math.max(width, length) > MAX_AREA) {
                    throw new RobotException(Result.Code.OUT_OF_RANGE, "width and length must be " + MIN_MAZE + " to " + MAX_AREA);
                }
                int wall = block(args.get(5)), floor = block(args.get(6));
                if (wall == 0 || floor == 0) throw new RobotException(Result.Code.BAD_ARGS, "the walls and the floor need a block, not air");
                int seed = args.size() == 8 ? integer(args.get(7), "seed") : ThreadLocalRandom.current().nextInt();
                return building(new RobotCommand(Op.MAZE, x, y, z, width, length, seed, wall, floor, null));
            }
            case "agent.camera" -> {
                arity(op, args, 1);
                int mode = integer(args.get(0), "mode");
                if (mode < 0 || mode >= CAMERA_MODES) throw new RobotException(Result.Code.BAD_ARGS, "mode must be 0 (off), 1 (eyes) or 2 (follow)");
                return new RobotCommand(Op.CAMERA, mode, 0);
            }
            case "positions.toWorld" -> {
                arity(op, args, 4);
                int kind = integer(args.get(0), "kind");
                if (kind != Positions.RELATIVE && kind != Positions.LOCAL) throw new RobotException(Result.Code.BAD_ARGS, "kind must be 1 (relative) or 2 (local)");
                return new RobotCommand(kind == Positions.RELATIVE ? Op.RELATIVE_TO_WORLD : Op.LOCAL_TO_WORLD,
                        coordinate(args.get(1), "x"), coordinate(args.get(2), "y"), coordinate(args.get(3), "z"));
            }
            default -> throw new RobotException(Result.Code.BAD_ARGS, "unknown op " + op);
        }
    }

    // The box around a building command may hold at most MAX_FILL_VOLUME blocks.
    private static RobotCommand building(RobotCommand command) throws RobotException {
        if (RobotShapes.volume(RobotShapes.box(command)) > MAX_FILL_VOLUME) {
            throw new RobotException(Result.Code.OUT_OF_RANGE, "at most " + MAX_FILL_VOLUME + " blocks at once");
        }
        return command;
    }

    // A block that has an item form, so the robot can place it, or 0 for air (clears).
    private static int block(Object value) throws RobotException {
        int block = integer(value, "block");
        if (block != 0 && (BlockIds.toModern(block) == null || ItemIds.toModern(block) == null)) {
            throw new RobotException(Result.Code.BAD_ARGS, "unknown or unplaceable block " + block);
        }
        if (block != 0) inThisVersion(ItemIds.toModern(block));
        return block;
    }

    private static int operation(Object value, int count) throws RobotException {
        int operation = integer(value, "operation");
        if (operation < 0 || operation >= count) throw new RobotException(Result.Code.BAD_ARGS, "operation must be 0 to " + (count - 1));
        return operation;
    }

    /**
     * One line of plain text of at most {@code max} characters: control characters and the section sign (which the
     * client reads as a colour or format code even in literal text) become spaces, longer text ends in "...".
     */
    static String sayText(String text, int max) {
        String line = text.replaceAll("[\\p{Cntrl}§]", " ");
        if (line.codePointCount(0, line.length()) <= max) return line;
        return line.substring(0, line.offsetByCodePoints(0, max - 3)) + "...";
    }

    private static void arity(String op, List<?> args, int n) throws RobotException {
        if (args.size() != n) throw new RobotException(Result.Code.BAD_ARGS, op + " takes " + n + " argument(s)");
    }

    private static int direction(Object value) throws RobotException {
        int direction = integer(value, "direction");
        if (direction < FORWARD || direction > DOWN) throw new RobotException(Result.Code.BAD_ARGS, "direction must be 0 to 5");
        return direction;
    }

    private static int entityKind(Object value) throws RobotException {
        int kind = integer(value, "kind");
        if (kind < 0 || kind >= ENTITY_KINDS) throw new RobotException(Result.Code.BAD_ARGS, "kind must be 0 to " + (ENTITY_KINDS - 1));
        return kind;
    }

    private static int slot(Object value, String name) throws RobotException {
        int slot = integer(value, name);
        if (slot < 1 || slot > SLOTS) throw new RobotException(Result.Code.OUT_OF_RANGE, name + " must be 1 to " + SLOTS);
        return slot;
    }

    // 0 means all slots (storeItems) or the first stack (takeItems).
    private static int slotOrZero(Object value, String name, int max) throws RobotException {
        int slot = integer(value, name);
        if (slot < 0 || slot > max) throw new RobotException(Result.Code.OUT_OF_RANGE, name + " must be 0 to " + max);
        return slot;
    }

    // An item code, or the code of a Block with an item form (issue #125); air, water and unknown codes are no item.
    private static int item(Object value) throws RobotException {
        int item = integer(value, "item");
        if (ItemIds.toModern(item) == null) throw new RobotException(Result.Code.BAD_ARGS, "unknown item " + item);
        inThisVersion(ItemIds.toModern(item));
        return item;
    }

    // The editor offers 26.3's items on every version (issue #189); an older Minecraft refuses the ones it lacks.
    private static void inThisVersion(String modernId) throws RobotException {
        if (!ItemIds.inThisVersion(modernId)) throw new RobotException(Result.Code.BAD_ARGS, modernId + " is not in this Minecraft version");
    }

    private static int quantity(Object value, String name) throws RobotException {
        int n = integer(value, name);
        if (n < 1 || n > MAX_QUANTITY) throw new RobotException(Result.Code.OUT_OF_RANGE, name + " must be 1 to " + MAX_QUANTITY);
        return n;
    }

    private static int upTo(Object value, String name, int max) throws RobotException {
        int n = integer(value, name);
        if (n < 1 || n > max) throw new RobotException(Result.Code.OUT_OF_RANGE, name + " must be 1 to " + max);
        return n;
    }

    private static int coordinate(Object value, String name) throws RobotException {
        int n = integer(value, name);
        if (Math.abs(n) > Positions.MAX_COORDINATE) throw new RobotException(Result.Code.OUT_OF_RANGE, name + " must be within " + Positions.MAX_COORDINATE);
        return n;
    }

    // JSON numbers may arrive as Double or Long; accept any whole number that fits an int.
    private static int integer(Object value, String name) throws RobotException {
        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) return (int) d;
        }
        throw new RobotException(Result.Code.BAD_ARGS, name + " must be a whole number");
    }
}
