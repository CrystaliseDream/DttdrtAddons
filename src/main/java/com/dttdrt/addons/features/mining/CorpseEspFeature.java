package com.dttdrt.addons.features.mining;

import com.dttdrt.addons.core.auth.WhitelistManager;
import com.dttdrt.addons.core.config.ModConfig;
import com.dttdrt.addons.core.feature.Feature;
import com.dttdrt.addons.core.lang.ModLang;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Glacite Mineshaft Frozen Corpse ESP (100% local, zero packets).
 * Scans all loaded ArmorStands across the entire Mineshaft, classifies Lapis / Umber /
 * Tungsten / Vanguard corpses, and renders through-wall boxes + distance labels.
 */
public class CorpseEspFeature implements Feature {

    public enum CorpseType {
        LAPIS("Lapis Corpse", 59, 130, 246),
        UMBER("Umber Corpse", 245, 158, 11),
        TUNGSTEN("Tungsten Corpse", 203, 213, 225);

        public final String label;
        public final int rgb;
        public final int fillArgb;
        public final int strokeArgb;

        CorpseType(String label, int r, int g, int b) {
            this.label = label;
            this.rgb = (r << 16) | (g << 8) | b;
            this.fillArgb = (90 << 24) | this.rgb;
            this.strokeArgb = (230 << 24) | this.rgb;
        }

        public String getDisplayName() {
            return ModLang.trWithDefault("dttdrtaddons.corpse." + name().toLowerCase(Locale.ROOT), label);
        }
    }

    private static final String MINERAL_HELMET_TEXTURE =
            "a95280a7b21b06e3887f165c8ca7a03bfac96378acd96cdf5944bcd08a0b6587";

    private record CorpseEntry(double x, double y, double z, CorpseType type) {}

    private static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath("dttdrtaddons", "corpse_esp_labels");

    private volatile List<CorpseEntry> corpses = Collections.emptyList();

    @Override
    public String getId() {
        return "corpse_esp";
    }

    @Override
    public String getName() {
        return "Corpse ESP";
    }

    @Override
    public String getCategory() {
        return "Gemstone ESP";
    }

