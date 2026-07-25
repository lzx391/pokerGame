# 多 Archetype 翻前 Trace + 8bit 右侧 Terminal Dock 计划

> 状态：**计划文档（待实现）**  
> 前置：TAG trace P0 已落地（[`npc-decision-trace-plan.md`](./npc-decision-trace-plan.md)、默认主题 action 矩阵 [`trace-action-grid-ui-plan.md`](./trace-action-grid-ui-plan.md)）  
> 关联：TAG 翻后精细化 [`npc-trace-tag-postflop-refinement-plan.md`](./npc-trace-tag-postflop-refinement-plan.md)（本计划不阻塞；翻后推广在其验收后）  
> 8bit 范式参考：[`retro8bit-invite-friends-panel-plan.md`](./retro8bit-invite-friends-panel-plan.md)（右侧滑入 shell，不遮牌桌）

---

## 1. 目标 / 非目标 / 验收

### 1.1 产品目标

1. **翻前**：除 TAG 外，其他**规则型性格 NPC**（Fish / Maniac / LAG / NIT / Call 等）决策 trace 在前端可见，**照抄 TAG 显示思路**（dock、四街 × NPC 矩阵、REST pull、WS `npcDecisionTraceHand` merge）。
2. **8bit 主题**：修决策分析 UI — **右侧显示/滑入 terminal**，不影响正常对局（不要全屏挡牌桌）；信息架构对齐默认主题 dock，皮肤 CRT 化。

### 1.2 技术目标

| 目标 | 说明 |
|------|------|
| 最小 Backend diff |  primarily `DpNpcEngine` 两处 TAG gate；翻前 steps 已在 `DpNpcUnifiedPreflopStrategy` |
| 契约向后兼容 | REST `/dpRoom/npcDecisionTrace/*`、WS `_ws:npcDecisionTraceHand` schema 不变；可选追加 `botArchetype` |
| 零行为漂移 | `DpNpcTagDecisionTraceStore.ENABLED=false` 时决策与现网一致；trace 不改变 `BotAction` |
| 8bit 复用 Body | 消灭 `GameNpcDecisionTracePanel` 与 `GameNpcDecisionTraceBody` 双份导航；矩阵 L2 + 详情 L3 一套代码 |

### 1.3 非目标

- **翻后** trace 推广到 Fish/Maniac 等（留给 TAG 翻后精细化验收后）
- **LLM NPC**（`BOT_LLM*` / `BOT_LLM_GLOBAL*`）trace
- **BOT_CUSTOM** trace（P1 可选；独立 `decideCustomBotAction` 路径）
- DB / Flyway / 新 WS 消息类型
- 修改翻前决策逻辑、矩阵内容或 `rangeLevel` 公式
- 8bit 窄屏矩阵专项 UX（P1 仅保证 sheet 可用）

### 1.4 验收标准

#### 多 archetype 翻前

| # | 场景 | 必须通过 |
|---|------|----------|
| M1 | 房间同时有 TAG + MANIAC，翻前均有行动 | WS/REST 含两手 actions；矩阵 **两行** NPC |
| M2 | 仅 FISH 行动 | steps 含 `CONTEXT` + `MATRIX`；`preflopMatrix.cells` 非空 |
| M3 | Maniac open spot | `matrixKind` / `rangeLevel` 与 TAG 同 spot 可辨差异 |
| M4 | TAG 回归 | 现有 TAG case steps/matrix **无回归** |
| M5 | LLM bot 行动 | **无** trace 记录 |
| M6 | `ENABLED=false` | 决策结果与现网一致 |

#### 8bit 右侧 dock

| # | 场景 | 必须通过 |
|---|------|----------|
| R1 | 宽屏 retro8bit 打开「分析决策」 | 右侧 terminal 出现，**牌桌完整可见、可操作** |
| R2 | 对局进行中开/关 dock | 可正常 fold/raise/check；**无**全屏遮罩 |
| R3 | 矩阵点选 → 详情 | 与默认主题 L2/L3 行为一致（REST pull action detail） |
| R4 | Owner Hub / 实验密码 gate | 打开 dock 而非 center overlay |
| R5 | 切回默认主题 | dock 行为与现网一致 |

---

## 2. 现状探索摘要

### 2.1 TAG trace 挂钩点（Backend）

