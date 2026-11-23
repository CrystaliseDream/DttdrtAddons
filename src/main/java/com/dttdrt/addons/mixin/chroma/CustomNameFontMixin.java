package com.dttdrt.addons.mixin.chroma;

import com.dttdrt.addons.features.custom.CustomNameFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Font.class)
public abstract class CustomNameFontMixin {

    @Shadow
    public abstract Font.PreparedText prepareText(
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            boolean includeEmpty,
            int backgroundColor
    );

    @Shadow
    public abstract int width(FormattedCharSequence text);

    @Inject(
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dttdrt$replacePrepareString(
            String text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            int backgroundColor,
            CallbackInfoReturnable<Font.PreparedText> cir
    ) {
        if (CustomNameFeature.shouldTransform(text)) {
            FormattedCharSequence seq = CustomNameFeature.replaceStringToSequence(text);
            cir.setReturnValue(this.prepareText(seq, x, y, color, dropShadow, false, backgroundColor));
        }
    }

    @ModifyVariable(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private FormattedCharSequence dttdrt$replacePrepareSequence(FormattedCharSequence text) {
        return CustomNameFeature.replaceInSequence(text);
    }

    @ModifyVariable(
            method = "drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private FormattedCharSequence dttdrt$replaceOutlineSequence(FormattedCharSequence text) {
        return CustomNameFeature.replaceInSequence(text);
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void dttdrt$replaceWidthString(String text, CallbackInfoReturnable<Integer> cir) {
        if (CustomNameFeature.shouldTransform(text)) {
            cir.setReturnValue(this.width(CustomNameFeature.replaceStringToSequence(text)));
        }
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedCharSequence dttdrt$replaceWidthSequence(FormattedCharSequence text) {
        return CustomNameFeature.replaceInSequence(text);
    }

    @ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
    private FormattedText dttdrt$replaceWidthFormattedText(FormattedText text) {
        return CustomNameFeature.replaceInFormattedText(text);
    }

    @ModifyVariable(method = "wordWrapHeight(Lnet/minecraft/network/chat/FormattedText;I)I", at = @At("HEAD"), argsOnly = true)
    private FormattedText dttdrt$replaceWordWrapFormattedText(FormattedText text) {
        return CustomNameFeature.replaceInFormattedText(text);
    }

    @ModifyVariable(method = "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;", at = @At("HEAD"), argsOnly = true)
    private FormattedText dttdrt$replaceSplitFormattedText(FormattedText text) {
        return CustomNameFeature.replaceInFormattedText(text);
    }

    @Inject(method = "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void dttdrt$markSplitLines(FormattedText text, int maxWidth, CallbackInfoReturnable<List<FormattedCharSequence>> cir) {
        if (CustomNameFeature.isEnabledStatic()) {
            cir.setReturnValue(CustomNameFeature.markTransformedLines(cir.getReturnValue()));
        }
    }

    @ModifyVariable(method = "splitIgnoringLanguage(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;", at = @At("HEAD"), argsOnly = true)
    private FormattedText dttdrt$replaceSplitRawFormattedText(FormattedText text) {
        return CustomNameFeature.replaceInFormattedText(text);
    }

    @Inject(method = "splitIgnoringLanguage(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void dttdrt$markSplitRawLines(FormattedText text, int maxWidth, CallbackInfoReturnable<List<FormattedText>> cir) {
        if (CustomNameFeature.isEnabledStatic()) {
            cir.setReturnValue(CustomNameFeature.markTransformedTexts(cir.getReturnValue()));
        }
    }

    @ModifyVariable(method = "substrByWidth(Lnet/minecraft/network/chat/FormattedText;I)Lnet/minecraft/network/chat/FormattedText;", at = @At("HEAD"), argsOnly = true)
    private FormattedText dttdrt$replaceSubstrFormattedText(FormattedText text) {
        return CustomNameFeature.replaceInFormattedText(text);
    }

    @Inject(method = "substrByWidth(Lnet/minecraft/network/chat/FormattedText;I)Lnet/minecraft/network/chat/FormattedText;", at = @At("RETURN"), cancellable = true)
    private void dttdrt$markSubstrFormattedText(FormattedText text, int maxWidth, CallbackInfoReturnable<FormattedText> cir) {
        if (CustomNameFeature.isEnabledStatic()) {
            cir.setReturnValue(CustomNameFeature.markTransformedText(cir.getReturnValue()));
        }
    }
}
