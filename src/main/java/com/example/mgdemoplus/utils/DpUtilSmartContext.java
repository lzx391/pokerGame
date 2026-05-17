package com.example.mgdemoplus.utils;

import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.entity.DpPlayerStats;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.CounterStrategyProfile;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.MultiwayVillainInfo;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.StackContext;
import com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.VillainRangeTier;

import java.util.List;

/**
 * 德扑 NPC 的统一“聪明决策上下文”数据结构。
 * 仅作为承载数据的简单对象，构建逻辑由 {@link DpNpcEngine} 内部负责。
 */
public final class DpUtilSmartContext {
    public final DpPlayer aggressor;            // 当前这轮的主要进攻者（最大 bet 且不是 hero）
    public final DpPlayerStats aggressorStats;    // 该玩家的统计（可为 null）
    public final DpNpcEngine.VillainRangeTier villainTier;  // 对手整体风格
    public final DpNpcEngine.ActionCredibility credibility; // 当前动作可信度（偏 value 还是偏 bluff）
   public final double showdownBluffiness;     // 最近摊牌 bluff 倾向 [0,1]
   public final double potOdds;                // 当前跟注的底池赔率 [0,1]
   public final double equityEst;              // 粗略赢率桶 [0,1]
   public final DpNpcEngine.StackContext stackCtx;         // 筹码深度上下文
    public final int activeVillains;           // hero 之外还在这手牌里的玩家数

    /**
     * multi-way 相关扩展数据：
     * - multiwayVillains：当前牌局中其它未弃牌玩家的简要信息列表；
     * - tightBehindCount：在 hero 后位且整体风格为 NIT/TIGHT 的人数；
     * - deepBehindCount：在 hero 后位且筹码深度为深码的玩家数；
     * - shortBehindCount：在 hero 后位且筹码 ≤ 典型短码阈值的玩家数。
     * 这些指标用于 Shark 在多人底池下更精细地控制 bluff/value 尺度。
     */
   public final List<DpNpcEngine.MultiwayVillainInfo> multiwayVillains;
    public final int tightBehindCount;
    public final int deepBehindCount;
    public final int shortBehindCount;

    /**
     * 针对主要对手的 counter-strategy 建议：由 DpNpcEngine.analyzeVillainCounterStrategy 生成。
     * 包含是否更适合 thin value、call down、对大注多弃牌、减少 bluff 等方向。
     */
    public final DpNpcEngine.CounterStrategyProfile counterStrategy;

    public DpUtilSmartContext(DpPlayer aggressor,
                       DpPlayerStats aggressorStats,
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

