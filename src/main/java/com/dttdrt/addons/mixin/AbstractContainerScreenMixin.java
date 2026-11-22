package com.dttdrt.addons.mixin;

import com.dttdrt.addons.features.storage.StorageOverlayCustom;
import com.dttdrt.addons.features.storage.gui.CoordRememberingSlot;
import com.dttdrt.addons.features.storage.gui.CustomGui;
import com.dttdrt.addons.features.storage.gui.CustomGuiHost;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> extends Screen implements CustomGuiHost {

    @Shadow
    @Final
    protected T menu;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    @Final
    protected int imageWidth;

    @Shadow
    @Final
    protected int imageHeight;

    @Unique
    private CustomGui dttdrt$customGui;

    @Unique
    private boolean dttdrt$hasRememberedSlots = false;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Override
    public CustomGui dttdrt$getCustomGui() {
        return dttdrt$customGui;
    }

    @Override
    public void dttdrt$setCustomGui(CustomGui gui) {
        this.dttdrt$customGui = gui;
        dttdrt$fixSize();
    }

    @Override
    public void dttdrt$clearCustomGuiAndReinit() {
        this.dttdrt$customGui = null;
        if (dttdrt$hasRememberedSlots && this.menu != null) {
            for (Slot slot : this.menu.slots) {
                if (slot instanceof CoordRememberingSlot crs) {
                    crs.dttdrt$restoreCoords();
                }
            }
            dttdrt$hasRememberedSlots = false;
        }
        dttdrt$fixSize();
        this.init(this.width, this.height);
    }

    @Unique
    private void dttdrt$fixSize() {
        if (this.dttdrt$customGui != null) {
            List<CustomGui.Rect> bounds = this.dttdrt$customGui.getBounds();
            if (!bounds.isEmpty()) {
                CustomGui.Rect first = bounds.get(0);
                this.leftPos = (this.width - first.width()) / 2;
                this.topPos = (this.height - first.height()) / 2;
                if (this.dttdrt$customGui instanceof StorageOverlayCustom soc) {
                    soc.updateScreenOrigin(this.leftPos, this.topPos);
                }
                return;
            }
        }
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dttdrt$onInit(CallbackInfo ci) {
        if (this.dttdrt$customGui != null) {
            this.dttdrt$customGui.onInit();
            dttdrt$fixSize();
        }
    }

    @Inject(method = "extractContents", at = @At("HEAD"))
    private void dttdrt$onExtractContentsHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.dttdrt$customGui != null) {
            if (this.menu != null) {
                for (Slot slot : this.menu.slots) {
                    if (!dttdrt$hasRememberedSlots && slot instanceof CoordRememberingSlot crs) {
                        crs.dttdrt$rememberCoords();
                    }
                    this.dttdrt$customGui.moveSlot(slot);
                }
                dttdrt$hasRememberedSlots = true;
            }
            if (this.dttdrt$customGui instanceof StorageOverlayCustom soc) {
                soc.updateScreenOrigin(this.leftPos, this.topPos);
            }
            this.dttdrt$customGui.render(graphics, delta, mouseX, mouseY);
        } else if (dttdrt$hasRememberedSlots && this.menu != null) {
            for (Slot slot : this.menu.slots) {
                if (slot instanceof CoordRememberingSlot crs) {
                    crs.dttdrt$restoreCoords();
                }
            }
            dttdrt$hasRememberedSlots = false;
        }
    }

    @Redirect(
            method = "extractContents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"
            )
    )
    private void dttdrt$redirectExtractLabels(AbstractContainerScreen<?> instance, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.dttdrt$customGui != null && !this.dttdrt$customGui.shouldDrawForeground()) {
            return;
        }
        ((AbstractContainerScreenMixin<?>) (Object) instance).dttdrt$callExtractLabels(graphics, mouseX, mouseY);
    }

    @Shadow
    protected abstract void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    @Unique
    private void dttdrt$callExtractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.extractLabels(graphics, mouseX, mouseY);
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void dttdrt$beforeSlotRender(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.dttdrt$customGui != null) {
            this.dttdrt$customGui.beforeSlotRender(graphics, slot);
        }
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void dttdrt$afterSlotRender(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.dttdrt$customGui != null) {
            this.dttdrt$customGui.afterSlotRender(graphics, slot);
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onHasClickedOutside(double mx, double my, int xo, int yo, CallbackInfoReturnable<Boolean> cir) {
        if (this.dttdrt$customGui != null) {
            cir.setReturnValue(this.dttdrt$customGui.isClickOutsideBounds(mx, my));
        }
    }

    @Inject(method = "isHovering(IIIIDD)Z", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onIsHoveringRect(int left, int top, int w, int h, double xm, double ym, CallbackInfoReturnable<Boolean> cir) {
        if (this.dttdrt$customGui != null) {
            cir.setReturnValue(this.dttdrt$customGui.isPointWithinBounds(left + this.leftPos, top + this.topPos, w, h, xm, ym));
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onIsHoveringSlot(Slot slot, double xm, double ym, CallbackInfoReturnable<Boolean> cir) {
        if (this.dttdrt$customGui != null) {
            cir.setReturnValue(this.dttdrt$customGui.isPointOverSlot(slot, this.leftPos, this.topPos, xm, ym));
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onMouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (this.dttdrt$customGui != null && this.dttdrt$customGui.mouseClick(click, doubled)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onMouseDragged(MouseButtonEvent click, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (this.dttdrt$customGui != null && this.dttdrt$customGui.mouseDragged(click, dx, dy)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onMouseReleased(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (this.dttdrt$customGui != null && this.dttdrt$customGui.mouseReleased(click)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onMouseScrolled(double mx, double my, double hx, double vy, CallbackInfoReturnable<Boolean> cir) {
        if (this.dttdrt$customGui != null && this.dttdrt$customGui.mouseScrolled(mx, my, hx, vy)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (this.dttdrt$customGui != null && this.dttdrt$customGui.keyPressed(input)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void dttdrt$onClose(CallbackInfo ci) {
        if (this.dttdrt$customGui != null && !this.dttdrt$customGui.onVoluntaryExit()) {
            ci.cancel();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void dttdrt$onRemoved(CallbackInfo ci) {
        if (this.dttdrt$customGui != null) {
            this.dttdrt$customGui.onRemoved();
        }
    }
}
