# retro8bit 摊牌亮牌 CRT 扫描线

> 状态：**计划文档（PM 已确认需求，待实现）**  
> 非目标：本阶段不改后端/WebSocket；不为非 `retro8bit` 主题加扫描线；不改动 rank 字体栈与字号；不改造公共牌（已完成）；不把牌谱/弹窗小牌/盖牌背面纳入；≤600px 不做专项 QA（CSS 可写但不验收）；不提交 dist。

## 1. 产品决策摘要（PM 确认）

| # | 决策 |
|---|------|
| 1 | **全部** — 牌桌摊牌时所有 **已亮出** 的牌面：对手 hole cards + 牌型区 best-hand 五张 + 英雄全部可见面（全息 / 底部 dock / 移动端 sheet） |
| 2 | **摊牌** — 仅 `stage === 'showdown'` 或 `stage === 'settled'`（牌实际公开时）；翻前/翻后/河牌圈未摊牌、房主「看穿底牌」提前可见 **均不加** 扫描线 |
| 3 | **自己也要** — 英雄位与对手一致：宽屏全息手牌（若打开）、`GameHeroDockFooter` 底栏 dock、窄屏 `GameDpGameSheets` 手牌 sheet（摊牌时若可见） |

### 默认（不变）

| 项 | 约定 |
|----|------|
| 主题 | 仅 `retro8bit` |
| 扫描线风格 | 与公共牌一致：**细黑横线**（2px 透明 + 1px 黑线，周期 3px），**非**粗色带 |
| 可读性 | rank 字体/字号/字重不变；扫描线为 `::after` 半透明 overlay，不挡文字 |
| QA 视口 | 宽屏 + 全屏（`layout-fs`）优先；≤600px CSS 可写但不验收 |
| eco / PRM | 扫描线 **静态** CSS，无额外 animation |

## 2. 现状盘点

### 2.1 组件与 DOM（摊牌亮牌触点）

```
game.vue (.dp-game-root[data-dp-stage][data-dp-game-theme])
├── GameRoundTable.vue
│   └── .dp-game-table__seat
│       └── GamePlayerCard (rival-mini=true)
│           ├── .dp-player-card__hole-row--rival
│           │   └── .hole-card-fly-wrapper → .hole-card-flip.card-base.bg-*   ← 对手亮牌
│           └── .dp-player-card__hand-rank--rival
│               └── .dp-player-card__best-hand--rival-leader
│                   └── .dp-player-card__best-card.card-base.bg-*             ← 领先者 best 5
├── GameHeroDockFooter.vue
│   └── .dp-game-hero-dock[--hand-reveal]
│       └── GamePlayerCard (hero-hand-dock)
│           ├── .dp-player-card__hole-row--hand-dock → .hole-card-flip
│           └── .dp-player-card__best-hand → .dp-player-card__best-card
├── GameDpGameSheets.vue（≤600 或全息 fallback）
│   └── .dp-game-hero-dock--in-sheet[--hand-reveal] → 同上结构
└── GameHeroHandHologram.vue（retro8bit + viewport >600）
    └── .dp-game-hero-hand-hologram__content
        └── .dp-game-hero-dock--in-sheet → GamePlayerCard (hero-hand-dock)
```

| 触点 | 文件 | 摊牌时 DOM | 现状扫描线 |
|------|------|------------|------------|
| 对手迷你席 | `GamePlayerCard.vue` L68–138 | `showHoleCardsRevealed` + `showHandRankSectionRivalSeat` | **无** |
| 英雄底栏 dock | `GameHeroDockFooter.vue` L52–76 | `showBottomHeroDock` + `dp-game-hero-dock--hand-reveal` | **无** |
| 移动端 sheet | `GameDpGameSheets.vue` L5–30 | `showMobileHandSheet` + `--hand-reveal` | **无** |
| 全息手牌 | `GameHeroHandHologram.vue` L34–64 | `hologramPhase === 'revealed'` | hole row **有**（**无 stage 门控**）；best-hand **无** |
| 公共牌 | `GameCommunityCards.vue` | 全阶段 flop+ | **有**（本需求不重复改） |

### 2.2 可见性逻辑（JS，P0 不改）

`GamePlayerCard.vue` computed（实现者须理解，CSS 用 `data-dp-stage` 门控即可）：

