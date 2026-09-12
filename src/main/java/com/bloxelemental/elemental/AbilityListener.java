package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casts elemental abilities from held catalyst items, tracks per-ability
 * cooldowns with an action bar countdown, handles Storm Core / Void Tear
 * awakening consumption, and grants Mastery XP for combat kills.
 */
public class AbilityListener implements Listener {

    private static NamespacedKey key(ElementalSMP plugin, String name) {
        return new NamespacedKey(plugin, name);
    }

    private final ElementalSMP plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    /** UUID -> timestamp (ms) up to which a pending awaken is confirmed by a second click. */
    private final Map<UUID, Long> pendingAwakenConfirmations = new HashMap<>();
    private static final long AWAKEN_CONFIRM_WINDOW_MS = 15_000L;
    /** UUID -> timestamp (ms) until which Fire Shield retaliation is active. */
    private final Map<UUID, Long> fireShieldExpiry = new HashMap<>();
    /** UUID -> timestamp (ms) when a Healing Surge channel completes, if not interrupted first. */
    private final Map<UUID, Long> healChannelUntil = new HashMap<>();

    public AbilityListener(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    public boolean hasFireShield(UUID uuid) {
        Long expiry = fireShieldExpiry.get(uuid);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /** Healing Surge is interrupted the moment the channeling player takes any damage. */
    @EventHandler
    public void onDamageInterruptsChannel(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getDamage() <= 0) {
            return;
        }
        if (healChannelUntil.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("Healing Surge interrupted!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 0.8F);
        }
    }

    // ---------------------------------------------------------------------
    // Item factories
    // ---------------------------------------------------------------------

    public static ItemStack catalystItem(ElementalSMP plugin, Element element) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(element.displayName() + " Catalyst", element.color(), TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Right-click ", NamedTextColor.GRAY)
                        .append(Component.text("(Lv.1) Basic: ", NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(AbilityInfo.describe(element, Tier.BASIC), NamedTextColor.GRAY)),
                Component.text("Shift+Right-click ", NamedTextColor.GRAY)
                        .append(Component.text("(Lv.25) Mobility: ", NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(AbilityInfo.describe(element, Tier.MOBILITY), NamedTextColor.GRAY)),
                Component.text("Off-hand click ", NamedTextColor.GRAY)
                        .append(Component.text("(Lv.50) Heavy: ", NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(AbilityInfo.describe(element, Tier.HEAVY), NamedTextColor.GRAY)),
                Component.text("Shift+Off-hand ", NamedTextColor.GRAY)
                        .append(Component.text("(Lv.100) Ultimate: ", NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(AbilityInfo.describe(element, Tier.ULTIMATE), NamedTextColor.GRAY)),
                Component.text("Run /element abilities for the full list.", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(key(plugin, "elemental_catalyst"), PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(key(plugin, "catalyst_element"), PersistentDataType.STRING, element.name());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack stormCoreItem() {
        ItemStack item = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Storm Core", NamedTextColor.YELLOW, TextDecoration.BOLD));
        meta.lore(List.of(Component.text("Shift + Right-click at Mastery Lv.100", NamedTextColor.GRAY),
                Component.text("to awaken the element of Lightning.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack voidTearItem() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Void Tear", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        meta.lore(List.of(Component.text("Shift + Right-click at Mastery Lv.100", NamedTextColor.GRAY),
                Component.text("to awaken the element of Void.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    // ---------------------------------------------------------------------
    // Interaction handling
    // ---------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        Player player = event.getPlayer();
        ItemMeta meta = item.getItemMeta();

        if (Boolean.TRUE.equals(meta.getPersistentDataContainer().get(key(plugin, "elemental_catalyst"), PersistentDataType.BOOLEAN))) {
            event.setCancelled(true);
            handleCatalystUse(player, item, event.getHand());
            return;
        }

        if (item.getType() == Material.BREEZE_ROD && "Storm Core".equals(plainName(meta))) {
            event.setCancelled(true);
            handleAwakening(player, item, Element.LIGHTNING);
            return;
        }
        if (item.getType() == Material.ECHO_SHARD && "Void Tear".equals(plainName(meta))) {
            event.setCancelled(true);
            handleAwakening(player, item, Element.VOID);
        }
    }

    private String plainName(ItemMeta meta) {
        Component name = meta.displayName();
        return name == null ? null : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
    }

    private void handleAwakening(Player player, ItemStack item, Element target) {
        MasteryManager manager = plugin.getMasteryManager();
        UUID uuid = player.getUniqueId();
        if (manager.ownsElement(uuid, target)) {
            player.sendMessage(Component.text("You have already awakened " + target.displayName() + ".", NamedTextColor.RED));
            return;
        }
        if (!manager.isAwakeningEligible(uuid)) {
            player.sendMessage(Component.text("You need Mastery Level 100 on a starter element before you can awaken.", NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        Long confirmBy = pendingAwakenConfirmations.get(uuid);
        if (confirmBy == null || confirmBy < now) {
            pendingAwakenConfirmations.put(uuid, now + AWAKEN_CONFIRM_WINDOW_MS);
            player.sendMessage(Component.text("This will consume the item and unlock ", NamedTextColor.YELLOW)
                    .append(Component.text(target.displayName(), target.color(), TextDecoration.BOLD))
                    .append(Component.text(" as an additional element. Your other elements are untouched.", NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("Right-click again within 15 seconds to confirm.", NamedTextColor.GRAY));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.7F);
            return;
        }
        pendingAwakenConfirmations.remove(uuid);

        item.setAmount(item.getAmount() - 1);
        manager.awaken(uuid, target);

        player.getInventory().addItem(catalystItem(plugin, target));
        player.getInventory().addItem(ArmorSets.armorPieces(plugin, target));
        PassiveInfo.applyBuffs(player, target);
        LevelStats.apply(plugin, player);

        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 120, 1, 1.5, 1, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, target == Element.VOID ? 0.5F : 1.5F);
        player.sendMessage(Component.text("You have awakened the element of ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(target.displayName(), target.color(), TextDecoration.BOLD))
                .append(Component.text("! It is now your active element.", NamedTextColor.LIGHT_PURPLE)));
        player.sendMessage(Component.text("Your new " + target.displayName() + " Catalyst has been added to your inventory. Switch back anytime in /element gui.", NamedTextColor.GRAY));
    }

    /**
     * Removes every catalyst item bound to the given element from a player's
     * inventory (used after an element change, since the old catalyst no longer works).
     */
    public static void removeCatalystsOfElement(ElementalSMP plugin, Player player, Element element) {
        if (element == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !stack.hasItemMeta()) {
                continue;
            }
            String tag = stack.getItemMeta().getPersistentDataContainer()
                    .get(key(plugin, "catalyst_element"), PersistentDataType.STRING);
            if (element.name().equals(tag)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private void handleCatalystUse(Player player, ItemStack item, EquipmentSlot hand) {
        MasteryManager manager = plugin.getMasteryManager();
        UUID uuid = player.getUniqueId();
        Element element = manager.getElement(uuid);
        if (element == null) {
            player.sendMessage(Component.text("You must choose an element with /element gui first.", NamedTextColor.RED));
            return;
        }

        String catalystElement = item.getItemMeta().getPersistentDataContainer()
                .get(key(plugin, "catalyst_element"), PersistentDataType.STRING);
        if (catalystElement == null || !catalystElement.equals(element.name())) {
            player.sendMessage(Component.text("This catalyst is not bound to your element.", NamedTextColor.RED));
            return;
        }

        boolean offhand = hand == EquipmentSlot.OFF_HAND;
        boolean sneaking = player.isSneaking();
        Tier tier;
        if (!offhand && !sneaking) {
            tier = Tier.BASIC;
        } else if (!offhand) {
            tier = Tier.MOBILITY;
        } else if (!sneaking) {
            tier = Tier.HEAVY;
        } else {
            tier = Tier.ULTIMATE;
        }

        if (!manager.canUseTier(uuid, tier.requiredLevel)) {
            player.sendMessage(Component.text(tier.label + " requires Mastery Level " + tier.requiredLevel + ".", NamedTextColor.RED));
            return;
        }

        String cooldownKey = element.name() + "_" + tier.name();
        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(uuid, k -> new HashMap<>());
        Long readyAt = playerCooldowns.get(cooldownKey);
        if (readyAt != null && readyAt > now) {
            double remaining = (readyAt - now) / 1000.0;
            player.sendActionBar(Component.text(String.format(tier.label + " on cooldown: %.1fs", remaining), NamedTextColor.RED));
            return;
        }

        castAbility(player, element, tier);
        double cooldownSeconds = tier.cooldownSecondsForLevel(manager.getLevel(uuid));
        long cooldownEnd = now + (long) (cooldownSeconds * 1000L);
        playerCooldowns.put(cooldownKey, cooldownEnd);
        startCooldownCountdown(player, tier.label, cooldownEnd);
    }

    private void startCooldownCountdown(Player player, String label, long readyAtMillis) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                long remainingMs = readyAtMillis - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    player.sendActionBar(Component.text(label + " ready!", NamedTextColor.GREEN));
                    cancel();
                    return;
                }
                player.sendActionBar(Component.text(String.format(label + ": %.1fs", remainingMs / 1000.0), NamedTextColor.AQUA));
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // ---------------------------------------------------------------------
    // Ability effects
    // ---------------------------------------------------------------------

    private void castAbility(Player player, Element element, Tier tier) {
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();

        switch (element) {
            case FIRE -> castFire(player, origin, direction, tier);
            case WATER -> castWater(player, origin, direction, tier);
            case AIR -> castAir(player, origin, direction, tier);
            case EARTH -> castEarth(player, origin, direction, tier);
            case LIGHTNING -> castLightning(player, origin, direction, tier);
            case VOID -> castVoid(player, origin, direction, tier);
        }
    }

    /** Draws a rotating double-helix particle stream between two points. */
    private void helix(Location start, Vector direction, double length, Particle particle) {
        helix(start, direction, length, particle, null);
    }

    private void helix(Location start, Vector direction, double length, Particle particle, Object data) {
        Vector normal = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        if (normal.lengthSquared() == 0) {
            normal = new Vector(1, 0, 0);
        }
        Vector normal2 = direction.clone().crossProduct(normal).normalize();
        for (double d = 0; d < length; d += 0.3) {
            double angle = d * 4;
            Location point = start.clone().add(direction.clone().multiply(d));
            point.add(normal.clone().multiply(Math.cos(angle) * 0.6));
            point.add(normal2.clone().multiply(Math.sin(angle) * 0.6));
            if (data != null) {
                point.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
            } else {
                point.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
            }
        }
    }

    private void sphereBurst(Location center, Particle particle, double radius) {
        sphereBurst(center, particle, radius, null);
    }

    private void sphereBurst(Location center, Particle particle, double radius, Object data) {
        for (double phi = 0; phi < Math.PI; phi += Math.PI / 12) {
            for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 12) {
                double x = radius * Math.sin(phi) * Math.cos(theta);
                double y = radius * Math.cos(phi);
                double z = radius * Math.sin(phi) * Math.sin(theta);
                Location point = center.clone().add(x, y, z);
                if (data != null) {
                    point.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
                } else {
                    point.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private List<LivingEntity> nearbyTargets(Player player, double radius) {
        return player.getLocation().getNearbyLivingEntities(radius, e -> !e.equals(player)).stream().toList();
    }

    private void awardXpForCast(Player player, Tier tier) {
        double amount = switch (tier) {
            case BASIC -> 2.0;
            case MOBILITY -> 3.0;
            case HEAVY -> 5.0;
            case ULTIMATE -> 8.0;
        };
        plugin.getMasteryManager().addXP(player, amount);
    }

    private void castFire(Player player, Location origin, Vector direction, Tier tier) {
        switch (tier) {
            case BASIC -> {
                // Combustion: ignite the nearest target in front of you.
                helix(origin, direction, 6, Particle.FLAME);
                player.playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 1.0F);
                LivingEntity target = firstInFront(player, 6);
                if (target != null) {
                    dealDamage(target, 5.0, player);
                    target.setFireTicks(100);
                }
            }
            case MOBILITY -> {
                // Blaze Dash: dash forward, leaving a brief fire trail.
                player.setVelocity(direction.clone().multiply(2.6).setY(0.25));
                player.playSound(origin, Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.2F);
                layFireTrail(player);
            }
            case HEAVY -> {
                // Fire Shield: reactive buff, checked by MeleePassiveListener on incoming melee hits.
                fireShieldExpiry.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
                player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0F, 0.8F);
                animateRing(player, Particle.FLAME, 3000L);
            }
            case ULTIMATE -> {
                // Inferno Blast: 1s charge, then a fireball explodes on whatever it hits first.
                player.sendActionBar(Component.text("Charging Inferno Blast...", NamedTextColor.GOLD));
                player.getWorld().spawnParticle(Particle.FLAME, origin, 40, 0.3, 0.3, 0.3, 0.05);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) {
                            return;
                        }
                        Location eye = player.getEyeLocation();
                        Vector dir = eye.getDirection();
                        Location impact = raytraceImpactPoint(player, eye, dir, 15);
                        sphereBurst(impact, Particle.FLAME, 2.0);
                        player.getWorld().spawnParticle(Particle.LAVA, impact, 20, 1, 1, 1, 0);
                        player.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.8F);
                        for (LivingEntity target : impact.getNearbyLivingEntities(3.0)) {
                            dealDamage(target, 18.0, player);
                            target.setFireTicks(160);
                        }
                        breakWeakBlocksNear(impact, 2);
                    }
                }.runTaskLater(plugin, 20L);
            }
        }
        awardXpForCast(player, tier);
    }

    /** Places short-lived fire blocks behind a dashing player, then cleans them up automatically. */
    private void layFireTrail(Player player) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= 12) {
                    cancel();
                    return;
                }
                Location feet = player.getLocation();
                var block = feet.getBlock();
                if (block.getType() == Material.AIR && block.getRelative(0, -1, 0).getType().isSolid()) {
                    block.setType(Material.FIRE);
                    Location firePos = block.getLocation();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (firePos.getBlock().getType() == Material.FIRE) {
                            firePos.getBlock().setType(Material.AIR);
                        }
                    }, 60L);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Shows a repeating ring of particles around a player for the given duration. */
    private void animateRing(Player player, Particle particle, long durationMs) {
        new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= durationMs) {
                    cancel();
                    return;
                }
                sphereBurst(player.getLocation().add(0, 1, 0), particle, 1.0);
                elapsed += 150;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /** Finds the closest living entity roughly in front of the player within range. */
    private LivingEntity firstInFront(Player player, double range) {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : nearbyTargets(player, range)) {
            if (!isInFront(player, candidate)) {
                continue;
            }
            double dist = candidate.getLocation().distanceSquared(player.getLocation());
            if (dist < closestDist) {
                closest = candidate;
                closestDist = dist;
            }
        }
        return closest;
    }

    /** Ray-traces blocks (and stops early on solid terrain) to find where an ability effect should land. */
    private Location raytraceImpactPoint(Player player, Location from, Vector direction, double maxDistance) {
        var result = player.getWorld().rayTraceBlocks(from, direction, maxDistance);
        if (result != null && result.getHitPosition() != null) {
            return result.getHitPosition().toLocation(player.getWorld());
        }
        return from.clone().add(direction.clone().multiply(maxDistance));
    }

    private static final java.util.Set<Material> WEAK_BLOCKS = java.util.Set.of(
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.GLASS, Material.GLASS_PANE,
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.DEAD_BUSH, Material.POPPY, Material.DANDELION
    );

    private void breakWeakBlocksNear(Location center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    var block = center.clone().add(x, y, z).getBlock();
                    if (WEAK_BLOCKS.contains(block.getType())) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    private void castWater(Player player, Location origin, Vector direction, Tier tier) {
        switch (tier) {
            case BASIC -> {
                // Water Whip: hitscan up to 10 blocks, pulls the target closer.
                helix(origin, direction, 10, Particle.SPLASH);
                player.playSound(origin, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0F, 1.0F);
                LivingEntity target = firstInFront(player, 10);
                if (target != null) {
                    dealDamage(target, 4.0, player);
                    Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.3);
                    target.setVelocity(pull.setY(0.15));
                }
            }
            case MOBILITY -> {
                boolean inWater = player.getLocation().getBlock().getType() == Material.WATER
                        || player.getEyeLocation().getBlock().getType() == Material.WATER;
                double multiplier = inWater ? 2.0 : 1.0;
                player.setVelocity(direction.clone().multiply(1.6 * multiplier).setY(0.35));
                sphereBurst(origin, Particle.BUBBLE_POP, 1.2);
                player.playSound(origin, Sound.ENTITY_DOLPHIN_JUMP, 1.0F, 1.0F);
            }
            case HEAVY -> {
                // Healing Surge: 2s channel, interrupted by any damage taken (see onDamageInterruptsChannel).
                UUID uuid = player.getUniqueId();
                long finishAt = System.currentTimeMillis() + 2000L;
                healChannelUntil.put(uuid, finishAt);
                player.sendMessage(Component.text("Channeling Healing Surge...", NamedTextColor.AQUA));
                player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 1.0F, 1.2F);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Long stillActive = healChannelUntil.get(uuid);
                        if (stillActive == null || !stillActive.equals(finishAt) || !player.isOnline()) {
                            return; // interrupted or already handled
                        }
                        healChannelUntil.remove(uuid);
                        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                        player.setHealth(Math.min(maxHealth, player.getHealth() + 6.0));
                        player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0);
                        player.sendMessage(Component.text("Healing Surge complete!", NamedTextColor.GREEN));
                    }
                }.runTaskLater(plugin, 40L);
            }
            case ULTIMATE -> {
                // Frost Freeze: a cone in front locks enemies in place for 1.5s.
                sphereBurst(origin.clone().add(direction.clone().multiply(2)), Particle.SNOWFLAKE, 1.5);
                player.playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0F, 1.2F);
                for (LivingEntity target : nearbyTargets(player, 6)) {
                    if (!isInFront(player, target)) {
                        continue;
                    }
                    dealDamage(target, 6.0, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 200, true, true));
                    target.setVelocity(new Vector(0, 0, 0));
                }
            }
        }
        awardXpForCast(player, tier);
    }

    private void castAir(Player player, Location origin, Vector direction, Tier tier) {
        switch (tier) {
            case BASIC -> {
                // Vacuum Pull: opens a vortex up to 15 blocks away that drags nearby players in after a delay.
                Location impact = raytraceImpactPoint(player, origin, direction, 15);
                player.getWorld().spawnParticle(Particle.CLOUD, impact, 20, 0.3, 0.3, 0.3, 0.02);
                player.playSound(origin, Sound.ENTITY_PHANTOM_FLAP, 1.0F, 0.7F);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        sphereBurst(impact, Particle.CLOUD, 1.5);
                        for (LivingEntity target : impact.getNearbyLivingEntities(4.0)) {
                            if (target.equals(player)) {
                                continue;
                            }
                            Vector pull = impact.toVector().subtract(target.getLocation().toVector()).normalize().multiply(1.1);
                            target.setVelocity(pull);
                        }
                    }
                }.runTaskLater(plugin, 10L);
            }
            case MOBILITY -> {
                // Gale Force: horizontal launch, or a double jump if already airborne.
                if (player.isOnGround()) {
                    player.setVelocity(direction.clone().multiply(2.2).setY(0.3));
                } else {
                    player.setVelocity(direction.clone().multiply(1.4).setY(0.8));
                }
                sphereBurst(origin, Particle.CLOUD, 1.2);
                player.playSound(origin, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0F, 1.5F);
            }
            case HEAVY -> {
                // Sonic Boom: fast hitscan shot, heavy knockback, bonus damage if the target slams a wall.
                LivingEntity target = firstInFront(player, 20);
                player.playSound(origin, Sound.ENTITY_BREEZE_WIND_BURST, 1.0F, 1.4F);
                helix(origin, direction, 8, Particle.CLOUD);
                if (target != null) {
                    dealDamage(target, 4.0, player);
                    Vector knockback = direction.clone().multiply(2.5).setY(0.5);
                    target.setVelocity(knockback);
                    double launchSpeed = knockback.length();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (target.isValid() && target.getVelocity().length() < launchSpeed * 0.3) {
                                dealDamage(target, 6.0, player);
                                target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 15, 0.3, 0.3, 0.3, 0.05);
                            }
                        }
                    }.runTaskLater(plugin, 4L);
                }
            }
            case ULTIMATE -> {
                // Suffocate: traps the nearest target's head in a vortex, dealing DoT and Blindness.
                LivingEntity target = firstInFront(player, 8);
                if (target != null) {
                    dealDamage(target, 3.0, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, true, true));
                    sphereBurst(target.getEyeLocation(), Particle.CLOUD, 0.8);
                    player.playSound(target.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1.0F, 0.6F);
                    for (int i = 1; i <= 3; i++) {
                        int tickDamageDelay = i * 20;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (target.isValid()) {
                                    dealDamage(target, 1.5, player);
                                    sphereBurst(target.getEyeLocation(), Particle.CLOUD, 0.5);
                                }
                            }
                        }.runTaskLater(plugin, tickDamageDelay);
                    }
                }
            }
        }
        awardXpForCast(player, tier);
    }

