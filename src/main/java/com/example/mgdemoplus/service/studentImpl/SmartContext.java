package com.example.mgdemoplus.service.studentImpl;

import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.entity.PlayerStats;

/**
 * 德扑 NPC 的统一“聪明决策上下文”数据结构。
 * 仅作为承载数据的简单对象，构建逻辑由 {@link DpNpcEngine} 内部负责。
 */
final class SmartContext {
    final DpPlayer aggressor;            // 当前这轮的主要进攻者（最大 bet 且不是 hero）
    final PlayerStats aggressorStats;    // 该玩家的统计（可为 null）
    final DpNpcEngine.VillainRangeTier villainTier;  // 对手整体风格
    final DpNpcEngine.ActionCredibility credibility; // 当前动作可信度（偏 value 还是偏 bluff）
    final double showdownBluffiness;     // 最近摊牌 bluff 倾向 [0,1]
    final double potOdds;                // 当前跟注的底池赔率 [0,1]
    final double equityEst;              // 粗略赢率桶 [0,1]
    final DpNpcEngine.StackContext stackCtx;         // 筹码深度上下文
    final int activeVillains;           // hero 之外还在这手牌里的玩家数

    SmartContext(DpPlayer aggressor,
                 PlayerStats aggressorStats,
                 DpNpcEngine.VillainRangeTier villainTier,
                 DpNpcEngine.ActionCredibility credibility,
                 double showdownBluffiness,
                 double potOdds,
                 double equityEst,
                 DpNpcEngine.StackContext stackCtx,
                 int activeVillains) {
        this.aggressor = aggressor;
        this.aggressorStats = aggressorStats;
        this.villainTier = villainTier;
        this.credibility = credibility;
        this.showdownBluffiness = showdownBluffiness;
        this.potOdds = potOdds;
        this.equityEst = equityEst;
        this.stackCtx = stackCtx;
        this.activeVillains = activeVillains;
    }
}

