package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.freecam.FreeCamFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes mouse look turns strictly to the FreeCam camera so player yaw/pitch never move.
 */
@Mixin(Entity.class)
public abstract class FreeCamEntityMixin {

    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onTurn(double yDelta, double xDelta, CallbackInfo ci) {
        if (!FreeCamFeature.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && (Object) this == mc.player) {
            FreeCamFeature fc = FreeCamFeature.getInstance();
            if (fc != null) {
                fc.turn(yDelta, xDelta);
                ci.cancel();
            }
        }
    }
}
