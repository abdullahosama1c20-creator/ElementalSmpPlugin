package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Full leather armor set per element, given to a player alongside their
 * catalyst. Wearing the complete set (all 4 pieces matching your current
 * element) grants a small passive bonus - see PassiveManager.
 */
public final class ArmorSets {

    private ArmorSets() {
    }

    private static NamespacedKey key(ElementalSMP plugin) {
        return new NamespacedKey(plugin, "armor_element");
    }

    private static Color colorFor(Element element) {
        return switch (element) {
            case FIRE -> Color.fromRGB(207, 68, 24);
            case WATER -> Color.fromRGB(45, 140, 180);
            case AIR -> Color.fromRGB(230, 230, 240);
            case EARTH -> Color.fromRGB(96, 128, 56);
            case LIGHTNING -> Color.fromRGB(230, 220, 60);
            case VOID -> Color.fromRGB(60, 20, 80);
        };
    }

    public static ItemStack[] armorPieces(ElementalSMP plugin, Element element) {
        return new ItemStack[]{
                piece(plugin, element, Material.LEATHER_HELMET),
                piece(plugin, element, Material.LEATHER_CHESTPLATE),
                piece(plugin, element, Material.LEATHER_LEGGINGS),
                piece(plugin, element, Material.LEATHER_BOOTS)
        };
    }

    private static ItemStack piece(ElementalSMP plugin, Element element, Material material) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(colorFor(element));
            meta.displayName(Component.text(element.displayName() + " Gear", element.color(), TextDecoration.BOLD));
            meta.lore(List.of(Component.text("Wear the full set for a bonus.", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, element.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Removes any equipped or inventoried armor pieces bound to the given element (used on element change). */
    public static void removeArmorOfElement(ElementalSMP plugin, Player player, Element element) {
        if (element == null) {
            return;
        }
        EntityEquipment equipment = player.getEquipment();
        if (equipment != null) {
            if (isTaggedFor(plugin, equipment.getHelmet(), element)) equipment.setHelmet(null);
            if (isTaggedFor(plugin, equipment.getChestplate(), element)) equipment.setChestplate(null);
            if (isTaggedFor(plugin, equipment.getLeggings(), element)) equipment.setLeggings(null);
            if (isTaggedFor(plugin, equipment.getBoots(), element)) equipment.setBoots(null);
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTaggedFor(plugin, contents[i], element)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private static boolean isTaggedFor(ElementalSMP plugin, ItemStack item, Element element) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
        return element.name().equals(tag);
    }

    /** True if every armor slot is filled with gear matching the given element. */
    public static boolean hasFullSet(ElementalSMP plugin, Player player, Element element) {
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return false;
        }
        return isTaggedFor(plugin, equipment.getHelmet(), element)
                && isTaggedFor(plugin, equipment.getChestplate(), element)
                && isTaggedFor(plugin, equipment.getLeggings(), element)
                && isTaggedFor(plugin, equipment.getBoots(), element);
    }
}
