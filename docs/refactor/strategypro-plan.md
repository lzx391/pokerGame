# 规则 NPC 策略重构方案（strategypro）

> **重构完成摘要（2026-06-08）**  
> Phase 1～2b 与 Wave 3a～3g 已全部落地：6 档 preset + CUSTOM 各拥有独立 `postflop/*/` 策略类，翻前统一走 `strategypro/preflop/DpNpcUnifiedPreflopStrategy`，翻后经 `DpNpcStrategyFacade` 路由。  
> Phase 5 已删除 `TightAggroPostflop` / `LooseAggroPostflop` / `PassiveStationPostflop` 三套壳及整个 `npc/strategy/` deprecated 目录。  
> CUSTOM 翻后由 `DpNpcCustomPostflopStrategy` 六维 L5 偏移器独立实现（foldToPressure / callStation / cbetFreq / bluffFreq / pfr / vpip 各读各的）。  
> `mvn compile` + `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿（134 tests）。Wave 4（MC equity）仍为可选后续项。

| 项 | 值 |
|---|---|
| 方案版本 | 2026-06-08 |
| 状态 | **Wave 3 + Phase 5 已完成**（Wave 4 可选未做） |
| 权威来源 | `DpNpcEngine#decideBotAction`、`npc/strategypro/*`、`npc/eval/*` |
| 文档索引 | [docs/ai/npc-engine/README.md](../ai/npc-engine/README.md) |
| 前置已完成 | [dp-npc-granular-strength-plan.md](dp-npc-granular-strength-plan.md)（12 档成牌 + 听牌轴） |

---

## 用户已确认决策（不可擅自修改）

| # | 决策 |
|---|------|
| 1 | **节奏**：Phase 1 先搬家（行为不变）→ 再分波改逻辑 |
| 2 | **翻前**：保持 **0/1 矩阵**（非频率矩阵）；`rangeLevel` + 性格/位置/码深 调阈值 |
| 3 | **六维 CUSTOM**：与 preset archetype 一起在翻后 wave 中处理；L5 偏移器保留概念，实现重做 |
| 4 | **总目标**：打得**合理、像正常人思维**，不犯蠢（如坚果弃牌），**不追求 GTO/最优** |
| 5 | **关键红线**：**禁止套壳/复用** — 旧实现 NIT/TAG 共用 `TightAggroPostflop`、LAG/MANIAC 共用 `LooseAggro`、FISH/CALL 共用 `PassiveStation` 必须废弃；新方案 **6 档 archetype + CUSTOM 各写独立策略**（可共享纯数学工具类 eval/math，**不可共享 postflop 决策逻辑类**） |
| 6 | **分波实现**：不要一次堆全功能，每波有独立验收 |

---

## 1. 目标 / 非目标 / 验收口径

### 1.1 总目标

将规则 NPC 从「三壳六脸」重构为 **7 套独立策略**（FISH / CALL / LAG / TAG / NIT / MANIAC / CUSTOM），在保留现有翻前 0/1 矩阵框架的前提下，让 Bot **像正常人打牌**：

- 坚果 / 强成牌面对下注 **永不 fold**
- 免费看牌（`callAmount == 0`）**不弃牌**
- pot odds 明显不利时，非跟注站型 **倾向 fold**
- 各 archetype **风格可区分**（不是同一公式换系数）
- 决策可复现（`buildHandRandom` 种子不变）

### 1.2 非目标

| 范围 | 说明 |
|------|------|
| GTO / 求解器级最优 | 不做范围平衡、不追求纳什 |
| LLM NPC | `BOT_LLM` / `BOT_LLM_GLOBAL` 完全不动 |
| Flyway / 数据库 | 无表变更 |
| 前端 UI | CUSTOM 六维滑条 API 保持兼容（见 §8） |
| 引入外部 Maven 库 | 不新增第三方依赖 |
| 玩家展示轨 | `DpUtilHandEvaluator` 展示 API 不变 |
| 成就 / 桌边话 / mood | 不在本次策略重构范围（mood 已不参与决策） |

### 1.3 总验收口径

人工 + 自动化双重验收，满足以下 **全部** 条件视为重构成功：

1. **合理性**：100 次 golden-path 抽样中，坚果 facing bet fold 率 = 0%；免费看牌 fold 率 = 0%
2. **可区分性**：任意相邻 archetype 对（如 TAG vs NIT），在 ≥2 个标准场景下行动分布差异显著（见 §7）
3. **稳定性**：`mvn test` 全绿；Phase 1 后行为与迁包前 **字节级 BotAction 一致**（同种子）
4. **架构**：`npc/strategy/` 旧套壳类删除；`npc/strategypro/` 为唯一策略实现包
5. **文档**：`docs/ai/npc-engine/*` 索引更新，指向 strategypro

### 1.4 分 Phase 验收条款

| Phase / Wave | 验收条款 |
|--------------|----------|
| **Phase 0** | 本文档评审通过，用户决策表无遗漏 |
| **Phase 1** | 12 类迁入 `strategypro`；Facade 委托；`DpNpcHandClassifier` 循环依赖解除；`mvn test` 全绿；**同种子 BotAction 与迁包前一致** |
| **Wave 2a** | 翻前 0/1 矩阵整理完成；`rangeLevel` 公式文档化；G1–G8 与矩阵切片对齐；spot 切片（UNOPENED / FACING_OPEN / FACING_3BET / FACING_4BET）行为可解释 |
| **Wave 2b** | L1 硬约束层生效：坚果永不 fold、playingTheBoard 修复、免费看牌守卫；golden-path 硬约束用例全过 |
| **Wave 3a~3g** | 每 archetype 独立验收（见 §5.3）；与相邻型差异用例通过 |
| **Wave 4** | MC equity 冷路径接入（可选）；热路径仍用粗桶 `equityEst` |
| **Phase 5** | 旧 `npc/strategy/*` 删除；docs 更新；无死引用 |

---

## 2. 设计原则（工业级）

### 2.1 决策分层（L1 → L5）

```text
L1 数学硬约束   → 坚果保护、pot odds 底线、playingTheBoard 上限、SPR 极端
L2 策略骨架     → 翻前矩阵 / 翻后街计划（HandPlan）、cbet 频率档
L3 战术分支     → facingBet / noBet / multiway / 湿面 分叉
L4 剥削微调     → 对手可信度、showdownBluffiness、VillainRangeTier
L5 人格偏移     → archetype 独有噪声与人格化（CUSTOM 六维映射到此层，实现重做）
```

**优先级**：L1 否决一切低层决策。任何 L3–L5 概率不得让 L1 被绕过。

### 2.2 基本合理性约束（L1 清单）

