package com.dttdrt.addons.texture;

import com.dttdrt.addons.core.log.ModLogManager;
import com.dttdrt.addons.features.mining.profit.MiningItemTable;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime bridge to Noamm9's PackDisabler mod.
 * Reads PackDisabler's idToLocation and idToSkullProfile maps via reflection so legacy
 * skull textures and classic vanilla models render cleanly without Hypixel's server pack.
 * Falls back to SkyHanni-style Items.BARRIER whenever PackDisabler or a texture is missing.
 */
public final class PackDisablerIconBridge {

    private static final String PACKDISABLER_MOD_ID = "packdisabler";
    private static final String PACKDISABLER_CLASS = "com.github.noamm9.packdisabler.PackDisabler";

    private static volatile boolean reflectInitAttempted = false;
    private static volatile boolean modLoaded = false;
    private static volatile Map<?, ?> idToLocationMap = null;
    private static volatile Map<?, ?> idToSkullProfileMap = null;

    private static final Map<String, ItemStack> ICON_CACHE = new ConcurrentHashMap<>();

    private PackDisablerIconBridge() {
    }

    public static boolean isAvailable() {
        ensureInitialized();
        return modLoaded && idToLocationMap != null && !idToLocationMap.isEmpty();
    }

    private static void ensureInitialized() {
        if (reflectInitAttempted && (!modLoaded || (idToLocationMap != null && !idToLocationMap.isEmpty()))) {
            return;
        }
        synchronized (PackDisablerIconBridge.class) {
            if (reflectInitAttempted && (!modLoaded || (idToLocationMap != null && !idToLocationMap.isEmpty()))) {
                return;
            }
            reflectInitAttempted = true;
            modLoaded = FabricLoader.getInstance().isModLoaded(PACKDISABLER_MOD_ID);
            if (!modLoaded) {
                return;
            }
            try {
                Class<?> pdClass = Class.forName(PACKDISABLER_CLASS);
                Field companionField = pdClass.getField("Companion");
                Object companion = companionField.get(null);
                if (companion == null) {
                    return;
                }
                Method getLocMethod = companion.getClass().getMethod("getIdToLocation");
                Object locObj = getLocMethod.invoke(companion);
                if (locObj instanceof Map<?, ?> locMap) {
                    idToLocationMap = locMap;
                }
                Method getSkullMethod = companion.getClass().getMethod("getIdToSkullProfile");
                Object skullObj = getSkullMethod.invoke(companion);
                if (skullObj instanceof Map<?, ?> skullMap) {
                    idToSkullProfileMap = skullMap;
                }
            } catch (Throwable t) {
                ModLogManager.warn("PackDisablerBridge", "Failed to reflect PackDisabler maps: " + t.getMessage());
            }
        }
    }

    /**
     * Resolves an ItemStack using PackDisabler's idToLocation and idToSkullProfile maps.
     * Returns null if PackDisabler is not installed or has no complete mapping/skull profile.
     */
    public static ItemStack tryResolveFromPackDisabler(String skyblockId) {
        if (skyblockId == null || skyblockId.isBlank()) {
            return null;
        }
        ensureInitialized();
        Map<?, ?> locMap = idToLocationMap;
        if (!modLoaded || locMap == null || locMap.isEmpty()) {
            return null;
        }

        String upper = skyblockId.toUpperCase(Locale.ROOT);
        String dashed = upper.replace(':', '-');

        String matchedKey = null;
        Object locVal = locMap.get(upper);
        if (locVal != null) {
            matchedKey = upper;
        } else {
            locVal = locMap.get(dashed);
            if (locVal != null) {
                matchedKey = dashed;
            }
        }
        if (!(locVal instanceof Identifier modelLoc)) {
            return null;
        }

        Item item = BuiltInRegistries.ITEM.getValue(modelLoc);
        if (item == null || item == Items.AIR) {
            return null;
        }

        ItemStack stack = new ItemStack(item);
        if (item == Items.PLAYER_HEAD) {
            Map<?, ?> skullMap = idToSkullProfileMap;
            Object profileObj = skullMap != null ? skullMap.get(matchedKey) : null;
            if (profileObj == null && skullMap != null && !matchedKey.equals(upper)) {
                profileObj = skullMap.get(upper);
            }
            if (!(profileObj instanceof ResolvableProfile profile)) {
                return null;
            }
            stack.set(DataComponents.PROFILE, profile);
        }
        return stack;
    }

    /**
     * Resolves an icon ItemStack via PackDisabler, falling back to Items.BARRIER
     * whenever PackDisabler is missing or the texture/model cannot be resolved.
     */
    public static ItemStack getIconStack(String skyblockId) {
        if (skyblockId == null || skyblockId.isBlank()) {
            return new ItemStack(Items.BARRIER);
        }
        String key = skyblockId.toUpperCase(Locale.ROOT);
        ItemStack cached = ICON_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        ensureInitialized();
        ItemStack resolved = tryResolveFromPackDisabler(key);
        if (resolved != null) {
            ICON_CACHE.put(key, resolved);
            return resolved;
        }

        // Only cache BARRIER once PackDisabler is confirmed absent or finished loading its map.
        ItemStack barrier = new ItemStack(Items.BARRIER);
        if (!modLoaded || (idToLocationMap != null && !idToLocationMap.isEmpty())) {
            ICON_CACHE.put(key, barrier);
        }
        return barrier;
    }

    public static void drawItemIcon(GuiGraphicsExtractor g, String skyblockId, int x, int y, int size) {
        int fallback = MiningItemTable.getColor(skyblockId);
        drawItemIconOrColor(g, skyblockId, x, y, size, fallback);
    }

    public static void drawItemIconOrColor(
            GuiGraphicsExtractor g,
            String skyblockId,
            int x,
            int y,
            int size,
            int fallbackColorArgb
    ) {
        if (g == null || size <= 0) return;
        ItemStack stack = tryResolveFromPackDisabler(skyblockId);
        if (stack != null) {
            float scale = size / 16.0f;
            g.pose().pushMatrix();
            g.pose().translate((float) x, (float) y);
            g.pose().scale(scale, scale);
            g.item(stack, 0, 0);
            g.pose().popMatrix();
            return;
        }
        drawColorBlock(g, x, y, size, fallbackColorArgb);
    }

    public static void drawColorBlock(GuiGraphicsExtractor g, int x, int y, int size, int colorArgb) {
        if (g == null || size <= 0) return;
        int col = (colorArgb & 0x00FFFFFF) == 0 ? 0xFFE5E7EB : (colorArgb | 0xFF000000);
        int pad = size >= 12 ? 1 : 0;
        int bw = Math.max(4, size - pad * 2);
        int x0 = x + pad;
        int y0 = y + pad;
        g.fill(x0, y0, x0 + bw, y0 + bw, col);
        g.outline(x0, y0, bw, bw, 0x88000000);
    }
}
