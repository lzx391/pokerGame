package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.service.serviceImpl.dp.npc.LlmNpcGameContext;
import com.example.mgdemoplus.utils.dp.DpUtilSmartContext;
import com.example.mgdemoplus.utils.dp.DpUtilHandEvaluator.SimpleStrength;

import java.util.List;

/**
 * 将 {@link DpUtilSmartContext} 与房间/英雄状态打成 {@link LlmNpcGameContext}，
 * 供 {@code npc} 包中的 {@link com.example.mgdemoplus.service.serviceImpl.dp.npc.LlmNpc} 使用。
 */
public final class DpLlmNpcContextMapper {

    private DpLlmNpcContextMapper() {
    }

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

        return new LlmNpcGameContext(//参数传入
                st,
                pot,
                callAmount,
                heroChips,
                heroNick,
                community,
                holes,
                pos,
                str,
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
                counter);
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
                    .append(" 本街加注=").append(v.hasRaisedThisStreet)
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
