package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.lang.ModLang;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * SkyHanni-style interactive HUD Position & Scale Editor for the Crystal Hollows Map.
 * - Left-click & drag to reposition
 * - Mouse wheel up/down to zoom in/out
 * - Press 'R' or click Reset to restore default position & size
 * - Press ESC or click Done to save and return
 */
public class CrystalHollowsHudEditScreen extends Screen {
    private final Screen parent;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public CrystalHollowsHudEditScreen(Screen parent) {
        super(Component.literal(ModLang.tr("dttdrtaddons.editor.ch_map.title")));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.extractTransparentBackground(graphics);

        ModConfig cfg = ModConfig.getInstance();
        int hudSize = Math.round(624 * cfg.chMapScale);

        // Update drag position while dragging
        if (dragging) {
            updateDragPosition(mouseX, mouseY, hudSize, cfg);
        }

        // Render live map preview
        if (this.minecraft != null) {
            CrystalHollowsMapHud.renderMapAt(graphics, this.minecraft, cfg.chMapX, cfg.chMapY, cfg.chMapScale);
        }

        // SkyHanni-style bounding highlight & info overlay
        boolean hovered = isInsideMap(mouseX, mouseY, cfg.chMapX, cfg.chMapY, hudSize);
        int highlightColor = (dragging || hovered) ? 0x55FFFFFF : 0x22FFFFFF;
        graphics.fill(cfg.chMapX - 2, cfg.chMapY - 2, cfg.chMapX + hudSize + 2, cfg.chMapY + hudSize + 2, highlightColor);
        graphics.outline(cfg.chMapX - 2, cfg.chMapY - 2, hudSize + 4, hudSize + 4, (dragging || hovered) ? 0xFF3898EC : 0xFF8A92A0);

        // Top instruction banner
        String info = ModLang.tr("dttdrtaddons.editor.ch_map.info",
                cfg.chMapX, cfg.chMapY, cfg.chMapScale, hudSize);
        String hint = ModLang.tr("dttdrtaddons.editor.ch_map.hint");

        int bannerW = Math.max(this.font.width(info), this.font.width(hint)) + 24;
        int bannerX = (this.width - bannerW) / 2;
        int bannerY = 12;
        if (cfg.chMapY < 60 && cfg.chMapX < bannerX + bannerW && cfg.chMapX + hudSize > bannerX) {
            bannerY = this.height - 70; // Dodge map if user drags it into top center
        }

        graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + 34, 0xCC101216);
        graphics.outline(bannerX, bannerY, bannerW, 34, 0xFF2B313D);
        graphics.centeredText(this.font, info, this.width / 2, bannerY + 6, 0xFFE6E8EB);
        graphics.centeredText(this.font, hint, this.width / 2, bannerY + 19, 0xFF8A92A0);

        // Bottom Reset & Done buttons
        int btnY = this.height - 30;
        int resetX = this.width / 2 - 86;
        int doneX = this.width / 2 + 6;
        drawBtn(graphics, resetX, btnY, 80, 20, ModLang.tr("dttdrtaddons.common.reset"), mouseX, mouseY, false);
        drawBtn(graphics, doneX, btnY, 80, 20, ModLang.tr("dttdrtaddons.common.done"), mouseX, mouseY, true);
    }

    private void drawBtn(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my, boolean primary) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int bg = primary ? (hover ? 0xFF4CA8FC : 0xFF3898EC) : (hover ? 0xEE262B35 : 0xEE1C1F26);
        int border = primary ? 0xFF3898EC : 0xFF2B313D;
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);
        int textW = Math.max(1, this.font.width(label));
        float scale = Math.min(1.15f, Math.max(0.60f, Math.min((w - 10.0f) / textW, (h - 6.0f) / 9.0f)));
        g.pose().pushMatrix();
        g.pose().translate(x + w * 0.5f, y + h * 0.5f);
        g.pose().scale(scale, scale);
        g.centeredText(this.font, label, 0, -4, 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isInsideWindow) {
        int mx = (int) click.x();
        int my = (int) click.y();
        ModConfig cfg = ModConfig.getInstance();
        int hudSize = Math.round(624 * cfg.chMapScale);

        int btnY = this.height - 30;
        int resetX = this.width / 2 - 86;
        int doneX = this.width / 2 + 6;

        if (mx >= resetX && mx <= resetX + 80 && my >= btnY && my <= btnY + 20) {
            resetDefaults(cfg);
            return true;
        }
        if (mx >= doneX && mx <= doneX + 80 && my >= btnY && my <= btnY + 20) {
            closeAndSave();
            return true;
        }

        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && isInsideMap(mx, my, cfg.chMapX, cfg.chMapY, hudSize)) {
            this.dragging = true;
            this.dragOffsetX = mx - cfg.chMapX;
            this.dragOffsetY = my - cfg.chMapY;
            return true;
        }

        return super.mouseClicked(click, isInsideWindow);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.dragging && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ModConfig cfg = ModConfig.getInstance();
            int hudSize = Math.round(624 * cfg.chMapScale);
            updateDragPosition(click.x(), click.y(), hudSize, cfg);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (this.dragging) {
            this.dragging = false;
            ModConfig.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void removed() {
        if (this.dragging) {
            this.dragging = false;
            ModConfig.save();
        }
        super.removed();
    }

    private void updateDragPosition(double mouseX, double mouseY, int hudSize, ModConfig cfg) {
        int maxX = Math.max(4, this.width - hudSize - 4);
        int maxY = Math.max(4, this.height - hudSize - 4);
        cfg.chMapX = Math.max(4, Math.min(maxX, (int) Math.round(mouseX) - dragOffsetX));
        cfg.chMapY = Math.max(4, Math.min(maxY, (int) Math.round(mouseY) - dragOffsetY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ModConfig cfg = ModConfig.getInstance();
        float delta = verticalAmount > 0 ? 0.02f : (verticalAmount < 0 ? -0.02f : 0f);
        if (delta != 0f) {
            float newScale = Math.max(0.08f, Math.min(0.60f, cfg.chMapScale + delta));
            cfg.chMapScale = Math.round(newScale * 100f) / 100f;
            int hudSize = Math.round(624 * cfg.chMapScale);
            cfg.chMapX = Math.max(4, Math.min(Math.max(4, this.width - hudSize - 4), cfg.chMapX));
            cfg.chMapY = Math.max(4, Math.min(Math.max(4, this.height - hudSize - 4), cfg.chMapY));
            ModConfig.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == InputConstants.KEY_R) {
            resetDefaults(ModConfig.getInstance());
            return true;
        }
        if (input.isEscape()) {
            closeAndSave();
            return true;
        }
        return super.keyPressed(input);
    }

    private void resetDefaults(ModConfig cfg) {
        cfg.chMapX = 8;
        cfg.chMapY = 8;
        cfg.chMapScale = 0.20f;
        ModConfig.save();
    }

    private void closeAndSave() {
        ModConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private boolean isInsideMap(int mx, int my, int x, int y, int size) {
        return mx >= x - 2 && mx <= x + size + 2 && my >= y - 2 && my <= y + size + 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
