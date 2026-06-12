# retro8bit 宽视口「房主终端」右侧滑入 + 花屏 + 键盘菜单

> 状态：**计划文档（PM 已确认全默认，待实现）**  
> 范式参考：`GameInviteFriendPanel` 阶段机 + `GameInviteFriendContent` 业务分离（`docs/refactor/retro8bit-invite-friends-panel-plan.md`）  
> 非目标：本阶段**不实现**、**不提交**；不改后端 `/dpRoom/*` API 契约；不为非 `retro8bit` 主题加终端；**不改动 ≤600px 视口**（仍用现有底部 `GameOwnerPanel` + `GameOwnerToolModal` sheet）。

---

## 1. 产品决策摘要（PM 确认）

| # | 决策 |
|---|------|
| 1 | **仅 `retro8bit` 主题** — 其它主题保持现有底部 `GameBottomSheet` 房主 hub |
| 2 | **视口 >600px** 启用右侧终端（含 `layout-fs`、`:fullscreen`、宽窗 QA）；**≤600px 本阶段不变** |
| 3 | **动效顺序（串行）** — 先 **RIGHT→LEFT 滑入**，再 **绿色 CRT 花屏 ≥1s**，再显示 **四级主菜单**（与邀请好友 snow min-hold 同责） |
| 4 | **主菜单（像素字）** — `添加NPC` · `移交房主` · `踢出玩家` · `看穿底牌`（仅 4 项，无分池结算） |
| 5 | **输入** — 鼠标点击 **或** 点击终端区域聚焦 → `W`/`S` 或 `↑`/`↓` 导航 → `Enter` 选中/确认 |
| 6 | **子菜单** — NPC 九档 + 数量；玩家列表（移交单选 / 踢人多选）；`Enter` 触发 API（复用 `game.vue` 现有方法） |
| 7 | **`Esc`** — 子菜单返回上一级；根菜单 `Esc` 关闭终端 |
| 8 | **看穿底牌** — 选中后进入 **确认步**（显示当前 ON/OFF），`Enter` 切换并提交 `SET_OWNER_REVEAL_ALL` |
| 9 | **权限** — 沿用 `isOwner` gate；失房时 `CLOSE_OWNER_HUB`（现 `game.vue` watch 已存在） |
| 10 | **摊牌结算 Option A** — `GameOwnerPanel` 分池裁判 **仍走底部 sheet**；终端 **仅 4 工具**，不含 pot judge |
| 11 | **NPC** — 完整 **9  archetypes** + 每档 **1～9 数量**（与 `GameOwnerToolModal` 现网一致） |
| 12 | **`CustomNpcStyleDialog`** —  overlay 行为与现网相同（sheet slot overlay / 全屏 reparent） |

---

## 2. 用户愿景（UX 叙事）

1. 房主在宽屏 retro8bit 对局点击顶栏「房主操作」→ 屏幕 **右侧** 滑入一块 **老式终端窗**（非底部 sheet）。
2. 终端壳到位后，内容区进入 **磷光绿花屏** ≥1s（「系统 boot」感，对齐邀请好友 CRT 家族）。
3. 花屏结束 → **闪白可选** → 四级 **像素字菜单** 显现；可用键鼠操作。
4. 选「添加 NPC」→ 进入九档列表子菜单，每档可调数量，`Enter` 确认 → 走 `confirmAddOwnerNpcs`（CUSTOM 仍弹 `CustomNpcStyleDialog`）。
5. 选「移交房主 / 踢出玩家」→ 玩家列表子菜单 → `Enter` 确认 → 走 `doTransferOwner` / `doKickPlayers`（含现有 `dpConfirm`）。
6. 选「看穿底牌」→ 确认步显示 `开启/关闭` → `Enter` toggle。
7. 摊牌阶段：分池裁判在 **独立底部 sheet**（仅 `GameOwnerPanel`）；顶栏「房主操作」仍只开 **4 工具终端**。
8. 再次点顶栏或 `Esc` 根级关闭 → 终端 **反向 retract**（LEFT→RIGHT 滑出）。
9. 节能 / 减少动态效果 → 无 slide、无花屏，**瞬间** 展示菜单（instant 路径）。

---

## 3. 现状盘点（只读核查，2026-05-31）

### 3.1 组件与 DOM 流（现网）

