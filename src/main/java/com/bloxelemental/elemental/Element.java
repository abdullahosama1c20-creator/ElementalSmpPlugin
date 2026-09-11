package com.bloxelemental.elemental;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * All elements available in the plugin. FIRE, WATER, AIR and EARTH are starter
 * elements chosen through the /element gui command. LIGHTNING and VOID are
 * advanced elements only obtainable through the awakening process.
 */
public enum Element {

    FIRE("Fire", NamedTextColor.RED, Material.BLAZE_POWDER, true),
    WATER("Water", NamedTextColor.AQUA, Material.PRISMARINE_CRYSTALS, true),
    AIR("Air", NamedTextColor.WHITE, Material.FEATHER, true),
    EARTH("Earth", NamedTextColor.GREEN, Material.STONE, true),
    LIGHTNING("Lightning", NamedTextColor.YELLOW, Material.LIGHTNING_ROD, false),
    VOID("Void", NamedTextColor.DARK_PURPLE, Material.END_CRYSTAL, false);

    private final String displayName;
    private final NamedTextColor color;
    private final Material icon;
    private final boolean starter;

    Element(String displayName, NamedTextColor color, Material icon, boolean starter) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
        this.starter = starter;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }

    public Material icon() {
        return icon;
    }

    public boolean isStarter() {
        return starter;
    }

    /**
     * Parses an element from a case-insensitive command argument. Returns null if invalid.
     */
    public static Element fromArgument(String arg) {
        if (arg == null) {
            return null;
        }
        for (Element element : values()) {
            if (element.name().equalsIgnoreCase(arg)) {
                return element;
            }
        }
        return null;
    }
}
