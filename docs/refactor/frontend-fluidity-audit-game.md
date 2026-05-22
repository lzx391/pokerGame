# 对局核心前端流畅度 / 质感审计（只读）

> **角色**：前端体验/性能审计 Agent（对局核心）  
> **日期**：2026-05-22（代码树 `front/dp_game`）  
> **约束**：只读源码；**禁止**改 `.vue` / `.css` / `.js`；**禁止** git commit / push / 代合并 PR。  
> **产品口径**：质感 = 色彩美学 + 视觉层次，非特效堆叠；须按 **节能档**（`data-dp-eco-mode="true"` + `dp-game-eco-mode.css`）与 **标准档**（默认 `false`）两套标准审计；Android 开特效易卡，须单独标出高风险 CSS。

---

## 0. 审计范围与文件索引

| 类别 | 路径 |
|------|------|
| 根壳 | `front/dp_game/src/components/game.vue` |
| 牌桌 | `GameRoundTable.vue`, `GameCommunityCards.vue`, `GamePlayerCard.vue`, `GameTableActionTimer.vue` |
| 底栏/说明 | `GameActionPanel.vue`, `GamePlayFlowContent.vue`（静态文案，无动画） |
| 样式 | `dp-poker-cards.css`, `dp-game-community-cards.css`, `dp-game-eco-mode.css`, `dp-game-shell.css`, `dp-game-layout-tiers.css`, `dp-game-themes.css` |
| 逻辑 | `utils/dpGameRoomFingerprint.js`, `store/modules/dpGame.js`, `utils/dpGameEcoMode.js`, `mixins/dpGameActionCountdownMixin.js`, `mixins/dpGameTableFitMixin.js` |

**接入点**：`game.vue` 根节点 `:data-dp-eco-mode="ecoMode ? 'true' : 'false'"`，`provide: { dpGameView: this }`；`GameCommunityCards` / `GamePlayerCard` 通过 `prefersReducedMotion()` 同时认 `ecoMode` 与系统 `prefers-reduced-motion`。

---

## 1. 现状：标准档 vs 节能档覆盖是否完整

### 1.1 两档机制摘要

| 档位 | 开关 | CSS 作用域 | JS 行为 |
|------|------|------------|---------|
| **标准档** | `ecoMode === false`（localStorage 默认关，`dpGameEcoMode.js`） | 全量动画/模糊；台呢额外层次见 `dp-game-shell.css` 中 `:not([data-dp-eco-mode='true'])` | 发牌飞入、`getBoundingClientRect` 测庄位、fold muck 飞牌、翻转 stagger |
| **节能档** | 顶栏勾选 → `SET_ECO_MODE` | `dp-game-eco-mode.css` 约 120 行覆盖 | `prefersReducedMotion()` 为 true → 跳过 hole deal fly、fold muck fly；CSS 关多数 `@keyframes` |

### 1.2 节能档已覆盖（CSS + 部分 JS）

- 对手思考点脉冲、摊牌毛玻璃、`card-base` 扫光/悬停、手牌 deal/fold/flip（改为轻 opacity flip）、best-hand 弹入、chip-leader 光晕动画、公共牌 deal-fly、牌背呼吸、占位浮动、计时器毛玻璃与 danger 脉冲等（见 `dp-game-eco-mode.css` 全文）。

### 1.3 标准档独有（设计如此，非缺口）

- 台呢 `::before` 纹理提亮、`::after` 内晕、多层 `box-shadow` / 径向渐变（`dp-game-shell.css` 649–691）——注释写明「非节能加厚围边」。

### 1.4 「节能关但仍很重」— 标准档预期内热点

用户在标准档即承担以下成本（Android 尤甚）：