```
game.vue
├── GameTopBar「房主操作」 v-if="isOwner" → $emit('open-owner-hub')
├── openOwnerHubSheet() → commit('dpGame/OPEN_OWNER_HUB')
└── GameDpGameSheets.vue
    └── GameBottomSheet v-if="showOwnerHubSheet && isOwner"
        ├── slot overlay: CustomNpcStyleDialog
        ├── GameOwnerPanel（in-sheet, hide-tool-entry）— 含 showdown 分池 UI
        └── GameOwnerToolModal（embedded=true）— NPC 九档 + 移交/踢人/看穿
```

| 触点 | 文件 | 当前行为 |
|------|------|----------|
| 入口 | `GameTopBar.vue` L85–94 | `aria-label="房主操作"` → emit `open-owner-hub` |
| 开关 | `dpGame.js` `showOwnerHubSheet` | `OPEN_OWNER_HUB` / `CLOSE_OWNER_HUB` |
| 业务 API | `game.vue` | `confirmAddOwnerNpcs`, `doTransferOwner`, `doKickPlayers`, `submitCustomNpcBatch` |
| 看穿 | `SET_OWNER_REVEAL_ALL` | `GameOwnerToolModal` 按钮 toggle；`GamePlayerCard` 读 `ownerRevealAll` |
| 分池 | `GameOwnerPanel` + `confirmPotJudge` / `confirmJudgeWin` | 与神器 **同 sheet 堆叠** |
| 玩家列表 | getter `ownerActionPlayers` | `transfer` 含观众；`kick` 仅在座 |
| NPC 九档 | `GameOwnerToolModal.ruleNpcRows` + custom/llm/llmGlobal | count 1–9，`confirm-add-npcs` emit |

**关键缺口：** 宽视口 `retro8bit` 仍走 **底部宽 sheet + legacy 表单 UI**，无右侧终端、无键盘菜单、无 slide+snow 开场。

### 3.2 参考资产（复用，不搬 DOM）

| 参考 | 可借鉴 |
|------|--------|
| `GameInviteFriendPanel.vue` | 阶段 FSM、`SNOW_MIN_HOLD_MS=1000`、`animationend` 推进、portal、`useRetro*Animated` |
| `GameInviteFriendContent.vue` | 壳/内容分离；Content 只 emit，API 在 `game.vue` |
| `GameOwnerToolModal.vue` | NPC 九档数据模型、`npcCounts`、`ruleNpcRows`、`kickSelectionNicknames` 逻辑 **迁移到 Content** |
| `dp-game-shell.css` invite panel 块 | snow 三层 keyframes **前缀化为** `dp-owner-terminal-*`；slide 改为 **translateX** |
| `dpInviteFriendsDevLog.js` | `[dp-owner-terminal]` dev log 模板 |

### 3.3 z-index / 全屏

| 层 | 建议 z-index |
|----|-------------|
| 底部 sheet mask | 1010（现网） |
| 邀请好友 panel | 1018 |
| **房主终端 panel** | **1019–1022**（略高于 invite，低于 Element UI 全屏 reparent 层） |
| `CustomNpcStyleDialog` | 随 sheet overlay / `$confirm` reparent（现 `scheduleReparentElementUiLayersIntoFullscreenRoot`） |

---

## 4. 视觉与动效规格

### 4.1 终端形态（retro8bit + 宽视口）

- **锚点：** 视口 **右上角**；`right: max(12px, env(safe-area-inset-right))`；`top` 与 invite panel 对称（`GameTopBar` 底边 + 8px，`refreshPanelAnchor` 抄 invite 改 `panelRight` 或仍用 `top` + `right: 12px`）。
- **尺寸：** 宽 `min(400px, calc(100vw - 24px))`；高 `min(480px, calc(100vh - topOffset - 24px))`；菜单区可滚动。
- **壳层：** 磷光绿 CRT 边框（`--dp-accent`）、暗底 `--dp-panel-bg`、轻扫描线；标题「房主终端」或「OWNER TERMINAL」用 `--dp-font-pixel`。
- **方向：** 展开 = 自右侧 **向左滑入**（`transform: translateX(100%) → 0`）；收起 = **反向 retract 向右**（exit ~70% enter 时长，约 220ms vs 280ms）。

### 4.2 花屏阶段（内容区 inset，绿色调）

