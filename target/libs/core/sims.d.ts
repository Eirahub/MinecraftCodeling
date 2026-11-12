// Auto-generated from simulator. Do not edit.
    /**
     * A block position: in the world, relative to you (~), or local to your facing (^).
     */
    //% blockNamespace=positions
    declare class Position {
        /**
         * One coordinate of the position.
         * @param axis which coordinate, eg: Axis.X
         */
        //% blockId=position_get_value block="%position=agent_get_position|%axis value"
        //% blockNamespace=positions weight=80
        //% shim=.getValue
        public getValue(axis: Axis): number;

        /**
         * The same place in world coordinates. The game works out positions relative to you from where you stand and face now.
         */
        //% blockId=position_to_world block="%position=positions_pos|to world"
        //% blockNamespace=positions weight=79
        //% shim=.toWorldAsync promise
        public toWorld(): Position;

        /**
         * Add the coordinates of another position. The result is the same kind as this one.
         * @param other what to add
         */
        //% blockId=position_add block="%position=positions_pos|+ %other=positions_pos"
        //% blockNamespace=positions weight=78
        //% shim=.add
        public add(other: Position): Position;

        /**
         * True if both positions are the same kind with the same coordinates.
         * @param other the position to compare with
         */
        //% blockId=position_equals block="%position=positions_pos|= %other=positions_pos"
        //% blockNamespace=positions weight=77
        //% shim=.equals
        public equals(other: Position): boolean;

        /**
         * The position as text, written as in commands: "1 2 3", "~1 ~2 ~3" or "^1 ^2 ^3".
         */
        //% blockId=position_to_string block="%position=positions_pos|as text"
        //% blockNamespace=positions weight=76
        //% shim=.toString
        public toString(): string;

        /**
         * How far it is to another position in a straight line, in blocks, with decimals: from world 0 64 0 to world
         * 3 68 0 is 5. Two positions of different kinds are first turned into world positions, as to world does.
         * @param other the position to measure to
         */
        //% blockId=position_distance_to block="%position=positions_pos|distance to %other=positions_pos"
        //% blockNamespace=positions weight=75
        //% shim=.distanceToAsync promise
        public distanceTo(other: Position): number;

        /**
         * Subtract the coordinates of another position: how far you go from the other one to this one. The result is
         * the same kind as this one, and like + it does not turn other kinds into world positions.
         * @param other what to subtract
         */
        //% blockId=position_subtract block="%position=positions_pos|- %other=positions_pos"
        //% blockNamespace=positions weight=74
        //% shim=.subtract
        public subtract(other: Position): Position;

        /**
         * Multiply every coordinate by a number, rounded to whole blocks (half away from zero). The same kind as this one.
         * @param factor how many times, eg: 2
         */
        //% blockId=position_scale block="%position=positions_pos|times %factor"
        //% blockNamespace=positions weight=73
        //% shim=.scale
        public scale(factor: number): Position;

    }
declare namespace positions {
    /**
     * A position relative to your feet, with world axes (x east, y up, z south). The same as pos(x, y, z).
     */
    //% blockHidden=true
    //% shim=positions::create
    function create(x: number, y: number, z: number): Position;

    /**
     * A position that turns with you: x to your left, y up, z forward. The same as posLocal(x, y, z).
     */
    //% blockHidden=true
    //% shim=positions::createLocal
    function createLocal(x: number, y: number, z: number): Position;

    /**
     * A fixed position in the world. The same as world(x, y, z).
     */
    //% blockHidden=true
    //% shim=positions::createWorld
    function createWorld(x: number, y: number, z: number): Position;

    /**
     * A random block position in the box between two corners, the same kind as the first corner.
     * @param from one corner
     * @param to the opposite corner
     */
    //% blockId=positions_random block="pick random position|from %from=positions_pos|to %to=positions_pos"
    //% weight=60
    //% shim=positions::random
    function random(from: Position, to: Position): Position;

    /**
     * A new list with the same positions, nearest to a position first (straight line, as distance to measures it).
     * Positions just as far away keep their order. The list itself does not change; no list gives an empty list.
     * @param list the positions to sort
     * @param from where to measure from
     */
    //% blockId=positions_sort_by_distance block="sort %list=variables_get(list)|by distance from %from=positions_pos"
    //% weight=59
    //% shim=positions::sortByDistanceAsync promise
    function sortByDistance(list: Position[], from: Position): Position[];

    /**
     * The position in the list nearest to a position (straight line, as distance to measures it), the first one if
     * two are just as near, or null when the list is empty.
     * @param list the positions to look through
     * @param from where to measure from
     */
    //% blockId=positions_nearest block="nearest in %list=variables_get(list)|to %from=positions_pos"
    //% weight=58
    //% shim=positions::nearestAsync promise
    function nearest(list: Position[], from: Position): Position;

}
declare namespace agent {
    /**
     * Move the robot in a direction
     * @param direction which way to move, eg: SixDirection.Forward
     * @param steps how many blocks, eg: 1
     */
    //% blockId=agent_move block="agent move %direction|by %steps"
    //% steps.min=1 steps.max=64
    //% group="Movement" weight=100
    //% shim=agent::moveAsync promise
    function move(direction: SixDirection, steps: number): void;

    /**
     * Turn the robot left or right
     * @param direction which way to turn, eg: TurnDirection.Left
     */
    //% blockId=agent_turn block="agent turn %direction"
    //% group="Movement" weight=99
    //% shim=agent::turnAsync promise
    function turn(direction: TurnDirection): void;

    /**
     * Walk or fly to a position by itself, around walls and through wooden doors. With gravity the robot walks: it
     * climbs 1-block steps and falls down. When the position is a solid block (a log from find nearest), the robot
     * stops next to it and turns to face it if it can. True when it got there, false when there is no way (or none
     * within the search limit). Relative positions are worked out from where you stand now.
     * @param position where to go, eg: pos(0, 0, 5)
     */
    //% blockId=agent_move_to block="agent move to %position=positions_pos"
    //% group="Movement" weight=97
    //% shim=agent::moveToAsync promise
    function moveTo(position: Position): boolean;

