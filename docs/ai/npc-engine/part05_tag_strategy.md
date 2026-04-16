## Part 05 - TAG 策略（摘自 `DpNpcEngine.java` 的 `case TAG` + 文件结尾）

> 覆盖：`case TAG` 的完整策略框架（翻前更紧、翻后更简单、带少量读人/计划/commit threshold），以及 `decideBotAction` 收尾。

```java
// DpNpcEngine.java (摘录) - L3068 ~ L3456

case TAG: {
    BoardDanger bd = boardDanger;
    SimpleStrength st = strength;
    String stage = room.getCurrentStage();
    callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
    callRatio = chips == 0 ? 1.0 : (callAmount * 1.0 / chips);

    if ("preflop".equals(stage)) {
        PreflopDecision pre = decidePreflopForTagOrShark(/* ... */);
        if (pre != null) { /* ... */ }
    }

    SmartContext ctx = buildSmartContext(room, bot, st, stage, callAmount, difficulty, random);
    initHandPlanIfNeededForPostflop(room, bot, type, st, bd, position, ctx, random);

    // commit threshold
    StackContext tagStackCtx = ctx.stackCtx;
    int tagTotalStack = bot.getBet() + bot.getChips();
    double tagCommitFactor = /* ... */;
    final double tagCommitThreshold = tagTotalStack * tagCommitFactor;

    // 1) preflop：更紧，弱牌常弃；强牌面对 open 更偏向 3bet
    if ("preflop".equals(stage)) { /* ... */ }

    // 2) 翻后：先 fold 概率，再在 free check / 有跟注成本 下分流 raise/call
    double baseFold = /* ... */;
    double foldProbTag = /* ... */;
    if (callAmount > 0 && foldProbTag > 0 && random.nextDouble() < foldProbTag) {
        return new BotAction(BotActionType.FOLD, 0);
    }

    if (callAmount == 0) {
        // free check：强牌高频 value，MEDIUM 少量 thin value
        /* ... */
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    // 有跟注成本：raiseProb 决定 raise 还是 call
    double raiseProb = /* ... */;
    if (r > raiseProb || chips <= callAmount) {
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    int raiseAmount = /* ... */;
    if (bot.getBet() + raiseAmount > tagCommitThreshold) {
        // 超出 TAG 心理价：回落到控制池 / 跟注 / 弃牌 / 少量 all-in
        /* ... */
    }
    if (shouldSkipAggressiveActionByPlan(bot, stage)) {
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }
    consumeOneBarrelIfAny(bot, stage);
    return new BotAction(BotActionType.RAISE, raiseAmount);
}
default:
    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
}
```

