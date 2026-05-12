# 对局页 UI：组件结构、`seatIndex` 与「本机视角」旋转、主题绑定

本文说明 **DP 游戏对局页**（`front/dp_game`）的界面结构、**主题系统**已做改动，以及后续如何扩展。

---

## 1. 对局页组件结构

入口路由为房间对局页组件 **`src/components/game.vue`**。其子组件与职责如下。

| 组件文件 | 职责 |
|----------|------|
| `GameTopBar.vue` | 顶栏：房间号、阶段、底池、跟注额；牌型说明 / 观众席 / 退出；旁观时在同一组件内展示「下一局加入对局」 |
| `GameCommunityCards.vue` | 公共牌展示、飞入与翻转动画 |
| `GamePlayerCard.vue` | 单个座位：昵称、筹码、底牌/遮罩、庄盲标记、行动状态等 |
| `GameActionPanel.vue` | 轮到自己时的跟注、加注、All-In、弃牌与倒计时；结算阶段「准备下一局 / 补码」同一组件内切换 |
| `GameOwnerPanel.vue` | 房主：神器入口、看穿底牌、摊牌阶段按池/简单选赢家 |
| `GameHandRankModal.vue` | 牌型说明弹窗 |
| `GameSpectatorModal.vue` | 观众席名单弹窗 |
| `GameOwnerToolModal.vue` | 房主神器（移交、踢人、加 Bot 等）弹窗 |

`game.vue` 负责：拉取/应用房间状态、WebSocket、玩家网格布局、`getPlayerBoxStyle`（座位卡片外框）、以及主题根节点与主题选择器。

**玩家网格顺序（本机）**：`playersDisplayOrder` 在 **`players` 数组顺序不变** 的前提下，把 **当前登录用户所在座位旋到第一项**（自己永远在列表最前），其余人按原相对顺序跟在后面；传给 `GamePlayerCard` 的 **`seatIndex` 仍为服务端座位下标**，因此轮到谁行动（`actIndex`）、庄位发牌动画、D/SB/BB 标记等与后端一致。纯观众（昵称不在 `players` 里）时保持服务端原始顺序。


**玩家网格布局**：`dp-game-players-grid` 在**包括手机在内的所有宽度**下均为 `repeat(auto-fill, minmax(...))` 多列（极窄时自动退成单列）；**≥768px** 时列宽 `148px` 略宽裕。所有座位同一紧凑样式。`GamePlayerCard` 默认 **`compact=true`**：未摊牌时不对他人渲染底牌背面占位，本人与摊牌/房主看穿后照常显示底牌与牌型。若需恢复旧版宽大卡片，可对组件传 **`compact=false`**。

---

## 2. 主题系统：已落地的改动

### 2.1 行为说明

- 对局页最外层为 **`div.dp-game-root`**，并通过 **`data-dp-game-theme`** 设置为当前主题 id（见下表 `data-dp-game-theme` 列）。
- 主题切换控件在 **`game.vue`** 顶部（「界面主题」下拉框）。
- 用户选择会写入浏览器 **`localStorage`**，键名：**`dp_game_ui_theme`**。下次进入对局页会通过 `readGameTheme()` 恢复。
- 合法主题 id 列表与下拉文案来自 **`src/constants/dpGameThemes.js`**；`writeGameTheme` 只会写入列表内 id，避免脏数据。

### 2.2 涉及文件

| 路径 | 作用 |
|------|------|
| `front/dp_game/src/constants/dpGameThemes.js` | 主题列表 `GAME_UI_THEMES`、id 数组 `GAME_UI_THEME_IDS` |
| `front/dp_game/src/utils/dpGameTheme.js` | `readGameTheme()` / `writeGameTheme(themeId)` |
| `front/dp_game/src/styles/dp-game-themes.css` | 各主题下 **`--dp-*` CSS 变量** 定义 |
| `front/dp_game/src/styles/dp-game-shell.css` | 对局页布局与面板/按钮的 **class**（颜色引用变量） |
| `front/dp_game/src/components/game.vue` | 引入上述样式；`gameUiTheme` + `watch` 持久化；`getPlayerBoxStyle` 使用 `var(--dp-player-*)` |
| `front/dp_game/src/components/GameTopBar.vue` | `dp-top-bar`、`dp-btn--*`；旁观条 `dp-top-bar__spectator*` |
| `front/dp_game/src/components/GameActionPanel.vue` | `dp-action-panel`、`dp-btn--call` 等；结算准备 `dp-action-panel--settled`、`dp-action-panel__settled-*` |
| `front/dp_game/src/components/GameOwnerPanel.vue` | 改为 `dp-owner-panel` 等；池内选人按钮仍用内联 style，但颜色用 **`var(--dp-*)`** |

### 2.3 当前预制主题

