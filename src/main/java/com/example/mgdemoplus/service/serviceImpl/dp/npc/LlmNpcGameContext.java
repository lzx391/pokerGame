package com.example.mgdemoplus.service.serviceImpl.dp.npc;

import java.util.Locale;
import java.util.Objects;

/**
 * 供 {@link LlmNpc} 使用的对局摘要：由 {@code DpLlmNpcContextMapper}
 * 从 {@code DpUtilSmartContext} 与房间状态拼装，避免 {@code npc} 包直接依赖引擎内部类型。
 */
public final class LlmNpcGameContext {

    private final String stage;
    private final int potChips;
    private final int callAmountChips;
    private final int heroChips;
    private final String heroNickname;
    private final String communityCardsText;
    private final String holeCardsText;
    private final String tablePosition;
    private final String simpleStrength;
    /** 服务器从七张牌算出的最佳成牌紧凑标签（英文 token），与大模型自检结果冲突时以此为真。 */
    private final String handStrengthLine;
    private final String aggressorNickname;
    private final String villainRangeTier;
    private final String actionCredibility;
    private final double showdownBluffiness;
    private final double potOdds;
    private final double equityEstimate;
    private final int villainMinStackChips;
    private final int villainMaxStackChips;
    private final int villainAvgStackChips;
    private final double villainMinStackBb;
    private final double villainMaxStackBb;
    private final double villainAvgStackBb;
    private final int activeVillains;
    private final String multiwayVillainsSummary;
    private final int tightBehindCount;
    private final int deepBehindCount;
    private final int shortBehindCount;
    private final String counterStrategySummary;

    /**
     * 构造函数，初始化所有字段
     */
    public LlmNpcGameContext(
            String stage,
            int potChips,
            int callAmountChips,
            int heroChips,
            String heroNickname,
            String communityCardsText,
            String holeCardsText,
            String tablePosition,
            String simpleStrength,
            String handStrengthLine,
            String aggressorNickname,
            String villainRangeTier,
            String actionCredibility,
            double showdownBluffiness,
            double potOdds,
            double equityEstimate,
            int villainMinStackChips,
            int villainMaxStackChips,
            int villainAvgStackChips,
            double villainMinStackBb,
            double villainMaxStackBb,
            double villainAvgStackBb,
            int activeVillains,
            String multiwayVillainsSummary,
            int tightBehindCount,
            int deepBehindCount,
            int shortBehindCount,
            String counterStrategySummary) {
        this.stage = stage != null ? stage : "";
        this.potChips = potChips;
        this.callAmountChips = callAmountChips;
        this.heroChips = heroChips;
        this.heroNickname = heroNickname != null ? heroNickname : "";
        this.communityCardsText = communityCardsText != null ? communityCardsText : "";
        this.holeCardsText = holeCardsText != null ? holeCardsText : "";
        this.tablePosition = tablePosition != null ? tablePosition : "";
        this.simpleStrength = simpleStrength != null ? simpleStrength : "";
        this.handStrengthLine = handStrengthLine != null ? handStrengthLine : "";
        this.aggressorNickname = aggressorNickname != null ? aggressorNickname : "";
        this.villainRangeTier = villainRangeTier != null ? villainRangeTier : "";
        this.actionCredibility = actionCredibility != null ? actionCredibility : "";
        this.showdownBluffiness = showdownBluffiness;
        this.potOdds = potOdds;
        this.equityEstimate = equityEstimate;
        this.villainMinStackChips = villainMinStackChips;
        this.villainMaxStackChips = villainMaxStackChips;
        this.villainAvgStackChips = villainAvgStackChips;
        this.villainMinStackBb = villainMinStackBb;
        this.villainMaxStackBb = villainMaxStackBb;
        this.villainAvgStackBb = villainAvgStackBb;
        this.activeVillains = activeVillains;
        this.multiwayVillainsSummary = multiwayVillainsSummary != null ? multiwayVillainsSummary : "";
        this.tightBehindCount = tightBehindCount;
        this.deepBehindCount = deepBehindCount;
        this.shortBehindCount = shortBehindCount;
        this.counterStrategySummary = counterStrategySummary != null ? counterStrategySummary : "";
    }

