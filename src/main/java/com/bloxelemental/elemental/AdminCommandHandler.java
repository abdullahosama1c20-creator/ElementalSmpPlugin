package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminCommandHandler implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("inspect", "setlevel", "setelement", "giveitem", "announce", "locate", "reroll", "top");

    private final ElementalSMP plugin;

    public AdminCommandHandler(ElementalSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /elemental top is public - everyone can check the leaderboard without elemental.admin.
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            handleTop(sender, args);
            return true;
        }

        if (!sender.hasPermission("elemental.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "inspect" -> handleInspect(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "setelement" -> handleSetElement(sender, args);
            case "giveitem" -> handleGiveItem(sender, args);
            case "announce" -> handleAnnounce(sender, args);
            case "locate" -> handleLocate(sender);
            case "reroll" -> handleReroll(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleTop(CommandSender sender, String[] args) {
        Element filter = args.length > 1 ? Element.fromArgument(args[1]) : null;
        if (args.length > 1 && filter == null) {
            sender.sendMessage(Component.text("Unknown element. Valid: Fire, Water, Air, Earth, Lightning, Void", NamedTextColor.RED));
            return;
        }
        List<MasteryManager.LeaderboardEntry> top = plugin.getMasteryManager().getTopPlayers(filter, 10);
        sender.sendMessage(Component.text("--- Mastery Leaderboard" + (filter != null ? " (" + filter.displayName() + ")" : "") + " ---",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        if (top.isEmpty()) {
            sender.sendMessage(Component.text("No one has chosen an element yet.", NamedTextColor.GRAY));
            return;
        }
        int rank = 1;
        for (MasteryManager.LeaderboardEntry entry : top) {
            sender.sendMessage(Component.text("#" + rank + " ", NamedTextColor.YELLOW)
                    .append(Component.text(entry.name() + " ", NamedTextColor.WHITE))
                    .append(Component.text("(" + entry.element().displayName() + ") ", entry.element().color()))
                    .append(Component.text("Lv." + entry.level(), NamedTextColor.GRAY)));
            rank++;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("--- /elemental commands ---", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("/elemental inspect <player>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/elemental setlevel <player> <level>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/elemental setelement <player> <element>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/elemental giveitem <player> <lightning_core|void_tear>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/elemental announce <message>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/elemental locate", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/elemental reroll", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/elemental top [element]", NamedTextColor.YELLOW));
    }

    private void handleLocate(CommandSender sender) {
        org.bukkit.Location center = plugin.getChamberManager().getChamberCenter();
        if (center == null) {
            sender.sendMessage(Component.text("No Elemental Chamber has spawned yet - it rolls within the first minute after the plugin starts.", NamedTextColor.RED));
            return;
        }
        Element active = plugin.getChamberManager().getActiveElement();
        String elitePrefix = plugin.getChamberManager().isElite() ? "ELITE " : "";
        sender.sendMessage(Component.text("Active Chamber: ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(elitePrefix + active.displayName() + " ", active.color()))
                .append(Component.text(center.getWorld().getName() + " (" + center.getBlockX() + ", "
                        + center.getBlockY() + ", " + center.getBlockZ() + ") radius " + plugin.getChamberManager().getRadius(),
                        NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Progress: " + plugin.getChamberManager().getKillsRegistered() + "/"
                + plugin.getChamberManager().getKillsRequired() + " kills"
                + (plugin.getChamberManager().isCleared() ? " (CLEARED)" : ""), NamedTextColor.GRAY));
        if (sender instanceof Player player && player.getWorld().equals(center.getWorld())) {
            double distance = player.getLocation().distance(center);
            sender.sendMessage(Component.text(String.format("Distance from you: %.0f blocks", distance), NamedTextColor.GRAY));
        }
    }

    private void handleReroll(CommandSender sender) {
        plugin.getChamberManager().forceReroll();
        sender.sendMessage(Component.text("Forced a new Elemental Chamber roll.", NamedTextColor.GREEN));
    }

    private void handleInspect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /elemental inspect <player>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
            return;
        }
        UUID uuid = target.getUniqueId();
        MasteryManager manager = plugin.getMasteryManager();
        Element element = manager.getElement(uuid);

        sender.sendMessage(Component.text("--- Elemental Profile: " + target.getName() + " ---", NamedTextColor.GOLD, TextDecoration.BOLD));
        if (element == null) {
            sender.sendMessage(Component.text("Element: None chosen yet", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Active Element: ", NamedTextColor.GRAY)
                .append(Component.text(element.displayName(), element.color())));
        sender.sendMessage(Component.text("Mastery Level: ", NamedTextColor.GRAY)
                .append(Component.text(manager.getLevel(uuid) + " / " + MasteryManager.MAX_LEVEL, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Total XP into next level: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", manager.getXP(uuid)), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Unlocked Abilities: ", NamedTextColor.GRAY)
                .append(Component.text(String.join(", ", manager.getUnlockedAbilities(uuid)), NamedTextColor.AQUA)));

        List<Element> owned = manager.getOwnedElements(uuid);
        if (owned.size() > 1) {
            StringBuilder others = new StringBuilder();
            for (Element other : owned) {
                if (other == element) {
                    continue;
                }
                if (!others.isEmpty()) {
                    others.append(", ");
                }
                others.append(other.displayName()).append(" (Lv.").append(manager.getLevel(uuid, other)).append(")");
            }
            sender.sendMessage(Component.text("Other Elements Owned: ", NamedTextColor.GRAY)
                    .append(Component.text(others.toString(), NamedTextColor.AQUA)));
        }

        boolean holdingStorm = target.getInventory().contains(AbilityListener.stormCoreItem().getType())
                && target.getInventory().containsAtLeast(AbilityListener.stormCoreItem(), 1);
        boolean holdingVoid = target.getInventory().containsAtLeast(AbilityListener.voidTearItem(), 1);
        sender.sendMessage(Component.text("Holding Storm Core: ", NamedTextColor.GRAY)
                .append(Component.text(holdingStorm ? "Yes" : "No", holdingStorm ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("Holding Void Tear: ", NamedTextColor.GRAY)
                .append(Component.text(holdingVoid ? "Yes" : "No", holdingVoid ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /elemental setlevel <player> <level>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
            return;
        }
        if (!plugin.getMasteryManager().hasElement(target.getUniqueId())) {
            sender.sendMessage(Component.text(target.getName() + " has not chosen an element yet.", NamedTextColor.RED));
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Level must be a number between 1 and " + MasteryManager.MAX_LEVEL, NamedTextColor.RED));
            return;
        }
        if (level < 1 || level > MasteryManager.MAX_LEVEL) {
            sender.sendMessage(Component.text("Level must be between 1 and " + MasteryManager.MAX_LEVEL, NamedTextColor.RED));
            return;
        }
        plugin.getMasteryManager().setLevel(target.getUniqueId(), level);
        sender.sendMessage(Component.text("Set " + target.getName() + "'s Mastery Level to " + level + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("An admin set your Mastery Level to " + level + ".", NamedTextColor.YELLOW));
    }

    private void handleSetElement(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /elemental setelement <player> <element>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
            return;
        }
        Element element = Element.fromArgument(args[2]);
        if (element == null) {
            sender.sendMessage(Component.text("Unknown element. Valid: Fire, Water, Air, Earth, Lightning, Void", NamedTextColor.RED));
            return;
        }

        MasteryManager manager = plugin.getMasteryManager();
        UUID uuid = target.getUniqueId();
        boolean alreadyOwned = manager.ownsElement(uuid, element);

        if (!manager.hasElement(uuid)) {
            manager.chooseFirstElement(uuid, element);
        } else if (alreadyOwned) {
            manager.switchActiveElement(uuid, element);
        } else {
            manager.unlockAdditionalElement(uuid, element);
        }

        // Only hand out a fresh catalyst/armor set if they didn't already own this element -
        // an admin switching someone TO an element they already have shouldn't duplicate gear.
        if (!alreadyOwned) {
            target.getInventory().addItem(AbilityListener.catalystItem(plugin, element));
            target.getInventory().addItem(ArmorSets.armorPieces(plugin, element));
        }
        PassiveInfo.applyBuffs(target, element);

        sender.sendMessage(Component.text("Set " + target.getName() + "'s active element to ", NamedTextColor.GREEN)
                .append(Component.text(element.displayName(), element.color())));
        target.sendMessage(Component.text("An admin set your active element to ", NamedTextColor.YELLOW)
                .append(Component.text(element.displayName(), element.color())));
    }

    private void handleGiveItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /elemental giveitem <player> <lightning_core|void_tear|catalyst_<element>>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
            return;
        }
        String itemArg = args[2].toLowerCase();
        switch (itemArg) {
            case "lightning_core" -> {
                target.getInventory().addItem(AbilityListener.stormCoreItem());
                sender.sendMessage(Component.text("Gave " + target.getName() + " a Storm Core.", NamedTextColor.GREEN));
                target.sendMessage(Component.text("You received a Storm Core!", NamedTextColor.YELLOW));
            }
            case "void_tear" -> {
                target.getInventory().addItem(AbilityListener.voidTearItem());
                sender.sendMessage(Component.text("Gave " + target.getName() + " a Void Tear.", NamedTextColor.GREEN));
                target.sendMessage(Component.text("You received a Void Tear!", NamedTextColor.YELLOW));
            }
            default -> {
                // catalyst_<element> lets an admin re-issue a lost/missing catalyst for any element,
                // e.g. "catalyst_air", "catalyst_fire", "catalyst_void".
                if (itemArg.startsWith("catalyst_")) {
                    Element element = Element.fromArgument(itemArg.substring("catalyst_".length()));
                    if (element == null) {
                        sender.sendMessage(Component.text("Unknown element for catalyst_<element>.", NamedTextColor.RED));
                        return;
                    }
                    target.getInventory().addItem(AbilityListener.catalystItem(plugin, element));
                    sender.sendMessage(Component.text("Gave " + target.getName() + " a " + element.displayName() + " Catalyst.", NamedTextColor.GREEN));
                    target.sendMessage(Component.text("You received a " + element.displayName() + " Catalyst!", NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text("Unknown item. Valid: lightning_core, void_tear, catalyst_<element>", NamedTextColor.RED));
                }
            }
        }
    }

    private void handleAnnounce(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /elemental announce <message>", NamedTextColor.RED));
            return;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Component broadcast = Component.text("[Elemental SMP] ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(message, NamedTextColor.WHITE));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(broadcast);
            online.showTitle(Title.title(
                    Component.text("Server Announcement", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(message, NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))
            ));
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }
        sender.sendMessage(Component.text("Announcement broadcast to " + Bukkit.getOnlinePlayers().size() + " players.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> results = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    results.add(sub);
                }
            }
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("announce")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                results.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setelement")) {
            for (Element element : Element.values()) {
                results.add(element.name());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("giveitem")) {
            results.add("lightning_core");
            results.add("void_tear");
        }
        return results;
    }
}
