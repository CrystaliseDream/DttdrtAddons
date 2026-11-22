package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.core.lang.ModLang;
import com.dttdrt.addons.core.log.ModLogManager;
import com.dttdrt.addons.util.NotificationUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MelodySky-style Gemstone ESP for Crystal Hollows (100% local, zero packets).
 * Scans loaded chunks within player radius at configurable intervals and renders
 * through-wall solid + outlined cuboids via MC 26.1.2 Gizmos.
 */
public class GemstoneEspFeature implements Feature {
    public enum Gemstone {
        JADE("Jade", 157, 249, 32, Blocks.LIME_STAINED_GLASS, Blocks.LIME_STAINED_GLASS_PANE),
        AMBER("Amber", 237, 139, 35, Blocks.ORANGE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS_PANE),
        TOPAZ("Topaz", 249, 215, 36, Blocks.YELLOW_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS_PANE),
        SAPPHIRE("Sapphire", 60, 121, 224, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE),
        AMETHYST("Amethyst", 137, 0, 201, Blocks.PURPLE_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS_PANE),
        JASPER("Jasper", 214, 15, 150, Blocks.MAGENTA_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS_PANE),
        RUBY("Ruby", 188, 3, 29, Blocks.RED_STAINED_GLASS, Blocks.RED_STAINED_GLASS_PANE),
        OPAL("Opal", 245, 245, 240, Blocks.WHITE_STAINED_GLASS, Blocks.WHITE_STAINED_GLASS_PANE),
        ONYX("Onyx", 60, 60, 65, Blocks.BLACK_STAINED_GLASS, Blocks.BLACK_STAINED_GLASS_PANE),
        AQUAMARINE("Aquamarine", 45, 95, 220, Blocks.BLUE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS_PANE),
        CITRINE("Citrine", 160, 105, 50, Blocks.BROWN_STAINED_GLASS, Blocks.BROWN_STAINED_GLASS_PANE),
        PERIDOT("Peridot", 70, 165, 60, Blocks.GREEN_STAINED_GLASS, Blocks.GREEN_STAINED_GLASS_PANE);

        public final String label;
        public final int rgb;
        public final int fillArgb;
        public final int fillArgb12;
        public final int fillArgb24;
        public final int strokeArgb;
        public final Block glassBlock;
        public final Block paneBlock;

        Gemstone(String label, int r, int g, int b, Block glassBlock, Block paneBlock) {
            this.label = label;
            this.rgb = (r << 16) | (g << 8) | b;
            this.fillArgb = (100 << 24) | this.rgb;   // <=12 blocks: base alpha=100
            this.fillArgb12 = (67 << 24) | this.rgb;  // >12 blocks: 33% transparency
            this.fillArgb24 = (34 << 24) | this.rgb;  // >24 blocks: 66% transparency
            this.strokeArgb = (220 << 24) | this.rgb; // crisp outline
            this.glassBlock = glassBlock;
            this.paneBlock = paneBlock;
        }

        public String getDisplayName() {
            return ModLang.trWithDefault("dttdrtaddons.gemstone." + name().toLowerCase(java.util.Locale.ROOT), label);
        }
    }

    private record GemstoneEntry(BlockPos pos, Gemstone gemstone) {}

    private static final int CH_MIN_X = 203;
    private static final int CH_MAX_X = 822;
    private static final int CH_MIN_Y = 32;
    private static final int CH_MAX_Y = 186;
    private static final int CH_MIN_Z = 203;
    private static final int CH_MAX_Z = 822;

    private static volatile GemstoneEspFeature instance;

    private volatile List<GemstoneEntry> renderList = Collections.emptyList();
    private Thread scannerThread;
    private ClientLevel lastLevel;

    public GemstoneEspFeature() {
        instance = this;
    }

