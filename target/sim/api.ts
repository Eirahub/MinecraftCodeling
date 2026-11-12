/// <reference path="../libs/core/enums.d.ts"/>
/// <reference path="../libs/core/blocks.d.ts"/>

namespace pxsim {
    // Position kinds, the same numbers as codeling.robot.Positions.
    const WORLD = 0, RELATIVE = 1, LOCAL = 2;

    /**
     * A block position: in the world, relative to you (~), or local to your facing (^).
     */
    //% blockNamespace=positions
    export class Position {
        constructor(public x: number, public y: number, public z: number, public kind = WORLD) {
        }

        /**
         * One coordinate of the position.
         * @param axis which coordinate, eg: Axis.X
         */
        //% blockId=position_get_value block="%position=agent_get_position|%axis value"
        //% blockNamespace=positions weight=80
        getValue(axis: Axis): number {
            return axis === Axis.Y ? this.y : axis === Axis.Z ? this.z : this.x;
        }

        /**
         * The same place in world coordinates. The game works out positions relative to you from where you stand and face now.
         */
        //% blockId=position_to_world block="%position=positions_pos|to world"
        //% blockNamespace=positions weight=79
        async toWorldAsync(): Promise<Position> {
            if (this.kind === WORLD) return this;
            const [x, y, z] = await agent.call("positions.toWorld", [this.kind, this.x, this.y, this.z]);
            return new Position(x, y, z);
        }

        /**
         * Add the coordinates of another position. The result is the same kind as this one.
         * @param other what to add
         */
        //% blockId=position_add block="%position=positions_pos|+ %other=positions_pos"
        //% blockNamespace=positions weight=78
        add(other: Position): Position {
            return new Position(this.x + other.x, this.y + other.y, this.z + other.z, this.kind);
        }

        /**
         * True if both positions are the same kind with the same coordinates.
         * @param other the position to compare with
         */
        //% blockId=position_equals block="%position=positions_pos|= %other=positions_pos"
        //% blockNamespace=positions weight=77
        equals(other: Position): boolean {
            return !!other && this.kind === other.kind && this.x === other.x && this.y === other.y && this.z === other.z;
        }

        /**
         * The position as text, written as in commands: "1 2 3", "~1 ~2 ~3" or "^1 ^2 ^3".
         */
        //% blockId=position_to_string block="%position=positions_pos|as text"
        //% blockNamespace=positions weight=76
        toString(): string {
            const p = this.kind === RELATIVE ? "~" : this.kind === LOCAL ? "^" : "";
            return `${p}${this.x} ${p}${this.y} ${p}${this.z}`;
        }

        /**
         * How far it is to another position in a straight line, in blocks, with decimals: from world 0 64 0 to world
         * 3 68 0 is 5. Two positions of different kinds are first turned into world positions, as to world does.
         * @param other the position to measure to
         */
        //% blockId=position_distance_to block="%position=positions_pos|distance to %other=positions_pos"
        //% blockNamespace=positions weight=75
        async distanceToAsync(other: Position): Promise<number> {
            // Same kind: the difference does not depend on where you stand or face, so no need to ask the game.
            const a = this.kind === other.kind ? this : await this.toWorldAsync();
            const b = this.kind === other.kind ? other : await other.toWorldAsync();
            const dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /**
         * Subtract the coordinates of another position: how far you go from the other one to this one. The result is
         * the same kind as this one, and like + it does not turn other kinds into world positions.
         * @param other what to subtract
         */
        //% blockId=position_subtract block="%position=positions_pos|- %other=positions_pos"
        //% blockNamespace=positions weight=74
        subtract(other: Position): Position {
            return new Position(this.x - other.x, this.y - other.y, this.z - other.z, this.kind);
        }

        /**
         * Multiply every coordinate by a number, rounded to whole blocks (half away from zero). The same kind as this one.
         * @param factor how many times, eg: 2
         */
        //% blockId=position_scale block="%position=positions_pos|times %factor"
        //% blockNamespace=positions weight=73
        scale(factor: number): Position {
            // The game takes whole block coordinates only; rounding the size keeps -p.scale(f) equal to p.scale(-f).
            const r = (v: number) => v * factor < 0 ? -Math.round(-v * factor) : Math.round(v * factor);
            return new Position(r(this.x), r(this.y), r(this.z), this.kind);
        }
    }
}

namespace pxsim.positions {
    /**
     * A position relative to your feet, with world axes (x east, y up, z south). The same as pos(x, y, z).
     */
    //% blockHidden=true
    export function create(x: number, y: number, z: number): Position {
        return new Position(x, y, z, 1);
    }

    /**
     * A position that turns with you: x to your left, y up, z forward. The same as posLocal(x, y, z).
     */
    //% blockHidden=true
    export function createLocal(x: number, y: number, z: number): Position {
        return new Position(x, y, z, 2);
    }

    /**
     * A fixed position in the world. The same as world(x, y, z).
     */
    //% blockHidden=true
    export function createWorld(x: number, y: number, z: number): Position {
        return new Position(x, y, z, 0);
    }

    /**
     * A random block position in the box between two corners, the same kind as the first corner.
     * @param from one corner
     * @param to the opposite corner
     */
    //% blockId=positions_random block="pick random position|from %from=positions_pos|to %to=positions_pos"
    //% weight=60
    export function random(from: Position, to: Position): Position {
        const r = (a: number, b: number) => Math.min(a, b) + Math.floor(Math.random() * (Math.abs(a - b) + 1));
        return new Position(r(from.x, to.x), r(from.y, to.y), r(from.z, to.z), from.kind);
    }

    // The distance of each position in the list from `from`, measured as distanceTo does. A list from the program is a
    // RefCollection in the simulator; no list (findBounds answers null) counts as empty.
    async function distances(list: Position[], from: Position): Promise<{ p: Position, d: number }[]> {
        const all: Position[] = list ? (list as any as RefCollection).toArray() : [];
        // Mixed kinds: turn `from` into a world position once instead of once for every position in the list.
        const f = all.some(p => p.kind !== from.kind) ? await from.toWorldAsync() : from;
        const res: { p: Position, d: number }[] = [];
        for (let i = 0; i < all.length; i++) res.push({ p: all[i], d: await f.distanceToAsync(all[i]) });
        return res;
    }

    /**
     * A new list with the same positions, nearest to a position first (straight line, as distance to measures it).
     * Positions just as far away keep their order. The list itself does not change; no list gives an empty list.
     * @param list the positions to sort
     * @param from where to measure from
     */
    //% blockId=positions_sort_by_distance block="sort %list=variables_get(list)|by distance from %from=positions_pos"
    //% weight=59
    export async function sortByDistanceAsync(list: Position[], from: Position): Promise<Position[]> {
        const sorted = (await distances(list, from)).sort((a, b) => a.d - b.d).map(e => e.p);
        return RefCollection.fromAny(sorted, false) as any;
    }

    /**
     * The position in the list nearest to a position (straight line, as distance to measures it), the first one if
     * two are just as near, or null when the list is empty.
     * @param list the positions to look through
     * @param from where to measure from
     */
    //% blockId=positions_nearest block="nearest in %list=variables_get(list)|to %from=positions_pos"
    //% weight=58
    export async function nearestAsync(list: Position[], from: Position): Promise<Position> {
        const all = await distances(list, from);
        let best = all[0];
        for (let i = 1; i < all.length; i++) if (all[i].d < best.d) best = all[i];
        return best ? best.p : null;
    }
}

namespace pxsim.agent {
    /**
     * Move the robot in a direction
     * @param direction which way to move, eg: SixDirection.Forward
     * @param steps how many blocks, eg: 1
     */
    //% blockId=agent_move block="agent move %direction|by %steps"
    //% steps.min=1 steps.max=64
    //% group="Movement" weight=100
    export async function moveAsync(direction: SixDirection, steps: number): Promise<void> {
        const b = board(), r = b.robot;
        const res = await b.relay.command("agent.move", [direction, steps], r);
        b.log(`${who(r)}move ${direction} ${steps}: ${res.value ? "done" : "blocked"}`);
    }

