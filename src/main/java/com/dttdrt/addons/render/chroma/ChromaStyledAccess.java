package com.dttdrt.addons.render.chroma;

import net.minecraft.network.chat.Style;

/**
 * Bridge interface applied via mixin to package-private {@code BakedSheetGlyph.GlyphInstance}
 * so GUI and world text hooks can inspect a glyph's style without access wideners.
 */
public interface ChromaStyledAccess {
    Style dttdrt$getStyle();
}
