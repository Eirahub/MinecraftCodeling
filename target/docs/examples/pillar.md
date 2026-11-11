# Pillar at a position

```blocks
let start = agent.getPosition()
console.log(start.toString())
if (agent.moveTo(world(0, 64, 3))) {
    agent.setItem(Block.Stone, 64, 1)
    for (let index = 0; index < 2; index++) {
        agent.place(SixDirection.Down)
        agent.move(SixDirection.Up, 1)
    }
}
```
