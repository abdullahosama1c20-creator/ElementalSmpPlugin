package com.bloxelemental.elemental;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Everything needed to build and populate one element's chamber structure:
 * the block palette, which mobs spawn there, how they're dressed, the loot
 * they can drop, and how the chamber's Y coordinate is chosen.
 */
public record ChamberTheme(
        Element element,
        Material wallMaterial,
        Material floorMaterial,
        Material ceilingMaterial,
        Material accentMaterial,
        Material lightMaterial,
        boolean floodInterior,
        boolean openCeiling,
        EntityType[] mobTypes,
        Color armorColor,
        Material[] lootPool,
        Material rareLoot,
        YPlacement yPlacement
) {
    public enum YPlacement {
        UNDERGROUND_MID,   // ~ y 30-45, carved into the ground
        UNDERGROUND_DEEP,  // ~ y 6-16, near bedrock
        SURFACE,           // highest block + 1
        SKY                // fixed high altitude, floating
    }

    public static ChamberTheme forElement(Element element) {
        return switch (element) {
            case FIRE -> new ChamberTheme(
                    Element.FIRE, Material.NETHER_BRICKS, Material.BLACKSTONE, Material.NETHER_BRICKS,
                    Material.MAGMA_BLOCK, Material.GLOWSTONE, false, false,
                    new EntityType[]{EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON},
                    Color.fromRGB(207, 68, 24),
                    new Material[]{Material.BLAZE_ROD, Material.MAGMA_CREAM, Material.FIRE_CHARGE, Material.GOLDEN_APPLE},
                    Material.NETHERITE_SCRAP,
                    YPlacement.UNDERGROUND_MID
            );
            case WATER -> new ChamberTheme(
                    Element.WATER, Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE,
                    Material.SEA_LANTERN, Material.SEA_LANTERN, true, false,
                    new EntityType[]{EntityType.DROWNED, EntityType.GUARDIAN},
                    Color.fromRGB(45, 140, 180),
                    new Material[]{Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS, Material.NAUTILUS_SHELL, Material.TRIDENT},
                    Material.HEART_OF_THE_SEA,
                    YPlacement.UNDERGROUND_MID
            );
            case AIR -> new ChamberTheme(
                    Element.AIR, Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ, Material.QUARTZ_BLOCK,
                    Material.WHITE_CONCRETE, Material.SEA_LANTERN, false, true,
                    new EntityType[]{EntityType.PHANTOM, EntityType.VEX, EntityType.BREEZE},
                    Color.fromRGB(230, 230, 240),
                    new Material[]{Material.FEATHER, Material.PHANTOM_MEMBRANE, Material.FIREWORK_ROCKET, Material.SLIME_BALL},
                    Material.ELYTRA,
                    YPlacement.SKY
            );
            case EARTH -> new ChamberTheme(
                    Element.EARTH, Material.DEEPSLATE_BRICKS, Material.MOSSY_COBBLESTONE, Material.DEEPSLATE_TILES,
                    Material.MOSSY_STONE_BRICKS, Material.OCHRE_FROGLIGHT, false, false,
                    new EntityType[]{EntityType.HUSK, EntityType.SILVERFISH, EntityType.ZOMBIE},
                    Color.fromRGB(96, 128, 56),
                    new Material[]{Material.EMERALD, Material.IRON_INGOT, Material.DIAMOND, Material.MOSS_BLOCK},
                    Material.TOTEM_OF_UNDYING,
                    YPlacement.UNDERGROUND_MID
            );
            case LIGHTNING -> new ChamberTheme(
                    Element.LIGHTNING, Material.WAXED_OXIDIZED_COPPER, Material.DEEPSLATE, Material.COPPER_BLOCK,
                    Material.LIGHTNING_ROD, Material.SEA_LANTERN, false, true,
                    new EntityType[]{EntityType.SKELETON, EntityType.PILLAGER, EntityType.VINDICATOR},
                    Color.fromRGB(230, 220, 60),
                    new Material[]{Material.LIGHTNING_ROD, Material.COPPER_INGOT, Material.EMERALD, Material.ARROW},
                    Material.TRIDENT,
                    YPlacement.SURFACE
            );
            case VOID -> new ChamberTheme(
                    Element.VOID, Material.OBSIDIAN, Material.BLACKSTONE, Material.CRYING_OBSIDIAN,
                    Material.CRYING_OBSIDIAN, Material.CRYING_OBSIDIAN, false, false,
                    new EntityType[]{EntityType.ENDERMAN, EntityType.SHULKER, EntityType.VEX},
                    Color.fromRGB(60, 20, 80),
                    new Material[]{Material.ENDER_PEARL, Material.CHORUS_FRUIT, Material.SHULKER_SHELL, Material.OBSIDIAN},
                    Material.ELYTRA,
                    YPlacement.UNDERGROUND_DEEP
            );
        };
    }
}
