package com.dttdrt.addons.texture;

import com.dttdrt.addons.features.market.model.SkyblockItem;
import com.dttdrt.addons.features.mining.profit.MiningItemTable;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps Skyblock Item IDs and 1.8.9 Bukkit material names to their classic vanilla Minecraft items.
 * When PackDisabler blocks Hypixel's custom server pack, the UI renders pure classic vanilla textures.
 */
public class ClassicTextureRegistry {
    private static final Map<String, String> CLASSIC_ID_TO_VANILLA = new HashMap<>();
    private static final Map<String, String> BUKKIT_TO_MODERN = new HashMap<>();
    private static final Map<String, ItemStack> STACK_CACHE = new ConcurrentHashMap<>();

    static {
        // Explicit classic overrides for iconic Skyblock items
        CLASSIC_ID_TO_VANILLA.put("HYPERION", "minecraft:iron_sword");
        CLASSIC_ID_TO_VANILLA.put("VALKYRIE", "minecraft:iron_sword");
        CLASSIC_ID_TO_VANILLA.put("SCYLLA", "minecraft:iron_sword");
        CLASSIC_ID_TO_VANILLA.put("ASTRAEA", "minecraft:iron_sword");
        CLASSIC_ID_TO_VANILLA.put("NECRON_HANDLE", "minecraft:stick");
        CLASSIC_ID_TO_VANILLA.put("ASPECT_OF_THE_END", "minecraft:diamond_sword");
        CLASSIC_ID_TO_VANILLA.put("ASPECT_OF_THE_VOID", "minecraft:diamond_shovel");
        CLASSIC_ID_TO_VANILLA.put("ASPECT_OF_THE_DRAGON", "minecraft:diamond_sword");
        CLASSIC_ID_TO_VANILLA.put("TERMINATOR", "minecraft:bow");
        CLASSIC_ID_TO_VANILLA.put("JUJU_SHORTBOW", "minecraft:bow");
        CLASSIC_ID_TO_VANILLA.put("LIVID_DAGGER", "minecraft:iron_sword");
        CLASSIC_ID_TO_VANILLA.put("SHADOW_FURY", "minecraft:diamond_sword");
        CLASSIC_ID_TO_VANILLA.put("GIANTS_SWORD", "minecraft:iron_sword");
        CLASSIC_ID_TO_VANILLA.put("DARK_CLAYMORE", "minecraft:stone_sword");
        CLASSIC_ID_TO_VANILLA.put("BONZO_STAFF", "minecraft:blaze_rod");
        CLASSIC_ID_TO_VANILLA.put("SPIRIT_SCEPTRE", "minecraft:allium");
        CLASSIC_ID_TO_VANILLA.put("YETI_SWORD", "minecraft:iron_sword");
        CLASSIC_ID_TO_VANILLA.put("MIDAS_STAFF", "minecraft:golden_shovel");
        CLASSIC_ID_TO_VANILLA.put("MIDAS_SWORD", "minecraft:golden_sword");
        CLASSIC_ID_TO_VANILLA.put("DIVAN_DRILL", "minecraft:prismarine_shard");
        CLASSIC_ID_TO_VANILLA.put("DIVAN_ALLOY", "minecraft:gold_block");
        CLASSIC_ID_TO_VANILLA.put("SUPER_COMPACTOR_3000", "minecraft:dropper");
        CLASSIC_ID_TO_VANILLA.put("BOOSTER_COOKIE", "minecraft:cookie");
        CLASSIC_ID_TO_VANILLA.put("STOCK_OF_STONKS", "minecraft:paper");
        CLASSIC_ID_TO_VANILLA.put("UMBER", "minecraft:terracotta");
        CLASSIC_ID_TO_VANILLA.put("ENCHANTED_UMBER", "minecraft:copper_ingot");
        CLASSIC_ID_TO_VANILLA.put("REFINED_UMBER", "minecraft:copper_block");
        CLASSIC_ID_TO_VANILLA.put("UMBER_PLATE", "minecraft:heavy_weighted_pressure_plate");
        CLASSIC_ID_TO_VANILLA.put("TUNGSTEN", "minecraft:deepslate");
        CLASSIC_ID_TO_VANILLA.put("ENCHANTED_TUNGSTEN", "minecraft:iron_ingot");
        CLASSIC_ID_TO_VANILLA.put("REFINED_TUNGSTEN", "minecraft:iron_block");
        CLASSIC_ID_TO_VANILLA.put("TUNGSTEN_PLATE", "minecraft:heavy_weighted_pressure_plate");
        CLASSIC_ID_TO_VANILLA.put("TUNGSTEN_KEY", "minecraft:tripwire_hook");
        CLASSIC_ID_TO_VANILLA.put("SKELETON_KEY", "minecraft:tripwire_hook");
        CLASSIC_ID_TO_VANILLA.put("COMPOST", "minecraft:podzol");
        CLASSIC_ID_TO_VANILLA.put("ENCHANTED_COMPOST", "minecraft:composter");
        CLASSIC_ID_TO_VANILLA.put("GLACITE_JEWEL", "minecraft:prismarine_crystals");
        CLASSIC_ID_TO_VANILLA.put("GLACITE_AMALGAMATION", "minecraft:blue_ice");
        CLASSIC_ID_TO_VANILLA.put("GLACITE_SHARD", "minecraft:prismarine_shard");

        // 1.8.9 Bukkit Material -> Modern Minecraft Item ID mappings
        BUKKIT_TO_MODERN.put("SKULL_ITEM", "minecraft:player_head");
        BUKKIT_TO_MODERN.put("PLAYER_HEAD", "minecraft:player_head");
        BUKKIT_TO_MODERN.put("INK_SACK", "minecraft:ink_sac");
        BUKKIT_TO_MODERN.put("WOOD_SWORD", "minecraft:wooden_sword");
        BUKKIT_TO_MODERN.put("WOOD_PICKAXE", "minecraft:wooden_pickaxe");
        BUKKIT_TO_MODERN.put("WOOD_AXE", "minecraft:wooden_axe");
        BUKKIT_TO_MODERN.put("WOOD_SPADE", "minecraft:wooden_shovel");
        BUKKIT_TO_MODERN.put("WOOD_HOE", "minecraft:wooden_hoe");
        BUKKIT_TO_MODERN.put("STONE_SPADE", "minecraft:stone_shovel");
        BUKKIT_TO_MODERN.put("IRON_SPADE", "minecraft:iron_shovel");
        BUKKIT_TO_MODERN.put("DIAMOND_SPADE", "minecraft:diamond_shovel");
        BUKKIT_TO_MODERN.put("GOLD_SWORD", "minecraft:golden_sword");
        BUKKIT_TO_MODERN.put("GOLD_PICKAXE", "minecraft:golden_pickaxe");
        BUKKIT_TO_MODERN.put("GOLD_AXE", "minecraft:golden_axe");
        BUKKIT_TO_MODERN.put("GOLD_SPADE", "minecraft:golden_shovel");
        BUKKIT_TO_MODERN.put("GOLD_HOE", "minecraft:golden_hoe");
        BUKKIT_TO_MODERN.put("GOLD_HELMET", "minecraft:golden_helmet");
        BUKKIT_TO_MODERN.put("GOLD_CHESTPLATE", "minecraft:golden_chestplate");
        BUKKIT_TO_MODERN.put("GOLD_LEGGINGS", "minecraft:golden_leggings");
        BUKKIT_TO_MODERN.put("GOLD_BOOTS", "minecraft:golden_boots");
        BUKKIT_TO_MODERN.put("GOLD_INGOT", "minecraft:gold_ingot");
        BUKKIT_TO_MODERN.put("GOLD_NUGGET", "minecraft:gold_nugget");
        BUKKIT_TO_MODERN.put("GOLD_ORE", "minecraft:gold_ore");
        BUKKIT_TO_MODERN.put("GOLD_BLOCK", "minecraft:gold_block");
        BUKKIT_TO_MODERN.put("RAW_FISH", "minecraft:cod");
        BUKKIT_TO_MODERN.put("COOKED_FISH", "minecraft:cooked_cod");
        BUKKIT_TO_MODERN.put("NETHER_STALK", "minecraft:nether_wart");
        BUKKIT_TO_MODERN.put("NETHER_WARTS", "minecraft:nether_wart");
        BUKKIT_TO_MODERN.put("PORK", "minecraft:porkchop");
        BUKKIT_TO_MODERN.put("GRILLED_PORK", "minecraft:cooked_porkchop");
        BUKKIT_TO_MODERN.put("RAW_BEEF", "minecraft:beef");
        BUKKIT_TO_MODERN.put("RAW_CHICKEN", "minecraft:chicken");
        BUKKIT_TO_MODERN.put("MELON", "minecraft:melon_slice");
        BUKKIT_TO_MODERN.put("MELON_BLOCK", "minecraft:melon");
        BUKKIT_TO_MODERN.put("WATCH", "minecraft:clock");
        BUKKIT_TO_MODERN.put("WORKBENCH", "minecraft:crafting_table");
        BUKKIT_TO_MODERN.put("SULPHUR", "minecraft:gunpowder");
        BUKKIT_TO_MODERN.put("SEEDS", "minecraft:wheat_seeds");
        BUKKIT_TO_MODERN.put("WATER_LILY", "minecraft:lily_pad");
        BUKKIT_TO_MODERN.put("SNOW_BALL", "minecraft:snowball");
        BUKKIT_TO_MODERN.put("FIREWORK", "minecraft:firework_rocket");
        BUKKIT_TO_MODERN.put("FIREBALL", "minecraft:fire_charge");
        BUKKIT_TO_MODERN.put("EMPTY_MAP", "minecraft:map");
        BUKKIT_TO_MODERN.put("BOOK_AND_QUILL", "minecraft:writable_book");
        BUKKIT_TO_MODERN.put("STAINED_GLASS", "minecraft:white_stained_glass");
        BUKKIT_TO_MODERN.put("STAINED_GLASS_PANE", "minecraft:white_stained_glass_pane");
        BUKKIT_TO_MODERN.put("STAINED_CLAY", "minecraft:terracotta");
        BUKKIT_TO_MODERN.put("HARD_CLAY", "minecraft:terracotta");
        BUKKIT_TO_MODERN.put("WOOL", "minecraft:white_wool");
        BUKKIT_TO_MODERN.put("CARPET", "minecraft:white_carpet");
        BUKKIT_TO_MODERN.put("MONSTER_EGG", "minecraft:ghast_spawn_egg");
        BUKKIT_TO_MODERN.put("RED_ROSE", "minecraft:poppy");
        BUKKIT_TO_MODERN.put("YELLOW_FLOWER", "minecraft:dandelion");
        BUKKIT_TO_MODERN.put("SMOOTH_BRICK", "minecraft:stone_bricks");
        BUKKIT_TO_MODERN.put("COBBLESTONE", "minecraft:cobblestone");
        BUKKIT_TO_MODERN.put("LOG", "minecraft:oak_log");
        BUKKIT_TO_MODERN.put("LOG_2", "minecraft:acacia_log");
        BUKKIT_TO_MODERN.put("WOOD", "minecraft:oak_planks");
        BUKKIT_TO_MODERN.put("SAPLING", "minecraft:oak_sapling");
        BUKKIT_TO_MODERN.put("LEAVES", "minecraft:oak_leaves");
        BUKKIT_TO_MODERN.put("LEAVES_2", "minecraft:acacia_leaves");
        BUKKIT_TO_MODERN.put("PRISMARINE_CRYSTALS", "minecraft:prismarine_crystals");
        BUKKIT_TO_MODERN.put("PRISMARINE_SHARD", "minecraft:prismarine_shard");
        BUKKIT_TO_MODERN.put("EXP_BOTTLE", "minecraft:experience_bottle");
        BUKKIT_TO_MODERN.put("EYE_OF_ENDER", "minecraft:ender_eye");
        BUKKIT_TO_MODERN.put("GOLDEN_APPLE", "minecraft:golden_apple");
        BUKKIT_TO_MODERN.put("CARROT_ITEM", "minecraft:carrot");
        BUKKIT_TO_MODERN.put("POTATO_ITEM", "minecraft:potato");
        BUKKIT_TO_MODERN.put("SUGAR_CANE", "minecraft:sugar_cane");
        BUKKIT_TO_MODERN.put("DIODE", "minecraft:repeater");
        BUKKIT_TO_MODERN.put("REDSTONE_COMPARATOR", "minecraft:comparator");
        BUKKIT_TO_MODERN.put("CAULDRON_ITEM", "minecraft:cauldron");
        BUKKIT_TO_MODERN.put("BREWING_STAND_ITEM", "minecraft:brewing_stand");
        BUKKIT_TO_MODERN.put("FLOWER_POT_ITEM", "minecraft:flower_pot");
        BUKKIT_TO_MODERN.put("STORAGE_MINECART", "minecraft:chest_minecart");
        BUKKIT_TO_MODERN.put("BANNER", "minecraft:white_banner");
        BUKKIT_TO_MODERN.put("MAGMA_CREAM", "minecraft:magma_cream");
        BUKKIT_TO_MODERN.put("BLAZE_POWDER", "minecraft:blaze_powder");
        BUKKIT_TO_MODERN.put("SPECKLED_MELON", "minecraft:glistering_melon_slice");
        BUKKIT_TO_MODERN.put("IRON_FENCE", "minecraft:iron_bars");
        BUKKIT_TO_MODERN.put("THIN_GLASS", "minecraft:glass_pane");
        BUKKIT_TO_MODERN.put("WEB", "minecraft:cobweb");
        BUKKIT_TO_MODERN.put("MYCEL", "minecraft:mycelium");
        BUKKIT_TO_MODERN.put("QUARTZ", "minecraft:quartz");
        BUKKIT_TO_MODERN.put("CLAY_BALL", "minecraft:clay_ball");
        BUKKIT_TO_MODERN.put("DOUBLE_PLANT", "minecraft:sunflower");
        BUKKIT_TO_MODERN.put("CARROT_STICK", "minecraft:carrot_on_a_stick");
        BUKKIT_TO_MODERN.put("STEP", "minecraft:stone_slab");
        BUKKIT_TO_MODERN.put("WOOD_STEP", "minecraft:oak_slab");
        BUKKIT_TO_MODERN.put("NETHER_FENCE", "minecraft:nether_brick_fence");
        BUKKIT_TO_MODERN.put("SIGN", "minecraft:oak_sign");
        BUKKIT_TO_MODERN.put("BED", "minecraft:red_bed");
        BUKKIT_TO_MODERN.put("BOAT", "minecraft:oak_boat");
        BUKKIT_TO_MODERN.put("LONG_GRASS", "minecraft:short_grass");
        BUKKIT_TO_MODERN.put("PISTON_BASE", "minecraft:piston");
        BUKKIT_TO_MODERN.put("PISTON_STICKY_BASE", "minecraft:sticky_piston");
        BUKKIT_TO_MODERN.put("GOLD_BARDING", "minecraft:golden_horse_armor");
        BUKKIT_TO_MODERN.put("IRON_BARDING", "minecraft:iron_horse_armor");
        BUKKIT_TO_MODERN.put("DIAMOND_BARDING", "minecraft:diamond_horse_armor");
        BUKKIT_TO_MODERN.put("STONE_PLATE", "minecraft:stone_pressure_plate");
        BUKKIT_TO_MODERN.put("WOOD_PLATE", "minecraft:oak_pressure_plate");
        BUKKIT_TO_MODERN.put("IRON_PLATE", "minecraft:heavy_weighted_pressure_plate");
        BUKKIT_TO_MODERN.put("GOLD_PLATE", "minecraft:light_weighted_pressure_plate");
        BUKKIT_TO_MODERN.put("RAILS", "minecraft:rail");
        BUKKIT_TO_MODERN.put("FIREWORK_CHARGE", "minecraft:firework_star");
        BUKKIT_TO_MODERN.put("BRICK", "minecraft:bricks");
        BUKKIT_TO_MODERN.put("CLAY_BRICK", "minecraft:brick");
        BUKKIT_TO_MODERN.put("NETHER_BRICK_ITEM", "minecraft:nether_brick");
        BUKKIT_TO_MODERN.put("EXPLOSIVE_MINECART", "minecraft:tnt_minecart");
        BUKKIT_TO_MODERN.put("HOPPER_MINECART", "minecraft:hopper_minecart");
        BUKKIT_TO_MODERN.put("POWERED_MINECART", "minecraft:furnace_minecart");
        BUKKIT_TO_MODERN.put("COMMAND", "minecraft:command_block");
        BUKKIT_TO_MODERN.put("ENCHANTMENT_TABLE", "minecraft:enchanting_table");
        BUKKIT_TO_MODERN.put("ENDER_PORTAL_FRAME", "minecraft:end_portal_frame");
        BUKKIT_TO_MODERN.put("ENDER_STONE", "minecraft:end_stone");
        BUKKIT_TO_MODERN.put("SOIL", "minecraft:farmland");
        BUKKIT_TO_MODERN.put("GRASS", "minecraft:grass_block");
        BUKKIT_TO_MODERN.put("SNOW", "minecraft:snow_block");
        BUKKIT_TO_MODERN.put("SUGAR_CANE_BLOCK", "minecraft:sugar_cane");
        BUKKIT_TO_VANILLA_EXT();
    }

