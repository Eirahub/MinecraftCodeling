package codeling.client;

import codeling.robot.RobotEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

//? if >=1.21.2 {
public class RobotRenderer extends EntityRenderer<RobotEntity, RobotRenderer.State> {
//?} else {
/*public class RobotRenderer extends EntityRenderer<RobotEntity> {*/
//?}
    private static final double OWNER_DISTANCE_SQ = 8 * 8;
    private static final float LINE = 9.0F * 1.15F * 0.025F;
    private static final Identifier PRIMARY_MASK = Identifier.fromNamespaceAndPath("codeling", "textures/entity/robot_primary_mask.png");
    private final RobotModel model = new RobotModel();
    private final Map<RobotEntity, RobotMotion> motions = new WeakHashMap<>();

    public RobotRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.35F;
    }

    private static Identifier texture(RobotEntity entity) {
        return Identifier.fromNamespaceAndPath("codeling", "textures/entity/robot_" + entity.skin() + ".png");
    }

    private static RenderType renderType(Identifier texture) {
        //? if >=26.1 {
        return RenderTypes.entityCutout(texture);
        //?} else if >=1.21.11 {
        /*return RenderTypes.entityCutoutNoCull(texture);*/
        //?} else
        /*return RenderType.entityCutoutNoCull(texture);*/
    }

    private RobotMotion.Frame frame(RobotEntity entity, float partialTick) {
        return motions.computeIfAbsent(entity, ignored -> new RobotMotion()).sample(entity, partialTick, model);
    }

    private boolean lookedAtUpClose(RobotEntity entity, double distance) {
        return entityRenderDispatcher.crosshairPickEntity == entity && distance < OWNER_DISTANCE_SQ;
    }

    private List<Component> lines(RobotEntity entity, double distance, boolean showName) {
        List<Component> lines = new ArrayList<>();
        if (!entity.said().isEmpty()) {
            lines.add(Component.literal(entity.said()));
            return lines;
        }
        if (lookedAtUpClose(entity, distance) && !entity.ownerName().isEmpty()) lines.add(Component.literal(entity.ownerName() + "'s robot"));
        if (showName) lines.add(entity.getCustomName());
        return lines;
    }

    private static void position(PoseStack poses, RobotMotion.Frame frame) {
        poses.translate(frame.offset().x, frame.offset().y, frame.offset().z);
        //? if >=26.3 {
        poses.rotate(Axis.YP.rotationDegrees(180 - frame.yaw()));
        //?} else
        /*poses.mulPose(Axis.YP.rotationDegrees(180 - frame.yaw()));*/
    }

    private interface DrawLayer {
        void draw(ModelPart part, PoseStack poses, Identifier texture, int color);
    }

    private void drawLayers(PoseStack poses, RobotMotion.Frame frame, Identifier texture, int primaryColor, DrawLayer draw) {
        model.draw(poses, frame.animation(), frame.seconds(), frame.amount(), frame.skipRoot(), (part, transformed) -> draw.draw(part, transformed, texture, -1));
        if (primaryColor < 0) return;
        poses.pushPose();
        poses.scale(1.0005F, 1.0005F, 1.0005F);
        model.draw(poses, frame.animation(), frame.seconds(), frame.amount(), frame.skipRoot(),
                (part, transformed) -> draw.draw(part, transformed, PRIMARY_MASK, 0xFF000000 | primaryColor));
        poses.popPose();
    }

    //? if >=1.21.2 {
    public static class State extends EntityRenderState {
        public List<Component> lines = List.of();
        public Identifier texture;
        public int primaryColor;
        public RobotMotion.Frame frame;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RobotEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.texture = texture(entity);
        state.primaryColor = entity.primaryColor();
        state.frame = frame(entity, partialTicks);
        state.lines = state.nameTag == null ? List.of() : lines(entity, state.distanceToCameraSq, super.shouldShowName(entity, state.distanceToCameraSq));
    }

    @Override
    protected boolean shouldShowName(RobotEntity entity, double distance) {
        return super.shouldShowName(entity, distance) || !entity.said().isEmpty() || lookedAtUpClose(entity, distance);
    }

    //? if >=1.21.9 {
    @Override
    //? if >=26.1 {
    protected void submitNameDisplay(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
    //?} else
    /*protected void submitNameTag(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {*/
        if (state.nameTagAttachment == null) return;
        poses.pushPose();
        for (Component line : state.lines) {
            //? if >=26.2 {
            collector.submitNameTag(poses, state.nameTagAttachment, 0, line, !state.isDiscrete, state.lightCoords, camera);
            //?} else
            /*collector.submitNameTag(poses, state.nameTagAttachment, 0, line, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);*/
            poses.translate(0, LINE, 0);
        }
        poses.popPose();
    }

    @Override
    public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        poses.pushPose();
        position(poses, state.frame);
        drawLayers(poses, state.frame, state.texture, state.primaryColor,
                (part, transformed, texture, color) -> {
                    //? if >=26.3 {
                    collector.submitModelPart(part, transformed, renderType(texture), state.lightCoords, OverlayTexture.NO_OVERLAY, null, color, state.outlineColor);
                    //?} else if >=26.2 {
                    /*collector.submitModelPart(part, transformed, renderType(texture), state.lightCoords, OverlayTexture.NO_OVERLAY, null, color, null, state.outlineColor);*/
                    //?} else {
                    /*collector.submitModelPart(part, transformed, renderType(texture), state.lightCoords, OverlayTexture.NO_OVERLAY, null, false, false, color, null, state.outlineColor);*/
                    //?}
                });
        poses.popPose();
        super.submit(state, poses, collector, camera);
    }
    //?} else {
    /*@Override
    protected void renderNameTag(State state, Component unused, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose();
        for (Component line : state.lines) {
            super.renderNameTag(state, line, poses, buffers, light);
            poses.translate(0, LINE, 0);
        }
        poses.popPose();
    }

    @Override
    public void render(State state, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose();
        position(poses, state.frame);
        drawLayers(poses, state.frame, state.texture, state.primaryColor,
                (part, transformed, texture, color) -> part.render(transformed, buffers.getBuffer(renderType(texture)), light, OverlayTexture.NO_OVERLAY, color));
        poses.popPose();
        super.render(state, poses, buffers, light);
    }
    *///?}
    //?} else {
    /*@Override
    public void render(RobotEntity entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        RobotMotion.Frame frame = frame(entity, partialTick);
        poses.pushPose();
        position(poses, frame);
        drawLayers(poses, frame, texture(entity), entity.primaryColor(),
                (part, transformed, texture, color) -> part.render(transformed, buffers.getBuffer(renderType(texture)), light, OverlayTexture.NO_OVERLAY, color));
        poses.popPose();
        if (!shouldShowName(entity)) return;
        poses.pushPose();
        for (Component line : lines(entity, entityRenderDispatcher.distanceToSqr(entity), super.shouldShowName(entity))) {
            renderNameTag(entity, line, poses, buffers, light, partialTick);
            poses.translate(0, LINE, 0);
        }
        poses.popPose();
    }

    @Override
    protected boolean shouldShowName(RobotEntity entity) {
        return super.shouldShowName(entity) || !entity.said().isEmpty() || lookedAtUpClose(entity, entityRenderDispatcher.distanceToSqr(entity));
    }

    @Override
    public Identifier getTextureLocation(RobotEntity entity) {
        return texture(entity);
    }
    *///?}
}
