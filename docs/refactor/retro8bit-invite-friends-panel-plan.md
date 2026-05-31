# retro8bit 宽视口「邀请好友」左上显示板 + 花屏加载

> 状态：**计划文档（PM 已确认，待实现）**  
> 非目标：本阶段不改后端/WebSocket/邀请 API；不改动 `home.vue` 大厅 `el-drawer` 好友列表；不为非 `retro8bit` 主题加显示板；**不改动 ≤600px 视口**（仍用现有底部 `GameBottomSheet`）。

## 1. 产品决策摘要（PM 确认）

| # | 决策 |
|---|------|
| 1 | **仅 `retro8bit` 主题** — 其它主题保持现有 `GameBottomSheet` 底部抽屉 |
| 2 | **仅对局页** — `GameTopBar`「邀请好友」→ 新显示板；**不含** `home.vue` 大厅邮箱/好友 drawer |
| 3 | **关闭** — 面板 **×** 或 **再次点击顶栏「邀请好友」** toggle；收起为 **反向 retract**（默认：向上收回，与展开方向相反） |
| 4 | **视口** — **>600px** 启用显示板（含 `layout-fs`、`:fullscreen`、`-webkit-full-screen` 宽窗 QA）；**≤600px 本阶段不变** |
| 5 | **加载** — `friendsLoading`（`dpMailbox`）期间面板内容区 **花屏（CRT snow）**；最长 **~2s fallback** 后强制展示列表；`ecoMode` / `prefers-reduced-motion` **instant**（跳过 slide + snow） |

## 2. 用户愿景（UX 叙事）

1. 对局中点击顶栏「邀请好友」→ 屏幕 **左上角** 降下一块 **retro 显示板**（像老式终端弹出信息窗，非底部 sheet）。
2. 显示板降下后，内容区进入 **花屏** 状态，表示正在同步好友列表与头像。
3. 数据就绪（或 2s 超时）→ 花屏 **淡出/闪白**，好友列表 **显现**（hint + 可滚动行 + 邀请按钮）。
4. 再次点「邀请好友」或点 **×** → 显示板 **向上 retract** 收回左上角。
5. 节能 / 减少动态效果 → 无 slide、无花屏，**瞬间** 展示完整列表（与手牌全息 instant 路径同责）。

## 3. 现状盘点（只读核查，2026-05-31）

### 3.1 组件与 DOM 流

```
game.vue
├── GameTopBar.vue
│   └── 「邀请好友」v-if="canInviteFriend" → $emit('open-invite-friend')
├── data: inviteFriendOpen (local, 非 Vuex)
├── openInviteFriendSheet()  → inviteFriendOpen = true（**仅打开，未 toggle**）
└── GameDpGameSheets.vue
    └── GameInviteFriendSheet.vue  v-if="vm.inviteFriendOpen"
        └── GameBottomSheet.vue（底部 mask + sheet）
            └── .dp-invite-friend-sheet（v-loading="friendsLoading"）
```

| 触点 | 文件 | 当前行为 |
|------|------|----------|
| 邀请按钮 | `GameTopBar.vue` L95–104 | `aria-label="邀请好友进房"` → emit `open-invite-friend` |
| 开关状态 | `game.vue` L195, L1201–1207 | `inviteFriendOpen` 布尔；`closeInviteFriendSheet` 仅关 |
| 抽屉壳 | `GameBottomSheet.vue` | 底部 `dp-game-sheet-mask--bottom`，`z-index: 1010` |
| 业务内容 | `GameInviteFriendSheet.vue` | `mapState('dpMailbox', ['friends', 'friendsLoading'])`；`opened()` → `fetchAllFriendsForInvite` |
| 加载 UI | 同上 L9 | Element `v-loading`  spinner（**非花屏**） |
| 大厅好友 | `home.vue` L361+ | `el-drawer` + `friendsLoading` 文案 — **本需求明确排除** |