    /**
     * Turn the robot left or right
     * @param direction which way to turn, eg: TurnDirection.Left
     */
    //% blockId=agent_turn block="agent turn %direction"
    //% group="Movement" weight=99
    export async function turnAsync(direction: TurnDirection): Promise<void> {
        const b = board(), r = b.robot;
        await b.relay.command("agent.turn", [direction], r);
        b.log(`${who(r)}turn ${direction}: done`);
    }

    /**
     * Walk or fly to a position by itself, around walls and through wooden doors. With gravity the robot walks: it
     * climbs 1-block steps and falls down. When the position is a solid block (a log from find nearest), the robot
     * stops next to it and turns to face it if it can. True when it got there, false when there is no way (or none
     * within the search limit). Relative positions are worked out from where you stand now.
     * @param position where to go, eg: pos(0, 0, 5)
     */
    //% blockId=agent_move_to block="agent move to %position=positions_pos"
    //% group="Movement" weight=97
    export async function moveToAsync(position: Position): Promise<boolean> {
        if (!position) return false;
        const r = board().robot; // before the first await, see Robot.on
        const p = await position.toWorldAsync();
        return call("agent.moveTo", [p.x, p.y, p.z], r);
    }

    /**
     * Remember where the robot stands now as its home. The home is saved with the robot, also over a restart.
     */
    //% blockId=agent_set_home block="agent set home"
    //% group="Movement" weight=96
    export function setHomeAsync(): Promise<void> {
        return call("agent.setHome", []);
    }

    /**
     * Walk or fly home by itself, the same way as move to. True when it got there, false when there is no home, the
     * home is in another dimension, or there is no way (or none within the search limit).
     */
    //% blockId=agent_go_home block="agent go home"
    //% group="Movement" weight=95
    export function goHomeAsync(): Promise<boolean> {
        return call("agent.goHome", []);
    }

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
    export function followPathAsync(block?: Block, side?: TurnDirection): Promise<PathStop> {
        return call("agent.followPath", [block || 0, side || 0]);
    }

    // Every function below sends the op and args exactly as codeling.net.Protocol and RobotCommand.parse decode them.

    /**
     * Sends one command, logs the result and returns the server's value. Also used by Position.toWorld. The robot is
     * the board's current one when call starts; a function that awaits before its command passes the robot it read.
     */
    export async function call(op: string, args: any[], robot = board().robot): Promise<any> {
        const b = board();
        const line = `${who(robot)}${op.replace(/^agent\./, "")} ${args.join(" ")}: `;
        // A refused command (issue #218) goes to the log with its error, then on into the program.
        const res = await b.relay.command(op, args, robot).catch((e: any) => {
            b.log(line + e.text);
            throw e;
        });
        b.log(line + (res.value === undefined ? "done" : res.value));
        // Why the robot did less than asked (no blocks left, not on the path): the robot says it in the game too.
        if (res.message) b.log(`${who(robot)}${res.message}`);
        return res.value;
    }

    // "Miner: " before a named robot's lines in the log; the default robot's lines are as they always were.
    function who(robot: string): string {
        return robot ? `${robot}: ` : "";
    }

    /**
     * Place the block or item in the selected slot.
     * @param direction where to place, eg: SixDirection.Forward
     */
    //% blockId=agent_place block="agent place %direction"
    //% group="Building" weight=90
    // A statement as in MakeCode, so pasted `agent.place(...)` lines stay blocks; the server's true/false goes to the log.
    // shortcut: no way to read the result in a program, add a separate boolean block if players ask for one.
    export async function placeAsync(direction: SixDirection): Promise<void> {
        await call("agent.place", [direction]);
    }

    /**
     * Break the block next to the robot; the drops go into its inventory.
     * @param direction which block to break, eg: SixDirection.Forward
     */
    //% blockId=agent_destroy block="agent destroy %direction"
    //% group="Building" weight=89
    export async function destroyAsync(direction: SixDirection): Promise<void> {
        await call("agent.destroy", [direction]);
    }

    /**
     * Use the block next to the robot like a right-click with an empty hand: open or close a wooden door, trapdoor or
     * fence gate, press a button, flip a lever. Iron doors need redstone. Other blocks are left alone.
     * @param direction which block to use, eg: SixDirection.Forward
     */
    //% blockId=agent_interact block="agent interact %direction"
    //% group="Building" weight=87
    // A statement as in MakeCode; the server's true/false (did the block react) goes to the log, agent.isOpen reads doors.
    export async function interactAsync(direction: SixDirection): Promise<void> {
        await call("agent.interact", [direction]);
    }

    /**
     * Turn a robot assist on or off. Place on move puts the selected block in each spot the robot leaves.
     * @param assist which assist, eg: AgentAssist.PlaceOnMove
     * @param on true to turn it on, eg: true
     */
    //% blockId=agent_set_assist block="agent assist %assist|%on"
    //% group="Building" weight=88
    export function setAssistAsync(assist: AgentAssist, on: boolean): Promise<void> {
        return call("agent.setAssist", [assist, on]);
    }

    /**
     * Turn gravity on or off. With gravity the robot falls after a sideways step and climbs 1-block bumps.
     * @param on true to turn it on, eg: true
     */
    //% blockId=agent_set_gravity block="agent gravity %on"
    //% group="Movement" weight=98
    export function setGravityAsync(on: boolean): Promise<void> {
        return call("agent.setGravity", [on]);
    }

    /**
     * Check what is next to the robot. Block: true if the block would stop a move.
     * @param kind what to look for, eg: AgentDetection.Block
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_detect block="agent detect %kind|%direction"
    //% group="Inspecting" weight=80
    export function detectAsync(kind: AgentDetection, direction: SixDirection): Promise<boolean> {
        return call("agent.detect", [kind, direction]);
    }

    /**
     * True if a hostile mob, an animal, a player or a dropped item is in the block next to the robot.
     * @param kind what to look for, eg: EntityKind.Mob
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_detect_entity block="agent detect %kind|%direction"
    //% group="Inspecting" weight=80
    export function detectEntityAsync(kind: EntityKind, direction: SixDirection): Promise<boolean> {
        return call("agent.detectEntity", [kind, direction]);
    }

    /**
     * How many hostile mobs, animals, players or dropped items are in the cube of that radius around the robot.
     * @param kind what to count, eg: EntityKind.Animal
     * @param radius how far to look, 1 to 8, eg: 4
     */
    //% blockId=agent_count_nearby block="agent count nearby %kind|within %radius"
    //% radius.min=1 radius.max=8
    //% group="Inspecting" weight=79
    export function countNearbyAsync(kind: EntityKind, radius: number): Promise<number> {
        return call("agent.countNearby", [kind, radius]);
    }

