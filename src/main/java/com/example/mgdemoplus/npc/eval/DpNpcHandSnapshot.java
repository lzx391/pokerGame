package com.example.mgdemoplus.npc.eval;

import com.example.mgdemoplus.utils.DpUtilHandEvaluator.HandStrength;

/**
 * NPC 决策轨不可变牌力快照：成牌档 + 听牌轴 + 元数据。
 */
public final class DpNpcHandSnapshot {

    public final DpNpcMadeHandCategory made;
    public final DpNpcDrawCategory draw;
    public final DpNpcPreflopCategory preflop;
    public final HandStrength handStrength;
    public final boolean playingTheBoard;
    public final boolean counterfeit;
    public final boolean holeContributes;
    /** Set（口袋对 + board 一张）标记，供权益微调 */
    public final boolean pocketSet;
    /** 超对（口袋对 &gt; board high）标记，供权益微调 */
    public final boolean overpair;

    private DpNpcHandSnapshot(
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpNpcPreflopCategory preflop,
            HandStrength handStrength,
            boolean playingTheBoard,
            boolean counterfeit,
            boolean holeContributes,
            boolean pocketSet,
            boolean overpair) {
        this.made = made;
        this.draw = draw;
        this.preflop = preflop;
        this.handStrength = handStrength;
        this.playingTheBoard = playingTheBoard;
        this.counterfeit = counterfeit;
        this.holeContributes = holeContributes;
        this.pocketSet = pocketSet;
        this.overpair = overpair;
    }

    public static DpNpcHandSnapshot preflop(DpNpcPreflopCategory preflop, HandStrength handStrength) {
        return new DpNpcHandSnapshot(
                null,
                DpNpcDrawCategory.NONE,
                preflop,
                handStrength,
                false,
                false,
                true,
                false,
                false);
    }

    public static DpNpcHandSnapshot postflop(
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            HandStrength handStrength,
            boolean playingTheBoard,
            boolean counterfeit,
            boolean holeContributes,
            boolean pocketSet,
            boolean overpair) {
        return new DpNpcHandSnapshot(
                made,
                draw != null ? draw : DpNpcDrawCategory.NONE,
                null,
                handStrength,
                playingTheBoard,
                counterfeit,
                holeContributes,
                pocketSet,
                overpair);
    }
}
