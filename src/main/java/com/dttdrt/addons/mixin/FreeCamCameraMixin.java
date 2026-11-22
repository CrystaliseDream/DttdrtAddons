package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.freecam.FreeCamFeature;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pure client-side camera decoupling for FreeCam.
 */
@Mixin(Camera.class)
public abstract class FreeCamCameraMixin {

    @Shadow
    private boolean initialized;

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "alignWithEntity(F)V", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onAlignWithEntity(float partialTick, CallbackInfo ci) {
        if (!FreeCamFeature.isActive()) {
            return;
        }
        FreeCamFeature fc = FreeCamFeature.getInstance();
        if (fc == null) {
            return;
        }
        fc.updateCameraPositionFrame();
        this.detached = true;
        this.initialized = true;
        this.setRotation(fc.getCamYaw(), fc.getCamPitch());
        this.setPosition(fc.getCamX(), fc.getCamY(), fc.getCamZ());
        ci.cancel();
    }
}
