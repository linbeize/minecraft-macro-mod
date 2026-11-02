package de.linbei.macro.mixin;

import de.linbei.macro.MacroModClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While aim lock is active, ignore mouse look for the local player. Resetting the rotation
 * once per tick alone makes the camera visibly jitter at frame rates above 20 FPS.
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void macroMod$blockTurnWhileAimLocked(double xo, double yo, CallbackInfo ci) {
        if (MacroModClient.ENGINE.isAimLockActive() && (Object) this == Minecraft.getInstance().player) {
            ci.cancel();
        }
    }
}
