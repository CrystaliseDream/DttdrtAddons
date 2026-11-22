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
 * Interactive HUD Position & Scale Editor for the Mining Profit Tracker.
 * - Left-click & drag to reposition
 * - Mouse wheel up/down to resize (0.50x .. 2.00x)
 * - Press 'R' or click Reset to restore default position & scale
 * - Press ESC or click Done to save and return
 */
public class MiningProfitHudEditScreen extends Screen {
    public enum HudMode {
        MINING,
        SCATHA,
        NUCLEUS
    }

    private final Screen parent;
    private final HudMode hudMode;
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public MiningProfitHudEditScreen(Screen parent) {
        this(parent, HudMode.MINING);
    }

    public MiningProfitHudEditScreen(Screen parent, boolean scathaMode) {
        this(parent, scathaMode ? HudMode.SCATHA : HudMode.MINING);
    }

    public MiningProfitHudEditScreen(Screen parent, HudMode hudMode) {
        super(Component.literal(ModLang.tr(switch (hudMode) {
            case SCATHA -> "dttdrtaddons.editor.scatha_profit.title";
            case NUCLEUS -> "dttdrtaddons.editor.nucleus_profit.title";
            case MINING -> "dttdrtaddons.editor.mining_profit.title";
        })));
        this.parent = parent;
        this.hudMode = hudMode;
    }

    private int getHudX(ModConfig cfg) {
        return switch (hudMode) {
            case SCATHA -> cfg.scathaProfitHudX;
            case NUCLEUS -> cfg.nucleusProfitHudX;
            case MINING -> cfg.miningProfitHudX;
        };
    }

    private int getHudY(ModConfig cfg) {
        return switch (hudMode) {
            case SCATHA -> cfg.scathaProfitHudY;
            case NUCLEUS -> cfg.nucleusProfitHudY;
            case MINING -> cfg.miningProfitHudY;
        };
    }

    private float getHudScale(ModConfig cfg) {
        return switch (hudMode) {
            case SCATHA -> cfg.scathaProfitHudScale;
            case NUCLEUS -> cfg.nucleusProfitHudScale;
            case MINING -> cfg.miningProfitHudScale;
        };
    }

    private String getPriceMode(ModConfig cfg) {
        return switch (hudMode) {
            case SCATHA -> cfg.scathaProfitPriceMode;
            case NUCLEUS -> cfg.nucleusProfitPriceMode;
            case MINING -> cfg.miningProfitPriceMode;
        };
    }

    private int[] resolveBounds(ModConfig cfg) {
        float scale = getHudScale(cfg);
        switch (hudMode) {
            case SCATHA -> {
                ScathaProfitFeature f = ScathaProfitFeature.getInstance();
                if (f != null) return f.getHudBounds(this.minecraft, scale, true);
            }
            case NUCLEUS -> {
                NucleusProfitFeature f = NucleusProfitFeature.getInstance();
                if (f != null) return f.getHudBounds(this.minecraft, scale, true);
            }
            case MINING -> {
                MiningProfitFeature f = MiningProfitFeature.getInstance();
                if (f != null) return f.getHudBounds(this.minecraft, scale, true);
            }
        }
        return new int[]{Math.round(216 * scale), Math.round(98 * scale)};
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.extractTransparentBackground(graphics);

        ModConfig cfg = ModConfig.getInstance();
        int[] bounds = resolveBounds(cfg);
        int hudW = bounds[0];
        int hudH = bounds[1];

        if (dragging) {
            updateDragPosition(mouseX, mouseY, hudW, hudH, cfg);
        }

        int hudX = getHudX(cfg);
        int hudY = getHudY(cfg);
        float hudScale = getHudScale(cfg);
        if (this.minecraft != null) {
            switch (hudMode) {
                case SCATHA -> {
                    ScathaProfitFeature f = ScathaProfitFeature.getInstance();
                    if (f != null) f.renderHudAt(graphics, this.minecraft, hudX, hudY, hudScale, true);
                }
                case NUCLEUS -> {
                    NucleusProfitFeature f = NucleusProfitFeature.getInstance();
                    if (f != null) f.renderHudAt(graphics, this.minecraft, hudX, hudY, hudScale, true);
                }
                case MINING -> {
                    MiningProfitFeature f = MiningProfitFeature.getInstance();
                    if (f != null) f.renderHudAt(graphics, this.minecraft, hudX, hudY, hudScale, true);
                }
            }
        }

        boolean hovered = isInside(mouseX, mouseY, hudX, hudY, hudW, hudH);
        int highlightColor = (dragging || hovered) ? 0x44FFFFFF : 0x18FFFFFF;
        graphics.fill(hudX - 2, hudY - 2, hudX + hudW + 2, hudY + hudH + 2, highlightColor);
        graphics.outline(hudX - 2, hudY - 2, hudW + 4, hudH + 4,
                (dragging || hovered) ? 0xFF3898EC : 0xFF8A92A0);

        String info = ModLang.tr(switch (hudMode) {
            case SCATHA -> "dttdrtaddons.editor.scatha_profit.info";
            case NUCLEUS -> "dttdrtaddons.editor.nucleus_profit.info";
            case MINING -> "dttdrtaddons.editor.mining_profit.info";
        }, hudX, hudY, hudScale, MiningProfitFeature.getModeDisplayName(getPriceMode(cfg)));
        String hint = ModLang.tr("dttdrtaddons.editor.mining_profit.hint");

        int bannerW = Math.max(this.font.width(info), this.font.width(hint)) + 24;
        int bannerX = (this.width - bannerW) / 2;
        int bannerY = 12;
        if (hudY < 60 && hudX < bannerX + bannerW && hudX + hudW > bannerX) {
            bannerY = this.height - 70;
        }

        graphics.fill(bannerX, bannerY, bannerX + bannerW, bannerY + 34, 0xCC101216);
        graphics.outline(bannerX, bannerY, bannerW, 34, 0xFF2B313D);
        graphics.centeredText(this.font, info, this.width / 2, bannerY + 6, 0xFFE6E8EB);
        graphics.centeredText(this.font, hint, this.width / 2, bannerY + 19, 0xFF8A92A0);

        int btnY = this.height - 30;
        int modeX = this.width / 2 - 140;
        int resetX = this.width / 2 - 36;
        int doneX = this.width / 2 + 54;
        drawBtn(graphics, modeX, btnY, 96, 20, ModLang.tr("dttdrtaddons.editor.mining_profit.cycle_mode"), mouseX, mouseY, false);
        drawBtn(graphics, resetX, btnY, 82, 20, ModLang.tr("dttdrtaddons.common.reset"), mouseX, mouseY, false);
        drawBtn(graphics, doneX, btnY, 82, 20, ModLang.tr("dttdrtaddons.common.done"), mouseX, mouseY, true);
    }

