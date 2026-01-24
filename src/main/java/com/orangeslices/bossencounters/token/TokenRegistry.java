package com.orangeslices.bossencounters.token;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One registry for all tokens so:
 * - drops can ask "what tokens exist?"
 * - apply logic can ask "what target + clamp rules apply?"
 * - kits can be generated from shared metadata later
 */
public final class TokenRegistry {

    private final Map<TokenType, TokenDefinition> defs = new EnumMap<>(TokenType.class);

    public TokenRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        // NOTE: Clamp ranges here are conservative defaults.
        // We can later wire these to config.yml if you want.
        // Also: these match your design intent (armor vs weapon separation).

        // Effect-math tokens
        register(new TokenDefinition(
                TokenType.SHARPEN,
                TokenDefinition.Target.WEAPON,
                1, 5,
                "Sharpen",
                "&7Sharpen: &f{level}"
        ));

        register(new TokenDefinition(
                TokenType.MARK,
                TokenDefinition.Target.WEAPON,
                1, 5,
                "Mark",
                "&7Mark: &f{level}"
        ));

        // Potion tokens (targets based on your “slot” thinking)
        register(new TokenDefinition(
                TokenType.HASTE,
                TokenDefinition.Target.WEAPON, // you mentioned haste/strength as weapon-ish
                1, 3,
                "Haste",
                "&7Haste: &f{level}"
        ));

        register(new TokenDefinition(
                TokenType.STRENGTH,
                TokenDefinition.Target.WEAPON,
                1, 3,
                "Strength",
                "&7Strength: &f{level}"
        ));

        register(new TokenDefinition(
                TokenType.SPEED,
                TokenDefinition.Target.ARMOR_BOOTS,
                1, 3,
                "Speed",
                "&7Speed: &f{level}"
        ));

        register(new TokenDefinition(
                TokenType.FIRE_RESIST,
                TokenDefinition.Target.ARMOR_CHESTPLATE,
                1, 1,
                "Fire Resist",
                "&7Fire Resist: &f{level}"
        ));

        register(new TokenDefinition(
                TokenType.HEALTH_BOOST,
                TokenDefinition.Target.ARMOR_CHESTPLATE,
                1, 2,
                "Health Boost",
                "&7Health Boost: &f{level}"
        ));

        register(new TokenDefinition(
                TokenType.NIGHT_VISION,
                TokenDefinition.Target.ARMOR_HELMET,
                1, 1,
                "Night Vision",
                "&7Night Vision: &f{level}"
        ));
    }

    public void register(TokenDefinition def) {
        Objects.requireNonNull(def, "def");
        defs.put(def.type(), def);
    }

    public TokenDefinition get(TokenType type) {
        return defs.get(type);
    }

    public boolean has(TokenType type) {
        return defs.containsKey(type);
    }

    public Collection<TokenDefinition> all() {
        return Collections.unmodifiableCollection(defs.values());
    }

    public Map<TokenType, TokenDefinition> asMap() {
        return Collections.unmodifiableMap(defs);
    }
}
