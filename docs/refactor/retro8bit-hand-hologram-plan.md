# retro8bit 宽视口「查看手牌」磷光绿全息投影

> 状态：**计划文档（PM 全默认，待实现）**  
> 非目标：本阶段不改后端/WebSocket/牌力计算；不为非 `retro8bit` 主题加全息；不改动 ≤600px 视口的底部抽屉；**不实现扇形展开（fan）**——用户已回退相关实验，代码库中不存在 fan 手牌 UI。

## 1. 产品决策摘要（PM 全默认）

| # | 决策 |
|---|------|
| 1 | **仅 `retro8bit` 主题** — 其它主题保持现有 `GameBottomSheet` 抽屉 |
| 2 | **视口 >600px** 启用全息（含 `layout-fs`、`:fullscreen`、`-webkit-full-screen`）；**≤600px 本阶段不变**（仍用底部抽屉） |
| 3 | **交互** — 点击「查看手牌」→ 自按钮/底栏区域 **全息投影** → 扫描线/辉光/透视成形 → **闪/glitch 节拍** → 展示与现抽屉相同的内容；**再次点击同一按钮或全息区关闭控件** → 反向收起 |
| 4 | **视觉** — **磷光绿 CRT 全息**（`--dp-accent` / `#4af626`，与 `retro8bit` token 一致）；扫描线、外发光、轻微透视，禁止紫色渐变等通用 AI 审美 |
| 5 | **翻前** — 无服务端牌力时 **仅两张底牌**；`handRankName`（牌力）、`bestHandCards`（成牌/最佳五张）**仅在服务端下发后**由 `GamePlayerCard` 现有 computed 展示 |
| 6 | **内容** — 与 `GameDpGameSheets` 手牌抽屉 **同数据、同组件逻辑**：`GamePlayerCard` + `hero-hand-dock` + `skip-hole-deal-animation` + `deal-reveal-stagger-sec` |
| 7 | **扇形** — **不存在、不引用、不预留** fan 相关 DOM/CSS/状态 |

## 2. 现状盘点（只读核查，2026-05-31）

### 2.1 扇形（fan）状态

- 全库 `front/dp_game` **无** `handFan`、`fan-open`、`GameHeroHandFan` 等手牌扇形实现。
- `grep fan` 命中均为 `PingFang` 字体、`font-weight: 600` 等无关项。
- **结论：按用户回退后的真实代码状态规划，全息方案从零接入，不迁移 fan 资产。**

### 2.2 组件与 DOM 流

```
game.vue
├── footer → GameHeroDockFooter.vue
│   ├── 宽屏行 .dp-game-hero-action-row（>600 且非 layout-fs/全屏）
│   │   ├── 「查看手牌」→ SET_MOBILE_SHEETS showMobileHandSheet: true
│   │   └── 翻牌后内联 .dp-game-hero-dock → GamePlayerCard (hero-hand-dock)
│   └── 窄屏/全屏底栏 .dp-game-mobile-hero-bar（≤600 或 layout-fs/全屏）
│       └── 「查看手牌」→ 同上 commit
└── GameDpGameSheets.vue
    └── game-bottom-sheet（showMobileHandSheet）
        └── GamePlayerCard（与内联 dock 相同 props 集）
```

| 触点 | 文件 | 当前行为 |
|------|------|----------|
| 「查看手牌」按钮 | `GameHeroDockFooter.vue` L41–48（宽）、L164–171（移动底栏） | 一律 `SET_MOBILE_SHEETS { showMobileHandSheet: true }` |
| 手牌抽屉 | `GameDpGameSheets.vue` L4–37 | `v-if="showMobileHandSheet && heroDockRow"`，标题「我的手牌」 |
| 内联手牌 dock | `GameHeroDockFooter.vue` L53–76 | `v-if="heroDockRow && showBottomHeroDock"`（**仅翻牌后**宽屏常驻） |
| 按钮显隐 | `dpGame.js` getter `showHeroViewHandButton` | 有 `heroDockRow`；翻前需 `heroHoleDealIntroDone` |
| 翻前内联隐藏 | `showBottomHeroDock` | `stage !== 'preflop'` |

