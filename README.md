# Codeling

A Minecraft Java mod (Fabric and NeoForge, Minecraft 26.3 on Java 25 and Minecraft 1.21.1 on Java 21) where you program a robot with blocks, JavaScript or Python, in the style of MakeCode.
The editor is a custom pxt target that runs in your browser and is served by the mod on `127.0.0.1`.
Each robot command goes over a local WebSocket to the mod.

**Status: step 2.** The server has a robot with a 27-slot inventory and `/robot` commands, and the browser editor drives it: every robot op (move, turn, place, destroy, detect, inspect, inventory) is a block, JavaScript and Python call, and each call returns when the robot has finished.
Plan: [docs/architecture.md](docs/architecture.md).

Not approved by or associated with Mojang or Microsoft. The editor is based on Microsoft MakeCode (pxt), MIT licence.
Theme files in `target/theme` started from microsoft/pxt-sample (MIT, see `target/LICENSE-pxt-sample.txt`). Notices and licence texts: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

Player guide (install, first program, every command): [English](docs/player/guide-en.md), [norsk](docs/player/guide-nb.md).

## Layout

| Path | What |
|---|---|
| `target/` | pxt target (npm, pxt-core 13.3.1). `npm run build` then `npm run staticpkg` writes `target/built/packaged`. `target/editor/` is the editor extension (`editor.js`) that upgrades Blocks files saved by older versions. |
| `stonecutter.gradle.kts` | Root build script and Stonecutter controller (issue #189): one Gradle project per module and Minecraft version (`:fabric:26.3`, `:fabric:1.21.1`, ...), the version `src/` holds right now (26.3) and the plain renames between versions. Each module's `versions/<version>/gradle.properties` holds what differs per version (loader API version, Java version, metadata range). |
| `fabric/` | Fabric loader module (Gradle projects `:fabric:<version>`, Loom): entrypoints, `fabric.mod.json`, the Fabric implementation of the platform layer, JUnit tests and GameTests. Compiles `common`'s sources into its jar and bundles `target/built/packaged` as `codeling-editor/`. |
| `neoforge/` | NeoForge loader module (Gradle projects `:neoforge:<version>`, ModDevGradle): `@Mod` entrypoints, `neoforge.mods.toml`, `enumextensions.json` (the enum game rule type), the NeoForge implementation of the platform layer and smoke GameTests (`src/gametest/`, not in the jar). Compiles `core`'s and `common`'s sources into its jar, applies `common`'s access transformer and bundles the editor like `:fabric`. |
| `common/` | Vanilla Minecraft code shared by every loader (Gradle projects `:common:<version>`, ModDevGradle in NeoForm mode, so no loader API on the classpath): robots, relay handler, payload types, screens, renderer, camera mixins, `assets/`, and the platform interfaces `codeling.platform.Platform` and `ClientPlatform`. `checkCommonImports` (part of `check`) fails the build on a `net.fabricmc`, `net.neoforged` or `net.minecraftforge` import. |
| `core/` | Plain Java (Gradle subproject `:core`, no Loom) with no Minecraft or loader imports: `Protocol`, `Sessions`, `RobotCommand`, `RobotShapes`, `Positions`, `Result`, `RobotException`, `BlockIds`, `ItemIds` and the relay. Same package names as in `common/`. Its classes go into the mod jar. `checkPureImports` (part of `check`) fails the build on a `net.minecraft`, `com.mojang`, `net.fabricmc`, `net.neoforged` or `net.minecraftforge` import. |
| `core/src/main/java/codeling/relay/` | HTTP + WebSocket server on `java.net` (no Netty, Java 17). No Minecraft imports, runs standalone. |
| `common/src/main/java/codeling/net/` | The two play payloads, the server end of the relay. Protocol v1 and heartbeat bookkeeping are in `core/`. |
| `common/src/main/java/codeling/robot/` | Server side: robot entity, queue, tick executor, `/robot` commands. The command validator is in `core/`. |
| `e2e/` | Node scripts that drive the editor in headless Chromium: the editor test suite (`editor-suite.mjs`) and the end-to-end client GameTest (`editor-run.mjs`). |
| `fabric/src/gametest/` | Server and client GameTests (own mod id `codeling-gametest`, not in the jar). Screenshot templates in `resources/templates/`. |
| `gradle/loader.gradle.kts` | Build logic both loader modules apply: version, jar name rule (`checkJarNames`) and the editor copy with the branding patch. |
| `docs/` | Architecture and research notes. |

## Build

```
cd target
npm ci
npm run build
npm run staticpkg
cd ..
./gradlew build
```

Gradle runs on Java 25 and downloads it on first use (`gradle/gradle-daemon-jvm.properties` and the foojay toolchain resolver), so an installed Java 21 is enough to start the wrapper. It also downloads Java 21 for the 1.21.1 projects.
Build the target first; otherwise the jars have no editor. One build makes four jars:

| Minecraft | Loader | Task | Jar |
|---|---|---|---|
| 26.3 | Fabric | `./gradlew :fabric:26.3:build` | `fabric/versions/26.3/build/libs/codeling-fabric-26.3-<version>.jar` |
| 26.3 | NeoForge | `./gradlew :neoforge:26.3:build` | `neoforge/versions/26.3/build/libs/codeling-neoforge-26.3-<version>.jar` |
| 1.21.1 | Fabric (Fabric API 0.116) | `./gradlew :fabric:1.21.1:build` | `fabric/versions/1.21.1/build/libs/codeling-fabric-1.21.1-<version>.jar` |
| 1.21.1 | NeoForge 21.1 | `./gradlew :neoforge:1.21.1:build` | `neoforge/versions/1.21.1/build/libs/codeling-neoforge-1.21.1-<version>.jar` |

Several Minecraft versions come from one source tree with [Stonecutter](https://stonecutter.kikugie.dev/) (issue #189): `src/` holds the 26.3 code, and lines that differ for 1.21.1 sit in `//? if` comments that Stonecutter swaps in for that version's build (`<module>/versions/1.21.1/build/generated/stonecutter/`). Keep 26.3 active in git; see [docs/architecture.md](docs/architecture.md) "Several Minecraft versions".

Jar names follow one rule for every loader and Minecraft version: `<modid>-<loader>-<minecraftversion>-<modversion>.jar`, all lowercase, loader `fabric`, `neoforge` or `forge` (today `codeling-fabric-26.3-0.0.1.jar`, `codeling-neoforge-26.3-0.0.1.jar`, `codeling-fabric-1.21.1-0.0.1.jar` and `codeling-neoforge-1.21.1-0.0.1.jar`; a sources jar adds `-sources`). The mod name comes first so the files sort together in a mods folder, then loader and Minecraft version so a player sees at a glance which file to pick, then the mod version. Gradle builds the name from `mod_id` and `mod_version` in `gradle.properties`, the loader (the module's name) and the Minecraft version (the Stonecutter project's name), in `gradle/loader.gradle.kts`, applied by every loader module. `checkJarNames` (part of `build`) fails when a jar in the module's `build/libs` breaks the rule. The version inside `fabric.mod.json` and `neoforge.mods.toml` stays the plain mod version.

`./gradlew build` runs the JUnit tests (`:core:test` for the Minecraft-free code, `:fabric:<version>:test` for the rest) and the server GameTests (`:fabric:<version>:runGameTest`, a headless GameTest server; `check` depends on it), for both Minecraft versions.
Run only the GameTests with `./gradlew :fabric:26.3:runGameTest` (or `:fabric:1.21.1:runGameTest`). The JUnit XML report is `fabric/versions/<version>/build/test-results/gameTest/TEST-gametest.xml`; the server world and log are in `fabric/versions/<version>/build/run/gameTest/`.
The 1.21.1 build runs the same GameTests (116 of them) through vanilla's older GameTest annotation; a test environment of 26.3 (a game rule set for some tests) is a batch there whose `@BeforeBatch` sets the rule.

On NeoForge, `build` runs six smoke GameTests (`:neoforge:<version>:runGameTestServer`, a headless GameTest server that exits with the number of failed tests): a robot spawns, moves and places a block, the three game rules are registered (the enum rule takes `/gamerule codeling:robot_camera ops`), the three payload types round-trip through NeoForge's registry, and a claim mod that refuses the robot's FakePlayer stops a placement and sees the owner's UUID. Report: `neoforge/versions/<version>/build/test-results/gameTest/TEST-gametest.xml`; world and log: `neoforge/versions/<version>/run/gametest/`. The full GameTest suite and the client GameTests stay on Fabric (NeoForge has no client GameTest API).
`./gradlew :neoforge:26.3:runClientSmoke` (or `:neoforge:1.21.1:runClientSmoke`) starts the NeoForge client once (a game window, not part of `build`), checks that the camera mixins were merged and that the relay serves the editor, logs `Codeling client smoke: OK` and quits.

Robustness tests in the same build: `ProtocolFuzzTest` (issue #139) sends random and broken JSON (wrong types, huge numbers, deep nesting, 8 KiB strings, unknown ops, mutated sim messages; fixed seeds) to the protocol decoder and fails on anything but a clean `BAD_ARGS`/`VERSION` error or a hang. The GameTest `twentyRobotsSearchWalkAndCraft` (issue #138) runs 20 robots searching, walking and crafting for 200 ticks and reads `RobotManager.lastTick()` (time in the robot executor and the budgets used) every tick. It fails when a tick uses more than `ACTIONS_PER_TICK`, `SCAN_BLOCKS_PER_TICK` or `PATH_NODES_PER_TICK`, when the robots take more than 4 ms per tick on average (8 percent of a 50 ms tick), or when more than 3 ticks take over 20 ms. GameTests share one server, so it measures the robot executor, not the whole server's MSPT; the log line "robot tick cost" shows the numbers.

Client GameTests (issue #134) start a real game window (2 to 3 GB of memory), so `build` never runs them. Close your own Minecraft first, then run `./gradlew :fabric:26.3:runClientGameTest`. The task fails when a test fails; the log is the console output, and the screenshots go to `fabric/versions/26.3/build/run/clientGameTest/screenshots/` (the run folder is cleared at the start of each run). `RobotClientGameTest` creates a flat singleplayer world, spawns the robot with a real `/robot spawn` command packet, sends `control start`, `agent.move`, `agent.say` and `control stop` as real `codeling:to_server` payloads, compares the speech bubble and the selected-slot frame with the templates in `fabric/src/gametest/resources/templates/` (mean squared difference 0.005, searched 10 pixels around the expected spot), moves a stack in the robot's inventory screen with the mouse, then closes and reopens the world and checks the robot's position, selected slot and items. The templates assume an 854 x 480 window at GUI scale 2 (the client GameTest default); the test says so when the window differs. To make a new template, move the old one to the deletion folder and run the test: Fabric writes the whole screenshot as the template, then crop it to the part the test names.

The same task runs the end-to-end test (issue #135), `EditorEndToEndClientGameTest`. It reads the editor URL from the running relay inside the test process and hands it to `e2e/editor-run.mjs` on stdin (never in argv, the environment or a log). The script opens the editor in headless Chromium (`playwright-core` pinned to 1.60.0 in `e2e/package.json`; the task runs `npm ci` there first), makes a new project, puts a JavaScript program in, presses Run and waits for a text in the simulator's robot log. Cases: at the title screen the editor shows "Not in a world"; a stale token shows "Editor link expired"; in a world `agent.move(SixDirection.Forward, 3)` moves the robot 3 blocks and the editor logs "move 0 3: done"; `/robot stop` typed during `agent.move(SixDirection.Forward, 30)` halts the robot part of the way and the editor shows "Program stopped" with the reason. It needs Node.js on PATH and Playwright's headless Chromium 1223: if it is missing, run `node node_modules/playwright-core/cli.js install chromium-headless-shell` in `e2e/`. A screenshot of the editor at the end of each case goes to the same screenshots folder.

### Editor test suite

`./gradlew :fabric:26.3:editorTest` (issue #136; not part of `build`, CI runs it after `build`). Build the target first, as for the jar. It needs Node.js on PATH and Playwright's headless Chromium (`node node_modules/playwright-core/cli.js install chromium-headless-shell` in `e2e/` once).
The task runs `npm ci` in `e2e/`, then `e2e/editor-suite.mjs`, which starts the standalone relay itself on port 25591 (`CODELING_TEST_PORT` for another) and reads the editor URL from the relay's output inside the Node process, so the token is never printed or written. It opens the editor in headless Chromium (`en-US`) and checks:

- Every block in the toolbox: the visible `blockId`s are read from `target/libs/core/sims.d.ts` and `ns.ts`, and each needs a test program in `e2e/programs/blocks.mjs` (the suite fails when one is missing). All programs together go JavaScript to Blocks to Python to Blocks to JavaScript; it fails on a grey block, on text that comes back different (a lost or changed argument), and on a block the programs do not make.
- Example programs in MakeCode style (`e2e/programs/examples.mjs`, written for this repo, issue #31): the same round trip, then Run in the simulator, and the robot log must match line by line (the standalone relay's fake answers are fixed).
- Old saved projects (`e2e/fixtures/`): a `.blocks` file with what an older editor saved and a `.ts` file with the JavaScript it must turn into. The suite packs them as a project saved by editor version 0.0.1 and drops it on the editor, as a player would import it.
- Branding: "Codeling" shows once in the header and "MakeCode" and "Microsoft" not at all, the About box says "pxt editor" and not "Microsoft MakeCode", and a new project opens without the README side panel. Screenshots of the header, the About box and the new project go to `fabric/versions/26.3/build/editor-test/` (`CODELING_SHOTS` for another folder).

The last line counts what passed: `editor suite: 90 blocks, 6 example programs, 1 fixtures, 127 checks, 0 failed`. The task fails when a check fails; each failure is a `FAIL` line naming the block, program or file.
A new block needs one line in `e2e/programs/blocks.mjs`, written the way the editor formats it, with values that differ from the block's defaults.

## Run without Minecraft

```
./gradlew :fabric:26.3:runRelay --args="--print-url"
```

Prints `EDITOR_URL http://127.0.0.1:25580/#local_token=...`. Open it in a browser. The token is new for every run.
Another port: `--args="25581 --print-url"`.
Without the game the relay answers every command with a fake `ok` after 300 ms, so the editor can be worked on alone.

## Run in Minecraft

Fabric: the Loom run configuration "Minecraft Client (:fabric:26.3)" or `./gradlew :fabric:26.3:runClient` (`:fabric:1.21.1:runClient` for 1.21.1).
NeoForge: the run configuration "NeoForge Client (:neoforge:26.3)" or `./gradlew :neoforge:26.3:runClient` (game folder `neoforge/versions/26.3/run/client/`); a dedicated server is `./gradlew :neoforge:26.3:runServer`. The same with `1.21.1` for Minecraft 1.21.1.
In game, type `/codeling` to open the editor in your browser.

### From the browser to the robot

1. `/robot spawn` in game, then `/codeling`. While a program runs (an editor session is linked) the game does not pause when you switch to the browser (issue #192, `PauseOnLostFocusMixin`). With the pause menu open a singleplayer game runs no ticks, so the robot waits; the editor's log says "Minecraft is paused. Go back to the game and close the menu." and the command runs once the menu is closed.
2. In the editor, JavaScript tab: `agent.move(SixDirection.Forward, 3)` and `agent.turn(TurnDirection.Right)`. The simulator runs the program.
3. The sim opens a WebSocket to the relay in the client mod (`hello` with the token, then `welcome`), sends `control start` and gets a fresh run id from the server. Each robot call is a `cmd` with that run id; the relay forwards it as a play payload (`codeling:to_server`), the server validates it, queues it and answers with `result` or `error` (`codeling:to_client`) when the robot has finished. The call returns then.
4. While the program runs the sim sends a heartbeat every 3 s. The server stops the run after 10 s without one, when you leave the world, on `/robot stop` or `/robot spawn`, and when the program is stopped or restarted in the editor (or its tab closes).
5. One browser session controls the robot. A newer one (a restarted program, or a second tab) takes over; the older one gets a "took over" message.

### Editor API

Every server op is a block in the Agent category (groups Movement, Building, Inspecting, Inventory, Containers, Crafting, Feedback), with the same name in JavaScript and snake_case in Python (`agent.set_item`).
Each call waits for the robot and returns the server's answer.

| Call | Returns |
|---|---|
| `agent.move(direction, steps)`, `agent.turn(direction)` | nothing |
| `agent.place(direction)`, `agent.destroy(direction)` | nothing (statements as in MakeCode; the sim log shows true/false) |
| `agent.interact(direction)` | nothing (a statement as in MakeCode; the log shows true when the block reacted): the robot's FakePlayer right-clicks the block with an empty hand, so claim mods apply. Opens and closes wooden doors, trapdoors and fence gates, presses buttons, flips levers; iron doors and iron trapdoors need redstone, and other blocks are left alone (issue #102). |
| `agent.moveTo(position)` | boolean: true when the robot got there, false when there is no path. Codeling extension (issues #44 to #46, #109). The server plans a path (A*, at most 8192 nodes per search and 1024 per tick for all robots together, loaded chunks only), then walks it with normal moves (4 ticks per block, place on move applies). Gravity off: the robot flies. Gravity on: it walks, climbs 1-block steps and falls down. Closed wooden doors and fence gates on the way are opened through the FakePlayer and left open. A solid target (a log from `findNearest`) ends next to it, facing it when it is beside the robot. `~` and `^` positions are resolved against you first; `null` answers false. `/robot stop` cancels the search or the walk. If the world changes on the way, the robot stops and answers false. |
| `agent.setHome()`, `agent.goHome()` | nothing, boolean. Codeling extension (issues #76 and #78). `setHome` saves the robot's block and dimension on the robot as its home (also `/robot sethome`). `goHome` walks there exactly like `moveTo` (same path search and limits, no teleport); false when no home is set, the home is in another dimension, or there is no path. |
| `agent.followPath(block?, side?)` | `PathStop`: End 0, Junction 1, Blocked 2, Limit 3. Codeling extension (issue #179). Op `agent.followPath [block]`, 0 (what `followPath()` sends) for the block under the robot; air is never a path. `RobotTrail` plans the route first (15 reads per block from the shared scan budget, over ticks when it runs out), then the robot walks it with the `moveTo` moves (step up and down, falls, doors and gates, place on move). The next path block is in front, left or right at the same height or one step up or down, never back the way it came; at the start it goes forward when it can. Stops at the end, on a junction block facing the way it came, in front of a blocked block, after 256 blocks, or back at the start of a loop (End). If the world changes on the way it answers Blocked where it stands. With a block given, a robot right next to that path steps onto it; with none under or next to it the answer is End with the message "I am not on the path. Put me on the gravel first.", which the editor logs and the robot says. With a side (issue #180, op `[block, side]`, `TurnDirection` Left 2 or Right 3, 0 none) it is a wall follower: the left-most (right-most) way of left, forward and right, never Junction; at a dead end with a solid block in front it turns round. It stops where the path ends without a solid block in front (End), back at the start setting off the same way again (End), Blocked or Limit (follow again to go on). Standalone relay answers 0. |
| `agent.setAssist(AgentAssist.PlaceOnMove, on)` | nothing (only `PlaceOnMove` is in the dropdown and works) |
| `agent.setGravity(on)` | nothing (Codeling extension, off by default: with gravity the robot falls after a sideways step or `destroy` down, steps up 1-block bumps, and a 2-block wall makes `move` answer false) |
| `agent.detect(AgentDetection.Block, direction)` | boolean |
| `agent.isOpen(direction)` | boolean: the block's `open` state (doors, trapdoors, fence gates, barrels); false for blocks that cannot open. Codeling extension (issue #106). |
| `agent.detectEntity(EntityKind.Mob, direction)` | boolean: a living entity of that kind in the block next to the robot. Kinds: `Mob` (hostile, mob category monster), `Animal` (the other natural mobs: animals, fish, bats, axolotls; villagers and golems are neither), `Player` (not spectators), `Item` (dropped items). Codeling extension (issue #86). |
| `agent.countNearby(EntityKind.Animal, radius)` | number of entities of that kind in the cube of that radius (1 to 8, else `OUT_OF_RANGE`) around the robot's block (issue #86). Standalone relay answers false and 0. |
| `agent.radar(EntityKind.Mob, radius)`, `agent.nearestEntity(kind, radius)` | `Position[]` (world block positions, nearest first by straight-line distance, at most 64, empty when none) and `Position` or `null` (the first of the same list). Same kinds and filter as `countNearby`, in the cube of that radius (1 to 16, else `OUT_OF_RANGE`) around the robot's block. Invisible players and spectators are never included (also for `detectEntity` and `countNearby`). One op `agent.radar [kind, radius]` answers a flat `[x1, y1, z1, x2, ...]`; `nearestEntity` sends the same op and takes the first. Codeling extension (issue #159). Standalone relay answers `[]`. |
| `agent.detectLiquid(direction)` | `Liquid`: None 0, Water 1 (also waterlogged blocks), Lava 2, with a reporter block `agent.liquid` to compare in Blocks (issue #161). Standalone relay answers 0. |
| `agent.isSafe(direction)` | boolean, false when the block next to the robot holds lava; or it does not stop a move and lava or more than 3 free blocks lie under it before the ground (a fall of 4 or more; water at the bottom still counts as a fall); or a hostile mob (mob category monster) is in it or touches one of the 26 blocks around it. A block that stops a move is checked for lava and mobs only (issue #161). Standalone relay answers true. |
| `agent.redstonePower(direction)` | number 0 to 15, as F3 shows it: what a source gives out (redstone dust its power, a lever or redstone block 15), otherwise the strongest signal the block receives. `detect(Redstone)` is unchanged (issue #107). |
| `agent.inspect(AgentInspection.Block \| Data, direction)` | number (block id, `-1` if unknown) |
| `agent.getPosition()` | `Position` (world block position); `.getValue(Axis.X)` (or `Y`, `Z`) reads one coordinate, in the Positions category |
| `agent.getOrientation()` | number, degrees: south 0, west 90, north 180, east -90 (MakeCode's convention as documented; not yet confirmed against MakeCode in game) |
| `agent.findNearest(Block.OakLog, radius)`, `agent.findNearestCategory(BlockCategory.Logs, radius)` | `Position` (world) of the nearest match in the cube of that radius (1 to 32) around the robot, nearest by block steps, or `null` when there is none. Codeling extension. Categories are block tags: logs, leaves, planks, wool, flowers (`minecraft:`) and ores (`c:ores`). The server checks at most 8192 blocks per tick for all robots together and skips unloaded chunks; `/robot stop` cancels a search |
| `agent.measure(direction, max)` | number: free blocks in that direction before the first block that would stop a move, or max (1 to 32) when none does. Codeling extension (issue #127). Every measuring call reads blocks without moving the robot, only in loaded chunks (an unloaded block ends the program with `UNLOADED`), and is costed against the same 8192-blocks-per-tick budget as `findNearest`. |
| `agent.measureRun(direction, along, max)` | number: solid blocks in a row starting with the block in direction and going along (for the wall in front: along right is its width, along up its height), at most max (1 to 32); 0 when there is no block (issue #128). |
| `agent.measureGap(Axis.X, max)` | number: the free width across the robot along a world axis (Y is floor to ceiling), the robot's own block included, between the first blocking blocks on both sides; max (1 to 32) when one side is open within max (issue #129). |
| `agent.groundHeight(position)` | number: the y of the highest block at the position's x and z by the vanilla `MOTION_BLOCKING_NO_LEAVES` heightmap (leaves ignored, water counts; below the world when the column is empty). The position's y is not used; `~` and `^` are resolved against you first. The column must be within 32 blocks of the robot, else `OUT_OF_RANGE` (issue #130). |
| `agent.heightProfile(direction, length)` | `number[]`: `groundHeight` of the length (1 to 32) columns from one block in direction outwards. Forward, back, left or right only (issue #130). |
| `agent.isAreaFree(corner1, corner2)`, `agent.countSolid(corner1, corner2)` | boolean, number: whether every block in the box is air or replaceable (grass, a snow layer, water), and how many are not. At most 32 blocks on each side, and both corners within 32 blocks of the robot on every axis (else `OUT_OF_RANGE`), so a program on a server cannot probe hidden blocks far away; read over several ticks for big boxes (issue #131). |
| `agent.findBounds(max)` | `Position[]` of two world corners (lowest, highest) of the solid blocks connected by their sides to the block in front, the box at most max (1 to 32) on each side, or `null` when the block in front is not solid. The cap keeps it within 32 blocks of the robot. A building on the ground connects to the ground, so its bounds include ground up to the cap (issue #132). |
| `agent.say(text)` | Shows the text over the robot for 5 s (a name tag, seen by everyone within 64 blocks). One line, at most 80 characters (longer text ends in "..."). A new `say` replaces the old one. Codeling extension (issue #80). |
| `agent.setSlot(slot)`, `agent.getItemCount(slot)`, `agent.getItemSpace(slot)` | nothing, number, number |
| `agent.freeSlots()`, `agent.isInventoryFull()` | number of empty slots (0 to 27), boolean true when no slot is empty (issue #162). Standalone relay answers 27 and false. |
| `agent.setItem(Item.IronIngot, count, slot)` | nothing (creative mode or the gamerule `codeling:free_items` only); `Block.Air` empties the slot |
| `agent.collectAll()`, `agent.drop(direction, slot, quantity)`, `agent.dropAll(direction)`, `agent.transfer(quantity, sourceSlot, destinationSlot)` | nothing |
| `agent.storeItems(direction, slot)` | boolean: true if anything moved into the container next to the robot. Slot 1 to 27, or 0 for all. Vanilla hopper rules: a furnace, smoker or blast furnace takes what to smelt from above and fuel from the side; a brewing stand takes the ingredient from above and bottles and blaze powder from the side. What does not fit stays in the robot. Codeling extension (issues #71, #72, #74). |
| `agent.takeItems(direction, slot, count)` | boolean: takes up to count (1 to 64) from one container slot (1 to 54), or from the first stack with slot 0. A furnace gives its output downwards. |
| `agent.takeItem(direction, Item.Coal, count)` | number taken: that item from any slot, until count or the robot is full. |
| `agent.containerCount(direction, Item.Stone)`, `agent.containerSize(direction)` | number: how many of the item, how many slots (27 chest, 54 double chest, 0 when there is no container). |
| `agent.craft(Item.OakPlanks, count)` | number made: crafts from the robot's own inventory with the server's recipes (any planks, tags and alternatives work). 2x2 recipes always; 3x3 recipes only with a crafting table in one of the six blocks next to the robot. Makes whole recipes until at least count (1 to 64) items are made, the ingredients run out or the output no longer fits; nothing is used for a craft that does not happen, and nothing is dropped. Codeling extension (issue #93). |
| `agent.canCraft(Item.Stick, count)` | boolean: whether `craft` would make at least count right now. |
| `agent.stonecut(Item.StoneBricks, count)` | number made, like `craft`, with the stonecutter recipes and a stonecutter next to the robot (issue #115). |
| `agent.sortContainer(direction)` | nothing (the log shows true/false): merges equal stacks and orders them by item id. Chests, barrels, shulker boxes, hoppers, dispensers and droppers only. |

Several robots (issues #140, #141, #151): the game rule `codeling:max_robots` (1 to 16, default 1) sets how many robots each player may have; the default robot (no name) counts as one, so with the default nothing changes. Named robots come from `/robot spawn <name>` (a letter, then up to 15 letters, digits, `_` or `-`; unique per owner ignoring case; `all`, `follow`, `off` and `on` are reserved). `robots.get("Miner")` (category Robots) returns a `Robot` with every `agent` method; each call adds `"robot": "Miner"` to the `cmd`, and the server looks the robot up under the sender's own UUID, so a name can never reach another player's robot. Each robot has its own queue, so calls to different robots overlap when they run in different `control.runInParallel` blocks (a `thread.runInBackground`); all of one owner's robots share the editor's run, and `control stop` or a lost heartbeat stops them all. Blocks has the Robots category with `robot (name)`, `run in parallel` and the common actions (move, turn, move to, place, destroy, collect all, say); the other `Robot` methods are JavaScript and Python only, and the agent blocks are unchanged. A dropdown on every agent block would change all of them and a full second block set would double the toolbox, so this was the smaller choice. The sim log prefixes a named robot's lines with its name ("Miner: move 0 2: done").
Save format: `robots.dat` keeps default robots in `links` as before and named ones in a new `named` map (owner, name, robot UUID), so an old save loads as the default robots. An older mod version reading a newer save ignores `named` and treats those robots as stale, removing them without their items when they load.

Positions (category Positions, MakeCode names): `pos(x, y, z)` is relative to your feet with world axes (`~`), `posLocal(x, y, z)` turns with your facing (`^`: x left, y up, z forward), `world(x, y, z)` is absolute; `positions.create`, `createLocal`, `createWorld` are the same without blocks.
A `Position` has `getValue(axis)`, `add(other)` (keeps its own kind), `equals(other)` (same kind and coordinates), `toString()` ("1 2 3", "~1 ~2 ~3", "^1 ^2 ^3") and `toWorld()`.
Building (categories Blocks, Builder and Shapes, MakeCode names, issue #90): `blocks.fill(block, from, to, FillOperation.Replace)` fills the box between two corners (Replace sets every block, Hollow sets the outside and clears the inside, Outline sets only the outside, Keep fills only air and replaceable blocks such as grass and water, Destroy is the same as Replace), and `Block.Air` clears; `blocks.place(block, position)` is a fill of one block. `shapes.line(block, from, to)` (both ends), `shapes.circle(block, center, radius, Axis.Y, ShapeOperation.Replace)` (flat across the axis, radius 1 to 32) and `shapes.sphere(block, center, radius, ShapeOperation.Replace)` (Replace filled, Outline the shell only, Hollow the shell with the inside cleared; a block is inside within radius + 0.5 of the centre) and `shapes.maze(corner, width, length, wall, floor, seed?)` (issue #182: a perfect maze 5 to 32 blocks each way, walls 2 high on a floor of the floor block, the corner at foot height and the floor replacing the ground layer under it, entrance north, exit south, the same seed gives the same maze). The game does all of it as `blocks.fill`, `shapes.line`, `shapes.circle`, `shapes.sphere` and `shapes.maze` through the robot's FakePlayer, so claim mods apply and a refused block is skipped: every block of the shape's box within 32 blocks of the robot on each axis (else `OUT_OF_RANGE`), at most 32768 blocks in the box (the vanilla `/fill` limit, else `OUT_OF_RANGE`), 256 blocks changed per tick. An owner in creative, an op or a singleplayer owner builds without items; anyone else builds from the robot's inventory (any slot holding the block) and the call ends when it runs out, with the reason in the result's `message` ("I have no stone in my inventory."), which the editor logs and the robot says. A block that is there is broken first and its drop goes into the robot. Each call answers how many blocks it changed. `builder` (`teleportTo`, `move`, `turn`, `face(CompassDirection.North)`, `mark`, `position`, `place`, `line` and `fill` from the last mark to the builder) runs in the editor: it starts at your feet facing south, and only its first position (`positions.toWorld`) and what it builds go to the game. Not yet: `builder.raiseWall`, `tracePath`, `shift`, copy and paste.
`positions.random(from, to)` (alias `randpos`) picks a block in the box. All of these run in the editor; only `toWorld()` of a `pos` or `posLocal` asks the server (`positions.toWorld`), which resolves it against your feet block and horizontal facing at that moment (pitch ignored). It needs no robot.

Containers are the blocks a player opens as a screen (chests, double chests, barrels, hoppers, shulker boxes, furnaces, dispensers, ...), opened by the robot's FakePlayer with an empty hand: a claim mod that refuses the owner's click, a lock without its key, or a chest under a solid block makes every container call answer false or 0, as if there were nothing.
Items are named with the `Item` enum (every vanilla item, issue #125). The item parameters are numbers with an item picker in Blocks (search by English name), so a `Block` still works: `Item.Cobblestone` and `Block.Cobblestone` are the same number, and an old `agent.containerCount(SixDirection.Left, Block.Cobblestone)` shows a block picker in that slot.
`storeItems` and `takeItems` return a boolean, so in Blocks they are reporters: write `if (!agent.storeItems(...))` or `let ok = ...`, because a bare `agent.storeItems(...);` line turns grey in Blocks.

Example: move all cobblestone from the chest on the left to the chest on the right (start with an empty robot).

```
while (agent.containerCount(SixDirection.Left, Block.Cobblestone) > 0) {
    if (agent.takeItem(SixDirection.Left, Block.Cobblestone, 64) == 0) {
        break;
    }
    if (!(agent.storeItems(SixDirection.Right, 0))) {
        break;
    }
}
agent.sortContainer(SixDirection.Left)
```

The `Block` enum in `target/libs/core/blocks.d.ts` is generated from `BlockIds.java` by the JUnit test `BlockEnumTest`.
After changing `BlockIds`, run `./gradlew :fabric:26.3:test`: the test rewrites the file and fails once, the next run passes; commit the file.
The same test fails if a block has no Norwegian label in `target/locales/nb/codeling/core-strings.json`.
The `Item` enum in `target/libs/core/items.d.ts` is generated the same way from `ItemIds.java` by `ItemEnumTest`; the GameTest `itemTableCoversEveryItem` fails when the item registry and the table differ. Items that are also blocks need a Norwegian label; the rest fall back to English.
The table is 26.3's on every Minecraft version, so the editor offers the same blocks and items for every version and a code means the same item everywhere. On 1.21.1, a command with an item that version lacks (a pale oak item, for example) answers the error "minecraft:<id> is not in this Minecraft version"; the 1.21.1 GameTest checks that every 1.21.1 item has a code.

The sim log and a dialog say why a program stopped: an expired editor link after a game restart ("Open the editor from the game again with /codeling"), no world loaded ("Not in a world"), the game stopped the robot (`/robot stop`, closed tab, 10 s without heartbeat), or a rule error such as a move longer than 64 blocks.
The first two also appear as a warning in the game log (logger `codeling/relay`).

Robot commands (server side). Every action is covered by GameTests. Step 1A commands were tried in game; the step 1B ones (place and below) not yet.
`<dir>` is `forward`, `back`, `left`, `right`, `up` or `down`, relative to the robot's facing.

| Command | What |
|---|---|
| `/robot spawn [name]` | Spawn your robot at your feet facing your way, or move it there. Without a name it is your default robot (`agent`); a new named robot needs room under `codeling:max_robots`. Ends the current run. |
| `/robot list` | Your robots, loaded or not, with positions. |
| `/robot remove <name>` | Remove a loaded named robot; its items go to your inventory (the rest drop at your feet). |
| `/robot rename <name> <new>` | Rename a named robot (its queue is cleared). `/robot rename <new>` sets the default robot's name tag only. Name tag items work too (owner and ops). |
| `/robot nametag <on\|off> [name]` | Show or hide the name tag of one robot, or of all your loaded robots. |
| `/robot move <forward\|back\|left\|right\|up\|down> <blocks>` | Queue a move, 1 to 64 blocks, 4 ticks per block. Stops early at a solid block; open doors and trapdoors let it through. |
| `/robot turn <left\|right>` | Queue a 90 degree turn. |
| `/robot stop [name]` | Without a name: drop the queues of all your robots and end the run. A program running in the editor halts at once and shows "Program stopped". With a name: drop only that robot's queue. |
| `/robot status` | Position, facing, queue length, run id. |
| `/robot stop all` | Ops only (gamemaster level): stop every robot. |
| `/robot place <dir>` | Place the block in the selected slot. |
| `/robot destroy <dir>` | Break the block as a player with a diamond pickaxe would; drops go into the robot. Bedrock and protected blocks stay. |
| `/robot inspect <dir>` | Encoded block id (`id \| data << 16`, pre-flattening numbers), `-1` for blocks not in the table. |
| `/robot detect <dir>` | `true` if the block would stop a move. |
| `/robot slot <1-27>` | Select a slot. |
| `/robot give <block> <count> [slot]` | Creative-style give (`setItem`): creative mode or the gamerule `codeling:free_items` only. `<block>` is a name such as `stone`, `oak_planks`, `red_wool`, an item id such as `iron_ingot`, or an encoded number. Slot defaults to the selected one. |
| `/robot inventory` | Opens the robot's inventory screen when the robot is within reach; otherwise prints the selected slot and every non-empty slot. |
| `/robot collect` | Pick up items within 1 block. |
| `/robot drop <dir>` | Drop everything (`dropAll`). |
| `/robot assist <true\|false>` | `PLACE_ON_MOVE`: each move step places the selected block in the spot it leaves. |
| `/robot gravity <true\|false>` | Gravity mode (`setGravity`): fall after sideways steps, step up 1-block bumps. Saved on the robot. |
| `/robot sethome` | Saves where the robot stands as its home (`agent.setHome`). Saved on the robot. |
| `/robot come` | Walks the robot to your block by the `moveTo` path search; when there is no path it teleports there instead (issue #77). Same dimension only (else "use /robot spawn") and loaded chunks only. Answers `walked` or `teleported`. |
| `/robot camera` | Toggles the view through your robot's eyes (issue #176); `/robot camera off` ends it. Also a key under Controls, Codeling (unbound by default). Escape opens the pause menu as usual and keeps the camera on. The gamerule `codeling:robot_camera` (`owner` default, `ops`, `off`) decides who may (issue #175). |
| `/robot camera follow` | Toggles the camera behind and above your robot (issue #177), looking down at 55 degrees: mouse orbits, scroll wheel sets the distance (2 to 12), walls pull it in. The camera eases after the robot's turns and in and out at walls, every frame, instead of jumping. Own key "Robot camera: follow". Same rules; programs use `agent.camera(CameraMode.Follow)`. |

Every command that acts on one robot takes an optional robot name as its last word (`/robot move forward 3 Miner`, `/robot give stone 5 1 Miner`, `/robot camera follow Miner`); without it, it acts on your default robot.
All commands except `spawn`, `stop`, `status` and `inventory` go through the same validator and queue as the browser commands; `come` is built from your position and skips the validator.
A robot that is killed drops its inventory where it stood. `/robot spawn` in another dimension moves the inventory to the new robot.
If the old robot was unloaded when you spawned a new one, it keeps its items and drops them where it stood when its chunk loads again (once). Any other stale copy of a robot (an older save, a structure-block copy) is removed without dropping, so items never double.
Right-click the robot to open its 27 slots as a chest screen (the selected slot has a gold frame). Only the owner and ops can open it; others get "This is not your robot." The screen is the robot's own inventory, so a running program sees every change at once.

## Open in IntelliJ IDEA

1. File > Open, choose the repo root (not `fabric/`), and pick "Open as Project" if asked.
2. Trust the project. IntelliJ detects `settings.gradle.kts` and starts a Gradle sync.
3. If sync fails with "Dependency requires at least JVM runtime version 25": Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM, choose a Java 25 JDK (IntelliJ can download one: "Download JDK", version 25), then reload the Gradle project. Normally the Gradle daemon toolchain picks Java 25 by itself.
4. Project Structure > Project > SDK: Java 25 (language level 25).
5. Wait for the sync to finish. Loom then generates the run configurations "Minecraft Client (:fabric:26.3)" and "Minecraft Server (:fabric:26.3)" (and the same for 1.21.1).
6. Shared run configurations in `.run/`:
   - "Relay standalone (no Minecraft)": the editor server with `--print-url`.
   - "target: build via Gradle (Community)": npm ci, build and staticpkg through the root Gradle task `buildEditor`.
   - "target: build + staticpkg (npm)" and "target: pxt serve (npm)": npm run configurations.

The npm run configurations and good TypeScript editing in `target/` need IntelliJ IDEA Ultimate (or the JavaScript and TypeScript plugin).
On Community, use the Gradle configuration above or a terminal.

## Licence

MIT, see [LICENSE](LICENSE).
