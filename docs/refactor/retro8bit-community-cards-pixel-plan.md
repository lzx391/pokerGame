# retro8bit 桌面公共牌像素化

> 状态：**计划文档（PM 已确认需求，待实现）**  
> 非目标：本阶段不改后端/WebSocket；不改手牌/对手迷你牌/牌谱；不为非 `retro8bit` 主题加像素层；不调整 rank 字体栈与字号；窄屏/mobile 不做专项 QA（用户有独立 Android 客户端）。

## 1. 产品决策摘要（PM 确认）

| # | 决策 |
|---|------|
| 1 | **仅 `retro8bit` 主题** — 后续主题 polish 均按此模式；其它主题零变更 |
| 2 | **仅桌面公共牌** — flop / turn / river（`.dp-game-table__center` 内 `GameCommunityCards`）；不含 hole cards、seat 迷你牌、hand history、弹窗小牌 |
| 3 | **像素化牌背 + 牌面底纹** — rank 展示（`A`/`10`/`K` 等）现有字体与可读性 **保持不变** |
| 4 | **可读性优先** — 当前点数显示已达标；像素效果不得降低 legibility |
| 5 | **QA 视口** — **宽屏 + 全屏（`layout-fs` / 浏览器 fullscreen）优先**；窄屏/mobile 本阶段不验收；CSS 主题门控即可 |
| 6 | **动效保留** — 发牌飞入、3D 翻面逻辑不动；只改视觉层 |
| 7 | **ecoMode** — 不引入重滤镜；像素层应为静态 CSS，无额外 per-frame 成本 |

## 2. 现状盘点

### 2.1 组件与 DOM

```
GameRoundTable.vue
└── .dp-game-table__center
    └── .dp-game-table__center-stack
        └── GameCommunityCards.vue
            └── .community-cards-row
                ├── .card-flip-wrapper[.deal-fly-in]     ← 发牌飞入
                │   └── .card-flip-inner[.flipped]     ← 3D 翻面
                │       ├── .card-face.card-back.card-base.bg-gray   → "?"
                │       └── .card-face.card-front.card-base.bg-{red|blue|green|black}  → rank 文本
                └── .card-base.bg-gray.community-card-placeholder    → 空槽 "?"
```

- 挂载点：`GameRoundTable.vue` L32–35（唯一牌桌中央公共牌实例）。
- **不在范围**：`GameHeroDockFooter` / `GameDpGameSheets` 内的 `community-cards-flip-complete` 等 prop 仅透传状态，不渲染独立公共牌 UI。

### 2.2 牌面数据与渲染方式

`dpGameCardVisual.js`：

| 函数 | 行为 |
|------|------|
| `getCardDisplay(c)` | `"hearts_A"` → `"A"`（纯文本，无花色 Unicode） |
| `getCardClass(c)` | 按花色映射 `bg-red` / `bg-blue` / `bg-green` / `bg-black` |

**结论：无 sprite、无 `<img>`、无 SVG 牌面。** 牌面 = **CSS 渐变背景** + **居中 rank 文本**；花色靠背景色区分。

### 2.3 样式分层

| 文件 | 职责 | 与本需求关系 |
|------|------|----------------|
| `dp-poker-cards.css` | `.card-base` 尺寸/边框/阴影；`.bg-*` 平滑 radial+linear 渐变；手牌/弹窗小牌共用 | **不改全局**；retro8bit 公共牌 override 应 scoped 到 community 选择器 |
| `dp-game-community-cards.css` | 3D flip、牌背多层 `--dp-card-back-*`、发牌 keyframes、占位 float | 动效保留；可选增 hook class（P1） |
| `dp-game-shell.css` L54–101 | 默认 `--dp-card-*` token；L950 `.dp-game-table__center .community-cards-row` 布局 | token 参考；table center 为 scope 锚点 |
| `dp-game-themes.css` L132–238 | `retro8bit` CRT 绿 token，含 `--dp-card-back-*` / `--dp-card-frame-*` | **主写入点**：增 community-only 像素 override 块 |
| `dp-game-layout-tiers.css` | phone 档缩小 `--dp-card-base-*` | 本阶段不验 phone；规则仍应只在 retro8bit 下生效 |
| `dp-game-eco-mode.css` L135–158 | eco/PRM 禁用 flip transition、deal 动画、placeholder float | 像素层须兼容；**禁止**在 eco 块外加 blur/filter 动画 |

### 2.4 retro8bit 已有牌相关 token

`dp-game-themes.css` retro8bit 段已定义磷光绿牌背：

