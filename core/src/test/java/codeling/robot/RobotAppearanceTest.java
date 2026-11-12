package codeling.robot;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RobotAppearanceTest {
    @Test
    void savedSkinsDefaultWithoutAllowingPathsFromCommands() {
        for (String skin : RobotAppearance.SKINS) assertEquals(skin, RobotAppearance.savedSkin(skin));
        assertEquals("workshop", RobotAppearance.savedSkin(""));
        assertEquals("workshop", RobotAppearance.savedSkin("../other"));
        assertThrows(IllegalArgumentException.class, () -> RobotAppearance.requireSkin("../other"));
        assertThrows(IllegalArgumentException.class, () -> RobotAppearance.requireAnimation("not_a_clip"));
    }

    @Test
    void turningAcrossNorthUsesTheShortestArc() {
        assertEquals(20, RobotAppearance.angleDelta(170, -170));
        assertEquals(-20, RobotAppearance.angleDelta(-170, 170));
        assertEquals(90, RobotAppearance.angleDelta(0, 90));
        assertEquals(-90, RobotAppearance.angleDelta(0, -90));
    }

    @Test
    void animationInterpolatesPositionAndRotationAndClampsOneShots() {
        var clip = JsonParser.parseString("""
                {"length":1,"loop":false,"bones":{"head":{
                "rotation":[{"time":0,"value":[0,0,0]},{"time":1,"value":[10,90,0]}],
                "position":[{"time":0,"value":[0,0,0]},{"time":1,"value":[0,2,0]}]}}}
                """).getAsJsonObject();
        var half = RobotAnimation.sample(clip, "head", 0.5F);
        assertEquals(45, half.yaw());
        assertEquals(5, half.pitch());
        assertEquals(1, half.y());
        assertEquals(90, RobotAnimation.sample(clip, "head", 5).yaw());
        assertEquals(RobotAnimation.Transform.ZERO, RobotAnimation.sample(clip, "missing", 0.5F));
        clip.addProperty("loop", true);
        assertEquals(45, RobotAnimation.sample(clip, "head", 1.5F).yaw());
    }
}