| # | 现象 | 路径 |
|---|------|------|
| S1 | 每张牌 `card-base::after` 无限扫光 + 多层阴影 + hover `filter` | `dp-poker-cards.css` 17–97 |
| S2 | 公共牌 3D `rotateY` 翻转 0.78s + `perspective: 880px` | `dp-game-community-cards.css` 38–55 |
| S3 | 翻牌落定 `community-card-reveal-glow`（多层 `box-shadow` + `filter`） | 同文件 57–79 |
| S4 | 未翻牌背 `card-back-breathe` 无限动画 | 同文件 108–120 |
| S5 | 占位 `community-placeholder-float` | 138–152 |
| S6 | 手牌 `hole-deal-fly` / `hole-fold-fly-muck` 带 `translate3d` + 关键帧 `blur` | `dp-poker-cards.css` 140–187 |
| S7 | 手牌 `hole-card-flip-anim`：`perspective` + `rotateY` + `filter`/`blur` | 189–218 |
| S8 | chip-leader `player-card--win-streak` 大面积脉冲阴影 + `filter` | 269–293 |
| S9 | 中央计时器 `--rich`：`backdrop-filter: blur(8px)` + danger 脉冲阴影 | `dp-game-shell.css` 719–826 |
| S10 | 摊牌 `dp-player-card--hand-reveal-glass`：`blur(14px) saturate` | 2203–2210 |
| S11 | 手机底栏「轮到你」`dp-mobile-action-pulse` 阴影脉冲 | 1334–1380 |
| S12 | 6–9 座 × 2 手牌 + 5 公共位同时存在 `.card-base` 绘制 | 组件树 `GameRoundTable` |

### 1.5 「节能开但仍偏重」— 节能档未充分降级

| # | 现象 | 路径 | 说明 |
|---|------|------|------|
| E-on-1 | 公共牌仍走 `card-flip-inner` **3D transform 0.45s** | `dp-game-eco-mode.css` 91–93 | 比 PRM 的 0.45s 更轻但仍触发合成层 |
| E-on-2 | `card-flip-wrapper` 保留 `filter: drop-shadow(...)` | eco 107–109 | 每牌一层滤镜 |
| E-on-3 | 所有 `card-base` **静态** 4–5 层 `box-shadow` 未减 | `dp-poker-cards.css` 18–23 | 牌多时 overdraw |
| E-on-4 | `hole-card-flip-eco` 仍 0.35s opacity 动画 | eco 39–57 | 可接受但非「零动画」 |
| E-on-5 | chip-leader 静态厚阴影（动画已关） | eco 75–77 | 层次保留，GPU 压力小于脉冲版 |
| E-on-6 | 整桌 `transform: scale()` 自适应（ResizeObserver + rAF） | `dpGameTableFitMixin.js` | 与 eco 正交，每帧可能重绘 |
| E-on-7 | `conic-gradient` 行动倒计时每秒随 `timeLeft` 更新 | `GameTableActionTimer.vue` + shell 738–742 | 非 CSS animation，但触发 repaints |
| E-on-8 | WS 仍 **每次** `APPLY_ROOM` 全量替换 `players` 等 | `game.vue` `applyRoomFromServer` | 见 §2 P0 |

**结论**：节能档对 **动画/模糊** 覆盖较完整；对 **静态层次（阴影/渐变/3D 翻转/全树 reconcile）** 覆盖不足。标准档「重」多数符合产品「全效果」定位，但 Android 上 S1–S12 叠加仍属 P0 风险组合。

---

## 2. 性能热点 P0 / P1

### 2.1 P0（对局进行中可感知卡顿）

