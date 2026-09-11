package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Sets playersSleepingPercentage very low (1%) on every world so that, no
 * matter how many players are online (up to 100), a single sleeper is always
 * enough to skip the night - vanilla rounds the required count up, so 1% of
 * any realistic player count still rounds up to exactly 1. Whoever slept
 * through the night is rewarded with bonus Mastery XP on their active element.
 */
public class SleepListener implements Listener {

    private final ElementalSMP plugin;
    private final double bonusXp;

    public SleepListener(ElementalSMP plugin) {
        this.plugin = plugin;
        this.bonusXp = plugin.getConfig().getDouble("sleep.bonus-xp", 50.0D);
    }

    public void applyToAllWorlds() {
        for (World world : org.bukkit.Bukkit.getWorlds()) {
            world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 1);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 1);
    }

    @EventHandler
    public void onTimeSkip(TimeSkipEvent event) {
        if (event.getSkipReason() != TimeSkipEvent.SkipReason.NIGHT_SKIP) {
            return;
        }
        for (Player player : event.getWorld().getPlayers()) {
            if (!player.isSleeping()) {
                continue;
            }
            plugin.getMasteryManager().addXP(player, bonusXp);
            player.sendMessage(Component.text("Thanks for sleeping! +" + (int) bonusXp + " Mastery XP.", NamedTextColor.LIGHT_PURPLE));
        }
    }
}