    /**
     * Remember where the robot stands now as its home. The home is saved with the robot, also over a restart.
     */
    //% blockId=agent_set_home block="agent set home"
    //% group="Movement" weight=96
    //% shim=agent::setHomeAsync promise
    function setHome(): void;

    /**
     * Walk or fly home by itself, the same way as move to. True when it got there, false when there is no home, the
     * home is in another dimension, or there is no way (or none within the search limit).
     */
    //% blockId=agent_go_home block="agent go home"
    //% group="Movement" weight=95
    //% shim=agent::goHomeAsync promise
    function goHome(): boolean;

    /**
     * Walk along a path of one kind of block (dirt path, gravel, planks, ...) until it ends or splits. Without a block
     * the robot follows the block it stands on. With a block, a robot next to the path steps onto it; one that is not
     * on the path or next to it answers end and says "I am not on the path". It goes forward if it can, else the only way the path goes, one step
     * up or down too, and through wooden doors and gates. At a junction it stops on the junction block, still facing
     * the way it came: turn and follow the path again. Answers why it stopped: end, junction, blocked, or limit after
     * 256 blocks. On a path that goes round in a ring, it stops when it is back where it started (end).
     * Keep left (or right) to solve a maze: at every junction the robot takes the left-most (right-most) way, and at
     * a dead end with a wall in front it turns round and walks back. It stops where the path ends without a wall (the
     * way out), back where it started, or at the limit: follow the path again to go on.
     * @param block the path block, eg: Block.Gravel
     * @param side the side to keep to, eg: TurnDirection.Left
     */
    //% blockId=agent_follow_path block="agent follow path||of %block|keep %side"
    //% expandableArgumentMode="enabled"
    //% group="Movement" weight=94
    //% shim=agent::followPathAsync promise
    function followPath(block?: Block, side?: TurnDirection): PathStop;

    /**
     * Place the block or item in the selected slot.
     * @param direction where to place, eg: SixDirection.Forward
     */
    //% blockId=agent_place block="agent place %direction"
    //% group="Building" weight=90
    // A statement as in MakeCode, so pasted `agent.place(...)` lines stay blocks; the server's true/false goes to the log.
    // shortcut: no way to read the result in a program, add a separate boolean block if players ask for one.
    //% shim=agent::placeAsync promise
    function place(direction: SixDirection): void;

    /**
     * Break the block next to the robot; the drops go into its inventory.
     * @param direction which block to break, eg: SixDirection.Forward
     */
    //% blockId=agent_destroy block="agent destroy %direction"
    //% group="Building" weight=89
    //% shim=agent::destroyAsync promise
    function destroy(direction: SixDirection): void;

    /**
     * Use the block next to the robot like a right-click with an empty hand: open or close a wooden door, trapdoor or
     * fence gate, press a button, flip a lever. Iron doors need redstone. Other blocks are left alone.
     * @param direction which block to use, eg: SixDirection.Forward
     */
    //% blockId=agent_interact block="agent interact %direction"
    //% group="Building" weight=87
    // A statement as in MakeCode; the server's true/false (did the block react) goes to the log, agent.isOpen reads doors.
    //% shim=agent::interactAsync promise
    function interact(direction: SixDirection): void;

    /**
     * Turn a robot assist on or off. Place on move puts the selected block in each spot the robot leaves.
     * @param assist which assist, eg: AgentAssist.PlaceOnMove
     * @param on true to turn it on, eg: true
     */
    //% blockId=agent_set_assist block="agent assist %assist|%on"
    //% group="Building" weight=88
    //% shim=agent::setAssistAsync promise
    function setAssist(assist: AgentAssist, on: boolean): void;

    /**
     * Turn gravity on or off. With gravity the robot falls after a sideways step and climbs 1-block bumps.
     * @param on true to turn it on, eg: true
     */
    //% blockId=agent_set_gravity block="agent gravity %on"
    //% group="Movement" weight=98
    //% shim=agent::setGravityAsync promise
    function setGravity(on: boolean): void;

    /**
     * Check what is next to the robot. Block: true if the block would stop a move.
     * @param kind what to look for, eg: AgentDetection.Block
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_detect block="agent detect %kind|%direction"
    //% group="Inspecting" weight=80
    //% shim=agent::detectAsync promise
    function detect(kind: AgentDetection, direction: SixDirection): boolean;

    /**
     * True if a hostile mob, an animal, a player or a dropped item is in the block next to the robot.
     * @param kind what to look for, eg: EntityKind.Mob
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_detect_entity block="agent detect %kind|%direction"
    //% group="Inspecting" weight=80
    //% shim=agent::detectEntityAsync promise
    function detectEntity(kind: EntityKind, direction: SixDirection): boolean;

    /**
     * How many hostile mobs, animals, players or dropped items are in the cube of that radius around the robot.
     * @param kind what to count, eg: EntityKind.Animal
     * @param radius how far to look, 1 to 8, eg: 4
     */
    //% blockId=agent_count_nearby block="agent count nearby %kind|within %radius"
    //% radius.min=1 radius.max=8
    //% group="Inspecting" weight=79
    //% shim=agent::countNearbyAsync promise
    function countNearby(kind: EntityKind, radius: number): number;

    /**
     * True if the door, trapdoor or fence gate next to the robot is open. False for blocks that cannot open.
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_is_open block="agent is open %direction"
    //% group="Inspecting" weight=79
    //% shim=agent::isOpenAsync promise
    function isOpen(direction: SixDirection): boolean;

    /**
     * Redstone signal strength of the block next to the robot, 0 to 15, as F3 shows it: what redstone dust, a lever or
     * a redstone block gives out, or how strongly another block is powered.
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_redstone_power block="agent redstone power %direction"
    //% group="Inspecting" weight=79
    //% shim=agent::redstonePowerAsync promise
    function redstonePower(direction: SixDirection): number;

    /**
     * Where the robot is: its block position in the world.
     */
    //% blockId=agent_get_position block="agent position"
    //% group="Inspecting" weight=78
    //% shim=agent::getPositionAsync promise
    function getPosition(): Position;

