# Two robots

```blocks
let miner = robots.get("Miner")
control.runInParallel(function () {
    miner.move(SixDirection.Forward, 2)
    miner.say("Done")
})
```
