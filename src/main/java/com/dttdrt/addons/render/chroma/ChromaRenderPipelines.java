package com.dttdrt.addons.render.chroma;

import com.dttdrt.addons.mixin.accessor.RenderPipelinesAccessor;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

/**
 * Registers the custom textured chroma {@link RenderPipeline} for Minecraft 26.1.2.
 */
public final class ChromaRenderPipelines {

    public static final String CHROMA_UNIFORM_NAME = "DttdrtChromaUniforms";
    private static RenderPipeline chromaTextPipeline;

    private ChromaRenderPipelines() {
    }

    public static RenderPipeline getChromaText() {
        if (chromaTextPipeline == null) {
            chromaTextPipeline = RenderPipelinesAccessor.dttdrt$register(
                    RenderPipeline.builder(RenderPipelinesAccessor.dttdrt$getMatricesProjectionSnippet())
                            .withLocation(Identifier.fromNamespaceAndPath("dttdrtaddons", "chroma_text"))
                            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                            .withVertexShader(Identifier.fromNamespaceAndPath("dttdrtaddons", "textured_chroma"))
                            .withFragmentShader(Identifier.fromNamespaceAndPath("dttdrtaddons", "textured_chroma"))
                            .withSampler("Sampler0")
                            .withUniform(CHROMA_UNIFORM_NAME, UniformType.UNIFORM_BUFFER)
                            .build()
            );
        }
        return chromaTextPipeline;
    }
}
