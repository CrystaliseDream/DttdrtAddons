package com.dttdrt.addons.features.storage;

import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.features.storage.gui.CoordRememberingSlot;
import com.dttdrt.addons.features.storage.gui.CustomGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * CustomGui adapter attaching StorageOverlayScreen onto a live ContainerScreen.
 * Direct 1:1 port of Firmament's StorageOverlayCustom.
 */
public final class StorageOverlayCustom extends CustomGui {

    private final StorageBackingHandle handler;
    private final ContainerScreen screen;
    private final StorageOverlayScreen overview;
    private int screenLeftPos = 0;
    private int screenTopPos = 0;

    public StorageOverlayCustom(StorageBackingHandle handler, ContainerScreen screen, StorageOverlayScreen overview) {
        this.handler = handler;
        this.screen = screen;
        this.overview = overview;
    }

    public StorageOverlayScreen getOverview() {
        return overview;
    }

    public void updateScreenOrigin(int leftPos, int topPos) {
        this.screenLeftPos = leftPos;
        this.screenTopPos = topPos;
    }

    @Override
    public boolean onVoluntaryExit() {
        overview.setExiting(true);
        overview.syncTextInputForHost(screen, false);
        StorageOverlayScreen.resetScroll();
        return super.onVoluntaryExit();
    }

    @Override
    public void onRemoved() {
        overview.syncTextInputForHost(screen, false);
    }

    @Override
    public List<Rect> getBounds() {
        return overview.getBounds();
    }

    @Override
    public void beforeSlotRender(GuiGraphicsExtractor graphics, Slot slot) {
        if (!(slot.container instanceof Inventory)) {
            overview.createScissors(graphics);
        }
        if (ModConfig.getInstance().storageOverlayHighlightSearch) {
            String search = StorageOverlayScreen.getSearchText();
            if (search != null && !search.isBlank() && slot.hasItem() && overview.matchesSearch(slot.getItem(), search)) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF00B000);
            }
        }
    }

    @Override
    public void afterSlotRender(GuiGraphicsExtractor graphics, Slot slot) {
        if (!(slot.container instanceof Inventory)) {
            graphics.disableScissor();
        }
    }

    @Override
    public void onInit() {
        overview.init(screen.width, screen.height);
        overview.syncTextInputForHost(screen, overview.isSearchFocused());
    }

    @Override
    public boolean isPointOverSlot(Slot slot, int xOffset, int yOffset, double pointX, double pointY) {
        if (!super.isPointOverSlot(slot, xOffset, yOffset, pointX, pointY)) {
            return false;
        }
        if (!(slot.container instanceof Inventory)) {
            return overview.getScrollPanelInner().contains(pointX, pointY);
        }
        return true;
    }

    @Override
    public boolean shouldDrawForeground() {
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return overview.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        return overview.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        return overview.keyReleased(input);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return overview.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return overview.charTyped(input);
    }

    @Override
    public boolean mouseClick(MouseButtonEvent click, boolean doubled) {
        StoragePageSlot activePage = (handler instanceof StorageBackingHandle.Page p) ? p.storagePageSlot() : null;
        return overview.mouseClicked(click, doubled, activePage, screen);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, float delta, int mouseX, int mouseY) {
        overview.syncTextInputForHost(screen, overview.isSearchFocused());
        overview.drawBackgrounds(graphics);
        StoragePageSlot activePage = (handler instanceof StorageBackingHandle.Page p) ? p.storagePageSlot() : null;
        int chestSlots = Math.min(screen.getMenu().slots.size(), screen.getMenu().getRowCount() * 9);
        List<Slot> pageSlots = chestSlots > 9
                ? screen.getMenu().slots.subList(9, chestSlots)
                : List.of();
        overview.drawPages(graphics, mouseX, mouseY, delta, activePage, pageSlots, screenLeftPos, screenTopPos);
        overview.drawScrollBar(graphics);
        overview.drawControls(graphics, mouseX, mouseY);
    }

    @Override
    public void moveSlot(Slot slot) {
        if (!(slot instanceof CoordRememberingSlot crs)) return;
        if (slot.container instanceof Inventory) {
            int idx = slot.getContainerSlot();
            if (idx >= 0 && idx < 36) {
                int[] pos = overview.getPlayerInventorySlotPosition(idx);
                crs.dttdrt$setCoords(pos[0] - screenLeftPos, pos[1] - screenTopPos);
                return;
            }
        }
        crs.dttdrt$setCoords(-100000, -100000);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ModConfig.getInstance().storageOverlayItemsBlockScroll) {
            Minecraft mc = Minecraft.getInstance();
            // If cursor is hovering over a non-empty slot inside player/active page, do not hijack item scroll actions
            for (Slot s : screen.getMenu().slots) {
                if (s.hasItem() && isPointOverSlot(s, screenLeftPos, screenTopPos, mouseX, mouseY)) {
                    return false;
                }
            }
        }
        return overview.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
