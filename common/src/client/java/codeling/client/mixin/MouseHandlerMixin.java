package codeling.client.mixin;

import codeling.client.RobotCameraClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Follow camera (issue #177): the mouse orbits around the robot and the scroll wheel sets the distance. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void codeling$orbit(LocalPlayer player, double x, double y, Operation<Void> original) {
        if (!RobotCameraClient.orbit(x, y)) original.call(player, x, y);
    }

    // At the hotbar scroll, which vanilla only reaches in game with no screen open. 1.21.1 has no ScrollWheelHandler:
    // there the spectator check that comes right before the hotbar change is the spot.
    //? if >=26.1 {
    @Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"), cancellable = true)
    //?} else
    /*@Inject(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"), cancellable = true)*/
    private void codeling$zoom(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (RobotCameraClient.zoom(yoffset)) ci.cancel();
    }
}
