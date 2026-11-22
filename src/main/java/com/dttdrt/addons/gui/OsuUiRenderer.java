package com.dttdrt.addons.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Consolidated High-DPI 512x512 Linear UI Atlas renderer.
 * Supports region-level scoped batching via GuiBatchHelper and sub-pixel float positioning
 * so cards, switches, pills, icons, fills, and chart polylines merge seamlessly into minimal Draw Calls.
 */
public class OsuUiRenderer {

    private static final Identifier UI_ATLAS_ID = Identifier.fromNamespaceAndPath("dttdrtaddons", "osu_ui_atlas_hd");
    private static final int ATLAS_SIZE = 512;

    private static final int BOX_TEX = 128;
    private static final int BOX_CORNER = 20;

    private static final int WHITE_X = 2, WHITE_Y = 2, WHITE_W = 12, WHITE_H = 12;
    private static final float WHITE_U = (WHITE_X + WHITE_W * 0.5f) / ATLAS_SIZE;
    private static final float WHITE_V = (WHITE_Y + WHITE_H * 0.5f) / ATLAS_SIZE;

    private static final int ROUNDED_X = 20, ROUNDED_Y = 4;
    private static final int BORDER_X = 156, BORDER_Y = 4;
    private static final int CIRCLE_X = 292, CIRCLE_Y = 4, CIRCLE_SZ = 96;
    private static final int SWITCH_RING_X = 4, SWITCH_RING_Y = 140, SWITCH_W = 224, SWITCH_H = 64;
    private static final int SWITCH_FILL_X = 236, SWITCH_FILL_Y = 140;
    private static final int SEARCH_X = 4, SEARCH_Y = 212, SEARCH_SZ = 64;
    private static final int ICON_MARKET_X = 76, ICON_Y = 212, ICON_SZ = 80;
    private static final int ICON_MINING_X = 164;
    private static final int ICON_CUSTOM_X = 252;

    private static boolean initialized = false;
    private static DynamicTexture uiAtlasTexture;
    private static TextureSetup uiTextureSetup;

