private boolean tryApplyRaffle(Player player, PlayerInteractEvent event, ItemStack main, ItemStack off) {
    boolean mainIsRaffle = RaffleTokenFactory.isRaffleToken(main);
    boolean offIsRaffle = RaffleTokenFactory.isRaffleToken(off);

    // main hand token -> off hand armor
    if (mainIsRaffle && isArmor(off)) {
        RaffleApplyResult result = RaffleService.applyToArmor(plugin, off);

        if (result.isSuccess()) {
            consumeOneFromHand(player, EquipmentSlot.HAND);

            // Vague lore: slots only (no effect names)
            RaffleLoreUtil.updateVagueLore(off, result.getUsedSlots(), result.getMaxSlots());

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
            player.sendMessage(ChatColor.GREEN + "Add-on applied.");
        } else {
            player.sendMessage(ChatColor.RED + result.getMessage());
        }

        event.setCancelled(true); // always cancel if the player used a raffle token
        return true;
    }

    // off hand token -> main hand armor
    if (offIsRaffle && isArmor(main)) {
        RaffleApplyResult result = RaffleService.applyToArmor(plugin, main);

        if (result.isSuccess()) {
            consumeOneFromHand(player, EquipmentSlot.OFF_HAND);

            // Vague lore: slots only (no effect names)
            RaffleLoreUtil.updateVagueLore(main, result.getUsedSlots(), result.getMaxSlots());

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
            player.sendMessage(ChatColor.GREEN + "Add-on applied.");
        } else {
            player.sendMessage(ChatColor.RED + result.getMessage());
        }

        event.setCancelled(true);
        return true;
    }

    // If they are holding a raffle token but not holding armor, cancel and message
    if (mainIsRaffle || offIsRaffle) {
        player.sendMessage(ChatColor.RED + "Hold armor in the other hand to apply the raffle token.");
        event.setCancelled(true);
        return true;
    }

    return false;
}