    private static void BUKKIT_TO_VANILLA_EXT() {
        BUKKIT_TO_MODERN.put("HUGE_MUSHROOM_1", "minecraft:brown_mushroom_block");
        BUKKIT_TO_MODERN.put("HUGE_MUSHROOM_2", "minecraft:red_mushroom_block");
        BUKKIT_TO_MODERN.put("QUARTZ_ORE", "minecraft:nether_quartz_ore");
        BUKKIT_TO_MODERN.put("REDSTONE_LAMP_OFF", "minecraft:redstone_lamp");
        BUKKIT_TO_MODERN.put("REDSTONE_TORCH_ON", "minecraft:redstone_torch");
        BUKKIT_TO_MODERN.put("SKULL", "minecraft:skeleton_skull");
        BUKKIT_TO_MODERN.put("SMOOTH_STAIRS", "minecraft:stone_brick_stairs");
        BUKKIT_TO_MODERN.put("WOOD_STAIRS", "minecraft:oak_stairs");
        BUKKIT_TO_MODERN.put("COBBLESTONE_STAIRS", "minecraft:cobblestone_stairs");
        BUKKIT_TO_MODERN.put("GOLD_RECORD", "minecraft:music_disc_13");
        BUKKIT_TO_MODERN.put("GREEN_RECORD", "minecraft:music_disc_cat");
        BUKKIT_TO_MODERN.put("RECORD_3", "minecraft:music_disc_blocks");
        BUKKIT_TO_MODERN.put("RECORD_4", "minecraft:music_disc_chirp");
        BUKKIT_TO_MODERN.put("RECORD_5", "minecraft:music_disc_far");
        BUKKIT_TO_MODERN.put("RECORD_6", "minecraft:music_disc_mall");
        BUKKIT_TO_MODERN.put("RECORD_7", "minecraft:music_disc_mellohi");
        BUKKIT_TO_MODERN.put("RECORD_8", "minecraft:music_disc_stal");
        BUKKIT_TO_MODERN.put("RECORD_9", "minecraft:music_disc_strad");
        BUKKIT_TO_MODERN.put("RECORD_10", "minecraft:music_disc_ward");
        BUKKIT_TO_MODERN.put("RECORD_11", "minecraft:music_disc_11");
        BUKKIT_TO_MODERN.put("RECORD_12", "minecraft:music_disc_wait");
    }

