# Open source readiness

Date: 2026-11-11. Branch checked: `develop` at `10ba722`. GitHub state checked with `gh` on the same day.

This is a gap analysis against common practice for a public open source project (GitHub's community profile, the
Modrinth and CurseForge listing requirements, and what players and contributors of other Minecraft mods expect).
Every "missing" below was checked in the repo (`git ls-files`) or on GitHub (`gh api`). `CONTRIBUTING.md` was
written together with this document and is not listed as a gap.

Priorities:

- **Must**: before the first public release (0.0.1 on Modrinth or as a GitHub release).
- **Should**: soon after the first release, or before inviting outside contributors.
- **Nice**: improves the project but nobody is blocked without it.

Effort: **S** under two hours, **M** half a day to two days, **L** more than two days.

## Summary

| # | Item | Priority | Effort |
|---|---|---|---|
| 1 | Security policy and private vulnerability reporting | Must | S |
| 2 | Release process: changelog, tags, release workflow | Must | M |
| 3 | Branch protection on `main` and `develop`, required checks | Must | S |
| 4 | Mod metadata: links, icon (`fabric.mod.json`, `neoforge.mods.toml`) | Must | S |
| 5 | Third-party notices: bundled editor libraries, `nb` translations | Must | M |
| 6 | GitHub repo metadata: description, topics, homepage, social preview | Must | S |
| 7 | Screenshots or a GIF in the README | Must | S |
| 8 | CI hygiene: action versions, duplicate runs, concurrency | Should | S |
| 9 | Dependabot for Actions, Gradle and npm | Should | S |
| 10 | Code of conduct | Should | S |
| 11 | Issue and pull request templates | Should | S |
| 12 | Troubleshooting and FAQ gaps | Should | S |
| 13 | Server admin guide | Should | M |
| 14 | Single command and game rule reference with permissions | Should | S |
| 15 | Editor API reference (all namespaces) | Should | M |
| 16 | Tutorials and use cases | Should | M |
| 17 | In-game translations and translation workflow | Should | M |
| 18 | Publishing to Modrinth and CurseForge | Should | M |
| 19 | Contributor docs: protocol and architecture split | Nice | M |
| 20 | CODEOWNERS and SUPPORT | Nice | S |
| 21 | Accessibility statement and checks | Nice | M |
| 22 | Jar signing and build provenance | Nice | M |
| 23 | Repository name contains "Minecraft" | Nice | S |

## Community files

### 1. SECURITY.md and private vulnerability reporting (Must, S)

- **Current state.** No `SECURITY.md` in the repo or in `.github/` (`git ls-files`). GitHub's community profile
  (`gh api repos/Eirahub/MinecraftCodeling/community/profile`) lists only README and LICENSE.
  `gh api .../private-vulnerability-reporting` answers `{"enabled": false}`. Secret scanning and push protection are
  `disabled` (`security_and_analysis` on the repo).
- **Why it matters.** Codeling runs an HTTP and WebSocket server on the player's machine (`core/.../RelayServer.java`)
  with a token, CSP and Host/Origin checks (docs/architecture.md, "Auth and token flow"), and the server executes
  commands from clients. A reporter who finds a bypass needs a private channel, not a public issue.
- **Action.** Add `SECURITY.md`: supported versions (the latest release on each Minecraft version), how to report
  (GitHub's "Report a vulnerability" button), what is in scope (relay, token handling, server-side validation, claim
  mod bypass through the FakePlayer), expected response time. Enable private vulnerability reporting, secret scanning
  and push protection in the repo settings.

### 10. CODE_OF_CONDUCT.md (Should, S)