| 位置 | 文件 | 作用 | 现状限制 |
|------|------|------|----------|
| **begin** | `DpNpcEngine.decideBotAction` (~L2022) | 决策前开启 ThreadLocal collector | `type == BotType.TAG` |
| **build + append** | `DpNpcEngine.decideActionIfReady` (~L1667) | 决策后写入 store | `traceType == BotType.TAG` |
| **clear** | 同上 `finally` (~L1679) | 清理 ThreadLocal | 已对所有 bot 执行 |
| **翻前 steps** | `DpNpcUnifiedPreflopStrategy` `tracePreflopContext` / `tracePreflopReturn` | CONTEXT / MATRIX / RESULT + matrix 附件 | 仅检查 `collector.current() != null`，**无 archetype 限制** |
| **L1 硬约束** | `DpNpcHardConstraints` (~L160) | `L1_BLOCK_FOLD` step | `botType == TAG`（翻后为主） |
| **翻后 steps** | `DpNpcTagPostflopStrategy` | POSTFLOP trace | 仅 TAG；**本需求非目标** |
| **生命周期** | `DpRoomServiceImpl` | `beginHand` / `sealHand` / `removeRoom` | 与 bot 类型无关 |
| **Store append** | `DpNpcTagDecisionTraceStore.appendAction` | 按 action 追加 | **不**按 TAG 过滤 |

**结论**：翻前 trace 采集已在统一翻前策略实现；瓶颈仅在 `DpNpcEngine` 两处 TAG gate。

### 2.2 各 archetype 翻前策略路径

翻前**没有**独立 `*PreflopStrategy`  per archetype，全部走：

```
DpNpcEngine.decideBotAction
  → DpNpcStrategyProvider.get().decidePreflop(ctx)
  → DpNpcStrategyFacadeImpl.decidePreflop
  → DpNpcUnifiedPreflopStrategy.decide(..., botType)
```

`botType` 通过 `rangeLevelBonus` / `defendLevelBonus` 等选不同 13×13 矩阵档。

| BotType | 翻后入口（本阶段不采集 trace） |
|---------|--------------------------------|
| FISH | `DpNpcFishStrategy` → `postflop/fish/DpNpcFishPostflopStrategy` |
| CALL | `DpNpcCallStrategy` → `postflop/call/DpNpcCallPostflopStrategy` |
| LAG | `DpNpcLagStrategy` → `postflop/lag/DpNpcLagPostflopStrategy` |
| MANIAC | `DpNpcManiacStrategy` → `postflop/maniac/DpNpcManiacPostflopStrategy` |
| TAG | `DpNpcTagStrategy` → `postflop/tag/DpNpcTagPostflopStrategy` |
| NIT | `DpNpcNitStrategy` → `postflop/nit/DpNpcNitPostflopStrategy` |
| CUSTOM | `decideCustomBotAction` → `DpNpcCustomStrategy`（P1） |

规则 bot 昵称前缀：`BOT_FISH_` / `BOT_MANIAC_` / `BOT_TAG_` / `BOT_LAG_` / `BOT_NIT_` / `BOT_CALL_`（`DpNpcEngine.getBotTypeByNickname`）。

### 2.3 前端 Default Dock vs 8bit Panel

| 维度 | 默认主题 Dock | 8bit Panel（现状） |
|------|---------------|-------------------|
| 组件 | `GameNpcDecisionTraceDock` + **`GameNpcDecisionTraceBody`** | **`GameNpcDecisionTracePanel`**（独立三屏 UI） |
| 布局 | `game.vue` → `dp-game-body-row` **右侧 flex 列**，牌桌左缩 | `position: fixed; inset: 0` **全屏遮罩** |
| 宽屏 | `useDecisionTraceDockWide` + 顶栏 toggle pin | Owner Hub 打开 center overlay |
| 窄屏 | `GameBottomSheet` | 同上 overlay |
| L2 | 四街 × NPC **矩阵**（`GameNpcDecisionTraceActionGrid`） | 线性 **action-list** |
| L3 | Body 内 steps + matrix grid | Panel 内重复逻辑 |
| 文案 | 多处硬编码 **TAG** | 标题 `DECISION_TRACE // TAG` |

布局 CSS：`front/dp_game/src/styles/dp-game-shell.css`（`dp-game-body-row`、`dp-game-root--trace-dock-open`）。  
8bit 侧滑参考：`GameInviteFriendPanel` / `GameFriendChatPanel`（右侧 shell，不 intercept 牌桌）。

---

## 3. P0 / P1 拆分

### P0（必须交付）

| ID | 域 | 内容 |
|----|-----|------|
| P0-B1 | Backend | `DpNpcEngine` 规则 bot trace 资格（6 档 preset） |
| P0-B2 | Backend | 单测：Fish/Maniac 翻前 action 含 `preflopMatrix` + steps |
| P0-F1 | Frontend 默认 | 文案去 TAG 化；矩阵展示多 NPC 行 |
| P0-F2 | Frontend 8bit | 宽屏右侧 dock + 复用 `GameNpcDecisionTraceBody` |
| P0-F3 | Frontend 8bit | 关闭全屏 Panel 主路径；Owner Hub / gate 改开 dock |

