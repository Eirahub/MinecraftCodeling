package codeling.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PositionsTest {
    @Test
    void relativeKeepsWorldAxesAndWorldIsAbsolute() {
        assertEquals(List.of(10, 65, 4), Positions.toWorld(Positions.RELATIVE, 0, 1, 1, 10, 64, 3, 1, 0));
        assertEquals(List.of(7, -60, 2), Positions.toWorld(Positions.WORLD, 7, -60, 2, 10, 64, 3, 0, 1));
    }

    @Test
    void localTurnsWithTheOwner() {
        // one left, two up, three forward, from (10, 64, 3)
        assertEquals(List.of(11, 66, 6), Positions.toWorld(Positions.LOCAL, 1, 2, 3, 10, 64, 3, 0, 1), "south: left is east");
        assertEquals(List.of(9, 66, 0), Positions.toWorld(Positions.LOCAL, 1, 2, 3, 10, 64, 3, 0, -1), "north: left is west");
        assertEquals(List.of(13, 66, 2), Positions.toWorld(Positions.LOCAL, 1, 2, 3, 10, 64, 3, 1, 0), "east: left is north");
        assertEquals(List.of(7, 66, 4), Positions.toWorld(Positions.LOCAL, 1, 2, 3, 10, 64, 3, -1, 0), "west: left is south");
    }

    @Test
    void describesOffsetsInPlainWords() {
        assertEquals("2 east, 0 up, 2 south", Positions.describe(Positions.RELATIVE, 2, 0, 2));
        assertEquals("3 west, 1 down, 4 north", Positions.describe(Positions.RELATIVE, -3, -1, -4));
        assertEquals("1 left, 2 up, 3 back", Positions.describe(Positions.LOCAL, 1, 2, -3));
    }
}
