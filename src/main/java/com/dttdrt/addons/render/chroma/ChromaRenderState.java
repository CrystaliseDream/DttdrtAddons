package com.dttdrt.addons.render.chroma;

import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.mixin.accessor.TextColorAccessor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Coordinates chroma style detection, per-frame UBO writes, and GUI/world pipeline overrides.
 */
public final class ChromaRenderState {

    public static final String CHROMA_COLOR_NAME = "chroma";
    private static final TextColor CHROMA_TEXT_COLOR = TextColorAccessor.dttdrt$create(0xFFFFFF, CHROMA_COLOR_NAME);
    private static final Style CHROMA_STYLE = Style.EMPTY.withColor(CHROMA_TEXT_COLOR);

    private static final long START_NANO = System.nanoTime();
    private static ChromaUniform chromaUniform;
    private static GpuBufferSlice chromaBufferSlice;
    private static boolean glyphIsChroma = false;

    private ChromaRenderState() {
    }

    public static void onClientTick() {
        // Animation timing uses continuous monotonic wall-clock nanos so GUI previews stay live even while paused.
    }

    public static float getElapsedTicks() {
        return (float) ((System.nanoTime() - START_NANO) / 50_000_000.0);
    }

    public static boolean isChromaFeatureActive() {
        return ModConfig.getInstance().chromaEnabled;
    }

    public static TextColor getChromaTextColor() {
        return CHROMA_TEXT_COLOR;
    }

    public static Style getChromaStyle() {
        return CHROMA_STYLE;
    }

    public static boolean isChromaCode(char code) {
        return isChromaFeatureActive() && (code == 'z' || code == 'Z');
    }

    public static boolean isChromaColor(TextColor color) {
        if (color == null) {
            return false;
        }
        String name = ((TextColorAccessor) (Object) color).dttdrt$getName();
        return CHROMA_COLOR_NAME.equals(name);
    }

    public static boolean isNotActuallyEqualBecauseOfChroma(TextColor self, Object other) {
        if (!(other instanceof TextColor otherTc)) {
            return false;
        }
        boolean selfChroma = isChromaColor(self);
        boolean otherChroma = isChromaColor(otherTc);
        if (!selfChroma && !otherChroma) {
            return false;
        }
        if (selfChroma && otherChroma) {
            return false;
        }
        return true;
    }

    public static boolean isGlyphChromaActive() {
        return glyphIsChroma && isChromaFeatureActive();
    }

    public static void setGlyphIsChroma(boolean chroma) {
        glyphIsChroma = chroma;
    }

    public static void checkIfGlyphIsChroma(Object styledRenderable) {
        if (!isChromaFeatureActive()) {
            glyphIsChroma = false;
            return;
        }
        if (styledRenderable instanceof ChromaStyledAccess access) {
            Style s = access.dttdrt$getStyle();
            glyphIsChroma = (s != null && isChromaColor(s.getColor()));
        } else {
            glyphIsChroma = false;
        }
    }

    public static RenderPipeline resolveGlyphPipeline(Object renderable) {
        if (!isChromaFeatureActive()) {
            return null;
        }
        if (renderable instanceof ChromaStyledAccess access) {
            Style style = access.dttdrt$getStyle();
            if (style != null && isChromaColor(style.getColor())) {
                return ChromaRenderPipelines.getChromaText();
            }
        }
        return null;
    }

    public static RenderPipeline resolveGuiPipeline(GuiElementRenderState state, RenderPipeline original) {
        if (!isChromaFeatureActive()) {
            return original;
        }
        if (state instanceof GlyphRenderState glyphState) {
            RenderPipeline override = resolveGlyphPipeline(glyphState.renderable());
            if (override != null) {
                return override;
            }
        }
        return original;
    }

    public static void computeChromaBufferSlice() {
        if (!isChromaFeatureActive()) {
            return;
        }
        if (chromaUniform == null) {
            chromaUniform = new ChromaUniform();
        }
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        int displayWidth = (mc != null && mc.getWindow() != null) ? Math.max(1, mc.getWindow().getWidth()) : 1920;

        float chromaSize = Math.max(1.0f, cfg.chromaSize * (displayWidth / 100.0f));
        float ticks = getElapsedTicks();
        String dir = cfg.chromaDirection != null ? cfg.chromaDirection : "FORWARD_RIGHT";
        boolean leftFlow = "FORWARD_LEFT".equalsIgnoreCase(dir) || "BACKWARD_LEFT".equalsIgnoreCase(dir);
        boolean forwardDiag = "FORWARD_RIGHT".equalsIgnoreCase(dir) || "FORWARD_LEFT".equalsIgnoreCase(dir);
        if (leftFlow) {
            ticks = -ticks;
        }

        float timeOffset = ticks * (cfg.chromaSpeed / 360.0f);
        float saturation = Math.max(0.0f, Math.min(1.0f, cfg.chromaSaturation));
        int forwardDirection = forwardDiag ? 1 : 0;

        chromaBufferSlice = chromaUniform.writeWith(chromaSize, timeOffset, saturation, forwardDirection);
    }

    /**
     * Must only be called outside an active RenderPass (e.g. at RenderType.draw HEAD or executeDrawRange HEAD).
     */
    public static void ensureChromaBufferSlice() {
        if (isChromaFeatureActive() && chromaBufferSlice == null) {
            computeChromaBufferSlice();
        }
    }

    public static void bindChromaUniform(RenderPass renderPass) {
        if (!isChromaFeatureActive() || renderPass == null || chromaBufferSlice == null) {
            return;
        }
        renderPass.setUniform(ChromaRenderPipelines.CHROMA_UNIFORM_NAME, chromaBufferSlice);
    }

    public static void bindChromaUniformIfPipelineMatches(RenderPass renderPass, RenderPipeline pipeline) {
        if (!isChromaFeatureActive() || renderPass == null || pipeline == null) {
            return;
        }
        if (pipeline == ChromaRenderPipelines.getChromaText()) {
            bindChromaUniform(renderPass);
        }
    }

    public static void clearChromaUniforms() {
        if (chromaUniform != null) {
            chromaUniform.clear();
        }
        chromaBufferSlice = null;
        glyphIsChroma = false;
    }
}
