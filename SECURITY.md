# Security policy

## Supported versions

Codeling has no release yet; 0.0.1 is in development on `develop`. Until the first release, only the current
`develop` branch gets security fixes.

After the first release, the latest release for each supported Minecraft version (now 26.3 and 1.21.1, on Fabric and
NeoForge) gets fixes. Older releases do not; update to the latest one.

## Reporting a vulnerability

Do not open a public issue for a security problem.

Report it privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**
(GitHub's private vulnerability reporting). Only the maintainers see the report.

Please include:

- the Codeling version, the loader (Fabric or NeoForge) and the Minecraft version,
- singleplayer or a dedicated server,
- the steps to reproduce, and what an attacker gains,
- a proof of concept if you have one, and the relevant lines of the game log (logger `codeling/relay`). Remove the
  editor token from anything you send.

## What to expect

Codeling is a volunteer project, so handling is best effort. We aim to acknowledge a report within 7 days. We then
keep you updated in the report, agree on a disclosure date with you, and credit you in the advisory unless you ask
us not to.

## Scope

In scope:

- **The local relay** (`core/src/main/java/codeling/relay/RelayServer.java`): the HTTP and WebSocket server the client
  mod runs on `127.0.0.1` for the editor. For example: reaching it from another machine or from another website,
  getting around the `Host` check (DNS rebinding defence) or the `Origin` check on the WebSocket upgrade, getting around
  the Content-Security-Policy or the framing headers, reading files outside the packaged editor, or crashing or
  stalling the game through the relay.
- **The editor token**: guessing it, reading it from another origin, finding it in a log or a file, or a session
  accepted without it. It is made per game session with `SecureRandom` and compared in constant time.
- **Server-side command validation** (`core/src/main/java/codeling/robot/RobotCommand.java` and
  `common/src/main/java/codeling/robot/RobotManager.java`): a payload that controls another player's robot, gets past
  the argument and range checks, or gets around the per-tick budgets.
- **Claim and protection mod bypass**: robots change the world through a FakePlayer with the owner's UUID, so claim
  mods apply. A robot that changes blocks or inventories its owner could not is in scope.
- **External requests from the editor**: the packaged editor must make no requests to hosts other than the local relay
  (no telemetry, no fonts, no CDN). Any request that leaves the machine is in scope.

Out of scope:

- Problems that need an attacker who already controls the player's computer, user account or browser profile.
- A player's own program misbehaving inside the limits the server allows (for example a robot that digs up its
  owner's own house).
- Server operators using operator commands (`/robot stop all`, game rules) as intended.
- Bugs in Minecraft, Fabric, NeoForge, other mods or the browser, unless Codeling makes them exploitable. Report those
  upstream.
- Denial of service that needs more traffic than one local browser can send, and findings from automated scanners
  without a working attack.
- Social engineering, and attacks on GitHub or on download sites.
