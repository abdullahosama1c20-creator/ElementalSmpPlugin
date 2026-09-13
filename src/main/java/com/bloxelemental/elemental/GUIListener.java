package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The unified /element gui: pick your first starter element, switch between
 * any you already own, unlock a new one once you've maxed an existing one,
 * and right-click to toggle buff-style passives on/off (yours, or your
 * fusion's). Lightning/Void can only ever be acquired by fusing them into
 * your active element via a Storm Core/Void Tear (see
 * AbilityListener.handleAwakening) - their slots here are informational,
 * except when your active element is fused with one, where right-clicking
 * toggles that fusion's passive.
 */
public class GUIListener implements Listener {

    /** Marks inventories opened by this plugin so we never mistake a player's own chest GUI for ours. */
    public static class ElementGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /** Marks the read-only /element abilities GUI so clicks can be safely cancelled. */
    public static class AbilitiesGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final Map<Integer, Element> STARTER_SLOTS = new HashMap<>();
    private static final Map<Integer, Element> ADVANCED_SLOTS = new HashMap<>();
    static {
        STARTER_SLOTS.put(10, Element.FIRE);
        STARTER_SLOTS.put(12, Element.WATER);
        STARTER_SLOTS.put(14, Element.AIR);
        STARTER_SLOTS.put(16, Element.EARTH);
        ADVANCED_SLOTS.put(20, Element.LIGHTNING);
        ADVANCED_SLOTS.put(24, Element.VOID);
    }

    private final ElementalSMP plugin;

    public GUIListener(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    public static void openElementGUI(ElementalSMP plugin, Player player) {
        MasteryManager manager = plugin.getMasteryManager();
        UUID uuid = player.getUniqueId();
        Element active = manager.getElement(uuid);
        Element activeFusion = active == null ? null : manager.getFusion(uuid, active);

        Inventory inventory = Bukkit.createInventory(new ElementGuiHolder(), 27,
                Component.text("Choose Your Element", NamedTextColor.GOLD, TextDecoration.BOLD));

        ItemStack filler = namedItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        Component header;
        if (active == null) {
            header = Component.text("Pick your starter element below!", NamedTextColor.YELLOW);
        } else {
            header = Component.text("Active: ", NamedTextColor.GRAY)
                    .append(Component.text(active.displayName(), active.color(), TextDecoration.BOLD));
            if (activeFusion != null) {
                header = header.append(Component.text(" (fused with " + activeFusion.displayName() + ")", activeFusion.color()));
            }
        }
        inventory.setItem(4, namedItem(Material.NETHER_STAR, header));

        for (Map.Entry<Integer, Element> entry : STARTER_SLOTS.entrySet()) {
            inventory.setItem(entry.getKey(), buildStarterIcon(plugin, manager, uuid, entry.getValue(), active));
        }
        for (Map.Entry<Integer, Element> entry : ADVANCED_SLOTS.entrySet()) {
            inventory.setItem(entry.getKey(), buildAdvancedIcon(plugin, manager, uuid, entry.getValue(), active, activeFusion));
        }

        player.openInventory(inventory);
    }

    private static ItemStack buildStarterIcon(ElementalSMP plugin, MasteryManager manager, UUID uuid, Element element, Element active) {
        boolean owned = manager.ownsElement(uuid, element);
        boolean isActive = element == active;
        List<Component> lore = new ArrayList<>();

        Component title;
        if (isActive) {
            title = Component.text(element.displayName() + " (ACTIVE)", element.color(), TextDecoration.BOLD);
            lore.add(Component.text("This is your active element.", NamedTextColor.GREEN));
            appendToggleLore(plugin, uuid, element, lore);
        } else if (owned) {
            title = Component.text(element.displayName(), element.color(), TextDecoration.BOLD);
            lore.add(Component.text("Level " + manager.getLevel(uuid, element), NamedTextColor.GRAY));
            lore.add(Component.text("Click to switch to this element.", NamedTextColor.GREEN));
        } else {
            boolean unlockable = !manager.hasElement(uuid) || manager.canUnlockAnotherElement(uuid);
            title = Component.text(element.displayName(), unlockable ? element.color() : NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
            if (unlockable) {
                lore.add(Component.text("Click to unlock this element!", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("Locked - reach level 100 on an", NamedTextColor.RED));
                lore.add(Component.text("element you own to unlock another.", NamedTextColor.RED));
            }
        }

        ItemStack item = new ItemStack(element.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildAdvancedIcon(ElementalSMP plugin, MasteryManager manager, UUID uuid, Element element, Element active, Element activeFusion) {
        List<Component> lore = new ArrayList<>();
        Component title;

        if (element == activeFusion) {
            title = Component.text(element.displayName() + " (FUSED)", element.color(), TextDecoration.BOLD);
            lore.add(Component.text("Fused with your active " + active.displayName() + ".", NamedTextColor.GREEN));
            appendToggleLore(plugin, uuid, element, lore);
        } else {
            boolean eligible = manager.canFuseActiveElement(uuid);
            title = Component.text(element.displayName(), eligible ? element.color() : NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
            if (eligible) {
                lore.add(Component.text("Right-click a Storm Core or Void Tear", NamedTextColor.YELLOW));
                lore.add(Component.text("to fuse it into your active element.", NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text("Locked - your active element needs to be", NamedTextColor.RED));
                lore.add(Component.text("Level 100 and unfused, then use a Storm Core/Void Tear.", NamedTextColor.RED));
            }
        }

        ItemStack item = new ItemStack(element.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Appends toggle status/instructions to an icon's lore if this element identity has a toggleable buff. */
    private static void appendToggleLore(ElementalSMP plugin, UUID uuid, Element element, List<Component> lore) {
        if (!PassiveInfo.hasToggleableBuff(element)) {
            return;
        }
        boolean enabled = plugin.getMasteryManager().isPassiveEnabled(uuid, element);
        lore.add(Component.text(""));
        lore.add(Component.text("Buff passive: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ENABLED" : "DISABLED", enabled ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)));
        lore.add(Component.text("Right-click to toggle.", NamedTextColor.DARK_GRAY));
    }

    private static ItemStack namedItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Shows a compact 9-slot readout of a player's ACTIVE element: the four
     * ability tiers plus their passive, each colored by whether it's unlocked.
     */
    public static void openAbilitiesGUI(ElementalSMP plugin, Player player) {
        MasteryManager manager = plugin.getMasteryManager();
        Element element = manager.getElement(player.getUniqueId());
        if (element == null) {
            player.sendMessage(Component.text("Choose an element with /element gui first.", NamedTextColor.RED));
            return;
        }
        int level = manager.getLevel(player.getUniqueId());

        Inventory inventory = Bukkit.createInventory(new AbilitiesGuiHolder(), 9,
                Component.text(element.displayName() + " Abilities - Lv." + level, element.color(), TextDecoration.BOLD));

        ItemStack filler = namedItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(1, abilityIcon(element, level, Material.IRON_SWORD, Tier.BASIC));
        inventory.setItem(3, abilityIcon(element, level, Material.FEATHER, Tier.MOBILITY));
        inventory.setItem(4, passiveIcon(plugin, player.getUniqueId(), element));
        inventory.setItem(5, abilityIcon(element, level, Material.BLAZE_POWDER, Tier.HEAVY));
        inventory.setItem(7, abilityIcon(element, level, Material.NETHER_STAR, Tier.ULTIMATE));

        player.openInventory(inventory);
    }

    private static ItemStack abilityIcon(Element element, int level, Material material, Tier tier) {
        boolean unlocked = level >= tier.requiredLevel;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text((unlocked ? "" : "[LOCKED] ") + tier.label, unlocked ? element.color() : NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        String cooldownLine = unlocked
                ? String.format("Requires Lv.%d | %.1fs cooldown at your level", tier.requiredLevel, tier.cooldownSecondsForLevel(level))
                : String.format("Requires Lv.%d | %.1fs to %.1fs cooldown", tier.requiredLevel, (double) tier.cooldownSeconds, tier.cooldownSeconds * Tier.startingMultiplier);
        meta.lore(List.of(
                Component.text(AbilityInfo.describe(element, tier), unlocked ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY),
                Component.text(""),
                Component.text(cooldownLine, unlocked ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text("Cooldown shortens as you level up.", NamedTextColor.DARK_GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack passiveIcon(ElementalSMP plugin, UUID uuid, Element element) {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Passive", element.color(), TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(PassiveInfo.describeWithFusion(plugin, uuid, element), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("Always active", NamedTextColor.GREEN));
        lore.add(Component.text("Toggle buff passives in /element gui.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof AbilitiesGuiHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ElementGuiHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        MasteryManager manager = plugin.getMasteryManager();
        UUID uuid = player.getUniqueId();
        int slot = event.getRawSlot();

        Element starterClicked = STARTER_SLOTS.get(slot);
        Element advancedClicked = ADVANCED_SLOTS.get(slot);

        if (event.isRightClick()) {
            handleToggleClick(player, manager, uuid, starterClicked, advancedClicked);
            return;
        }

        if (starterClicked != null) {
            handleStarterClick(player, manager, uuid, starterClicked);
        } else if (advancedClicked != null) {
            player.sendMessage(Component.text("Lightning and Void can only be fused into your active element with a Storm Core or Void Tear.", NamedTextColor.RED));
        }
    }

    private void handleToggleClick(Player player, MasteryManager manager, UUID uuid, Element starterClicked, Element advancedClicked) {
        Element active = manager.getElement(uuid);

        if (starterClicked != null && starterClicked == active && PassiveInfo.hasToggleableBuff(starterClicked)) {
            boolean nowEnabled = manager.togglePassive(uuid, starterClicked);
            applyToggleFeedback(player, starterClicked, nowEnabled);
            openElementGUI(plugin, player);
            return;
        }

        if (advancedClicked != null && active != null && advancedClicked == manager.getFusion(uuid, active)) {
            boolean nowEnabled = manager.togglePassive(uuid, advancedClicked);
            applyToggleFeedback(player, advancedClicked, nowEnabled);
            openElementGUI(plugin, player);
        }
    }

    private void applyToggleFeedback(Player player, Element element, boolean nowEnabled) {
        PassiveInfo.applyBuffs(plugin, player, plugin.getMasteryManager().getElement(player.getUniqueId()));
        if (!nowEnabled) {
            // Actively strip the potion effects this bundle grants so turning it off feels instant,
            // not just "stops refreshing" over the next 40 seconds.
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
            if (element == Element.WATER) {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.HASTE);
            } else if (element == Element.VOID) {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
            }
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, nowEnabled ? 1.2F : 0.8F);
        player.sendMessage(Component.text(element.displayName() + "'s buff passive is now ", NamedTextColor.YELLOW)
                .append(Component.text(nowEnabled ? "ENABLED" : "DISABLED", nowEnabled ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(".", NamedTextColor.YELLOW)));
    }

    private void handleStarterClick(Player player, MasteryManager manager, UUID uuid, Element clicked) {
        if (manager.ownsElement(uuid, clicked)) {
            if (manager.getElement(uuid) == clicked) {
                return; // already active, nothing to do
            }
            manager.switchActiveElement(uuid, clicked);
            PassiveInfo.applyBuffs(plugin, player, clicked);
            LevelStats.apply(plugin, player);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0F, 1.2F);
            player.sendMessage(Component.text("Switched your active element to ", NamedTextColor.GREEN)
                    .append(Component.text(clicked.displayName(), clicked.color(), TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GREEN)));
            return;
        }

        boolean firstPick = !manager.hasElement(uuid);
        if (!firstPick && !manager.canUnlockAnotherElement(uuid)) {
            player.sendMessage(Component.text("You need to reach level 100 on an element you own before unlocking another.", NamedTextColor.RED));
            return;
        }

        if (firstPick) {
            manager.chooseFirstElement(uuid, clicked);
        } else {
            manager.unlockAdditionalElement(uuid, clicked);
        }
        player.getInventory().addItem(AbilityListener.catalystItem(plugin, clicked));
        PassiveInfo.applyBuffs(plugin, player, clicked);
        LevelStats.apply(plugin, player);
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
        player.sendMessage(Component.text("You have bound yourself to the element of ", NamedTextColor.GREEN)
                .append(Component.text(clicked.displayName(), clicked.color(), TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Your Elemental Catalyst has been added to your inventory.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getChamberManager() != null) {
            plugin.getChamberManager().trackPlayer(event.getPlayer());
            plugin.getChamberManager().giveOrUpdateCompass(event.getPlayer());
        }
        if (!plugin.getMasteryManager().hasElement(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(Component.text("Welcome! Run ", NamedTextColor.YELLOW)
                    .append(Component.text("/element gui", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" to choose your starter element.", NamedTextColor.YELLOW)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getChamberManager() != null) {
            plugin.getChamberManager().untrackPlayer(event.getPlayer());
        }
    }
}
