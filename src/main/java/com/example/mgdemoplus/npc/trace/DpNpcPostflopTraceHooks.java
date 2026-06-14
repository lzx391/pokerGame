package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.npc.engine.DpNpcEngine.BoardDanger;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.HandPlanType;
import com.example.mgdemoplus.npc.eval.DpBoardTexture;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.npc.strategypro.DpNpcRuleDecisionParams;
import com.example.mgdemoplus.utils.DpUtilSmartContext;

/** 规则 NPC 翻后 trace 埋点：各 archetype 策略在关键分支调用，决策逻辑不变。 */
public final class DpNpcPostflopTraceHooks {

    private DpNpcPostflopTraceHooks() {
    }

    public static void postflopContext(
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
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "CONTEXT",
                "POSTFLOP_SPOT",
                DpNpcPostflopTraceSupport.postflopSpotMessage(stage, callAmount, plan, made, draw, tex),
                DpNpcPostflopTraceSupport.postflopSpotData(
                        stage, callAmount, plan, made, draw, tex, boardDanger, p, ctx, commitFactor, commitThreshold));
    }

    public static void giveUpFoldBoost(
            HandPlanType plan,
            DpNpcMadeHandCategory made,
            double baseFoldBefore,
            double baseFoldAfter,
            double delta) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "EVAL",
                "GIVE_UP_FOLD_BOOST",
                "GIVE_UP 计划：非 TRIPS+ 边缘牌 foldProb +" + delta,
                DpNpcTagDecisionTraceCollector.dataOf(
                        "plan", plan != null ? plan.name() : "",
                        "made", made != null ? made.name() : "",
                        "delta", delta,
                        "baseFoldBefore", baseFoldBefore,
                        "baseFoldAfter", baseFoldAfter));
    }

    public static void heroCall() {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "EVAL",
                "HERO_CALL",
                "L4 hero call：跳过弃牌骰，继续后续线",
                DpNpcTagDecisionTraceCollector.dataOf("reason", "heroCall"));
    }

    public static void foldRollHit(double baseFold, double foldProb) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "PROB",
                "FOLD_ROLL",
                "弃牌骰命中：roll < foldProb",
                DpNpcTagDecisionTraceCollector.dataOf("baseFold", baseFold, "foldProb", foldProb));
        DpNpcTagDecisionTraceCollector.step(
                "RESULT",
                "FOLD",
                "面对下注选择弃牌",
                DpNpcTagDecisionTraceCollector.dataOf("baseFold", baseFold, "foldProb", foldProb));
    }

    public static void foldRollMiss(double baseFold, double foldProb, HandPlanType plan) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        String planNote = plan == HandPlanType.GIVE_UP
                ? "GIVE_UP 已抬高 foldProb，本次未弃牌"
                : "";
        DpNpcTagDecisionTraceCollector.step(
                "PROB",
                "FOLD_ROLL_MISS",
                planNote.isEmpty()
                        ? "弃牌骰未命中：继续 facing bet 线（非弃牌）"
                        : "弃牌骰未命中：继续 facing bet 线（" + planNote + "）",
                DpNpcTagDecisionTraceCollector.dataOf(
                        "baseFold", baseFold,
                        "foldProb", foldProb,
                        "plan", plan != null ? plan.name() : "",
                        "note", planNote));
    }

    public static void raiseRoll(double raiseProb, double aggro, HandPlanType plan, DpNpcMadeHandCategory made) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "PROB",
                "RAISE_ROLL",
                "加注骰命中：roll < raiseProb",
                DpNpcTagDecisionTraceCollector.dataOf(
                        "raiseProb", raiseProb,
                        "aggro", aggro,
                        "plan", plan != null ? plan.name() : "",
                        "made", made != null ? made.name() : ""));
    }

    public static void raiseRollMiss(
            double raiseProb,
            double aggro,
            HandPlanType plan,
            DpNpcMadeHandCategory made) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "PROB",
                "RAISE_ROLL_MISS",
                "加注骰未命中 → 默认跟注（非因 plan 直接弃牌）",
                DpNpcTagDecisionTraceCollector.dataOf(
                        "raiseProb", raiseProb,
                        "aggro", aggro,
                        "plan", plan != null ? plan.name() : "",
                        "made", made != null ? made.name() : "",
                        "note", "call 为 facing bet 默认回退"));
    }

    public static void valueBetRoll(double valueBetProb, double potFraction) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "PROB",
                "VALUE_BET_ROLL",
                "价值下注骰命中",
                DpNpcTagDecisionTraceCollector.dataOf("valueBetProb", valueBetProb, "potFraction", potFraction));
    }

    public static void valueBetRollMiss(double valueBetProb) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "PROB",
                "VALUE_BET_ROLL_MISS",
                "价值下注骰未命中 → check",
                DpNpcTagDecisionTraceCollector.dataOf("valueBetProb", valueBetProb));
    }

    public static void skipAggressive(HandPlanType plan, String stage, DpNpcMadeHandCategory made) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "PLAN",
                "SKIP_AGGRESSIVE",
                "计划/barrels 限制进攻 → check/call",
                DpNpcTagDecisionTraceCollector.dataOf(
                        "plan", plan != null ? plan.name() : "",
                        "stage", stage != null ? stage : "",
                        "made", made != null ? made.name() : ""));
    }

    public static void commitThreshold(
            double commitThreshold,
            int heroInvestAfter,
            String branch,
            BotActionType actionType) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "EVAL",
                "COMMIT_THRESHOLD",
                "投入超过 commit 阈值，随机 " + branch,
                DpNpcTagDecisionTraceCollector.dataOf(
                        "commitThreshold", commitThreshold,
                        "heroInvestAfter", heroInvestAfter,
                        "branch", branch,
                        "actionIntent", actionType != null ? actionType.name() : ""));
    }

    public static void postflopAction(String actionIntent, String message) {
        if (DpNpcTagDecisionTraceCollector.current() == null) {
            return;
        }
        DpNpcTagDecisionTraceCollector.step(
                "RESULT",
                "POSTFLOP_ACTION",
                message,
                DpNpcTagDecisionTraceCollector.dataOf("actionIntent", actionIntent));
    }
}
