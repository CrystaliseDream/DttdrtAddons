package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaRenderState;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class ChromaRenderSystemMixin {

    @Inject(method = "flipFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicUniforms;reset()V"))
    private static void dttdrt$clearChromaUniforms(CallbackInfo ci) {
        ChromaRenderState.clearChromaUniforms();
    }
}
