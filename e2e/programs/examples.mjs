// Small example programs in the style of MakeCode for Minecraft lessons (issue #31), written for this repo. Each one
// must round-trip through Blocks and Python unchanged and run in the simulator. `log` is the robot log the run must
// show against the standalone relay, whose fake answers are fixed (true for most calls, [0, 0, 0] for positions).
// The log prints enums and items as their numbers: SixDirection Forward 0, Back 1, Left 2, Right 3, Up 4, Down 5.
const START = ["connected as standalone", "run 0 started"];

export const EXAMPLES = [
    {
        name: "stone path (place on move)",
        js: `agent.setItem(Block.Cobblestone, 64, 1)
agent.setSlot(1)
agent.setAssist(AgentAssist.PlaceOnMove, true)
for (let index = 0; index < 3; index++) {
    agent.move(SixDirection.Forward, 2)
    agent.turn(TurnDirection.Left)
}
agent.setAssist(AgentAssist.PlaceOnMove, false)
`,
        log: [...START, "setItem 4 64 1: true", "setSlot 1: true", "setAssist 0 true: true",
            "move 0 2: done", "turn 2: done", "move 0 2: done", "turn 2: done", "move 0 2: done", "turn 2: done",
            "setAssist 0 false: true"],
    },
    {
        name: "staircase",
        js: `agent.setItem(Block.OakPlanks, 64, 1)
for (let index = 0; index < 3; index++) {
    agent.move(SixDirection.Up, 1)
    agent.move(SixDirection.Forward, 1)
    agent.place(SixDirection.Down)
}
`,
        log: [...START, "setItem 5 64 1: true",
            "move 4 1: done", "move 0 1: done", "place 5: true",
            "move 4 1: done", "move 0 1: done", "place 5: true",
            "move 4 1: done", "move 0 1: done", "place 5: true"],
    },
    {
        name: "tunnel with a function",
        js: `function digStep () {
    agent.destroy(SixDirection.Forward)
    agent.destroy(SixDirection.Up)
    agent.move(SixDirection.Forward, 1)
}
let steps = 0
while (steps < 2) {
    if (agent.detect(AgentDetection.Block, SixDirection.Forward)) {
        digStep()
    } else {
        agent.move(SixDirection.Forward, 1)
    }
    steps += 1
}
agent.say("Tunnel done")
`,
        log: [...START, "detect 0 0: true", "destroy 0: true", "destroy 4: true", "move 0 1: done",
            "detect 0 0: true", "destroy 0: true", "destroy 4: true", "move 0 1: done", "say Tunnel done: true"],
    },
    {
        name: "pillar at a position",
        js: `let start = agent.getPosition()
console.log(start.toString())
if (agent.moveTo(world(0, 64, 3))) {
    agent.setItem(Block.Stone, 64, 1)
    for (let index = 0; index < 2; index++) {
        agent.place(SixDirection.Down)
        agent.move(SixDirection.Up, 1)
    }
}
`,
        log: [...START, "getPosition : 0,0,0", "say: 0 0 0", "moveTo 0 64 3: true", "setItem 1 64 1: true",
            "place 5: true", "move 4 1: done", "place 5: true", "move 4 1: done"],
    },
    {
        // The builder moves in the editor: it starts at your feet (the fake relay says 0 0 0) facing south, and only
        // what it builds goes to the game, with world coordinates worked out from its moves. Building answers 0 here.
        name: "builder room and fence",
        js: `builder.mark()
builder.move(SixDirection.Forward, 4)
builder.turn(TurnDirection.Left)
builder.move(SixDirection.Forward, 2)
builder.move(SixDirection.Up, 3)
builder.fill(Block.Stone, FillOperation.Hollow)
builder.teleportTo(world(10, 64, 10))
builder.place(Block.Glass)
builder.face(CompassDirection.North)
builder.move(SixDirection.Forward, 1)
builder.mark()
builder.move(SixDirection.Right, 2)
builder.line(Block.Glass)
console.log(builder.position().toString())
`,
        log: [...START, "positions.toWorld 1 0 0 0: 0,0,0", "blocks.fill 1 0 0 0 2 3 4 1: 0",
            "blocks.fill 20 10 64 10 10 64 10 0: 0", "shapes.line 20 10 64 9 12 64 9: 0", "say: 12 64 9"],
    },
    {
        // Issue #141: a named robot in its own run in parallel; its lines in the log start with its name.
        name: "second robot in parallel",
        js: `let miner = robots.get("Miner")
control.runInParallel(function () {
    miner.move(SixDirection.Forward, 2)
    miner.say("Done")
})
`,
        log: [...START, "Miner: move 0 2: done", "Miner: say Done: true"],
    },
    {
        // Issue #215: a guard walks back and forth on its own, half a second apart. Two rounds keep the log fixed;
        // the loop goes on (waiting) until the suite presses Stop.
        name: "guard patrol (forever and pause)",
        js: `let rounds = 0
loops.forever(function () {
    if (rounds < 2) {
        agent.move(SixDirection.Forward, 3)
        agent.turn(TurnDirection.Left)
        agent.turn(TurnDirection.Left)
        rounds += 1
    }
    loops.pause(500)
})
`,
        log: [...START, "move 0 3: done", "turn 2: done", "turn 2: done", "move 0 3: done", "turn 2: done", "turn 2: done"],
    },
];
