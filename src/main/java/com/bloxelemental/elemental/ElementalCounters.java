package com.bloxelemental.elemental;

/**
 * A six-way counter cycle: each element deals bonus damage to the next one
 * in the ring, and takes a penalty against the one before it. Order:
 * Fire -> Earth -> Lightning -> Water -> Air -> Void -> (back to Fire).
 * Fire beats Earth, Earth beats Lightning, Lightning beats Water,
 * Water beats Air, Air beats Void, Void beats Fire.
 */
public final class ElementalCounters {

    private static final Element[] CYCLE = {
            Element.FIRE, Element.EARTH, Element.LIGHTNING, Element.WATER, Element.AIR, Element.VOID
    };

    private static final double ADVANTAGE_MULTIPLIER = 1.25D;
    private static final double DISADVANTAGE_MULTIPLIER = 0.85D;

    private ElementalCounters() {
    }

    /**
     * Returns the damage multiplier for an attacker's element hitting a
     * defender's element. Either side may be null (no element chosen, or a
     * mob with no elemental affinity) - in that case the fight is neutral.
     */
    public static double damageMultiplier(Element attacker, Element defender) {
        if (attacker == null || defender == null || attacker == defender) {
            return 1.0D;
        }
        int attackerIndex = indexOf(attacker);
        int defenderIndex = indexOf(defender);
        if (attackerIndex == -1 || defenderIndex == -1) {
            return 1.0D;
        }
        if ((attackerIndex + 1) % CYCLE.length == defenderIndex) {
            return ADVANTAGE_MULTIPLIER;
        }
        if ((defenderIndex + 1) % CYCLE.length == attackerIndex) {
            return DISADVANTAGE_MULTIPLIER;
        }
        return 1.0D;
    }

    private static int indexOf(Element element) {
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static String describeCycle() {
        return "Fire > Earth > Lightning > Water > Air > Void > Fire";
    }
}