- 结构对齐 invite：`__snow-noise` / `__snow-bars` / `__snow-bright`；色调偏 **磷光绿**（`--dp-accent` 染色 noise/bars，区别于 invite 中性灰绿）。
- **激活：** `panelPhase === 'snow'`。
- **最短持有：** `SNOW_MIN_HOLD_MS = 1000`（与 invite 常量一致）；**无网络等待** — snow 结束仅看 min-hold + slide 完成（owner 菜单无 fetch）。
- **结束：** min-hold 后 → 可选 **flash ≤120ms** → `ready` 显示菜单。

### 4.3 时序（标准档）

```
点击打开（ownerTerminalOpen = true）
│
├─ [0] idle
├─ [1] slide-in        280ms   translateX(100%)→0, ease-out
├─ [2] snow            ≥1000ms  至 min-hold 满足（与 slide 并行，advance 需两者皆就绪）
├─ [3] reveal-flash    100ms   可选 CRT flash
└─ [4] ready           主菜单可交互

关闭（× / toggle 顶栏 / Esc 根级）
│
├─ [4] ready | 任意子菜单 stack
├─ [5] retract         220ms   translateX(0)→100%, ease-in
└─ [0] idle

Instant（ecoMode || prefersReducedMotion）
│
└─ [0] ↔ [4]           无 slide/snow；菜单 stack 直接可用
```

**串行语义（PM）：** slide **先开始**；slide `animationend` → 进入 `snow`；snow min-hold 满 → flash → `ready`。**不可** slide 未结束就露出菜单（instant 路径除外）。

### 4.4 ASCII 结构

```
┌─────────────────────────────────────────── GameTopBar ─┐
│  …  │ [房主操作] │ 邀请好友 │ …                              │
└──────────────────────────────────────────────────────────┘
                              ┌─ dp-owner-terminal ──────────┐
                              │ ▓ 房主终端              × │ ← pixel title
                              ├─────────────────────────────┤
                              │ ░░ green snow (boot) ░░░░░ │ ← phase snow
                              │  或                          │
                              │  › 添加NPC                   │ ← menu cursor
                              │    移交房主                   │
                              │    踢出玩家                   │
                              │    看穿底牌                   │
                              └─────────────────────────────┘
                                        ↑ top-right, slides ← from right

摊牌（Option A，独立 bottom sheet，仅 pot judge）：
┌──────────────── bottom GameBottomSheet ────────────────┐
│ 结算阶段 — 请为每个池选择赢家  …  GameOwnerPanel only   │
└────────────────────────────────────────────────────────┘
```

---

## 5. 架构设计

### 5.1 门控条件（`game.vue` computed，与 invite/hologram 同源）

```js
useRetroOwnerPanelWide =
  gameUiTheme === 'retro8bit'
  && viewportWidth > 600    // resize debounce 150ms

useRetroOwnerPanelAnimated =
  useRetroOwnerPanelWide
  && !ecoMode
  && !prefersReducedMotion
```

CSS 双保险：

```css
@media (min-width: 601px) {
  .dp-game-root[data-dp-game-theme='retro8bit'] .dp-owner-terminal { … }
}
```

### 5.2 路由：点击「房主操作」

```js
onOwnerHubClick() {
  if (!this.isOwner) return
  if (this.useRetroOwnerPanelWide) {
    if (this.ownerTerminalOpen) this.closeOwnerTerminal()
    else this.openOwnerTerminal()
  } else {
    // ≤600 或非 retro：现有 Vuex sheet
    if (this.showOwnerHubSheet) this.closeOwnerHubPanel()
    else this.openOwnerHubSheet()
  }
  this.scheduleReparentElementUiLayersIntoFullscreenRoot()
}
```

| 路径 | 终端 UI | 底部 sheet |
|------|---------|------------|
| retro8bit + 宽 | `GameOwnerHubPanel` | **摊牌时** 仅 `GameOwnerPanel` pot judge（见 §5.7） |
| 其它 / 窄 | 无 | `GameOwnerPanel` + `GameOwnerToolModal`（现网） |

**状态存储建议：**

- 宽终端开关：`ownerTerminalOpen` — **local data** on `game.vue`（对齐 `inviteFriendOpen`，不新增 Vuex）。
- 窄 sheet：继续 `showOwnerHubSheet` Vuex。

### 5.3 外壳阶段状态机（`GameOwnerHubPanel.vue`）

