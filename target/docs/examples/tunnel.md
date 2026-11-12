# Tunnel

```blocks
function digStep () {
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
```
