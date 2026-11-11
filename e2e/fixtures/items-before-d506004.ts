agent.setItem(Block.Glass, 20, 3)
let inChest = agent.containerCount(SixDirection.Left, Block.Sandstone)
let took = agent.takeItem(SixDirection.Right, Block.OakLog, 7)