    /**
     * Which way the robot faces, in degrees: south 0, west 90, north 180, east -90.
     */
    //% blockId=agent_get_orientation block="agent orientation"
    //% group="Inspecting" weight=77
    //% shim=agent::getOrientationAsync promise
    function getOrientation(): number;

    /**
     * Where the nearest block of this kind is, counted in block steps from the robot, or null if there is none
     * within the radius (in loaded chunks).
     * @param block the block to look for, eg: Block.OakLog
     * @param radius how far to look, 1 to 32, eg: 16
     */
    //% blockId=agent_find_nearest block="agent find nearest %block|within %radius"
    //% radius.min=1 radius.max=32
    //% group="Inspecting" weight=76
    //% shim=agent::findNearestAsync promise
    function findNearest(block: Block, radius: number): Position;

    /**
     * Where the nearest block of a category (all logs, all leaves, all ores, ...) is, or null if there is none
     * within the radius (in loaded chunks).
     * @param category what to look for, eg: BlockCategory.Logs
     * @param radius how far to look, 1 to 32, eg: 16
     */
    //% blockId=agent_find_nearest_category block="agent find nearest %category|within %radius"
    //% radius.min=1 radius.max=32
    //% group="Inspecting" weight=75
    //% shim=agent::findNearestCategoryAsync promise
    function findNearestCategory(category: BlockCategory, radius: number): Position;

    // Sensors (issues #159 and #161): what is around the robot. Invisible players and spectators are never found.
    /**
     * Where the hostile mobs, animals, players or dropped items in the cube of that radius around the robot are,
     * nearest first: a list of their block positions in the world, empty when there is none. At most the 64 nearest.
     * @param kind what to look for, eg: EntityKind.Mob
     * @param radius how far to look, 1 to 16, eg: 8
     */
    //% blockId=agent_radar block="agent radar %kind|within %radius"
    //% radius.min=1 radius.max=16
    //% group="Sensors" weight=62
    //% shim=agent::radarAsync promise
    function radar(kind: EntityKind, radius: number): Position[];

    /**
     * Where the nearest hostile mob, animal, player or dropped item in the cube of that radius around the robot is,
     * or null if there is none. The same as the first position from radar.
     * @param kind what to look for, eg: EntityKind.Player
     * @param radius how far to look, 1 to 16, eg: 8
     */
    //% blockId=agent_nearest_entity block="agent nearest %kind|within %radius"
    //% radius.min=1 radius.max=16
    //% group="Sensors" weight=61
    //% shim=agent::nearestEntityAsync promise
    function nearestEntity(kind: EntityKind, radius: number): Position;

    /**
     * Which liquid is in the block next to the robot: water (also a waterlogged block), lava or none.
     * @param direction where to look, eg: SixDirection.Down
     */
    //% blockId=agent_detect_liquid block="agent detect liquid %direction"
    //% group="Sensors" weight=60
    //% shim=agent::detectLiquidAsync promise
    function detectLiquid(direction: SixDirection): Liquid;

    /**
     * False if the block next to the robot is dangerous: lava in it or under it, a drop of more than 3 blocks under
     * it, or a hostile mob in it or next to it. A wall is safe: the robot cannot go there.
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_is_safe block="agent is safe %direction"
    //% group="Sensors" weight=59
    //% shim=agent::isSafeAsync promise
    function isSafe(direction: SixDirection): boolean;

    // Measuring (issues #126 to #132): the robot reads blocks in loaded chunks and does not move.
    /**
     * How many free blocks there are in a direction before the first block that would stop a move. Answers max when
     * nothing is in the way within max blocks.
     * @param direction where to measure, eg: SixDirection.Forward
     * @param max how far to look, 1 to 32, eg: 32
     */
    //% blockId=agent_measure block="agent measure free %direction|up to %max"
    //% max.min=1 max.max=32
    //% group="Measuring" weight=74
    //% shim=agent::measureAsync promise
    function measure(direction: SixDirection, max: number): number;

    /**
     * How long a wall is. Counts the solid blocks in a row, starting with the block in direction and going along: for
     * the wall in front, along right gives its width and along up its height. 0 when there is no block there.
     * @param direction where the wall is, eg: SixDirection.Forward
     * @param along which way to count, eg: SixDirection.Right
     * @param max how far to count, 1 to 32, eg: 32
     */
    //% blockId=agent_measure_run block="agent measure wall %direction|along %along|up to %max"
    //% max.min=1 max.max=32
    //% group="Measuring" weight=73
    //% shim=agent::measureRunAsync promise
    function measureRun(direction: SixDirection, along: SixDirection, max: number): number;

    /**
     * The free width across the robot along an axis, from the obstacle on one side to the obstacle on the other, at
     * the robot's height (the robot's own block counts): the width of a valley or corridor, or floor to ceiling with Y.
     * Answers max when one side is open.
     * @param axis which way across, eg: Axis.X
     * @param max how far to look on each side, 1 to 32, eg: 32
     */
    //% blockId=agent_measure_gap block="agent measure gap %axis|up to %max"
    //% max.min=1 max.max=32
    //% group="Measuring" weight=72
    //% shim=agent::measureGapAsync promise
    function measureGap(axis: Axis, max: number): number;

    /**
     * The y of the highest solid block at a position's x and z, with leaves ignored (the game's own height map, so
     * water counts as ground). The position's own y is not used.
     * @param position where to look, eg: pos(0, 0, 5)
     */
    //% blockId=agent_ground_height block="agent ground height at %position=positions_pos"
    //% group="Measuring" weight=71
    //% shim=agent::groundHeightAsync promise
    function groundHeight(position: Position): number;

    /**
     * The ground heights along a line: the first one block from the robot in direction, then one per block. Each is
     * what ground height answers there. Shows the shape of a valley or a hill.
     * @param direction which way: forward, back, left or right, eg: SixDirection.Forward
     * @param length how many blocks, 1 to 32, eg: 16
     */
    //% blockId=agent_height_profile block="agent height profile %direction|length %length"
    //% length.min=1 length.max=32
    //% group="Measuring" weight=70
    //% shim=agent::heightProfileAsync promise
    function heightProfile(direction: SixDirection, length: number): number[];

