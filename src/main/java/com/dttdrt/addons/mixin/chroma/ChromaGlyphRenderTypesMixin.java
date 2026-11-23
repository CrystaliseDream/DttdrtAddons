package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaRenderState;
import com.dttdrt.addons.render.chroma.ChromaRenderTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderTypes.class)
public abstract class ChromaGlyphRenderTypesMixin {

    @Unique
    private Identifier dttdrt$identifier;

    @Inject(method = {"createForIntensityTexture", "createForColorTexture"}, at = @At("RETURN"))
    private static void dttdrt$captureIdentifier(Identifier identifier, CallbackInfoReturnable<GlyphRenderTypes> cir) {
        GlyphRenderTypes created = cir.getReturnValue();
        if (created != null) {
            ((ChromaGlyphRenderTypesMixin) (Object) created).dttdrt$identifier = identifier;
        }
    }

    @Inject(method = "select", at = @At("HEAD"), cancellable = true)
    private void dttdrt$selectChromaLayer(Font.DisplayMode displayMode, CallbackInfoReturnable<RenderType> cir) {
        if (ChromaRenderState.isGlyphChromaActive() && this.dttdrt$identifier != null) {
            RenderType chromaLayer = ChromaRenderTypes.getTextChroma(this.dttdrt$identifier);
            if (chromaLayer != null) {
                cir.setReturnValue(chromaLayer);
            }
        }
    }
}
