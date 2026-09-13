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

    /** Cooldown at level 1 is cooldownSeconds * startingMultiplier before it starts decreasing. */
    static double startingMultiplier = 1.5D;
    /**
     * How much each level shaves off, as a fraction of the tier's base cooldown -
     * e.g. 0.0333 means Basic (3s base) drops ~0.1s/level (-0.3s every 3 levels),
     * while Ultimate (30s base) drops ~1.0s/level, proportionally faster since it
     * has more room to shed. Decreasing stops once it hits the flat cooldownSeconds
     * floor - it does not creep toward it forever like the old version did.
     */
    static double decreaseFractionPerLevel = 0.0333D;

    final int requiredLevel;
    int cooldownSeconds; // the LEVEL-100 (minimum/floor) cooldown - not final, overridable from config.yml's cooldowns section
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
        if (cooldowns.isDouble("decrease-fraction-per-level") || cooldowns.isInt("decrease-fraction-per-level")) {
            decreaseFractionPerLevel = cooldowns.getDouble("decrease-fraction-per-level", 0.0333D);
        }
    }

    /**
     * The actual cooldown for a player at the given Mastery level: highest at
     * level 1 (cooldownSeconds * startingMultiplier), dropping by
     * (cooldownSeconds * decreaseFractionPerLevel) each level, floored at the
     * flat cooldownSeconds value - reachable well before level 100 for low-base
     * tiers, at which point further leveling doesn't shrink it any further.
     */
    public double cooldownSecondsForLevel(int level) {
        int clamped = Math.max(1, Math.min(MasteryManager.MAX_LEVEL, level));
        double startValue = cooldownSeconds * startingMultiplier;
        double perLevelDecrease = cooldownSeconds * decreaseFractionPerLevel;
        double value = startValue - perLevelDecrease * (clamped - 1);
        return Math.max(cooldownSeconds, value);
    }
}