| 约束 | 规则 |
|------|------|
| 坚果保护 | `FULL_HOUSE` 及以上（含 `QUADS`、`STRAIGHT_FLUSH`、`ROYAL_FLUSH`）facing bet → **永不 FOLD** |
| 强牌保护 | `TRIPS`+ 且 `equityEst ≥ 0.55` facing 非 all-in → fold 概率上限 5% |
| 免费看牌 | `callAmount == 0` → **永不 FOLD** |
| playingTheBoard | `playingTheBoard == true` 时 equity 上限 0.22；非听牌不价值加注 |
| pot odds | `equityEst < potOdds - 0.12` 且非 CALL 型 → fold 概率 ≥ 0.70 |
| 深码控池 | SPR > 14 且仅 `TOP_PAIR_WEAK_KICKER` → 不做 3 街 build pot |

### 2.3 一 archetype 一策略类

**可共享**（纯数学 / 无决策分叉）：

- `npc/eval/*`：`DpNpcHandClassifier`、`DpNpcPostflopFormula`、`DpNpcEquityEstimator`、`DpBoardTexture`
- `utils/dp/DpUtilHandEvaluator`（展示 + 牌力计算）
- 未来 `npc/math/DpPokerMath`（MC equity，无 archetype 分支）

**禁止共享**（含 postflop 决策分叉的类）：

- 任何 `*Postflop` 聚合决策类
- 带 `PersonalityProfile` / `LooseProfile` / `StationProfile` 枚举切换的套壳

### 2.4 旧套壳对照表（废弃原因）

| 旧套壳类 | 共用方 | 表面差异 | 为何废弃 |
|----------|--------|----------|----------|
| `DpNpcTightAggroPostflop` | TAG, NIT | `PersonalityProfile` 系数（foldTightnessMul 1.0 vs 1.18 等） | **同一 facingBet 分支树**，NIT 只是更紧系数；无法表达 NIT「无听牌不对注」「TAG 顶对 3 街价值」等**决策分叉** |
| `DpNpcLooseAggroPostflop` | LAG, MANIAC | `LooseProfile` 系数（commitMul、baseAllInProb 等） | MANIAC 应 **极低 fold + 高频 all-in**；LAG 应 **选择性侵略**。共用类导致 MANIAC 不够疯、LAG 不够区分 |
| `DpNpcPassiveStationPostflop` | FISH, CALL | `StationProfile` 系数（baseCallProb 0.68 vs 0.80 等） | CALL 是 **纯跟注站**；FISH 是 **松弱鱼**（会偶尔 donk bluff）。共用类无法让 FISH 翻前松翻后被动、CALL 翻前也宽但从不加注 |
| `DpNpcCustomStrategy` | — | 六维系数缩放 `DpNpcPostflopFormula` | 无独立人格，只是「公式 NPC」；与 preset 关系不清晰 |

**新方案要求**：每个 archetype 拥有 **独立的 `decide()` 方法体**，可以调用共享 `DpNpcPostflopFormula.xxx()` 算概率，但 **facingBet / noBet / river** 的 if-else 骨架不可复用另一 archetype 的类。

---

## 3. strategypro 包结构（最终态目录树）

```text
npc/
├── engine/
│   ├── DpNpcEngine.java              # 入口、HandPlan、SmartContext、调度、LLM 隔离（保留）
│   └── DpNpcStreetActionLog.java
├── strategypro/                       # ★ 新策略包（Phase 1 迁入，Phase 5 为唯一策略包）
│   ├── facade/
│   │   ├── DpNpcStrategyFacade.java       # 统一门面接口
│   │   ├── DpNpcStrategyProvider.java     # archetype → 策略实例
│   │   └── DpNpcDecisionContext.java      # 从 RuleDecisionParams 演化（或保留别名）
│   ├── preflop/
│   │   ├── DpNpcUnifiedPreflopStrategy.java   # 0/1 矩阵 + G1–G8 + rangeLevel
│   │   ├── DpNpcPreflopMatrix.java            # 矩阵切片数据（可选拆分）
│   │   └── DpNpcPreflopSpot.java              # Spot 枚举
│   ├── postflop/
│   │   ├── DpNpcTagPostflopStrategy.java
│   │   ├── DpNpcNitPostflopStrategy.java
│   │   ├── DpNpcFishPostflopStrategy.java
│   │   ├── DpNpcCallPostflopStrategy.java
│   │   ├── DpNpcLagPostflopStrategy.java
│   │   ├── DpNpcManiacPostflopStrategy.java
│   │   └── DpNpcCustomPostflopStrategy.java
│   ├── DpNpcFishStrategy.java         # 薄门面：preflop 委托 + postflop 委托（Phase 1 可保留旧名）
│   ├── DpNpcCallStrategy.java
│   ├── DpNpcLagStrategy.java
│   ├── DpNpcTagStrategy.java
│   ├── DpNpcNitStrategy.java
│   ├── DpNpcManiacStrategy.java
│   ├── DpNpcCustomStrategy.java
│   ├── DpNpcRuleDecisionParams.java
│   └── l1/
│       └── DpNpcHardConstraints.java  # Wave 2b：L1 硬约束（坚果/免费看牌/playingTheBoard）
├── eval/                              # 保留；解耦后新增：
│   ├── DpNpcPreflopHandGrouper.java   # 从 UnifiedPreflop 抽出 groupOf / preflopCategoryOf
│   └── ...（现有 12 档分类器）
├── math/                              # Wave 4（可选）
│   └── DpPokerMath.java               # MC equity 冷路径
├── llm/                               # 不动
├── rulethink/                         # 不动
└── tabletalk/                         # 不动

# Phase 5 删除：
npc/strategy/DpNpcTightAggroPostflop.java
npc/strategy/DpNpcLooseAggroPostflop.java
npc/strategy/DpNpcPassiveStationPostflop.java
npc/strategy/DpNpc*.java（迁入后旧路径残留）
```

**迁包边界说明**：

| 留在 `engine/` | 迁入 `strategypro/` |
|----------------|---------------------|
| `HandPlan` / `HandPlanType` 框架 | 12 个 strategy 类 |
| `buildSmartContext` / `DpUtilSmartContext` 组装 | `DpNpcUnifiedPreflopStrategy` |
| `decideActionIfReady` / `decideBotAction` 调度 | `DpNpcRuleDecisionParams` |
| `StyleProfile` / `STYLE_PROFILE_MAP` | Facade + Provider |
| LLM 分流 / Bot 昵称识别 | L1 `DpNpcHardConstraints`（Wave 2b 新增） |

---

## 4. 六档 + CUSTOM 性格设计专章

> 以下人设以 `DpNpcEngine.StyleProfile` preset 为基准（`application.yml` 不覆盖），翻前统一走 `rangeLevel` + `BotType.rangeLevelBonus`，翻后各型 **独立决策树**。

### 4.1 FISH（松弱鱼）

