# Guard patrol

```blocks
let rounds = 0
loops.forever(function () {
    if (rounds < 2) {
        agent.move(SixDirection.Forward, 3)
        agent.turn(TurnDirection.Left)
        agent.turn(TurnDirection.Left)
        rounds += 1
    }
    loops.pause(500)
})
```
