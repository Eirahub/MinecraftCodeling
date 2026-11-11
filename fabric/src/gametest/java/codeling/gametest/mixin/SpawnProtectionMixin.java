package codeling.gametest.mixin;

import codeling.gametest.RobotGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only a dedicated server has spawn protection (DedicatedServer.isUnderSpawnProtection); the GameTest server always
 * answers false. This marks the blocks in {@link RobotGameTests#SPAWN_PROTECTED} as protected, so a test proves the
 * robot asks the vanilla Level.mayInteract path. The radius and the op bypass stay the dedicated server's own code.
 */
@Mixin(MinecraftServer.class)
public abstract class SpawnProtectionMixin {
    @Inject(method = "isUnderSpawnProtection", at = @At("HEAD"), cancellable = true)
    private void codeling$spawnProtected(ServerLevel level, BlockPos pos, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (RobotGameTests.SPAWN_PROTECTED.contains(pos)) cir.setReturnValue(true);
    }
}
