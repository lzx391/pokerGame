# retro8bit 聊天历史「翻板 + 心电图」动效

> 状态：**计划文档（PM 已确认需求，待实现）**  
> 非目标：本阶段不改后端/WebSocket；不改动 ≤600px 视口的现有 popover；不为非 `retro8bit` 主题加动画；不把「说一句」输入栏纳入翻板。

## 1. 产品决策摘要（PM 确认）

| # | 决策 |
|---|------|
| 1 | **仅 `retro8bit` 主题** — 其它主题保持现有 popover，无动画 |
| 2 | **仅消息历史** — 「说一句」输入条始终可见，不在翻板内 |
| 3 | **消息内容** — 与现网相同（`messages` 列表 / 最近记录 / 空态文案） |
| 4 | **关闭** — 再次点击「聊天」toggle；收起时 **反向 90° 翻下** |
| 5 | **视口** — 视口 **>600px** 时启用动画（含 `layout-fs` / 浏览器全屏）；**≤600px 本阶段不变** |
| 6 | **字体** — 复用项目像素字体栈（`--dp-font-pixel` / `--dp-font-ui`）；昵称像素、正文可读 UI 栈 |

## 2. 现状盘点

### 2.1 组件与 DOM

```
GameHeroDockFooter.vue
└── .dp-game-footer-chat-cluster
    ├── GameRoomChatPanel (dock, messages)   ← toggle + 历史列表
    └── GameRoomChatBar (variant hero/mobile) ← 「说一句…」+ 发送（始终可见）
```

- **宽屏（>600px，非全屏）**：聊天在 `.dp-game-hero-action-row` 内联手牌行（`GameHeroDockFooter` 第一段）。
- **窄屏（≤600px）或全屏/layout-fs**：聊天在 `.dp-game-mobile-hero-bar` 底栏（同组件，第二段）。
- **旁观者 HUD**：`.dp-game-action-hud` 内也有 dock 面板（无 hero 手牌行时）。

`GameRoomChatPanel.vue` 当前行为：

- `expanded` 布尔 + `@click` toggle「聊天」。
- 历史区 `__list-wrap` 用 `v-show="expanded"`，dock 模式下 **绝对定位 popover**（`bottom: calc(100% + 6px)`，`z-index: 24`）。
- 列表 `max-height: 140px`，滚动到底由 `watch messages / expanded` 触发。

### 2.2 样式锚点

| 文件 | 职责 |
|------|------|
| `dp-game-shell.css` L1166–1199 | dock popover 定位、尺寸、z-index |
| `dp-game-themes.css` L132–137, L696–754 | `retro8bit` 字体 token 与标题像素规则 |
| `public/index.html` L10 | Google Fonts：`Press Start 2P` + `ZCOOL QingKe HuangYou` |
| `dp-game-guide.css` L11–13 | 教程内展开聊天时 `z-index: 3` 抬高 |
| `dp-motion-tokens.css` | 全站动效时长 token + eco 覆盖 |
| `dp-game-eco-mode.css` | `data-dp-eco-mode` + PRM 降级模板 |

### 2.3 参考动效（只借模式，不搬 DOM）

| 参考 | 可复用模式 |
|------|------------|
| `DpAuthStage.vue` | `perspective` + `rotateX` + `transform-origin: 50% 100%`；`animationend` 驱动阶段；`ecoMode \|\| prefersReducedMotion` 走 instant class |
| `DpRetroTv.vue` | retro CRT 扫描线/噪点 aesthetic；eco + PRM 关闭装饰层 |
| `GameCommunityCards.vue` / `game.vue` | `instantFlip = ecoMode \|\| matchMedia(PRM)` 门控范式 |

### 2.4 已知缺陷（实现前必须修）

`GameRoomChatPanel.vue` **重复声明 `methods` 块**（L73–79 与 L100–108）。Vue 2 合并选项时后者覆盖前者，导致 **`openForGuide` / `closeForGuide` 丢失**。新手引导 `GameButtonGuidePage.vue` 依赖这两个方法（L558–559）。合并为单一 `methods` 是 P0 前置项。