**关键缺口（待本需求填补）：** 宽屏 `retro8bit` 下翻前无内联 dock，点「查看手牌」仍打开 **底部抽屉**，与 PM「宽视口全息」不一致。

### 2.3 `GamePlayerCard` 与牌力（内容真源）

| 逻辑 | 位置 | 行为 |
|------|------|------|
| 底牌区 `showHoleCardsArea` | `heroHandDock && isMe` | 抽屉 `skipHoleDealAnimation` → 始终渲染底牌行；翻前 intro 结束后宽屏 dock 收起，靠「查看手牌」 |
| 牌力区 `showHandRankSection` | `communityCards.length >= 3` + 翻牌完成/摊牌条件 | 翻前 **不展示**（符合 PM） |
| `displayHandRankName` | `player.handRankName` 服务端字段 | 无则隐藏 pill |
| `showHandRankFiveCardRow` | `player.bestHandCards.length === 5` | 无则隐藏最佳五张行 |
| 模板分支 | `v-else-if="heroHandDock"` | 精简头 + 底牌行 + 牌力块（L147–229） |

抽屉与内联 dock 共用 props（`GameDpGameSheets.vue` L14–35 vs `GameHeroDockFooter.vue` L57–76），全息 **应复用同一 props 表**，避免第三套展示逻辑。

### 2.4 布局断点与全屏（与聊天翻板对齐）

| 条件 | CSS/行为 | 手牌 UI |
|------|----------|---------|
| `innerWidth > 600` 且非全屏 | `.dp-game-hero-action-row` 显示；`.dp-game-mobile-hero-bar` 隐藏（L1491–1494） | 宽屏内联 dock + 工具栏按钮 |
| `≤600px` | 隐藏 hero-action-row；显示 mobile-hero-bar | 底栏 + 底部抽屉 |
| `layout-fs` / `:fullscreen` | 隐藏 hero-action-row；显示 mobile-hero-bar（L1024–1085） | 底栏 + 底部抽屉（**即使宽度 >600**） |

PM：**>600px 含全屏** → 门控用 **`window.innerWidth > 600`**，**不**用「是否 mobile-hero-bar」作为动画条件。即：桌面全屏宽窗仍走全息；仅物理宽度 ≤600 走抽屉。

### 2.5 样式与参考动效

| 文件 | 可借鉴 |
|------|--------|
| `dp-game-themes.css` L132–153 | retro8bit 磷光绿 token、`--dp-font-pixel` / `--dp-font-ui` |
| `dp-game-shell.css` L786–848 | 公共牌 CRT `::after` 扫描线（community cards） |
| `dp-game-shell.css` L1201–1290 | retro8bit 聊天翻板 + ECG（**模式参考**：`perspective`、`rotateX`、`animationend` 阶段机） |
| `GameRoomChatPanel.vue` | 已实现 `useRetroChatReveal` 门控 + `revealPhase` 状态机（实现时对齐范式） |
| `dp-game-modals.css` L313+ | 底部抽屉 mask `z-index: 1010` — 全息层需 **≥1015** 且低于 seat-chat 气泡（35–42）时注意 stacking |
| `data-dp-hero-deal-target` | 发牌动画锚点；全息 **投影原点** 应 `querySelector` 最近激活的该按钮 |

### 2.6 不在范围

- `GameOwnerPanel.vue` 的 `show-hand-sheet` emit（当前无父级监听，房主神器 sheet 内 `hideToolEntry`）— 本迭代不改为全息，除非 PM 追加。
- 圆桌 `rival-mini` 本人席、牌谱、对手历史弹窗。

## 3. 视觉与动效规格

### 3.1 用户叙事（retro8bit + 宽视口）

1. 点击「查看手牌」→ 自按钮位置向上（或略前倾）射出 **磷光绿光束/锥形渐变**（`projecting`）。
2. 光束末端 **全息面板** 以底边或按钮为铰链 **立起**（`materializing`：`perspective` + `scaleY`/`rotateX` + 扫描线 `repeating-linear-gradient`）。
3. 面板成形瞬间 **短 glitch/flash**（`flash`：1–2 帧级 `opacity` + `translateX` 抖动或 `clip-path` 闪烁，≤120ms）。
4. 闪后 **内容显现**：`GamePlayerCard` 底牌 + 牌力 + 最佳五张（与抽屉一致）。
5. 再次点击「查看手牌」或全息 **关闭** → `collapsing` 反向（投影收束 + 面板塌下/淡出）。
6. `ecoMode` 或 `prefers-reduced-motion: reduce` → **瞬间展开/收起**，跳过光束/flash（与聊天、公共牌 flip 一致）。