    /**
     * True if the door, trapdoor or fence gate next to the robot is open. False for blocks that cannot open.
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_is_open block="agent is open %direction"
    //% group="Inspecting" weight=79
    export function isOpenAsync(direction: SixDirection): Promise<boolean> {
        return call("agent.isOpen", [direction]);
    }

    /**
     * Redstone signal strength of the block next to the robot, 0 to 15, as F3 shows it: what redstone dust, a lever or
     * a redstone block gives out, or how strongly another block is powered.
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_redstone_power block="agent redstone power %direction"
    //% group="Inspecting" weight=79
    export function redstonePowerAsync(direction: SixDirection): Promise<number> {
        return call("agent.redstonePower", [direction]);
    }

    /**
     * Run code when your robot gets a redstone signal: when the block it stands in goes from no signal to a signal,
     * for example from a lever, a button or a redstone block next to it. Only your default robot, while it is loaded.
     * @param handler the code to run
     */
    //% blockId=agent_on_redstone_powered block="on agent redstone powered"
    //% group="Inspecting" weight=78
    export function onRedstonePowered(handler: () => void): void {
        board().onRedstonePowered(handler as any as RefAction);
    }

    /**
     * Where the robot is: its block position in the world.
     */
    //% blockId=agent_get_position block="agent position"
    //% group="Inspecting" weight=78
    export async function getPositionAsync(): Promise<Position> {
        const [x, y, z] = await call("agent.getPosition", []);
        return new Position(x, y, z);
    }

    /**
     * Which way the robot faces, in degrees: south 0, west 90, north 180, east -90.
     */
    //% blockId=agent_get_orientation block="agent orientation"
    //% group="Inspecting" weight=77
    export function getOrientationAsync(): Promise<number> {
        return call("agent.getOrientation", []);
    }

    /**
     * Where the nearest block of this kind is, counted in block steps from the robot, or null if there is none
     * within the radius (in loaded chunks).
     * @param block the block to look for, eg: Block.OakLog
     * @param radius how far to look, 1 to 32, eg: 16
     */
    //% blockId=agent_find_nearest block="agent find nearest %block|within %radius"
    //% radius.min=1 radius.max=32
    //% group="Inspecting" weight=76
    export async function findNearestAsync(block: Block, radius: number): Promise<Position> {
        return found(await call("agent.findNearest", [block, radius]));
    }

    /**
     * Where the nearest block of a category (all logs, all leaves, all ores, ...) is, or null if there is none
     * within the radius (in loaded chunks).
     * @param category what to look for, eg: BlockCategory.Logs
     * @param radius how far to look, 1 to 32, eg: 16
     */
    //% blockId=agent_find_nearest_category block="agent find nearest %category|within %radius"
    //% radius.min=1 radius.max=32
    //% group="Inspecting" weight=75
    export async function findNearestCategoryAsync(category: BlockCategory, radius: number): Promise<Position> {
        return found(await call("agent.findNearestCategory", [category, radius]));
    }

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
    export async function radarAsync(kind: EntityKind, radius: number): Promise<Position[]> {
        const v: number[] = await call("agent.radar", [kind, radius]);
        const found: Position[] = [];
        for (let i = 0; i + 2 < v.length; i += 3) found.push(new Position(v[i], v[i + 1], v[i + 2]));
        // Not deep: a deep copy turns each Position into a plain record without its methods (toString, toWorld).
        return RefCollection.fromAny(found, false) as any;
    }

    /**
     * Where the nearest hostile mob, animal, player or dropped item in the cube of that radius around the robot is,
     * or null if there is none. The same as the first position from radar.
     * @param kind what to look for, eg: EntityKind.Player
     * @param radius how far to look, 1 to 16, eg: 8
     */
    //% blockId=agent_nearest_entity block="agent nearest %kind|within %radius"
    //% radius.min=1 radius.max=16
    //% group="Sensors" weight=61
    export async function nearestEntityAsync(kind: EntityKind, radius: number): Promise<Position> {
        const v: number[] = await call("agent.radar", [kind, radius]);
        return v.length ? new Position(v[0], v[1], v[2]) : null;
    }

    /**
     * Which liquid is in the block next to the robot: water (also a waterlogged block), lava or none.
     * @param direction where to look, eg: SixDirection.Down
     */
    //% blockId=agent_detect_liquid block="agent detect liquid %direction"
    //% group="Sensors" weight=60
    export function detectLiquidAsync(direction: SixDirection): Promise<Liquid> {
        return call("agent.detectLiquid", [direction]);
    }

    /**
     * False if the block next to the robot is dangerous: lava in it or under it, a drop of more than 3 blocks under
     * it, or a hostile mob in it or next to it. A wall is safe: the robot cannot go there.
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_is_safe block="agent is safe %direction"
    //% group="Sensors" weight=59
    export function isSafeAsync(direction: SixDirection): Promise<boolean> {
        return call("agent.isSafe", [direction]);
    }

    // World queries (issue #87): the robot's own block, nothing further away.

    /**
     * How light it is where the robot stands, 0 (dark) to 15 (full daylight or right next to a torch). Sky light is
     * lower at night and in rain. Hostile mobs can spawn on blocks with light 0.
     */
    //% blockId=agent_light_level block="agent light level"
    //% group="Sensors" weight=57
    export function lightLevelAsync(): Promise<number> {
        return call("agent.lightLevel", []);
    }

    /**
     * The biome where the robot stands, as its short id: "plains", "desert", "dark_forest". Biomes from other mods
     * keep their mod name: "mymod:crystal_cave".
     */
    //% blockId=agent_biome block="agent biome"
    //% group="Sensors" weight=56
    export function biomeAsync(): Promise<string> {
        return call("agent.biome", []);
    }

    /**
     * The temperature of the biome where the robot stands. Biomes below 0.15 get snow instead of rain (snowy plains 0),
     * plains 0.8, desert 2.
     */
    //% blockId=agent_temperature block="agent temperature"
    //% group="Sensors" weight=55
    export function temperatureAsync(): Promise<number> {
        return call("agent.temperature", []);
    }

    // The server answers [x, y, z], or no value when nothing was found.
    function found(value: number[]): Position {
        return value ? new Position(value[0], value[1], value[2]) : null;
    }

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
    export function measureAsync(direction: SixDirection, max: number): Promise<number> {
        return call("agent.measure", [direction, max]);
    }

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
    export function measureRunAsync(direction: SixDirection, along: SixDirection, max: number): Promise<number> {
        return call("agent.measureRun", [direction, along, max]);
    }

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
    export function measureGapAsync(axis: Axis, max: number): Promise<number> {
        return call("agent.measureGap", [axis, max]);
    }

    /**
     * The y of the highest solid block at a position's x and z, with leaves ignored (the game's own height map, so
     * water counts as ground). The position's own y is not used.
     * @param position where to look, eg: pos(0, 0, 5)
     */
    //% blockId=agent_ground_height block="agent ground height at %position=positions_pos"
    //% group="Measuring" weight=71
    export async function groundHeightAsync(position: Position): Promise<number> {
        const r = board().robot;
        const p = await position.toWorldAsync();
        return call("agent.groundHeight", [p.x, p.z], r);
    }

    /**
     * The ground heights along a line: the first one block from the robot in direction, then one per block. Each is
     * what ground height answers there. Shows the shape of a valley or a hill.
     * @param direction which way: forward, back, left or right, eg: SixDirection.Forward
     * @param length how many blocks, 1 to 32, eg: 16
     */
    //% blockId=agent_height_profile block="agent height profile %direction|length %length"
    //% length.min=1 length.max=32
    //% group="Measuring" weight=70
    export async function heightProfileAsync(direction: SixDirection, length: number): Promise<number[]> {
        // The program's arrays are RefCollections in the simulator.
        return RefCollection.fromAny(await call("agent.heightProfile", [direction, length])) as any;
    }