**关键缺口：** 宽视口 `retro8bit` 下仍走 **底部 sheet + Element loading**，与 PM「左上显示板 + CRT 花屏」不一致。

### 3.2 数据与加载真源

| 项 | 位置 | 行为 |
|----|------|------|
| `friendsLoading` | `dpMailbox.js` `SET_FRIENDS_LOADING` | `fetchAllFriendsForInvite` 全程 true，finally false |
| 好友列表 | `fetchAllFriendsForInvite` | 分页合并至 100/页；commit `SET_FRIENDS`；`prefetchAvatarUrls` fire-and-forget |
| 可邀请过滤 | `GameInviteFriendSheet.selectableFriends` | 排除 self、无效 userId |
| 邀请 POST | `inviteFriend()` | `/dp/room-invites`；行级 `sendingUserId` loading |
| 权限 | `dpGame.js` getter `canInviteFriend` | 上桌未离座真人或观众（非 bot） |

**花屏结束条件（实现约定）：**

```js
contentReady =
  !friendsLoading                           // store 已 false
  || elapsedSinceOpen >= 2000               // 2s fallback（防 spinner 卡死）
// avatar prefetch 不阻塞 ready（与现网 v-loading 一致；头像 lazy 显示）
```

### 3.3 参考动效资产（复用，不搬 fullscreen 壳）

| 参考 | 可借鉴 | 本需求用法 |
|------|--------|------------|
| `DpAuthStage.vue` L73–81, L762+ | 面板内 **snow 三层**（noise / bars / bright）+ jitter | **P0 首选**：内容区 inset 花屏，非全屏 |
| `DpCrtFullscreenOverlay.vue` | `phase: snow \| fade-out`、`fullSnow` / `--lite`、eco/PRM CSS | 时序与降级模式；**DOM 不直接复用**（全屏 z-index 10050） |
| `GameHeroHandHologram.vue` | 门控、`useRetro*Wide`、`instant` class、Teleport、`dpHandHologramDevLog` | 架构范式 **1:1 对齐** |
| `dp-game-modals.css` L313+ | 底部 sheet 动画 `translateY(100%)` | 新 panel 用 **translateY(-100%)** 自顶滑入 |
| `retro8bit-hand-hologram-plan.md` | 互斥门控、toggle、resize debounce |  sibling 文档，结构照抄 |

### 3.4 布局 / 全屏 / z-index

| 条件 | 邀请 UI（现网） | 目标（retro8bit 宽） |
|------|----------------|---------------------|
| `innerWidth > 600` | 底部 sheet | **左上 display panel** |
| `≤600px` | 底部 sheet | **不变** |
| `layout-fs` / 浏览器全屏 + 宽窗 | 底部 sheet + `scheduleReparentElementUiLayersIntoFullscreenRoot` | 显示板 + 同 reparent 策略 |
| z-index | sheet mask **1010** | panel **1015–1025**（高于 sheet，低于 seat-chat 教程异常层需联调） |

### 3.5 明确不在范围

- `home.vue` 好友 drawer / 邮箱社交 sheet。
- 非 `retro8bit` 主题的任何左上 panel。
- ≤600px 视口（含手机竖屏全屏）。
- 修改 `/dp/friends`、`/dp/room-invites` 后端契约。
- 邀请权限规则（仍用 `canInviteFriend` getter）。

## 4. 视觉与动效规格

### 4.1 显示板形态（retro8bit + 宽视口）

- **锚点：** 视口 **左上角**，左边距 / 顶距与顶栏视觉对齐（建议 `left: max(12px, env(safe-area-inset-left))`；`top: calc(var(--dp-top-bar-offset, 0px) + 8px)` 或 `getBoundingClientRect` 取 `GameTopBar` 底边 + 8px）。
- **尺寸：** 宽 `min(380px, calc(100vw - 24px))`；高 `min(420px, calc(100vh - topOffset - 24px))`；内容区可滚动。
- **壳层：** 磷光绿 CRT 边框（`--dp-accent`）、暗底 `--dp-panel-bg`、内扫描线（轻量，与手牌全息/header 一致）；标题「邀请好友进房」用 `--dp-font-pixel`。
- **方向：** 展开 = 自顶部 **向下滑入**（`transform: translateY(-100%) → 0`）；收起 = **反向 retract 向上**（exit ~70% enter 时长）。

