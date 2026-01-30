package com.orangeslices.bossencounters.raffle;

import com.orangeslices.bossencounters.raffle.effects.RaffleEffectReader;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Builds vague lore for raffle armor based on PDC.
 *
 * IMPORTANT:
 * - Never expose potion effect names.
 * - Lore is thematic and intentionally ambiguous.
 */
public final class RaffleLoreUtil {

    private RaffleLoreUtil() {}

    public static void applyLore(ItemStack armor, int maxSlots) {
        if (armor == null) return;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return;

        Map<RaffleEffectId, Integer> effects = RaffleEffectReader.readFromItem(armor);

        List<String> lore = new ArrayList<>();

        int usedSlots = effects.size();
        lore.add(color("&8\u25A0 &7Add-On Slots: &f" + usedSlots + "&7/&f" + maxSlots));

        if (effects.isEmpty()) {
            lore.add(color("&8\u25A0 &7No lingering influence."));
        } else {
            lore.add(color("&8\u25A0 &7Imprinted Effects:"));

            // Sort: GOOD first then CURSE, then stable by name
            List<Map.Entry<RaffleEffectId, Integer>> sorted = new ArrayList<>(effects.entrySet());
            sorted.sort((a, b) -> {
                boolean ac = a.getKey().isCurse();
                boolean bc = b.getKey().isCurse();
                if (ac != bc) return ac ? 1 : -1; // curses last
                return a.getKey().name().compareTo(b.getKey().name());
            });

            for (Map.Entry<RaffleEffectId, Integer> e : sorted) {
                RaffleEffectId id = e.getKey();
                int level = e.getValue();

                String name = displayName(id);
                String line;

                if (id.isCurse()) {
                    // Curse: no levels shown (keeps it mysterious)
                    line = "&8\u25A0 &5" + name;
                } else {
                    // Good: show level only if it can level
                    if (id.canLevel()) {
                        line = "&8\u25A0 &d" + name + " &7(I" + roman(Math.max(1, level)) + ")";
                    } else {
                        line = "&8\u25A0 &d" + name;
                    }
                }

                lore.add(color(line));
            }
        }

        // Add a subtle footer line (optional flavor)
        lore.add(color("&8\u25A0 &8&o\"The armor remembers...\""));

        meta.setLore(lore);
        armor.setItemMeta(meta);
    }

    private static String displayName(RaffleEffectId id) {
        // Thematic names only — never mechanical names.
        return switch (id) {
            case VITALITY -> "Vitality";
            case IRON_WILL -> "Iron Will";
            case BLOOD_MENDING -> "Blood Mending";
            case SKYBOUND -> "Skybound";

            case EMBER_WARD -> "Ember Ward";
            case FORTUNE -> "Fortune";
            case TIDEBOUND -> "Tidebound";
            case OCEAN_GRACE -> "Ocean Grace";
            case VILLAGER_FAVOR -> "Villager's Favor";

            case TERROR -> "Terror";
            case DREAD -> "Dread";
            case MISSTEP -> "Misstep";
        };
    }

    private static String roman(int n) {
        // Small set only (we don't expect huge levels)
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
