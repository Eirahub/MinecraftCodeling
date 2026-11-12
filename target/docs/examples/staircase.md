# Staircase

```blocks
agent.setItem(Block.OakPlanks, 64, 1)
for (let index = 0; index < 3; index++) {
    agent.move(SixDirection.Up, 1)
    agent.move(SixDirection.Forward, 1)
    agent.place(SixDirection.Down)
}
```
