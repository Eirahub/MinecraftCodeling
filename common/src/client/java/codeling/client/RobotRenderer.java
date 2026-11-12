package codeling.client;

// Render states arrived in Minecraft 1.21.2 and the submit pipeline in 1.21.9; before 1.21.2 a renderer draws directly
// in render(). All draw the same thing: a scaled observer facing the robot's way and the same text lines.
//? if >=1.21.2 {
import codeling.robot.RobotEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
*///?}
//? if >=26.1
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
//? if >=1.21.9
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
//?} else {
/*import codeling.robot.RobotEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
*///?}

// shortcut: placeholder vanilla look, replace when the robot design is decided.
// Draws a scaled vanilla observer block whose face points the way the robot faces (same path as TntRenderer).
//? if >=1.21.2 {
public class RobotRenderer extends EntityRenderer<RobotEntity, RobotRenderer.State> {
    private static final float SCALE = 0.8F;
    // shortcut: 8 blocks counts as up close for the owner's name (issue #151); vanilla shows scores within 10.
    private static final double OWNER_DISTANCE_SQ = 8 * 8;
    /** One text line over the robot, as vanilla stacks a score under a name. */
    private static final float LINE = 9.0F * 1.15F * 0.025F;
    //? if >=26.1
    private final BlockModelResolver blockModelResolver;
    //? if <1.21.9
    /*private final BlockRenderDispatcher blockRenderer;*/

    /** The text lines over the robot, bottom first. */
    public static class State extends TntRenderState {
        public final List<Component> lines = new ArrayList<>();
    }

    public RobotRenderer(EntityRendererProvider.Context context) {
        super(context);
        //? if >=26.1
        this.blockModelResolver = context.getBlockModelResolver();
        //? if <1.21.9
        /*this.blockRenderer = context.getBlockRenderDispatcher();*/
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RobotEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        //? if >=26.1 {
        blockModelResolver.update(state.blockState,
                Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, entity.getDirection()),
                TntRenderer.BLOCK_DISPLAY_CONTEXT);
        //?} else
        /*state.blockState = Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, entity.getDirection());*/
        state.lines.clear();
        if (state.nameTag == null) return; // nothing to show, or too far away
        // agent.say: while the robot talks, its bubble takes the place of the tag (issue #80).
        if (!entity.said().isEmpty()) {
            state.lines.add(Component.literal(entity.said()));
            return;
        }
        // Issue #151: the owner's name under the robot's name, when the player looks at the robot up close.
        if (lookedAtUpClose(entity, state.distanceToCameraSq) && !entity.ownerName().isEmpty()) {
            state.lines.add(Component.literal(entity.ownerName() + "'s robot"));
        }
        if (super.shouldShowName(entity, state.distanceToCameraSq)) state.lines.add(entity.getCustomName());
    }

    private boolean lookedAtUpClose(RobotEntity entity, double distanceToCameraSq) {
        return entityRenderDispatcher.crosshairPickEntity == entity && distanceToCameraSq < OWNER_DISTANCE_SQ;
    }

    // The name tag (vanilla: shown when /robot nametag is on, or when looked at), the owner up close, or the bubble.
    @Override
    protected boolean shouldShowName(RobotEntity entity, double distanceToCameraSq) {
        return super.shouldShowName(entity, distanceToCameraSq) || !entity.said().isEmpty() || lookedAtUpClose(entity, distanceToCameraSq);
    }

    //? if >=1.21.9 {
    @Override
    //? if >=26.1 {
    protected void submitNameDisplay(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
    //?} else
    /*protected void submitNameTag(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {*/
        if (state.nameTagAttachment == null) return;
        poseStack.pushPose();
        for (Component line : state.lines) {
            //? if >=26.2 {
            collector.submitNameTag(poseStack, state.nameTagAttachment, 0, line, !state.isDiscrete, state.lightCoords, camera);
            //?} else
            /*collector.submitNameTag(poseStack, state.nameTagAttachment, 0, line, !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, camera);*/
            poseStack.translate(0.0F, LINE, 0.0F);
        }
        poseStack.popPose();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        //? if >=26.1 {
        state.blockState.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        //?} else
        /*if (state.blockState != null) collector.submitBlock(poseStack, state.blockState, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);*/
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
    //?} else {
    /*// 1.21.2 to 1.21.8 draw from the render state in render(); super.render draws the name tag through renderNameTag.
    @Override
    protected void renderNameTag(State state, Component name, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        for (Component line : state.lines) {
            super.renderNameTag(state, line, poseStack, buffers, light);
            poseStack.translate(0.0F, LINE, 0.0F);
        }
        poseStack.popPose();
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        if (state.blockState != null) blockRenderer.renderSingleBlock(state.blockState, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(state, poseStack, buffers, light);
    }
    *///?}
}
//?} else {
/*public class RobotRenderer extends EntityRenderer<RobotEntity> {
    private static final float SCALE = 0.8F;
    // shortcut: 8 blocks counts as up close for the owner's name (issue #151); vanilla shows scores within 10.
    private static final double OWNER_DISTANCE_SQ = 8 * 8;
    /^* One text line over the robot, as vanilla stacks a score under a name. ^/
    private static final float LINE = 9.0F * 1.15F * 0.025F;
    private final BlockRenderDispatcher blockRenderer;

    public RobotRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(RobotEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        blockRenderer.renderSingleBlock(Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, entity.getDirection()),
                poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        // No super.render: it would draw vanilla's single name tag; the lines below replace it.
        if (!shouldShowName(entity)) return;
        poseStack.pushPose();
        for (Component line : lines(entity, entityRenderDispatcher.distanceToSqr(entity))) {
            renderNameTag(entity, line, poseStack, buffers, light, partialTicks);
            poseStack.translate(0.0F, LINE, 0.0F);
        }
        poseStack.popPose();
    }

    /^* The text lines over the robot, bottom first: the bubble, or the owner up close and the name. ^/
    private List<Component> lines(RobotEntity entity, double distanceToCameraSq) {
        List<Component> lines = new ArrayList<>();
        // agent.say: while the robot talks, its bubble takes the place of the tag (issue #80).
        if (!entity.said().isEmpty()) {
            lines.add(Component.literal(entity.said()));
            return lines;
        }
        // Issue #151: the owner's name under the robot's name, when the player looks at the robot up close.
        if (lookedAtUpClose(entity, distanceToCameraSq) && !entity.ownerName().isEmpty()) {
            lines.add(Component.literal(entity.ownerName() + "'s robot"));
        }
        if (super.shouldShowName(entity)) lines.add(entity.getCustomName());
        return lines;
    }

    private boolean lookedAtUpClose(RobotEntity entity, double distanceToCameraSq) {
        return entityRenderDispatcher.crosshairPickEntity == entity && distanceToCameraSq < OWNER_DISTANCE_SQ;
    }

    // The name tag (vanilla: shown when /robot nametag is on, or when looked at), the owner up close, or the bubble.
    @Override
    protected boolean shouldShowName(RobotEntity entity) {
        return super.shouldShowName(entity) || !entity.said().isEmpty()
                || lookedAtUpClose(entity, entityRenderDispatcher.distanceToSqr(entity));
    }

    @Override
    public Identifier getTextureLocation(RobotEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
*///?}