    private void drawBtn(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my, boolean primary) {
        boolean hover = isInside(mx, my, x, y, w, h);
        int bg = primary ? (hover ? 0xFF4CA8FC : 0xFF3898EC) : (hover ? 0xEE262B35 : 0xEE1C1F26);
        int border = primary ? 0xFF3898EC : 0xFF2B313D;
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);
        int textW = Math.max(1, this.font.width(label));
        float scale = Math.min(1.15f, Math.max(0.60f, Math.min((w - 10.0f) / textW, (h - 6.0f) / 9.0f)));
        g.pose().pushMatrix();
        g.pose().translate(x + w * 0.5f, y + h * 0.5f);
        g.pose().scale(scale, scale);
        g.centeredText(this.font, label, 0, -4, primary ? 0xFFFFFFFF : 0xFFE6E8EB);
        g.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isInsideWindow) {
        double mx = click.x();
        double my = click.y();

        int btnY = this.height - 30;
        int modeX = this.width / 2 - 140;
        int resetX = this.width / 2 - 36;
        int doneX = this.width / 2 + 54;

        if (isInside(mx, my, modeX, btnY, 96, 20)) {
            switch (hudMode) {
                case SCATHA -> ScathaProfitFeature.cyclePriceMode();
                case NUCLEUS -> NucleusProfitFeature.cyclePriceMode();
                case MINING -> MiningProfitFeature.cyclePriceMode();
            }
            return true;
        }
        if (isInside(mx, my, resetX, btnY, 82, 20)) {
            resetDefaults();
            return true;
        }
        if (isInside(mx, my, doneX, btnY, 82, 20)) {
            onClose();
            return true;
        }

        ModConfig cfg = ModConfig.getInstance();
        int[] bounds = resolveBounds(cfg);
        int hudX = getHudX(cfg);
        int hudY = getHudY(cfg);
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT
                && isInside(mx, my, hudX - 4, hudY - 4, bounds[0] + 8, bounds[1] + 8)) {
            this.dragging = true;
            this.dragOffsetX = (int) Math.round(mx - hudX);
            this.dragOffsetY = (int) Math.round(my - hudY);
            return true;
        }
        return super.mouseClicked(click, isInsideWindow);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.dragging && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ModConfig cfg = ModConfig.getInstance();
            int[] bounds = resolveBounds(cfg);
            updateDragPosition(click.x(), click.y(), bounds[0], bounds[1], cfg);
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

    private void updateDragPosition(double mouseX, double mouseY, int hudW, int hudH, ModConfig cfg) {
        int maxX = Math.max(4, this.width - hudW - 4);
        int maxY = Math.max(4, this.height - hudH - 4);
        int nx = Math.max(4, Math.min(maxX, (int) Math.round(mouseX) - dragOffsetX));
        int ny = Math.max(4, Math.min(maxY, (int) Math.round(mouseY) - dragOffsetY));
        switch (hudMode) {
            case SCATHA -> {
                cfg.scathaProfitHudX = nx;
                cfg.scathaProfitHudY = ny;
            }
            case NUCLEUS -> {
                cfg.nucleusProfitHudX = nx;
                cfg.nucleusProfitHudY = ny;
            }
            case MINING -> {
                cfg.miningProfitHudX = nx;
                cfg.miningProfitHudY = ny;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ModConfig cfg = ModConfig.getInstance();
        float delta = (float) (verticalAmount * 0.05);
        float next = Math.max(0.50f, Math.min(2.00f,
                Math.round((getHudScale(cfg) + delta) * 100.0f) / 100.0f));
        switch (hudMode) {
            case SCATHA -> cfg.scathaProfitHudScale = next;
            case NUCLEUS -> cfg.nucleusProfitHudScale = next;
            case MINING -> cfg.miningProfitHudScale = next;
        }
        ModConfig.save();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == InputConstants.KEY_R) {
            resetDefaults();
            return true;
        }
        return super.keyPressed(input);
    }

    private void resetDefaults() {
        ModConfig cfg = ModConfig.getInstance();
        switch (hudMode) {
            case SCATHA -> {
                cfg.scathaProfitHudX = 12;
                cfg.scathaProfitHudY = 260;
                cfg.scathaProfitHudScale = 1.0f;
            }
            case NUCLEUS -> {
                cfg.nucleusProfitHudX = 12;
                cfg.nucleusProfitHudY = 380;
                cfg.nucleusProfitHudScale = 1.0f;
            }
            case MINING -> {
                cfg.miningProfitHudX = 12;
                cfg.miningProfitHudY = 140;
                cfg.miningProfitHudScale = 1.0f;
            }
        }
        ModConfig.save();
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void onClose() {
        ModConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
