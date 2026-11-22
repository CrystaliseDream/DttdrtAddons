package com.dttdrt.addons.features.market.model;

/**
 * Represents a Hypixel Skyblock item with its classic vanilla material and optional skull skin.
 */
public class SkyblockItem {
    private final String id;
    private final String name;
    private final String tier;
    private final String vanillaMaterial;
    private final String category;
    private final boolean bazaarEligible;
    private final String skinValue;

    public SkyblockItem(String id, String name, String tier, String vanillaMaterial, String category, boolean bazaarEligible) {
        this(id, name, tier, vanillaMaterial, category, bazaarEligible, null);
    }

    public SkyblockItem(String id, String name, String tier, String vanillaMaterial, String category, boolean bazaarEligible, String skinValue) {
        this.id = id;
        this.name = name;
        this.tier = tier != null ? tier.toUpperCase() : "COMMON";
        this.vanillaMaterial = vanillaMaterial != null ? vanillaMaterial : "PAPER";
        this.category = category != null ? category : "MISC";
        this.bazaarEligible = bazaarEligible;
        this.skinValue = skinValue;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTier() {
        return tier;
    }

    public String getVanillaMaterial() {
        return vanillaMaterial;
    }

    public String getCategory() {
        return category;
    }

    public boolean isBazaarEligible() {
        return bazaarEligible;
    }

    public String getSkinValue() {
        return skinValue;
    }

    /**
     * ARGB color hex (with full 0xFF alpha) for the Hypixel Skyblock rarity tier.
     */
    public int getTierColor() {
        return switch (tier) {
            case "UNCOMMON" -> 0xFF55FF55;
            case "RARE" -> 0xFF5555FF;
            case "EPIC" -> 0xFFAA00AA;
            case "LEGENDARY" -> 0xFFFFAA00;
            case "MYTHIC" -> 0xFFFF55FF;
            case "DIVINE" -> 0xFF55FFFF;
            case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
            default -> 0xFFFFFFFF;
        };
    }

    @Override
    public String toString() {
        return "[" + tier + "] " + name + " (" + id + ")";
    }
}