## 3. 视觉与动效规格

### 3.1 用户叙事（retro8bit + 宽视口）

1. 点击「聊天」→ 消息 **显示板** 以底边为铰链 **向上翻起 90°**（3D `rotateX`，像平板从桌面立起）。
2. 翻板到位 → **心电图/心跳锯齿线** 从左到右扫过（约 **0.3–0.5s**）。
3. 扫线结束 → **完整消息列表显现**（opacity fade 或 clip unmask，二选一，推荐 fade 400ms 内）。
4. 再次点击「聊天」→ **反向 90° 翻下** 收起。
5. `ecoMode` 或 `prefers-reduced-motion: reduce` → **瞬间显示/隐藏**，跳过 flip + ECG。

### 3.2 不动效范围

- 主题 ≠ `retro8bit`：保持现有 popover（`v-show` + 无 3D）。
- 视口 ≤600px：保持现有 popover（即使 `layout-fs` / 全屏，只要 `window.innerWidth ≤ 600`）。
- 输入栏 `GameRoomChatBar`：**永不参与** flip scene。

### 3.3 时序（标准档）

```
点击展开
│
├─ [0] idle
├─ [1] flipping-up     450ms  rotateX 90°→0°  ease-out
├─ [2] ecg-sweep       400ms  SVG/伪元素扫线
└─ [3] revealed        列表 opacity 0→1（可与 [2] 尾部重叠 80ms）

点击收起
│
├─ [3] revealed
├─ [4] collapsing-down 320ms  rotateX 0°→90°  ease-in（exit-faster-than-enter）
└─ [0] idle            列表隐藏
```

总展开体感 ~**0.85–0.95s**；收起 ~**0.32s**。

### 3.4 ECG 实现建议

- **P0 推荐**：内联 SVG `<polyline>` + `stroke-dashoffset` 动画，颜色 `var(--dp-accent)` / `#4af626`，1–2px 锯齿路径，单次 sweep。
- 备选：CSS `linear-gradient` + `mask-image` 扫过 jagged PNG/SVG data-uri（更重，P1 再考虑）。
- ECG 层 `pointer-events: none`，位于列表之上、翻板之内。

### 3.5 ASCII 结构（宽视口 retro8bit）

```
                    ┌─────────────────────────┐
                    │  message list (scroll)  │  ← __list，revealed 后可见
                    │  nick: pixel  body: ui  │
                    ├─────────────────────────┤
                    │ ~~~~ ECG sweep ~~~~~~~~ │  ← ecg 阶段
                    └───────────┬─────────────┘
                                │ hinge (bottom edge)
┌──────────┐  ┌─────────────────┴──┐  ┌──────────────────┐
│ 聊天 (n) │  │  说一句…           │  │ 发送 │ 离座 │ … │
└──────────┘  └──────────────────────┘  └──────────────────┘
   toggle          GameRoomChatBar（始终可见，不在 board 内）
```

## 4. 架构设计

### 4.1 门控条件（单一 computed）

```js
useRetroChatReveal =
  theme === 'retro8bit'           // gameUiTheme / data-dp-game-theme
  && viewportWidth > 600          // resize 监听，debounce 150ms
  && !ecoMode
  && !prefersReducedMotion
```

- `theme`：inject `dpGameView.gameUiTheme` 或 prop `gameUiTheme`（与 `game.vue` `:data-dp-game-theme` 一致）。
- `viewportWidth`：`window.innerWidth`；边界 **601px 开、600px 关**，与 `dp-game-shell.css` 现有 `@media (max-width: 600px)` 对齐。
- 门控关闭时：走 **legacy 分支**（现有 popover DOM/CSS，零 3D）。

CSS 侧镜像门控（双保险，避免 FOUC）：

```css
@media (min-width: 601px) {
  .dp-game-root[data-dp-game-theme='retro8bit'] … { /* flip rules */ }
}
```

### 4.2 阶段状态机