### 4.2 花屏阶段（内容区内 inset）

- 覆盖 **内容区**（非全屏）：`.dp-invite-friend-panel__snow` 绝对定位 `inset: 0`，子层 `__snow-noise` / `__snow-bars` / `__snow-bright`（结构对齐 `DpAuthStage`）。
- **激活：** `panelPhase === 'snow'` 或 `!contentReady` 时 `snow--active`。
- **结束：** `contentReady` 后 `snow` → 可选 **短 flash**（≤120ms，对齐全息 `__flash`）→ 列表 `opacity: 1`。
- **eco / PRM：** 跳过 snow 与 flash；`slide-in` 亦跳过 → `[idle] ↔ [ready]` instant。

### 4.3 时序（标准档，可调）

```
点击打开（inviteFriendOpen = true）
│
├─ [0] idle
├─ [1] slide-in        280ms   translateY(-100%)→0, ease-out
├─ [2] snow            可变     至 contentReady（friendsLoading false 或 2s cap）
├─ [3] reveal-flash    100ms   可选 CRT flash（可省略，P1）
└─ [4] ready           列表可交互；hint + rows + 邀请

点击关闭（× 或 toggle 顶栏按钮）
│
├─ [4] ready
├─ [5] retract         220ms   translateY(0)→(-100%), ease-in
└─ [0] idle            inviteFriendOpen = false；清理 timers

Instant（ecoMode || prefersReducedMotion）
│
└─ [0] ↔ [4]           无 mask 动画；内容直接 ready
```

**并行：** `slide-in` 开始后立即 `opened()` → `fetchAllFriendsForInvite`；snow 与网络并行，不等待 slide 结束再请求。

### 4.4 ASCII 结构

```
┌─ GameTopBar ─────────────────────────────────────────────┐
│  …  │ [邀请好友] │ 历史对局 │ …                              │
└──────────────────────────────────────────────────────────┘
 ┌─ dp-invite-friend-panel ─────────────┐
 │ ▓ 邀请好友进房                    × │  ← pixel title + close
 ├─────────────────────────────────────┤
 │ ░░ snow / scanlines (loading) ░░░░░ │  ← phase snow
 │  或                                 │
 │  hint                               │  ← phase ready
 │  ┌ avatar │ nickname    [邀请] ┐   │
 │  └ … scroll list … ────────────┘   │
 └─────────────────────────────────────┘
  ↑ top-left anchor, slides down
```

## 5. 架构设计

### 5.1 门控条件（单一 computed，建议 `game.vue` + 子组件同源）

```js
useRetroInvitePanelWide =
  gameUiTheme === 'retro8bit'
  && viewportWidth > 600    // resize debounce 150ms，与 hologram 一致
```

**注意：** 与手牌全息不同，**eco/PRM 不上门控**（宽视口仍用 panel 壳），仅影响 **是否 instant 动画**：

```js
useRetroInvitePanelAnimated =
  useRetroInvitePanelWide
  && !ecoMode
  && !prefersReducedMotion
```

CSS 双保险：

```css
@media (min-width: 601px) {
  .dp-game-root[data-dp-game-theme='retro8bit'] .dp-invite-friend-panel { … }
}
```

### 5.2 路由：点击「邀请好友」

```js
onInviteFriendClick() {
  if (!canInviteFriend) return
  if (useRetroInvitePanelWide) {
    if (inviteFriendOpen) closeInviteFriendPanel()
    else openInviteFriendPanel()
  } else {
    // ≤600 或非 retro：保持 sheet；建议同样支持 toggle
    inviteFriendOpen = !inviteFriendOpen
  }
  scheduleReparentElementUiLayersIntoFullscreenRoot()
}
```

