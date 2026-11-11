// Our own declaration; values match the documented MakeCode agent API (architecture.md section 5).
declare const enum SixDirection {
    //% block=forward
    Forward = 0,
    //% block=back
    Back = 1,
    //% block=left
    Left = 2,
    //% block=right
    Right = 3,
    //% block=up
    Up = 4,
    //% block=down
    Down = 5
}
declare const enum TurnDirection {
    //% block=left
    Left = 2,
    //% block=right
    Right = 3
}
declare const enum AgentDetection {
    //% block=block
    Block = 0,
    //% block=redstone
    Redstone = 1
}
declare const enum AgentInspection {
    //% block=block
    Block = 0,
    //% block=data
    Data = 1
}
// Codeling extension (issue #41): the server maps these, in this order, to block tags (codeling.robot.RobotManager).
declare const enum BlockCategory {
    //% block=logs
    Logs = 0,
    //% block=leaves
    Leaves = 1,
    //% block=ores
    Ores = 2,
    //% block=planks
    Planks = 3,
    //% block=wool
    Wool = 4,
    //% block=flowers
    Flowers = 5
}
// Codeling extension (issue #86): codeling.robot.RobotManager.entities maps these, in this order.
declare const enum EntityKind {
    //% block="hostile mob"
    Mob = 0,
    //% block=animal
    Animal = 1,
    //% block=player
    Player = 2,
    //% block=item
    Item = 3
}
// Codeling extension (issue #161): codeling.robot.RobotManager.liquid answers these numbers.
declare const enum Liquid {
    //% block=none blockIdentity=agent.liquid
    None = 0,
    //% block=water blockIdentity=agent.liquid
    Water = 1,
    //% block=lava blockIdentity=agent.liquid
    Lava = 2
}
// Codeling extension (issue #177): codeling.robot.RobotCamera has the same numbers.
declare const enum CameraMode {
    //% block=off
    Off = 0,
    //% block=eyes
    Eyes = 1,
    //% block=follow
    Follow = 2
}
// Codeling extension (issue #179): codeling.robot.RobotTrail has the same numbers.
declare const enum PathStop {
    //% block=end blockIdentity=agent.pathStop
    End = 0,
    //% block=junction blockIdentity=agent.pathStop
    Junction = 1,
    //% block=blocked blockIdentity=agent.pathStop
    Blocked = 2,
    //% block=limit blockIdentity=agent.pathStop
    Limit = 3
}
declare const enum Axis {
    //% block=x
    X = 0,
    //% block=y
    Y = 1,
    //% block=z
    Z = 2
}
// The server implements PLACE_ON_MOVE only; the others keep their API values but stay out of the dropdown.
declare const enum AgentAssist {
    //% block="place on move"
    PlaceOnMove = 0,
    //% blockHidden=true
    PlaceFromAnySlot = 1,
    //% blockHidden=true
    DestroyObstacles = 2
}
// Issue #90: MakeCode's names. codeling.robot.RobotShapes uses the same numbers.
declare const enum FillOperation {
    //% block=replace
    Replace = 0,
    //% block=hollow
    Hollow = 1,
    //% block=outline
    Outline = 2,
    //% block=keep
    Keep = 3,
    //% block=destroy
    Destroy = 4
}
declare const enum ShapeOperation {
    //% block=replace
    Replace = 0,
    //% block=outline
    Outline = 1,
    //% block=hollow
    Hollow = 2
}
// The builder's facing (issue #90), clockwise from north; only the editor uses these numbers.
declare const enum CompassDirection {
    //% block=north
    North = 0,
    //% block=east
    East = 1,
    //% block=south
    South = 2,
    //% block=west
    West = 3
}
