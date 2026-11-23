package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.freecam.FreeCamFeature;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips first-person hand rendering while FreeCam is active.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onRenderItemInHand(CallbackInfo ci) {
        if (FreeCamFeature.isActive()) {
            ci.cancel();
        }
    }
}
