package com.dttdrt.addons.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * High-performance GuiRenderState batching bridge for Minecraft 26.1.2 - 26.3.
 * Supports region-level scoped batching (beginScope / flushScope / endScope) so dozens of widgets
 * and hundreds of text labels inside a panel coalesce into ONLY 1 UI batch + 1 Font batch,
 * eliminating GuiRenderState O(N^2) intersection scans, reflection overhead, and GC churn.
 */
public final class GuiBatchHelper {

    private static final Field GUI_RENDER_STATE_FIELD;
    private static final Field SCISSOR_STACK_FIELD;
    private static final Method SCISSOR_PEEK_METHOD;
    private static final boolean AVAILABLE;

    // Reusable pooled collectors (zero per-frame working array churn)
    private static final QuadCollector SCOPE_UI_COLLECTOR = new QuadCollector(256);
    private static final QuadCollector SCOPE_FONT_COLLECTOR = new QuadCollector(512);
    private static final QuadCollector SCRATCH_COLLECTOR = new QuadCollector(32);

    private static boolean scopeActive = false;
    private static GuiRenderState scopeRenderState = null;
    private static ScreenRectangle scopeScissor = null;
    private static final Matrix3x2f SCOPE_POSE = new Matrix3x2f();
    private static TextureSetup scopeUiSetup = null;
    private static TextureSetup scopeFontSetup = null;

    static {
        Field rsField = null;
        Field scField = null;
        Method peekMethod = null;
        boolean ok = false;
        try {
            rsField = GuiGraphicsExtractor.class.getDeclaredField("guiRenderState");
            rsField.setAccessible(true);
            scField = GuiGraphicsExtractor.class.getDeclaredField("scissorStack");
            scField.setAccessible(true);
            Class<?> stackCls = Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor$ScissorStack");
            peekMethod = stackCls.getDeclaredMethod("peek");
            peekMethod.setAccessible(true);
            ok = true;
        } catch (Throwable ignored) {}
        GUI_RENDER_STATE_FIELD = rsField;
        SCISSOR_STACK_FIELD = scField;
        SCISSOR_PEEK_METHOD = peekMethod;
        AVAILABLE = ok;
    }

    private GuiBatchHelper() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static boolean isScopeActive() {
        return scopeActive && AVAILABLE;
    }

    public static QuadCollector getScratchCollector() {
        SCRATCH_COLLECTOR.reset();
        return SCRATCH_COLLECTOR;
    }

    /**
     * Begins a batched UI + Font collection scope capturing current pose and scissor ONCE.
     */
    public static void beginScope(GuiGraphicsExtractor g) {
        if (!AVAILABLE || g == null) return;
        if (scopeActive) {
            flushScope(g);
        }
        GuiRenderState rs = getRenderState(g);
        if (rs == null) {
            scopeActive = false;
            return;
        }
        scopeActive = true;
        scopeRenderState = rs;
        scopeScissor = peekScissor(g);
        SCOPE_POSE.set(g.pose());
        SCOPE_UI_COLLECTOR.reset();
        SCOPE_FONT_COLLECTOR.reset();
        scopeUiSetup = null;
        scopeFontSetup = null;
    }

    /**
     * Returns the active UI collector for this scope, flushing first if TextureSetup changed.
     */
    public static QuadCollector getUiCollector(GuiGraphicsExtractor g, TextureSetup uiSetup) {
        if (scopeUiSetup != null && !scopeUiSetup.equals(uiSetup)) {
            flushScope(g);
        }
        scopeUiSetup = uiSetup;
        return SCOPE_UI_COLLECTOR;
    }

    /**
     * Returns the active Font collector for this scope, flushing first if TextureSetup changed.
     */
    public static QuadCollector getFontCollector(GuiGraphicsExtractor g, TextureSetup fontSetup) {
        if (scopeFontSetup != null && !scopeFontSetup.equals(fontSetup)) {
            flushScope(g);
        }
        scopeFontSetup = fontSetup;
        return SCOPE_FONT_COLLECTOR;
    }

