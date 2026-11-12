# Codeling player guide

Norsk: [guide-nb.md](guide-nb.md)

Codeling is a mod for Minecraft Java Edition. You get a small robot in your world.
You program it in your web browser with blocks, JavaScript or Python.
Each block you run makes the robot do one thing in the game.

Codeling is not made by Microsoft, Mojang or the MakeCode team. See [Disclaimer](#disclaimer).

## What you need

- Minecraft Java Edition, version 26.3 or 1.21.1, on a computer (Windows, macOS or Linux).
- A mod loader: Fabric Loader 0.20.1 or newer with Fabric API for your Minecraft version, or NeoForge (26.3.0.102 or newer for 26.3, 21.1.265 or newer for 1.21.1).
- The Codeling mod file for your loader and Minecraft version (a `.jar` file). The name says which one it is:

  | Minecraft | Fabric | NeoForge |
  |---|---|---|
  | 26.3 | `codeling-fabric-26.3-...jar` | `codeling-neoforge-26.3-...jar` |
  | 1.21.1 | `codeling-fabric-1.21.1-...jar` | `codeling-neoforge-1.21.1-...jar` |
- A web browser (Chrome, Edge or Firefox).

A parent can do the install. It takes about ten minutes. The steps below say 26.3; on 1.21.1 pick 1.21.1 in the installers and use the 1.21.1 files.

On Minecraft 1.21.1 the editor still lists blocks and items that came later (pale oak, for example). A program that uses one gets the error "minecraft:... is not in this Minecraft version". Everything else works the same.

## Install

1. Close Minecraft.
2. Go to https://fabricmc.net/use/installer/ and download the Fabric installer.
3. Run the installer. Pick "Client", Minecraft version 26.3, and press "Install".
4. Open the Minecraft Launcher once and start the new "fabric-loader-26.3" profile. Close the game again. This creates the `mods` folder.
5. Download Fabric API for 26.3 from https://modrinth.com/mod/fabric-api.
6. Put the Fabric API `.jar` file and the Codeling `.jar` file in the `mods` folder:
   - Windows: press Win+R, type `%appdata%\.minecraft\mods` and press Enter.
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
7. Start Minecraft with the "fabric-loader-26.3" profile.

### Install on NeoForge

Use these steps instead if you play with NeoForge. NeoForge needs no Fabric API.

1. Close Minecraft.
2. Go to https://neoforged.net/ and download the NeoForge installer for Minecraft 26.3.
3. Run the installer. Pick "Install client" and press "Proceed".
4. Open the Minecraft Launcher once and start the new NeoForge profile. Close the game again. This creates the `mods` folder.
5. Put the file `codeling-neoforge-26.3-...jar` in the `mods` folder (the same folders as in step 6 above).
6. Start Minecraft with the NeoForge profile.

## Open the editor

1. Open a world (singleplayer, or a server that has Codeling, see [Multiplayer](#multiplayer)).
2. Press T to open chat. Type `/codeling` and press Enter.
3. Your browser opens the Codeling editor. The link only works on this computer, and only until you close Minecraft.
4. While a program runs, the game keeps going when you click on the browser. If you open the pause menu (Escape), the game pauses and the robot waits: the log under the simulator says "Minecraft is paused. Go back to the game and close the menu." Close the menu and the robot goes on.

## Spawn the robot

Type `/robot spawn` in chat. The robot appears at your feet and looks the same way as you.
You have one robot. Typing `/robot spawn` again moves it to you.
If the world allows more, you can also have robots with names: see [Several robots](#several-robots).

## Your first program

With blocks:

1. In the editor, open the "Agent" category (green).
2. Drag "agent move forward by 1" into the workspace. Change 1 to 3.
3. Drag "agent turn left" under it. Change left to right.
4. Press the Run (play) button under the simulator on the left.
5. Look in the game. The robot moves 3 blocks forward and turns right.

The same program in JavaScript (the JavaScript tab at the top):

```
agent.move(SixDirection.Forward, 3)
agent.turn(TurnDirection.Right)
```

In Python, names use underscores: `agent.move_to(...)`, `agent.find_nearest(...)`.

Each command waits until the robot is done. Then the next one starts.
To stop a program, press the stop button in the editor, or type `/robot stop` in the game.

## Examples

The start page of the editor (the first page it shows) has an "Examples" row under "My Projects": eight small
programs, such as a staircase, a tunnel, a room with a fence, a guard patrol and torches in the dark. Click one to read what it teaches,
then press "Open Example" under Blocks, Python or JavaScript. You get a new project of your own with the program in
it: press Run to try it, and change it as you like. The example itself stays as it was.

## Wait and repeat

"pause (ms)" in the Loops category waits before the next command: 1000 ms is one second. "forever" runs the code
inside it over and over, for as long as the program runs. This robot stands guard and walks 3 blocks forward and back
every two seconds:

```
loops.forever(function () {
    agent.move(SixDirection.Forward, 3)
    agent.turn(TurnDirection.Left)
    agent.turn(TurnDirection.Left)
    loops.pause(2000)
})
```

A pause only holds the code it is in. Code in "run in parallel" and in other "forever" blocks keeps going. A program
with "forever" never ends by itself: press the stop button in the editor, or type `/robot stop` in the game. Both
stop it at once, also in the middle of a pause.

## Step through a program

The bug button under the simulator turns on debug mode. The program starts and stops before its first command.

- Click the circle on a block (or the space left of a line number in JavaScript and Python) to set a breakpoint. The
  program stops there each time it gets to it.
- In Blocks, "Step" runs one block and stops again. In JavaScript and Python, "Step over" runs one line (a function
  call in one step), and "Step into" goes into the function.
- The arrow (Continue) runs on to the next breakpoint, or to the end.
- While the program is stopped, the block or line that comes next is highlighted, and "Variables" shows the value of
  each variable.
- The turtle button (Slow-Mo) runs the program slowly and highlights each block as it runs, without stopping.

A robot command always finishes before the program stops. The robot stands still while the program is stopped, and
so do "forever" and "run in parallel". To stop the program, press "Exit Debug Mode", or type `/robot stop` in the
game. Turning debug mode or Slow-Mo on starts the program from the beginning, so the robot does its commands again
from where it stands.

## Errors, and checking your own program

When the game refuses a robot command (for example a move of more than 64 blocks), the command throws an error into
your program. If nothing catches it, the program stops, the robot log shows the command and the error, and a "Robot
error" box says what went wrong. In debug mode the editor also marks the block or line it came from.

In JavaScript and Python your program can catch the error and go on, for example to go home instead:

```
try {
    agent.move(SixDirection.Forward, 100)
} catch (e) {
    let error: string = e
    console.log("could not: " + error)
    agent.goHome()
}
```

```python
try:
    agent.move(SixDirection.FORWARD, 100)
except:
    print("could not move")
    agent.go_home()
```

In JavaScript the error is a text: the code, a colon and the message, such as
`OUT_OF_RANGE: blocks must be 1 to 64`. Put it in a `string` variable first, as above, to use it as text (for
example `error.indexOf("OUT_OF_RANGE") == 0`). In Python write a plain `except:`; Python in the editor cannot name the
error (`except Exception as e` does not compile), but the robot log shows it. Blocks have no "try" block, so catching
errors is for JavaScript and Python.

The error codes a program can catch:

| Code | Why |
|---|---|
| `BAD_ARGS` | The command or a value is not valid, for example an item that is not in this Minecraft version. |
| `NO_PERMISSION` | You may not do this here, for example "agent give" without creative mode, or the robot camera when the server turned it off. |
| `RATE_LIMITED` | The robot has too many commands waiting. |
| `PROTECTED` | Kept for protected areas. Today the game answers false instead (the action did nothing). |
| `UNLOADED` | The robot or the block is in a part of the world that is not loaded, or the robot is not spawned. |
| `OUT_OF_RANGE` | A number is too big or too small, for example a move of more than 64 blocks or a slot above 27. |

These end the program and cannot be caught: the stop button, `/robot stop`, "Exit Debug Mode", a lost connection to
the game, `STOPPED` (the game stopped the robot) and `VERSION` (the editor is older than the game; reload it).

Your program can also check its own work. "check ... or stop with" in the Logic category stops the program with your
message when the condition is false, and "stop with error" stops it at once. In JavaScript they are
`control.assert(condition, message)` (the block writes it as `control.check`) and `control.fail(message)`, and
`throw "message"` does the same. In Python, `assert condition, "message"` and `raise ValueError("message")` work. The
program stops and a "Program error" box shows the message; debug mode marks the block or line it came from. A
`try`/`catch` (or `try`/`except`) around them catches them like a robot error. The "Room and fence" example checks
where its line of glass ends.

## All robot commands

Directions are relative to the robot: forward, back, left, right, up, down.
Slots are the robot's 27 inventory places, numbered 1 to 27.

### Movement

| Block | JavaScript | What it does |
|---|---|---|
| agent move (direction) by (steps) | `agent.move(SixDirection.Forward, 3)` | Moves 1 to 64 blocks. Stops early at a solid block. |
| agent turn (left/right) | `agent.turn(TurnDirection.Left)` | Turns 90 degrees. |
| agent gravity (on/off) | `agent.setGravity(true)` | On: the robot falls and walks like a player. Off (the start): it flies. |
| agent move to (position) | `agent.moveTo(pos(0, 0, 5))` | Finds a path and walks there. Answers true or false. Opens wooden doors on the way. |
| agent set home | `agent.setHome()` | Remembers where the robot stands as its home. Also `/robot sethome`. |
| agent go home | `agent.goHome()` | Walks home like "move to". False if there is no home or no way. |
| agent go to robot (name) | `agent.goToRobot(robots.get("Miner"))` | Walks like "move to" to a free block next to your robot Miner and turns to face it. Answers true or false. See "Several robots". |
| agent follow path (of block) | `agent.followPath()` | Walks along the path it stands on, or a path of the block you pick (`agent.followPath(Block.Gravel)`), one step up or down too, until the path ends or splits. A robot right next to the path steps onto it; one further away answers end and says "I am not on the path". At most 256 blocks. Answers why it stopped: end, junction, blocked or limit (`PathStop.Junction`). At a junction it stops facing the way it came: turn and follow the path again. |
| agent follow path of (block) keep (left/right) | `agent.followPath(Block.OakPlanks, TurnDirection.Left)` | Keeps one hand on the wall, as you can in a real maze. At every junction it takes the way furthest to the left (or right). At a dead end with a wall in front it turns round and walks back. It stops where the path ends with no wall in front (the way out: end), back where it started (end), or after 256 blocks (limit: follow the path again to go on). Press + twice on the block to choose the side. |

### Building

| Block | JavaScript | What it does |
|---|---|---|
| agent place (direction) | `agent.place(SixDirection.Down)` | Places the block from the selected slot. |
| agent destroy (direction) | `agent.destroy(SixDirection.Forward)` | Breaks the block. The drops go into the robot. |
| agent assist (place on move) (on/off) | `agent.setAssist(AgentAssist.PlaceOnMove, true)` | Places a block behind it on every step. |
| agent interact (direction) | `agent.interact(SixDirection.Forward)` | Opens doors, trapdoors and gates, presses buttons, flips levers. |

### Inspecting

| Block | JavaScript | What it does |
|---|---|---|
| agent detect (block/redstone) (direction) | `agent.detect(AgentDetection.Block, SixDirection.Forward)` | True if a block is in the way. |
| agent inspect (block/data) (direction) | `agent.inspect(AgentInspection.Block, SixDirection.Down)` | The block's number. |
| agent is open (direction) | `agent.isOpen(SixDirection.Forward)` | True if a door, trapdoor, gate or barrel is open. |
| agent detect (hostile mob/animal/player/item) (direction) | `agent.detectEntity(EntityKind.Mob, SixDirection.Forward)` | True if one is in the block next to the robot. |
| agent count nearby (kind) within (radius) | `agent.countNearby(EntityKind.Animal, 4)` | How many are near the robot, at most 8 blocks away. |
| agent redstone power (direction) | `agent.redstonePower(SixDirection.Forward)` | Redstone power, 0 to 15. |
| on agent redstone powered | `agent.onRedstonePowered(function () { })` | Runs its code each time the block your robot stands in gets a redstone signal, for example when you flip a lever next to it. See "React to blocks, redstone and the day" below. |
| agent position | `agent.getPosition()` | Where the robot is. |
| agent orientation | `agent.getOrientation()` | Which way it looks, in degrees. |
| agent find nearest (block) within (radius) | `agent.findNearest(Block.OakLog, 16)` | The nearest block of that kind, or nothing. |
| agent find nearest (category) within (radius) | `agent.findNearestCategory(BlockCategory.Logs, 16)` | Same, for logs, leaves, ores, planks, wool or flowers. |

### Sensors

The radar looks at most 16 blocks around the robot. Invisible players and players in spectator mode are never found.

| Block | JavaScript | What it does |
|---|---|---|
| agent radar (kind) within (radius) | `agent.radar(EntityKind.Mob, 8)` | A list of where they are, nearest first. Empty when there is none. At most the 64 nearest. |
| agent nearest (kind) within (radius) | `agent.nearestEntity(EntityKind.Player, 8)` | Where the nearest one is, or nothing. |
| agent detect liquid (direction) | `agent.detectLiquid(SixDirection.Down)` | Water, lava or none (`Liquid.Lava`). |
| agent is safe (direction) | `agent.isSafe(SixDirection.Forward)` | False if that block has lava in it or under it, a drop of more than 3 blocks under it, or a hostile mob in it or right next to it. A wall is safe, because the robot cannot go there. |
| agent light level | `agent.lightLevel()` | How light it is where the robot stands: 0 (dark) to 15 (daylight, or a torch right there). Sky light is lower at night and in rain. Hostile mobs can spawn where it is 0. |
| agent biome | `agent.biome()` | The biome where the robot stands, as a short text: `"plains"`, `"desert"`. Biomes from mods keep the mod name: `"mymod:crystal_cave"`. |
| agent temperature | `agent.temperature()` | How warm that biome is: snowy plains 0, plains 0.8, desert 2. Below 0.15 it snows instead of rains. |

### Measuring

The robot stays where it is and only looks. A position or corner you give must be within 32 blocks of the robot, or the program stops.

| Block | JavaScript | What it does |
|---|---|---|
| agent measure free (direction) up to (max) | `agent.measure(SixDirection.Forward, 32)` | How many free blocks until something is in the way. |
| agent measure wall (direction) along (along) up to (max) | `agent.measureRun(SixDirection.Forward, SixDirection.Up, 32)` | How high or wide the wall next to it is. |
| agent measure gap (axis) up to (max) | `agent.measureGap(Axis.X, 32)` | How wide the valley or corridor is where the robot stands. |
| agent ground height at (position) | `agent.groundHeight(pos(0, 0, 5))` | The height of the ground there. Leaves do not count. |
| agent height profile (direction) length (length) | `agent.heightProfile(SixDirection.Forward, 16)` | A list of ground heights along a line. |
| agent is area free from (corner) to (corner) | `agent.isAreaFree(pos(1, 0, 1), pos(5, 4, 5))` | True if nothing is in the box (grass and snow are fine). At most 32 blocks each way. |
| agent count solid blocks from (corner) to (corner) | `agent.countSolid(pos(1, 0, 1), pos(5, 4, 5))` | How many blocks are in the box. |
| agent find bounds forward up to (max) | `agent.findBounds(32)` | The two outer corners of the building in front, or nothing. |

### Inventory

| Block | JavaScript | What it does |
|---|---|---|
| agent set active slot (slot) | `agent.setSlot(1)` | Picks the slot to place from. |
| agent item count in slot (slot) | `agent.getItemCount(1)` | How many items are in the slot. |
| agent space left in slot (slot) | `agent.getItemSpace(1)` | How many more fit. |
| agent free slots | `agent.freeSlots()` | How many of the 27 slots are empty. |
| agent is inventory full | `agent.isInventoryFull()` | True if no slot is empty. A stack that is not full can still take more of the same item. |
| agent give (item) count (count) in slot (slot) | `agent.setItem(Item.Stone, 64, 1)` | Gives items. Only in creative mode or with the gamerule `codeling:free_items`. |
| agent collect all | `agent.collectAll()` | Picks up items right next to it. |
| agent drop (direction) from slot (slot) amount (amount) | `agent.drop(SixDirection.Back, 1, 10)` | Drops some items. |
| agent drop all (direction) | `agent.dropAll(SixDirection.Back)` | Drops everything. |
| agent transfer amount (amount) from slot (from) to slot (to) | `agent.transfer(10, 1, 2)` | Moves items between its own slots. |

### Containers

Chests, barrels, hoppers, furnaces and similar blocks.

| Block | JavaScript | What it does |
|---|---|---|
| agent store items (direction) from slot (slot) | `agent.storeItems(SixDirection.Forward, 0)` | Puts items in the container. Slot 0 means all. |
| agent take items (direction) from slot (slot) amount (amount) | `agent.takeItems(SixDirection.Forward, 1, 64)` | Takes from one container slot. |
| agent take from (direction) item (item) amount (amount) | `agent.takeItem(SixDirection.Forward, Item.Coal, 16)` | Takes one kind of item. |
| agent count in container (direction) of (item) | `agent.containerCount(SixDirection.Forward, Item.Coal)` | How many of the item are inside. |
| agent container size (direction) | `agent.containerSize(SixDirection.Forward)` | Number of slots, 0 if there is no container. |
| agent sort container (direction) | `agent.sortContainer(SixDirection.Forward)` | Tidies the chest. |

### Crafting

| Block | JavaScript | What it does |
|---|---|---|
| agent craft (item) amount (count) | `agent.craft(Item.OakPlanks, 4)` | Crafts from its own items. Big recipes need a crafting table next to the robot. |
| agent can craft (item) amount (count) | `agent.canCraft(Item.Stick, 4)` | True if it can craft that now. |
| agent stonecut (item) amount (count) | `agent.stonecut(Item.StoneBricks, 4)` | Uses a stonecutter next to the robot. |

### Feedback

| Block | JavaScript | What it does |
|---|---|---|
| agent say (text) | `agent.say("Hello")` | Shows text over the robot for 5 seconds. At most 80 characters. |
| agent camera (mode) | `agent.camera(CameraMode.Follow)` | Turns the robot camera to eyes, follow or off (see Robot camera below). If the server does not allow it, the program stops with an error. |

### Player (category Player)

These blocks are about you, the player who runs the program. They work without a robot. Nobody else's name or
position can be read, and nobody else can be moved.

| Block | JavaScript | What it does |
|---|---|---|
| say (message) | `player.say("Hi!")` | Shows the message in your chat. Only you see it (in MakeCode everyone does). Plain text, at most 256 characters. |
| player world position | `player.position()` | Where you stand: the block your feet are in, as a world position. |
| player orientation | `player.getOrientation()` | Which way you look, in degrees: south 0, west 90, north 180 (or -180), east -90. |
| player name | `player.name()` | Your player name. |
| teleport to (position) | `player.teleport(pos(0, 10, 0))` | Moves you there, in the dimension you are in. Only in a singleplayer world (as its host) or for a server op; for anyone else the game refuses with `NO_PERMISSION`. Outside the world border or the build height it refuses with `OUT_OF_RANGE`. |
| on chat command (keyword) | `player.onChat("jump", function (num1, num2, num3) { })` | Runs its code when you type a chat line that starts with the keyword, for example `jump 10`. See "React to chat and death" below. |
| on player died | `player.onDied(function () { })` | Runs its code when you die. |
| run chat command (text) | `player.runChatCommand("jump 10")` | Runs your own "on chat command" code for that keyword, as if you had typed it. It sends nothing to the chat or the game. |

Game mode does not matter for these blocks: in spectator or adventure mode you can still use all of them (teleport
still needs singleplayer or op).

#### React to chat and death

"on chat command" and "on player died" let your program wait for you. This program teleports you up when you type
`jump 10` in the chat:

```
player.onChat("jump", function (num1, num2, num3) {
    player.teleport(pos(0, num1, 0))
})
```

- The keyword is one word: letters, digits, `-` and `_`, at most 32 characters, and capital letters count (`Jump`
  is not `jump`). The keyword `*` gets every chat line you type. A program can listen for at most 16 keywords.
- The words after the keyword become num1, num2 and num3: whole numbers. A word that is not a number, or a missing
  one, is 0. `jump 10 abc` gives 10, 0 and 0.
- A line that starts with your keyword is not shown in the chat; it goes to your program only. Every other line is
  sent as usual. Lines from other players never reach your program, and your program never sees the words of a line,
  only the keyword and the numbers.
- If the server has a chat plugin that takes chat lines first, type `/codeling chat jump 10` instead.
- A program with "on chat command" or "on player died" keeps running after its last line, so it can react. Press the
  stop button in the editor, or type `/robot stop`, to end it. From then on your chat lines are shown as usual again.
- At most 16 chat commands wait for your program; more at once are dropped.

#### React to blocks, redstone and the day

Three more blocks let your program wait for something to happen. This program says so each time you dig up diamond
ore, each time the robot gets a signal, and each morning:

```
blocks.onBlockBroken(Block.DiamondOre, function () {
    player.say("Diamonds!")
})
agent.onRedstonePowered(function () {
    agent.move(SixDirection.Forward, 1)
})
gameplay.onDayStart(function () {
    player.say("Good morning")
})
```

- "on (block) broken" runs when you break that kind of block yourself. Blocks your robots break, and blocks other
  players break, do not count. A program can listen for at most 16 kinds of block.
- "on agent redstone powered" runs when the block your default robot stands in goes from no redstone signal to a
  signal: a lever, a button or a redstone block next to the robot, for example. It runs once per new signal, not
  again while the signal stays. A robot that already has a signal when the program starts does not count. Robots
  with a name do not have this block.
- "on day start" runs when the time of day goes round from night to morning in your world. Sleeping through the night
  counts, and so does `/time set` to an earlier time of day (`/time set day` in the evening). `/time add` a whole
  day does not.
- These programs keep running after their last line too, until you press stop or type `/robot stop`. At most 16 events
  wait for your program; more at once are dropped.

### Time and weather (category Gameplay)

These blocks read the world you are in. They work without a robot.

| Block | JavaScript | What it does |
|---|---|---|
| time of day | `gameplay.timeOfDay()` | The time in game ticks, 0 to 23999: 0 is sunrise, 6000 noon, 12000 sunset, 18000 midnight. The Nether and the End have the Overworld's time. |
| is raining | `gameplay.isRaining()` | True when it rains or thunders in your dimension (snow counts too). Never in the Nether or the End. It does not say whether rain reaches one spot: under a roof it still answers true. |
| on day start | `gameplay.onDayStart(function () { })` | Runs its code when a new day starts (time 0, sunrise). See "React to blocks, redstone and the day" above. |

This program works only in daytime and stops when night comes:

```
while (gameplay.timeOfDay() < 12000) {
    agent.move(SixDirection.Forward, 1)
}
```

### Signals (category Signals)

A signal is a name, such as `qr-forest` or `lamp-red`. Another mod on the server can send one to you, for example
when you scan a QR code at a museum post or reach a checkpoint, and your program can send one to the other mods, for
example to switch a lamp. An op can send one with `/codeling signal` (see "Chat commands" below), so a command block
or a map can send signals without any other mod.

| Block | JavaScript | What it does |
|---|---|---|
| on signal (name) | `signals.onReceived("qr-forest", function (value) { })` | Runs its code when a signal with that name comes to you. `value` is the number it carries, 0 when it has none. |
| send signal (name) | `signals.send("lamp-red")` | Sends the name to the other mods on the server. The robot log shows true when a mod took it, else false and why. |

```
signals.onReceived("qr-forest", function (value) {
    agent.move(SixDirection.Forward, value)
})
signals.send("lamp-red")
```

- A name is one word of letters, digits, `-` and `_`, 1 to 32 characters. Case matters: `Lamp` and `lamp` are two
  names. A program can listen for at most 16 names.
- A signal comes only to your own program, only while it runs and has an "on signal" block for that name. Signals
  share the 16 waiting events with chat, death, blocks, redstone and the day.
- "send signal" sends the name only, never anything else from your program. It goes to the other mods on the server,
  never to another player's program and never back to your own. The same name goes at most once a second; a second
  one sooner answers false. On a server without a mod that listens, it always answers false.
- A program with an "on signal" block keeps running after its last line, until you press stop or type `/robot stop`.

### Positions (category Positions)

| Block | JavaScript | What it does |
|---|---|---|
| ~x ~y ~z | `pos(0, 0, 5)` | A place measured from your feet. |
| ^x ^y ^z | `posLocal(0, 0, 5)` | A place measured from where you look (z is forward). |
| world x y z | `world(100, 64, 200)` | An exact place in the world. |
| pick random position from (a) to (b) | `positions.random(pos(-5, 0, -5), pos(5, 0, 5))` | A random place in the box. |
| (position) x value | `p.getValue(Axis.X)` | One number of a position. |
| (position) to world | `p.toWorld()` | Turns `~` or `^` into an exact place. |
| (position) + (other) | `p.add(other)` | Adds two positions. |
| (position) = (other) | `p.equals(other)` | True if they are the same. |
| (position) as text | `p.toString()` | The position as text. |
| (position) distance to (other) | `p.distanceTo(other)` | How many blocks apart they are in a straight line, with decimals. A `~` or `^` position against another kind is first turned into an exact place. |
| (position) - (other) | `p.subtract(other)` | How far it is from the other one to this one, as a position of the same kind. |
| (position) times (number) | `p.scale(2)` | Every number multiplied, rounded to whole blocks. |
| sort (list) by distance from (position) | `positions.sortByDistance(list, p)` | A new list of the same positions, nearest first. |
| nearest in (list) to (position) | `positions.nearest(list, p)` | The nearest position in the list, or `null` when the list is empty. |

### Building big (categories Blocks, Shapes and Builder)

| Block | JavaScript | What it does |
|---|---|---|
| fill with (block) from (a) to (b) (how) | `blocks.fill(Block.Stone, pos(1, 0, 1), pos(5, 3, 5), FillOperation.Hollow)` | Fills the box. Hollow makes walls with air inside, Outline only the walls, Keep fills only air. Air clears the box. |
| place (block) at (position) | `blocks.place(Block.Glass, pos(0, 2, 0))` | Places one block. |
| on (block) broken | `blocks.onBlockBroken(Block.DiamondOre, function () { })` | Runs its code when you break that kind of block by hand. See "React to blocks, redstone and the day" above. |
| line of (block) from (a) to (b) | `shapes.line(Block.OakPlanks, pos(0, 0, 1), pos(5, 3, 1))` | A straight line of blocks. |
| circle of (block) center (c) radius (r) around (axis) (how) | `shapes.circle(Block.Stone, pos(0, 0, 8), 5, Axis.Y, ShapeOperation.Outline)` | A circle; around Y it lies flat on the ground. |
| sphere of (block) center (c) radius (r) (how) | `shapes.sphere(Block.Glass, pos(0, 6, 0), 4, ShapeOperation.Hollow)` | A ball. Hollow makes a bubble with air inside. |
| maze at (corner) width (w) length (l) walls of (block) floor of (block) (seed) | `shapes.maze(pos(2, 0, 2), 15, 15, Block.StoneBricks, Block.OakPlanks, 42)` | A maze with one way between any two places, walls 2 high, the entrance on the north side and the exit on the south side. The corner is at foot height, like a fill's corner: the walls stand on that layer and the floor replaces the ground one block below it. Width and length are 5 to 32; an even number builds one block less. The same seed always gives the same maze, so friends can race through maze 42; without a seed (press + on the block to add one) it is new each time. Walk it with `agent.followPath(Block.OakPlanks)`. |
| builder move (direction) by (steps) | `builder.move(SixDirection.Forward, 4)` | Moves an invisible builder. It starts at your feet facing south. |
| builder turn, builder face, builder teleport to | `builder.turn(TurnDirection.Left)` | Turns or moves the builder. |
| builder place mark | `builder.mark()` | Remembers where the builder is. |
| builder line from mark with (block), builder fill from mark with (block) (how) | `builder.fill(Block.Stone, FillOperation.Hollow)` | Builds from the mark to the builder. |
| builder place (block), builder position | `builder.place(Block.Torch)` | Places a block where the builder is, or tells where it is. |

The robot builds all of this, so it must be within 32 blocks of every part. In survival it uses the blocks in its inventory and stops when they run out. Then it says why, over its head and in the log under the simulator (for example "I have no stone in my inventory."); in creative mode, or with the gamerule `codeling:free_items`, it needs none. Blocks that are in the way are broken and go into the robot.

The Console category has "say (text)" (`console.log("text")`). It writes to the log under the simulator.

### Solve a maze

Build a maze in front of you:

```
shapes.maze(pos(2, 0, 2), 15, 15, Block.StoneBricks, Block.OakPlanks, 42)
```

Walk into the gap in the north wall (the entrance), look south into the maze and type `/robot spawn`. Then run:

```
while (agent.followPath(Block.OakPlanks, TurnDirection.Left) == PathStop.Limit) {
}
agent.say("I am out!")
```

The robot keeps its left hand on the wall until it walks out of the exit on the south side. In a big maze the walk is longer than 256 blocks, so the loop follows the path again each time it stops at the limit. Try `TurnDirection.Right`: which way is shorter in maze 42?

## Several robots

A world can let every player have more than one robot. The game rule `codeling:max_robots` says how many (1 to 16,
the robot without a name counts as one). It is 1 at first, so everything works with one robot as before. An op turns
it up, for example `/gamerule codeling:max_robots 4`. Turning it down again never removes robots; it only stops new ones.

- `/robot spawn Miner` makes a robot called Miner at your feet. A name starts with a letter and has at most 16
  letters, digits, `_` or `-`. Your robots all have different names (Miner and miner count as the same name).
- Your first robot, the one without a name, stays your default robot. In programs it is `agent`, so old programs
  and saved worlds work as before.
- Each robot has its own inventory, slot, home, gravity and assist, and is saved with the world.
- `/robot list` shows your robots and where they are. `/robot remove Miner` removes one: everything it carries goes
  into your inventory, and what does not fit drops at your feet. The robot must be loaded (close to you).
- The chat commands for one robot take the name at the end: `/robot move forward 3 Miner`, `/robot come Miner`,
  `/robot inventory Miner`, `/robot camera Miner`. Without a name they are for your default robot.
- `/robot stop` stops all your robots and your program. `/robot stop Miner` stops only what Miner is doing.

In a program, `robots.get("Miner")` is the robot called Miner. It can do everything `agent` can:

```
let miner = robots.get("Miner")
control.runInParallel(function () {
    miner.move(SixDirection.Forward, 5)
    miner.destroy(SixDirection.Down)
})
agent.move(SixDirection.Back, 5)
```

Each robot does one thing at a time, and every robot has its own line of commands. "run in parallel" lets the
code inside it run at the same time as the rest, so Miner and your default robot move together. Without it, the
program waits for each command before the next, as always.

In Blocks, the Robots category has "robot (name)", "run in parallel" and the most used robot blocks: move, turn,
move to, go to robot, position, place, destroy, collect all and say. Put "robot (name)" in a variable and use the variable in those blocks.
The other robot calls (for example `miner.findNearest(Block.CoalOre, 16)`) are in JavaScript and Python. The agent
blocks stay as they are, for your default robot.

### Go to another robot

"go to robot" sends one of your robots to another one, the first step of any job they share:

```
let miner = robots.get("Miner")
agent.goToRobot(miner)
miner.goToRobot(robots.get(""))
```

The robot walks (or flies) the same way as "move to" and stops on a free block next to the other robot, turned to
face it. If it ends right above or below it, it keeps the way it faced. It answers true when it got there, and false
when there is no way, no free block next to the other robot, or the other robot moved away while it walked (then
just ask again). A robot told to go to itself answers true at once; one that is already next to the other only turns.
`robots.get("")` is your default robot, `agent`, so the last line walks Miner to `agent`.

Only your own robots can be the target: another player's robot is never found, even with the same name. These
stop the program with an error that you can catch (see "Errors, and checking your own program"):

- `BAD_ARGS`: you have no robot with that name. The message lists the robots you have.
- `UNLOADED`: the other robot is not loaded (far from every player).
- `OUT_OF_RANGE`: the other robot is in another dimension, or so far away that the path search gives up.

"robot (name) position" gives where a robot is, for the position blocks: the distance to it, `player.teleport` to it
(singleplayer or op only), or building next to it.

### Name tags

A robot with a name shows it over its head. Rename it with `/robot rename Miner Digger`, or with a name tag item
from an anvil (right-click the robot with it). Only the owner and ops can rename a robot. `/robot rename Bob` (one
name) gives your default robot a tag; it is still `agent` in programs.

Look at a robot up close and you also see whose robot it is. `/robot nametag off` hides the names of all your robots,
`/robot nametag on` shows them again; add a name to change only one robot. While a robot talks (`agent.say`), the
speech bubble takes the place of its tag.

## Chat commands

A robot name at the end of a command (shown as `[name]`) picks one of your robots. Without it the command is for
your default robot.

| Command | What it does |
|---|---|
| `/codeling` | Opens the editor in your browser. |
| `/codeling chat <text>` | Sends a chat line to your own running program's "on chat command" code, as typing it in the chat would. Works on servers whose chat plugins take chat lines first. Says so when no program of yours is listening. |
| `/codeling signal <players> <name> [number]` | Sends a signal to the programs of the players named (for example `@a` or a player name), with the number (0 without one). Ops only, and command blocks. It says how many programs got it and names the players it did not reach. |
| `/robot spawn [name]` | Spawns your robot, or moves it to you. Stops a running program. A new robot with a name needs room under `codeling:max_robots`. |
| `/robot list` | Your robots and where they are. |
| `/robot remove <name>` | Removes a robot with a name. Its items go to you. |
| `/robot rename <name> <new name>` | Renames a robot. `/robot rename <new name>` gives your default robot a name tag. |
| `/robot nametag <on\|off> [name]` | Shows or hides the name over your robots (without a name: all of them). |
| `/robot stop [name]` | Without a name: stops all your robots and your program. With a name: stops what that robot is doing. |
| `/robot status [name]` | Where the robot is and which way it looks. |
| `/robot move <dir> <blocks> [name]` | Moves 1 to 64 blocks. `<dir>` is forward, back, left, right, up or down. |
| `/robot turn <left\|right> [name]` | Turns. |
| `/robot place <dir> [name]` | Places a block. |
| `/robot destroy <dir> [name]` | Breaks a block. |
| `/robot inspect <dir> [name]` | Shows the block number. |
| `/robot detect <dir> [name]` | True if a block is in the way. |
| `/robot slot <1-27> [name]` | Picks a slot. |
| `/robot give <block> <count> [slot] [name]` | Gives items, for example `/robot give oak_planks 64`. Only in creative mode or with the gamerule `codeling:free_items`. |
| `/robot inventory [name]` | Opens the robot's inventory if you are close. Otherwise it lists the items. |
| `/robot collect [name]` | Picks up items next to it. |
| `/robot drop <dir> [name]` | Drops everything. |
| `/robot assist <true\|false> [name]` | Place on move. |
| `/robot gravity <true\|false> [name]` | Gravity on or off. |
| `/robot sethome [name]` | Remembers where the robot stands as its home. |
| `/robot come [name]` | The robot walks to you. If there is no way, it jumps to you. Not from another dimension: use `/robot spawn`. |
| `/robot camera [name]` | Look through your robot's eyes. Run it again, `/robot camera off` or the camera key takes you back to your own. Escape opens the pause menu as usual and keeps the camera on. |
| `/robot camera follow [name]` | Watch your robot from behind. Run it again, `/robot camera off` or the follow key takes you back to your own eyes. Escape opens the pause menu as usual and keeps the camera on. |
| `/robot stop all` | Stops every robot. Ops only. |

You can also right-click the robot to open its inventory.

### Robot camera

`/robot camera` shows the world through your robot's eyes. You can also give it a key: in Options, Controls, Key Binds, find "Robot camera" under Codeling (it has no key at first). The same key, `/robot camera` or `/robot camera off` takes you back. Escape opens the pause menu (so you can switch to the editor) and leaves the camera on.

While you look through the robot, your own body stays where it is and does not move. The top left corner shows "Robot camera" and the robot's name. The camera only works while the robot is in the same dimension and close enough that your game has its part of the world loaded. It ends by itself when the robot is removed, goes too far away or changes dimension.

`/robot camera follow` puts the camera behind and above the robot instead, so you can watch a long build job or route. The camera turns with the robot. Move the mouse to look around the robot, and use the scroll wheel to come closer or move away (2 to 12 blocks). When a wall is in the way, the camera moves in front of it. "Robot camera: follow" in Key Binds gives it a key of its own. The same rules apply as for the eyes view.

## Limits

These keep the game fast for everyone.

- One robot per player, unless the game rule `codeling:max_robots` allows more (at most 16).
- `move` goes at most 64 blocks per command. Each block takes 4 game ticks (0.2 seconds).
- `find nearest` and the measuring blocks look at most 32 blocks away from the robot.
- Fill and shapes build at most 32 blocks away from the robot, at most 32768 blocks in one go, 256 blocks per tick.
- `move to` gives up when the path is very long or there is none, and answers false. `go to robot` answers
  `OUT_OF_RANGE` when the path is that long.
- The robot only works in loaded parts of the world (near players).
- Amounts are at most 64. Robot slots are 1 to 27. Chest slots are 1 to 54.
- `say` shows at most 80 characters, and the player's `say` at most 256.
- The game runs at most one player block per tick (20 a second) for you; more than 16 waiting is refused with `RATE_LIMITED`.
- A program listens for at most 16 chat command keywords of at most 32 characters, at most 16 kinds of broken block and at most 16 signal names. At most 16 events wait for it.
- "send signal" sends the same name at most once a second.
- Gravity is off at the start, so the robot floats. Turn it on with "agent gravity" or `/robot gravity true`.
- The program runs in your browser. If you close the tab or leave the world, the program stops. After 10 seconds without contact the game stops the robot.

## Multiplayer

- The server needs Codeling for its loader (Fabric Loader and Fabric API, or NeoForge). Every player who wants to code needs the same loader and Codeling too.
- Only you can program your robot. Nobody else can send it commands. This goes for robots with a name too: a name
  only ever picks one of your own robots.
- `/gamerule codeling:max_robots <1-16>` sets how many robots each player may have (1, the default, is one robot
  without a name).
- Only you and server ops can open your robot's inventory. Others see "This is not your robot."
- In survival the robot gathers its own resources. "agent give" and `/robot give` only work in creative mode, or when the world has `/gamerule codeling:free_items true`. Otherwise put items in the robot through its inventory screen.
- Ops can stop all robots with `/robot stop all`.
- A program's `player.say` goes to its own player only, and `player.teleport` works on a server only for ops.
- A program hears only its own player's chat commands, death and broken blocks, its own robot's redstone signal and
  the day in its own player's world, never anything about another player. A chat line is held back from the chat
  only while your program runs and listens for its first word.
- A signal reaches only the program of the player it was sent to. A program's "send signal" goes to the server's
  other mods only, never to another player, and carries the name only.
- The gamerule `codeling:robot_camera` decides who may use the robot camera: `owner` (the default: every player through their own robot), `ops` (only ops) or `off` (nobody). For example `/gamerule codeling:robot_camera off`. On NeoForge 26.3 the Edit Game Rules screen shows this rule as a text field: type `off`, `owner` or `ops` (on NeoForge 1.21.1 the screen leaves it out; use the command). The server checks this, and the camera ends at once when the rule takes it away.
- The robot breaks and uses things as if it were you. If the server protects an area, the robot cannot change it either.

## Troubleshooting

| Problem | What to do |
|---|---|
| "Editor link expired" | Minecraft was restarted after you opened the tab. Type `/codeling` again. |
| "Not in a world" | Open a world first, then press Run again. |
| "This server does not have Codeling installed" | The server needs the mod. Ask the server owner, or play in singleplayer. |
| The robot does nothing while you are in the browser, and the log says "Minecraft is paused" | The pause menu is open in the game. Go back to the game and close it; the robot goes on by itself. |
| "Robot is not loaded (or not spawned: /robot spawn)" | Type `/robot spawn`, or walk closer to the robot. |
| "robot Miner is not loaded (or not spawned: /robot spawn Miner, see /robot list)" | Check the name with `/robot list`. Walk closer to the robot, or type `/robot spawn Miner`. |
| "This world allows one robot per player" | Robots with names need the game rule `codeling:max_robots` above 1. Ask an op. |
| `/codeling` does nothing | Another program uses port 25580, often a second Minecraft with Codeling. Close it and restart Minecraft. The game log says "Codeling could not start the editor server on port 25580". |
| The blocks look old after an update | Press Ctrl+F5 in the browser. |
| "Program stopped" | `/robot stop`, `/robot spawn`, a closed tab or leaving the world stopped it. Press Run again. |

## Disclaimer

Codeling is a fan project. It is not affiliated with or endorsed by Microsoft, Mojang or the Microsoft MakeCode team.
Minecraft is a trademark of Mojang Synergies AB (Microsoft).
The editor is built with pxt, Microsoft's open-source MakeCode toolkit, which is MIT-licensed.
Codeling itself is MIT-licensed. Details and licence texts: [THIRD-PARTY-NOTICES.md](../../THIRD-PARTY-NOTICES.md) and [LICENSE](../../LICENSE).
