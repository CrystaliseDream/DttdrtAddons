package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.storage.gui.CustomGuiHost;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerScreen.class)
public class ContainerScreenMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onExtractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this instanceof CustomGuiHost host && host.dttdrt$getCustomGui() != null) {
            ((Screen) (Object) this).extractTransparentBackground(graphics);
            ci.cancel();
        }
    }
}
