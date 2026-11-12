package codeling.client;

import codeling.robot.RobotAnimation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Rigid model parts with the exact per-face UVs and keyframes of the editable robot asset. */
final class RobotModel {
    interface DrawPart {
        void draw(ModelPart part, PoseStack poses);
    }

    private record Bone(String name, String parent, float[] pivot, ModelPart part) {
    }

    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final JsonObject animations;

    RobotModel() {
        try (InputStream input = RobotModel.class.getResourceAsStream("/assets/codeling/models/entity/robot.json")) {
            if (input == null) throw new IllegalStateException("Missing robot model");
            JsonObject model = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            animations = model.getAsJsonObject("animations");
            for (JsonElement element : model.getAsJsonArray("bones")) {
                JsonObject bone = element.getAsJsonObject();
                List<ModelPart.Cube> faces = new ArrayList<>();
                for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    float[] from = vector(cube.getAsJsonArray("from")), to = vector(cube.getAsJsonArray("to"));
                    for (var face : cube.getAsJsonObject("faces").entrySet()) {
                        faces.add(new Face(from, to, face.getKey(), vector(face.getValue().getAsJsonObject().getAsJsonArray("uv"))));
                    }
                }
                bones.put(bone.get("name").getAsString(), new Bone(bone.get("name").getAsString(), bone.get("parent").isJsonNull() ? null : bone.get("parent").getAsString(),
                        vector(bone.getAsJsonArray("pivot")), new ModelPart(faces, Map.of())));
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load robot model", exception);
        }
    }

    float length(String animation) {
        return animations.getAsJsonObject(animation).get("length").getAsFloat();
    }

    void draw(PoseStack poses, String animation, float seconds, float amount, boolean skipRoot, DrawPart draw) {
        JsonObject clip = animations.getAsJsonObject(animation);
        for (Bone bone : bones.values()) {
            poses.pushPose();
            applyParents(poses, bone, bones, clip, seconds, amount, skipRoot);
            draw.draw(bone.part(), poses);
            poses.popPose();
        }
    }

    private static void applyParents(PoseStack poses, Bone bone, Map<String, Bone> lookup, JsonObject clip, float seconds, float amount, boolean skipRoot) {
        if (bone.parent() != null) applyParents(poses, lookup.get(bone.parent()), lookup, clip, seconds, amount, skipRoot);
        if (skipRoot && bone.name().equals("root")) return;
        RobotAnimation.Transform transform = RobotAnimation.sample(clip, bone.name(), seconds).scale(amount);
        float[] p = bone.pivot();
        poses.translate((p[0] + transform.x()) / 16, (p[1] + transform.y()) / 16, (p[2] + transform.z()) / 16);
        float radians = (float) (Math.PI / 180);
        //? if >=26.3 {
        poses.rotate(new Quaternionf().rotationZYX(transform.roll() * radians, transform.yaw() * radians, transform.pitch() * radians));
        //?} else
        /*poses.mulPose(new Quaternionf().rotationZYX(transform.roll() * radians, transform.yaw() * radians, transform.pitch() * radians));*/
        poses.translate(-p[0] / 16, -p[1] / 16, -p[2] / 16);
    }

    private static float[] vector(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsFloat();
        return values;
    }

    // A face is a vanilla cube with a custom compile step: the atlas uses independent face UV islands, not box UVs.
    private static final class Face extends ModelPart.Cube {
        private final float[][] corners;
        private final float[] uv;
        private final Vector3f normal;

        Face(float[] a, float[] b, String face, float[] uv) {
            super(0, 0, a[0], a[1], a[2], b[0] - a[0], b[1] - a[1], b[2] - a[2], 0, 0, 0, false, 64, 64, EnumSet.noneOf(Direction.class));
            this.uv = uv;
            float x = a[0], y = a[1], z = a[2], X = b[0], Y = b[1], Z = b[2];
            corners = switch (face) {
                case "north" -> new float[][] {{x,Y,z},{X,Y,z},{X,y,z},{x,y,z}};
                case "south" -> new float[][] {{X,Y,Z},{x,Y,Z},{x,y,Z},{X,y,Z}};
                case "west" -> new float[][] {{x,Y,Z},{x,Y,z},{x,y,z},{x,y,Z}};
                case "east" -> new float[][] {{X,Y,z},{X,Y,Z},{X,y,Z},{X,y,z}};
                case "up" -> new float[][] {{x,Y,Z},{X,Y,Z},{X,Y,z},{x,Y,z}};
                case "down" -> new float[][] {{x,y,z},{X,y,z},{X,y,Z},{x,y,Z}};
                default -> throw new IllegalArgumentException(face);
            };
            normal = switch (face) {
                case "north" -> new Vector3f(0,0,-1);
                case "south" -> new Vector3f(0,0,1);
                case "west" -> new Vector3f(-1,0,0);
                case "east" -> new Vector3f(1,0,0);
                case "up" -> new Vector3f(0,1,0);
                default -> new Vector3f(0,-1,0);
            };
        }

        @Override
        public void compile(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color) {
            Vector3f n = pose.transformNormal(normal, new Vector3f());
            for (int i = 0; i < 4; i++) {
                float[] corner = corners[i];
                Vector3f p = pose.pose().transformPosition(corner[0] / 16, corner[1] / 16, corner[2] / 16, new Vector3f());
                float u = (i == 0 || i == 3 ? uv[0] : uv[2]) / 64;
                float v = (i < 2 ? uv[1] : uv[3]) / 64;
                buffer.addVertex(p.x(), p.y(), p.z(), color, u, v, overlay, light, n.x(), n.y(), n.z());
            }
        }
    }
}