    /**
     * 获取当前牌局阶段（如preflop/flop/turn/river）
     */
    public String getStage() {
        return stage;
    }

    /**
     * 获取底池筹码数
     */
    public int getPotChips() {
        return potChips;
    }

    /**
     * 获取当前需要call的筹码数
     */
    public int getCallAmountChips() {
        return callAmountChips;
    }

    /**
     * 获取hero（我方）持有筹码数
     */
    public int getHeroChips() {
        return heroChips;
    }

    /**
     * 获取hero（我方）昵称
     */
    public String getHeroNickname() {
        return heroNickname;
    }

    /**
     * 获取当前公共牌文本
     */
    public String getCommunityCardsText() {
        return communityCardsText;
    }

    /**
     * 获取hero手牌文本
     */
    public String getHoleCardsText() {
        return holeCardsText;
    }

    /**
     * 获取桌上hero的位置
     */
    public String getTablePosition() {
        return tablePosition;
    }

    /**
     * 获取简化后的牌力描述
     */
    public String getSimpleStrength() {
        return simpleStrength;
    }

    /**
     * 服务器侧最佳五张牌牌型（英文紧凑标签），供 LLM 作为成牌事实依据。
     */
    public String getHandStrengthLine() {
        return handStrengthLine;
    }

    /**
     * 获取最新激进下注者昵称
     */
    public String getAggressorNickname() {
        return aggressorNickname;
    }

    /**
     * 获取主对手的范围强弱tier
     */
    public String getVillainRangeTier() {
        return villainRangeTier;
    }

    /**
     * 获取行为可信度描述
     */
    public String getActionCredibility() {
        return actionCredibility;
    }

    /**
     * 获取摊牌唬牌偏好（数值）
     */
    public double getShowdownBluffiness() {
        return showdownBluffiness;
    }

    /**
     * 获取底池赔率
     */
    public double getPotOdds() {
        return potOdds;
    }

    /**
     * 获取胜率估算
     */
    public double getEquityEstimate() {
        return equityEstimate;
    }

    /**
     * 获取主对手最小筹码量（小盲筹码）
     */
    public int getVillainMinStackChips() {
        return villainMinStackChips;
    }

    /**
     * 获取主对手最大筹码量（小盲筹码）
     */
    public int getVillainMaxStackChips() {
        return villainMaxStackChips;
    }

    /**
     * 获取主对手平均筹码量（小盲筹码）
     */
    public int getVillainAvgStackChips() {
        return villainAvgStackChips;
    }

    /**
     * 获取主对手最小筹码量（以BB为单位）
     */
    public double getVillainMinStackBb() {
        return villainMinStackBb;
    }

    /**
     * 获取主对手最大筹码量（以BB为单位）
     */
    public double getVillainMaxStackBb() {
        return villainMaxStackBb;
    }

    /**
     * 获取主对手平均筹码量（以BB为单位）
     */
    public double getVillainAvgStackBb() {
        return villainAvgStackBb;
    }

    /**
     * 获取当前场上活跃对手人数
     */
    public int getActiveVillains() {
        return activeVillains;
    }

    /**
     * 获取多路对手摘要描述
     */
    public String getMultiwayVillainsSummary() {
        return multiwayVillainsSummary;
    }

    /**
     * 获取身后“紧”玩家数量
     */
    public int getTightBehindCount() {
        return tightBehindCount;
    }

    /**
     * 获取身后“深筹”玩家数量
     */
    public int getDeepBehindCount() {
        return deepBehindCount;
    }

    /**
     * 获取身后“短筹”玩家数量
     */
    public int getShortBehindCount() {
        return shortBehindCount;
    }

    /**
     * 获取反制策略简述
     */
    public String getCounterStrategySummary() {
        return counterStrategySummary;
    }

