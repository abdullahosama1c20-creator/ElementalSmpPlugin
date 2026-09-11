package com.bloxelemental.elemental;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PassiveManager {

    private static final int STILLNESS_CHECK_PERIOD_TICKS = 10; // 0.5s
    private static final int STILLNESS_TICKS_REQUIRED = 4;      // 4 * 0.5s = 2s standing still
    private static final double STILLNESS_MOVE_TOLERANCE = 0.05D;

    private final ElementalSMP plugin;
    private final Map<UUID, Location> earthLastLocation = new HashMap<>();
    private final Map<UUID, Integer> earthStillTicks = new HashMap<>();
    private final Map<UUID, Long> earthNextResistanceAt = new HashMap<>();

    public PassiveManager(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L * 30L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickEarthStillness, 0L, STILLNESS_CHECK_PERIOD_TICKS);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Element element = plugin.getMasteryManager().getElement(player.getUniqueId());
            if (element != null) {
                PassiveInfo.applyBuffs(player, element);
                applySetBonus(player, element);
            }
        }
    }

    private void applySetBonus(Player player, Element element) {
        if (ArmorSets.hasFullSet(plugin, player, element)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 40, 0, true, false));
        }
    }

    /** Earthen Armor: standing still for 2+ seconds grants Resistance I for 3s. */
    private void tickEarthStillness() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Element element = plugin.getMasteryManager().getElement(uuid);
            if (element != Element.EARTH) {
                earthLastLocation.remove(uuid);
                earthStillTicks.remove(uuid);
                continue;
            }

            Location current = player.getLocation();
            Location last = earthLastLocation.get(uuid);
            boolean stillMoved = last == null || last.getWorld() != current.getWorld()
                    || last.distanceSquared(current) > STILLNESS_MOVE_TOLERANCE * STILLNESS_MOVE_TOLERANCE;

            if (stillMoved) {
                earthLastLocation.put(uuid, current);
                earthStillTicks.put(uuid, 0);
                continue;
            }

            int streak = earthStillTicks.getOrDefault(uuid, 0) + 1;
            earthStillTicks.put(uuid, streak);

            if (streak >= STILLNESS_TICKS_REQUIRED) {
                long now = System.currentTimeMillis();
                Long nextAt = earthNextResistanceAt.get(uuid);
                if (nextAt == null || nextAt <= now) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, true));
                    earthNextResistanceAt.put(uuid, now + 3000L);
                }
            }
        }
    }
}
