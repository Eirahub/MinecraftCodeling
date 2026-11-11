# MakeCode-style robot programming for Minecraft Java: research

Research date: 2026-11-09. Every claim carries a source link. Items I could not confirm are marked **[UNVERIFIED]** and collected in the last section.

## Recommendation (read this first)

**Build path (updated 2026-11-09, see [architecture.md](architecture.md)).** Do not try to reuse Microsoft's MakeCode Minecraft target (Microsoft's only public Java mod, Malmo, is a 1.11.2 AI-research mod and not a base to build on). Build a Fabric mod (Minecraft 26.3, Java 25) that owns a Java-side robot ("turtle-like agent") with a small, explicit command API. Build the editor as a custom pxt target (MIT), which gives blocks, JavaScript and Python with round trip. The client mod serves it on `127.0.0.1` and the player opens it in their normal browser. **The program runs in the pxt simulator in the browser (the MakeCode model)** and sends one command at a time through the client mod to the server, which validates each command and executes it on the server thread under a per-tick budget. No user code runs on the server. Add an in-game embedded browser (MCEF) later, only if its 26.x support appears.

**Reusable parts.**
- The MakeCode/PXT framework (MIT) as the editor, compiler and simulator. Current pxt uses upstream Blockly (Apache-2.0) directly; `pxt-blockly` is no longer a pxt dependency.
- The *shape* of the Agent API (public docs) and CC: Tweaked's turtle API (public docs) as the behavioral spec.
- Ideas, not code, from CC: Tweaked (mostly MPL-2.0, mixed licenses).
- Microsoft's own archived Malmo mod (MIT, Forge 1.11.2) as a design reference for the socket/command layer only (section 5). The code itself is obsolete.

**Not reusable.** The `pxt-minecraft` target (blocks, agent glue, in-game host) is not public (confirmed: absent from `github.com/orgs/microsoft/repositories?q=minecraft`, and `gh api repos/microsoft/pxt-minecraft` returns 404), and the Code Connection source is not available. The Bedrock `/connect` WebSocket protocol does not exist on Java Edition.

**Biggest risks.**
1. No Minecraft target source to copy, so the block set and the Agent semantics must be re-specified from docs.
2. MCEF (in-game Chromium) has no confirmed release past 1.21.4/1.21.5, while current Java is 26.x (Java 25, unobfuscated). Plan on the external-browser route.
3. Player-driven robots on a multiplayer server: user code runs in the browser, but the server still needs strict command validation, a hard tick budget, permission gating and rate limits. This is the real engineering cost.
4. Branding and legal: do not use "MakeCode" or "Minecraft" in the product name, and do not copy Microsoft's assets. Mojang's usage guidelines text could not be fetched here (see section 2).

## 1. Architecture: how MakeCode talks to Minecraft

