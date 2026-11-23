package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaRenderState;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class ChromaGuiRendererMixin {

    @Inject(method = "executeDrawRange", at = @At("HEAD"))
    private void dttdrt$computeChromaUniform(CallbackInfo ci) {
        ChromaRenderState.computeChromaBufferSlice();
    }

    @Redirect(
            method = "executeDrawRange",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", ordinal = 1)
    )
    private void dttdrt$bindChromaUniform(RenderPass renderPass, String name, GpuBufferSlice slice) {
        renderPass.setUniform(name, slice);
        ChromaRenderState.bindChromaUniform(renderPass);
    }

    @Redirect(
            method = "addElementToMesh",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;pipeline()Lcom/mojang/blaze3d/pipeline/RenderPipeline;")
    )
    private RenderPipeline dttdrt$overrideGuiTextPipeline(GuiElementRenderState state) {
        RenderPipeline original = state.pipeline();
        return ChromaRenderState.resolveGuiPipeline(state, original);
    }
}