**人设**：VPIP ≈ 52%，PFR ≈ 15%；入池宽、翻后被动、爱跟注、偶尔莫名其妙下注。

| 维度 | 特征 |
|------|------|
| 翻前 | `rangeLevel` 偏高（+1 bonus）；矩阵档 5–7；后位宽 open（G5–G7 边缘牌）；面对 3bet **极少继续**（仅 G1–G2）；几乎不 3bet |
| 翻后 cbet | 低频率（≈20%）；有牌才 bet，无牌大概率 check |
| 面对压力 | 中对、弱顶对 **跟注偏多**；高牌 fold 慢（需较大注才弃） |
| 河牌 | 有对子就跟；无牌 check-fold；**极少河牌 bluff** |
| 诈唬 | 仅 flop 小额 donk（≈9% bluffFreq），turn/river 几乎不诈唬 |

**与 CALL 差异**：FISH 翻前 **更松**（会 open 垃圾牌），翻后会 **偶尔 lead bet**；CALL 翻前也宽但 **从不主动加注**，翻后 **100% 跟注站**。

**与 LAG 差异**：FISH 无侵略性；LAG 高频 cbet / raise。

**禁止项**：不得与 CALL 共用 `PassiveStationPostflop`；不得用系数区分。

---

### 4.2 CALL（跟注站）

**人设**：VPIP ≈ 64%，PFR ≈ 5%；「只要能看牌就跟」，翻后 **从不加注**（除被迫 all-in）。

| 维度 | 特征 |
|------|------|
| 翻前 | `rangeLevel` 最高档（+1 bonus）； defend 极宽；面对 open **大量 flat**；永不 3bet |
| 翻后 cbet | **0%** — 永远 check |
| 面对压力 | 任意对子、任意听牌 **跟注**；仅高牌 + 大注 + 无听牌才 fold |
| 河牌 | 有牌就跟到尾；无牌才 fold |
| 诈唬 | **无** |

**与 FISH 差异**：CALL **从不加注**（包括价值）；FISH 会用顶对/两对 **偶尔 bet**。

**与 NIT 差异**：NIT 翻前极紧、翻后易 fold；CALL 翻前宽、翻后 **几乎不 fold**。

**禁止项**：不得有 raise 分支（除 chips 不足被迫 all-in call）。

---

### 4.3 LAG（松凶）

**人设**：VPIP ≈ 41%，PFR ≈ 74%；宽范围进池、翻后 **高频 cbet / raise**，但仍有基本牌力判断。

| 维度 | 特征 |
|------|------|
| 翻前 | `rangeLevel` +1；后位 steal 积极；面对 open **3bet 混合**（价值 + 部分 bluff）；4bet 偶尔 |
| 翻后 cbet | 高频率（≈79%）；flop 无牌也 **semi-bluff** |
| 面对压力 | 听牌 **raise semi-bluff**；顶对 **raise 保护**；空气 fold 较快 |
| 河牌 | 价值薄注；**选择性 bluff**（干燥面 + 对手可信度高） |
| 诈唬 | 三街均有，但 **有牌力支撑**（semi-bluff 为主） |

**与 TAG 差异**：LAG 范围更宽、bluff 更多；TAG 翻前更紧、价值线更清晰。

**与 MANIAC 差异**：LAG 会 fold 空气；MANIAC **极少 fold**，all-in 频率远高于 LAG。

**禁止项**：不得与 MANIAC 共用 `LooseAggroPostflop`；facingBet 分支必须独立。

---

### 4.4 TAG（紧凶）

**人设**：VPIP ≈ 24%，PFR ≈ 76%；标准「好牌打快、坏牌快弃」，翻后 **平衡的价值 + 诈唬**。

| 维度 | 特征 |
|------|------|
| 翻前 | `rangeLevel` 基准（bonus 0）；早位紧、后位 steal；面对 3bet **按牌力继续**；价值 3bet 标准 |
| 翻后 cbet | 高频率（≈82%）；**有范围优势时 cbet** |
| 面对压力 | 顶对好踢脚 **call/raise**；弱顶对 **控池 call**；听牌按 pot odds |
| 河牌 | 价值最大化；**河牌 bluff 少而精**（干燥面、对手 overfold） |
| HandPlan | 使用 `HandPlan` 多街计划（VALUE / BLUFF / GIVE_UP） |

**与 NIT 差异**：TAG 会 **主动 steal / 3bet bluff**；NIT 几乎不偷盲、不 bluff 3bet。

**与 LAG 差异**：TAG 翻前 **紧得多**；翻后 bluff 频率低但 **尺度更优**。

**禁止项**：不得与 NIT 共用 `TightAggroPostflop`。

---

### 4.5 NIT（极紧）

**人设**：VPIP ≈ 11%，PFR ≈ 15%；只打好牌，翻后 **易 fold**，极少 bluff。

| 维度 | 特征 |
|------|------|
| 翻前 | `rangeLevel` 最低（bonus −2）；仅 G1–G3 open；面对 open **紧 defend**；几乎不 3bet |
| 翻后 cbet | 低频率（≈28%）；**仅强牌 cbet** |
| 面对压力 | 顶对弱踢脚 **易 fold**；两对以下 **大注 fold** |
| 河牌 | 除非坚果级，否则 **大注 fold** |
| 诈唬 | **几乎无**（≈3.5% bluffFreq） |

**与 TAG 差异**：NIT 翻前 **不 steal**；翻后 **顶对易弃**；TAG 顶对会打价值。

**与 CALL 差异**：NIT 翻前 **极紧**；CALL 翻前宽。

**禁止项**：不得仅通过 `foldTightnessMul` 系数与 TAG 区分。

---

### 4.6 MANIAC（疯子）

**人设**：VPIP ≈ 48%，PFR ≈ 90%；**极少 fold**，大量 raise / all-in，翻后侵略性极端。

| 维度 | 特征 |
|------|------|
| 翻前 | `rangeLevel` +2；**任何位置 open**；面对加注 **reraise 或 all-in** |
| 翻后 cbet | 极高（≈88%）；**无牌也 bet** |
| 面对压力 | **极少 fold**（基础 fold < 5%）；倾向 **raise 而非 call** |
| 河牌 | 空气 **高频 bluff**；强牌 **overbet all-in** |
| all-in | SPR 低时 **频繁 jam** |

**与 LAG 差异**：MANIAC **不 fold 空气**；LAG 会；MANIAC all-in 频率 **远高于** LAG。

**与 FISH 差异**：MANIAC **极度侵略**；FISH 被动。

**禁止项**：不得与 LAG 共用 `LooseAggroPostflop`；必须有独立的 `facingBet` 极低 fold 分支。

---

### 4.7 CUSTOM（六维自定义）

**人设**：由前端六维滑条定义（`CustomNpcStyleProfileDto` / `CustomNpcStyleSnapshot`）：