    /**
     * Flushes accumulated UI shapes first (Painter's Order) followed by all accumulated Font glyphs,
     * and refreshes the pose/scissor snapshot for continued collection in the same scope.
     */
    public static void flushScope(GuiGraphicsExtractor g) {
        if (!scopeActive || scopeRenderState == null) return;

        if (SCOPE_UI_COLLECTOR.quadCount > 0 && scopeUiSetup != null) {
            commitCollector(scopeRenderState, RenderPipelines.GUI_TEXTURED, scopeUiSetup,
                    new Matrix3x2f(SCOPE_POSE), scopeScissor, SCOPE_UI_COLLECTOR);
            SCOPE_UI_COLLECTOR.reset();
        }
        if (SCOPE_FONT_COLLECTOR.quadCount > 0 && scopeFontSetup != null) {
            commitCollector(scopeRenderState, RenderPipelines.GUI_TEXTURED, scopeFontSetup,
                    new Matrix3x2f(SCOPE_POSE), scopeScissor, SCOPE_FONT_COLLECTOR);
            SCOPE_FONT_COLLECTOR.reset();
        }
        if (g != null) {
            scopeScissor = peekScissor(g);
            SCOPE_POSE.set(g.pose());
        }
    }

    public static void endScope(GuiGraphicsExtractor g) {
        if (!scopeActive) return;
        flushScope(g);
        scopeActive = false;
        scopeRenderState = null;
        scopeScissor = null;
        scopeUiSetup = null;
        scopeFontSetup = null;
    }

