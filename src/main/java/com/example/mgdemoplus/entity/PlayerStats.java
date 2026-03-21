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
        /**
         * 公共牌（5 张）自身可组成的“最佳牌型大类”（同样使用 rankCategory 编码）。
         *
         * <p>用途：过滤“公共牌主导”的摊牌样本。若玩家最终 rankCategory 与 boardRankCategory 相同，
         * 说明这次摊牌对“玩家真实风格/范围”提供的信息较弱（很可能只是打公共牌）。</p>
         *
         * <p>仅在 wentToShowdown=true 时有意义。</p>
         */
        private int boardRankCategory;
        /**
         * 是否属于“公共牌主导”的摊牌：
         * - true：最终最佳 5 张牌完全由公共牌构成（没有使用任何手牌）。
         *
         * <p>注意：这比 “finalRankCategory == boardRankCategory” 更精确：
         * 即便牌型大类相同，只要使用了手牌 kicker，也能提供信息（例如空气 AK 摊牌仍会暴露风格）。</p>
         */
        private boolean boardDominantShowdown;

        /**
         * 摊牌时的“精确牌力值”（包含 kicker），用于精确比较大小。
         * 由结算阶段写入：{@code DpHandEvaluator.encodeStrengthValue(hs)}。
         */
        private long finalStrengthValue;

        /**
         * 是否在“Shark 也摊牌”的前提下，当前玩家摊牌牌力属于明显偏弱：
         * - 只认可高牌/一对（finalRankCategory <= 2）
         * - 且精确牌力严格小于 Shark（包含 kicker 比较）
         *
         * <p>用途：让 Shark 的学习统计更贴近你说的“弱牌摊牌 / 空气诈唬摊牌”。</p>
         */
        private boolean showdownWeakVsShark;

        /**
         * 本手摊牌是否存在“与 Shark 的精确比较样本”。
         * - true：Shark 与该玩家都去摊牌（且有评估结果），则可用 {@link #showdownWeakVsShark} 做统计
         * - false：说明本手不是“对 Shark 的摊牌”，不应混入 Shark 的 WEAK 样本
         */
        private boolean comparedToSharkAtShowdown;

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

        /**
         * 翻前是否以“只跟注未加注”的方式进池（limp）：
         * - limpedPreflop = true：本手曾在 preflop 只跟注入池，且从未 open-raise；
         * - 调用方在结算阶段根据本手下注轨迹推断并赋值。
         * 该标志用于识别典型 limp 玩家，便于 Shark 做 thin value 或更少尊重其 limp 线。
         */
        private boolean limpedPreflop;

        /**
         * 翻前是否有 open-raise 行为：
         * - openRaisedPreflop = true：本手中曾在无人加注前首先加注；
         * - 用于区分“主动开局者”与纯 limp / 跟注型玩家。
         */
        private boolean openRaisedPreflop;

        /**
         * 是否存在“前一街曾主动加注，转牌圈明显放弃”的模式：
         * - 由结算阶段根据各街下注记录进行粗略标记；
         * - 频繁为 true 时，说明该玩家经常只打一枪就放弃，Shark 可以多用 delayed bluff 或加大 turn 压力。
         */
        private boolean gaveUpTurnAfterRaise;

        /**
         * 是否存在“一直激进到 river，最终在 river 放弃”的模式：
         * - 频繁为 true 时，说明该玩家怕河牌完成的听牌或常在 river 收手；
         * - Shark 可以在 river 用更大尺度的 value bet 或合适的 bluff。
         */
        private boolean gaveUpRiverAfterRaise;

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

        public int getBoardRankCategory() {
            return boardRankCategory;
        }

        public void setBoardRankCategory(int boardRankCategory) {
            this.boardRankCategory = boardRankCategory;
        }

        public boolean isBoardDominantShowdown() {
            return boardDominantShowdown;
        }

        public void setBoardDominantShowdown(boolean boardDominantShowdown) {
            this.boardDominantShowdown = boardDominantShowdown;
        }

        public long getFinalStrengthValue() {
            return finalStrengthValue;
        }

        public void setFinalStrengthValue(long finalStrengthValue) {
            this.finalStrengthValue = finalStrengthValue;
        }

        public boolean isShowdownWeakVsShark() {
            return showdownWeakVsShark;
        }

        public void setShowdownWeakVsShark(boolean showdownWeakVsShark) {
            this.showdownWeakVsShark = showdownWeakVsShark;
        }

        public boolean isComparedToSharkAtShowdown() {
            return comparedToSharkAtShowdown;
        }

        public void setComparedToSharkAtShowdown(boolean comparedToSharkAtShowdown) {
            this.comparedToSharkAtShowdown = comparedToSharkAtShowdown;
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

        public boolean isLimpedPreflop() {
            return limpedPreflop;
        }

        public void setLimpedPreflop(boolean limpedPreflop) {
            this.limpedPreflop = limpedPreflop;
        }

        public boolean isOpenRaisedPreflop() {
            return openRaisedPreflop;
        }

        public void setOpenRaisedPreflop(boolean openRaisedPreflop) {
            this.openRaisedPreflop = openRaisedPreflop;
        }

        public boolean isGaveUpTurnAfterRaise() {
            return gaveUpTurnAfterRaise;
        }

        public void setGaveUpTurnAfterRaise(boolean gaveUpTurnAfterRaise) {
            this.gaveUpTurnAfterRaise = gaveUpTurnAfterRaise;
        }

        public boolean isGaveUpRiverAfterRaise() {
            return gaveUpRiverAfterRaise;
        }

        public void setGaveUpRiverAfterRaise(boolean gaveUpRiverAfterRaise) {
            this.gaveUpRiverAfterRaise = gaveUpRiverAfterRaise;
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
     *
     * <p>优化版（用于 Shark 学习）：</p>
     * <ul>
     *   <li>弱牌：仅统计到“一对”为止（finalRankCategory <= 2），不把两对/三条算作“弱”。</li>
     *   <li>过滤公共牌主导：若最终最佳 5 张牌完全不使用手牌（boardDominantShowdown==true），则该次摊牌不计入样本。</li>
     * </ul>
     */
    public double getRecentShowdownWeakRatio(int windowSize) {
        if (recentHands.isEmpty() || windowSize <= 0) {
            return 0.0;
        }
        ShowdownWeakStats s = getRecentShowdownWeakStats(windowSize);
        return s.sample == 0 ? 0.0 : (s.weakCount * 1.0 / s.sample);
    }

    /**
     * 给 Shark 学习调试用：返回弱摊牌比例的“分子/分母/过滤信息”。
     */
    public ShowdownWeakStats getRecentShowdownWeakStats(int windowSize) {
        ShowdownWeakStats out = new ShowdownWeakStats();
        if (recentHands.isEmpty() || windowSize <= 0) {
            return out;
        }
        int processed = 0;
        for (SingleHandStats hand : recentHands) {
            if (processed >= windowSize) break;
            processed++;
            if (!hand.isWentToShowdown()) continue;
            int finalCat = hand.getFinalRankCategory();
            if (finalCat <= 0) continue;
            if (hand.isBoardDominantShowdown()) {
                out.skippedBoardDominant++;
                continue;
            }
            // Shark 学习的 WEAK：只统计“对 Shark 有意义的样本”（双方都摊牌且可比较）
            if (!hand.isComparedToSharkAtShowdown()) {
                out.skippedNotVsShark++;
                continue;
            }
            out.sample++;
            if (hand.isShowdownWeakVsShark()) {
                out.weakCount++;
            }
        }
        return out;
    }

    public static final class ShowdownWeakStats {
        public int sample;
        public int weakCount;
        public int skippedBoardDominant;
        public int skippedNotVsShark;

        public double ratio() {
            return sample <= 0 ? 0.0 : (weakCount * 1.0 / sample);
        }
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

