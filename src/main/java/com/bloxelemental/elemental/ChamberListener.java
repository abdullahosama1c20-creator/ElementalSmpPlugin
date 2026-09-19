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
import org.bukkit.event.entity.EntityCombustEvent;
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

    /**
     * Some chamber mobs (Zombie, Skeleton) normally catch fire and die in
     * direct sunlight - without this, "the spawners aren't spawning" would
     * actually just be them burning to death seconds after spawning during
     * the day, especially in open-ceiling chambers like Lightning's. This
     * makes every chamber mob immune to sunlight combustion specifically,
     * without touching any other fire source (lava, our own Fire abilities, etc.).
     */
    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        Boolean isChamberMob = living.getPersistentDataContainer().get(chamberMobKey, PersistentDataType.BOOLEAN);
        if (Boolean.TRUE.equals(isChamberMob)) {
            event.setCancelled(true);
        }
    }

    /** Chamber Creepers still hurt you, but never touch the chamber's own terrain. */
    @EventHandler
    public void onExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        Boolean isChamberMob = event.getEntity().getPersistentDataContainer().get(chamberMobKey, PersistentDataType.BOOLEAN);
        if (Boolean.TRUE.equals(isChamberMob)) {
            event.blockList().clear();
        }
    }

    /**
     * Vanilla never lets Silk Touch drop a spawner, full stop - that's hardcoded
     * regardless of enchantment. This carves out a narrow exception for chamber
     * spawners specifically: Silk Touch on one of ours gives back a spawner item
     * pre-configured with whatever mob it was set to spawn. Any other spawner on
     * the server (dungeons, player-placed, etc.) is completely untouched by this -
     * still fully vanilla, still never drops. Works on spawners from ANY chamber,
     * including ones superseded by a newer roll - detection is by a persistent
     * tag on the block itself, not by whether its chamber is still the active one.
     */
    @EventHandler
    public void onSpawnerBreak(org.bukkit.event.block.BlockBreakEvent event) {
        org.bukkit.block.Block block = event.getBlock();
        if (!ChamberManager.isChamberSpawnerBlock(plugin, block)) {
            return; // not one of ours - leave fully vanilla, including the no-drop rule
        }

        org.bukkit.inventory.ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        boolean hasSilkTouch = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SILK_TOUCH) > 0;

        event.setDropItems(false); // we handle the drop manually (or not at all without Silk Touch)

        if (hasSilkTouch) {
            EntityType spawnedType = EntityType.PIG;
            if (block.getState() instanceof org.bukkit.block.CreatureSpawner spawner) {
                spawnedType = spawner.getSpawnedType();
            }
            block.getWorld().dropItemNaturally(block.getLocation(), ChamberManager.createSpawnerItem(plugin, spawnedType));
            event.getPlayer().sendMessage(Component.text("Silk Touch harvested the chamber spawner!", NamedTextColor.LIGHT_PURPLE));
        }

        plugin.getChamberManager().removeSpawnerLocation(block.getLocation());
    }

    /**
     * Safety net for placing a harvested chamber spawner elsewhere: explicitly
     * re-applies the full spawner configuration (not just the mob type) right
     * after placement, so it's guaranteed to actually spawn something and stay
     * tagged as ours, rather than relying entirely on the game's own
     * item-to-block state transfer.
     */
    @EventHandler
    public void onSpawnerPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (event.getBlock().getType() != org.bukkit.Material.SPAWNER) {
            return;
        }
        org.bukkit.inventory.ItemStack placed = event.getItemInHand();
        if (!ChamberManager.isChamberSpawnerItem(plugin, placed)) {
            return;
        }
        EntityType type = EntityType.PIG;
        if (placed.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta
                && meta.getBlockState() instanceof org.bukkit.block.CreatureSpawner itemSpawner) {
            type = itemSpawner.getSpawnedType();
        }
        if (event.getBlock().getState() instanceof org.bukkit.block.CreatureSpawner spawner) {
            ChamberManager.configureSpawnerState(plugin, spawner, type);
            spawner.update(true);
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
