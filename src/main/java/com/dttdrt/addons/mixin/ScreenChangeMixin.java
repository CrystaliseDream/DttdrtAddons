package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.storage.StorageOverlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepts screen changes to attach Firmament-style Storage Preview overlay on /storage, /ec, /bp.
 * Build scripts for 26.2 and 26.3 rewrite @Mixin(Minecraft.class) to @Mixin(net.minecraft.client.gui.Gui.class).
 */
@Mixin(Minecraft.class)
public class ScreenChangeMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen dttdrt$onSetScreen(Screen newScreen) {
        Minecraft mc = Minecraft.getInstance();
        Screen oldScreen = mc != null ? mc.screen : null;
        Screen override = StorageOverlayManager.onScreenChange(oldScreen, newScreen);
        return override != null ? override : newScreen;
    }
}