    /**
     * True if every block in the box between two corners is air or something a block can replace (grass, a snow
     * layer, water). At most 32 blocks on each side.
     * @param corner1 one corner
     * @param corner2 the opposite corner
     */
    //% blockId=agent_is_area_free block="agent is area free|from %corner1=positions_pos|to %corner2=positions_pos"
    //% group="Measuring" weight=69
    //% shim=agent::isAreaFreeAsync promise
    function isAreaFree(corner1: Position, corner2: Position): boolean;

    /**
     * How many blocks in the box between two corners are not air or replaceable, so a program can decide whether to
     * clear it first. At most 32 blocks on each side.
     * @param corner1 one corner
     * @param corner2 the opposite corner
     */
    //% blockId=agent_count_solid block="agent count solid blocks|from %corner1=positions_pos|to %corner2=positions_pos"
    //% group="Measuring" weight=68
    //% shim=agent::countSolidAsync promise
    function countSolid(corner1: Position, corner2: Position): number;

    /**
     * The outer edges of the building in front of the robot: the lowest and the highest corner of the solid blocks
     * connected to the block in front (touching sides), at most max blocks on each side. A list of two world
     * positions, or null when there is no block in front. Blocks on the ground connect to the ground.
     * @param max the largest size on each side, 1 to 32, eg: 32
     */
    //% blockId=agent_find_bounds block="agent find bounds forward|up to %max"
    //% max.min=1 max.max=32
    //% group="Measuring" weight=67
    //% shim=agent::findBoundsAsync promise
    function findBounds(max: number): Position[];

    /**
     * Read the block next to the robot. Block: its id (-1 if unknown). Data: its data value.
     * @param kind what to read, eg: AgentInspection.Block
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_inspect block="agent inspect %kind|%direction"
    //% group="Inspecting" weight=79
    //% shim=agent::inspectAsync promise
    function inspect(kind: AgentInspection, direction: SixDirection): number;

    /**
     * Choose the slot that place and place on move use.
     * @param slot slot 1 to 27, eg: 1
     */
    //% blockId=agent_set_slot block="agent set active slot %slot"
    //% slot.min=1 slot.max=27
    //% group="Inventory" weight=70
    //% shim=agent::setSlotAsync promise
    function setSlot(slot: number): void;

    /**
     * Number of items in a slot.
     * @param slot slot 1 to 27, eg: 1
     */
    //% blockId=agent_get_item_count block="agent item count in slot %slot"
    //% slot.min=1 slot.max=27
    //% group="Inventory" weight=69
    //% shim=agent::getItemCountAsync promise
    function getItemCount(slot: number): number;

    /**
     * How many more items fit in a slot.
     * @param slot slot 1 to 27, eg: 1
     */
    //% blockId=agent_get_item_space block="agent space left in slot %slot"
    //% slot.min=1 slot.max=27
    //% group="Inventory" weight=68
    //% shim=agent::getItemSpaceAsync promise
    function getItemSpace(slot: number): number;

    /**
     * How many of the robot's 27 slots are empty.
     */
    //% blockId=agent_free_slots block="agent free slots"
    //% group="Inventory" weight=68
    //% shim=agent::freeSlotsAsync promise
    function freeSlots(): number;

    /**
     * True if no slot is empty. A slot that is not a full stack can still take more of the same item.
     */
    //% blockId=agent_is_inventory_full block="agent is inventory full"
    //% group="Inventory" weight=68
    //% shim=agent::isInventoryFullAsync promise
    function isInventoryFull(): boolean;

    /**
     * Put blocks or items in a slot, replacing what is there (creative mode or the gamerule codeling:free_items only). Air empties it.
     * @param blockOrItem the item or block, eg: Item.Stone
     * @param count how many, eg: 64
     * @param slot slot 1 to 27, eg: 1
     */
    //% blockId=agent_set_item block="agent give %blockOrItem|count %count|in slot %slot"
    //% blockOrItem.shadow=codeling_item count.min=1 count.max=64 slot.min=1 slot.max=27
    //% group="Inventory" weight=67
    //% shim=agent::setItemAsync promise
    function setItem(blockOrItem: number, count: number, slot: number): void;

    /**
     * Pick up the items lying within one block of the robot.
     */
    //% blockId=agent_collect_all block="agent collect all"
    //% group="Inventory" weight=66
    //% shim=agent::collectAllAsync promise
    function collectAll(): void;

    /**
     * Drop items from a slot.
     * @param direction where to drop, eg: SixDirection.Forward
     * @param slot slot 1 to 27, eg: 1
     * @param quantity how many, eg: 1
     */
    //% blockId=agent_drop block="agent drop %direction|from slot %slot|amount %quantity"
    //% slot.min=1 slot.max=27 quantity.min=1 quantity.max=64
    //% group="Inventory" weight=65
    //% shim=agent::dropAsync promise
    function drop(direction: SixDirection, slot: number, quantity: number): void;

    /**
     * Drop everything the robot carries.
     * @param direction where to drop, eg: SixDirection.Forward
     */
    //% blockId=agent_drop_all block="agent drop all %direction"
    //% group="Inventory" weight=64
    //% shim=agent::dropAllAsync promise
    function dropAll(direction: SixDirection): void;

    /**
     * Move items from one slot to another.
     * @param quantity how many, eg: 1
     * @param sourceSlot from slot, eg: 1
     * @param destinationSlot to slot, eg: 2
     */
    //% blockId=agent_transfer block="agent transfer amount %quantity|from slot %sourceSlot|to slot %destinationSlot"
    //% quantity.min=1 quantity.max=64 sourceSlot.min=1 sourceSlot.max=27 destinationSlot.min=1 destinationSlot.max=27
    //% group="Inventory" weight=63
    //% shim=agent::transferAsync promise
    function transfer(quantity: number, sourceSlot: number, destinationSlot: number): void;