    /**
     * True if every block in the box between two corners is air or something a block can replace (grass, a snow
     * layer, water). At most 32 blocks on each side.
     * @param corner1 one corner
     * @param corner2 the opposite corner
     */
    //% blockId=agent_is_area_free block="agent is area free|from %corner1=positions_pos|to %corner2=positions_pos"
    //% group="Measuring" weight=69
    export async function isAreaFreeAsync(corner1: Position, corner2: Position): Promise<boolean> {
        const r = board().robot;
        return call("agent.isAreaFree", await corners(corner1, corner2), r);
    }

    /**
     * How many blocks in the box between two corners are not air or replaceable, so a program can decide whether to
     * clear it first. At most 32 blocks on each side.
     * @param corner1 one corner
     * @param corner2 the opposite corner
     */
    //% blockId=agent_count_solid block="agent count solid blocks|from %corner1=positions_pos|to %corner2=positions_pos"
    //% group="Measuring" weight=68
    export async function countSolidAsync(corner1: Position, corner2: Position): Promise<number> {
        const r = board().robot;
        return call("agent.countSolid", await corners(corner1, corner2), r);
    }

    // Both corners in world coordinates, as the server takes a box: [x1, y1, z1, x2, y2, z2].
    async function corners(a: Position, b: Position): Promise<number[]> {
        const p = await a.toWorldAsync();
        const q = await b.toWorldAsync();
        return [p.x, p.y, p.z, q.x, q.y, q.z];
    }

    /**
     * The outer edges of the building in front of the robot: the lowest and the highest corner of the solid blocks
     * connected to the block in front (touching sides), at most max blocks on each side. A list of two world
     * positions, or null when there is no block in front. Blocks on the ground connect to the ground.
     * @param max the largest size on each side, 1 to 32, eg: 32
     */
    //% blockId=agent_find_bounds block="agent find bounds forward|up to %max"
    //% max.min=1 max.max=32
    //% group="Measuring" weight=67
    export async function findBoundsAsync(max: number): Promise<Position[]> {
        const v: number[] = await call("agent.findBounds", [max]);
        return v ? RefCollection.fromAny([new Position(v[0], v[1], v[2]), new Position(v[3], v[4], v[5])], false) as any : null;
    }

    /**
     * Read the block next to the robot. Block: its id (-1 if unknown). Data: its data value.
     * @param kind what to read, eg: AgentInspection.Block
     * @param direction where to look, eg: SixDirection.Forward
     */
    //% blockId=agent_inspect block="agent inspect %kind|%direction"
    //% group="Inspecting" weight=79
    export function inspectAsync(kind: AgentInspection, direction: SixDirection): Promise<number> {
        return call("agent.inspect", [kind, direction]);
    }

    /**
     * Choose the slot that place and place on move use.
     * @param slot slot 1 to 27, eg: 1
     */
    //% blockId=agent_set_slot block="agent set active slot %slot"
    //% slot.min=1 slot.max=27
    //% group="Inventory" weight=70
    export function setSlotAsync(slot: number): Promise<void> {
        return call("agent.setSlot", [slot]);
    }

    /**
     * Number of items in a slot.
     * @param slot slot 1 to 27, eg: 1
     */
    //% blockId=agent_get_item_count block="agent item count in slot %slot"
    //% slot.min=1 slot.max=27
    //% group="Inventory" weight=69
    export function getItemCountAsync(slot: number): Promise<number> {
        return call("agent.getItemCount", [slot]);
    }

    /**
     * How many more items fit in a slot.
     * @param slot slot 1 to 27, eg: 1
     */
    //% blockId=agent_get_item_space block="agent space left in slot %slot"
    //% slot.min=1 slot.max=27
    //% group="Inventory" weight=68
    export function getItemSpaceAsync(slot: number): Promise<number> {
        return call("agent.getItemSpace", [slot]);
    }

    /**
     * How many of the robot's 27 slots are empty.
     */
    //% blockId=agent_free_slots block="agent free slots"
    //% group="Inventory" weight=68
    export function freeSlotsAsync(): Promise<number> {
        return call("agent.freeSlots", []);
    }

    /**
     * True if no slot is empty. A slot that is not a full stack can still take more of the same item.
     */
    //% blockId=agent_is_inventory_full block="agent is inventory full"
    //% group="Inventory" weight=68
    export function isInventoryFullAsync(): Promise<boolean> {
        return call("agent.isInventoryFull", []);
    }

    /**
     * Put blocks or items in a slot, replacing what is there (creative mode or the gamerule codeling:free_items only). Air empties it.
     * @param blockOrItem the item or block, eg: Item.Stone
     * @param count how many, eg: 64
     * @param slot slot 1 to 27, eg: 1
     */
    //% blockId=agent_set_item block="agent give %blockOrItem|count %count|in slot %slot"
    //% blockOrItem.shadow=codeling_item count.min=1 count.max=64 slot.min=1 slot.max=27
    //% group="Inventory" weight=67
    export function setItemAsync(blockOrItem: number, count: number, slot: number): Promise<void> {
        return call("agent.setItem", [blockOrItem, count, slot]);
    }

    /**
     * Pick up the items lying within one block of the robot.
     */
    //% blockId=agent_collect_all block="agent collect all"
    //% group="Inventory" weight=66
    export function collectAllAsync(): Promise<void> {
        return call("agent.collectAll", []);
    }

    /**
     * Drop items from a slot.
     * @param direction where to drop, eg: SixDirection.Forward
     * @param slot slot 1 to 27, eg: 1
     * @param quantity how many, eg: 1
     */
    //% blockId=agent_drop block="agent drop %direction|from slot %slot|amount %quantity"
    //% slot.min=1 slot.max=27 quantity.min=1 quantity.max=64
    //% group="Inventory" weight=65
    export async function dropAsync(direction: SixDirection, slot: number, quantity: number): Promise<void> {
        await call("agent.drop", [direction, slot, quantity]);
    }

    /**
     * Drop everything the robot carries.
     * @param direction where to drop, eg: SixDirection.Forward
     */
    //% blockId=agent_drop_all block="agent drop all %direction"
    //% group="Inventory" weight=64
    export function dropAllAsync(direction: SixDirection): Promise<void> {
        return call("agent.dropAll", [direction]);
    }

    /**
     * Move items from one slot to another.
     * @param quantity how many, eg: 1
     * @param sourceSlot from slot, eg: 1
     * @param destinationSlot to slot, eg: 2
     */
    //% blockId=agent_transfer block="agent transfer amount %quantity|from slot %sourceSlot|to slot %destinationSlot"
    //% quantity.min=1 quantity.max=64 sourceSlot.min=1 sourceSlot.max=27 destinationSlot.min=1 destinationSlot.max=27
    //% group="Inventory" weight=63
    export function transferAsync(quantity: number, sourceSlot: number, destinationSlot: number): Promise<void> {
        return call("agent.transfer", [quantity, sourceSlot, destinationSlot]);
    }

    /**
     * Put items from the robot into the chest, barrel, hopper or furnace next to it, as a hopper would: a furnace takes
     * what to smelt from above and fuel from the side. True if anything moved; what does not fit stays in the robot.
     * @param direction where the container is, eg: SixDirection.Forward
     * @param slot robot slot 1 to 27, or 0 for all slots, eg: 0
     */
    //% blockId=agent_store_items block="agent store items %direction|from slot %slot"
    //% slot.min=0 slot.max=27
    //% group="Containers" weight=60
    export function storeItemsAsync(direction: SixDirection, slot: number): Promise<boolean> {
        return call("agent.storeItems", [direction, slot]);
    }

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
    export function takeItemsAsync(direction: SixDirection, slot: number, count: number): Promise<boolean> {
        return call("agent.takeItems", [direction, slot, count]);
    }

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
    export function takeItemAsync(direction: SixDirection, item: number, count: number): Promise<number> {
        return call("agent.takeItem", [direction, item, count]);
    }

