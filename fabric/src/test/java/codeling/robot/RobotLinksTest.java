package codeling.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** robots.dat must keep its links: a lost link makes every robot stale on load. */
class RobotLinksTest {
    private static RobotLinks decode(JsonElement json) {
        return RobotLinks.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void linksAndOrphansSurviveSaveAndLoad() {
        UUID owner = UUID.randomUUID(), robot = UUID.randomUUID(), orphan = UUID.randomUUID();
        RobotLinks links = decode(new JsonObject());
        links.set(owner, robot);
        links.orphan(orphan);
        RobotLinks loaded = decode(RobotLinks.CODEC.encodeStart(JsonOps.INSTANCE, links).getOrThrow());
        assertEquals(robot, loaded.robot(owner));
        assertTrue(loaded.takeOrphan(orphan));
        assertFalse(loaded.takeOrphan(orphan), "an orphan gives its items back once");
    }

    // Issue #140: named robots are saved beside the default robots, which keep the old format.
    @Test
    void namedRobotsSurviveSaveAndLoad() {
        UUID owner = UUID.randomUUID(), robot = UUID.randomUUID(), miner = UUID.randomUUID(), digger = UUID.randomUUID();
        RobotLinks links = decode(new JsonObject());
        links.set(owner, robot);
        links.set(new RobotLinks.Key(owner, "Miner"), miner);
        links.set(new RobotLinks.Key(owner, "Digger"), digger);
        JsonObject saved = RobotLinks.CODEC.encodeStart(JsonOps.INSTANCE, links).getOrThrow().getAsJsonObject();
        assertEquals(robot.toString(), saved.getAsJsonObject("links").get(owner.toString()).getAsString(), "the default robot as before");
        RobotLinks loaded = decode(saved);
        assertEquals(robot, loaded.robot(owner));
        assertEquals(miner, loaded.robot(new RobotLinks.Key(owner, "Miner")));
        assertEquals(new RobotLinks.Key(owner, "Digger"), loaded.key(digger));
        assertEquals(java.util.List.of("Digger", "Miner"), loaded.names(owner));
        assertEquals("Miner", loaded.sameName(owner, "MINER"));
        loaded.remove(new RobotLinks.Key(owner, "Miner"));
        assertNull(loaded.key(miner), "a removed robot is no one's");
    }

    // A world saved before named robots: links and orphans only.
    @Test
    void readsLinksWithoutNamedRobots() {
        UUID owner = UUID.randomUUID(), robot = UUID.randomUUID();
        JsonObject links = new JsonObject();
        links.addProperty(owner.toString(), robot.toString());
        JsonObject old = new JsonObject();
        old.add("links", links);
        old.add("orphans", new com.google.gson.JsonArray());
        RobotLinks loaded = decode(old);
        assertEquals(robot, loaded.robot(owner));
        assertEquals(new RobotLinks.Key(owner, ""), loaded.key(robot));
        assertTrue(loaded.names(owner).isEmpty());
    }

    @Test
    void readsTheOldBareMap() {
        UUID owner = UUID.randomUUID(), robot = UUID.randomUUID();
        JsonObject old = new JsonObject();
        old.addProperty(owner.toString(), robot.toString());
        assertEquals(robot, decode(old).robot(owner));
    }
}