| ID | 问题 | 路径 / 触发 |
|----|------|-------------|
| **P0-1** | **房间指纹未接入**：`encodeRoomApplyFingerprint` 仅定义、无任何 import；每条 WS `applyRoomFromServer` → `APPLY_ROOM` 替换整表 `players`，触发 `GameRoundTable` 全座位 + 公共牌子树更新 | `utils/dpGameRoomFingerprint.js`；`game.vue` 769–777、489–504 |
| **P0-2** | **~1s 级房间推送** + P0-1：NPC/心跳导致快照频率高，主线程 reconcile 与 CSS 动画抢帧 | `game.vue` WS `onmessage`；注释 `syncCommunityCardsFlipState` 1439 |
| **P0-3** | **`syncCommunityCardsFlipState` 不识别 eco/PRM**：仍 `setTimeout` 520+350×n ms 多次 `SET_FLIP_AT` / `SET_COMMUNITY_FLIP_COMPLETE`，节能档 CSS 已瞬时翻牌但 JS 仍拖节奏 | `game.vue` 1443–1491 |
| **P0-4** | **多路无限 CSS 动画叠加**（标准档）：扫光 × 牌数 + 牌背呼吸 × 未翻公共位 + chip-leader aura + 思考点 | 见 §1.4 S1/S4/S8 + shell 2074 |
| **P0-5** | **发牌/测距布局抖动**：`GameCommunityCards` / `GamePlayerCard` 在 `$nextTick` 对每新发牌 `getBoundingClientRect` + 庄位锚点 | `GameCommunityCards.vue` 67–136；`GamePlayerCard` `startHoleDealFly` 同类 |

### 2.2 P1（中等：特定场景或机型）

| ID | 问题 | 路径 |
|----|------|------|
| **P1-1** | `communityCards` watcher 与 `key="'cc-' + cIdx + '-' + c"`：牌面字符变会 **重建 DOM**，中断 CSS 动画 | `GameCommunityCards.vue` 4–8 |
| **P1-2** | `SET_FLIP_AT` 逐张 `Vue.set` → 每张公共牌翻转触发一次依赖 `communityCardsFlipState` 的更新 | `dpGame.js` 467–468；`GamePlayerCard` 牌型区 `communityCardsFlipComplete` |
| **P1-3** | `actionTimer` `setInterval(1000)` + 双计时器（桌面 orbit + 底栏 `dp-timer-ring`）同时存在时重复 tick | `dpGameActionCountdownMixin.js`；`GameActionPanel.vue` 55–57 |
| **P1-4** | `playersDisplayOrder` getter 每次访问新建数组；`v-for` key 用 nickname，**离线**变 `offline-{seatIndex}` 会卸载重挂座位 | `dpGame.js` 212–227；`GameRoundTable.vue` 57–92 |
| **P1-5** | 未连 WS 时 **1s HTTP 轮询** `loadGame` 叠加 P0-1 | `game.vue` 307–313 |
| **P1-6** | fold muck：`querySelector` + 每牌 `getBoundingClientRect`（标准档） | `GamePlayerCard.vue` 875–929 |
| **P1-7** | `tableFit` 在 `stage` / `players.length` / resize / ResizeObserver 下连续 `scheduleTableFitUpdate` | `dpGameTableFitMixin.js` 25–42 |

### 2.3 Android 高风险项（专节）

以下在 **标准档** 下对 Android WebView/Chrome 最易掉帧（合成层 + 大面积绘制 + 多动画并行）。节能档若未关对应规则，风险仍存。

