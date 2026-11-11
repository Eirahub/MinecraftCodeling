// Editor test suite (issue #136): drives the Codeling editor in headless Chromium against the standalone relay.
// Run it with ./gradlew :fabric:26.3:editorTest (not part of `build`). It checks:
//   1. every toolbox block from target/libs/core: JavaScript to Blocks to Python to Blocks to JavaScript, no grey
//      blocks, no changed or lost arguments, and a test program for every block (programs/blocks.mjs);
//   2. small MakeCode-style example programs (programs/examples.mjs) round-trip the same way and run unchanged in the
//      simulator against the relay's fake answers, with the robot log they must produce;
//   3. pause and forever (issue #215): the times in the robot log, a forever that repeats, and Stop during a pause;
//   4. position math (issue #219): distance, subtract, scale, sort by distance and nearest give the right numbers;
//   5. robot errors and assert (issues #218, #221): a refused command is caught in JavaScript, Python and run in
//      parallel code, and nobody catching it, STOPPED and Stop end the program; assert, raise and throw end it with
//      their message;
//   6. debug mode (issue #217): a breakpoint and a step stop before the next robot command, the variables show, the
//      heartbeat goes on, continue runs to the end, Exit Debug Mode and /robot stop end a stopped program, breakpoints
//      in Python and JavaScript, slow motion gives the normal robot log, and a failed assert marks its block;
//   7. saved projects from before commit d506004 (fixtures/) load with their Block values intact;
//   8. the Examples gallery (issue #216): a card per example program, in English and Norwegian, that opens its program,
//      with no request to another host;
//   9. branding: the header names Codeling once, the About box says pxt editor and not Microsoft MakeCode, and a new
//      project opens without the README side panel. Screenshots go to CODELING_SHOTS (default fabric/versions/26.3/build/editor-test).
// The relay token stays in this process (lib/relay.mjs). Output: progress lines, then one summary line.
import { chromium } from "playwright-core";
import { mkdirSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { startRelay, URL_PATTERN } from "./lib/relay.mjs";
import * as editor from "./lib/editor.mjs";
import { BLOCK_PROGRAMS } from "./programs/blocks.mjs";
import { EXAMPLES, galleryCards } from "./programs/examples.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const core = join(here, "..", "target", "libs", "core");
const shots = process.env.CODELING_SHOTS ?? join(here, "..", "fabric", "versions", "26.3", "build", "editor-test");
const port = Number(process.env.CODELING_TEST_PORT ?? 25591);
const failures = [];
const counts = { blocks: 0, programs: 0, fixtures: 0, checks: 0 };

const step = text => console.log(`# ${text}`);
const fail = (what, why) => {
    failures.push(`${what}: ${why}`);
    console.log(`FAIL ${what}: ${why}`);
};
const check = (ok, what, why) => {
    counts.checks++;
    if (!ok) fail(what, why);
    return ok;
};

/** Every visible blockId declared in the core library: a `//%` comment group with blockId= and without blockHidden. */
export function toolboxBlocks() {
    const ids = [];
    for (const file of ["sims.d.ts", "ns.ts"]) {
        let group = "";
        for (const line of readFileSync(join(core, file), "utf8").split("\n").concat("")) {
            if (line.trim().startsWith("//%")) {
                group += " " + line;
                continue;
            }
            const id = group.match(/blockId=(\w+)/)?.[1];
            if (id && !/blockHidden=true/.test(group)) ids.push(id);
            group = "";
        }
    }
    return ids;
}

// Lines that differ between what went in and what came back, for the failure message.
function changedLines(before, after) {
    const a = before.trim().split("\n").map(l => l.trim());
    const b = after.trim().split("\n").map(l => l.trim());
    const gone = a.filter(l => !b.includes(l));
    const added = b.filter(l => !a.includes(l));
    if (gone.length === 0 && added.length === 0) {
        // Same lines, so the order, indentation or line endings changed: show the first line that differs.
        const x = before.trim().split("\n"), y = after.trim().split("\n");
        const i = x.findIndex((l, k) => l !== y[k]);
        return `line ${i + 1} was ${JSON.stringify(x[i])}, came back as ${JSON.stringify(y[i])}`;
    }
    return `lost or changed: ${JSON.stringify(gone)}; came back as: ${JSON.stringify(added)}`;
}

/** JavaScript to Blocks to Python to Blocks to JavaScript. Returns the block types seen in Blocks. */
async function roundTrip(page, name, js) {
    await editor.toJavaScript(page, true);
    await editor.setSource(page, "ts", js);
    await editor.toBlocks(page);
    const types = await editor.blockTypes(page);
    check((await editor.greyBlocks(page)).length === 0, name, `grey blocks from JavaScript: ${JSON.stringify(await editor.greyBlocks(page))}`);
    const py = await editor.toPython(page);
    await editor.toBlocks(page);
    check((await editor.greyBlocks(page)).length === 0, name, `grey blocks from Python: ${JSON.stringify(await editor.greyBlocks(page))}`);
    const back = await editor.toJavaScript(page);
    check(back.trim() === js.trim(), name, changedLines(js, back));
    return { types, py, back };
}

async function blocksSection(page) {
    const ids = toolboxBlocks();
    step(`${ids.length} toolbox blocks in target/libs/core`);
    const missing = ids.filter(id => !BLOCK_PROGRAMS[id]);
    check(missing.length === 0, "block programs", `no test program in e2e/programs/blocks.mjs for ${missing.join(", ")}`);
    const stale = Object.keys(BLOCK_PROGRAMS).filter(id => !ids.includes(id));
    check(stale.length === 0, "block programs", `programs for blocks that are not in the toolbox: ${stale.join(", ")}`);
    // One program with every block keeps the run short; a failure names the lines (and so the blocks) that changed.
    const js = Object.values(BLOCK_PROGRAMS).join("\n") + "\n";
    const { types, py } = await roundTrip(page, "every block", js);
    for (const id of ids.filter(id => BLOCK_PROGRAMS[id])) {
        if (check(types.includes(id), id, `its program did not make a ${id} block (got ${[...new Set(types)].join(", ")})`)) counts.blocks++;
    }
    check(/agent\.set_item\(/.test(py) && /agent\.find_nearest\(/.test(py), "every block", "Python has no snake_case agent calls");
}

async function examplesSection(page) {
    for (const example of EXAMPLES) {
        step(`example: ${example.name}`);
        const before = failures.length;
        await roundTrip(page, example.name, example.js);
        const log = await editor.run(page, example.log.length);
        check(JSON.stringify(log) === JSON.stringify(example.log), example.name,
            `robot log ${JSON.stringify(log)}, expected ${JSON.stringify(example.log)}`);
        await editor.stop(page);
        if (failures.length === before) counts.programs++;
    }
}

// Issue #215: pause holds only its own code, forever repeats, an empty forever does not hang the page, and Stop ends
// the program in the middle of a pause. Times come from the robot log's own time stamps.
const TIMING = `control.runInParallel(function () {
    for (let index = 0; index < 5; index++) {
        console.log("tick")
        loops.pause(100)
    }
})
control.runInParallel(function () {
    loops.pause(3000)
    console.log("too late")
})
agent.say("start")
loops.pause(1000)
console.log("end")
loops.forever(function () {
})
loops.forever(function () {
    console.log("loop")
    loops.pause(200)
})
`;

async function timingSection(page) {
    step("timing: pause, forever and Stop");
    await editor.toJavaScript(page, true);
    await editor.setSource(page, "ts", TIMING);
    await editor.start(page);
    const started = Date.now();
    let lines = [];
    while (Date.now() - started < 30000 && lines.filter(l => l.endsWith("say: loop")).length < 3) {
        await page.waitForTimeout(100);
        lines = await editor.robotLog(page, true);
    }
    await editor.stop(page);
    // The click reaches the simulator within a few ms; a line already on its way may still land.
    await page.waitForTimeout(200);
    const atStop = await editor.robotLog(page, true);
    // The parallel 3 s pause would have ended by now if Stop had not ended it.
    await page.waitForTimeout(Math.max(0, started + 4000 - Date.now()));
    const after = await editor.robotLog(page, true);
    const ms = line => line && line.slice(0, 12).split(/[:.]/).reduce((t, n, i) => t + Number(n) * [3600000, 60000, 1000, 1][i], 0);
    const text = atStop.map(l => l.slice(13));
    const pauseStart = ms(atStop[text.indexOf("say start: true")]), pauseEnd = ms(atStop[text.indexOf("say: end")]);
    check(pauseEnd - pauseStart >= 950 && pauseEnd - pauseStart <= 1600, "pause",
        `pause(1000) took ${pauseEnd - pauseStart} ms: ${text.join(" | ")}`);
    check(text.slice(0, text.indexOf("say: end")).filter(l => l === "say: tick").length === 5, "pause",
        `run in parallel did not tick 5 times during the pause: ${text.join(" | ")}`);
    // The empty forever runs next to this one, so lines that keep coming also show it does not hang the page.
    check(text.filter(l => l === "say: loop").length >= 3, "forever", `the forever loop did not repeat: ${text.join(" | ")}`);
    check(after.length === atStop.length && !after.some(l => l.endsWith("too late")), "stop",
        `lines after Stop: ${after.slice(atStop.length).join(" | ") || "none"}`);
}

// Issue #219: known positions give known answers. pos(0, 64, 0) against a world position goes through
// positions.toWorld, which the standalone relay answers with the same numbers, so it is world 0 64 0 here.
// The relay answers findBounds with two world 0 0 0 corners: a list from the game must keep working Positions.
const POSITION_MATH = `let a = world(0, 64, 0)
let b = world(3, 68, 0)
console.log("distance " + a.distanceTo(b))
console.log("mixed " + pos(0, 64, 0).distanceTo(b))
console.log("subtract " + b.subtract(a).toString())
console.log("scale " + pos(3, -3, 1).scale(0.5).toString())
let list = [world(10, 64, 0), world(1, 64, 0), world(5, 64, 0)]
let sorted = positions.sortByDistance(list, a)
console.log("sorted " + sorted[0].toString() + ", " + sorted[1].toString() + ", " + sorted[2].toString())
console.log("list " + list[0].toString())
console.log("nearest " + positions.nearest(list, world(6, 64, 0)).toString())
let empty: Position[] = []
console.log("empty " + (positions.nearest(empty, a) ? "found" : "none") + " " + positions.sortByDistance(empty, a).length)
let bounds = agent.findBounds(8)
console.log("bounds " + bounds[1].toString() + " " + positions.nearest(bounds, b).toString())
`;
const POSITION_MATH_SAYS = ["distance 5", "mixed 5", "subtract 3 4 0", "scale ~2 ~-2 ~1",
    "sorted 1 64 0, 5 64 0, 10 64 0", "list 10 64 0", "nearest 5 64 0", "empty none 0",
    "bounds 0 0 0 0 0 0"];

async function positionMathSection(page) {
    step("position math: distance, subtract, scale, sort and nearest");
    await editor.toJavaScript(page, true);
    await editor.setSource(page, "ts", POSITION_MATH);
    const says = (await editor.run(page, 2 + POSITION_MATH_SAYS.length)).filter(l => l.startsWith("say: ")).map(l => l.slice(5));
    await editor.stop(page);
    check(JSON.stringify(says) === JSON.stringify(POSITION_MATH_SAYS), "position math",
        `said ${JSON.stringify(says)}, expected ${JSON.stringify(POSITION_MATH_SAYS)}`);
}

// Issues #218 and #221: the standalone relay refuses say "refuse <CODE>" with that code. A refused command throws into
// the program, a catch takes it and the program goes on; nobody catching it, STOPPED and Stop end the program. A failed
// assert, Python's assert and raise and JavaScript's throw all end the program with their message in a dialog.
const CONNECTED = ["connected as standalone", "run 0 started"];
const REFUSED = code => `say refuse ${code}: ${code}: refused on purpose by the standalone relay`;
const DEBUG_HINT = " (debug mode, the bug button, shows the block or line it came from)";
const ERRORS = [
    {
        name: "caught in JavaScript", lang: "ts",
        src: `try {
    agent.say("refuse OUT_OF_RANGE")
    console.log("not here")
} catch (e) {
    let error: string = e
    console.log("caught " + error.substr(0, error.indexOf(":")))
}
try {
    control.assert(false, "check")
} catch (e) {
    console.log("caught " + e)
}
console.log("after")
`,
        log: [...CONNECTED, REFUSED("OUT_OF_RANGE"), "say: caught OUT_OF_RANGE", "say: caught check", "say: after"],
    },
    {
        name: "caught in Python", lang: "py",
        src: `try:
    agent.say("refuse PROTECTED")
    print("not here")
except:
    print("caught")
try:
    assert 1 == 2, "check"
except:
    print("caught assert")
print("after")
`,
        log: [...CONNECTED, REFUSED("PROTECTED"), "say: caught", "say: caught assert", "say: after"],
    },
    {
        name: "caught in run in parallel", lang: "ts",
        src: `control.runInParallel(function () {
    try {
        agent.say("refuse NO_PERMISSION")
    } catch (e) {
        console.log("caught in parallel")
    }
})
console.log("main goes on")
`,
        log: ["say: main goes on", ...CONNECTED, REFUSED("NO_PERMISSION"), "say: caught in parallel"],
    },
    {
        name: "not caught", lang: "ts",
        src: `agent.move(SixDirection.Forward, 1)
agent.say("refuse OUT_OF_RANGE")
console.log("not here")
`,
        log: [...CONNECTED, "move 0 1: done", REFUSED("OUT_OF_RANGE"), "Robot error: OUT_OF_RANGE: refused on purpose by the standalone relay"],
        dialog: "Robot error OUT_OF_RANGE: refused on purpose by the standalone relay",
    },
    {
        name: "not caught in forever", lang: "ts",
        src: `loops.forever(function () {
    agent.say("refuse PROTECTED")
})
`,
        log: [...CONNECTED, REFUSED("PROTECTED"), "Robot error: PROTECTED: refused on purpose by the standalone relay"],
        dialog: "Robot error PROTECTED: refused on purpose by the standalone relay",
    },
    {
        name: "STOPPED cannot be caught", lang: "ts",
        src: `try {
    agent.say("refuse STOPPED")
} catch (e) {
    console.log("caught")
}
console.log("after")
`,
        log: [...CONNECTED, "Program stopped: The robot was stopped in the game (refused on purpose by the standalone relay)."],
        dialog: "Program stopped The robot was stopped in the game (refused on purpose by the standalone relay).",
    },
    {
        name: "failed assert", lang: "ts",
        src: `control.assert(1 + 1 == 3, "one and one is not three")
console.log("not here")
`,
        log: ["Program error: one and one is not three" + DEBUG_HINT],
        dialog: "Program error one and one is not three" + DEBUG_HINT,
    },
    {
        name: "Python assert", lang: "py",
        src: `x = 2
assert x == 3, "x is not 3"
print("not here")
`,
        log: ["Program error: x is not 3" + DEBUG_HINT],
        dialog: "Program error x is not 3" + DEBUG_HINT,
    },
    {
        name: "Python raise", lang: "py",
        src: `raise ValueError("gave up")
print("not here")
`,
        log: ["Program error: gave up" + DEBUG_HINT],
        dialog: "Program error gave up" + DEBUG_HINT,
    },
    {
        name: "JavaScript throw", lang: "ts",
        src: `throw "gave up"
console.log("not here")
`,
        log: ["Program error: gave up" + DEBUG_HINT],
        dialog: "Program error gave up" + DEBUG_HINT,
    },
];

// Puts a program in the JavaScript or Python editor. Python is reached through Blocks, as roundTrip does.
async function setProgram(page, lang, src) {
    await editor.toJavaScript(page, true);
    await editor.setSource(page, "ts", lang === "ts" ? src : "let x = 0\n");
    if (lang === "py") {
        await editor.toBlocks(page);
        await editor.toPython(page);
        await editor.setSource(page, "py", src);
    }
}

// The dialog the simulator opened (its text, spaces joined), closed again; "" when there is none.
async function closeDialog(page) {
    const dialog = page.locator(".ui.modal:visible, .common-modal:visible").first();
    if (!(await dialog.count())) return "";
    const text = (await dialog.innerText()).replace(/\s+/g, " ").replace(/ Close$/, "").trim();
    await dialog.locator("button:has-text('Close'), .closeIcon").first().click();
    return text;
}

async function errorsSection(page) {
    for (const test of ERRORS) {
        step(`errors: ${test.name}`);
        await setProgram(page, test.lang, test.src);
        const log = await editor.run(page, test.log.length);
        const dialog = await closeDialog(page);
        await editor.stop(page);
        check(JSON.stringify(log) === JSON.stringify(test.log), test.name, `robot log ${JSON.stringify(log)}, expected ${JSON.stringify(test.log)}`);
        check(dialog === (test.dialog ?? ""), test.name, `dialog "${dialog}", expected "${test.dialog ?? ""}"`);
        // Back to an empty program, so the next one starts from JavaScript.
        if (test.lang === "py") {
            await editor.setSource(page, "py", "x = 0\n");
            await editor.toBlocks(page);
        }
    }

    step("errors: Stop cannot be caught");
    await setProgram(page, "ts", `try {
    agent.say("start")
    loops.pause(2000)
    console.log("not here")
} catch (e) {
    console.log("caught")
}
`);
    const started = await editor.run(page, CONNECTED.length + 1);
    await editor.stop(page);
    await page.waitForTimeout(2500);
    check(JSON.stringify(await editor.robotLog(page)) === JSON.stringify(started), "Stop",
        `lines after Stop: ${(await editor.robotLog(page)).slice(started.length).join(" | ") || "none"}`);
}

// Issue #217: debug mode (appTheme.debugger) and the main.js patch that makes "Exit Debug Mode" stop the program.
// The breakpoint sits on the say line: the moves before it are done, the say and the turn after it are not sent.
const DEBUG = `let count = 0
for (let index = 0; index < 3; index++) {
    count += 1
    agent.move(SixDirection.Forward, 1)
}
agent.say("done " + count)
agent.turn(TurnDirection.Left)
`;
const DEBUG_START = ["connected as standalone", "run 0 started"];
const DEBUG_MOVES = [...DEBUG_START, "move 0 1: done", "move 0 1: done", "move 0 1: done"];
const DEBUG_LOG = [...DEBUG_MOVES, "say done 3: true", "turn 2: done"];

// Polls until ok(value) or the time is up; returns the last value.
async function until(page, poll, ok, ms = 10000) {
    const deadline = Date.now() + ms;
    let value = await poll();
    while (!ok(value) && Date.now() < deadline) {
        await page.waitForTimeout(100);
        value = await poll();
    }
    return value;
}

async function debugSection(page) {
    // Every message the page sends on the relay socket, without the hello (it holds the token).
    const sent = [];
    page.on("websocket", ws => ws.on("framesent", f => {
        const text = String(f.payload);
        if (!text.includes("\"hello\"")) sent.push(JSON.parse(text));
    }));
    const log = () => editor.robotLog(page);
    const variables = () => page.evaluate(() => document.querySelector(".debugvariables")?.innerText.replace(/\s+/g, " ").trim() ?? "");
    const stopped = () => page.locator(".debugtoolbar .dbg-pause-resume.play").count().then(n => n > 0);
    const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
    const enter = async () => {
        // pxt restarts the program in debug mode without a new robot log, so the old lines go first.
        await editor.clearLog(page);
        await page.locator("button.debug-button").click();
        await until(page, stopped, s => s);
    };
    const exit = async () => {
        await page.locator(".exit-debugmode-btn").first().click();
        await page.locator(".debugtoolbar").first().waitFor({ state: "detached" });
    };
    const resume = () => page.locator(".debugtoolbar .dbg-pause-resume").click();
    const sayBreakpoint = () => page.locator(".blocklyWorkspace g.blocklyBlock[data-id][class^=agent_say] > .blocklyIconGroup").first().click();
    // A breakpoint in the Monaco margin, on the line that starts with `text`.
    const lineBreakpoint = async text => {
        const lines = await page.locator(".monaco-editor:visible .view-line").allInnerTexts();
        const read = () => page.locator(".monaco-editor:visible .view-line").evaluateAll(ls => ls.map(l => l.getBoundingClientRect().top + l.offsetHeight / 2));
        // The editor moves while the debugger panels open; click once the lines stand still.
        let tops = await read();
        for (let last; JSON.stringify(last) !== JSON.stringify(tops); tops = await read()) {
            last = tops;
            await page.waitForTimeout(300);
        }
        const margin = await page.locator(".monaco-editor:visible .glyph-margin").first().boundingBox();
        await page.mouse.click(margin.x + margin.width / 2, tops[lines.findIndex(l => l.trim().startsWith(text))]);
    };

    step("debug mode in Blocks: breakpoint, variables, step, continue");
    await editor.toJavaScript(page, true);
    await editor.setSource(page, "ts", DEBUG);
    await editor.toBlocks(page);
    await editor.start(page);
    const normal = await until(page, log, l => l.length >= DEBUG_LOG.length);
    await editor.stop(page);
    check(same(normal, DEBUG_LOG), "debug", `normal run logged ${JSON.stringify(normal)}, expected ${JSON.stringify(DEBUG_LOG)}`);
    await enter();
    check(await page.locator(".debugtoolbar .dbg-step").isVisible(), "debug mode", "no Step button in the debugger toolbar");
    check((await log()).length === 0, "debug mode", `the program did not stop before its first command: ${(await log()).join(" | ")}`);
    await sayBreakpoint();
    await resume();
    const atBreakpoint = await until(page, log, l => l.length >= DEBUG_MOVES.length);
    await until(page, stopped, s => s);
    await page.waitForTimeout(1500);
    check(same(await log(), DEBUG_MOVES), "breakpoint", `stopped before say with ${JSON.stringify(await log())}, expected ${JSON.stringify(DEBUG_MOVES)}`);
    check(/\bcount: 3\b/.test(await variables()), "variables", `the Variables panel shows "${await variables()}", expected count: 3`);
    const highlighted = await page.evaluate(() => [...document.querySelectorAll(".blocklyPath.pxtRendererHighlight")]
        .map(p => p.closest("g.blocklyBlock").getAttribute("class").split(" ")[0]));
    check(same(highlighted, ["agent_say"]), "breakpoint", `highlighted blocks ${JSON.stringify(highlighted)}, expected the say block`);
    // The heartbeat keeps the run alive while the program stands still (the game ends a run after 10 s without one).
    sent.length = 0;
    await page.waitForTimeout(3500);
    check(sent.some(m => m.op === "heartbeat") && !sent.some(m => m.t === "cmd"), "breakpoint",
        `sent while stopped: ${JSON.stringify(sent.map(m => m.op ?? m.t))}, expected a heartbeat and no command`);
    await page.locator(".debugtoolbar .dbg-step").click();
    await until(page, log, l => l.length > atBreakpoint.length);
    await until(page, stopped, s => s);
    await page.waitForTimeout(1500);
    check(same(await log(), [...DEBUG_MOVES, "say done 3: true"]), "step",
        `one step logged ${JSON.stringify((await log()).slice(atBreakpoint.length))}, expected the say only`);
    await resume();
    const end = await until(page, log, l => l.length >= DEBUG_LOG.length);
    check(same(end, DEBUG_LOG), "continue", `continue logged ${JSON.stringify(end)}, expected ${JSON.stringify(DEBUG_LOG)}`);

    step("debug mode: Exit Debug Mode and /robot stop at a breakpoint");
    await exit();
    // Leaving debug mode clears the Blocks breakpoints (as pxt does), so each session sets its own.
    await enter();
    await sayBreakpoint();
    await resume();
    await until(page, log, l => l.length >= DEBUG_MOVES.length);
    await until(page, stopped, s => s);
    sent.length = 0;
    await exit();
    await page.waitForTimeout(1500);
    check(sent.some(m => m.t === "control" && m.op === "stop") && !sent.some(m => m.t === "cmd" || m.op === "start"), "stop",
        `Exit Debug Mode at a breakpoint sent ${JSON.stringify(sent.map(m => m.op ?? m.t))}, expected stop and nothing else`);
    check(same(await log(), DEBUG_MOVES), "stop", `lines after Exit Debug Mode: ${(await log()).slice(DEBUG_MOVES.length).join(" | ")}`);
    check(await page.locator("button.play-button:not(.disabled)").first().isVisible(), "stop", "Run is not enabled after Exit Debug Mode");
    // A second editor session takes the robot over: the relay sends the same control stop as /robot stop.
    await enter();
    await sayBreakpoint();
    await resume();
    await until(page, log, l => l.length >= DEBUG_MOVES.length);
    await until(page, stopped, s => s);
    await page.evaluate(() => new Promise(done => {
        const ws = new WebSocket(`ws://${location.host}/relay`);
        ws.onopen = () => ws.send(JSON.stringify({ t: "hello", token: localStorage.getItem("codeling/local_token"), client: "suite", v: 2 }));
        ws.onmessage = () => { ws.close(); done(); };
    }));
    const dialog = page.locator(".ui.modal:visible, .common-modal:visible").first();
    await dialog.waitFor();
    check(/Program stopped/.test(await dialog.innerText()), "robot stop", `dialog at a breakpoint: ${(await dialog.innerText()).replace(/\s+/g, " ")}`);
    await dialog.locator("button:has-text('Close'), .closeIcon").first().click();
    await resume();
    await page.waitForTimeout(1500);
    check(same(await log(), [...DEBUG_MOVES, "Program stopped: Another editor session took over the robot."]), "robot stop",
        `after the stop and Continue: ${JSON.stringify((await log()).slice(DEBUG_MOVES.length))}`);
    await exit();

    // Python and JavaScript: a breakpoint in the margin on the say line, the variables, then Exit Debug Mode.
    for (const lang of ["py", "ts"]) {
        step(`debug mode in ${lang === "py" ? "Python" : "JavaScript"}: breakpoint and variables`);
        if (lang === "py") await editor.toPython(page); else await editor.toJavaScript(page);
        await enter();
        await lineBreakpoint("agent.say");
        await resume();
        await until(page, log, l => l.length >= DEBUG_MOVES.length);
        await until(page, stopped, s => s);
        await page.waitForTimeout(1500);
        check(same(await log(), DEBUG_MOVES), `breakpoint ${lang}`, `stopped before say with ${JSON.stringify(await log())}`);
        check(/\bcount: 3\b/.test(await variables()), `variables ${lang}`, `the Variables panel shows "${await variables()}"`);
        check(await page.locator(".monaco-editor:visible .highlight-statement").count() > 0, `breakpoint ${lang}`, "no highlighted statement");
        await exit();
    }

    step("slow motion: highlights each block and runs to the end");
    await editor.toBlocks(page);
    // New blocks from the source, so no breakpoint is left on them.
    await editor.toJavaScript(page);
    await editor.setSource(page, "ts", DEBUG);
    await editor.toBlocks(page);
    await enter();
    await page.locator(".debugtoolbar .dbg-trace").click();
    const seen = [];
    const slow = await until(page, async () => {
        const now = await page.evaluate(() => [...document.querySelectorAll(".blocklyPath.pxtRendererHighlight")]
            .map(p => p.closest("g.blocklyBlock").getAttribute("class").split(" ")[0]).join());
        if (now && seen[seen.length - 1] !== now) seen.push(now);
        return log();
    }, l => l.length >= DEBUG_LOG.length, 30000);
    check(same(slow, DEBUG_LOG), "slow motion", `logged ${JSON.stringify(slow)}, expected ${JSON.stringify(DEBUG_LOG)}`);
    check(["variables_change", "agent_move", "agent_say", "agent_turn"].every(t => seen.includes(t)), "slow motion",
        `highlighted ${seen.join(" > ")}`);
    // Slow motion off again restarts the program in debug mode, where it stops before its first statement.
    await page.locator(".debugtoolbar .dbg-trace").click();
    await until(page, stopped, s => s);
    await exit();

    // Issue #221: in debug mode pxt marks the block a failed assert came from, next to the dialog with its message.
    step("debug mode: a failed assert marks its block");
    await editor.toJavaScript(page);
    await editor.setSource(page, "ts", `agent.move(SixDirection.Forward, 1)
control.assert(1 + 1 == 3, "one and one is not three")
agent.turn(TurnDirection.Left)
`);
    await editor.toBlocks(page);
    await enter();
    // Continue clicked at once after the stop on start left the program waiting after its first command here.
    await page.waitForTimeout(2000);
    await resume();
    const failed = page.locator(".ui.modal:visible, .common-modal:visible").first();
    await failed.waitFor();
    check(/Program error\s+one and one is not three/.test(await failed.innerText()), "debug assert", `dialog: ${(await failed.innerText()).replace(/\s+/g, " ")}`);
    await failed.locator("button:has-text('Close'), .closeIcon").first().click();
    const marked = await page.evaluate(() => [...document.querySelectorAll(".blocklyPath.pxtRendererHighlight")]
        .map(p => p.closest("g.blocklyBlock").getAttribute("class").split(" ")[0]));
    check(same(marked, ["control_assert"]), "debug assert", `highlighted blocks ${JSON.stringify(marked)}, expected the check block`);
    check(same(await log(), [...DEBUG_START, "move 0 1: done", `Program error: one and one is not three${DEBUG_HINT}`]), "debug assert",
        `logged ${JSON.stringify(await log())}`);
    await exit();
}

// Projects saved before commit d506004 have the Block dropdown as a field where the item slot is now an input.
// Each fixture is the main.blocks (and main.ts) such a project saved. It is packed into a project file as editor
// version 0.0.1 saved it (every version before the fix), so loading it runs pxt's upgrade with our blocklyPatch.
const SAVED_BY = "0.0.1";

function projectFile(name, blocks, ts) {
    const config = { name, dependencies: { core: "*" }, description: "", files: ["main.blocks", "main.ts", "README.md"] };
    const files = { "main.blocks": blocks, "main.ts": ts, "README.md": "", "pxt.json": JSON.stringify(config) };
    // pxt reads a project file that starts with "{" as plain JSON (no LZMA); cloudId names our target.
    return JSON.stringify({ meta: { cloudId: "pxt/codeling", targetVersions: { target: SAVED_BY }, editor: "blocksprj", name },
        source: JSON.stringify(files) });
}

async function fixturesSection(page) {
    const dir = join(here, "fixtures");
    for (const file of readdirSync(dir).filter(f => f.endsWith(".blocks"))) {
        step(`fixture: ${file}`);
        const before = failures.length;
        const expected = readFileSync(join(dir, file.replace(/[.]blocks$/, ".ts")), "utf8");
        const name = file.replace(/[.]blocks$/, "");
        await editor.dropFile(page, `${name}.mkcd`, projectFile(name, readFileSync(join(dir, file), "utf8"), expected));
        const grey = await editor.greyBlocks(page);
        check(grey.length === 0, file, `grey blocks: ${JSON.stringify(grey)}`);
        const js = await editor.toJavaScript(page);
        check(js.trim() === expected.trim(), file, changedLines(expected, js));
        if (failures.length === before) counts.fixtures++;
    }
}

// Issue #216: the home screen's Examples gallery has one card per tested program (EXAMPLES reads its programs from the
// gallery's pages), each card opens a project with that program, the Norwegian cards show with ?lang=nb, and no
// request leaves the relay. Each language gets its own context: pxt keeps the chosen language in the browser.
async function gallerySection(browser, url) {
    const cards = galleryCards();
    const nbCards = galleryCards("_locales/nb/examples.md");
    const pages = EXAMPLES.map(e => `/examples/${e.page}`);
    check(JSON.stringify(cards.map(c => c.url)) === JSON.stringify(pages), "gallery",
        `docs/examples.md cards ${JSON.stringify(cards.map(c => c.url))}, tested examples ${JSON.stringify(pages)}`);
    check(JSON.stringify(nbCards.map(c => c.url)) === JSON.stringify(pages), "gallery nb",
        `docs/_locales/nb/examples.md cards ${JSON.stringify(nbCards.map(c => c.url))}, tested examples ${JSON.stringify(pages)}`);
    const origin = new URL(url).origin;
    const foreign = [];
    const home = async (lang, shot) => {
        const context = await browser.newContext({ locale: "en-US", viewport: { width: 1280, height: 800 } });
        context.on("request", r => { if (!/^(data|blob):/.test(r.url()) && new URL(r.url()).origin !== origin) foreign.push(r.url()); });
        const page = await context.newPage();
        page.setDefaultTimeout(60000);
        await page.goto(lang ? url.replace("/#", `/?lang=${lang}#`) : url);
        const shown = page.locator(".gallerysegment:not(.mystuff-segment) .ui.card");
        await shown.nth(cards.length - 1).waitFor();
        await page.waitForTimeout(1000);
        await page.screenshot({ path: join(shots, shot) });
        return { context, page, titles: (await shown.locator(".header").allInnerTexts()).map(t => t.trim()) };
    };

    step("examples gallery: one card per tested program, each opens its program");
    const en = await home(null, "gallery.png");
    check(JSON.stringify(en.titles) === JSON.stringify(cards.map(c => c.name)), "gallery",
        `cards on the home screen ${JSON.stringify(en.titles)}, expected ${JSON.stringify(cards.map(c => c.name))}`);
    for (const [i, example] of EXAMPLES.entries()) {
        const page = en.page;
        if (i > 0) {
            await page.goto("about:blank");
            await page.goto(url);
        }
        await page.locator(`.gallerysegment:not(.mystuff-segment) .ui.card[aria-label="${cards[i].name}"]`).click();
        await page.locator("button:visible:has-text('Open Example')").first().click();
        await page.locator(".blocks-menuitem.selected").first().waitFor();
        const js = await editor.toJavaScript(page);
        check(js.trim() === example.js.trim(), `gallery ${example.page}`, changedLines(example.js, js));
    }
    await en.context.close();

    step("examples gallery in Norwegian");
    const nb = await home("nb", "gallery-nb.png");
    check(JSON.stringify(nb.titles) === JSON.stringify(nbCards.map(c => c.name)), "gallery nb",
        `cards with ?lang=nb ${JSON.stringify(nb.titles)}, expected ${JSON.stringify(nbCards.map(c => c.name))}`);
    await nb.context.close();
    check(foreign.length === 0, "gallery", `requests to other hosts: ${[...new Set(foreign)].join(", ")}`);
}

// Issue #16 and commit f0cdd02: our name once in the header, pxt (not Microsoft MakeCode) in About, no README panel.
async function brandingSection(page) {
    await page.goto("about:blank");
    await page.goto(page.__editorUrl);
    await editor.newProject(page);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: join(shots, "new-project.png") });
    await page.locator("#mainmenu").screenshot({ path: join(shots, "header.png") });
    const names = await page.evaluate(() => [...document.querySelectorAll("#mainmenu *")]
        .filter(e => e.offsetParent !== null && [...e.childNodes].some(n => n.nodeType === 3 && /\S/.test(n.textContent)))
        .map(e => e.textContent.trim()));
    const header = names.join(" | ");
    check(names.filter(n => /codeling/i.test(n)).length === 1, "header", `"Codeling" should show once, header texts: ${header}`);
    check(!/makecode|microsoft/i.test(header), "header", `header mentions MakeCode or Microsoft: ${header}`);
    const sideDocs = await page.locator("#sidedocs:visible, #sidedocsframe:visible, #sidedocsbar:visible").count();
    check(sideDocs === 0, "new project", "the README side panel is open");

    await page.locator("#settingsmenu, .settings-menuitem, [aria-label*=Settings]").first().click();
    await page.locator("[role=menuitem]:visible:has-text('About')").first().click();
    const about = page.locator(".ui.modal:visible, .common-modal:visible").first();
    await about.waitFor();
    await page.waitForTimeout(500);
    await about.screenshot({ path: join(shots, "about.png") });
    const text = await about.innerText();
    check(/pxt editor/.test(text), "about box", `no "pxt editor" in: ${text.replace(/\s+/g, " ")}`);
    check(!/microsoft makecode/i.test(text), "about box", `says Microsoft MakeCode: ${text.replace(/\s+/g, " ")}`);
}

async function main() {
    mkdirSync(shots, { recursive: true });
    step(`starting the standalone relay on port ${port}`);
    const relay = await startRelay(port);
    let browser;
    try {
        browser = await chromium.launch({ headless: true });
        // en-US so selectors and texts do not follow the OS language.
        const page = await (await browser.newContext({ locale: "en-US", viewport: { width: 1280, height: 800 } })).newPage();
        page.setDefaultTimeout(60000);
        page.__editorUrl = relay.url;
        // Issue #191: open the launcher file as /codeling does; it must land on the editor with the token stored.
        step("open the editor through the launcher file");
        await page.goto(pathToFileURL(relay.launcher).href);
        await page.waitForURL(u => u.origin === new URL(relay.url).origin);
        const stored = await page.waitForFunction(() => localStorage.getItem("codeling/local_token")).then(h => h.jsonValue());
        check(relay.url.endsWith("#local_token=" + stored), "launcher", "the editor did not store this run's token");
        step("new project");
        await editor.newProject(page);
        step("round trip of every block");
        await blocksSection(page);
        await examplesSection(page);
        await timingSection(page);
        await positionMathSection(page);
        await errorsSection(page);
        await debugSection(page);
        await fixturesSection(page);
        await gallerySection(browser, relay.url);
        step("header, About box, side panel");
        await brandingSection(page);
    } catch (e) {
        fail("suite", String(e?.message ?? e).split("\n")[0].replace(URL_PATTERN, "<editor url>"));
    } finally {
        if (browser) await browser.close().catch(() => {});
        relay.stop();
    }
    console.log(`editor suite: ${counts.blocks} blocks, ${counts.programs} example programs, ${counts.fixtures} fixtures, `
        + `${counts.checks} checks, ${failures.length} failed`);
    process.exit(failures.length ? 1 : 0);
}

await main();
