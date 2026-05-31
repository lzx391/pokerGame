# retro8bit 牌桌座位「CRT 入座」扫描线显现

> 状态：**计划文档（PM 全默认已确认，待实现）**  
> 非目标：本阶段不改后端/WebSocket；不为非 `retro8bit` 主题加入座动效；不改动 ≤600px 视口座位 UI；不把英雄位内联 dock / 全息手牌纳入本动效；不实现音效/震动。

## 1. 产品决策摘要（PM 确认）

| # | 决策 |
|---|------|
| 1 | **仅 `retro8bit` 主题** — 其它主题座位卡片保持现状 |
| 2 | **触发** — 对局进行中 `room.players` 出现**新 nickname**（相对上一帧已播种的快照）；**不含**首次进房 `loadGame` / 首帧 `applyRoomFromServer` 引导 |
| 3 | **范围** — 圆桌全部对手座位：`GameRoundTable` → `GamePlayerCard`（`rival-mini`）；英雄位在环上为空锚点（`heroDockRow`），不在此动效范围 |
| 4 | **视口** — `window.innerWidth > 600` 启用（含 `layout-fs` / 浏览器全屏宽窗）；**≤600px 本阶段不变** |
| 5 | **动效** — 自下而上扫描线揭示 + `blur → clear` + 约 **0.4–0.8s** CRT materialize；视觉与公共牌/全息手牌扫描线 token 同族 |
| 6 | **eco / PRM** — `ecoMode` 或 `prefers-reduced-motion: reduce` → **瞬间显示**，无动画 class |
| 7 | **Dev 日志** — 前缀 `[dp-seat-enter]`，仅 `process.env.NODE_ENV === 'development'`（对齐 `dpHandHologramDevLog`） |

## 2. 现状盘点（只读核查）

### 2.1 组件与 DOM

```
game.vue
└── .dp-game-table-fit
    └── GameRoundTable.vue
        └── v-for playersDisplayOrder
            ├── .dp-game-table__seat          ← 动效挂载点（计划增 --join-reveal）
            └── GamePlayerCard (rival-mini)   ← 模糊/扫描线作用于卡片根
```

| 触点 | 文件 | 现状 |
|------|------|------|
| 房间快照入口 | `game.vue` `applyRoomFromServer(room)` L964–984 | 指纹未变则 early return；变则 `APPLY_ROOM` |
| 玩家列表真源 | `dpGame.js` `state.players` + getter `playersDisplayOrder` | 按本机 `user.nickname` 旋转展示序，**nickname 集合**与数组下标无关 |
| 座位 `v-for` key | `GameRoundTable.vue` L92 | `leftThisHand ? 'offline-' + seatIndex : nickname` |
| 主题门控先例 | `game.vue` `useRetroHandHologramWide` L236–238 | `retro8bit && viewportWidth > 600` |
| 聊天门控先例 | `GameRoomChatPanel.vue` `useRetroChatReveal` | + `!ecoMode && !prefersReducedMotion` |

### 2.2 数据流与指纹

`encodeRoomApplyFingerprint`（`dpGameRoomFingerprint.js`）对每个 player 含 `nickname` 及筹码/盲注/离座等字段。  
**推论：**

- 新玩家入座 → 指纹必变 → 会走完整 `applyRoomFromServer`。
- 指纹相同 → `players` 视觉态不变 → **无需**在 early return 分支做 nickname diff。
- nickname diff 应挂在 **`applyRoomFromServer` 内、提交 Vuex 之前**（用入参 `room.players` 对比本地 `this.players`），且与指纹短路**独立**：仅当即将/已经接受新快照时更新播种集；首帧只播种不动画。

### 2.3 扫描线视觉参考（复用 token，不搬 DOM）

| 参考 | 位置 | 可复用 |
|------|------|--------|
| 公共牌 CRT 横线 | `dp-game-themes.css` L788–854 | `--dp-card-community-scanline-*` + `repeating-linear-gradient` `::after` |
| 全息手牌 CRT | `dp-game-themes.css` L856–899 | 同上 token 别名到 hologram scope |
| 全息 materialize 时长 | `dp-game-shell.css` `dp-retro-hand-hologram-materialize` **420ms** | 座位可取 **500–600ms**（PM 0.4–0.8s 中值） |
| 聊天翻板门控 | `GameRoomChatPanel.vue` | `ecoMode \|\| PRM` → instant class 范式 |