| 路径 | 渲染组件 | `GameBottomSheet` |
|------|----------|-------------------|
| retro8bit + 宽 | `GameInviteFriendPanel` | **不挂载** |
| 其它 / 窄 | `GameInviteFriendSheet` | 挂载 |

**互斥：** 同一时刻只渲染一种壳；业务逻辑共用。

### 5.3 阶段状态机

| 阶段 | 说明 | 用户再点「邀请好友」 |
|------|------|---------------------|
| `idle` | 未挂载 / `v-if="false"` | → `slide-in` |
| `slide-in` | 面板降下 | → `retract`（可中断） |
| `snow` | 花屏加载 | → `retract` |
| `reveal-flash` | 可选闪白 | → `retract` |
| `ready` | 列表可见 | → `retract` |
| `retract` | 向上收回 | 忽略至 `idle` |

字段建议（`GameInviteFriendPanel.vue` data）：

- `panelPhase: 'idle' | 'slide-in' | 'snow' | 'reveal-flash' | 'ready' | 'retract'`
- `openedAt: number | null` — 用于 2s fallback
- `contentReady: boolean` — computed：`!friendsLoading || (Date.now() - openedAt >= 2000)`
- `snowActive: boolean` — `panelPhase === 'snow' && !contentReady`（instant 路径恒 false）

推进：`animationend` on `__panel`（slide/retract）；`watch contentReady` snow → ready；flash 用 `setTimeout`。

### 5.4 组件分层

| 组件 | 职责 |
|------|------|
| **`GameInviteFriendPanel.vue`（新建，P0）** | retro8bit 宽视口壳：门控、阶段机、左上定位、slide/snow/flash/retract、× 关闭；内嵌内容子组件 |
| **`GameInviteFriendContent.vue`（新建，P0）** | 从 `GameInviteFriendSheet` **抽出** hint / empty / list / invite / profile click / `opened()` fetch；props: `roomId`, `myUserId`；无壳 |
| **`GameInviteFriendSheet.vue`（改，P0）** | 窄屏/非 retro：`GameBottomSheet` + `GameInviteFriendContent`；移除重复业务逻辑 |
| **`GameDpGameSheets.vue`（改，P0）** | `v-if` 分支：`useRetroInvitePanelWide ? Panel : Sheet`（computed 与 `game.vue` 同源或通过 `vm.useRetroInvitePanelWide`） |
| **`game.vue`（改，P0）** | `useRetroInvitePanelWide` computed；`onInviteFriendClick` toggle；`viewportWidth` 已有；挂载无需改（仍经 Sheets） |
| **`DpCrtSnowInset.vue`（可选，P1）** | 抽取 auth/crt 花屏三层为可复用 inset；Panel 先 **scoped CSS 复制** auth 关键帧，P1 再 DRY |

**不新建 Vuex state：** `inviteFriendOpen` 保持 `game.vue` local data（与现网一致）。

### 5.5 Proposed DOM（`GameInviteFriendPanel.vue`）

```html
<transition name="dp-invite-panel">
  <div
    v-if="visible"
    class="dp-invite-friend-panel"
    :class="phaseClass"
    role="dialog"
    aria-modal="true"
    aria-label="向好友发送进房邀请"
  >
    <div
      class="dp-invite-friend-panel__shell"
      @animationend="onShellAnimEnd"
    >
      <header class="dp-invite-friend-panel__head">
        <span class="dp-invite-friend-panel__title">邀请好友进房</span>
        <button type="button" class="dp-invite-friend-panel__close" aria-label="关闭" @click="$emit('close')">×</button>
      </header>
      <div class="dp-invite-friend-panel__body">
        <!-- 花屏层：仅 animated 且 !contentReady -->
        <div
          v-show="showSnowLayer"
          class="dp-invite-friend-panel__snow"
          :class="{ 'dp-invite-friend-panel__snow--active': snowActive }"
          aria-hidden="true"
        >
          <span class="dp-invite-friend-panel__snow-noise" />
          <span class="dp-invite-friend-panel__snow-bars" />
          <span class="dp-invite-friend-panel__snow-bright" />
        </div>
        <div class="dp-invite-friend-panel__scanlines" aria-hidden="true" />
        <div
          v-show="showContent"
          class="dp-invite-friend-panel__content"
        >
          <game-invite-friend-content
            :room-id="roomId"
            :my-user-id="myUserId"
            @close="$emit('close')"
          />
        </div>
      </div>
    </div>
  </div>
</transition>
```