    public static ScreenRectangle peekScissor(GuiGraphicsExtractor g) {
        if (!AVAILABLE || g == null) return null;
        try {
            Object stack = SCISSOR_STACK_FIELD.get(g);
            return stack == null ? null : (ScreenRectangle) SCISSOR_PEEK_METHOD.invoke(stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static GuiRenderState getRenderState(GuiGraphicsExtractor g) {
        if (!AVAILABLE || g == null) return null;
        try {
            return (GuiRenderState) GUI_RENDER_STATE_FIELD.get(g);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean submitBatch(GuiGraphicsExtractor g, RenderPipeline pipeline,
                                      TextureSetup textureSetup, QuadCollector collector) {
        if (!AVAILABLE || collector == null || collector.quadCount <= 0) return false;
        GuiRenderState rs = getRenderState(g);
        if (rs == null) return false;
        ScreenRectangle scissor = peekScissor(g);
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        return commitCollector(rs, pipeline, textureSetup, pose, scissor, collector);
    }

    private static boolean commitCollector(GuiRenderState rs, RenderPipeline pipeline,
                                           TextureSetup textureSetup, Matrix3x2f pose,
                                           ScreenRectangle scissor, QuadCollector collector) {
        if (collector.quadCount <= 0) return true;
        int ix0 = (int) Math.floor(collector.minX);
        int iy0 = (int) Math.floor(collector.minY);
        int ix1 = (int) Math.ceil(collector.maxX);
        int iy1 = (int) Math.ceil(collector.maxY);
        if (ix1 <= ix0) ix1 = ix0 + 1;
        if (iy1 <= iy0) iy1 = iy0 + 1;

        ScreenRectangle rawBounds = new ScreenRectangle(ix0, iy0, ix1 - ix0, iy1 - iy0).transformMaxBounds(pose);
        ScreenRectangle finalBounds = scissor != null ? scissor.intersection(rawBounds) : rawBounds;
        if (finalBounds == null) return true;

        MultiQuadElementState state = new MultiQuadElementState(
                pipeline,
                textureSetup,
                pose,
                collector.trimCoords(),
                collector.trimColors(),
                collector.quadCount,
                scissor,
                finalBounds
        );
        rs.addGuiElement(state);
        return true;
    }

    /**
     * Collects multiple 2D textured/colored quads into reusable primitive buffers.
     */
    public static final class QuadCollector {
        private float[] coords;
        private int[] colors;
        private int quadCount = 0;
        private float minX = Float.MAX_VALUE;
        private float minY = Float.MAX_VALUE;
        private float maxX = -Float.MAX_VALUE;
        private float maxY = -Float.MAX_VALUE;

        public QuadCollector(int initialQuadCapacity) {
            int cap = Math.max(4, initialQuadCapacity);
            this.coords = new float[cap * 16];
            this.colors = new int[cap];
        }

        public void reset() {
            this.quadCount = 0;
            this.minX = Float.MAX_VALUE;
            this.minY = Float.MAX_VALUE;
            this.maxX = -Float.MAX_VALUE;
            this.maxY = -Float.MAX_VALUE;
        }

        public void addRect(float x0, float y0, float x1, float y1,
                            float u0, float v0, float u1, float v1, int argb) {
            if (((argb >>> 24) & 0xFF) == 0 || x1 <= x0 || y1 <= y0) return;
            addFreeQuad(x0, y0, x0, y1, x1, y1, x1, y0, u0, v0, u1, v1, argb);
        }

        public void addFreeQuad(float x0, float y0, float x1, float y1,
                                float x2, float y2, float x3, float y3,
                                float u0, float v0, float u1, float v1, int argb) {
            if (((argb >>> 24) & 0xFF) == 0) return;
            ensureCapacity(quadCount + 1);
            int base = quadCount * 16;
            // Winding order matching BlitRenderState: TL -> BL -> BR -> TR
            coords[base]      = x0; coords[base + 1]  = y0; coords[base + 2]  = u0; coords[base + 3]  = v0;
            coords[base + 4]  = x1; coords[base + 5]  = y1; coords[base + 6]  = u0; coords[base + 7]  = v1;
            coords[base + 8]  = x2; coords[base + 9]  = y2; coords[base + 10] = u1; coords[base + 11] = v1;
            coords[base + 12] = x3; coords[base + 13] = y3; coords[base + 14] = u1; coords[base + 15] = v0;
            colors[quadCount] = argb;
            quadCount++;

            minX = Math.min(minX, Math.min(Math.min(x0, x1), Math.min(x2, x3)));
            minY = Math.min(minY, Math.min(Math.min(y0, y1), Math.min(y2, y3)));
            maxX = Math.max(maxX, Math.max(Math.max(x0, x1), Math.max(x2, x3)));
            maxY = Math.max(maxY, Math.max(Math.max(y0, y1), Math.max(y2, y3)));
        }

        public int size() {
            return quadCount;
        }

        private void ensureCapacity(int targetQuads) {
            if (targetQuads <= colors.length) return;
            int newCap = Math.max(targetQuads, colors.length * 2);
            float[] nc = new float[newCap * 16];
            System.arraycopy(coords, 0, nc, 0, quadCount * 16);
            coords = nc;
            int[] ncol = new int[newCap];
            System.arraycopy(colors, 0, ncol, 0, quadCount);
            colors = ncol;
        }

        private float[] trimCoords() {
            float[] out = new float[quadCount * 16];
            System.arraycopy(coords, 0, out, 0, out.length);
            return out;
        }

        private int[] trimColors() {
            int[] out = new int[quadCount];
            System.arraycopy(colors, 0, out, 0, out.length);
            return out;
        }
    }

    private record MultiQuadElementState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            float[] coords,
            int[] colors,
            int quadCount,
            ScreenRectangle scissorArea,
            ScreenRectangle bounds
    ) implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int q = 0; q < quadCount; q++) {
                int c = colors[q];
                int base = q * 16;
                consumer.addVertexWith2DPose(pose, coords[base], coords[base + 1])
                        .setUv(coords[base + 2], coords[base + 3]).setColor(c);
                consumer.addVertexWith2DPose(pose, coords[base + 4], coords[base + 5])
                        .setUv(coords[base + 6], coords[base + 7]).setColor(c);
                consumer.addVertexWith2DPose(pose, coords[base + 8], coords[base + 9])
                        .setUv(coords[base + 10], coords[base + 11]).setColor(c);
                consumer.addVertexWith2DPose(pose, coords[base + 12], coords[base + 13])
                        .setUv(coords[base + 14], coords[base + 15]).setColor(c);
            }
        }
    }
}
