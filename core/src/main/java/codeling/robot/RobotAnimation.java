package codeling.robot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Samples the same linear animation tracks used by the editable model and asset preview. */
public final class RobotAnimation {
    private RobotAnimation() {
    }

    public record Transform(float x, float y, float z, float pitch, float yaw, float roll) {
        public static final Transform ZERO = new Transform(0, 0, 0, 0, 0, 0);

        public Transform scale(float amount) {
            return new Transform(x * amount, y * amount, z * amount, pitch * amount, yaw * amount, roll * amount);
        }
    }

    public static Transform sample(JsonObject clip, String bone, float seconds) {
        float length = clip.get("length").getAsFloat();
        float time = clip.get("loop").getAsBoolean() ? Math.max(0, seconds) % length : Math.min(length, Math.max(0, seconds));
        JsonObject tracks = clip.getAsJsonObject("bones").getAsJsonObject(bone);
        if (tracks == null) return Transform.ZERO;
        float[] position = track(tracks.getAsJsonArray("position"), time);
        float[] rotation = track(tracks.getAsJsonArray("rotation"), time);
        return new Transform(position[0], position[1], position[2], rotation[0], rotation[1], rotation[2]);
    }

    private static float[] track(JsonArray frames, float time) {
        if (frames == null || frames.isEmpty()) return new float[3];
        JsonObject before = frames.get(0).getAsJsonObject();
        for (JsonElement element : frames) {
            JsonObject after = element.getAsJsonObject();
            float start = before.get("time").getAsFloat();
            float end = after.get("time").getAsFloat();
            if (time <= end) {
                float amount = end == start ? 0 : Math.max(0, Math.min(1, (time - start) / (end - start)));
                float[] result = new float[3];
                for (int axis = 0; axis < 3; axis++) {
                    float a = before.getAsJsonArray("value").get(axis).getAsFloat();
                    float b = after.getAsJsonArray("value").get(axis).getAsFloat();
                    result[axis] = a + (b - a) * amount;
                }
                return result;
            }
            before = after;
        }
        JsonArray value = before.getAsJsonArray("value");
        return new float[] {value.get(0).getAsFloat(), value.get(1).getAsFloat(), value.get(2).getAsFloat()};
    }
}