| 条件 | 行为 |
|------|------|
| `showHoleCardsRevealed` | 本人 / 房主看穿 / **`showdown`∨`settled` 且未 fold** |
| `showHandRankSection` | community ≥3 + boardOk + 同上可见性 |
| `showHandRankFiveCardRow`（rival-mini） | 仅 `showShowdownLeaderDetail`（牌力最高者并列）返回 5 张 |
| `showHandRankFiveCardRow`（hero dock） | 服务端 `bestHandCards.length === 5` 即展示 |
| 圆桌本人 rival-mini | `showHoleCardsArea` 摊牌前仅 preflop 飞入；**摊牌 hole 在 dock/全息，不在环上** |

**推论：** CSS 阶段门控 `[data-dp-stage='showdown'|'settled']` 与 JS 亮牌时机对齐；盖牌/未参战玩家无 revealed DOM → 自然无扫描线。

### 2.3 已有扫描线实现（复用基准）

`dp-game-themes.css` L788–899：

| 块 | Scope | Token |
|----|-------|-------|
| 公共牌 | `.dp-game-table__center .community-cards-row` | `--dp-card-community-scanline-*` |
| 全息 hole | `.dp-game-hero-hand-hologram .dp-player-card__hole-row .card-base` | 同上 token 别名 |

**扫描线 grammar（必须一致）：**

```css
--dp-card-community-scanline-period: 3px;
--dp-card-community-scanline-thick: 1px;
--dp-card-community-scanline-color: rgba(0, 0, 0, 0.38);
--dp-card-community-scanline-opacity: 0.4;

.card-base::after {
  display: block;
  content: '';
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent calc(period - thick),
    color calc(period - thick),
    color period
  );
  background-size: 100% period;
  opacity: var(--dp-card-community-scanline-opacity);
}
```

全局 `dp-poker-cards.css` L50–52：`.card-base::after { display: none }` — retro8bit 块须 **`display: block`** 显式开启。

### 2.4 阶段门控锚点

`game.vue` L13：`.dp-game-root[data-dp-stage="stage"]` — 取值含 `showdown`、`settled` 等。  
**本需求唯一阶段门控：** 选择器前缀

```css
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='showdown'] …,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='settled'] …
```

## 3. 视觉规格

### 3.1 目标观感

1. 进入 **摊牌 / 准备下一局**，牌桌上已公开的 hole / best-hand 牌面出现与 **公共牌同族** 的细横 CRT 扫描线。
2. 扫描线为 **静态** 半透明 overlay；rank（`A`/`10`/`K`）保持现网 crisp 字体。
3. 非摊牌阶段（含翻前看自己手牌、全息「查看手牌」、河牌圈未摊牌）→ **零扫描线**。
4. 其它主题、牌谱弹窗、盖牌背面 `?` → 零变更。

### 3.2 不动范围

| 元素 | 保持 |
|------|------|
| rank typography | `font-family` / `font-size` / `font-weight` / 现有 `text-shadow` |
| 公共牌 | 已有块不动（或仅 P1 token 上提时 refactor） |
| 盖牌背面 / placeholder | 无扫描线 |
| 牌型 pill / rank-detail 文本 | 非 card-base，不加扫描线 |
| hole-deal / best-hand-pop / flip 动效 | keyframes 不动 |
| eco / PRM | 扫描线不 animate；与 eco 下 instant flip 兼容 |

### 3.3 与公共牌差异

| 维度 | 公共牌 | 摊牌亮牌 |
|------|--------|----------|
| 阶段 | flop 起全程 | **仅 showdown / settled** |
| 牌尺寸 | 44×62 community | 28×40 rival hole、24×34 rival best、32×46 dock best 等 |
| 3D flip | community flip inner | hole `hole-card-flip` animation |
| 边框 | 磷光绿 hard border（community 块） | P0 **仅扫描线**；硬边/像素底纹 **不做**（避免 scope 膨胀） |

## 4. 架构设计

### 4.1 门控条件（AND）

| 门控 | 实现 |
|------|------|
| 主题 | `[data-dp-game-theme='retro8bit']` |
| 阶段 | `[data-dp-stage='showdown']` **或** `[data-dp-stage='settled']` |
| 区域 | 见 §4.2 选择器清单 |
| eco / PRM | 不 disable 扫描线（静态 overlay，无 motion 成本） |