    /**
     * How many of an item the container next to the robot holds (0 if there is none or it is protected).
     * @param direction where the container is, eg: SixDirection.Forward
     * @param item what to count, eg: Item.Cobblestone
     */
    //% blockId=agent_container_count block="agent count in container %direction|of %item"
    //% item.shadow=codeling_item
    //% group="Containers" weight=57
    export function containerCountAsync(direction: SixDirection, item: number): Promise<number> {
        return call("agent.containerCount", [direction, item]);
    }

    /**
     * Number of slots in the container next to the robot: 27 for a chest, 54 for a double chest, 0 if there is none.
     * @param direction where the container is, eg: SixDirection.Forward
     */
    //% blockId=agent_container_size block="agent container size %direction"
    //% group="Containers" weight=56
    export function containerSizeAsync(direction: SixDirection): Promise<number> {
        return call("agent.containerSize", [direction]);
    }

    /**
     * Sort the chest, barrel, shulker box or hopper next to the robot: equal items are stacked together and ordered by
     * name. Furnaces are left alone.
     * @param direction where the container is, eg: SixDirection.Forward
     */
    //% blockId=agent_sort_container block="agent sort container %direction"
    //% group="Containers" weight=55
    export async function sortContainerAsync(direction: SixDirection): Promise<void> {
        await call("agent.sortContainer", [direction]);
    }

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
    export function craftAsync(item: number, count: number): Promise<number> {
        return call("agent.craft", [item, count]);
    }

    /**
     * True if the robot can craft at least count of an item right now, with what it carries and where it stands.
     * @param item what to make, eg: Item.Stick
     * @param count how many at least, eg: 4
     */
    //% blockId=agent_can_craft block="agent can craft %item|amount %count"
    //% item.shadow=codeling_item count.min=1 count.max=64
    //% group="Crafting" weight=53
    export function canCraftAsync(item: number, count: number): Promise<boolean> {
        return call("agent.canCraft", [item, count]);
    }

    /**
     * Cut stone with the stonecutter next to the robot, from the robot's own inventory. Answers how many it made.
     * @param item what to make, eg: Item.StoneBricks
     * @param count how many at least, eg: 4
     */
    //% blockId=agent_stonecut block="agent stonecut %item|amount %count"
    //% item.shadow=codeling_item count.min=1 count.max=64
    //% group="Crafting" weight=52
    export function stonecutAsync(item: number, count: number): Promise<number> {
        return call("agent.stonecut", [item, count]);
    }

    /**
     * Show a short text over the robot for a few seconds. Text longer than 80 characters is cut.
     * @param text what to say, eg: "Hello!"
     */
    //% blockId=agent_say block="agent say %text"
    //% group="Feedback" weight=50
    export function sayAsync(text: string): Promise<void> {
        return call("agent.say", ["" + text]);
    }

    /**
     * Show your screen through the robot's eyes, from behind the robot, or your own view again. The server decides
     * who may use the robot camera; if it is not allowed, the program stops with an error.
     * @param mode which view, eg: CameraMode.Follow
     */
    //% blockId=agent_camera block="agent camera %mode"
    //% group="Feedback" weight=49
    export function cameraAsync(mode: CameraMode): Promise<void> {
        return call("agent.camera", [mode]);
    }
}

// Several robots (issue #141): robots.get("Miner") is one of your robots with the same calls as agent, which is your
// default robot. Every call goes through the agent function with the board's current robot set for that moment.

namespace pxsim {
    /**
     * One of your robots, by name. It has the same calls as agent; agent is your default robot.
     */
    //% blockNamespace=robots
    export class Robot {
        constructor(public name: string) {
        }

        // Runs an agent function for this robot. Commands read board().robot before their first await, and the
        // simulator only switches between parallel code at an await, so a robot in another runInParallel never mixes in.
        private on<T>(f: () => T): T {
            const b = board();
            b.robot = this.name;
            try {
                return f();
            } finally {
                b.robot = "";
            }
        }

        /**
         * Move the robot in a direction
         * @param direction which way to move, eg: SixDirection.Forward
         * @param steps how many blocks, eg: 1
         */
        //% blockId=robot_move block="%robot=robots_get|move %direction|by %steps"
        //% steps.min=1 steps.max=64
        //% blockNamespace=robots weight=90
        moveAsync(direction: SixDirection, steps: number): Promise<void> {
            return this.on(() => agent.moveAsync(direction, steps));
        }

        /**
         * Turn the robot left or right
         * @param direction which way to turn, eg: TurnDirection.Left
         */
        //% blockId=robot_turn block="%robot=robots_get|turn %direction"
        //% blockNamespace=robots weight=89
        turnAsync(direction: TurnDirection): Promise<void> {
            return this.on(() => agent.turnAsync(direction));
        }

        /**
         * Walk or fly to a position by itself, like agent move to. True when it got there.
         * @param position where to go, eg: pos(0, 0, 5)
         */
        //% blockId=robot_move_to block="%robot=robots_get|move to %position=positions_pos"
        //% blockNamespace=robots weight=88
        moveToAsync(position: Position): Promise<boolean> {
            return this.on(() => agent.moveToAsync(position));
        }

        /**
         * Place the block or item in the selected slot.
         * @param direction where to place, eg: SixDirection.Forward
         */
        //% blockId=robot_place block="%robot=robots_get|place %direction"
        //% blockNamespace=robots weight=87
        placeAsync(direction: SixDirection): Promise<void> {
            return this.on(() => agent.placeAsync(direction));
        }

        /**
         * Break the block next to the robot; the drops go into its inventory.
         * @param direction which block to break, eg: SixDirection.Forward
         */
        //% blockId=robot_destroy block="%robot=robots_get|destroy %direction"
        //% blockNamespace=robots weight=86
        destroyAsync(direction: SixDirection): Promise<void> {
            return this.on(() => agent.destroyAsync(direction));
        }

        /**
         * Pick up the items lying within one block of the robot.
         */
        //% blockId=robot_collect_all block="%robot=robots_get|collect all"
        //% blockNamespace=robots weight=85
        collectAllAsync(): Promise<void> {
            return this.on(() => agent.collectAllAsync());
        }

        /**
         * Show a short text over the robot for a few seconds.
         * @param text what to say, eg: "Hello!"
         */
        //% blockId=robot_say block="%robot=robots_get|say %text"
        //% blockNamespace=robots weight=84
        sayAsync(text: string): Promise<void> {
            return this.on(() => agent.sayAsync(text));
        }

        // The other agent calls, in JavaScript and Python only (no blocks: the agent blocks already show them all).

        /** Like agent.setHome. */
        //%
        setHomeAsync(): Promise<void> {
            return this.on(() => agent.setHomeAsync());
        }

        /** Like agent.goHome. */
        //%
        goHomeAsync(): Promise<boolean> {
            return this.on(() => agent.goHomeAsync());
        }

        /** Like agent.followPath. */
        //%
        followPathAsync(block?: Block, side?: TurnDirection): Promise<PathStop> {
            return this.on(() => agent.followPathAsync(block, side));
        }

        /** Like agent.interact. */
        //%
        interactAsync(direction: SixDirection): Promise<void> {
            return this.on(() => agent.interactAsync(direction));
        }