    private void castEarth(Player player, Location origin, Vector direction, Tier tier) {
        Object stoneData = Material.STONE.createBlockData();
        switch (tier) {
            case BASIC -> {
                // Fissure: a line of raised stone toward your target, popping enemies it hits.
                helix(origin, direction, 6, Particle.BLOCK_CRUMBLE, stoneData);
                player.playSound(origin, Sound.BLOCK_STONE_BREAK, 1.0F, 0.8F);
                raiseFissureLine(player, direction, 6);
                LivingEntity target = firstInFront(player, 6);
                if (target != null) {
                    dealDamage(target, 5.0, player);
                    target.setVelocity(new Vector(0, 0.6, 0));
                }
            }
            case MOBILITY -> {
                // Seismic Leap: launch forward, then shockwave on landing.
                player.setVelocity(direction.clone().multiply(1.4).setY(0.6));
                player.playSound(origin, Sound.BLOCK_STONE_STEP, 1.0F, 0.6F);
                watchForLanding(player);
            }
            case HEAVY -> {
                // Rock Wall: a temporary 3x2 cobblestone wall in front of you.
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0F, 0.8F);
                raiseTemporaryWall(player, direction, 80L);
            }
            case ULTIMATE -> {
                // Sand Tomb: turns the ground under the nearest target to soul sand and pins their feet.
                LivingEntity target = firstInFront(player, 12);
                if (target != null) {
                    dealDamage(target, 6.0, player);
                    trapInQuicksand(target, 50L);
                    sphereBurst(target.getLocation(), Particle.BLOCK_CRUMBLE, 1.2, stoneData);
                    player.playSound(target.getLocation(), Sound.BLOCK_SAND_BREAK, 1.0F, 0.6F);
                }
            }
        }
        awardXpForCast(player, tier);
    }

    /** Temporarily raises a short line of stone blocks toward the target direction (visual + minor terrain punch). */
    private void raiseFissureLine(Player player, Vector direction, int length) {
        Location base = player.getLocation();
        for (int i = 1; i <= length; i++) {
            Location spot = base.clone().add(direction.clone().multiply(i));
            var ground = spot.clone().subtract(0, 1, 0).getBlock();
            if (ground.getType().isSolid() && spot.getBlock().getType() == Material.AIR) {
                ground.getRelative(0, 1, 0).setType(Material.STONE);
                Location revertPos = ground.getRelative(0, 1, 0).getLocation();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (revertPos.getBlock().getType() == Material.STONE) {
                        revertPos.getBlock().setType(Material.AIR);
                    }
                }, 30L);
            }
        }
    }

    /** Polls until a leaping player lands, then triggers a knockback/slow shockwave. */
    private void watchForLanding(Player player) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 2;
                if (!player.isOnline() || ticks > 60) {
                    cancel();
                    return;
                }
                if (player.isOnGround() && ticks > 4) {
                    sphereBurst(player.getLocation(), Particle.BLOCK_CRUMBLE, 1.5, Material.DIRT.createBlockData());
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_BIG_FALL, 1.0F, 0.8F);
                    for (LivingEntity target : player.getLocation().getNearbyLivingEntities(3.0)) {
                        if (target.equals(player)) {
                            continue;
                        }
                        dealDamage(target, 3.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, true));
                        Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().setY(0.2);
                        target.setVelocity(push);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** Places a temporary 3-wide, 2-tall wall directly in front of the player, reverting after durationTicks. */
    private void raiseTemporaryWall(Player player, Vector direction, long durationTicks) {
        Vector flatDirection = direction.clone().setY(0).normalize();
        Vector side = new Vector(-flatDirection.getZ(), 0, flatDirection.getX());
        Location base = player.getLocation().add(flatDirection.clone().multiply(2));

        for (int across = -1; across <= 1; across++) {
            for (int up = 0; up <= 1; up++) {
                Location spot = base.clone().add(side.clone().multiply(across)).add(0, up, 0);
                var block = spot.getBlock();
                if (block.getType() == Material.AIR) {
                    block.setType(Material.COBBLESTONE);
                    Location revertPos = block.getLocation();
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (revertPos.getBlock().getType() == Material.COBBLESTONE) {
                            revertPos.getBlock().setType(Material.AIR);
                        }
                    }, durationTicks);
                }
            }
        }
    }

    /** Turns the ground under a target to soul sand and actively cancels their jumps for durationTicks. */
    private void trapInQuicksand(LivingEntity target, long durationTicks) {
        var ground = target.getLocation().clone().subtract(0, 1, 0).getBlock();
        Material original = ground.getType();
        if (original.isSolid()) {
            ground.setType(Material.SOUL_SAND);
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) durationTicks, 3, true, true));

        new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                elapsed += 2;
                if (!target.isValid() || elapsed >= durationTicks) {
                    if (ground.getType() == Material.SOUL_SAND) {
                        ground.setType(original.isSolid() ? original : Material.DIRT);
                    }
                    cancel();
                    return;
                }
                // Cancel any upward velocity so the target genuinely can't jump out.
                Vector velocity = target.getVelocity();
                if (velocity.getY() > 0) {
                    target.setVelocity(new Vector(velocity.getX(), 0, velocity.getZ()));
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void castLightning(Player player, Location origin, Vector direction, Tier tier) {
        switch (tier) {
            case BASIC -> {
                helix(origin, direction, 6, Particle.ELECTRIC_SPARK);
                player.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6F, 1.4F);
                LivingEntity target = firstInFront(player, 6);
                if (target != null) {
                    dealDamage(target, 5.0, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, true, true));
                }
            }
            case MOBILITY -> {
                Location dest = origin.clone().add(direction.clone().multiply(6));
                player.teleport(dest);
                sphereBurst(dest, Particle.ELECTRIC_SPARK, 1.2);
                player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.5F);
            }
            case HEAVY -> {
                sphereBurst(player.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 2.5);
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0F, 1.0F);
                for (LivingEntity target : nearbyTargets(player, 5)) {
                    dealDamage(target, 12.0, player);
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 200, true, true));
                }
            }
            case ULTIMATE -> {
                for (LivingEntity target : nearbyTargets(player, 8)) {
                    dealDamage(target, 22.0, player);
                    target.getWorld().strikeLightningEffect(target.getLocation());
                }
                player.playSound(origin, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5F, 0.7F);
            }
        }
        awardXpForCast(player, tier);
    }

    private void castVoid(Player player, Location origin, Vector direction, Tier tier) {
        switch (tier) {
            case BASIC -> {
                helix(origin, direction, 6, Particle.PORTAL);
                player.playSound(origin, Sound.ENTITY_ENDERMAN_STARE, 0.6F, 1.0F);
                for (LivingEntity target : nearbyTargets(player, 4)) {
                    if (isInFront(player, target)) {
                        dealDamage(target, 5.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                    }
                }
            }
            case MOBILITY -> {
                Location dest = origin.clone().add(direction.clone().multiply(8));
                player.teleport(dest);
                sphereBurst(dest, Particle.PORTAL, 1.2);
                player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.8F);
            }
            case HEAVY -> {
                sphereBurst(player.getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 2.5);
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6F, 1.0F);
                for (LivingEntity target : nearbyTargets(player, 5)) {
                    dealDamage(target, 13.0, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.8);
                    target.setVelocity(pull.setY(0.1));
                }
            }
            case ULTIMATE -> {
                for (int i = 0; i < 3; i++) {
                    sphereBurst(origin.clone().add(direction.clone().multiply(i * 3)), Particle.PORTAL, 2.0);
                }
                player.playSound(origin, Sound.ENTITY_WARDEN_ROAR, 1.0F, 0.7F);
                for (LivingEntity target : nearbyTargets(player, 8)) {
                    dealDamage(target, 24.0, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                }
            }
        }
        awardXpForCast(player, tier);
    }

    private boolean isInFront(Player player, Entity target) {
        Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
        return player.getLocation().getDirection().dot(toTarget) > 0.3;
    }

    /**
     * Applies the elemental counter cycle on top of a base ability damage
     * amount, then deals it. Chamber mobs count as having their chamber's
     * element for this purpose; other mobs and elementless players are neutral.
     */
    private void dealDamage(LivingEntity target, double baseAmount, Player attacker) {
        Element attackerElement = plugin.getMasteryManager().getElement(attacker.getUniqueId());
        Element defenderElement = resolveElement(target);
        double multiplier = ElementalCounters.damageMultiplier(attackerElement, defenderElement);
        target.damage(baseAmount * multiplier, attacker);
    }

    private Element resolveElement(LivingEntity entity) {
        if (entity instanceof Player p) {
            return plugin.getMasteryManager().getElement(p.getUniqueId());
        }
        String tag = entity.getPersistentDataContainer().get(key(plugin, "chamber_element"), PersistentDataType.STRING);
        return tag == null ? null : Element.fromArgument(tag);
    }

    // ---------------------------------------------------------------------
    // Combat XP
    // ---------------------------------------------------------------------

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        MasteryManager manager = plugin.getMasteryManager();
        if (!manager.hasElement(killer.getUniqueId())) {
            return;
        }
        double baseXp = event.getEntity() instanceof Player ? 50.0 : 10.0;
        manager.addXP(killer, baseXp);
        manager.incrementKills(killer.getUniqueId());
    }
}
