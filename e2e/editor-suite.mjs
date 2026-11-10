// Editor test suite (issue #136): drives the Codeling editor in headless Chromium against the standalone relay.
// Run it with ./gradlew :fabric:26.3:editorTest (not part of `build`). It checks:
//   1. every toolbox block from target/libs/core: JavaScript to Blocks to Python to Blocks to JavaScript, no grey
//      blocks, no changed or lost arguments, and a test program for every block (programs/blocks.mjs);
//   2. small MakeCode-style example programs (programs/examples/) round-trip the same way and run unchanged in the
//      simulator against the relay's fake answers, with the robot log they must produce;
//   3. saved projects from before commit d506004 (fixtures/) load with their Block values intact;
//   4. branding: the header names Codeling once, the About box says pxt editor and not Microsoft MakeCode, and a new
//      project opens without the README side panel. Screenshots go to CODELING_SHOTS (default fabric/versions/26.3/build/editor-test).
// The relay token stays in this process (lib/relay.mjs). Output: progress lines, then one summary line.
import { chromium } from "playwright-core";
import { mkdirSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { startRelay, URL_PATTERN } from "./lib/relay.mjs";
import * as editor from "./lib/editor.mjs";
import { BLOCK_PROGRAMS } from "./programs/blocks.mjs";
import { EXAMPLES } from "./programs/examples.mjs";

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
        await page.goto(relay.url);
        step("new project");
        await editor.newProject(page);
        step("round trip of every block");
        await blocksSection(page);
        await examplesSection(page);
        await fixturesSection(page);
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