| 阶段 | 说明 | 用户再点顶栏 / Esc 根 |
|------|------|----------------------|
| `idle` | 未挂载 / `v-show=false` | → `slide-in` |
| `slide-in` | 右→左滑入 | → `retract`（可中断） |
| `snow` | 绿色花屏 boot | → `retract` |
| `reveal-flash` | 可选闪白 | → `retract` |
| `ready` | 菜单可见 | → `retract` |
| `retract` | 左→右收回 | 忽略至 `idle` |

**字段（Panel data）：**

- `panelPhase` — 同上枚举
- `openedAt: number | null`
- `snowStartedAt: number | null`
- timers：`flashTimer`, `snowMinHoldTimer`（抄 invite panel cleanup）

**推进：**

- `onShellAnimEnd`：`slide-in` → `snow`；`retract` → `resetToIdle` + `$emit('close')`
- `tryAdvanceFromSnow`：`Date.now() - snowStartedAt >= 1000` → `advanceFromLoading()`
- instant：`startOpen()` 直接 `setPhase('ready')`

### 5.4 内容菜单栈（`GameOwnerHubContent.vue`）

与外壳 FSM **正交**：Content 维护 `menuStack: string[]`，根为 `['root']`。

| Stack 顶 | 视图 | Enter 行为 |
|----------|------|------------|
| `root` | 4 项主菜单 | 进入对应子栈 |
| `npc-pick` | 9 档 NPC 列表 + 当前行 count | 进入 `npc-confirm` |
| `npc-confirm` | 「确认添加 BOT_XXX × N」 | `$emit('confirm-add-npcs', payload)` → pop 至 `root` |
| `transfer-pick` | `ownerActionPlayers`（需 commit `ownerToolType='transfer'`） | 进入 `transfer-confirm` |
| `transfer-confirm` | 显示目标昵称 | `$emit('transfer-owner')` |
| `kick-pick` | 多选 checkbox 列表（`ownerToolType='kick'`） | 进入 `kick-confirm` |
| `kick-confirm` | 已选 N 人摘要 | `$emit('kick-players', nicks)` |
| `reveal-confirm` | 「看穿底牌：[开启/关闭]」 | `$emit('toggle-reveal')` → pop `root` |

**NPC 九档（与 `GameOwnerToolModal` 一致）：**

| id | archetype / type | 标签 |
|----|------------------|------|
| fish | rule FISH | 新手猫 BOT_FISH |
| tag | rule TAG | 保守猫 BOT_TAG |
| lag | rule LAG | 松凶猫 BOT_LAG |
| nit | rule NIT | 胆小猫 BOT_NIT |
| call | rule CALL | 头铁猫 BOT_CALL |
| maniac | rule MANIAC | 激进猫 BOT_MANIAC |
| custom | custom | BOT_CUSTOM |
| llm | llm | BOT_LLM |
| llmGlobal | llmGlobal | BOT_LLM_GLOBAL |

每档 `npcCounts[id]` 1–9；`Enter` on `npc-pick` 时 custom 仍 emit `{ type:'custom', count }` 由 **game.vue** 打开 dialog（不改 API）。

**Content emits（Panel 转发至 game.vue）：**

```js
@confirm-add-npcs="vm.confirmAddOwnerNpcs"
@transfer-owner="vm.doTransferOwner"
@kick-players="vm.doKickPlayers"
@toggle-reveal="onOwnerTerminalToggleReveal"  // game.vue 内 commit SET_OWNER_REVEAL_ALL
@stack-change   // 可选，供 dev log
```

`game.vue` **不新增 HTTP**；`onOwnerTerminalToggleReveal` 仅 `commit('dpGame/SET_OWNER_REVEAL_ALL', !ownerRevealAll)` + toast。

### 5.5 键盘交互规格

**聚焦模型：**

- Panel 壳 `@click="focusTerminal"`（stop 不冒泡到牌桌）；首次点击即 `terminalFocused = true`。
- `tabindex="-1"` 的 `.dp-owner-terminal__body` 接收 focus ring（retro 磷光 outline）。
- 未聚焦时：仅 × 与鼠标点菜单行有效；聚焦后键盘生效（避免抢全局 W/S）。

**按键表（Content 在 `terminalFocused && panelPhase==='ready'` 时监听 `keydown`）：**

