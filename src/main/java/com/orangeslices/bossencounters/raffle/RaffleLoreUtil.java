private static String displayName(RaffleEffectId id) {
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

        // New effects/curses can be added without touching lore.
        // Fallback: Title Case the enum name.
        default -> titleCase(id.name());
    };
}

private static String titleCase(String raw) {
    String[] parts = raw.toLowerCase().split("_");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
        String p = parts[i];
        if (p.isEmpty()) continue;
        sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        if (i < parts.length - 1) sb.append(' ');
    }
    return sb.toString();
}