### 4.2 选择器 scope（P0 必做）

**共享前缀（建议抽为注释块 §showdown-scanlines）：**

```css
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='showdown'] …,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='settled'] …
```

#### A. 对手 rival-mini（牌桌座位）

Token 挂载点（任选其一子树根）：

```css
… .dp-player-card--rival-mini { --dp-card-community-scanline-*: …; }
```

| 目标 | 选择器后缀 |
|------|------------|
| 亮牌 hole | `.dp-player-card__hole-row--rival .hole-card-flip.card-base` |
| 领先者 best 5 | `.dp-player-card__best-hand--rival-leader .dp-player-card__best-card.card-base` |

配套 wrapper（可选，对齐 public 牌 drop-shadow）：

```css
… .dp-player-card__hole-row--rival .hole-card-fly-wrapper { filter: drop-shadow(…); image-rendering: pixelated; }
```

#### B. 英雄底栏 dock（宽屏摊牌）

```css
… .dp-game-hero-dock:not(.dp-game-hero-dock--in-sheet) .dp-player-card--hand-dock …
```

| 目标 | 后缀 |
|------|------|
| hole | `.dp-player-card__hole-row--hand-dock .hole-card-flip.card-base` |
| best 5 | `.dp-player-card__best-hand .dp-player-card__best-card.card-base` |

#### C. 移动端 sheet

```css
… .dp-game-hero-dock--in-sheet .dp-player-card--hand-dock …
```

（hole + best-hand 选择器同 B）

#### D. 全息手牌（**扩展现有块 + 加 stage 门控**）

**P0 必做 refactor：**

1. 现有 L856–899 全息 hole 扫描线 **加上** `[data-dp-stage='showdown'|'settled']` 前缀（摊牌前打开全息 → 无扫描线）。
2. **新增** 同条件下 hologram 内 `.dp-player-card__best-hand .dp-player-card__best-card.card-base::after`。

```css
body[data-dp-game-theme='retro8bit'] …,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='showdown'] .dp-game-hero-hand-hologram …
```

> 全息组件挂在 `body` 下时，`body[data-dp-game-theme='retro8bit']` 仍需要，但 **必须** 叠加 stage 条件（可通过 `.dp-game-root[data-dp-stage]` 兄弟/祖先 — 全息为 gameRoot 子节点，用 `.dp-game-root[data-dp-stage='showdown'] .dp-game-hero-hand-hologram` 即可）。

#### E. 禁止误伤

**不得**仅写：

- `.card-base::after`（全站）
- `.hole-card-flip::after` 无 stage 门控（会污染翻前飞入）
- `.dp-player-card__hole-back-rival`（盖牌背面）
- `.community-cards-row`（已由公共牌块覆盖，本需求不重复）

### 4.3 伪元素与动效交互

| 场景 | 注意 |
|------|------|
| `hole-card-flip` rotateY | `::after` 设 `backface-visibility: hidden`；card-base 已有 |
| `best-hand-card-enter` pop | 扫描线在 card 上，随 transform 同步；不单独 animate opacity |
| `.best-hand-cards .best-hand-card::after`（dp-poker-cards L225） | 全局 `display:none`；retro8bit showdown 块 `display:block` 覆盖 |
| rival 小牌 overflow | 确认 `overflow:hidden` + `border-radius` 不 clip 扫描线 |

### 4.4 文件变更优先级

| 优先级 | 文件 | 变更 |
|--------|------|------|
| **P0** | `front/dp_game/src/styles/dp-game-themes.css` | 新增 **showdown scanlines block**；refactor 全息块加 stage 门控 + best-hand |
| **P0** | — | **零** Vue/JS 变更（纯 CSS） |
| **P1** | `dp-game-themes.css` 或新建 `dp-game-retro8bit-showdown-scanlines.css` | token 上提到 `.dp-game-root[data-dp-game-theme='retro8bit']`；DRY 公共 `@mixin` 式注释块 |
| **P1** | `dp-game-shell.css` | 若 dock/hologram 需 hook class（仅当选择器过长） |
| **验证** | `dp-game-eco-mode.css` | 确认无冲突；扫描线静态，无需 eco 特例 |
| **不改** | `GamePlayerCard.vue` / `game.vue` | P0 |
| **不改** | `dp-poker-cards.css` 全局段 | P0 |

