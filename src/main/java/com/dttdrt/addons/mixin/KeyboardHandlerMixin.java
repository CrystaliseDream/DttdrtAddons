package com.dttdrt.addons.mixin;

import com.dttdrt.addons.DttdrtAddons;
import com.dttdrt.addons.features.storage.gui.CustomGui;
import com.dttdrt.addons.features.storage.gui.CustomGuiHost;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onCharTyped(long window, CharacterEvent input, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.getWindow() == null) return;
        if (window != 0L && window != this.minecraft.getWindow().handle()) return;
        Screen screen = this.minecraft.screen;
        if (screen instanceof CustomGuiHost host) {
            CustomGui custom = host.dttdrt$getCustomGui();
            if (custom != null && custom.charTyped(input)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onKeyPress(long window, int action, KeyEvent input, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.getWindow() == null) return;
        if (window != 0L && window != this.minecraft.getWindow().handle()) return;

        if (action == 1 && this.minecraft.screen == null) {
            DttdrtAddons.handleInGameKeyPress(input);
            return;
        }

        if (action != 0) return; // only forward key release here; keyPressed handled by AbstractContainerScreen
        Screen screen = this.minecraft.screen;
        if (screen instanceof CustomGuiHost host) {
            CustomGui custom = host.dttdrt$getCustomGui();
            if (custom != null && custom.keyReleased(input)) {
                ci.cancel();
            }
        }
    }
}