### 3.2 不动效范围

- 主题 ≠ `retro8bit`：`showMobileHandSheet` 底部抽屉，**零 3D/全息**。
- `viewportWidth ≤ 600`：底部抽屉，即使 `layout-fs` / 浏览器全屏。
- 非 `showHeroViewHandButton` 时不渲染按钮，全息也不得打开。

### 3.3 时序（标准档，可调）

```
点击打开（用户意图 open=true）
│
├─ [0] idle
├─ [1] projecting      280ms   锚点 → 面板方向的 beam/锥光 opacity+scale
├─ [2] materializing   420ms   面板 rotateX/scale + 扫描线扫过
├─ [3] flash           100ms   glitch 节拍（内容 opacity 0）
└─ [4] revealed        内容 opacity 1；GamePlayerCard 可交互（仅查看，无下注）

点击关闭
│
├─ [4] revealed
├─ [5] collapsing      360ms   可选短 flash 后 reverse projecting
└─ [0] idle            卸载/隐藏 scene；open=false
```

总展开体感 ~**0.8–0.9s**；收起 ~**0.36–0.45s**（exit 略快于 enter，符合 ui-ux-pro-max）。

**Instant 路径：** `[0] ↔ [4]`，`revealed` 时直接挂载 `GamePlayerCard`。

### 3.4 ASCII 结构（宽视口 retro8bit）

```
                    ┌─────────────────────────────┐
                    │  GamePlayerCard (hand-dock) │  ← revealed
                    │  [♠A][♥K]  牌力  成牌五张    │
                    ├─────────────────────────────┤
                    │ ░░ scanlines + glow border ░│  ← materializing
                    └──────────────┬──────────────┘
                                   │ hinge / 投影轴
              ╱╱╱ 磷光光束 ╱╱╱     │
┌──────────┐ ┌──────────┐ ┌────────────────────────┐
│ 主动离座 │ │ 查看手牌 │ │ 聊天 … │ 说一句 …      │
└──────────┘ └────┬─────┘ └────────────────────────┘
                  data-dp-hero-deal-target（投影锚点）
```

### 3.5 字体与可读性（retro8bit）

| 元素 | 字体 | 备注 |
|------|------|------|
| 全息标题「我的手牌」/ 关闭 | `--dp-font-pixel` | 与 `.dp-game-sheet__title` retro8bit 规则一致 |
| 牌力 pill、底牌 rank 文本 | 沿用 `GamePlayerCard` + `card-base` | 已受 retro8bit body 规则约束 |
| 辅助说明 | `--dp-font-ui` | 中文可读 monospace 栈 |

对比度：磷光绿文字在 `#12151a` 全息底上须满足 **≥4.5:1**（ui-ux-pro-max P1）；必要时内容区用 `color-mix` 略提亮正文，**不改**牌面 rank 字号。

## 4. 架构设计

### 4.1 门控条件（单一 computed，建议放 `GameHeroHandHologram.vue` 或 `game.vue` mixin）

```js
useRetroHandHologram =
  gameUiTheme === 'retro8bit'           // gameUiTheme / data-dp-game-theme
  && viewportWidth > 600                // resize debounce 150ms；601 开 / 600 关
  && !ecoMode
  && !prefersReducedMotion
```

CSS 双保险（防 FOUC）：

```css
@media (min-width: 601px) {
  .dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-hero-hand-hologram { … }
}
```

### 4.2 路由：点击「查看手牌」

```js
onViewHandClick() {
  if (!showHeroViewHandButton || !heroDockRow) return
  if (useRetroHandHologram) {
    if (hologramOpen) closeHologram()
    else openHologram()
  } else {
    SET_MOBILE_SHEETS({ showMobileHandSheet: !showMobileHandSheet }) // 或仅 true；窄屏保持 toggle 关闭靠 sheet @close
  }
}
```

