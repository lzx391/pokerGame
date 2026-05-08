# 4. Shark（`BOT_Shark`）逐步实现说明

代码路径：`DpNpcEngine.decideBotAction` → `case SHARK` → 翻前模块 / 翻后 **`DpNpcSharkStrategy.decide`**。

---

## 4.1 与其它类型相同的公共前置

与 [02_normal_npc_implementation.md](02_normal_npc_implementation.md) 相同：`callAmount`、`position`、`strength`（真值牌力档）、`boardDanger`、`mood`（默认 0）、`StyleProfile`（Shark 映射为 **`LOOSE_AGGRO`**，与 Maniac 共用同一套风格**数值表**，但决策树不同）。

---

## 4.2 翻前（`stage == preflop`）

1. 调用 **`DpNpcSharkPreflopStrategy.decideForShark(...)`**。
2. 该模块内部（详见 [05_shark_modules.md](05_shark_modules.md)）大致是：  
   - 起手牌 → **`HandGroup`**（G1～G8）；  
   - 人数、位置、有效深度、情绪、`preflopTightness` 等 → **`rangeLevel`（1～8）**；  
   - 再按当前 spot（无人 open / 面对 open / 面对 3bet / 更高压力）决定 **fold / call / raise / all-in**。
3. 若返回非 null，**直接作为本步动作**返回给房间服务。

---

## 4.3 翻后（flop / turn / river）

1. **`DpNpcSharkStrategy.decide`**（约定：若仍是 preflop 则返回 null，实际 preflop 已在引擎内处理完）。
2. **公共牌主导修正**：若 `isBoardDominantBestHand` 为真，会下调一档「自信牌力」，避免把板面强当成自己强。
3. **`buildSmartContext`**：与 Maniac/TAG 同源，但 Shark 在后续逻辑里会**完整使用**赔率、赢率桶、对手画像、multi-way、counter-strategy 等。
4. **HandPlan 初始化**：仅在 **flop**、且本手尚未写过计划时，调用 **`initHandPlanIfNeededForPostflop`**：  
   - 先按牌力 + 牌面干湿 + 人数生成 **VALUE / BLUFF / POT_CONTROL / GIVE_UP** 与 **barrels**、**aggression**；  
   - **仅 Shark** 会再调用 **`DpNpcSharkExploitHandPlan.tuneAfterBasePlan`**：根据对手统计、`DpNpcSharkLearningLab` 旋钮、主对手昵称（如识别 `BOT_Fish` 为跟注站）调整计划类型与开枪数。
5. **后续街纠正**：**`updateHandPlanForLaterStreetIfNeeded`**（当前实现主要服务 Shark）在 turn/river 若牌力明显变强，可把过于保守的计划拉回价值线并补足 barrel。
6. **剩余决策**：同在 `DpNpcSharkStrategy` 内，结合 **`SharkStrategyProfile` / `SharkConfig`**、筹码深度、位置、c-bet、河牌 block 等输出 `BotAction`。

---

## 4.4 与 TAG 的关键差异（便于对照）

| 维度 | TAG | Shark |
|------|-----|--------|
| 翻前 | `decidePreflopForTagOrShark` + `PREFLOP_TAG_PROFILE` | **`DpNpcSharkPreflopStrategy` 专用** |
| 翻后主逻辑 | `DpNpcEngine` 内 `case TAG` | **`DpNpcSharkStrategy`** |
| Flop 剥削剧本 | 无 | **`DpNpcSharkExploitHandPlan`** |
| 学习旋钮 | 无 | **`DpNpcSharkLearningLab` + DB** |
| 牌力/赔率人为噪声 | 无 | 无（与其它规则 NPC 一致；策略实现仍见 `DpNpcSharkStrategy`） |

---

## 4.5 调试输出

工程中可能保留 `[SHARK]` 类调试输出（部分注释掉）；若需复盘，可结合 `DpNpcSharkHandActionLog`（仅桌上有 Shark 时、本手内存）的逐街记录。

下一篇：[05_shark_modules.md](05_shark_modules.md)。
