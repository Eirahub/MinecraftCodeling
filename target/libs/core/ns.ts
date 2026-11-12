/**
 * Robot commands
 */
//% color=#2E7D32 weight=100 icon="\uf1b9"
//% groups='["Movement", "Building", "Inspecting", "Sensors", "Measuring", "Inventory", "Containers", "Crafting", "Feedback"]'
namespace agent {
    /**
     * Why follow path stopped, to compare with what it answers.
     * @param stop the reason, eg: PathStop.Junction
     */
    //% shim=TD_ID blockId=agent_path_stop block="%stop"
    //% group="Movement" weight=93
    export function pathStop(stop: PathStop): number {
        return stop;
    }

    /**
     * A liquid, to compare with what detect liquid answers.
     * @param liquid the liquid, eg: Liquid.Lava
     */
    //% shim=TD_ID blockId=agent_liquid block="%liquid"
    //% group="Sensors" weight=58
    export function liquid(liquid: Liquid): number {
        return liquid;
    }
}

/**
 * You, the player who runs the program (issue #26)
 */
//% color=#1565C0 weight=97 icon=""
namespace player {
}

/**
 * The time and weather where you are (issue #87)
 */
//% color=#F9A825 weight=92 icon=""
namespace gameplay {
}

/**
 * Your other robots, by name (issue #141). agent is your default robot.
 */
//% color=#558B2F weight=95 icon="\uf0c0"
namespace robots {
}

/**
 * Positions in the world
 */
//% color=#00838F weight=90 icon=""
namespace positions {
}

// The MakeCode globals. They carry the blocks, so pasted `pos(0, 0, 1)` lines decompile to blocks.

/**
 * A position relative to your feet, with world axes: x east, y up, z south. Like ~ in commands.
 * @param x blocks east, eg: 0
 * @param y blocks up, eg: 0
 * @param z blocks south, eg: 0
 */
//% blockId=positions_pos block="~%x|~%y|~%z" blockNamespace=positions
//% inlineInputMode=inline weight=100
function pos(x: number, y: number, z: number): Position {
    return positions.create(x, y, z);
}

/**
 * A position that turns with you: x to your left, y up, z forward. Like ^ in commands.
 * @param x blocks to the left, eg: 0
 * @param y blocks up, eg: 0
 * @param z blocks forward, eg: 0
 */
//% blockId=positions_pos_local block="^%x|^%y|^%z" blockNamespace=positions
//% inlineInputMode=inline weight=99
function posLocal(x: number, y: number, z: number): Position {
    return positions.createLocal(x, y, z);
}

/**
 * A fixed position in the world.
 * @param x world x, eg: 0
 * @param y world y, eg: 64
 * @param z world z, eg: 0
 */
//% blockId=positions_world block="world %x|%y|%z" blockNamespace=positions
//% inlineInputMode=inline weight=98
function world(x: number, y: number, z: number): Position {
    return positions.createWorld(x, y, z);
}

/**
 * A random block position in the box between two corners. The same as positions.random.
 */
function randpos(from: Position, to: Position): Position {
    return positions.random(from, to);
}

/**
 * An invisible builder that moves around and builds lines and boxes (issue #90)
 */
//% color=#AD1457 weight=75 icon=""
namespace builder {
}

/**
 * Lines, circles, spheres and mazes of blocks (issues #90 and #182)
 */
//% color=#6A1B9A weight=70 icon=""
namespace shapes {
}

/**
 * Text output in the simulator
 */
//% color=#455A64 weight=50
namespace console {
}

/**
 * Blocks and items as numbers, and filling a box with blocks
 */
//% color=#6D4C41 weight=80 icon=""
namespace blocks {
    /**
     * An item (or block) for the calls that take items: chests, crafting, setItem.
     * @param item the item, eg: Item.Coal
     */
    //% shim=TD_ID blockId=codeling_item block="%item" blockHidden=true
    //% item.fieldEditor="gridpicker" item.fieldOptions.hasSearchBar=true
    //% item.fieldOptions.columns=3 item.fieldOptions.width=360 item.fieldOptions.maxRows=8
    export function item(item: Item): number {
        return item;
    }

    /**
     * A block as a number. Programs that pass Block.Stone where an item is wanted show this block.
     * @param block the block, eg: Block.Stone
     */
    //% shim=TD_ID blockId=codeling_block block="%block" blockHidden=true
    export function block(block: Block): number {
        return block;
    }
}

// Issue #221. MakeCode Python turns `assert x, "text"` into control.assert and `raise Error("text")` into control.fail,
// so these names make both compile. Both throw like JavaScript `throw`: a try/catch catches the text, and with none
// the program ends with the text in a dialog (debug mode also marks the line or block).
namespace control {
    /**
     * Stop the program with a message when a condition is false, to check that the program did what it should.
     * @param condition what must be true, eg: true
     * @param message what to show when it is false, eg: "check failed"
     */
    // pxt writes Python calls by name, and `control.assert(...)` is not Python (assert is a keyword). So the block
    // writes control.check (blockAliasFor), and control.assert in JavaScript or Python decompiles to the same block.
    //% blockId=control_assert block="check %condition|or stop with %message"
    //% blockNamespace=logic weight=10 blockAliasFor="control.check"
    export function assert(condition: boolean, message: string): void {
        if (!condition) throw message;
    }

    /**
     * Stop the program with a message when a condition is false, to check that the program did what it should.
     * @param condition what must be true, eg: true
     * @param message what to show when it is false, eg: "check failed"
     */
    export function check(condition: boolean, message: string): void {
        assert(condition, message);
    }

    /**
     * Stop the program with an error message.
     * @param message what went wrong, eg: "something went wrong"
     */
    //% blockId=control_fail block="stop with error %message"
    //% blockNamespace=logic weight=9
    export function fail(message: string): void {
        throw message;
    }
}