## 5. 待改文件清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `front/dp_game/src/styles/dp-game-themes.css` | **新增 + 修改** | §showdown-scanlines；全息 L856–899 stage 门控 + best-hand |
| `docs/refactor/retro8bit-showdown-cards-pixel-plan.md` | 本文档 | 计划 |
| `front/dp_game/src/styles/dp-game-retro8bit-showdown-scanlines.css` | P1 可选新建 | themes 过长时拆分 |
| `front/dp_game/src/components/*.vue` | **不改** | P0 |
| 后端 / WebSocket | **不改** | — |

## 6. 验收标准

### 6.1 功能 / 回归

- [ ] retro8bit 宽屏：完整一手打至 **showdown**，所有 **未 fold** 对手 hole 牌有扫描线；fold 玩家无 hole 行 → 无扫描线。
- [ ] 牌力最高者（含并列）rival-mini **best 5** 有扫描线；非领先者无 best 行 → 无扫描线。
- [ ] 英雄：摊牌时 bottom dock hole + best 5 有扫描线；`settled` 阶段仍保留至下一手开始。
- [ ] 英雄全息（宽屏 >600，摊牌时打开）：hole + best 5 有扫描线；**翻前打开「查看手牌」无扫描线**。
- [ ] 窄屏 sheet（若摊牌可见）：hole + best 5 有扫描线。
- [ ] 河牌圈 / 翻后 **未进入 showdown**：对手与自己 **均无** 扫描线。
- [ ] 房主「看穿底牌」在 **非摊牌阶段**：**无** 扫描线。
- [ ] default / gothic / sakura：摊牌亮牌 **零视觉变更**。
- [ ] 公共牌扫描线：与现网一致，无回归。
- [ ] ecoMode ON / OS PRM：摊牌扫描线 **仍显示**（静态）；flip/pop instant 路径无 glitch。

### 6.2 视觉（retro8bit + 宽屏 / 全屏）

- [ ] 扫描线为 **细黑横线**，与 table center 公共牌 **同周期/同粗细**（肉眼一致）。
- [ ] **非** 粗色带、非 seat-enter 动画扫描遮罩。
- [ ] rank 可读性 ≥ 现网（`A`/`10`/`K` 不糊、不被线压暗过度）。
- [ ] rival 28×40、best 24×34 小牌上扫描线不断层、不溢出圆角。
- [ ] 全屏 `layout-fs`：多座位同时亮牌无 z-fighting。

### 6.3 性能

- [ ] 无新增 keyframes / infinite animation。
- [ ] 无 `filter: blur()` on 牌面（wrapper `drop-shadow` 可选，与 public 牌一致）。
- [ ] 一手 showdown Chrome Performance：相对 baseline 无新增 >16ms 长任务。

### 6.4 范围外（本阶段不要求）

- [ ] ≤600px 专项 QA
- [ ] 牌谱 / hand-rank 弹窗 / modals 小牌
- [ ] 公共牌像素底纹二次改造
- [ ] 非 retro8bit 主题

## 7. 验证步骤（实现后）

### 7.1 环境

1. `npm run dev`，主题 **retro8bit**，视口 **1280×800** 与 **1920×1080**（窗口 + `layout-fs` 各测）。
2. 可选：≤600 冒烟（不阻塞 PR）。

### 7.2 对局流程（主路径）

1. 6 人桌打至 **showdown**（多人未 fold）— 确认 **每个** 亮牌对手 hole 有扫描线。
2. 确认 **牌力最高者** seat 下 best 5 有扫描线。
3. 确认 **英雄 bottom dock** hole + best 5 有扫描线。
4. 宽屏点击「查看手牌」打开全息 — hole + best 5 有扫描线。
5. 房主点「准备下一局」进入 **`settled`** — 扫描线仍在；新一手 `preflop` 开始 → **消失**。
6. 再开一手，**河牌圈停止**（未摊牌）— 确认 **无** 扫描线。

### 7.3 负例

1. **翻前**打开全息 / sheet — 无扫描线。
2. 房主开启看穿底牌，**flop 阶段** — 无扫描线。
3. 切换 **default** 主题 showdown — 无扫描线。
4. fold 玩家 seat — 无 hole 扫描线。

### 7.4 DevTools 快检

