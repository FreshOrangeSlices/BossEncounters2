package com.orangeslices.bossencounters;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class SharpeningAnvilListener implements Listener {

    private final BossEncountersPlugin plugin;

    private final NamespacedKey kitLevelKey;
    private final NamespacedKey weaponSharpKey;

    // Stable UUID so we overwrite ONLY our sharpening modifier
    private static final UUID SHARP_MOD_UUID =
            UUID.fromString("0b3c4a7d-5b15-4b38-9a78-8d7d1f4b1c2a");

    public SharpeningAnvilListener(BossEncountersPlugin plugin) {
        this.plugin = plugin;
        this.kitLevelKey = new NamespacedKey(plugin, "sharpen_kit_level");
        this.weaponSharpKey = new NamespacedKey(plugin, "sharpen_level");
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory inv)) return;

        ItemStack weapon = inv.getItem(0);
        ItemStack kit = inv.getItem(1);

        if (isEmpty(weapon) || isEmpty(kit)) {
            event.setResult(null);
            return;
        }

        if (!isSharpenableWeapon(weapon)) {
            event.setResult(null);
            return;
        }

        int kitLevel = getKitLevel(kit);
        if (kitLevel <= 0) {
            event.setResult(null);
            return;
        }

        int current = getWeaponSharpLevel(weapon);
        int target = Math.min(2, Math.max(current, kitLevel));

        // No change -> no result (prevents wasting kit)
        if (target == current) {
            event.setResult(null);
            return;
        }

        ItemStack result = weapon.clone();
        applySharpening(result, target);
        event.setResult(result);

        // Force anvil cost = 1 level
        inv.setRepairCost(1);
        inv.setMaximumRepairCost(1);
    }

    @EventHandler
    public void onTakeResult(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top instanceof AnvilInventory inv)) return;

        // Result slot is 2
        if (event.getRawSlot() != 2) return;

        ItemStack result = event.getCurrentItem();
        if (isEmpty(result)) return;

        // Only consume if result actually has our sharpening tag
        if (getWeaponSharpLevel(result) <= 0) return;

        ItemStack kit = inv.getItem(1);
        if (isEmpty(kit)) return;

        if (getKitLevel(kit) <= 0) return;

        // Consume exactly 1 kit
        int newAmount = kit.getAmount() - 1;
        if (newAmount <= 0) {
            inv.setItem(1, null);
        } else {
            kit.setAmount(newAmount);
            inv.setItem(1, kit);
        }
    }

    /* =============================
       Weapon rules
       ============================= */

    private boolean isSharpenableWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        String type = item.getType().name();

        if (type.endsWith("_SWORD")) return true;
        if (type.endsWith("_AXE")) return true;
        if (type.endsWith("_SPEAR")) return true;

        // Optional: keep trident
        if (type.equals("TRIDENT")) return true;

        return false;
    }

    /* =============================
       Kit detection
       ============================= */

    private int getKitLevel(ItemStack kit) {
        ItemMeta meta = kit.getItemMeta();
        if (meta == null) return 0;

        Integer lvl = meta.getPersistentDataContainer()
                .get(kitLevelKey, PersistentDataType.INTEGER);

        if (lvl == null) return 0;
        return Math.min(2, Math.max(0, lvl));
    }

    /* =============================
       Weapon sharpening tag
       ============================= */

    private int getWeaponSharpLevel(ItemStack weapon) {
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return 0;

        Integer lvl = meta.getPersistentDataContainer()
                .get(weaponSharpKey, PersistentDataType.INTEGER);

        if (lvl == null) return 0;
        return Math.min(2, Math.max(0, lvl));
    }

    private void setWeaponSharpLevel(ItemMeta meta, int lvl) {
        meta.getPersistentDataContainer().set(
                weaponSharpKey,
                PersistentDataType.INTEGER,
                Math.min(2, Math.max(0, lvl))
        );
    }

    /* =============================
       Apply sharpening correctly
       ============================= */

    private void applySharpening(ItemStack weapon, int level) {
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        setWeaponSharpLevel(meta, level);

        Attribute attackAttr = resolveAttackDamageAttribute();
        if (attackAttr == null) {
            // Fail safe: don’t modify damage if we can’t resolve attribute
            applySharpenLore(meta, level);
            weapon.setItemMeta(meta);
            return;
        }

        // Remove ONLY our previous modifier (by UUID), leave vanilla weapon damage intact
        if (meta.hasAttributeModifiers()) {
            Collection<AttributeModifier> mods = meta.getAttributeModifiers(attackAttr);
            if (mods != null) {
                for (AttributeModifier m : mods) {
                    if (SHARP_MOD_UUID.equals(m.getUniqueId())) {
                        meta.removeAttributeModifier(attackAttr, m);
                    }
                }
            }
        }

        double bonus = (level == 1) ? 1.0 : 2.0;

        AttributeModifier mod = new AttributeModifier(
                SHARP_MOD_UUID,
                "bossencounters_sharpen",
                bonus,
                AttributeModifier.Operation.ADD_NUMBER
        );

        meta.addAttributeModifier(attackAttr, mod);

        // Add/update lore line
        applySharpenLore(meta, level);

        weapon.setItemMeta(meta);
    }

    private void applySharpenLore(ItemMeta meta, int level) {
        String line = (level == 1)
                ? "§7Sharpened: §aI"
                : "§7Sharpened: §bII";

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        // Remove any existing "Sharpened:" line (ignoring color codes)
        lore.removeIf(s -> stripColor(s).toLowerCase().startsWith("sharpened:"));

        // Put it at the top for visibility
        lore.add(0, line);

        meta.setLore(lore);
    }

    /**
     * Resolve attack damage attribute in a way that compiles across APIs where
     * Attribute may be an enum OR a class with static fields.
     */
    private Attribute resolveAttackDamageAttribute() {
        Attribute a = getAttributeStatic("GENERIC_ATTACK_DAMAGE");
        if (a != null) return a;

        return getAttributeStatic("ATTACK_DAMAGE");
    }

    private Attribute getAttributeStatic(String fieldName) {
        try {
            Field f = Attribute.class.getField(fieldName);
            Object v = f.get(null);
            return (v instanceof Attribute) ? (Attribute) v : null;
        } catch (NoSuchFieldException ignored) {
            return null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private String stripColor(String s) {
        if (s == null) return "";
        // remove § color codes
        return s.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }
}