    private static void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        try {
            NativeImage atlas = new NativeImage(NativeImage.Format.RGBA, ATLAS_SIZE, ATLAS_SIZE, true);

            for (int y = WHITE_Y; y < WHITE_Y + WHITE_H; y++) {
                for (int x = WHITE_X; x < WHITE_X + WHITE_W; x++) {
                    atlas.setPixel(x, y, 0xFFFFFFFF);
                }
            }

            BufferedImage rImg = new BufferedImage(BOX_TEX, BOX_TEX, BufferedImage.TYPE_INT_ARGB);
            Graphics2D rg = aa(rImg);
            rg.setColor(Color.WHITE);
            rg.fill(new RoundRectangle2D.Float(1, 1, BOX_TEX - 2, BOX_TEX - 2, BOX_CORNER * 2f, BOX_CORNER * 2f));
            rg.dispose();
            blitToAtlas(atlas, rImg, ROUNDED_X, ROUNDED_Y);

            BufferedImage bImg = new BufferedImage(BOX_TEX, BOX_TEX, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = aa(bImg);
            bg.setColor(Color.WHITE);
            bg.setStroke(new BasicStroke(9.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            bg.draw(new RoundRectangle2D.Float(5.5f, 5.5f, BOX_TEX - 11f, BOX_TEX - 11f, BOX_CORNER * 1.8f, BOX_CORNER * 1.8f));
            bg.dispose();
            blitToAtlas(atlas, bImg, BORDER_X, BORDER_Y);

            BufferedImage cImg = new BufferedImage(CIRCLE_SZ, CIRCLE_SZ, BufferedImage.TYPE_INT_ARGB);
            Graphics2D cg = aa(cImg);
            cg.setColor(Color.WHITE);
            cg.fill(new Ellipse2D.Float(1, 1, CIRCLE_SZ - 2, CIRCLE_SZ - 2));
            cg.dispose();
            blitToAtlas(atlas, cImg, CIRCLE_X, CIRCLE_Y);

            BufferedImage srImg = new BufferedImage(SWITCH_W, SWITCH_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D srg = aa(srImg);
            srg.setColor(Color.WHITE);
            srg.setStroke(new BasicStroke(12.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            srg.draw(new RoundRectangle2D.Float(7.0f, 7.0f, SWITCH_W - 14.0f, SWITCH_H - 14.0f, 50f, 50f));
            srg.dispose();
            blitToAtlas(atlas, srImg, SWITCH_RING_X, SWITCH_RING_Y);

            BufferedImage sfImg = new BufferedImage(SWITCH_W, SWITCH_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sfg = aa(sfImg);
            sfg.setColor(Color.WHITE);
            sfg.fill(new RoundRectangle2D.Float(1.0f, 1.0f, SWITCH_W - 2.0f, SWITCH_H - 2.0f, 62f, 62f));
            sfg.dispose();
            blitToAtlas(atlas, sfImg, SWITCH_FILL_X, SWITCH_FILL_Y);

            BufferedImage qImg = new BufferedImage(SEARCH_SZ, SEARCH_SZ, BufferedImage.TYPE_INT_ARGB);
            Graphics2D qg = aa(qImg);
            qg.setColor(Color.WHITE);
            qg.setStroke(new BasicStroke(6.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            qg.draw(new Ellipse2D.Float(10, 10, 30, 30));
            qg.draw(new Line2D.Float(36, 36, 52, 52));
            qg.dispose();
            blitToAtlas(atlas, qImg, SEARCH_X, SEARCH_Y);

            blitToAtlas(atlas, createMarketIcon(), ICON_MARKET_X, ICON_Y);
            blitToAtlas(atlas, createMiningIcon(), ICON_MINING_X, ICON_Y);
            blitToAtlas(atlas, createCustomIcon(), ICON_CUSTOM_X, ICON_Y);

            GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            uiAtlasTexture = new DynamicTexture(() -> "dttdrt_osu_ui_atlas", atlas) {
                {
                    this.sampler = linearSampler;
                }
            };
            Minecraft.getInstance().getTextureManager().register(UI_ATLAS_ID, uiAtlasTexture);
            uiTextureSetup = TextureSetup.singleTexture(uiAtlasTexture.getTextureView(), uiAtlasTexture.getSampler());
        } catch (Throwable ignored) {}
    }

    private static void blitToAtlas(NativeImage atlas, BufferedImage src, int dstX, int dstY) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int a = (pixels[row + x] >>> 24) & 0xFF;
                atlas.setPixel(dstX + x, dstY + y, a == 0 ? 0 : (a << 24) | 0x00FFFFFF);
            }
        }
    }

    private static BufferedImage createMarketIcon() {
        BufferedImage img = new BufferedImage(ICON_SZ, ICON_SZ, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Float(12, 18, 56, 44, 10, 10));
        g.draw(new Line2D.Float(22, 48, 34, 36));
        g.draw(new Line2D.Float(34, 36, 44, 44));
        g.draw(new Line2D.Float(44, 44, 58, 28));
        g.dispose();
        return img;
    }

    private static BufferedImage createMiningIcon() {
        BufferedImage img = new BufferedImage(ICON_SZ, ICON_SZ, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        GeneralPath gem = new GeneralPath();
        gem.moveTo(26, 16);
        gem.lineTo(54, 16);
        gem.lineTo(66, 34);
        gem.lineTo(40, 64);
        gem.lineTo(14, 34);
        gem.closePath();
        g.draw(gem);
        g.draw(new Line2D.Float(14, 34, 66, 34));
        g.dispose();
        return img;
    }

    private static BufferedImage createCustomIcon() {
        BufferedImage img = new BufferedImage(ICON_SZ, ICON_SZ, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(24, 24, 32, 32));
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4.0;
            float x1 = (float) (40 + Math.cos(ang) * 19);
            float y1 = (float) (40 + Math.sin(ang) * 19);
            float x2 = (float) (40 + Math.cos(ang) * 26);
            float y2 = (float) (40 + Math.sin(ang) * 26);
            g.draw(new Line2D.Float(x1, y1, x2, y2));
        }
        g.dispose();
        return img;
    }

    private static Graphics2D aa(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    public static void fillRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
        ensureInitialized();
        if (uiTextureSetup != null) {
            if (GuiBatchHelper.isScopeActive()) {
                GuiBatchHelper.getUiCollector(g, uiTextureSetup)
                        .addRect(x, y, x + w, y + h, WHITE_U, WHITE_V, WHITE_U, WHITE_V, argb);
                return;
            }
            GuiBatchHelper.QuadCollector qc = GuiBatchHelper.getScratchCollector();
            qc.addRect(x, y, x + w, y + h, WHITE_U, WHITE_V, WHITE_U, WHITE_V, argb);
            if (GuiBatchHelper.submitBatch(g, RenderPipelines.GUI_TEXTURED, uiTextureSetup, qc)) {
                return;
            }
        }
        g.fill(x, y, x + w, y + h, argb);
    }

    public static void drawLineSegment(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2,
                                       float thickness, int argb) {
        if (((argb >>> 24) & 0xFF) == 0) return;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.25f) return;
        float halfT = thickness * 0.5f;
        float nx = (-dy / len) * halfT;
        float ny = (dx / len) * halfT;

        ensureInitialized();
        if (uiTextureSetup != null) {
            if (GuiBatchHelper.isScopeActive()) {
                GuiBatchHelper.getUiCollector(g, uiTextureSetup).addFreeQuad(
                        x1 - nx, y1 - ny,
                        x1 + nx, y1 + ny,
                        x2 + nx, y2 + ny,
                        x2 - nx, y2 - ny,
                        WHITE_U, WHITE_V, WHITE_U, WHITE_V, argb
                );
                return;
            }
            GuiBatchHelper.QuadCollector qc = GuiBatchHelper.getScratchCollector();
            qc.addFreeQuad(
                    x1 - nx, y1 - ny,
                    x1 + nx, y1 + ny,
                    x2 + nx, y2 + ny,
                    x2 - nx, y2 - ny,
                    WHITE_U, WHITE_V, WHITE_U, WHITE_V, argb
            );
            if (GuiBatchHelper.submitBatch(g, RenderPipelines.GUI_TEXTURED, uiTextureSetup, qc)) {
                return;
            }
        }
        float angle = (float) Math.atan2(dy, dx);
        g.pose().pushMatrix();
        g.pose().translate(x1, y1);
        g.pose().rotate(angle);
        g.fill(0, 0, (int) Math.ceil(len), Math.max(1, Math.round(thickness)), argb);
        g.pose().popMatrix();
    }

    public static void drawRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int argb) {
        drawNineSlice(g, ROUNDED_X, ROUNDED_Y, x, y, w, h, radius, argb, true);
    }

    public static void drawRoundedRectF(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int argb) {
        drawNineSlice(g, ROUNDED_X, ROUNDED_Y, x, y, w, h, radius, argb, true);
    }

    public static void drawRoundedBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int argb) {
        drawNineSlice(g, BORDER_X, BORDER_Y, x, y, w, h, radius, argb, false);
    }

    private static void drawNineSlice(GuiGraphicsExtractor g, int texX, int texY, float x, float y, float w, float h,
                                      float radius, int argb, boolean fillCenter) {
        if (w <= 0f || h <= 0f || ((argb >>> 24) & 0xFF) == 0) return;
        ensureInitialized();

        float r = Math.max(1f, Math.min(radius, Math.min(w, h) * 0.5f));
        float midW = Math.max(0f, w - r * 2f);
        float midH = Math.max(0f, h - r * 2f);
        int c = BOX_CORNER;
        int centerTex = BOX_TEX - c * 2;

        float uL0 = texX / (float) ATLAS_SIZE;
        float uL1 = (texX + c) / (float) ATLAS_SIZE;
        float uR0 = (texX + BOX_TEX - c) / (float) ATLAS_SIZE;
        float uR1 = (texX + BOX_TEX) / (float) ATLAS_SIZE;

        float vT0 = texY / (float) ATLAS_SIZE;
        float vT1 = (texY + c) / (float) ATLAS_SIZE;
        float vB0 = (texY + BOX_TEX - c) / (float) ATLAS_SIZE;
        float vB1 = (texY + BOX_TEX) / (float) ATLAS_SIZE;

        if (uiTextureSetup != null) {
            boolean scoped = GuiBatchHelper.isScopeActive();
            GuiBatchHelper.QuadCollector qc = scoped
                    ? GuiBatchHelper.getUiCollector(g, uiTextureSetup)
                    : GuiBatchHelper.getScratchCollector();

            qc.addRect(x, y, x + r, y + r, uL0, vT0, uL1, vT1, argb);
            qc.addRect(x + w - r, y, x + w, y + r, uR0, vT0, uR1, vT1, argb);
            qc.addRect(x, y + h - r, x + r, y + h, uL0, vB0, uL1, vB1, argb);
            qc.addRect(x + w - r, y + h - r, x + w, y + h, uR0, vB0, uR1, vB1, argb);
            if (midW > 0f) {
                qc.addRect(x + r, y, x + w - r, y + r, uL1, vT0, uR0, vT1, argb);
                qc.addRect(x + r, y + h - r, x + w - r, y + h, uL1, vB0, uR0, vB1, argb);
            }
            if (midH > 0f) {
                qc.addRect(x, y + r, x + r, y + h - r, uL0, vT1, uL1, vB0, argb);
                qc.addRect(x + w - r, y + r, x + w, y + h - r, uR0, vT1, uR1, vB0, argb);
            }
            if (midW > 0f && midH > 0f) {
                if (fillCenter) {
                    qc.addRect(x + r, y + r, x + r + midW, y + r + midH, WHITE_U, WHITE_V, WHITE_U, WHITE_V, argb);
                } else {
                    qc.addRect(x + r, y + r, x + r + midW, y + r + midH, uL1, vT1, uR0, vB0, argb);
                }
            }
            if (scoped || GuiBatchHelper.submitBatch(g, RenderPipelines.GUI_TEXTURED, uiTextureSetup, qc)) {
                return;
            }
        }

        int ix = Math.round(x), iy = Math.round(y), iw = Math.max(1, Math.round(w)), ih = Math.max(1, Math.round(h));
        int ir = Math.max(1, Math.min(Math.round(r), Math.min(iw, ih) / 2));
        int imw = Math.max(0, iw - ir * 2), imh = Math.max(0, ih - ir * 2);

        g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID, ix, iy, (float) texX, (float) texY, ir, ir, c, c, ATLAS_SIZE, ATLAS_SIZE, argb);
        g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID, ix + iw - ir, iy, (float) (texX + BOX_TEX - c), (float) texY, ir, ir, c, c, ATLAS_SIZE, ATLAS_SIZE, argb);
        g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID, ix, iy + ih - ir, (float) texX, (float) (texY + BOX_TEX - c), ir, ir, c, c, ATLAS_SIZE, ATLAS_SIZE, argb);
        g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID, ix + iw - ir, iy + ih - ir, (float) (texX + BOX_TEX - c), (float) (texY + BOX_TEX - c), ir, ir, c, c, ATLAS_SIZE, ATLAS_SIZE, argb);

        if (imw > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID, ix + ir, iy, (float) (texX + c), (float) texY, imw, ir, centerTex, c, ATLAS_SIZE, ATLAS_SIZE, argb);
            g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID, ix + ir, iy + ih - ir, (float) (texX + c), (float) (texY + BOX_TEX - c), imw, ir, centerTex, c, ATLAS_SIZE, ATLAS_SIZE, argb);
        }
        if (imh > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID, ix, iy + ir, (float) texX, (float) (texY + c), ir, imh, c, centerTex, ATLAS_SIZE, ATLAS_SIZE, argb);
            g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID, ix + iw - ir, iy + ir, (float) (texX + BOX_TEX - c), (float) (texY + c), ir, imh, c, centerTex, ATLAS_SIZE, ATLAS_SIZE, argb);
        }
        if (imw > 0 && imh > 0) {
            if (fillCenter) {
                g.fill(ix + ir, iy + ir, ix + ir + imw, iy + ir + imh, argb);
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID, ix + ir, iy + ir, (float) (texX + c), (float) (texY + c), imw, imh, centerTex, centerTex, ATLAS_SIZE, ATLAS_SIZE, argb);
            }
        }
    }

    public static void drawPill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
        drawRoundedRect(g, x, y, w, h, Math.min(w, h) / 2, argb);
    }

    public static void drawPillF(GuiGraphicsExtractor g, float x, float y, float w, float h, int argb) {
        drawRoundedRectF(g, x, y, w, h, Math.min(w, h) * 0.5f, argb);
    }

    public static void drawSolidRectF(GuiGraphicsExtractor g, float x, float y, float w, float h, int argb) {
        if (w <= 0f || h <= 0f || ((argb >>> 24) & 0xFF) == 0) return;
        ensureInitialized();
        if (uiTextureSetup != null) {
            boolean scoped = GuiBatchHelper.isScopeActive();
            GuiBatchHelper.QuadCollector qc = scoped
                    ? GuiBatchHelper.getUiCollector(g, uiTextureSetup)
                    : GuiBatchHelper.getScratchCollector();
            qc.addRect(x, y, x + w, y + h, WHITE_U, WHITE_V, WHITE_U, WHITE_V, argb);
            if (scoped || GuiBatchHelper.submitBatch(g, RenderPipelines.GUI_TEXTURED, uiTextureSetup, qc)) {
                return;
            }
        }
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), argb);
    }

    public static void drawCircle(GuiGraphicsExtractor g, int x, int y, int diameter, int argb) {
        if (diameter <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
        drawAtlasSprite(g, x, y, diameter, diameter, CIRCLE_X, CIRCLE_Y, CIRCLE_SZ, CIRCLE_SZ, argb);
    }

    public static void drawOsuSwitch(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                     int borderColor, int fillColor, float fillAlpha) {
        drawOsuSwitchF(g, x, y, w, h, borderColor, fillColor, fillAlpha);
    }

    public static void drawOsuSwitchF(GuiGraphicsExtractor g, float x, float y, float w, float h,
                                      int borderColor, int fillColor, float fillAlpha) {
        if (w <= 0f || h <= 0f) return;
        ensureInitialized();

        int tintedFill = 0;
        if (fillAlpha > 0.01f) {
            int fa = Math.min(255, Math.max(0, Math.round(((fillColor >>> 24) & 0xFF) * fillAlpha)));
            tintedFill = (fa << 24) | (fillColor & 0x00FFFFFF);
        }

        if (uiTextureSetup != null) {
            boolean scoped = GuiBatchHelper.isScopeActive();
            GuiBatchHelper.QuadCollector qc = scoped
                    ? GuiBatchHelper.getUiCollector(g, uiTextureSetup)
                    : GuiBatchHelper.getScratchCollector();

            if (((tintedFill >>> 24) & 0xFF) > 0) {
                qc.addRect(x, y, x + w, y + h,
                        SWITCH_FILL_X / (float) ATLAS_SIZE, SWITCH_FILL_Y / (float) ATLAS_SIZE,
                        (SWITCH_FILL_X + SWITCH_W) / (float) ATLAS_SIZE, (SWITCH_FILL_Y + SWITCH_H) / (float) ATLAS_SIZE,
                        tintedFill);
            }
            qc.addRect(x, y, x + w, y + h,
                    SWITCH_RING_X / (float) ATLAS_SIZE, SWITCH_RING_Y / (float) ATLAS_SIZE,
                    (SWITCH_RING_X + SWITCH_W) / (float) ATLAS_SIZE, (SWITCH_RING_Y + SWITCH_H) / (float) ATLAS_SIZE,
                    borderColor);
            if (scoped || GuiBatchHelper.submitBatch(g, RenderPipelines.GUI_TEXTURED, uiTextureSetup, qc)) {
                return;
            }
        }

        int ix = Math.round(x), iy = Math.round(y), iw = Math.round(w), ih = Math.round(h);
        if (((tintedFill >>> 24) & 0xFF) > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID,
                    ix, iy, (float) SWITCH_FILL_X, (float) SWITCH_FILL_Y, iw, ih, SWITCH_W, SWITCH_H, ATLAS_SIZE, ATLAS_SIZE, tintedFill);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID,
                ix, iy, (float) SWITCH_RING_X, (float) SWITCH_RING_Y, iw, ih, SWITCH_W, SWITCH_H, ATLAS_SIZE, ATLAS_SIZE, borderColor);
    }

    public static void drawSearchIcon(GuiGraphicsExtractor g, int x, int y, int size, int argb) {
        drawAtlasSprite(g, x, y, size, size, SEARCH_X, SEARCH_Y, SEARCH_SZ, SEARCH_SZ, argb);
    }

    public static void drawSidebarIcon(GuiGraphicsExtractor g, String section, int x, int y, int size, int argb) {
        if ("Combat".equals(section)) {
            int cx = x + size / 2;
            int cy = y + size / 2;
            int r = Math.max(6, size / 2 - 2);
            drawRoundedBorder(g, cx - r, cy - r, r * 2, r * 2, r, argb);
            drawRoundedRect(g, cx - 1, cy - r - 2, 2, 5, 1, argb);
            drawRoundedRect(g, cx - 1, cy + r - 3, 2, 5, 1, argb);
            drawRoundedRect(g, cx - r - 2, cy - 1, 5, 2, 1, argb);
            drawRoundedRect(g, cx + r - 3, cy - 1, 5, 2, 1, argb);
            drawCircle(g, cx - 2, cy - 2, 4, argb);
            return;
        }
        if ("Debug".equals(section)) {
            int pad = 2;
            int bx = x + pad;
            int by = y + pad;
            int bw = Math.max(8, size - pad * 2);
            int bh = Math.max(8, size - pad * 2);
            drawRoundedBorder(g, bx, by, bw, bh, 4, argb);
            drawRoundedRect(g, bx + 4, by + 5, 4, 2, 1, argb);
            drawRoundedRect(g, bx + 6, by + 7, 2, 2, 1, argb);
            drawRoundedRect(g, bx + 4, by + 9, 4, 2, 1, argb);
            drawRoundedRect(g, bx + 9, by + 11, 5, 2, 1, argb);
            return;
        }
        int sx = switch (section) {
            case "Market" -> ICON_MARKET_X;
            case "Mining" -> ICON_MINING_X;
            default -> ICON_CUSTOM_X;
        };
        drawAtlasSprite(g, x, y, size, size, sx, ICON_Y, ICON_SZ, ICON_SZ, argb);
    }

    private static void drawAtlasSprite(GuiGraphicsExtractor g, float x, float y, float w, float h,
                                        int texX, int texY, int texW, int texH, int argb) {
        if (w <= 0f || h <= 0f || ((argb >>> 24) & 0xFF) == 0) return;
        ensureInitialized();
        if (uiTextureSetup != null) {
            if (GuiBatchHelper.isScopeActive()) {
                GuiBatchHelper.getUiCollector(g, uiTextureSetup).addRect(x, y, x + w, y + h,
                        texX / (float) ATLAS_SIZE, texY / (float) ATLAS_SIZE,
                        (texX + texW) / (float) ATLAS_SIZE, (texY + texH) / (float) ATLAS_SIZE,
                        argb);
                return;
            }
            GuiBatchHelper.QuadCollector qc = GuiBatchHelper.getScratchCollector();
            qc.addRect(x, y, x + w, y + h,
                    texX / (float) ATLAS_SIZE, texY / (float) ATLAS_SIZE,
                    (texX + texW) / (float) ATLAS_SIZE, (texY + texH) / (float) ATLAS_SIZE,
                    argb);
            if (GuiBatchHelper.submitBatch(g, RenderPipelines.GUI_TEXTURED, uiTextureSetup, qc)) {
                return;
            }
        }
        g.blit(RenderPipelines.GUI_TEXTURED, UI_ATLAS_ID,
                Math.round(x), Math.round(y), (float) texX, (float) texY, Math.round(w), Math.round(h), texW, texH, ATLAS_SIZE, ATLAS_SIZE, argb);
    }
}
