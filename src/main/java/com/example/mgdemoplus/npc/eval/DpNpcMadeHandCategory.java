package com.example.mgdemoplus.npc.eval;

/**
 * 翻后成牌 12 档（NPC 决策轨）；强度序与 {@link #strengthOrder()} 一致。
 */
public enum DpNpcMadeHandCategory {
    HIGH_CARD,
    BOTTOM_PAIR,
    MIDDLE_PAIR,
    TOP_PAIR_WEAK_KICKER,
    TOP_PAIR_TOP_KICKER,
    TWO_PAIR,
    TRIPS,
    STRAIGHT,
    FLUSH,
    FULL_HOUSE,
    QUADS,
    ROCKET;

    public int strengthOrder() {
        return ordinal();
    }

    public boolean isAtLeast(DpNpcMadeHandCategory other) {
        return other != null && this.strengthOrder() >= other.strengthOrder();
    }
}
