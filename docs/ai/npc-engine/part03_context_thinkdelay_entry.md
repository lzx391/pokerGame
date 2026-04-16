## Part 03 - SmartContext 构建、思考时间、入口（摘自 `DpNpcEngine.java`）

> 覆盖：对手画像（range tier / credibility / counter-strategy）、底池赔率、筹码结构、摊牌 bluff 倾向、`decideActionIfReady` 与 `calculateBotThinkDelay`，以及 `buildSmartContext` 的主体。

```java
// DpNpcEngine.java (摘录) - L1300 ~ L2060 左右（含少量前后文）

private static VillainRangeTier estimateVillainRangeTier(PlayerStats stats) { /* ... */ }

static class MultiwayVillainInfo { /* ... */ }

static class CounterStrategyProfile {
    final boolean moreThinValue;
    final boolean callDownMore;
    final boolean foldMoreToBigBets;
    final boolean bluffLess;
    final boolean bluffCatchMore;
    /* ... */
    static final CounterStrategyProfile NEUTRAL = new CounterStrategyProfile(false,false,false,false,false);
}

private static CounterStrategyProfile analyzeVillainCounterStrategy(PlayerStats stats) { /* ... */ }
private static ActionCredibility estimateActionCredibility(DpRoom room, DpPlayer bot, DpPlayer aggressor) { /* ... */ }

private static double computePotOdds(DpRoom room, DpPlayer bot, int callAmount, Random random, NpcDifficulty difficulty) { /* ... */ }

static class StackContext { /* ... */ }
private static StackContext analyzeStacks(DpRoom room, DpPlayer hero) { /* ... */ }
private static double computeHeroVsVillainStackRatio(DpPlayer hero, DpPlayer villain) { /* ... */ }
private static double estimateShowdownBluffiness(PlayerStats stats) { /* ... */ }

public static BotAction decideActionIfReady(DpRoom room, DpPlayer bot) {
    if (room == null || bot == null || !room.isPlaying()) return null;
    int actorIndex = room.getCurrentActorIndex();
    if (actorIndex < 0 || actorIndex >= room.getPlayers().size()) return null;
    DpPlayer current = room.getPlayers().get(actorIndex);
    if (current != bot) return null;
    if (bot.isFold() || bot.isAllIn() || bot.isLeftThisHand()) return null;

    long now = System.currentTimeMillis();
    long nextTime = bot.getNextBotActionTime();
    if (nextTime <= 0L) {
        long delayMs = calculateBotThinkDelay(room, bot);
        bot.setNextBotActionTime(now + delayMs);
        return null;
    }
    if (now < nextTime) return null;
    bot.setNextBotActionTime(0L);

    BotType type = getBotTypeByNickname(bot.getNickname());
    if (type == null) return null;
    return decideBotAction(room, bot, type);
}

private static class BotThinkProfile { /* ... */ }
private static final BotThinkProfile THINK_DEMO = new BotThinkProfile(/* ... */);
private static final BotThinkProfile THINK_MANIAC = new BotThinkProfile(/* ... */);
private static final BotThinkProfile THINK_SHARK = new BotThinkProfile(/* ... */);
private static BotThinkProfile getThinkProfile(BotType type) { /* ... */ }
private static long randomBetween(long minInclusive, long maxInclusive, Random random) { /* ... */ }

private static long calculateBotThinkDelay(DpRoom room, DpPlayer bot) {
    int chips = bot.getChips();
    int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
    double callRatio = chips == 0 ? 1.0 : (callAmount * 1.0 / Math.max(1, chips));
    SimpleStrength strength = estimateCurrentStrength(room, bot);
    BotType type = getBotTypeByNickname(bot.getNickname());
    double mood = bot.getMood();
    Random random = buildHandRandom(room, bot);
    BotThinkProfile profile = getThinkProfile(type);
    /* ... snap / cheap snap / strength bucket / moodFactor / cap ... */
    return 0L;
}

private static Random buildHandRandom(DpRoom room, DpPlayer bot) { /* ... */ }
private static double applySoftNoise(double prob, double delta, Random random) { /* ... */ }
private static int countActiveVillains(DpRoom room, DpPlayer hero) { /* ... */ }

private static SmartContext buildSmartContext(
        DpRoom room,
        DpPlayer hero,
        SimpleStrength strength,
        String stage,
        int callAmount,
        NpcDifficulty difficulty,
        Random random
) {
    // 1) aggressor
    // 2) aggressorStats
    // 3) villainTier
    // 4) credibility
    // 5) showdownBluffiness
    // 6) potOdds
    // 7) equityEst
    // 8) stackCtx
    // 9) activeVillains
    // 10) multi-way 扩展 + counterStrategy
    /* ... */
    return new SmartContext(/* ... */);
}
```

补充：`SmartContext` 类本身在独立文件 `src/main/java/com/example/mgdemoplus/service/studentImpl/SmartContext.java`。