    public static void toggleByHotkey(Minecraft mc) {
        if (!WhitelistManager.isAuthorized()) return;
        ModConfig cfg = ModConfig.getInstance();
        boolean next = !cfg.gemstoneEspEnabled;
        cfg.gemstoneEspEnabled = next;
        ModConfig.save();
        if (!next && instance != null) {
            instance.renderList = Collections.emptyList();
        }
        NotificationUtils.sendClientChatMessage(
                Component.literal(ModLang.tr("dttdrtaddons.gemstone_esp.toggled")).withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(ModLang.tr(next ? "dttdrtaddons.common.on" : "dttdrtaddons.common.off"))
                                .withStyle(next ? ChatFormatting.GREEN : ChatFormatting.RED))
        );
        ModLogManager.info("GemstoneESP", "Gemstone ESP toggled via hotkey -> " + next);
    }

    @Override
    public String getId() {
        return "gemstone_esp";
    }

    @Override
    public String getName() {
        return "gemstone esp";
    }

    @Override
    public String getCategory() {
        return "gemstone esp";
    }

    @Override
    public void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> renderEsp());
    }

    @Override
    public void clientTick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            renderList = Collections.emptyList();
        }

        if (!isEnabled() || !CrystalHollowsTracker.isInGemstoneMiningArea() || mc.player == null || mc.level == null) {
            renderList = Collections.emptyList();
            return;
        }

        if (scannerThread == null || !scannerThread.isAlive()) {
            scannerThread = new Thread(() -> runScanLoop(mc), "Gemstone-ESP-Scanner");
            scannerThread.setDaemon(true);
            scannerThread.start();
        }
    }

    private void runScanLoop(Minecraft mc) {
        while (isEnabled() && CrystalHollowsTracker.isInGemstoneMiningArea() && mc.player != null && mc.level != null) {
            try {
                scanNearbyChunks(mc);
            } catch (Throwable ignored) {
            }

            int interval = Math.max(100, Math.min(5000, ModConfig.getInstance().gemstoneEspScanIntervalMs));
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                return;
            }
        }
        renderList = Collections.emptyList();
    }

    private void scanNearbyChunks(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        ModConfig cfg = ModConfig.getInstance();
        int radius = Math.max(1, Math.min(500, cfg.gemstoneEspRadius));
        double radiusSq = (double) radius * (double) radius;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        boolean inHollows = CrystalHollowsTracker.isInCrystalHollows();
        int minX = inHollows ? Math.max(CH_MIN_X, (int) Math.floor(px) - radius) : (int) Math.floor(px) - radius;
        int maxX = inHollows ? Math.min(CH_MAX_X, (int) Math.ceil(px) + radius) : (int) Math.ceil(px) + radius;
        int minY = inHollows ? Math.max(CH_MIN_Y, (int) Math.floor(py) - radius) : Math.max(level.getMinY(), (int) Math.floor(py) - radius);
        int maxY = inHollows ? Math.min(CH_MAX_Y, (int) Math.ceil(py) + radius) : Math.min(level.getMaxY() - 1, (int) Math.ceil(py) + radius);
        int minZ = inHollows ? Math.max(CH_MIN_Z, (int) Math.floor(pz) - radius) : (int) Math.floor(pz) - radius;
        int maxZ = inHollows ? Math.min(CH_MAX_Z, (int) Math.ceil(pz) + radius) : (int) Math.ceil(pz) + radius;

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            renderList = Collections.emptyList();
            return;
        }

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        List<GemstoneEntry> found = new ArrayList<>();
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!isEnabled() || !CrystalHollowsTracker.isInGemstoneMiningArea() || mc.level != level) {
                    return;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                int xStart = Math.max(minX, cx << 4);
                int xEnd = Math.min(maxX, (cx << 4) + 15);
                int zStart = Math.max(minZ, cz << 4);
                int zEnd = Math.min(maxZ, (cz << 4) + 15);

                LevelChunkSection[] sections = chunk.getSections();
                int minSecIdx = Math.max(0, chunk.getSectionIndex(minY));
                int maxSecIdx = Math.min(sections.length - 1, chunk.getSectionIndex(maxY));

                for (int sIdx = minSecIdx; sIdx <= maxSecIdx; sIdx++) {
                    LevelChunkSection section = sections[sIdx];
                    if (section == null || section.hasOnlyAir()) continue;
                    if (!section.maybeHas(state -> matchGemstone(state) != null)) {
                        continue;
                    }

                    int secBaseY = chunk.getSectionYFromSectionIndex(sIdx) << 4;
                    int yStart = Math.max(minY, secBaseY);
                    int yEnd = Math.min(maxY, secBaseY + 15);

                    for (int y = yStart; y <= yEnd; y++) {
                        for (int z = zStart; z <= zEnd; z++) {
                            for (int x = xStart; x <= xEnd; x++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                Gemstone gem = matchGemstone(state);
                                if (gem != null && isGemstoneEnabled(gem, cfg)) {
                                    double dx = (x + 0.5) - px;
                                    double dy = (y + 0.5) - py;
                                    double dz = (z + 0.5) - pz;
                                    if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                                        mPos.set(x, y, z);
                                        found.add(new GemstoneEntry(mPos.immutable(), gem));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        renderList = found;
    }

    private void renderEsp() {
        if (!WhitelistManager.isAuthorized() || !isEnabled() || !CrystalHollowsTracker.isInGemstoneMiningArea()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        final double dist12Sq = 12.0 * 12.0;
        final double dist24Sq = 24.0 * 24.0;

        List<GemstoneEntry> entries = renderList;
        for (int i = 0; i < entries.size(); i++) {
            GemstoneEntry entry = entries.get(i);
            if (!mc.level.getBlockState(entry.pos).isAir()) {
                double dx = (entry.pos.getX() + 0.5) - px;
                double dy = (entry.pos.getY() + 0.5) - py;
                double dz = (entry.pos.getZ() + 0.5) - pz;
                double distSq = dx * dx + dy * dy + dz * dz;
                int argb = distSq > dist24Sq
                        ? entry.gemstone.fillArgb24
                        : (distSq > dist12Sq ? entry.gemstone.fillArgb12 : entry.gemstone.fillArgb);
                Gizmos.cuboid(
                        entry.pos,
                        GizmoStyle.stroke(argb, 1.0f)
                ).setAlwaysOnTop();
            }
        }
    }

    private static Gemstone matchGemstone(BlockState state) {
        if (state.isAir()) return null;
        Block block = state.getBlock();
        for (Gemstone gem : Gemstone.values()) {
            if (block == gem.paneBlock || block == gem.glassBlock) {
                return gem;
            }
        }
        return null;
    }

    public static boolean isGemstoneEnabled(Gemstone gem, ModConfig cfg) {
        return switch (gem) {
            case JADE -> cfg.gemstoneJade;
            case AMBER -> cfg.gemstoneAmber;
            case TOPAZ -> cfg.gemstoneTopaz;
            case SAPPHIRE -> cfg.gemstoneSapphire;
            case AMETHYST -> cfg.gemstoneAmethyst;
            case JASPER -> cfg.gemstoneJasper;
            case RUBY -> cfg.gemstoneRuby;
            case OPAL -> cfg.gemstoneOpal;
            case ONYX -> cfg.gemstoneOnyx;
            case AQUAMARINE -> cfg.gemstoneAquamarine;
            case CITRINE -> cfg.gemstoneCitrine;
            case PERIDOT -> cfg.gemstonePeridot;
        };
    }

    public static void toggleGemstone(Gemstone gem, ModConfig cfg) {
        switch (gem) {
            case JADE -> cfg.gemstoneJade = !cfg.gemstoneJade;
            case AMBER -> cfg.gemstoneAmber = !cfg.gemstoneAmber;
            case TOPAZ -> cfg.gemstoneTopaz = !cfg.gemstoneTopaz;
            case SAPPHIRE -> cfg.gemstoneSapphire = !cfg.gemstoneSapphire;
            case AMETHYST -> cfg.gemstoneAmethyst = !cfg.gemstoneAmethyst;
            case JASPER -> cfg.gemstoneJasper = !cfg.gemstoneJasper;
            case RUBY -> cfg.gemstoneRuby = !cfg.gemstoneRuby;
            case OPAL -> cfg.gemstoneOpal = !cfg.gemstoneOpal;
            case ONYX -> cfg.gemstoneOnyx = !cfg.gemstoneOnyx;
            case AQUAMARINE -> cfg.gemstoneAquamarine = !cfg.gemstoneAquamarine;
            case CITRINE -> cfg.gemstoneCitrine = !cfg.gemstoneCitrine;
            case PERIDOT -> cfg.gemstonePeridot = !cfg.gemstonePeridot;
        }
        ModConfig.save();
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().gemstoneEspEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().gemstoneEspEnabled = enabled;
        ModConfig.save();
    }
}
