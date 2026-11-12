// Small example programs in the style of MakeCode for Minecraft lessons (issue #31), written for this repo. Each one
// must round-trip through Blocks and Python unchanged and run in the simulator. `log` is the robot log the run must
// show against the standalone relay, whose fake answers are fixed (true for most calls, [0, 0, 0] for positions).
// The log prints enums and items as their numbers: SixDirection Forward 0, Back 1, Left 2, Right 3, Up 4, Down 5.
// Issue #216: the program is the blocks code in target/docs/examples/<page>.md, the page the editor's Examples gallery
// opens, so the gallery can only show what this suite tests.
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const docs = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "target", "docs");
const START = ["connected as standalone", "run 0 started"];

export const EXAMPLES = [
    {
        page: "stone-path",
        log: [...START, "setItem 4 64 1: true", "setSlot 1: true", "setAssist 0 true: true",
            "move 0 2: done", "turn 2: done", "move 0 2: done", "turn 2: done", "move 0 2: done", "turn 2: done",
            "setAssist 0 false: true"],
    },
    {
        page: "staircase",
        log: [...START, "setItem 5 64 1: true",
            "move 4 1: done", "move 0 1: done", "place 5: true",
            "move 4 1: done", "move 0 1: done", "place 5: true",
            "move 4 1: done", "move 0 1: done", "place 5: true"],
    },
    {
        page: "tunnel",
        log: [...START, "detect 0 0: true", "destroy 0: true", "destroy 4: true", "move 0 1: done",
            "detect 0 0: true", "destroy 0: true", "destroy 4: true", "move 0 1: done", "say Tunnel done: true"],
    },
    {
        page: "pillar",
        log: [...START, "getPosition : 0,0,0", "say: 0 0 0", "moveTo 0 64 3: true", "setItem 1 64 1: true",
            "place 5: true", "move 4 1: done", "place 5: true", "move 4 1: done"],
    },
    {
        // The builder moves in the editor: it starts at your feet (the fake relay says 0 0 0) facing south, and only
        // what it builds goes to the game, with world coordinates worked out from its moves. Building answers 0 here.
        page: "room-and-fence",
        log: [...START, "positions.toWorld 1 0 0 0: 0,0,0", "blocks.fill 1 0 0 0 2 3 4 1: 0",
            "blocks.fill 20 10 64 10 10 64 10 0: 0", "shapes.line 20 10 64 9 12 64 9: 0", "say: 12 64 9"],
    },
    {
        // Issue #141: a named robot in its own run in parallel; its lines in the log start with its name.
        page: "two-robots",
        log: [...START, "Miner: move 0 2: done", "Miner: say Done: true"],
    },
    {
        // Issue #215: a guard walks back and forth on its own, half a second apart. Two rounds keep the log fixed;
        // the loop goes on (waiting) until the suite presses Stop.
        page: "guard-patrol",
        log: [...START, "move 0 3: done", "turn 2: done", "turn 2: done", "move 0 3: done", "turn 2: done", "turn 2: done"],
    },
    {
        // Issue #87: the fake relay answers light level 0, a dark cave, so every round places a torch behind the robot.
        page: "dark-torches",
        log: [...START, "setItem 50 16 1: true", "setSlot 1: true",
            "move 0 2: done", "lightLevel : 0", "place 1: true",
            "move 0 2: done", "lightLevel : 0", "place 1: true",
            "move 0 2: done", "lightLevel : 0", "place 1: true", "say Lit up: true"],
    },
].map(example => ({ ...example, name: example.page, js: program(example.page) }));

/** The program in docs/examples/<page>.md: its one blocks code block, as the gallery opens it. */
function program(page) {
    const md = readFileSync(join(docs, "examples", `${page}.md`), "utf8");
    const code = md.match(/```blocks\n([\s\S]*?)```/)?.[1];
    if (!code) throw new Error(`no blocks code in target/docs/examples/${page}.md`);
    return code;
}

/** The cards of a gallery page (docs/examples.md, or docs/_locales/<locale>/examples.md). */
export function galleryCards(file = "examples.md") {
    return JSON.parse(readFileSync(join(docs, file), "utf8").match(/```codecard\n([\s\S]*?)```/)[1]);
}
