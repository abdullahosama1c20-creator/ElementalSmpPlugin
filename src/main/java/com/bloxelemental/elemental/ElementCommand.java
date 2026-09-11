package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ElementCommand implements CommandExecutor, TabCompleter {

    private final ElementalSMP plugin;

    public ElementCommand(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /element gui | /element abilities | /element stats", NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("abilities")) {
            GUIListener.openAbilitiesGUI(plugin, player);
            return true;
        }

        if (args[0].equalsIgnoreCase("stats")) {
            handleStats(player);
            return true;
        }

        if (!args[0].equalsIgnoreCase("gui")) {
            player.sendMessage(Component.text("Usage: /element gui | /element abilities | /element stats", NamedTextColor.YELLOW));
            return true;
        }

        GUIListener.openElementGUI(plugin, player);
        return true;
    }

    private void handleStats(Player player) {
        MasteryManager manager = plugin.getMasteryManager();
        UUID uuid = player.getUniqueId();
        Element element = manager.getElement(uuid);
        if (element == null) {
            player.sendMessage(Component.text("Choose an element with /element gui first.", NamedTextColor.RED));
            return;
        }
        int level = manager.getLevel(uuid);
        double xp = manager.getXP(uuid);
        double xpNeeded = manager.xpForNextLevel(level);

        player.sendMessage(Component.text("--- Your Stats ---", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("Active Element: ", NamedTextColor.GRAY).append(Component.text(element.displayName(), element.color())));
        player.sendMessage(Component.text("Mastery Level: ", NamedTextColor.GRAY).append(Component.text(level + "/" + MasteryManager.MAX_LEVEL, NamedTextColor.WHITE)));
        if (level < MasteryManager.MAX_LEVEL) {
            player.sendMessage(Component.text("XP to next level: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.0f/%.0f", xp, xpNeeded), NamedTextColor.WHITE)));
        }
        player.sendMessage(Component.text("Kills: ", NamedTextColor.GRAY).append(Component.text(manager.getKills(uuid), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Chambers Cleared: ", NamedTextColor.GRAY).append(Component.text(manager.getChambersCleared(uuid), NamedTextColor.WHITE)));

        List<Element> owned = manager.getOwnedElements(uuid);
        if (owned.size() > 1) {
            player.sendMessage(Component.text("Other Elements Owned:", NamedTextColor.GRAY));
            for (Element other : owned) {
                if (other == element) {
                    continue;
                }
                player.sendMessage(Component.text("  " + other.displayName() + " ", other.color())
                        .append(Component.text("Lv." + manager.getLevel(uuid, other), NamedTextColor.WHITE)));
            }
        }
        if (manager.canUnlockAnotherElement(uuid)) {
            player.sendMessage(Component.text("You can unlock another starter element via /element gui!", NamedTextColor.LIGHT_PURPLE));
        }
        if (manager.isAwakeningEligible(uuid) && !manager.ownsElement(uuid, Element.LIGHTNING) && !manager.ownsElement(uuid, Element.VOID)) {
            player.sendMessage(Component.text("Awakening Eligible! ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("Find a Storm Core or Void Tear.", NamedTextColor.GRAY)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("gui", "abilities", "stats");
        }
        return List.of();
    }
}