    /**
     * Put items from the robot into the chest, barrel, hopper or furnace next to it, as a hopper would: a furnace takes
     * what to smelt from above and fuel from the side. True if anything moved; what does not fit stays in the robot.
     * @param direction where the container is, eg: SixDirection.Forward
     * @param slot robot slot 1 to 27, or 0 for all slots, eg: 0
     */
    //% blockId=agent_store_items block="agent store items %direction|from slot %slot"
    //% slot.min=0 slot.max=27
    //% group="Containers" weight=60
    //% shim=agent::storeItemsAsync promise
    function storeItems(direction: SixDirection, slot: number): boolean;

    /**
     * Take items from one stack in the container next to the robot. A furnace gives its output downwards.
     * True if anything moved.
     * @param direction where the container is, eg: SixDirection.Forward
     * @param slot container slot, or 0 for the first stack, eg: 0
     * @param count how many at most, eg: 64
     */
    //% blockId=agent_take_items block="agent take items %direction|from slot %slot|amount %count"
    //% slot.min=0 slot.max=54 count.min=1 count.max=64
    //% group="Containers" weight=59
    //% shim=agent::takeItemsAsync promise
    function takeItems(direction: SixDirection, slot: number, count: number): boolean;

    /**
     * Take one kind of item from any slot of the container next to the robot. Stops when the robot is full.
     * Answers how many it got.
     * @param direction where the container is, eg: SixDirection.Forward
     * @param item what to take, eg: Item.Cobblestone
     * @param count how many at most, eg: 64
     */
    //% blockId=agent_take_item block="agent take from %direction|item %item|amount %count"
    //% item.shadow=codeling_item count.min=1 count.max=64
    //% group="Containers" weight=58
    //% shim=agent::takeItemAsync promise
    function takeItem(direction: SixDirection, item: number, count: number): number;

    /**
     * How many of an item the container next to the robot holds (0 if there is none or it is protected).
     * @param direction where the container is, eg: SixDirection.Forward
     * @param item what to count, eg: Item.Cobblestone
     */
    //% blockId=agent_container_count block="agent count in container %direction|of %item"
    //% item.shadow=codeling_item
    //% group="Containers" weight=57
    //% shim=agent::containerCountAsync promise
    function containerCount(direction: SixDirection, item: number): number;

    /**
     * Number of slots in the container next to the robot: 27 for a chest, 54 for a double chest, 0 if there is none.
     * @param direction where the container is, eg: SixDirection.Forward
     */
    //% blockId=agent_container_size block="agent container size %direction"
    //% group="Containers" weight=56
    //% shim=agent::containerSizeAsync promise
    function containerSize(direction: SixDirection): number;

    /**
     * Sort the chest, barrel, shulker box or hopper next to the robot: equal items are stacked together and ordered by
     * name. Furnaces are left alone.
     * @param direction where the container is, eg: SixDirection.Forward
     */
    //% blockId=agent_sort_container block="agent sort container %direction"
    //% group="Containers" weight=55
    //% shim=agent::sortContainerAsync promise
    function sortContainer(direction: SixDirection): void;

    /**
     * Craft an item from the robot's own inventory, like a player: 2x2 recipes always, 3x3 recipes only with a crafting
     * table next to the robot. Makes whole recipes until at least count items are made, the ingredients run out or the
     * robot is full. Answers how many it made; with nothing to make it is 0 and nothing is used.
     * @param item what to make, eg: Item.OakPlanks
     * @param count how many at least, eg: 4
     */
    //% blockId=agent_craft block="agent craft %item|amount %count"
    //% item.shadow=codeling_item count.min=1 count.max=64
    //% group="Crafting" weight=54
    //% shim=agent::craftAsync promise
    function craft(item: number, count: number): number;

    /**
     * True if the robot can craft at least count of an item right now, with what it carries and where it stands.
     * @param item what to make, eg: Item.Stick
     * @param count how many at least, eg: 4
     */
    //% blockId=agent_can_craft block="agent can craft %item|amount %count"
    //% item.shadow=codeling_item count.min=1 count.max=64
    //% group="Crafting" weight=53
    //% shim=agent::canCraftAsync promise
    function canCraft(item: number, count: number): boolean;

    /**
     * Cut stone with the stonecutter next to the robot, from the robot's own inventory. Answers how many it made.
     * @param item what to make, eg: Item.StoneBricks
     * @param count how many at least, eg: 4
     */
    //% blockId=agent_stonecut block="agent stonecut %item|amount %count"
    //% item.shadow=codeling_item count.min=1 count.max=64
    //% group="Crafting" weight=52
    //% shim=agent::stonecutAsync promise
    function stonecut(item: number, count: number): number;

    /**
     * Show a short text over the robot for a few seconds. Text longer than 80 characters is cut.
     * @param text what to say, eg: "Hello!"
     */
    //% blockId=agent_say block="agent say %text"
    //% group="Feedback" weight=50
    //% shim=agent::sayAsync promise
    function say(text: string): void;

