package com.example.mgdemoplus.service.serviceImpl;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.service.serviceImpl.npc.LlmNpcGameContext;
import com.example.mgdemoplus.utils.DpUtilSmartContext;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator.HandStrength;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator.SimpleStrength;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 {@link DpUtilSmartContext} 与房间/英雄状态打成 {@link LlmNpcGameContext}，
 * 供 {@code npc} 包中的 {@link com.example.mgdemoplus.service.serviceImpl.npc.LlmNpc} 使用。
 */
public final class DpLlmNpcContextMapper {

    private DpLlmNpcContextMapper() {
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
        String squeeze = buildSqueezeRiskSummary(callAmount, room != null ? room.getBigBlindChips() : 0,
                ctx.multiwayVillains);

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

    /**
     * 身后激进短码「跟注后被挤压/全下」风险摘要，供 BOT_LLM 与赔率一并阅读。
     */
    private static String buildSqueezeRiskSummary(int callAmount, int bigBlindChips,
            List<DpNpcEngine.MultiwayVillainInfo> list) {
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

    /**
     * 与 {@link DpUtilHandEvaluator#evaluateBestHand(java.util.List)} 一致：翻前或无足够张数时不算五张成牌。
     */
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
}
