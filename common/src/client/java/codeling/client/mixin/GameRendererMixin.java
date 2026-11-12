package codeling.client.mixin;

//? if <26.3 {
/*import codeling.client.RobotCameraClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Robot camera before 26.3 (issue #189): the first-person hand belongs to the player, not the robot. 26.3 clears it
 * from the level render state instead (RobotCameraClient.extracted); this mixin is only in the mixin list of the
 * older versions (the loaders fill in codeling.mixins.json). The handler takes no target arguments, so it fits
 * renderItemInHand on every version, whose parameters changed several times.
 ^/
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void codeling$noHandOnRobot(CallbackInfo ci) {
        if (RobotCameraClient.robot() != null) ci.cancel();
    }
}
*///?}
