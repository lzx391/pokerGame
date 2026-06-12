# retro8bit 手牌全息 — 投影光束 v2 重设计

> 状态：**计划文档（PM 全默认 + 用户反馈，待实现）**  
> 前置：`docs/refactor/retro8bit-hand-hologram-plan.md`（v1 已实现，用户验收 **不通过**）  
> 非目标：本阶段不改后端/WebSocket/牌力计算；不为非 `retro8bit` 主题加全息；不改动 ≤600px 底部抽屉；**不实现**；**不提交**。

---

## 0. 用户反馈（权威，v2 设计驱动力）

| 现状问题 | 用户原话 / 意图 |
|----------|----------------|
| 视觉隐喻错误 | 当前像 **显示屏/面板翻出来**（`rotateX` 立起 + 圆角矩形暗底 + 标题栏），不是全息 |
| 期望隐喻 | **投影光束** 从「查看手牌」按钮射出并扩展 → **信息在光束体积内/上浮现**，科幻全息感 |
| 明确禁止 | 矩形 **monitor/panel shell**、厚重 CRT 边框/底框、像 iPad dock fan、像 bottom sheet |
| 可保留 | 磷光绿 retro8bit、toggle 开/关、内容与抽屉一致、eco/PRM instant、gameRoot portal、dev log |

**v2 一句话：** 光束是主角，内容是光束里的虚像；删掉「翻出来的屏幕」。

---

## 1. 产品决策摘要（PM 全默认，v1 延续）

| # | 决策 |
|---|------|
| 1 | **仅 `retro8bit`** — 其它主题仍用 `GameBottomSheet` 手牌抽屉 |
| 2 | **视口 >600px** 启用全息（含 `layout-fs` / 浏览器全屏）；**≤600px 不变** |
| 3 | **交互** — 点击「查看手牌」toggle 开/关；关闭：再点按钮、全息内关闭控件 |
| 4 | **内容** — 与 `GameDpGameSheets` 相同：`GamePlayerCard` + `hero-hand-dock`；翻前仅底牌；`handRankName` / `bestHandCards` 由现有 computed 门控 |
| 5 | **摊牌扫描线** — `data-dp-stage='showdown'|'settled'` 时牌面 `::after` 扫描线（`dp-game-themes.css` 现有规则 **保留**，选择器随 DOM 类名更新） |
| 6 | **eco / PRM** — 瞬间展开/收起，跳过光束动画 |
| 7 | **Dev log** — `[dp-hand-hologram]` 前缀（`dpHandHologramDevLog`） |
| 8 | **Portal** — 宽视口 retro8bit 始终挂 `gameRoot`（现有 `syncPortalMount` **保留**） |

---

## 2. v1 实现诊断（为何像「显示屏翻出来」）

### 2.1 根因对照

| 元素 | v1 实现 | 造成的感知 |
|------|---------|------------|
| `__panel` | 340px 圆角矩形、`background: #12151a 94%`、1px 边框、`box-shadow` 深影 | **物理显示器外壳** |
| `__header` + `__title` | 像素字标题栏 + `border-bottom` | **Sheet / Modal chrome**（与 `.dp-game-sheet__title` 同族） |
| `materialize` keyframe | `rotateX(88deg → 0deg)` + `scale` on **`__panel`** | **翻板立起**（与聊天翻板同模式，用户明确不要） |
| `__beam` | 单层 `clip-path` 三角渐变，260px 高，与 panel **分离动画** | 光束像装饰贴纸，panel 才是主体 |
| 扫描线 | 覆盖整个 panel `inset: 0` | 扫描的是「屏幕框」而非「光锥内的虚像」 |

### 2.2 应保留的 v1 资产（勿推倒重来）

| 模块 | 文件 | 保留理由 |
|------|------|----------|
| Vuex `showHeroHandHologram` + 与 sheet 互斥 | `dpGame.js`, `game.vue` | 路由已正确 |
| 门控 `useRetroHandHologramWide` / `Animated` | `GameHeroHandHologram.vue` | 条件正确 |
| 阶段机 `idle → projecting → materializing → flash → revealed → collapsing` | 同上 | 时序骨架可用，**驱动对象改为 volume/content** |
| 锚点 `[data-dp-hero-deal-target]` + gameRoot 坐标换算 | 同上 | 投影原点正确 |
| Portal mount + fullscreen 联调 | 同上 | 已修 stacking |
| `GamePlayerCard` props 表 | 同 `GameDpGameSheets` | 内容真源 |
| eco-mode CSS 禁用 keyframes | `dp-game-eco-mode.css` | 模式正确，选择器随 refactor 更新 |
| showdown 牌面像素扫描线 | `dp-game-themes.css` L863–934 | 仅改选择器前缀 |