```js
// 摊牌时应 > 0
document.querySelectorAll(
  '.dp-game-root[data-dp-game-theme="retro8bit"][data-dp-stage="showdown"] ' +
  '.dp-player-card--rival-mini .hole-card-flip.card-base'
).length

// 河牌圈（stage 非 showdown）应为 0 命中 showdown 规则
document.querySelectorAll(
  '.dp-game-root[data-dp-stage="river"] .dp-player-card--rival-mini .hole-card-flip.card-base::after'
).length
```

Computed：命中元素 `::after` 的 `background` 含 `repeating-linear-gradient`，`opacity ≈ 0.4`。

对比公共牌：

```js
getComputedStyle(
  document.querySelector('.community-cards-row .card-front.card-base'),
  '::after'
).background
// 与 showdown hole card 的 ::after background 同结构
```

### 7.5 录屏交付

PR 附：**retro8bit 宽屏 fullscreen** showdown → settled 一段 + **河牌圈无扫描线** 5s + **default 对照** 5s。

## 8. 风险与缓解

| 风险 | 原因 | 缓解 |
|------|------|------|
| 全息块已无条件扫描线 | L856–899 缺 stage | P0 refactor 加 `[data-dp-stage='showdown'|'settled']` |
| 选择器过宽 | `.card-base::after` 全局 | 强制 rival-mini / hand-dock / stage 三前缀 |
| 小牌扫描线过密 | 物理尺寸 24–28px 宽 | 复用 **相同** 3px 周期（与公共牌一致）；QA 可读性不过则仅降 `--dp-card-community-scanline-opacity` |
| `::after` 与 flip 穿帮 | 3D transform | `backface-visibility: hidden` + `overflow: hidden` |
| 房主看穿误亮 | JS 提前 reveal | 阶段门控仅 CSS，不跟 `showHoleCardsRevealed` 走 |
| themes.css 膨胀 | 多 scope 重复 | P1 拆文件或 token 上提 |
| hologram portal | 组件在 gameRoot 内 | 用 `.dp-game-root[data-dp-stage] .dp-game-hero-hand-hologram` 即可 |

## 9. P0 / P1 范围

### P0（本迭代必做）

1. retro8bit + **showdown/settled** 门控下，对手 rival-mini **hole 亮牌 + best 5** 扫描线。
2. 英雄 **bottom dock + mobile sheet** hole + best 5 扫描线。
3. 全息：**stage 门控**现有 hole 扫描线 + **新增** best 5 扫描线。
4. 复用 `--dp-card-community-scanline-*` token；rank typography 不变。
5. 其它主题 + 非摊牌阶段 + 公共牌 **零回归**。

### P1（可跟进）

1. 将 `--dp-card-community-scanline-*` 上提到 `.dp-game-root[data-dp-game-theme='retro8bit']`，community / hologram / showdown 共用。
2. 拆 `dp-game-retro8bit-showdown-scanlines.css`。
3. 提取共享 SCSS-less 注释模板（scanline `::after` 一段复制 4 处 → 一处维护）。
4. rival/dock 牌面磷光 hard border（对齐 community 牌框 — **非本需求 P0**）。
5. ≤600px 专项 QA 与微调 opacity。

### 明确不做

- 非 retro8bit 主题。
- 盖牌背面 / 未 reveal 的 `?`。
- 公共牌块行为变更（除 P1 token refactor）。
- 牌谱、modal、hand-rank 参考页小牌。
- JS stage class 注入（已有 `data-dp-stage`）。
- 扫描线 animate / 扫光。

## 10. 实现交接步骤（给开发）

1. **读基准**：`Inspect` 摊牌后 `.community-cards-row .card-front::after`，记录 `--dp-card-community-scanline-*` computed 值。
2. **读全息现状**：翻前打开全息，确认 hole **不应**有扫描线（若现有 bug，P0 一并修）。
3. **写块**：在 `dp-game-themes.css` retro8bit 段 **公共牌块之后** 新增 `/* —— retro8bit：摊牌亮牌 CRT 扫描线 —— */`。
4. **阶段前缀**：每条规则以  
   `.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='showdown'],`  
   `.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='settled']` 开头。