| 键 | 根菜单 / 列表 | 确认步 |
|----|--------------|--------|
| `W` / `ArrowUp` | 上移光标 | — |
| `S` / `ArrowDown` | 下移光标 | — |
| `A` / `ArrowLeft` | — | NPC count −1（仅 npc-pick） |
| `D` / `ArrowRight` | — | NPC count +1（仅 npc-pick） |
| `Space` | kick 列表 toggle 当前行 | — |
| `Enter` | 进入子栈 / 下一确认步 | 执行 emit API |
| `Esc` | 若 stack depth>1 → pop；否则 `$emit('close')` | pop 一级 |

**注意：** 子菜单打开时 **不** 关闭外壳；仅 Content stack pop。Panel 在 `retract` 阶段 detach key listener。

**a11y：**

- 当前行 `aria-selected="true"` + `.dp-owner-terminal__row--active` 磷光高亮。
- `role="menu"` / `role="menuitem"`（根菜单）；列表用 `role="listbox"`。
- 触达高度行 ≥44px（ui-ux-pro-max `touch-target-size`）。

### 5.6 组件分层

| 组件 | 职责 |
|------|------|
| **`GameOwnerHubPanel.vue`（新建）** | retro8bit 宽壳：门控、外壳 FSM、右上定位、slide/snow/flash/retract、×、focus 容器、portal、Esc 根关闭 |
| **`GameOwnerHubContent.vue`（新建）** | 菜单 stack、键盘、NPC 九档+count、玩家列表 UI、emit 至 game.vue |
| **`GameOwnerToolModal.vue`（保留）** | ≤600 / 非 retro **embedded** 神器 UI；宽 retro **不挂载** |
| **`GameOwnerPanel.vue`（保留）** | pot judge；宽 retro 仅出现在 **pot judge sheet** |
| **`GameDpGameSheets.vue`（改）** | 分支：`useRetroOwnerPanelWide` → Panel + pot judge sheet；否则现网 combined sheet |
| **`game.vue`（改）** | `useRetroOwnerPanelWide`、`ownerTerminalOpen`、`onOwnerHubClick` toggle |
| **`dpOwnerTerminalDevLog.js`（新建）** | `[dp-owner-terminal]` |

**不修改：** `confirmAddOwnerNpcs` / `doTransferOwner` / `doKickPlayers` / `submitCustomNpcBatch` / `confirmPotJudge` 方法签名与 HTTP。

### 5.7 摊牌 Option A — pot judge 独立 sheet

**规则：**

- 宽 retro：**禁止** 在 `GameOwnerHubContent` 出现分池 UI。
- `GameDpGameSheets` 新增条件 sheet：

```html
<game-bottom-sheet
  v-if="vm.isOwner && vm.stage === 'showdown' && vm.useRetroOwnerPanelWide && vm.showOwnerPotJudgeSheet"
  title="结算阶段"
  body-modifier="owner-hub"
  @close="vm.closeOwnerPotJudgeSheet"
>
  <game-owner-panel in-sheet hide-title hide-tool-entry … />
</game-bottom-sheet>
```

**打开策略（推荐 P0）：**

- `game.vue` `watch stage`：当 `stage === 'showdown' && isOwner && useRetroOwnerPanelWide` → `showOwnerPotJudgeSheet = true`（local data）。
- 房主可关闭 sheet；若尚未 `confirmPotJudge` 且仍 showdown，顶栏或桌内提示可再次打开（P1：「分池结算」浮动提示）。
- **互斥：** pot judge sheet 与 owner terminal **可同时存在**（不同角位）；z-index sheet < terminal 或反之需 QA（建议 terminal 在上，sheet 在底）。

**窄屏 / 非 retro：** 行为不变（单 sheet 含 panel + tool modal）。

### 5.8 CustomNpcStyleDialog overlay

- 宽 retro：dialog 挂于 **pot judge sheet overlay slot** 或 **game root**（与现 `GameDpGameSheets` L91–99 相同 pattern）。
- 终端打开时触 custom NPC：`confirmAddOwnerNpcs({ type:'custom' })` → Vuex `showCustomNpcStyleDialog=true`；dialog **叠在终端之上**（z-index 高于 1022）。
- **`scheduleReparentElementUiLayersIntoFullscreenRoot`**：在 dialog 打开 / terminal 打开 / `$confirm` 前调用（现 mixin 已 wrap `dpConfirm`）。