    /**
     * Renders a Skyblock item icon: uses SkyblockTextureAtlas (1 Draw Call NEAREST pixel sprite + full ARGB tint)
     * when a custom PNG exists for the item ID, and seamlessly falls back to vanilla ItemStack rendering otherwise.
     */
    public static void drawSkyblockIcon(GuiGraphicsExtractor g, SkyblockItem item, int x, int y, int size, int tintArgb) {
        if (item == null || size <= 0) return;
        if (SkyblockTextureAtlas.drawSprite(g, item.getId(), x, y, size, tintArgb)) {
            return;
        }
        if (((tintArgb >>> 24) & 0xFF) < 64) {
            return;
        }
        String upperId = item.getId() != null ? item.getId().toUpperCase() : "";
        if (upperId.endsWith("_GEM") && PackDisablerIconBridge.tryResolveFromPackDisabler(upperId) == null) {
            int gemCol = MiningItemTable.getColor(upperId);
            PackDisablerIconBridge.drawColorBlock(g, x, y, size, gemCol);
            return;
        }
        ItemStack icon = getClassicItemStack(item);
        float scale = size / 16.0f;
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
        g.item(icon, 0, 0);
        g.pose().popMatrix();
    }

    /**
     * Resolves the classic vanilla Minecraft ItemStack for any Skyblock item.
     */
    public static ItemStack getClassicItemStack(SkyblockItem skyblockItem) {
        if (skyblockItem == null) {
            return new ItemStack(Items.BARRIER);
        }

        String id = skyblockItem.getId();
        ItemStack cached = STACK_CACHE.get(id);
        if (cached != null) {
            return cached;
        }

        ItemStack resolved = resolveStack(skyblockItem);
        // If PackDisabler is loaded and still initializing its map, avoid caching BARRIER prematurely.
        if (!resolved.is(Items.BARRIER) || !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("packdisabler")
                || PackDisablerIconBridge.isAvailable()) {
            STACK_CACHE.put(id, resolved);
        }
        return resolved;
    }

