package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaRenderState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class ChromaPreparedTextBuilderMixin {

    @Redirect(
            method = "visit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font$GlyphVisitor;acceptGlyph(Lnet/minecraft/client/gui/font/TextRenderable$Styled;)V")
    )
    private void dttdrt$beforeAcceptGlyph(Font.GlyphVisitor visitor, TextRenderable.Styled glyph) {
        ChromaRenderState.checkIfGlyphIsChroma(glyph);
        visitor.acceptGlyph(glyph);
    }

    @Inject(method = "visit", at = @At("RETURN"))
    private void dttdrt$afterVisit(Font.GlyphVisitor visitor, CallbackInfo ci) {
        ChromaRenderState.setGlyphIsChroma(false);
    }
}
