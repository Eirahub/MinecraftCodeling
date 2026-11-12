# Changelog

Every user-visible change to Codeling is listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the mod version follows
[Semantic Versioning](https://semver.org/) (see "Versioning" in [CONTRIBUTING.md](CONTRIBUTING.md)).

## [Unreleased]

### Added

- World queries (issue #87): `gameplay.timeOfDay()` and `gameplay.isRaining()` read the time and weather of your
  dimension, and `agent.lightLevel()`, `agent.biome()` and `agent.temperature()` read the robot's own block, in
  Blocks, JavaScript and Python, with a new "Torches in the dark" example.
- More events (issue #89): `blocks.onBlockBroken(block, handler)` ("on block broken") runs when you break that kind of
  block by hand, `agent.onRedstonePowered(handler)` ("on agent redstone powered") when your default robot's block gets
  a new redstone signal, and `gameplay.onDayStart(handler)` ("on day start") when the time of day wraps round to the
  morning, in Blocks, JavaScript and Python. Never for other players' breaks or your robot's own. At most 16 blocks
  per run; the 16 waiting events per player are shared with chat and death.

### Fixed

- `scripts/setup-multimc.ps1 -Release` stopped with a bare 404 for a draft release or a tag without a release. It
  now says so and shows the `gh release download` and `-JarDir` commands that work instead.

## [0.0.2] - 2026-11-12

The second pre-release, for testing.

### Added

- `loops.pause(ms)` and `loops.forever` with the blocks "pause (ms)" and "forever" in the Loops category: wait in
  one part of a program, and repeat code until the program is stopped (issue #215).
- Position math in the Positions category: `distanceTo` (straight line, with decimals), `subtract` and `scale` on a
  position, and `positions.sortByDistance` and `positions.nearest` for a list of positions, so Blocks can pick the
  closest of a radar list without a function argument (issue #219).
- An Examples gallery on the editor's start page with the seven example programs the editor suite tests, each with a
  title, one sentence on what it teaches and an icon, in English and Norwegian. Opening one makes a new project in
  Blocks, JavaScript or Python. It works offline like the rest of the editor (issue #216).
- Debug mode in the editor (the bug button under the simulator): breakpoints and Step in Blocks, JavaScript and
  Python, the running block or line highlighted, the values of the variables while the program is stopped, and
  Slow-Mo, which highlights each block as it runs. A robot command always finishes before the program stops.
  "Exit Debug Mode" stops the program; pxt would have run it again from the start (issue #217).
- A robot command the game refuses throws an error into the program, the text `CODE: message`, so JavaScript
  `try`/`catch` and Python `try`/`except` can catch it and go on. Not caught, it stops the program with the "Robot
  error" dialog as before, and the robot log names the command. Stop, `/robot stop`, a lost connection, `STOPPED` and
  `VERSION` still end the program and cannot be caught. The player guide lists the error codes (issue #218).
- `control.assert(condition, message)` and `control.fail(message)`, with the blocks "check ... or stop with" and
  "stop with error" in the Logic category. Python `assert` and `raise` now compile, and they, JavaScript `throw` and
  the two blocks all stop the program with the message in a "Program error" dialog; debug mode marks the block or line
  it came from. The "Room and fence" example checks where its line of glass ends (issue #221).
- A Player category with MakeCode's `player.say`, `player.position`, `player.getOrientation`, `player.name` and
  `player.teleport` (issue #26). They are about the program's own player only and need no robot. Unlike MakeCode,
  `say` shows the message to that player only, as plain text of at most 256 characters. `teleport` works for the host
  of a singleplayer world or an op, in the player's own dimension, inside the world border and the build height;
  anyone else gets `NO_PERMISSION`. One player call per tick, at most 16 waiting (`RATE_LIMITED`).
- Chat and death events in the Player category (issue #27): `player.onChat(keyword, handler)` ("on chat command"),
  `player.onDied(handler)` ("on player died") and `player.runChatCommand(text)` ("run chat command"), in Blocks,
  JavaScript and Python. A chat line whose first word is a keyword of the sender's running program (or any line for
  `*`) is not broadcast; the program gets the keyword and up to three numbers parsed as JavaScript's `parseInt`
  (0 when missing or not a number), never the line. Lines from other players, and every line once the run ends, are
  broadcast as usual. `/codeling chat <text>` does the same for servers whose chat plugins take chat lines first.
  `runChatCommand` runs the program's own handlers in the editor and sends nothing to the game. A program with
  handlers keeps running after its last line until Stop or `/robot stop`. At most 16 keywords of 32 characters per
  run, and 16 events waiting per player.
- Mod jars for Minecraft 26.2 and for 26.1 to 26.1.2 (Fabric and NeoForge, Java 25), and for 1.21.11, 1.21.9 to 1.21.10 and 1.21.6 to 1.21.8, 1.21.5, 1.21.4 and 1.21.2 to 1.21.3 (Java 21).
- `scripts/setup-multimc.ps1` sets up a MultiMC instance for every loader and Minecraft version Codeling supports, to test the jars in a launcher.

### Changed

- `agent.say` turns the section sign (the colour code character) into a space, as `player.say` does.

### Fixed

- The Fabric jar for Minecraft 1.21.1 accepted every 1.21.x version and crashed on 1.21.2 and later. It now
  accepts 1.21 and 1.21.1 only, so Fabric names the right jar instead.
- A world moved from Minecraft 1.21.x to 26.x keeps its robots: 26.x reads the robot links from
  `data/codeling_robots.dat` when `data/codeling/robots.dat` is missing.
- Positions in the lists from `agent.radar` and `agent.findBounds` lost their methods, so `toString` or `toWorld` on
  one of them failed (issue #219).

## [0.0.1] - 2026-11-11

The first public version, a pre-release for testing.

### Added

- Mod jars for Fabric and NeoForge, on Minecraft 26.3 (Java 25) and 1.21.1 (Java 21).
- A robot per player, spawned with `/robot spawn`, with a 27-slot inventory. The game rule `codeling:max_robots`
  (1 to 16, default 1) allows more robots with names, which programs reach through `robots.get("name")`.
- A browser editor (a pxt target) with Blocks, JavaScript and Python, in English and Norwegian. `/codeling` opens it;
  the mod serves it from `127.0.0.1` with a per-session token, and it makes no requests to other hosts.
- Robot blocks and calls for movement (move, turn, move to, follow path, go home), building, inspecting, sensors
  (radar, liquids, safety), measuring, inventory, containers, crafting and stonecutting, and `say`.
- Positions relative to the player, to where the player looks, or in the world, and building helpers: `blocks.fill`,
  `blocks.place`, lines, circles, spheres, mazes and a builder.
- `control.runInParallel` to run several robots at the same time.
- `/robot` chat commands for every basic action, plus `list`, `remove`, `rename`, `nametag`, `come`, `sethome` and
  `stop all` for ops.
- A robot camera: look through the robot's eyes or follow it from behind (`/robot camera`, key binds). The game rule
  `codeling:robot_camera` (off, owner, ops) controls who may use it.
- The game rule `codeling:free_items` for free items in survival.
- Multiplayer: a player can only program their own robots. Robots act through a fake player with the owner's
  identity, so protection and claim mods apply to them.
- Per-tick limits on actions, scanned blocks, path search and placed blocks, and a stop after 10 seconds without
  contact from the editor.
- Projects saved by an older editor version load with their item and block values kept.
- A player guide in English and Norwegian.
- `SECURITY.md`: how to report a vulnerability privately, and what is in scope.
- Mod list links: source and issue tracker in `fabric.mod.json` and `neoforge.mods.toml`.
- A name for the `codeling:max_robots` game rule on the Edit Game Rules screen.
- `LICENSE` and `THIRD-PARTY-NOTICES.md` in every jar under `META-INF/`, and a full list of the libraries inside the
  packaged editor.
- A release workflow that builds the four jars from a `v*` tag and publishes them with SHA-256 sums.

### Security

- `/codeling` no longer puts the editor link with its token on a command line, where other users on the same computer
  could read it (issue #191). It opens a one-time launcher file that only you can read, deleted after a minute, and the
  chat line has a link that copies the editor link for browsers that cannot open the file.
- Robots follow their owner's rules (issue #53): no edits inside spawn protection unless the owner is an op, no placing
  or breaking for an owner in adventure mode, and nothing at all for an owner in spectator mode.

