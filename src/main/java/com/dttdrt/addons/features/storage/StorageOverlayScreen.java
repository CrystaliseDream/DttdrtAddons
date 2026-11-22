package com.dttdrt.addons.features.storage;

import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.features.mining.profit.MiningItemTable;
import com.dttdrt.addons.features.storage.gui.CustomGui.Rect;
import com.dttdrt.addons.features.storage.gui.CustomGuiHost;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.joml.Matrix3x2f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scrollable multi-column Ender Chest & Backpack Storage Preview overlay screen.
 * Direct 1:1 port of Firmament's StorageOverlayScreen using the same sprite assets and geometry.
 */
public class StorageOverlayScreen extends Screen {

    public static final int PLAYER_WIDTH = 184;
    public static final int PLAYER_HEIGHT = 91;
    public static final int PLAYER_Y_INSET = 3;
    public static final int SLOT_SIZE = 18;
    public static final int PADDING = 10;
    public static final int PAGE_SLOTS_WIDTH = SLOT_SIZE * 9;
    public static final int PAGE_WIDTH = PAGE_SLOTS_WIDTH + 4;
    public static final int HOTBAR_X = 12;
    public static final int HOTBAR_Y = 67;
    public static final int MAIN_INVENTORY_Y = 9;
    public static final int SCROLL_BAR_WIDTH = 8;
    public static final int SCROLL_BAR_HEIGHT = 16;
    public static final int CONTROL_X_INSET = 3;
    public static final int CONTROL_Y_INSET = 5;
    public static final int CONTROL_WIDTH = 70;
    public static final int CONTROL_BACKGROUND_WIDTH = CONTROL_WIDTH + CONTROL_X_INSET + 1;
    public static final int CONTROL_HEIGHT = 50;

