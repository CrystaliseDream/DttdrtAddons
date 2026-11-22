package com.dttdrt.addons.features.storage.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Custom container GUI overlay interface adapted from Firmament's CustomGui.
 */
public abstract class CustomGui {

    public record Rect(int x, int y, int width, int height) {
        public int minX() { return x; }
        public int minY() { return y; }
        public int maxX() { return x + width; }
        public int maxY() { return y + height; }

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public abstract List<Rect> getBounds();

    public void moveSlot(Slot slot) {}

    public void render(GuiGraphicsExtractor graphics, float delta, int mouseX, int mouseY) {}

    public boolean mouseClick(MouseButtonEvent click, boolean doubled) {
        return false;
    }

    public void beforeSlotRender(GuiGraphicsExtractor graphics, Slot slot) {}

    public void afterSlotRender(GuiGraphicsExtractor graphics, Slot slot) {}

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    public boolean isClickOutsideBounds(double mouseX, double mouseY) {
        for (Rect r : getBounds()) {
            if (r.contains(mouseX, mouseY)) return false;
        }
        return true;
    }

    public boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        boolean inAnyBound = false;
        for (Rect r : getBounds()) {
            if (r.contains(pointX, pointY)) {
                inAnyBound = true;
                break;
            }
        }
        return inAnyBound && new Rect(x, y, width, height).contains(pointX, pointY);
    }

    public boolean isPointOverSlot(Slot slot, int xOffset, int yOffset, double pointX, double pointY) {
        return isPointWithinBounds(slot.x + xOffset, slot.y + yOffset, 16, 16, pointX, pointY);
    }

    public void onInit() {}

    public boolean shouldDrawForeground() {
        return true;
    }

    public boolean onVoluntaryExit() {
        return true;
    }

    public void onRemoved() {}

    public boolean mouseReleased(MouseButtonEvent click) {
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        return false;
    }

    public boolean keyPressed(KeyEvent input) {
        return false;
    }

    public boolean charTyped(CharacterEvent input) {
        return false;
    }

    public boolean keyReleased(KeyEvent input) {
        return false;
    }
}
