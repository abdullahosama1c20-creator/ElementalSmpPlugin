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

    /** Cooldown decreases from cooldownSeconds*startingMultiplier at level 1 down to cooldownSeconds at level 100. */
    static double startingMultiplier = 1.5D;

    final int requiredLevel;
    int cooldownSeconds; // the LEVEL-100 (minimum) cooldown - not final, overridable from config.yml's cooldowns section
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
        if (cooldowns.isDouble("starting-multiplier") || cooldowns.isInt("starting-multiplier")) {
            startingMultiplier = cooldowns.getDouble("starting-multiplier", 1.5D);
        }
    }

    /**
     * The actual cooldown for a player at the given Mastery level: highest at
     * level 1 (cooldownSeconds * startingMultiplier), linearly decreasing down
     * to the flat cooldownSeconds value by level 100.
     */
    public double cooldownSecondsForLevel(int level) {
        int clamped = Math.max(1, Math.min(MasteryManager.MAX_LEVEL, level));
        double progress = (clamped - 1) / (double) (MasteryManager.MAX_LEVEL - 1); // 0 at lvl1, 1 at lvl100
        double startValue = cooldownSeconds * startingMultiplier;
        return startValue - (startValue - cooldownSeconds) * progress;
    }
}
