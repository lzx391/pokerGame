package com.example.mgdemoplus.service.serviceImpl;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator.HandStrength;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator.SimpleStrength;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BOT_LLM 局面信息包：扁平字段 + 从 {@link DpUtilSmartContext} 与房间状态映射构造。
 * 局面包正文由 {@link LlmNpcUserSnapshot} 统一格式化。
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
    /** 身后激进短码等对「平跟后被挤压」风险的简述（服务端推算）。 */
    private final String squeezeRiskSummary;

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
            String counterStrategySummary,
            String squeezeRiskSummary) {
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
        this.squeezeRiskSummary = squeezeRiskSummary != null ? squeezeRiskSummary : "";
    }

    /** 将引擎上下文映射为 LLM 可直接消费的扁平数据对象。 */
    public static LlmNpcGameContext map(
            DpRoomBO room,
            DpPlayer hero,
            DpUtilSmartContext ctx,
            String stage,
            int callAmount,
            SimpleStrength strength,
            DpNpcEngine.TablePosition position) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx");
        }
        String st = stage != null ? stage : "";
        int pot = room != null ? room.getPot() : 0;
        String community = formatCards(room != null ? room.getCommunityCards() : null);
        String holes = formatCards(hero != null ? hero.getHoleCards() : null);
        String heroNick = hero != null ? hero.getNickname() : "";
        int heroChips = hero != null ? hero.getChips() : 0;
        String pos = position != null ? position.name() : "";
        String str = strength != null ? strength.name() : "";
        String hsl = resolveHandStrengthLineForLlm(room, hero, st);

        String aggressor = "";
        if (ctx.aggressor != null) {
            aggressor = ctx.aggressor.getNickname() != null ? ctx.aggressor.getNickname() : "";
        }
        String tier = ctx.villainTier != null ? ctx.villainTier.name() : "";
        String cred = ctx.credibility != null ? ctx.credibility.name() : "";

        var stack = ctx.stackCtx;
        int vmin = stack != null ? stack.minStack : 0;
        int vmax = stack != null ? stack.maxStack : 0;
        int vavg = stack != null ? stack.avgStack : 0;
        double vminBb = stack != null ? stack.minStackBB : 0;
        double vmaxBb = stack != null ? stack.maxStackBB : 0;
        double vavgBb = stack != null ? stack.avgStackBB : 0;

        String multi = summarizeMultiway(ctx.multiwayVillains);
        String counter = summarizeCounter(ctx.counterStrategy);
        String squeeze =
                buildSqueezeRiskSummary(callAmount, room != null ? room.getBigBlindChips() : 0, ctx.multiwayVillains);

        return new LlmNpcGameContext(
                st,
                pot,
                callAmount,
                heroChips,
                heroNick,
                community,
                holes,
                pos,
                str,
                hsl,
                aggressor,
                tier,
                cred,
                ctx.showdownBluffiness,
                ctx.potOdds,
                ctx.equityEst,
                vmin,
                vmax,
                vavg,
                vminBb,
                vmaxBb,
                vavgBb,
                ctx.activeVillains,
                multi,
                ctx.tightBehindCount,
                ctx.deepBehindCount,
                ctx.shortBehindCount,
                counter,
                squeeze);
    }

    private static String buildSqueezeRiskSummary(
            int callAmount, int bigBlindChips, List<DpNpcEngine.MultiwayVillainInfo> list) {
        int bb = Math.max(1, bigBlindChips);
        double shortBb = DpNpcEngine.RuleNpcConfig.SHORT_STACK_BB;
        double deepBb = DpNpcEngine.RuleNpcConfig.DEEP_STACK_MIN_BB;
        if (callAmount <= 0) {
            return "不适用（本轮你还须支付为 0：无「跟注后被身后加注」问题）。";
        }
        if (list == null || list.isEmpty()) {
            return "低（无多路摘要）。";
        }
        List<String> aggShortNicks = new ArrayList<>();
        int aggShortCnt = 0;
        int nitDeepBehind = 0;
        for (DpNpcEngine.MultiwayVillainInfo v : list) {
            if (v == null || !v.behindHero) {
                continue;
            }
            boolean agg = v.tier == DpNpcEngine.VillainRangeTier.MANIAC
                    || v.tier == DpNpcEngine.VillainRangeTier.LOOSE;
            boolean shortStack = v.stackBB > 0 && v.stackBB <= shortBb;
            if (agg && shortStack) {
                aggShortCnt++;
                if (v.nickname != null && !v.nickname.isBlank()) {
                    aggShortNicks.add(v.nickname);
                }
            }
            if ((v.tier == DpNpcEngine.VillainRangeTier.NIT || v.tier == DpNpcEngine.VillainRangeTier.TIGHT)
                    && v.stackBB >= deepBb) {
                nitDeepBehind++;
            }
        }
        if (aggShortCnt > 0) {
            String names = aggShortNicks.isEmpty() ? "（见多路摘要）" : String.join("、", aggShortNicks);
            return String.format(
                    "高｜身后尚有约≤%.0fBB 的激进短码共%d人，可能在你平跟后继续加注或全下：%s。边缘牌慎纯跟注，可考虑弃牌或以大块加注夺回主动权。",
                    shortBb, aggShortCnt, names);
        }
        if (nitDeepBehind > 0 && callAmount >= 5 * bb) {
            return "中｜身后有紧凶深码，面对较大跟注可能被冷加注挤压；中等牌力避免勉强跟注。";
        }
        return "低｜身后未见显著「激进+短码」组合（仍有被加注的可能，勿理解为零风险）。";
    }

    private static String resolveHandStrengthLineForLlm(DpRoomBO room, DpPlayer hero, String stage) {
        String stg = stage != null ? stage : "";
        if ("preflop".equals(stg)) {
            return "PREFLOP";
        }
        List<String> hole = hero != null ? hero.getHoleCards() : null;
        List<String> comm = room != null ? room.getCommunityCards() : null;
        List<String> all = new ArrayList<>(6);
        if (hole != null) {
            all.addAll(hole);
        }
        if (comm != null) {
            all.addAll(comm);
        }
        if (all.size() < 5) {
            return "PREFLOP_OR_INCOMPLETE";
        }
        HandStrength hs = DpUtilHandEvaluator.evaluateBestHand(all);
        return DpUtilHandEvaluator.describeHandStrengthForLlm(hs);
    }

    private static String formatCards(List<String> cards) {
        if (cards == null || cards.isEmpty()) {
            return "";
        }
        return String.join(" ", cards);
    }

    private static String summarizeMultiway(List<DpNpcEngine.MultiwayVillainInfo> list) {
        if (list == null || list.isEmpty()) {
            return "(无)";
        }
        StringBuilder sb = new StringBuilder();
        for (DpNpcEngine.MultiwayVillainInfo v : list) {
            if (v == null) {
                continue;
            }
            String nick = v.nickname != null ? v.nickname : "?";
            String tier = v.tier != null ? v.tier.name() : "";
            sb.append("- ")
                    .append(nick)
                    .append(" 后位=").append(v.behindHero)
                    .append(" stackBB=").append(String.format("%.1f", v.stackBB))
                    .append(" 风格=").append(tier)
                    .append(" 已加注本街=").append(v.hasRaisedThisStreet)
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private static String summarizeCounter(DpNpcEngine.CounterStrategyProfile c) {
        if (c == null) {
            return "NEUTRAL";
        }
        StringBuilder sb = new StringBuilder();
        if (c.moreThinValue) {
            sb.append("thinValue ");
        }
        if (c.callDownMore) {
            sb.append("callDown ");
        }
        if (c.foldMoreToBigBets) {
            sb.append("foldToBig ");
        }
        if (c.bluffLess) {
            sb.append("bluffLess ");
        }
        if (c.bluffCatchMore) {
            sb.append("bluffCatch ");
        }
        if (sb.length() == 0) {
            return "NEUTRAL";
        }
        return sb.toString().trim();
    }

    public String getHoleCardsText() {
        return holeCardsText;
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

    public String getTablePosition() {
        return tablePosition;
    }

    public String getSimpleStrength() {
        return simpleStrength;
    }

    public String getHandStrengthLine() {
        return handStrengthLine;
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

    public String getSqueezeRiskSummary() {
        return squeezeRiskSummary;
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
                && Objects.equals(handStrengthLine, that.handStrengthLine)
                && Objects.equals(aggressorNickname, that.aggressorNickname)
                && Objects.equals(villainRangeTier, that.villainRangeTier)
                && Objects.equals(actionCredibility, that.actionCredibility)
                && Objects.equals(multiwayVillainsSummary, that.multiwayVillainsSummary)
                && Objects.equals(counterStrategySummary, that.counterStrategySummary)
                && Objects.equals(squeezeRiskSummary, that.squeezeRiskSummary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                stage,
                potChips,
                callAmountChips,
                heroChips,
                heroNickname,
                communityCardsText,
                holeCardsText,
                tablePosition,
                simpleStrength,
                handStrengthLine,
                aggressorNickname,
                villainRangeTier,
                actionCredibility,
                showdownBluffiness,
                potOdds,
                equityEstimate,
                villainMinStackChips,
                villainMaxStackChips,
                villainAvgStackChips,
                villainMinStackBb,
                villainMaxStackBb,
                villainAvgStackBb,
                activeVillains,
                multiwayVillainsSummary,
                tightBehindCount,
                deepBehindCount,
                shortBehindCount,
                counterStrategySummary,
                squeezeRiskSummary);
    }
}
