# Shark 专属类：翻前模块、剥削剧本、`LearningLab` 与持久化

以下类均在包 `com.example.mgdemoplus.service.serviceImpl.dp` 下（与 `DpNpcEngine` 同包或相邻），除非另注。

---

## 5.1 `DpNpcSharkPreflopStrategy`

| 项目 | 说明 |
|------|------|
| **职责** | 仅处理 **Shark 翻前**；与 TAG 的 `decidePreflopForTagOrShark` **完全独立**。 |
| **核心抽象** | `HandGroup`（G1～G8）表示 13×13 手牌强度分层；`PreflopSpot` 区分 UNOPENED / 面对加注 / 3bet 线 / 4bet+ 等；`rangeLevel` 综合人数、位置、码深、情绪。 |
| **入口** | `decideForShark(room, bot, position, callAmount, callRatio, preflopTight, callStation, mood, random)`，由 `DpNpcEngine` 的 `case SHARK` 在 `preflop` 时调用。 |

---

## 5.2 `DpNpcSharkStrategy`

| 项目 | 说明 |
|------|------|
| **职责** | **Shark 翻后**全部逻辑（从 `DpNpcEngine` 拆出以减小单文件体积）。 |
| **依赖** | `buildSmartContext`、`initHandPlanIfNeededForPostflop`、`updateHandPlanForLaterStreetIfNeeded`、`SharkConfig`、`SharkStrategyProfile`、下注尺度吸附（小盲倍数）等。 |
| **牌面修正** | 公共牌主导时下调牌力档；与引擎内 `isBoardDominantBestHand` 一致。 |

---

## 5.3 `DpNpcEngine` 中与 Shark 强相关的内部设施

| 名称 | 作用 |
|------|------|
| **`SharkConfig` / `SharkStrategyProfile`** | 集中管理 Shark 常数（短码/深码阈值、c-bet、河牌 block、equity 与赔率比较容差等）。 |
| **`initHandPlanIfNeededForPostflop`** | flop 首次生成 `HandPlanType` + barrels + aggression；Shark 路径上调用 **`DpNpcSharkExploitHandPlan.tuneAfterBasePlan`**。 |
| **`resolvePrimaryVillainForShark`** | 从 aggressor 或顺时针下一位「该行动的人」解析 **主对手**，供剥削剧本与学习旋钮挂接。 |
| **`updateHandPlanForLaterStreetIfNeeded`** | turn/river 强牌纠正计划（避免 flop 打一枪后全程 check）。 |
| **`consumeOneBarrelIfAny`** | 成功主动下注/加注后扣减 barrel（由策略在合适处调用）。 |
| **`estimateEquityBucket` / `computePotOdds` / `estimateShowdownBluffiness`** 等 | Shark 弃牌与尺度会用到；定义在 `DpNpcEngine` 内。 |

---

## 5.4 `DpNpcSharkExploitHandPlan`

| 项目 | 说明 |
|------|------|
| **`ExploitScript`** | 粗分类：`BALANCED`、`CALLING_STATION`、`TIGHT_PASSIVE`、`LOOSE_AGGRESSIVE`。 |
| **`classify(stats, adj, villainNickname)`** | 若 `villainNickname` 为 **`BOT_Fish`**，可直接按 **跟注站** 归类（无需等统计样本）。 |
| **`tuneAfterBasePlan`** | 在基础 HandPlan 上改 **线路类型 / barrels / aggression**，使学习参数驱动整手计划。 |

---

## 5.5 `DpNpcSharkLearningLab`

| 项目 | 说明 |
|------|------|
| **定位** | 按 **对手昵称** 存「压缩旋钮」——不保存每手完整 `SmartContext`。 |
| **`LearnedAdjust`** | 含 `foldAdjustment`、`bluffCatchBoost`、`sizingFactor`、`bluffFrequencyScale`、按街的 `bluffFireBoost*`、`valueRaiseBoost*` 等。 |
| **使用** | `getAdjust(room, bot, villain)` 供策略与 `DpNpcSharkExploitHandPlan` 读取。 |

---

## 5.6 `DpNpcSharkOpponentMemoryService`（Spring）

| 项目 | 说明 |
|------|------|
| **职责** | 桌上有 **Shark** 时，在结算后把各对手的 `PlayerStats` + LearningLab 快照 **upsert** 到表 **`dp_shark_opponent_profile`**（主键昵称）。 |
| **读取** | 玩家 `joinRoom` / 新一手 `newHand` 等时机 **hydrate** 回内存（若 `playerStatsMap` 尚无该昵称）。 |
| **SQL** | `src/main/resources/db/dp_shark_opponent_profile.sql` |

---

## 5.7 `DpNpcSharkHandActionLog`

| 项目 | 说明 |
|------|------|
| **职责** | 仅桌上有 Shark 时，在内存中记录 **本手** 逐街动作（bet/raise/ fold 等），用于分析激进者与弃牌模式；**不**替代正式牌谱。 |
| **生命周期** | 本手内有效，结算后清空。 |

---

## 5.8 数据流小结（Shark）

```
DpRoomServiceImpl
    → DpNpcEngine.decideActionIfReady
        → preflop: DpNpcSharkPreflopStrategy
        → postflop: DpNpcSharkStrategy
            → buildSmartContext
            → initHandPlanIfNeededForPostflop (+ DpNpcSharkExploitHandPlan + LearningLab)
            → updateHandPlanForLaterStreetIfNeeded
            → SharkConfig / 尺度 / 弃牌方程
    ← BotAction

结算后（有 Shark）:
    → DpNpcSharkLearningLab 更新
    → DpNpcSharkOpponentMemoryService.persistOpponentsAfterHand
```

返回目录：[README.md](README.md)。