### 5.9 Proposed DOM（`GameOwnerHubPanel.vue` 摘要）

```html
<div
  v-show="sceneVisible"
  class="dp-owner-terminal"
  :class="phaseClass"
  role="dialog"
  aria-modal="true"
  aria-label="房主终端"
>
  <div class="dp-owner-terminal__shell" @animationend="onShellAnimEnd">
    <header class="dp-owner-terminal__head">
      <span class="dp-owner-terminal__title">房主终端</span>
      <button type="button" class="dp-owner-terminal__close" aria-label="关闭" @click="requestClose">×</button>
    </header>
    <div
      class="dp-owner-terminal__body"
      tabindex="-1"
      ref="focusRoot"
      @click="focusTerminal"
      @keydown="onKeydown"
    >
      <!-- snow / flash layers（同 invite 结构，class 前缀 dp-owner-terminal） -->
      <game-owner-hub-content
        v-show="showContentReady"
        :active="contentActive"
        :owner-reveal-all="ownerRevealAll"
        :owner-action-players-transfer="ownerActionPlayersTransfer"
        :owner-action-players-kick="ownerActionPlayersKick"
        :bot-state="botStateProps"
        @confirm-add-npcs="$emit('confirm-add-npcs', $event)"
        @transfer-owner="$emit('transfer-owner')"
        @kick-players="$emit('kick-players', $event)"
        @toggle-reveal="$emit('toggle-reveal')"
        @request-close="requestClose"
      />
    </div>
  </div>
</div>
```

- **Teleport / portal：** 抄 `GameInviteFriendPanel.syncPortalMount` → `gameRoot`。
- **无全屏 mask**（与 invite 一致）；面板外点击 **不关闭**（防误触）。

---

## 6. CSS 要点（translateX + retro8bit）

| 项 | 说明 |
|----|------|
| Slide in | `@keyframes dp-owner-terminal-slide-in { from { transform: translateX(100%); opacity:0 } to { transform: translateX(0); opacity:1 } }` |
| Retract | `translateX(0) → translateX(100%)`，220ms ease-in |
| 定位 | `position:absolute; right: max(12px, env(safe-area-inset-right)); top: <computed>` |
| Snow | 复制 invite 三层 + keyframes，前缀 `dp-owner-terminal-*`；noise/bars 加 `hue`/`filter` 偏绿 |
| 菜单行 | `--dp-font-pixel` 标题；列表项 `--dp-font-ui`；active 行 `box-shadow: inset 0 0 0 2px var(--dp-accent)` |
| eco / PRM | `dp-game-eco-mode.css` 禁用 slide/snow/flash animation |
| themes | `dp-game-themes.css` 补 `.dp-owner-terminal__title` 像素字 |

**性能：** 仅 `transform` + `opacity` 动画；避免 `width/height` 动画。

---

## 7. P0 / P1 范围

### P0（本迭代必做）

1. `GameOwnerHubPanel.vue` + `GameOwnerHubContent.vue`
2. `game.vue` 门控 + toggle + `ownerTerminalOpen`
3. `GameDpGameSheets` 三分支：宽 terminal / 宽 pot judge sheet / 窄 combined sheet
4. CSS：slide translateX、snow、scanlines、菜单样式
5. 键盘 W/S/↑/↓/Enter/Esc + 点击聚焦
6. NPC 九档 + count + 全 emit 接现有 API
7. Option A showdown pot judge 独立 sheet + stage watch 自动打开
8. `dpOwnerTerminalDevLog.js`
9. eco/PRM instant；fullscreen portal QA

### P1（可跟进）

1. 抽取 `DpCrtSnowInset.vue`（invite + owner 共用）
2. `--dp-motion-duration-owner-terminal-*` tokens
3. 顶栏 `aria-expanded`；关闭后 focus 回「房主操作」
4. resize 跨 600px：force close terminal + 无 toast
5. kick 列表「全选/全不选」键盘快捷键
6. 独立样式文件 `dp-game-retro-owner-terminal.css`（若 shell.css 过长）

### 明确不做

- 修改后端 NPC / 踢人 / 移交 API
- ≤600px 右侧终端
- 非 retro8bit slide/snow
- 终端内 pot judge（违反 Option A）
- 重写 `game.vue` HTTP 方法

---

## 8. 待改文件清单

