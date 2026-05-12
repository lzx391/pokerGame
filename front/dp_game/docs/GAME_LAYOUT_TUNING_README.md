# 对局页布局调参说明（给完全不懂代码的人）

`game.vue` 里只有**三块区域的 HTML 名字**，真正决定「顶栏多高、圆桌离顶栏多远、底下操作区离桌子多远」的，几乎都在样式文件 **`front/dp_game/src/styles/dp-game-shell.css`** 里。颜色、主题变量在 **`dp-game-themes.css`**；节能模式在 **`dp-game-eco-mode.css`**（一般不用动布局间距）。

读完这一篇，你就知道该搜哪个类名、改哪几个数字。

---

## 1. 页面结构（心里有个图）

在 `game.vue` 里，结构是自上而下：

| 区域 | HTML 类名 | 里面装什么 |
|------|-----------|------------|
| 顶栏 | `header.dp-game-layout__header` | `GameTopBar`（房间信息、按钮） |
| 中间（可滚动） | `main.dp-game-layout__main` | `GameRoundTable`（圆桌、座位、公共牌） |
| 底部 | `footer.dp-game-layout__footer` | `GameHeroDockFooter`（本人手牌、聊天、操作按钮等） |

外层还有一层 **`div.dp-game-layout`**，用 **flex 纵向排列**：顶栏和底栏「高度随内容」，中间主区吃掉剩余空间并可滚动。

**和顶栏、圆桌间距最相关的**，通常是：

1. **顶栏组件自己的下边距** → `.dp-top-bar` 的 `margin-bottom`
2. **主区顶部的留白**（ mainly 宽屏）→ `.dp-game-layout__main` 的 `padding-top`
3. **圆桌整块的上边距** → `.dp-game-table` 的 `margin`（简写里第一个数是「上」）

---

## 2. 顶栏：`.dp-top-bar`

文件：`dp-game-shell.css`，搜索 **`.dp-top-bar`**。

### 2.1 顶栏和「下面那一块」（主区）之间的空隙

- **`margin-bottom`**（默认约 **10px**）  
  - 越大：顶栏和圆桌之间的**空白越大**。  
  - 越小：圆桌**更靠上**。

同一文件里还有按屏幕尺寸的覆盖：

- **`@media (max-width: 600px)`** 里顶栏更紧凑，`margin-bottom` 会变成 **4px**（手机竖屏常见）。
- **`@media (max-width: 1024px) and (max-height: 560px)`**（横屏、矮视口）里会变成 **2px**，让牌桌整体上移。

### 2.2 顶栏本身的胖瘦（不占「与圆桌间距」但会变总高度）

- **`padding`**、**`gap`**：内边距和两行之间的间距，越大顶栏**越高**，圆桌相对更靠下。
- **`margin-top` / `padding-top`（带 safe-area）**：和 **刘海屏**有关，和根节点 `.dp-game-root` 成对使用，**不要只改一半**，否则容易出现「顶栏和屏幕顶有条缝」或「字进刘海」。文件开头有中文注释说明。

---

## 3. 主区（圆桌所在容器）：`.dp-game-layout__main`

搜索 **`.dp-game-layout__main`**。

### 3.1 对局 `/game`：主区顶距（三档变量）

非结算阶段，主区顶部留白来自 **`dp-game-shell.css`** 中的 **`padding-top: var(--dp-game-main-pad-top, 0px)`**，实际数值由 **`front/dp_game/src/styles/dp-game-layout-tiers.css`** 按 **`data-dp-layout-tier`**（phone / tablet / desktop）设不同 **`--dp-game-main-pad-top`**。

**想整体拉近/拉远圆桌与顶栏**：改 **`dp-game-layout-tiers.css`** 里对应档位那一行的 **`clamp(...)`**（详见同仓库 **`GAME_LAYOUT_TUNING_README.md`** 第 10 节）。

### 3.2 摊牌 / 结算阶段：主区离顶栏（结算参数在哪）

**一定要改 **`dp-game-shell.css` 文件最末尾** 注释为 `SETTLEMENT_TOP_SPACING` 的那一段**（约在文件结尾、`dp-room-bgm` 样式后面）。

原因：对局页的 `<main>` **固定带有类名** **`dp-game-layout__main--fit-table`**。若只写：