- **无全屏 mask**（PM 未要求 dim 对局）；面板外点击 **不关闭**（与底部 sheet 不同，避免误触）；仅 × / toggle 关闭。
- **Teleport：** 宽窗 `layout-fs` 时优先挂 `gameRoot`（对标 hologram `portalInGameRoot`），避免 `overflow: hidden` 裁剪。
- **顶栏按钮 `aria-expanded`：** P1 给 `GameTopBar` 邀请钮加 `:aria-expanded="inviteFriendOpen"`。

### 5.6 `GameInviteFriendContent.vue` 行为（自 Sheet 迁移）

保留现有逻辑，**删除** `v-loading`（panel 用 snow 替代；sheet 窄屏可保留 `v-loading` 或统一 snow P1）。

| 方法 / 计算 | 说明 |
|-------------|------|
| `opened()` | `watch visible/immediate` 触发 fetch；`sendingUserId` reset |
| `selectableFriends` | 不变 |
| `inviteFriend` / `onFriendProfileClick` | 不变 |
| `friendsLoading` | 只读 store；**不**在 content 内控花屏（panel 父级 watch） |

Content 在 panel `ready` 前可 **已挂载但 hidden**（`visibility: hidden` + `aria-hidden`），以便 prefetch 触发；或 `ready` 后再 mount（更简单，推荐 **mount 于 slide-in 开始**，列表 `opacity: 0` 直至 ready）。

## 6. P0 / P1 范围

### P0（本迭代必做）

1. 新建 `GameInviteFriendPanel.vue` + `GameInviteFriendContent.vue`。
2. `GameInviteFriendSheet` 瘦身为 sheet 壳 + Content。
3. `GameDpGameSheets` + `game.vue` toggle 与门控分支。
4. retro8bit 宽视口 CSS：slide-in / retract、snow 三层、scanlines、pixel 标题（`dp-game-shell.css` 或 `dp-game-modals.css` 新块）。
5. `friendsLoading` + **2s fallback** + eco/PRM instant。
6. `dpInviteFriendsDevLog.js` 开发日志。
7. 全屏宽窗 QA + `scheduleReparentElementUiLayersIntoFullscreenRoot` 回归。

### P1（可跟进）

1. 抽取 `DpCrtSnowInset.vue` 供 auth / panel / 未来复用。
2. `--dp-motion-duration-invite-panel-*` token（`dp-motion-tokens.css`）。
3. Esc 关闭、focus trap、关闭后焦点回顶栏邀请钮。
4. 顶栏 `aria-expanded`；`prefers-reduced-motion` media query 监听（现可从 `game.vue` 传入）。
5. resize 跨 600px：instant 切 sheet 或 force close + 无 toast。
6. 独立样式表 `dp-game-retro-invite-panel.css` 若 shell 过长。

### 明确不做（本阶段）

- 大厅 `home.vue` 好友 UI。
- ≤600px 左上 panel。
- 非 retro8bit 花屏/slide。
- 修改邀请 API 或好友分页策略。

## 7. 待改文件清单

