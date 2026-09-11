package com.bloxelemental.elemental;

/**
 * The four ability tiers every element unlocks at, matching the Mastery
 * thresholds in MasteryManager.
 */
public enum Tier {
    BASIC(1, 3, "Basic Skill"),
    MOBILITY(MasteryManager.MOBILITY_THRESHOLD, 8, "Mobility Skill"),
    HEAVY(MasteryManager.HEAVY_THRESHOLD, 15, "Heavy Combat Skill"),
    ULTIMATE(MasteryManager.ULTIMATE_THRESHOLD, 30, "Ultimate Skill");

    final int requiredLevel;
    int cooldownSeconds; // not final - overridable at startup from config.yml's cooldowns section
    final String label;

    Tier(int requiredLevel, int cooldownSeconds, String label) {
        this.requiredLevel = requiredLevel;
        this.cooldownSeconds = cooldownSeconds;
        this.label = label;
    }

    /** Applies admin-configured cooldowns from config.yml, if present. Called once at startup. */
    public static void applyConfig(org.bukkit.configuration.ConfigurationSection cooldowns) {
        if (cooldowns == null) {
            return;
        }
        for (Tier tier : values()) {
            String key = tier.name().toLowerCase() + "-seconds";
            if (cooldowns.isInt(key)) {
                tier.cooldownSeconds = cooldowns.getInt(key);
            }
        }
    }
}
