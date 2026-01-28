package com.orangeslices.bossencounters.raffle;

public final class RaffleApplyResult {
    private final boolean success;
    private final String message;
    private final String rolledId;
    private final boolean curseApplied;
    private final int usedSlots;
    private final int maxSlots;

    private RaffleApplyResult(boolean success, String message, String rolledId, boolean curseApplied, int usedSlots, int maxSlots) {
        this.success = success;
        this.message = message;
        this.rolledId = rolledId;
        this.curseApplied = curseApplied;
        this.usedSlots = usedSlots;
        this.maxSlots = maxSlots;
    }

    public static RaffleApplyResult ok(String rolledId, boolean curseApplied, int usedSlots, int maxSlots) {
        return new RaffleApplyResult(true, "Applied.", rolledId, curseApplied, usedSlots, maxSlots);
    }

    public static RaffleApplyResult fail(String message, int usedSlots, int maxSlots) {
        return new RaffleApplyResult(false, message, null, false, usedSlots, maxSlots);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getRolledId() { return rolledId; }
    public boolean isCurseApplied() { return curseApplied; }
    public int getUsedSlots() { return usedSlots; }
    public int getMaxSlots() { return maxSlots; }
}