    /**
     * Show your screen through the robot's eyes, from behind the robot, or your own view again. The server decides
     * who may use the robot camera; if it is not allowed, the program stops with an error.
     * @param mode which view, eg: CameraMode.Follow
     */
    //% blockId=agent_camera block="agent camera %mode"
    //% group="Feedback" weight=49
    //% shim=agent::cameraAsync promise
    function camera(mode: CameraMode): void;

}
    /**
     * One of your robots, by name. It has the same calls as agent; agent is your default robot.
     */
    //% blockNamespace=robots
    declare class Robot {
        /**
         * Move the robot in a direction
         * @param direction which way to move, eg: SixDirection.Forward
         * @param steps how many blocks, eg: 1
         */
        //% blockId=robot_move block="%robot=robots_get|move %direction|by %steps"
        //% steps.min=1 steps.max=64
        //% blockNamespace=robots weight=90
        //% shim=.moveAsync promise
        public move(direction: SixDirection, steps: number): void;

        /**
         * Turn the robot left or right
         * @param direction which way to turn, eg: TurnDirection.Left
         */
        //% blockId=robot_turn block="%robot=robots_get|turn %direction"
        //% blockNamespace=robots weight=89
        //% shim=.turnAsync promise
        public turn(direction: TurnDirection): void;

        /**
         * Walk or fly to a position by itself, like agent move to. True when it got there.
         * @param position where to go, eg: pos(0, 0, 5)
         */
        //% blockId=robot_move_to block="%robot=robots_get|move to %position=positions_pos"
        //% blockNamespace=robots weight=88
        //% shim=.moveToAsync promise
        public moveTo(position: Position): boolean;

        /**
         * Place the block or item in the selected slot.
         * @param direction where to place, eg: SixDirection.Forward
         */
        //% blockId=robot_place block="%robot=robots_get|place %direction"
        //% blockNamespace=robots weight=87
        //% shim=.placeAsync promise
        public place(direction: SixDirection): void;

        /**
         * Break the block next to the robot; the drops go into its inventory.
         * @param direction which block to break, eg: SixDirection.Forward
         */
        //% blockId=robot_destroy block="%robot=robots_get|destroy %direction"
        //% blockNamespace=robots weight=86
        //% shim=.destroyAsync promise
        public destroy(direction: SixDirection): void;

        /**
         * Pick up the items lying within one block of the robot.
         */
        //% blockId=robot_collect_all block="%robot=robots_get|collect all"
        //% blockNamespace=robots weight=85
        //% shim=.collectAllAsync promise
        public collectAll(): void;

        /**
         * Show a short text over the robot for a few seconds.
         * @param text what to say, eg: "Hello!"
         */
        //% blockId=robot_say block="%robot=robots_get|say %text"
        //% blockNamespace=robots weight=84
        //% shim=.sayAsync promise
        public say(text: string): void;

        // The other agent calls, in JavaScript and Python only (no blocks: the agent blocks already show them all).
        /** Like agent.setHome. */
        //%
        //% shim=.setHomeAsync promise
        public setHome(): void;

        /** Like agent.goHome. */
        //%
        //% shim=.goHomeAsync promise
        public goHome(): boolean;

        /** Like agent.followPath. */
        //%
        //% shim=.followPathAsync promise
        public followPath(block?: Block, side?: TurnDirection): PathStop;

        /** Like agent.interact. */
        //%
        //% shim=.interactAsync promise
        public interact(direction: SixDirection): void;

        /** Like agent.setAssist. */
        //%
        //% shim=.setAssistAsync promise
        public setAssist(assist: AgentAssist, on: boolean): void;

        /** Like agent.setGravity. */
        //%
        //% shim=.setGravityAsync promise
        public setGravity(on: boolean): void;

        /** Like agent.detect. */
        //%
        //% shim=.detectAsync promise
        public detect(kind: AgentDetection, direction: SixDirection): boolean;

        /** Like agent.detectEntity. */
        //%
        //% shim=.detectEntityAsync promise
        public detectEntity(kind: EntityKind, direction: SixDirection): boolean;

        /** Like agent.countNearby. */
        //%
        //% shim=.countNearbyAsync promise
        public countNearby(kind: EntityKind, radius: number): number;

        /** Like agent.isOpen. */
        //%
        //% shim=.isOpenAsync promise
        public isOpen(direction: SixDirection): boolean;

        /** Like agent.redstonePower. */
        //%
        //% shim=.redstonePowerAsync promise
        public redstonePower(direction: SixDirection): number;

        /** Like agent.getPosition. */
        //%
        //% shim=.getPositionAsync promise
        public getPosition(): Position;

        /** Like agent.getOrientation. */
        //%
        //% shim=.getOrientationAsync promise
        public getOrientation(): number;

        /** Like agent.findNearest. */
        //%
        //% shim=.findNearestAsync promise
        public findNearest(block: Block, radius: number): Position;

        /** Like agent.findNearestCategory. */
        //%
        //% shim=.findNearestCategoryAsync promise
        public findNearestCategory(category: BlockCategory, radius: number): Position;

        /** Like agent.radar. */
        //%
        //% shim=.radarAsync promise
        public radar(kind: EntityKind, radius: number): Position[];

        /** Like agent.nearestEntity. */
        //%
        //% shim=.nearestEntityAsync promise
        public nearestEntity(kind: EntityKind, radius: number): Position;

        /** Like agent.detectLiquid. */
        //%
        //% shim=.detectLiquidAsync promise
        public detectLiquid(direction: SixDirection): Liquid;

        /** Like agent.isSafe. */
        //%
        //% shim=.isSafeAsync promise
        public isSafe(direction: SixDirection): boolean;

        /** Like agent.measure. */
        //%
        //% shim=.measureAsync promise
        public measure(direction: SixDirection, max: number): number;

        /** Like agent.measureRun. */
        //%
        //% shim=.measureRunAsync promise
        public measureRun(direction: SixDirection, along: SixDirection, max: number): number;

        /** Like agent.measureGap. */
        //%
        //% shim=.measureGapAsync promise
        public measureGap(axis: Axis, max: number): number;

        /** Like agent.groundHeight. */
        //%
        //% shim=.groundHeightAsync promise
        public groundHeight(position: Position): number;

        /** Like agent.heightProfile. */
        //%
        //% shim=.heightProfileAsync promise
        public heightProfile(direction: SixDirection, length: number): number[];

        /** Like agent.isAreaFree. */
        //%
        //% shim=.isAreaFreeAsync promise
        public isAreaFree(corner1: Position, corner2: Position): boolean;

        /** Like agent.countSolid. */
        //%
        //% shim=.countSolidAsync promise
        public countSolid(corner1: Position, corner2: Position): number;

        /** Like agent.findBounds. */
        //%
        //% shim=.findBoundsAsync promise
        public findBounds(max: number): Position[];

        /** Like agent.inspect. */
        //%
        //% shim=.inspectAsync promise
        public inspect(kind: AgentInspection, direction: SixDirection): number;

        /** Like agent.setSlot. */
        //%
        //% shim=.setSlotAsync promise
        public setSlot(slot: number): void;

        /** Like agent.getItemCount. */
        //%
        //% shim=.getItemCountAsync promise
        public getItemCount(slot: number): number;

        /** Like agent.getItemSpace. */
        //%
        //% shim=.getItemSpaceAsync promise
        public getItemSpace(slot: number): number;

        /** Like agent.freeSlots. */
        //%
        //% shim=.freeSlotsAsync promise
        public freeSlots(): number;

        /** Like agent.isInventoryFull. */
        //%
        //% shim=.isInventoryFullAsync promise
        public isInventoryFull(): boolean;

        /** Like agent.setItem. */
        //%
        //% shim=.setItemAsync promise
        public setItem(blockOrItem: number, count: number, slot: number): void;

        /** Like agent.drop. */
        //%
        //% shim=.dropAsync promise
        public drop(direction: SixDirection, slot: number, quantity: number): void;

        /** Like agent.dropAll. */
        //%
        //% shim=.dropAllAsync promise
        public dropAll(direction: SixDirection): void;

        /** Like agent.transfer. */
        //%
        //% shim=.transferAsync promise
        public transfer(quantity: number, sourceSlot: number, destinationSlot: number): void;

        /** Like agent.storeItems. */
        //%
        //% shim=.storeItemsAsync promise
        public storeItems(direction: SixDirection, slot: number): boolean;

        /** Like agent.takeItems. */
        //%
        //% shim=.takeItemsAsync promise
        public takeItems(direction: SixDirection, slot: number, count: number): boolean;

        /** Like agent.takeItem. */
        //%
        //% shim=.takeItemAsync promise
        public takeItem(direction: SixDirection, item: number, count: number): number;

        /** Like agent.containerCount. */
        //%
        //% shim=.containerCountAsync promise
        public containerCount(direction: SixDirection, item: number): number;

        /** Like agent.containerSize. */
        //%
        //% shim=.containerSizeAsync promise
        public containerSize(direction: SixDirection): number;

        /** Like agent.sortContainer. */
        //%
        //% shim=.sortContainerAsync promise
        public sortContainer(direction: SixDirection): void;

        /** Like agent.craft. */
        //%
        //% shim=.craftAsync promise
        public craft(item: number, count: number): number;

        /** Like agent.canCraft. */
        //%
        //% shim=.canCraftAsync promise
        public canCraft(item: number, count: number): boolean;

        /** Like agent.stonecut. */
        //%
        //% shim=.stonecutAsync promise
        public stonecut(item: number, count: number): number;

        /** Like agent.camera: your view through this robot. */
        //%
        //% shim=.cameraAsync promise
        public camera(mode: CameraMode): void;

    }