        /** Like agent.setAssist. */
        //%
        setAssistAsync(assist: AgentAssist, on: boolean): Promise<void> {
            return this.on(() => agent.setAssistAsync(assist, on));
        }

        /** Like agent.setGravity. */
        //%
        setGravityAsync(on: boolean): Promise<void> {
            return this.on(() => agent.setGravityAsync(on));
        }

        /** Like agent.detect. */
        //%
        detectAsync(kind: AgentDetection, direction: SixDirection): Promise<boolean> {
            return this.on(() => agent.detectAsync(kind, direction));
        }

        /** Like agent.detectEntity. */
        //%
        detectEntityAsync(kind: EntityKind, direction: SixDirection): Promise<boolean> {
            return this.on(() => agent.detectEntityAsync(kind, direction));
        }

        /** Like agent.countNearby. */
        //%
        countNearbyAsync(kind: EntityKind, radius: number): Promise<number> {
            return this.on(() => agent.countNearbyAsync(kind, radius));
        }

        /** Like agent.isOpen. */
        //%
        isOpenAsync(direction: SixDirection): Promise<boolean> {
            return this.on(() => agent.isOpenAsync(direction));
        }

        /** Like agent.redstonePower. */
        //%
        redstonePowerAsync(direction: SixDirection): Promise<number> {
            return this.on(() => agent.redstonePowerAsync(direction));
        }

        /** Like agent.getPosition. */
        //%
        getPositionAsync(): Promise<Position> {
            return this.on(() => agent.getPositionAsync());
        }

        /** Like agent.getOrientation. */
        //%
        getOrientationAsync(): Promise<number> {
            return this.on(() => agent.getOrientationAsync());
        }

        /** Like agent.findNearest. */
        //%
        findNearestAsync(block: Block, radius: number): Promise<Position> {
            return this.on(() => agent.findNearestAsync(block, radius));
        }

        /** Like agent.findNearestCategory. */
        //%
        findNearestCategoryAsync(category: BlockCategory, radius: number): Promise<Position> {
            return this.on(() => agent.findNearestCategoryAsync(category, radius));
        }

        /** Like agent.radar. */
        //%
        radarAsync(kind: EntityKind, radius: number): Promise<Position[]> {
            return this.on(() => agent.radarAsync(kind, radius));
        }

        /** Like agent.nearestEntity. */
        //%
        nearestEntityAsync(kind: EntityKind, radius: number): Promise<Position> {
            return this.on(() => agent.nearestEntityAsync(kind, radius));
        }

        /** Like agent.detectLiquid. */
        //%
        detectLiquidAsync(direction: SixDirection): Promise<Liquid> {
            return this.on(() => agent.detectLiquidAsync(direction));
        }

        /** Like agent.isSafe. */
        //%
        isSafeAsync(direction: SixDirection): Promise<boolean> {
            return this.on(() => agent.isSafeAsync(direction));
        }

        /** Like agent.lightLevel. */
        //%
        lightLevelAsync(): Promise<number> {
            return this.on(() => agent.lightLevelAsync());
        }

        /** Like agent.biome. */
        //%
        biomeAsync(): Promise<string> {
            return this.on(() => agent.biomeAsync());
        }

        /** Like agent.temperature. */
        //%
        temperatureAsync(): Promise<number> {
            return this.on(() => agent.temperatureAsync());
        }

        /** Like agent.measure. */
        //%
        measureAsync(direction: SixDirection, max: number): Promise<number> {
            return this.on(() => agent.measureAsync(direction, max));
        }

        /** Like agent.measureRun. */
        //%
        measureRunAsync(direction: SixDirection, along: SixDirection, max: number): Promise<number> {
            return this.on(() => agent.measureRunAsync(direction, along, max));
        }

        /** Like agent.measureGap. */
        //%
        measureGapAsync(axis: Axis, max: number): Promise<number> {
            return this.on(() => agent.measureGapAsync(axis, max));
        }

        /** Like agent.groundHeight. */
        //%
        groundHeightAsync(position: Position): Promise<number> {
            return this.on(() => agent.groundHeightAsync(position));
        }

        /** Like agent.heightProfile. */
        //%
        heightProfileAsync(direction: SixDirection, length: number): Promise<number[]> {
            return this.on(() => agent.heightProfileAsync(direction, length));
        }

        /** Like agent.isAreaFree. */
        //%
        isAreaFreeAsync(corner1: Position, corner2: Position): Promise<boolean> {
            return this.on(() => agent.isAreaFreeAsync(corner1, corner2));
        }

        /** Like agent.countSolid. */
        //%
        countSolidAsync(corner1: Position, corner2: Position): Promise<number> {
            return this.on(() => agent.countSolidAsync(corner1, corner2));
        }

        /** Like agent.findBounds. */
        //%
        findBoundsAsync(max: number): Promise<Position[]> {
            return this.on(() => agent.findBoundsAsync(max));
        }

        /** Like agent.inspect. */
        //%
        inspectAsync(kind: AgentInspection, direction: SixDirection): Promise<number> {
            return this.on(() => agent.inspectAsync(kind, direction));
        }

        /** Like agent.setSlot. */
        //%
        setSlotAsync(slot: number): Promise<void> {
            return this.on(() => agent.setSlotAsync(slot));
        }

        /** Like agent.getItemCount. */
        //%
        getItemCountAsync(slot: number): Promise<number> {
            return this.on(() => agent.getItemCountAsync(slot));
        }

        /** Like agent.getItemSpace. */
        //%
        getItemSpaceAsync(slot: number): Promise<number> {
            return this.on(() => agent.getItemSpaceAsync(slot));
        }

        /** Like agent.freeSlots. */
        //%
        freeSlotsAsync(): Promise<number> {
            return this.on(() => agent.freeSlotsAsync());
        }

        /** Like agent.isInventoryFull. */
        //%
        isInventoryFullAsync(): Promise<boolean> {
            return this.on(() => agent.isInventoryFullAsync());
        }

        /** Like agent.setItem. */
        //%
        setItemAsync(blockOrItem: number, count: number, slot: number): Promise<void> {
            return this.on(() => agent.setItemAsync(blockOrItem, count, slot));
        }

        /** Like agent.drop. */
        //%
        dropAsync(direction: SixDirection, slot: number, quantity: number): Promise<void> {
            return this.on(() => agent.dropAsync(direction, slot, quantity));
        }

        /** Like agent.dropAll. */
        //%
        dropAllAsync(direction: SixDirection): Promise<void> {
            return this.on(() => agent.dropAllAsync(direction));
        }

        /** Like agent.transfer. */
        //%
        transferAsync(quantity: number, sourceSlot: number, destinationSlot: number): Promise<void> {
            return this.on(() => agent.transferAsync(quantity, sourceSlot, destinationSlot));
        }

        /** Like agent.storeItems. */
        //%
        storeItemsAsync(direction: SixDirection, slot: number): Promise<boolean> {
            return this.on(() => agent.storeItemsAsync(direction, slot));
        }

        /** Like agent.takeItems. */
        //%
        takeItemsAsync(direction: SixDirection, slot: number, count: number): Promise<boolean> {
            return this.on(() => agent.takeItemsAsync(direction, slot, count));
        }

        /** Like agent.takeItem. */
        //%
        takeItemAsync(direction: SixDirection, item: number, count: number): Promise<number> {
            return this.on(() => agent.takeItemAsync(direction, item, count));
        }

        /** Like agent.containerCount. */
        //%
        containerCountAsync(direction: SixDirection, item: number): Promise<number> {
            return this.on(() => agent.containerCountAsync(direction, item));
        }

