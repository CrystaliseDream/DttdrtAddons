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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * High-DPI Single-Character Glyph Atlas font renderer (2048x2048 DynamicTexture, FilterMode.LINEAR).
 * Pre-warms ASCII and lazily caches Unicode/CJK glyphs at 3 baked resolution tiers (24px, 36px, 60px)
 * so text renders smoothly at arbitrary UI scales with zero per-frame texture creation and minimal Draw Calls.
 */
public class TorusFontRenderer {

    public enum Weight {
        REGULAR,
        SEMIBOLD,
        BOLD,
        ALTERNATE_REGULAR
    }

    private static final Identifier FONT_ATLAS_ID =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "osu_font_atlas_hd");
    private static final int ATLAS_SIZE = 2048;
    private static final int GLYPH_PAD = 3;
    private static final float VISUAL_SCALE_CALIBRATION = 0.96f;

    private static final float[] TIER_BAKE_PX = new float[]{24.0f, 36.0f, 60.0f};

    private record GlyphEntry(
            float u0, float v0, float u1, float v1,
            float offX, float offY,
            float bmpW, float bmpH,
            float advance
    ) {}

    private static final GlyphEntry EMPTY_SPACE = new GlyphEntry(0, 0, 0, 0, 0, 0, 0, 0, 0);

    private static boolean initialized = false;
    private static NativeImage atlasImage;
    private static DynamicTexture atlasTexture;
    private static TextureSetup atlasTextureSetup;
    private static boolean dirtyAtlas = false;

    private static int cursorX = 2;
    private static int cursorY = 2;
    private static int rowHeight = 0;

    // Keyed by (weight.ordinal() << 40) | (tierIdx << 32) | codepoint
    private static final Map<Long, GlyphEntry> GLYPH_CACHE = new HashMap<>(512);
    private record MeasureKey(int weightOrd, int sizeTenths, String text) {}
    private record TrimKey(int weightOrd, int maxWidthTenths, int sizeTenths, String text) {}
    private static final Map<MeasureKey, Float> MEASURE_CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MeasureKey, Float> eldest) {
            return size() > 1024;
        }
    };
    private static final Map<TrimKey, String> TRIM_CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TrimKey, String> eldest) {
            return size() > 1024;
        }
    };
    private static final Font[][] PRIMARY_FONTS = new Font[Weight.values().length][TIER_BAKE_PX.length];
    private static final Font[][] CJK_FONTS = new Font[Weight.values().length][TIER_BAKE_PX.length];
    private static final float[][] CAP_HEIGHTS = new float[Weight.values().length][TIER_BAKE_PX.length];

    private static Graphics2D scratchG2d;
    private static FontRenderContext sharedFrc;

    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        try {
            Set<String> available = new HashSet<>();
            try {
                for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames(Locale.ROOT)) {
                    available.add(name);
                }
            } catch (Throwable ignored) {}

            String regFamily = pickFirst(available, "Segoe UI", "Inter", "PingFang SC", "Microsoft YaHei UI", Font.SANS_SERIF);
            String semiFamily = pickFirst(available, "Segoe UI Semibold", "Inter SemiBold", "Microsoft YaHei UI", regFamily);
            String altFamily = pickFirst(available, "Segoe UI Light", "Segoe UI", regFamily);
            String cjkFamily = pickFirst(available, "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", Font.SANS_SERIF);

            BufferedImage dummy = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            scratchG2d = dummy.createGraphics();
            applyAaHints(scratchG2d);
            sharedFrc = scratchG2d.getFontRenderContext();

            for (Weight w : Weight.values()) {
                int wi = w.ordinal();
                for (int t = 0; t < TIER_BAKE_PX.length; t++) {
                    float px = TIER_BAKE_PX[t];
                    Font pf = switch (w) {
                        case SEMIBOLD -> semiFamily.equals(regFamily)
                                ? new Font(regFamily, Font.BOLD, Math.round(px)).deriveFont(px)
                                : new Font(semiFamily, Font.PLAIN, Math.round(px)).deriveFont(px);
                        case BOLD -> new Font(regFamily, Font.BOLD, Math.round(px)).deriveFont(px);
                        case ALTERNATE_REGULAR -> new Font(altFamily, Font.PLAIN, Math.round(px)).deriveFont(px);
                        default -> new Font(regFamily, Font.PLAIN, Math.round(px)).deriveFont(px);
                    };
                    Font cf = new Font(cjkFamily, (w == Weight.BOLD || w == Weight.SEMIBOLD) ? Font.BOLD : Font.PLAIN,
                            Math.round(px)).deriveFont(px);

                    PRIMARY_FONTS[wi][t] = pf;
                    CJK_FONTS[wi][t] = cf;

                    GlyphVector hv = pf.createGlyphVector(sharedFrc, "H");
                    Rectangle2D hb = hv.getVisualBounds();
                    CAP_HEIGHTS[wi][t] = hb.getHeight() > 1.0 ? (float) hb.getHeight() : px * 0.70f;
                }
            }

            atlasImage = new NativeImage(NativeImage.Format.RGBA, ATLAS_SIZE, ATLAS_SIZE, true);
            GpuSampler linearSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            atlasTexture = new DynamicTexture(() -> "dttdrt_osu_font_atlas", atlasImage) {
                {
                    this.sampler = linearSampler;
                }
            };
            Minecraft.getInstance().getTextureManager().register(FONT_ATLAS_ID, atlasTexture);
            atlasTextureSetup = TextureSetup.singleTexture(atlasTexture.getTextureView(), atlasTexture.getSampler());

            // Pre-warm common ASCII glyphs for Regular & Semibold UI tiers so opening settings has zero stutter
            for (int cp = 32; cp <= 126; cp++) {
                getOrBakeGlyph(Weight.REGULAR, 0, cp);
                getOrBakeGlyph(Weight.REGULAR, 1, cp);
                getOrBakeGlyph(Weight.SEMIBOLD, 0, cp);
                getOrBakeGlyph(Weight.SEMIBOLD, 1, cp);
            }
            for (char ch : "SettingsMarketMiningCustomConfigureDA".toCharArray()) {
                getOrBakeGlyph(Weight.ALTERNATE_REGULAR, 2, ch);
            }
            flushDirtyAtlas();
        } catch (Throwable ignored) {}
    }

    private static String pickFirst(Set<String> available, String... candidates) {
        for (String c : candidates) {
            if (available.contains(c)) return c;
        }
        return Font.SANS_SERIF;
    }

    private static void applyAaHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static int selectTier(float sizePx) {
        if (sizePx <= 15.5f) return 0;
        if (sizePx <= 24.0f) return 1;
        return 2;
    }

    private static long cacheKey(int weightOrd, int tierIdx, int codepoint) {
        return ((long) weightOrd << 40) | ((long) tierIdx << 32) | (codepoint & 0xFFFFFFFFL);
    }

    private static synchronized GlyphEntry getOrBakeGlyph(Weight weight, int tierIdx, int codepoint) {
        long key = cacheKey(weight.ordinal(), tierIdx, codepoint);
        GlyphEntry cached = GLYPH_CACHE.get(key);
        if (cached != null) return cached;

        if (atlasImage == null) return EMPTY_SPACE;

        Font primary = PRIMARY_FONTS[weight.ordinal()][tierIdx];
        Font fallback = CJK_FONTS[weight.ordinal()][tierIdx];
        Font font = (primary != null && primary.canDisplay(codepoint)) ? primary : fallback;
        if (font == null) return EMPTY_SPACE;

        String ch = new String(Character.toChars(codepoint));
        FontMetrics fm = scratchG2d.getFontMetrics(font);
        float advance = (float) font.getStringBounds(ch, sharedFrc).getWidth();
        if (advance <= 0.0f) {
            advance = Math.max(0, fm.charWidth(codepoint));
        }

        if (Character.isWhitespace(codepoint) && codepoint != '\t') {
            GlyphEntry space = new GlyphEntry(0, 0, 0, 0, 0, 0, 0, 0, advance);
            GLYPH_CACHE.put(key, space);
            return space;
        }

        GlyphVector gv = font.createGlyphVector(sharedFrc, ch);
        Rectangle2D vb = gv.getVisualBounds();
        if (vb.getWidth() <= 0.01 || vb.getHeight() <= 0.01) {
            GlyphEntry blank = new GlyphEntry(0, 0, 0, 0, 0, 0, 0, 0, advance);
            GLYPH_CACHE.put(key, blank);
            return blank;
        }

        int gxMin = (int) Math.floor(vb.getX()) - 1;
        int gyMin = (int) Math.floor(vb.getY()) - 1;
        int gxMax = (int) Math.ceil(vb.getX() + vb.getWidth()) + 1;
        int gyMax = (int) Math.ceil(vb.getY() + vb.getHeight()) + 1;
        int drawW = Math.max(1, gxMax - gxMin);
        int drawH = Math.max(1, gyMax - gyMin);

        int cellW = drawW + GLYPH_PAD * 2;
        int cellH = drawH + GLYPH_PAD * 2;

        if (cursorX + cellW + 2 > ATLAS_SIZE) {
            cursorX = 2;
            cursorY += rowHeight + 2;
            rowHeight = 0;
        }
        if (cursorY + cellH + 2 > ATLAS_SIZE) {
            // Safety fallback if atlas fills up completely in extreme long sessions
            GlyphEntry empty = new GlyphEntry(0, 0, 0, 0, 0, 0, 0, 0, advance);
            GLYPH_CACHE.put(key, empty);
            return empty;
        }

        BufferedImage img = new BufferedImage(drawW, drawH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        applyAaHints(g2);
        g2.setFont(font);
        g2.setColor(Color.WHITE);
        g2.drawString(ch, -gxMin, -gyMin);
        g2.dispose();

        int dstX = cursorX + GLYPH_PAD;
        int dstY = cursorY + GLYPH_PAD;
        int[] pixels = img.getRGB(0, 0, drawW, drawH, null, 0, drawW);
        for (int py = 0; py < drawH; py++) {
            int row = py * drawW;
            for (int px = 0; px < drawW; px++) {
                int a = (pixels[row + px] >>> 24) & 0xFF;
                atlasImage.setPixel(dstX + px, dstY + py, a == 0 ? 0 : (a << 24) | 0x00FFFFFF);
            }
        }

        float inv = 1.0f / ATLAS_SIZE;
        GlyphEntry entry = new GlyphEntry(
                dstX * inv, dstY * inv,
                (dstX + drawW) * inv, (dstY + drawH) * inv,
                (float) gxMin, (float) gyMin,
                (float) drawW, (float) drawH,
                advance
        );

        cursorX += cellW + 2;
        rowHeight = Math.max(rowHeight, cellH);
        dirtyAtlas = true;

        GLYPH_CACHE.put(key, entry);
        return entry;
    }

    private static synchronized void flushDirtyAtlas() {
        if (dirtyAtlas && atlasTexture != null) {
            try {
                atlasTexture.upload();
            } catch (Throwable ignored) {}
            dirtyAtlas = false;
        }
    }

    private static float getRenderScale(int tierIdx, float sizePx) {
        return (sizePx * VISUAL_SCALE_CALIBRATION) / TIER_BAKE_PX[tierIdx];
    }

    public static float measureWidth(Weight weight, String text, float sizePx) {
        if (text == null || text.isEmpty()) return 0f;
        ensureInitialized();
        Weight w = weight != null ? weight : Weight.REGULAR;
        int sizeTenths = Math.round(sizePx * 10.0f);
        if (text.length() <= 96) {
            MeasureKey mk = new MeasureKey(w.ordinal(), sizeTenths, text);
            Float cached = MEASURE_CACHE.get(mk);
            if (cached != null) return cached;
        }

        int tierIdx = selectTier(sizePx);
        float scale = getRenderScale(tierIdx, sizePx);

        float total = 0f;
        int len = text.length();
        for (int i = 0; i < len; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            GlyphEntry glyph = getOrBakeGlyph(w, tierIdx, cp);
            total += glyph.advance;
        }
        float result = total * scale;
        if (text.length() <= 96) {
            MEASURE_CACHE.put(new MeasureKey(w.ordinal(), sizeTenths, text), result);
        }
        return result;
    }

    public static void drawString(GuiGraphicsExtractor graphics, Weight weight, String text,
                                  float x, float y, float sizePx, int argb) {
        if (text == null || text.isEmpty() || ((argb >>> 24) & 0xFF) == 0) return;
        ensureInitialized();
        Weight w = weight != null ? weight : Weight.REGULAR;
        int tierIdx = selectTier(sizePx);
        float scale = getRenderScale(tierIdx, sizePx);

        float capHeight = CAP_HEIGHTS[w.ordinal()][tierIdx];
        float baselineY = y + sizePx * 0.14f + capHeight * scale;
        float penX = x;

        boolean scoped = atlasTextureSetup != null && GuiBatchHelper.isScopeActive();
        GuiBatchHelper.QuadCollector qc = scoped
                ? GuiBatchHelper.getFontCollector(graphics, atlasTextureSetup)
                : GuiBatchHelper.getScratchCollector();

        int len = text.length();
        for (int i = 0; i < len; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            GlyphEntry g = getOrBakeGlyph(w, tierIdx, cp);
            if (g.bmpW > 0 && g.bmpH > 0) {
                float gx0 = penX + g.offX * scale;
                float gy0 = baselineY + g.offY * scale;
                float gx1 = gx0 + g.bmpW * scale;
                float gy1 = gy0 + g.bmpH * scale;
                qc.addRect(gx0, gy0, gx1, gy1, g.u0, g.v0, g.u1, g.v1, argb);
            }
            penX += g.advance * scale;
        }

        flushDirtyAtlas();
        if (scoped) return;

        if (qc.size() > 0 && atlasTextureSetup != null) {
            if (GuiBatchHelper.submitBatch(graphics, RenderPipelines.GUI_TEXTURED, atlasTextureSetup, qc)) {
                return;
            }
        }

        float fallbackScale = sizePx / 10.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y + sizePx * 0.12f);
        graphics.pose().scale(fallbackScale, fallbackScale);
        graphics.text(Minecraft.getInstance().font, text, 0, 0, argb, false);
        graphics.pose().popMatrix();
    }

    public static void drawCenteredString(GuiGraphicsExtractor graphics, Weight weight, String text,
                                          float centerX, float y, float sizePx, int argb) {
        float w = measureWidth(weight, text, sizePx);
        drawString(graphics, weight, text, centerX - w * 0.5f, y, sizePx, argb);
    }

    public static float fitFontSize(Weight weight, String text, float maxWidth, float boxHeight,
                                      float preferredSize, float minSize, float maxSize) {
        float lo = Math.max(6.0f, minSize);
        float hi = Math.max(lo, maxSize);
        float target = preferredSize;
        if (boxHeight > 0.0f) {
            float heightTarget = Math.max(lo, Math.min(hi, boxHeight * 0.44f));
            target = Math.max(target, heightTarget);
        }
        target = Math.max(lo, Math.min(hi, target));

        if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
            return target;
        }
        float measured = measureWidth(weight, text, target);
        if (measured > maxWidth) {
            target = Math.max(lo, target * (maxWidth / measured) * 0.98f);
            float recheck = measureWidth(weight, text, target);
            if (recheck > maxWidth && target > lo) {
                target = Math.max(lo, target * (maxWidth / recheck) * 0.98f);
            }
        }
        return target;
    }

    public static void drawFittedString(GuiGraphicsExtractor graphics, Weight weight, String text,
                                        float x, float boxY, float maxWidth, float boxHeight,
                                        float preferredSize, float minSize, float maxSize, int argb) {
        if (text == null || text.isEmpty()) return;
        float fitted = fitFontSize(weight, text, maxWidth, boxHeight, preferredSize, minSize, maxSize);
        String finalText = (maxWidth > 0.0f && measureWidth(weight, text, fitted) > maxWidth)
                ? trimToWidth(weight, text, maxWidth, fitted)
                : text;
        float drawY = boxHeight > 0.0f ? (boxY + (boxHeight - fitted) * 0.5f) : boxY;
        drawString(graphics, weight, finalText, x, drawY, fitted, argb);
    }

    public static void drawFittedCenteredString(GuiGraphicsExtractor graphics, Weight weight, String text,
                                                float centerX, float boxY, float maxWidth, float boxHeight,
                                                float preferredSize, float minSize, float maxSize, int argb) {
        if (text == null || text.isEmpty()) return;
        float fitted = fitFontSize(weight, text, maxWidth, boxHeight, preferredSize, minSize, maxSize);
        String finalText = (maxWidth > 0.0f && measureWidth(weight, text, fitted) > maxWidth)
                ? trimToWidth(weight, text, maxWidth, fitted)
                : text;
        float drawY = boxHeight > 0.0f ? (boxY + (boxHeight - fitted) * 0.5f) : boxY;
        drawCenteredString(graphics, weight, finalText, centerX, drawY, fitted, argb);
    }

    public static String trimToWidth(Weight weight, String text, float maxWidth, float sizePx) {
        if (text == null || text.isEmpty()) return text;
        Weight w = weight != null ? weight : Weight.REGULAR;
        int wTenths = Math.round(maxWidth * 10.0f);
        int sTenths = Math.round(sizePx * 10.0f);
        TrimKey tk = new TrimKey(w.ordinal(), wTenths, sTenths, text);
        String cached = TRIM_CACHE.get(tk);
        if (cached != null) return cached;

        String result;
        if (measureWidth(w, text, sizePx) <= maxWidth) {
            result = text;
        } else {
            float ellW = measureWidth(w, "...", sizePx);
            if (ellW >= maxWidth) {
                result = "";
            } else {
                StringBuilder sb = new StringBuilder();
                float acc = 0f;
                int tierIdx = selectTier(sizePx);
                float scale = getRenderScale(tierIdx, sizePx);
                int len = text.length();
                for (int i = 0; i < len; ) {
                    int cp = text.codePointAt(i);
                    int charCount = Character.charCount(cp);
                    float adv = getOrBakeGlyph(w, tierIdx, cp).advance * scale;
                    if (acc + adv + ellW > maxWidth) {
                        sb.append("...");
                        break;
                    }
                    sb.appendCodePoint(cp);
                    acc += adv;
                    i += charCount;
                }
                result = sb.toString();
            }
        }
        TRIM_CACHE.put(tk, result);
        return result;
    }
}