| 文件 | 变更类型 |
|------|----------|
| `front/dp_game/src/components/GameInviteFriendPanel.vue` | **新建** — 壳、阶段机、snow、定位 |
| `front/dp_game/src/components/GameInviteFriendContent.vue` | **新建** — 业务内容自 Sheet 抽出 |
| `front/dp_game/src/components/GameInviteFriendSheet.vue` | **改** — 仅 BottomSheet + Content |
| `front/dp_game/src/components/GameDpGameSheets.vue` | **改** — panel/sheet `v-if` 门控 |
| `front/dp_game/src/components/game.vue` | **改** — `useRetroInvitePanelWide`、`onInviteFriendClick` toggle |
| `front/dp_game/src/utils/dpInviteFriendsDevLog.js` | **新建** — `[dp-invite-friends]` 日志 |
| `front/dp_game/src/styles/dp-game-modals.css` | **改** — panel transition、shell 基础（或新建 retro 块） |
| `front/dp_game/src/styles/dp-game-shell.css` | **改** — `@media (min-width: 601px)` retro8bit panel 定位/keyframes |
| `front/dp_game/src/styles/dp-game-themes.css` | **改** — `.dp-invite-friend-panel__title` 像素字 |
| `front/dp_game/src/styles/dp-game-eco-mode.css` | **改** — 禁用 panel snow/slide keyframes |

**不改：** `home.vue`、`GameBottomSheet.vue`（其它 sheet 仍用）、`dpMailbox.js` fetch 逻辑、后端。

## 8. 验收标准

### 8.1 功能

- [ ] `retro8bit` + 视口 601px+：点「邀请好友」→ 左上 panel slide-down → 花屏 → 好友列表（与现 sheet **同数据、同邀请行为**）。
- [ ] 再次点「邀请好友」或 × → retract 收起；`inviteFriendOpen` false。
- [ ] `friendsLoading` 期间可见花屏（非 Element spinner）；加载完成后列表出现。
- [ ] 模拟慢网 / 人为延迟：`openedAt` 起 **≥2s** 必现列表（fallback）。
- [ ] `default` / 其它主题 + 任意宽度：仍 **仅底部 sheet**，无 panel DOM。
- [ ] 视口 ≤600px：与现网 sheet 一致（含全屏窄手机）。
- [ ] 视口 601px+ **layout-fs / 浏览器全屏**：panel 可见且不被裁剪。
- [ ] `ecoMode` ON：instant 展示列表，无 slide/snow。
- [ ] 系统「减少动态效果」：同 instant。
- [ ] 邀请单行 loading、403/好友错误 toast、点头像开资料 — 与现 `GameInviteFriendSheet` 一致。
- [ ] `home.vue` 好友 drawer **无行为变化**（回归 smoke）。

### 8.2 视觉

- [ ] 面板锚定 **左上**，slide-down / retract-up 方向正确。
- [ ] 花屏 aesthetic 与 `DpAuthStage` / retro8bit CRT 家族一致（噪点 + 横纹 + 亮带）。
- [ ] 标题像素字；列表正文 `--dp-font-ui` 可读。
- [ ] 磷绿 accent 边框/扫描线与手牌全息、聊天翻板协调。

### 8.3 性能与 a11y

- [ ] 动画主用 `transform` + `opacity`。
- [ ] × 关闭钮触达 ≥36px（现有 sheet close 36px，可保持）。
- [ ] `role="dialog"` + `aria-label="向好友发送进房邀请"`。
- [ ] snow 层 `aria-hidden="true"`；ready 后列表可键盘 Tab 到邀请钮。
- [ ] 无 CLS：panel shell 预留 min-height。

## 9. 验证步骤（实现后）

1. **主题矩阵**：`retro8bit` vs `default`，各开关邀请 3 次。
2. **视口矩阵**：375px（sheet）、768px（panel）、1280px + layout-fs（panel + reparent）。
3. **加载矩阵**：正常好友 / 空列表 / DevTools Slow 3G / 人为 `SET_FRIENDS_LOADING true` 验证 2s fallback。
4. **eco / PRM**：TopBar 节能 ON；OS 减少动态效果。
5. **互斥**：宽 retro 打开 panel 时不应出现底部 invite sheet。
6. **回归**：手牌全息、行动 sheet、房主 hub、聊天 retro 翻板不受影响。
7. **Console**：开发环境可见 `[dp-invite-friends]` 阶段迁移日志。