| 状态 | DOM 可见性 | 允许操作 |
|------|------------|----------|
| `idle` | board 隐藏 / rotateX(90deg) | 点击 → expand |
| `flipping-up` | board 在动画中，列表 opacity 0 | 点击 → 中断，转 `collapsing-down`（interruptible） |
| `ecg-sweep` | board 竖立，ECG 层 active | 点击 → `collapsing-down` |
| `revealed` | 列表可滚动 | 点击 → `collapsing-down` |
| `collapsing-down` | 反向 flip | 点击 → 忽略或加速完成（推荐：忽略至 idle） |

**Instant 路径**（eco / PRM / 门控 false）：`idle ↔ revealed`，无中间态；`expanded` 布尔仍驱动数据逻辑（scrollToBottom 等）。

状态字段建议：`revealPhase: 'idle' | 'flipping-up' | 'ecg-sweep' | 'revealed' | 'collapsing-down'`；`expanded` 保留为「用户意图是否打开」。

### 4.3 组件 / CSS 分层

| 优先级 | 项 | 说明 |
|--------|-----|------|
| **P0** | `GameRoomChatPanel.vue` | 合并 methods；加 `revealPhase`、门控 computed、`onBoardAnimationEnd`；DOM 增加 `__board-scene` / `__board` / `__ecg` 包装层；legacy 与 retro 两套 template 分支或 class 切换 |
| **P0** | `dp-game-shell.css` | retro8bit + min-width 601px 下 popover → flip scene 定位；`perspective`、`transform-style: preserve-3d`、`backface-visibility` |
| **P0** | `dp-game-themes.css` | 聊天像素/正文字体选择器（见 §5） |
| **P0** | `dp-game-eco-mode.css` | retro chat 动画禁用块（与 PRM 并列） |
| **P1** | `DpRetroChatBoard.vue` | 可选子组件：仅 flip + ECG .presentational，减轻 Panel 体积 |
| **P1** | `dp-motion-tokens.css` | `--dp-motion-duration-chat-flip` / `--dp-motion-duration-chat-ecg` |
| **P1** | `dp-game-retro-chat.css` | 若 shell 过长，拆出 retro 聊天专用表 |
| **P2** | Teleport 到 `body` | 仅当实测 clipping 无法靠 z-index/overflow 解决时 |

### 4.4  proposed DOM（retro 分支）

```html
<div class="dp-game-room-chat-panel" :class="panelClasses">
  <button class="dp-game-room-chat-panel__toggle" … />

  <!-- retro + wide only -->
  <div class="dp-game-room-chat-panel__board-scene" v-show="sceneVisible">
    <div
      class="dp-game-room-chat-panel__board"
      :class="boardPhaseClass"
      @animationend="onBoardAnimEnd"
    >
      <div class="dp-game-room-chat-panel__ecg" aria-hidden="true">
        <!-- SVG polyline -->
      </div>
      <div class="dp-game-room-chat-panel__list-wrap">
        <div class="dp-game-room-chat-panel__list" role="log">…</div>
      </div>
    </div>
  </div>

  <!-- legacy: 原 __list-wrap 结构，v-show="expanded" -->
</div>
```

- `__board-scene`：继承现 popover 定位（`absolute; bottom: calc(100% + 6px); z-index: 24+`）。
- `__board`：`transform-origin: 50% 100%`；初始 `rotateX(90deg)`；展开 `rotateX(0)`。

## 5. 字体规范（retro8bit）

### 5.1 已有基础设施

- 预加载：`front/dp_game/public/index.html` → `Press Start 2P`, `ZCOOL QingKe HuangYou`。
- Token（`dp-game-themes.css`）：
  - `--dp-font-pixel: 'Press Start 2P', 'ZCOOL QingKe HuangYou', monospace, sans-serif`
  - `--dp-font-ui: ui-monospace, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', monospace`

### 5.2 待增选择器（写入 `dp-game-themes.css` retro8bit 段）

