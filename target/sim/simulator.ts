/// <reference path="../node_modules/pxt-core/built/pxtsim.d.ts"/>

namespace pxsim {
    initCurrentRuntime = () => {
        runtime.board = new Board();
        // Issues #218 and #221: an error nobody catches (a robot error, a failed assert, raise or throw) ends the program
        // through pxt, which marks the block or line only in debug mode, so the player always gets the message too. The
        // dialog goes after pxt's own message: before it, pxt no longer marks the block in debug mode.
        runtime.postError = (e: any) => {
            const text = String(e && e.message).replace(/^unhandled exception: /, "");
            const where = "(debug mode, the bug button, shows the block or line it came from)";
            const b = board();
            if (b.relay.told) return;
            if (b.robotError) b.relay.notify("Robot error", b.robotError);
            else b.relay.notify("Program error", `${text} ${where}`);
        };
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
        /** A robot error nobody catches (issue #218), for its dialog. */
        public robotError = "";
        /** player.onChat handlers by keyword and player.onDied handlers (issue #27). */
        // No prototype, so a keyword such as "constructor" finds no inherited member.
        private chat: { [command: string]: RefAction[] } = Object.create(null);
        private died: RefAction[] = [];
        /** Issue #89: blocks.onBlockBroken handlers by block code, agent.onRedstonePowered and gameplay.onDayStart handlers. */
        private broken: { [block: number]: RefAction[] } = {};
        private redstone: RefAction[] = [];
        private dayStart: RefAction[] = [];
        /** Issue #249: signals.onReceived handlers by signal name, no prototype as for chat. */
        private signals: { [name: string]: RefAction[] } = Object.create(null);

        onChat(command: string, handler: RefAction) {
            (this.chat[command] = this.chat[command] || []).push(handler);
            this.subscribe();
        }

        onDied(handler: RefAction) {
            this.died.push(handler);
            this.subscribe();
        }

        onBlockBroken(block: number, handler: RefAction) {
            (this.broken[block] = this.broken[block] || []).push(handler);
            this.subscribe();
        }

        onRedstonePowered(handler: RefAction) {
            this.redstone.push(handler);
            this.subscribe();
        }

        onDayStart(handler: RefAction) {
            this.dayStart.push(handler);
            this.subscribe();
        }

        onSignal(name: string, handler: RefAction) {
            (this.signals[name] = this.signals[name] || []).push(handler);
            this.subscribe();
        }

        // Everything the program listens for; each subscribe replaces the one before.
        private subscribe() {
            this.relay.subscribe({
                chat: Object.keys(this.chat), died: this.died.length > 0, broken: Object.keys(this.broken).map(Number),
                redstone: this.redstone.length > 0, dayStart: this.dayStart.length > 0, signals: Object.keys(this.signals)
            });
        }

        /** An event from the game: only for what this program subscribed to. */
        event(name: string, args: any[]) {
            const run = (handlers: RefAction[]) => (handlers || []).forEach(h => runtime.runFiberAsync(h));
            if (name === "chat") this.runChat(args[0], args.slice(1, 4));
            else if (name === "playerDied") run(this.died);
            else if (name === "blockBroken") run(this.broken[args[0]]);
            else if (name === "redstonePowered") run(this.redstone);
            else if (name === "dayStart") run(this.dayStart);
            else if (name === "signal") (this.signals[args[0]] || []).forEach(h => runtime.runFiberAsync(h, args[1] || 0));
        }

        /** player.runChatCommand: the handlers for the line's first word and for "*", as the game would match it, and nothing sent. */
        runChatCommand(line: string): Promise<void> {
            const words = line.trim().split(/\s+/);
            const command = this.chat[words[0]] ? words[0] : "*";
            // parseInt as MakeCode does; NaN (and -0) become 0.
            return this.runChat(command, [1, 2, 3].map(i => parseInt(words[i]) || 0));
        }

        // The keyword's handlers, and the "*" handlers too unless the keyword is "*", each in its own fiber.
        private runChat(command: string, nums: number[]): Promise<void> {
            const handlers = (this.chat[command] || []).concat(command === "*" ? [] : this.chat["*"] || []);
            return Promise.all(handlers.map(h => runtime.runFiberAsync(h, nums[0], nums[1], nums[2]))).then(() => { });
        }

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
     * A robot command the game refused (issue #218). The program gets the text "CODE: message" as the thrown value.
     * Catchable: an error tied to one command, that is BAD_ARGS, NO_PERMISSION, RATE_LIMITED, PROTECTED, UNLOADED and
     * OUT_OF_RANGE. Not catchable, they end the program as before: STOPPED, VERSION, a control stop (Stop, /robot stop,
     * another editor session) and a lost connection.
     */
    class RobotError {
        constructor(public text: string) {
        }
    }

    // pxt resumes a program that waits on a promise shim with `promise.then(resume)` and never passes a reject handler,
    // so a rejected command would leave the program waiting for good. For a RobotError only, the resume runs a frame
    // (pxt's FnWrapper, as pxtcore.runAction does) that throws the text exactly as the program's own `throw` does: into
    // the nearest catch, or, with none, pxt ends the program (in debug mode it also marks the line or block). await,
    // .catch and Promise.all always pass a reject handler, so only that resume takes this path.
    const then = Promise.prototype.then;
    Promise.prototype.then = function (this: Promise<any>, ok?: any, no?: any) {
        if (no !== undefined || typeof ok !== "function") return then.call(this, ok, no);
        return then.call(this, ok, (e: any) => {
            if (!(e instanceof RobotError)) throw e;
            ok(new FnWrapper((s: StackFrame) => {
                // pxt marks (in debug mode) the statement of the frame that threw: this one stands where the program waited.
                s.lastBrkId = s.parent.lastBrkId;
                // Nobody catches it: pxt ends the program, then postError shows the dialog as before (call logged the command).
                if (!runtime.currTryFrame()) board().robotError = e.text;
                pxtcore.throwValue(e.text);
                return null;
            }, null, []));
        });
    } as any;

    /**
     * WebSocket link to the relay in the client mod (protocol v2, architecture section 3). The sim frame shares
     * the editor's origin, so it reads the token pxt stored from #local_token and connects to its own host.
     * One socket per run: hello, welcome, control start (the server returns the run id), then the cmds, answered by
     * id (robots in parallel each have their own command waiting), with a heartbeat every few seconds. A refused command
     * throws into the program (RobotError); on any other error the program stays blocked and a message explains why.
     * shortcut: no reconnect; a dropped socket ends the run, the player presses Run again.
     */
    export class Relay {
        private ws: WebSocket;
        private ready: Promise<void>;
        private run = -1;
        private nextId = 1;
        private pending: { [id: number]: { ok: (msg: any) => void, no: (e: RobotError) => void } } = {};
        private heartbeat: number;
        private welcomed = false;
        private done = false;
        /** A dialog already told the player why the program ended. */
        public told = false;

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
                        else if (msg.code !== "VERSION" && this.pending[msg.id]) this.answer(msg.id).no(new RobotError(`${msg.code}: ${msg.message}`));
                        // Before a run exists the relay could not reach the game: its first sentence is the header ("Not in a world").
                        else if (this.run < 0) this.fail(msg.message.split(".")[0], msg.message);
                        else this.fail("Robot error", `${msg.code}: ${msg.message}`);
                    } else if (msg.t === "event") {
                        board().event(msg.name, msg.args || []);
                    } else if (msg.t === "result" && this.pending[msg.id]) {
                        this.answer(msg.id).ok(msg);
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
            return new Promise<any>((ok, no) => {
                this.pending[id] = { ok, no };
                this.send(robot ? { t: "cmd", id, run: this.run, op, args, robot } : { t: "cmd", id, run: this.run, op, args });
            });
        }

        /** Tells the game what the program listens for (issues #27, #89, #249); each call replaces the one before. */
        async subscribe(what: { chat: string[], died: boolean, broken: number[], redstone: boolean, dayStart: boolean, signals: string[] }) {
            await this.connect();
            this.send({ t: "control", op: "subscribe", run: this.run, ...what });
        }

        private answer(id: number) {
            const waiting = this.pending[id];
            delete this.pending[id];
            return waiting;
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
            this.shutdown();
            this.notify(header, body);
        }

        /** Tells the player in the robot log and in a dialog. */
        notify(header: string, body: string) {
            this.told = true;
            board().log(`${header}: ${body}`);
            Runtime.postMessage(<SimulatorCommandMessage>{ type: "simulator", command: "modal", header, body });
        }

        private shutdown() {
            this.done = true;
            if (this.heartbeat) window.clearInterval(this.heartbeat);
            if (this.ws) this.ws.close();
        }
    }
}
