package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaRenderState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderState.class)
public abstract class ChromaGlyphRenderStateMixin {

    @Shadow
    public abstract TextRenderable renderable();

    @Inject(method = "pipeline", at = @At("HEAD"), cancellable = true)
    private void dttdrt$overrideGlyphPipeline(CallbackInfoReturnable<RenderPipeline> cir) {
        RenderPipeline override = ChromaRenderState.resolveGlyphPipeline(this.renderable());
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