### 2.4 已知风险（实现前必须写清 guard）

| 风险 | 原因 | 计划对策 |
|------|------|----------|
| **离线 key  remount** | `leftThisHand` 时 key 从 `nickname` → `offline-{seatIndex}` | 仅以 **nickname 集合差集** 触发；离座/回座不视为「新 nick」 |
| **座位环旋转假阳性** | `playersDisplayOrder` 随本机视角旋转 | diff 用 **Set(nickname)**，不用 displayIndex / seatIndex |
| **首屏全员闪现** | 进房已有 6 人 | 首帧 **seed only**，`joinRevealNicks` 为空 |
| **换房残留** | `beforeRouteUpdate` / `created` 换 `roomId` | 重置 seed + 清空 `joinRevealNicks` |
| **指纹跳过** | 同快照重复 WS | 无新 nick，不 diff；无需处理 |
| **动画叠加** | 发牌飞入 / 盖牌 ghost 同座位 | 扫描线层 `pointer-events: none`；不取消 hole-deal keyframes |
| **eco 性能** | 多座位同时入座（批量 bot） | 允许并行动画；静态 gradient + 单次 keyframes，禁止 per-frame JS |

## 3. 视觉与动效规格

### 3.1 用户叙事（retro8bit + 宽视口）

1. 对局进行中，对手/NPC **新进入** `room.players`。
2. 其圆桌座位卡片自下而上出现 **CRT 扫描带**，同时卡片由 **轻度 blur + 低对比** 过渡到 **清晰**。
3. 约 0.5s 后动效结束，卡片与现网一致，可正常显示行动点、盖牌、桌边聊天气泡。
4. eco 或系统「减少动态效果」→ 卡片 **立即** 以终态显示。

### 3.2 不动效范围

- 主题 ≠ `retro8bit`
- `viewportWidth ≤ 600`
- `ecoMode === true` 或 `prefers-reduced-motion: reduce`
- 首次进房/bootstrap 已有玩家
- 仅 `leftThisHand` / 离座回座、筹码变化、庄盲切换、display 序旋转
- 英雄位（`playersDisplayOrder[0]` 为本机且非 rival-mini 展示路径）

### 3.3 时序（标准档，建议 560ms）

```
检测到 nick ∈ (newSet \ oldSet)
│
├─ [0] 父级 joinRevealNicks[nick] = true
├─ [1] 座位 .dp-game-table__seat--join-reveal
│       ├─ ::after 扫描遮罩 bottom→top (clip-path / transform)
│       └─ .dp-player-card filter: blur(6px) → blur(0); opacity 0.45→1
└─ [2] animationend → 父级删除 joinRevealNicks[nick]
```

- 总时长目标 **0.4–0.8s**（实现取 **560ms** `ease-out`，可在 QA 微调）。
- **Instant 路径**：不挂 `--join-reveal`；卡片 CSS 无 animation。

### 3.4 ASCII 层叠

```
.dp-game-table__seat.dp-game-table__seat--join-reveal
├── ::after  扫描线遮罩（repeating-linear-gradient，自下而上 reveal）
└── .dp-player-card.dp-player-card--rival-mini
    └── 昵称/筹码/盖牌 UI（现有）
```

## 4. 架构设计

### 4.1 门控（`game.vue` computed）

```js
useRetroSeatEnterReveal =
  gameUiTheme === 'retro8bit'
  && viewportWidth > 600
  && !ecoMode
  && !prefersReducedMotion
```

- `viewportWidth`：复用 `game.vue` 已有字段 + resize 监听（与全息/聊天一致，debounce 150ms）。
- CSS 双保险：`@media (min-width: 601px)` + `[data-dp-game-theme='retro8bit']`。

### 4.2 Nickname diff（`game.vue` methods）

**状态字段（`data`，非 Vuex）：**

| 字段 | 类型 | 职责 |
|------|------|------|
| `_seatEnterNickSnapshot` | `Set<string>` 或 `Object` | 上一帧已知 nickname |
| `_seatEnterNickSeeded` | `boolean` | 是否已完成首帧播种 |
| `joinRevealNicks` | `{ [nick: string]: true }` | 当前正在播放入座动画的 nick |

