## Part 04 - Shark 翻后决策主体（摘自 `DpNpcEngine.java` 的 `case SHARK`）

> 覆盖：`decideBotAction` 进入 `case SHARK` 后的核心流程：翻前分流、SmartContext、HandPlan、commit 阈值、fold 概率合成、c-bet、call/raise、river overbet/block 等。

```java
// DpNpcEngine.java (摘录) - L2386 ~ L3067 左右（含少量前后文）

case SHARK: {
    BoardDanger bd = boardDanger;
    SimpleStrength st = strength;
    String stage = room.getCurrentStage();

    // 翻前：走 decidePreflopForTagOrShark(...)
    if ("preflop".equals(stage)) { /* ... */ }

    // 翻后：构建 SmartContext + 初始化 HandPlan
    SmartContext ctx = buildSmartContext(room, bot, st, stage, callAmount, difficulty, random);
    initHandPlanIfNeededForPostflop(room, bot, type, st, bd, position, ctx, random);

    StackContext stackCtx = ctx.stackCtx;
    int activeVillains = ctx.activeVillains;
    CounterStrategyProfile counter = ctx.counterStrategy;

    // commit threshold：这手牌最多愿意投入多少
    int sharkTotalStack = bot.getBet() + bot.getChips();
    double sharkCommitFactor = /* MONSTER/STRONG/MEDIUM/WEAK + WET/深码调整 */;
    final double sharkCommitThreshold = sharkTotalStack * sharkCommitFactor;

    // fold 概率合成：对手风格/可信度/摊牌bluff倾向/赔率/粗略赢率/counter/multi-way/HandPlan
    double baseFold = 0.0;
    if (st == SimpleStrength.WEAK && callAmount > 0 && callRatio > 0.4) {
        baseFold = (bd == BoardDanger.WET) ? 0.7 : 0.5;
    }
    // ... (大量 baseFold 调整逻辑) ...
    double foldProbShark = Math.min(1.0, Math.max(0.0, baseFold + (-mood) * 0.1));
    foldProbShark = applySoftNoise(foldProbShark, SharkConfig.PROB_NOISE_DELTA, random);
    if (callAmount > 0 && foldProbShark > 0 && random.nextDouble() < foldProbShark) {
        return new BotAction(BotActionType.FOLD, 0);
    }

    // c-bet：晚位 + 干燥牌面 + 弱/中牌 + 无人下注 时，可能小额下注
    if (callAmount == 0
            && ("flop".equals(stage) || "turn".equals(stage))
            && position == TablePosition.LATE
            && bd == BoardDanger.DRY
            && (st == SimpleStrength.WEAK || st == SimpleStrength.MEDIUM)) {
        /* ... */
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }

    // call vs raise 的分流
    double callProb = /* ... */;
    if (r < Math.max(0.1, Math.min(0.9, callProb))) {
        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    // raise：根据 callAmount==0 (value/cbet) 或 有跟注成本 (反加/压力) 计算 raiseAmount
    if (!"preflop".equals(stage) && chips > callAmount) {
        if (shouldSkipAggressiveActionByPlan(bot, stage)) {
            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
        int raiseAmount = /* ... 根据 pot / bb / 短码压力 / 深码收缩 / multi-way / commitThreshold 等 ... */;

        if (bot.getBet() + raiseAmount > sharkCommitThreshold) {
            // 超出心理价：在 all-in / call / fold 之间选
            /* ... */
        }

        // river overbet / river block
        /* ... */
        consumeOneBarrelIfAny(bot, stage);
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }

    // river block bet（MEDIUM 且无人下注）
    if ("river".equals(stage) && callAmount == 0 && st == SimpleStrength.MEDIUM
            && chips > 0 && random.nextDouble() < SharkConfig.RIVER_BLOCK_PROB) {
        /* ... */
        return new BotAction(BotActionType.RAISE, raiseAmount);
    }

    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
}
```

