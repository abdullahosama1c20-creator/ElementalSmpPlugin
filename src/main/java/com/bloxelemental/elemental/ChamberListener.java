package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.UUID;

public class ChamberListener implements Listener {

    private final ElementalSMP plugin;
    private final NamespacedKey chamberMobKey;
    private final Random random = new Random();

    public ChamberListener(ElementalSMP plugin) {
        this.plugin = plugin;
        this.chamberMobKey = new NamespacedKey(plugin, "chamber_mob");
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }
        ChamberManager chamberManager = plugin.getChamberManager();
        Element activeElement = chamberManager.getActiveElement();
        if (activeElement == null || !chamberManager.isInChamber(event.getLocation())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }

        ChamberTheme theme = ChamberTheme.forElement(activeElement);
        living.getPersistentDataContainer().set(chamberMobKey, PersistentDataType.BOOLEAN, true);
        living.getPersistentDataContainer().set(new NamespacedKey(plugin, "chamber_element"), PersistentDataType.STRING, activeElement.name());
        living.customName(Component.text(activeElement.displayName() + " Chamber Guardian", activeElement.color()));
        living.setCustomNameVisible(true);

        dressMob(living, theme);

        living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 60 * 10, 0, true, false));

        double bonusHealth = 4.0D + random.nextInt(6);
        if (chamberManager.isElite()) {
            bonusHealth *= 2;
        }
        var maxHealthAttr = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(maxHealthAttr.getBaseValue() + bonusHealth);
            living.setHealth(maxHealthAttr.getValue());
        }
    }

    private void dressMob(LivingEntity living, ChamberTheme theme) {
        var equipment = living.getEquipment();
        if (equipment == null) {
            return;
        }
        // Endermen, Shulkers, Guardians and similar can't wear armor - skip gently.
        EntityType type = living.getType();
        if (type == EntityType.ENDERMAN || type == EntityType.SHULKER || type == EntityType.GUARDIAN
                || type == EntityType.SILVERFISH || type == EntityType.PHANTOM || type == EntityType.BREEZE
                || type == EntityType.VEX) {
            return;
        }

        ItemStack helmet = leatherPiece(org.bukkit.Material.LEATHER_HELMET, theme.armorColor());
        ItemStack chestplate = leatherPiece(org.bukkit.Material.LEATHER_CHESTPLATE, theme.armorColor());
        equipment.setHelmet(helmet);
        equipment.setChestplate(chestplate);
        equipment.setHelmetDropChance(0.0F);
        equipment.setChestplateDropChance(0.0F);
    }

    private ItemStack leatherPiece(org.bukkit.Material material, org.bukkit.Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Boolean isChamberMob = entity.getPersistentDataContainer().get(chamberMobKey, PersistentDataType.BOOLEAN);
        if (isChamberMob == null || !isChamberMob) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        ChamberManager chamberManager = plugin.getChamberManager();
        Element activeElement = chamberManager.getActiveElement();
        UUID killerUuid = killer.getUniqueId();
        Element killerElement = plugin.getMasteryManager().getElement(killerUuid);

        // Matching-element players get a bonus item drop on top of the mob's normal loot.
        if (activeElement != null && killerElement == activeElement) {
            ChamberTheme theme = ChamberTheme.forElement(activeElement);
            org.bukkit.Material bonus = theme.lootPool()[random.nextInt(theme.lootPool().length)];
            event.getDrops().add(new ItemStack(bonus, 1));
            killer.sendMessage(Component.text("Your matching element grants bonus chamber loot!", NamedTextColor.LIGHT_PURPLE));
        }

        boolean justCleared = chamberManager.registerKill(killer);
        if (justCleared) {
            plugin.getMasteryManager().incrementChambersCleared(killerUuid);
        }
    }
}