    /**
     * 拼接出用于大模型 prompt 的局面简要文本
     * 即塞进大模型 user 的紧凑局面块（英文键、无长中文），显著比早期版本省 token。
     * {@code hsl=} 行为服务端 {@link com.example.mgdemoplus.utils.dp.DpUtilHandEvaluator#describeHandStrengthForLlm} 生成的最佳五张牌标签。
     * 多人逐行明细、对手 min/max/avg 筹码等已省略——桌上列表已在 {@code DpLlmNpcDecisionService} 里提供。
     * @return 简化的对局字符串
     */
    public String toPromptBlock() {
        String agg = aggressorNickname.isEmpty() ? "-" : aggressorNickname;
        String ctr = counterStrategySummary.isEmpty() ? "-" : counterStrategySummary.replace(' ', '_');
        // board= 即公共牌；翻前无牌时写 "-"，避免看成漏字段
        String board = communityCardsText == null || communityCardsText.isEmpty() ? "-" : communityCardsText;
        String hole = holeCardsText == null || holeCardsText.isEmpty() ? "-" : holeCardsText;
        String hsl = handStrengthLine == null || handStrengthLine.isEmpty() ? "-" : handStrengthLine;
        return String.format(
                Locale.ROOT,
                "H n=%s st=%s pot=%d call=%d stk=%d pos=%s rk=%s hsl=%s hole=%s board=%s\n"
                        + "E agg=%s vt=%s cr=%s sb=%.2f po=%.2f eq=%.2f nv=%d tds=%d/%d/%d ctr=%s\n",
                heroNickname,
                stage,
                potChips,
                callAmountChips,
                heroChips,
                tablePosition,
                simpleStrength,
                hsl,
                hole,
                board,
                agg,
                villainRangeTier,
                actionCredibility,
                showdownBluffiness,
                potOdds,
                equityEstimate,
                activeVillains,
                tightBehindCount,
                deepBehindCount,
                shortBehindCount,
                ctr);
    }

    /**
     * 判断两个对象的字段值是否全部相等
     * @param o 传入的比较对象
     * @return 相等返回true，否则false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LlmNpcGameContext that = (LlmNpcGameContext) o;
        return potChips == that.potChips
                && callAmountChips == that.callAmountChips
                && heroChips == that.heroChips
                && Double.compare(that.showdownBluffiness, showdownBluffiness) == 0
                && Double.compare(that.potOdds, potOdds) == 0
                && Double.compare(that.equityEstimate, equityEstimate) == 0
                && villainMinStackChips == that.villainMinStackChips
                && villainMaxStackChips == that.villainMaxStackChips
                && villainAvgStackChips == that.villainAvgStackChips
                && Double.compare(that.villainMinStackBb, villainMinStackBb) == 0
                && Double.compare(that.villainMaxStackBb, villainMaxStackBb) == 0
                && Double.compare(that.villainAvgStackBb, villainAvgStackBb) == 0
                && activeVillains == that.activeVillains
                && tightBehindCount == that.tightBehindCount
                && deepBehindCount == that.deepBehindCount
                && shortBehindCount == that.shortBehindCount
                && Objects.equals(stage, that.stage)
                && Objects.equals(heroNickname, that.heroNickname)
                && Objects.equals(communityCardsText, that.communityCardsText)
                && Objects.equals(holeCardsText, that.holeCardsText)
                && Objects.equals(tablePosition, that.tablePosition)
                && Objects.equals(simpleStrength, that.simpleStrength)
                && Objects.equals(handStrengthLine, that.handStrengthLine)
                && Objects.equals(aggressorNickname, that.aggressorNickname)
                && Objects.equals(villainRangeTier, that.villainRangeTier)
                && Objects.equals(actionCredibility, that.actionCredibility)
                && Objects.equals(multiwayVillainsSummary, that.multiwayVillainsSummary)
                && Objects.equals(counterStrategySummary, that.counterStrategySummary);
    }

    /**
     * 计算对象的hash值，便于作为key存储等
     * @return hash值
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                stage, potChips, callAmountChips, heroChips, heroNickname,
                communityCardsText, holeCardsText, tablePosition, simpleStrength, handStrengthLine,
                aggressorNickname, villainRangeTier, actionCredibility,
                showdownBluffiness, potOdds, equityEstimate,
                villainMinStackChips, villainMaxStackChips, villainAvgStackChips,
                villainMinStackBb, villainMaxStackBb, villainAvgStackBb,
                activeVillains, multiwayVillainsSummary,
                tightBehindCount, deepBehindCount, shortBehindCount,
                counterStrategySummary);
    }
}
