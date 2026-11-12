# Torches in the dark

```blocks
agent.setItem(Item.Torch, 16, 1)
agent.setSlot(1)
for (let index = 0; index < 3; index++) {
    agent.move(SixDirection.Forward, 2)
    if (agent.lightLevel() < 8) {
        agent.place(SixDirection.Back)
    }
}
agent.say("Lit up")
```