---

## 3. v2 视觉方向（必须实现的规格）

### 3.1 设计原则（skills 对齐）

- **frontend-design**：单一高 impact moment — **光锥展开 + 虚像扫描成形**；磷光绿 volumetric light；避免「又一个 modal 框」。
- **ui-ux-pro-max**：动效分段 150–500ms、exit 快于 enter；仅 `transform`/`opacity`；PRM/eco 必降级；对比度 ≥4.5:1；关闭钮 ≥44px。

**参考（学隐喻，不抄 DOM）：** 科幻全息投影仪（Star Wars / Blade Runner UI）、 volumetric fog cone — **不是** iPad fan、**不是** bottom sheet、**不是** 聊天翻板。

### 3.2 空间叙事

```
                         ╭─ 虚像区（半透明，无边框） ─╮
                         │  [♠A][♥K]  牌力  成牌五张   │  ← content 在光锥内浮现
                         │  ░ scan sweep ░            │
                         ╰────────────────────────────╯
                    ▒▒▒▒▒▒ 光锥外层 glow ▒▒▒▒▒▒
               ▒▒▒▒▒▒▒▒▒▒▒ 核心光束 (phosphor) ▒▒▒▒▒▒▒▒▒
          ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒
    ────────╳────────────────────────────────────────────  ← 投影原点（按钮中心）
    │ 主动离座 │ │ 查看手牌 │ │ 聊天 … │
    └──────────┴────┬─────┴────────────┘
                    data-dp-hero-deal-target
```

1. **Origin**：`[data-dp-hero-deal-target]` 按钮中心，光锥 `transform-origin: 50% 100%`。
2. **Beam expands**：向上 + 略向前（`rotateX(-8deg ~ -14deg)` 在 **scene** 或 **volume** 上，非 panel 翻 hinge）。
3. **Content materializes**：在光锥上半部 **opacity 0→1** + **扫描线自上而下扫过** + 可选 1–2px 横向 glitch（flash 阶段）。
4. **Dismiss**：光锥 **scaleY/opacity 收束** → 内容先 fade → 光束消失（exit ~60–70% enter 时长）。

### 3.3 光束（Beam）规格

| 属性 | 规格 |
|------|------|
| 结构 | **多层**（至少 3 层 DOM 或伪元素）：`__beam-core`（高饱和锥形）、`__beam-glow`（宽、低 opacity 外晕）、`__beam-dust`（可选：细颗粒 noise / 1px 竖线 shimmer） |
| 形状 | 锥形：`clip-path: polygon(...)` 或 `conic-gradient` + mask；底宽 ≈ 按钮宽 × 2.4（沿用 v1 `beamWidth` 计算），顶宽 ≈ content 宽 × 1.1 |
| 高度 | 动态：`anchor.bottom + contentEstimatedHeight + 48px`；最小 220px，最大 420px |
| 颜色 | `--dp-accent` / `#4af626` 磷光绿；核心 72%→28% 渐变；外晕 35%→0% |
| 3D | Scene 设 `perspective: 900px`；光锥容器 `rotateX(-10deg)` **静态倾斜**（营造向前投影），**禁止** 光锥绕底边 90° 翻转 |
| 动画 `project` | `scaleY(0.15→1)` + `opacity(0→1)` + 可选 `scaleX(0.6→1)`；280ms ease-out |
| 动画 `collapse` | reverse，`220ms` ease-in |

### 3.4 内容（Content）规格 — **无 panel shell**