## 10. 开发日志 `[dp-invite-friends]`

新建 `front/dp_game/src/utils/dpInviteFriendsDevLog.js`（模式同 `dpHandHologramDevLog.js`）：

```js
var PREFIX = '[dp-invite-friends]'

export function dpInviteFriendsDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development') return
  if (detail !== undefined) console.info(PREFIX + ' ' + message, detail)
  else console.info(PREFIX + ' ' + message)
}
```

**建议埋点：**

| 事件 | 字段 |
|------|------|
| 顶栏 click | `theme`, `viewportWidth`, `useRetroInvitePanelWide`, `inviteFriendOpen`, `canInviteFriend` |
| 分支 | `open panel` / `close panel` / `open sheet` / `blocked` |
| phase 迁移 | `from`, `to`, `animated`, `contentReady`, `friendsLoading`, `elapsedMs` |
| fetch 结果 | `ok`, `friendsCount`（沿用现有 `[dp][invite]` 可保留或合并） |
| fallback | `reason: '2s-cap'` |
| instant | `ecoMode`, `prefersReducedMotion` |
| Teleport | `portalTarget: 'gameRoot' \| 'body'` |

## 11. 实现交接步骤（给开发）

1. **抽 Content**：将 `GameInviteFriendSheet.vue` script/template 业务块迁至 `GameInviteFriendContent.vue`；Sheet 仅保留 `game-bottom-sheet` 包裹 + `v-loading`（窄屏）。
2. **新建 Panel**：阶段机 + snow DOM（复制 `DpAuthStage` 关键帧名前缀化为 `dp-invite-friend-panel-*`）+ 内嵌 Content。
3. **门控**：`game.vue` 增加 `useRetroInvitePanelWide`；`openInviteFriendSheet` 改名为 `onInviteFriendClick` 并 **toggle**。
4. **GameDpGameSheets**：  
   `v-if="vm.inviteFriendOpen && vm.useRetroInvitePanelWide"` → Panel；  
   `v-if="vm.inviteFriendOpen && !vm.useRetroInvitePanelWide"` → Sheet。
5. **CSS**：`dp-game-shell.css` @601px 块；`dp-game-eco-mode.css` 禁用动画；`dp-game-themes.css` 标题字体。
6. **2s fallback**：Panel `watch visible` 记录 `openedAt`；`setInterval` 或 `requestAnimationFrame` 检查 `contentReady`（注意 `beforeDestroy` 清理）。
7. **全屏**：Panel `mounted` 调用父级 `scheduleReparentElementUiLayersIntoFullscreenRoot`（或 emit）；Teleport target 逻辑抄 hologram。
8. **DevLog**：Panel + `game.vue` click 路径接入 `dpInviteFriendsDevLog`。
9. **自测 §9**；PR 截图：retro 宽屏 slide → snow → list + default 对照 + ≤600 sheet。

## 12. 设计原则对齐（skills 摘要）

- **frontend-design**：单一 memorable moment — 左上终端窗 **降下 + 花屏开机 + 列表 reveal**；磷光绿 CRT 与 retro8bit 世界观一致；避免通用 bottom sheet 审美。
- **ui-ux-pro-max**：P1 对比度 / 44px 触达；P2 loading 反馈（snow 替代 spinner）；P3 2s fallback 防卡死；P7 动效 150–300ms 分段、retract 快于 slide；PRM/eco instant；`transform`-only；toggle 关闭可预期（对标聊天/全息）。

---

*文档版本：2026-05-31 · 快照组件：`GameInviteFriendSheet.vue`、`GameBottomSheet.vue`、`GameDpGameSheets.vue`、`GameTopBar.vue`、`game.vue`、`DpAuthStage.vue`、`DpCrtFullscreenOverlay.vue`、`dpMailbox.js`。*