- `--dp-card-back-fg: #4af626`
- `--dp-card-back-base` / `pattern-1/2`：仍为 **smooth gradient + repeating-linear-gradient**
- `body[data-dp-game-theme='retro8bit'] .card-base { font-family: var(--dp-font-ui) }` — rank 用 UI monospace，**非** `--dp-font-pixel`

牌面 `.bg-red/.bg-blue/.bg-green/.bg-black` 仍继承 `dp-poker-cards.css` 的 **平滑高光渐变**，与 CRT 像素风不一致。

### 2.5 项目内像素渲染先例

`DpAuthStage.vue` L1224–1237：SVG + `image-rendering: pixelated; crisp-edges` — 适用于 **位图/SVG**，不直接作用于 CSS gradient。公共牌需 **背景层与文字层分离** 策略。

## 3. 视觉规格

### 3.1 目标观感（retro8bit + 宽屏牌桌）

1. **牌背**：块状像素格纹 / 硬边边框 / 磷光绿 `#4af626` 描边；`?` 中心字保持现有字号与 `--dp-font-ui`，仅背景像素化。
2. **牌面**：平滑渐变 → **2–4 档色阶** 或 **8px 网格 repeating  pattern**；保留红/蓝/绿/黑四色语义；rank 文本 **不** 加 `-webkit-font-smoothing: none`、**不** 改 `font-size` / `font-weight`。
3. **外框**：`border-radius` 降至 **0–2px**（或 `--dp-card-base-radius: 2px` 仅 community scope）；双层硬边替代 soft gold glow。
4. **阴影**：单层 `box-shadow` + 可选磷光 `drop-shadow`；eco 下与现网一致降级，不叠 blur 动画。

### 3.2 不动范围

| 元素 | 保持 |
|------|------|
| rank 文本 | 字体栈、字号、字重、text-shadow 强度（可微调对比度，不可改 typography 系统） |
| 3D flip / deal-fly | keyframes、duration、easing、JS stagger |
| 其它主题 | default / gothic / sakura 等牌面仍为 smooth gradient |
| hole / muck / hand-rank 小牌 | 不受 community 选择器影响 |
| `GameCommunityCards.vue` 逻辑 | 无 JS 变更（CSS-only P0） |

### 3.3 推荐 CSS 技术路线

**P0：纯 CSS，主题 + 区域双门控**

```css
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-table__center .community-cards-row …
```

| 层级 | 手法 | 说明 |
|------|------|------|
| 牌背 `.card-back` | 重写 `background` 为 **stepped gradient + 粗 grid repeating-linear-gradient**（步长 4px/8px）；`border-radius: 0`；硬 `border: 2px solid` | 替代 smooth `--dp-card-back-*` 叠层 |
| 牌面 `.card-front.bg-*` | **透明背景** + `::before` 伪元素承载像素底纹；`z-index` 使文字在上 | 避免 `image-rendering` 作用于文本 |
| 伪元素像素化 | `::before { inset: 0; border-radius: inherit; pointer-events: none; }` + 色块 pattern | 不用 `filter: url()` |
| 可选 sharpen | 父级 `transform: translateZ(0)` 已有；**不** 对整卡 `scale(1.02)` 动画 |
| `image-rendering` | 仅用于未来若引入 **data-uri 像素 PNG** 的 `background-image`；渐变主路径不依赖此项 | 与 DpAuthStage 先例一致 |

**P1 备选**（仅 P0 渐变阶梯不够「像素」时）：

- 内联 **8×11 逻辑像素** SVG data-uri 牌背/牌面纹理，`background-size: 100% 100%` + `image-rendering: pixelated`。
- 新建 `dp-game-retro8bit-community-cards.css` 拆文件，由 `GameCommunityCards.vue` 或 `game.vue` 条件 import — **非 P0**。

### 3.4 牌面四色像素 palette（建议）

与 retro8bit accent 协调，**flat + 1px 高光块**：

| Class | 主色 | 高光块 | rank 字色 |
|-------|------|--------|-----------|
| `bg-red` | `#8b1a2b` | `#ff6b8a` top-left 25% | `#f5f5f5`（保持现网） |
| `bg-blue` | `#0a3d6b` | `#69c0ff` | `#f5f5f5` |
| `bg-green` | `#1a5010` | `#95de64` | `#f5f5f5` |
| `bg-black` | `#141820` | `#4a5568` | `#f5f5f5` |

占位 `.community-card-placeholder`：与牌背同像素 grammar，opacity 保持现网 float（eco 仍禁用 animation）。

