package com.dttdrt.addons.render.chroma;

import com.dttdrt.addons.mixin.accessor.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches {@link RenderType} instances per font atlas texture for batched world/nametag chroma text.
 */
public final class ChromaRenderTypes {

    public static final String CHROMA_TEXT_LAYER_NAME = "dttdrt_text_chroma";
    private static final Map<Identifier, RenderType> TEXT_CACHE = new ConcurrentHashMap<>();

    private ChromaRenderTypes() {
    }

    public static RenderType getTextChroma(Identifier texture) {
        if (texture == null) {
            return null;
        }
        return TEXT_CACHE.computeIfAbsent(texture, id ->
                RenderTypeAccessor.dttdrt$create(
                        CHROMA_TEXT_LAYER_NAME,
                        RenderSetup.builder(ChromaRenderPipelines.getChromaText())
                                .withTexture("Sampler0", id)
                                .createRenderSetup()
                )
        );
    }
}
