package codeling.client.mixin;

import codeling.client.RobotCameraClient;
import codeling.robot.RobotEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Robot camera (issues #176 and #177): places the camera after vanilla has put it on the camera entity. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    private boolean detached;

    //? if >=26.1 {
    @Shadow
    public abstract net.minecraft.world.entity.Entity entity();
    //?} else {
    /*@Shadow
    public abstract net.minecraft.world.entity.Entity getEntity();

    private net.minecraft.world.entity.Entity entity() {
        return getEntity();
    }
    *///?}

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void move(float forwards, float up, float right);

    @Shadow
    private float getMaxZoom(float cameraDist) {
        throw new AssertionError();
    }

    // 26.x places the camera in alignWithEntity, 1.21.1 in setup (with the level, entity and view flags first).
    //? if >=26.1 {
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void codeling$robotCamera(float partialTicks, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "setup", at = @At("TAIL"))
    private void codeling$robotCamera(net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity cameraEntity,
            boolean detachedView, boolean mirrored, float partialTicks, CallbackInfo ci) {
    *///?}
        RobotEntity robot = RobotCameraClient.robot();
        if (robot == null || entity() != robot) return;
        if (RobotCameraClient.following()) {
            // Behind the robot's eye, pulled in front of any block in the way as vanilla's third person view does, with
            // the yaw and the distance eased per frame so turns and walls do not jump.
            setRotation(RobotCameraClient.yaw(robot.getViewYRot(partialTicks)), RobotCameraClient.pitch());
            setPosition(robot.getEyePosition(partialTicks));
            move(-RobotCameraClient.shownDistance(getMaxZoom(RobotCameraClient.distance())), 0, 0);
            detached = true; // so the robot's model is drawn
            // shortcut: vanilla's LevelExtractor never draws the local player unless it is the camera entity, so your own
            // body is not seen from the follow camera; draw it here if players miss it.
        } else if (detached) {
            // F5 in eye mode: stay in the robot's eye, so its model stays hidden.
            setRotation(robot.getViewYRot(partialTicks), robot.getViewXRot(partialTicks));
            setPosition(robot.getEyePosition(partialTicks));
            detached = false;
        }
    }
}