    private static ItemStack resolveStack(SkyblockItem skyblockItem) {
        String id = skyblockItem.getId().toUpperCase();
        ItemStack pdStack = PackDisablerIconBridge.tryResolveFromPackDisabler(id);
        if (pdStack != null) {
            return pdStack;
        }
        if (id.endsWith("_GEM")) {
            return new ItemStack(Items.BARRIER);
        }
        String vanillaId = CLASSIC_ID_TO_VANILLA.get(id);
        if (vanillaId == null) {
            String mat = skyblockItem.getVanillaMaterial();
            if (mat != null && !mat.isEmpty()) {
                String upper = mat.toUpperCase().replace("MINECRAFT:", "");
                if ("PAPER".equals(upper)) {
                    vanillaId = inferFallbackForPaper(id);
                }
                if (vanillaId == null) {
                    vanillaId = BUKKIT_TO_MODERN.getOrDefault(upper, "minecraft:" + upper.toLowerCase());
                }
            }
        }

        if (vanillaId != null) {
            try {
                Identifier rl = Identifier.parse(vanillaId);
                Item item = BuiltInRegistries.ITEM.getValue(rl);
                if (item != null && item != Items.AIR) {
                    ItemStack stack = new ItemStack(item);
                    if (item == Items.PLAYER_HEAD && skyblockItem.getSkinValue() != null && !skyblockItem.getSkinValue().isEmpty()) {
                        try {
                            PropertyMap props = new PropertyMap(ImmutableMultimap.of(
                                    "textures", new Property("textures", skyblockItem.getSkinValue())));
                            UUID uid = UUID.nameUUIDFromBytes(skyblockItem.getId().getBytes(StandardCharsets.UTF_8));
                            GameProfile profile = new GameProfile(uid, "SkyblockItem", props);
                            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
                        } catch (Throwable ignored) {}
                    }
                    return stack;
                }
            } catch (Throwable ignored) {
            }
        }

        return new ItemStack(Items.PAPER);
    }

    private static String inferFallbackForPaper(String id) {
        if (id.startsWith("ESSENCE_")) return "minecraft:amethyst_shard";
        if (id.startsWith("SHARD_")) return "minecraft:prismarine_shard";
        if (id.contains("UMBER")) return "minecraft:copper_ingot";
        if (id.contains("TUNGSTEN")) return "minecraft:iron_ingot";
        if (id.contains("COMPOST")) return "minecraft:podzol";
        if (id.contains("GLACITE")) return "minecraft:packed_ice";
        if (id.endsWith("_KEY")) return "minecraft:tripwire_hook";
        if (id.endsWith("_GEM") || id.contains("GEMSTONE")) return "minecraft:emerald";
        if (id.endsWith("_TALISMAN") || id.endsWith("_RING") || id.endsWith("_ARTIFACT") || id.endsWith("_RELIC")) {
            return "minecraft:gold_nugget";
        }
        if (id.endsWith("_PLATE")) return "minecraft:heavy_weighted_pressure_plate";
        if (id.endsWith("_ALLOY")) return "minecraft:gold_block";
        return null;
    }
}
