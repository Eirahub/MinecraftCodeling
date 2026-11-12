# Room and fence

```blocks
builder.mark()
builder.move(SixDirection.Forward, 4)
builder.turn(TurnDirection.Left)
builder.move(SixDirection.Forward, 2)
builder.move(SixDirection.Up, 3)
builder.fill(Block.Stone, FillOperation.Hollow)
builder.teleportTo(world(10, 64, 10))
builder.place(Block.Glass)
builder.face(CompassDirection.North)
builder.move(SixDirection.Forward, 1)
builder.mark()
builder.move(SixDirection.Right, 2)
builder.line(Block.Glass)
console.log(builder.position().toString())
control.check(builder.position().equals(world(12, 64, 9)), "the line of glass does not end at 12 64 9")
```
