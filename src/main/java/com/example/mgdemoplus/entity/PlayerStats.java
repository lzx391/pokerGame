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