| 文件 | 变更 |
|------|------|
| `front/dp_game/src/components/GameOwnerHubPanel.vue` | **新建** — 壳、外壳 FSM、snow、portal、focus |
| `front/dp_game/src/components/GameOwnerHubContent.vue` | **新建** — menu stack、键盘、NPC/玩家 UI |
| `front/dp_game/src/components/GameDpGameSheets.vue` | **改** — terminal / pot judge / legacy 分支 |
| `front/dp_game/src/components/game.vue` | **改** — 门控、toggle、`showOwnerPotJudgeSheet`、`onOwnerHubClick` |
| `front/dp_game/src/components/GameTopBar.vue` | **改（P1）** — `@open-owner-hub` 改接 toggle handler（经 game.vue） |
| `front/dp_game/src/utils/dpOwnerTerminalDevLog.js` | **新建** |
| `front/dp_game/src/styles/dp-game-shell.css` | **改** — `@media (min-width:601px)` owner terminal 块 + keyframes |
| `front/dp_game/src/styles/dp-game-eco-mode.css` | **改** — 禁用 owner terminal 动画 |
| `front/dp_game/src/styles/dp-game-themes.css` | **改** — 像素标题 |

**不改：** `GameOwnerToolModal.vue` 业务逻辑（窄路径仍用）、`dpGame.js` API 层、后端。

---

## 9. 验收标准

### 9.1 功能

- [ ] `retro8bit` + 601px+：点「房主操作」→ 右滑入 → 绿花屏 ≥1s → 四行主菜单。
- [ ] 再次点顶栏 / × / 根级 Esc → retract 关闭。
- [ ] 四工具子流程与现 `GameOwnerToolModal` **同 API 行为**（含 custom dialog、dpConfirm 移交/踢人）。
- [ ] NPC 九档均可设 1–9 并提交；tips/adding 状态与现网一致。
- [ ] 看穿：确认步 Enter toggle；牌桌 hole cards 可见性同现网。
- [ ] `stage===showdown'` + 宽 retro：底部 **仅** `GameOwnerPanel` sheet 可用；终端 **无** 分池项。
- [ ] `default` 主题 / ≤600px：与现网 combined bottom sheet **一致**。
- [ ] 非房主无入口；失房后 terminal + sheet 关闭。
- [ ] `ecoMode` / PRM：instant 菜单，无 slide/snow。

### 9.2 键盘

- [ ] 点击终端后 W/S/↑/↓ 移动高亮；Enter 进入/确认；Esc 返回/关闭。
- [ ] npc-pick 下 A/D 或 ←/→ 改 count。
- [ ] kick-pick 下 Space toggle 选中。

### 9.3 视觉

- [ ] 锚定右上；R→L slide / L→R retract 方向正确。
- [ ] 花屏绿色调与 retro8bit CRT 家族一致。
- [ ] 主菜单像素字；行高与 contrast ≥4.5:1。

### 9.4 回归

- [ ] 邀请好友 panel、手牌全息、聊天翻板不受影响。
- [ ] 全屏 `layout-fs` 下 terminal 不裁剪；`CustomNpcStyleDialog` / `$confirm` 可见。

---

## 10. 风险与对策

### 10.1 `dpConfirm` hybrid（Element `$confirm` + 全屏 reparent）

| 风险 | 对策 |
|------|------|
| 终端 portal 到 `gameRoot` 后，`$confirm` 被挡或 z-index 反序 | 保留 `dpConfirm` mixin 内 `scheduleReparentElementUiLayersIntoFullscreenRoot()`；终端打开/关时同样 schedule |
| 键盘 Enter 在 confirm 步与 dialog 双触发 | Content 在 emit 前置 `submitting` 锁；API 返回前忽略 Enter |
| `$confirm` 与终端同时 focus | 打开 confirm 时 `terminalFocused=false`；confirm 关闭后可选 restore focus |

### 10.2 菜单 stack × 外壳 phase

- retract 开始时若 Content 在 deep stack：P0 **直接 reset stack 至 root** 并 detach listener，避免动画中 keydown 泄漏。

### 10.3 Option A 双 UI 认知

- 摊牌时 owner 可能同时见 bottom pot sheet + 右侧终端；P1 可在 pot sheet title 加文案「分池结算 · 神器请点顶栏房主操作」。

### 10.4 `ownerActionPlayers` 与 `ownerToolType`

