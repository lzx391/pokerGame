package com.example.mgdemoplus.entity;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 玩家最近若干手牌的简单统计信息。
 * 仅用于高级机器人（如 BOT_Shark）根据对手风格做大致判断，不追求精确。
 */
public class PlayerStats {

    /**
     * 最近若干手的记录，长度控制在一个固定窗口（例如 12 手以内）。
     * 同时维护“长期统计”和“最近几手统计”，方便 BOT_Shark 既看整体风格又看短期变化。
     */
    private final Deque<SingleHandStats> recentHands = new ArrayDeque<>();

    // 累计统计：用于长期风格判断
    private int totalHands = 0;
    private int totalParticipated = 0;
    private int totalRaised = 0;
    private int totalShowdown = 0;

    /**
     * 每手牌的粗略统计。
     */
    public static class SingleHandStats {
        private boolean participated;   // 是否入池（有投入筹码且未直接弃牌）
        private boolean raised;        // 是否在这一手中主动加注过
        private boolean wentToShowdown;// 是否摊牌
        /**
         * 摊牌时最终牌型的大类（参考 HandStrength.rankCategory：10 皇家同花顺, 9 同花顺, ..., 1 高牌）。
         * 仅在 wentToShowdown = true 时有意义，用于 BOT_Shark 评估对手是否经常拿弱牌摊牌。
         */
        private int finalRankCategory;

        // === 位置/街道行为占位字段：用于后续更细粒度读牌 ===
        /**
         * 翻前是否曾主动开局加注（open raise）。
         * 目前由结算阶段基于本手行为快照进行填充，占位字段方便后续扩展。
         */
        private boolean preflopOpenRaise;

        /** flop 阶段是否有过主动下注或加注（bet/raise）。 */
        private boolean flopAggressive;

        /** turn 阶段是否有过主动下注或加注（bet/raise）。 */
        private boolean turnAggressive;

        /** river 阶段是否有过主动下注或加注（bet/raise）。 */
        private boolean riverAggressive;

        public boolean isParticipated() {
            return participated;
        }

        public void setParticipated(boolean participated) {
            this.participated = participated;
        }

        public boolean isRaised() {
            return raised;
        }

        public void setRaised(boolean raised) {
            this.raised = raised;
        }

        public boolean isWentToShowdown() {
            return wentToShowdown;
        }

        public void setWentToShowdown(boolean wentToShowdown) {
            this.wentToShowdown = wentToShowdown;
        }

        public int getFinalRankCategory() {
            return finalRankCategory;
        }

        public void setFinalRankCategory(int finalRankCategory) {
            this.finalRankCategory = finalRankCategory;
        }

        public boolean isPreflopOpenRaise() {
            return preflopOpenRaise;
        }

        public void setPreflopOpenRaise(boolean preflopOpenRaise) {
            this.preflopOpenRaise = preflopOpenRaise;
        }

        public boolean isFlopAggressive() {
            return flopAggressive;
        }

        public void setFlopAggressive(boolean flopAggressive) {
            this.flopAggressive = flopAggressive;
        }

        public boolean isTurnAggressive() {
            return turnAggressive;
        }

        public void setTurnAggressive(boolean turnAggressive) {
            this.turnAggressive = turnAggressive;
        }

        public boolean isRiverAggressive() {
            return riverAggressive;
        }

        public void setRiverAggressive(boolean riverAggressive) {
            this.riverAggressive = riverAggressive;
        }
    }

    /**
     * 添加一手牌的统计；只保留最近 windowSize 手。
     * 同时更新累计统计。
     */
    public void addHand(SingleHandStats hand, int windowSize) {
        if (recentHands.size() >= windowSize) {
            recentHands.pollFirst();
        }
        recentHands.addLast(hand);

        totalHands++;
        if (hand.isParticipated()) {
            totalParticipated++;
        }
        if (hand.isRaised()) {
            totalRaised++;
        }
        if (hand.isWentToShowdown()) {
            totalShowdown++;
        }
    }

    public Deque<SingleHandStats> getRecentHands() {
        return recentHands;
    }

    public int getSampleCount() {
        return recentHands.size();
    }

    /**
     * 最近若干手的进攻频率（简单定义为：有主动加注行为的手数 / 总样本手数）。
     * windowSize 为滑动窗口大小，超出时只统计最近的 windowSize 手。
     */
    public double getRecentAggressionRate(int windowSize) {
        if (recentHands.isEmpty() || windowSize <= 0) {
            return 0.0;
        }
        int count = 0;
        int raisedCount = 0;
        int processed = 0;
        for (SingleHandStats hand : recentHands) {
            if (processed >= windowSize) {
                break;
            }
            processed++;
            count++;
            if (hand.isRaised()) {
                raisedCount++;
            }
        }
        if (count == 0) return 0.0;
        return raisedCount * 1.0 / count;
    }

    /**
     * 最近若干手中，“亮出较弱摊牌牌力”的比例。
     * 简单视为：wentToShowdown==true 且 finalRankCategory <= 4 的手数 / 样本手数。
     */
    public double getRecentShowdownWeakRatio(int windowSize) {
        if (recentHands.isEmpty() || windowSize <= 0) {
            return 0.0;
        }
        int sample = 0;
        int weakCount = 0;
        int processed = 0;
        for (SingleHandStats hand : recentHands) {
            if (processed >= windowSize) {
                break;
            }
            processed++;
            if (!hand.isWentToShowdown()) {
                continue;
            }
            sample++;
            if (hand.getFinalRankCategory() > 0 && hand.getFinalRankCategory() <= 4) {
                weakCount++;
            }
        }
        if (sample == 0) return 0.0;
        return weakCount * 1.0 / sample;
    }

    // === 个体化长期调整：对特定玩家整体弃牌倾向的微调因子（占位实现） ===
    /**
     * 针对某个玩家整体的弃牌倾向微调值，可正可负。
     * 由结算逻辑在长期内根据该玩家摊牌习惯缓慢调整，范围通常裁剪在 [-0.3, 0.3]。
     */
    private double foldAdjustmentAgainstHero;

    public double getFoldAdjustmentAgainstHero() {
        return foldAdjustmentAgainstHero;
    }

    public void setFoldAdjustmentAgainstHero(double foldAdjustmentAgainstHero) {
        this.foldAdjustmentAgainstHero = foldAdjustmentAgainstHero;
    }

    // ===== 长期统计的一些便捷方法 =====

    public int getTotalHands() {
        return totalHands;
    }

    public double getOverallParticipationRate() {
        if (totalHands == 0) return 0.0;
        return totalParticipated * 1.0 / totalHands;
    }

    public double getOverallRaiseRate() {
        if (totalHands == 0) return 0.0;
        return totalRaised * 1.0 / totalHands;
    }
}