| 元素 | 选择器 | 字体 |
|------|--------|------|
| Toggle 标签「聊天」 | `.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-room-chat-panel__toggle-label` | `--dp-font-pixel` |
| 消息数 `(n)` | `…__count` | `--dp-font-pixel`（可选缩小 `font-size: 0.65em`） |
| 昵称 | `…__nick` | `--dp-font-pixel` + `font-size: 10px; line-height: 1.6` |
| 消息正文 | `…__text` | `--dp-font-ui` |
| 空态 | `…__empty` | `--dp-font-ui` |
| 输入/发送 | 已有 `dp-game-room-chat__*` + body retro8bit UI 强制栈 | `--dp-font-ui`（无需改） |

像素字通用修饰（与现有标题规则一致）：`letter-spacing: 0.05em; text-rendering: geometricPrecision; -webkit-font-smoothing: none`。

## 6. Clipping / z-index 风险

| 风险 | 现状 | 缓解 |
|------|------|------|
| popover 被父级裁切 | `.dp-game-root--layout-fs { overflow: hidden }`；layout `overflow-y: visible` | flip 高度 ≤ 现 max-height 140px + 边框；实测全屏/宽屏；必要时 `__board-scene` 提 z-index 至 **28**（高于 fold-ghost 26，低于 seat-chat 35） |
| 3D 被 flatten | 祖先无 `perspective` | `__board-scene { perspective: 640px; transform-style: preserve-3d }` |
| 与牌桌 seat chat 重叠 | seat bubble z 35–42 | 可接受：历史板在底栏上方，一般不覆盖 seat；若重叠，board z-index 仍低于 seat chat |
| 教程镂空 | guide 展开 z-index 3 偏低 | retro 展开时保留 `openForGuide`；必要时 guide.css 提到 ≥ board z-index |
| 动画中误触输入 | 输入在 board 外 | 无额外处理 |
| resize 跨 600px | 动画中途变宽/变窄 | resize 时若 `expanded`，重置为 legacy 或 instant revealed，避免半态 |

## 7. P0 / P1 范围

### P0（本迭代必做）

1. 修复 `GameRoomChatPanel` 重复 `methods`。
2. retro8bit + viewport >600px flip / ECG / reveal / collapse 状态机。
3. 其它主题 & ≤600px 零回归（legacy popover）。
4. ecoMode + PRM instant 路径。
5. retro8bit 聊天字体选择器。
6. `openForGuide` / `closeForGuide` 兼容（instant 或 legacy 展开）。

### P1（可跟进）

1. 动效 token 入 `dp-motion-tokens.css`。
2. 抽 `DpRetroChatBoard.vue`。
3. 教程/guide z-index 与 retro board 联调。
4. resize 跨断点 polish。
5. 可选：展开时 subtle 磷光 box-shadow（retro8bit accent）。

### 明确不做（本阶段）

- ≤600px 翻板动画。
- 非 retro8bit 主题动画。
- 输入栏纳入 board。
- 音效 / 震动。

## 8. 待改文件清单

| 文件 | 变更类型 |
|------|----------|
| `front/dp_game/src/components/GameRoomChatPanel.vue` | 逻辑 + 模板 + scoped 基础样式 |
| `front/dp_game/src/styles/dp-game-shell.css` | dock flip scene 定位/3D（retro8bit @601px） |
| `front/dp_game/src/styles/dp-game-themes.css` | 聊天 pixel/ui 字体规则 |
| `front/dp_game/src/styles/dp-game-eco-mode.css` | 禁用 chat flip/ECG keyframes |
| `front/dp_game/src/styles/dp-motion-tokens.css` | P1：chat 时长 token |
| `front/dp_game/src/styles/dp-game-guide.css` | P1：必要时 z-index |
| `front/dp_game/src/components/DpRetroChatBoard.vue` | P1：可选新建 |

**不改**：`GameRoomChatBar.vue`（除验收确认字体外）、后端、WebSocket、Vuex chat 数据结构。

## 9. 验收标准

### 9.1 功能