| # | 类型 | 选择器 / 规则 | 文件:行（约） |
|---|------|----------------|---------------|
| A1 | `backdrop-filter` | `.dp-game-table-action-timer--rich .dp-game-table-action-timer__inner` | `dp-game-shell.css` 724–725 |
| A2 | `backdrop-filter` | `.dp-player-card--hand-reveal-glass`（10–14px blur + saturate） | `dp-game-shell.css` 2204–2210 |
| A3 | 多路 `animation` + `filter` | `.card-base::after` `card-luxury-shine` | `dp-poker-cards.css` 63, 69–86 |
| A4 | `filter` + `blur` 关键帧 | `@keyframes community-deal-fly` / `hole-deal-fly` | `dp-game-community-cards.css` 18–35；`dp-poker-cards.css` 144–160 |
| A5 | 3D `transform` + `filter` | `@keyframes hole-card-flip-anim` | `dp-poker-cards.css` 200–217 |
| A6 | 3D `perspective` + 长 `transition` | `.card-flip-inner` 0.78s `rotateY` | `dp-game-community-cards.css` 38–55 |
| A7 | 动画 + 多层 `box-shadow` | `@keyframes community-card-reveal-glow` | `dp-game-community-cards.css` 62–79 |
| A8 | 无限 `filter`+`box-shadow` | `@keyframes card-back-breathe` | `dp-game-community-cards.css` 108–120 |
| A9 | 大面积 `box-shadow` 脉冲 | `@keyframes win-streak-card-aura`（chip-leader） | `dp-poker-cards.css` 275–292 |
| A10 | 多 `@keyframes` 并行 | `.win-streak-badge` gradient+float+shine+emoji | `dp-poker-cards.css` 318–349 |
| A11 | `box-shadow` 脉冲 | `@keyframes dp-table-action-timer-pulse` | `dp-game-shell.css` 811–826 |
| A12 | `box-shadow`+`transform` 脉冲 | `@keyframes dp-rival-turn-pulse` | `dp-game-shell.css` 2073–2087 |
| A13 | `box-shadow` 脉冲 | `@keyframes dp-mobile-action-pulse` | `dp-game-shell.css` 1373–1380 |
| A14 | 大面积静态阴影栈 | `.dp-game-root:not([data-dp-eco-mode='true']) .dp-game-table__felt` | `dp-game-shell.css` 655–679 |
| A15 | 每牌 `drop-shadow` | `.card-flip-wrapper { filter: var(--dp-card-drop-shadow) }` | `dp-game-community-cards.css` 42 |
| A16 | `conic-gradient` 高频重绘 | `.dp-game-table-action-timer__ring` + 每秒 `progressPct` | `dp-game-shell.css` 738–742；`game.vue` 195–198 |
| A17 | 整桌 `transform: scale()` | `.dp-game-table-fit__inner` 行内 style | `dpGameTableFitMixin.js` |
| A18 | `color-mix` 大量用于台呢/胶囊（中等） | `dp-game-shell.css` 多处 | 主题混色计算 |

**Android 高风险条数：18**（上表 A1–A18）。

---

## 3. 质感（色彩 / 层次）改进机会

> 质感口径：**色彩美学 + 层次**，不增加无意义粒子/扫光。标注适用档位。

| ID | 建议 | 档位 | 依据 |
|----|------|------|------|
| Q1 | **台呢层次**：标准档已有径向高光 + 围边 + 内晕（`felt` / `::after`），节能档保持基础台呢即可；避免再叠动画 | 标准：保持静态；节能：勿复制标准 `:not(eco)` 块 | `dp-game-shell.css` 637–691 |
| Q2 | **牌面层次**：用主题 token 统一 `--dp-card-frame-*`、牌背 `--dp-card-back-*`，减少每主题手写阴影不一致 | 两档共用静态 | `dp-game-themes.css` + `dp-poker-cards.css` |
| Q3 | **牌型 pill**：`rank-pill--open/showdown` 已用 `color-mix` 与面板底融合；可略提高对比度（文字与底），不增加动画 | 两档共用 | `dp-game-shell.css` 2176–2186 |
| Q4 | **chip-leader 高亮**：标准档脉冲过抢；改为 **静态** 2px 描边 + 单层外阴影，脉冲仅标准档可选 | 标准：轻静态；节能：已实现静态描边（eco 75–77） | `dp-poker-cards.css` 269–293 |
| Q5 | **计时器胶囊**：`--rich` 毛玻璃可换 **半透明实色 + 1px 描边**（层次靠边框与字重，不靠 blur） | 标准优先；节能已关 blur | `dp-game-shell.css` 719–726 |
| Q6 | **摊牌玻璃**：showdown 毛玻璃改 **半透明渐变底**（与结算一致），层次靠边框与牌型 pill | 标准可改；节能已 `backdrop-filter: none` | `GamePlayerCard.vue` 10–13；shell 2202–2217 |
| Q7 | **公共牌区**：未翻牌背呼吸可改为 **静态** 亮度差（取消 infinite），翻开后单次 200ms 边框高亮即可 | 标准减动画；节能已关呼吸 | `dp-game-community-cards.css` 108–120 |
| Q8 | **手机底栏**：`dp-mobile-action-pulse` 改为 **纯色 urgent 按钮 + 图标**，去掉阴影动画 | 标准/节能均应关（现仅 PRM） | `dp-game-shell.css` 1334–1387 |