| 路径 | `showMobileHandSheet` | `showHeroHandHologram`（新 state） |
|------|----------------------|----------------------------------|
| retro8bit + 宽 | **false**（`v-if` 门控） | toggle |
| 其它 / 窄 | true/false | **false** |

**互斥：** 打开全息时强制 `showMobileHandSheet: false`；打开抽屉时强制关闭全息。

### 4.3 阶段状态机

| 阶段 | 说明 | 用户再点「查看手牌」 |
|------|------|---------------------|
| `idle` | 无全息 DOM 或仅透明占位 | → `projecting` |
| `projecting` | 光束动画 | → `collapsing`（可中断） |
| `materializing` | 面板立起 + 扫描线 | → `collapsing` |
| `flash` | glitch 节拍 | → `collapsing` |
| `revealed` | 内容可见 | → `collapsing` |
| `collapsing` | 反向 | 忽略至 `idle` |

字段建议：

- `hologramPhase: 'idle' | 'projecting' | 'materializing' | 'flash' | 'revealed' | 'collapsing'`
- `hologramOpen: boolean`（用户意图，与聊天 `expanded` 同责）
- Vuex：`showHeroHandHologram`（bool）便于 `game.vue` 在 `heroDockRow` 消失时 commit 关闭（对标 L235 `showMobileHandSheet`）

`animationend` / `transitionend` 驱动阶段推进；`flash` 可用 `setTimeout` 固定时长。

### 4.4 组件分层

| 组件 | 职责 |
|------|------|
| **`GameHeroHandHologram.vue`（新建，P0）** | 门控、阶段机、锚点定位（`getBoundingClientRect` on `[data-dp-hero-deal-target]`）、光束/面板/扫描线/flash 装饰层、`Teleport` 至 `body` 或 `gameRoot` |
| **`GameHeroDockFooter.vue`（改，P0）** | 按钮 `@click` 改为调用 `vm.onHeroViewHandClick()`（由 `game.vue` provide 或 store action） |
| **`GameDpGameSheets.vue`（改，P0）** | 手牌 sheet `v-if` 增加 `&& !useRetroHandHologram`（computed 与全息组件同源） |
| **`game.vue`（改，P0）** | 挂载 `<game-hero-hand-hologram />`；`heroDockRow` watch 关闭全息；实现 `onHeroViewHandClick` |
| **`GamePlayerCard.vue`（复用，P0）** | 全息 **内容区** 内嵌，props 抄 `GameDpGameSheets` L14–35 |
| **`GamePlayerCardHandContent.vue`（可选，P1）** | 仅当全息需去掉 `__head--hand-dock-only` 等 chrome 时再抽 |

**锚点策略：** 优先 `document.querySelector('[data-dp-hero-deal-target]')` 中 **可见** 按钮（宽屏行 vs 底栏二选一）；全屏宽窗用底栏按钮。面板 `position: fixed; left/top` 由锚点 rect + `transform-origin: 50% 100%` 定位在按钮上方。

### 4.5 proposed DOM（`GameHeroHandHologram.vue`）

```html
<Teleport to="body">
  <div
    v-show="sceneVisible"
    class="dp-game-hero-hand-hologram"
    :class="phaseClass"
    role="dialog"
    aria-label="我的手牌"
    :aria-hidden="hologramPhase === 'idle'"
  >
    <div class="dp-game-hero-hand-hologram__beam" aria-hidden="true" />
    <div
      class="dp-game-hero-hand-hologram__panel"
      :style="panelAnchorStyle"
      @animationend="onPanelAnimEnd"
    >
      <div class="dp-game-hero-hand-hologram__scanlines" aria-hidden="true" />
      <div class="dp-game-hero-hand-hologram__flash" aria-hidden="true" />
      <div v-show="hologramPhase === 'revealed'" class="dp-game-hero-hand-hologram__content">
        <game-player-card … />  <!-- 同 GameDpGameSheets -->
      </div>
      <button type="button" class="dp-game-hero-hand-hologram__close" @click="close">×</button>
    </div>
  </div>
</Teleport>
```

- 半透明遮罩：**可选**（PM 未要求 dim）；若加则 `pointer-events` 仅遮罩点击关闭，勿挡底栏「查看手牌」toggle。
- **关闭：** 再点「查看手牌」、关闭钮、（可选）Esc — `game.vue` 键盘监听 P1。

