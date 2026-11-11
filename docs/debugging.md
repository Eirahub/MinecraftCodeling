# Debugging in the editor (issue #217)

Spike, 2026-11-11, pxt-core 13.3.1, packaged editor in headless Chromium against the standalone relay. Test program:
a variable, a `for` loop with `agent.move`, `loops.pause`, `agent.say`, two `forever` loops and a `runInParallel`.

## What turns it on

- `appTheme.debugger: true` in `target/pxtarget.json`. That is the only setting. It adds the bug button under the
  simulator, breakpoint circles on blocks, a breakpoint margin in Monaco (JavaScript and Python), the debugger toolbar
  and the Variables panel. Slow motion is the turtle button ("Slow-Mo") inside the debugger toolbar; pxt has no
  separate target setting for it (`toggleTrace`, interval `SLOW_TRACE_INTERVAL`).
- No simulator change is needed. In debug mode pxt compiles with breakpoint checks between statements
  (`setupDebugger`, `breakpoint` and `trace` in `pxtsim.js`).

## What works as it is

- Breakpoints and Step in Blocks, JavaScript and Python. Blocks has Step (into) and Continue; the text editors have
  Continue, Step over, Step into and Step out. The block or line that runs next is highlighted.
- Variables: the panel lists the program's variables with their values while it is stopped.
- Robot commands: a robot function is a promise shim, and pxt only checks breakpoints between statements, so a command
  that was sent always gets its answer before the program stops at the next step. A step over `agent.move` logs
  `move 0 1: done` before it stops.
- Fibers: at a breakpoint pxt takes the run lock and pauses `runtime.schedule` timers, so `forever`, `runInParallel`
  and `loops.pause` all stand still and go on after Continue. A command another fiber had already sent still finishes
  in the game; its answer waits until Continue.
- Heartbeat: it is a plain `window.setInterval` in `Relay`, not a pxt timer, so it keeps going while the program is
  stopped (seen as heartbeat frames during a 14 s stop). The run does not time out while a player looks at variables.
- `/robot stop`, another session taking over, and a lost connection: the relay's socket handlers are not pxt code,
  so they run at a breakpoint as well. The player gets the "Program stopped" dialog and nothing more reaches the game.
- Slow motion runs the program to the end without stopping, highlights each block in turn, and gives the same robot
  log as a normal run.
- No request to another host in debug mode.

## What does not work as it is

- Stop at a breakpoint. pxt disables the Run/Stop button for the whole debug session, and "Exit Debug Mode" restarts
  the program in normal mode, so the robot would do the whole program again unasked.
  Fix (small, done): one more `main.js` patch next to the branding patches in `gradle/loader.gradle.kts`. Leaving
  debug mode stops the program (sends `stop` to the relay) instead of running it again. "Exit Debug Mode" is the Stop
  button in debug mode. Making pxt's own Stop button work at a breakpoint would need two more patches (its render rule
  and `stopSimulator`, which skips a paused simulator); not done.
- Entering debug mode, turning slow motion on or off, and "Restart debugging" start the program from the beginning
  (a new run). The robot does not undo what it did. This is how pxt works; changing it would mean forking its
  debugger flow.
- Breakpoints set in JavaScript stay in the shared text editor. After switching to Python, debug mode can open
  `main.ts` with the old breakpoint. Remove breakpoints before switching language. Left as pxt does it.
- Some debugger texts are missing from pxt's Norwegian strings ("Debug Mode", "Step") and show in English.

Result: the debugger and slow motion work with the setting and one editor patch; nothing in the simulator, the
protocol or the server has to change.
