package codeling.client.mixin;

//? if <1.21.2 {
/*import codeling.client.RobotCameraClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Robot camera on 1.21.1 (issue #189): the first-person hand and the fire, water and portal overlays belong to the
 * player, not the robot. 26.x clears them from the level render state instead (RobotCameraClient.extracted); this
 * mixin is only in the 1.21.1 mixin list (the loaders fill in codeling.mixins.json).
 ^/
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void codeling$noHandOnRobot(Camera camera, float partialTicks, Matrix4f projection, CallbackInfo ci) {
        if (RobotCameraClient.robot() != null) ci.cancel();
    }
}
*///?}
