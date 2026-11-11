package codeling.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Keeps the editor's {@code Block} enum (target/libs/core/blocks.d.ts) equal to {@link BlockIds}. The file is
 * generated here: when it differs, the test writes the new version and fails, so rerun the test and commit the file.
 * Every member also needs a Norwegian label in target/locales/nb/codeling/core-strings.json.
 */
class BlockEnumTest {
    // Gradle runs tests with the mod/ project directory as working directory.
    private static final Path ENUM_FILE = Path.of("..", "target", "libs", "core", "blocks.d.ts");
    private static final Path NB_STRINGS = Path.of("..", "target", "locales", "nb", "codeling", "core-strings.json");

    /** STONE to Stone, OAK_PLANKS to OakPlanks; pxt's Python names turn these back into OAK_PLANKS. */
    static String member(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("_")) sb.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
        return sb.toString();
    }

    private static List<Integer> codes() {
        List<Integer> codes = new ArrayList<>();
        for (String modern : BlockIds.modernIds()) codes.add(BlockIds.fromModern(modern));
        codes.sort(Comparator.<Integer>comparingInt(c -> c & 0xFFFF).thenComparingInt(BlockIds::data));
        return codes;
    }

    static String generate() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from core/src/main/java/codeling/robot/BlockIds.java by BlockEnumTest. Do not edit.\n");
        sb.append("// Values are id | data << 16 (architecture section 5, Block ids); names are ours.\n");
        sb.append("declare const enum Block {\n");
        List<Integer> codes = codes();
        for (int i = 0; i < codes.size(); i++) {
            String name = BlockIds.name(codes.get(i));
            sb.append("    //% block=\"").append(name.toLowerCase(Locale.ROOT).replace('_', ' ')).append("\" blockIdentity=blocks.block\n");
            sb.append("    ").append(member(name)).append(" = ").append(codes.get(i)).append(i < codes.size() - 1 ? ",\n" : "\n");
        }
        return sb.append("}\n").toString();
    }

    @Test
    void enumFileMatchesBlockIds() throws IOException {
        String expected = generate();
        String actual = Files.exists(ENUM_FILE) ? Files.readString(ENUM_FILE, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
        if (!expected.equals(actual)) Files.writeString(ENUM_FILE, expected, StandardCharsets.UTF_8);
        assertEquals(expected, actual, ENUM_FILE + " was out of date and has been regenerated; rerun the test and commit it");
    }

    @Test
    void everyBlockHasANorwegianLabel() throws IOException {
        JsonObject nb = JsonParser.parseString(Files.readString(NB_STRINGS, StandardCharsets.UTF_8)).getAsJsonObject();
        List<String> missing = new ArrayList<>();
        for (int code : codes()) {
            String key = "Block." + member(BlockIds.name(code)) + "|block";
            if (!nb.has(key)) missing.add(key);
        }
        assertTrue(missing.isEmpty(), "missing in " + NB_STRINGS + ": " + missing);
    }
}
