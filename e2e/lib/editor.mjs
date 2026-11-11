// Page helpers for the editor suite: switch between Blocks, JavaScript and Python the way a player does (the editor
// toggle in the header) and read what the editor shows.

const MAIN = { ts: "main.ts", py: "main.py" };

export async function newProject(page) {
    await page.locator(".newprojectcard").first().click();
    await page.locator(".blocks-menuitem.selected").first().waitFor();
}

// Text of one file as the Monaco editor holds it ("" when that model does not exist yet).
export function source(page, lang) {
    return page.evaluate(name => window.monaco?.editor?.getModels().find(m => m.uri.path.endsWith(name))?.getValue() ?? "", MAIN[lang]);
}

export function setSource(page, lang, text) {
    return page.evaluate(([name, value]) => window.monaco.editor.getModels().find(m => m.uri.path.endsWith(name)).setValue(value), [MAIN[lang], text]);
}

// Waits until a value read by poll() has been the same (and accepted by ok) for 600 ms.
async function settle(page, poll, ok, what) {
    let last;
    let since = Date.now();
    const deadline = Date.now() + 60000;
    while (Date.now() < deadline) {
        const value = await poll();
        if (JSON.stringify(value) !== JSON.stringify(last)) {
            last = value;
            since = Date.now();
        } else if (ok(value) && Date.now() - since >= 600) {
            return value;
        }
        await page.waitForTimeout(100);
    }
    throw new Error(`${what} did not settle within 60 s`);
}

// Fails when pxt shows a dialog (for example "cannot convert"); its text goes in the error.
async function noDialog(page, step) {
    const dialog = page.locator(".ui.modal:visible, .common-modal:visible").first();
    if (await dialog.count()) throw new Error(`${step}: the editor showed a dialog: ${(await dialog.innerText()).replace(/\s+/g, " ").trim()}`);
}

// The header toggle shows Blocks and the last text language; the other one is in the dropdown next to it.
async function pick(page, item) {
    const direct = page.locator(`#editortoggle > .${item}`);
    if (await direct.count()) {
        await direct.first().click();
    } else {
        await page.locator("#editordropdown").click();
        await page.locator(`#editordropdown .${item}`).first().click();
    }
    await page.locator(`#editortoggle > .${item}.selected`).first().waitFor();
}

export async function toBlocks(page) {
    await pick(page, "blocks-menuitem");
    await settle(page, () => blockTypes(page), types => types.length > 0, "Blocks workspace");
    await noDialog(page, "to Blocks");
}

export async function toJavaScript(page, allowEmpty = false) {
    await pick(page, "javascript-menuitem");
    const text = await settle(page, () => source(page, "ts"), t => allowEmpty || t.trim().length > 0, "JavaScript");
    await noDialog(page, "to JavaScript");
    return text;
}

export async function toPython(page) {
    await pick(page, "python-menuitem");
    const text = await settle(page, () => source(page, "py"), t => t.trim().length > 0, "Python");
    await noDialog(page, "to Python");
    return text;
}

// The type of every block on the workspace: Blockly puts it first in the class list of the block's SVG group.
export function blockTypes(page) {
    return page.evaluate(() => [...document.querySelectorAll(".blocklyWorkspace g.blocklyBlock[data-id]")]
        .map(g => g.getAttribute("class").split(" ")[0]));
}

// Grey blocks: code pxt could not turn into a block shows as a typescript_statement or typescript_expression block.
export function greyBlocks(page) {
    return page.evaluate(() => [...document.querySelectorAll(".blocklyWorkspace g.blocklyBlock[data-id]")]
        .filter(g => /^typescript_(statement|expression)\b/.test(g.getAttribute("class")))
        // Only the block's own texts, not those of the blocks attached below it.
        .map(g => [...g.querySelectorAll("text")].filter(t => t.closest("g.blocklyBlock") === g)
            .map(t => t.textContent).join(" ").replace(/\s+/g, " ").trim()));
}

// Loads a saved project file (.mkcd) the way a player does by dropping it on the editor (pxt's own import path).
export async function dropFile(page, name, xml) {
    await page.evaluate(([fileName, text]) => {
        const data = new DataTransfer();
        data.items.add(new File([text], fileName, { type: "application/octet-stream" }));
        for (const type of ["dragenter", "dragover", "drop"]) {
            document.body.dispatchEvent(new DragEvent(type, { bubbles: true, cancelable: true, dataTransfer: data }));
        }
    }, [name, xml]);
    await page.locator(".blocks-menuitem.selected").first().waitFor();
    await settle(page, () => blockTypes(page), types => types.length > 1, "imported Blocks");
}

// The simulator's robot log, one entry per line; without `times` the lines lose their "12:34:56.789 " time.
export async function robotLog(page, times = false) {
    const sim = page.frames().find(f => f.url().includes("simulator"));
    const text = sim ? await sim.evaluate(() => document.getElementById("robotlog")?.innerText ?? "").catch(() => "") : "";
    return text.split("\n").map(l => (times ? l : l.replace(/^\d\d:\d\d:\d\d\.\d{3} /, "")).trim()).filter(Boolean);
}

// Empties the robot log, so lines from the previous run cannot count, and presses Run.
export async function start(page) {
    const sim = page.frames().find(f => f.url().includes("simulator"));
    if (sim) await sim.evaluate(() => { const log = document.getElementById("robotlog"); if (log) log.innerHTML = ""; }).catch(() => {});
    await page.locator("button.play-button").first().click();
}

// Presses Run and waits until the simulator's robot log has `count` lines and no more come for 1 s; returns the lines
// without their times.
export async function run(page, count, timeout = 60000) {
    await start(page);
    const deadline = Date.now() + timeout;
    let lines = [];
    while (Date.now() < deadline) {
        lines = await robotLog(page);
        if (lines.length >= count) {
            await page.waitForTimeout(1000);
            return robotLog(page);
        }
        await page.waitForTimeout(200);
    }
    throw new Error(`robot log has ${lines.length} of ${count} lines after ${timeout} ms: ${lines.join(" | ")}`);
}

export async function stop(page) {
    const button = page.locator("button.stop-button:visible, button.play-button.stop:visible").first();
    if (await button.count()) await button.click().catch(() => {});
}