        /** Like agent.containerSize. */
        //%
        containerSizeAsync(direction: SixDirection): Promise<number> {
            return this.on(() => agent.containerSizeAsync(direction));
        }

        /** Like agent.sortContainer. */
        //%
        sortContainerAsync(direction: SixDirection): Promise<void> {
            return this.on(() => agent.sortContainerAsync(direction));
        }

        /** Like agent.craft. */
        //%
        craftAsync(item: number, count: number): Promise<number> {
            return this.on(() => agent.craftAsync(item, count));
        }

        /** Like agent.canCraft. */
        //%
        canCraftAsync(item: number, count: number): Promise<boolean> {
            return this.on(() => agent.canCraftAsync(item, count));
        }

        /** Like agent.stonecut. */
        //%
        stonecutAsync(item: number, count: number): Promise<number> {
            return this.on(() => agent.stonecutAsync(item, count));
        }

        /** Like agent.camera: your view through this robot. */
        //%
        cameraAsync(mode: CameraMode): Promise<void> {
            return this.on(() => agent.cameraAsync(mode));
        }
    }
}

// The player API (issue #26): names as in MakeCode for Minecraft. Every call is about you, the program's owner, and
// is checked by the game. Unlike MakeCode, say shows the message to you only, not to everyone in the chat.
namespace pxsim.player {
    /**
     * Show a message in your chat. Only you see it. Longer than 256 characters is cut, and it is plain text (no colours).
     * @param message what to show, eg: "Hi!"
     */
    //% blockId=player_say block="say %message"
    //% weight=100
    export function sayAsync(message: string): Promise<void> {
        return agent.call("player.say", ["" + message], "");
    }

    /**
     * Where you are: the block your feet are in, as a world position.
     */
    //% blockId=player_position block="player world position"
    //% weight=90
    export async function positionAsync(): Promise<Position> {
        const [x, y, z] = await agent.call("player.position", [], "");
        return new Position(x, y, z);
    }

    /**
     * Which way you look, in degrees: south 0, west 90, north 180 (or -180), east -90.
     */
    //% blockId=player_get_orientation block="player orientation"
    //% weight=89
    export function getOrientationAsync(): Promise<number> {
        return agent.call("player.getOrientation", [], "");
    }

    /**
     * Your player name.
     */
    //% blockId=player_name block="player name"
    //% weight=88
    export function nameAsync(): Promise<string> {
        return agent.call("player.name", [], "");
    }

    /**
     * Teleport yourself to a position in the same dimension. Only in singleplayer or for an operator; otherwise the
     * game refuses it with a NO_PERMISSION error. Relative positions are worked out from where you stand now.
     * @param to where to go, eg: pos(0, 10, 0)
     */
    //% blockId=player_teleport block="teleport to %to=positions_pos"
    //% weight=80
    export async function teleportAsync(to: Position): Promise<void> {
        if (!to) return;
        const p = await to.toWorldAsync();
        await agent.call("player.teleport", [p.x, p.y, p.z], "");
    }

    // Issue #27: chat commands and death. The game sends only your own chat lines and deaths, and only the keyword and
    // the numbers, never the line itself. A program with handlers keeps running after its last line until you press Stop.

    /**
     * Run code when you type a chat command: a chat line that starts with the keyword, for example "jump 10". The
     * line is then not shown in the chat. The words after the keyword are num1, num2 and num3: whole numbers, or 0
     * when missing or not a number. Use * as the keyword to get every chat line. /codeling chat jump 10 does the same.
     * @param command the keyword, one word, eg: "jump"
     * @param handler the code to run
     */
    //% blockId=player_on_chat block="on chat command %command"
    //% draggableParameters=reporter optionalVariableArgs=1
    //% weight=70
    export function onChat(command: string, handler: (num1: number, num2: number, num3: number) => void): void {
        board().onChat("" + command, handler as any as RefAction);
    }

    /**
     * Run code when you die.
     * @param handler the code to run
     */
    //% blockId=player_on_died block="on player died"
    //% weight=69
    export function onDied(handler: () => void): void {
        board().onDied(handler as any as RefAction);
    }

    /**
     * Run your program's own on chat command code for a keyword, as if you had typed it, for example "jump 10". It
     * never sends anything to the chat or to the game, and it waits until that code has finished.
     * @param command the keyword and up to three numbers, eg: "jump"
     */
    //% blockId=player_run_chat_command block="run chat command %command"
    //% weight=68
    export function runChatCommandAsync(command: string): Promise<void> {
        return board().runChatCommand("" + command);
    }
}

// Time and weather (issue #87): MakeCode's gameplay category. They read the dimension you are in and need no robot.
namespace pxsim.gameplay {
    /**
     * The time of day in game ticks, 0 to 23999: 0 is sunrise, 6000 noon, 12000 sunset, 18000 midnight. Every
     * dimension has the Overworld's time.
     */
    //% blockId=gameplay_time_of_day block="time of day"
    //% weight=100
    export function timeOfDayAsync(): Promise<number> {
        return agent.call("gameplay.timeOfDay", [], "");
    }

    /**
     * True when it rains or thunders where you are (snow in cold biomes counts too). Never in the Nether or the End.
     */
    //% blockId=gameplay_is_raining block="is raining"
    //% weight=90
    export function isRainingAsync(): Promise<boolean> {
        return agent.call("gameplay.isRaining", [], "");
    }

    /**
     * Run code when a new day starts where you are: when the time of day goes round from the night to the morning
     * (time 0). Sleeping through the night counts, and so does /time set to an earlier time of day.
     * @param handler the code to run
     */
    //% blockId=gameplay_on_day_start block="on day start"
    //% weight=80
    export function onDayStart(handler: () => void): void {
        board().onDayStart(handler as any as RefAction);
    }
}

namespace pxsim.robots {
    /**
     * One of your robots, by the name it got with /robot spawn <name>. It does what agent does. Give each robot its
     * own "run in parallel" so they work at the same time. The robot must exist in the game (see /robot list).
     * @param name the robot's name, eg: "Miner"
     */
    //% blockId=robots_get block="robot %name"
    //% weight=100
    export function get(name: string): Robot {
        return new Robot("" + name);
    }
}

// Building (issue #90): MakeCode's blocks.fill and blocks.place, shapes and builder, with our own declarations. The game
// sets every block through the robot's FakePlayer (claim mods apply), within 32 blocks of the robot, from the robot's
// inventory unless you get items free (creative mode or the gamerule codeling:free_items), at most 32768 blocks per call.

namespace pxsim.blocks {
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
    export async function fillAsync(block: number, from: Position, to: Position, operator: FillOperation): Promise<void> {
        const p = await from.toWorldAsync();
        const q = await to.toWorldAsync();
        await agent.call("blocks.fill", [block, p.x, p.y, p.z, q.x, q.y, q.z, operator]);
    }

    /**
     * Place one block at a position, replacing what is there.
     * @param block the block, eg: Block.Stone
     * @param position where to place it
     */
    //% blockId=blocks_place block="place %block|at %position=positions_pos"
    //% block.shadow=codeling_block weight=89
    export async function placeAsync(block: number, position: Position): Promise<void> {
        const p = await position.toWorldAsync();
        await agent.call("blocks.fill", [block, p.x, p.y, p.z, p.x, p.y, p.z, FillOperation.Replace]);
    }