| Piece | What is established | Source |
|---|---|---|
| Editor | Browser-based MakeCode editor at `minecraft.makecode.com`. Blocks, JavaScript and Python. | [MakeCode blog post, 2017](https://github.com/microsoft/pxt/blob/master/docs/blog/minecraft-code-builder.md), [Education support article on Python Notebooks removal](https://edusupport.minecraft.net/hc/en-us/articles/34761715494292-Changes-To-Code-Builder-Removing-Support-for-Python-Notebooks) (as quoted in search results) |
| 2017 transport | "Code Connection companion app" bridged MakeCode and the game. | [pxt blog, May 2017](https://github.com/microsoft/pxt/blob/master/docs/blog/minecraft-code-builder.md) |
| Command link | `/wsserver <uri>` (alias `/connect`) connects the game to an external WebSocket server so it can exchange command messages. Bedrock and Education only. Permission level 2 on Bedrock, 0 in Education. | [Minecraft Wiki: /wsserver](https://minecraft.wiki/w/Commands/wsserver) |
| Code Connection | Separate window, user picks an editor, pastes `/connect` into chat, then meets the Agent. Written in C++, source not available, released 2017-07-19, last version 1.50, **discontinued 2023-12-01**. | [Minecraft Wiki: Code Connection](https://minecraft.wiki/w/Code_Connection) |
| Code Builder | In-game panel opened with `C` (not `/code`). MakeCode home screen appears inside the Code Builder window; you press Resume Game to return and the code then runs. | [MakeCode setup page](https://minecraft.makecode.com/setup/minecraft-education-edition) |
| Editors today | Python Notebooks removed in 1.21.132 (Feb 2026). MakeCode is the only coding experience. | [Education support](https://edusupport.minecraft.net/hc/en-us/articles/34761715494292-Changes-To-Code-Builder-Removing-Support-for-Python-Notebooks) (via search snippet) |
| Page navigation | The `codebuilder navigate` command opens a URL (for example a MakeCode tutorial) in Code Builder. | [community thread](https://edusupport.minecraft.net/hc/en-us/community/posts/4407412063764-codebuilder-navigate-command) (via search snippet only) |

**Wire format (community reverse engineering, Bedrock).** JSON frames with a `header` and `body`. A subscribe frame is `{"header":{"requestId":<uuid>,"messagePurpose":"subscribe","version":1,"messageType":"commandRequest"},"body":{"eventName":"<Event>"}}`, one subscription per event name. Source: [jocopa3 gist](https://gist.github.com/jocopa3/5f718f4198f1ea91a37e3a9da468675c). The same gists disagree on whether `messageType` is in the subscribe header, and the protocol changed in Bedrock 1.18.30 ([community thread, 403 on fetch, snippet only](https://edusupport.minecraft.net/hc/en-us/community/posts/7129239047316--Technical-Issue-WebSocket-Specification-Changes)). Libraries that implement it: [mcwss (Go)](https://github.com/faishasj/mcwss), [CodeConnectFix (MIT, MITM server for Code Connection 1.50 on newer Bedrock)](https://github.com/lrocher/CodeConnectFix).

**What this means for Java.** The `/connect` WebSocket is a Bedrock/Education feature (see wiki above). On Java you would define your own transport. Whether the in-game MakeCode host uses the same WebSocket internally, or a private bridge to an embedded web view, **[UNVERIFIED]**: no public source describes it.

## 2. Source, license and trademarks

| Repo | License | Status | Use in a Java mod |
|---|---|---|---|
| [microsoft/pxt](https://github.com/microsoft/pxt) | MIT (LICENSE in repo) | Active, pushed 2026-11-06 | Legal to reuse. Large TypeScript framework (compiler, simulator, webapp). Contains no Minecraft target. |
| [microsoft/pxt-blockly](https://github.com/microsoft/pxt-blockly) | Repo LICENSE is MIT (Microsoft); GitHub reports NOASSERTION because upstream Blockly files keep their own notices (see its THIRD-PARTY-NOTICES.txt) | Active, 2026-10-28 | Fork of Blockly used by PXT. Check notices before copying. |
| [RaspberryPiFoundation/blockly](https://github.com/RaspberryPiFoundation/blockly) (formerly google/blockly) | Apache-2.0 | Active, release blockly-v13.4.0 (2026-10-13) | Preferred editor engine. Keep NOTICE and license text. |
| `microsoft/pxt-minecraft` | n/a | **Returns 404** (private or removed). MakeCode docs still link to it. | Cannot be reused. A [community thread](https://edusupport.minecraft.net/hc/en-us/community/posts/360072423551-pxt-minecraft-source-code) asked about it (403 on fetch, title only). |
| [Microsoft MIT-licensed Minecraft MakeCode projects](https://github.com/microsoft/makecode-minecraft-music) (music, space, castle-builder, roller-coaster, mob-events, pixel-art, sound-and-music ext) | MIT | Some active in 2026 | Example *programs/extensions* written against the Bedrock API. Useful as test programs, not as engine code. |
| [ProjectHSI/pxt-minecraft-types](https://github.com/ProjectHSI/pxt-minecraft-types) | MIT | Last push 2023-07-19, docs stub | Community "approximate types" of the Minecraft target. Third-party, incomplete. Can help derive the API signatures. |

**Trademarks.** The pxt README states: "MICROSOFT, the Microsoft Logo, and MAKECODE are registered trademarks of Microsoft Corporation" and can only be used per Microsoft's trademark guidelines; questions go to makecode@microsoft.com ([pxt README](https://github.com/microsoft/pxt/blob/master/README.md)). The MIT license covers code, not those marks. Do not name the product MakeCode, and do not imply Microsoft endorsement.

**Mojang.** Minecraft and Agent are Mojang/Microsoft IP. "Agent" is a feature name used in Bedrock/Education; I found no trademark filing for it **[UNVERIFIED]**. Use a different product name for your robot to stay clear. Search snippets of the Minecraft EULA and Usage Guidelines say products should carry a "not approved by or associated with Mojang or Microsoft" disclaimer and that commercial use needs separate permission ([EULA](https://www.minecraft.net/en-us/eula), [Usage Guidelines via search](https://www.minecraft.net/en-us/usage-guidelines)). Both pages timed out when fetched, so **the exact text is UNVERIFIED**. Read both yourself before release.

## 3. API surface (Bedrock/Education MakeCode)

Official reference root: [minecraft.makecode.com/reference](https://minecraft.makecode.com/reference). Namespaces on that page: `player`, `blocks`, `mobs`, `agent`, `builder`, `gameplay`, `positions`, `shapes`, plus loops/logic/math/variables blocks. Per-function source files are not public (pxt-minecraft is not public), so the mapping from block to game command is documented only where the public Agent command table shows it.

**Verified from source (call sites in [pxt-minecraft-HOC2019 `extension.ts`](https://github.com/microsoft/pxt-minecraft-HOC2019/blob/master/extension.ts), MIT, archived, v0.0.55).** This is a Microsoft-written Hour of Code extension that calls the real target API, so the signatures below are shown by usage, not by declaration. Its `enums.d.ts` is a one-line placeholder ("Will be auto-generated if needed."), so no enum definitions are published there; enum members are known only where used.

| Call (as used) | Shape | Notes |
|---|---|---|
| `agent.inspect(AgentInspection.Block, dir)` | returns a block id comparable with `Block.*` constants and numeric ids | Used with `indexOf` on numeric id arrays (for example 31, 175) and `== Block.Air`. Legacy numeric ids are still in play. |
| `agent.destroy(dir)`, `agent.place(dir)`, `agent.attack(dir)` | `dir: SixDirection` | `place` takes only a direction; the item comes from the selected slot. |
| `agent.setItem(item, count, slot)` | item, quantity, slot | Setting `AIR` with count 1 clears a slot. |
| `agent.setSlot(slot)` | selects the active slot | Slots are used as 1 to 27 (`MIN_SLOT = 1`, `MAX_SLOT = 27`), so 1-based, matching the 27-slot inventory in section 4. |
| `agent.collectAll()` | no args | |
| `agent.getItemCount(slot)` | returns a number | |
| `agent.getPosition().getValue(Axis.X)` | returns a position object; read per axis | `Axis.X`, `Axis.Z` seen. |
| `blocks.testForBlock(block, position)` | returns boolean | The extension notes it "takes one tick" and uses it as a tick-based delay. |
| `blocks.place(block, world(x, y, z))` | block, position | `world(...)` is a global shorthand for a world-space position. |
| `positions.create(x, y, z)`, `positions.createWorld(x, y, z)` | position values | `create(0,0,0)` is used as a relative origin. |
| `mobs.execute(mobs.target(TargetSelectorKind.NearestPlayer), positions.create(0,0,0), "<command string>")` | target selector, position, raw command | Escape hatch that runs any vanilla command, for example `scoreboard players set @s success 1`. This is how `mobs.*` reaches game commands. |
| `loops.pause(ms)` | milliseconds | |

Enums seen: `SixDirection` (`Forward`, `Up`, and a global `DOWN`), `AgentInspection.Block`, `Axis` (`X`, `Z`), `TargetSelectorKind.NearestPlayer`, `Block.Air`, `Block.GoldBlock`, plus global constants such as `GRASS`, `FERN`, `REDSTONE_TORCH`, `PLANKS_ACACIA`, `CARROTS`, `GOLD_BLOCK`, `AIR`. The full member lists of `SixDirection` and the other enums are not in this source.

| Namespace | Examples seen in docs | Maps to |
|---|---|---|
| `agent` | `move`, `turn`, `detect`, `inspect`, `place`, `destroy`, `till`, `attack`, `collect`, `collectAll`, `setSlot`, `transfer`, `drop`, `dropAll`, `getItemCount/Space/Detail`, `setItem`, `teleport`, `teleportToPlayer`, `getPosition`, `getOrientation`, `setAssist`, `interact` ([reference](https://minecraft.makecode.com/reference/agent), descriptions largely inferred from names). Verified by HOC2019 usage: `inspect`, `destroy`, `place`, `attack`, `setItem`, `setSlot`, `collectAll`, `getItemCount`, `getPosition` | `agent <sub>` commands, see section 4 |
| `player` | `say`, teleport, position queries | chat and player commands **[UNVERIFIED mapping]** |
| `blocks` | `place`, `fill`, `replace`, `testForBlock` | `setblock`/`fill`/`testforblock` style commands **[UNVERIFIED mapping]**. `place(block, pos)` and `testForBlock(block, pos)` signatures verified (HOC2019). |
| `mobs` | `spawn`, `kill`, `give`, `teleport`, `execute`, `target` | `summon`, `kill`, `give`, `tp` **[UNVERIFIED mapping]**. `mobs.execute(target, pos, commandString)` and `mobs.target(TargetSelectorKind.*)` verified (HOC2019): runs a raw command. |
| `builder` | `teleportTo`, `move`, `place`, `mark`, `line/circle/shape/mount` with a "builder" cursor | client-side library that emits `setblock`/`fill` **[UNVERIFIED]** |
| `shapes` | `line`, `circle`, `sphere`, and similar | emitted as many block commands **[UNVERIFIED]** |
| `gameplay` | `setGameMode`, weather, time, game rules, events | `gamemode`, `weather`, `time`, `gamerule` **[UNVERIFIED mapping]** |
| `positions` | `create`, `createWorld`, `toWorld`, `fromPlayer`, `fromAgent` | local helpers, no command **[UNVERIFIED]**. `create` and `createWorld` verified (HOC2019). |

The latest block additions listed by Microsoft: weather, Math constrain/map/constants, spawn-egg picker, Agent as a spawnable mob ([2025 release post](https://github.com/microsoft/pxt/blob/master/docs/blog/minecraft/2025-release.md)). Other release notes: [2022](https://github.com/microsoft/pxt/blob/master/docs/blog/minecraft/2022-release.md), [2023](https://github.com/microsoft/pxt/blob/master/docs/blog/minecraft/2023-release.md).

**Recommended scope for a Java v1** (small and testable): `agent` movement, turn, detect/inspect, place/destroy/till/attack, inventory slots, plus `player.say`, `blocks.place/fill`, `positions`, and loops/logic/math/variables from stock Blockly. Defer `mobs`, `builder`/`shapes` and `gameplay` (these are mostly convenience over vanilla commands and are the main grief vector on shared servers).

## 4. Agent behavior

Primary sources: [Minecraft Wiki: Agent](https://minecraft.wiki/w/Agent), [Minecraft Wiki: /agent](https://minecraft.wiki/w/Commands/agent). These are community wiki pages, not Mojang docs.

| Topic | Behavior |
|---|---|
| Movement | `agent move <forward/back/left/right/up/down>`, `agent turn <left/right>` (90 degrees). Floats in midair with no solid block below. Passes through blocks, vehicles and projectiles. Education 1.20.10.0+: no range restriction from the player. |
| Collision | `detect` tests for a "collidable block." The wiki gives no further collision rules. Agent can push pressure plates and operate doors, buttons, levers, fence gates. |
| Inventory | 27 slots. Commands: `transfer`, `drop`, `dropall`, `getitemcount`, `getitemspace`, `getitemdetail`, `setitem` (undocumented on the wiki). |
| Detect / inspect | `detect`, `detectredstone`, `inspect` (block name), `inspectdata` (data value). Education 1.20.10.0+: line-of-sight picking of the item to detect or interact with, up to 1 block. |
| Place / destroy | `place <slot> <dir>`, `destroy`, `till`, `collect <item>` (within 1 block), `attack`. Can break some blocks that are unbreakable in Survival (bedrock, command blocks), but not allow/deny blocks. |
| Lifecycle | `agent create`, `agent tp`. Teleports between dimensions with the player. Name tag "<player>.Agent". Spawn egg does not work in Bedrock. |
| Damage | Invulnerable to all damage, but Wardens still target it. |
| Permissions | `/agent` permission level is 1 on Bedrock, 0 on Education. Agent commands can only be run from the connected WebSocket side. The wiki does not discuss worldbuilder. A secondary site says advanced commands (spawning mobs, placing blocks not in the agent's inventory) require Operator permission, worldbuilder defaults to false, and there is no native "relative to agent position" block and unreliable half-block detection ([mc-mod.net reference](https://www.mc-mod.net/education-edition-code-builder-reference/), low-quality source). **Worldbuilder behavior is therefore UNVERIFIED.** |

**Verified from HOC2019 source** ([extension.ts](https://github.com/microsoft/pxt-minecraft-HOC2019/blob/master/extension.ts)): each Agent call takes game time and the program waits for it. The extension pads time with `blocks.testForBlock(...)` calls because "it takes one tick (this is more reliable than waiting for X milliseconds because it's not platform dependent)", and elsewhere uses `loops.pause(500)`. Inventory slots are addressed 1 to 27. `agent.place(dir)` places whatever is in the selected slot, so the lesson code sets a slot with `setItem` then `setSlot` first, and clears it afterwards with `setItem(AIR, 1, slot)`. `agent.inspect` returns a block id compared against numeric ids and `Block.*` constants, and `agent.getPosition()` is read per axis. The Java mod should therefore define "one Agent action takes at least one tick" explicitly. `agent.move` and `agent.turn` signatures do not appear in this source and stay unverified.

**Contrast: CC: Tweaked turtle** ([API](https://tweaked.cc/module/turtle.html)). Moves one block per call, has fuel (default 20,000 for normal, 100,000 for advanced), movement costs fuel and digging/placing/turning do not, actions return `false` plus a reason instead of throwing. This is a ready-made, safer-for-servers design (fuel prevents unbounded agents) and the Agent's no-fuel flying/phasing model is a design choice you should make explicitly rather than copy.

## 5. Prior art on Java Edition

| Project | What it is | License | Last activity | Notes / gap |
|---|---|---|---|---|
| [CC: Tweaked](https://github.com/cc-tweaked/CC-Tweaked) | Programmable computers and turtles in Lua. Forge/Fabric/NeoForge. | Mixed per file under `LICENSES/` (Apache-2.0, CC-BY, CC0, MIT, MPL-2.0 and a CCPL licenseref); main code MPL-2.0 per its `REUSE.toml`. Modrinth shows `LicenseRef-CCPL`. | Release `v26.3-1.121.0` on 2026-11-03; branch `mc-26.3` exists. Millions of downloads. | Proven turtle design, sandboxed Lua VM (Cobalt, now archived and moved to Codeberg). Text only: no blocks. Does not ship a Blockly editor. |
| [Project Malmo](https://github.com/microsoft/malmo) (`Minecraft/` folder) | Microsoft-authored Java (Forge) mod that lets external programs (Python, C++, Java, C#) control a player for AI research. Package `com.microsoft.Malmo`. Target: Minecraft 1.11.2, Forge 13.20.0.2228, ForgeGradle 2.2, mappings `snapshot_20161220` ([Minecraft/build.gradle](https://github.com/microsoft/malmo/blob/master/Minecraft/build.gradle)). | MIT (GitHub SPDX) | Archived, last push 2025-09-03 | Design reference only (see below). Not a block editor, no separate robot entity: it drives the real player. |
| [CC: Androids](https://modrinth.com/mod/cc-androids) | CC: Tweaked addon, programmable androids | CC-BY-NC-4.0 | 2025-07, MC 1.20.1 | Non-commercial licence; not a base for reuse. |
| [BlocklyCraft](https://github.com/bgon/BlocklyCraft) | Blockly editor that generates ScriptCraft JavaScript plugins. | MIT | **2017-04-24** | The only Blockly+Minecraft Java project I found. Dead, built on ScriptCraft, no robot. Shows the "browser Blockly -> JS -> game" model works. |
| [ScriptCraft](https://github.com/walterhiggins/ScriptCraft) | JavaScript plugins for Bukkit | MIT | 2021-08-27 | Dead. Has a "Drone" builder object. |
| [RaspberryJuice](https://github.com/zhuowei/RaspberryJuice) | Bukkit plugin implementing the Minecraft Pi socket API; used by Scratch and Python tools. | Other/unclear (GitHub reports NOASSERTION) | 2024-03-03 (a [fork](https://github.com/sakebomb/RaspberryJuice) was updated 2026-11-03) | Closest Java equivalent to a "connect external editor over a socket" model. Server plugin, not Fabric/NeoForge. [mcpi-scratch (GPL-3.0)](https://github.com/olliecheng/mcpi-scratch) bridges it to Scratch 2.0, dead since 2018. |
| [Robotics](https://modrinth.com/mod/robotics) | Robot mod | MIT | 2024-09-22, Forge, MC 1.20.1 | Not Blockly (programming method not verified). |
| [Redbyte](https://modrinth.com/mod/redbyte) | Programmable robots, custom text language | MIT | 2026-04-08, Forge, MC 1.21.7 to 1.21.9 | Text language, no editor in a browser. |
| VRobot (uses the Visibilis visual language) | Visual-coded robots | **[UNVERIFIED]** | **[UNVERIFIED]** | Found only in a search summary; could not confirm via Modrinth API. Check before claiming it as prior art. |
| [MCEF](https://github.com/CinemaMod/mcef) | Chromium inside Minecraft, Fabric and NeoForge | LGPL-2.1 | Repo last pushed 2025-08-11; branches up to 1.21.5; Modrinth builds up to 1.21.4 (2025-01-16); Chromium 116 | See section 6. |

**Malmo as a reference (read from `Minecraft/src/main/java/com/microsoft/Malmo/`).**
- *Transports.* (1) Native "AgentHost": a TCP line protocol where each newline-terminated text line is handed to a command handler (`Utils/TCPInputPoller.java`); mission control ports start at 10000 and dynamic ports run 10100 to 11000 (`Utils/AddressHelper.java`). I did not read the exact per-command line format. (2) "MalmoEnv" (`Client/MalmoEnvServer.java`), enabled by config key `env` and a launch `-port` (its README uses 9000): a single socket, every message a 4-byte big-endian length followed by UTF-8 text. It starts with a hello of `<MalmoEnv` plus the mod version, then XML-tagged requests `<Init>`, `<MissionInit>`, `<Step_N>actions</Step_N>`, `<Peek>`, `<Find>`, `<Status>`, `<Quit>`, `<Exit>`, `<Close>`, `<Echo>`. The `<Step>` reply is length-prefixed observation bytes, then a double reward, a done byte, a "sent" byte, and optionally an info string and a turn key (for lock-step multi-agent turns). Commands are queued from the socket thread and consumed by the game, with a 3 second wait on a condition variable before replying.
- *Mission schema.* Missions are XML validated by XSDs in [`Schemas/`](https://github.com/microsoft/malmo/tree/master/Schemas) (`Mission.xsd`, `MissionInit.xsd`, `MissionEnded.xsd`, `MissionHandlers.xsd`, `Types.xsd`). The Java bindings are generated from them at build time. `MissionHandlers.xsd` is the authoritative command vocabulary: continuous (`move`, `strafe`, `pitch`, `turn`, `jump`, `crouch`, `attack`, `use`), discrete (`move`, `jumpmove`, `strafe`, `turn`, `movenorth/east/south/west`, `jumpnorth/...`, `jump`, `look`, `attack`, `use`, `jumpuse`), absolute (`tpx`, `tpy`, `tpz`, `tp`, `setYaw`, `setPitch`), inventory (`swapInventoryItems`, `combineInventoryItems`, `discardCurrentItem`, `hotbar.1` to `hotbar.9`), `craft`, `craftNearby`, `smeltNearby`, `place`, `chat`, `quit`, and a human-level keyboard set (`forward`, `left`, `back`, `right`, `sneak`, `sprint`, `drop`, `pickItem`, `moveMouse`, ...). Handlers extend `CommandBase` and are grouped by `CommandGroup` per mission.
- *Observations.* Producers (`ObservationFrom*Implementation`: full stats, grid, ray, nearby entities, inventory, chat, ...) build JSON with Gson (`Utils/JSONWorldDataHelper.java`); rewards and quit conditions are separate handler families (`RewardFor*`, `AgentQuitFrom*`). I did not read every producer's JSON shape.
- *Timing.* The mod can overclock: `ModSettings/MsPerTick` in `Mission.xsd` sets `TimeHelper.serverTickLength` (default 50 ms) and the client clock speed via `TimeHelper.setMinecraftClientClockSpeed` (`Utils/TimeHelper.java`). `ServerStateMachine` warns when fewer ticks than the target occur per second. A step is lock-step with the observation, and `TurnBasedCommandsImplementation` adds a turn scheduler. Socket reads run on their own threads, command handlers on the Minecraft game threads.
- *Useful for the new mod:* the split of socket thread (enqueue) and game thread (execute), the handler-per-command-family structure, the one-observation-per-step contract, the explicit tick-rate control, and a declarative command vocabulary (an XSD here) that keeps clients and mod in sync. A block set could be generated from such a vocabulary.
- *Obsolete:* everything Forge 1.11.2 specific (ForgeGradle 2.2, `EntityPlayerMP`, MCP names), the ClassTransformer based overclocking (`OverclockingClassTransformer.java`), the XML+JAXB mission machinery, and the video/depth producers. It drives the real player rather than a separate robot entity, and I saw no client authentication or multiplayer abuse controls in the files read.

**Gap a new mod could fill.** Microsoft's one public Java mod (Malmo) is archived, pinned to 1.11.2 and has no block editor. I found no maintained Java Edition mod that combines (a) a block-based editor, (b) an in-world robot with turtle/Agent-like commands, and (c) server-safe execution. This is a negative search result (GitHub CLI, Modrinth API and web search) and not proof of absence.

## 6. Implementation options for Fabric or NeoForge

**Target version.** Minecraft Java is now year-versioned: current stable is 26.3, released 2026-09-15, Java 25 ([Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json), checked 2026-11-09). The [Fabric porting guide](https://docs.fabricmc.net/26.1.2/develop/porting/) confirms 26.1 is unobfuscated. Fabric API already ships builds for `26.4-snapshot-*` ([Modrinth](https://modrinth.com/mod/fabric-api)). NeoForge has non-beta 26.3 builds (26.3.0.101, [maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)). Fabric is the lower-risk first target; use Loom's new plugin id `net.fabricmc.fabric-loom` with no `mappings` line, per the porting guide.

**Embedding the editor.**

| Option | Pros | Cons |
|---|---|---|
| A. External browser + local server in the client mod (HTTP for the static pxt editor, WebSocket for commands, results and events) | Works on any MC version; no native deps; the same page can be hosted by a dedicated server; no Chromium to ship. | Context switch out of the game; localhost binding and an auth token are required; on a remote server the browser must reach the server, so ship a "paste the code" or "upload from page" path too. |
| B. MCEF in-game browser | Seamless UI, Code Builder-like. | LGPL-2.1, downloads Chromium from `mcef-download.cinemamod.com` at runtime, antivirus issues on Windows, Chromium 116, and **no confirmed release for 26.x**. Client-only; each client must have it installed. |
| C. Native Minecraft GUI blocks editor | No browser. | A very large build; do not start here. |

Recommendation: A first. Add B behind a config flag only if MCEF supports your target version.

**Running user code safely** (decided 2026-11-09, full comparison in [architecture.md](architecture.md) section 2). User programs run in the pxt simulator in the player's browser, as in MakeCode. pxt simulator functions that return a Promise suspend the program until resolved ([simshim docs](https://github.com/microsoft/pxt/blob/master/docs/simshim.md)), so each robot call sends one command and waits for the server's result. The server never runs user code; it validates every command as untrusted input. Do not trust the browser.

- Commands form a small typed set (`agent.move`, `agent.place`, ...). The server checks owner, permission, range, loaded chunk, protection and rate before queueing.
- Server-side interpreters ([QuickJS4J](https://github.com/roastedroot/quickjs4j), [GraalJS](https://github.com/oracle/graaljs), [Rhino](https://github.com/mozilla/rhino)) are not needed in v1. They stay a later option for "upload a program that keeps running with the tab closed".
- Execute robot actions on the server thread, one action (or N blocks of movement) per tick. Cap actions per tick per player and per server.
- Trade-off accepted: the program stops when the browser tab closes, and remote servers add one round trip per action.
- Multiplayer abuse controls: permission node (default op-only), per-player robot cap, chunk-loaded-only movement, protected-region checks via the same APIs other mods use, rate limit for `place/destroy`, undo log for destroyed blocks, fuel as in CC: Tweaked, kill switch command.
- Authenticate the local web server (random token per session, bind `127.0.0.1`, check `Host` and `Origin`, reject cross-origin requests). The client mod relays commands to the server over the normal game connection, so a multiplayer server opens no extra port.

**Licensing consequences.** Blockly Apache-2.0: ship its LICENSE/NOTICE. If you embed PXT (MIT), ship its notices. If you depend on MCEF as a library, LGPL means dynamic linking/replaceability; the project page notes that a dependent project that does not include MCEF source can use another license.

## 7. Claims I could not verify

1. The wire protocol used inside the in-game Code Builder (whether it still uses the `/wsserver` WebSocket or a private web-view bridge). No public source.
2. Source of `pxt-minecraft`: confirmed not public. `gh api repos/microsoft/pxt-minecraft` returns 404 and it is absent from `github.com/orgs/microsoft/repositories?q=minecraft`. Whether it is private, removed or renamed is still unknown. Only the HOC2019 extension (a consumer of the API) is public.
3. Block-to-command mappings for `player`, `blocks`, `mobs`, `builder`, `shapes`, `gameplay`, `positions`. Narrowed: signatures of `blocks.place/testForBlock`, `mobs.execute/target` and `positions.create/createWorld` are now verified from HOC2019, but not which commands they emit. The `player`, `builder`, `shapes` and `gameplay` namespaces were not seen in source at all.
4. Section 3 `agent.*` signatures: now verified for `inspect`, `destroy`, `place`, `attack`, `setItem`, `setSlot`, `collectAll`, `getItemCount`, `getPosition`. Still inferred from names: `move`, `turn`, `detect`, `till`, `collect`, `transfer`, `drop`, `dropAll`, `getItemSpace/Detail`, `teleport*`, `getOrientation`, `setAssist`, `interact`. Enum member lists (`SixDirection` and others) are not published in HOC2019 (`enums.d.ts` is a placeholder).
5. Exact text of the Minecraft EULA and Usage Guidelines (fetches timed out twice). The mod/brand/commercial statements come from search snippets and a third-party summary site (ConductAtlas), which itself reports withdrawn clauses.
6. Whether "Agent" is a registered trademark. Not checked.
7. The Code Connection discontinuation date (2023-12-01): confirmed only by the wiki page; the official community thread requires access that returned 403/redirect.
8. Python Notebooks removal in 1.21.132 (Feb 2026): confirmed through search snippets of the official support article; the article itself returned 403.
9. `codebuilder navigate` syntax: community posts only; and the claim that `/code` no longer opens Code Builder comes from a secondary site.
10. Worldbuilder/permission semantics of the Agent: no primary source.
11. Resolved 2026-11-09: Minecraft Java 26.3 is the latest release (Mojang manifest) and NeoForge has non-beta 26.3 builds (NeoForged maven).
12. MCEF compatibility with 26.x: nothing found, but I did not test or contact the maintainers.
13. VRobot (Visibilis) details and licence.
14. Whether CC: Tweaked's `CCPL` licence applies to anything you might reuse; the repository mixes licences per file.
15. Performance and sandbox-escape properties of QuickJS4J, GraalJS and Rhino. Not tested.
16. Whether a raw `microsoft/pxt-blockly` MIT LICENSE covers the whole tree (it ships upstream Blockly Apache-2.0 files); read its THIRD-PARTY-NOTICES.txt before copying.
17. Malmo: the exact line format of the native AgentHost command protocol, the JSON shape of each observation producer, and client authentication were not read or not seen. It is archived and pinned to Minecraft 1.11.2.