| 属性 | 规格 |
|------|------|
| 容器 | 新类 `__projection`（替代 `__panel`）：**无** `background` 实色底、**无** `border`、**无** `border-radius` 盒子、**无** 深 `box-shadow` |
| 定位 | 绝对定位于光锥 **上半 55–65%** 区域；`translateX(-50%)`；宽度 `min(340px, 88vw)` |
| 外观 | `background: transparent` 或极淡 `color-mix(accent 4%, transparent)`；文字/牌面自带对比度 |
| 扫描线 | 仅覆盖 **content 区域**（非全屏矩形）；`repeating-linear-gradient` + materialize 阶段 **mask/clip 自上而下展开** |
| 标题 | **Ghost title**：`opacity: 0.45` 小字 `--dp-font-pixel`，**无** header 底部分割线；或 **revealed 后 2s fade out**；aria 仍用 `aria-label="我的手牌"` |
| 关闭 | 浮动在 content 右上；44×44；半透明磷光 hover；**无** title bar 容器 |
| 牌面 | 沿用 `GamePlayerCard`；外层 dock `background: transparent; border: none; box-shadow: none`（v1 已有，保留） |

### 3.5 明确禁止（验收否决项）

- ❌ `rotateX(>45deg)` 作为 **内容容器** 的主入场动画（翻屏感）
- ❌ 圆角矩形暗色 **panel 底** + 1px accent 边框组合（monitor bezel）
- ❌ 独立 `__header` 条 + `border-bottom`（sheet chrome）
- ❌ 内容在 panel 外、beam 仅装饰（主次颠倒）
- ❌ 与 bottom sheet 相同的 slide-up / mask  dim 全屏遮罩（除非 PM 追加）

---

## 4. 组件策略：Refactor，不 Remove

### 4.1 决策

| 选项 | 结论 |
|------|------|
| **Remove** `GameHeroHandHologram.vue` 新建 | ❌ 浪费已稳定的 portal/anchor/阶段机/store 集成 |
| **Refactor** 同文件 DOM + 重命名 CSS BEM | ✅ **推荐** — 保留 JS 逻辑 ~80%，替换视觉层 ~100% |

组件名 **`GameHeroHandHologram.vue` 保留**（store、game.vue、dev log 无需改名）。

### 4.2 建议 DOM 树（v2）

```html
<div class="dp-game-hero-hand-hologram" :class="phaseClass" role="dialog" aria-label="我的手牌">
  <!-- 投影场景：perspective + 倾斜 -->
  <div class="dp-game-hero-hand-hologram__scene" :style="sceneAnchorStyle">
    <!-- 光锥体积（主视觉） -->
    <div class="dp-game-hero-hand-hologram__volume" :style="volumeStyle" @animationend="onVolumeAnimEnd">
      <div class="dp-game-hero-hand-hologram__beam-core" aria-hidden="true" />
      <div class="dp-game-hero-hand-hologram__beam-glow" aria-hidden="true" />
      <div class="dp-game-hero-hand-hologram__beam-dust" aria-hidden="true" />

      <!-- 虚像内容：在光锥内 -->
      <div class="dp-game-hero-hand-hologram__projection">
        <span class="dp-game-hero-hand-hologram__ghost-title" aria-hidden="true">我的手牌</span>
        <button class="dp-game-hero-hand-hologram__close" aria-label="关闭手牌全息" @click="requestClose">×</button>
        <div class="dp-game-hero-hand-hologram__scan-sweep" aria-hidden="true" />
        <div class="dp-game-hero-hand-hologram__flash" aria-hidden="true" />
        <div v-show="showContent" class="dp-game-hero-hand-hologram__content">
          <div class="dp-game-hero-dock dp-game-hero-dock--in-sheet" …>
            <game-player-card … />
          </div>
        </div>
      </div>
    </div>
  </div>
</div>
```

**删除：** `__panel`、`__header`（合并为 ghost title + floating close）。

### 4.3 JS 变更要点（小改）

| 项 | v1 | v2 |
|----|----|-----|
| `panelAnchorStyle` | 定位 panel | 重命名为 `sceneAnchorStyle`，定位 `__scene`（原点仍在按钮上方） |
| `beamStyle` | 独立 beam 层 | 合并入 `volumeStyle`（宽/高/left/bottom） |
| `onPanelAnimEnd` | panel materialize/collapse | 改为 `onVolumeAnimEnd` — 监听 `__volume` |
| `onBeamAnimEnd` | beam project → materializing | **合并**：volume project 结束 → materializing；或单元素双 keyframe |
| `logPanelStyleApplied` | 调试 panel | 改为 `logVolumeStyleApplied`，query `__volume` / `__projection` |
| `showContent` computed | `revealed && heroDockRowSafe` | 可增加 `materializing` 尾段预挂载（opacity 0）以减少 CLS |

