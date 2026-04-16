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

    public String getStage() {
        return stage;
    }

    public int getPotChips() {
        return potChips;
    }

    public int getCallAmountChips() {
        return callAmountChips;
    }

    public int getHeroChips() {
        return heroChips;
    }

    public String getHeroNickname() {
        return heroNickname;
    }

    public String getCommunityCardsText() {
        return communityCardsText;
    }

    public String getHoleCardsText() {
        return holeCardsText;
    }

    public String getTablePosition() {
        return tablePosition;
    }

    public String getSimpleStrength() {
        return simpleStrength;
    }

    public String getAggressorNickname() {
        return aggressorNickname;
    }

    public String getVillainRangeTier() {
        return villainRangeTier;
    }

    public String getActionCredibility() {
        return actionCredibility;
    }

    public double getShowdownBluffiness() {
        return showdownBluffiness;
    }

    public double getPotOdds() {
        return potOdds;
    }

    public double getEquityEstimate() {
        return equityEstimate;
    }

    public int getVillainMinStackChips() {
        return villainMinStackChips;
    }

    public int getVillainMaxStackChips() {
        return villainMaxStackChips;
    }

    public int getVillainAvgStackChips() {
        return villainAvgStackChips;
    }

    public double getVillainMinStackBb() {
        return villainMinStackBb;
    }

    public double getVillainMaxStackBb() {
        return villainMaxStackBb;
    }

    public double getVillainAvgStackBb() {
        return villainAvgStackBb;
    }

    public int getActiveVillains() {
        return activeVillains;
    }

    public String getMultiwayVillainsSummary() {
        return multiwayVillainsSummary;
    }

    public int getTightBehindCount() {
        return tightBehindCount;
    }

    public int getDeepBehindCount() {
        return deepBehindCount;
    }

    public int getShortBehindCount() {
        return shortBehindCount;
    }

    public String getCounterStrategySummary() {
        return counterStrategySummary;
    }

    /**
     * 塞进大模型 user 的紧凑局面块（英文键、无长中文），显著比早期版本省 token。
     * 多人逐行明细、对手 min/max/avg 筹码等已省略——桌上列表已在 {@code DpLlmNpcDecisionService} 里提供。
     */
    public String toPromptBlock() {
        String agg = aggressorNickname.isEmpty() ? "-" : aggressorNickname;
        String ctr = counterStrategySummary.isEmpty() ? "-" : counterStrategySummary.replace(' ', '_');
        // board= 即公共牌；翻前无牌时写 "-"，避免看成漏字段
        String board = communityCardsText == null || communityCardsText.isEmpty() ? "-" : communityCardsText;
        String hole = holeCardsText == null || holeCardsText.isEmpty() ? "-" : holeCardsText;
        return String.format(
                Locale.ROOT,
                "H n=%s st=%s pot=%d call=%d stk=%d pos=%s rk=%s hole=%s board=%s\n"
                        + "E agg=%s vt=%s cr=%s sb=%.2f po=%.2f eq=%.2f nv=%d tds=%d/%d/%d ctr=%s\n",
                heroNickname,
                stage,
                potChips,
                callAmountChips,
                heroChips,
                tablePosition,
                simpleStrength,
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
                && Objects.equals(aggressorNickname, that.aggressorNickname)
                && Objects.equals(villainRangeTier, that.villainRangeTier)
                && Objects.equals(actionCredibility, that.actionCredibility)
                && Objects.equals(multiwayVillainsSummary, that.multiwayVillainsSummary)
                && Objects.equals(counterStrategySummary, that.counterStrategySummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                stage, potChips, callAmountChips, heroChips, heroNickname,
                communityCardsText, holeCardsText, tablePosition, simpleStrength,
                aggressorNickname, villainRangeTier, actionCredibility,
                showdownBluffiness, potOdds, equityEstimate,
                villainMinStackChips, villainMaxStackChips, villainAvgStackChips,
                villainMinStackBb, villainMaxStackBb, villainAvgStackBb,
                activeVillains, multiwayVillainsSummary,
                tightBehindCount, deepBehindCount, shortBehindCount,
                counterStrategySummary);
    }
}
