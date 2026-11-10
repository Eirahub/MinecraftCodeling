package codeling.client.mixin;

import codeling.client.CodelingClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Issue #192: in singleplayer, switching to the browser editor took the focus away and vanilla paused the game, so the
 * integrated server stopped and robot commands waited. While an editor session is linked (a program is running) the
 * "pause on lost focus" option counts as off. 26.x checks it in Minecraft.pauseIfInactive, 1.21.1 in GameRenderer.render.
 */
//? if >=26.1 {
@Mixin(net.minecraft.client.Minecraft.class)
//?} else
/*@Mixin(net.minecraft.client.renderer.GameRenderer.class)*/
abstract class PauseOnLostFocusMixin {
    //? if >=26.1 {
    @ModifyExpressionValue(method = "pauseIfInactive", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;pauseOnLostFocus:Z"))
    //?} else
    /*@ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;pauseOnLostFocus:Z"))*/
    private boolean codeling$keepRunning(boolean pauseOnLostFocus) {
        return pauseOnLostFocus && !CodelingClient.keepRunning();
    }
}