阶段名 **不变**（避免 store/game.vue 扩散改动）。

---

## 5. CSS 架构

### 5.1 文件分工

| 文件 | 动作 |
|------|------|
| **`dp-game-shell.css`** | **主战场**：删除/替换 L1329–1538 `__panel`/`__beam` 规则；新增 `__scene`/`__volume`/`__beam-*`/`__projection`；新 keyframes（见 §5.3） |
| **`dp-game-themes.css`** | 更新选择器：`.dp-game-hero-hand-hologram__title` → `__ghost-title`；showdown 扫描线选择器保持 `.dp-game-hero-hand-hologram` 前缀 |
| **`dp-game-eco-mode.css`** | 更新 class 名：`__panel` → `__volume`；instant 时隐藏 beam 层、projection 直接 visible |
| **`dp-motion-tokens.css`**（P1） | 可选 token：`--dp-motion-hand-hologram-project` 等 |
| **新文件 `dp-game-retro-hand-hologram.css`**（P1 可选） | 若 shell 超 200 行则拆出 |

### 5.2 层叠与 z-index（沿用 v1）

| 层 | z-index |
|----|---------|
| 全息 scene | 1020（body）/ 2100（gameRoot portal） |
| sheet mask | 1010（在其上） |
| 底栏按钮 | 可点（scene `pointer-events: none`，projection/close `auto`） |

### 5.3 Keyframes 命名（v2，替换 v1）

| 名称 | 绑定元素 | 阶段 | 时长 |
|------|----------|------|------|
| `dp-retro-hand-hologram-beam-expand` | `__volume` | projecting | 280ms ease-out |
| `dp-retro-hand-hologram-content-scan` | `__scan-sweep` 或 `__content` | materializing | 380–420ms |
| `dp-retro-hand-hologram-content-flicker` | `__flash` | flash | 100ms steps(2) |
| `dp-retro-hand-hologram-beam-collapse` | `__volume` | collapsing | 240ms ease-in |

**删除或停用：** `dp-retro-hand-hologram-materialize`（panel rotateX）、`dp-retro-hand-hologram-collapse`（panel rotateX）。

**content 入场（materializing 内）：**

```css
/* 示意 — 实现时微调 */
@keyframes dp-retro-hand-hologram-content-scan {
  from {
    opacity: 0;
    transform: translateY(12px);
    filter: brightness(1.8) blur(2px);
  }
  60% {
    opacity: 0.85;
    filter: brightness(1.2) blur(0.5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
    filter: none;
  }
}
```

扫描线 sweep：伪元素 `scaleY(0→1)` + `transform-origin: top`，与 content fade 并行。

### 5.4 Instant 路径（eco / PRM）

- `__beam-*`：`display: none` 或 `opacity: 0`
- `__projection`：直接 `opacity: 1`；无 keyframes
- `__content`：立即 visible（与 v1 `--instant` 同类）

---

## 6. 动画时序（v2）

```
打开（animated）
│
├─ [0] idle
├─ [1] projecting       280ms   volume/beam scaleY + opacity；scene 固定 perspective
├─ [2] materializing    400ms   content scan-in + scan-sweep；**无 panel rotateX**
├─ [3] flash            100ms   glitch flicker
└─ [4] revealed         稳定显示；ghost title 可选淡出

关闭
│
├─ [4] revealed
├─ [5] collapsing       240ms   content fade → beam collapse
└─ [0] idle
```

总展开 ~**0.78–0.85s**；收起 ~**0.24s**（content 先隐，beam 后收）。

**Fallback timer**：保留 v1 `schedulePhaseRevealFallback` 900ms。

---

## 7. 待改文件清单

| 文件 | 变更 |
|------|------|
| `front/dp_game/src/components/GameHeroHandHologram.vue` | **Refactor** DOM；重命名 style computed；animationend 目标改为 volume |
| `front/dp_game/src/styles/dp-game-shell.css` | 替换全息块 + keyframes |
| `front/dp_game/src/styles/dp-game-themes.css` | ghost title 字体；showdown 选择器微调 |
| `front/dp_game/src/styles/dp-game-eco-mode.css` | 选择器 `__panel` → `__volume` |

**不改（除非回归失败）：** `game.vue` 路由、`dpGame.js` store、`GamePlayerCard.vue` 逻辑、`GameDpGameSheets.vue` 互斥、`GameHeroDockFooter.vue` 点击。

