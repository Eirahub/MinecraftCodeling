package codeling.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Keeps the editor's {@code Item} enum (target/libs/core/items.d.ts) equal to {@link ItemIds}, generated the same way
 * as {@link BlockEnumTest}: when the file differs, the test writes the new version and fails; rerun and commit it.
 * Norwegian labels: every item that is also in the Block enum needs one; the rest fall back to English, and every
 * Item key in the nb strings must name a real member.
 */
class ItemEnumTest {
    private static final Path ENUM_FILE = Path.of("..", "target", "libs", "core", "items.d.ts");
    private static final Path NB_STRINGS = Path.of("..", "target", "locales", "nb", "codeling", "core-strings.json");

    static String member(String modern) {
        return BlockEnumTest.member(modern.substring("minecraft:".length()).toUpperCase(Locale.ROOT));
    }

    static String generate() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from core/src/main/java/codeling/robot/ItemIds.java by ItemEnumTest. Do not edit.\n");
        sb.append("// Items that are also in the Block enum have the same value, so Item.Cobblestone == Block.Cobblestone.\n");
        sb.append("declare const enum Item {\n");
        List<String> ids = ItemIds.modernIds();
        for (int i = 0; i < ids.size(); i++) {
            String modern = ids.get(i);
            sb.append("    //% block=\"").append(modern.substring("minecraft:".length()).replace('_', ' ')).append("\" blockIdentity=blocks.item\n");
            sb.append("    ").append(member(modern)).append(" = ").append(ItemIds.fromModern(modern)).append(i < ids.size() - 1 ? ",\n" : "\n");
        }
        return sb.append("}\n").toString();
    }

    @Test
    void enumFileMatchesItemIds() throws IOException {
        String expected = generate();
        String actual = Files.exists(ENUM_FILE) ? Files.readString(ENUM_FILE, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
        if (!expected.equals(actual)) Files.writeString(ENUM_FILE, expected, StandardCharsets.UTF_8);
        assertEquals(expected, actual, ENUM_FILE + " was out of date and has been regenerated; rerun the test and commit it");
    }

    @Test
    void norwegianLabels() throws IOException {
        JsonObject nb = JsonParser.parseString(Files.readString(NB_STRINGS, StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> members = new HashSet<>();
        List<String> missing = new ArrayList<>();
        for (String modern : ItemIds.modernIds()) {
            String key = "Item." + member(modern) + "|block";
            members.add(key);
            if (BlockIds.fromModern(modern) != BlockIds.UNKNOWN && !nb.has(key)) missing.add(key);
        }
        assertTrue(missing.isEmpty(), "missing in " + NB_STRINGS + ": " + missing);
        List<String> stale = nb.keySet().stream().filter(k -> k.startsWith("Item.") && !members.contains(k)).toList();
        assertTrue(stale.isEmpty(), "no such Item member: " + stale);
    }

    // Every Block with an item form names the same item, so old programs passing a Block still mean the same thing.
    @Test
    void blockCodesAreItemCodes() {
        for (String modern : BlockIds.modernIds()) {
            int code = BlockIds.fromModern(modern);
            if (modern.equals("minecraft:air") || modern.equals("minecraft:water") || modern.equals("minecraft:lava")) {
                assertEquals(null, ItemIds.toModern(code), modern);
            } else {
                assertEquals(modern, ItemIds.toModern(code), modern);
            }
        }
        assertNotNull(ItemIds.toModern(ItemIds.byName("iron_ingot")));
        assertTrue(ItemIds.byName("COAL") >= ItemIds.ITEM_BASE);
        assertEquals(BlockIds.byName("SMOOTH_SANDSTONE"), ItemIds.byName("cut_sandstone"));
        assertEquals(ItemIds.modernIds().size(), new HashSet<>(ItemIds.modernIds().stream().map(ItemIds::fromModern).toList()).size());
    }
}