| 维度 | 字段 | 作用层 |
|------|------|--------|
| 翻前松紧 | `vpip` | `rangeLevel` 公式输入 |
| 翻前凶度 | `pfr` | 3bet/4bet 概率缩放 |
| 翻后领先下注 | `cbetFreq` | cbet / lead 概率 |
| 诈唬倾向 | `bluffFreq` | bluff / semi-bluff 概率 |
| 跟注站倾向 | `callStation` | fold 概率反向缩放 |
| 抗压性 | `foldToPressure` | 面对大注 fold 概率 |

**L5 偏移器（概念保留，实现重做）**：

- 不再用单一公式缩放 `DpNpcPostflopFormula`
- 改为：**选最近 preset 骨架**（`nearestNpcStyleForCustomSixAxis` 逻辑保留）+ **六维独立偏移**
  - `vpip` / `foldToPressure` → 翻前 `rangeLevel` 偏移
  - `pfr` → 翻前 3bet 概率偏移
  - `cbetFreq` / `bluffFreq` → 翻后 L5 偏移（独立函数，非乘系数）
  - `callStation` → 翻后 facingBet 时 **raise 分支门控**（高 callStation → 禁用 raise）

**与 preset 关系**：CUSTOM 翻后 **独立类** `DpNpcCustomPostflopStrategy`，可参考 TAG 骨架，但 **代码不得继承或委托** TAG 策略类。

**禁止项**：不得继续用 `DpNpcPostflopFormula` 概率 × 六维系数的单公式模式。

---

## 5. 分 Phase / 分 Wave 实施计划

### 5.0 Phase 0：本文档

| 项 | 内容 |
|----|------|
| 交付物 | `docs/refactor/strategypro-plan.md` |
| 验收 | 用户确认决策表 § 顶部无遗漏 |
| 回滚 | 删除文档即可 |

---

### 5.1 Phase 1：迁包 + Facade + 解耦 + 测试全绿

**目标**：零行为变更的结构搬迁。

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `npc/strategypro/` 包；移动 12 个 strategy 类；新建 `facade/DpNpcStrategyFacade` + `DpNpcStrategyProvider`；修改 `DpNpcEngine.decideBotAction` 一处委托；新建 `eval/DpNpcPreflopHandGrouper`（抽出 `groupOf` / `preflopCategoryOf`）；修改 `DpNpcHandClassifier` 改引 eval |
| 解耦 | `DpNpcHandClassifier` → `DpNpcUnifiedPreflopStrategy.preflopCategoryOf` 循环依赖 **必须在本 Phase 消除** |
| 验收 | ① `mvn test` 全绿 ② 选 20 组（archetype × stage × 种子）对比迁包前后 `BotAction` 完全一致 ③ `grep` 无新增 `strategy.` 旧路径 import（engine 除外过渡期 re-export） |
| 回滚 | `git checkout` 还原 `strategypro/` 目录；engine 恢复直接 import 旧路径 |
| 依赖 | Phase 0 |

**循环依赖处理方案**：

```text
DpNpcUnifiedPreflopStrategy.groupOf / preflopCategoryOf
    → 抽取到 eval/DpNpcPreflopHandGrouper
DpNpcHandClassifier.classifyPreflop
    → 调用 DpNpcPreflopHandGrouper.preflopCategoryOf
DpNpcUnifiedPreflopStrategy
    → 调用 DpNpcPreflopHandGrouper.groupOf（内部仍用于 G1–G8 填表与 bluff 候选）
```

---

### 5.2 Wave 2a：翻前 0/1 矩阵整理

| 项 | 内容 |
|----|------|
| 改动文件 | `strategypro/preflop/DpNpcUnifiedPreflopStrategy`；可选拆分 `DpNpcPreflopMatrix`；`docs/ai/npc-preflop-unified-decision-flow.md` 同步 |
| 工作内容 | ① 文档化 `rangeLevel` 公式 ② 校验 G1–G8 与 13×13 矩阵一致性 ③ `BotType.rangeLevelBonus` 与性格对齐 ④ spot 切片行为审查（UNOPENED / FACING_OPEN / FACING_3BET / FACING_4BET） |
| 验收 | ① 六 archetype 翻前 open 率排序：MANIAC > LAG > FISH > CALL > TAG > NIT ② 同手牌 NIT fold / MANIAC open 可复现 ③ 矩阵仍为 0/1（无频率格） |
| 回滚 | 恢复矩阵静态数据快照（类加载时 byte 数组） |
| 依赖 | Phase 1 |

#### Wave 2a 验收勾选（2026-06-08）

- [x] `vsOpenContinue` 矩阵按位置放宽（EARLY/MIDDLE/LATE 分档），不再全员像 NIT
- [x] 新增 `defendLevelBonus`：FISH/CALL +2、LAG/MANIAC +1、NIT −1，与 open 用 `rangeLevelBonus` 解耦
- [x] 面对 ≤2BB 跟注额（min-raise）额外 +1 defend 档
- [x] open 尺度：`baseOpenSizeBB` 连续化 + `roundRaiseToBlind` 半 BB 步进，避免 12/13/14 全吸附到 10
- [x] `DpNpcPreflopDefendTest`：FISH 77/98s、TAG broadway defend、open 尺度非恒定
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

---

### 5.3 Wave 2b：翻后 L1 硬约束

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `strategypro/l1/DpNpcHardConstraints`；各 postflop 策略类入口调用 `HardConstraints.applyOrOverride` |
| 工作内容 | ① 坚果永不 fold ② playingTheBoard 修复（公牌成牌 hero 无贡献 → 高牌 + equity 上限）③ 免费看牌守卫 ④ pot odds 底线 |
| 验收 | 见 §7 golden-path：皇家同花顺 facing bet → 永不 fold；playingTheBoard 不价值加注 |
| 回滚 | `HardConstraints` 整体禁用开关（`RuleNpcConfig.HARD_CONSTRAINTS_ENABLED = false`） |
| 依赖 | Phase 1（可与 2a 并行，但合并前各自验收） |

#### Wave 2b 验收勾选（2026-06-08）

- [x] 新建 `strategypro/l1/DpNpcHardConstraints`（`mustNotFold` / `capFoldProb` / `multiwayFoldBoost` / `adjustEquityForPlayingTheBoard` / `applyOrOverride`）
- [x] `RuleNpcConfig.HARD_CONSTRAINTS_ENABLED` 总开关
- [x] Facade `decidePostflop` 统一 L1 拦截；`buildSmartContext` 接入 playingTheBoard 权益修正
- [x] 套壳 postflop（TightAggro / LooseAggro / PassiveStation / Custom）接入 `capFoldProb` + 增强 `multiwayFoldBoost`
- [x] `LooseAggroPostflop` 强牌支路移除随机 FOLD；`updateHandPlanForLaterStreetIfNeeded` MANIAC + TRIPS+ GIVE_UP 纠错
- [x] `DpNpcHardConstraintsTest`：G1 皇家 facing overbet、公牌皇家平分、3 人池 TPTK 收紧
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

