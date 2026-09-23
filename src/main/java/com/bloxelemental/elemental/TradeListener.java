package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * /element trade - browse two sections of spawner types (ones that never
 * appear in an Elemental Chamber, and ones that do - derived live from
 * ChamberTheme so this can never drift out of sync with the actual mob
 * pools) and swap one owned chamber-spawner item for a different type.
 */
public class TradeListener implements Listener {

    public static class TradeGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final int INPUT_SLOT = 4;
    private static final int WILD_LABEL_SLOT = 9;
    private static final int WILD_START_SLOT = 10;
    private static final int WILD_MAX = 8; // slots 10-17
    private static final int CHAMBER_LABEL_SLOT = 18;
    private static final int CHAMBER_START_SLOT = 19;
    private static final int CHAMBER_MAX = 17; // slots 19-35

    private final ElementalSMP plugin;

    public TradeListener(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    /** Every mob type used anywhere across the six chamber themes, deduplicated. */
    private Set<EntityType> chamberTypes() {
        Set<EntityType> types = new LinkedHashSet<>();
        for (Element element : Element.values()) {
            for (EntityType type : ChamberTheme.forElement(element).mobTypes()) {
                types.add(type);
            }
        }
        return types;
    }

    /** Admin-configurable list of "wild" spawner types that never show up in a chamber. */
    private List<EntityType> wildTypes() {
        List<String> configured = plugin.getConfig().getStringList("trade.wild-spawners");
        if (configured.isEmpty()) {
            configured = List.of("SPIDER", "COD", "COW", "SHEEP", "PIG", "CHICKEN", "RABBIT", "CAVE_SPIDER");
        }
        Set<EntityType> chamberTypes = chamberTypes();
        List<EntityType> result = new ArrayList<>();
        for (String name : configured) {
            try {
                EntityType type = EntityType.valueOf(name.toUpperCase());
                if (!chamberTypes.contains(type)) { // never let a configured "wild" type overlap a real chamber mob
                    result.add(type);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown entity type in trade.wild-spawners: " + name);
            }
        }
        return result;
    }

    public void openTradeGUI(Player player) {
        Inventory inventory = Bukkit.createInventory(new TradeGuiHolder(), 54,
                Component.text("Element Trading", NamedTextColor.GOLD, TextDecoration.BOLD));

        ItemStack filler = namedItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        ItemMeta infoMeta = new ItemStack(Material.PAPER).getItemMeta();
        infoMeta.displayName(Component.text("How Trading Works", NamedTextColor.YELLOW, TextDecoration.BOLD));
        infoMeta.lore(List.of(
                Component.text("Place a chamber spawner you own in the", NamedTextColor.GRAY),
                Component.text("empty slot below (the one gap in this row),", NamedTextColor.GRAY),
                Component.text("then click any spawner type to trade for it.", NamedTextColor.GRAY)
        ));
        ItemStack info = new ItemStack(Material.PAPER);
        info.setItemMeta(infoMeta);
        inventory.setItem(0, info);

        inventory.setItem(INPUT_SLOT, null); // deliberately left empty - it's the one open gap among the black filler,
        // which is its own visual cue. A placeholder item here would just be something the player
        // has to fight to remove before they can put their own spawner in.

        inventory.setItem(WILD_LABEL_SLOT, namedItem(Material.GRASS_BLOCK,
                Component.text("Wild Spawners", NamedTextColor.GREEN, TextDecoration.BOLD)));
        List<EntityType> wild = wildTypes();
        for (int i = 0; i < wild.size() && i < WILD_MAX; i++) {
            inventory.setItem(WILD_START_SLOT + i, targetIcon(wild.get(i), false));
        }

        inventory.setItem(CHAMBER_LABEL_SLOT, namedItem(Material.NETHER_STAR,
                Component.text("Chamber Spawners", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)));
        List<EntityType> chamber = new ArrayList<>(chamberTypes());
        for (int i = 0; i < chamber.size() && i < CHAMBER_MAX; i++) {
            inventory.setItem(CHAMBER_START_SLOT + i, targetIcon(chamber.get(i), true));
        }

        player.openInventory(inventory);
    }

    private ItemStack targetIcon(EntityType type, boolean isChamberType) {
        Material eggMaterial = spawnEggFor(type);
        ItemStack item = new ItemStack(eggMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(ChamberManager.prettyMobName(type) + " Spawner",
                isChamberType ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text(isChamberType ? "Found in an Elemental Chamber." : "Never appears in a chamber.", NamedTextColor.GRAY),
                Component.text("Click to trade for this spawner.", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private Material spawnEggFor(EntityType type) {
        Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return egg != null ? egg : Material.SPAWNER;
    }

    private ItemStack namedItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeGuiHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        if (!clickedTop) {
            // Click was in the player's OWN inventory - always allow normal management there.
            // The one exception: don't let shift-click dump some unrelated item into the trade
            // slot just because it happens to be the only empty one; a valid chamber spawner is
            // fine and vanilla will naturally route it into that one open gap on its own.
            if (event.isShiftClick() && !ChamberManager.isChamberSpawnerItem(plugin, event.getCurrentItem())) {
                event.setCancelled(true);
            }
            return;
        }

        int slot = event.getRawSlot();
        if (slot == INPUT_SLOT) {
            return; // let the player freely place/remove their own item here
        }
        event.setCancelled(true);

        EntityType target = resolveTarget(slot);
        if (target == null) {
            return;
        }

        ItemStack input = event.getInventory().getItem(INPUT_SLOT);
        if (!ChamberManager.isChamberSpawnerItem(plugin, input)) {
            player.sendMessage(Component.text("Place one of your chamber spawners in the input slot first!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }

        int remaining = input.getAmount() - 1;
        ItemStack output = ChamberManager.createSpawnerItem(plugin, target);
        if (remaining <= 0) {
            event.getInventory().setItem(INPUT_SLOT, output);
        } else {
            input.setAmount(remaining);
            event.getInventory().setItem(INPUT_SLOT, input); // keep any leftover stacked input visible
            var overflow = player.getInventory().addItem(output);
            for (ItemStack extra : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0F, 1.2F);
        player.sendMessage(Component.text("Traded for a ", NamedTextColor.GREEN)
                .append(Component.text(ChamberManager.prettyMobName(target) + " Spawner", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text("! Take it from the input slot.", NamedTextColor.GREEN)));
    }

    private EntityType resolveTarget(int slot) {
        List<EntityType> wild = wildTypes();
        if (slot >= WILD_START_SLOT && slot < WILD_START_SLOT + Math.min(wild.size(), WILD_MAX)) {
            return wild.get(slot - WILD_START_SLOT);
        }
        List<EntityType> chamber = new ArrayList<>(chamberTypes());
        if (slot >= CHAMBER_START_SLOT && slot < CHAMBER_START_SLOT + Math.min(chamber.size(), CHAMBER_MAX)) {
            return chamber.get(slot - CHAMBER_START_SLOT);
        }
        return null;
    }

    /** Returns whatever's left in the input slot when the GUI closes, so nothing gets eaten. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeGuiHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack leftover = event.getInventory().getItem(INPUT_SLOT);
        if (leftover == null || leftover.getType().isAir()) {
            return;
        }
        var overflow = player.getInventory().addItem(leftover);
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }
}