    /**
     * Run code when you break a block of this kind by hand, as MakeCode's on block broken. Blocks your robots break
     * and blocks other players break do not count.
     * @param block the kind of block, eg: Block.DiamondOre
     * @param handler the code to run
     */
    //% blockId=blocks_on_block_broken block="on %block broken"
    //% block.shadow=codeling_block weight=88
    export function onBlockBroken(block: number, handler: () => void): void {
        board().onBlockBroken(block, handler as any as RefAction);
    }
}

namespace pxsim.shapes {
    /**
     * A straight line of blocks from one position to another, both ends included.
     * @param block the block, eg: Block.Stone
     * @param from where the line starts
     * @param to where the line ends
     */
    //% blockId=shapes_line block="line of %block|from %from=positions_pos|to %to=positions_pos"
    //% block.shadow=codeling_block weight=90
    export async function lineAsync(block: number, from: Position, to: Position): Promise<void> {
        const p = await from.toWorldAsync();
        const q = await to.toWorldAsync();
        await agent.call("shapes.line", [block, p.x, p.y, p.z, q.x, q.y, q.z]);
    }

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
    export async function circleAsync(block: number, center: Position, radius: number, orientation: Axis, operator: ShapeOperation): Promise<void> {
        const p = await center.toWorldAsync();
        await agent.call("shapes.circle", [block, p.x, p.y, p.z, radius, orientation, operator]);
    }

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
    export async function sphereAsync(block: number, center: Position, radius: number, operator: ShapeOperation): Promise<void> {
        const p = await center.toWorldAsync();
        await agent.call("shapes.sphere", [block, p.x, p.y, p.z, radius, operator]);
    }

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
    export async function mazeAsync(corner: Position, width: number, length: number, wall: number, floor: number, seed?: number): Promise<void> {
        const p = await corner.toWorldAsync();
        const args = [p.x, p.y, p.z, width, length, wall, floor];
        if (seed !== undefined) args.push(seed);
        await agent.call("shapes.maze", args);
    }
}

namespace pxsim.builder {
    // CompassDirection clockwise from north, as x and z steps.
    const STEPS = [[0, -1], [1, 0], [0, 1], [-1, 0]];

    // The builder lives in the editor: only where it starts (your feet) and what it builds ask the game.
    async function state(): Promise<{ at: Position, facing: number, marks: Position[] }> {
        const b = board().builder;
        if (!b.at) b.at = await positions.create(0, 0, 0).toWorldAsync();
        return b;
    }

    /**
     * Put the builder at a position. It starts at your feet facing south; it is invisible and never touches blocks
     * when it moves.
     * @param position where to go
     */
    //% blockId=builder_teleport_to block="builder teleport to %position=positions_pos"
    //% weight=100
    export async function teleportToAsync(position: Position): Promise<void> {
        const b = board().builder;
        b.at = await position.toWorldAsync();
    }

    /**
     * Move the builder, relative to the way it faces.
     * @param direction which way, eg: SixDirection.Forward
     * @param steps how many blocks, eg: 1
     */
    //% blockId=builder_move block="builder move %direction|by %steps"
    //% weight=99
    export async function moveAsync(direction: SixDirection, steps: number): Promise<void> {
        const b = await state();
        if (direction === SixDirection.Up || direction === SixDirection.Down) {
            b.at = new Position(b.at.x, b.at.y + (direction === SixDirection.Up ? steps : -steps), b.at.z);
            return;
        }
        const turn = direction === SixDirection.Back ? 2 : direction === SixDirection.Left ? 3 : direction === SixDirection.Right ? 1 : 0;
        const step = STEPS[(b.facing + turn) % 4];
        b.at = new Position(b.at.x + step[0] * steps, b.at.y, b.at.z + step[1] * steps);
    }

    /**
     * Turn the builder left or right.
     * @param direction which way, eg: TurnDirection.Left
     */
    //% blockId=builder_turn block="builder turn %direction"
    //% weight=98
    export function turn(direction: TurnDirection): void {
        const b = board().builder;
        b.facing = (b.facing + (direction === TurnDirection.Left ? 3 : 1)) % 4;
    }

    /**
     * Make the builder face a compass direction.
     * @param direction which way, eg: CompassDirection.North
     */
    //% blockId=builder_face block="builder face %direction"
    //% weight=97
    export function face(direction: CompassDirection): void {
        board().builder.facing = direction;
    }

    /**
     * Remember where the builder is. Line and fill go from the last mark to the builder.
     */
    //% blockId=builder_mark block="builder place mark"
    //% weight=96
    export async function markAsync(): Promise<void> {
        const b = await state();
        b.marks.push(b.at);
    }

    /**
     * Where the builder is.
     */
    //% blockId=builder_position block="builder position"
    //% weight=95
    export async function positionAsync(): Promise<Position> {
        return (await state()).at;
    }

    /**
     * Place a block where the builder is.
     * @param block the block, eg: Block.Stone
     */
    //% blockId=builder_place block="builder place %block"
    //% block.shadow=codeling_block weight=94
    export async function placeAsync(block: number): Promise<void> {
        const b = await state();
        await blocks.placeAsync(block, b.at);
    }

    /**
     * A line of blocks from the last mark to the builder. Without a mark it starts where the builder is.
     * @param block the block, eg: Block.Stone
     */
    //% blockId=builder_line block="builder line from mark with %block"
    //% block.shadow=codeling_block weight=93
    export async function lineAsync(block: number): Promise<void> {
        const b = await state();
        await shapes.lineAsync(block, b.marks[b.marks.length - 1] || b.at, b.at);
    }

    /**
     * Fill the box between the last mark and the builder. Without a mark it fills where the builder is.
     * @param block the block, eg: Block.Stone
     * @param operator how to fill, eg: FillOperation.Replace
     */
    //% blockId=builder_fill block="builder fill from mark with %block|%operator"
    //% block.shadow=codeling_block weight=92
    export async function fillAsync(block: number, operator: FillOperation): Promise<void> {
        const b = await state();
        await blocks.fillAsync(block, b.marks[b.marks.length - 1] || b.at, b.at, operator);
    }
}

namespace pxsim.console {
    /**
     * Write a line in the simulator
     */
    //% blockId=console_say block="say %text"
    export function log(text: string): void {
        board().log(`say: ${text}`);
    }
}

namespace pxsim.control {
    /**
     * Run code at the same time as the rest of the program, for example to let a second robot work while the first
     * one does something else. Each robot does one thing at a time; give each robot its own run in parallel.
     * @param handler the code to run
     */
    //% blockId=control_run_in_parallel block="run in parallel"
    //% handlerStatement=1 blockNamespace=robots weight=80
    export function runInParallel(handler: RefAction): void {
        thread.runInBackground(handler);
    }
}

// Issue #215, names and block texts as in MakeCode for Minecraft. Both use pxt's own fibers, like runInParallel: a
// pause only holds the code that awaits it, and Stop kills the runtime, so a pause or loop that is waiting never resumes.
namespace pxsim.loops {
    /**
     * Pause this part of the program for some milliseconds (1000 ms is one second). Code in run in parallel and in
     * other forever loops keeps running.
     * @param ms how long to pause, in milliseconds, eg: 100
     */
    //% blockId=loops_pause block="pause (ms) %ms"
    //% weight=99
    export function pauseAsync(ms: number): Promise<void> {
        return new Promise<void>(resolve => runtime.schedule(resolve, ms));
    }

    /**
     * Run code over and over again, at the same time as the rest of the program, until the program is stopped.
     * @param handler the code to repeat
     */
    //% blockId=loops_forever block="forever"
    //% afterOnStart=true blockAllowMultiple=1 weight=100
    export function forever(handler: RefAction): void {
        // thread.forever waits 20 ms between rounds, so a loop that never awaits cannot freeze the editor.
        thread.forever(handler);
    }
}
