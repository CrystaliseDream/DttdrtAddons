package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.freecam.FreeCamFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adjusts FreeCam flight speed on scroll wheel without sending hotbar slot-change packets.
 */
@Mixin(MouseHandler.class)
public class FreeCamMouseHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!FreeCamFeature.isActive() || this.minecraft == null || this.minecraft.getWindow() == null) {
            return;
        }
        if (window != this.minecraft.getWindow().handle() || this.minecraft.screen != null) {
            return;
        }
        double delta = vertical != 0.0 ? vertical : horizontal;
        FreeCamFeature fc = FreeCamFeature.getInstance();
        if (fc != null && delta != 0.0) {
            fc.adjustSpeed(delta);
        }
        ci.cancel();
    }
}
