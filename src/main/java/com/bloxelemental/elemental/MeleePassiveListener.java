package com.bloxelemental.elemental;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Melee-triggered passive procs that don't fit as simple potion effects:
 * Fire's lifesteal-on-burning-target, Water's wet/rain damage bonus,
 * Lightning's stun chance, Void's blind chance, and retaliation for anyone
 * who melees a player currently shielded by Fire's Fire Shield ability.
 */
public class MeleePassiveListener implements Listener {

    private static final double FIRE_HEAL_AMOUNT = 1.0D; // half a heart
    private static final long FIRE_HEAL_COOLDOWN_MS = 2000L;
    private static final double WATER_WET_DAMAGE_BONUS = 1.10D;
    private static final double LIGHTNING_STUN_CHANCE = 0.15D;
    private static final double VOID_BLIND_CHANCE = 0.15D;

    private final ElementalSMP plugin;
    private final AbilityListener abilityListener;
    private final Random random = new Random();
    private final Map<UUID, Long> fireHealCooldowns = new HashMap<>();

    public MeleePassiveListener(ElementalSMP plugin, AbilityListener abilityListener) {
        this.plugin = plugin;
        this.abilityListener = abilityListener;
    }

    @EventHandler
    public void onMelee(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageByEntityEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // Fire Shield retaliation: check the victim's shield status regardless of the victim's element,
        // since the shield is a temporary buff independent of what the attacker is.
        if (victim instanceof Player shieldedPlayer && abilityListener.hasFireShield(shieldedPlayer.getUniqueId())) {
            attacker.setFireTicks(100);
            attacker.setVelocity(attacker.getLocation().toVector()
                    .subtract(victim.getLocation().toVector()).normalize().setY(0.3).multiply(1.5));
        }

        Element attackerElement = plugin.getMasteryManager().getElement(attacker.getUniqueId());
        if (attackerElement == null) {
            return;
        }

        switch (attackerElement) {
            case FIRE -> handleFireLifesteal(attacker, victim);
            case WATER -> handleWaterWetBonus(attacker, event);
            case LIGHTNING -> handleLightningStun(victim);
            case VOID -> handleVoidBlind(victim);
            default -> {
            }
        }
    }

    private void handleFireLifesteal(Player attacker, LivingEntity victim) {
        if (victim.getFireTicks() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long readyAt = fireHealCooldowns.get(attacker.getUniqueId());
        if (readyAt != null && readyAt > now) {
            return;
        }
        fireHealCooldowns.put(attacker.getUniqueId(), now + FIRE_HEAL_COOLDOWN_MS);
        double maxHealth = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + FIRE_HEAL_AMOUNT));
    }

    private void handleWaterWetBonus(Player attacker, EntityDamageByEntityEvent event) {
        if (attacker.isInWater() || attacker.getWorld().hasStorm()) {
            event.setDamage(event.getDamage() * WATER_WET_DAMAGE_BONUS);
        }
    }

    private void handleLightningStun(LivingEntity victim) {
        if (random.nextDouble() < LIGHTNING_STUN_CHANCE) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 4, true, true));
        }
    }

    private void handleVoidBlind(LivingEntity victim) {
        if (random.nextDouble() < VOID_BLIND_CHANCE) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, true, true));
        }
    }
}
