// Opens the Codeling editor in headless Chromium, puts a JavaScript program in, presses Run and waits until the
// simulator's robot log contains a text. Used by EditorEndToEndClientGameTest (issue #135).
//
// The editor URL (it holds the session token) comes on stdin, never in argv or the environment, and is never printed.
// Settings: CODELING_PROGRAM (JavaScript source), CODELING_EXPECT (text the robot log must contain),
// CODELING_TIMEOUT_MS (default 60000), CODELING_SHOTS (optional folder for a screenshot when it ends).
// Output: one JSON line {"ok": true|false, "log": "...", "error": "..."}; lines starting with "# " are progress.
import { chromium } from "playwright-core";
import { createInterface } from "node:readline";

const URL_PATTERN = /http:\/\/127\.0\.0\.1:\d+\/#local_token=[A-Za-z0-9_-]+/;
const program = process.env.CODELING_PROGRAM ?? "";
const expect = process.env.CODELING_EXPECT ?? "";
const timeout = Number(process.env.CODELING_TIMEOUT_MS ?? 60000);
const shots = process.env.CODELING_SHOTS;

const step = text => console.log(`# ${text}`);

async function readUrl() {
    // The first stdin line holding an editor URL; other lines are ignored.
    for await (const line of createInterface({ input: process.stdin })) {
        const match = line.match(URL_PATTERN);
        if (match) return match[0];
    }
    throw new Error("no editor URL on stdin");
}

async function robotLog(page) {
    const sim = page.frames().find(f => f.url().includes("simulator"));
    if (!sim) return "";
    return sim.evaluate(() => document.getElementById("robotlog")?.innerText ?? "").catch(() => "");
}

let browser;
let page;
let result = { ok: false, log: "" };
try {
    const url = await readUrl();
    browser = await chromium.launch({ headless: true });
    page = await (await browser.newContext({ locale: "en-US", viewport: { width: 1280, height: 800 } })).newPage();
    page.setDefaultTimeout(timeout);
    step("open editor");
    await page.goto(url);

    step("new project");
    await page.locator(".newprojectcard").first().click();

    step("switch to JavaScript");
    await page.locator(".javascript-menuitem:visible").first().click();
    await page.waitForFunction(() => window.monaco?.editor?.getModels().some(m => m.uri.path.endsWith("main.ts")));
    await page.evaluate(code => window.monaco.editor.getModels().find(m => m.uri.path.endsWith("main.ts")).setValue(code), program);

    step("run");
    await page.locator("button.play-button").first().click();

    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
        result.log = await robotLog(page);
        if (expect && result.log.includes(expect)) {
            result.ok = true;
            break;
        }
        await page.waitForTimeout(250);
    }
    if (!result.ok) result.error = `robot log did not show "${expect}" within ${timeout} ms`;
} catch (e) {
    result.error = String(e?.message ?? e).split("\n")[0].replace(URL_PATTERN, "<editor url>");
} finally {
    if (shots && page) await page.screenshot({ path: `${shots}/editor-${Date.now()}.png` }).catch(() => {});
    if (browser) await browser.close().catch(() => {});
}
console.log(JSON.stringify(result));
process.exit(result.ok ? 0 : 1);
