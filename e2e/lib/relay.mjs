// Starts the standalone relay (codeling.relay.StandaloneRelay, no Minecraft) for the editor suite and hands back the
// editor URL. The URL holds the session token: it is read from the relay's stdout inside this process and never
// printed, logged or written anywhere. `./gradlew :fabric:editorTest` sets CODELING_JAVA and CODELING_RELAY_CLASSPATH.
import { spawn } from "node:child_process";
import { createInterface } from "node:readline";

export const URL_PATTERN = /http:\/\/127\.0\.0\.1:\d+\/#local_token=[A-Za-z0-9_-]+/;

export async function startRelay(port) {
    const java = process.env.CODELING_JAVA;
    const classpath = process.env.CODELING_RELAY_CLASSPATH;
    if (!java || !classpath) throw new Error("CODELING_JAVA or CODELING_RELAY_CLASSPATH missing: run ./gradlew :fabric:editorTest");
    const child = spawn(java, ["-cp", classpath, "codeling.relay.StandaloneRelay", String(port), "--print-url"],
        { stdio: ["ignore", "pipe", "pipe"] });
    // The relay logs every message it relays (never the token); keep the tail for a failure report only.
    const tail = [];
    createInterface({ input: child.stderr }).on("line", line => { tail.push(line); if (tail.length > 40) tail.shift(); });
    const url = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error(`relay did not start on port ${port} within 60 s`)), 60000);
        child.once("exit", code => { clearTimeout(timer); reject(new Error(`relay exited with ${code}: ${tail.join(" | ")}`)); });
        createInterface({ input: child.stdout }).on("line", line => {
            const match = line.match(URL_PATTERN);
            if (match) { clearTimeout(timer); resolve(match[0]); }
        });
    });
    return { url, stop: () => child.kill() };
}