declare namespace player {
    /**
     * Show a message in your chat. Only you see it. Longer than 256 characters is cut, and it is plain text (no colours).
     * @param message what to show, eg: "Hi!"
     */
    //% blockId=player_say block="say %message"
    //% weight=100
    //% shim=player::sayAsync promise
    function say(message: string): void;

    /**
     * Where you are: the block your feet are in, as a world position.
     */
    //% blockId=player_position block="player world position"
    //% weight=90
    //% shim=player::positionAsync promise
    function position(): Position;

    /**
     * Which way you look, in degrees: south 0, west 90, north 180 (or -180), east -90.
     */
    //% blockId=player_get_orientation block="player orientation"
    //% weight=89
    //% shim=player::getOrientationAsync promise
    function getOrientation(): number;

    /**
     * Your player name.
     */
    //% blockId=player_name block="player name"
    //% weight=88
    //% shim=player::nameAsync promise
    function name(): string;

    /**
     * Teleport yourself to a position in the same dimension. Only in singleplayer or for an operator; otherwise the
     * game refuses it with a NO_PERMISSION error. Relative positions are worked out from where you stand now.
     * @param to where to go, eg: pos(0, 10, 0)
     */
    //% blockId=player_teleport block="teleport to %to=positions_pos"
    //% weight=80
    //% shim=player::teleportAsync promise
    function teleport(to: Position): void;

}
declare namespace robots {
    /**
     * One of your robots, by the name it got with /robot spawn <name>. It does what agent does. Give each robot its
     * own "run in parallel" so they work at the same time. The robot must exist in the game (see /robot list).
     * @param name the robot's name, eg: "Miner"
     */
    //% blockId=robots_get block="robot %name"
    //% weight=100
    //% shim=robots::get
    function get(name: string): Robot;

}
declare namespace blocks {
    /**
     * Fill the box between two corners with a block. Replace sets every block, Hollow sets the outside and clears the
     * inside, Outline sets only the outside, Keep fills only air (and grass, water), Destroy is the same as Replace.
     * Air clears the box. The log shows how many blocks changed.
     * @param block the block, eg: Block.Stone
     * @param from one corner
     * @param to the opposite corner
     * @param operator how to fill, eg: FillOperation.Replace
     */
    //% blockId=blocks_fill block="fill with %block|from %from=positions_pos|to %to=positions_pos|%operator"
    //% block.shadow=codeling_block weight=90
    //% shim=blocks::fillAsync promise
    function fill(block: number, from: Position, to: Position, operator: FillOperation): void;

    /**
     * Place one block at a position, replacing what is there.
     * @param block the block, eg: Block.Stone
     * @param position where to place it
     */
    //% blockId=blocks_place block="place %block|at %position=positions_pos"
    //% block.shadow=codeling_block weight=89
    //% shim=blocks::placeAsync promise
    function place(block: number, position: Position): void;

}
declare namespace shapes {
    /**
     * A straight line of blocks from one position to another, both ends included.
     * @param block the block, eg: Block.Stone
     * @param from where the line starts
     * @param to where the line ends
     */
    //% blockId=shapes_line block="line of %block|from %from=positions_pos|to %to=positions_pos"
    //% block.shadow=codeling_block weight=90
    //% shim=shapes::lineAsync promise
    function line(block: number, from: Position, to: Position): void;

    /**
     * A circle of blocks around a centre, flat across an axis (Y lies on the ground). Replace fills it, Outline sets
     * only the ring, Hollow sets the ring and clears the inside.
     * @param block the block, eg: Block.Stone
     * @param center the centre
     * @param radius 1 to 32, eg: 5
     * @param orientation the axis the circle goes around, eg: Axis.Y
     * @param operator how to fill, eg: ShapeOperation.Replace
     */
    //% blockId=shapes_circle block="circle of %block|center %center=positions_pos|radius %radius|around %orientation|%operator"
    //% block.shadow=codeling_block radius.min=1 radius.max=32 weight=89
    //% shim=shapes::circleAsync promise
    function circle(block: number, center: Position, radius: number, orientation: Axis, operator: ShapeOperation): void;