### 3.5 ASCII：层叠结构

```
.card-front.card-base.bg-red          .card-back.card-base
┌─────────────────────┐              ┌─────────────────────┐
│  rank "A"  (crisp)  │  z-index 2   │  "?"  (crisp)       │
│  ─────────────────  │              │  ─────────────────  │
│  ::before pixel bg  │  z-index 0   │  pixel grid bg      │
│  hard 2px border    │              │  #4af626 border     │
└─────────────────────┘              └─────────────────────┘
```

## 4. 架构设计

### 4.1 门控条件

| 门控 | 实现 |
|------|------|
| 主题 | `[data-dp-game-theme='retro8bit']` on `.dp-game-root`（与 `game.vue` 一致） |
| 区域 | `.dp-game-table__center .community-cards-row` 及其子代 |
| 视口（QA） | 实现 **不强制** `@media` 断点；验收矩阵聚焦 desktop/tablet ≥641px + fullscreen |
| eco / PRM | 像素样式 **静态**；不新增 keyframes；不依赖 `filter: blur()` |

**刻意不做**：像 chat reveal 那样的 `viewportWidth > 600` JS 门控 — 本需求 CSS 主题门控即可，窄屏仅「不保证观感」。

### 4.2 选择器 scope（防误伤）

**必须包含**：

```css
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-table__center .community-cards-row
```

**禁止**仅写：

- `.card-base`（会波及 hole cards、hand-rank 弹窗）
- `body[data-dp-game-theme='retro8bit'] .card-base`（已存在字体规则，扩展时勿改 font）

**子选择器清单**：

- `.card-flip-wrapper` / `.card-flip-inner` — 仅改 `filter` 降级（如有）
- `.card-flip-inner .card-back`
- `.card-flip-inner .card-front.bg-red|blue|green|black`
- `.community-card-placeholder`

### 4.3 与动效交互

| 动效 | 像素层注意 |
|------|------------|
| `community-deal-fly` | 无 blur keyframe（现网已无）；保持 |
| `card-flip-inner` rotateY | 伪元素需 `backface-visibility: hidden` 与 face 同步 |
| `.card-flip-wrapper { filter: drop-shadow }` | retro8bit 可改为磷光绿 `drop-shadow`；eco 已有 lighter shadow override |
| hover `.card-base:hover` | 公共牌 flip wrapper 内 **无 hover**（非交互）；placeholder 无 hover |

### 4.4 文件变更优先级

| 优先级 | 文件 | 变更 |
|--------|------|------|
| **P0** | `front/dp_game/src/styles/dp-game-themes.css` | retro8bit 段末尾增 **community cards pixel block**（~80–120 行） |
| **P0** | `front/dp_game/src/styles/dp-game-community-cards.css` | 可选：placeholder/back 增 `--dp-card-community-*` hook；或零改动 |
| **P1** | `front/dp_game/src/styles/dp-game-retro8bit-community-cards.css` | 新建拆文件 + `@import` 或组件 `<style src>` |
| **P1** | `front/dp_game/src/styles/dp-motion-tokens.css` | 若需 `--dp-card-community-radius: 0` token |
| **不改** | `GameCommunityCards.vue` | P0 无 template/script 变更 |
| **不改** | `dpGameCardVisual.js` | 无 |
| **不改** | `dp-poker-cards.css` | 全局 gradient 保留；避免 hole card 回归 |

## 5. 待改文件清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `front/dp_game/src/styles/dp-game-themes.css` | **新增 CSS 块** | `[data-dp-game-theme='retro8bit']` + `.dp-game-table__center .community-cards-row` 像素 override |
| `front/dp_game/src/styles/dp-game-community-cards.css` | 可选微调 | 仅当需 shared custom property（如 `--dp-card-community-pixel-radius`） |
| `front/dp_game/src/styles/dp-game-retro8bit-community-cards.css` | P1 新建 | themes 过长时拆分 |
| `front/dp_game/src/styles/dp-game-eco-mode.css` | 验证无冲突 | 确保未对 community 施加与像素底纹冲突的 blur |
| `docs/refactor/retro8bit-community-cards-pixel-plan.md` | 本文档 | 计划 |

**明确不改**：`GameCommunityCards.vue`、`dpGameCardVisual.js`、`dp-poker-cards.css`（全局段）、后端、牌谱页、手牌组件。

## 6. 验收标准

### 6.1 功能 / 回归