---

## 4. eco 缺口清单（`prefers-reduced-motion` 与 eco 重复/遗漏）

### 4.1 重复（维护成本，非运行时 bug）

`dp-poker-cards.css` 底部 `@media (prefers-reduced-motion: reduce)`（421–478）与 `dp-game-eco-mode.css` 大量 **同效果重复**（关动画、简化 win-streak、简化 flip）。`dp-game-community-cards.css` 155–179 亦与 eco 公共牌段重复。

**建议（给方案 Agent）**：抽一份「减动效」mixins 或合并为「`[data-dp-eco-mode='true'], @media (prefers-reduced-motion: reduce)`」联合选择器，避免双份漂移。

### 4.2 遗漏（eco 未覆盖，PRM 可能覆盖）

| # | 项 | PRM | eco CSS | eco/JS 行为 | 路径 |
|---|-----|-----|---------|-------------|------|
| G1 | 手机 urgent 按钮脉冲 | ✅ 关动画 | ❌ | ❌ | `dp-game-shell.css` 1383–1386 |
| G2 | 公共牌翻转 JS 时序 | ❌ | ❌ | ❌ 仍长 delay | `game.vue` 1469–1486 |
| G3 | `syncCommunityCardsFlip` 与 `prefersReducedMotion` | ❌ | ❌ | 组件内 PRM 只跳过 deal fly，不缩短 flip 定时 | `GameCommunityCards.vue` 61–65 vs `game.vue` |
| G4 | 台呢加厚（仅标准） | N/A | N/A（故意） | — | shell 649–691 |
| G5 | `backdrop-filter` 计时器 rich | ❌ | ✅ | 组件 `:eco-mode` 去 rich class | `GameTableActionTimer.vue` 6 |
| G6 | 对手思考点 | ✅ | ✅ | — | shell 2090 / eco 6–8 |
| G7 | 摊牌毛玻璃 | ✅ | ✅ | — | shell 2213 / eco 11–14 |
| G8 | 3D 公共牌 flip transition | 缩短 0.45s | 仍 0.45s | 无 instant flip 提交 | eco 91–93 |
| G9 | `encodeRoomApplyFingerprint` | N/A | N/A | 未实现跳过 reconcile | `dpGameRoomFingerprint.js` |
| G10 | `card-flip-inner.flipped .card-front` glow | ✅ 关 | ✅ | — | community 165 / eco 95–96 |
| G11 | 整桌 scale fit | N/A | N/A | 无 | `dpGameTableFitMixin.js` |
| G12 | `conic-gradient` 秒级更新 | N/A | N/A | 无 | shell 738 |
| G13 | `best-hand-card-enter` stagger delay JS | ❌ | CSS 关动画 | `bestHandCardEnterStyle` 仍写 delay | `GamePlayerCard.vue` 857–861 |
| G14 | 系统 PRM 不自动勾选 eco | N/A | N/A | 用户须手动开节能 | `dpGameEcoMode.js` |

**节能覆盖缺口条数：14**（上表 G1–G14；其中 G4 为 intentional 标准-only，G9/G11/G12 为 JS/布局层缺口）。

### 4.3 行为不一致摘要

- **CSS**：eco ≈ 手动 PRM，但 **mobile pulse、3D flip 时长、静态阴影栈** 仍不一致。  
- **JS**：`prefersReducedMotion()` 用于 hole deal / fold muck / community deal fly，**不用于** `syncCommunityCardsFlipState` 与 fingerprint 跳过。  
- **产品**：Android 用户若未开节能且系统未开「减少动态效果」，将承受 §2.3 全部 A 类风险。

---

## 5. 验证场景（手工 / 录屏建议）

在 **标准档** 与 **节能档** 各跑一轮；设备优先 **Android 中低端 Chrome**，对照 **iOS Safari** 与 **桌面 Chrome**。

