# Changelog

Every user-visible change to Codeling is listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the mod version follows
[Semantic Versioning](https://semver.org/) (see "Versioning" in [CONTRIBUTING.md](CONTRIBUTING.md)).

## [Unreleased]

### Added

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

## [0.0.1] - unreleased

The first public version, not tagged or released yet. At release the lines under Unreleased move into this section
and "unreleased" becomes the release date.

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