- [ ] retro8bit 宽屏对局：flop 3 张、turn 1 张、river 1 张发牌与翻面时序与现网一致。
- [ ] 发牌飞入（dealer anchor / 默认自下方）轨迹与 stagger 不变。
- [ ] ecoMode ON 或系统 PRM：翻面/发牌 instant 路径仍正常；**无** 新增 jank。
- [ ] default / gothic / sakura 等主题：公共牌仍为 smooth gradient，视觉与现网 pixel-diff 为零。
- [ ] 手牌、seat 迷你牌、muck pile、hand-rank 弹窗、settlement 小牌：**零视觉变更**（截图 diff）。

### 6.2 视觉（retro8bit + 宽屏 / 全屏）

- [ ] 牌背：可见像素格/硬边，磷光绿 CRT 边框；`?` 清晰可读。
- [ ] 牌面：四色可区分；rank（`A`/`10`/`K`/`Q`/`J`）与现网 **同等或更好** 可读性。
- [ ] 牌角 **无** 过度 anti-alias 糊边（硬边 aesthetic）；文字 **无** 像素锯齿化。
- [ ] 空槽 placeholder 与牌背风格一致。
- [ ] 全屏（`layout-fs` 或 browser fullscreen）desktop 档：5 张公共牌对齐、无 clip、无 z-fighting。

### 6.3 性能

- [ ] 无新增 `filter: blur()` / `backdrop-filter` on community cards。
- [ ] 无 infinite animation 增项。
- [ ] Chrome Performance：一整手 flop→river 主线程无新增 >16ms 长任务（相对现网 baseline）。

### 6.4 范围外（本阶段不要求）

- [ ] ≤640px phone 档像素完美
- [ ] hand history / 牌谱 replay 公共牌
- [ ] 非 retro8bit 主题像素化

## 7. 验证步骤（实现后）

### 7.1 环境

1. 本地 `npm run dev`，进入对局，`retro8bit` 主题。
2. 视口 **1280×800** 与 **1920×1080**；各测 **窗口** + **layout-fs 全屏** 两种。

### 7.2 对局流程

1. 创建房间，发至 **flop** — 观察 3 张牌背像素 → 翻面 rank 可读。
2. 继续 **turn / river** — deal-fly + flip 正常。
3. 新一手重置 — placeholder `?` ×5 风格正确。
4. 切换 **default** 主题同流程 — 确认 smooth 牌面无像素 override。

### 7.3 回归扫描

1. 展开手牌区 — hole cards 仍为 smooth gradient + 原 font。
2. 打开牌型说明 / settlement — 小牌未变。
3. TopBar **ecoMode** ON — 重复 flop；翻面 instant 且无视觉 glitch。
4. OS「减少动态效果」— 同 eco。

### 7.4 DevTools 快检

```js
// 控制台：确认仅 table center 公共牌命中像素规则
document.querySelectorAll('.dp-game-table__center .community-cards-row .card-front')
```

- Computed：`background` / `::before` 为 stepped pattern，非原 `radial-gradient` smooth。
- 手牌 `.hole-card-flip .card-base` computed background **仍为** 原 smooth gradient。

### 7.5 录屏交付

PR 附：**retro8bit 宽屏 fullscreen** flop→river 一段 + **default 对照** 5s。

## 8. 风险与缓解

| 风险 | 原因 | 缓解 |
|------|------|------|
| `image-rendering` 对 gradient 无效 | CSS gradient 非 raster | 用 **stepped gradient + grid pattern** 或 SVG data-uri；文本放伪元素之上 |
| 选择器过宽误伤 hole card | `.card-base` 全局 | 强制 `.dp-game-table__center .community-cards-row` 前缀 |
| 3D flip 期间伪元素穿帮 | `backface-visibility` | face/back 的 `::before` 同设 hidden；与 `.card-face` 同 transform 上下文 |
| rank 可读性下降 | 整卡 `image-rendering` 或 pixel font | **禁止**改 community rank 的 `font-family`/`font-size`；文字层不用 pixelated |
| 磷光 text-shadow + 像素底纹对比不足 | 绿底绿字 | 牌面 rank 保持 `#f5f5f5`；必要时仅 community front 增 `text-shadow: 0 1px 0 #000` |
| eco 与 drop-shadow 叠加 | wrapper `filter` | 沿用 eco 既有 lighter shadow；像素层不用 animate filter |
| themes.css 膨胀 | 大块 override | P1 拆 `dp-game-retro8bit-community-cards.css` |
| phone 档过小 | 38→30px 宽 | 本阶段不验；规则不依赖 min-width 即可 |

## 9. P0 / P1 范围

### P0（本迭代必做）

