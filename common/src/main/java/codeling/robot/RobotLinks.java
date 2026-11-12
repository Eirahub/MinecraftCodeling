package codeling.robot;

import codeling.Codeling;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
//? if >=26.1 {
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.LevelResource;
//?}
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21.5 {
import net.minecraft.world.level.saveddata.SavedDataType;
//?} else {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
*///?}

/**
 * Which robot entity is each owner's robot, saved with the world (data/codeling/robots.dat), plus the orphans: robots
 * that were replaced by {@code /robot spawn} while unloaded and so still hold the only copy of their items.
 * SavedData rather than a player attachment: it must be readable when a robot's chunk loads while its owner is offline.
 * Issue #140: an owner may have named robots too. The default robot (empty name) stays in "links" exactly as before,
 * so old worlds load unchanged; named robots go in "named" (owner to name to robot), which older worlds do not have.
 */
public final class RobotLinks extends SavedData {
    /** One robot of one owner; the default robot has the empty name. */
    public record Key(UUID owner, String name) {
    }

    public record Appearance(String skin, int color) {
        public Appearance {
            skin = RobotAppearance.savedSkin(skin);
            color = RobotAppearance.savedColor(color);
        }

        private static final Codec<Appearance> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("skin", "workshop").forGetter(Appearance::skin),
                Codec.INT.optionalFieldOf("color", -1).forGetter(Appearance::color)
        ).apply(i, Appearance::new));
    }

    private static final Codec<Map<UUID, Appearance>> APPEARANCES = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Appearance.CODEC);
    private static final Codec<Map<UUID, UUID>> LINKS = Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC);
    private static final Codec<Map<UUID, Map<String, UUID>>> NAMED =
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, UUIDUtil.STRING_CODEC));
    static final Codec<RobotLinks> CODEC = Codec.withAlternative(
            RecordCodecBuilder.create(i -> i.group(
                    LINKS.fieldOf("links").forGetter(RobotLinks::defaults),
                    NAMED.optionalFieldOf("named", Map.of()).forGetter(RobotLinks::named),
                    UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("orphans", List.of()).forGetter(l -> List.copyOf(l.orphans)),
                    APPEARANCES.optionalFieldOf("appearances", Map.of()).forGetter(l -> Map.copyOf(l.appearances))
            ).apply(i, RobotLinks::new)),
            // Worlds saved before orphans existed hold the bare map.
            LINKS.xmap(links -> new RobotLinks(links, Map.of(), List.of(), Map.of()), RobotLinks::defaults));
    //? if >=1.21.5 {
    // null DataFixTypes: no data fixer for mod data. Fabric's SavedDataStorageMixin accepts it, and NeoForge patches
    // SavedDataType and SavedDataStorage to skip the fixer when it is null (checked in 26.3.0.102, issue #187).
    //? if >=26.1 {
    private static final SavedDataType<RobotLinks> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("codeling", "robots"), RobotLinks::new, CODEC, null);
    //?} else {
    /*// Before 26.1 the id is a file name, as before 1.21.5: data/codeling_robots.dat.
    private static final SavedDataType<RobotLinks> TYPE = new SavedDataType<>("codeling_robots", RobotLinks::new, CODEC, null);
    *///?}
    //?} else {
    /*// Before 1.21.5 saved data is a CompoundTag under a plain file name: data/codeling_robots.dat (26.x:
    // data/codeling/robots.dat). The fields inside are the same, written by the same CODEC. null DataFixTypes as above.
    private static final String FILE = "codeling_robots";
    private static final SavedData.Factory<RobotLinks> FACTORY = new SavedData.Factory<>(RobotLinks::new,
            (tag, registries) -> CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial(Codeling.LOG::error).orElseGet(RobotLinks::new), null);

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encodeStart(NbtOps.INSTANCE, this).resultOrPartial(Codeling.LOG::error)
                .ifPresent(encoded -> tag.merge((CompoundTag) encoded));
        return tag;
    }
    *///?}

    private final Map<Key, UUID> robots = new HashMap<>();
    private final Set<UUID> orphans;
    private final Map<UUID, Appearance> appearances;

    private RobotLinks() {
        this(Map.of(), Map.of(), List.of(), Map.of());
    }

    private RobotLinks(Map<UUID, UUID> links, Map<UUID, Map<String, UUID>> named, List<UUID> orphans, Map<UUID, Appearance> appearances) {
        links.forEach((owner, robot) -> robots.put(new Key(owner, ""), robot));
        named.forEach((owner, names) -> names.forEach((name, robot) -> robots.put(new Key(owner, name), robot)));
        this.orphans = new HashSet<>(orphans);
        this.appearances = new HashMap<>(appearances);
    }

    private Map<UUID, UUID> defaults() {
        Map<UUID, UUID> links = new HashMap<>();
        robots.forEach((key, robot) -> {
            if (key.name().isEmpty()) links.put(key.owner(), robot);
        });
        return links;
    }

    private Map<UUID, Map<String, UUID>> named() {
        Map<UUID, Map<String, UUID>> named = new HashMap<>();
        robots.forEach((key, robot) -> {
            if (!key.name().isEmpty()) named.computeIfAbsent(key.owner(), o -> new HashMap<>()).put(key.name(), robot);
        });
        return named;
    }

    public static RobotLinks get(MinecraftServer server) {
        //? if >=26.1 {
        RobotLinks links = server.getDataStorage().get(TYPE);
        if (links != null) return links;
        // A world from 1.21.x has data/codeling_robots.dat, which 26.1's world upgrade leaves where it is. Read it once;
        // set() marks the links dirty, so they are saved as data/codeling/robots.dat. The old file stays for a downgrade.
        links = readLegacy(server.getWorldPath(LevelResource.DATA).resolve(LEGACY_FILE));
        if (links == null) return server.getDataStorage().computeIfAbsent(TYPE);
        Codeling.LOG.info("Robot links read from data/{} (saved before Minecraft 26.1)", LEGACY_FILE);
        server.getDataStorage().set(TYPE, links);
        return links;
        //?} else if >=1.21.5 {
        /*return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        *///?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
        *///?}
    }

    //? if >=26.1 {
    static final String LEGACY_FILE = "codeling_robots.dat";

    /** The links in a file saved by 1.21.x ({"data": links, "DataVersion": n}), or null when there is none or it is broken. */
    static RobotLinks readLegacy(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            return CODEC.parse(NbtOps.INSTANCE, NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).get("data"))
                    .resultOrPartial(error -> Codeling.LOG.error("Could not read {}: {}", file, error)).orElse(null);
        } catch (IOException | RuntimeException e) {
            Codeling.LOG.error("Could not read {}", file, e);
            return null;
        }
    }
    //?}

    /** The owner's default robot. */
    public UUID robot(UUID owner) {
        return robot(new Key(owner, ""));
    }

    public UUID robot(Key key) {
        return robots.get(key);
    }

    public Appearance appearance(UUID robot) {
        return appearances.getOrDefault(robot, new Appearance("workshop", -1));
    }

    void setAppearance(UUID robot, String skin, int color) {
        Appearance appearance = new Appearance(skin, color);
        if (!appearance.equals(appearances.put(robot, appearance))) setDirty();
    }

    /** Which owner's robot (and which name) the entity {@code robot} is, or null when it is no one's (a stale copy). */
    public Key key(UUID robot) {
        for (Map.Entry<Key, UUID> e : robots.entrySet()) if (e.getValue().equals(robot)) return e.getKey();
        return null;
    }

    /** The owner's named robots, sorted; the default robot is not in the list. */
    public List<String> names(UUID owner) {
        List<String> names = new ArrayList<>();
        for (Key key : robots.keySet()) if (key.owner().equals(owner) && !key.name().isEmpty()) names.add(key.name());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** The owner's name that equals {@code name} apart from upper and lower case, or null: Miner and miner are one name. */
    public String sameName(UUID owner, String name) {
        for (String n : names(owner)) if (n.equalsIgnoreCase(name)) return n;
        return null;
    }

    void set(UUID owner, UUID robot) {
        set(new Key(owner, ""), robot);
    }

    void set(Key key, UUID robot) {
        robots.put(key, robot);
        setDirty();
    }

    void remove(Key key) {
        robots.remove(key);
        setDirty();
    }

    /** Remembers an unloaded robot that was replaced: it still owns its items. */
    void orphan(UUID robot) {
        orphans.add(robot);
        setDirty();
    }

    /** True once for an orphan, which is then forgotten: only that first copy may give its items back. */
    boolean takeOrphan(UUID robot) {
        if (!orphans.remove(robot)) return false;
        appearances.remove(robot);
        setDirty();
        return true;
    }
}
