# Contributing to Codeling

Thanks for helping. This page says how to build, run and test Codeling, and what a pull request needs.
The [README](README.md) has the details on each part; this page links to it instead of repeating it.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 or newer, to start the Gradle wrapper | The Gradle daemon runs on Java 25 and downloads it on first use (`gradle/gradle-daemon-jvm.properties`, foojay resolver). It also downloads Java 21 for the 1.21.1 projects and Java 17 for the relay compat check. CI starts the wrapper with Temurin 25. |
| Node.js | 24 (what CI uses) | Builds the pxt editor in `target/` and runs the Playwright scripts in `e2e/`. Must be on `PATH` for the editor suite and the client GameTests. |
| Bash | any | For `scripts/check-text.sh` (Git Bash on Windows). |
| Gradle | none | Use the wrapper (`./gradlew`, Gradle 9.8.2). |

IntelliJ IDEA setup: [README, Open in IntelliJ IDEA](README.md#open-in-intellij-idea).

## Build

Build the editor first; without it the jars have no editor.

```
cd target
npm ci
npm run build
npm run build
npm run staticpkg
cd ..
./gradlew build
```

- `npm run build` twice: the first run regenerates `target/libs/core/sims.d.ts` from `target/sim/api.ts` but bundles the old API info. Running it once is enough when `api.ts` did not change. CI always runs it twice.
- `./gradlew buildEditor` does `npm ci`, one `npm run build` and `npm run staticpkg` in `target/` (output `target/built/packaged`).
- Use `node node_modules/pxt-core/built/pxt.js` (what the npm scripts call), never `npx pxt`: that installs an unrelated package.
- `./gradlew build` makes a Fabric and a NeoForge jar for every Minecraft version in `settings.gradle.kts` and runs the JUnit tests and the server GameTests for all of them. One jar: `./gradlew :<loader>:<version>:build`, for example `:fabric:26.3:build` or `:neoforge:1.21.1:build`. Jar names and paths: [README, Build](README.md#build).
- `src/` holds the 26.3 code. Lines that differ for an older version are Stonecutter `//? if` comments; keep 26.3 active in git. See [docs/architecture.md](docs/architecture.md), "Several Minecraft versions".

## Run

| What | Command |
|---|---|
| Editor and relay without Minecraft | `./gradlew :fabric:26.3:runRelay --args="--print-url"`, then open the printed `EDITOR_URL`. Every command gets a fake answer. See [README, Run without Minecraft](README.md#run-without-minecraft). |
| Fabric client | `./gradlew :fabric:26.3:runClient` (or `:fabric:1.21.1:runClient`) |
| NeoForge client | `./gradlew :neoforge:26.3:runClient` (or `:neoforge:1.21.1:runClient`) |
| Dedicated server | `./gradlew :fabric:26.3:runServer` or `./gradlew :neoforge:26.3:runServer` (same with `1.21.1`) |
| Editor dev server (pxt) | `npm run serve` in `target/` |

In game: `/robot spawn`, then `/codeling` opens the editor. More: [README, Run in Minecraft](README.md#run-in-minecraft).

## Tests

| Layer | Command | In `./gradlew build`? | In CI? |
|---|---|---|---|
| Text checks (em dash, CRLF) | `bash scripts/check-text.sh` | no | every push and PR |
| JUnit, Minecraft-free code | `./gradlew :core:test` | yes | every push and PR |
| JUnit, mod code | `./gradlew :fabric:26.3:test` (or `:fabric:1.21.1:test`) | yes | every push and PR |
| Server GameTests (Fabric, full suite) | `./gradlew :fabric:26.3:runGameTest` (or `:fabric:1.21.1:runGameTest`) | yes, through `check` | every push and PR |
| NeoForge smoke GameTests | `./gradlew :neoforge:26.3:runGameTestServer` (or `:neoforge:1.21.1:runGameTestServer`) | yes, through `check` | every push and PR |
| Import guards and jar names | `checkPureImports`, `checkCommonImports`, `checkJarNames` | yes | every push and PR |
| Relay on Java 17 and 21 without Netty | `./gradlew :core:relayCompatTest` (needs the built editor) | no | every push and PR |
| Editor suite (every block round trip, examples, old projects, branding) | `./gradlew :fabric:26.3:editorTest` (needs the built editor) | no | every push and PR |
| Client GameTests and the end-to-end editor test | `./gradlew :fabric:26.3:runClientGameTest` | no | `main` and manual runs only |
| Client smoke, 1.21.1 Fabric | `./gradlew :fabric:1.21.1:runClientSmoke` | no | no |
| Client smoke, NeoForge | `./gradlew :neoforge:26.3:runClientSmoke` (or `:neoforge:1.21.1:runClientSmoke`) | no | no |

- The editor suite and the client GameTests need Playwright's headless Chromium once: `./gradlew :fabric:26.3:installE2e`, then `node node_modules/playwright-core/cli.js install chromium-headless-shell` in `e2e/`.
- Client GameTests and client smokes open a real game window (2 to 3 GB of memory). Close your own Minecraft first.
- Reports, logs and screenshots: [README, Build](README.md#build) and [README, Editor test suite](README.md#editor-test-suite).
- After changing `BlockIds` or `ItemIds`, run `./gradlew :fabric:26.3:test` twice: the first run rewrites `target/libs/core/blocks.d.ts` or `items.d.ts` and fails once. Commit the generated file.

Run the client GameTests yourself before a pull request that touches the client, the camera, screens or the relay: CI only runs them on `main`.

## Branches and pull requests

- `main` is the release branch. Never push to it, and do not open pull requests against it (only the release pull
  request from `develop` goes there, see [Releases](#releases)).
- Branch from `develop` (for example `feature/robot-attack` or `fix/relay-port`) and open the pull request against `develop`. GitHub's default branch is `main`, so pick `develop` as the base when you open it.
- One topic per pull request. Link the issue it closes.
- CI must be green.

### Commit messages

Look at `git log` on `develop`: `Area: summary`, a short summary in plain words, no full stop at the end.

```
CI: client GameTests get Mesa's Vulkan driver, Loom provides the virtual display
Test: scale robot performance budgets by 2.5 on CI (CI=true)
Docs: README and architecture match CI and the current tests
```

Areas in use: `Core`, `Editor`, `Minecraft mod`, `NeoForge loader`, `Build`, `CI`, `Test`, `e2e`, `Docs`, `Documentation`.

### What CI checks

`.github/workflows/ci.yml` runs on every pull request, on pushes to `main` and `develop`, and on manual runs (a newer run for the same pull request or branch cancels the older one, except on `main`):

1. `build` job: `scripts/check-text.sh`, the pxt target build (twice, then `staticpkg`), `./gradlew :core:build :common:26.3:build :fabric:26.3:build` (JUnit, server GameTests, import guards, jar names), `:core:relayCompatTest` and `:fabric:26.3:editorTest`.
2. `neoforge` job: `./gradlew :neoforge:26.3:build` (with the NeoForge smoke GameTests).
3. `mc-1-21-1` job: `./gradlew :fabric:1.21.1:build` and `:neoforge:1.21.1:build`.
4. `client-gametest` job, on `main` and manual runs only: `./gradlew :fabric:26.3:runClientGameTest` under Xvfb.

## Versioning

Three version numbers change independently of each other:

| Version | Where | Bump it when |
|---|---|---|
| Mod version (SemVer) | `mod_version` in `gradle.properties` | Every release. Major for a change that breaks saved programs, `robots.dat` or an older client or server; minor for new blocks, commands or game rules; patch for fixes only. While it is `0.x`, a minor bump may also break things. |
| Protocol version | `Protocol.VERSION` in `core/src/main/java/codeling/net/Protocol.java` | When the shape of a relay message changes (see step 3 of "Adding a robot op and its block"). A new op inside `cmd` needs no bump. An editor with another version gets the VERSION error and is asked to reload. |
| Editor version | `version` in `target/package.json` (and `target/package-lock.json`) | When old Blocks files need an upgrade rule in `target/editor/extension.ts`. pxt passes the version a project was saved with to that rule. |

Every user-visible change gets a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md), in the same pull
request.

## Releases

Maintainers only. `.github/workflows/release.yml` does the build and the upload.

1. In `CHANGELOG.md`, move the lines under `## [Unreleased]` into a new `## [X.Y.Z] - YYYY-MM-DD` section (for 0.0.1:
   fill in the existing section, replace its intro paragraph and "unreleased" with the date). Set
   `mod_version=X.Y.Z` in `gradle.properties` if it is not already. Merge that to `develop`.
2. Open a pull request from `develop` to `main` and merge it when CI is green. CI only warns when the robot performance
   test is slow, because shared runners vary too much (issue #229). So on your own PC, with `CI` unset, run
   `./gradlew :fabric:26.3:build` and check that it ends with "All 148 required tests passed" (the count may have
   grown); there the time limits fail the test.
3. Tag the merge commit on `main` and push the tag: `git tag vX.Y.Z` and `git push origin vX.Y.Z`.
4. The workflow fails if the tag does not match `mod_version` or `CHANGELOG.md` has no `## [X.Y.Z]` section. Otherwise
   it builds the editor and the jars (with their tests), and creates a GitHub release with the jars, a
   `SHA256SUMS` file and the changelog section as notes. Versions starting with `0.` are marked as pre-releases.

## Code style

- `.editorconfig`: UTF-8, LF line endings, a final newline, no trailing spaces, 4 spaces in Java and Kotlin, 2 spaces in TypeScript, JavaScript, JSON, HTML, CSS, LESS, YAML and Markdown. `.bat` files are CRLF.
- `target/sim/api.ts`, `target/libs/core/ns.ts` and `target/libs/core/enums.d.ts` must stay LF; `check-text.sh` fails on CRLF there.
- No em dash (U+2014) anywhere in tracked text, code comments included. Rewrite the sentence with a full stop, comma, colon or brackets. `check-text.sh` fails on it.
- `core/` stays free of Minecraft and loader imports, and `common/` free of loader imports (`checkPureImports`, `checkCommonImports`). Loader code goes behind `codeling.platform.Platform` or `ClientPlatform`.
- Comments say why, not what.
- A deliberate shortcut with a known limit gets a comment in this form, so it can be found later:

  ```java
  // shortcut: <the limit>, <when to upgrade>
  ```

  Example from `RobotEntity.java`: `// shortcut: 5 s proposal for "a few seconds" (issue #80); make it a client option if players ask.` List them with `git grep -n "shortcut:"`.

## Adding a robot op and its block

A new op touches every layer. Follow an existing one end to end; `agent.freeSlots` is a small example (`git grep -n freeSlots`).

1. **Validator** (`core/src/main/java/codeling/robot/RobotCommand.java`): add the `Op` value and a `case` for the op string (`"agent.yourOp"`) that checks the argument count, types and ranges. Rejections are `BAD_ARGS` or `OUT_OF_RANGE`.
2. **Server** (`common/src/main/java/codeling/robot/RobotManager.java`): run the op on the tick and return a `Result`. Use the robot's FakePlayer for anything that changes the world, so claim mods apply. Reads that scan many blocks use the shared per-tick budgets.
3. **Protocol**: an op is a string inside the `cmd` message, so a new op needs no protocol change. If you change the message shape, bump `Protocol.VERSION` in `core/src/main/java/codeling/net/Protocol.java` and update the table in [docs/architecture.md](docs/architecture.md), "Message protocol".
4. **Standalone relay** (`core/src/main/java/codeling/relay/RelayServer.java`): add a fixed fake answer if `true` is wrong for the op, so the editor and the editor suite work without Minecraft.
5. **Editor API** (`target/sim/api.ts`): a `xxxAsync` function that calls `call("agent.yourOp", [...])`, with `//% blockId=... block="..."` annotations. Add the method to the `Robot` class too if named robots should have it. Run `npm run build` twice and commit the regenerated `target/libs/core/sims.d.ts`. pxt binds block-string slots by parameter order, so keep the words and the parameters in the same order.
6. **Tests**:
   - `core/src/test/java/codeling/net/ProtocolTest.java` (and the op list in `ProtocolFuzzTest.java`),
   - rejections in `fabric/src/test/java/codeling/robot/RobotCommandTest.java`,
   - a GameTest in `fabric/src/gametest/java/codeling/gametest/RobotGameTests.java`,
   - one line in `e2e/programs/blocks.mjs` keyed by the `blockId`, written the way the editor formats it, with values that differ from the block's defaults. The editor suite fails when a visible block has no line.
7. **Translations**: the English block text is the `block="..."` string in `api.ts`. Add the Norwegian text to `target/locales/nb/codeling/core-strings.json` (key `agent.yourOp|block`). In-game text keys go in `common/src/main/resources/assets/codeling/lang/en_us.json`.
8. **Docs**: the Editor API table in [README.md](README.md#editor-api) and the command tables in [docs/player/guide-en.md](docs/player/guide-en.md) and [docs/player/guide-nb.md](docs/player/guide-nb.md). A new `/robot` subcommand goes in `common/src/main/java/codeling/robot/RobotCommands.java` and in the same tables.

A change to how old Blocks files load needs a patch rule in `target/editor/extension.ts`, a version bump in `target/package.json` and `target/package-lock.json`, and a fixture in `e2e/fixtures/`.

## Branding and licences

- Codeling is not affiliated with Mojang, Microsoft or the MakeCode team. Never put "MakeCode", "Minecraft" or "Agent" in a product name, logo or mod name. API names that match MakeCode for paste compatibility (`agent.move`, the Agent category) are fine.
- Never add Microsoft assets: logos, icons, screenshots or images from MakeCode. Screenshots of MakeCode are a private reference and are gitignored.
- Never copy MakeCode typings, comments or enum lists from GPL sources (for example the Education Edition typings in BrandonFOSSedu/MinecraftEDUtutorials, GPL-3.0) into this MIT repo. Read them for names only and write our own declarations.
- Keep `target/docfiles/apptracking.html`: it removes Microsoft telemetry from the static editor. The editor must make no external requests.
- The editor suite checks the branding ("Codeling" in the header, no "MakeCode" or "Microsoft"). After a pxt upgrade, check the `main.js` patches in `gradle/loader.gradle.kts`; the build fails when a pattern is not found.
- A new bundled dependency needs its licence in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). Contributions are under the MIT licence ([LICENSE](LICENSE)).

## Secrets

- Never commit usernames, passwords, API keys, tokens or private keys, not even local dev values.
- Configuration that needs a secret reads it from the environment or a `.env` file (gitignored); only an `.env.example` with empty placeholders is checked in. Release tokens and signing keys belong in GitHub Actions secrets.
- The editor token is made at runtime with `SecureRandom` and must never be logged or written to a file. Keep it out of process arguments and environment variables too: `/codeling` opens a one-time launcher file that only the user can read, never the URL itself (`EditorLauncher`, issue #191).
