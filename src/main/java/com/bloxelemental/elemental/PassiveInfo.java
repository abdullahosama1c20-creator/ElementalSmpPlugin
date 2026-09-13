package com.bloxelemental.elemental;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Defines and applies each element's passive kit. Some effects (fire/lava
 * immunity, fall damage immunity, lightning immunity, melee procs) are
 * enforced live by PassiveListener/MeleePassiveListener since they trigger
 * off events rather than being a simple potion effect. This class handles
 * the parts that work as refreshable potion effects/attributes, reapplied
 * periodically by PassiveManager so they never wear off while you hold that
 * element - and, if the active element is fused with Lightning or Void, also
 * grants that fusion's own passive on top. None of this requires wearing any
 * gear - armor was removed, and every passive/weakness works from the
 * element itself.
 */
public final class PassiveInfo {

    /** Earth's passive knockback reduction, expressed as a 0-1 attribute value. */
    public static final double EARTH_KNOCKBACK_RESISTANCE = 0.15D;

    private PassiveInfo() {
    }

    public static String describe(Element element) {
        return switch (element) {
            case FIRE -> "Thermal Adaptation: immune to fire/lava. Meleeing a burning enemy heals half a heart (2s cooldown).";
            case WATER -> "Aquatic Swiftness: permanent Dolphin's Grace + Water Breathing. +10% melee damage while touching water or rain. Mines faster than normal underwater (strong Haste while submerged).";
            case AIR -> "Lightfoot: fully immune to fall damage. (No passive speed effect.)";
            case EARTH -> "Earthen Armor: 15% less knockback always, plus Resistance I for 3s whenever you stand still for 2+ seconds.";
            case LIGHTNING -> "Static Charge: permanent Speed I, immune to lightning strikes. Melee hits have a chance to briefly stun.";
            case VOID -> "Umbral Step: permanent Night Vision + Slow Falling. Melee hits have a chance to briefly blind.";
        };
    }

    /** Appends the fusion's passive to a base description, if the given element is fused. */
    public static String describeWithFusion(ElementalSMP plugin, java.util.UUID uuid, Element element) {
        String base = describe(element);
        Element fusion = plugin.getMasteryManager().getFusion(uuid, element);
        if (fusion == null) {
            return base;
        }
        return base + " Fused with " + fusion.displayName() + ": " + describe(fusion);
    }

    /** True if this element has a buff-style passive component the player can toggle off. */
    public static boolean hasToggleableBuff(Element element) {
        return element == Element.WATER || element == Element.LIGHTNING || element == Element.VOID;
    }

    /**
     * Re-applies this element's refreshable buffs (potion effects/attributes),
     * plus its fusion's buffs on top if fused - skipping either bundle if the
     * player has toggled it off via /element gui. Safe to call repeatedly.
     */
    public static void applyBuffs(ElementalSMP plugin, Player player, Element element) {
        int refreshTicks = 20 * 40; // reapplied every 30s by PassiveManager, so 40s covers the gap
        UUID uuid = player.getUniqueId();

        if (!hasToggleableBuff(element) || plugin.getMasteryManager().isPassiveEnabled(uuid, element)) {
            applyBaseBuffs(player, element, refreshTicks);
        }

        Element fusion = plugin.getMasteryManager().getFusion(uuid, element);
        if (fusion != null && (!hasToggleableBuff(fusion) || plugin.getMasteryManager().isPassiveEnabled(uuid, fusion))) {
            applyBaseBuffs(player, fusion, refreshTicks);
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

    private static void applyBaseBuffs(Player player, Element element, int refreshTicks) {
        switch (element) {
            case WATER -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, refreshTicks, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, refreshTicks, 0, true, false));
                // Faster-than-normal mining while submerged - strong enough Haste overcomes vanilla's
                // underwater mining penalty on its own, no armor/enchant needed.
                boolean submerged = player.getEyeLocation().getBlock().getType() == Material.WATER;
                if (submerged) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, refreshTicks, 2, true, false));
                }
            }
            case LIGHTNING -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, refreshTicks, 0, true, false));
            case VOID -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, refreshTicks, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, refreshTicks, 0, true, false));
            }
            default -> {
                // FIRE and AIR have no permanent potion buff - their passives are purely event-driven.
            }
        }
    }
}