    @Override
    public void init() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> renderBoxes());
        HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, HUD_ID, (graphics, deltaTracker) -> renderLabels(graphics));
    }

    @Override
    public void clientTick(Minecraft mc) {
        if (!isEnabled() || !CrystalHollowsTracker.isInMineshaft() || mc.level == null || mc.player == null) {
            corpses = Collections.emptyList();
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        List<CorpseEntry> found = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand)) continue;

            CorpseType type = classifyCorpse(stand);
            if (type != null && isCorpseEnabled(type, cfg)) {
                found.add(new CorpseEntry(stand.getX(), stand.getY(), stand.getZ(), type));
            }
        }
        corpses = found;
    }

    private static CorpseType classifyCorpse(ArmorStand stand) {
        ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = stand.getItemBySlot(EquipmentSlot.CHEST);
        if (head.isEmpty() && chest.isEmpty()) {
            return null;
        }

        if (!head.isEmpty()) {
            // 1. Exact Skull Texture Hash (Tungsten uses Mineral Helmet)
            ResolvableProfile profile = head.get(DataComponents.PROFILE);
            if (profile != null) {
                var props = profile.partialProfile().properties().get("textures");
                for (var prop : props) {
                    try {
                        String decoded = new String(java.util.Base64.getDecoder().decode(prop.value()));
                        if (decoded.contains(MINERAL_HELMET_TEXTURE)) {
                            return CorpseType.TUNGSTEN;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // 2. Exact Vanilla / Named Item Check
            if (head.is(Items.SEA_LANTERN) || head.is(Items.LAPIS_BLOCK)) {
                return CorpseType.LAPIS;
            }
            String headName = head.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (headName.contains("lapis")) return CorpseType.LAPIS;
            if (headName.contains("mineral") || headName.contains("tungsten")) return CorpseType.TUNGSTEN;
            if (headName.contains("yog") || headName.contains("umber")) return CorpseType.UMBER;
        }

        if (!chest.isEmpty()) {
            if (chest.is(Items.IRON_CHESTPLATE) || chest.is(Items.CHAINMAIL_CHESTPLATE)) {
                return CorpseType.TUNGSTEN;
            }
            String chestName = chest.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (chestName.contains("lapis")) return CorpseType.LAPIS;
            if (chestName.contains("mineral") || chestName.contains("tungsten")) return CorpseType.TUNGSTEN;
            if (chestName.contains("yog") || chestName.contains("umber")) return CorpseType.UMBER;
        }

        // 3. Dyed Leather Armor RGB Check (Hypixel Lapis/Yog/Mineral + vanilla blue/orange leather dyes)
        int rgb = !chest.isEmpty() ? DyedItemColor.getOrDefault(chest, -1) : -1;
        if (rgb == -1 && !head.isEmpty()) {
            rgb = DyedItemColor.getOrDefault(head, -1);
        }
        if (rgb != -1) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            // Lapis Armor (0, 0, 255) or vanilla blue dye (#3C44AA)
            if (b >= 150 && b >= r + 50 && b >= g + 45) return CorpseType.LAPIS;
            // Armor of Yog / Umber (200, 50, 0) or vanilla orange dye (#F9801D)
            if (r >= 150 && g <= 140 && b <= 55 && r >= g + 70) return CorpseType.UMBER;
            // Mineral Armor / Tungsten (204, 229, 255) or neutral gray
            if (b >= 195 && g >= 175 && r >= 145) return CorpseType.TUNGSTEN;
            if (Math.abs(r - g) <= 25 && Math.abs(g - b) <= 25 && r >= 120) return CorpseType.TUNGSTEN;
        }

        return null;
    }

    private void renderBoxes() {
        if (!WhitelistManager.isAuthorized() || !isEnabled() || !CrystalHollowsTracker.isInMineshaft()) return;
        List<CorpseEntry> list = corpses;
        if (list.isEmpty()) return;

        for (CorpseEntry entry : list) {
            AABB box = new AABB(
                    entry.x - 0.45, entry.y + 0.1, entry.z - 0.45,
                    entry.x + 0.45, entry.y + 1.5, entry.z + 0.45
            );
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(entry.type.strokeArgb, 2.5f, entry.type.fillArgb))
                    .setAlwaysOnTop();
        }
    }

    private void renderLabels(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!WhitelistManager.isAuthorized() || !isEnabled() || !CrystalHollowsTracker.isInMineshaft()) return;
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;

        List<CorpseEntry> list = corpses;
        if (list.isEmpty()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;

        Vec3 camPos = camera.position();
        Matrix4f viewProj = camera.getViewRotationProjectionMatrix(new Matrix4f());
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        for (CorpseEntry entry : list) {
            float dx = (float) (entry.x - camPos.x);
            float dy = (float) (entry.y + 1.35 - camPos.y);
            float dz = (float) (entry.z - camPos.z);

            Vector4f clip = new Vector4f(dx, dy, dz, 1.0f).mul(viewProj);
            if (clip.w <= 0.05f) continue;

            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.15f || ndcY > 1.15f) continue;

            int sx = Math.round((ndcX * 0.5f + 0.5f) * screenW);
            int sy = Math.round((0.5f - ndcY * 0.5f) * screenH);

            int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            String label = entry.type.label + " (" + dist + "m)";

            graphics.pose().pushMatrix();
            graphics.pose().translate((float) sx, (float) sy);
            graphics.pose().scale(0.8f, 0.8f);
            graphics.centeredText(mc.font, label, 0, -4, 0xFF000000 | entry.type.rgb);
            graphics.pose().popMatrix();
        }
    }

    public static boolean isCorpseEnabled(CorpseType type, ModConfig cfg) {
        return switch (type) {
            case LAPIS -> cfg.corpseLapis;
            case UMBER -> cfg.corpseUmber;
            case TUNGSTEN -> cfg.corpseTungsten;
        };
    }

    public static void toggleCorpse(CorpseType type, ModConfig cfg) {
        switch (type) {
            case LAPIS -> cfg.corpseLapis = !cfg.corpseLapis;
            case UMBER -> cfg.corpseUmber = !cfg.corpseUmber;
            case TUNGSTEN -> cfg.corpseTungsten = !cfg.corpseTungsten;
        }
        ModConfig.save();
    }

    @Override
    public boolean isEnabled() {
        return ModConfig.getInstance().corpseEspEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        ModConfig.getInstance().corpseEspEnabled = enabled;
        ModConfig.save();
    }
}
