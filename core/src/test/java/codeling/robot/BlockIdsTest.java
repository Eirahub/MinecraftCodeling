package codeling.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BlockIdsTest {
    @Test
    void roundTripsCommonBlocks() {
        assertEquals(0, BlockIds.fromModern("minecraft:air"));
        assertEquals(1, BlockIds.fromModern("minecraft:stone"));
        assertEquals(1 | 1 << 16, BlockIds.fromModern("minecraft:granite"));
        assertEquals(24 | 1 << 16, BlockIds.fromModern("minecraft:chiseled_sandstone"));
        assertEquals(35 | 14 << 16, BlockIds.byName("red_wool"));
        assertEquals(162 | 1 << 16, BlockIds.byName("DARK_OAK_LOG"));
        for (String name : new String[] {"STONE", "GRANITE", "SPRUCE_PLANKS", "BLACK_WOOL", "ACACIA_LEAVES", "OBSIDIAN"}) {
            int code = BlockIds.byName(name);
            assertEquals(name, BlockIds.name(code));
            assertEquals(code, BlockIds.fromModern(BlockIds.toModern(code)));
        }
        assertEquals(1, BlockIds.data(BlockIds.byName("GRANITE")));
    }

    @Test
    void unknownBlocksFallBack() {
        assertEquals(BlockIds.UNKNOWN, BlockIds.fromModern("minecraft:sculk"));
        assertEquals(BlockIds.UNKNOWN, BlockIds.byName("nope"));
        assertEquals(BlockIds.UNKNOWN, BlockIds.data(BlockIds.UNKNOWN));
        assertNull(BlockIds.toModern(999));
    }
}
