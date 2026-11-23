package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaRenderPipelines;
import com.dttdrt.addons.render.chroma.ChromaRenderState;
import com.dttdrt.addons.render.chroma.RenderTypeNameAccess;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public abstract class ChromaRenderTypeMixin implements RenderTypeNameAccess {

    @Shadow
    @Final
    protected String name;

    @Shadow
    public abstract RenderPipeline pipeline();

    @Override
    public String dttdrt$getName() {
        return this.name;
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void dttdrt$prepareWorldChromaUniform(MeshData meshData, CallbackInfo ci) {
        if (ChromaRenderState.isChromaFeatureActive() && this.pipeline() == ChromaRenderPipelines.getChromaText()) {
            ChromaRenderState.ensureChromaBufferSlice();
        }
    }

    @Redirect(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V")
    )
    private void dttdrt$bindWorldChromaUniform(RenderPass renderPass, String uniformName, GpuBufferSlice slice) {
        renderPass.setUniform(uniformName, slice);
        ChromaRenderState.bindChromaUniformIfPipelineMatches(renderPass, this.pipeline());
    }
}