- getter 依赖 Vuex `ownerToolType`；Content 进入 transfer/kick 子栈前须 `commit SET_OWNER_TOOL`（在 Content 或 game.vue 包装 emit 中）。

### 10.5 移交/踢人成功后 `closeOwnerHubPanel`

- 现 `doTransferOwner` / `doKickPlayers` 调用 `closeOwnerHubPanel()`；宽 retro 须改为 **同时** `closeOwnerTerminal()` + 不清 pot judge sheet（若仍 showdown）。

---

## 11. 开发日志 `[dp-owner-terminal]`

新建 `front/dp_game/src/utils/dpOwnerTerminalDevLog.js`：

```js
var PREFIX = '[dp-owner-terminal]'

export function dpOwnerTerminalDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development') return
  if (detail !== undefined) console.info(PREFIX + ' ' + message, detail)
  else console.info(PREFIX + ' ' + message)
}
```

**建议埋点：**

| 事件 | 字段 |
|------|------|
| 顶栏 click | `theme`, `viewportWidth`, `useRetroOwnerPanelWide`, `ownerTerminalOpen`, `isOwner`, `stage` |
| 分支 | `open terminal` / `close terminal` / `open legacy sheet` / `blocked` |
| 外壳 phase | `from`, `to`, `animated`, `snowElapsedMs` |
| menu stack | `push`, `pop`, `stackDepth`, `screen` |
| keyboard | `key`, `focused`, `cursorIndex` |
| API emit | `confirm-add-npcs`, `transfer-owner`, `kick-players`, `toggle-reveal` |
| pot judge sheet | `auto-open`, `close`, `stage` |
| portal | `portalTarget: 'gameRoot' \| 'body'` |
| instant | `ecoMode`, `prefersReducedMotion` |

---

## 12. 实现交接步骤（给开发）

1. **game.vue 门控** — 增加 `useRetroOwnerPanelWide`、`ownerTerminalOpen`、`showOwnerPotJudgeSheet`；`openOwnerHubSheet` 重命名为 `onOwnerHubClick` 并分支 toggle。
2. **新建 Panel** — 复制 `GameInviteFriendPanel` 阶段骨架；slide 改 `translateX(100%)`；anchor 改右上；常量 `SNOW_MIN_HOLD_MS=1000`。
3. **新建 Content** — 从 `GameOwnerToolModal` 抽离 NPC rows/count、kick selection、玩家列表；改为 menu stack + keyboard；**不要** import `$http`。
4. **GameDpGameSheets** —  
   - `v-if="vm.useRetroOwnerPanelWide"` → `GameOwnerHubPanel`  
   - `v-if="vm.showOwnerHubSheet && !vm.useRetroOwnerPanelWide"` → 现 combined sheet  
   - `v-if="vm.showOwnerPotJudgeSheet && …"` → 仅 `GameOwnerPanel`
5. **Wire emits** — Panel → game.vue 现有方法；新增薄包装 `closeOwnerTerminal`、`onOwnerTerminalToggleReveal`。
6. **stage watch** — showdown 自动 `showOwnerPotJudgeSheet=true`（宽 retro owner）。
7. **CSS** — `dp-game-shell.css` @601px 块 + eco-mode 禁用。
8. **DevLog** — Panel/Content/game click 路径接入。
9. **自测矩阵** — §9；截图：slide→snow→menu、NPC 九档、transfer/kick confirm、showdown 双 UI、≤600 legacy。

---

## 13. 设计原则对齐（skills 摘要）

- **frontend-design**：memorable moment = **右侧终端滑入 + 绿屏 boot + 像素菜单**；与左上 invite 形成 **对角 retro 对称**；键盘操作强化「控制台」隐喻，避免 generic modal。
- **ui-ux-pro-max**：P1 键盘 + focus ring + Esc 退出路径；P2 行级 press 反馈；P7 slide 280ms / retract 220ms / snow ≥1s；PRM/eco instant；菜单行 ≥44px；destructive（踢人）确认步用 `--dp-danger` 文案；`transform`-only 动画。

---

*文档版本：2026-05-31 · 快照组件：`GameOwnerPanel.vue`、`GameOwnerToolModal.vue`、`GameDpGameSheets.vue`、`GameInviteFriendPanel.vue`、`game.vue`、`dpGame.js`。*