---

### 5.4 Wave 3a~3g：每 archetype 独立一波

**建议顺序**：TAG → NIT → FISH → CALL → LAG → MANIAC → CUSTOM

> 顺序理由：TAG 有 HandPlan 骨架最完整，先做标杆；NIT 与 TAG 对比验收；被动型（FISH/CALL）在中段；激进型（LAG/MANIAC）在后；CUSTOM 最后（依赖 preset 骨架理解）。

#### Wave 3a：TAG 独立翻后

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `DpNpcTagPostflopStrategy`；`DpNpcTagStrategy` 改委托；**删除**对 `TightAggroPostflop` 的调用 |
| 验收用例 | ① BTN AKs flop 顶对 → cbet ≥ 70% ② 河牌空气干燥面 → bluff ≤ 25% ③ 坚果 facing bet → 永不 fold（L1） |
| 回滚 | `TagStrategy` 恢复委托 `TightAggroPostflop` |
| 依赖 | Wave 2b |

#### Wave 3a 验收勾选（2026-06-08）

- [x] 新建 `strategypro/postflop/tag/DpNpcTagPostflopStrategy`（独立决策树，禁止委托 `TightAggroPostflop`）
- [x] `DpNpcTagStrategy` 改委托新类；干面 TPTK flop cbet 下限抬高（≈82% 基准）
- [x] HandPlan VALUE / POT_CONTROL 分线；GIVE_UP 不削弱 TRIPS+；多人池收紧
- [x] 所有 FOLD 路径经 `DpNpcHardConstraints.capFoldProb` / `mustNotFold` / `guardFold`
- [x] `DpNpcTagPostflopStrategyTest`：TPTK flop cbet、中对大注弃牌、TRIPS+GIVE_UP 不弃
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

#### Wave 3b：NIT 独立翻后

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `DpNpcNitPostflopStrategy`；删除 `TightAggroPostflop` 对 NIT 的引用 |
| 验收用例 | ① 翻前 UTG 72o → fold ② 翻后顶对弱踢脚 facing 3/4 pot → fold ≥ 60% ③ 翻后无 bluff lead |
| 与 TAG 差异 | 同场景 TAG cbet ≥ 70%，NIT cbet ≤ 30% |
| 依赖 | Wave 3a |

- [x] 新建 `strategypro/postflop/nit/DpNpcNitPostflopStrategy`（独立决策树，禁止套壳 TAG/TightAggro）
- [x] `DpNpcNitStrategy` 路由到新类；L1 `DpNpcHardConstraints` 接入
- [x] `DpNpcNitPostflopStrategyTest`：TPTK cbet ≤30%、湿面 TPWK facing 2/3 pot fold ≥60%、NIT fold > TAG、HIGH_CARD+draw 更少 semi-bluff、TRIPS 不弃
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

#### Wave 3c：FISH 独立翻后

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `DpNpcFishPostflopStrategy`；删除 `PassiveStationPostflop` 对 FISH 的引用 |
| 验收用例 | ① 中对 facing 1/2 pot → call ≥ 70% ② flop 偶尔 donk（≤ 15%）③ 河牌无 bluff |
| 与 CALL 差异 | FISH 有少量 raise；CALL raise = 0 |
| 依赖 | Wave 2b |

- [x] 新建 `strategypro/postflop/fish/DpNpcFishPostflopStrategy`（独立决策树，禁止套壳 PassiveStation）
- [x] `DpNpcFishStrategy` 路由到新类；L1 `DpNpcHardConstraints` 经 Facade 接入
- [x] `DpNpcFishPostflopStrategyTest`：听牌跟注、中对宽 call、比 NIT 宽、flop donk ≤15%、河牌极少 bluff
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

#### Wave 3d：CALL 独立翻后

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `DpNpcCallPostflopStrategy` |
| 验收用例 | ① 任意对子 facing bet → call ≥ 85% ② 无主动 raise（100 次 0 raise）③ 翻前 defend 宽于 TAG |
| 依赖 | Wave 3c（便于 FISH/CALL 对比验收） |

- [x] 新建 `strategypro/postflop/call/DpNpcCallPostflopStrategy`（独立决策树，禁止套壳 PassiveStation）
- [x] `DpNpcCallStrategy` 路由到新类；L1 `DpNpcHardConstraints` 经 Facade 接入
- [x] `DpNpcCallPostflopStrategyTest`：中对大注 call > FISH、任意对子宽 call、听牌不弃、0% 主动 raise、坚果不弃
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

#### Wave 3e：LAG 独立翻后

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `DpNpcLagPostflopStrategy`；删除 `LooseAggroPostflop` 对 LAG 的引用 |
| 验收用例 | ① flop cbet ≥ 65% ② 听牌 semi-bluff raise ≥ 20% ③ 空气 facing 大注 → fold ≥ 50% |
| 与 MANIAC 差异 | LAG 空气会 fold；MANIAC 不会 |
| 依赖 | Wave 3a, 3b |

- [x] 新建 `strategypro/postflop/lag/DpNpcLagPostflopStrategy`（独立决策树，禁止套壳 LooseAggro）
- [x] `DpNpcLagStrategy` 路由到新类；LAG 空气 facing 大注比 TAG/MANIAC 更纪律性弃牌
- [x] `DpNpcLagPostflopStrategyTest`：TPTK cbet ≥65%、听牌 semi-bluff raise、空气 fold ≥50% 且 > MANIAC
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

#### Wave 3f：MANIAC 独立翻后

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `DpNpcManiacPostflopStrategy` |
| 验收用例 | ① flop cbet ≥ 80% ② facing bet fold ≤ 10% ③ 低 SPR 频繁 all-in |
| 依赖 | Wave 3e |

- [x] 新建 `strategypro/postflop/maniac/DpNpcManiacPostflopStrategy`（独立决策树，禁止套壳 LooseAggro）
- [x] `DpNpcManiacStrategy` 路由到新类；低 SPR 频繁 jam；空气 facing bet 极少 fold
- [x] `DpNpcManiacPostflopStrategyTest`：flop cbet ≥80%、facing bet fold ≤10%、LAG fold > MANIAC 预埋
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

#### Wave 3g：CUSTOM 独立翻后（L5 偏移器重做）

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `DpNpcCustomPostflopStrategy`；`CustomNpcStyleSnapshot` 不变；`decideCustomBotAction` 改委托 |
| 验收用例 | ① 高 cbetFreq（0.95）vs 低 cbetFreq（0.10）→ cbet 率差异 ≥ 40% ② 高 callStation（0.96）→ 无 raise ③ API 字段名不变 |
| 依赖 | Wave 3a~3f 全部完成 |

