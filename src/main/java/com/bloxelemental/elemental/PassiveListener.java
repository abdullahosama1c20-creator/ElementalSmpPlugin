package com.bloxelemental.elemental;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class PassiveListener implements Listener {

    private final ElementalSMP plugin;

    public PassiveListener(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Element element = plugin.getMasteryManager().getElement(player.getUniqueId());
        if (element == null) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();

        if (element == Element.FIRE && (cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR)) {
            event.setCancelled(true);
            return;
        }

        if (element == Element.AIR && cause == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }

        if (element == Element.LIGHTNING && cause == EntityDamageEvent.DamageCause.LIGHTNING) {
            event.setCancelled(true);
        }
    }
}