### 4.6 Clipping / z-index

| 风险 | 缓解 |
|------|------|
| `.dp-game-root--layout-fs { overflow: hidden }` | **Teleport `body`**，定位用 fixed + 锚点 rect |
| 与聊天翻板、底部 sheet 重叠 | 全息 `z-index: 1020`；sheet mask 1010；教程/guide 联调 P1 |
| 3D flatten | `perspective` 设在 `__panel` 祖先 |
| resize 跨 600px | debounce 后：若 `hologramOpen`，instant 切 legacy 抽屉或强制 close + toast 无 |

## 5. P0 / P1 范围

### P0（本迭代必做）

1. 新建 `GameHeroHandHologram.vue` + retro8bit 宽视口门控与阶段机动效（投影 → 成形 → flash → 内容）。
2. `GameHeroDockFooter` 按钮改路由；`GameDpGameSheets` 手牌 sheet 与全息互斥。
3. Vuex `showHeroHandHologram`（或等价）+ `game.vue` 生命周期清理（离座、无 `heroDockRow`、换局）。
4. 内容区 **复用 `GamePlayerCard`**（与抽屉相同 props）；翻前仅底牌、翻后牌力/成牌遵循现有 computed。
5. `ecoMode` + `prefers-reduced-motion` instant 路径（JS + `dp-game-eco-mode.css`）。
6. retro8bit 磷光绿全息样式（`dp-game-themes.css` / `dp-game-shell.css` 专用块）。
7. **零 fan**；代码评审禁止 reintroduce fan 命名。

### P1（可跟进）

1. `dp-motion-tokens.css`：`--dp-motion-duration-hand-hologram-*`。
2. 抽 `GamePlayerCardHandContent.vue` 减负。
3. Esc 关闭、focus trap、`aria-modal="true"`。
4. `GameButtonGuidePage` 教程步若指向「查看手牌」，补 `openForGuide` 类 API（对标聊天 `openForGuide`）。
5. resize 跨断点 polish；展开时轻微 `box-shadow` 脉动（磷光 accent）。
6. 独立样式表 `dp-game-retro-hand-hologram.css` 若 shell 过长。

### 明确不做（本阶段）

- ≤600px 全息（含全屏手机竖屏）。
- 非 retro8bit 主题全息。
- 扇形（fan）展开或任何扇形关键词实现。
- 修改 `handRankName` / `bestHandCards` 服务端计算。
- 房主面板 `show-hand-sheet` 接线（除非 PM 追加）。

## 6. 待改文件清单

| 文件 | 变更类型 |
|------|----------|
| `front/dp_game/src/components/GameHeroHandHologram.vue` | **新建** — 门控、阶段机、动效 DOM、内嵌 `GamePlayerCard` |
| `front/dp_game/src/components/GameHeroDockFooter.vue` | 按钮 click → `onHeroViewHandClick` |
| `front/dp_game/src/components/GameDpGameSheets.vue` | 手牌 sheet `v-if` 增加窄屏/非 retro 门控 |
| `front/dp_game/src/components/game.vue` | 挂载全息组件；`onHeroViewHandClick`；watch 清理 |
| `front/dp_game/src/store/modules/dpGame.js` | `showHeroHandHologram` state + `SET_MOBILE_SHEETS` / 新 mutation 互斥 |
| `front/dp_game/src/styles/dp-game-shell.css` | retro8bit @601px 全息定位、3D、keyframes |
| `front/dp_game/src/styles/dp-game-themes.css` | 全息标题像素字、面板边框/辉光 token |
| `front/dp_game/src/styles/dp-game-eco-mode.css` | 禁用全息 keyframes |
| `front/dp_game/src/styles/dp-motion-tokens.css` | P1：时长 token |

**不改：** 后端、`GamePlayerCard` 牌力 computed 逻辑（除非抽组件 P1）、`GameBottomSheet.vue` 行动/房主 sheet。

## 7. 验收标准

### 7.1 功能