- [ ] retro8bit + 视口 601px+：点击「聊天」播放 flip-up → ECG → 列表可见；再点击 flip-down 收起。
- [ ] 列表内容与现网一致（昵称、正文、空态、滚动到底）。
- [ ] 「说一句」与「发送」始终可见、可输入，动画前后位置不变。
- [ ] default / gothic / 其它主题：行为与现网 popover 一致，无 3D。
- [ ] 视口 600px 及以下：行为与现网一致（含全屏手机）。
- [ ] 视口 601px+ 全屏桌面/tablet 横屏：动画生效。
- [ ] ecoMode 开或系统「减少动态效果」：展开/收起瞬间完成，无 flip/ECG。
- [ ] 新手引导步骤可 programmatic 展开/收起聊天（`openForGuide` / `closeForGuide`）。

### 9.2 视觉

- [ ] retro8bit：toggle 标签与昵称为像素字；正文为 UI  monospace 栈，中文可读。
- [ ] ECG 扫线时长 0.3–0.5s，accent 色，不遮挡最终阅读（扫后消失或 opacity 0）。
- [ ] 翻板铰链在底边，无明显 z-fighting / 背面透视穿帮（`backface-visibility: hidden`）。

### 9.3 性能与 a11y

- [ ] 动画仅 `transform` + `opacity`（ECG 可用 SVG stroke，不用 layout 属性）。
- [ ] toggle 保持 `aria-expanded` 与阶段同步。
- [ ] 列表保留 `role="log"` + `aria-live="polite"`。
- [ ] 无 CLS：board 占位在 scene 内预留高度。

## 10. 验证步骤（实现后）

1. **主题矩阵**：`retro8bit` vs `default`，各开/关聊天 3 次。
2. **视口矩阵**：375px（无动画）、768px（有动画）、1280px + layout-fs（有动画）。
3. **eco 开关**：TopBar 节能档 ON → 瞬间切换。
4. **OS PRM**：系统设置减少动态效果 → 同 eco。
5. **消息流**：展开态收到新消息 → 自动滚到底。
6. **教程页**：`GameButtonGuidePage` 聊天步骤 → `openForGuide` 有效。
7. **旁观者 HUD**：无手牌时 `.dp-game-action-hud` 内 chat 不受影响（legacy 或同门控）。
8. **Chrome DevTools**：Performance 录展开一次，主线程无 >50ms 长任务。

## 11. 实现交接步骤（给开发）

1. **修 bug**：合并 `GameRoomChatPanel.vue` 双 `methods`；跑引导页 smoke。
2. **加门控**：`useRetroChatReveal` computed + resize listener（`beforeDestroy` 清理）。
3. **拆 template 分支**：legacy vs retro board；retro 下禁用 legacy `__list-wrap` 双渲染。
4. **写 CSS**：`dp-game-shell.css` 内 `@media (min-width: 601px)` + `[data-dp-game-theme='retro8bit']` 块；keyframes `dp-retro-chat-flip-up/down`、`dp-retro-chat-ecg-sweep`。
5. **接 animationend**：flip 完 → ecg → revealed；collapse 完 → idle + 隐藏 scene。
6. **字体**：`dp-game-themes.css` 增 §5.2 选择器。
7. **eco/PRM**：`dp-game-eco-mode.css` 禁用 keyframes；JS instant 路径与 CSS 双保险。
8. **自测 §10**；PR 附 录屏（retro8bit 宽屏展开/收起 + default 对照）。

## 12. 设计原则对齐（skills 摘要）

- **frontend-design**：retro8bit 翻板是单一高 impact moment，不做 scattered micro-interactions；磷光 accent + 像素/可读字体配对。
- **ui-ux-pro-max**：动效 150–500ms；exit 短于 enter；PRM/eco 必降级；transform-only；toggle 44px 触达已满足；门控避免在窄屏引入 precision tap 问题。

---

*文档版本：2026-05-31 · 对应组件快照：`GameRoomChatPanel.vue`、`GameHeroDockFooter.vue`、`dp-game-shell.css` dock 段。*
