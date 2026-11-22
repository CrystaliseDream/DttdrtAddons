package com.dttdrt.addons.features.storage;

import com.dttdrt.addons.core.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple multi-column overview grid screen for Storage pages (companion to StorageOverlayScreen).
 * Direct 1:1 port of Firmament's StorageOverviewScreen.
 */
public class StorageOverviewScreen extends Screen {

    public static final Set<Item> EMPTY_STORAGE_SLOT_ITEMS = Set.of(
            Blocks.RED_STAINED_GLASS_PANE.asItem(),
            Blocks.BROWN_STAINED_GLASS_PANE.asItem(),
            Items.GRAY_DYE
    );

    public static final int PAGE_WIDTH = 19 * 9;
    private static int scroll = 0;
    private static int lastRenderedHeight = 0;

    private final StorageData content = StorageData.getInstance();
    private boolean isClosing = false;

    public StorageOverviewScreen() {
        super(Component.empty());
    }

    public boolean isClosing() {
        return isClosing;
    }

    public void setClosing(boolean closing) {
        this.isClosing = closing;
    }

    @Override
    protected void init() {
        super.init();
        scroll = Math.max(0, Math.min(getMaxScroll(), scroll));
    }

    @Override
    public void onClose() {
        if (!ModConfig.getInstance().storageOverlayRetainScroll) {
            scroll = 0;
        }
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
        forEachPageLayout((slot, inv, offsetX, offsetY) -> {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) offsetX, (float) offsetY);
            renderStoragePage(graphics, inv, mouseX - offsetX, mouseY - offsetY);
            graphics.pose().popMatrix();
        });
    }

    @FunctionalInterface
    private interface PageLayoutConsumer {
        void accept(StoragePageSlot slot, StorageData.StorageInventory inv, int offsetX, int offsetY);
    }

    private void forEachPageLayout(PageLayoutConsumer consumer) {
        int columns = Math.max(1, ModConfig.getInstance().storageOverlayColumns);
        int padding = 5;
        int margin = 20;
        int offsetY = 0;
        int currentMaxHeight = margin - padding - scroll;
        int totalHeight = -currentMaxHeight;
        int idx = 0;
        for (Map.Entry<StoragePageSlot, StorageData.StorageInventory> entry : content.snapshotInventories().entrySet()) {
            int pageX = idx % columns;
            if (pageX == 0) {
                currentMaxHeight += padding;
                offsetY += currentMaxHeight;
                totalHeight += currentMaxHeight;
                currentMaxHeight = 0;
            }
            int xPos = this.width / 2
                    - (columns * (PAGE_WIDTH + padding) - padding) / 2
                    + pageX * (PAGE_WIDTH + padding);
            consumer.accept(entry.getKey(), entry.getValue(), xPos, offsetY);
            int h = getStorePageHeight(entry.getValue());
            currentMaxHeight = Math.max(currentMaxHeight, h);
            idx++;
        }
        lastRenderedHeight = totalHeight + currentMaxHeight;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        int columns = Math.max(1, ModConfig.getInstance().storageOverlayColumns);
        int padding = 5;
        int margin = 20;
        int offsetY = 0;
        int currentMaxHeight = margin - padding - scroll;
        int idx = 0;
        for (Map.Entry<StoragePageSlot, StorageData.StorageInventory> entry : content.snapshotInventories().entrySet()) {
            int pageX = idx % columns;
            if (pageX == 0) {
                currentMaxHeight += padding;
                offsetY += currentMaxHeight;
                currentMaxHeight = 0;
            }
            int xPos = this.width / 2
                    - (columns * (PAGE_WIDTH + padding) - padding) / 2
                    + pageX * (PAGE_WIDTH + padding);
            double rx = click.x() - xPos;
            double ry = click.y() - offsetY;
            int h = getStorePageHeight(entry.getValue());
            if (rx >= 0.0 && rx <= PAGE_WIDTH && ry >= 0.0 && ry <= h) {
                onClose();
                StorageOverlayManager.lastStorageOverview = this;
                entry.getKey().navigateTo();
                return true;
            }
            currentMaxHeight = Math.max(currentMaxHeight, h);
            idx++;
        }
        return super.mouseClicked(click, doubled);
    }

    private int getStorePageHeight(StorageData.StorageInventory page) {
        VirtualInventory inv = page.inventory;
        return inv != null ? (inv.rows() * 19 + this.font.lineHeight + 2) : 60;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(getMaxScroll(),
                scroll + (int) StorageOverlayManager.adjustScrollSpeed(verticalAmount)));
        return true;
    }

    private int getMaxScroll() {
        return Math.max(0, lastRenderedHeight - this.height + 40);
    }

    private void renderStoragePage(GuiGraphicsExtractor graphics, StorageData.StorageInventory page, int mouseX, int mouseY) {
        graphics.text(this.font, content.displayName(page.slot), 2, 2, -1, true);
        VirtualInventory inv = page.inventory;
        if (inv == null) {
            graphics.fill(0, 0, PAGE_WIDTH, 60, 0xFF401010);
            graphics.centeredText(this.font, Component.literal("Not loaded yet"), PAGE_WIDTH / 2, 30, -1);
            return;
        }
        List<ItemStack> stacks = inv.stacks();
        for (int i = 0; i < stacks.size(); i++) {
            int x = (i % 9) * 19;
            int y = (i / 9) * 19 + this.font.lineHeight + 2;
            boolean hovered = (mouseX - x) >= 0 && (mouseX - x) < 18 && (mouseY - y) >= 0 && (mouseY - y) < 18;
            graphics.fill(x, y, x + 18, y + 18, hovered ? 0x80808080 : 0x40808080);
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                graphics.item(stack, x + 1, y + 1);
                graphics.itemDecorations(this.font, stack, x + 1, y + 1);
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.isEscape()) {
            isClosing = true;
        }
        return super.keyPressed(input);
    }
}
