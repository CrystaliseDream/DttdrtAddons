package com.dttdrt.addons.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {

    @Accessor("MATRICES_PROJECTION_SNIPPET")
    static RenderPipeline.Snippet dttdrt$getMatricesProjectionSnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline dttdrt$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
