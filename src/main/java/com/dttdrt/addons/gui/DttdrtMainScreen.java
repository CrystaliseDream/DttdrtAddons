package com.dttdrt.addons.gui;

import com.dttdrt.addons.DttdrtAddons;
import com.dttdrt.addons.audio.OsuSoundEngine;
import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.FeatureManager;
import com.dttdrt.addons.core.lang.ModLang;
import com.dttdrt.addons.core.log.ModLogManager;
import com.dttdrt.addons.features.custom.CustomNameFeature;
import com.dttdrt.addons.features.freecam.FreeCamFeature;
import com.dttdrt.addons.features.market.MarketState;
import com.dttdrt.addons.features.market.model.AhItemListing;
import com.dttdrt.addons.features.market.model.BazaarItemSummary;
import com.dttdrt.addons.features.market.model.BazaarOrderEntry;
import com.dttdrt.addons.features.market.model.PriceHistoryPoint;
import com.dttdrt.addons.features.market.model.SkyblockItem;
import com.dttdrt.addons.features.mining.CorpseEspFeature;
import com.dttdrt.addons.features.mining.CorpseEspFeature.CorpseType;
import com.dttdrt.addons.features.mining.CrystalHollowsHudEditScreen;
import com.dttdrt.addons.features.mining.CrystalHollowsTracker;
import com.dttdrt.addons.features.mining.CrystalHollowsTracker.CrystalLocation;
import com.dttdrt.addons.features.mining.GemstoneEspFeature;
import com.dttdrt.addons.features.mining.GemstoneEspFeature.Gemstone;
import com.dttdrt.addons.features.mining.LittleFootEspFeature;
import com.dttdrt.addons.features.mining.MiningProfitFeature;
import com.dttdrt.addons.features.mining.MiningProfitHudEditScreen;
import com.dttdrt.addons.features.mining.NucleusProfitFeature;
import com.dttdrt.addons.features.mining.ScathaProfitFeature;
import com.dttdrt.addons.features.mining.TempleWaypointFeature;
import com.dttdrt.addons.features.nametag.NametagFeature;
import com.dttdrt.addons.gui.TorusFontRenderer.Weight;
import com.dttdrt.addons.texture.ClassicTextureRegistry;
import com.dttdrt.addons.texture.PackDisablerIconBridge;
import com.dttdrt.addons.util.NotificationUtils;
import com.dttdrt.addons.util.NumberFormatUtils;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.awt.Color;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 1:1 osu!lazer (OverlayColourScheme.Purple, 1080p HiDPI resolution, active game font)
 * Settings Drawer + Right-Side Scaled Market HUD.
 * - Centered SwitchButton width stretch (0.75x hollow -> 1.0x solid elastic expansion from center).
 * - Section focus dimming (inactive = 0.80 alpha OSU_BG5, hovered = 0.50, active = 0.0; click inactive to focus & scroll).
 * - No revert-to-default buttons, no click sounds, no animated button triangle overlays.
 */
public class DttdrtMainScreen extends Screen {

    // Exact osu!lazer OverlayColourScheme.Purple HSL palette (Hue = 255)
    private static final int OSU_COLOUR1 = 0xFF9966FF; // Highlight1
    private static final int OSU_COLOUR2 = 0xFF7A47EB; // Primary button
    private static final int OSU_LIGHT1 = 0xFFBFB8E0;  // Sidebar hover
    private static final int OSU_LIGHT3 = 0xFFA493D1;  // Sidebar inactive
    private static final int OSU_LIGHT4 = 0xFF6A4CB3;  // Border / switch outline / slider left
    private static final int OSU_DARK4 = 0xFF2D293D;   // Control hover
    private static final int OSU_DARK5 = 0xFF221F2E;   // Slider drag
    private static final int OSU_BG2 = 0xFF484554;
    private static final int OSU_BG3 = 0xFF3C3A46;
    private static final int OSU_BG4 = 0xFF302E38;     // Panel background
    private static final int OSU_BG4_DARK = 0xFF2B2932;// Control normal
    private static final int OSU_BG5 = 0xFF24222A;     // Sidebar / section dim / search box
    private static final int OSU_BG6 = 0xFF18171C;     // Section separator
    private static final int OSU_CONTENT1 = 0xFFFFFFFF;
    private static final int OSU_CONTENT2 = 0xFFDFD9F2;
    private static final int OSU_GRAY_TEXT = 0xFF999999;

    private static final int COLOR_SUCCESS = 0xFF10B981;
    private static final int COLOR_DANGER = 0xFFEF4444;
    private static final int COLOR_GOLD = 0xFFF59E0B;
    private static final int COLOR_GRID = 0x24FFFFFF;

    // Plan A: Dark Financial Terminal 4-step Elevation & Text Hierarchy (Hue 255)
    private static final int MK_SURFACE0 = 0xFF15141A;
    private static final int MK_SURFACE1 = 0xFF211F2B;
    private static final int MK_SURFACE2 = 0xFF181620;
    private static final int MK_SURFACE_HOVER = 0xFF2B2838;
    private static final int MK_SURFACE3 = 0xFF2F2B3D;
    private static final int MK_TEXT_PRIMARY = 0xFFECEAF4;
    private static final int MK_TEXT_SECONDARY = 0xFFBDB7D4;
    private static final int MK_TEXT_MUTED = 0xFF928BA6;

    private static final int MK_CARD_GAP = 12;
    private static final int MK_EMPTY_H = 220;
    private static final int MK_CHART_H = 192;
    private static final DecimalFormat PCT_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private static final int[] TOP_LINE_PRESETS = new int[]{
            0xFFFF66AA, // osu! Pink
            0xFF9966FF, // osu! Purple
            0xFF38BDF8, // Cyan
            0xFFF59E0B, // Gold
            0xFF10B981, // Emerald
            0xFFEF4444, // Crimson
            0xFFE5E7EB  // White
    };

    private static final int[] NAMETAG_COLOR_PRESETS = new int[]{
            0xFFFF69B4, // Hot Pink
            0xFF9966FF, // Purple
            0xFF38BDF8, // Sky Blue
            0xFF55FF55, // Lime Green
            0xFFF59E0B, // Gold
            0xFFEF4444, // Crimson
            0xFFFFFF55, // Yellow
            0xFFFFFFFF  // White
    };

    private static final int[] MC_16_COLOR_PRESETS = new int[]{
            0xFF000000, // 0 Black
            0xFF0000AA, // 1 Dark Blue
            0xFF00AA00, // 2 Dark Green
            0xFF00AAAA, // 3 Dark Aqua
            0xFFAA0000, // 4 Dark Red
            0xFFAA00AA, // 5 Dark Purple
            0xFFFFAA00, // 6 Gold
            0xFFAAAAAA, // 7 Gray
            0xFF555555, // 8 Dark Gray
            0xFF5555FF, // 9 Blue
            0xFF55FF55, // a Green
            0xFF55FFFF, // b Aqua
            0xFFFF5555, // c Red
            0xFFFF55FF, // d Light Purple
            0xFFFFFF55, // e Yellow
            0xFFFFFFFF  // f White
    };

    private static final String[] CHROMA_DIRECTIONS = new String[]{
            "FORWARD_RIGHT", "FORWARD_LEFT", "BACKWARD_RIGHT", "BACKWARD_LEFT"
    };

    // Exact SettingsPanel.cs constants (1080p virtual coordinates)
    private static final int SIDEBAR_W = 170;
    private static final int PANEL_W = 400;
    private static final int DRAWER_W = SIDEBAR_W + PANEL_W; // 570
    private static final int CONTENT_PAD_L = 12;
    private static final int CONTENT_PAD_R = 16;
    private static final int ITEM_SPACING_V2 = 4;
    private static final int EXPANDABLE_HEADER_H = 104;
    private static final int FIXED_SEARCH_H = 52;
    private static final int TOP_LINE_H = 3;

    private static final String[] SECTIONS = new String[]{"Market", "Mining", "Combat", "Custom", "Debug"};
    private static final String[] SECTION_LANG_KEYS = new String[]{
            "dttdrtaddons.section.market",
            "dttdrtaddons.section.mining",
            "dttdrtaddons.section.combat",
            "dttdrtaddons.section.custom",
            "dttdrtaddons.section.debug"
    };

    // Frame-rate independent animations
    private long lastFrameNs = 0L;
    private float marketHudAlpha = 0.0f;
    private float marketAlphaAtClose = 0.0f;
    private float marketModeAnim = 0.0f;         // 0.0f = AH, 1.0f = BZ
    private float marketModeContentAlpha = 1.0f; // Fade-in on mode switch
    private float activeMarketFade = 1.0f;       // Scoped multiplier applied to mCol()
    private float sidebarIndicatorY = 26.0f;
    private long closeStartMs = 0L;
    private boolean closingDrawer = false;
    private boolean playedPopInSound = false;

    // Hover-enter & dropdown sound edge tracking
    private String lastHoveredKey = "";
    private String frameHoveredKey = "";
    private int frameHoverType = 0; // 1 = sidebar, 2 = button, 3 = default row/checkbox/slider
    private boolean lastFuzzyOpen = false;

    // osu!lazer SettingsSearchTextBox animation state
    private float searchCaretX = 0.0f;
    private float searchCaretAlpha = 1.0f;
    private float searchPlaceholderAlpha = 1.0f;
    private long searchLastCharMs = 0L;
    private int searchLastLen = 0;

    // Toggle fill & centered width-stretch tracking
    private final Map<String, Float> toggleFillAnims = new HashMap<>();
    private final Map<String, Long> toggleChangeMs = new HashMap<>();
    private final Map<String, Boolean> toggleLastVal = new HashMap<>();
    private final Map<String, Float> hoverAnims = new HashMap<>();

    // Section focus & dimming tracking
    private int activeSectionIdx = 0;
    private int lockedFocusSectionIdx = -1; // Preserved while smoothly scrolling to clicked/sidebar section
    private final float[] sectionDimAnims = new float[]{0.0f, 0.8f, 0.8f, 0.8f, 0.8f};
    private final float[] sidebarIndHeightAnims = new float[]{18.0f, 4.0f, 4.0f, 4.0f, 4.0f};
    private final int[] sectionStartY = new int[SECTIONS.length];
    private final int[] sectionScreenTop = new int[SECTIONS.length];
    private final int[] sectionScreenBot = new int[SECTIONS.length];

    // Drawer scroll & search
    private double scrollCurrent = 0.0;
    private double scrollTarget = 0.0;
    private double drawerMaxScroll = 0.0;
    private boolean draggingDrawerScrollbar = false;
    private String settingsSearchText = "";

    // Right-side Market HUD scroll
    private double marketScrollOffset = 0.0;
    private double marketScrollTarget = 0.0;
    private boolean draggingMarketScrollbar = false;

    // Focus tracking: 0=none, 1=market search, 2=min price, 3=max price, 4=chX, 5=chY, 6=chZ, 7=drawer search
    private int focusedInput = 0;
    // Keybind capture: 0=none, 1=open menu keybind, 2=quick !vote keybind
    private int capturingKeybindId = 0;
    // Sliders: 1=gem radius, 2=gem interval, 3=bg darkness, 4=panel opacity, 5=bg scale, 6=bg offsetX, 7=bg offsetY, 8=marketHudScale
    private int draggingSlider = 0;

    // Nametag HSV color palette state
    private float nametagPaletteHue = 0.91f;
    private float nametagPaletteSat = 0.59f;
    private float nametagPaletteVal = 1.0f;
    private int draggingPaletteMode = 0; // 0=none, 1=Nametag SV, 2=Nametag Hue, 3=CustomName SV, 4=CustomName Hue
    private int palSvSec = -1;
    private int palSvX = 0, palSvY = 0, palSvW = 0, palSvH = 0;
    private int palHueX = 0, palHueY = 0, palHueW = 0, palHueH = 0;

    // Custom Name HSV color palette state
    private float customNamePaletteHue = 0.83f;
    private float customNamePaletteSat = 0.67f;
    private float customNamePaletteVal = 1.0f;
    private int namePalSvSec = -1;
    private int namePalSvX = 0, namePalSvY = 0, namePalSvW = 0, namePalSvH = 0;
    private int namePalHueX = 0, namePalHueY = 0, namePalHueW = 0, namePalHueH = 0;

    // MC 26.3+ SDL3 TextInputManager reflection bridge (startTextInput/stopTextInput so charTyped fires)
    private static boolean textInputReflectInit = false;
    private static Method mcTextInputManagerMethod = null;
    private static Method timOnFocusChangeMethod = null;
    private boolean nativeTextInputActive = false;

    private String searchInputText = "";
    private String minPriceText = "";
    private String maxPriceText = "";
    private long openTime = 0L;

    private int selectedChLocIndex = 0;
    private String chXText = "";
    private String chYText = "";
    private String chZText = "";
    private String chStatusMsg = "";
    private long chStatusExpiry = 0L;

    // Debug Log Viewer state
    private int debugLogBoxX = 0;
    private int debugLogBoxY = 0;
    private int debugLogBoxW = 0;
    private int debugLogBoxH = 0;
    private double debugLogScroll = 0.0;
    private double debugLogMaxScroll = 0.0;
    private boolean debugLogAutoFollow = true;
    private long lastDebugLogVersion = -1L;
    private final List<String> cachedWrappedDebugLines = new ArrayList<>();
    private String debugFeedbackText = "";
    private long debugFeedbackUntilMs = 0L;

    // Drag-click state for all switches & checkboxes
    private boolean dragToggleActive = false;
    private boolean dragToggleTargetState = false;
    private final Set<String> dragToggledKeys = new HashSet<>();

    private record ToggleHitbox(int sectionIdx, String key, int x, int y, int w, int h, boolean value, Consumer<Boolean> setter) {}
    private record ButtonHitbox(int sectionIdx, int x, int y, int w, int h, Runnable action) {}
    private record SliderHitbox(int sectionIdx, int id, int x, int y, int w, int h) {}
    private record InputHitbox(int sectionIdx, int id, int x, int y, int w, int h) {}

    private final List<ToggleHitbox> drawerToggles = new ArrayList<>();
    private final List<ButtonHitbox> drawerButtons = new ArrayList<>();
    private final List<SliderHitbox> drawerSliders = new ArrayList<>();
    private final List<InputHitbox> drawerInputs = new ArrayList<>();

    private record RecentChipHitbox(SkyblockItem item, int x, int y, int w, int h) {}
    private final List<RecentChipHitbox> marketRecentHitboxes = new ArrayList<>();

