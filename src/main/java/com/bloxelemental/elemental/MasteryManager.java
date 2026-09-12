package com.bloxelemental.elemental;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles persistent storage and business logic for elements, mastery levels
 * and XP. Backed by a flat-file data.yml living in the plugin's data folder.
 *
 * A player can own multiple elements at once (players.&lt;uuid&gt;.elements.&lt;NAME&gt;
 * each with their own level/xp) but only one is "active"
 * (players.&lt;uuid&gt;.active) at a time - abilities, passives, and combat all
 * key off whichever element is currently active. Reaching level 100 on any
 * owned element unlocks the right to pick up another one via /element gui.
 */
public class MasteryManager {

    public static final int MAX_LEVEL = 100;
    public static final int MOBILITY_THRESHOLD = 25;
    public static final int HEAVY_THRESHOLD = 50;
    public static final int ULTIMATE_THRESHOLD = 100;

    private final ElementalSMP plugin;
    private final File dataFile;
    private FileConfiguration data;
    private double xpBasePerLevel = 50.0D;
    private double xpScalingPerLevel = 15.0D;

    public MasteryManager(ElementalSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.xpBasePerLevel = plugin.getConfig().getDouble("mastery.xp-base-per-level", 50.0D);
        this.xpScalingPerLevel = plugin.getConfig().getDouble("mastery.xp-scaling-per-level", 15.0D);
    }

