package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.PlayerStats;
import java.util.List;

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

    /**
     * multi-way 相关扩展数据：
     * - multiwayVillains：当前牌局中其它未弃牌玩家的简要信息列表；
     * - tightBehindCount：在 hero 后位且整体风格为 NIT/TIGHT 的人数；
     * - deepBehindCount：在 hero 后位且筹码深度为深码的玩家数；
     * - shortBehindCount：在 hero 后位且筹码 ≤ 典型短码阈值的玩家数。
     * 这些指标用于 Shark 在多人底池下更精细地控制 bluff/value 尺度。
     */
    final List<DpNpcEngine.MultiwayVillainInfo> multiwayVillains;
    final int tightBehindCount;
    final int deepBehindCount;
    final int shortBehindCount;

    /**
     * 针对主要对手的 counter-strategy 建议：由 DpNpcEngine.analyzeVillainCounterStrategy 生成。
     * 包含是否更适合 thin value、call down、对大注多弃牌、减少 bluff 等方向。
     */
    final DpNpcEngine.CounterStrategyProfile counterStrategy;

    SmartContext(DpPlayer aggressor,
                 PlayerStats aggressorStats,
                 DpNpcEngine.VillainRangeTier villainTier,
                 DpNpcEngine.ActionCredibility credibility,
                 double showdownBluffiness,
                 double potOdds,
                 double equityEst,
                 DpNpcEngine.StackContext stackCtx,
                 int activeVillains,
                 List<DpNpcEngine.MultiwayVillainInfo> multiwayVillains,
                 int tightBehindCount,
                 int deepBehindCount,
                 int shortBehindCount,
                 DpNpcEngine.CounterStrategyProfile counterStrategy) {
        this.aggressor = aggressor;
        this.aggressorStats = aggressorStats;
        this.villainTier = villainTier;
        this.credibility = credibility;
        this.showdownBluffiness = showdownBluffiness;
        this.potOdds = potOdds;
        this.equityEst = equityEst;
        this.stackCtx = stackCtx;
        this.activeVillains = activeVillains;
        this.multiwayVillains = multiwayVillains;
        this.tightBehindCount = tightBehindCount;
        this.deepBehindCount = deepBehindCount;
        this.shortBehindCount = shortBehindCount;
        this.counterStrategy = counterStrategy;
    }
}