---

## 8. 验收标准

### 8.1 视觉（v2 专项 — 否决项）

- [ ] 展开时 **首先感知到光锥**从按钮射出，而非矩形框翻起
- [ ] **无** 可见圆角 monitor 底框 / 1px 面板边框 / 标题栏分割线
- [ ] 手牌、牌力、成牌 **看起来浮在光里**（半透明、无外框阴影）
- [ ] 与聊天翻板、bottom sheet、iPad fan **明显不同**（产品/设计 walkthrough）
- [ ] 磷光绿与 retro8bit 公共牌/整体 token 协调
- [ ] showdown/settled 牌面扫描线仍生效

### 8.2 功能（v1 回归）

- [ ] retro8bit + 601px+：toggle 开/关；内容与抽屉一致
- [ ] 翻前仅底牌；翻后有 `handRankName` / `bestHandCards` 时与抽屉一致
- [ ] default 主题 / ≤600px：仅底部抽屉
- [ ] layout-fs / 浏览器全屏 + 宽窗：全息 + 底栏锚点
- [ ] eco / PRM：instant，内容正确
- [ ] 离座 / `heroDockRow` 消失：关闭
- [ ] `[dp-hand-hologram]` dev log 仍输出 phase/anchor/portal

### 8.3 性能与 a11y

- [ ] 动画仅 `transform` + `opacity`（flash 可短时 `filter`，≤100ms）
- [ ] 关闭钮 ≥44px；`role="dialog"` + `aria-label`
- [ ] `prefers-reduced-motion` 生效
- [ ] 无明显 CLS（content 区预留 min-height ≈ 128px）

---

## 9. 验证步骤（实现后）

1. **侧面对比**：录屏 v1 vs v2 同场景 — 确认无「翻屏」
2. **视口矩阵**：375（抽屉）/ 768 / 1280+layout-fs
3. **阶段矩阵**：preflop / flop+ / showdown 扫描线
4. **eco + PRM**
5. **全屏 portal**：native + pseudo fullscreen，光束不被裁切
6. **回归**：聊天翻板、行动 sheet、邀请好友 panel 互不影响

---

## 10. 实现交接（给开发）

### 10.1 推荐顺序

1. **CSS 原型**（静态 HTML 或 Storybook 片段）：仅 `__volume` + 多层 beam + 透明 projection，**无 Vue**，给设计确认光锥形态
2. **Refactor `GameHeroHandHologram.vue` DOM** — 保留 script 阶段机
3. **替换 `dp-game-shell.css` 全息段** — 删 panel keyframes，加 beam-expand / content-scan
4. **更新 themes + eco 选择器**
5. **自测 §9**；Console 过滤 `[dp-hand-hologram]`

### 10.2 风险与缓解

| 风险 | 缓解 |
|------|------|
| 透明 content 对比度不足 | 牌面自有白底；牌力 pill 沿用 card 样式；必要时文字 `text-shadow: 0 0 8px accent` |
| 3D + `filter` 性能 | dust 层 eco 关；blur 仅 materializing 前 60% |
| gameRoot `zoom` | 沿用 v1 `gameRootZoomFactor` |
| 旧 class 残留 | grep `__panel` / `hand-hologram-materialize` 清零 |

### 10.3 PR 附录建议

- 录屏：768px retro8bit 开/关 ×2（强调光束）
- 截图：revealed 态 **无矩形框**
- 对照：default 主题仍 drawer

---

## 11. v1 → v2 差异摘要

| 维度 | v1 | v2 |
|------|----|----|
| 主视觉隐喻 | CRT 面板翻起 | 投影光锥展开 |
| 主容器 | `__panel`（暗色圆角盒） | `__volume` + `__projection`（透明） |
| 主入场动画 | `rotateX(88deg)` | `scaleY` beam + content scan/fade |
| 标题 | Header bar | Ghost title / aria only |
| Beam | 装饰层 | **视觉主角**（多层 volumetric） |
| 组件文件 | 新建 | **Refactor 保留** |

---

*文档版本：2026-05-31 · 基于已实现 `GameHeroHandHologram.vue`、`dp-game-shell.css` L1329–1538、用户反馈「不要显示屏翻出来，要投影光束内浮现信息」。*
