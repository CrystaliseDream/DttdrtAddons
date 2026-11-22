package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.freecam.FreeCamFeature;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents accidental left/right/middle click world interactions (and swing packets) during FreeCam.
 * Uses value = Minecraft.class intentionally so 26.2/26.3 ScreenChangeMixin regex does not alter target.
 */
@Mixin(value = Minecraft.class)
public class FreeCamMinecraftMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FreeCamFeature.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (FreeCamFeature.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onStartUseItem(CallbackInfo ci) {
        if (FreeCamFeature.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onPickBlock(CallbackInfo ci) {
        if (FreeCamFeature.isActive()) {
            ci.cancel();
        }
    }
}
