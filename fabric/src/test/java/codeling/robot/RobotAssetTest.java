package codeling.robot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Issue #242: the robot model, its clips and textures as the renderer reads them from the jar. */
class RobotAssetTest {
    private static final String DIR = "/assets/codeling/";

    private static JsonObject model() throws IOException {
        try (InputStream in = RobotAssetTest.class.getResourceAsStream(DIR + "models/entity/robot.json")) {
            assertNotNull(in, "robot.json");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static float[] floats(JsonElement array) {
        var a = array.getAsJsonArray();
        float[] values = new float[a.size()];
        for (int i = 0; i < values.length; i++) values[i] = a.get(i).getAsFloat();
        return values;
    }

    @Test
    void restPoseFitsInOneBlockAndFaceUvsDoNotOverlap() throws IOException {
        List<float[]> uvs = new ArrayList<>();
        for (JsonElement bone : model().getAsJsonArray("bones")) {
            for (JsonElement cube : bone.getAsJsonObject().getAsJsonArray("cubes")) {
                float[] from = floats(cube.getAsJsonObject().get("from")), to = floats(cube.getAsJsonObject().get("to"));
                for (int axis = 0; axis < 3; axis++) {
                    float low = axis == 1 ? 0 : -8, high = axis == 1 ? 16 : 8;
                    assertTrue(from[axis] >= low && to[axis] <= high, "cube outside the block on axis " + axis);
                }
                for (Map.Entry<String, JsonElement> face : cube.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    float[] uv = floats(face.getValue().getAsJsonObject().get("uv"));
                    for (float v : uv) assertTrue(v >= 0 && v <= 64, "uv inside the 64 x 64 atlas");
                    uvs.add(new float[] {Math.min(uv[0], uv[2]), Math.min(uv[1], uv[3]), Math.max(uv[0], uv[2]), Math.max(uv[1], uv[3])});
                }
            }
        }
        assertEquals(48, uvs.size(), "eight cuboids with six faces");
        for (int i = 0; i < uvs.size(); i++) {
            for (int j = i + 1; j < uvs.size(); j++) {
                float[] a = uvs.get(i), b = uvs.get(j);
                assertFalse(a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3], "faces " + i + " and " + j + " share texels");
            }
        }
    }

    @Test
    void threeDistinctRgbaTexturesOfSixtyFourPixels() throws IOException {
        Set<String> seen = new HashSet<>();
        for (String skin : RobotAppearance.SKINS) {
            try (InputStream in = RobotAssetTest.class.getResourceAsStream(DIR + "textures/entity/robot_" + skin + ".png")) {
                assertNotNull(in, skin);
                byte[] bytes = in.readAllBytes();
                BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                assertEquals(64, image.getWidth(), skin);
                assertEquals(64, image.getHeight(), skin);
                assertTrue(image.getColorModel().hasAlpha(), skin + " has alpha");
                assertTrue(seen.add(java.util.Arrays.toString(bytes)), skin + " differs from the others");
            }
        }
    }

    @Test
    void clipsMoveTheRightLimbsAndEndWhereTheyStart() throws IOException {
        JsonObject clips = model().getAsJsonObject("animations");
        for (String gesture : RobotAppearance.ANIMATIONS) assertTrue(clips.has(gesture), gesture);
        for (String clip : List.of("walk", "turn_left", "turn_right")) assertTrue(clips.has(clip), clip);
        JsonObject walk = clips.getAsJsonObject("walk");
        for (float t = 0; t < 1; t += 0.05F) {
            float left = RobotAnimation.sample(walk, "left_leg", t).pitch(), right = RobotAnimation.sample(walk, "right_leg", t).pitch();
            assertEquals(-left, right, 0.01, "feet alternate at " + t);
            assertEquals(-left, RobotAnimation.sample(walk, "left_arm", t).pitch(), 0.01, "left arm against the left foot at " + t);
        }
        for (String look : List.of("look_left", "look_right", "look_up", "look_down")) {
            assertEquals(Set.of("head"), clips.getAsJsonObject(look).getAsJsonObject("bones").keySet(), look + " moves the head only");
        }
        assertEquals(35, RobotAnimation.sample(clips.getAsJsonObject("look_left"), "head", 0.6F).yaw(), 0.01, "look_left turns the head");
        for (Map.Entry<String, JsonElement> entry : clips.entrySet()) {
            JsonObject clip = entry.getValue().getAsJsonObject();
            float length = clip.get("length").getAsFloat();
            for (String bone : clip.getAsJsonObject("bones").keySet()) {
                // A loop wraps at its length, so its end is sampled just before.
                float last = clip.get("loop").getAsBoolean() ? length - 0.0001F : length;
                RobotAnimation.Transform start = RobotAnimation.sample(clip, bone, 0), end = RobotAnimation.sample(clip, bone, last);
                if (entry.getKey().startsWith("turn_") && bone.equals("root")) {
                    assertEquals(entry.getKey().equals("turn_left") ? 90 : -90, end.yaw(), 0.01, entry.getKey() + " turns a quarter");
                } else if (clip.get("loop").getAsBoolean()) {
                    assertClose(start, end, entry.getKey() + " " + bone + " loops without a jump");
                } else {
                    assertClose(RobotAnimation.Transform.ZERO, end, entry.getKey() + " " + bone + " returns to neutral");
                }
            }
        }
    }

    private static void assertClose(RobotAnimation.Transform expected, RobotAnimation.Transform actual, String what) {
        float[] a = {expected.x(), expected.y(), expected.z(), expected.pitch(), expected.yaw(), expected.roll()};
        float[] b = {actual.x(), actual.y(), actual.z(), actual.pitch(), actual.yaw(), actual.roll()};
        assertArrayEquals(a, b, 0.05F, what);
    }
}
