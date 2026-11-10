/// <reference path="../node_modules/pxt-core/built/pxtsim.d.ts"/>

namespace pxsim {
    initCurrentRuntime = () => {
        runtime.board = new Board();
    };

    export function board(): Board {
        return runtime.board as Board;
    }

    /** Program state. One per run. */
    export class Board extends pxsim.BaseBoard {
        public relay = new Relay();
        /** The builder (issue #90): where it stands (null until first used), its CompassDirection, its marks. */
        public builder: { at: Position, facing: number, marks: Position[] } = { at: null, facing: 2, marks: [] };
        /**
         * The robot the next command is for (issue #141): a Robot method sets it, calls the agent function and puts it
         * back at once. Commands read it before their first await, so robots in parallel never mix (see Robot.on).
         */
        public robot = "";

        initAsync(msg: pxsim.SimulatorRunMessage): Promise<void> {
            document.getElementById("robotlog").innerHTML = "";
            return Promise.resolve();
        }

        kill() {
            super.kill();
            this.relay.close();
        }

        log(text: string) {
            const line = document.createElement("div");
            line.textContent = `${new Date().toISOString().substring(11, 23)} ${text}`;
            document.getElementById("robotlog").appendChild(line);
        }
    }

    const TARGET_ID = "codeling";
    // Version 2 (issue #141): cmd may name one of the player's robots in "robot".
    const PROTOCOL = 2;
    // shortcut: 3 s keeps well inside the server's 10 s heartbeat timeout; read limits.heartbeatTimeoutMs if it ever changes.
    const HEARTBEAT_MS = 3000;
    const REOPEN = "Open the editor from the game again with /codeling.";

    /**
     * WebSocket link to the relay in the client mod (protocol v2, architecture section 3). The sim frame shares
     * the editor's origin, so it reads the token pxt stored from #local_token and connects to its own host.
     * One socket per run: hello, welcome, control start (the server returns the run id), then the cmds, answered by
     * id (robots in parallel each have their own command waiting), with a heartbeat every few seconds. On any error
     * the program stays blocked and a message explains why.
     * shortcut: no reconnect; a dropped socket ends the run, the player presses Run again.
     */
    export class Relay {
        private ws: WebSocket;
        private ready: Promise<void>;
        private run = -1;
        private nextId = 1;
        private pending: { [id: number]: (msg: any) => void } = {};
        private heartbeat: number;
        private welcomed = false;
        private done = false;

        private connect(): Promise<void> {
            if (this.ready) return this.ready;
            const token = window.localStorage.getItem(`${TARGET_ID}/local_token`) || "";
            // Never rejects: on failure the program stays blocked on its await and fail() tells the player why.
            this.ready = new Promise<void>(resolve => {
                const ws = new WebSocket(`ws://${window.location.host}/relay`);
                this.ws = ws;
                ws.onopen = () => this.send({ t: "hello", token, client: "0.0.1" });
                ws.onerror = () => this.fail("Cannot reach the game", "Is Minecraft running with Codeling? " + REOPEN);
                ws.onclose = () => this.fail("Connection to the game closed", REOPEN);
                ws.onmessage = ev => {
                    const msg = JSON.parse(ev.data as string);
                    if (msg.t === "welcome") {
                        this.welcomed = true;
                        board().log(`connected as ${msg.player}`);
                        this.send({ t: "control", op: "start" });
                    } else if (msg.t === "control" && msg.op === "start") {
                        this.run = msg.run;
                        this.heartbeat = window.setInterval(() => this.send({ t: "control", op: "heartbeat", run: this.run }), HEARTBEAT_MS);
                        board().log(`run ${this.run} started`);
                        resolve();
                    } else if (msg.t === "control" && msg.op === "notice") {
                        // Issue #192: the game is paused (its commands wait) or runs again; the program goes on.
                        board().log(msg.message);
                    } else if (msg.t === "control" && msg.op === "stop") {
                        this.fail("Program stopped", msg.message || "The game stopped the robot.");
                    } else if (msg.t === "error") {
                        if (!this.welcomed && msg.code === "NO_PERMISSION") this.fail("Editor link expired", "The game was restarted since this tab was opened. " + REOPEN);
                        else if (msg.code === "STOPPED") this.fail("Program stopped", "The robot was stopped in the game (" + msg.message + ").");
                        // Before a run exists the relay could not reach the game: its first sentence is the header ("Not in a world").
                        else if (this.run < 0) this.fail(msg.message.split(".")[0], msg.message);
                        else this.fail("Robot error", `${msg.code}: ${msg.message}`);
                    } else if (msg.t === "result" && this.pending[msg.id]) {
                        const done = this.pending[msg.id];
                        delete this.pending[msg.id];
                        done(msg);
                    }
                };
            });
            return this.ready;
        }

        /**
         * Sends one command and resolves with the server's result message once the robot has finished it. The robot
         * is the board's current one, read now (before the first await); "" is the default robot.
         */
        async command(op: string, args: any[], robot = board().robot): Promise<any> {
            await this.connect();
            const id = this.nextId++;
            return new Promise<any>(resolve => {
                this.pending[id] = resolve;
                this.send(robot ? { t: "cmd", id, run: this.run, op, args, robot } : { t: "cmd", id, run: this.run, op, args });
            });
        }

        /** Program stopped in the editor: end the run on the server. */
        close() {
            if (this.run >= 0 && !this.done) this.send({ t: "control", op: "stop", run: this.run });
            this.shutdown();
        }

        private send(msg: any) {
            msg.v = PROTOCOL;
            if (this.ws && this.ws.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify(msg));
        }

        private fail(header: string, body: string) {
            if (this.done) return;
            board().log(`${header}: ${body}`);
            this.shutdown();
            Runtime.postMessage(<SimulatorCommandMessage>{ type: "simulator", command: "modal", header, body });
        }

        private shutdown() {
            this.done = true;
            if (this.heartbeat) window.clearInterval(this.heartbeat);
            if (this.ws) this.ws.close();
        }
    }
}