**工具函数（建议 `utils/dpSeatEnterNickDiff.js`）：**

```js
/** @returns {string[]} */
export function extractPlayerNicknames(players) {
  // 遍历 room.players，trim nickname，跳过空串
}

/** @returns {string[]} 新增 nick */
export function diffNewSeatNicknames(prevSet, nextList) { ... }
```

**`syncSeatEnterRevealFromRoom(room)` 流程（在 `applyRoomFromServer` 内调用）：**

1. `nextNicks = extractPlayerNicknames(room.players)`
2. 若 `!_seatEnterNickSeeded`：  
   - `_seatEnterNickSnapshot = new Set(nextNicks)`  
   - `_seatEnterNickSeeded = true`  
   - `dpSeatEnterDevLog('seed', { count, nicks })`  
   - **return**（不写入 `joinRevealNicks`）
3. `added = nextNicks.filter(n => !_seatEnterNickSnapshot.has(n))`
4. 若 `added.length && useRetroSeatEnterReveal`：对每个 `nick` 执行 `this.$set(this.joinRevealNicks, nick, true)`（或替换整个新对象以触发响应式）
5. `_seatEnterNickSnapshot = new Set(nextNicks)`
6. `dpSeatEnterDevLog('diff', { added, skipped: !useRetroSeatEnterReveal })`
7. 然后执行既有指纹判断 + `APPLY_ROOM`

**重置（换房）：** 在 `resetRoomChatUiForEnter()` 同级增加 `resetSeatEnterStateForRoom()`，并在 `beforeRouteUpdate`（roomId 变化）调用：

- `_seatEnterNickSeeded = false`
- `_seatEnterNickSnapshot = new Set()`
- `joinRevealNicks = {}`

`created` / `beforeDestroy`：销毁时清空，避免泄漏。

### 4.3 Props 下发

```html
<!-- game.vue -->
<game-round-table
  :join-reveal-nicks="joinRevealNicks"
  :seat-enter-reveal-enabled="useRetroSeatEnterReveal"
  …
/>
```

**`GameRoundTable.vue`：**

- props：`joinRevealNicks: Object`, `seatEnterRevealEnabled: Boolean`
- 模板 L90–117：  
  `:class="{ 'dp-game-table__seat--join-reveal': seatEnterRevealEnabled && joinRevealNicks[row.player.nickname] && !row.player.leftThisHand }"`
- `@animationend` 在 seat 或内部 card 上：`$emit('seat-enter-reveal-done', row.player.nickname)`

**`game.vue` 监听：**

```js
onSeatEnterRevealDone(nick) {
  this.$delete(this.joinRevealNicks, nick)
  dpSeatEnterDevLog('done', { nick })
}
```

> **不**为 `GamePlayerCard` 增 prop（P0）：动效纯 CSS 后代选择器即可；P1 若需 hole-deal 冲突再传 `join-reveal` class 到 card root。

### 4.4 Guard 细则（假阳性）

| 场景 | 是否播放入座动画 |
|------|------------------|
| 首帧 `loadGame` 已有玩家 | 否（仅 seed） |
| 新 NPC/真人 `nickname` 首次出现在 `players` | 是 |
| `leftThisHand: false → true`（离线） | 否（nick 已在 snapshot） |
| 离线 key remount（`offline-2`） | 否 |
| 离线后同座换新人（新 nick） | 是 |
| 玩家离开房间（nick 从列表消失） | 否；snapshot 删除该 nick |
| `players` 数组顺序变化、集合不变 | 否 |
| 指纹相同重复推送 | 否（不进 diff） |
| 批量添加 3 bot | 是（3 个 nick 各一条并行动画） |

### 4.5 Dev 日志

新建 `front/dp_game/src/utils/dpSeatEnterDevLog.js`：

```js
var PREFIX = '[dp-seat-enter]'
export function dpSeatEnterDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development') return
  …
}
```

建议埋点：`seed` | `diff` | `done` | `reset` | `gate-off`（门控关闭时有新增 nick）。

## 5. CSS 设计（`dp-game-themes.css` + `dp-game-eco-mode.css`）

### 5.1 Token 复用（不新建语义色）

在 retro8bit 段为座位作用域 **别名** 公共牌 token（便于日后统一调参）：

