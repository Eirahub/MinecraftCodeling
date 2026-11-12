# Stone path

```blocks
agent.setItem(Block.Cobblestone, 64, 1)
agent.setSlot(1)
agent.setAssist(AgentAssist.PlaceOnMove, true)
for (let index = 0; index < 3; index++) {
    agent.move(SixDirection.Forward, 2)
    agent.turn(TurnDirection.Left)
}
agent.setAssist(AgentAssist.PlaceOnMove, false)
```