`.dp-game-root[data-dp-stage='settled'] .dp-game-layout__main { padding-top: ... }`

**特异性不够**，会被别的 `.fit-table` 规则压住，浏览器里看起来就像「怎么改都不生效」。**正确写法必须带上** `.dp-game-layout__main--fit-table`，与工程里当前选择器保持一致。

- **`padding-top`**：调 **`clamp(第一个数, …)`** 里最左边的数（例如 `140px`、`176px`），越大整张桌子离顶栏越远；**你手滑滚动、看「离顶栏多远」时改它就够。**
- **`scroll-padding-top`**：只参与 **滚动算法里的「最佳可视区」**（主要是 **`scrollIntoView()`**、**`scroll-snap`** 等对滚动位置的计算），**不会**像普通 `padding` 那样把内容顶下去。当前对局前端 **没有** 使用 scroll-snap / scrollIntoView，所以改到 `900px` 也常 **完全看不出变化**；需要顶边空白请改上面的 **`padding-top`**。

矮横屏在同一文件里用 **`@media (max-width: 1024px) and (max-height: 560px)`** 再叠一档更大的 `padding-top`。

### 3.3 与各档位的关系

- **phone** 档会额外缩小顶栏字号、牌尺寸与整页 **`zoom`**（仍在一屏内优先），变量同在 **`dp-game-layout-tiers.css`**。
- 牌桌 **`transform: scale`** 的可用宽高由 **`dpGameTableFitMixin.js`** 按主区**内容盒**计算。

---

## 4. 圆桌整块：`.dp-game-table` 与 `.dp-game-table__layout`

搜索 **`.dp-game-table`**（注意后面不要带 `__felt` 等，那是台呢子元素）。

### 4.1 圆桌相对主区的上下间距（默认）

默认大致是：

```css
margin: 8px 0 18px;  /* 上 右 下 左（左右为 0） */
```

- **第一个数（上）**：圆桌**离主区内容顶部**的额外距离（会和顶栏、`main` 的 padding 叠在一起）。
- **第三个数（下）**：圆桌**和下面操作区**之间的间距。

不同 **`@media`** 里会改成别的数字，例如：

- **`min-width: 768px`**：`margin: 4px 0 12px`（宽一点的屏，圆桌上下略收紧）。
- **`max-width: 600px`**：`margin: 4px 0 10px`（手机：**顶栏与圆桌更近**）。
- **全屏相关**（`:fullscreen`、`.dp-game-root--layout-fs` 等）：圆桌可能有 **4px / 6px** 等固定上下边距；宽屏全屏时又会用 **`margin-top: auto; margin-bottom: auto`** 让圆桌在**主区里垂直居中**（那时「离顶栏多远」主要由主区高度和 `padding-top` 一起决定）。

### 4.2 圆桌区域「有多高」：`.dp-game-table__layout`

- **`min-height: clamp(...)`**：圆桌占位区域的**最小高度**，越大桌面**越占纵向空间**，底下操作区越容易被挤到屏外（要滑动）。
- **`padding-bottom`**：圆桌区域**内部的底内边距**，略影响台呢和底边的距离。

---

## 5. 底部操作区（手牌 + 按钮那一带）

主要类名：**`.dp-game-hero-action-row`**（以及 footer 里其它块）。

常见间距：

- 默认/宽屏里 **`margin-top` / `margin-bottom`**：操作区与圆桌、与屏幕底之间的空隙。
- **`@media (max-width: 600px)`** 里会单独收紧 **`margin-top`**、**`margin-bottom`**。

若你只改圆桌的 **`margin` 第三个数（下）**，通常就能改变「桌子和底下操作区」的间距。

---

## 6. 最外层边距：`.dp-game-root`

整个对局页最外层，控制**和浏览器窗口边缘**的距离（含安全区）：

- **`padding`**：四边留白；**`padding-top`** 常与顶栏 safe-area 成对，见文件头注释。
- 改这里会改变**整体**是否贴边，也会间接影响「第一屏能放下多少」。

---

## 7. 和 `App.vue` 的关系（可选读）