- [ ] `retro8bit` + 视口 601px+：点「查看手牌」播放全息序列后显示与 **现抽屉相同** 的底牌/牌力/成牌；再点收起。
- [ ] 翻前无 `handRankName`：全息内 **仅两张底牌**，无牌力 pill、无最佳五张。
- [ ] 翻后服务端有 `handRankName` / `bestHandCards`：全息内与抽屉一致展示。
- [ ] `default` / 其它主题 + 任意宽度：仍仅底部抽屉，无全息 DOM。
- [ ] 视口 600px 及以下：行为与现网抽屉一致（含 `layout-fs` 手机全屏）。
- [ ] 视口 601px+ 桌面 **layout-fs / 浏览器全屏**：全息生效（底栏按钮锚点）。
- [ ] `ecoMode` 或系统「减少动态效果」：展开/收起瞬间完成，内容正确。
- [ ] 离座 / `heroDockRow` 消失：全息与抽屉均关闭。
- [ ] 代码库 **无** fan 手牌相关实现或文档引用。

### 7.2 视觉

- [ ] 磷光绿 CRT：扫描线、外发光、透视与 `retro8bit` 公共牌/聊天风格协调。
- [ ] flash 节拍短促可感知，不长时间遮挡阅读。
- [ ] 全息标题（若有）为像素字；牌面 rank 可读性不劣于抽屉。

### 7.3 性能与 a11y

- [ ] 动画主用 `transform` + `opacity`（flash 可用短时 `clip-path`）。
- [ ] 「查看手牌」保持 ≥44px 触达高度（现有底栏已满足）。
- [ ] `role="dialog"` + `aria-label="我的手牌"`；关闭后焦点回到按钮（P1 可加强）。
- [ ] 无 CLS：revealed 前面板预留 min-height ≈ 抽屉内手牌块高度。

## 8. 验证步骤（实现后）

1. **主题矩阵**：`retro8bit` vs `default`，各开/关手牌 3 次。
2. **视口矩阵**：375px（仅抽屉）、768px 窗口（全息）、1280px + layout-fs（全息，锚点为底栏按钮）。
3. **阶段矩阵**：preflop（仅底牌）、flop+（有牌力时 pill + 五张）。
4. **eco / PRM**：TopBar 节能 ON；系统减少动态效果。
5. **互斥**：宽屏 retro 打开全息时不应同时出现底部「我的手牌」sheet。
6. **回归**：行动抽屉、房主 sheet、聊天 retro 翻板不受影响。
7. **DevTools Performance**：展开一次，主线程无 >50ms 长任务（标准档）。

## 9. 实现交接步骤（给开发）

1. **Store**：`showHeroHandHologram` + mutation；与 `showMobileHandSheet` 互斥 helper。
2. **门控 util/computed**：与 `GameRoomChatPanel.useRetroChatReveal` 同构（可复制 resize/PRM 监听模式）。
3. **新建 `GameHeroHandHologram.vue`**：阶段机 + Teleport + 锚点定位；内嵌 `GamePlayerCard`（props 从 `GameDpGameSheets` 复制）。
4. **改 `GameHeroDockFooter.vue`**：两处「查看手牌」统一 `onHeroViewHandClick`。
5. **改 `GameDpGameSheets.vue`**：`v-if="showMobileHandSheet && heroDockRow && !useRetroHandHologramWide"`。
6. **改 `game.vue`**：注册组件、provide 方法、watch `heroDockRow`。
7. **CSS**：`dp-game-shell.css` + `dp-game-themes.css` keyframes `dp-retro-hand-hologram-project` / `materialize` / `flash` / `collapse`；`dp-game-eco-mode.css` 禁用。
8. **自测 §8**；PR 附录屏（retro8bit 宽屏展开/收起 + default 对照 + 翻前仅底牌）。

## 10. 设计原则对齐（skills 摘要）

- **frontend-design**：单一高 impact moment（全息投影 + glitch Reveal），磷光绿 CRT 与 retro8bit 世界观一致；避免分散微动效或与主题无关的紫色渐变。
- **ui-ux-pro-max**：动效 150–500ms 分段、exit 快于 enter；PRM/eco 必降级；`transform`-only；门控避免在 ≤600px 引入 precision tap 问题；对比度与 44px 触达。

---

*文档版本：2026-05-31 · 快照组件：`GameHeroDockFooter.vue`、`GameDpGameSheets.vue`、`GamePlayerCard.vue`、`game.vue`、`dpGame.js` getters；**已确认无 fan 实现**。*
