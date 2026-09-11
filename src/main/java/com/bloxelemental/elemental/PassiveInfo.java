package com.bloxelemental.elemental;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Defines and applies each element's passive kit. Some effects (fire/lava
 * immunity, reduced fall damage, lightning immunity, melee procs) are
 * enforced live by PassiveListener/MeleePassiveListener since they trigger
 * off events rather than being a simple potion effect. This class handles
 * the parts that work as refreshable potion effects/attributes, reapplied
 * periodically by PassiveManager so they never wear off while you hold
 * that element.
 */
public final class PassiveInfo {

    /** Earth's passive knockback reduction, expressed as a 0-1 attribute value. */
    public static final double EARTH_KNOCKBACK_RESISTANCE = 0.15D;

    private PassiveInfo() {
    }

    public static String describe(Element element) {
        return switch (element) {
            case FIRE -> "Thermal Adaptation: immune to fire/lava. Meleeing a burning enemy heals half a heart (2s cooldown).";
            case WATER -> "Aquatic Swiftness: permanent Dolphin's Grace + Water Breathing. +10% melee damage while touching water or rain.";
            case AIR -> "Lightfoot: permanent Speed I and 50% less fall damage.";
            case EARTH -> "Earthen Armor: 15% less knockback always, plus Resistance I for 3s whenever you stand still for 2+ seconds.";
            case LIGHTNING -> "Static Charge: permanent Speed I, immune to lightning strikes. Melee hits have a chance to briefly stun.";
            case VOID -> "Umbral Step: permanent Night Vision + Slow Falling. Melee hits have a chance to briefly blind.";
        };
    }

    /** Re-applies this element's refreshable buffs (potion effects/attributes). Safe to call repeatedly. */
    public static void applyBuffs(Player player, Element element) {
        int refreshTicks = 20 * 40; // reapplied every 30s by PassiveManager, so 40s covers the gap
        switch (element) {
            case WATER -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, refreshTicks, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, refreshTicks, 0, true, false));
            }
            case AIR -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, refreshTicks, 0, true, false));
            case LIGHTNING -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, refreshTicks, 0, true, false));
            case VOID -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, refreshTicks, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, refreshTicks, 0, true, false));
            }
            default -> {
                // FIRE has no permanent potion buff - its passive is purely event-driven.
            }
        }

        AttributeInstance knockbackResistance = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackResistance != null) {
            if (element == Element.EARTH) {
                knockbackResistance.setBaseValue(EARTH_KNOCKBACK_RESISTANCE);
            } else if (knockbackResistance.getBaseValue() == EARTH_KNOCKBACK_RESISTANCE) {
                // Reset if they switched away from Earth, so the bonus doesn't linger.
                knockbackResistance.setBaseValue(0.0D);
            }
        }
    }
}