路由 **`/game`** 时，`App.vue` 会给外层加 **`full-page--dp-game`**，让 **`#app` → `.full-page` → `.dp-game-root`** 形成一条 **flex 链**，把对局页在竖屏上铺满视口、减少上下露灰边。  
一般**调顶栏—圆桌—底栏间距不用改 `App.vue`**，除非你遇到整页高度不对。

---

## 8. 推荐调参顺序（小白照着做）

1. **先定设备**：用手机竖屏、横屏、电脑宽屏各看一眼；不同 **`@media`** 可能各管一段。
2. **改「顶栏和圆桌」**：
   - 优先动 **`.dp-top-bar` 的 `margin-bottom`**（全局最直观）。
   - **对局 `/game`**：三档变量在 **`dp-game-layout-tiers.css`**（`--dp-game-main-pad-top` 等）；通用兜底仍在 **`dp-game-shell.css`**。
   - 再微调 **`.dp-game-table` 的 `margin` 第一个数**。
3. **改「圆桌和底部操作区」**：动 **`.dp-game-table` 的 `margin` 第三个数**，以及 **`.dp-game-hero-action-row`** 的上下 margin。
4. **改一页后刷新**，全屏/非全屏各试一次；刘海机再试一次。

---

## 9. 相关文件速查

| 用途 | 文件 |
|------|------|
| 对局页结构（只有类名，几乎无数值） | `front/dp_game/src/components/game.vue` |
| **布局与间距的主战场** | `front/dp_game/src/styles/dp-game-shell.css` |
| 主题颜色、`--dp-*` 变量 | `front/dp_game/src/styles/dp-game-themes.css` |
| 顶栏组件结构 | `front/dp_game/src/components/GameTopBar.vue` |
| 圆桌组件结构 | `front/dp_game/src/components/GameRoundTable.vue` |
| 底部手牌与操作 | `front/dp_game/src/components/GameHeroDockFooter.vue` |
| 牌桌按视口 scale 适配（内容盒量高） | `front/dp_game/src/mixins/dpGameTableFitMixin.js` |
| 三档布局（phone / tablet / desktop） | `front/dp_game/src/styles/dp-game-layout-tiers.css`、`front/dp_game/src/mixins/dpGameLayoutTierMixin.js` |
| 整页外壳（/game 铺满） | `front/dp_game/src/App.vue` |

---

## 10. 三档设备标准（对局 /game 专用）

对局根节点带 **`data-dp-layout-tier`**（由 `dpGameLayoutTierMixin.js` 随窗口更新）：

| 档位 | 大致判断 |
|------|-----------|
| **phone** | 宽度 ≤640px；或「短边 ≤480 且长边 ≤1100」（常见手机横屏全屏） |
| **tablet** | 宽度 641～1024px |
| **desktop** | 宽度 ≥1025px |

**主区顶距、顶栏字号、圆桌最小高度、整页 zoom** 等集中在 **`dp-game-layout-tiers.css`** 的 CSS 变量（如 `--dp-game-main-pad-top`、`--dp-game-top-title-fs`）。顶栏与圆桌在 **`game.vue`** 中为 **`header` | `main` | `footer` 同级 flex**，不是顶栏浮在桌面上。进入对局会为 `html`/`body` 加 **`dp-game-no-scroll`** 以减少整页滚动；浏览器全屏仍由 **`dpGameFullscreenMixin`** 在挂载时尝试进入。

---

## 11. CSS 小词典（只看这一节也能改）

| 术语 | 意思 |
|------|------|
| `margin` | 块外面的空隙；两个块都设 margin 时**会叠在一起**（不是简单相加）。 |
| `padding` | 块**边框内侧**的空隙，会把背景色一起撑开。 |
| `clamp(a, b, c)` | 取不小于 `a`、不大于 `c` 的值，中间随 `b` 算。 |
| `vmin` | 当前视口宽、高中**较短边**的百分比，用来随屏幕大小变。 |
| `@media (max-width: 600px)` | 仅当窗口宽度 ≤600px 时生效（常用来区分手机）。 |

---

若你改完出现「顶栏进刘海」或「顶栏和屏幕顶有缝」，请回到 **`dp-game-shell.css` 文件最开头**的安全区注释，按说明成对检查 **`.dp-game-root`** 与 **`.dp-top-bar`**，不要只改一侧。
