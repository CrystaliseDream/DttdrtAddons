package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.freecam.FreeCamFeature;
import com.dttdrt.addons.gui.WorldBackgroundFreezeManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optionally skips 3D world rendering when ModConfig.freezeWorldBackground is enabled
 * while DttdrtMainScreen is active, restoring the cached frame in VRAM.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onRenderLevelHead(CallbackInfo ci) {
        if (WorldBackgroundFreezeManager.onRenderLevelHead((GameRenderer) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void dttdrt$onRenderLevelReturn(CallbackInfo ci) {
        WorldBackgroundFreezeManager.onRenderLevelReturn((GameRenderer) (Object) this);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onRenderItemInHand(CallbackInfo ci) {
        if (FreeCamFeature.isActive()) {
            ci.cancel();
        }
    }
}
