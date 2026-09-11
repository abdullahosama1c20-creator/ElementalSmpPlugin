package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player's catalyst and armor set are their "element" made physical -
 * losing either voluntarily (drop) or permanently (death, if unclaimed
 * before despawn) shouldn't be possible. Awakening items (Storm Core / Void
 * Tear) are deliberately NOT protected here - those are meant to be
 * spendable/tradeable one-time unlock items, not part of your identity.
 */
public class ElementalItemProtectionListener implements Listener {

    private final ElementalSMP plugin;
    private final Map<UUID, List<ItemStack>> pendingRestoration = new HashMap<>();

    public ElementalItemProtectionListener(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isProtected(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("You can't drop your elemental gear!", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> saved = new ArrayList<>();
        event.getDrops().removeIf(item -> {
            if (isProtected(item)) {
                saved.add(item);
                return true;
            }
            return false;
        });
        if (!saved.isEmpty()) {
            pendingRestoration.put(event.getEntity().getUniqueId(), saved);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> saved = pendingRestoration.remove(event.getPlayer().getUniqueId());
        if (saved == null || saved.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(saved.toArray(new ItemStack[0]));
        // If their inventory was full, drop any overflow at their feet instead of
        // silently discarding it - still safe, never handed to another player's luck.
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
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