```css
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-table__seat--join-reveal {
  --dp-seat-enter-scanline-period: var(--dp-card-community-scanline-period, 3px);
  --dp-seat-enter-scanline-thick: var(--dp-card-community-scanline-thick, 1px);
  --dp-seat-enter-scanline-color: var(--dp-card-community-scanline-color, rgba(0, 0, 0, 0.38));
  --dp-seat-enter-accent: var(--dp-accent, #4af626);
}
```

### 5.2 规则草案（`@media (min-width: 601px)` 块内）

| 选择器 | 行为 |
|--------|------|
| `.dp-game-table__seat--join-reveal` | `position: relative; overflow: visible`（继承 seat） |
| `.dp-game-table__seat--join-reveal::after` | 与 community `::after` 相同 `repeating-linear-gradient`；`animation: dp-retro-seat-enter-scan-sweep 560ms ease-out forwards`；`clip-path` 或 `transform: scaleY()` 实现 **自下而上** reveal |
| `.dp-game-table__seat--join-reveal .dp-player-card--rival-mini` | `animation: dp-retro-seat-enter-materialize 560ms ease-out forwards`；`filter: blur(6px)` → `blur(0)`；`opacity` 0.4→1 |
| `@keyframes dp-retro-seat-enter-scan-sweep` | 扫描带从 seat 底部推至顶部后淡出 |
| `@keyframes dp-retro-seat-enter-materialize` | 与 hologram materialize 同思路，**无** `rotateX`（座位已落位，避免与圆桌 transform 冲突） |

**禁止：** `width`/`height` 动画、`backdrop-filter` 动画、紫色渐变。

### 5.3 eco / PRM（`dp-game-eco-mode.css`）

并列模板（与 chat/hologram 一致）：

```css
.dp-game-root[data-dp-eco-mode='true'] .dp-game-table__seat--join-reveal::after,
.dp-game-root[data-dp-eco-mode='true'] .dp-game-table__seat--join-reveal .dp-player-card--rival-mini,
@media (prefers-reduced-motion: reduce) { … } {
  animation: none !important;
  filter: none !important;
  opacity: 1 !important;
}
```

门控为 false 时不加 `--join-reveal`，与 eco 块互补。

### 5.4 与桌边聊天气泡 z-index

- 座位 `z-index: 20`（`dp-game-shell.css` L961–964）
- 桌边聊天气泡更高（约 35–42）  
扫描线 `::after` 应用 `z-index: 1` **相对 seat**，勿盖住 `dp-player-card__seat-chat`。

## 6. P0 / P1 范围

### P0（本迭代必做）

1. `game.vue`：nickname diff + seed + `joinRevealNicks` + `useRetroSeatEnterReveal` + 换房 reset。
2. `GameRoundTable.vue`：`--join-reveal` class + `seat-enter-reveal-done` 事件。
3. `dp-game-themes.css`（601px+ retro8bit）：扫描线 + materialize keyframes，复用 community scanline token。
4. `dp-game-eco-mode.css`：eco + PRM 禁用动画。
5. `dpSeatEnterDevLog.js` + 开发环境日志。
6. 宽屏 + 全屏 QA（见 §8）；≤600px 零回归。

### P1（可跟进）

1. `dp-motion-tokens.css`：`--dp-motion-duration-seat-enter`（560ms）。
2. 抽 `dpSeatEnterNickDiff.js` 单测（nickname 集合 diff 纯函数）。
3. 若与 `hole-deal-fly-in` 冲突：错峰 120ms 或跳过正在发牌的座位。
4. 磷光绿 `box-shadow` 入场闪（accent pulse 80ms，可选）。
5. `GamePlayerCard` 显式 `dp-player-card--join-reveal` class（便于调试）。

### 明确不做（本阶段）

- ≤600px 入座动画。
- 非 retro8bit 主题。
- 后端推送「入座」专用事件（仍靠 `players` 快照 diff）。
- 英雄位 / 内联 dock / 全息手牌。
- 音效、震动、教程/guide 专用动效。

## 7. 待改文件清单