- **Current state.** Missing (`git ls-files`, community profile `code_of_conduct: null`).
- **Why it matters.** The audience includes children and parents (docs/player/guide-en.md: "A parent can do the
  install"). A code of conduct with a contact address sets expectations for issues and pull requests.
- **Action.** Add Contributor Covenant 2.1 with a contact method that is not a personal address (for example a
  project alias or GitHub's moderation tools).

### 11. Issue and pull request templates (Should, S)

- **Current state.** No `.github/ISSUE_TEMPLATE/` and no `.github/pull_request_template.md`. The repo has 24 labels
  already (`type: feature`, `area: relay`, `P0` to `P3`, and so on), but nothing applies them.
- **Why it matters.** A bug report for this mod is only useful with the Minecraft version, loader and version, mod
  version, the editor's log line and the game log (`codeling/relay` logger). Templates ask for that up front.
- **Action.** Issue forms (YAML) for bug report (versions, loader, singleplayer or server, steps, log), feature or
  block request, and a `config.yml` that points questions to Discussions or the guide. A PR template with: base branch
  is `develop`, linked issue, tests run (from the table in `CONTRIBUTING.md`), `check-text.sh` passed, docs and `nb`
  strings updated.

### 20. CODEOWNERS and SUPPORT.md (Nice, S)

- **Current state.** Neither exists. Discussions are off (`hasDiscussionsEnabled: false`).
- **Why it matters.** With one maintainer CODEOWNERS adds little, but it becomes useful as soon as branch protection
  requires a review. SUPPORT tells players where to ask instead of opening bug reports.
- **Action.** `SUPPORT.md` pointing to the player guide, its troubleshooting table, and Discussions (turn them on) or
  issues. Add `.github/CODEOWNERS` when a second maintainer joins.

## Releases and versioning

### 2. CHANGELOG, tags and a release workflow (Must, M)

- **Current state.** No `CHANGELOG.md`. No git tags (`git tag` is empty), no GitHub releases (`gh release list` is
  empty). `.github/workflows/` holds only `ci.yml`, which uploads the jars as workflow artifacts. The mod version is
  `mod_version=0.0.1` in `gradle.properties`; the editor has its own version `0.0.2` in `target/package.json`, which
  drives the Blocks upgrade in `target/editor/extension.ts`. docs/architecture.md section 12 says "Releases only from
  tags (planned; there is no release workflow yet)".
- **Why it matters.** Players and server owners need to know what changed and whether an update breaks saved
  programs or `robots.dat`. A release built by hand from a dev machine is hard to reproduce.
- **Action.**
  1. Add `CHANGELOG.md` in Keep a Changelog form, starting with 0.0.1.
  2. Write down the versioning rule: SemVer for the mod; say when the protocol version (`Protocol.VERSION`, now 2)
     and the editor version (`target/package.json`) change, and that they are separate from the mod version.
  3. Release flow: merge `develop` into `main`, tag `v0.0.1` on `main`, and let a `release.yml` workflow (on tag push)
     build the editor and the four jars, run the same checks as CI, and attach the jars and their SHA-256 sums to a
     GitHub release with the changelog section as notes.

### 18. Modrinth and CurseForge publishing (Should, M)

- **Current state.** Nothing in the build publishes (`git grep` for `modrinth`, `curseforge`, `minotaur`,
  `publishing` in `*.kts`, `*.yml` and `*.properties` finds nothing). docs/architecture.md section 12 plans "Modrinth
  first, CurseForge later" with tokens in CI secrets.
- **Why it matters.** Players install mods from Modrinth and CurseForge, not from GitHub artifacts. The guide's
  install steps (docs/player/guide-en.md, "Install") cannot point to a download yet.
- **Action.** Add a publish step to the release workflow (for example the Minotaur Gradle plugin or the
  `Kir-Antipov/mc-publish` action) with the Modrinth token in a GitHub Actions secret. Upload four files with the
  right loader and game version tags. Then update the guide's "What you need" with the download link.

### 22. Jar signing and build provenance (Nice, M)

- **Current state.** No signing in the build (`git grep signing` finds nothing).
- **Why it matters.** Minecraft mod hosts do not require signing, but checksums and provenance help players trust a
  jar that runs a local web server.
- **Action.** Publish SHA-256 sums with each release; consider GitHub artifact attestations
  (`actions/attest-build-provenance`) in the release workflow. Keys, if any, go in CI secrets only.

## GitHub setup and CI

### 3. Branch protection and required checks (Must, S)

- **Current state.** `gh api .../branches/main/protection` and `.../branches/develop/protection` both answer
  "Branch not protected"; `gh api .../rulesets` is `[]`. The GitHub default branch is `main`
  (`defaultBranchRef`), while work and pull requests go to `develop`.
- **Why it matters.** The project rule "never push to `main`" is only a habit. A required green CI on `develop` keeps
  a broken build out of the integration branch.
- **Action.** A ruleset for `main`: no direct pushes, no force pushes, no deletion, pull request required, required
  checks `build`, `neoforge`, `mc-1-21-1 (fabric)`, `mc-1-21-1 (neoforge)` and (because CI only runs it there)
  `client-gametest`. A ruleset for `develop`: no force push or deletion, required checks the same minus
  `client-gametest`. Consider making `develop` the default branch so new pull requests target it.

### 8. CI hygiene (Should, S)

- **Current state.** `.github/workflows/ci.yml`:
  - `actions/checkout@v4`, `actions/setup-node@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`,
    `actions/upload-artifact@v4`, `actions/download-artifact@v4`. The known warnings say the v4 actions run on the
    deprecated Node 20 runtime and setup-java v4 is deprecated.
  - Caching is in place: `setup-node` caches npm for both lockfiles, `setup-gradle` caches Gradle.
  - Triggers are `push` (every branch), `pull_request` and `workflow_dispatch`, so a pull request from a branch in
    this repo runs the whole matrix twice. There is no `concurrency` group, so a new push does not cancel the old run.
  - The `client-gametest` job runs only on `main` and manual runs, so a pull request to `develop` that breaks the
    client is found only after the merge to `main`.
  - `permissions: contents: read` at the top level is already set (good).
- **Why it matters.** Deprecated runtimes stop working on a fixed date; duplicate runs waste runner minutes on a
  long build (GameTests on four projects plus the editor suite).
- **Action.** Move to the current major versions (`checkout@v5`, `setup-java@v5`, `setup-node@v5`,
  `setup-gradle@v5`, artifact actions v5 or later, after reading each changelog). Limit `push` to `develop` and `main`
  and add `concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }`. Consider running
  `client-gametest` on pull requests that touch `common/src/client/`, `fabric/src/client/` or `core/.../relay/`
  (a `paths` filter or a label).

- **Done (branch `ci/actions-upgrade`).** All actions are on current majors (checkout v7, setup-node v7, setup-java v6,
  upload-artifact v7, download-artifact v8, setup-gradle v5, because v6 needs the owner to accept Gradle's Terms of Use) in `ci.yml` and `release.yml`. `push` is limited to `main` and
  `develop`, and `ci.yml` has a `concurrency` group that cancels superseded runs except on `main`. Running
  `client-gametest` on pull requests is not done.

### 9. Dependabot or Renovate (Should, S)

- **Current state.** No `.github/dependabot.yml` and no Renovate config. Dependabot security updates are `disabled`.
- **Why it matters.** Three ecosystems need watching: GitHub Actions, Gradle plugins (Loom, ModDevGradle,
  Stonecutter, foojay) and npm (`pxt-core` in `target/`, `playwright-core` in `e2e/`). Minecraft, Fabric API and
  NeoForge versions in `<module>/versions/<v>/gradle.properties` should stay manual, since each bump needs GameTests.
- **Action.** `dependabot.yml` with `github-actions` (weekly), `npm` for `/target` and `/e2e` (weekly, grouped), and
  `gradle` for `/` with the Minecraft-related coordinates ignored. Target branch `develop`. Turn on Dependabot
  security updates. A `pxt-core` bump must also check the `main.js` branding patches in `gradle/loader.gradle.kts`;
  say so in the PR template.

### 6. Repository metadata (Must, S)

- **Current state.** `gh repo view` shows an empty description, no topics, no homepage, and
  `usesCustomOpenGraphImage: false`.
- **Why it matters.** Search on GitHub and the link preview in chat and forums depend on these fields.
- **Action.** Description, for example "Program a robot in Minecraft Java with blocks, JavaScript or Python. Fabric
  and NeoForge mod with a browser editor." Topics such as `minecraft-mod`, `fabric`, `neoforge`, `pxt`, `blockly`,
  `education`, `programming`, `robot`. Homepage: the Modrinth page once it exists. A social preview image made from
  our own screenshot (no Microsoft or Mojang logos).

### 23. Repository name contains "Minecraft" (Nice, S)

- **Current state.** The repo is `Eirahub/MinecraftCodeling`. The product name is Codeling, and the project rule is
  that "Minecraft" must not be part of a product name.
- **Why it matters.** The repo name is not the product name, but it appears in every link. Mojang's usage guidelines
  allow saying a mod is "for Minecraft"; whether a repo name counts as a product name is a judgment call.
- **Action.** Decide once and write it down. If it should change, rename to `codeling` or `codeling-mod` (GitHub
  redirects the old URL).

## Documentation

### 7. Screenshots or a GIF in the README (Must, S)

- **Current state.** `README.md` has no image. The only tracked PNGs are GameTest templates in
  `fabric/src/gametest/resources/templates/`.
- **Why it matters.** A visitor decides in seconds whether a mod is interesting; the editor next to the robot in the
  game says more than the Layout table.
- **Action.** One screenshot of the editor (Blocks) and the robot in game, and a short GIF of a program running.
  Put them in `docs/images/`, made from our own builds only.

### 12. Troubleshooting and FAQ (Should, S)

- **Current state.** docs/player/guide-en.md and guide-nb.md have a troubleshooting table (expired link, not in a
  world, server without Codeling, pause menu, robot not loaded, max robots, port 25580 busy, old blocks after an
  update, program stopped). Not covered (checked with `git grep` for `F3`, `firewall`, `antivirus` in `docs/player`):
  - F3+P (pause on lost focus) for the case where no editor session is linked yet. README "From the browser to the
    robot" says the game only skips the pause while a program runs (issue #192).
  - The fixed port 25580 cannot be changed by the player; the guide says to close the other program but not how to
    find it.
  - Firewall or antivirus prompts: the relay binds `127.0.0.1` only, so no inbound rule is needed; saying so stops
    players from opening ports.
  - Multiplayer: what happens when the server has Codeling and the client does not (vanilla clients can join, per
    docs/architecture.md section 12), and that the editor always runs on the player's own machine.
  - Where the saved programs live (pxt keeps them in the browser's IndexedDB per origin, docs/architecture.md
    "Auth and token flow"), and that clearing site data deletes them.
- **Action.** Add these rows to both guides, and a short FAQ section ("Does my code run on the server?", "Can other
  players control my robot?", "Where are my programs saved?", "Does it work offline?").

### 13. Server admin guide (Should, M)

- **Current state.** Admin facts are spread over the guide's "Multiplayer" section, README's command table and the
  three game rules. There is no page for server owners.
- **Why it matters.** Codeling only works on a server that has it, so server owners decide whether players get it.
  They need limits, permissions and protection behaviour in one place.
- **Action.** `docs/server-admin.md`: install on a dedicated server (`runServer` in dev, the jar in `mods/`), the game
  rules with defaults and ranges, permissions (only `/robot stop all` needs op level 2; every other `/robot`
  subcommand is open to all players and acts on their own robots), the per-tick budgets (actions, 8192 scanned blocks,
  1024 path nodes, 256 placed blocks per tick, from `RobotManager`), how claim and protection mods see the robot (a
  FakePlayer with the owner's UUID; the NeoForge smoke GameTest covers one refusing claim mod), what is saved
  (`robots.dat`), and how to stop every robot.

### 14. Single command and game rule reference (Should, S)

- **Current state.** Two command tables exist: README "Robot commands" and docs/player/guide-en.md "Chat commands"
  (plus `guide-nb.md`). Checked against `common/src/main/java/codeling/robot/RobotCommands.java`: the subcommands
  match (`spawn`, `list`, `remove`, `rename`, `nametag`, `move`, `turn`, `stop`, `stop all`, `status`, `place`,
  `destroy`, `inspect`, `detect`, `slot`, `give`, `inventory`, `collect`, `drop`, `assist`, `gravity`, `sethome`,
  `come`, `camera`, `camera follow`, `camera off`). Only `stop all` has a `requires` (gamemaster level); the
  `/robot` root has none. `/codeling` is a client command. The game rules are registered in
  `common/src/main/java/codeling/Codeling.java`: `codeling:free_items` (boolean, default false),
  `codeling:robot_camera` (off, owner, ops; default owner), `codeling:max_robots` (integer 1 to 16, default 1).
  Gaps:
  - No table lists the permission level per command or the game rules with type, default and range in one place.
  - `common/src/main/resources/assets/codeling/lang/en_us.json` has keys for `free_items` and `robot_camera` but
    none for `gamerule.codeling.max_robots`, so the Edit Game Rules screen shows the raw key for that rule.
  - The README and guide tables duplicate each other and can drift.
- **Action.** Make one reference page (`docs/commands.md`) with every subcommand, its arguments and ranges, who may
  use it, and the three game rules; link to it from README and both guides instead of keeping three tables. Add the
  missing `max_robots` lang key (a code fix, separate from the docs).

### 15. Editor API reference (Should, M)

- **Current state.** README "Editor API" documents every `agent` call in detail, and the guides list the blocks per
  category. The other namespaces are described in running text only: `positions` and `pos`/`posLocal`/`world`,
  `blocks.fill` and `blocks.place`, `shapes`, `builder`, `robots.get` and the `Robot` class, `control.runInParallel`,
  and the enums (`SixDirection`, `TurnDirection`, `AgentDetection`, `EntityKind`, `Liquid`, `PathStop`,
  `BlockCategory`, `CameraMode`, `Block`, `Item`). The in-editor docs are stubs: `target/docs/docs.md` is one code
  block and `target/docs/about.md` four lines, so the editor's help panel and block help links show nothing useful.
  The source of truth is `target/sim/api.ts` and `target/libs/core/ns.ts`, with the generated
  `target/libs/core/sims.d.ts`.
- **Why it matters.** JavaScript and Python users need signatures, return types, ranges and errors per call, in the
  editor where they code.
- **Action.** Generate a reference from the `//%` annotations and doc comments in `sims.d.ts` and `ns.ts` (one page
  per namespace, with the JavaScript and Python names) and serve it as pxt docs pages in `target/docs/reference/`, so
  the help links in the editor work offline. Keep README's table short and link to it.

### 16. Tutorials and use cases (Should, M)

- **Current state.** docs/player/guide-en.md has "Your first program" and "Solve a maze" (`shapes.maze` plus
  `agent.followPath`). `e2e/programs/examples.mjs` has six example programs (stone path, staircase, tunnel with a
  function, pillar at a position, builder room and fence, second robot in parallel), but they exist only as tests:
  `target/pxtarget.json` has no gallery or tutorials, and no doc page shows them. No tutorial covers building a house,
  mining, farming, or using containers and crafting.
- **Why it matters.** The audience is hobby players who want to learn to code. Step-by-step tasks are how MakeCode
  users learn, and examples that also run in CI cannot silently break.
- **Action.** Publish the six examples as a docs page and an editor gallery. Write tutorials for: first program
  (exists), build a house (`blocks.fill` Hollow, `builder`), strip mine with `detect`, `destroy`, `isSafe` and
  `storeItems`, tree farm with `findNearestCategory` and `craft`, maze (exists). Add each tutorial's final program to
  `examples.mjs` so CI runs it.

### 19. Contributor docs: protocol and architecture (Nice, M)

- **Current state.** docs/architecture.md is a 16-section plan with the message protocol, auth flow, tick model,
  threat model and test strategy, partly written as a plan ("Status: first draft", "(proposal)", "[UNVERIFIED]"
  markers). The protocol table still says "Message protocol (v1)" while `Protocol.VERSION` is 2 (the `robot` field
  is described inline). README carries much implementation detail (GameTest internals, CI quirks).
  `docs/makecode-java-mod-research.md` is background research.
- **Why it matters.** A new contributor cannot tell which parts are decided and built from those that are still a
  plan.
- **Action.** Split a short `docs/protocol.md` (v2 message reference: types, fields, error codes, limits) out of
  architecture.md, mark architecture.md as a design record, and move test internals from README to a
  `docs/testing.md` that `CONTRIBUTING.md` links to.

### 17. Translations (Should, M)

- **Current state.**
  - Editor: English in the `block="..."` strings of `target/sim/api.ts`; Norwegian in
    `target/locales/nb/codeling/core-strings.json` and `target-strings.json`. `BlockEnumTest` fails when a block has
    no Norwegian label. pxt's own `nb` UI strings (`target/locales/nb/strings.json`, `webstrings.json`) are a
    one-time snapshot from makecode.com; `target/locales/README.md` marks it `shortcut:` and says the licence for
    reuse is not verified.
  - In game: only `common/src/main/resources/assets/codeling/lang/en_us.json` (7 keys). docs/architecture.md section
    11 plans an `nb_no.json` that does not exist. Command replies are hard-coded English: `git grep -c
    Component.literal` counts 23 in `RobotCommands.java` and 33 in `common/` overall, against 2
    `Component.translatable` (in `RobotCameraClient.java`).
  - No translation workflow: no guide for adding a language, no Crowdin or Weblate project.
- **Why it matters.** The guide is bilingual, so Norwegian players see Norwegian blocks and English chat replies.
  An unverified licence on bundled strings is a release risk (see item 5).
- **Action.** Resolve the licence of the pxt `nb` snapshot before release, or drop it and fall back to English.
  Move in-game messages to `Component.translatable` with keys in `en_us.json` and add `nb_no.json`. Add a
  "Translations" section to `CONTRIBUTING.md` (which files, how `BlockEnumTest` checks labels) once there is a
  workflow.

### 21. Accessibility (Nice, M)

- **Current state.** `target/pxtarget.json` sets `highContrast: true` and `immersiveReader: false`. pxt brings
  keyboard navigation for Blocks and the Monaco editor. Nothing in the repo describes or tests accessibility; the
  editor suite checks function and branding, not contrast or keyboard use.
- **Why it matters.** Learners include people using keyboards only, screen readers or high contrast.
- **Action.** Add a short accessibility note to the guide (high contrast mode, keyboard controls, text programming
  as an alternative to dragging). Consider an axe-core check on the editor page in `e2e/editor-suite.mjs`.

## Licences and metadata

### 4. Mod metadata (Must, S)

- **Current state.** `fabric/src/main/resources/fabric.mod.json` has id, name, description, authors and licence, but
  no `contact` (`homepage`, `sources`, `issues`) and no `icon`. `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
  has licence, authors and description, but no `displayURL`, `issueTrackerURL` or `logoFile`. No mod icon is tracked.
- **Why it matters.** Mod Menu and NeoForge's mod list show these links and the icon; players use them to find the
  source and report bugs. Modrinth also needs a project icon.
- **Action.** Add `contact.homepage`, `contact.sources` and `contact.issues` and an `icon` to `fabric.mod.json`, and
  `displayURL`, `issueTrackerURL` and `logoFile` to `neoforge.mods.toml`. Draw an original icon (no Mojang or
  Microsoft art).

### 5. Third-party notices completeness (Must, M)

- **Current state.** `THIRD-PARTY-NOTICES.md` has the trademark statement, pxt-core's MIT text, pxt-sample (full
  text in `target/LICENSE-pxt-sample.txt`) and a Fabric note. It says itself that the libraries pxt bundles into the
  static editor (Blockly, Apache-2.0; Semantic UI, MIT; Monaco, MIT; and others) have no complete list yet
  (issue #61). docs/architecture.md section 4 says Blockly's LICENSE and NOTICE must ship with the jar. The `nb`
  editor strings have an unverified licence (item 17). NeoForge (LGPL-2.1, linked, not bundled) is not mentioned;
  that is fine legally but worth a line for symmetry with Fabric.
- **Why it matters.** The jars redistribute `target/built/packaged` under `codeling-editor/`. Apache-2.0 requires
  its licence and any NOTICE text to travel with redistributed copies.
- **Action.** Generate the list from `target/node_modules` and the files that end up in `built/packaged` (for
  example with `license-checker` or pxt's own third-party file if it ships one), add the licence texts to
  `THIRD-PARTY-NOTICES.md`, and copy that file and `LICENSE` into the jar (`META-INF/`). Close issue #61. Settle the
  `nb` strings.

## Ordered action list

1. Enable private vulnerability reporting, secret scanning and push protection; add `SECURITY.md`.
2. Add rulesets for `main` and `develop` with the CI jobs as required checks; consider `develop` as default branch.
3. Fill in the repo description, topics and social preview.
4. Add links and an icon to `fabric.mod.json` and `neoforge.mods.toml`.
5. Complete `THIRD-PARTY-NOTICES.md` for the bundled editor (issue #61), ship it in the jar, and settle the licence
   of the `nb` pxt strings.
6. Add screenshots and a GIF to the README.
7. Add `CHANGELOG.md`, write down the versioning rule, and create a tag-driven `release.yml`.
8. Update the CI actions to current majors, limit `push` triggers, add a concurrency group.
9. Add `dependabot.yml` for Actions, npm and Gradle (Minecraft versions excluded).
10. Add `CODE_OF_CONDUCT.md`, issue forms and a PR template.
11. Publish to Modrinth from the release workflow; update the guide's install steps.
12. Write `docs/commands.md` and `docs/server-admin.md`; add the missing `max_robots` lang key.
13. Extend troubleshooting and add a FAQ in both guides.
14. Generate the editor API reference into `target/docs/` and publish the six examples plus new tutorials.
15. Move in-game messages to translation keys and add `nb_no.json`.
16. Split protocol and testing docs out of architecture.md and README; add SUPPORT, CODEOWNERS and an accessibility
    note.