    public DttdrtMainScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
        if (!DttdrtAddons.canOpenMenu(this.minecraft)) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
            return;
        }
        ModLang.reloadExternalOverrides();
        this.openTime = System.currentTimeMillis();
        this.lastFrameNs = System.nanoTime();
        this.closingDrawer = false;
        this.closeStartMs = 0L;
        this.marketHudAlpha = 0.0f;
        this.marketAlphaAtClose = 0.0f;
        this.marketScrollTarget = this.marketScrollOffset;
        this.activeSectionIdx = 0;
        this.lockedFocusSectionIdx = -1;
        // SettingsPanel.PopIn: SearchTextBox.TakeFocus() eagerly holds focus on open
        this.focusedInput = 7;
        this.capturingKeybindId = 0;
        this.draggingPaletteMode = 0;
        syncPaletteHsvFromColor(ModConfig.getInstance().nametagColor);
        syncCustomNamePaletteHsvFromColor(ModConfig.getInstance().customNameColor);
        this.nativeTextInputActive = false;
        this.searchCaretX = TorusFontRenderer.measureWidth(Weight.REGULAR, this.settingsSearchText, 16f);
        this.searchCaretAlpha = 1.0f;
        this.searchPlaceholderAlpha = this.settingsSearchText.isEmpty() ? 1.0f : 0.0f;
        this.searchLastCharMs = 0L;
        this.searchLastLen = this.settingsSearchText.length();
        this.lastHoveredKey = "";
        this.frameHoveredKey = "";
        this.lastFuzzyOpen = false;
        syncNativeTextInput();

        if (!this.playedPopInSound) {
            this.playedPopInSound = true;
            OsuSoundEngine.playPopIn();
        }

        MarketState state = MarketState.getInstance();
        this.marketModeAnim = "AH".equalsIgnoreCase(state.getMode()) ? 0.0f : 1.0f;
        this.marketModeContentAlpha = 1.0f;
        this.activeMarketFade = 1.0f;
        this.searchInputText = state.getSearchQuery() != null ? state.getSearchQuery() : "";
        this.minPriceText = state.getMinPriceInput() != null ? state.getMinPriceInput() : "";
        this.maxPriceText = state.getMaxPriceInput() != null ? state.getMaxPriceInput() : "";
        state.onScreenOpened();
    }

    // ==========================================
    // 1080p HiDPI Virtual Coordinate Helpers
    // ==========================================
    private float getOsuScale() {
        if (this.minecraft == null) return 1.0f;
        Window win = this.minecraft.getWindow();
        return Math.max(0.45f, win.getHeight() / 1080.0f);
    }

    private float getTotalRenderScale() {
        if (this.minecraft == null) return 1.0f;
        Window win = this.minecraft.getWindow();
        int guiScale = Math.max(1, win.getGuiScale());
        return getOsuScale() / (float) guiScale;
    }

    private int getVirtW() {
        if (this.minecraft == null) return 1920;
        return Math.max(640, Math.round(this.minecraft.getWindow().getWidth() / getOsuScale()));
    }

    private int getVirtH() {
        if (this.minecraft == null) return 1080;
        return Math.max(480, Math.round(this.minecraft.getWindow().getHeight() / getOsuScale()));
    }

    private double toVirtX(double guiMouseX) {
        return guiMouseX / getTotalRenderScale();
    }

    private double toVirtY(double guiMouseY) {
        return guiMouseY / getTotalRenderScale();
    }

    private float getMarketScale() {
        return Math.max(0.60f, Math.min(1.50f, ModConfig.getInstance().marketHudScale));
    }

    private int mCol(int argb) {
        return mulAlpha(argb, marketHudAlpha * activeMarketFade);
    }

    private int marketCardBg() {
        int a = Math.max(40, Math.min(245, ModConfig.getInstance().uiPanelOpacity));
        return mCol((a << 24) | (MK_SURFACE1 & 0x00FFFFFF));
    }

    private int marketCardInnerBg() {
        int a = Math.max(40, Math.min(255, ModConfig.getInstance().uiPanelOpacity + 14));
        return mCol((a << 24) | (MK_SURFACE2 & 0x00FFFFFF));
    }

    private int marketCardHoverBg() {
        int a = Math.max(40, Math.min(255, ModConfig.getInstance().uiPanelOpacity + 18));
        return mCol((a << 24) | (MK_SURFACE_HOVER & 0x00FFFFFF));
    }

    private int marketElevatedBg() {
        return mCol(0xF6000000 | (MK_SURFACE3 & 0x00FFFFFF));
    }

    private static float damp(float current, float target, float speed, float dt) {
        return current + (target - current) * (float) (1.0 - Math.exp(-speed * dt));
    }

    private static double damp(double current, double target, double speed, float dt) {
        return current + (target - current) * (double) (1.0 - Math.exp(-speed * dt));
    }

    private static float outExpo(float t) {
        return t >= 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
    }

    private static float outQuint(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv * inv * inv;
    }

    private static int mulAlpha(int argb, float factor) {
        int a = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static float outElasticQuarter(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        double p = 0.45;
        return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t - p / 4.0) * (2.0 * Math.PI) / p) + 1.0);
    }

    private int getMarketLogicalW(int virtW, float mScale) {
        int bx = DRAWER_W + 16;
        int availW = Math.max(360, virtW - bx - 16);
        return Math.max(360, Math.round(availW / mScale));
    }

    private int getMarketDataHeight(MarketState state) {
        if (state.getSelectedItem() == null) {
            return 0;
        }
        if ("AH".equalsIgnoreCase(state.getMode())) {
            List<AhItemListing> bins = state.getLowestBins();
            if (bins == null || bins.isEmpty()) {
                return 114;
            }
            int rows = Math.max(5, Math.min(state.getAhDisplayLimit(), bins.size()));
            return 156 + rows * 26;
        } else {
            BazaarItemSummary bz = state.getBazaarSummary();
            if (bz == null) {
                return 114;
            }
            int avail = Math.max(bz.getBuyOrderBook().size(), bz.getSellOrderBook().size());
            int maxRows = Math.max(5, Math.min(state.getBzOrderLimit(), avail));
            return 190 + maxRows * 22;
        }
    }

    private int getMarketAlertHeight(int cw) {
        return cw >= 420 ? 140 : 178;
    }

    private int getMarketContentHeight(MarketState state, int cw) {
        if (state.getSelectedItem() == null) {
            return 92 + MK_EMPTY_H + 16;
        }
        return 92 + getMarketDataHeight(state) + MK_CARD_GAP + MK_CHART_H + MK_CARD_GAP + getMarketAlertHeight(cw) + 16;
    }

    private double getMarketMaxScroll(MarketState state, int cw, int logicalH) {
        int contentH = getMarketContentHeight(state, cw);
        return Math.max(0.0, contentH - (logicalH - 28));
    }

    private void updateActiveSectionFromScroll() {
        if (lockedFocusSectionIdx >= 0) {
            activeSectionIdx = lockedFocusSectionIdx;
            if (Math.abs(scrollCurrent - scrollTarget) < 6.0) {
                lockedFocusSectionIdx = -1;
            }
            return;
        }
        double focalPos = scrollCurrent + 110.0;
        int best = 0;
        if (drawerMaxScroll > 4.0 && scrollCurrent >= drawerMaxScroll - 4.0) {
            for (int i = SECTIONS.length - 1; i >= 0; i--) {
                if (sectionScreenTop[i] >= 0) {
                    activeSectionIdx = i;
                    return;
                }
            }
        }
        for (int i = SECTIONS.length - 1; i >= 0; i--) {
            if (focalPos >= sectionStartY[i]) {
                best = i;
                break;
            }
        }
        activeSectionIdx = best;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        syncNativeTextInput();
        long nowNs = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.016f : Math.min(0.05f, Math.max(0.001f, (nowNs - lastFrameNs) / 1_000_000_000.0f));
        lastFrameNs = nowNs;

        ModConfig cfg = ModConfig.getInstance();

        long nowMs = System.currentTimeMillis();
        long openElapsed = Math.max(0L, nowMs - openTime);

        float closeP = 0.0f;
        if (closingDrawer) {
            float closeFrac = Math.min(1.0f, Math.max(0L, nowMs - closeStartMs) / 280.0f);
            closeP = outQuint(closeFrac);
            if (closeFrac >= 1.0f) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
                return;
            }
        }

        int virtW = getVirtW();
        int virtH = getVirtH();
        float totalScale = getTotalRenderScale();
        double exactMouseX = toVirtX(mouseX);
        int vMouseX = (int) Math.round(exactMouseX);
        int vMouseY = (int) Math.round(toVirtY(mouseY));

        if (closingDrawer) {
            marketHudAlpha = marketAlphaAtClose * (1.0f - closeP);
        } else {
            float targetMarketAlpha = (cfg.showMarketHud && virtW - DRAWER_W > 360) ? 1.0f : 0.0f;
            marketHudAlpha = damp(marketHudAlpha, targetMarketAlpha, 12.0f, dt);
        }
        boolean isAhModeNow = "AH".equalsIgnoreCase(MarketState.getInstance().getMode());
        marketModeAnim = damp(marketModeAnim, isAhModeNow ? 0.0f : 1.0f, 13.0f, dt);
        marketModeContentAlpha = damp(marketModeContentAlpha, 1.0f, 11.0f, dt);

        scrollTarget = Math.max(0.0, Math.min(drawerMaxScroll, scrollTarget));
        scrollCurrent = damp(scrollCurrent, scrollTarget, 10.5, dt);
        updateActiveSectionFromScroll();

        if (cfg.showMarketHud && marketHudAlpha > 0.02f && !draggingMarketScrollbar) {
            float mScale = getMarketScale();
            int availH = Math.max(200, virtH - 32);
            int logicalW = getMarketLogicalW(virtW, mScale);
            int logicalH = Math.max(200, Math.round(availH / mScale));
            double mMax = getMarketMaxScroll(MarketState.getInstance(), logicalW - 36, logicalH);
            marketScrollTarget = Math.max(0.0, Math.min(mMax, marketScrollTarget));
            marketScrollOffset = damp(marketScrollOffset, marketScrollTarget, 10.5, dt);
        }

        this.extractTransparentBackground(graphics);

        if (draggingSlider != 0 && this.minecraft != null) {
            for (SliderHitbox s : drawerSliders) {
                if (s.id == draggingSlider) {
                    double frac = Math.max(0.0, Math.min(1.0, (exactMouseX - s.x) / (double) s.w));
                    applySliderFraction(s.id, frac, cfg);
                    break;
                }
            }
        }
        if (draggingPaletteMode != 0 && this.minecraft != null) {
            applyPaletteDrag(exactMouseX, toVirtY(mouseY), cfg);
        }

        if (dragToggleActive && this.minecraft != null) {
            handleToggleHit(vMouseX, vMouseY, false);
        }

        int contentViewTop = TOP_LINE_H + FIXED_SEARCH_H;
        int contentViewH = Math.max(100, virtH - contentViewTop);
        if (draggingDrawerScrollbar && this.minecraft != null && drawerMaxScroll > 0.5) {
            double frac = Math.max(0.0, Math.min(1.0, (vMouseY - contentViewTop) / (double) contentViewH));
            lockedFocusSectionIdx = -1;
            scrollTarget = frac * drawerMaxScroll;
            scrollCurrent = scrollTarget;
        }

        graphics.pose().pushMatrix();
        graphics.pose().scale(totalScale, totalScale);

        int sidebarX;
        int panelX;
        float sectionsAlpha;
        if (closingDrawer) {
            sidebarX = Math.round(-SIDEBAR_W * closeP);
            panelX = Math.round(SIDEBAR_W - DRAWER_W * closeP);
            sectionsAlpha = 1.0f;
        } else {
            float sidebarP = outQuint(Math.min(1.0f, openElapsed / 420.0f));
            float panelP = outQuint(Math.min(1.0f, openElapsed / 600.0f));
            sidebarX = Math.round(-SIDEBAR_W * (1.0f - sidebarP));
            panelX = Math.round(-PANEL_W + DRAWER_W * panelP);
            sectionsAlpha = outQuint(Math.max(0.0f, Math.min(1.0f, (openElapsed - 200.0f) / 500.0f)));
        }

        frameHoveredKey = "";
        frameHoverType = 0;

        try {
            if (marketHudAlpha > 0.02f && cfg.showMarketHud) {
                renderRightMarketHud(graphics, virtW, virtH, vMouseX, vMouseY);
            }

            renderDrawer(graphics, sidebarX, panelX, sectionsAlpha, openElapsed, virtH, vMouseX, vMouseY, dt);
        } finally {
            GuiBatchHelper.endScope(graphics);
            graphics.pose().popMatrix();
        }

        if (!closingDrawer) {
            if (!frameHoveredKey.isEmpty() && !frameHoveredKey.equals(lastHoveredKey)) {
                if (frameHoverType == 1) OsuSoundEngine.playSidebarHover();
                else if (frameHoverType == 2) OsuSoundEngine.playButtonHover();
                else OsuSoundEngine.playDefaultHover();
            }
            lastHoveredKey = frameHoveredKey;

            boolean fuzzyOpen = (focusedInput == 1) && !MarketState.getInstance().getFuzzySuggestions().isEmpty()
                    && cfg.showMarketHud && marketHudAlpha > 0.5f;
            if (fuzzyOpen && !lastFuzzyOpen) {
                OsuSoundEngine.playDropdownOpen();
            } else if (!fuzzyOpen && lastFuzzyOpen) {
                OsuSoundEngine.playDropdownClose();
            }
            lastFuzzyOpen = fuzzyOpen;
        }
    }

    private void markHover(String key, int type) {
        if (closingDrawer) return;
        this.frameHoveredKey = key;
        this.frameHoverType = type;
    }

    // ==========================================
    // 1:1 osu!lazer Settings Drawer (170px Sidebar + 400px Panel, 2-stage PopIn)
    // ==========================================
    private void renderDrawer(GuiGraphicsExtractor g, int sidebarX, int panelX, float sectionsAlpha,
                              long openElapsed, int virtH, int mouseX, int mouseY, float dt) {
        ModConfig cfg = ModConfig.getInstance();
        int topLineColor = 0xFF000000 | (cfg.drawerTopLineColor & 0x00FFFFFF);

        drawerToggles.clear();
        drawerButtons.clear();
        drawerSliders.clear();
        palSvSec = -1;
        namePalSvSec = -1;
        drawerInputs.clear();

        if (panelX + PANEL_W > 0) {
            GuiBatchHelper.beginScope(g);
            fillRect(g, panelX, 0, PANEL_W, virtH, OSU_BG4);
            fillRect(g, panelX, 0, PANEL_W, TOP_LINE_H, topLineColor);
            GuiBatchHelper.endScope(g);

            int py = TOP_LINE_H;
            double clampedScroll = Math.max(0.0, scrollCurrent);
            int scrollWhole = (int) Math.floor(clampedScroll);
            float scrollSub = (float) (clampedScroll - scrollWhole);

            int headerShiftWhole = Math.min(EXPANDABLE_HEADER_H, scrollWhole);
            float headerShiftFloat = (float) Math.min(EXPANDABLE_HEADER_H, clampedScroll);
            float hdrShiftSub = headerShiftFloat - headerShiftWhole;
            int stickyHeaderH = (int) Math.ceil((EXPANDABLE_HEADER_H - headerShiftFloat) + FIXED_SEARCH_H);
            float headerOverlayAlpha = Math.min(1.0f, (float) (clampedScroll / (double) EXPANDABLE_HEADER_H));

            int viewTop = py + stickyHeaderH;
            int viewBottom = virtH;
            int drawStartY = py + EXPANDABLE_HEADER_H + FIXED_SEARCH_H - scrollWhole;

            g.enableScissor(Math.max(0, panelX), py, Math.max(0, panelX + PANEL_W), virtH);
            g.pose().pushMatrix();
            g.pose().translate(0f, -scrollSub);
            int endY = renderDrawerSections(g, panelX, drawStartY, PANEL_W, viewTop, viewBottom, mouseX, mouseY, dt);
            g.pose().popMatrix();

            int footerSpace = Math.max(300, virtH - 420);
            int totalContentH = (endY - drawStartY) + footerSpace;
            int scrollViewportH = Math.max(100, virtH - py - EXPANDABLE_HEADER_H - FIXED_SEARCH_H);
            drawerMaxScroll = Math.max(0.0, totalContentH - scrollViewportH);

            if (drawerMaxScroll > 0.5) {
                int trackX = panelX + PANEL_W - 6;
                int barTop = py + stickyHeaderH + 4;
                int barH = Math.max(40, virtH - barTop - 8);
                int thumbH = Math.max(36, (int) Math.round(barH * (scrollViewportH / (double) Math.max(1, totalContentH))));
                float thumbY = barTop + (float) ((Math.min(drawerMaxScroll, clampedScroll) / drawerMaxScroll) * (barH - thumbH));
                OsuUiRenderer.drawPillF(g, trackX, thumbY, 4f, thumbH,
                        draggingDrawerScrollbar ? OSU_COLOUR1 : 0x889966FF);
            }

            // Stage 2: Smooth 500ms OutQuint fade-in reveal of Sections after 200ms initial delay
            if (sectionsAlpha < 0.995f && virtH > viewTop) {
                int veilA = Math.min(255, Math.max(0, Math.round((1.0f - sectionsAlpha) * 255)));
                fillRect(g, panelX, viewTop, PANEL_W, virtH - viewTop, (veilA << 24) | (OSU_BG4 & 0x00FFFFFF));
            }
            g.disableScissor();

            // Sticky Header & Search Box overlay (Stage 1: visible immediately with panel slide)
            g.enableScissor(Math.max(0, panelX), py, Math.max(0, panelX + PANEL_W), py + stickyHeaderH);
            g.pose().pushMatrix();
            g.pose().translate(0f, -hdrShiftSub);
            GuiBatchHelper.beginScope(g);
            int stickyBgAlpha = Math.round(headerOverlayAlpha * 245);
            if (stickyBgAlpha > 4) {
                fillRect(g, panelX, py, PANEL_W, stickyHeaderH + 2, (stickyBgAlpha << 24) | (OSU_BG5 & 0x00FFFFFF));
            } else {
                fillRect(g, panelX, py, PANEL_W, stickyHeaderH + 2, OSU_BG4);
            }

            int hdrY = py + 20 - headerShiftWhole;
            if (hdrY + 62 > py) {
                TorusFontRenderer.drawFittedString(g, Weight.ALTERNATE_REGULAR,
                        ModLang.tr("dttdrtaddons.drawer.settings"),
                        panelX + CONTENT_PAD_L, hdrY, PANEL_W - CONTENT_PAD_L - CONTENT_PAD_R, 42f,
                        40f, 20f, 40f, OSU_CONTENT1);
                TorusFontRenderer.drawFittedString(g, Weight.REGULAR,
                        ModLang.tr("dttdrtaddons.drawer.configure_da"),
                        panelX + CONTENT_PAD_L, hdrY + 42, PANEL_W - CONTENT_PAD_L - CONTENT_PAD_R, 20f,
                        18f, 12f, 18f, OSU_CONTENT2);
            }

            // Authentic osu!lazer SettingsSearchTextBox (BorderThickness = 0, smooth OsuCaret, character drop-in)
            int searchX = panelX + CONTENT_PAD_L;
            int searchY = py + EXPANDABLE_HEADER_H - headerShiftWhole + 6;
            int searchW = PANEL_W - CONTENT_PAD_L - CONTENT_PAD_R;
            int searchH = 38;
            boolean searchFocus = (focusedInput == 7);

            searchCaretAlpha = damp(searchCaretAlpha, searchFocus ? 1.0f : 0.0f, 14.0f, dt);
            searchPlaceholderAlpha = damp(searchPlaceholderAlpha, settingsSearchText.isEmpty() ? 1.0f : 0.0f, 18.0f, dt);

            int searchBg = lerpColor(0xFF201E26, OSU_BG5, searchCaretAlpha);
            OsuUiRenderer.drawRoundedRect(g, searchX, searchY, searchW, searchH, 5, searchBg);

            int textX = searchX + 12;
            int textY = searchY + 11;
            int maxTextW = searchW - 44;

            if (searchPlaceholderAlpha > 0.02f) {
                TorusFontRenderer.drawString(g, Weight.REGULAR,
                        TorusFontRenderer.trimToWidth(Weight.REGULAR,
                                ModLang.tr("dttdrtaddons.drawer.search_placeholder"), maxTextW, 16f),
                        textX, textY, 16f, mulAlpha(OSU_GRAY_TEXT, searchPlaceholderAlpha));
            }

            String visSearchText = settingsSearchText;
            if (TorusFontRenderer.measureWidth(Weight.REGULAR, visSearchText, 16f) > maxTextW) {
                int cut = 1;
                while (cut < visSearchText.length()
                        && TorusFontRenderer.measureWidth(Weight.REGULAR, visSearchText.substring(cut), 16f) > maxTextW) {
                    cut++;
                }
                visSearchText = visSearchText.substring(cut);
            }

            float textEndW = TorusFontRenderer.measureWidth(Weight.REGULAR, visSearchText, 16f);
            if (!visSearchText.isEmpty()) {
                long charElapsed = Math.max(0L, System.currentTimeMillis() - searchLastCharMs);
                if (charElapsed < 140L && searchLastLen == settingsSearchText.length() && visSearchText.length() > 0) {
                    float cp = outQuint(charElapsed / 140.0f);
                    String prefix = visSearchText.substring(0, visSearchText.length() - 1);
                    String lastCh = visSearchText.substring(visSearchText.length() - 1);
                    float prefixW = TorusFontRenderer.measureWidth(Weight.REGULAR, prefix, 16f);
                    if (!prefix.isEmpty()) {
                        TorusFontRenderer.drawString(g, Weight.REGULAR, prefix, textX, textY, 16f, OSU_CONTENT1);
                    }
                    int dropOffset = Math.round((1.0f - cp) * 6.0f);
                    TorusFontRenderer.drawString(g, Weight.REGULAR, lastCh, textX + prefixW, textY - dropOffset, 16f,
                            mulAlpha(OSU_CONTENT1, cp));
                } else {
                    TorusFontRenderer.drawString(g, Weight.REGULAR, visSearchText, textX, textY, 16f, OSU_CONTENT1);
                }
            }

            searchCaretX = damp(searchCaretX, textEndW, 35.0f, dt);
            if (searchCaretAlpha > 0.02f) {
                double phase = (System.currentTimeMillis() - openTime) / 1000.0 * Math.PI * 1.4;
                float pulse = searchFocus ? (0.56f + 0.18f * (float) Math.sin(phase)) : 0.65f;
                int caretXPos = textX + Math.round(searchCaretX);
                OsuUiRenderer.drawRoundedRect(g, caretXPos, searchY + 9, 3, 20, 1,
                        mulAlpha(OSU_CONTENT1, searchCaretAlpha * pulse));
            }

            OsuUiRenderer.drawSearchIcon(g, searchX + searchW - 28, searchY + 10, 18,
                    lerpColor(OSU_CONTENT2, OSU_CONTENT1, searchCaretAlpha));
            drawerInputs.add(new InputHitbox(-1, 7, searchX, searchY, searchW, searchH));
            GuiBatchHelper.endScope(g);
            g.pose().popMatrix();
            g.disableScissor();
        }

        renderDrawerSidebar(g, sidebarX, openElapsed, topLineColor, virtH, mouseX, mouseY, dt);
    }

    private void renderDrawerSidebar(GuiGraphicsExtractor g, int sidebarX, long openElapsed, int topLineColor,
                                     int virtH, int mouseX, int mouseY, float dt) {
        if (sidebarX + SIDEBAR_W <= 0) return;

        GuiBatchHelper.beginScope(g);
        fillRect(g, sidebarX, 0, SIDEBAR_W, TOP_LINE_H, topLineColor);
        fillRect(g, sidebarX, TOP_LINE_H, SIDEBAR_W, virtH - TOP_LINE_H, OSU_BG5);

        int btnY = TOP_LINE_H + 14;
        int btnH = 46;

        float targetIndY = btnY + activeSectionIdx * btnH + (btnH - 18) / 2.0f;
        sidebarIndicatorY = damp(sidebarIndicatorY, targetIndY, 18.0f, dt);

        for (int i = 0; i < SECTIONS.length; i++) {
            float itemAppear = closingDrawer ? 1.0f
                    : outQuint(Math.max(0.0f, Math.min(1.0f, (openElapsed - (200.0f + i * 45.0f)) / 500.0f)));
            if (itemAppear <= 0.01f) {
                btnY += btnH;
                continue;
            }

            String secIconId = SECTIONS[i];
            String secLabel = ModLang.tr(SECTION_LANG_KEYS[i]);
            boolean active = (i == activeSectionIdx);
            boolean hover = isInside(mouseX, mouseY, sidebarX, btnY, SIDEBAR_W, btnH);
            if (hover) markHover("sb_" + i, 1);

            float hAnim = hoverAnims.getOrDefault("sb_" + i, hover ? 1.0f : 0.0f);
            hAnim = damp(hAnim, hover ? 1.0f : 0.0f, 12.0f, dt);
            hoverAnims.put("sb_" + i, hAnim);

            if (hAnim > 0.01f) {
                int ha = Math.round(hAnim * 26 * itemAppear);
                fillRect(g, sidebarX, btnY, SIDEBAR_W, btnH, (ha << 24) | (OSU_LIGHT4 & 0x00FFFFFF));
            }

            float selAnim = hoverAnims.getOrDefault("sbsel_" + i, active ? 1.0f : 0.0f);
            selAnim = damp(selAnim, active ? 1.0f : 0.0f, 14.0f, dt);
            hoverAnims.put("sbsel_" + i, selAnim);

            sidebarIndHeightAnims[i] = damp(sidebarIndHeightAnims[i], active ? 18.0f : 4.0f, 16.0f, dt);
            if (selAnim > 0.03f) {
                int indH = Math.max(4, Math.round(sidebarIndHeightAnims[i]));
                int indA = Math.round(selAnim * 255 * itemAppear);
                OsuUiRenderer.drawPill(g, sidebarX + 9, btnY + (btnH - indH) / 2, 4, indH,
                        (indA << 24) | (OSU_COLOUR1 & 0x00FFFFFF));
            }

            int idleCol = hover ? OSU_LIGHT1 : OSU_LIGHT3;
            int iconColor = mulAlpha(lerpColor(idleCol, OSU_CONTENT1, selAnim), itemAppear);
            int iconSize = 20;
            OsuUiRenderer.drawSidebarIcon(g, secIconId, sidebarX + 22, btnY + (btnH - iconSize) / 2, iconSize, iconColor);
            TorusFontRenderer.drawFittedString(g, active ? Weight.SEMIBOLD : Weight.REGULAR, secLabel,
                    sidebarX + 52, btnY, SIDEBAR_W - 60, btnH, 16f, 10.5f, 17f, iconColor);

            btnY += btnH;
        }
        GuiBatchHelper.endScope(g);
    }

    private boolean matchesFilter(String... terms) {
        if (settingsSearchText == null || settingsSearchText.trim().isEmpty()) return true;
        String q = settingsSearchText.trim().toLowerCase(Locale.ROOT);
        for (String t : terms) {
            if (t == null) continue;
            if (t.toLowerCase(Locale.ROOT).contains(q)) return true;
            if (t.startsWith("dttdrtaddons.") && ModLang.tr(t).toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private int renderDrawerSections(GuiGraphicsExtractor g, int panelX, int startY, int panelW,
                                     int viewTop, int viewBottom, int mouseX, int mouseY, float dt) {
        ModConfig cfg = ModConfig.getInstance();
        int curY = startY;
        int baseOriginY = startY;
        int x = panelX + CONTENT_PAD_L;
        int w = panelW - CONTENT_PAD_L - CONTENT_PAD_R;

        // When searching, keep all sections bright (un-dimmed) so search hits are immediately interactive
        boolean searching = settingsSearchText != null && !settingsSearchText.trim().isEmpty();
        boolean hadPrevSection = false;

        // ==========================================
        // Section 0: Market
        // ==========================================
        boolean marketMatches = !searching || matchesFilter(
                "Market", "dttdrtaddons.section.market",
                "Show Market HUD", "dttdrtaddons.market.show_hud",
                "Market HUD Scale", "dttdrtaddons.market.hud_scale", "AH", "BZ", "Scale");
        if (marketMatches) {
            GuiBatchHelper.beginScope(g);
            sectionStartY[0] = curY - baseOriginY;
            sectionScreenTop[0] = curY;
            curY = drawSectionHeader(g, panelX, curY, panelW, ModLang.tr("dttdrtaddons.section.market"),
                    hadPrevSection, viewTop, viewBottom);
            hadPrevSection = true;
            if (matchesFilter("Market", "dttdrtaddons.section.market", "Show Market HUD", "dttdrtaddons.market.show_hud", "AH", "BZ")) {
                curY = drawFormCheckBox(g, 0, "show_market_hud", x, curY, w, ModLang.tr("dttdrtaddons.market.show_hud"), 0,
                        cfg.showMarketHud, v -> cfg.showMarketHud = v, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;
            }
            if (matchesFilter("Market", "dttdrtaddons.section.market", "Market HUD Scale", "dttdrtaddons.market.hud_scale", "Scale")) {
                curY = drawFormSliderBar(g, 0, 8, x, curY, w, ModLang.tr("dttdrtaddons.market.hud_scale"),
                        String.format("%.2fx", cfg.marketHudScale),
                        (cfg.marketHudScale - 0.60) / 0.90, (1.0 - 0.60) / 0.90,
                        Math.abs(cfg.marketHudScale - 1.0f) < 0.01f, viewTop, viewBottom, mouseX, mouseY, dt);
            }
            curY += 36;
            sectionScreenBot[0] = curY;
            GuiBatchHelper.endScope(g);
            drawSectionDimOverlay(g, 0, panelX, sectionScreenTop[0], panelW, sectionScreenBot[0] - sectionScreenTop[0],
                    searching, viewTop, viewBottom, mouseX, mouseY, dt);
        } else {
            sectionStartY[0] = 0;
            sectionScreenTop[0] = -1;
            sectionScreenBot[0] = -1;
        }

        // ==========================================
        // Section 1: Mining
        // ==========================================
        boolean miningMatches = !searching || matchesFilter(
                "Mining", "dttdrtaddons.section.mining",
                "Crystal Hollows Map", "dttdrtaddons.mining.ch_map_header", "dttdrtaddons.mining.ch_map_enable", "Waypoint", "HUD",
                "Temple", "Generate", "NEU", "Skytils", "Render",
                "dttdrtaddons.mining.temple_header", "dttdrtaddons.mining.temple_generate",
                "Gemstone ESP", "dttdrtaddons.mining.gemstone_header", "dttdrtaddons.mining.gemstone_enable",
                "dttdrtaddons.mining.gemstone_toggle_key", "dttdrtaddons.mining.gemstone_radius", "dttdrtaddons.mining.gemstone_interval",
                "Radius", "Interval", "Keybind", "Hotkey", "Toggle",
                "Jade", "Amber", "Topaz", "Sapphire", "Amethyst", "Jasper", "Ruby", "Opal", "Onyx", "Aquamarine", "Citrine", "Peridot",
                "dttdrtaddons.gemstone.jade", "dttdrtaddons.gemstone.amber", "dttdrtaddons.gemstone.topaz",
                "dttdrtaddons.gemstone.sapphire", "dttdrtaddons.gemstone.amethyst", "dttdrtaddons.gemstone.jasper",
                "dttdrtaddons.gemstone.ruby", "dttdrtaddons.gemstone.opal", "dttdrtaddons.gemstone.onyx",
                "dttdrtaddons.gemstone.aquamarine", "dttdrtaddons.gemstone.citrine", "dttdrtaddons.gemstone.peridot",
                "Corpse ESP", "Mineshaft", "Lapis", "Umber", "Tungsten", "LittleFoot",
                "dttdrtaddons.mining.mineshaft_header", "dttdrtaddons.mining.corpse_enable", "dttdrtaddons.mining.littlefoot_enable",
                "dttdrtaddons.corpse.lapis", "dttdrtaddons.corpse.umber", "dttdrtaddons.corpse.tungsten",
                "Profit", "NPC", "BZ", "Tracker", "Persistent", "Session", "Scatha", "Worm", "Cooldown",
                "dttdrtaddons.mining.profit_header", "dttdrtaddons.mining.profit_enable", "dttdrtaddons.mining.profit_display_mode",
                "dttdrtaddons.mining.scatha_header", "dttdrtaddons.mining.scatha_enable",
                "Party", "vote", "putoff", "dttdrtaddons.mining.party_header", "dttdrtaddons.mining.party_vote_enable");
        if (miningMatches) {
            GuiBatchHelper.beginScope(g);
            sectionStartY[1] = curY - baseOriginY;
            sectionScreenTop[1] = curY;
            curY = drawSectionHeader(g, panelX, curY, panelW, ModLang.tr("dttdrtaddons.section.mining"),
                    hadPrevSection, viewTop, viewBottom);
            hadPrevSection = true;

            if (matchesFilter("Mining", "dttdrtaddons.section.mining", "Crystal Hollows Map",
                    "dttdrtaddons.mining.ch_map_header", "dttdrtaddons.mining.ch_map_enable", "dttdrtaddons.mining.ch_map_edit_hud",
                    "Waypoint", "HUD")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.mining.ch_map_header"), viewTop, viewBottom);
                curY = drawFormCheckBox(g, 1, "ch_map_enabled", x, curY, w,
                        ModLang.tr("dttdrtaddons.mining.ch_map_enable"), 0,
                        cfg.crystalHollowsMapEnabled, v -> cfg.crystalHollowsMapEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;
                curY = drawSettingsButtonV2(g, 1, x, curY, w, 38, ModLang.tr("dttdrtaddons.mining.ch_map_edit_hud"),
                        OSU_COLOUR2, viewTop, viewBottom, mouseX, mouseY, () -> {
                            if (this.minecraft != null) this.minecraft.setScreen(new CrystalHollowsHudEditScreen(this));
                        });
                curY += ITEM_SPACING_V2;
                curY = renderDrawerChWaypointRow(g, 1, x, curY, w, viewTop, viewBottom, mouseX, mouseY);
                curY += 8;
            }

            if (matchesFilter("Mining", "dttdrtaddons.section.mining", "Temple", "Waypoint", "Generate", "NEU", "Skytils", "Render",
                    "dttdrtaddons.mining.temple_header", "dttdrtaddons.mining.temple_generate",
                    "dttdrtaddons.mining.temple_render_neu", "dttdrtaddons.mining.temple_render_skytils")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.mining.temple_header"), viewTop, viewBottom);
                BlockPos templeTarget = TempleWaypointFeature.getActiveTarget();
                if (isVisible(curY, 20, viewTop, viewBottom)) {
                    String statusLine = templeTarget != null
                            ? ModLang.tr("dttdrtaddons.mining.temple_active",
                                    templeTarget.getX(), templeTarget.getY(), templeTarget.getZ(),
                                    TempleWaypointFeature.OFFSET_X, TempleWaypointFeature.OFFSET_Y, TempleWaypointFeature.OFFSET_Z)
                            : ModLang.tr("dttdrtaddons.mining.temple_idle",
                                    TempleWaypointFeature.OFFSET_X, TempleWaypointFeature.OFFSET_Y, TempleWaypointFeature.OFFSET_Z);
                    TorusFontRenderer.drawFittedString(g, Weight.REGULAR, statusLine,
                            x + 4, curY, w - 8, 20f, 13f, 9.5f, 13.5f,
                            templeTarget != null ? COLOR_SUCCESS : OSU_CONTENT2);
                }
                curY += 22;

                int genW = Math.round(w * 0.68f);
                drawSettingsButtonV2(g, 1, x, curY, genW, 36, ModLang.tr("dttdrtaddons.mining.temple_generate"), OSU_COLOUR2,
                        viewTop, viewBottom, mouseX, mouseY, TempleWaypointFeature::generateFromPlayer);
                curY = drawSettingsButtonV2(g, 1, x + genW + 6, curY, w - genW - 6, 36, ModLang.tr("dttdrtaddons.common.clear"), 0xFFB91C1C,
                        viewTop, viewBottom, mouseX, mouseY, TempleWaypointFeature::clearWaypoint);
                curY += ITEM_SPACING_V2;

                String activeStyle = TempleWaypointFeature.getRenderStyle();
                int halfW = (w - 6) / 2;
                int neuBg = TempleWaypointFeature.STYLE_NEU.equals(activeStyle) ? OSU_COLOUR2 : OSU_BG3;
                int skytilsBg = TempleWaypointFeature.STYLE_SKYTILS.equals(activeStyle) ? OSU_COLOUR2 : OSU_BG3;
                drawSettingsButtonV2(g, 1, x, curY, halfW, 34, ModLang.tr("dttdrtaddons.mining.temple_render_neu"), neuBg,
                        viewTop, viewBottom, mouseX, mouseY,
                        () -> TempleWaypointFeature.setRenderStyle(TempleWaypointFeature.STYLE_NEU));
                curY = drawSettingsButtonV2(g, 1, x + halfW + 6, curY, w - halfW - 6, 34, ModLang.tr("dttdrtaddons.mining.temple_render_skytils"), skytilsBg,
                        viewTop, viewBottom, mouseX, mouseY,
                        () -> TempleWaypointFeature.setRenderStyle(TempleWaypointFeature.STYLE_SKYTILS));
                curY += 8;
            }

            if (matchesFilter("Mining", "dttdrtaddons.section.mining", "Gemstone ESP",
                    "dttdrtaddons.mining.gemstone_header", "dttdrtaddons.mining.gemstone_enable",
                    "dttdrtaddons.mining.gemstone_toggle_key", "dttdrtaddons.mining.gemstone_radius", "dttdrtaddons.mining.gemstone_interval",
                    "Radius", "Interval", "Keybind", "Hotkey", "Toggle",
                    "Jade", "Amber", "Topaz", "Sapphire", "Amethyst", "Jasper", "Ruby", "Opal", "Onyx", "Aquamarine", "Citrine", "Peridot",
                    "dttdrtaddons.gemstone.jade", "dttdrtaddons.gemstone.amber", "dttdrtaddons.gemstone.topaz",
                    "dttdrtaddons.gemstone.sapphire", "dttdrtaddons.gemstone.amethyst", "dttdrtaddons.gemstone.jasper",
                    "dttdrtaddons.gemstone.ruby", "dttdrtaddons.gemstone.opal", "dttdrtaddons.gemstone.onyx",
                    "dttdrtaddons.gemstone.aquamarine", "dttdrtaddons.gemstone.citrine", "dttdrtaddons.gemstone.peridot")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.mining.gemstone_header"), viewTop, viewBottom);
                if (matchesFilter("Enable Gemstone ESP", "Gemstone ESP", "Mining", "dttdrtaddons.section.mining",
                        "dttdrtaddons.mining.gemstone_header", "dttdrtaddons.mining.gemstone_enable")) {
                    curY = drawFormCheckBox(g, 1, "gem_esp_enabled", x, curY, w,
                            ModLang.tr("dttdrtaddons.mining.gemstone_enable"), 0,
                            cfg.gemstoneEspEnabled, v -> cfg.gemstoneEspEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Gemstone ESP", "Mining", "dttdrtaddons.section.mining",
                        "dttdrtaddons.mining.gemstone_header", "dttdrtaddons.mining.gemstone_toggle_key",
                        "Keybind", "Hotkey", "Toggle")) {
                    curY = drawKeybindRow(g, 1, 7, x, curY, w, ModLang.tr("dttdrtaddons.mining.gemstone_toggle_key"),
                            DttdrtAddons.getGemstoneEspKeyLabel(),
                            !DttdrtAddons.isGemstoneEspKeyUnbound(),
                            viewTop, viewBottom, mouseX, mouseY);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Scan Radius", "Radius", "Gemstone ESP", "Mining", "dttdrtaddons.section.mining",
                        "dttdrtaddons.mining.gemstone_header", "dttdrtaddons.mining.gemstone_radius")) {
                    curY = drawFormSliderBar(g, 1, 1, x, curY, w,
                            ModLang.tr("dttdrtaddons.mining.gemstone_radius"),
                            ModLang.tr("dttdrtaddons.mining.gemstone_radius_val", cfg.gemstoneEspRadius),
                            (cfg.gemstoneEspRadius - 1) / 499.0, 99.0 / 499.0,
                            cfg.gemstoneEspRadius == 100, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Scan Interval", "Interval", "Gemstone ESP", "Mining", "dttdrtaddons.section.mining",
                        "dttdrtaddons.mining.gemstone_header", "dttdrtaddons.mining.gemstone_interval")) {
                    curY = drawFormSliderBar(g, 1, 2, x, curY, w,
                            ModLang.tr("dttdrtaddons.mining.gemstone_interval"),
                            ModLang.tr("dttdrtaddons.mining.gemstone_interval_val", cfg.gemstoneEspScanIntervalMs),
                            (cfg.gemstoneEspScanIntervalMs - 100) / 4900.0, 0.0,
                            cfg.gemstoneEspScanIntervalMs == 100, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }

                // 12 Gemstone types vertically 1 per row with Perfect gemstone texture icons
                for (Gemstone gem : Gemstone.values()) {
                    String gemDisplay = gem.getDisplayName();
                    if (!matchesFilter("Gemstone ESP", "Mining", "dttdrtaddons.section.mining",
                            "dttdrtaddons.mining.gemstone_header", gem.label, gemDisplay)) continue;
                    boolean curVal = GemstoneEspFeature.isGemstoneEnabled(gem, cfg);
                    curY = drawFormCheckBox(g, 1, "gem_" + gem.name(), x, curY, w, gemDisplay,
                            0xFF000000 | gem.rgb, "PERFECT_" + gem.name() + "_GEM",
                            curVal, v -> {
                                if (GemstoneEspFeature.isGemstoneEnabled(gem, cfg) != v) {
                                    GemstoneEspFeature.toggleGemstone(gem, cfg);
                                }
                            }, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                curY += 8;
            }

            if (matchesFilter("Mining", "dttdrtaddons.section.mining", "Corpse ESP", "Mineshaft",
                    "Lapis", "Umber", "Tungsten", "LittleFoot",
                    "dttdrtaddons.mining.mineshaft_header", "dttdrtaddons.mining.corpse_enable", "dttdrtaddons.mining.littlefoot_enable",
                    "dttdrtaddons.corpse.lapis", "dttdrtaddons.corpse.umber", "dttdrtaddons.corpse.tungsten")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.mining.mineshaft_header"), viewTop, viewBottom);
                curY = drawFormCheckBox(g, 1, "corpse_esp_enabled", x, curY, w,
                        ModLang.tr("dttdrtaddons.mining.corpse_enable"), 0,
                        cfg.corpseEspEnabled, v -> cfg.corpseEspEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;

                for (CorpseType type : CorpseType.values()) {
                    boolean curVal = CorpseEspFeature.isCorpseEnabled(type, cfg);
                    curY = drawFormCheckBox(g, 1, "corpse_" + type.name(), x, curY, w, type.getDisplayName(), 0xFF000000 | type.rgb,
                            curVal, v -> {
                                if (CorpseEspFeature.isCorpseEnabled(type, cfg) != v) {
                                    CorpseEspFeature.toggleCorpse(type, cfg);
                                }
                            }, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                curY = drawFormCheckBox(g, 1, "littlefoot_esp_enabled", x, curY, w,
                        ModLang.tr("dttdrtaddons.mining.littlefoot_enable"),
                        0xFF000000 | LittleFootEspFeature.RGB, cfg.littleFootEspEnabled,
                        v -> cfg.littleFootEspEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += 8;
            }

            if (matchesFilter("Mining", "dttdrtaddons.section.mining", "Profit", "NPC", "BZ", "Tracker", "Persistent", "Session",
                    "dttdrtaddons.mining.profit_header", "dttdrtaddons.mining.profit_enable",
                    "dttdrtaddons.mining.profit_price_mode", "dttdrtaddons.mining.profit_display_mode",
                    "dttdrtaddons.mining.profit_edit_hud", "dttdrtaddons.mining.profit_reset_session")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.mining.profit_header"), viewTop, viewBottom);
                curY = drawFormCheckBox(g, 1, "mining_profit_enabled", x, curY, w,
                        ModLang.tr("dttdrtaddons.mining.profit_enable"), 0,
                        cfg.miningProfitEnabled, v -> cfg.miningProfitEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;
                int modeHalfW = (w - 6) / 2;
                drawSettingsButtonV2(g, 1, x, curY, modeHalfW, 36,
                        ModLang.tr("dttdrtaddons.mining.profit_price_mode",
                                MiningProfitFeature.getModeDisplayName(cfg.miningProfitPriceMode)),
                        OSU_BG3, viewTop, viewBottom, mouseX, mouseY, MiningProfitFeature::cyclePriceMode);
                curY = drawSettingsButtonV2(g, 1, x + modeHalfW + 6, curY, w - modeHalfW - 6, 36,
                        ModLang.tr("dttdrtaddons.mining.profit_display_mode",
                                MiningProfitFeature.getDisplayModeName(cfg.miningProfitDisplayMode)),
                        OSU_BG3, viewTop, viewBottom, mouseX, mouseY, MiningProfitFeature::cycleDisplayMode);
                curY += ITEM_SPACING_V2;
                int halfW = (w - 6) / 2;
                drawSettingsButtonV2(g, 1, x, curY, halfW, 36, ModLang.tr("dttdrtaddons.mining.profit_edit_hud"),
                        OSU_COLOUR2, viewTop, viewBottom, mouseX, mouseY, () -> {
                            if (this.minecraft != null) this.minecraft.setScreen(new MiningProfitHudEditScreen(this));
                        });
                curY = drawSettingsButtonV2(g, 1, x + halfW + 6, curY, w - halfW - 6, 36,
                        ModLang.tr("dttdrtaddons.mining.profit_reset_session"), 0xFFB91C1C, viewTop, viewBottom, mouseX, mouseY, () -> {
                            if (MiningProfitFeature.getInstance() != null) MiningProfitFeature.getInstance().resetSession();
                        });
                curY += 8;
            }

            if (matchesFilter("Mining", "dttdrtaddons.section.mining", "Scatha", "Worm", "Profit", "NPC", "BZ", "Persistent", "Session", "Cooldown",
                    "dttdrtaddons.mining.scatha_header", "dttdrtaddons.mining.scatha_enable",
                    "dttdrtaddons.mining.profit_price_mode", "dttdrtaddons.mining.profit_display_mode",
                    "dttdrtaddons.mining.scatha_edit_hud", "dttdrtaddons.mining.profit_reset_session")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.mining.scatha_header"), viewTop, viewBottom);
                curY = drawFormCheckBox(g, 1, "scatha_profit_enabled", x, curY, w,
                        ModLang.tr("dttdrtaddons.mining.scatha_enable"), 0,
                        cfg.scathaProfitEnabled, v -> cfg.scathaProfitEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;
                int modeHalfW = (w - 6) / 2;
                drawSettingsButtonV2(g, 1, x, curY, modeHalfW, 36,
                        ModLang.tr("dttdrtaddons.mining.profit_price_mode",
                                MiningProfitFeature.getModeDisplayName(cfg.scathaProfitPriceMode)),
                        OSU_BG3, viewTop, viewBottom, mouseX, mouseY, ScathaProfitFeature::cyclePriceMode);
                curY = drawSettingsButtonV2(g, 1, x + modeHalfW + 6, curY, w - modeHalfW - 6, 36,
                        ModLang.tr("dttdrtaddons.mining.profit_display_mode",
                                MiningProfitFeature.getDisplayModeName(cfg.scathaProfitDisplayMode)),
                        OSU_BG3, viewTop, viewBottom, mouseX, mouseY, ScathaProfitFeature::cycleDisplayMode);
                curY += ITEM_SPACING_V2;
                int halfW = (w - 6) / 2;
                drawSettingsButtonV2(g, 1, x, curY, halfW, 36, ModLang.tr("dttdrtaddons.mining.scatha_edit_hud"),
                        OSU_COLOUR2, viewTop, viewBottom, mouseX, mouseY, () -> {
                            if (this.minecraft != null) this.minecraft.setScreen(new MiningProfitHudEditScreen(this, true));
                        });
                curY = drawSettingsButtonV2(g, 1, x + halfW + 6, curY, w - halfW - 6, 36,
                        ModLang.tr("dttdrtaddons.mining.profit_reset_session"), 0xFFB91C1C, viewTop, viewBottom, mouseX, mouseY, () -> {
                            if (ScathaProfitFeature.getInstance() != null) ScathaProfitFeature.getInstance().resetSession();
                        });
                curY += 8;
            }

            if (matchesFilter("Mining", "dttdrtaddons.section.mining", "Nucleus", "Crystal", "Run", "Profit", "Bal", "Alloy", "NPC", "BZ", "Persistent", "Session",
                    "dttdrtaddons.mining.nucleus_header", "dttdrtaddons.mining.nucleus_enable",
                    "dttdrtaddons.mining.profit_price_mode", "dttdrtaddons.mining.profit_display_mode",
                    "dttdrtaddons.mining.nucleus_edit_hud", "dttdrtaddons.mining.profit_reset_session")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.mining.nucleus_header"), viewTop, viewBottom);
                curY = drawFormCheckBox(g, 1, "nucleus_profit_enabled", x, curY, w,
                        ModLang.tr("dttdrtaddons.mining.nucleus_enable"), 0,
                        cfg.nucleusProfitEnabled, v -> cfg.nucleusProfitEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;
                int modeHalfW = (w - 6) / 2;
                drawSettingsButtonV2(g, 1, x, curY, modeHalfW, 36,
                        ModLang.tr("dttdrtaddons.mining.profit_price_mode",
                                MiningProfitFeature.getModeDisplayName(cfg.nucleusProfitPriceMode)),
                        OSU_BG3, viewTop, viewBottom, mouseX, mouseY, NucleusProfitFeature::cyclePriceMode);
                curY = drawSettingsButtonV2(g, 1, x + modeHalfW + 6, curY, w - modeHalfW - 6, 36,
                        ModLang.tr("dttdrtaddons.mining.profit_display_mode",
                                MiningProfitFeature.getDisplayModeName(cfg.nucleusProfitDisplayMode)),
                        OSU_BG3, viewTop, viewBottom, mouseX, mouseY, NucleusProfitFeature::cycleDisplayMode);
                curY += ITEM_SPACING_V2;
                int halfW = (w - 6) / 2;
                drawSettingsButtonV2(g, 1, x, curY, halfW, 36, ModLang.tr("dttdrtaddons.mining.nucleus_edit_hud"),
                        OSU_COLOUR2, viewTop, viewBottom, mouseX, mouseY, () -> {
                            if (this.minecraft != null) this.minecraft.setScreen(
                                    new MiningProfitHudEditScreen(this, MiningProfitHudEditScreen.HudMode.NUCLEUS));
                        });
                curY = drawSettingsButtonV2(g, 1, x + halfW + 6, curY, w - halfW - 6, 36,
                        ModLang.tr("dttdrtaddons.mining.profit_reset_session"), 0xFFB91C1C, viewTop, viewBottom, mouseX, mouseY, () -> {
                            if (NucleusProfitFeature.getInstance() != null) NucleusProfitFeature.getInstance().resetSession();
                        });
                curY += 8;
            }

            if (matchesFilter("Mining", "dttdrtaddons.section.mining", "Party", "vote", "putoff",
                    "dttdrtaddons.mining.party_header", "dttdrtaddons.mining.party_vote_enable")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.mining.party_header"), viewTop, viewBottom);
                curY = drawFormCheckBox(g, 1, "party_vote_enabled", x, curY, w,
                        ModLang.tr("dttdrtaddons.mining.party_vote_enable"), 0,
                        cfg.partyVoteEnabled, v -> cfg.partyVoteEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
            }
            curY += 36;
            sectionScreenBot[1] = curY;
            GuiBatchHelper.endScope(g);
            drawSectionDimOverlay(g, 1, panelX, sectionScreenTop[1], panelW, sectionScreenBot[1] - sectionScreenTop[1],
                    searching, viewTop, viewBottom, mouseX, mouseY, dt);
        } else {
            sectionStartY[1] = curY - baseOriginY;
            sectionScreenTop[1] = -1;
            sectionScreenBot[1] = -1;
        }

        // ==========================================
        // Section 2: Combat
        // ==========================================
        boolean combatMatches = !searching || matchesFilter(
                "Combat", "dttdrtaddons.section.combat",
                "Damage Tracker", "Damage", "Tracker", "ArmorStand", "Armor Stand", "Chat",
                "dttdrtaddons.combat.damage_header", "dttdrtaddons.combat.damage_enable");
        if (combatMatches) {
            GuiBatchHelper.beginScope(g);
            sectionStartY[2] = curY - baseOriginY;
            sectionScreenTop[2] = curY;
            curY = drawSectionHeader(g, panelX, curY, panelW, ModLang.tr("dttdrtaddons.section.combat"),
                    hadPrevSection, viewTop, viewBottom);
            hadPrevSection = true;

            if (matchesFilter("Combat", "dttdrtaddons.section.combat", "Damage Tracker", "Damage", "Tracker",
                    "ArmorStand", "Armor Stand", "Chat", "dttdrtaddons.combat.damage_header", "dttdrtaddons.combat.damage_enable")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.combat.damage_header"), viewTop, viewBottom);
                curY = drawFormCheckBox(g, 2, "damage_tracker_enabled", x, curY, w,
                        ModLang.tr("dttdrtaddons.combat.damage_enable"), 0,
                        cfg.damageTrackerEnabled, v -> cfg.damageTrackerEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
            }

            curY += 36;
            sectionScreenBot[2] = curY;
            GuiBatchHelper.endScope(g);
            drawSectionDimOverlay(g, 2, panelX, sectionScreenTop[2], panelW, sectionScreenBot[2] - sectionScreenTop[2],
                    searching, viewTop, viewBottom, mouseX, mouseY, dt);
        } else {
            sectionStartY[2] = curY - baseOriginY;
            sectionScreenTop[2] = -1;
            sectionScreenBot[2] = -1;
        }

        // ==========================================
        // Section 3: Custom
        // ==========================================
        boolean customMatches = !searching || matchesFilter(
                "Custom", "dttdrtaddons.section.custom", "Top", "Accent", "Line", "Color",
                "Disable", "All", "Reset", "Panic", "Off", "dttdrtaddons.custom.disable_all_btn",
                "Background", "Image", "Market", "Scale", "Offset", "Darken",
                "Display", "Opacity", "Sound", "Audio", "SFX", "Effect", "Update", "Auto", "dttdrtaddons.custom.auto_update",
                "Keybind", "Shortcut", "Hotkey", "Menu", "Settings", "Vote", "Party", "!vote", "Yes", "No",
                "Storage", "EC", "BP", "Ender", "Chest", "Backpack", "Preview", "Overlay", "Tooltip", "Outline", "Search",
                "Mineshaft", "Streak", "Announce", "Message",
                "Nametag", "Distance", "Palette", "Color", "Other",
                "Name", "Nickname", "Chroma", "Rainbow", "Bold", "Italic", "Underline", "Shader", "Speed", "Size", "Saturation", "Direction",
                "FreeCam", "Camera", "Spectator", "Fly", "HUD",
                "dttdrtaddons.custom.top_line_header", "dttdrtaddons.custom.bg_header", "dttdrtaddons.custom.display_header",
                "dttdrtaddons.custom.keybind_header", "dttdrtaddons.custom.streak_header", "dttdrtaddons.custom.storage_header",
                "dttdrtaddons.custom.nametag_header", "dttdrtaddons.custom.nametag_color",
                "dttdrtaddons.custom.name_header", "dttdrtaddons.custom.chroma_header", "dttdrtaddons.custom.freecam_header");
        if (customMatches) {
            GuiBatchHelper.beginScope(g);
            sectionStartY[3] = curY - baseOriginY;
            sectionScreenTop[3] = curY;
            curY = drawSectionHeader(g, panelX, curY, panelW, ModLang.tr("dttdrtaddons.section.custom"),
                    hadPrevSection, viewTop, viewBottom);
            hadPrevSection = true;

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Disable", "All", "Reset", "Panic", "Off", "Keybind",
                    "dttdrtaddons.custom.disable_all_btn")) {
                curY = drawSettingsButtonV2(g, 3, x, curY, w, 36,
                        ModLang.tr("dttdrtaddons.custom.disable_all_btn"),
                        0xFFB91C1C, viewTop, viewBottom, mouseX, mouseY, () -> {
                            FeatureManager.disableAllFeatures();
                            OsuSoundEngine.playToggle(false);
                        });
                curY += 10;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Top", "Accent", "Line", "Color", "dttdrtaddons.custom.top_line_header")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.top_line_header"), viewTop, viewBottom);
                curY = renderTopLineColorRow(g, 3, x, curY, w, viewTop, viewBottom, mouseX, mouseY);
                curY += 10;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Background", "Image", "Market", "Scale", "Offset", "Darken",
                    "dttdrtaddons.custom.bg_header", "dttdrtaddons.custom.bg_darken", "dttdrtaddons.custom.bg_scale",
                    "dttdrtaddons.custom.bg_offset_x", "dttdrtaddons.custom.bg_offset_y")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.bg_header"), viewTop, viewBottom);
                if (isVisible(curY, 20, viewTop, viewBottom)) {
                    TorusFontRenderer.drawFittedString(g, Weight.REGULAR, BackgroundTextureManager.getStatusText(),
                            x + 4, curY, w - 8, 20f, 13f, 9.5f, 13.5f, OSU_CONTENT2);
                }
                curY += 22;

                int btnW3 = (w - 8) / 3;
                drawSettingsButtonV2(g, 3, x, curY, btnW3, 36, ModLang.tr("dttdrtaddons.custom.bg_select"), OSU_COLOUR2,
                        viewTop, viewBottom, mouseX, mouseY, BackgroundTextureManager::openFileExplorerAsync);
                drawSettingsButtonV2(g, 3, x + btnW3 + 4, curY, btnW3, 36, ModLang.tr("dttdrtaddons.common.clear"), 0xFFB91C1C,
                        viewTop, viewBottom, mouseX, mouseY, BackgroundTextureManager::clearBackground);
                curY = drawSettingsButtonV2(g, 3, x + (btnW3 + 4) * 2, curY, w - (btnW3 + 4) * 2, 36,
                        ModLang.tr("dttdrtaddons.custom.bg_reset_pos"), OSU_BG3, viewTop, viewBottom, mouseX, mouseY, () -> {
                    cfg.bgScale = 1.0f;
                    cfg.bgOffsetX = 0;
                    cfg.bgOffsetY = 0;
                    ModConfig.save();
                });
                curY += ITEM_SPACING_V2;

                int pct = (int) Math.round((cfg.backgroundDarkness / 255.0) * 100.0);
                curY = drawFormSliderBar(g, 3, 3, x, curY, w, ModLang.tr("dttdrtaddons.custom.bg_darken"), pct + "%",
                        cfg.backgroundDarkness / 242.0, 165.0 / 242.0,
                        cfg.backgroundDarkness == 165, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;
                curY = drawFormSliderBar(g, 3, 5, x, curY, w, ModLang.tr("dttdrtaddons.custom.bg_scale"), String.format("%.2fx", cfg.bgScale),
                        (cfg.bgScale - 0.25) / 2.75, 0.75 / 2.75,
                        Math.abs(cfg.bgScale - 1.0f) < 0.01f, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;
                curY = drawFormSliderBar(g, 3, 6, x, curY, w, ModLang.tr("dttdrtaddons.custom.bg_offset_x"), cfg.bgOffsetX + " px",
                        (cfg.bgOffsetX + 300) / 600.0, 0.5,
                        cfg.bgOffsetX == 0, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;
                curY = drawFormSliderBar(g, 3, 7, x, curY, w, ModLang.tr("dttdrtaddons.custom.bg_offset_y"), cfg.bgOffsetY + " px",
                        (cfg.bgOffsetY + 300) / 600.0, 0.5,
                        cfg.bgOffsetY == 0, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += 8;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Display", "Opacity", "Sound", "Audio", "SFX", "Effect",
                    "Update", "Auto", "dttdrtaddons.custom.display_header", "dttdrtaddons.custom.ui_sounds",
                    "dttdrtaddons.custom.auto_update", "dttdrtaddons.custom.panel_opacity")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.display_header"), viewTop, viewBottom);
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Display", "Sound", "Audio", "SFX", "Effect",
                        "dttdrtaddons.custom.display_header", "dttdrtaddons.custom.ui_sounds")) {
                    curY = drawFormCheckBox(g, 3, "ui_sounds_enabled", x, curY, w, ModLang.tr("dttdrtaddons.custom.ui_sounds"), 0,
                            cfg.uiSoundsEnabled, v -> cfg.uiSoundsEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Update", "Auto", "dttdrtaddons.custom.auto_update")) {
                    curY = drawFormCheckBox(g, 3, "auto_update_enabled", x, curY, w, ModLang.tr("dttdrtaddons.custom.auto_update"), 0,
                            cfg.autoUpdateEnabled, v -> cfg.autoUpdateEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Display", "Opacity", "Market Card Opacity",
                        "dttdrtaddons.custom.display_header", "dttdrtaddons.custom.panel_opacity")) {
                    int opPct = (int) Math.round((cfg.uiPanelOpacity / 255.0) * 100.0);
                    curY = drawFormSliderBar(g, 3, 4, x, curY, w, ModLang.tr("dttdrtaddons.custom.panel_opacity"), opPct + "%",
                            (cfg.uiPanelOpacity - 40) / 205.0, (215 - 40) / 205.0,
                            cfg.uiPanelOpacity == 215, viewTop, viewBottom, mouseX, mouseY, dt);
                }
                curY += 10;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Keybind", "Shortcut", "Hotkey", "Menu", "Settings", "Open", "Quick", "Vote", "Party", "!vote", "Yes", "No",
                    "dttdrtaddons.custom.keybind_header", "dttdrtaddons.custom.key_open_menu",
                    "dttdrtaddons.custom.key_quick_vote", "dttdrtaddons.custom.key_quick_vote_yes", "dttdrtaddons.custom.key_quick_vote_no")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.keybind_header"), viewTop, viewBottom);
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Keybind", "Shortcut", "Hotkey", "Menu", "Settings", "Open", "dttdrtaddons.custom.key_open_menu")) {
                    curY = drawKeybindRow(g, 3, 1, x, curY, w, ModLang.tr("dttdrtaddons.custom.key_open_menu"),
                            DttdrtAddons.getOpenMenuKeyLabel(),
                            !DttdrtAddons.isOpenMenuKeyDefault(),
                            viewTop, viewBottom, mouseX, mouseY);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Keybind", "Shortcut", "Hotkey", "Quick", "Party", "Vote", "!vote", "dttdrtaddons.custom.key_quick_vote")) {
                    curY = drawKeybindRow(g, 3, 2, x, curY, w, ModLang.tr("dttdrtaddons.custom.key_quick_vote"),
                            DttdrtAddons.getQuickVoteKeyLabel(),
                            !DttdrtAddons.isQuickVoteKeyUnbound(),
                            viewTop, viewBottom, mouseX, mouseY);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Keybind", "Shortcut", "Hotkey", "Quick", "Party", "Vote", "Yes", "y", "dttdrtaddons.custom.key_quick_vote_yes")) {
                    curY = drawKeybindRow(g, 3, 3, x, curY, w, ModLang.tr("dttdrtaddons.custom.key_quick_vote_yes"),
                            DttdrtAddons.getQuickVoteYesKeyLabel(),
                            !DttdrtAddons.isQuickVoteYesKeyUnbound(),
                            viewTop, viewBottom, mouseX, mouseY);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Keybind", "Shortcut", "Hotkey", "Quick", "Party", "Vote", "No", "n", "dttdrtaddons.custom.key_quick_vote_no")) {
                    curY = drawKeybindRow(g, 3, 4, x, curY, w, ModLang.tr("dttdrtaddons.custom.key_quick_vote_no"),
                            DttdrtAddons.getQuickVoteNoKeyLabel(),
                            !DttdrtAddons.isQuickVoteNoKeyUnbound(),
                            viewTop, viewBottom, mouseX, mouseY);
                }
                curY += 10;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Mineshaft", "Streak", "Vote", "!vote", "Announce", "Message",
                    "dttdrtaddons.custom.streak_header", "dttdrtaddons.custom.streak_enable",
                    "dttdrtaddons.custom.streak_message", "dttdrtaddons.custom.streak_reset")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.streak_header"), viewTop, viewBottom);
                curY = drawFormCheckBox(g, 3, "mineshaft_streak_enabled", x, curY, w, ModLang.tr("dttdrtaddons.custom.streak_enable"), 0,
                        cfg.mineshaftStreakEnabled, v -> cfg.mineshaftStreakEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += ITEM_SPACING_V2;

                if (isVisible(curY, 20, viewTop, viewBottom)) {
                    TorusFontRenderer.drawFittedString(g, Weight.REGULAR, ModLang.tr("dttdrtaddons.custom.streak_message"),
                            x + 2, curY, w - 4, 20f, 13f, 9.5f, 13.5f, OSU_CONTENT2);
                }
                curY += 20;

                int rstMsgW = 70;
                int msgBoxW = w - rstMsgW - 6;
                drawDrawerTextBox(g, 3, 8, x, curY, msgBoxW, 34, cfg.mineshaftStreakMessage,
                        ModConfig.DEFAULT_STREAK_MESSAGE, viewTop, viewBottom, mouseX, mouseY);
                curY = drawSettingsButtonV2(g, 3, x + msgBoxW + 6, curY, rstMsgW, 34, ModLang.tr("dttdrtaddons.common.reset"), OSU_BG3,
                        viewTop, viewBottom, mouseX, mouseY, () -> {
                            cfg.mineshaftStreakMessage = ModConfig.DEFAULT_STREAK_MESSAGE;
                            ModConfig.save();
                        });
                curY += ITEM_SPACING_V2;

                if (isVisible(curY, 30, viewTop, viewBottom)) {
                    TorusFontRenderer.drawFittedString(g, Weight.SEMIBOLD,
                            ModLang.tr("dttdrtaddons.custom.streak_current", cfg.mineshaftStreak),
                            x + 2, curY, w - 120, 30f, 14f, 10f, 14.5f, COLOR_GOLD);
                }
                int rstCntW = 112;
                curY = drawSettingsButtonV2(g, 3, x + w - rstCntW, curY, rstCntW, 30, ModLang.tr("dttdrtaddons.custom.streak_reset"), 0xFFB91C1C,
                        viewTop, viewBottom, mouseX, mouseY, () -> {
                            cfg.mineshaftStreak = 0;
                            ModConfig.save();
                        });
                curY += 10;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Storage", "EC", "BP", "Ender", "Chest", "Backpack",
                    "Preview", "Overlay", "Tooltip", "Outline", "Search", "Scroll",
                    "dttdrtaddons.custom.storage_header", "dttdrtaddons.custom.storage_enable",
                    "dttdrtaddons.custom.storage_highlight", "dttdrtaddons.custom.storage_tooltips", "dttdrtaddons.custom.storage_outline")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.storage_header"), viewTop, viewBottom);
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Storage", "EC", "BP", "Ender", "Chest", "Backpack", "Preview", "Overlay", "dttdrtaddons.custom.storage_enable")) {
                    curY = drawFormCheckBox(g, 3, "storage_overlay_enabled", x, curY, w, ModLang.tr("dttdrtaddons.custom.storage_enable"), 0,
                            cfg.storageOverlayEnabled, v -> cfg.storageOverlayEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Storage", "EC", "BP", "Search", "Highlight", "dttdrtaddons.custom.storage_highlight")) {
                    curY = drawFormCheckBox(g, 3, "storage_overlay_highlight", x, curY, w, ModLang.tr("dttdrtaddons.custom.storage_highlight"), 0,
                            cfg.storageOverlayHighlightSearch, v -> cfg.storageOverlayHighlightSearch = v, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Storage", "EC", "BP", "Tooltip", "Inactive", "dttdrtaddons.custom.storage_tooltips")) {
                    curY = drawFormCheckBox(g, 3, "storage_overlay_tooltips", x, curY, w, ModLang.tr("dttdrtaddons.custom.storage_tooltips"), 0,
                            cfg.storageOverlayShowInactiveTooltips, v -> cfg.storageOverlayShowInactiveTooltips = v, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Storage", "EC", "BP", "Outline", "Active", "dttdrtaddons.custom.storage_outline")) {
                    curY = drawFormCheckBox(g, 3, "storage_overlay_outline", x, curY, w, ModLang.tr("dttdrtaddons.custom.storage_outline"), 0,
                            cfg.storageOverlayOutlineActivePage, v -> cfg.storageOverlayOutlineActivePage = v, viewTop, viewBottom, mouseX, mouseY, dt);
                }
                curY += 10;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Nametag", "Distance", "Palette", "Color", "Other",
                    "dttdrtaddons.custom.nametag_header", "dttdrtaddons.custom.nametag_enable",
                    "dttdrtaddons.custom.nametag_distance", "dttdrtaddons.custom.nametag_color")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.nametag_header"), viewTop, viewBottom);
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Nametag", "dttdrtaddons.custom.nametag_enable")) {
                    curY = drawFormCheckBox(g, 3, "nametag_enabled", x, curY, w, ModLang.tr("dttdrtaddons.custom.nametag_enable"), 0,
                            cfg.nametagEnabled, v -> cfg.nametagEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Nametag", "Distance", "dttdrtaddons.custom.nametag_distance")) {
                    curY = drawFormCheckBox(g, 3, "nametag_show_distance", x, curY, w, ModLang.tr("dttdrtaddons.custom.nametag_distance"), 0,
                            cfg.nametagShowDistance, v -> cfg.nametagShowDistance = v, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Nametag", "Palette", "Color", "dttdrtaddons.custom.nametag_color")) {
                    curY = renderNametagColorPalette(g, 3, x, curY, w, viewTop, viewBottom, mouseX, mouseY);
                }
                curY += 10;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Name", "Nickname", "Color", "Chroma", "Rainbow",
                    "Bold", "Italic", "Underline", "Visual",
                    "dttdrtaddons.custom.name_header", "dttdrtaddons.custom.name_enable",
                    "dttdrtaddons.custom.name_input_label", "dttdrtaddons.custom.name_visual_title")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.name_header"), viewTop, viewBottom);
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Name", "Nickname", "dttdrtaddons.custom.name_enable")) {
                    curY = drawFormCheckBox(g, 3, "custom_name_enabled", x, curY, w, ModLang.tr("dttdrtaddons.custom.name_enable"), 0,
                            cfg.customNameEnabled, v -> cfg.customNameEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Name", "Nickname", "Input",
                        "dttdrtaddons.custom.name_input_label")) {
                    if (isVisible(curY, 20, viewTop, viewBottom)) {
                        TorusFontRenderer.drawFittedString(g, Weight.REGULAR, ModLang.tr("dttdrtaddons.custom.name_input_label"),
                                x + 2, curY, w - 4, 20f, 13f, 9.5f, 13.5f, OSU_CONTENT2);
                    }
                    curY += 20;

                    int rstNameW = 70;
                    int nameBoxW = w - rstNameW - 6;
                    drawDrawerTextBox(g, 3, 9, x, curY, nameBoxW, 34, cfg.customNameText,
                            ModLang.tr("dttdrtaddons.custom.name_placeholder"), viewTop, viewBottom, mouseX, mouseY);
                    curY = drawSettingsButtonV2(g, 3, x + nameBoxW + 6, curY, rstNameW, 34, ModLang.tr("dttdrtaddons.common.reset"), OSU_BG3,
                            viewTop, viewBottom, mouseX, mouseY, () -> {
                                cfg.customNameText = "";
                                ModConfig.save();
                            });
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Name", "Nickname", "Color", "Chroma", "Rainbow",
                        "Bold", "Italic", "Underline", "Visual", "dttdrtaddons.custom.name_visual_title")) {
                    curY = renderCustomNameVisualCard(g, 3, x, curY, w, viewTop, viewBottom, mouseX, mouseY);
                }
                curY += 10;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Chroma", "Rainbow", "Shader", "Speed", "Size",
                    "Saturation", "Direction", "§z",
                    "dttdrtaddons.custom.chroma_header", "dttdrtaddons.custom.chroma_enable",
                    "dttdrtaddons.custom.chroma_speed", "dttdrtaddons.custom.chroma_size",
                    "dttdrtaddons.custom.chroma_saturation", "dttdrtaddons.custom.chroma_direction")) {
                curY = renderChromaSection(g, 3, x, curY, w, viewTop, viewBottom, mouseX, mouseY, dt);
                curY += 10;
            }

            if (matchesFilter("Custom", "dttdrtaddons.section.custom", "FreeCam", "Camera", "Spectator", "Fly", "Speed", "HUD", "Keybind", "Hotkey", "Toggle",
                    "dttdrtaddons.custom.freecam_header", "dttdrtaddons.custom.freecam_key",
                    "dttdrtaddons.custom.freecam_speed", "dttdrtaddons.custom.freecam_hud")) {
                curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.freecam_header"), viewTop, viewBottom);
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "FreeCam", "Camera", "Spectator", "Keybind", "Hotkey", "Toggle", "dttdrtaddons.custom.freecam_key")) {
                    curY = drawKeybindRow(g, 3, 6, x, curY, w, ModLang.tr("dttdrtaddons.custom.freecam_key"),
                            DttdrtAddons.getFreecamKeyLabel(),
                            !DttdrtAddons.isFreecamKeyUnbound(),
                            viewTop, viewBottom, mouseX, mouseY);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "FreeCam", "Camera", "Fly", "Speed", "dttdrtaddons.custom.freecam_speed")) {
                    curY = drawFormSliderBar(g, 3, 9, x, curY, w, ModLang.tr("dttdrtaddons.custom.freecam_speed"),
                            String.format(Locale.ROOT, "%.2fx", cfg.freecamSpeed),
                            (cfg.freecamSpeed - 0.25) / 5.75, (1.50 - 0.25) / 5.75,
                            Math.abs(cfg.freecamSpeed - 1.50f) < 0.01f, viewTop, viewBottom, mouseX, mouseY, dt);
                    curY += ITEM_SPACING_V2;
                }
                if (matchesFilter("Custom", "dttdrtaddons.section.custom", "FreeCam", "Camera", "HUD", "dttdrtaddons.custom.freecam_hud")) {
                    curY = drawFormCheckBox(g, 3, "freecam_show_hud", x, curY, w, ModLang.tr("dttdrtaddons.custom.freecam_hud"), 0,
                            cfg.freecamShowHud, v -> cfg.freecamShowHud = v, viewTop, viewBottom, mouseX, mouseY, dt);
                }
            }

            curY += 36;
            sectionScreenBot[3] = curY;
            GuiBatchHelper.endScope(g);
            drawSectionDimOverlay(g, 3, panelX, sectionScreenTop[3], panelW, sectionScreenBot[3] - sectionScreenTop[3],
                    searching, viewTop, viewBottom, mouseX, mouseY, dt);
        } else {
            sectionStartY[3] = curY - baseOriginY;
            sectionScreenTop[3] = -1;
            sectionScreenBot[3] = -1;
        }

        // ==========================================
        // Section 4 (Always Last, Below Custom): Debug
        // ==========================================
        boolean debugMatches = !searching || matchesFilter(
                "Debug", "dttdrtaddons.section.debug", "Log", "Logs", "Copy", "Clipboard", "Error", "FreeCam", "Console", "Diagnostic",
                "dttdrtaddons.debug.copy_mod_logs", "dttdrtaddons.debug.copy_error_summary", "dttdrtaddons.debug.clear_logs");
        if (debugMatches) {
            GuiBatchHelper.beginScope(g);
            sectionStartY[4] = curY - baseOriginY;
            sectionScreenTop[4] = curY;
            curY = drawSectionHeader(g, panelX, curY, panelW, ModLang.tr("dttdrtaddons.section.debug"),
                    hadPrevSection, viewTop, viewBottom);
            hadPrevSection = true;

            curY = renderDebugSectionBody(g, 4, x, curY, w, viewTop, viewBottom, mouseX, mouseY);
            curY += 36;
            sectionScreenBot[4] = curY;
            GuiBatchHelper.endScope(g);
            drawSectionDimOverlay(g, 4, panelX, sectionScreenTop[4], panelW, sectionScreenBot[4] - sectionScreenTop[4],
                    searching, viewTop, viewBottom, mouseX, mouseY, dt);
        } else {
            sectionStartY[4] = curY - baseOriginY;
            sectionScreenTop[4] = -1;
            sectionScreenBot[4] = -1;
            debugLogBoxW = 0;
            debugLogBoxH = 0;
        }

        if (searching && !hadPrevSection) {
            TorusFontRenderer.drawFittedString(g, Weight.REGULAR, ModLang.tr("dttdrtaddons.drawer.no_matches"),
                    x + 8, curY + 12, w - 16, 26f, 15f, 10f, 16f, OSU_GRAY_TEXT);
            curY += 48;
        }

        return curY;
    }

    private int renderDebugSectionBody(GuiGraphicsExtractor g, int secIdx, int x, int curY, int w,
                                       int viewTop, int viewBottom, int mouseX, int mouseY) {
        ModConfig cfg = ModConfig.getInstance();
        boolean fcActive = FreeCamFeature.isActive();

        // Status summary card
        int cardH = 50;
        if (isVisible(curY, cardH, viewTop, viewBottom)) {
            OsuUiRenderer.drawRoundedRect(g, x, curY, w, cardH, 6, OSU_BG5);
            OsuUiRenderer.drawRoundedBorder(g, x, curY, w, cardH, 6, OSU_LIGHT4);
            String fcText = "FreeCam: " + ModLang.tr(fcActive ? "dttdrtaddons.debug.active" : "dttdrtaddons.debug.inactive")
                    + "  |  Key: " + DttdrtAddons.getFreecamKeyLabel()
                    + "  |  Speed: " + String.format(Locale.ROOT, "%.2fx", cfg.freecamSpeed);
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD,
                    TorusFontRenderer.trimToWidth(Weight.SEMIBOLD, fcText, w - 20, 12.5f),
                    x + 10, curY + 8, 12.5f, fcActive ? COLOR_SUCCESS : OSU_CONTENT1);
            String hint = System.currentTimeMillis() < debugFeedbackUntilMs && !debugFeedbackText.isEmpty()
                    ? debugFeedbackText
                    : ("Mod Log Entries: " + ModLogManager.getEntriesSnapshot().size() + " (scroll box below)");
            int hintCol = System.currentTimeMillis() < debugFeedbackUntilMs ? COLOR_SUCCESS : OSU_GRAY_TEXT;
            TorusFontRenderer.drawString(g, Weight.REGULAR,
                    TorusFontRenderer.trimToWidth(Weight.REGULAR, hint, w - 20, 11.5f),
                    x + 10, curY + 28, 11.5f, hintCol);
        }
        curY += cardH + 8;

        // Action Buttons Row 1: Copy Mod Logs / Copy MC latest.log
        int halfW = (w - 6) / 2;
        drawSettingsButtonV2(g, secIdx, x, curY, halfW, 34, ModLang.tr("dttdrtaddons.debug.copy_mod_logs"), OSU_COLOUR2, viewTop, viewBottom, mouseX, mouseY, () -> {
            String report = ModLogManager.buildDiagnosticReport();
            if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
                this.minecraft.keyboardHandler.setClipboard(report);
            }
            debugFeedbackText = ModLang.tr("dttdrtaddons.debug.copied_mod");
            debugFeedbackUntilMs = System.currentTimeMillis() + 3000L;
        });
        curY = drawSettingsButtonV2(g, secIdx, x + halfW + 6, curY, w - halfW - 6, 34, ModLang.tr("dttdrtaddons.debug.copy_error_summary"), OSU_BG3, viewTop, viewBottom, mouseX, mouseY, () -> {
            String snippet = ModLogManager.buildLatestMcLogSnippet();
            if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
                this.minecraft.keyboardHandler.setClipboard(snippet);
            }
            debugFeedbackText = ModLang.tr("dttdrtaddons.debug.copied_error");
            debugFeedbackUntilMs = System.currentTimeMillis() + 3000L;
        });
        curY += ITEM_SPACING_V2;

        // Action Button Row 2: Clear Logs
        curY = drawSettingsButtonV2(g, secIdx, x, curY, w, 30, ModLang.tr("dttdrtaddons.debug.clear_logs"), 0xFFB91C1C, viewTop, viewBottom, mouseX, mouseY, () -> {
            ModLogManager.clear();
            debugLogScroll = 0.0;
            debugLogAutoFollow = true;
            debugFeedbackText = ModLang.tr("dttdrtaddons.debug.cleared");
            debugFeedbackUntilMs = System.currentTimeMillis() + 2000L;
        });
        curY += 8;

        // Scrollable Mod Log Terminal Box
        int boxH = 260;
        debugLogBoxX = x;
        debugLogBoxY = curY;
        debugLogBoxW = w;
        debugLogBoxH = boxH;

        long logVer = ModLogManager.getVersionCounter();
        int maxTextW = Math.max(80, w - 24);
        if (logVer != lastDebugLogVersion) {
            lastDebugLogVersion = logVer;
            cachedWrappedDebugLines.clear();
            for (String rawLine : ModLogManager.getEntriesSnapshot()) {
                wrapTextInto(cachedWrappedDebugLines, rawLine, maxTextW, 11.5f);
            }
            if (cachedWrappedDebugLines.isEmpty()) {
                cachedWrappedDebugLines.add("(No mod logs recorded yet)");
            }
        }

        int lineH = 15;
        int padY = 8;
        int visibleLinesH = boxH - padY * 2;
        int totalLogH = cachedWrappedDebugLines.size() * lineH;
        debugLogMaxScroll = Math.max(0.0, totalLogH - visibleLinesH);
        if (debugLogAutoFollow) {
            debugLogScroll = debugLogMaxScroll;
        } else {
            debugLogScroll = Math.max(0.0, Math.min(debugLogMaxScroll, debugLogScroll));
        }

        if (isVisible(curY, boxH, viewTop, viewBottom)) {
            OsuUiRenderer.drawRoundedRect(g, x, curY, w, boxH, 6, OSU_BG5);
            OsuUiRenderer.drawRoundedBorder(g, x, curY, w, boxH, 6, OSU_LIGHT4);

            int innerTop = curY + padY;
            int innerBot = curY + boxH - padY;
            int scrollOff = (int) Math.round(debugLogScroll);
            for (int i = 0; i < cachedWrappedDebugLines.size(); i++) {
                int ly = innerTop + i * lineH - scrollOff;
                if (ly < innerTop - 1) continue;
                if (ly + 12 > innerBot) break;
                String line = cachedWrappedDebugLines.get(i);
                int col = OSU_CONTENT2;
                if (line.contains("[ERROR") || line.contains("blocked") || line.contains("failed")) {
                    col = COLOR_DANGER;
                } else if (line.contains("[WARN")) {
                    col = COLOR_GOLD;
                } else if (line.contains("Enabled")) {
                    col = COLOR_SUCCESS;
                }
                TorusFontRenderer.drawString(g, Weight.REGULAR, line, x + 10, ly, 11.5f, col);
            }

            if (debugLogMaxScroll > 0.5) {
                int trackX = x + w - 7;
                int trackTop = curY + 6;
                int trackH = boxH - 12;
                int thumbH = Math.max(24, (int) Math.round(trackH * (visibleLinesH / (double) Math.max(1, totalLogH))));
                float thumbY = trackTop + (float) ((debugLogScroll / debugLogMaxScroll) * (trackH - thumbH));
                OsuUiRenderer.drawPillF(g, trackX, thumbY, 3f, thumbH, 0x99DFD9F2);
            }
        }
        curY += boxH;
        return curY;
    }

    private static void wrapTextInto(List<String> out, String text, int maxW, float size) {
        if (text == null || text.isEmpty()) {
            out.add("");
            return;
        }
        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = start;
            while (end < len && TorusFontRenderer.measureWidth(Weight.REGULAR, text.substring(start, end + 1), size) <= maxW) {
                end++;
            }
            if (end == start) {
                end++;
            }
            out.add(text.substring(start, end));
            start = end;
        }
    }

    private void drawSectionDimOverlay(GuiGraphicsExtractor g, int secIdx, int panelX, int topY, int panelW, int height,
                                       boolean searching, int viewTop, int viewBottom, int mouseX, int mouseY, float dt) {
        boolean isCurrent = searching || (secIdx == activeSectionIdx);
        boolean hovered = !isCurrent && isInside(mouseX, mouseY, panelX, Math.max(viewTop, topY), panelW, Math.max(0, Math.min(viewBottom, topY + height) - Math.max(viewTop, topY)));
        float targetDim = isCurrent ? 0.0f : (hovered ? 0.50f : 0.80f);
        sectionDimAnims[secIdx] = damp(sectionDimAnims[secIdx], targetDim, 14.0f, dt);

        if (sectionDimAnims[secIdx] > 0.01f && isVisible(topY, height, viewTop, viewBottom)) {
            int alpha = Math.min(245, Math.max(0, Math.round(sectionDimAnims[secIdx] * 255)));
            int startDraw = topY + (secIdx > 0 ? 2 : 0);
            int drawH = height - (secIdx > 0 ? 2 : 0);
            if (drawH > 0) {
                fillRect(g, panelX, startDraw, panelW, drawH, (alpha << 24) | (OSU_BG5 & 0x00FFFFFF));
            }
        }
    }

    // ==========================================
    // 1:1 osu!lazer Section / Control Builders (NO revert-to-default, NO triangles)
    // ==========================================
    private static boolean isVisible(int y, int h, int viewTop, int viewBottom) {
        return y + h >= viewTop - 2 && y <= viewBottom + 2;
    }

    private int drawSectionHeader(GuiGraphicsExtractor g, int panelX, int y, int panelW,
                                  String title, boolean withSeparator, int viewTop, int viewBottom) {
        int sepH = withSeparator ? 2 : 0;
        int totalH = sepH + 24 + 36;
        if (isVisible(y, totalH, viewTop, viewBottom)) {
            if (withSeparator) {
                fillRect(g, panelX, y, panelW, 2, OSU_BG6);
            }
            TorusFontRenderer.drawFittedString(g, Weight.ALTERNATE_REGULAR, title,
                    panelX + CONTENT_PAD_L, y + sepH + 16, panelW - CONTENT_PAD_L - CONTENT_PAD_R, 34f,
                    30f, 16f, 30f, OSU_CONTENT1);
        }
        return y + sepH + 24 + 36;
    }

    private int drawSubsectionHeader(GuiGraphicsExtractor g, int x, int y, String title, int viewTop, int viewBottom) {
        int h = 43;
        if (isVisible(y, h, viewTop, viewBottom)) {
            TorusFontRenderer.drawFittedString(g, Weight.SEMIBOLD, title,
                    x + 5, y + 6, PANEL_W - CONTENT_PAD_L - CONTENT_PAD_R - 10, 28f,
                    20f, 13f, 20f, OSU_CONTENT1);
        }
        return y + h + ITEM_SPACING_V2;
    }

    private float computeSwitchWidthFraction(String key, boolean value) {
        long now = System.currentTimeMillis();
        Boolean prev = toggleLastVal.get(key);
        if (prev == null) {
            toggleLastVal.put(key, value);
            toggleChangeMs.put(key, now - 500L);
            return value ? 1.0f : 0.75f;
        }
        if (prev != value) {
            toggleLastVal.put(key, value);
            toggleChangeMs.put(key, now);
        }
        long elapsed = Math.max(0L, now - toggleChangeMs.getOrDefault(key, 0L));
        if (value) {
            // Unchecked (0.75) -> Checked (1.00): 200ms OutElasticQuarter outward stretch from center!
            float p = Math.min(1.0f, elapsed / 200.0f);
            return 0.75f + 0.25f * outElasticQuarter(p);
        } else {
            // Checked (1.00) -> Unchecked (0.75): 120ms OutExpo contract back to center
            float p = Math.min(1.0f, elapsed / 120.0f);
            return 1.0f - 0.25f * outExpo(p);
        }
    }

    private int drawFormCheckBox(GuiGraphicsExtractor g, int secIdx, String key, int x, int y, int w,
                                 String caption, int swatchColor, boolean value, Consumer<Boolean> setter,
                                 int viewTop, int viewBottom, int mouseX, int mouseY, float dt) {
        return drawFormCheckBox(g, secIdx, key, x, y, w, caption, swatchColor, null, value, setter,
                viewTop, viewBottom, mouseX, mouseY, dt);
    }

    private int drawFormCheckBox(GuiGraphicsExtractor g, int secIdx, String key, int x, int y, int w,
                                 String caption, int swatchColor, String iconSkyblockId,
                                 boolean value, Consumer<Boolean> setter,
                                 int viewTop, int viewBottom, int mouseX, int mouseY, float dt) {
        int h = 38;
        float fillAnim = toggleFillAnims.getOrDefault(key, value ? 1.0f : 0.0f);
        fillAnim = damp(fillAnim, value ? 1.0f : 0.0f, 16.0f, dt);
        toggleFillAnims.put(key, fillAnim);

        boolean canInteract = (secIdx == activeSectionIdx);
        boolean hover = canInteract && isVisible(y, h, viewTop, viewBottom) && isInside(mouseX, mouseY, x, y, w, h);
        if (hover) markHover(key, 3);
        float hAnim = hoverAnims.getOrDefault(key, hover ? 1.0f : 0.0f);
        hAnim = damp(hAnim, hover ? 1.0f : 0.0f, 16.0f, dt);
        hoverAnims.put(key, hAnim);

        if (isVisible(y, h, viewTop, viewBottom)) {
            int bg = lerpColor(OSU_BG4_DARK, OSU_DARK4, hAnim);
            OsuUiRenderer.drawRoundedRect(g, x, y, w, h, 5, bg);
            if (hAnim > 0.02f) {
                int ba = Math.round(hAnim * 255);
                OsuUiRenderer.drawRoundedBorder(g, x, y, w, h, 5, (ba << 24) | (OSU_LIGHT4 & 0x00FFFFFF));
            }

            boolean hasPdIcon = iconSkyblockId != null && !iconSkyblockId.isEmpty()
                    && PackDisablerIconBridge.tryResolveFromPackDisabler(iconSkyblockId) != null;
            int textX = x + 9;
            if (hasPdIcon) {
                textX = x + 30;
            } else if (swatchColor != 0) {
                OsuUiRenderer.drawRoundedRect(g, x + 9, y + (h - 14) / 2, 14, 14, 3, swatchColor);
                textX = x + 29;
            }
            TorusFontRenderer.drawFittedString(g, Weight.REGULAR, caption,
                    textX, y, w - (textX - x) - 70, h, 14f, 10f, 15.5f, OSU_CONTENT1);

            // SwitchButton centered outward stretch: 42px (0.75f) <-> 56px (1.0f) anchored at center!
            int slotW = 56;
            int swH = 16;
            float widthFrac = computeSwitchWidthFraction(key, value);
            int curW = Math.max(16, Math.round(slotW * widthFrac));
            int centerX = x + w - 9 - slotW / 2;
            int swX = centerX - curW / 2;
            int swY = y + (h - swH) / 2;
            int borderCol = lerpColor(value ? 0xFF8A5AE8 : OSU_LIGHT4, OSU_COLOUR1, Math.max(hAnim, fillAnim * 0.8f));
            OsuUiRenderer.drawOsuSwitch(g, swX, swY, curW, swH, borderCol, OSU_COLOUR1, fillAnim);

            drawerToggles.add(new ToggleHitbox(secIdx, key, x, y, w, h, value, setter));
            if (hasPdIcon) {
                GuiBatchHelper.endScope(g);
                PackDisablerIconBridge.drawItemIcon(g, iconSkyblockId, x + 8, y + (h - 16) / 2, 16);
                GuiBatchHelper.beginScope(g);
            }
        }
        return y + h;
    }

    private int drawFormSliderBar(GuiGraphicsExtractor g, int secIdx, int sliderId, int x, int y, int w,
                                  String caption, String valueText, double fraction, double defaultFraction,
                                  boolean isDefaultVal, int viewTop, int viewBottom, int mouseX, int mouseY, float dt) {
        int h = 50;
        boolean canInteract = (secIdx == activeSectionIdx);
        boolean hover = canInteract && isVisible(y, h, viewTop, viewBottom) && (isInside(mouseX, mouseY, x, y, w, h) || draggingSlider == sliderId);
        if (hover && draggingSlider == 0) markHover("sl_" + sliderId, 3);
        float hAnim = hoverAnims.getOrDefault("sl_" + sliderId, hover ? 1.0f : 0.0f);
        hAnim = damp(hAnim, hover ? 1.0f : 0.0f, 16.0f, dt);
        hoverAnims.put("sl_" + sliderId, hAnim);

        if (isVisible(y, h, viewTop, viewBottom)) {
            int bg = lerpColor(OSU_BG4_DARK, (draggingSlider == sliderId ? OSU_DARK5 : OSU_DARK4), hAnim);
            OsuUiRenderer.drawRoundedRect(g, x, y, w, h, 5, bg);
            if (hAnim > 0.02f || draggingSlider == sliderId) {
                int borderCol = (draggingSlider == sliderId) ? OSU_COLOUR1 : OSU_LIGHT4;
                int ba = Math.round(Math.max(hAnim, draggingSlider == sliderId ? 1.0f : 0.0f) * 255);
                OsuUiRenderer.drawRoundedBorder(g, x, y, w, h, 5, (ba << 24) | (borderCol & 0x00FFFFFF));
            }

            int leftW = (w - 14) / 2 - 8;
            TorusFontRenderer.drawFittedString(g, Weight.REGULAR, caption,
                    x + 9, y + 5, leftW, 18f, 14f, 9.5f, 14.5f, OSU_CONTENT2);
            TorusFontRenderer.drawFittedString(g, Weight.SEMIBOLD, valueText,
                    x + 9, y + 24, leftW, 20f, 15f, 10f, 15.5f, OSU_CONTENT1);

            int sx = x + w / 2;
            int sw = w / 2 - 5;
            int sy = y + 5;
            int sh = 40;
            int nubW = 10;
            float fClamped = (float) Math.max(0.0, Math.min(1.0, fraction));
            int rangeW = Math.max(1, sw - nubW);
            float nubCenterXf = sx + nubW * 0.5f + fClamped * rangeW;

            OsuUiRenderer.drawRoundedRect(g, sx, sy, sw, sh, 5, OSU_BG5);
            float fillWf = nubCenterXf - sx;
            if (fillWf >= 4.0f) {
                OsuUiRenderer.drawRoundedRectF(g, sx, sy, fillWf, sh, 5.0f, OSU_LIGHT4);
            }

            double defClamped = Math.max(0.0, Math.min(1.0, defaultFraction));
            float defXf = sx + nubW * 0.5f + (float) (defClamped * rangeW) - 2.0f;
            int defH = isDefaultVal ? 28 : 6;
            OsuUiRenderer.drawPillF(g, defXf, sy + (sh - defH) * 0.5f, 4.0f, defH, 0x66DFD9F2);

            int nubColor = (hover || draggingSlider == sliderId) ? OSU_COLOUR1 : 0xFF8A5AE8;
            OsuUiRenderer.drawPillF(g, nubCenterXf - nubW * 0.5f, sy, nubW, sh, nubColor);

            drawerSliders.add(new SliderHitbox(secIdx, sliderId, sx, sy, sw, sh));
        }
        return y + h;
    }

    private int drawSettingsButtonV2(GuiGraphicsExtractor g, int secIdx, int x, int y, int w, int h, String label, int baseBg,
                                     int viewTop, int viewBottom, int mouseX, int mouseY, Runnable action) {
        if (isVisible(y, h, viewTop, viewBottom)) {
            boolean hover = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, x, y, w, h);
            if (hover) markHover("btn_" + secIdx + "_" + label + "_" + y, 2);
            int bg = hover ? brighten(baseBg) : baseBg;
            OsuUiRenderer.drawRoundedRect(g, x, y, w, h, 5, bg);
            TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD, label,
                    x + w * 0.5f, y, w - 16, h, 15f, 9.5f, Math.min(17.5f, h * 0.46f), OSU_CONTENT1);
            drawerButtons.add(new ButtonHitbox(secIdx, x, y, w, h, action));
        }
        return y + h;
    }

    private int drawKeybindRow(GuiGraphicsExtractor g, int secIdx, int keybindId, int x, int y, int w,
                               String label, String keyLabel, boolean isNonDefault,
                               int viewTop, int viewBottom, int mouseX, int mouseY) {
        int h = 34;
        if (isVisible(y, h, viewTop, viewBottom)) {
            boolean capturing = (capturingKeybindId == keybindId);
            int accent = ModConfig.getInstance().drawerTopLineColor | 0xFF000000;
            if (isNonDefault || capturing) {
                OsuUiRenderer.drawRoundedRect(g, x, y + (h - 14) / 2, 4, 14, 2, accent);
            }
            int btnW = Math.min(168, Math.max(126, w / 3 + 12));
            int btnX = x + w - btnW;
            int btnY = y + 3;
            int btnH = 28;
            int textX = (isNonDefault || capturing) ? x + 10 : x;
            TorusFontRenderer.drawFittedString(g, Weight.REGULAR, label,
                    textX, y, btnX - textX - 8, h, 15f, 10f, 15.5f, OSU_CONTENT1);

            boolean hover = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
            if (hover) markHover("keybind_" + keybindId, 2);

            String noneLabel = ModLang.tr("dttdrtaddons.common.none");
            String btnText = capturing
                    ? ModLang.tr("dttdrtaddons.common.press_key")
                    : (keyLabel == null || keyLabel.isEmpty() || "None".equalsIgnoreCase(keyLabel) ? noneLabel : keyLabel);
            int bg = capturing ? OSU_BG5 : (hover ? brighten(OSU_BG3) : OSU_BG3);
            if (capturing) {
                OsuUiRenderer.drawRoundedRect(g, btnX - 1, btnY - 1, btnW + 2, btnH + 2, 6, accent);
            }
            OsuUiRenderer.drawRoundedRect(g, btnX, btnY, btnW, btnH, 5, bg);
            boolean isUnbound = !capturing && (keyLabel == null || keyLabel.isEmpty() || "None".equalsIgnoreCase(keyLabel));
            int txtColor = capturing ? accent : (isUnbound ? OSU_CONTENT2 : OSU_CONTENT1);
            TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD, btnText,
                    btnX + btnW * 0.5f, btnY, btnW - 12, btnH, 13.5f, 8.5f, 14.5f, txtColor);

            final boolean wasCapturing = capturing;
            drawerButtons.add(new ButtonHitbox(secIdx, btnX, btnY, btnW, btnH, () -> {
                focusedInput = 0;
                capturingKeybindId = wasCapturing ? 0 : keybindId;
                syncNativeTextInput();
            }));
        }
        return y + h;
    }

    private void drawDrawerTextBox(GuiGraphicsExtractor g, int secIdx, int inputId, int x, int y, int w, int h,
                                   String text, String placeholder, int viewTop, int viewBottom, int mouseX, int mouseY) {
        if (!isVisible(y, h, viewTop, viewBottom)) return;
        boolean focused = (focusedInput == inputId);
        boolean hover = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, x, y, w, h);
        int bg = focused ? OSU_BG5 : (hover ? OSU_DARK4 : OSU_BG4_DARK);
        OsuUiRenderer.drawRoundedRect(g, x, y, w, h, 5, bg);
        int accent = ModConfig.getInstance().drawerTopLineColor | 0xFF000000;
        if (focused || hover) {
            OsuUiRenderer.drawRoundedBorder(g, x, y, w, h, 5, focused ? accent : OSU_LIGHT4);
        }
        String safeText = text != null ? text : "";
        boolean showPlaceholder = safeText.isEmpty() && !focused;
        String shown = showPlaceholder ? placeholder : safeText;
        String trimmed = trimTailToFit(Weight.REGULAR, shown, w - 20, 14f);
        TorusFontRenderer.drawString(g, Weight.REGULAR, trimmed, x + 10, y + (h - 14) * 0.5f, 14f,
                showPlaceholder ? OSU_CONTENT2 : OSU_CONTENT1);
        if (focused && ((System.currentTimeMillis() / 500L) % 2L == 0L)) {
            float cx = x + 10 + TorusFontRenderer.measureWidth(Weight.REGULAR, trimmed, 14f);
            fillRect(g, Math.round(cx), y + 7, 1, h - 14, accent);
        }
        drawerInputs.add(new InputHitbox(secIdx, inputId, x, y, w, h));
    }

    private static String trimTailToFit(Weight w, String s, int maxW, float size) {
        if (s == null || s.isEmpty()) return "";
        if (TorusFontRenderer.measureWidth(w, s, size) <= maxW) return s;
        int start = 0;
        while (start < s.length() && TorusFontRenderer.measureWidth(w, s.substring(start), size) > maxW) {
            start++;
        }
        return s.substring(start);
    }

    private int renderTopLineColorRow(GuiGraphicsExtractor g, int secIdx, int x, int y, int w, int viewTop, int viewBottom, int mouseX, int mouseY) {
        int h = 34;
        if (isVisible(y, h, viewTop, viewBottom)) {
            ModConfig cfg = ModConfig.getInstance();
            int swatchW = (w - (TOP_LINE_PRESETS.length - 1) * 6) / TOP_LINE_PRESETS.length;
            for (int i = 0; i < TOP_LINE_PRESETS.length; i++) {
                int color = TOP_LINE_PRESETS[i];
                int sx = x + i * (swatchW + 6);
                boolean active = (cfg.drawerTopLineColor & 0x00FFFFFF) == (color & 0x00FFFFFF);
                boolean hover = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, sx, y, swatchW, 28);
                if (hover) markHover("swatch_" + i, 3);
                if (active || hover) {
                    OsuUiRenderer.drawRoundedRect(g, sx - 2, y - 2, swatchW + 4, 32, 6, active ? OSU_CONTENT1 : OSU_LIGHT4);
                }
                OsuUiRenderer.drawRoundedRect(g, sx, y, swatchW, 28, 5, color);
                drawerButtons.add(new ButtonHitbox(secIdx, sx, y, swatchW, 28, () -> {
                    cfg.drawerTopLineColor = color;
                    ModConfig.save();
                }));
            }
        }
        return y + h;
    }

    private void syncPaletteHsvFromColor(int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.nametagPaletteHue = hsb[0];
        this.nametagPaletteSat = hsb[1];
        this.nametagPaletteVal = hsb[2];
    }

    private void syncCustomNamePaletteHsvFromColor(int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.customNamePaletteHue = hsb[0];
        this.customNamePaletteSat = hsb[1];
        this.customNamePaletteVal = hsb[2];
    }

    private void applyPaletteDrag(double mx, double my, ModConfig cfg) {
        if (draggingPaletteMode == 1 && palSvW > 0 && palSvH > 0) {
            nametagPaletteSat = (float) Math.max(0.0, Math.min(1.0, (mx - palSvX) / (double) palSvW));
            nametagPaletteVal = (float) Math.max(0.0, Math.min(1.0, 1.0 - (my - palSvY) / (double) palSvH));
            cfg.nametagColor = Color.HSBtoRGB(nametagPaletteHue, nametagPaletteSat, nametagPaletteVal) | 0xFF000000;
        } else if (draggingPaletteMode == 2 && palHueH > 0) {
            nametagPaletteHue = (float) Math.max(0.0, Math.min(0.9999, (my - palHueY) / (double) palHueH));
            cfg.nametagColor = Color.HSBtoRGB(nametagPaletteHue, nametagPaletteSat, nametagPaletteVal) | 0xFF000000;
        } else if (draggingPaletteMode == 3 && namePalSvW > 0 && namePalSvH > 0) {
            customNamePaletteSat = (float) Math.max(0.0, Math.min(1.0, (mx - namePalSvX) / (double) namePalSvW));
            customNamePaletteVal = (float) Math.max(0.0, Math.min(1.0, 1.0 - (my - namePalSvY) / (double) namePalSvH));
            cfg.customNameColorMode = "SOLID";
            cfg.customNameColor = Color.HSBtoRGB(customNamePaletteHue, customNamePaletteSat, customNamePaletteVal) | 0xFF000000;
        } else if (draggingPaletteMode == 4 && namePalHueH > 0) {
            customNamePaletteHue = (float) Math.max(0.0, Math.min(0.9999, (my - namePalHueY) / (double) namePalHueH));
            cfg.customNameColorMode = "SOLID";
            cfg.customNameColor = Color.HSBtoRGB(customNamePaletteHue, customNamePaletteSat, customNamePaletteVal) | 0xFF000000;
        }
    }

    private void drawHsvPaletteWidget(GuiGraphicsExtractor g, int innerX, int palY, int svW, int palH,
                                      int hueX, int hueW, float hue, float sat, float val, int curColor) {
        int cols = 28;
        int rows = 16;
        float cellW = svW / (float) cols;
        float cellH = palH / (float) rows;
        for (int row = 0; row < rows; row++) {
            float v = 1.0f - (row + 0.5f) / rows;
            float cy = palY + row * cellH;
            for (int col = 0; col < cols; col++) {
                float s = (col + 0.5f) / cols;
                int cellCol = Color.HSBtoRGB(hue, s, v) | 0xFF000000;
                OsuUiRenderer.drawSolidRectF(g, innerX + col * cellW, cy, cellW + 0.6f, cellH + 0.6f, cellCol);
            }
        }
        OsuUiRenderer.drawRoundedBorder(g, innerX - 1, palY - 1, svW + 2, palH + 2, 3, OSU_BG6);

        int dotX = innerX + Math.round(sat * svW) - 5;
        int dotY = palY + Math.round((1.0f - val) * palH) - 5;
        OsuUiRenderer.drawCircle(g, dotX - 1, dotY - 1, 12, 0xFF000000);
        OsuUiRenderer.drawCircle(g, dotX, dotY, 10, 0xFFFFFFFF);
        OsuUiRenderer.drawCircle(g, dotX + 2, dotY + 2, 6, curColor);

        int hueSlices = 36;
        float sliceH = palH / (float) hueSlices;
        for (int i = 0; i < hueSlices; i++) {
            float h = (i + 0.5f) / hueSlices;
            int hCol = Color.HSBtoRGB(h, 1.0f, 1.0f) | 0xFF000000;
            OsuUiRenderer.drawSolidRectF(g, hueX, palY + i * sliceH, hueW, sliceH + 0.6f, hCol);
        }
        OsuUiRenderer.drawRoundedBorder(g, hueX - 1, palY - 1, hueW + 2, palH + 2, 3, OSU_BG6);

        int hueBarY = palY + Math.round(hue * (palH - 4));
        OsuUiRenderer.drawRoundedRect(g, hueX - 2, hueBarY - 1, hueW + 4, 6, 2, 0xFFFFFFFF);
        OsuUiRenderer.drawRoundedRect(g, hueX, hueBarY, hueW, 4, 1, Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000);
    }

    private int renderNametagColorPalette(GuiGraphicsExtractor g, int secIdx, int x, int y, int w,
                                          int viewTop, int viewBottom, int mouseX, int mouseY) {
        int cardH = 186;
        if (isVisible(y, cardH, viewTop, viewBottom)) {
            ModConfig cfg = ModConfig.getInstance();
            int curColor = cfg.nametagColor | 0xFF000000;

            OsuUiRenderer.drawRoundedRect(g, x, y, w, cardH, 6, OSU_BG4_DARK);

            int pad = 10;
            int innerX = x + pad;
            int innerW = w - pad * 2;

            // Row 1: Title + Preview Badge
            String title = ModLang.tr("dttdrtaddons.custom.nametag_color");
            String hexStr = String.format(Locale.ROOT, "#%06X", curColor & 0x00FFFFFF);
            TorusFontRenderer.drawFittedString(g, Weight.REGULAR, title,
                    innerX, y + 8, innerW - 140, 20f, 13.5f, 9.5f, 14f, OSU_CONTENT2);

            int previewW = 132;
            int previewX = x + w - pad - previewW;
            OsuUiRenderer.drawRoundedRect(g, previewX, y + 6, previewW, 24, 4, OSU_BG5);
            OsuUiRenderer.drawCircle(g, previewX + 6, y + 12, 12, curColor);
            TorusFontRenderer.drawFittedString(g, Weight.SEMIBOLD, hexStr,
                    previewX + 24, y + 8, previewW - 28, 20f, 12.5f, 9f, 13f, curColor);

            // Row 2: Preset Color Swatches
            int swatchY = y + 36;
            int swatchGap = 5;
            int swatchW = (innerW - (NAMETAG_COLOR_PRESETS.length - 1) * swatchGap) / NAMETAG_COLOR_PRESETS.length;
            for (int i = 0; i < NAMETAG_COLOR_PRESETS.length; i++) {
                int pCol = NAMETAG_COLOR_PRESETS[i];
                int sx = innerX + i * (swatchW + swatchGap);
                boolean active = (curColor & 0x00FFFFFF) == (pCol & 0x00FFFFFF);
                boolean hover = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, sx, swatchY, swatchW, 22);
                if (hover) markHover("nt_swatch_" + i, 3);
                if (active || hover) {
                    OsuUiRenderer.drawRoundedRect(g, sx - 2, swatchY - 2, swatchW + 4, 26, 5, active ? OSU_CONTENT1 : OSU_LIGHT4);
                }
                OsuUiRenderer.drawRoundedRect(g, sx, swatchY, swatchW, 22, 4, pCol);
                drawerButtons.add(new ButtonHitbox(secIdx, sx, swatchY, swatchW, 22, () -> {
                    cfg.nametagColor = pCol | 0xFF000000;
                    syncPaletteHsvFromColor(cfg.nametagColor);
                    ModConfig.save();
                }));
            }

            // Row 3: 2D Saturation/Value Palette Box + Vertical Hue Bar
            int palY = swatchY + 30;
            int palH = 92;
            int hueW = 24;
            int gap = 8;
            int svW = innerW - hueW - gap;
            int hueX = innerX + svW + gap;

            this.palSvSec = secIdx;
            this.palSvX = innerX;
            this.palSvY = palY;
            this.palSvW = svW;
            this.palSvH = palH;
            this.palHueX = hueX;
            this.palHueY = palY;
            this.palHueW = hueW;
            this.palHueH = palH;

            int cols = 28;
            // Render shared 2D SV + Hue palette
            drawHsvPaletteWidget(g, innerX, palY, svW, palH, hueX, hueW,
                    nametagPaletteHue, nametagPaletteSat, nametagPaletteVal, curColor);

            // Row 4: Live sample nametag preview strip
            int sampleY = palY + palH + 6;
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, "Player", innerX, sampleY, 13.5f, curColor);
            if (cfg.nametagShowDistance) {
                float nameW = TorusFontRenderer.measureWidth(Weight.SEMIBOLD, "Player", 13.5f);
                TorusFontRenderer.drawString(g, Weight.SEMIBOLD, " [12]", innerX + nameW, sampleY, 13.5f, 0xFF00FFFF);
            }
        }
        return y + cardH;
    }

    private void drawVanillaTextPreview(GuiGraphicsExtractor g, FormattedCharSequence seq, int x, int y, int maxWidth, float scale) {
        if (this.minecraft == null || this.minecraft.font == null || seq == null) {
            return;
        }
        GuiBatchHelper.flushScope(g);
        int rawW = Math.max(1, this.minecraft.font.width(seq));
        float fitScale = Math.min(scale, maxWidth / (float) rawW);
        int yOff = Math.round((9.0f * (scale - fitScale)) * 0.5f);
        g.pose().pushMatrix();
        g.pose().translate(x, y + yOff);
        g.pose().scale(fitScale, fitScale);
        g.text(this.minecraft.font, seq, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    private int renderCustomNameVisualCard(GuiGraphicsExtractor g, int secIdx, int x, int y, int w,
                                           int viewTop, int viewBottom, int mouseX, int mouseY) {
        int cardH = 290;
        if (isVisible(y, cardH, viewTop, viewBottom)) {
            ModConfig cfg = ModConfig.getInstance();
            boolean isChroma = "CHROMA".equalsIgnoreCase(cfg.customNameColorMode);
            int curColor = cfg.customNameColor | 0xFF000000;

            OsuUiRenderer.drawRoundedRect(g, x, y, w, cardH, 6, OSU_BG4_DARK);

            int pad = 10;
            int innerX = x + pad;
            int innerW = w - pad * 2;

            // Row 1: Title + Live Minecraft Font Preview Pill
            String title = ModLang.tr("dttdrtaddons.custom.name_visual_title");
            TorusFontRenderer.drawFittedString(g, Weight.REGULAR, title,
                    innerX, y + 8, innerW - 180, 20f, 13.5f, 9.5f, 14f, OSU_CONTENT2);

            int previewW = 172;
            int previewX = x + w - pad - previewW;
            OsuUiRenderer.drawRoundedRect(g, previewX, y + 6, previewW, 24, 4, OSU_BG5);
            OsuUiRenderer.drawRoundedBorder(g, previewX, y + 6, previewW, 24, 4, OSU_BG6);
            drawVanillaTextPreview(g, CustomNameFeature.buildPreviewSequence(), previewX + 8, y + 12, previewW - 16, 1.25f);

            // Row 2: Animated Rainbow Chroma Card Button
            int chromaY = y + 36;
            int chromaH = 26;
            boolean chromaHover = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, innerX, chromaY, innerW, chromaH);
            if (chromaHover) markHover("cn_chroma", 2);
            if (isChroma || chromaHover) {
                OsuUiRenderer.drawRoundedRect(g, innerX - 2, chromaY - 2, innerW + 4, chromaH + 4, 6, isChroma ? OSU_CONTENT1 : OSU_LIGHT4);
            }
            int slices = 36;
            float sliceW = innerW / (float) slices;
            float phase = (System.currentTimeMillis() % 4000L) / 4000.0f;
            for (int i = 0; i < slices; i++) {
                float h = (phase + i / (float) slices) % 1.0f;
                int c = Color.HSBtoRGB(h, Math.max(0.45f, cfg.chromaSaturation), 0.90f) | 0xFF000000;
                OsuUiRenderer.drawSolidRectF(g, innerX + i * sliceW, chromaY, sliceW + 0.6f, chromaH, c);
            }
            OsuUiRenderer.drawRoundedRect(g, innerX, chromaY, innerW, chromaH, 4, 0x55000000);
            TorusFontRenderer.drawCenteredString(g, Weight.BOLD, ModLang.tr("dttdrtaddons.custom.name_chroma_btn"),
                    innerX + innerW * 0.5f, chromaY + 6, 13.5f, 0xFFFFFFFF);
            drawerButtons.add(new ButtonHitbox(secIdx, innerX, chromaY, innerW, chromaH, () -> {
                cfg.customNameColorMode = "CHROMA";
                ModConfig.save();
            }));

            // Row 3 & 4: Classic 16 MC Color Swatches (2 rows x 8 cols)
            int gridY = chromaY + chromaH + 8;
            int swatchGap = 5;
            int colsPerRow = 8;
            int swatchW = (innerW - (colsPerRow - 1) * swatchGap) / colsPerRow;
            int swatchH = 20;
            for (int i = 0; i < MC_16_COLOR_PRESETS.length; i++) {
                int rIdx = i / colsPerRow;
                int cIdx = i % colsPerRow;
                int sx = innerX + cIdx * (swatchW + swatchGap);
                int sy = gridY + rIdx * (swatchH + 5);
                int pCol = MC_16_COLOR_PRESETS[i];
                boolean active = !isChroma && ((curColor & 0x00FFFFFF) == (pCol & 0x00FFFFFF));
                boolean hover = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, sx, sy, swatchW, swatchH);
                if (hover) markHover("cn_mc_" + i, 3);
                if (active || hover) {
                    OsuUiRenderer.drawRoundedRect(g, sx - 2, sy - 2, swatchW + 4, swatchH + 4, 5, active ? OSU_CONTENT1 : OSU_LIGHT4);
                }
                OsuUiRenderer.drawRoundedRect(g, sx, sy, swatchW, swatchH, 4, pCol);
                drawerButtons.add(new ButtonHitbox(secIdx, sx, sy, swatchW, swatchH, () -> {
                    cfg.customNameColorMode = "SOLID";
                    cfg.customNameColor = pCol | 0xFF000000;
                    syncCustomNamePaletteHsvFromColor(cfg.customNameColor);
                    ModConfig.save();
                }));
            }

            // Row 5: Formatting Style Toggles (Bold / Italic / Underline)
            int fmtY = gridY + 2 * swatchH + 12;
            int fmtH = 26;
            int fmtGap = 6;
            int fmtW = (innerW - 2 * fmtGap) / 3;
            drawStyleTogglePill(g, secIdx, "cn_bold", innerX, fmtY, fmtW, fmtH,
                    ModLang.tr("dttdrtaddons.custom.name_bold"), cfg.customNameBold, mouseX, mouseY, () -> {
                        cfg.customNameBold = !cfg.customNameBold;
                        ModConfig.save();
                    });
            drawStyleTogglePill(g, secIdx, "cn_italic", innerX + fmtW + fmtGap, fmtY, fmtW, fmtH,
                    ModLang.tr("dttdrtaddons.custom.name_italic"), cfg.customNameItalic, mouseX, mouseY, () -> {
                        cfg.customNameItalic = !cfg.customNameItalic;
                        ModConfig.save();
                    });
            drawStyleTogglePill(g, secIdx, "cn_under", innerX + (fmtW + fmtGap) * 2, fmtY,
                    innerW - (fmtW + fmtGap) * 2, fmtH,
                    ModLang.tr("dttdrtaddons.custom.name_underline"), cfg.customNameUnderlined, mouseX, mouseY, () -> {
                        cfg.customNameUnderlined = !cfg.customNameUnderlined;
                        ModConfig.save();
                    });

            // Row 6: Free 2D HSV Color Picker (SV Box + Hue Bar)
            int palY = fmtY + fmtH + 10;
            int palH = 88;
            int hueW = 24;
            int gap = 8;
            int svW = innerW - hueW - gap;
            int hueX = innerX + svW + gap;

            this.namePalSvSec = secIdx;
            this.namePalSvX = innerX;
            this.namePalSvY = palY;
            this.namePalSvW = svW;
            this.namePalSvH = palH;
            this.namePalHueX = hueX;
            this.namePalHueY = palY;
            this.namePalHueW = hueW;
            this.namePalHueH = palH;

            drawHsvPaletteWidget(g, innerX, palY, svW, palH, hueX, hueW,
                    customNamePaletteHue, customNamePaletteSat, customNamePaletteVal, curColor);

            // Bottom strip: hex badge or CHROMA tag
            int footY = palY + palH + 6;
            String badge = isChroma ? "MODE: CHROMA (§z)" : String.format(Locale.ROOT, "HEX #%06X", curColor & 0x00FFFFFF);
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, badge, innerX, footY, 12f, isChroma ? OSU_COLOUR1 : curColor);
        }
        return y + cardH;
    }

    private void drawStyleTogglePill(GuiGraphicsExtractor g, int secIdx, String hoverKey, int x, int y, int w, int h,
                                     String label, boolean active, int mouseX, int mouseY, Runnable onClick) {
        boolean hover = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, x, y, w, h);
        if (hover) markHover(hoverKey, 2);
        int bg = active ? OSU_COLOUR1 : (hover ? OSU_DARK4 : OSU_BG5);
        OsuUiRenderer.drawRoundedRect(g, x, y, w, h, 5, bg);
        if (!active && hover) {
            OsuUiRenderer.drawRoundedBorder(g, x, y, w, h, 5, OSU_LIGHT4);
        }
        TorusFontRenderer.drawCenteredString(g, active ? Weight.BOLD : Weight.REGULAR, label,
                x + w * 0.5f, y + (h - 13) * 0.5f, 12.5f, active ? 0xFFFFFFFF : OSU_CONTENT2);
        drawerButtons.add(new ButtonHitbox(secIdx, x, y, w, h, onClick));
    }

    private int renderChromaSection(GuiGraphicsExtractor g, int secIdx, int x, int curY, int w,
                                    int viewTop, int viewBottom, int mouseX, int mouseY, float dt) {
        ModConfig cfg = ModConfig.getInstance();
        curY = drawSubsectionHeader(g, x, curY, ModLang.tr("dttdrtaddons.custom.chroma_header"), viewTop, viewBottom);

        if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Chroma", "Enable", "§z", "dttdrtaddons.custom.chroma_enable")) {
            curY = drawFormCheckBox(g, secIdx, "chroma_enabled", x, curY, w, ModLang.tr("dttdrtaddons.custom.chroma_enable"), 0,
                    cfg.chromaEnabled, v -> cfg.chromaEnabled = v, viewTop, viewBottom, mouseX, mouseY, dt);
            curY += ITEM_SPACING_V2;
        }

        // Live §z sample preview strip
        int prevH = 28;
        if (isVisible(curY, prevH, viewTop, viewBottom)) {
            OsuUiRenderer.drawRoundedRect(g, x, curY, w, prevH, 5, OSU_BG4_DARK);
            OsuUiRenderer.drawRoundedBorder(g, x, curY, w, prevH, 5, OSU_BG6);
            drawVanillaTextPreview(g, CustomNameFeature.buildChromaSampleSequence(), x + 10, curY + 7, w - 20, 1.3f);
        }
        curY += prevH + ITEM_SPACING_V2;

        if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Chroma", "Speed", "dttdrtaddons.custom.chroma_speed")) {
            curY = drawFormSliderBar(g, secIdx, 10, x, curY, w, ModLang.tr("dttdrtaddons.custom.chroma_speed"),
                    String.format(Locale.ROOT, "%.1f", cfg.chromaSpeed),
                    (cfg.chromaSpeed - 0.5) / 19.5, (6.0 - 0.5) / 19.5,
                    Math.abs(cfg.chromaSpeed - 6.0f) < 0.05f, viewTop, viewBottom, mouseX, mouseY, dt);
            curY += ITEM_SPACING_V2;
        }
        if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Chroma", "Size", "dttdrtaddons.custom.chroma_size")) {
            curY = drawFormSliderBar(g, secIdx, 11, x, curY, w, ModLang.tr("dttdrtaddons.custom.chroma_size"),
                    String.format(Locale.ROOT, "%d", Math.round(cfg.chromaSize)),
                    (cfg.chromaSize - 5.0) / 95.0, (30.0 - 5.0) / 95.0,
                    Math.round(cfg.chromaSize) == 30, viewTop, viewBottom, mouseX, mouseY, dt);
            curY += ITEM_SPACING_V2;
        }
        if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Chroma", "Saturation", "dttdrtaddons.custom.chroma_saturation")) {
            int satPct = Math.round(cfg.chromaSaturation * 100.0f);
            curY = drawFormSliderBar(g, secIdx, 12, x, curY, w, ModLang.tr("dttdrtaddons.custom.chroma_saturation"),
                    satPct + "%",
                    cfg.chromaSaturation, 0.75,
                    satPct == 75, viewTop, viewBottom, mouseX, mouseY, dt);
            curY += ITEM_SPACING_V2;
        }
        if (matchesFilter("Custom", "dttdrtaddons.section.custom", "Chroma", "Direction", "dttdrtaddons.custom.chroma_direction")) {
            String dirLabel = getChromaDirectionLabel(cfg.chromaDirection);
            curY = drawSettingsButtonV2(g, secIdx, x, curY, w, 32,
                    ModLang.tr("dttdrtaddons.custom.chroma_direction", dirLabel), OSU_BG4_DARK,
                    viewTop, viewBottom, mouseX, mouseY, () -> {
                        int idx = 0;
                        for (int i = 0; i < CHROMA_DIRECTIONS.length; i++) {
                            if (CHROMA_DIRECTIONS[i].equalsIgnoreCase(cfg.chromaDirection)) {
                                idx = (i + 1) % CHROMA_DIRECTIONS.length;
                                break;
                            }
                        }
                        cfg.chromaDirection = CHROMA_DIRECTIONS[idx];
                        ModConfig.save();
                    });
        }
        return curY;
    }

    private static String getChromaDirectionLabel(String dir) {
        if ("FORWARD_LEFT".equalsIgnoreCase(dir)) return ModLang.tr("dttdrtaddons.custom.chroma_dir_fl");
        if ("BACKWARD_RIGHT".equalsIgnoreCase(dir)) return ModLang.tr("dttdrtaddons.custom.chroma_dir_br");
        if ("BACKWARD_LEFT".equalsIgnoreCase(dir)) return ModLang.tr("dttdrtaddons.custom.chroma_dir_bl");
        return ModLang.tr("dttdrtaddons.custom.chroma_dir_fr");
    }

    private int renderDrawerChWaypointRow(GuiGraphicsExtractor g, int secIdx, int x, int y, int w, int viewTop, int viewBottom,
                                          int mouseX, int mouseY) {
        CrystalLocation[] locs = CrystalLocation.values();
        if (selectedChLocIndex < 0 || selectedChLocIndex >= locs.length) selectedChLocIndex = 0;
        CrystalLocation selLoc = locs[selectedChLocIndex];

        if (isVisible(y, 76, viewTop, viewBottom)) {
            int addW = 70;
            int dropW = w - addW - 6;
            boolean dropHover = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, x, y, dropW, 34);
            OsuUiRenderer.drawRoundedRect(g, x, y, dropW, 34, 5, dropHover ? OSU_DARK4 : OSU_BG4_DARK);
            if (dropHover) OsuUiRenderer.drawRoundedBorder(g, x, y, dropW, 34, 5, OSU_LIGHT4);
            TorusFontRenderer.drawString(g, Weight.REGULAR,
                    TorusFontRenderer.trimToWidth(Weight.REGULAR, "Waypoint: " + selLoc.displayName, dropW - 18, 14f),
                    x + 9, y + 10, 14f, 0xFF000000 | selLoc.fillColor);
            drawerButtons.add(new ButtonHitbox(secIdx, x, y, dropW, 34, () -> selectedChLocIndex = (selectedChLocIndex + 1) % locs.length));

            drawSettingsButtonV2(g, secIdx, x + dropW + 6, y, addW, 34, "Add", OSU_COLOUR2, viewTop, viewBottom, mouseX, mouseY, () -> {
                try {
                    double vx = Double.parseDouble(chXText.trim());
                    double vy = chYText.trim().isEmpty() ? 100.0 : Double.parseDouble(chYText.trim());
                    double vz = Double.parseDouble(chZText.trim());
                    CrystalHollowsTracker.setManualLocation(selLoc, vx, vy, vz);
                    chStatusMsg = "Added " + selLoc.displayName;
                    chStatusExpiry = System.currentTimeMillis() + 3000L;
                    chXText = "";
                    chYText = "";
                    chZText = "";
                    focusedInput = 0;
                } catch (NumberFormatException ignored) {
                    chStatusMsg = "Invalid X/Z";
                    chStatusExpiry = System.currentTimeMillis() + 3000L;
                }
            });

            int rowY = y + 38;
            int boxW = (w - 12) / 3;
            drawChInput(g, 4, x, rowY, boxW, 34, "X", chXText);
            drawChInput(g, 5, x + boxW + 6, rowY, boxW, 34, "Y", chYText);
            drawChInput(g, 6, x + (boxW + 6) * 2, rowY, w - (boxW + 6) * 2, 34, "Z", chZText);
            drawerInputs.add(new InputHitbox(secIdx, 4, x, rowY, boxW, 34));
            drawerInputs.add(new InputHitbox(secIdx, 5, x + boxW + 6, rowY, boxW, 34));
            drawerInputs.add(new InputHitbox(secIdx, 6, x + (boxW + 6) * 2, rowY, w - (boxW + 6) * 2, 34));
        }
        int nextY = y + 78;

        if (!chStatusMsg.isEmpty() && System.currentTimeMillis() < chStatusExpiry) {
            if (isVisible(nextY, 18, viewTop, viewBottom)) {
                TorusFontRenderer.drawString(g, Weight.REGULAR, chStatusMsg, x + 4, nextY + 2, 13f, COLOR_GOLD);
            }
            nextY += 20;
        }

        for (CrystalLocation loc : locs) {
            if (!loc.exists()) continue;
            if (isVisible(nextY, 30, viewTop, viewBottom)) {
                OsuUiRenderer.drawRoundedRect(g, x, nextY, w, 28, 5, OSU_BG5);
                String text = loc.displayName + " (" + Math.round(loc.getWorldX()) + ", " + Math.round(loc.getWorldY()) + ", " + Math.round(loc.getWorldZ()) + ")";
                TorusFontRenderer.drawString(g, Weight.REGULAR,
                        TorusFontRenderer.trimToWidth(Weight.REGULAR, text, w - 38, 13f),
                        x + 9, nextY + 7, 13f, 0xFF000000 | loc.fillColor);
                int remX = x + w - 26;
                boolean remHov = (secIdx == activeSectionIdx) && isInside(mouseX, mouseY, remX, nextY + 4, 20, 20);
                TorusFontRenderer.drawCenteredString(g, Weight.BOLD, "x", remX + 10, nextY + 7, 14f, remHov ? COLOR_DANGER : OSU_GRAY_TEXT);
                drawerButtons.add(new ButtonHitbox(secIdx, remX, nextY + 4, 20, 20, () -> CrystalHollowsTracker.clearLocation(loc)));
            }
            nextY += 32;
        }
        return nextY;
    }

    private void drawChInput(GuiGraphicsExtractor g, int id, int x, int y, int w, int h, String placeholder, String text) {
        boolean focus = (focusedInput == id);
        OsuUiRenderer.drawRoundedRect(g, x, y, w, h, 5, OSU_BG5);
        if (focus) {
            OsuUiRenderer.drawRoundedBorder(g, x, y, w, h, 5, OSU_COLOUR1);
        }
        String cursor = (focus && ((System.currentTimeMillis() - openTime) / 500) % 2 == 0) ? "|" : "";
        String disp = (text.isEmpty() && !focus) ? placeholder : TorusFontRenderer.trimToWidth(Weight.REGULAR, text + cursor, w - 14, 14f);
        TorusFontRenderer.drawString(g, Weight.REGULAR, disp, x + 8, y + 10, 14f, (text.isEmpty() && !focus) ? OSU_GRAY_TEXT : OSU_CONTENT1);
    }

    private void applySliderFraction(int sliderId, double frac, ModConfig cfg) {
        double norm = Math.max(0.0, Math.min(1.0, frac));
        switch (sliderId) {
            case 1 -> {
                cfg.gemstoneEspRadius = 1 + (int) Math.round(norm * 499.0);
                norm = (cfg.gemstoneEspRadius - 1) / 499.0;
            }
            case 2 -> {
                int rawMs = 100 + (int) Math.round(norm * 4900.0);
                cfg.gemstoneEspScanIntervalMs = Math.max(100, Math.min(5000, ((rawMs + 25) / 50) * 50));
                norm = (cfg.gemstoneEspScanIntervalMs - 100) / 4900.0;
            }
            case 3 -> {
                cfg.backgroundDarkness = (int) Math.round(norm * 242.0);
                norm = cfg.backgroundDarkness / 242.0;
            }
            case 4 -> {
                cfg.uiPanelOpacity = 40 + (int) Math.round(norm * 205.0);
                norm = (cfg.uiPanelOpacity - 40) / 205.0;
            }
            case 5 -> cfg.bgScale = (float) (0.25 + norm * 2.75);
            case 6 -> {
                cfg.bgOffsetX = -300 + (int) Math.round(norm * 600.0);
                norm = (cfg.bgOffsetX + 300) / 600.0;
            }
            case 7 -> {
                cfg.bgOffsetY = -300 + (int) Math.round(norm * 600.0);
                norm = (cfg.bgOffsetY + 300) / 600.0;
            }
            case 8 -> {
                cfg.marketHudScale = (float) (Math.round((0.60 + norm * 0.90) * 20.0) / 20.0);
                norm = (cfg.marketHudScale - 0.60) / 0.90;
            }
            case 9 -> {
                cfg.freecamSpeed = (float) (Math.round((0.25 + norm * 5.75) * 20.0) / 20.0);
                norm = (cfg.freecamSpeed - 0.25) / 5.75;
            }
            case 10 -> {
                cfg.chromaSpeed = (float) (Math.round((0.5 + norm * 19.5) * 10.0) / 10.0);
                norm = (cfg.chromaSpeed - 0.5) / 19.5;
            }
            case 11 -> {
                cfg.chromaSize = 5 + Math.round((float) (norm * 95.0));
                norm = (cfg.chromaSize - 5.0) / 95.0;
            }
            case 12 -> {
                cfg.chromaSaturation = Math.round((float) (norm * 100.0)) / 100.0f;
                norm = cfg.chromaSaturation;
            }
        }
        OsuSoundEngine.playSliderTick(norm);
    }

    private static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int brighten(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >>> 16) & 0xFF) + 26);
        int g = Math.min(255, ((argb >>> 8) & 0xFF) + 26);
        int b = Math.min(255, (argb & 0xFF) + 26);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ==========================================
    // Right-Side Scaled Market HUD (with exclusive Custom Background Image)
    // ==========================================
    private void renderRightMarketHud(GuiGraphicsExtractor g, int virtW, int virtH, int mouseX, int mouseY) {
        ModConfig cfg = ModConfig.getInstance();
        float mScale = getMarketScale();
        int bx = DRAWER_W + 16;
        int by = 16;
        int availW = Math.max(360, virtW - bx - 16);
        int availH = Math.max(200, virtH - 32);

        int logicalW = Math.max(360, Math.round(availW / mScale));
        int logicalH = Math.max(200, Math.round(availH / mScale));
        int visW = Math.round(logicalW * mScale);
        int visH = Math.round(logicalH * mScale);

        int bgAlpha = Math.max(0, Math.min(235, Math.round(228 * marketHudAlpha)));
        if (bgAlpha > 1) {
            OsuUiRenderer.drawRoundedRect(g, bx, by, visW, visH, 8, (bgAlpha << 24) | (MK_SURFACE0 & 0x00FFFFFF));
        }

        if (BackgroundTextureManager.hasTexture()) {
            int imgW = Math.max(1, BackgroundTextureManager.getTexWidth());
            int imgH = Math.max(1, BackgroundTextureManager.getTexHeight());
            double fitRatio = Math.min((double) visW / imgW, (double) visH / imgH);
            float bgScale = Math.max(0.25f, Math.min(3.0f, cfg.bgScale));
            int drawW = Math.max(1, (int) Math.round(imgW * fitRatio * bgScale));
            int drawH = Math.max(1, (int) Math.round(imgH * fitRatio * bgScale));
            int drawX = bx + (visW - drawW) / 2 + Math.round(cfg.bgOffsetX * 1.5f);
            int drawY = by + (visH - drawH) / 2 + Math.round(cfg.bgOffsetY * 1.5f);

            int imgTint = mulAlpha(0xFFFFFFFF, marketHudAlpha);
            if (((imgTint >>> 24) & 0xFF) > 1) {
                g.enableScissor(bx + 2, by + 2, bx + visW - 2, by + visH - 2);
                g.blit(RenderPipelines.GUI_TEXTURED, BackgroundTextureManager.getTextureId(),
                        drawX, drawY, 0f, 0f, drawW, drawH, drawW, drawH, drawW, drawH, imgTint);
                g.disableScissor();
            }

            int darknessAlpha = Math.max(0, Math.min(242, Math.round(cfg.backgroundDarkness * marketHudAlpha)));
            if (darknessAlpha > 1) {
                OsuUiRenderer.drawRoundedRect(g, bx + 1, by + 1, visW - 2, visH - 2, 7, (darknessAlpha << 24));
            }
        }

        int cx = bx + 16;
        int cy = by + 16;
        int cw = logicalW - 36;
        MarketState state = MarketState.getInstance();
        double maxScroll = getMarketMaxScroll(state, cw, logicalH);

        if (draggingMarketScrollbar && this.minecraft != null && maxScroll > 0.5) {
            double frac = Math.max(0.0, Math.min(1.0, (mouseY - (by + 12)) / (double) Math.max(1, visH - 24)));
            marketScrollTarget = frac * maxScroll;
            marketScrollOffset = marketScrollTarget;
        }
        marketScrollTarget = Math.max(0.0, Math.min(maxScroll, marketScrollTarget));
        marketScrollOffset = Math.max(0.0, Math.min(maxScroll, marketScrollOffset));

        boolean inBox = !closingDrawer && isInside(mouseX, mouseY, bx, by, visW - 10, visH);
        int pmx = inBox ? (int) Math.round(bx + (mouseX - bx) / mScale) : -10000;
        int pmy = inBox ? (int) Math.round(by + (mouseY - by) / mScale + marketScrollOffset) : -10000;

        marketRecentHitboxes.clear();

        g.enableScissor(bx + 4, by + 4, bx + visW - 4, by + visH - 4);
        g.pose().pushMatrix();
        g.pose().translate((float) bx, (float) by);
        g.pose().scale(mScale, mScale);
        g.pose().translate((float) -bx, (float) (-by - (float) marketScrollOffset));

        GuiBatchHelper.beginScope(g);
        renderMarketPanel(g, cx, cy, cw, pmx, pmy);
        GuiBatchHelper.endScope(g);

        if (focusedInput == 1 && !state.getFuzzySuggestions().isEmpty() && !closingDrawer) {
            g.nextStratum();
            renderFuzzyDropdown(g, cx, cy + 44 + 40, cw, pmx, pmy);
        }

        g.pose().popMatrix();
        g.disableScissor();

        if (maxScroll > 0.5) {
            int trackX = bx + visW - 8;
            int trackY = by + 12;
            int trackH = visH - 24;
            double contentH = getMarketContentHeight(state, cw);
            int thumbH = Math.max(30, (int) Math.round(trackH * ((logicalH - 28) / contentH)));
            float thumbY = trackY + (float) ((marketScrollOffset / maxScroll) * (trackH - thumbH));
            OsuUiRenderer.drawPillF(g, trackX, thumbY, 4f, thumbH,
                    mCol(draggingMarketScrollbar ? OSU_COLOUR1 : 0x889966FF));
        }
    }

    private record AlertLayout(
            int masterToggleX, int masterToggleY, int masterToggleW, int masterToggleH,
            int minX, int minY, int maxX, int maxY, int inputW, int inputH,
            int chatX, int soundX, int toastX, int chanY, int chanW, int chanH,
            int testX, int testY, int testW, int testH
    ) {}

    private AlertLayout computeAlertLayout(int ax, int ay, int aw) {
        int masterW = 92;
        int masterH = 22;
        int masterX = ax + aw - masterW;
        int masterY = ay;

        int group1Y = ay + 30;
        int labelGap = 15;
        int inputH = 26;
        boolean twoCol = aw >= 420;
        int minY = group1Y + labelGap;
        int minX = ax;
        int maxX = twoCol ? ax + (aw - 10) / 2 + 10 : ax;
        int maxY = twoCol ? minY : (minY + inputH + 22);
        int inputW = twoCol ? (aw - 10) / 2 : aw;

        int chanY = (twoCol ? minY : maxY) + inputH + 14;
        int chanH = 24;
        int chanW = 68;
        int chatX = ax;
        int soundX = chatX + chanW + 6;
        int toastX = soundX + chanW + 6;
        int testW = 80;
        int testH = 24;
        int testX = ax + aw - testW;
        int testY = chanY;

        return new AlertLayout(
                masterX, masterY, masterW, masterH,
                minX, minY, maxX, maxY, inputW, inputH,
                chatX, soundX, toastX, chanY, chanW, chanH,
                testX, testY, testW, testH
        );
    }

    private void renderMarketPanel(GuiGraphicsExtractor g, int cx, int cy, int cw, int mouseX, int mouseY) {
        MarketState state = MarketState.getInstance();
        boolean isAh = "AH".equalsIgnoreCase(state.getMode());

        // 1. Segmented Mode Switch + Refresh Action (smooth fade-in/out crossfade on switch)
        int segW = Math.min(300, cw - 118);
        int segH = 36;
        OsuUiRenderer.drawRoundedRect(g, cx, cy, segW, segH, 6, marketCardInnerBg());

        int tabW = (segW - 6) / 2;
        int ahX = cx + 3;
        int bzX = ahX + tabW;
        boolean ahHover = isInside(mouseX, mouseY, ahX, cy + 3, tabW, segH - 6);
        boolean bzHover = isInside(mouseX, mouseY, bzX, cy + 3, tabW, segH - 6);
        if (ahHover) markHover("mk_ah", 2);
        if (bzHover) markHover("mk_bz", 2);

        float ahPillAlpha = Math.max(0.0f, Math.min(1.0f, 1.0f - marketModeAnim));
        float bzPillAlpha = Math.max(0.0f, Math.min(1.0f, marketModeAnim));

        if (ahHover && ahPillAlpha < 0.99f) {
            OsuUiRenderer.drawRoundedRect(g, ahX, cy + 3, tabW, segH - 6, 4, marketCardHoverBg());
        }
        if (ahPillAlpha > 0.01f) {
            OsuUiRenderer.drawRoundedRect(g, ahX, cy + 3, tabW, segH - 6, 4, mulAlpha(mCol(OSU_COLOUR2), ahPillAlpha));
        }
        TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD, ModLang.tr("dttdrtaddons.market.tab_ah"),
                ahX + tabW * 0.5f, cy + 3, tabW - 8, segH - 6, 14f, 9f, 15f,
                mCol(lerpColor(MK_TEXT_MUTED, MK_TEXT_PRIMARY, ahPillAlpha)));

        if (bzHover && bzPillAlpha < 0.99f) {
            OsuUiRenderer.drawRoundedRect(g, bzX, cy + 3, tabW, segH - 6, 4, marketCardHoverBg());
        }
        if (bzPillAlpha > 0.01f) {
            OsuUiRenderer.drawRoundedRect(g, bzX, cy + 3, tabW, segH - 6, 4, mulAlpha(mCol(OSU_COLOUR2), bzPillAlpha));
        }
        TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD, ModLang.tr("dttdrtaddons.market.tab_bz"),
                bzX + tabW * 0.5f, cy + 3, tabW - 8, segH - 6, 14f, 9f, 15f,
                mCol(lerpColor(MK_TEXT_MUTED, MK_TEXT_PRIMARY, bzPillAlpha)));

        int refW = 108;
        int refX = cx + cw - refW;
        int cd = state.getRemainingCooldownSeconds();
        boolean onCooldown = cd > 0;
        boolean refHover = !onCooldown && isInside(mouseX, mouseY, refX, cy, refW, segH);
        if (refHover) markHover("mk_ref", 2);
        OsuUiRenderer.drawRoundedRect(g, refX, cy, refW, segH, 6,
                onCooldown ? marketCardInnerBg() : (refHover ? marketCardHoverBg() : marketCardBg()));
        String refLabel = state.isLoading()
                ? ModLang.tr("dttdrtaddons.market.syncing")
                : (onCooldown ? ModLang.tr("dttdrtaddons.market.wait_seconds", cd) : ModLang.tr("dttdrtaddons.market.refresh"));
        int refColor = state.isLoading() ? COLOR_GOLD : (onCooldown ? MK_TEXT_MUTED : MK_TEXT_PRIMARY);
        TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD, refLabel,
                refX + refW * 0.5f, cy, refW - 10, segH, 14f, 9f, 15f, mCol(refColor));

        // 2. Inset Search Well
        int searchY = cy + 44;
        int searchH = 38;
        boolean searchFocused = (focusedInput == 1);
        OsuUiRenderer.drawRoundedRect(g, cx, searchY, cw, searchH, 6, marketCardInnerBg());
        if (searchFocused) {
            OsuUiRenderer.drawRoundedBorder(g, cx, searchY, cw, searchH, 6, mCol(OSU_COLOUR1));
        }

        SkyblockItem selectedItem = state.getSelectedItem();
        int textX = cx + 36;
        if (selectedItem != null) {
            GuiBatchHelper.endScope(g);
            ClassicTextureRegistry.drawSkyblockIcon(g, selectedItem, cx + 8, searchY + 7, 24, mCol(0xFFFFFFFF));
            GuiBatchHelper.beginScope(g);
        } else {
            OsuUiRenderer.drawSearchIcon(g, cx + 10, searchY + 10, 18, mCol(searchFocused ? OSU_COLOUR1 : MK_TEXT_MUTED));
        }

        int maxTextW = cw - (textX - cx) - 36;
        String displayStr = TorusFontRenderer.trimToWidth(Weight.REGULAR, searchInputText, maxTextW, 15f);
        if (displayStr.isEmpty() && !searchFocused) {
            TorusFontRenderer.drawString(g, Weight.REGULAR,
                    TorusFontRenderer.trimToWidth(Weight.REGULAR, "Search item name or shortcut (e.g. hyp, aote, term)...", maxTextW, 14f),
                    textX, searchY + 11, 14f, mCol(MK_TEXT_MUTED));
        } else {
            String cursor = (searchFocused && ((System.currentTimeMillis() - openTime) / 500) % 2 == 0) ? "|" : "";
            TorusFontRenderer.drawString(g, Weight.REGULAR, displayStr + cursor, textX, searchY + 11, 15f, mCol(MK_TEXT_PRIMARY));
        }

        int clearX = cx + cw - 30;
        boolean clearHover = isInside(mouseX, mouseY, clearX, searchY + 5, 26, 28);
        if (!searchInputText.isEmpty()) {
            TorusFontRenderer.drawCenteredString(g, Weight.BOLD, "x", clearX + 13, searchY + 11, 15f,
                    mCol(clearHover ? COLOR_DANGER : MK_TEXT_MUTED));
        }

        int bodyY = searchY + 48;

        this.activeMarketFade = this.marketModeContentAlpha;
        try {
            // 3. Empty State (P0: hide data/chart/alert frames until an item is selected)
            if (selectedItem == null) {
                OsuUiRenderer.drawRoundedRect(g, cx, bodyY, cw, MK_EMPTY_H, 6, marketCardBg());
                renderMarketEmptyState(g, cx, bodyY, cw, MK_EMPTY_H, mouseX, mouseY, state);
                return;
            }

            // 4. Data Card + Chart Card + Alert Card
            int dataH = getMarketDataHeight(state);
            OsuUiRenderer.drawRoundedRect(g, cx, bodyY, cw, dataH, 6, marketCardBg());
            if (isAh) {
                renderAhListings(g, cx, bodyY, cw, dataH, mouseX, mouseY);
            } else {
                renderBazaarOrderBook(g, cx, bodyY, cw, dataH, mouseX, mouseY);
            }

            int chartY = bodyY + dataH + MK_CARD_GAP;
            OsuUiRenderer.drawRoundedRect(g, cx, chartY, cw, MK_CHART_H, 6, marketCardBg());
            renderPriceChart(g, cx, chartY, cw, MK_CHART_H, mouseX, mouseY);

            int alertY = chartY + MK_CHART_H + MK_CARD_GAP;
            int alertH = getMarketAlertHeight(cw);
            OsuUiRenderer.drawRoundedRect(g, cx, alertY, cw, alertH, 6, marketCardBg());
            renderAlertSection(g, cx + 14, alertY + 12, cw - 28, mouseX, mouseY);
        } finally {
            this.activeMarketFade = 1.0f;
        }
    }

    private void renderMarketEmptyState(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                        int mouseX, int mouseY, MarketState state) {
        int iconCircle = 42;
        int iconX = x + (w - iconCircle) / 2;
        int iconY = y + 26;
        OsuUiRenderer.drawCircle(g, iconX, iconY, iconCircle, marketCardInnerBg());
        OsuUiRenderer.drawSearchIcon(g, iconX + 12, iconY + 12, 18, mCol(OSU_COLOUR1));

        TorusFontRenderer.drawCenteredString(g, Weight.SEMIBOLD, "Search SkyBlock Market",
                x + w * 0.5f, iconY + 52, 16f, mCol(MK_TEXT_PRIMARY));
        TorusFontRenderer.drawCenteredString(g, Weight.REGULAR,
                "Inspect live Lowest BINs, Bazaar order books, history & alerts.",
                x + w * 0.5f, iconY + 74, 13f, mCol(MK_TEXT_MUTED));

        List<SkyblockItem> recents = WhitelistManager.isAuthorized() ? state.getRecentItems() : List.of();
        if (recents.isEmpty()) {
            TorusFontRenderer.drawCenteredString(g, Weight.REGULAR,
                    "Click the search bar above to begin",
                    x + w * 0.5f, iconY + 104, 13f, mCol(MK_TEXT_SECONDARY));
            return;
        }

        int labelY = iconY + 104;
        TorusFontRenderer.drawCenteredString(g, Weight.SEMIBOLD, "RECENT LOOKUPS",
                x + w * 0.5f, labelY, 11f, mCol(MK_TEXT_MUTED));

        int chipH = 26;
        int rowX = x + 20;
        int maxRowX = x + w - 20;
        int chipY = labelY + 18;
        int count = Math.min(5, recents.size());

        List<RecentChipHitbox> laidOut = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SkyblockItem item = recents.get(i);
            String label = TorusFontRenderer.trimToWidth(Weight.SEMIBOLD, item.getName(), 150, 12f);
            int chipW = Math.round(TorusFontRenderer.measureWidth(Weight.SEMIBOLD, label, 12f)) + 34;
            if (rowX + chipW > maxRowX && rowX > x + 20) {
                rowX = x + 20;
                chipY += chipH + 6;
                if (chipY + chipH > y + h - 10) break;
            }
            laidOut.add(new RecentChipHitbox(item, rowX, chipY, chipW, chipH));
            rowX += chipW + 8;
        }

        for (int i = 0; i < laidOut.size(); i++) {
            RecentChipHitbox chip = laidOut.get(i);
            boolean hov = isInside(mouseX, mouseY, chip.x, chip.y, chip.w, chip.h);
            if (hov) markHover("mk_rec_" + i, 2);
            OsuUiRenderer.drawRoundedRect(g, chip.x, chip.y, chip.w, chip.h, 5,
                    hov ? marketCardHoverBg() : marketCardInnerBg());
            marketRecentHitboxes.add(chip);
        }

        GuiBatchHelper.endScope(g);
        for (RecentChipHitbox chip : laidOut) {
            ClassicTextureRegistry.drawSkyblockIcon(g, chip.item, chip.x + 6, chip.y + 5, 16, mCol(0xFFFFFFFF));
        }
        GuiBatchHelper.beginScope(g);

        for (RecentChipHitbox chip : laidOut) {
            String label = TorusFontRenderer.trimToWidth(Weight.SEMIBOLD, chip.item.getName(), 150, 12f);
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, label, chip.x + 26, chip.y + 6, 12f,
                    mCol(chip.item.getTierColor()));
        }
    }

    private static Double computePercentDelta(double currentVal, double prevVal) {
        if (Math.abs(prevVal) < 1e-6 || Double.isNaN(currentVal) || Double.isInfinite(currentVal)) {
            return null;
        }
        return ((currentVal - prevVal) / Math.abs(prevVal)) * 100.0;
    }

    private static double pickPreviousReference(List<PriceHistoryPoint> pts, double liveValue, int mode) {
        if (pts == null || pts.isEmpty()) return 0.0;
        int last = pts.size() - 1;
        double candidate = extractHistoryValue(pts.get(last), mode);
        if (pts.size() >= 2 && (candidate <= 0.0 || Math.abs(candidate - liveValue) < 1e-6)) {
            double prev = extractHistoryValue(pts.get(last - 1), mode);
            if (prev > 0.0) return prev;
        }
        return candidate;
    }

    // mode: 0 = AH min (fallback avg), 1 = primary, 2 = secondary, 3 = BZ margin (primary - secondary)
    private static double extractHistoryValue(PriceHistoryPoint pt, int mode) {
        if (pt == null) return 0.0;
        return switch (mode) {
            case 0 -> pt.getSecondaryPrice() > 0.0 ? pt.getSecondaryPrice() : pt.getPrimaryPrice();
            case 1 -> pt.getPrimaryPrice();
            case 2 -> pt.getSecondaryPrice();
            case 3 -> (pt.getPrimaryPrice() > 0.0 && pt.getSecondaryPrice() > 0.0)
                    ? (pt.getPrimaryPrice() - pt.getSecondaryPrice())
                    : 0.0;
            default -> 0.0;
        };
    }

    private void renderHeroMetricBox(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                     String label, String value, int valueColor, Double pctChange) {
        OsuUiRenderer.drawRoundedRect(g, x, y, w, h, 5, marketCardInnerBg());
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, label, x + 10, y + 7, 11f, mCol(MK_TEXT_MUTED));

        int deltaReserve = 0;
        if (pctChange != null && !Double.isNaN(pctChange) && !Double.isInfinite(pctChange)) {
            double pct = pctChange;
            String deltaText;
            int deltaColor;
            if (pct > 0.005) {
                deltaText = "\u2191 +" + PCT_FORMAT.format(pct) + "%";
                deltaColor = COLOR_DANGER;  // Rise = Red
            } else if (pct < -0.005) {
                deltaText = "\u2193 -" + PCT_FORMAT.format(Math.abs(pct)) + "%";
                deltaColor = COLOR_SUCCESS; // Drop = Green
            } else {
                deltaText = "0%";
                deltaColor = MK_TEXT_MUTED;
            }
            float deltaW = TorusFontRenderer.measureWidth(Weight.SEMIBOLD, deltaText, 12f);
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, deltaText, x + w - 10 - deltaW, y + 26, 12f, mCol(deltaColor));
            deltaReserve = Math.round(deltaW) + 8;
        }

        int availValW = Math.max(30, w - 20 - deltaReserve);
        float fontSize = 15f;
        if (TorusFontRenderer.measureWidth(Weight.BOLD, value, fontSize) > availValW) {
            fontSize = 13f;
        }
        if (TorusFontRenderer.measureWidth(Weight.BOLD, value, fontSize) > availValW) {
            fontSize = 11.5f;
        }
        float valY = y + (fontSize >= 14.5f ? 23f : (fontSize >= 12.5f ? 24.5f : 25.5f));
        TorusFontRenderer.drawString(g, Weight.BOLD,
                TorusFontRenderer.trimToWidth(Weight.BOLD, value, availValW, fontSize),
                x + 10, valY, fontSize, mCol(valueColor));
    }

    private void renderAhListings(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        MarketState state = MarketState.getInstance();
        SkyblockItem item = state.getSelectedItem();
        List<AhItemListing> bins = state.getLowestBins();

        String title = item != null ? item.getName() : "Lowest BINs";
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD,
                TorusFontRenderer.trimToWidth(Weight.SEMIBOLD, title, w - 160, 15f),
                x + 14, y + 10, 15f, mCol(item != null ? item.getTierColor() : MK_TEXT_PRIMARY));
        float statusW = TorusFontRenderer.measureWidth(Weight.REGULAR, state.getStatusMessage(), 12f);
        TorusFontRenderer.drawString(g, Weight.REGULAR, state.getStatusMessage(),
                x + w - statusW - 14, y + 12, 12f, mCol(MK_TEXT_MUTED));

        if (bins == null || bins.isEmpty()) {
            String msg = state.isLoading() ? "Fetching lowest BINs from Coflnet..." : "No active BIN listings found.";
            TorusFontRenderer.drawCenteredString(g, Weight.REGULAR, msg, x + w * 0.5f, y + h * 0.5f + 2, 14f, mCol(MK_TEXT_MUTED));
            return;
        }

        // Hero Summary Strip (3 equal metric wells with exact prices & period deltas)
        int metricY = y + 36;
        int metricH = 50;
        int boxW = (w - 28 - 16) / 3;
        long lowestPrice = bins.get(0).getPrice();
        String spreadStr = bins.size() >= 2
                ? "+" + NumberFormatUtils.formatWithCommas(bins.get(1).getPrice() - lowestPrice)
                : bins.size() + " BIN";

        List<PriceHistoryPoint> cmpPts = state.getComparisonHistoryPoints();
        Double lowestDeltaPct = null;
        if (cmpPts != null && !cmpPts.isEmpty()) {
            double refMin = pickPreviousReference(cmpPts, (double) lowestPrice, 0);
            lowestDeltaPct = computePercentDelta((double) lowestPrice, refMin);
        }

        String avgHistStr = "\u2014";
        Double avgDeltaPct = null;
        if (cmpPts != null && !cmpPts.isEmpty()) {
            double latestAvg = cmpPts.get(cmpPts.size() - 1).getPrimaryPrice();
            if (latestAvg > 0) {
                avgHistStr = NumberFormatUtils.formatExact(latestAvg);
                if (cmpPts.size() >= 2) {
                    double prevAvg = cmpPts.get(cmpPts.size() - 2).getPrimaryPrice();
                    avgDeltaPct = computePercentDelta(latestAvg, prevAvg);
                }
            }
        }

        renderHeroMetricBox(g, x + 14, metricY, boxW, metricH, "LOWEST BIN",
                NumberFormatUtils.formatWithCommas(lowestPrice), COLOR_SUCCESS, lowestDeltaPct);
        renderHeroMetricBox(g, x + 14 + boxW + 8, metricY, boxW, metricH,
                bins.size() >= 2 ? "SPREAD (#1 \u2192 #2)" : "ACTIVE LISTINGS", spreadStr, MK_TEXT_PRIMARY, null);
        renderHeroMetricBox(g, x + 14 + (boxW + 8) * 2, metricY, w - 28 - (boxW + 8) * 2, metricH,
                "LATEST AVG", avgHistStr, COLOR_GOLD, avgDeltaPct);

        // Column headers (numbers right-aligned)
        int headY = metricY + metricH + 10;
        int colRank = x + 18;
        int colPriceRight = x + Math.min(205, w / 2 - 12);
        int colSeller = colPriceRight + 16;
        int copyW = 54;
        int copyX = x + w - copyW - 14;
        int colAgeRight = copyX - 12;

        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, "RANK", colRank, headY, 11f, mCol(MK_TEXT_MUTED));
        String pHdr = "PRICE";
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, pHdr,
                colPriceRight - TorusFontRenderer.measureWidth(Weight.SEMIBOLD, pHdr, 11f), headY, 11f, mCol(MK_TEXT_MUTED));
        if (colAgeRight > colSeller + 70) {
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, "SELLER", colSeller, headY, 11f, mCol(MK_TEXT_MUTED));
            String aHdr = "AGE";
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, aHdr,
                    colAgeRight - TorusFontRenderer.measureWidth(Weight.SEMIBOLD, aHdr, 11f), headY, 11f, mCol(MK_TEXT_MUTED));
        }

        int rowY = headY + 18;
        int maxRows = Math.min(state.getAhDisplayLimit(), bins.size());
        for (int i = 0; i < maxRows; i++) {
            AhItemListing listing = bins.get(i);
            boolean rowHover = isInside(mouseX, mouseY, x + 10, rowY, w - 20, 24);
            if (rowHover) {
                OsuUiRenderer.drawRoundedRect(g, x + 10, rowY, w - 20, 24, 4, marketCardHoverBg());
            } else if (i == 0) {
                OsuUiRenderer.drawRoundedRect(g, x + 10, rowY, w - 20, 24, 4, marketCardInnerBg());
            }

            if (i == 0) {
                OsuUiRenderer.drawPillF(g, x + 11, rowY + 4, 3f, 16f, mCol(COLOR_SUCCESS));
            }

            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, "#" + (i + 1), colRank, rowY + 5, 13f,
                    mCol((i == 0) ? COLOR_SUCCESS : MK_TEXT_MUTED));

            String priceText = NumberFormatUtils.formatWithCommas(listing.getPrice());
            float priceW = TorusFontRenderer.measureWidth(Weight.SEMIBOLD, priceText, 14f);
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, priceText, colPriceRight - priceW, rowY + 5, 14f,
                    mCol((i == 0) ? COLOR_SUCCESS : MK_TEXT_PRIMARY));

            if (colAgeRight > colSeller + 70) {
                String ageStr = listing.getRelativeAgeString();
                float ageW = TorusFontRenderer.measureWidth(Weight.REGULAR, ageStr, 12f);
                int maxSellerW = Math.max(40, (int) (colAgeRight - ageW - 10 - colSeller));
                TorusFontRenderer.drawString(g, Weight.REGULAR,
                        TorusFontRenderer.trimToWidth(Weight.REGULAR, listing.getSellerName(), maxSellerW, 13f),
                        colSeller, rowY + 5, 13f, mCol(MK_TEXT_SECONDARY));
                TorusFontRenderer.drawString(g, Weight.REGULAR, ageStr, colAgeRight - ageW, rowY + 6, 12f, mCol(MK_TEXT_MUTED));
            }

            boolean copyHover = isInside(mouseX, mouseY, copyX, rowY + 2, copyW, 20);
            if (copyHover) markHover("mk_copy_" + i, 2);
            OsuUiRenderer.drawRoundedRect(g, copyX, rowY + 2, copyW, 20, 4, copyHover ? mCol(OSU_COLOUR2) : marketCardInnerBg());
            TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD, ModLang.tr("dttdrtaddons.market.copy"),
                    copyX + copyW * 0.5f, rowY + 2, copyW - 8, 20, 12f, 8.5f, 13f,
                    mCol(copyHover ? MK_TEXT_PRIMARY : MK_TEXT_SECONDARY));
            rowY += 26;
        }

        int moreW = 128;
        int moreH = 24;
        int moreX = x + (w - moreW) / 2;
        int moreY = y + h - 32;
        boolean moreHover = isInside(mouseX, mouseY, moreX, moreY, moreW, moreH);
        if (moreHover) markHover("mk_more_ah", 2);
        OsuUiRenderer.drawRoundedRect(g, moreX, moreY, moreW, moreH, 4, moreHover ? mCol(OSU_COLOUR2) : marketCardInnerBg());
        TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD,
                state.isLoading() ? ModLang.tr("dttdrtaddons.market.loading") : ModLang.tr("dttdrtaddons.market.show_more"),
                moreX + moreW * 0.5f, moreY, moreW - 12, moreH, 13f, 8.5f, 14f,
                mCol(moreHover ? MK_TEXT_PRIMARY : MK_TEXT_SECONDARY));
    }

    private void renderBazaarOrderBook(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        MarketState state = MarketState.getInstance();
        SkyblockItem item = state.getSelectedItem();
        BazaarItemSummary bz = state.getBazaarSummary();

        String title = item != null ? item.getName() : "Bazaar Order Book";
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD,
                TorusFontRenderer.trimToWidth(Weight.SEMIBOLD, title, w - 160, 15f),
                x + 14, y + 10, 15f, mCol(item != null ? item.getTierColor() : MK_TEXT_PRIMARY));
        float statusW = TorusFontRenderer.measureWidth(Weight.REGULAR, state.getStatusMessage(), 12f);
        TorusFontRenderer.drawString(g, Weight.REGULAR, state.getStatusMessage(),
                x + w - statusW - 14, y + 12, 12f, mCol(MK_TEXT_MUTED));

        if (bz == null) {
            String msg = state.isLoading() ? "Fetching Bazaar order book..." : "Not available on Bazaar.";
            TorusFontRenderer.drawCenteredString(g, Weight.REGULAR, msg, x + w * 0.5f, y + h * 0.5f + 2, 14f, mCol(MK_TEXT_MUTED));
            return;
        }

        // Hero Summary Strip (with right-aligned period deltas)
        int metricY = y + 36;
        int metricH = 50;
        int boxW = (w - 28 - 16) / 3;
        List<PriceHistoryPoint> cmpPts = state.getComparisonHistoryPoints();
        double buyNow = bz.getInstantBuyPrice();
        double sellNow = bz.getInstantSellPrice();
        double marginNow = bz.getMargin();

        Double buyDelta = (cmpPts != null && !cmpPts.isEmpty())
                ? computePercentDelta(buyNow, pickPreviousReference(cmpPts, buyNow, 1)) : null;
        Double sellDelta = (cmpPts != null && !cmpPts.isEmpty())
                ? computePercentDelta(sellNow, pickPreviousReference(cmpPts, sellNow, 2)) : null;
        Double marginDelta = (cmpPts != null && !cmpPts.isEmpty())
                ? computePercentDelta(marginNow, pickPreviousReference(cmpPts, marginNow, 3)) : null;

        renderHeroMetricBox(g, x + 14, metricY, boxW, metricH, "INSTANT BUY",
                NumberFormatUtils.formatExact(buyNow), COLOR_GOLD, buyDelta);
        renderHeroMetricBox(g, x + 14 + boxW + 8, metricY, boxW, metricH, "INSTANT SELL",
                NumberFormatUtils.formatExact(sellNow), COLOR_SUCCESS, sellDelta);
        String marginStr = NumberFormatUtils.formatExact(marginNow) + " (" + String.format("%.1f%%", bz.getMarginPercentage()) + ")";
        renderHeroMetricBox(g, x + 14 + (boxW + 8) * 2, metricY, w - 28 - (boxW + 8) * 2, metricH, "MARGIN",
                marginStr, marginNow > 0 ? COLOR_SUCCESS : COLOR_DANGER, marginDelta);

        int halfW = (w - 36) / 2;
        int leftX = x + 14;
        int rightX = leftX + halfW + 8;
        int tableY = metricY + metricH + 10;
        int tableH = Math.max(120, h - (tableY - y) - 40);

        renderOrderTable(g, leftX, tableY, halfW, tableH, "BUY ORDERS", COLOR_SUCCESS, bz.getBuyOrderBook(), state.getBzOrderLimit());
        renderOrderTable(g, rightX, tableY, halfW, tableH, "SELL ORDERS", COLOR_GOLD, bz.getSellOrderBook(), state.getBzOrderLimit());

        int moreW = 128;
        int moreH = 24;
        int moreX = x + (w - moreW) / 2;
        int moreY = y + h - 32;
        boolean moreHover = isInside(mouseX, mouseY, moreX, moreY, moreW, moreH);
        if (moreHover) markHover("mk_more_bz", 2);
        OsuUiRenderer.drawRoundedRect(g, moreX, moreY, moreW, moreH, 4, moreHover ? mCol(OSU_COLOUR2) : marketCardInnerBg());
        TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD,
                state.isLoading() ? ModLang.tr("dttdrtaddons.market.loading") : ModLang.tr("dttdrtaddons.market.show_more"),
                moreX + moreW * 0.5f, moreY, moreW - 12, moreH, 13f, 8.5f, 14f,
                mCol(moreHover ? MK_TEXT_PRIMARY : MK_TEXT_SECONDARY));
    }

    private void renderOrderTable(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                  String title, int titleColor, List<BazaarOrderEntry> entries, int orderLimit) {
        OsuUiRenderer.drawRoundedRect(g, x, y, w, h, 5, marketCardInnerBg());
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, title, x + 10, y + 7, 12f, mCol(titleColor));

        int colOrdLeft = x + 10;
        int colAmtRight = x + Math.round(w * 0.36f);
        int colPriceRight = x + Math.round(w * 0.72f);
        int colTotalRight = x + w - 10;

        int headY = y + 26;
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, "ORD", colOrdLeft, headY, 11f, mCol(MK_TEXT_MUTED));
        String amtH = "AMT";
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, amtH,
                colAmtRight - TorusFontRenderer.measureWidth(Weight.SEMIBOLD, amtH, 11f), headY, 11f, mCol(MK_TEXT_MUTED));
        String unitH = "UNIT";
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, unitH,
                colPriceRight - TorusFontRenderer.measureWidth(Weight.SEMIBOLD, unitH, 11f), headY, 11f, mCol(MK_TEXT_MUTED));
        String totH = "TOTAL";
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, totH,
                colTotalRight - TorusFontRenderer.measureWidth(Weight.SEMIBOLD, totH, 11f), headY, 11f, mCol(MK_TEXT_MUTED));

        int rowY = headY + 18;
        int maxRows = Math.min(orderLimit, entries.size());
        if (maxRows == 0) {
            TorusFontRenderer.drawCenteredString(g, Weight.REGULAR, "No orders", x + w * 0.5f, rowY + 32, 13f, mCol(MK_TEXT_MUTED));
            return;
        }

        for (int i = 0; i < maxRows; i++) {
            BazaarOrderEntry e = entries.get(i);
            if (i % 2 == 1) {
                OsuUiRenderer.drawRoundedRect(g, x + 4, rowY - 1, w - 8, 20, 3, mCol(0x10FFFFFF));
            }
            if (i == 0) {
                OsuUiRenderer.drawPillF(g, x + 4, rowY + 2, 2.5f, 14f, mCol(titleColor));
            }
            TorusFontRenderer.drawString(g, Weight.REGULAR, String.valueOf(e.getOrders()),
                    colOrdLeft + (i == 0 ? 4 : 0), rowY + 2, 12f, mCol(MK_TEXT_MUTED));

            String amtStr = NumberFormatUtils.formatCompact(e.getAmount());
            float amtW = TorusFontRenderer.measureWidth(Weight.REGULAR, amtStr, 12f);
            TorusFontRenderer.drawString(g, Weight.REGULAR, amtStr, colAmtRight - amtW, rowY + 2, 12f, mCol(MK_TEXT_SECONDARY));

            String priceStr = NumberFormatUtils.formatExact(e.getPricePerUnit());
            float priceW = TorusFontRenderer.measureWidth(Weight.SEMIBOLD, priceStr, 12f);
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, priceStr, colPriceRight - priceW, rowY + 2, 12f,
                    mCol(i == 0 ? titleColor : MK_TEXT_PRIMARY));

            String eqStr = NumberFormatUtils.formatCompact(e.getCoinEquivalent());
            float eqW = TorusFontRenderer.measureWidth(Weight.REGULAR, eqStr, 12f);
            TorusFontRenderer.drawString(g, Weight.REGULAR, eqStr, colTotalRight - eqW, rowY + 2, 12f, mCol(MK_TEXT_MUTED));
            rowY += 22;
        }
    }

    private void renderPriceChart(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        MarketState state = MarketState.getInstance();
        boolean isAh = "AH".equalsIgnoreCase(state.getMode());
        List<PriceHistoryPoint> points = state.getHistoryPoints();
        String activeRange = state.getHistoryRange();

        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, "Price History", x + 14, y + 10, 15f, mCol(MK_TEXT_PRIMARY));

        int primaryColor = mCol(isAh ? OSU_COLOUR1 : COLOR_SUCCESS);
        int secondaryColor = mCol(isAh ? COLOR_SUCCESS : COLOR_GOLD);
        OsuUiRenderer.drawCircle(g, x + 122, y + 14, 7, primaryColor);
        TorusFontRenderer.drawString(g, Weight.REGULAR, isAh ? "Avg" : "Buy", x + 133, y + 11, 13f, mCol(MK_TEXT_SECONDARY));
        OsuUiRenderer.drawCircle(g, x + 170, y + 14, 7, secondaryColor);
        TorusFontRenderer.drawString(g, Weight.REGULAR, isAh ? "Min" : "Sell", x + 181, y + 11, 13f, mCol(MK_TEXT_SECONDARY));

        String[] ranges = isAh ? new String[]{"day", "week", "month"} : new String[]{"hour", "day", "week"};
        String[] labels = isAh ? new String[]{"24H", "7D", "30D"} : new String[]{"1H", "24H", "7D"};
        int btnW = 42;
        int trackW = ranges.length * btnW + 6;
        int trackX = x + w - trackW - 14;
        OsuUiRenderer.drawRoundedRect(g, trackX, y + 7, trackW, 24, 5, marketCardInnerBg());

        int btnX = trackX + 3;
        for (int i = 0; i < ranges.length; i++) {
            boolean active = ranges[i].equalsIgnoreCase(activeRange);
            boolean hover = isInside(mouseX, mouseY, btnX, y + 9, btnW, 20);
            if (hover) markHover("mk_range_" + i, 2);
            if (active) {
                OsuUiRenderer.drawRoundedRect(g, btnX, y + 9, btnW, 20, 4, mCol(OSU_COLOUR2));
            } else if (hover) {
                OsuUiRenderer.drawRoundedRect(g, btnX, y + 9, btnW, 20, 4, marketCardHoverBg());
            }
            TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD, labels[i],
                    btnX + btnW * 0.5f, y + 9, btnW - 4, 20, 12f, 8.5f, 12.5f,
                    mCol(active ? MK_TEXT_PRIMARY : MK_TEXT_MUTED));
            btnX += btnW;
        }

        int plotX = x + 78;
        int plotY = y + 38;
        int plotW = w - 92;
        int plotH = h - 62;

        OsuUiRenderer.drawRoundedRect(g, plotX, plotY, plotW, plotH, 5, marketCardInnerBg());

        if (points == null || points.size() < 2) {
            TorusFontRenderer.drawCenteredString(g, Weight.REGULAR,
                    state.isLoading() ? "Loading history..." : "No price history available",
                    plotX + plotW * 0.5f, plotY + plotH * 0.5f - 7, 14f, mCol(MK_TEXT_MUTED));
            return;
        }

        double minVal = Double.MAX_VALUE;
        double maxVal = Double.MIN_VALUE;
        for (PriceHistoryPoint pt : points) {
            if (pt.getPrimaryPrice() > 0) {
                minVal = Math.min(minVal, pt.getPrimaryPrice());
                maxVal = Math.max(maxVal, pt.getPrimaryPrice());
            }
            if (pt.getSecondaryPrice() > 0) {
                minVal = Math.min(minVal, pt.getSecondaryPrice());
                maxVal = Math.max(maxVal, pt.getSecondaryPrice());
            }
        }
        if (minVal == Double.MAX_VALUE || maxVal <= minVal) {
            maxVal = minVal + 1.0;
        }
        double pad = (maxVal - minVal) * 0.08;
        minVal = Math.max(0, minVal - pad);
        maxVal = maxVal + pad;

        for (int gr = 0; gr <= 2; gr++) {
            float frac = gr / 2.0f;
            int gy = plotY + Math.round(frac * (plotH - 8)) + 4;
            if (gr == 1) {
                fillRect(g, plotX + 4, gy, plotW - 8, 1, mCol(COLOR_GRID));
            }
            double val = maxVal - frac * (maxVal - minVal);
            String lbl = NumberFormatUtils.formatCompact(val);
            float lblW = TorusFontRenderer.measureWidth(Weight.REGULAR, lbl, 12f);
            TorusFontRenderer.drawString(g, Weight.REGULAR, lbl, plotX - lblW - 8, gy - 6, 12f, mCol(MK_TEXT_MUTED));
        }

        drawSeriesPolyline(g, points, plotX, plotY, plotW, plotH, minVal, maxVal, false, secondaryColor);
        drawSeriesPolyline(g, points, plotX, plotY, plotW, plotH, minVal, maxVal, true, primaryColor);

        TorusFontRenderer.drawString(g, Weight.REGULAR, points.get(0).getTimestampLabel(), plotX, plotY + plotH + 5, 11f, mCol(MK_TEXT_MUTED));
        String endLbl = points.get(points.size() - 1).getTimestampLabel();
        float endW = TorusFontRenderer.measureWidth(Weight.REGULAR, endLbl, 11f);
        TorusFontRenderer.drawString(g, Weight.REGULAR, endLbl, plotX + plotW - endW, plotY + plotH + 5, 11f, mCol(MK_TEXT_MUTED));

        if (isInside(mouseX, mouseY, plotX, plotY, plotW, plotH)) {
            double relX = (mouseX - plotX) / (double) plotW;
            int idx = Math.max(0, Math.min(points.size() - 1, (int) Math.round(relX * (points.size() - 1))));
            PriceHistoryPoint pt = points.get(idx);
            int px = plotX + (int) Math.round((idx / (double) (points.size() - 1)) * (plotW - 4)) + 2;
            int py1 = mapY(pt.getPrimaryPrice(), plotY, plotH, minVal, maxVal);
            int py2 = mapY(pt.getSecondaryPrice(), plotY, plotH, minVal, maxVal);

            fillRect(g, px, plotY + 2, 1, plotH - 4, mCol(0x66FFFFFF));
            OsuUiRenderer.drawCircle(g, px - 3, py2 - 3, 7, secondaryColor);
            OsuUiRenderer.drawCircle(g, px - 3, py1 - 3, 7, primaryColor);

            String l1 = pt.getTimestampLabel();
            String l2 = (isAh ? "Avg: " : "Buy: ") + NumberFormatUtils.formatExact(pt.getPrimaryPrice());
            String l3 = (isAh ? "Min: " : "Sell: ") + NumberFormatUtils.formatExact(pt.getSecondaryPrice());
            float w1 = TorusFontRenderer.measureWidth(Weight.REGULAR, l1, 12f);
            float w2 = TorusFontRenderer.measureWidth(Weight.SEMIBOLD, l2, 12f);
            float w3 = TorusFontRenderer.measureWidth(Weight.SEMIBOLD, l3, 12f);
            int tipW = Math.round(Math.max(w1, Math.max(w2, w3))) + 22;
            int tipH = 56;
            int tipX = (mouseX + 16 + tipW > plotX + plotW) ? (mouseX - tipW - 12) : (mouseX + 14);
            int tipY = Math.max(plotY + 4, Math.min(plotY + plotH - tipH - 4, mouseY - tipH / 2));

            GuiBatchHelper.flushScope(g);
            OsuUiRenderer.drawRoundedRect(g, tipX, tipY, tipW, tipH, 5, marketElevatedBg());
            OsuUiRenderer.drawRoundedBorder(g, tipX, tipY, tipW, tipH, 5, mCol(OSU_COLOUR1));
            TorusFontRenderer.drawString(g, Weight.REGULAR, l1, tipX + 10, tipY + 7, 12f, mCol(MK_TEXT_MUTED));
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, l2, tipX + 10, tipY + 23, 12f, primaryColor);
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD, l3, tipX + 10, tipY + 38, 12f, secondaryColor);
        }
    }

    private void drawSeriesPolyline(GuiGraphicsExtractor g, List<PriceHistoryPoint> points,
                                    int plotX, int plotY, int plotW, int plotH,
                                    double minVal, double maxVal, boolean primary, int color) {
        int n = points.size();
        for (int i = 0; i < n - 1; i++) {
            double v1 = primary ? points.get(i).getPrimaryPrice() : points.get(i).getSecondaryPrice();
            double v2 = primary ? points.get(i + 1).getPrimaryPrice() : points.get(i + 1).getSecondaryPrice();
            if (v1 <= 0 || v2 <= 0) continue;

            float x1 = plotX + 2 + (float) ((i / (double) (n - 1)) * (plotW - 4));
            float y1 = mapY(v1, plotY, plotH, minVal, maxVal);
            float x2 = plotX + 2 + (float) (((i + 1) / (double) (n - 1)) * (plotW - 4));
            float y2 = mapY(v2, plotY, plotH, minVal, maxVal);
            drawLineSegment(g, x1, y1, x2, y2, color);
        }
    }

    private static int mapY(double val, int plotY, int plotH, double minVal, double maxVal) {
        double norm = Math.max(0.0, Math.min(1.0, (val - minVal) / (maxVal - minVal)));
        return plotY + plotH - 4 - (int) Math.round(norm * (plotH - 8));
    }

    private static void drawLineSegment(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int color) {
        OsuUiRenderer.drawLineSegment(g, x1, y1, x2, y2, 2.0f, color);
    }

    private void renderAlertSection(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
        MarketState state = MarketState.getInstance();
        AlertLayout l = computeAlertLayout(x, y, w);

        // Header + Master Toggle Pill
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, ModLang.tr("dttdrtaddons.market.price_alert"), x, y + 2, 15f, mCol(MK_TEXT_PRIMARY));
        boolean masterHover = isInside(mouseX, mouseY, l.masterToggleX, l.masterToggleY, l.masterToggleW, l.masterToggleH);
        if (masterHover) markHover("mk_alert_toggle", 3);
        boolean active = state.isAlertEnabled();
        OsuUiRenderer.drawRoundedRect(g, l.masterToggleX, l.masterToggleY, l.masterToggleW, l.masterToggleH, 5,
                active ? mCol(OSU_COLOUR2) : (masterHover ? marketCardHoverBg() : marketCardInnerBg()));
        OsuUiRenderer.drawCircle(g, l.masterToggleX + 8, l.masterToggleY + 7, 8, mCol(active ? COLOR_SUCCESS : MK_TEXT_MUTED));
        TorusFontRenderer.drawFittedString(g, Weight.SEMIBOLD,
                ModLang.tr(active ? "dttdrtaddons.common.enabled" : "dttdrtaddons.common.paused"),
                l.masterToggleX + 21, l.masterToggleY, l.masterToggleW - 26, l.masterToggleH, 12f, 8.5f, 13f,
                mCol(active ? MK_TEXT_PRIMARY : MK_TEXT_MUTED));

        // Threshold Inputs (outer group spacing > inner label-to-input gap)
        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, "BELOW TARGET", l.minX, l.minY - 14, 11f, mCol(MK_TEXT_MUTED));
        boolean focusMin = (focusedInput == 2);
        OsuUiRenderer.drawRoundedRect(g, l.minX, l.minY, l.inputW, l.inputH, 5, marketCardInnerBg());
        if (focusMin) OsuUiRenderer.drawRoundedBorder(g, l.minX, l.minY, l.inputW, l.inputH, 5, mCol(OSU_COLOUR1));
        String minCursor = (focusMin && ((System.currentTimeMillis() - openTime) / 500) % 2 == 0) ? "|" : "";
        String minDisp = (minPriceText.isEmpty() && !focusMin) ? "e.g. 1.1b or 500k" : minPriceText + minCursor;
        TorusFontRenderer.drawString(g, Weight.REGULAR,
                TorusFontRenderer.trimToWidth(Weight.REGULAR, minDisp, l.inputW - 18, 13f),
                l.minX + 10, l.minY + 6, 13f,
                mCol((minPriceText.isEmpty() && !focusMin) ? MK_TEXT_MUTED : MK_TEXT_PRIMARY));

        TorusFontRenderer.drawString(g, Weight.SEMIBOLD, "ABOVE TARGET", l.maxX, l.maxY - 14, 11f, mCol(MK_TEXT_MUTED));
        boolean focusMax = (focusedInput == 3);
        OsuUiRenderer.drawRoundedRect(g, l.maxX, l.maxY, l.inputW, l.inputH, 5, marketCardInnerBg());
        if (focusMax) OsuUiRenderer.drawRoundedBorder(g, l.maxX, l.maxY, l.inputW, l.inputH, 5, mCol(OSU_COLOUR1));
        String maxCursor = (focusMax && ((System.currentTimeMillis() - openTime) / 500) % 2 == 0) ? "|" : "";
        String maxDisp = (maxPriceText.isEmpty() && !focusMax) ? "e.g. 1.3b or 10m" : maxPriceText + maxCursor;
        TorusFontRenderer.drawString(g, Weight.REGULAR,
                TorusFontRenderer.trimToWidth(Weight.REGULAR, maxDisp, l.inputW - 18, 13f),
                l.maxX + 10, l.maxY + 6, 13f,
                mCol((maxPriceText.isEmpty() && !focusMax) ? MK_TEXT_MUTED : MK_TEXT_PRIMARY));

        // Channel Toggles + Single Quiet Test Action
        renderAlertChannelPill(g, "mk_alert_chat", l.chatX, l.chanY, l.chanW, l.chanH, state.isNotifyChat(),
                ModLang.tr("dttdrtaddons.market.channel_chat"), mouseX, mouseY);
        renderAlertChannelPill(g, "mk_alert_sound", l.soundX, l.chanY, l.chanW, l.chanH, state.isNotifySound(),
                ModLang.tr("dttdrtaddons.market.channel_sound"), mouseX, mouseY);
        renderAlertChannelPill(g, "mk_alert_toast", l.toastX, l.chanY, l.chanW, l.chanH, state.isNotifyTitle(),
                ModLang.tr("dttdrtaddons.market.channel_toast"), mouseX, mouseY);

        boolean testHover = isInside(mouseX, mouseY, l.testX, l.testY, l.testW, l.testH);
        if (testHover) markHover("mk_alert_test", 2);
        OsuUiRenderer.drawRoundedRect(g, l.testX, l.testY, l.testW, l.testH, 4,
                testHover ? mCol(OSU_COLOUR2) : marketCardInnerBg());
        TorusFontRenderer.drawFittedCenteredString(g, Weight.SEMIBOLD, ModLang.tr("dttdrtaddons.market.test_alert"),
                l.testX + l.testW * 0.5f, l.testY, l.testW - 10, l.testH, 12f, 8.5f, 13f,
                mCol(testHover ? MK_TEXT_PRIMARY : MK_TEXT_SECONDARY));
    }

    private void renderAlertChannelPill(GuiGraphicsExtractor g, String hoverKey, int x, int y, int w, int h,
                                        boolean checked, String label, int mouseX, int mouseY) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        if (hover) markHover(hoverKey, 3);
        OsuUiRenderer.drawRoundedRect(g, x, y, w, h, 4, hover ? marketCardHoverBg() : marketCardInnerBg());
        if (checked) {
            OsuUiRenderer.drawRoundedBorder(g, x, y, w, h, 4, mCol(OSU_LIGHT4));
        }
        OsuUiRenderer.drawCircle(g, x + 8, y + (h - 8) / 2, 8, mCol(checked ? OSU_COLOUR1 : OSU_BG2));
        TorusFontRenderer.drawFittedString(g, Weight.SEMIBOLD, label,
                x + 21, y, w - 25, h, 12f, 8.5f, 13f, mCol(checked ? MK_TEXT_PRIMARY : MK_TEXT_MUTED));
    }

    private void renderFuzzyDropdown(GuiGraphicsExtractor g, int dx, int dy, int dw, int mouseX, int mouseY) {
        MarketState state = MarketState.getInstance();
        List<SkyblockItem> suggestions = state.getFuzzySuggestions();
        if (suggestions.isEmpty()) return;

        int rowH = 30;
        int dh = suggestions.size() * rowH + 8;

        GuiBatchHelper.beginScope(g);
        OsuUiRenderer.drawRoundedRect(g, dx, dy, dw, dh, 6, marketElevatedBg());
        OsuUiRenderer.drawRoundedBorder(g, dx, dy, dw, dh, 6, mCol(OSU_LIGHT4));
        for (int i = 0; i < suggestions.size(); i++) {
            int ry = dy + 4 + i * rowH;
            boolean hovered = isInside(mouseX, mouseY, dx + 4, ry, dw - 8, rowH);
            if (hovered) {
                markHover("mk_fz_" + i, 3);
                OsuUiRenderer.drawRoundedRect(g, dx + 4, ry, dw - 8, rowH, 4, marketCardHoverBg());
            }
        }
        GuiBatchHelper.endScope(g);

        for (int i = 0; i < suggestions.size(); i++) {
            int ry = dy + 4 + i * rowH;
            ClassicTextureRegistry.drawSkyblockIcon(g, suggestions.get(i), dx + 8, ry + 4, 22, mCol(0xFFFFFFFF));
        }

        GuiBatchHelper.beginScope(g);
        for (int i = 0; i < suggestions.size(); i++) {
            SkyblockItem item = suggestions.get(i);
            int ry = dy + 4 + i * rowH;
            TorusFontRenderer.drawString(g, Weight.SEMIBOLD,
                    TorusFontRenderer.trimToWidth(Weight.SEMIBOLD, item.getName(), dw - 140, 14f),
                    dx + 38, ry + 8, 14f, mCol(item.getTierColor()));
            String tag = item.getTier();
            float tagW = TorusFontRenderer.measureWidth(Weight.REGULAR, tag, 12f);
            TorusFontRenderer.drawString(g, Weight.REGULAR, tag, dx + dw - tagW - 12, ry + 9, 12f, mCol(MK_TEXT_MUTED));
        }
        GuiBatchHelper.endScope(g);
    }

    // ==========================================
    // Mouse & Keyboard Events (all converted to 1080p virtual coordinates)
    // ==========================================
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double vx = toVirtX(mouseX);
        double vy = toVirtY(mouseY);
        int virtW = getVirtW();
        int virtH = getVirtH();

        if (vx >= 0 && vx <= DRAWER_W) {
            if (debugLogBoxW > 0 && debugLogMaxScroll > 0.5
                    && isInside(vx, vy, debugLogBoxX, debugLogBoxY, debugLogBoxW, debugLogBoxH)) {
                debugLogScroll = Math.max(0.0, Math.min(debugLogMaxScroll, debugLogScroll - verticalAmount * 45.0));
                debugLogAutoFollow = (debugLogScroll >= debugLogMaxScroll - 2.0);
                return true;
            }
            if (drawerMaxScroll > 0.5) {
                lockedFocusSectionIdx = -1;
                scrollTarget = Math.max(0.0, Math.min(drawerMaxScroll, scrollTarget - verticalAmount * 100.0));
                return true;
            }
        }
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.showMarketHud) {
            float mScale = getMarketScale();
            int bx = DRAWER_W + 16;
            int by = 16;
            int availH = Math.max(200, virtH - 32);
            int logicalW = getMarketLogicalW(virtW, mScale);
            int logicalH = Math.max(200, Math.round(availH / mScale));
            int visW = Math.round(logicalW * mScale);
            int visH = Math.round(logicalH * mScale);
            double maxMScroll = getMarketMaxScroll(MarketState.getInstance(), logicalW - 36, logicalH);
            if (maxMScroll > 0.5 && isInside(vx, vy, bx, by, visW, visH)) {
                marketScrollTarget = Math.max(0.0, Math.min(maxMScroll, marketScrollTarget - verticalAmount * 84.0));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isInsideWindow) {
        if (closingDrawer) return true;
        capturingKeybindId = 0;
        boolean handled = handleMouseClicked(click, isInsideWindow);
        syncNativeTextInput();
        return handled;
    }

    private boolean handleMouseClicked(MouseButtonEvent click, boolean isInsideWindow) {
        double vx = toVirtX(click.x());
        double vy = toVirtY(click.y());
        int virtW = getVirtW();
        int virtH = getVirtH();
        ModConfig cfg = ModConfig.getInstance();
        MarketState state = MarketState.getInstance();
        boolean searching = settingsSearchText != null && !settingsSearchText.trim().isEmpty();

        // 1. Inside 570px osu!lazer Drawer
        if (vx >= 0 && vx <= DRAWER_W) {
            if (vx <= SIDEBAR_W) {
                int btnY = TOP_LINE_H + 14;
                int btnH = 46;
                for (int i = 0; i < SECTIONS.length; i++) {
                    if (isInside(vx, vy, 0, btnY, SIDEBAR_W, btnH)) {
                        activeSectionIdx = i;
                        lockedFocusSectionIdx = i;
                        scrollTarget = Math.max(0.0, Math.min(drawerMaxScroll, sectionStartY[i]));
                        focusedInput = 0;
                        OsuSoundEngine.playSidebarSelect();
                        return true;
                    }
                    btnY += btnH;
                }
                focusedInput = 0;
                return true;
            }

            int scrollPx = (int) Math.round(scrollCurrent);
            int headerShift = Math.min(EXPANDABLE_HEADER_H, scrollPx);
            int stickyHeaderH = (EXPANDABLE_HEADER_H - headerShift) + FIXED_SEARCH_H;
            int barTop = TOP_LINE_H + stickyHeaderH;
            if (drawerMaxScroll > 0.5 && isInside(vx, vy, DRAWER_W - 10, barTop, 10, virtH - barTop)) {
                draggingDrawerScrollbar = true;
                lockedFocusSectionIdx = -1;
                double frac = Math.max(0.0, Math.min(1.0, (vy - barTop) / (double) Math.max(1, virtH - barTop)));
                scrollTarget = frac * drawerMaxScroll;
                scrollCurrent = scrollTarget;
                OsuSoundEngine.playDefaultSelect();
                return true;
            }

            // Always allow focusing top Search Box
            for (InputHitbox inp : drawerInputs) {
                if (inp.sectionIdx == -1 && isInside(vx, vy, inp.x, inp.y, inp.w, inp.h)) {
                    focusedInput = inp.id;
                    OsuSoundEngine.playDefaultSelect();
                    return true;
                }
            }

            // First click on an inactive dimmed section focuses & scrolls to that section cleanly
            if (!searching && vy >= barTop) {
                for (int i = 0; i < SECTIONS.length; i++) {
                    if (i != activeSectionIdx && vy >= sectionScreenTop[i] && vy <= sectionScreenBot[i]) {
                        activeSectionIdx = i;
                        lockedFocusSectionIdx = i;
                        scrollTarget = Math.max(0.0, Math.min(drawerMaxScroll, sectionStartY[i]));
                        focusedInput = 0;
                        OsuSoundEngine.playDefaultSelect();
                        return true;
                    }
                }
            }

            for (InputHitbox inp : drawerInputs) {
                if ((searching || inp.sectionIdx == activeSectionIdx) && isInside(vx, vy, inp.x, inp.y, inp.w, inp.h)) {
                    focusedInput = inp.id;
                    OsuSoundEngine.playDefaultSelect();
                    return true;
                }
            }

            for (SliderHitbox s : drawerSliders) {
                if ((searching || s.sectionIdx == activeSectionIdx) && isInside(vx, vy, s.x, s.y, s.w, s.h)) {
                    draggingSlider = s.id;
                    double frac = Math.max(0.0, Math.min(1.0, (vx - s.x) / (double) s.w));
                    applySliderFraction(s.id, frac, cfg);
                    ModConfig.save();
                    return true;
                }
            }

            if (palSvSec >= 0 && (searching || palSvSec == activeSectionIdx)) {
                if (isInside(vx, vy, palSvX, palSvY, palSvW, palSvH)) {
                    draggingPaletteMode = 1;
                    applyPaletteDrag(vx, vy, cfg);
                    ModConfig.save();
                    return true;
                }
                if (isInside(vx, vy, palHueX, palHueY, palHueW, palHueH)) {
                    draggingPaletteMode = 2;
                    applyPaletteDrag(vx, vy, cfg);
                    ModConfig.save();
                    return true;
                }
            }

            if (namePalSvSec >= 0 && (searching || namePalSvSec == activeSectionIdx)) {
                if (isInside(vx, vy, namePalSvX, namePalSvY, namePalSvW, namePalSvH)) {
                    draggingPaletteMode = 3;
                    applyPaletteDrag(vx, vy, cfg);
                    ModConfig.save();
                    return true;
                }
                if (isInside(vx, vy, namePalHueX, namePalHueY, namePalHueW, namePalHueH)) {
                    draggingPaletteMode = 4;
                    applyPaletteDrag(vx, vy, cfg);
                    ModConfig.save();
                    return true;
                }
            }

            if (handleToggleHit(vx, vy, true)) {
                return true;
            }

            for (ButtonHitbox b : drawerButtons) {
                if ((searching || b.sectionIdx == activeSectionIdx) && isInside(vx, vy, b.x, b.y, b.w, b.h)) {
                    OsuSoundEngine.playButtonSelect();
                    b.action.run();
                    return true;
                }
            }

            focusedInput = 0;
            return true;
        }

        // 2. Inside Right-Side Scaled Market HUD
        if (cfg.showMarketHud && marketHudAlpha > 0.5f) {
            float mScale = getMarketScale();
            int bx = DRAWER_W + 16;
            int by = 16;
            int availW = Math.max(360, virtW - bx - 16);
            int availH = Math.max(200, virtH - 32);
            int logicalW = Math.max(360, Math.round(availW / mScale));
            int logicalH = Math.max(200, Math.round(availH / mScale));
            int visW = Math.round(logicalW * mScale);
            int visH = Math.round(logicalH * mScale);

            if (isInside(vx, vy, bx, by, visW, visH)) {
                int cw = logicalW - 36;
                double maxMScroll = getMarketMaxScroll(state, cw, logicalH);
                if (maxMScroll > 0.5 && isInside(vx, vy, bx + visW - 12, by + 12, 12, visH - 24)) {
                    draggingMarketScrollbar = true;
                    double frac = Math.max(0.0, Math.min(1.0, (vy - (by + 12)) / (double) Math.max(1, visH - 24)));
                    marketScrollTarget = frac * maxMScroll;
                    marketScrollOffset = marketScrollTarget;
                    return true;
                }

                double mx = bx + (vx - bx) / mScale;
                double my = by + (vy - by) / mScale + marketScrollOffset;
                int cx = bx + 16;
                int cy = by + 16;
                return handleMarketClick(mx, my, cx, cy, cw, state, click, isInsideWindow);
            }
        }

        this.focusedInput = 0;
        return super.mouseClicked(click, isInsideWindow);
    }

    private boolean handleMarketClick(double mx, double my, int cx, int cy, int cw, MarketState state,
                                      MouseButtonEvent click, boolean isInsideWindow) {
        if (focusedInput == 1 && !state.getFuzzySuggestions().isEmpty()) {
            int dy = cy + 44 + 40;
            int rowH = 30;
            List<SkyblockItem> suggestions = state.getFuzzySuggestions();
            for (int i = 0; i < suggestions.size(); i++) {
                int ry = dy + 4 + i * rowH;
                if (isInside(mx, my, cx + 4, ry, cw - 8, rowH)) {
                    SkyblockItem chosen = suggestions.get(i);
                    this.searchInputText = chosen.getName();
                    state.selectItem(chosen);
                    this.focusedInput = 0;
                    OsuSoundEngine.playDefaultSelect();
                    return true;
                }
            }
        }

        int segW = Math.min(300, cw - 118);
        int segH = 36;
        int tabW = (segW - 6) / 2;
        int ahX = cx + 3;
        int bzX = ahX + tabW;
        if (isInside(mx, my, ahX, cy + 3, tabW, segH - 6)) {
            if (!"AH".equalsIgnoreCase(state.getMode())) {
                this.marketModeContentAlpha = 0.08f;
                state.setMode("AH");
                this.searchInputText = state.getSearchQuery();
            }
            this.focusedInput = 0;
            OsuSoundEngine.playButtonSelect();
            return true;
        }
        if (isInside(mx, my, bzX, cy + 3, tabW, segH - 6)) {
            if (!"BZ".equalsIgnoreCase(state.getMode())) {
                this.marketModeContentAlpha = 0.08f;
                state.setMode("BZ");
                this.searchInputText = state.getSearchQuery();
            }
            this.focusedInput = 0;
            OsuSoundEngine.playButtonSelect();
            return true;
        }
        int refW = 108;
        if (isInside(mx, my, cx + cw - refW, cy, refW, segH)) {
            OsuSoundEngine.playButtonSelect();
            if (state.canManualRefresh()) state.refreshDataAsync(true);
            return true;
        }

        int searchY = cy + 44;
        int searchH = 38;
        if (!searchInputText.isEmpty() && isInside(mx, my, cx + cw - 30, searchY + 5, 26, 28)) {
            this.searchInputText = "";
            state.updateSearchQuery("");
            this.focusedInput = 1;
            OsuSoundEngine.playKeyDelete();
            return true;
        }
        if (isInside(mx, my, cx, searchY, cw, searchH)) {
            if (!WhitelistManager.isAuthorized()) {
                this.focusedInput = 0;
                return true;
            }
            this.focusedInput = 1;
            OsuSoundEngine.playDefaultSelect();
            return true;
        }

        if (state.getSelectedItem() == null) {
            for (RecentChipHitbox chip : marketRecentHitboxes) {
                if (isInside(mx, my, chip.x, chip.y, chip.w, chip.h)) {
                    if (!WhitelistManager.isAuthorized()) {
                        return true;
                    }
                    this.searchInputText = chip.item.getName();
                    state.selectItem(chip.item);
                    this.focusedInput = 0;
                    OsuSoundEngine.playButtonSelect();
                    return true;
                }
            }
            this.focusedInput = 0;
            return true;
        }

        int bodyY = searchY + 48;
        int dataH = getMarketDataHeight(state);
        if ("AH".equalsIgnoreCase(state.getMode())) {
            List<AhItemListing> bins = state.getLowestBins();
            if (bins != null && !bins.isEmpty()) {
                int maxRows = Math.min(state.getAhDisplayLimit(), bins.size());
                int rowY = bodyY + 36 + 50 + 10 + 18;
                int copyW = 54;
                int copyX = cx + cw - copyW - 14;
                for (int i = 0; i < maxRows; i++) {
                    if (isInside(mx, my, copyX, rowY + 2, copyW, 20)) {
                        String uuid = bins.get(i).getAuctionUuid();
                        if (this.minecraft != null && uuid != null && !uuid.isEmpty()) {
                            this.minecraft.keyboardHandler.setClipboard("/viewauction " + uuid);
                            NotificationUtils.sendClientChatMessage(Component.literal("§aCopied command: §e/viewauction " + uuid));
                            NotificationUtils.playAlertSound();
                        }
                        OsuSoundEngine.playButtonSelect();
                        return true;
                    }
                    rowY += 26;
                }
                int moreW = 128;
                int moreX = cx + (cw - moreW) / 2;
                int moreY = bodyY + dataH - 32;
                if (isInside(mx, my, moreX, moreY, moreW, 24)) {
                    OsuSoundEngine.playButtonSelect();
                    state.loadMoreAhAsync();
                    return true;
                }
            }
        } else if (state.getBazaarSummary() != null) {
            int moreW = 128;
            int moreX = cx + (cw - moreW) / 2;
            int moreY = bodyY + dataH - 32;
            if (isInside(mx, my, moreX, moreY, moreW, 24)) {
                OsuSoundEngine.playButtonSelect();
                state.loadMoreBzAsync();
                return true;
            }
        }

        int chartY = bodyY + dataH + MK_CARD_GAP;
        boolean isAh = "AH".equalsIgnoreCase(state.getMode());
        String[] ranges = isAh ? new String[]{"day", "week", "month"} : new String[]{"hour", "day", "week"};
        int btnW = 42;
        int trackW = ranges.length * btnW + 6;
        int btnX = cx + cw - trackW - 14 + 3;
        for (String r : ranges) {
            if (isInside(mx, my, btnX, chartY + 9, btnW, 20)) {
                OsuSoundEngine.playButtonSelect();
                state.setHistoryRange(r);
                return true;
            }
            btnX += btnW;
        }

        int alertY = chartY + MK_CHART_H + MK_CARD_GAP + 12;
        AlertLayout l = computeAlertLayout(cx + 14, alertY, cw - 28);
        if (isInside(mx, my, l.masterToggleX, l.masterToggleY, l.masterToggleW, l.masterToggleH)) {
            return resolveDragToggle("alert_enabled", state.isAlertEnabled(), true, state::setAlertEnabled);
        }
        if (isInside(mx, my, l.chatX, l.chanY, l.chanW, l.chanH)) {
            return resolveDragToggle("alert_chat", state.isNotifyChat(), true, state::setNotifyChat);
        }
        if (isInside(mx, my, l.soundX, l.chanY, l.chanW, l.chanH)) {
            return resolveDragToggle("alert_sound", state.isNotifySound(), true, state::setNotifySound);
        }
        if (isInside(mx, my, l.toastX, l.chanY, l.chanW, l.chanH)) {
            return resolveDragToggle("alert_toast", state.isNotifyTitle(), true, state::setNotifyTitle);
        }
        if (isInside(mx, my, l.testX, l.testY, l.testW, l.testH)) {
            OsuSoundEngine.playButtonSelect();
            SkyblockItem item = state.getSelectedItem();
            String itemName = item != null ? item.getName() : "Hyperion";
            boolean triggeredAny = false;
            if (state.isNotifyChat()) {
                NotificationUtils.sendAhPriceAlert(itemName, 500_000_000L, "below", 550_000_000L, "example-uuid-test");
                triggeredAny = true;
            }
            if (state.isNotifySound()) {
                NotificationUtils.playAlertSound();
                triggeredAny = true;
            }
            if (state.isNotifyTitle() || !triggeredAny) {
                NotificationUtils.showOverlayToast(itemName, "Test Alert: 500,000,000 Coins");
            }
            return true;
        }
        if (isInside(mx, my, l.minX, l.minY, l.inputW, l.inputH)) {
            this.focusedInput = 2;
            OsuSoundEngine.playDefaultSelect();
            return true;
        }
        if (isInside(mx, my, l.maxX, l.maxY, l.inputW, l.inputH)) {
            this.focusedInput = 3;
            OsuSoundEngine.playDefaultSelect();
            return true;
        }

        this.focusedInput = 0;
        return super.mouseClicked(click, isInsideWindow);
    }

    private boolean resolveDragToggle(String key, boolean currentValue, boolean isInitialClick, Consumer<Boolean> setter) {
        if (isInitialClick) {
            boolean next = !currentValue;
            dragToggleActive = true;
            dragToggleTargetState = next;
            dragToggledKeys.clear();
            dragToggledKeys.add(key);
            applyToggleWithSound(key, next, setter);
            ModConfig.save();
            return true;
        }
        if (dragToggleActive && !dragToggledKeys.contains(key)) {
            dragToggledKeys.add(key);
            applyToggleWithSound(key, dragToggleTargetState, setter);
            ModConfig.save();
            return true;
        }
        return false;
    }

    private static void applyToggleWithSound(String key, boolean next, Consumer<Boolean> setter) {
        if ("ui_sounds_enabled".equals(key)) {
            if (next) {
                setter.accept(true);
                OsuSoundEngine.playToggle(true);
            } else {
                OsuSoundEngine.playToggle(false);
                setter.accept(false);
            }
        } else {
            setter.accept(next);
            OsuSoundEngine.playToggle(next);
        }
    }

    private boolean handleToggleHit(double vx, double vy, boolean isInitialClick) {
        boolean searching = settingsSearchText != null && !settingsSearchText.trim().isEmpty();
        for (ToggleHitbox t : drawerToggles) {
            if ((searching || t.sectionIdx == activeSectionIdx) && isInside(vx, vy, t.x, t.y, t.w, t.h)) {
                return resolveDragToggle(t.key, t.value, isInitialClick, t.setter);
            }
        }
        if (!isInitialClick && dragToggleActive && ModConfig.getInstance().showMarketHud) {
            float mScale = getMarketScale();
            int bx = DRAWER_W + 16;
            int by = 16;
            int availW = Math.max(360, getVirtW() - bx - 16);
            int availH = Math.max(200, getVirtH() - 32);
            int visW = Math.round(Math.max(360, Math.round(availW / mScale)) * mScale);
            int visH = Math.round(Math.max(200, Math.round(availH / mScale)) * mScale);
            if (isInside(vx, vy, bx, by, visW, visH)) {
                MarketState state = MarketState.getInstance();
                if (state.getSelectedItem() == null) return false;
                double pmx = bx + (vx - bx) / mScale;
                double pmy = by + (vy - by) / mScale + marketScrollOffset;
                int cw = Math.max(360, Math.round(availW / mScale)) - 36;
                int dataH = getMarketDataHeight(state);
                int alertY = by + 16 + 44 + 48 + dataH + MK_CARD_GAP + MK_CHART_H + MK_CARD_GAP + 12;
                AlertLayout l = computeAlertLayout(bx + 16 + 14, alertY, cw - 28);
                if (isInside(pmx, pmy, l.masterToggleX, l.masterToggleY, l.masterToggleW, l.masterToggleH)) {
                    return resolveDragToggle("alert_enabled", state.isAlertEnabled(), false, state::setAlertEnabled);
                }
                if (isInside(pmx, pmy, l.chatX, l.chanY, l.chanW, l.chanH)) {
                    return resolveDragToggle("alert_chat", state.isNotifyChat(), false, state::setNotifyChat);
                }
                if (isInside(pmx, pmy, l.soundX, l.chanY, l.chanW, l.chanH)) {
                    return resolveDragToggle("alert_sound", state.isNotifySound(), false, state::setNotifySound);
                }
                if (isInside(pmx, pmy, l.toastX, l.chanY, l.chanW, l.chanH)) {
                    return resolveDragToggle("alert_toast", state.isNotifyTitle(), false, state::setNotifyTitle);
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (draggingSlider != 0) {
            draggingSlider = 0;
            OsuSoundEngine.resetSliderTickState();
            ModConfig.save();
        }
        if (draggingPaletteMode != 0) {
            draggingPaletteMode = 0;
            ModConfig.save();
        }
        draggingDrawerScrollbar = false;
        draggingMarketScrollbar = false;
        if (dragToggleActive) {
            dragToggleActive = false;
            dragToggledKeys.clear();
            ModConfig.save();
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        boolean handled = handleKeyPressed(input);
        syncNativeTextInput();
        return handled;
    }

    private boolean handleKeyPressed(KeyEvent input) {
        if (capturingKeybindId != 0) {
            InputConstants.Key targetKey = input.isEscape()
                    ? InputConstants.UNKNOWN
                    : InputConstants.getKey(input);
            if (capturingKeybindId == 1) {
                DttdrtAddons.setOpenMenuKey(targetKey);
            } else if (capturingKeybindId == 2) {
                DttdrtAddons.setQuickVoteKey(targetKey);
            } else if (capturingKeybindId == 3) {
                DttdrtAddons.setQuickVoteYesKey(targetKey);
            } else if (capturingKeybindId == 4) {
                DttdrtAddons.setQuickVoteNoKey(targetKey);
            } else if (capturingKeybindId == 6) {
                DttdrtAddons.setFreecamKey(targetKey);
            } else if (capturingKeybindId == 7) {
                DttdrtAddons.setGemstoneEspKey(targetKey);
            }
            capturingKeybindId = 0;
            if (input.isEscape()) {
                OsuSoundEngine.playKeyDelete();
            } else {
                OsuSoundEngine.playKeyConfirm();
            }
            return true;
        }
        int key = input.key();
        boolean ctrl = input.hasControlDown();

        if (input.isEscape()) {
            if (focusedInput == 7) {
                if (!settingsSearchText.isEmpty()) {
                    settingsSearchText = "";
                    searchLastLen = 0;
                    searchLastCharMs = 0L;
                    scrollTarget = 0.0;
                    return true;
                }
            } else if (focusedInput != 0) {
                focusedInput = 0;
                return true;
            }
            this.onClose();
            return true;
        }

        if (focusedInput == 7) {
            if (input.isSelectAll() || (ctrl && key == InputConstants.KEY_BACKSPACE)) {
                settingsSearchText = "";
                searchLastLen = 0;
                searchLastCharMs = 0L;
                scrollTarget = 0.0;
                OsuSoundEngine.playKeySelectAll();
                return true;
            }
            if (input.isPaste() && this.minecraft != null) {
                String clip = this.minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    settingsSearchText += clip.replace("\n", "").replace("\r", "");
                    searchLastCharMs = System.currentTimeMillis();
                    searchLastLen = settingsSearchText.length();
                    scrollTarget = 0.0;
                    OsuSoundEngine.playKeyAdd(clip.charAt(0));
                }
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                if (!settingsSearchText.isEmpty()) {
                    settingsSearchText = settingsSearchText.substring(0, settingsSearchText.length() - 1);
                    searchLastLen = settingsSearchText.length();
                    searchLastCharMs = 0L;
                    scrollTarget = 0.0;
                    OsuSoundEngine.playKeyDelete();
                }
                return true;
            }
            if (input.isConfirmation()) {
                focusedInput = 0;
                OsuSoundEngine.playKeyConfirm();
                return true;
            }
        } else if (focusedInput == 1) {
            if (!WhitelistManager.isAuthorized()) {
                focusedInput = 0;
                return super.keyPressed(input);
            }
            if (input.isSelectAll() || (ctrl && key == InputConstants.KEY_BACKSPACE)) {
                searchInputText = "";
                MarketState.getInstance().updateSearchQuery("");
                OsuSoundEngine.playKeySelectAll();
                return true;
            }
            if (input.isPaste() && this.minecraft != null) {
                String clip = this.minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    searchInputText += clip.replace("\n", "").replace("\r", "");
                    MarketState.getInstance().updateSearchQuery(searchInputText);
                    OsuSoundEngine.playKeyAdd(clip.charAt(0));
                }
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                if (!searchInputText.isEmpty()) {
                    searchInputText = searchInputText.substring(0, searchInputText.length() - 1);
                    MarketState.getInstance().updateSearchQuery(searchInputText);
                    OsuSoundEngine.playKeyDelete();
                }
                return true;
            }
            if (input.isConfirmation()) {
                MarketState state = MarketState.getInstance();
                if (!state.getFuzzySuggestions().isEmpty()) {
                    SkyblockItem first = state.getFuzzySuggestions().get(0);
                    searchInputText = first.getName();
                    state.selectItem(first);
                }
                focusedInput = 0;
                OsuSoundEngine.playKeyConfirm();
                return true;
            }
        } else if (focusedInput == 2) {
            if (input.isSelectAll()) {
                minPriceText = "";
                MarketState.getInstance().setMinPriceInput("");
                OsuSoundEngine.playKeySelectAll();
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!minPriceText.isEmpty()) {
                    minPriceText = minPriceText.substring(0, minPriceText.length() - 1);
                    MarketState.getInstance().setMinPriceInput(minPriceText);
                    OsuSoundEngine.playKeyDelete();
                }
                return true;
            }
            if (input.isConfirmation() || input.isCycleFocus()) {
                focusedInput = 3;
                OsuSoundEngine.playKeyConfirm();
                return true;
            }
        } else if (focusedInput == 3) {
            if (input.isSelectAll()) {
                maxPriceText = "";
                MarketState.getInstance().setMaxPriceInput(maxPriceText);
                OsuSoundEngine.playKeySelectAll();
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                if (!maxPriceText.isEmpty()) {
                    maxPriceText = maxPriceText.substring(0, maxPriceText.length() - 1);
                    MarketState.getInstance().setMaxPriceInput(maxPriceText);
                    OsuSoundEngine.playKeyDelete();
                }
                return true;
            }
            if (input.isConfirmation()) {
                focusedInput = 0;
                OsuSoundEngine.playKeyConfirm();
                return true;
            }
        } else if (focusedInput == 4 || focusedInput == 5 || focusedInput == 6) {
            if (input.isSelectAll()) {
                if (focusedInput == 4) chXText = "";
                else if (focusedInput == 5) chYText = "";
                else chZText = "";
                OsuSoundEngine.playKeySelectAll();
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE) {
                boolean deleted = false;
                if (focusedInput == 4 && !chXText.isEmpty()) { chXText = chXText.substring(0, chXText.length() - 1); deleted = true; }
                else if (focusedInput == 5 && !chYText.isEmpty()) { chYText = chYText.substring(0, chYText.length() - 1); deleted = true; }
                else if (focusedInput == 6 && !chZText.isEmpty()) { chZText = chZText.substring(0, chZText.length() - 1); deleted = true; }
                if (deleted) OsuSoundEngine.playKeyDelete();
                return true;
            }
            if (input.isCycleFocus()) {
                focusedInput = focusedInput == 6 ? 4 : focusedInput + 1;
                OsuSoundEngine.playDefaultSelect();
                return true;
            }
            if (input.isConfirmation()) {
                focusedInput = 0;
                OsuSoundEngine.playKeyConfirm();
                return true;
            }
        } else if (focusedInput == 8) {
            ModConfig cfg = ModConfig.getInstance();
            if (input.isSelectAll() || (ctrl && key == InputConstants.KEY_BACKSPACE)) {
                cfg.mineshaftStreakMessage = "";
                ModConfig.save();
                OsuSoundEngine.playKeySelectAll();
                return true;
            }
            if (input.isPaste() && this.minecraft != null) {
                String clip = this.minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    String cur = cfg.mineshaftStreakMessage != null ? cfg.mineshaftStreakMessage : "";
                    String next = cur + clip.replace("\n", "").replace("\r", "");
                    cfg.mineshaftStreakMessage = next.length() > 120 ? next.substring(0, 120) : next;
                    ModConfig.save();
                    OsuSoundEngine.playKeyAdd(clip.charAt(0));
                }
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                if (cfg.mineshaftStreakMessage != null && !cfg.mineshaftStreakMessage.isEmpty()) {
                    cfg.mineshaftStreakMessage = cfg.mineshaftStreakMessage.substring(0, cfg.mineshaftStreakMessage.length() - 1);
                    ModConfig.save();
                    OsuSoundEngine.playKeyDelete();
                }
                return true;
            }
            if (input.isConfirmation()) {
                focusedInput = 0;
                ModConfig.save();
                OsuSoundEngine.playKeyConfirm();
                return true;
            }
        } else if (focusedInput == 9) {
            ModConfig cfg = ModConfig.getInstance();
            if (input.isSelectAll() || (ctrl && key == InputConstants.KEY_BACKSPACE)) {
                cfg.customNameText = "";
                ModConfig.save();
                OsuSoundEngine.playKeySelectAll();
                return true;
            }
            if (input.isPaste() && this.minecraft != null) {
                String clip = this.minecraft.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    String cur = cfg.customNameText != null ? cfg.customNameText : "";
                    String next = cur + clip.replace("\n", "").replace("\r", "");
                    cfg.customNameText = next.length() > 32 ? next.substring(0, 32) : next;
                    ModConfig.save();
                    OsuSoundEngine.playKeyAdd(clip.charAt(0));
                }
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                if (cfg.customNameText != null && !cfg.customNameText.isEmpty()) {
                    cfg.customNameText = cfg.customNameText.substring(0, cfg.customNameText.length() - 1);
                    ModConfig.save();
                    OsuSoundEngine.playKeyDelete();
                }
                return true;
            }
            if (input.isConfirmation()) {
                focusedInput = 0;
                ModConfig.save();
                OsuSoundEngine.playKeyConfirm();
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (capturingKeybindId != 0) {
            return true;
        }
        if (!input.isAllowedChatCharacter()) {
            return super.charTyped(input);
        }
        String chars = input.codepointAsString();
        if (chars == null || chars.isEmpty()) {
            return super.charTyped(input);
        }

        if (focusedInput == 7) {
            settingsSearchText += chars;
            searchLastCharMs = System.currentTimeMillis();
            searchLastLen = settingsSearchText.length();
            scrollTarget = 0.0;
            OsuSoundEngine.playKeyAdd(chars.charAt(0));
            return true;
        } else if (focusedInput == 1) {
            if (!WhitelistManager.isAuthorized()) {
                focusedInput = 0;
                return super.charTyped(input);
            }
            searchInputText += chars;
            MarketState.getInstance().updateSearchQuery(searchInputText);
            OsuSoundEngine.playKeyAdd(chars.charAt(0));
            return true;
        } else if (focusedInput == 2) {
            minPriceText += chars;
            MarketState.getInstance().setMinPriceInput(minPriceText);
            OsuSoundEngine.playKeyAdd(chars.charAt(0));
            return true;
        } else if (focusedInput == 3) {
            maxPriceText += chars;
            MarketState.getInstance().setMaxPriceInput(maxPriceText);
            OsuSoundEngine.playKeyAdd(chars.charAt(0));
            return true;
        } else if (focusedInput == 4 || focusedInput == 5 || focusedInput == 6) {
            StringBuilder filtered = new StringBuilder();
            for (int i = 0; i < chars.length(); i++) {
                char c = chars.charAt(i);
                if (Character.isDigit(c) || c == '-' || c == '.') {
                    filtered.append(c);
                }
            }
            if (filtered.length() > 0) {
                if (focusedInput == 4 && chXText.length() < 8) chXText += filtered;
                else if (focusedInput == 5 && chYText.length() < 8) chYText += filtered;
                else if (focusedInput == 6 && chZText.length() < 8) chZText += filtered;
                OsuSoundEngine.playKeyAdd(filtered.charAt(0));
            } else {
                OsuSoundEngine.playKeyInvalid();
            }
            return true;
        } else if (focusedInput == 8) {
            ModConfig cfg = ModConfig.getInstance();
            String cur = cfg.mineshaftStreakMessage != null ? cfg.mineshaftStreakMessage : "";
            if (cur.length() < 120) {
                String next = cur + chars;
                cfg.mineshaftStreakMessage = next.length() > 120 ? next.substring(0, 120) : next;
                ModConfig.save();
                OsuSoundEngine.playKeyAdd(chars.charAt(0));
            } else {
                OsuSoundEngine.playKeyInvalid();
            }
            return true;
        } else if (focusedInput == 9) {
            ModConfig cfg = ModConfig.getInstance();
            String cur = cfg.customNameText != null ? cfg.customNameText : "";
            if (cur.length() < 32) {
                String next = cur + chars;
                cfg.customNameText = next.length() > 32 ? next.substring(0, 32) : next;
                ModConfig.save();
                OsuSoundEngine.playKeyAdd(chars.charAt(0));
            } else {
                OsuSoundEngine.playKeyInvalid();
            }
            return true;
        }

        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        capturingKeybindId = 0;
        applyNativeTextInput(false);
        if (!closingDrawer) {
            ModConfig.save();
            closingDrawer = true;
            closeStartMs = System.currentTimeMillis();
            marketAlphaAtClose = marketHudAlpha;
            OsuSoundEngine.playPopOut();
        }
    }

    @Override
    public void removed() {
        capturingKeybindId = 0;
        draggingSlider = 0;
        draggingDrawerScrollbar = false;
        draggingMarketScrollbar = false;
        dragToggleActive = false;
        dragToggledKeys.clear();
        OsuSoundEngine.resetSliderTickState();
        applyNativeTextInput(false);
        super.removed();
    }

    private void syncNativeTextInput() {
        boolean shouldEnable = !closingDrawer && capturingKeybindId == 0 && focusedInput != 0;
        if (shouldEnable != nativeTextInputActive) {
            applyNativeTextInput(shouldEnable);
        }
    }

    private void applyNativeTextInput(boolean enable) {
        if (this.minecraft == null) {
            nativeTextInputActive = false;
            return;
        }
        if (!textInputReflectInit) {
            textInputReflectInit = true;
            try {
                mcTextInputManagerMethod = this.minecraft.getClass().getMethod("textInputManager");
                Class<?> timClass = mcTextInputManagerMethod.getReturnType();
                timOnFocusChangeMethod = timClass.getMethod("onTextInputFocusChange", Object.class, boolean.class);
            } catch (Throwable ignored) {
                mcTextInputManagerMethod = null;
                timOnFocusChangeMethod = null;
            }
        }
        nativeTextInputActive = enable;
        if (mcTextInputManagerMethod != null && timOnFocusChangeMethod != null) {
            try {
                Object tim = mcTextInputManagerMethod.invoke(this.minecraft);
                if (tim != null) {
                    timOnFocusChangeMethod.invoke(tim, this, enable);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void fillRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        OsuUiRenderer.fillRect(g, x, y, w, h, color);
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
