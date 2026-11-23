package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaRenderState;
import net.minecraft.network.chat.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextColor.class)
public abstract class ChromaTextColorMixin {

    @Inject(method = "equals", at = @At("TAIL"), cancellable = true)
    private void dttdrt$fixChromaEquals(Object other, CallbackInfoReturnable<Boolean> cir) {
        if (ChromaRenderState.isNotActuallyEqualBecauseOfChroma((TextColor) (Object) this, other)) {
            cir.setReturnValue(false);
        }
    }
}