    /**
     * A ball of blocks around a centre. Replace fills it, Outline sets only the shell, Hollow sets the shell and
     * clears the inside. A radius above 15 is more than 32768 blocks and ends the program.
     * @param block the block, eg: Block.Stone
     * @param center the centre
     * @param radius 1 to 15, eg: 5
     * @param operator how to fill, eg: ShapeOperation.Replace
     */
    //% blockId=shapes_sphere block="sphere of %block|center %center=positions_pos|radius %radius|%operator"
    //% block.shadow=codeling_block radius.min=1 radius.max=15 weight=88
    //% shim=shapes::sphereAsync promise
    function sphere(block: number, center: Position, radius: number, operator: ShapeOperation): void;

    /**
     * A maze with exactly one way between any two places in it: walls 2 blocks high on a floor, with the entrance on
     * the north side and the exit on the south side. It goes east (width) and south (length) from the corner. The
     * corner is at foot height, like a fill's corner: the walls stand on that layer and the floor replaces the ground
     * one block below it, so pos(0, 0, 0) builds on the ground you stand on. An even width or length builds one block less. The same seed always builds the
     * same maze; without a seed it is a new one each time. Follow the floor block with agent follow path.
     * @param corner the north-west corner at foot height (the floor goes one block below)
     * @param width 5 to 32 blocks, eg: 15
     * @param length 5 to 32 blocks, eg: 15
     * @param wall the wall block, eg: Block.StoneBricks
     * @param floor the floor block, eg: Block.OakPlanks
     * @param seed the maze number, eg: 42
     */
    //% blockId=shapes_maze block="maze at %corner=positions_pos|width %width|length %length|walls of %wall|floor of %floor||seed %seed"
    //% wall.shadow=codeling_block floor.shadow=codeling_block width.min=5 width.max=32 length.min=5 length.max=32 weight=87
    //% shim=shapes::mazeAsync promise
    function maze(corner: Position, width: number, length: number, wall: number, floor: number, seed?: number): void;

}
declare namespace builder {
    /**
     * Put the builder at a position. It starts at your feet facing south; it is invisible and never touches blocks
     * when it moves.
     * @param position where to go
     */
    //% blockId=builder_teleport_to block="builder teleport to %position=positions_pos"
    //% weight=100
    //% shim=builder::teleportToAsync promise
    function teleportTo(position: Position): void;

    /**
     * Move the builder, relative to the way it faces.
     * @param direction which way, eg: SixDirection.Forward
     * @param steps how many blocks, eg: 1
     */
    //% blockId=builder_move block="builder move %direction|by %steps"
    //% weight=99
    //% shim=builder::moveAsync promise
    function move(direction: SixDirection, steps: number): void;

    /**
     * Turn the builder left or right.
     * @param direction which way, eg: TurnDirection.Left
     */
    //% blockId=builder_turn block="builder turn %direction"
    //% weight=98
    //% shim=builder::turn
    function turn(direction: TurnDirection): void;

    /**
     * Make the builder face a compass direction.
     * @param direction which way, eg: CompassDirection.North
     */
    //% blockId=builder_face block="builder face %direction"
    //% weight=97
    //% shim=builder::face
    function face(direction: CompassDirection): void;

    /**
     * Remember where the builder is. Line and fill go from the last mark to the builder.
     */
    //% blockId=builder_mark block="builder place mark"
    //% weight=96
    //% shim=builder::markAsync promise
    function mark(): void;

    /**
     * Where the builder is.
     */
    //% blockId=builder_position block="builder position"
    //% weight=95
    //% shim=builder::positionAsync promise
    function position(): Position;

    /**
     * Place a block where the builder is.
     * @param block the block, eg: Block.Stone
     */
    //% blockId=builder_place block="builder place %block"
    //% block.shadow=codeling_block weight=94
    //% shim=builder::placeAsync promise
    function place(block: number): void;

    /**
     * A line of blocks from the last mark to the builder. Without a mark it starts where the builder is.
     * @param block the block, eg: Block.Stone
     */
    //% blockId=builder_line block="builder line from mark with %block"
    //% block.shadow=codeling_block weight=93
    //% shim=builder::lineAsync promise
    function line(block: number): void;

    /**
     * Fill the box between the last mark and the builder. Without a mark it fills where the builder is.
     * @param block the block, eg: Block.Stone
     * @param operator how to fill, eg: FillOperation.Replace
     */
    //% blockId=builder_fill block="builder fill from mark with %block|%operator"
    //% block.shadow=codeling_block weight=92
    //% shim=builder::fillAsync promise
    function fill(block: number, operator: FillOperation): void;

}
declare namespace console {
    /**
     * Write a line in the simulator
     */
    //% blockId=console_say block="say %text"
    //% shim=console::log
    function log(text: string): void;

}
declare namespace control {
    /**
     * Run code at the same time as the rest of the program, for example to let a second robot work while the first
     * one does something else. Each robot does one thing at a time; give each robot its own run in parallel.
     * @param handler the code to run
     */
    //% blockId=control_run_in_parallel block="run in parallel"
    //% handlerStatement=1 blockNamespace=robots weight=80
    //% shim=control::runInParallel
    function runInParallel(handler: () => void): void;

}
declare namespace loops {
    /**
     * Pause this part of the program for some milliseconds (1000 ms is one second). Code in run in parallel and in
     * other forever loops keeps running.
     * @param ms how long to pause, in milliseconds, eg: 100
     */
    //% blockId=loops_pause block="pause (ms) %ms"
    //% weight=99
    //% shim=loops::pauseAsync promise
    function pause(ms: number): void;

    /**
     * Run code over and over again, at the same time as the rest of the program, until the program is stopped.
     * @param handler the code to repeat
     */
    //% blockId=loops_forever block="forever"
    //% afterOnStart=true blockAllowMultiple=1 weight=100
    //% shim=loops::forever
    function forever(handler: () => void): void;

}

// Auto-generated. Do not edit. Really.
