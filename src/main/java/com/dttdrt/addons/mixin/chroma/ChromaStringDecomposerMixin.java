package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.render.chroma.ChromaRenderState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(StringDecomposer.class)
public abstract class ChromaStringDecomposerMixin {

    @Unique
    private static final ThreadLocal<Boolean> DTTDRT_PENDING_CHROMA =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Redirect(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/ChatFormatting;getByCode(C)Lnet/minecraft/ChatFormatting;")
    )
    private static ChatFormatting dttdrt$onGetByCode(char code) {
        if (ChromaRenderState.isChromaCode(code)) {
            DTTDRT_PENDING_CHROMA.set(Boolean.TRUE);
            return ChatFormatting.WHITE;
        }
        DTTDRT_PENDING_CHROMA.set(Boolean.FALSE);
        return ChatFormatting.getByCode(code);
    }

    @Redirect(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Style;applyLegacyFormat(Lnet/minecraft/ChatFormatting;)Lnet/minecraft/network/chat/Style;")
    )
    private static Style dttdrt$onApplyLegacyFormat(Style style, ChatFormatting formatting) {
        if (Boolean.TRUE.equals(DTTDRT_PENDING_CHROMA.get())) {
            DTTDRT_PENDING_CHROMA.set(Boolean.FALSE);
            return ChromaRenderState.getChromaStyle();
        }
        return style.applyLegacyFormat(formatting);
    }
}