| 场景 | 观察点 | 预期差异 |
|------|--------|----------|
| **发牌** | 新一手 preflop，6 人桌 hole deal 飞入 + 庄位测距 | 标准：连续 fly+blur；节能：牌直接落位，无 fly class |
| **翻牌** | flop/turn/river 新增公共牌 | 标准：deal-fly + 0.78s 3D flip + glow；节能：无 fly，较短 transform，无 glow 动画 |
| **fold** | 对手 fold，ghost/muck 飞向锚点 | 标准：0.58s 多牌 fly；节能：立即 `foldMuckAnimComplete`（JS） |
| **muck** | 同上 + `data-dp-muck-anchor` 不可见时回退 | 无 dom 时不应卡死；计时器与 reconcile 仍应前进 |
| **多人计时** | 2+ 人连续行动，orbit + 中央 timer，`timeLeft` 递减 | `conic-gradient` 是否掉帧；danger 脉冲仅标准 rich |
| **连续 WS 推送** | NPC 房约 1s 推送，观察 DevTools Performance / Vue commit | 指纹未接时每次推送应看到全树 patch；翻牌中推送不应长期 `flipComplete=false`（已有 game.vue 注释修复逻辑，但 eco 仍慢） |

**回归 bug 锚点**：`game.vue` 1439–1441（高频推送 + `numNew===0` 勿清 flip 定时器）；须确认节能档下 flip 定时仍不会拖慢牌型展示。

---

## 6. 给方案 Agent 的 Top 5 结论

1. **接入 `encodeRoomApplyFingerprint`**：在 `applyRoomFromServer`（或 WS 入口）比较指纹，未变则跳过 `APPLY_ROOM`——当前 P0 最大项，工具已写好未接线。  
2. **`syncCommunityCardsFlipState` 与 eco/PRM 对齐**：eco 或 `prefers-reduced-motion` 时即时 `SET_FLIP_AT` 全 true + 短 `flipComplete`，避免 CSS 已瞬时翻牌仍等 1.5s+ JS。  
3. **Android 标准档「减特效不减质感」**：默认或按 UA 关闭 `backdrop-filter`、牌背/扫光 infinite、chip-leader 脉冲；保留静态阴影与 `color-mix` 层次（见 §3 Q4–Q7）。  
4. **合并 eco + PRM 样式源**，并补 **G1 mobile pulse**、**G8 公共牌 eco 下 instant flip（transform:none）**、**G13 best-hand JS delay**。  
5. **控制布局/read 热路径**：发牌测距节流；`communityCards` 列表 key 稳定化；评估 tableFit 在推送高频时暂停 Observer。

---

## 7. 组件级速记（范围文件）

| 组件 | 性能 | 质感 |
|------|------|------|
| `game.vue` | WS→全量 APPLY；flip 定时器；1s poll | 主题/布局 tier 属性齐全 |
| `GameRoundTable` | N 座位 PlayerCard + SVG rays + 双 timer | 台呢/座位 ray 色彩来自主题 |
| `GamePlayerCard` | hole/fold 动画+测距；chip-leader class | 摊牌 glass；rank pill |
| `GameCommunityCards` | deal fly+flip；key 含牌面 | 公共牌背纹理主题化 |
| `GameTableActionTimer` | rich blur+pulse；conic 秒 tick | 胶囊+圆环层次清晰 |
| `GameActionPanel` | 底栏 `dp-timer-ring` 与 store 倒计时 | 无重度 CSS |
| `GamePlayFlowContent` | 无 | 说明文案 subpanel，无问题 |

---

## 8. 交接汇总

| 项 | 值 |
|----|-----|
| **交付文件** | `docs/refactor/frontend-fluidity-audit-game.md` |
| **Android 高风险条数** | **18**（§2.3 A1–A18） |
| **节能覆盖缺口条数** | **14**（§4.2 G1–G14） |

---

*本文件为只读审计产出；实现变更由后续方案/开发 Agent 按 Flyway/仓库规范另行提交。*
