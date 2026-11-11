package codeling.robot;

import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Encoded block ids as programs see them: {@code id | data << 16}, where {@code id} is the legacy (pre-flattening)
 * numeric block id and {@code data} its data value. The numbers are public game facts (Minecraft Wiki data values,
 * PrismarineJS minecraft-data); the constant names are ours. Plain Java so it is unit-testable without Minecraft;
 * the server resolves the modern id through the block registry.
 */
public final class BlockIds {
    /** What {@code inspect} returns for a block that has no entry. Never a valid code: 0 is air. */
    public static final int UNKNOWN = -1;

    private static final Map<String, Integer> BY_NAME = new HashMap<>();
    private static final Map<Integer, String> NAME_BY_CODE = new HashMap<>();
    private static final Map<Integer, String> MODERN_BY_CODE = new HashMap<>();
    private static final Map<String, Integer> CODE_BY_MODERN = new HashMap<>();

    // shortcut: common blocks only, no items and no modern-only blocks; extend (own ids from 256 up for modern blocks) in step 3.
    static {
        add("AIR", 0, 0, "air");
        add("STONE", 1, 0, "stone");
        add("GRANITE", 1, 1, "granite");
        add("POLISHED_GRANITE", 1, 2, "polished_granite");
        add("DIORITE", 1, 3, "diorite");
        add("POLISHED_DIORITE", 1, 4, "polished_diorite");
        add("ANDESITE", 1, 5, "andesite");
        add("POLISHED_ANDESITE", 1, 6, "polished_andesite");
        add("GRASS", 2, 0, "grass_block");
        add("DIRT", 3, 0, "dirt");
        add("COARSE_DIRT", 3, 1, "coarse_dirt");
        add("PODZOL", 3, 2, "podzol");
        add("COBBLESTONE", 4, 0, "cobblestone");
        String[] woods = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK"};
        for (int i = 0; i < woods.length; i++) add(woods[i] + "_PLANKS", 5, i, woods[i].toLowerCase(Locale.ROOT) + "_planks");
        add("BEDROCK", 7, 0, "bedrock");
        add("WATER", 9, 0, "water");
        add("LAVA", 11, 0, "lava");
        add("SAND", 12, 0, "sand");
        add("RED_SAND", 12, 1, "red_sand");
        add("GRAVEL", 13, 0, "gravel");
        add("GOLD_ORE", 14, 0, "gold_ore");
        add("IRON_ORE", 15, 0, "iron_ore");
        add("COAL_ORE", 16, 0, "coal_ore");
        // Logs and leaves: ids 17/18 hold the first four woods, 162/161 the last two.
        for (int i = 0; i < woods.length; i++) {
            String wood = woods[i].toLowerCase(Locale.ROOT);
            add(woods[i] + "_LOG", i < 4 ? 17 : 162, i % 4, wood + "_log");
            add(woods[i] + "_LEAVES", i < 4 ? 18 : 161, i % 4, wood + "_leaves");
        }
        add("GLASS", 20, 0, "glass");
        add("LAPIS_ORE", 21, 0, "lapis_ore");
        add("LAPIS_BLOCK", 22, 0, "lapis_block");
        add("SANDSTONE", 24, 0, "sandstone");
        add("CHISELED_SANDSTONE", 24, 1, "chiseled_sandstone");
        add("SMOOTH_SANDSTONE", 24, 2, "cut_sandstone");
        String[] colors = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
                "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"};
        for (int i = 0; i < colors.length; i++) add(colors[i] + "_WOOL", 35, i, colors[i].toLowerCase(Locale.ROOT) + "_wool");
        add("GOLD_BLOCK", 41, 0, "gold_block");
        add("IRON_BLOCK", 42, 0, "iron_block");
        add("BRICKS", 45, 0, "bricks");
        add("TNT", 46, 0, "tnt");
        add("BOOKSHELF", 47, 0, "bookshelf");
        add("MOSSY_COBBLESTONE", 48, 0, "mossy_cobblestone");
        add("OBSIDIAN", 49, 0, "obsidian");
        add("TORCH", 50, 0, "torch");
        add("CHEST", 54, 0, "chest");
        add("DIAMOND_ORE", 56, 0, "diamond_ore");
        add("DIAMOND_BLOCK", 57, 0, "diamond_block");
        add("CRAFTING_TABLE", 58, 0, "crafting_table");
        add("FURNACE", 61, 0, "furnace");
        add("REDSTONE_ORE", 73, 0, "redstone_ore");
        add("ICE", 79, 0, "ice");
        add("SNOW_BLOCK", 80, 0, "snow_block");
        add("CLAY", 82, 0, "clay");
        add("NETHERRACK", 87, 0, "netherrack");
        add("SOUL_SAND", 88, 0, "soul_sand");
        add("GLOWSTONE", 89, 0, "glowstone");
        add("STONE_BRICKS", 98, 0, "stone_bricks");
        add("MOSSY_STONE_BRICKS", 98, 1, "mossy_stone_bricks");
        add("CRACKED_STONE_BRICKS", 98, 2, "cracked_stone_bricks");
        add("CHISELED_STONE_BRICKS", 98, 3, "chiseled_stone_bricks");
        add("MELON", 103, 0, "melon");
        add("END_STONE", 121, 0, "end_stone");
        add("EMERALD_ORE", 129, 0, "emerald_ore");
        add("EMERALD_BLOCK", 133, 0, "emerald_block");
        add("REDSTONE_BLOCK", 152, 0, "redstone_block");
        add("QUARTZ_BLOCK", 155, 0, "quartz_block");
        add("TERRACOTTA", 172, 0, "terracotta");
        add("COAL_BLOCK", 173, 0, "coal_block");
    }

    private BlockIds() {
    }

    private static void add(String name, int id, int data, String modern) {
        int code = encode(id, data);
        BY_NAME.put(name, code);
        NAME_BY_CODE.put(code, name);
        MODERN_BY_CODE.put(code, "minecraft:" + modern);
        CODE_BY_MODERN.put("minecraft:" + modern, code);
    }

    public static int encode(int id, int data) {
        return id | data << 16;
    }

    public static int data(int code) {
        return code == UNKNOWN ? UNKNOWN : code >>> 16;
    }

    /** Code for a modern block id such as {@code minecraft:stone}, or {@link #UNKNOWN}. */
    public static int fromModern(String modernId) {
        return CODE_BY_MODERN.getOrDefault(modernId, UNKNOWN);
    }

    /** Modern block id for a code, or null if the code has no entry. */
    public static String toModern(int code) {
        return MODERN_BY_CODE.get(code);
    }

    /** Code for one of our constant names (any case), or {@link #UNKNOWN}. */
    public static int byName(String name) {
        return BY_NAME.getOrDefault(name.toUpperCase(Locale.ROOT), UNKNOWN);
    }

    public static String name(int code) {
        return NAME_BY_CODE.get(code);
    }

    /** Every modern id in the table, for the GameTest that checks they all exist in the registry. */
    public static Collection<String> modernIds() {
        return Collections.unmodifiableCollection(MODERN_BY_CODE.values());
    }
}
