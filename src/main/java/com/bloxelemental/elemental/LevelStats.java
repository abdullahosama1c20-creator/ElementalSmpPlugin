package com.bloxelemental.elemental;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * Leveling up your active element now grants small permanent stat bonuses on
 * top of unlocking abilities - Max Health and Attack Damage both scale
 * linearly with your current active element's level (1-100). These are
 * re-applied (not stacked) every time they're computed, keyed off whichever
 * element is currently active, so switching elements changes your stats to
 * match that element's own level.
 */
public final class LevelStats {

    private static final double VANILLA_BASE_HEALTH = 20.0D;
    private static final double VANILLA_BASE_ATTACK_DAMAGE = 1.0D;

    private LevelStats() {
    }

    public static void apply(ElementalSMP plugin, Player player) {
        int level = plugin.getMasteryManager().getLevel(player.getUniqueId());
        double healthPerLevel = plugin.getConfig().getDouble("mastery.health-per-level", 0.1D);
        double damagePerLevel = plugin.getConfig().getDouble("mastery.damage-per-level", 0.02D);

        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double newMax = VANILLA_BASE_HEALTH + (level * healthPerLevel);
            health.setBaseValue(newMax);
            if (player.getHealth() > health.getValue()) {
                player.setHealth(health.getValue());
            }
        }

        AttributeInstance damage = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(VANILLA_BASE_ATTACK_DAMAGE + (level * damagePerLevel));
        }
    }
}