### P1（后续）

| ID | 内容 |
|----|------|
| P1-B1 | `botArchetype` 字段；`DpNpcHardConstraints` trace 扩 archetype |
| P1-B2 | `DpNpcTagDecisionTrace*` 重命名为 `DpNpcRuleDecisionTrace*` |
| P1-F1 | 8bit 顶栏 toggle、窄屏 sheet、Matrix `variant="retro8bit"` 打磨 |
| P1-F2 | BOT_CUSTOM / 翻后多 archetype trace |

---

## 4. Backend 任务

### 4.1 Trace 资格（P0-B1）

**文件**：`src/main/java/com/example/mgdemoplus/npc/engine/DpNpcEngine.java`

新增 helper（包内 private static）：

```java
private static boolean isRuleBotTraceEligible(BotType type) {
    return type == BotType.FISH || type == BotType.CALL || type == BotType.LAG
        || type == BotType.MANIAC || type == BotType.TAG || type == BotType.NIT;
}
```

替换：

- `decideBotAction`：`begin` 条件 `ENABLED && isRuleBotTraceEligible(type)`
- `decideActionIfReady`：`append` 条件 `ENABLED && isRuleBotTraceEligible(traceType)`

**不**对 `decideCustomBotAction` / LLM 路径 begin。

### 4.2 已有采集点（无需改）

| 文件 | 说明 |
|------|------|
| `npc/strategypro/preflop/DpNpcUnifiedPreflopStrategy.java` | `tracePreflopContext` / `tracePreflopReturn` |
| `npc/trace/preflop/DpNpcPreflopMatrixExporter.java` | matrix 快照 |
| `npc/trace/DpNpcTagDecisionTraceStore.java` | 内存 ring buffer |
| `npc/trace/DpNpcTagDecisionTracePushService.java` | seal 后 WS 推房主 |
| `controller/DpNpcDecisionTraceController.java` | REST query |

### 4.3 可选 enrich（P1-B1）

- `DpNpcActionTraceSummary.botArchetype`：`BotType.name()`，由 `DpNpcTagDecisionTraceCollector.build` 从 nickname 解析写入
- 前端矩阵 NPC 列头可显示 `[MANIAC] BOT_MANIAC_1`；P0 可 fallback `getBotTypeByNickname` 等价逻辑

### 4.4 测试（P0-B2）

- 扩展 `DpNpcUnifiedPreflopTraceMatrixTest` 或新建 `DpNpcRuleBotPreflopTraceTest`：Fish + Maniac 各 1 case
- 回归 `DpNpcTagDecisionTraceStoreTest`

---

## 5. Frontend 任务

### 5.1 默认主题 — 多 archetype（P0-F1）

| 文件 | 改动 |
|------|------|
| `GameNpcDecisionTraceDock.vue` | 移除固定 `<span class="dp-trace-dock__tag">TAG</span>` |
| `GameNpcDecisionTraceBody.vue` | empty：「与规则 NPC 打完一手…」；matrix empty 去 TAG |
| `GameNpcDecisionTraceActionGrid.vue` | 「本手暂无 TAG 行动」→「本手暂无规则 NPC 行动记录」 |
| `GameOwnerToolModal.vue` / `GameOwnerHubContent.vue` | 「规则 NPC」替代「TAG NPC」 |
| `utils/dpNpcDecisionTrace.js` / `dpNpcDecisionTraceMatrix.js` | 注释去 TAG-only |

矩阵按 `actions[].actorNickname` 分行，**无需** archetype 过滤。

### 5.2 8bit — 右侧 Terminal Dock（P0-F2 / P0-F3）

#### 8bit 零破坏对局布局原则

1. **Dock 是 `dp-game-body-row` 的 flex 兄弟**，不是 `position:fixed` 盖在牌桌上。
2. 打开 dock 时牌桌 `flex:1` 缩小，顶栏/底栏/hero dock **文档流不变**。
3. z-index：dock 低于 modal（`decisionTrace` 9310 以下），不 intercept 牌桌 click（除 dock 自身）。
4. 关闭 dock 后布局与打开前一致（仅主区宽度恢复）。
5. showdown TV 期间允许 dock 开着（产品可选默认关）。

#### 实现清单

