package com.orangeslices.bossencounters.token;

/**
 * All token types supported by the plugin.
 *
 * IMPORTANT:
 * - This is the single unified list (no more multiple enums across classes).
 * - Naming here should be stable because it may be saved in PDC as a string.
 */
public enum TokenType {

    // “Effect math” tokens (custom logic)
    SHARPEN,   // sharpening kit (damage scaling)
    MARK,      // mark kit (your custom mechanic)

    // Potion-based addons (vanilla potion effects)
    HASTE,
    STRENGTH,
    SPEED,
    FIRE_RESIST,
    HEALTH_BOOST,
    NIGHT_VISION
}