    public void loadData() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create data.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        migrateLegacySingleElementFormat();
    }

    /**
     * One-time upgrade for data.yml files written by pre-multi-element versions
     * of this plugin, which stored players.&lt;uuid&gt;.element/level/xp directly
     * instead of under an "elements" map with an "active" pointer. Converts
     * each legacy entry into the new format (preserving level/xp exactly) and
     * removes the old flat keys. Safe to run every startup - already-migrated
     * or brand-new entries are simply skipped. Kills/chambersCleared use the
     * same key names in both formats, so they're untouched either way.
     */
    private void migrateLegacySingleElementFormat() {
        if (!data.isConfigurationSection("players")) {
            return;
        }
        int migrated = 0;
        for (String uuidString : data.getConfigurationSection("players").getKeys(false)) {
            String playerBase = "players." + uuidString;
            boolean alreadyMigrated = data.isString(playerBase + ".active");
            boolean hasLegacyData = data.isString(playerBase + ".element");
            if (alreadyMigrated || !hasLegacyData) {
                continue;
            }

            String legacyElementName = data.getString(playerBase + ".element");
            Element legacyElement = Element.fromArgument(legacyElementName);
            if (legacyElement == null) {
                continue;
            }
            int legacyLevel = data.getInt(playerBase + ".level", 1);
            double legacyXp = data.getDouble(playerBase + ".xp", 0.0D);

            data.set(playerBase + ".elements." + legacyElement.name() + ".level", legacyLevel);
            data.set(playerBase + ".elements." + legacyElement.name() + ".xp", legacyXp);
            data.set(playerBase + ".active", legacyElement.name());

            // Clean up the old flat keys now that they've been copied over - kills/chambersCleared
            // are left completely alone since both formats use those same key names.
            data.set(playerBase + ".element", null);
            data.set(playerBase + ".level", null);
            data.set(playerBase + ".xp", null);

            migrated++;
        }
        if (migrated > 0) {
            saveData();
            plugin.getLogger().info("Migrated " + migrated + " player(s) from the old single-element data format - no progress lost.");
        }
    }

    public void saveData() {
        if (data == null) {
            return;
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    private String base(UUID uuid) {
        return "players." + uuid;
    }

    private String elementPath(UUID uuid, Element element, String key) {
        return base(uuid) + ".elements." + element.name() + "." + key;
    }

    // ---------------------------------------------------------------------
    // Ownership and active-element switching
    // ---------------------------------------------------------------------

    public boolean hasElement(UUID uuid) {
        return data.isString(base(uuid) + ".active");
    }

    /** The element a player is currently using for abilities/passives/combat. */
    public Element getElement(UUID uuid) {
        String raw = data.getString(base(uuid) + ".active");
        return raw == null ? null : Element.fromArgument(raw);
    }

    public boolean ownsElement(UUID uuid, Element element) {
        return data.isInt(elementPath(uuid, element, "level"));
    }

    public List<Element> getOwnedElements(UUID uuid) {
        List<Element> owned = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(base(uuid) + ".elements");
        if (section == null) {
            return owned;
        }
        for (String key : section.getKeys(false)) {
            Element element = Element.fromArgument(key);
            if (element != null) {
                owned.add(element);
            }
        }
        return owned;
    }

    /** First-ever element pick for a brand new player: owns it and makes it active. */
    public void chooseFirstElement(UUID uuid, Element element) {
        initializeElementSlot(uuid, element);
        data.set(base(uuid) + ".active", element.name());
        saveData();
    }

    /**
     * Grants ownership of a new element slot (fresh level 1/xp 0, or preserved
     * if they somehow already own it) and switches active to it. Used both for
     * unlocking an additional starter element and for awakening into an
     * advanced one - neither destroys progress on any other owned element.
     */
    public void unlockAdditionalElement(UUID uuid, Element element) {
        if (!ownsElement(uuid, element)) {
            initializeElementSlot(uuid, element);
        }
        data.set(base(uuid) + ".active", element.name());
        saveData();
    }

    /** Switches active element to one the player already owns, preserving its level/xp untouched. */
    public boolean switchActiveElement(UUID uuid, Element element) {
        if (!ownsElement(uuid, element)) {
            return false;
        }
        data.set(base(uuid) + ".active", element.name());
        saveData();
        return true;
    }

    private void initializeElementSlot(UUID uuid, Element element) {
        data.set(elementPath(uuid, element, "level"), 1);
        data.set(elementPath(uuid, element, "xp"), 0.0D);
    }

    // ---------------------------------------------------------------------
    // Level / XP (all keyed to a specific element, with convenience
    // overloads that operate on whichever element is currently active)
    // ---------------------------------------------------------------------

    public int getLevel(UUID uuid) {
        Element active = getElement(uuid);
        return active == null ? 1 : getLevel(uuid, active);
    }

    public int getLevel(UUID uuid, Element element) {
        return data.getInt(elementPath(uuid, element, "level"), 1);
    }

    public double getXP(UUID uuid) {
        Element active = getElement(uuid);
        return active == null ? 0.0D : getXP(uuid, active);
    }

    public double getXP(UUID uuid, Element element) {
        return data.getDouble(elementPath(uuid, element, "xp"), 0.0D);
    }

    /** Sets the level of the player's currently ACTIVE element (used by the admin /elemental setlevel command). */
    public void setLevel(UUID uuid, int level) {
        Element active = getElement(uuid);
        if (active == null) {
            return;
        }
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        data.set(elementPath(uuid, active, "level"), clamped);
        // Whenever the level is manually forced, remove any partial xp so
        // display stays consistent with the new level's threshold.
        data.set(elementPath(uuid, active, "xp"), 0.0D);
        saveData();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            announceAbilities(player, clamped);
            LevelStats.apply(plugin, player);
        }
    }

    /**
     * XP required to advance from the given level to the next one.
     */
    public double xpForNextLevel(int level) {
        return xpBasePerLevel + (level * xpScalingPerLevel);
    }

    /**
     * Adds mastery XP to the given player's currently active element, applying
     * the elemental chamber bonus multiplier if the player is fighting inside
     * the active chamber, then resolves any level-ups (and the ability unlocks
     * that come with them).
     */
    public void addXP(Player player, double baseAmount) {
        UUID uuid = player.getUniqueId();
        Element element = getElement(uuid);
        if (element == null) {
            return;
        }
        int level = getLevel(uuid, element);
        if (level >= MAX_LEVEL) {
            return;
        }

        double multiplier = plugin.getChamberManager() != null
                ? plugin.getChamberManager().getBonusMultiplier(player.getLocation(), element)
                : 1.0D;
        double amount = baseAmount * multiplier;
        boolean bonusApplied = multiplier > 1.0D;

        double xp = getXP(uuid, element) + amount;

        boolean leveledUp = false;
        while (level < MAX_LEVEL && xp >= xpForNextLevel(level)) {
            xp -= xpForNextLevel(level);
            level++;
            leveledUp = true;
        }
        if (level >= MAX_LEVEL) {
            level = MAX_LEVEL;
            xp = 0.0D;
        }

        data.set(elementPath(uuid, element, "level"), level);
        data.set(elementPath(uuid, element, "xp"), xp);
        saveData();

        if (bonusApplied) {
            String label = multiplier >= 3.0D ? "3x Matching Chamber Bonus" : "1.5x Chamber Bonus";
            player.sendActionBar(Component.text("+" + String.format("%.1f", amount) + " Mastery XP (" + label + ")", NamedTextColor.LIGHT_PURPLE));
        }

        if (leveledUp) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
            player.sendMessage(Component.text("Your Mastery has grown! Level " + level, NamedTextColor.GOLD, TextDecoration.BOLD));
            announceAbilities(player, level);
            LevelStats.apply(plugin, player);
        }
    }

    /**
     * If the given level exactly matches an unlock threshold, informs the player
     * of the new ability with a title and chat message.
     */
    private void announceAbilities(Player player, int level) {
        String unlocked = null;
        if (level == 1) {
            unlocked = "Basic Skill";
        } else if (level == MOBILITY_THRESHOLD) {
            unlocked = "Mobility Skill";
        } else if (level == HEAVY_THRESHOLD) {
            unlocked = "Heavy Combat Skill";
        } else if (level == ULTIMATE_THRESHOLD) {
            unlocked = "Ultimate Skill - you can now pick up another element via /element gui!";
        } else {
            return;
        }
        player.showTitle(Title.title(
                Component.text("Ability Unlocked!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(unlocked, NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        ));
    }

    /**
     * Returns the human readable list of abilities unlocked at the player's current active level.
     */
    public List<String> getUnlockedAbilities(UUID uuid) {
        List<String> abilities = new ArrayList<>();
        int level = getLevel(uuid);
        if (level >= 1) {
            abilities.add("Basic Skill");
        }
        if (level >= MOBILITY_THRESHOLD) {
            abilities.add("Mobility Skill");
        }
        if (level >= HEAVY_THRESHOLD) {
            abilities.add("Heavy Combat Skill");
        }
        if (level >= ULTIMATE_THRESHOLD) {
            abilities.add("Ultimate Skill");
        }
        return abilities;
    }

    public boolean canUseTier(UUID uuid, int requiredLevel) {
        return getLevel(uuid) >= requiredLevel;
    }

    /** True if the player owns any STARTER element at max level - the gate for awakening into Lightning/Void. */
    public boolean isAwakeningEligible(UUID uuid) {
        for (Element element : getOwnedElements(uuid)) {
            if (element.isStarter() && getLevel(uuid, element) >= ULTIMATE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /** True if the player owns ANY element at max level - the general gate for picking up another element slot. */
    public boolean canUnlockAnotherElement(UUID uuid) {
        for (Element element : getOwnedElements(uuid)) {
            if (getLevel(uuid, element) >= ULTIMATE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Awakens a player into Lightning or Void. This is purely additive now -
     * it does not touch any element they already own.
     */
    public void awaken(UUID uuid, Element advancedElement) {
        unlockAdditionalElement(uuid, advancedElement);
    }

    // ---------------------------------------------------------------------
    // Stats tracking (kills, chambers cleared) and leaderboards
    // ---------------------------------------------------------------------

    public void incrementKills(UUID uuid) {
        data.set(base(uuid) + ".kills", getKills(uuid) + 1);
        saveData();
    }

    public int getKills(UUID uuid) {
        return data.getInt(base(uuid) + ".kills", 0);
    }

    public void incrementChambersCleared(UUID uuid) {
        data.set(base(uuid) + ".chambersCleared", getChambersCleared(uuid) + 1);
        saveData();
    }

    public int getChambersCleared(UUID uuid) {
        return data.getInt(base(uuid) + ".chambersCleared", 0);
    }

    /** One row of a /elemental top leaderboard - one per (player, owned element) pair. */
    public record LeaderboardEntry(UUID uuid, String name, Element element, int level, double xp) {
    }

    /**
     * Returns the top (player, element) pairs by Mastery level (ties broken by
     * XP), optionally filtered to one element. A player who has mastered
     * several elements can appear multiple times, once per element. Player
     * names are resolved via Bukkit's offline player cache, so anyone who has
     * ever joined the server will show up.
     */
    public List<LeaderboardEntry> getTopPlayers(Element filter, int limit) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        if (!data.isConfigurationSection("players")) {
            return entries;
        }
        for (String uuidString : data.getConfigurationSection("players").getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            String displayName = name != null ? name : uuidString.substring(0, 8);
            for (Element element : getOwnedElements(uuid)) {
                if (filter != null && element != filter) {
                    continue;
                }
                entries.add(new LeaderboardEntry(uuid, displayName, element, getLevel(uuid, element), getXP(uuid, element)));
            }
        }
        entries.sort((a, b) -> {
            if (b.level() != a.level()) {
                return Integer.compare(b.level(), a.level());
            }
            return Double.compare(b.xp(), a.xp());
        });
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }
}
