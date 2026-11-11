// Starts the standalone relay (codeling.relay.StandaloneRelay, no Minecraft) for the editor suite and hands back the
// editor URL and the path of the launcher page that /codeling opens (issue #191, the path holds no token). The URL holds
// the session token: it is read from the relay's stdout inside this process and never printed, logged or written
// anywhere. `./gradlew :fabric:editorTest` sets CODELING_JAVA and CODELING_RELAY_CLASSPATH.
import { spawn } from "node:child_process";
import { rmSync } from "node:fs";
import { dirname } from "node:path";
import { createInterface } from "node:readline";

export const URL_PATTERN = /http:\/\/127\.0\.0\.1:\d+\/#local_token=[A-Za-z0-9_-]+/;

export async function startRelay(port) {
    const java = process.env.CODELING_JAVA;
    const classpath = process.env.CODELING_RELAY_CLASSPATH;
    if (!java || !classpath) throw new Error("CODELING_JAVA or CODELING_RELAY_CLASSPATH missing: run ./gradlew :fabric:editorTest");
    const child = spawn(java, ["-cp", classpath, "codeling.relay.StandaloneRelay", String(port), "--print-url", "--print-launcher"],
        { stdio: ["ignore", "pipe", "pipe"] });
    // The relay logs every message it relays (never the token); keep the tail for a failure report only.
    const tail = [];
    createInterface({ input: child.stderr }).on("line", line => { tail.push(line); if (tail.length > 40) tail.shift(); });
    let url;
    const launcher = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error(`relay did not start on port ${port} within 60 s`)), 60000);
        child.once("exit", code => { clearTimeout(timer); reject(new Error(`relay exited with ${code}: ${tail.join(" | ")}`)); });
        createInterface({ input: child.stdout }).on("line", line => {
            url ??= line.match(URL_PATTERN)?.[0];
            const path = line.match(/^EDITOR_LAUNCHER (.+)$/)?.[1];
            if (url && path) { clearTimeout(timer); resolve(path); }
        });
    });
    // kill() skips the relay's shutdown hook on Windows, so the launcher directory is removed here too.
    return { url, launcher, stop: () => { child.kill(); rmSync(dirname(launcher), { recursive: true, force: true }); } };
}
