## Part 01 - 配置与核心类型（摘自 `DpNpcEngine.java`）

> 覆盖：Shark 策略配置、Bot/NPC 基础枚举、`BotAction`、HandPlan 初始化（开头一大段）。

```java
// DpNpcEngine.java (摘录) - L1 ~ L520 左右
package com.example.mgdemoplus.service.serviceImpl;

import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpRoom;

import java.util.*;

import static com.example.mgdemoplus.service.serviceImpl.dp.DpUtilHandEvaluator.SimpleStrength;

public final class DpNpcEngine {

    private DpNpcEngine() {
    }

    private static final class SharkStrategyProfile { /* ... 省略：大量参数注释 ... */
    }

    private static final class SharkConfig {
        private static final SharkStrategyProfile P = SharkStrategyProfile.DEFAULT;
        static final double SHORT_STACK_BB = P.shortStackBB;
        static final double DEEP_STACK_MIN_BB = P.deepStackMinBB;
        static final double DEEP_TABLE_AVG_BB = P.deepTableAvgBB;
        static final double EQUITY_POTODDS_EPS = P.equityPotOddsEps;
        static final double EQUITY_POTODDS_MARGIN = P.equityPotOddsMargin;
        static final double EQUITY_FOLD_BOOST = P.equityFoldBoost;
        static final double EQUITY_FOLD_SHRINK = P.equityFoldShrinkFactor;
        static final double MULTIWAY_FOLD_BOOST = P.multiwayFoldBoostBase;
        static final double PROB_NOISE_DELTA = P.probNoiseDelta;
        static final double RIVER_OVERBET_PROB = P.riverOverbetProb;
        static final double RIVER_BLOCK_PROB = P.riverBlockProb;
    }

    public static final String DEMO_BOT_NICKNAME = "BOT_Fish";
    public static final String MANIAC_BOT_NICKNAME = "BOT_Maniac";
    public static final String SHARK_BOT_NICKNAME = "BOT_Shark";
    public static final String TAG_BOT_NICKNAME = "BOT_Tag";

    private enum BotType {DEMO, MANIAC, SHARK, TAG}

    private enum HandPlanType {VALUE, BLUFF, POT_CONTROL, GIVE_UP}

    private enum NpcStyle {TIGHT_AGGRO, LOOSE_AGGRO, TIGHT_PASSIVE, LOOSE_FUN}

    private enum NpcDifficulty {EASY, MEDIUM, HARD, PRO}

    public enum BotActionType {FOLD, CALL_OR_CHECK, RAISE, ALL_IN}

    public static class BotAction {
        private final BotActionType type;
        private final int amount;

        BotAction(BotActionType type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        public BotActionType getType() {
            return type;
        }

        public int getAmount() {
            return amount;
        }
    }

    private enum BoardDanger {DRY, WET}

    private enum TablePosition {EARLY, MIDDLE, LATE, BLINDS}

    private enum PreflopPosition {EARLY, MIDDLE, LATE, BLINDS}

    private enum PreflopHandCategory {
        PREMIUM_PAIR, MEDIUM_PAIR, SMALL_PAIR, STRONG_SUITED_BROADWAY, SUITED_CONNECTOR, TRASH
    }

    // HandPlan 相关（flop 首次行动时初始化）
    private static HandPlanType getHandPlanType(DpPlayer bot) { /* ... */
        return null;
    }

    private static void initHandPlanIfNeededForPostflop(
            DpRoom room, DpPlayer bot, BotType type, SimpleStrength strength,
            BoardDanger boardDanger, TablePosition position, SmartContext ctx, Random random
    ) { /* ... */ }

    private static boolean shouldSkipAggressiveActionByPlan(DpPlayer bot, String stage) { /* ... */
        return false;
    }

    private static void consumeOneBarrelIfAny(DpPlayer bot, String stage) { /* ... */ }

    // PreflopDecision / PreflopStrategyProfile（后续 part02 会继续）
    private static class PreflopDecision { /* ... */
    }

    private static final class PreflopStrategyProfile { /* ... */
    }
}
```

