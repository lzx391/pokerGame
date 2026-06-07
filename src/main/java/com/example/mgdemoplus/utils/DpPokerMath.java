package com.example.mgdemoplus.utils;

import java.util.List;

/**
 * 上帝视角精确扑克数学（成就 / 复盘 / 未来精确 NPC）。
 * 本次仅接口占位，规则 NPC 仍用 {@link com.example.mgdemoplus.npc.eval.DpNpcEquityEstimator} 启发式粗桶。
 */
public interface DpPokerMath {

    /** 精确 win% vs 1~N 随机范围（枚举或 MC），成就系统用 */
    double exactEquity(List<String> heroHole, List<String> board,
                       List<List<String>> villainHoles, int iterations);

    /** 精确 outs 数（含 dirty outs 标记） */
    int countOuts(List<String> heroHole, List<String> board, boolean includeDirty);

    /** 是否已实现 MC（本次返回 false） */
    default boolean isMonteCarloAvailable() {
        return false;
    }
}