- [x] 新建 `strategypro/postflop/custom/DpNpcCustomPostflopStrategy`（六维 L5 偏移独立实现，禁止套壳旧 CustomStrategy）
- [x] `DpNpcCustomStrategy` 路由新类；翻前仍走 UnifiedPreflop
- [x] `DpNpcCustomPostflopStrategyTest`：高 cbet vs 低 cbet ≥40% 差、callStation 0.96 零 raise、foldToPressure 分化
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

---

### 5.4b Wave L4：频繁小加注修复 + 概率弱牌抓诈（hero call）

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `strategypro/l4/DpNpcRaiseEscalation`、`DpNpcHeroCall`；TAG/LAG/NIT/FISH/MANIAC/CUSTOM postflop facing bet raise 统一走 escalation；各型 `tryFoldFacingBet` 接入 hero call；`DpNpcUnifiedPreflopStrategy.decideFacing4Bet` 深码 jam 加成 |
| 工作内容 | ① 读 `raiseLevel`/`lastRaiseIncrement` 递增加注尺度，禁止连续同增量 ② 深码 TRIPS+ commit 不叠加边际惩罚 ③ 河牌弱牌概率 hero call（含 `bluffCatchMore`）④ MANIAC re-raise jam 路径 |
| 验收 | 同街 3 次 re-raise 后第 3 次增量 > 第 1 次；MANIAC raiseLevel≥2 facing bet 有 jam；L1 坚果保护不变 |
| 依赖 | Wave 3g |

#### Wave L4 验收勾选（2026-06-09）

- [x] 新建 `strategypro/l4/DpNpcRaiseEscalation`（读 raiseLevel、反重复增量、深码 commit 修正、MANIAC jam 概率）
- [x] 新建 `strategypro/l4/DpNpcHeroCall`（河牌优先、≤0.55 pot 小中注、bluffCatchMore/可信度加分）
- [x] TAG/LAG/NIT/FISH/MANIAC/CUSTOM facing bet raise 统一调 escalation helper
- [x] TAG/NIT/LAG/FISH/MANIAC/CUSTOM `tryFoldFacingBet` 掷 fold 骰前接入 hero call（CALL 型除外）
- [x] `decideFacing4Bet`：effStackBB≥35 jamProb +15%，MANIAC 再 +10%
- [x] `DpNpcL4RaiseEscalationTest`、`DpNpcL4HeroCallTest` 各 ≥3 场景
- [x] `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

---

### 5.5 Wave 4：MC equity 接入 DpPokerMath（可选）

| 项 | 内容 |
|----|------|
| 改动文件 | 新建 `npc/math/DpPokerMath`；`DpNpcEquityEstimator` 增加冷路径开关；`buildSmartContext` 可选调用 |
| 工作内容 | 热路径仍用 12 档粗桶 `equityEst`；**仅** L1 pot odds 边界 / 河牌大决策走 MC（自研，无外部库） |
| 验收 | `DpPokerMathTest` MC 结果与已知场景误差 < 5%；热路径性能无退化（决策 < 1ms） |
| 回滚 | `RuleNpcConfig.MC_EQUITY_ENABLED = false` |
| 依赖 | Wave 2b |

---

### 5.6 Phase 5：废弃旧 strategy、删代码、更新 docs

| 项 | 内容 |
|----|------|
| 改动文件 | 删除 `npc/strategy/DpNpcTightAggroPostflop` 等 3 壳类；删除旧 `npc/strategy/` 空壳；更新 `docs/ai/npc-engine/README.md`、`02_normal_npc_implementation.md` |
| 验收 | ① `grep TightAggroPostflop` 无结果 ② 全量测试绿 ③ 文档索引指向 `strategypro` |
| 依赖 | Wave 3g + Wave 4（若做） |

#### Phase 5 验收勾选（2026-06-08）

- [x] 删除 `strategypro/DpNpcTightAggroPostflop.java`、`DpNpcLooseAggroPostflop.java`、`DpNpcPassiveStationPostflop.java`
- [x] 删除整个 `npc/strategy/` deprecated 转发目录（12 文件）
- [x] `grep TightAggroPostflop|LooseAggroPostflop|PassiveStationPostflop` 在 `*.java` 无引用
- [x] `mvn compile` + `mvn test -Dtest=com.example.mgdemoplus.npc.**` 全绿

---

## 6. DpNpcEngine 切换与 Facade 接口草案

### 6.1 设计目标

- `DpNpcEngine` **仅一处**委托策略包，不散落 `switch (type)` 到各策略 import
- Phase 1 行为不变：Facade 内部仍调用迁包后的同名类
- Wave 3 后：Facade 路由到各 `*PostflopStrategy`

### 6.2 接口草案

```java
// strategypro/facade/DpNpcStrategyFacade.java
public interface DpNpcStrategyFacade {
    /** 翻前统一出口 */
    BotAction decidePreflop(DpNpcDecisionContext ctx);

    /** 翻后分 archetype */
    BotAction decidePostflop(DpNpcDecisionContext ctx);
}

// strategypro/facade/DpNpcDecisionContext.java
// 封装现有 DpNpcRuleDecisionParams + StyleProfile + BotType
public final class DpNpcDecisionContext {
    public final DpNpcRuleDecisionParams params;
    public final StyleProfile style;
    public final BotType botType;
    // 工厂：从 decideBotAction 局部变量组装
}

// strategypro/facade/DpNpcStrategyProvider.java
public final class DpNpcStrategyProvider {
    private static final DpNpcStrategyFacade INSTANCE = new DpNpcStrategyFacadeImpl();

    public static DpNpcStrategyFacade get() { return INSTANCE; }
}
```

### 6.3 Facade 实现伪代码

```java
final class DpNpcStrategyFacadeImpl implements DpNpcStrategyFacade {

    @Override
    public BotAction decidePreflop(DpNpcDecisionContext ctx) {
        return DpNpcUnifiedPreflopStrategy.decide(
            ctx.params.room, ctx.params.bot,
            ctx.params.callAmount, ctx.params.callRatio,
            ctx.style.vpip, ctx.style.pfr,
            ctx.style.callStation, ctx.style.foldToPressure,
            ctx.params.random, ctx.botType);
    }

