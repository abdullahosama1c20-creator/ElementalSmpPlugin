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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The unified /element gui: pick your first element, switch between any
 * elements you already own, or unlock a new one once you've maxed an
 * existing element. Lightning/Void can only ever be unlocked by consuming a
 * Storm Core/Void Tear (see AbilityListener.handleAwakening) - this GUI just
 * displays their status and tells you what to do.
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

    private static final Map<Integer, Element> SLOT_MAP = new HashMap<>();
    static {
        SLOT_MAP.put(10, Element.FIRE);
        SLOT_MAP.put(12, Element.WATER);
        SLOT_MAP.put(14, Element.AIR);
        SLOT_MAP.put(16, Element.EARTH);
        SLOT_MAP.put(20, Element.LIGHTNING);
        SLOT_MAP.put(24, Element.VOID);
    }

    private final ElementalSMP plugin;

    public GUIListener(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    public static void openElementGUI(ElementalSMP plugin, Player player) {
        MasteryManager manager = plugin.getMasteryManager();
        UUID uuid = player.getUniqueId();
        Element active = manager.getElement(uuid);

        Inventory inventory = Bukkit.createInventory(new ElementGuiHolder(), 27,
                Component.text("Choose Your Element", NamedTextColor.GOLD, TextDecoration.BOLD));

        ItemStack filler = namedItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        Component header = active == null
                ? Component.text("Pick your starter element below!", NamedTextColor.YELLOW)
                : Component.text("Active: ", NamedTextColor.GRAY).append(Component.text(active.displayName(), active.color(), TextDecoration.BOLD));
        inventory.setItem(4, namedItem(Material.NETHER_STAR, header));

        for (Map.Entry<Integer, Element> entry : SLOT_MAP.entrySet()) {
            inventory.setItem(entry.getKey(), buildElementIcon(manager, uuid, entry.getValue(), active));
        }

        player.openInventory(inventory);
    }

    private static ItemStack buildElementIcon(MasteryManager manager, UUID uuid, Element element, Element active) {
        boolean owned = manager.ownsElement(uuid, element);
        boolean isActive = element == active;
        List<Component> lore = new java.util.ArrayList<>();

        Component title;
        if (isActive) {
            title = Component.text(element.displayName() + " (ACTIVE)", element.color(), TextDecoration.BOLD);
            lore.add(Component.text("This is your active element.", NamedTextColor.GREEN));
        } else if (owned) {
            title = Component.text(element.displayName(), element.color(), TextDecoration.BOLD);
            lore.add(Component.text("Level " + manager.getLevel(uuid, element), NamedTextColor.GRAY));
            lore.add(Component.text("Click to switch to this element.", NamedTextColor.GREEN));
        } else if (element.isStarter()) {
            boolean unlockable = !manager.hasElement(uuid) || manager.canUnlockAnotherElement(uuid);
            title = Component.text(element.displayName(), unlockable ? element.color() : NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
            if (unlockable) {
                lore.add(Component.text("Click to unlock this element!", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("Locked - reach level 100 on an", NamedTextColor.RED));
                lore.add(Component.text("element you own to unlock another.", NamedTextColor.RED));
            }
        } else {
            // Advanced element (Lightning/Void), not owned.
            boolean eligible = manager.isAwakeningEligible(uuid);
            title = Component.text(element.displayName(), eligible ? element.color() : NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
            if (eligible) {
                lore.add(Component.text("Right-click a Storm Core or Void Tear", NamedTextColor.YELLOW));
                lore.add(Component.text("to awaken this element.", NamedTextColor.YELLOW));
            } else {
                lore.add(Component.text("Locked - reach level 100 on a starter", NamedTextColor.RED));
                lore.add(Component.text("element, then use a Storm Core/Void Tear.", NamedTextColor.RED));
            }
        }

        ItemStack item = new ItemStack(element.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(title);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
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
        inventory.setItem(4, passiveIcon(element));
        inventory.setItem(5, abilityIcon(element, level, Material.BLAZE_POWDER, Tier.HEAVY));
        inventory.setItem(7, abilityIcon(element, level, Material.NETHER_STAR, Tier.ULTIMATE));

        player.openInventory(inventory);
    }

    private static ItemStack abilityIcon(Element element, int level, Material material, Tier tier) {
        boolean unlocked = level >= tier.requiredLevel;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text((unlocked ? "" : "[LOCKED] ") + tier.label, unlocked ? element.color() : NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text(AbilityInfo.describe(element, tier), unlocked ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY),
                Component.text(""),
                Component.text("Requires Lv." + tier.requiredLevel + " | " + tier.cooldownSeconds + "s cooldown",
                        unlocked ? NamedTextColor.GREEN : NamedTextColor.RED)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack passiveIcon(Element element) {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Passive", element.color(), TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text(PassiveInfo.describe(element), NamedTextColor.GRAY),
                Component.text(""),
                Component.text("Always active", NamedTextColor.GREEN)
        ));
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
        Element clicked = SLOT_MAP.get(event.getRawSlot());
        if (clicked == null) {
            return;
        }

        MasteryManager manager = plugin.getMasteryManager();
        UUID uuid = player.getUniqueId();

        if (manager.ownsElement(uuid, clicked)) {
            if (manager.getElement(uuid) == clicked) {
                return; // already active, nothing to do
            }
            manager.switchActiveElement(uuid, clicked);
            PassiveInfo.applyBuffs(player, clicked);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0F, 1.2F);
            player.sendMessage(Component.text("Switched your active element to ", NamedTextColor.GREEN)
                    .append(Component.text(clicked.displayName(), clicked.color(), TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GREEN)));
            return;
        }

        if (!clicked.isStarter()) {
            player.sendMessage(Component.text("Lightning and Void can only be unlocked by using a Storm Core or Void Tear.", NamedTextColor.RED));
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
        player.getInventory().addItem(ArmorSets.armorPieces(plugin, clicked));
        PassiveInfo.applyBuffs(player, clicked);
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
        player.sendMessage(Component.text("You have bound yourself to the element of ", NamedTextColor.GREEN)
                .append(Component.text(clicked.displayName(), clicked.color(), TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Your Elemental Catalyst and a matching armor set were added to your inventory.", NamedTextColor.GRAY));
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