| # | 文件 | 改动 |
|---|------|------|
| F-8-1 | `game.vue` | 放宽 `gameUiTheme !== 'retro8bit'` 对 wide dock 的 `v-if`；retro8bit 同样 mount `GameNpcDecisionTraceDock` 于 `dp-game-body-row` |
| F-8-2 | `game.vue` | `openDecisionTracePanel`：retro8bit 调用 `activateDecisionTraceDock()`，不再 `showNpcDecisionTracePanel = true` |
| F-8-3 | `GameNpcDecisionTraceDock.vue` | prop `variant: 'default' \| 'retro8bit'`；retro CRT head / scanline / monospace |
| F-8-4 | `GameNpcDecisionTraceDock.vue` | body **仍用** `GameNpcDecisionTraceBody`（含矩阵 UI） |
| F-8-5 | `GameDpGameSheets.vue` | 移除或 deprecated `GameNpcDecisionTracePanel` mount（L291–302） |
| F-8-6 | `dp-game-shell.css` | `[data-dp-game-theme='retro8bit']` + `dp-game-root--trace-dock-open` 联选择器 |
| F-8-7 | `GameNpcDecisionTracePanel.vue` | P0 不再作为主 UI；P1 删除或保留 deprecated 薄壳 |

#### 窄屏 / 顶栏（P1-F1）

- retro8bit 窄屏：复用默认 `GameBottomSheet` sheet 模式（`GameDpGameSheets.vue` dock sheet）
- 可选：8bit 顶栏「决策追踪」toggle（`GameTopBar.vue` 现仅非 retro8bit 显示）

---

## 6. 执行顺序

```
Phase 1 — Backend (P0-B1, P0-B2)
    ↓ handoff：curl/WS 样例含 BOT_FISH_1 / BOT_MANIAC_1 preflop matrix
Phase 2 — Frontend 默认主题 (P0-F1)
    ↓ 验证矩阵多 NPC 行、REST pull、WS merge
Phase 3 — Frontend 8bit (P0-F2, P0-F3)
    ↓ 右侧 dock、关 overlay、Owner Hub 入口
Phase 4 — P1 打磨（命名 refactor、8bit 窄屏、botArchetype、CUSTOM）
```

**建议 Agent 顺序**：`Backend → Frontend default 验证 → Frontend 8bit`

---

## 7. 三条关键决策

1. **Backend 只改 Engine 两处 gate，不改 `DpNpcUnifiedPreflopStrategy`**  
   翻前 trace 已挂在统一策略；放宽 `begin/append` 到 6 档规则 bot 即可让 Fish/Maniac 等自动带上 matrix，成本最低。

2. **Frontend 多 NPC 靠现有「按 `actorNickname` 分行」矩阵，P0 不必新 API**  
   Store/REST 已支持多 actor；P0 主要是去 TAG 文案 + 验证数据。`botArchetype` 放 P1。

3. **8bit 放弃全屏 Panel，改为与默认相同的 `dp-game-body-row` 右侧 dock**  
   复用 `GameNpcDecisionTraceBody`（含矩阵 UI），仅加 retro 皮肤；牌桌 flex 缩小而非 overlay。

---

## 8. 风险与回滚

| 风险 | 缓解 |
|------|------|
| Store 体积增大（多 bot trace/手） | 仍每 room 100 手 ring；单 action 体积与 TAG 同量级 |
| 8bit flex 挤压牌桌 | dock `width: min(320px, 28vw)` 与默认一致 |
| Panel 删除影响入口 | 无 deep link；Owner Hub 统一改 dock |

**回滚**：`DpNpcTagDecisionTraceStore.ENABLED=false`；前端 revert retro8bit mount 条件。

---

## 9. 文件索引

| 类型 | 路径 |
|------|------|
| Engine gate | `src/main/java/com/example/mgdemoplus/npc/engine/DpNpcEngine.java` |
| 翻前 trace | `src/main/java/com/example/mgdemoplus/npc/strategypro/preflop/DpNpcUnifiedPreflopStrategy.java` |
| Collector / Store | `src/main/java/com/example/mgdemoplus/npc/trace/DpNpcTagDecisionTraceCollector.java`, `DpNpcTagDecisionTraceStore.java` |
| REST | `src/main/java/com/example/mgdemoplus/controller/DpNpcDecisionTraceController.java` |
| 默认 Dock | `front/dp_game/src/components/GameNpcDecisionTraceDock.vue` |
| 共用 Body | `front/dp_game/src/components/GameNpcDecisionTraceBody.vue` |
| 8bit Panel（待退役） | `front/dp_game/src/components/GameNpcDecisionTracePanel.vue` |
| 布局 | `front/dp_game/src/components/game.vue`, `front/dp_game/src/styles/dp-game-shell.css` |
