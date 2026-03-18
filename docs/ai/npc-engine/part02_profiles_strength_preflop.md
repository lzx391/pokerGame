## Part 02 - 风格/难度参数、牌力评估、牌面干湿、翻前分类/决策（摘自 `DpNpcEngine.java`）

> 覆盖：`StyleProfile`/`DifficultyProfile` 配置、`estimateCurrentStrength`、`evaluateBoardDanger`、翻前手牌分类 `classifyHoleCards`、翻前入口 `decidePreflopForTagOrShark`。

```java
// DpNpcEngine.java (摘录) - L521 ~ L1325 左右（含少量前后文）

private static final PreflopStrategyProfile PREFLOP_TAG_PROFILE;
private static final PreflopStrategyProfile PREFLOP_SHARK_PROFILE;

static {
    // ... 初始化 PREFLOP_TAG_PROFILE / PREFLOP_SHARK_PROFILE 的 open/3bet 参数表 ...
}

enum VillainRangeTier { NIT, TIGHT, BALANCED, LOOSE, MANIAC }
enum ActionCredibility { HIGH, MEDIUM, LOW }

private static class StyleProfile {
    final double preflopTightness;
    final double aggression;
    final double bluffFrequency;
    final double callStation;
    final double stealBlindFrequency;
    final double checkRaiseFear;
    StyleProfile(double preflopTightness, double aggression, double bluffFrequency,
                 double callStation, double stealBlindFrequency, double checkRaiseFear) { /*...*/ }
}

private static class DifficultyProfile {
    final double strengthNoise;
    final double potOddsNoise;
    final double ignorePositionProb;
    final double ignorePotOddsProb;
    final double missBluffCatchProb;
    final double randomSpewProb;
    DifficultyProfile(double strengthNoise, double potOddsNoise, double ignorePositionProb,
                      double ignorePotOddsProb, double missBluffCatchProb, double randomSpewProb) { /*...*/ }
}

private static final Map<NpcStyle, StyleProfile> STYLE_PROFILE_MAP = new EnumMap<>(NpcStyle.class);
private static final Map<NpcDifficulty, DifficultyProfile> DIFFICULTY_PROFILE_MAP = new EnumMap<>(NpcDifficulty.class);

static {
    // ... STYLE_PROFILE_MAP / DIFFICULTY_PROFILE_MAP 初始化 ...
}

public static boolean isBotPlayer(DpPlayer p) { /* ... */ }
public static BotType getBotTypeByNickname(String nickname) { /* ... */ }
private static NpcStyle getStyleByBotType(BotType type) { /* ... */ }

private static double estimateEquityBucket(SimpleStrength st, String stage,
                                           List<String> hole, List<String> community) { /* ... */ }
private static boolean hasStrongFlushDraw(List<String> hole, List<String> community) { /* ... */ }
private static boolean hasOpenEndedStraightDraw(List<String> hole, List<String> community) { /* ... */ }

private static SimpleStrength estimateCurrentStrength(DpRoom room, DpPlayer bot) {
    List<String> hole = bot.getHoleCards();
    List<String> community = room.getCommunityCards();
    if (hole == null || hole.size() < 2) return SimpleStrength.WEAK;
    List<String> all = new ArrayList<>(hole);
    if (community != null) all.addAll(community);
    if (all.size() < 5) {
        return toSimpleStrength(null, room.getCurrentStage(), hole, community);
    }
    DpHandEvaluator.HandStrength hs = evaluateBestHand(all);
    return toSimpleStrength(hs, room.getCurrentStage(), hole, community);
}

private static SimpleStrength applyStrengthNoise(SimpleStrength raw, NpcDifficulty difficulty, Random random) { /* ... */ }

private static BoardDanger evaluateBoardDanger(List<String> communityCards) {
    // 同花危险：某一花色数量 >= 3
    // 顺子危险：有 3 张以上接近的连张结构
    /* ... */
    return BoardDanger.DRY;
}

private static TablePosition getTablePosition(DpRoom room, DpPlayer bot) { /* ... */ }
private static PreflopPosition toPreflopPosition(TablePosition pos) { /* ... */ }

private static PreflopHandCategory classifyHoleCards(List<String> holeCards) { /* ... */ }

private static PreflopDecision decidePreflopForTagOrShark(
        DpRoom room,
        DpPlayer bot,
        BotType type,
        SimpleStrength strength,
        PreflopPosition pos,
        int callAmount,
        double callRatio,
        Random random
) {
    // short stack 强牌 all-in、open、面对 open 的 3bet/跟注/弃牌
    /* ... */
    return new PreflopDecision(BotActionType.CALL_OR_CHECK, 0, false);
}
```

