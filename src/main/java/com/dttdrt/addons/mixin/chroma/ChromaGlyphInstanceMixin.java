package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaStyledAccess;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = {
        "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$GlyphInstance",
        "net.minecraft.client.gui.font.AtlasGlyphProvider$Instance",
        "net.minecraft.client.gui.font.PlayerGlyphProvider$Instance"
})
public abstract class ChromaGlyphInstanceMixin implements ChromaStyledAccess {

    @Shadow
    public abstract Style style();

    @Override
    public Style dttdrt$getStyle() {
        return this.style();
    }
}
