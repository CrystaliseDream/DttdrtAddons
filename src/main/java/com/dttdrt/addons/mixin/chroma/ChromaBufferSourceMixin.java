package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaRenderTypes;
import com.dttdrt.addons.render.chroma.RenderTypeNameAccess;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class ChromaBufferSourceMixin {

    @Shadow
    protected RenderType lastSharedType;

    @Redirect(
            method = "getBuffer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderType;canConsolidateConsecutiveGeometry()Z")
    )
    private boolean dttdrt$keepChromaBatchOpen(RenderType renderType) {
        if (renderType != null && this.lastSharedType != null) {
            String curName = ((RenderTypeNameAccess) (Object) renderType).dttdrt$getName();
            String lastName = ((RenderTypeNameAccess) (Object) this.lastSharedType).dttdrt$getName();
            if (ChromaRenderTypes.CHROMA_TEXT_LAYER_NAME.equals(curName) && curName.equals(lastName)) {
                return true;
            }
        }
        return renderType.canConsolidateConsecutiveGeometry();
    }
}