| 文件 | 变更类型 |
|------|----------|
| `front/dp_game/src/components/game.vue` | diff 逻辑、state、computed、props、reset、事件处理 |
| `front/dp_game/src/components/GameRoundTable.vue` | props、class、animationend emit |
| `front/dp_game/src/styles/dp-game-themes.css` | retro8bit 座位 `--join-reveal` 动画块 |
| `front/dp_game/src/styles/dp-game-eco-mode.css` | 禁用 seat enter keyframes |
| `front/dp_game/src/utils/dpSeatEnterDevLog.js` | **新建** dev log |
| `front/dp_game/src/utils/dpSeatEnterNickDiff.js` | P1 可选新建 diff 纯函数 |

**不改动：** `dpGame.js` store、`encodeRoomApplyFingerprint`、`GamePlayerCard.vue`（P0）、后端 API。

## 8. 验收标准（Acceptance）

| # | 场景 | 期望 |
|---|------|------|
| A1 | retro8bit，宽屏，房主添加 1 个 demo bot | 该座位单次扫描线 + 清晰化，~0.5s，dev 日志 `diff` + `done` |
| A2 | 进房时桌上已有 3 人 | 三人 **无** 动画；日志 `seed` |
| A3 | 对局中玩家断线 `leftThisHand` | **无** 入座动画；key 变为 `offline-*` 不触发 diff |
| A4 | 离线座位被 **新 nick** 占用 | 仅新 nick 动画 |
| A5 | eco 模式 ON，新 bot 入座 | 瞬间显示，无 keyframes |
| A6 | 系统 PRM ON | 同 A5 |
| A7 | 主题切到 `default`，新 bot | 无 `--join-reveal` |
| A8 | `innerWidth` 600 vs 601 | 600 无动画；601 有动画 |
| A9 | 浏览器全屏且宽度 1920 | 有动画（门控只看 width，不看 layout-fs） |
| A10 | 换房再进 | 新房首帧 seed，无旧 nick 残留动画 |
| A11 | production build | 控制台 **无** `[dp-seat-enter]` |

## 9. 验证步骤（QA / 开发）

1. `npm run dev`，主题 **retro8bit**，窗口宽度 ≥1280。
2. 建房并排入 2 人 → 确认 **无** 入座动画（A2）。
3. 房主工具添加 `demo` bot → 观察座位扫描线（A1）；DevTools 过滤 `[dp-seat-enter]`。
4. 打开 eco（设置内「节能」/`data-dp-eco-mode`）重复加 bot（A5）。
5. OS 开启「减少动态效果」重复（A6）。
6. 断线模拟（关 WS 或离座）→ 座位变离线 **无** 二次扫描（A3）。
7. `roomId` 路由跳转另一房 → 首帧无动画（A10）。
8. 将窗口缩至 600px 加 bot → 无动画（A8）。
9. `npm run build` + 预览 → 无 dev 日志（A11）。

## 10. 实现 handoff（给下一开发者）

### 10.1 推荐实施顺序

1. `dpSeatEnterDevLog.js` + `resetSeatEnterStateForRoom` + diff/seed（`game.vue`），**先不接 CSS**，用日志验证 diff。
2. `joinRevealNicks` → `GameRoundTable` class 绑定 + `animationend` 清理。
3. `dp-game-themes.css` 动画；本地调 560ms ↔ 700ms。
4. `dp-game-eco-mode.css` 兜底。
5. 按 §8 跑一遍；若有 hole-deal 重叠再开 P1 错峰。

### 10.2 与相邻 retro8bit 文档关系

| 文档 | 关系 |
|------|------|
| `retro8bit-community-cards-pixel-plan.md` | 扫描线 token **来源** |
| `retro8bit-hand-hologram-plan.md` | materialize 时长/门控 **范式** |
| `retro8bit-chat-reveal-plan.md` | eco/PRM/viewport 门控 **范式** |

### 10.3 设计原则（frontend-design / ui-ux-pro-max）

- **一种 aesthetic**：磷光绿 CRT，与牌桌公共牌一致，避免第二套扫描线配色。
- **动效有意义**：仅「新玩家出现」；遵守 PRM（ui-ux Priority 1 / 7）。
- **性能**：CSS-only、短单次动画；eco 下零动画。
- **最小 diff**：逻辑集中在 `game.vue`，样式集中在 themes/eco 块。

---

*文档版本：2026-05-31 · PM defaults locked · 实现前请再读 `game.vue` `applyRoomFromServer` 与 `GameRoundTable` 座位 key 行号（约 L92）。*