1. retro8bit + table center community cards 牌背/牌面/placeholder 像素 override。
2. rank typography 不变；四色语义保留。
3. 动效零回归；eco/PRM 兼容。
4. 其它主题 + 非 community 牌零回归。

### P1（可跟进）

1. 拆独立 CSS 文件。
2. SVG data-uri 纹理（若 stepped CSS 不够「8bit」）。
3. `--dp-card-community-pixel-*` design tokens 入 shell/themes。
4. 与后续 hole card 像素化共用 mixin/token（**不在本阶段实现**）。

### 明确不做

- hole cards / opponent mini cards / hand history 像素化。
- 非 retro8bit 主题。
- 窄屏/mobile QA 与专项 breakpoint。
- JS 渲染路径变更、sprite sheet 资产管线。
- 提交 dist 构建产物。

## 10. 实现交接步骤（给开发）

1. **读现状**：打开对局 retro8bit，`Inspect` `.community-cards-row .card-front.bg-red`，确认 smooth gradient 来源为 `dp-poker-cards.css`。
2. **写 scope**：在 `dp-game-themes.css` retro8bit 段末新增 §community-pixel 块；首行选择器含 `.dp-game-table__center .community-cards-row`。
3. **牌背**：override `.card-flip-inner .card-back` 的 `background`、`border-radius`、`border`、`box-shadow`；保留 `color`/`font-size` token。
4. **牌面**：对 `.card-front.bg-*` 设 `background: transparent`（或 none），`::before` 画像素底；rank 选择器显式 `position: relative; z-index: 1`。
5. **placeholder**：`.community-card-placeholder` 复用牌背 grammar。
6. **动效冒烟**：完整一手 + eco toggle + 主题切换。
7. **回归**：hole cards、default 主题截图对比。
8. **自测 §7**；PR 描述链接本文档。

### 10.1 建议 CSS 骨架（实现参考，非最终值）

```css
/* dp-game-themes.css — retro8bit community cards pixel (P0) */
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-table__center .community-cards-row {
  --dp-card-community-pixel-radius: 2px;
}

.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-table__center .community-cards-row .card-flip-inner .card-back,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-table__center .community-cards-row .community-card-placeholder {
  border-radius: var(--dp-card-community-pixel-radius);
  /* stepped bg + 8px grid — replace smooth --dp-card-back-* stack */
}

.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-table__center .community-cards-row .card-flip-inner .card-front {
  border-radius: var(--dp-card-community-pixel-radius);
  background: transparent;
  overflow: hidden;
}

.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-table__center .community-cards-row .card-flip-inner .card-front::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  z-index: 0;
  /* per .bg-red|.bg-blue|.bg-green|.bg-black pattern */
}

.dp-game-root[data-dp-game-theme='retro8bit'] .dp-game-table__center .community-cards-row .card-flip-inner .card-front {
  position: relative;
  z-index: 0;
  /* rank text inherits existing font — do not override font-family */
}
```

## 11. 设计原则对齐（skills 摘要）

- **frontend-design**：retro8bit 公共牌是牌桌 **单一视觉锚点**；磷光绿硬边 + 色块底纹与 CRT 主题一致；不做 scatter 微动效，动效交给现有 deal/flip。
- **ui-ux-pro-max**：
  - **Style consistency**（§4）：仅 retro8bit 改，其它主题 flat gradient 不动。
  - **Performance**（§3）：静态 CSS pattern，无 blur/filter 动画；eco 无额外 hit。
  - **Accessibility / readability**（§1, §6）：rank 对比度 ≥ 现网；花色不单靠颜色时可依赖 rank + 历史上下文（扑克惯例）。
  - **Animation**（§7）：保留 transform-only deal/flip；不 animating width/height。
  - **Viewport**：QA 聚焦 wide/fullscreen；mobile 非本阶段交付面。

## 12. 后续模板（给未来主题 polish）

本迭代确立的模式，供 hole cards / 其它 retro8bit 元素复用：

1. **主题门控** `[data-dp-game-theme='retro8bit']`
2. **区域门控** 精确 DOM 前缀（table center / chat panel / …）
3. **背景/文字分离** `::before` 像素底 + 顶层 crisp text
4. **零 JS** 优先；动效层不动
5. **验收**：宽屏 fullscreen + 它主题零 diff + eco/PRM

---

*文档版本：2026-05-31 · 对应快照：`GameCommunityCards.vue`、`dp-poker-cards.css`、`dp-game-community-cards.css`、`dpGameCardVisual.js`、`dp-game-themes.css` retro8bit 段。*