| `data-dp-game-theme` | 显示名 | 风格概要 |
|----------------------|--------|----------|
| `default` | 明亮经典 | 灰底、白面板，与早期对局页接近 |
| `scifi` | 未来科幻 | 深色底、青蓝霓虹描边与高对比强调色 |
| `gothic` | 哥特暗夜 | 暗褐紫底、衬线字体、金/酒红点缀 |
| `macau` | 澳门风云 | 墨绿赌桌光晕、香槟金边丝绒感；**面板为纯色**便于台呢 color-mix |
| `midnight` | 午夜幽蓝 | 深海蓝底、银蓝描边与冷灰字，夜场贵宾厅气质 |
| `forest` | 森野秘境 | 苔绿深林、琥珀与嫩绿强调，户外木屋牌桌感 |
| `sunset` | 落日熔金 | 暮紫底、顶部霞光、暖橙与洋红底池对比 |
| `ink` | 水墨丹青 | 浅宣纸底、墨灰字、朱砂红强调（唯一浅色预制主题） |
| `strawberry` | 草莓甜心 | 粉嫩童话、草莓糖霜点缀 |
| `cotton` | 绵云轻柔 | 浅粉紫童话、偏云雾感 |
| `halloween` | 万圣惊魂 | 暗夜底、南瓜橙与幽紫描边、牌背与台呢同色氛围 |

### 2.4 CSS 变量约定（节选）

完整定义见 **`dp-game-themes.css`**。扩展新主题时建议至少覆盖下列一类变量（可从 `default` 整块复制再改色）：

- **页面与面板**：`--dp-font-ui`、`--dp-game-bg`、`--dp-game-bg-image`、`--dp-panel-bg`、`--dp-panel-border`、`--dp-panel-shadow`
- **文字与语义色**：`--dp-text-primary`、`--dp-text-secondary`、`--dp-text-muted`、`--dp-accent`、`--dp-pot`、`--dp-warning`、`--dp-success`、`--dp-danger`、`--dp-cyan`
- **按钮与输入**：`--dp-btn-primary-bg`、`--dp-btn-primary-fg`、`--dp-btn-raise-*`、`--dp-btn-allin-*`、`--dp-btn-ghost-*`、`--dp-input-bg`、`--dp-input-border`、`--dp-timer-ring`、`--dp-timer-text`
- **房主专用按钮**：`--dp-owner-purple-*`、`--dp-owner-orange-*`
- **子块（边池卡片等）**：`--dp-subpanel-bg`、`--dp-subpanel-border`
- **座位卡片外框**（由 `game.vue` 的 `getPlayerBoxStyle` 使用）：`--dp-player-card-bg`、`--dp-player-card-offline-*`、`--dp-player-card-turn-*`、`--dp-player-border-me`、`--dp-player-card-winner-*`、`--dp-player-showdown-border`

新增 UI 时，**优先使用已有 `var(--dp-*)`**，避免在组件里写死十六进制颜色，以便各预制主题自动一致。

### 2.5 尚未接入主题的部分（可后续做）

- 各 **Element UI 弹窗** 与 **`dp-game-modals.css`** 仍以默认浅色为主。
- **`GamePlayerCard.vue`** 内标签、积分条等仍有大量 **内联颜色**；仅 **卡片容器背景/边框** 随主题变化。
- **牌背 / 圆桌台呢 / 弃牌小卡**：各预制主题已在 **`dp-game-themes.css`** 中通过 **`--dp-card-back-*`**、**`--dp-table-felt-*`**、**`--dp-muck-*`** 一套变量对齐风格（与万圣节同级）；**正面牌面**的额外装饰仍以 **`dp-poker-cards.css` / `dp-game-community-cards.css`** 为主，尚未按主题细拆。

---

## 3. 后续增加主题（操作步骤）

1. 在 **`src/constants/dpGameThemes.js`** 的 `GAME_UI_THEMES` 中增加一项，例如 `{ id: 'forest', label: '森林' }`（`id` 仅使用字母数字与连字符，与 CSS 选择器兼容）。
2. 在 **`src/styles/dp-game-themes.css`** 末尾增加一块：
   ```css
   .dp-game-root[data-dp-game-theme='forest'] {
     --dp-game-bg: #...;
     /* 其余变量从 default 复制后按需修改 */
   }
   ```
3. 保存后构建或刷新即可；**无需改** `dpGameTheme.js`（合法 id 由 `GAME_UI_THEME_IDS` 自动生成）。

若新主题缺少某个变量，浏览器会沿继承链回退；为减少意外，建议每个主题块都写全与 `default` 同名的变量集合。

---

## 4. 后续增加对局页组件时的建议

1. 将新面板/按钮放在 **`dp-game-root` 内部**，以便继承 `--dp-*` 变量。
2. 样式优先写在 **`dp-game-shell.css`**（或组件 `<style>` 中使用 `var(--dp-*)`），与现有 `dp-top-bar`、`dp-action-panel` 等命名风格保持一致。
3. 需要随主题变的颜色：**不要写死**，改用变量；需要新增语义时，先在 **`dp-game-themes.css` 的各主题块里同时补充**该变量，再在 shell 或组件里引用。

---

## 5. 与后端 / WebSocket 的关系

当前 **主题仅前端本地存储**，不参与 **`/dpRoom/*` HTTP** 与 **`/ws/dp-game`** 推送。若将来要「房主统一房间主题」，需要在后端房间模型与广播 JSON 中增加字段，并在 `game.vue` 的 `applyRoomFromServer` 中合并显示策略（注意与本地用户偏好冲突时的产品规则）。
