package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Catalysts are built on top of a Nether Star, which is normally a beacon
 * ingredient - without this, a player could feed their ability item into a
 * beacon recipe. This blocks any crafting recipe that has a catalyst
 * (or armor-set piece) anywhere in the grid, regardless of what the recipe is.
 */
public class CraftingProtectionListener implements Listener {

    private final ElementalSMP plugin;

    public CraftingProtectionListener(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isProtected(item)) {
                event.getInventory().setResult(null);
                for (HumanEntity viewer : event.getViewers()) {
                    viewer.sendMessage(Component.text("Elemental gear can't be used in crafting recipes.", NamedTextColor.RED));
                }
                return;
            }
        }
    }

    private boolean isProtected(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        Boolean catalyst = pdc.get(new NamespacedKey(plugin, "elemental_catalyst"), PersistentDataType.BOOLEAN);
        String armorElement = pdc.get(new NamespacedKey(plugin, "armor_element"), PersistentDataType.STRING);
        return Boolean.TRUE.equals(catalyst) || armorElement != null;
    }
}