    private static final Identifier PLAYER_INVENTORY_SPRITE =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "storageoverlay/player_inventory");
    private static final Identifier UPPER_BACKGROUND_SPRITE =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "storageoverlay/upper_background");
    private static final Identifier SLOT_ROW_SPRITE =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "storageoverlay/storage_row");
    private static final Identifier SCROLLBAR_BACKGROUND_SPRITE =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "storageoverlay/scroll_bar_background");
    private static final Identifier SCROLLBAR_KNOB_SPRITE =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "storageoverlay/scroll_bar_knob");
    private static final Identifier CONTROLLER_BACKGROUND_SPRITE =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "storageoverlay/storage_controls");

    private static float scroll = 0f;
    private static int lastRenderedInnerHeight = 0;
    private static String searchText = "";

    // MC 26.3+ SDL3 TextInputManager reflection bridge so search input works in 26.3
    private static boolean textInputReflectInit = false;
    private static Method mcTextInputManagerMethod = null;
    private static Method timOnFocusChangeMethod = null;

    private boolean isExiting = false;
    private boolean searchFocused = false;
    private boolean knobGrabbed = false;
    private boolean editBtnHovered = false;
    private int pageWidthCount = 3;
    private Measurements measurements = new Measurements();

    private String searchCache = null;
    private Set<StoragePageSlot> filteredPagesCache = Set.of();

    private Screen textInputOwner = null;
    private boolean textInputActive = false;

    public final class Measurements {
        public final int innerScrollPanelWidth;
        public final int overviewWidth;
        public final int x;
        public final int overviewHeight;
        public final int innerScrollPanelHeight;
        public final int y;
        public final int playerX;
        public final int playerY;
        public final int controlX;
        public final int controlY;
        public final int totalWidth;
        public final int totalHeight;

        Measurements() {
            ModConfig cfg = ModConfig.getInstance();
            this.innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING;
            this.overviewWidth = innerScrollPanelWidth + 3 * PADDING + SCROLL_BAR_WIDTH;
            this.x = StorageOverlayScreen.this.width / 2 - overviewWidth / 2;
            this.overviewHeight = Math.max(80, Math.min(
                    StorageOverlayScreen.this.height - PLAYER_HEIGHT - Math.min(80, StorageOverlayScreen.this.height / 10),
                    cfg.storageOverlayHeight));
            this.innerScrollPanelHeight = overviewHeight - PADDING * 2;
            this.y = StorageOverlayScreen.this.height / 2 - (overviewHeight + PLAYER_HEIGHT) / 2;
            this.playerX = StorageOverlayScreen.this.width / 2 - PLAYER_WIDTH / 2;
            this.playerY = y + overviewHeight - PLAYER_Y_INSET;
            this.controlX = playerX - CONTROL_WIDTH + CONTROL_X_INSET;
            this.controlY = playerY - CONTROL_Y_INSET;
            this.totalWidth = overviewWidth;
            this.totalHeight = overviewHeight - PLAYER_Y_INSET + PLAYER_HEIGHT;
        }
    }

    public StorageOverlayScreen() {
        super(Component.literal(""));
    }

    public static String getSearchText() {
        return searchText;
    }

    public static void resetScroll() {
        if (!ModConfig.getInstance().storageOverlayRetainScroll) {
            scroll = 0f;
        }
    }

    public boolean isExiting() {
        return isExiting;
    }

    public void setExiting(boolean exiting) {
        this.isExiting = exiting;
    }

    public boolean isSearchFocused() {
        return searchFocused;
    }

    public Measurements getMeasurements() {
        return measurements;
    }

    @Override
    protected void init() {
        super.init();
        int cols = Math.max(1, ModConfig.getInstance().storageOverlayColumns);
        int maxCols = Math.max(1, (this.width - PADDING) / (PAGE_WIDTH + PADDING));
        this.pageWidthCount = Math.max(1, Math.min(cols, maxCols));
        this.measurements = new Measurements();
        layoutPages(StorageData.getInstance(), null);
        coerceScroll(0f);
        syncTextInputForHost(this, searchFocused);
    }

    @Override
    public void onClose() {
        isExiting = true;
        searchFocused = false;
        knobGrabbed = false;
        syncTextInputForHost(this, false);
        resetScroll();
        super.onClose();
    }

    @Override
    public void removed() {
        searchFocused = false;
        knobGrabbed = false;
        syncTextInputForHost(this, false);
        super.removed();
    }

    public void syncTextInputForHost(Screen hostScreen, boolean active) {
        if (hostScreen == null) {
            active = false;
        }
        if (active == textInputActive && hostScreen == textInputOwner) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            textInputActive = false;
            return;
        }
        if (!textInputReflectInit) {
            textInputReflectInit = true;
            try {
                mcTextInputManagerMethod = mc.getClass().getMethod("textInputManager");
                Class<?> timClass = mcTextInputManagerMethod.getReturnType();
                timOnFocusChangeMethod = timClass.getMethod("onTextInputFocusChange", Object.class, boolean.class);
            } catch (Throwable ignored) {
                mcTextInputManagerMethod = null;
                timOnFocusChangeMethod = null;
            }
        }
        if (mcTextInputManagerMethod != null && timOnFocusChangeMethod != null) {
            try {
                Object tim = mcTextInputManagerMethod.invoke(mc);
                if (tim != null) {
                    if (textInputActive && textInputOwner != null && (!active || textInputOwner != hostScreen)) {
                        timOnFocusChangeMethod.invoke(tim, textInputOwner, false);
                    }
                    if (active && hostScreen != null) {
                        timOnFocusChangeMethod.invoke(tim, hostScreen, true);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        textInputOwner = active ? hostScreen : null;
        textInputActive = active;
    }

    public void coerceScroll(float offset) {
        scroll = Math.max(0f, Math.min(getMaxScroll(), scroll + offset));
    }

    public float getMaxScroll() {
        return Math.max(0f, (float) lastRenderedInnerHeight - getScrollPanelInner().height());
    }

    public float getScrollbarPercentage() {
        float max = getMaxScroll();
        if (max <= 0f) return 0f;
        return Math.max(0f, Math.min(1f, scroll / max));
    }

    public Rect getScrollBarRect() {
        return new Rect(
                measurements.x + PADDING + measurements.innerScrollPanelWidth + PADDING,
                measurements.y + PADDING,
                SCROLL_BAR_WIDTH,
                measurements.innerScrollPanelHeight
        );
    }

    public Rect getScrollPanelInner() {
        return new Rect(
                measurements.x + PADDING,
                measurements.y + PADDING,
                measurements.innerScrollPanelWidth,
                measurements.innerScrollPanelHeight
        );
    }

    private Rect getSearchBoxRect() {
        return new Rect(measurements.controlX + 6, measurements.controlY + 6, CONTROL_WIDTH - 10, 15);
    }

    private Rect getEditButtonRect() {
        return new Rect(measurements.controlX + 6, measurements.controlY + 25, CONTROL_WIDTH - 10, 18);
    }

    public void createScissors(GuiGraphicsExtractor context) {
        Rect rect = getScrollPanelInner();
        Matrix3x2f inv = new Matrix3x2f(context.pose()).invert();
        Vector3f p1 = new Vector3f(rect.minX(), rect.minY(), 1f).mul(inv);
        Vector3f p2 = new Vector3f(rect.maxX(), rect.maxY(), 1f).mul(inv);
        context.enableScissor((int) p1.x, (int) p1.y, (int) p2.x, (int) p2.y);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        syncTextInputForHost(this, searchFocused);
        drawBackgrounds(context);
        drawPages(context, mouseX, mouseY, delta, null, null, 0, 0);
        drawScrollBar(context);
        drawPlayerInventory(context);
        drawControls(context, mouseX, mouseY);
    }

    public void drawBackgrounds(GuiGraphicsExtractor context) {
        context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                UPPER_BACKGROUND_SPRITE,
                measurements.x,
                measurements.y,
                measurements.overviewWidth,
                measurements.overviewHeight
        );
        context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                PLAYER_INVENTORY_SPRITE,
                measurements.playerX,
                measurements.playerY,
                PLAYER_WIDTH,
                PLAYER_HEIGHT
        );
    }

    public void drawScrollBar(GuiGraphicsExtractor context) {
        Rect sbRect = getScrollBarRect();
        context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                SCROLLBAR_BACKGROUND_SPRITE,
                sbRect.minX(), sbRect.minY(),
                sbRect.width(), sbRect.height()
        );
        int knobY = sbRect.minY() + (int) (getScrollbarPercentage() * Math.max(0, sbRect.height() - SCROLL_BAR_HEIGHT));
        context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                SCROLLBAR_KNOB_SPRITE,
                sbRect.minX(), knobY,
                SCROLL_BAR_WIDTH, SCROLL_BAR_HEIGHT
        );
    }

    public void drawControls(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                CONTROLLER_BACKGROUND_SPRITE,
                measurements.controlX,
                measurements.controlY,
                CONTROL_BACKGROUND_WIDTH,
                CONTROL_HEIGHT
        );

        Minecraft mc = Minecraft.getInstance();
        var f = this.font != null ? this.font : mc.font;

        // 1. Search TextField matching Firmament's controls
        Rect searchRect = getSearchBoxRect();
        context.fill(searchRect.x(), searchRect.y(), searchRect.maxX(), searchRect.maxY(), 0xFF000000);
        int borderCol = searchFocused ? 0xFFFFFFFF : 0xFFA0A0A0;
        context.outline(searchRect.x(), searchRect.y(), searchRect.width(), searchRect.height(), borderCol);

        int textX = searchRect.x() + 3;
        int textY = searchRect.y() + 3;
        int maxTextW = searchRect.width() - 6;
        if (searchText.isEmpty() && !searchFocused) {
            String ph = trimLeftToWidth(f, "Search...", maxTextW);
            context.text(f, ph, textX, textY, 0xFF808080, false);
        } else {
            String visible = trimRightEndToWidth(f, searchText, maxTextW - 3);
            context.text(f, visible, textX, textY, 0xFFE0E0E0, false);
            if (searchFocused && ((System.currentTimeMillis() / 500L) % 2L == 0L)) {
                int caretX = textX + f.width(visible);
                context.fill(caretX, textY - 1, caretX + 1, textY + 9, 0xFFE0E0E0);
            }
        }

        // 2. Edit Pages button
        Rect editRect = getEditButtonRect();
        editBtnHovered = editRect.contains(mouseX, mouseY);
        int btnBg = editBtnHovered ? 0xFF6B6B6B : 0xFF545454;
        int btnBorder = editBtnHovered ? 0xFFFFFFFF : 0xFF202020;
        context.fill(editRect.x(), editRect.y(), editRect.maxX(), editRect.maxY(), btnBg);
        context.outline(editRect.x(), editRect.y(), editRect.width(), editRect.height(), btnBorder);
        context.centeredText(f, "Edit Pages", editRect.x() + editRect.width() / 2, editRect.y() + 5, 0xFFFFFFFF);
    }

    private static String trimLeftToWidth(net.minecraft.client.gui.Font f, String s, int maxW) {
        if (s == null || f.width(s) <= maxW) return s == null ? "" : s;
        String out = s;
        while (!out.isEmpty() && f.width(out) > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static String trimRightEndToWidth(net.minecraft.client.gui.Font f, String s, int maxW) {
        if (s == null || f.width(s) <= maxW) return s == null ? "" : s;
        int start = 0;
        while (start < s.length() && f.width(s.substring(start)) > maxW) {
            start++;
        }
        return s.substring(start);
    }

    public int[] getPlayerInventorySlotPosition(int index) {
        if (index < 9) {
            return new int[]{measurements.playerX + index * SLOT_SIZE + HOTBAR_X, HOTBAR_Y + measurements.playerY};
        }
        return new int[]{
                measurements.playerX + (index % 9) * SLOT_SIZE + HOTBAR_X,
                measurements.playerY + (index / 9 - 1) * SLOT_SIZE + MAIN_INVENTORY_Y
        };
    }

    private void drawPlayerInventory(GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            int[] pos = getPlayerInventorySlotPosition(i);
            context.item(item, pos[0], pos[1], 0);
            context.itemDecorations(this.font, item, pos[0], pos[1]);
        }
    }

    public void drawPages(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float delta,
            StoragePageSlot excluding,
            List<Slot> slots,
            int slotOffsetX,
            int slotOffsetY
    ) {
        createScissors(context);
        StorageData data = StorageData.getInstance();
        layoutPages(data, (rect, page, inventory) -> drawPage(
                context,
                rect.x(),
                rect.y(),
                page,
                inventory,
                (excluding != null && excluding.equals(page)) ? slots : null,
                slotOffsetX,
                slotOffsetY,
                mouseX,
                mouseY
        ));
        context.disableScissor();
    }

    @FunctionalInterface
    private interface PageDrawConsumer {
        void accept(Rect rect, StoragePageSlot page, StorageData.StorageInventory inventory);
    }

    private void layoutPages(StorageData data, PageDrawConsumer consumer) {
        int yOffset = -(int) scroll;
        int xOffset = 0;
        int maxHeight = 0;
        Set<StoragePageSlot> filter = getFilteredPages();
        var f = this.font != null ? this.font : Minecraft.getInstance().font;
        for (Map.Entry<StoragePageSlot, StorageData.StorageInventory> entry : data.snapshotInventories().entrySet()) {
            StoragePageSlot page = entry.getKey();
            StorageData.StorageInventory inventory = entry.getValue();
            if (!filter.contains(page)) continue;
            VirtualInventory vi = inventory.inventory;
            int currentHeight = vi != null ? (vi.rows() * SLOT_SIZE + 6 + f.lineHeight) : 18;
            maxHeight = Math.max(maxHeight, currentHeight);
            Rect rect = new Rect(
                    measurements.x + PADDING + (PAGE_WIDTH + PADDING) * xOffset,
                    yOffset + measurements.y + PADDING,
                    PAGE_WIDTH,
                    currentHeight
            );
            if (consumer != null) {
                consumer.accept(rect, page, inventory);
            }
            xOffset++;
            if (xOffset >= pageWidthCount) {
                yOffset += maxHeight;
                xOffset = 0;
                maxHeight = 0;
            }
        }
        lastRenderedInnerHeight = maxHeight + yOffset + (int) scroll;
    }

    private int drawPage(
            GuiGraphicsExtractor context,
            int x,
            int y,
            StoragePageSlot page,
            StorageData.StorageInventory inventory,
            List<Slot> slots,
            int slotOffsetX,
            int slotOffsetY,
            int mouseX,
            int mouseY
    ) {
        var f = this.font != null ? this.font : Minecraft.getInstance().font;
        VirtualInventory inv = inventory.inventory;
        if (inv == null) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, UPPER_BACKGROUND_SPRITE, x, y, PAGE_WIDTH, 18);
            context.text(f, Component.literal("TODO: open this page"), x + 4, y + 4, -1, true);
            return 18;
        }
        if (slots != null && slots.size() != inv.stacks().size()) {
            return 0;
        }
        ModConfig cfg = ModConfig.getInstance();
        String name = StorageData.getInstance().displayName(page);
        int pageHeight = inv.rows() * SLOT_SIZE + 8 + f.lineHeight;
        if (slots != null && cfg.storageOverlayOutlineActivePage) {
            context.outline(x, y + 3 + f.lineHeight, PAGE_WIDTH, inv.rows() * SLOT_SIZE + 4, 0xFFFFFF00);
        }
        context.text(f, Component.literal(name), x + 6, y + 3, slots == null ? 0xFFFFFFFF : 0xFFFFFF00, true);
        context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                SLOT_ROW_SPRITE,
                x + 2,
                y + 5 + f.lineHeight,
                PAGE_SLOTS_WIDTH,
                inv.rows() * SLOT_SIZE
        );

        Rect scrollPanel = getScrollPanelInner();
        List<ItemStack> stacks = inv.stacks();
        for (int i = 0; i < stacks.size(); i++) {
            int slotX = (i % 9) * SLOT_SIZE + x + 3;
            int slotY = (i / 9) * SLOT_SIZE + y + 5 + f.lineHeight + 1;
            ItemStack stack = stacks.get(i);
            if (slots == null) {
                if (cfg.storageOverlayHighlightSearch && !searchText.isBlank()
                        && !stack.isEmpty() && matchesSearch(stack, searchText)) {
                    context.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF00B000);
                }
                context.item(stack, slotX, slotY);
                context.itemDecorations(f, stack, slotX, slotY);
                if (cfg.storageOverlayShowInactiveTooltips && !stack.isEmpty()
                        && mouseX >= slotX && mouseY >= slotY
                        && mouseX <= slotX + 16 && mouseY <= slotY + 16
                        && scrollPanel.contains(mouseX, mouseY)) {
                    try {
                        context.setTooltipForNextFrame(f, stack, mouseX, mouseY);
                    } catch (IllegalStateException ex) {
                        context.setComponentTooltipForNextFrame(f, List.of(
                                Component.literal(ChatFormatting.RED + "Error Getting Tooltip!"),
                                Component.literal(ChatFormatting.YELLOW + "Open page to fix")
                        ), mouseX, mouseY);
                    }
                }
            } else {
                Slot slot = slots.get(i);
                if (slot instanceof com.dttdrt.addons.features.storage.gui.CoordRememberingSlot crs) {
                    crs.dttdrt$setCoords(slotX - slotOffsetX, slotY - slotOffsetY);
                }
            }
        }
        return pageHeight + 6;
    }

    public Set<StoragePageSlot> getFilteredPages() {
        String searchValue = searchText != null ? searchText : "";
        StorageData data = StorageData.getInstance();
        Map<StoragePageSlot, StorageData.StorageInventory> snap = data.snapshotInventories();
        if (searchCache != null && searchCache.equals(searchValue)) {
            return filteredPagesCache;
        }
        Set<StoragePageSlot> result = new HashSet<>();
        for (Map.Entry<StoragePageSlot, StorageData.StorageInventory> entry : snap.entrySet()) {
            VirtualInventory vi = entry.getValue().inventory;
            if (vi == null) {
                result.add(entry.getKey());
                continue;
            }
            if (searchValue.isBlank()) {
                result.add(entry.getKey());
                continue;
            }
            boolean anyMatch = false;
            for (ItemStack s : vi.stacks()) {
                if (!s.isEmpty() && matchesSearch(s, searchValue)) {
                    anyMatch = true;
                    break;
                }
            }
            if (anyMatch) {
                result.add(entry.getKey());
            }
        }
        searchCache = searchValue;
        filteredPagesCache = result;
        return result;
    }

    public boolean matchesSearch(ItemStack itemStack, String search) {
        if (itemStack == null || itemStack.isEmpty()) return false;
        if (search == null || search.isBlank()) return true;
        String[] searchWords = search.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder searchable = new StringBuilder();
        searchable.append(MiningItemTable.stripFormatting(itemStack.getHoverName().getString()).toLowerCase(Locale.ROOT));
        ItemLore lore = itemStack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                searchable.append('\n')
                        .append(MiningItemTable.stripFormatting(line.getString()).toLowerCase(Locale.ROOT));
            }
        }
        String hay = searchable.toString();
        for (String word : searchWords) {
            if (!word.isEmpty() && !hay.contains(word)) {
                return false;
            }
        }
        return true;
    }

    public List<Rect> getBounds() {
        return List.of(
                new Rect(measurements.x, measurements.y, measurements.overviewWidth, measurements.overviewHeight),
                new Rect(measurements.playerX, measurements.playerY, PLAYER_WIDTH, PLAYER_HEIGHT),
                new Rect(measurements.controlX, measurements.controlY, CONTROL_WIDTH, CONTROL_HEIGHT)
        );
    }

    public void editPages() {
        isExiting = true;
        searchFocused = false;
        syncTextInputForHost(this, false);
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.schedule(() -> {
            Screen cur = mc.screen;
            if (cur instanceof AbstractContainerScreen<?> acs
                    && StorageBackingHandle.fromScreen(acs) instanceof StorageBackingHandle.Overview
                    && acs instanceof CustomGuiHost host) {
                host.dttdrt$clearCustomGuiAndReinit();
            } else {
                ClientPacketListener conn = mc.getConnection();
                if (conn != null) {
                    conn.sendCommand("storage");
                }
            }
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        coerceScroll((float) StorageOverlayManager.adjustScrollSpeed(verticalAmount));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return mouseClicked(click, doubled, null, this);
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled, StoragePageSlot activePage, Screen hostScreen) {
        double mouseX = click.x();
        double mouseY = click.y();
        boolean isLeft = click.button() == InputConstants.MOUSE_BUTTON_LEFT;

        searchFocused = false;

        if (getSearchBoxRect().contains(mouseX, mouseY) && isLeft) {
            searchFocused = true;
            syncTextInputForHost(hostScreen, true);
            return true;
        }
        if (getEditButtonRect().contains(mouseX, mouseY) && isLeft) {
            editPages();
            return true;
        }

        syncTextInputForHost(hostScreen, false);

        if (getScrollPanelInner().contains(mouseX, mouseY)) {
            StorageData data = StorageData.getInstance();
            final StoragePageSlot[] hitPage = new StoragePageSlot[1];
            layoutPages(data, (rect, page, inv) -> {
                if (hitPage[0] == null && rect.contains(mouseX, mouseY)) {
                    hitPage[0] = page;
                }
            });
            if (hitPage[0] != null && !hitPage[0].equals(activePage) && isLeft) {
                hitPage[0].navigateTo();
                return true;
            }
            return false;
        }

        Rect sbRect = getScrollBarRect();
        if (sbRect.contains(mouseX, mouseY) && isLeft) {
            float percentage = (float) ((mouseY - sbRect.minY()) / Math.max(1, sbRect.height()));
            scroll = Math.max(0f, Math.min(getMaxScroll(), getMaxScroll() * percentage));
            knobGrabbed = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (knobGrabbed && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Rect sbRect = getScrollBarRect();
            float percentage = (float) ((click.y() - sbRect.minY()) / Math.max(1, sbRect.height()));
            scroll = Math.max(0f, Math.min(getMaxScroll(), getMaxScroll() * percentage));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (knobGrabbed) {
            knobGrabbed = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (searchFocused) {
            int key = input.key();
            if (input.isEscape()) {
                if (!searchText.isEmpty()) {
                    setSearchText("");
                } else {
                    searchFocused = false;
                }
                return true;
            }
            if (input.isSelectAll() || (input.hasControlDown() && key == InputConstants.KEY_BACKSPACE)) {
                setSearchText("");
                return true;
            }
            if (input.isPaste()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.keyboardHandler != null) {
                    String clip = mc.keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        setSearchText(searchText + clip.replace("\n", "").replace("\r", ""));
                    }
                }
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                if (!searchText.isEmpty()) {
                    setSearchText(searchText.substring(0, searchText.length() - 1));
                }
                return true;
            }
            if (input.isConfirmation()) {
                searchFocused = false;
                return true;
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (searchFocused && input.isAllowedChatCharacter()) {
            String chars = input.codepointAsString();
            if (chars != null && !chars.isEmpty()) {
                setSearchText(searchText + chars);
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        if (searchFocused) {
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && this == mc.screen;
    }

    private void setSearchText(String text) {
        searchText = text != null ? text : "";
        searchCache = null;
        layoutPages(StorageData.getInstance(), null);
        coerceScroll(0f);
    }
}
