package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.ActionCredibility;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.eval.DpBoardTexture;
import com.example.mgdemoplus.npc.eval.DpNpcCategoryLabels;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

import java.util.Map;

/** TAG 翻后 trace：POSTFLOP_SPOT data 组装与 message 摘要。 */
public final class DpNpcPostflopTraceSupport {

    private DpNpcPostflopTraceSupport() {
    }

    public static String postflopSpotMessage(
            String stage,
            int callAmount,
            HandPlanType plan,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex) {
        String madeLabel = DpNpcCategoryLabels.madeZh(made);
        String drawLabel = DpNpcCategoryLabels.drawZh(draw);
        String texLabel = tex != null && tex.wet ? "湿面" : "干面";
        String planName = plan != null ? plan.name() : "";
        return stage + " 需跟注 " + callAmount + "，计划 " + planName
                + "，" + madeLabel + "·" + drawLabel + "·" + texLabel;
    }

    public static Map<String, Object> postflopSpotData(
            String stage,
            int callAmount,
            HandPlanType plan,
            DpNpcMadeHandCategory made,
            DpNpcDrawCategory draw,
            DpBoardTexture tex,
            BoardDanger boardDanger,
            DpNpcRuleDecisionParams p,
            DpUtilSmartContext ctx,
            double commitFactor,
            double commitThreshold) {
        DpRoomBO room = p != null ? p.room : null;
        DpPlayer bot = p != null ? p.bot : null;
        double spr = room != null && bot != null ? DpNpcEngine.computeHeroPotSpr(room, bot) : 0.0;
        double effStackBB = 0.0;
        if (ctx != null && ctx.stackCtx != null) {
            effStackBB = ctx.stackCtx.avgStackBB;
        }
        String aggressor = "";
        if (ctx != null && ctx.aggressor != null && ctx.aggressor.getNickname() != null) {
            aggressor = ctx.aggressor.getNickname();
        }
        ActionCredibility cred = ctx != null ? ctx.credibility : null;
        return DpNpcTagDecisionTraceCollector.dataOf(
                "stage", stage != null ? stage : "",
                "callAmount", callAmount,
                "plan", plan != null ? plan.name() : "",
                "made", made != null ? made.name() : "",
                "madeLabel", DpNpcCategoryLabels.madeZh(made),
                "draw", draw != null ? draw.name() : "",
                "drawLabel", DpNpcCategoryLabels.drawZh(draw),
                "tex", tex != null ? (tex.wet ? "wet" : "dry") : "",
                "boardDanger", boardDanger != null ? boardDanger.name() : "",
                "position", p != null && p.position != null ? p.position.name() : "",
                "pot", room != null ? room.getPot() : 0,
                "heroChips", bot != null ? bot.getChips() : 0,
                "spr", spr,
                "effStackBB", effStackBB,
                "callRatio", p != null ? p.callRatio : 0.0,
                "equityEst", ctx != null ? ctx.equityEst : 0.0,
                "potOdds", ctx != null ? ctx.potOdds : 0.0,
                "maxBarrels", bot != null ? bot.getNpcHandPlanMaxBarrels() : 0,
                "planAggression", bot != null ? bot.getNpcHandPlanAggression() : 0.0,
                "activeVillains", ctx != null ? ctx.activeVillains : 0,
                "aggressor", aggressor,
                "credibility", cred != null ? cred.name() : "",
                "commitFactor", commitFactor,
                "commitThreshold", commitThreshold);
    }
}