    @Override
    public BotAction decidePostflop(DpNpcDecisionContext ctx) {
        return switch (ctx.botType) {
            case FISH   -> DpNpcFishPostflopStrategy.decide(ctx.params);
            case CALL   -> DpNpcCallPostflopStrategy.decide(ctx.params);
            case LAG    -> DpNpcLagPostflopStrategy.decide(ctx.params);
            case MANIAC -> DpNpcManiacPostflopStrategy.decide(ctx.params);
            case TAG    -> DpNpcTagPostflopStrategy.decide(ctx.params);
            case NIT    -> DpNpcNitPostflopStrategy.decide(ctx.params);
            default     -> new BotAction(BotActionType.CALL_OR_CHECK, 0);
        };
    }
}
```

> Phase 1 时 `*PostflopStrategy` 尚未独立，可暂委托迁包后的 `DpNpcXxxStrategy.decide`（仍含旧套壳）；Wave 3 逐波替换。

### 6.4 Engine 委托伪代码

```java
// DpNpcEngine.decideBotAction — 改造后
private static BotAction decideBotAction(DpRoomBO room, DpPlayer bot, BotType type) {
    // ... chips / callAmount / style / handSnapshot 组装不变 ...

    DpNpcDecisionContext ctx = DpNpcDecisionContext.of(
        ruleParams, style, type);

    if ("preflop".equals(stageForNpc)) {
        BotAction pre = DpNpcStrategyProvider.get().decidePreflop(ctx);
        return pre != null ? pre : new BotAction(BotActionType.CALL_OR_CHECK, 0);
    }

    // CUSTOM 走 decideCustomBotAction（已有独立入口）
    if (isCustomBotNickname(bot.getNickname())) {
        return decideCustomBotAction(room, bot); // 内部同样委托 Facade
    }

    return DpNpcStrategyProvider.get().decidePostflop(ctx);
}
```

---

## 7. 测试策略

### 7.1 测试分层

| 层 | 范围 | 工具 |
|----|------|------|
| 单元 | `DpNpcHandClassifier`、`DpNpcHardConstraints`、`DpNpcPreflopHandGrouper` | JUnit 5 |
| 公式 | `DpNpcPostflopFormula`、`DpNpcEquityEstimator` | 已有 `*Test` |
| 集成 | 各 archetype `decideActionIfReady` 端到端 | 新建 `DpNpcStrategyProGoldenTest` |
| 回归 | Phase 1 迁包前后 BotAction 快照 | 新建 `DpNpcMigrationParityTest` |

### 7.2 Golden-path 用例清单

| # | 场景 | archetype | 期望 |
|---|------|-----------|------|
| G1 | 皇家同花顺，河牌 facing 3/4 pot bet | 全部 | **永不 FOLD** |
| G2 | 坚果葫芦，turn facing bet | 全部 | **永不 FOLD** |
| G3 | 高牌，河牌对手 check（callAmount=0） | 全部 | **永不 FOLD**（check） |
| G4 | playingTheBoard（公牌满堂红），hero 无贡献 | 全部 | 不价值加注；equity ≤ 0.22 |
| G5 | BTN 72o，UNOPENED | NIT | FOLD / check |
| G6 | BTN 72o，UNOPENED | MANIAC | RAISE |
| G7 | 顶对弱踢脚，facing 3/4 pot | TAG | call 为主 |
| G8 | 顶对弱踢脚，facing 3/4 pot | NIT | fold ≥ 60% |
| G9 | 中对，facing 1/2 pot | FISH | call ≥ 70% |
| G10 | 中对，facing 1/2 pot | CALL | call ≥ 85% |
| G11 | flop 空气，no bet | LAG | cbet ≥ 50% |
| G12 | flop 空气，no bet | MANIAC | cbet ≥ 75% |
| G13 | flop 空气，facing bet | LAG | fold ≥ 40% |
| G14 | flop 空气，facing bet | MANIAC | fold ≤ 15% |
| G15 | CUSTOM cbetFreq=0.95 vs 0.10 | CUSTOM | cbet 率差 ≥ 40% |
| G16 | CUSTOM callStation=0.96 | CUSTOM | 100 次 0 raise |

每 archetype 至少覆盖 **2 个专属场景**（G5–G16 已覆盖）。

### 7.3 `mvn test` 范围建议

```bash
# Phase 1 全量回归
mvn test -Dtest="com.example.mgdemoplus.npc.**"

# Wave 2b L1 硬约束
mvn test -Dtest=DpNpcHardConstraintsTest,DpNpcStrategyProGoldenTest

# Wave 3 单 archetype
mvn test -Dtest=DpNpcTagPostflopStrategyTest
mvn test -Dtest=DpNpcNitPostflopStrategyTest
# ... 依此类推

# Wave 4 MC（若做）
mvn test -Dtest=DpPokerMathTest,DpNpcEquityEstimatorTest

# 迁包parity
mvn test -Dtest=DpNpcMigrationParityTest
```

**测试约定**：

- 所有决策测试 **关闭 rule-think**（`DpNpcRuleThinkSampler.bind(enabled=false)`），与现有 `DpNpcCustomBotTest` 一致
- 使用 `room.setCurrentHandSeed(seed)` 固定随机性
- 断言 `BotActionType` + `amount`（金额允许 ±1BB 整数吸附误差）

---

## 8. 风险 / 假设 / 不在范围

### 8.1 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| Phase 1 迁包遗漏 import | 编译失败 | `mvn compile` + 全量 test |
| 循环依赖解耦改变 G1–G8 分类 | 翻前行为漂移 | `DpNpcPreflopHandGrouper` 单测 + parity test |
| Wave 3 逐波导致中间态套壳残留 | 架构债 | Phase 5 强制删除；每波验收 grep 旧类引用 |
| CUSTOM 六维 API 变更 | 前端破坏 | **假设 API 不变**；仅后端策略重做 |
| MC equity 性能 | 心跳延迟 | 仅冷路径；热路径粗桶不变 |

### 8.2 假设

| 假设 | 说明 |
|------|------|
| 前端 CUSTOM 滑条 API 不变 | `CustomNpcStyleProfileDto` 六字段保持 |
| `StyleProfile` preset 数值不变 | 除非单独立项调参 |
| `HandPlan` 框架保留在 engine | TAG / LAG 继续使用 |
| 六人桌 / 盲注结构不变 | 翻前 `lateFactor` 公式有效 |
| `dp.npc.rule-think` 不变 | 测试时禁用 |

### 8.3 明确不在范围

- `BOT_LLM` / `DpLlmNpcDecisionService` 任何改动
- Flyway 迁移脚本
- 前端 `dp_game` 任何文件
- `DpPlayerStats` / 对手建模大改
- 成就系统、牌谱展示
- 引入外部 Maven 库（MC 自研）

---

## 9. 给实现 Agent 的 Wave 1（Phase 1）提示词附录

> 复制以下整块给实现 Agent。硬约束：禁止 commit；Element UI 不涉及；交付本地变更 + 验证步骤。

```markdown
## 任务：Phase 1 — strategypro 迁包（零行为变更）

### 硬约束
- **禁止** `git commit` / `git push`
- **禁止** 修改业务逻辑（仅搬家 + 接线 + 解耦）
- **禁止** 修改前端 / Flyway / LLM 相关代码
- **仅允许** 改动：`npc/strategypro/**`（新建）、`npc/eval/DpNpcPreflopHandGrouper.java`（新建）、
  `npc/eval/DpNpcHandClassifier.java`、`npc/engine/DpNpcEngine.java`（委托接线）、
  迁移动作的旧路径文件（move）、测试文件（若 import 路径变化）