5. **A–C 选择器**：按 §4.2 表逐组添加 token + `overflow:hidden` + `::after` gradient（复制 public 牌 L811–831 grammar）。
6. **D 全息 refactor**：给 L856–899 每选择器加 stage 前缀；复制 `::after` 到 `.dp-player-card__best-hand .dp-player-card__best-card`。
7. **冒烟**：§7.2 主路径 + §7.3 负例。
8. **回归**：default 主题 showdown；eco toggle；公共牌 screenshot diff。
9. PR 描述链接本文档 + 录屏。

### 10.1 建议 CSS 骨架（实现参考，非最终值）

```css
/* dp-game-themes.css — retro8bit showdown revealed cards scanlines (P0) */

.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='showdown'] .dp-player-card--rival-mini,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='settled'] .dp-player-card--rival-mini,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='showdown'] .dp-game-hero-dock .dp-player-card--hand-dock,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='settled'] .dp-game-hero-dock .dp-player-card--hand-dock,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='showdown'] .dp-game-hero-hand-hologram,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='settled'] .dp-game-hero-hand-hologram {
  --dp-card-community-scanline-period: 3px;
  --dp-card-community-scanline-thick: 1px;
  --dp-card-community-scanline-color: rgba(0, 0, 0, 0.38);
  --dp-card-community-scanline-opacity: 0.4;
}

/* 示例：rival-mini hole 亮牌 */
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='showdown'] .dp-player-card--rival-mini .dp-player-card__hole-row--rival .hole-card-flip.card-base,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='settled'] .dp-player-card--rival-mini .dp-player-card__hole-row--rival .hole-card-flip.card-base,
/* … best-hand / hand-dock / hologram 同理 … */ {
  border-radius: var(--dp-card-community-pixel-radius, 2px);
  overflow: hidden;
}

.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='showdown'] .dp-player-card--rival-mini .dp-player-card__hole-row--rival .hole-card-flip.card-base::after,
.dp-game-root[data-dp-game-theme='retro8bit'][data-dp-stage='settled'] .dp-player-card--rival-mini .dp-player-card__hole-row--rival .hole-card-flip.card-base::after
/* … 所有 card-base 目标 … */ {
  display: block;
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  z-index: 2;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent calc(var(--dp-card-community-scanline-period) - var(--dp-card-community-scanline-thick)),
    var(--dp-card-community-scanline-color) calc(var(--dp-card-community-scanline-period) - var(--dp-card-community-scanline-thick)),
    var(--dp-card-community-scanline-color) var(--dp-card-community-scanline-period)
  );
  background-size: 100% var(--dp-card-community-scanline-period);
  opacity: var(--dp-card-community-scanline-opacity);
}

/* 全息：删除或改写原 L856–899 无 stage 前缀规则，避免翻前泄漏 */
```

## 11. 设计原则对齐（skills 摘要）

- **frontend-design**：摊牌是 retro8bit **高潮时刻**；扫描线统一公共牌 / 全息 / 座位亮牌的 CRT 语法，静态 overlay 不抢 rank 可读性。
- **ui-ux-pro-max**：
  - **Style consistency**（§4）：仅 retro8bit + 仅摊牌；其它主题零 diff。
  - **Accessibility**（§1）：扫描线降 opacity，不替代花色信息；rank 对比度 ≥ 现网。
  - **Performance**（§3）：静态 `repeating-linear-gradient`，无 blur 动画；eco 无额外 hit。
  - **Animation**（§7）：不新增 motion；现有 flip/pop 不动。
  - **State clarity**：阶段门控使「未摊牌 = 无扫描线」可预测。

## 12. 与相邻文档关系

| 文档 | 关系 |
|------|------|
| `retro8bit-community-cards-pixel-plan.md` | 公共牌扫描线/token **已落地**；本需求 **扩展** 至摊牌亮牌 |
| `retro8bit-seat-enter-plan.md` | 入座 **动画** 扫描带；本需求 **静态** 牌面 overlay，勿混用 keyframes |
| `retro8bit-hand-hologram-plan.md` | 全息容器动效；本需求仅改 **牌面 ::after** + stage 门控 |

---

*文档版本：2026-05-31 · 对应快照：`GamePlayerCard.vue`、`GameHeroDockFooter.vue`、`GameDpGameSheets.vue`、`GameHeroHandHologram.vue`、`game.vue`（`data-dp-stage`）、`dp-game-themes.css` L788–899。*
