package com.example.mgdemoplus.npc.eval;

/**
 * 听牌第二轴（与成牌档独立）；河牌恒为 {@link #NONE}。
 */
public enum DpNpcDrawCategory {
    NONE,
    GUTSHOT,
    OESD,
    FLUSH_DRAW,
    COMBO_DRAW;

    public int drawStrengthOrder() {
        return ordinal();
    }

    public boolean isAtLeast(DpNpcDrawCategory other) {
        return other != null && this.drawStrengthOrder() >= other.drawStrengthOrder();
    }
}