- 不改 `application.yml`

### 步骤
1. 创建包 `com.example.mgdemoplus.npc.strategypro` 及子包 `facade`、`preflop`
2. 将以下 12 个类 **原样搬迁**（可重命名 postflop 壳但 Phase 1 行为必须一致）：
   - DpNpcUnifiedPreflopStrategy → strategypro/preflop/
   - DpNpcFishStrategy, DpNpcCallStrategy, DpNpcLagStrategy, DpNpcManiacStrategy,
     DpNpcTagStrategy, DpNpcNitStrategy, DpNpcCustomStrategy → strategypro/
   - DpNpcTightAggroPostflop, DpNpcLooseAggroPostflop, DpNpcPassiveStationPostflop → strategypro/（暂留）
   - DpNpcRuleDecisionParams → strategypro/
3. 新建 `eval/DpNpcPreflopHandGrouper`：
   - 从 DpNpcUnifiedPreflopStrategy 抽出 `groupOf`、`preflopCategoryOf`
   - DpNpcHandClassifier 改引 Grouper，**消除** eval→strategy 循环依赖
4. 新建 Facade：
   - DpNpcStrategyFacade（接口）
   - DpNpcStrategyFacadeImpl（Phase 1 内部仍调迁包后的旧逻辑）
   - DpNpcStrategyProvider（单例）
   - DpNpcDecisionContext（封装 RuleDecisionParams + StyleProfile + BotType）
5. 修改 DpNpcEngine.decideBotAction（L1993–2072）：
   - 翻前 → `DpNpcStrategyProvider.get().decidePreflop(ctx)`
   - 翻后 switch → `DpNpcStrategyProvider.get().decidePostflop(ctx)`
   - decideCustomBotAction 同步改委托
6. 旧路径 `npc/strategy/` 保留 **deprecated 空壳 re-export** 或直接删除（以编译通过为准）
7. 修复所有 import；全仓库 grep 确保无断裂引用

### 验证步骤（必须执行并汇报结果）
```bash
mvn compile -q
mvn test -Dtest="com.example.mgdemoplus.npc.**"
```

### 验收标准
- [ ] 编译通过
- [ ] 全量 npc 测试绿
- [ ] DpNpcHandClassifier 不再 import strategypro/preflop 以外的 strategy 类
- [ ] decideBotAction 仅通过 Facade 调用策略
- [ ] 随机抽 5 组同种子决策，与迁包前 BotAction 一致（若无法对比，至少无新增测试失败）

### 交付
- 本地文件变更列表
- 上述命令输出摘要
- 未解决问题（若有）
```

---

## 附录 A：相关源码速查

| 文件 | 职责 |
|------|------|
| `DpNpcEngine.java` L1993–2072 | `decideBotAction` 入口 |
| `DpNpcUnifiedPreflopStrategy.java` | 翻前 0/1 矩阵 + G1–G8 |
| `postflop/*/DpNpc*PostflopStrategy.java` | 7 套独立翻后策略（含 CUSTOM L5 偏移） |
| `DpNpcHandClassifier.java` L95 | 循环依赖点 |
| `CustomNpcStyleProfileDto.java` | CUSTOM 六维 API |
| [npc-preflop-unified-decision-flow.md](../ai/npc-preflop-unified-decision-flow.md) | 翻前流程专文 |

## §10 用户体感问题与 Wave 映射

> Phase 1 **不改行为**，仅记录已确认的用户体感问题及后续修复波次，供 Wave 2+ 验收对照。

| # | 问题描述 | 疑似根因 | 对应修复 Wave | 验收口径 |
|---|----------|----------|---------------|----------|
| 1 | **翻前遇小额 open 全员过紧**：各性格大量弃牌，仅留强范围；可被便宜 raise + 翻后小对子在低面（无 AKQJ）拿捏 | `vsOpenContinueAllow` 矩阵偏紧；`rangeLevel` / `foldToPressure` 叠加过保守；面对小额 open 未按 pot odds 放宽 defend | **Wave 2a** ✅ 已修复 | 同手牌 BTN 中对 / 小对子 facing min-raise：FISH/CALL defend ≥ TAG；`defendLevelBonus` + ≤2BB 跟注额加成；`DpNpcPreflopDefendTest` 覆盖 |
| 2 | **该凶不凶、普遍慢打**：强牌爱 check，很少价值下注，多性格雷同 | TAG/NIT 共用 `TightAggroPostflop`、FISH/CALL 共用 `PassiveStationPostflop`、LAG/MANIAC 共用 `LooseAggroPostflop`；`cbetPotFactor` 与 lead 概率偏低且 archetype 无分叉 | **Wave 3** 各 archetype 独立价值线 + cbet 逻辑 | 同场景（顶对好踢脚 flop no bet）TAG cbet ≥ 70%、NIT ≤ 30%、MANIAC ≥ 75%；相邻 archetype ≥2 场景行动分布显著不同 |
| 3 | **raise 尺度机械**：虽有浮动但与盲注对齐导致 12/13/14 压回 10 | `DpNpcUnifiedPreflopStrategy.baseOpenSizeBB` + `roundToSB` 吸附；翻后 `cbetPotFactor` 取整后再 `roundToSB` / `max(bb)` 二次对齐 | **Wave 2a** ✅ open 已修复 / **Wave 3** 翻后 cbet 待做 | 翻前 open：`roundRaiseToBlind` 半 BB 步进 + 连续化 `baseOpenSizeBB`；分布含 2.5BB/3.5BB 等；翻后 cbet 取整仍待 Wave 3 |
| 4 | **该弃不弃 / 该硬刚怂**：高牌力未中面仍跟大注、多人池不收紧；nuts 却被 jam 吓跑 | 缺 L1 硬约束；`multiwayFoldProbBoost` 未充分生效；套壳 postflop 对坚果 / 免费看牌无保护分支 | **Wave 2b** L1 硬约束 + **Wave 3** multiway 因子 + 坚果保护 | G1/G2 golden-path：皇家同花顺 / 坚果葫芦 facing bet 永不 fold；playingTheBoard 不价值加注；多人池空气 facing 大注 fold 率高于 HU |

---

## 附录 B：修订记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-06-08 | v1.0 | 初稿：用户确认决策 + 7 型独立策略 + 分波计划 |
| 2026-06-08 | v1.1 | Phase 1 完成：新增 §10 用户体感问题与 Wave 映射 |
| 2026-06-08 | v1.2 | Wave 2a 完成：翻前 defend 放宽 + open 尺度修复 + `DpNpcPreflopDefendTest` |
