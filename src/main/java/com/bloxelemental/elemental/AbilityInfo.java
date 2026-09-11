package com.bloxelemental.elemental;

import java.util.EnumMap;
import java.util.Map;

/**
 * Static lookup of gameplay descriptions for each element's four ability
 * tiers (index 0 = Basic, 1 = Mobility, 2 = Heavy, 3 = Ultimate).
 */
public final class AbilityInfo {

    private static final Map<Element, String[]> DESCRIPTIONS = new EnumMap<>(Element.class);

    static {
        DESCRIPTIONS.put(Element.FIRE, new String[]{
                "Combustion - snap your fingers to ignite the target, dealing instant damage and burning them for 5s.",
                "Blaze Dash - become a streak of fire and dash forward 8 blocks, leaving a 3s trail of fire behind you.",
                "Fire Shield - a spinning ring of flame for 3s; anyone who melees you during it is ignited and knocked back.",
                "Inferno Blast - charge a fireball for 1s, then launch it forward for a fiery AoE explosion that breaks weak blocks."
        });
        DESCRIPTIONS.put(Element.WATER, new String[]{
                "Water Whip - extend a whip of water up to 10 blocks, damaging and dragging the target 3 blocks closer.",
                "Torrent Surge - a wave carries you rapidly forward; double distance and speed while in water.",
                "Healing Surge - channel for 2s (interrupted by damage) to restore 3 hearts.",
                "Frost Freeze - splash a freezing cone in front of you, locking caught enemies in place for 1.5s."
        });
        DESCRIPTIONS.put(Element.AIR, new String[]{
                "Vacuum Pull - open a vortex up to 15 blocks away; after a short delay it drags nearby players to its center.",
                "Gale Force - blast forward horizontally in the direction you're looking; doubles as a mid-air double jump.",
                "Sonic Boom - fire a fast invisible shot with heavy knockback, dealing bonus damage if the target slams a wall.",
                "Suffocate - trap an enemy's head in a low-pressure vortex for 4s, dealing minor damage and Blindness."
        });
        DESCRIPTIONS.put(Element.EARTH, new String[]{
                "Fissure - slam the ground to send a line of raised stone at your target, damaging and popping them up.",
                "Seismic Leap - launch forward and slightly up; landing sends out a shockwave that knocks back and slows.",
                "Rock Wall - instantly raise a 3x2 cobblestone wall in front of you that crumbles after 4s.",
                "Sand Tomb - turn the ground beneath a target within 12 blocks to quicksand for 2.5s, trapping their feet."
        });
        DESCRIPTIONS.put(Element.LIGHTNING, new String[]{
                "Static Discharge - a bolt of lightning arcs forward, damaging and briefly slowing the first enemy it hits.",
                "Storm Step - blink instantly 6 blocks forward in a flash of lightning.",
                "Thunder Slam - call down lightning strikes on every enemy nearby, damaging and briefly stunning them.",
                "Tempest's Wrath - a barrage of repeated lightning strikes devastates every enemy in a wide radius."
        });
        DESCRIPTIONS.put(Element.VOID, new String[]{
                "Void Rend - tear open space in front of you, damaging and darkening the vision of enemies caught in it.",
                "Void Step - blink 8 blocks forward, passing straight through thin walls.",
                "Event Horizon - warp space around you, pulling in, damaging, and blinding nearby enemies.",
                "Oblivion - rip open the void itself, dealing devastating damage and withering everything nearby."
        });
    }

    private AbilityInfo() {
    }

    public static String describe(Element element, Tier tier) {
        String[] arr = DESCRIPTIONS.get(element);
        if (arr == null) {
            return "No description available.";
        }
        return arr[tier.ordinal()];
    }
}
