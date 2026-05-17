# 对局页（`game.vue`）主题绑定说明与新增主题自检问卷

本文说明 **`game.vue` 里各子组件与「界面主题」的关系**：哪些靠 **CSS 变量继承**，哪些需要 **单独传参**；新增一个主题时应在哪些文件里登记与写样式。

---

## 1. 核心机制（先读这段）

| 机制 | 说明 |
|------|------|
| **主题 id** | 与 `localStorage`、`data-dp-game-theme` 取值一致的小写英文 id（如 `default`、`strawberry`）。 |
| **根节点** | `game.vue` 根元素为 `.dp-game-root`，绑定 `:data-dp-game-theme="gameUiTheme"`。子树内所有使用 `var(--dp-*)` 的样式都会从这里继承变量。 |
| **body 同步** | 在 `created` / `watch gameUiTheme` 时，`game.vue` 把 `data-dp-game-theme` 同步到 **`document.body`**，用于挂在 body 上的 **Element UI**（`$message`、`$confirm` MessageBox、`v-modal` 等），它们不在 `.dp-game-root` 内。离开对局 `beforeDestroy` 会移除 body 上的属性。 |
| **节能模式** | 与主题 **正交**：根节点 `data-dp-eco-mode` + `dp-game-eco-mode.css` 统一降级动画；**不需要**为每个主题单独写两套节能样式。 |

**结论**：绝大多数子组件 **不需要** `theme` 这类 props；**外观**靠 `dp-game-themes.css` 里为每个主题定义同一套 `--dp-*` 变量即可。

---

## 2. `game.vue` 直接使用的子组件与主题关系

| 组件 | 文件 | 是否接收主题相关 props | 实际如何随主题变化 |
|------|------|------------------------|----------------------|
| **GameTopBar** | `GameTopBar.vue` | **是**：`:game-ui-theme.sync`、`:theme-options="gameThemeOptions"`、`:eco-mode.sync` | 顶栏内 **主题下拉框** 与当前选中 id 双向绑定；选项来自 `GAME_UI_THEMES`。 |
| **GameCommunityCards** | `GameCommunityCards.vue` | 否 | 样式在 `dp-game-community-cards.css` 等，引用 `--dp-*`（由根继承）。 |
| **GamePlayerCard** | `GamePlayerCard.vue` | 否（多处使用） | 牌背、灰底等来自 `dp-poker-cards.css` + 根上 `--dp-card-*`、`--dp-player-card-*` 等。 |
| **GameActionPanel** | `GameActionPanel.vue` | 否 | 按钮、面板色在 `dp-game-shell.css` 等，用 `--dp-btn-*`、`--dp-panel-*` 等。 |
| **GameHandRankModal** | `GameHandRankModal.vue` | 否 | 弹窗样式在 `dp-game-modals.css`，随 `--dp-panel-*` / `--dp-text-*`。 |
| **GameSpectatorModal** | `GameSpectatorModal.vue` | 否 | 同上。 |
| **GameOwnerPanel** | `GameOwnerPanel.vue` | 否 | 同上。 |
| **GameOwnerToolModal** | `GameOwnerToolModal.vue` | 否 | 同上；若含 Element UI 弹层，仍依赖 body 上主题变量 + `dp-game-element-ui.css`。 |

**模板内非组件区块**（聊天条、圆桌、倒计时、底栏、抽屉 `dp-game-sheet-*` 等）同样 **只依赖** `.dp-game-root` 上的 CSS 变量，**没有**逐块单独绑主题 id。

---

## 3. `game.vue` 引入的样式文件（与主题相关的职责）

| 文件 | 职责 |
|------|------|
| **`dp-game-themes.css`** | **每个主题**一组选择器：`.dp-game-root[data-dp-game-theme='…'], body[data-dp-game-theme='…'] { --dp-*: … }`。**新增主题时必须在这里增加一块**（与 `default` 同级变量对齐最省事）。 |
| **`dp-game-shell.css`** | 布局、顶栏、圆桌、操作区、聊天等；颜色引用 `var(--dp-*)`，**一般不改主题名**，除非该主题需要特殊布局。 |
| **`dp-game-modals.css`** | 居中弹窗、抽屉等，引用 `--dp-panel-*` 等。 |
| **`dp-game-element-ui.css`** | 覆盖 Element UI 挂载到 body 的层，使用 `body[data-dp-game-theme]` 上的变量（变量仍由 `dp-game-themes.css` 定义）。 |
| **`dp-game-eco-mode.css`** | 节能模式降级，**不按主题**复制。 |
| **`dp-poker-cards.css`** | 牌面/牌背；引用 `--dp-card-*` 等（主题在 `dp-game-themes.css` 或 shell 默认值）。 |
| **`dp-game-community-cards.css`** | 公共区扑克牌动画与外观，引用上述变量。 |

---

## 4. 新增主题：自检问卷（按顺序打勾）

复制下面清单，新增主题 id（例如 `ocean`）时逐项完成。

### A. 数据与持久化

- [ ] **`front/dp_game/src/constants/dpGameThemes.js`**  
  在 `GAME_UI_THEMES` 中增加 `{ id: 'ocean', label: '海洋' }`（id 与下文 CSS 选择器一致）。  
- [ ] **`front/dp_game/src/utils/dpGameTheme.js`**  
  `readGameTheme` / `writeGameTheme` 使用 `GAME_UI_THEME_IDS`，**无需改代码**（只要上一步 id 已加入列表）。  

### B. 样式（核心）

- [ ] **`front/dp_game/src/styles/dp-game-themes.css`**  
  增加一块：  
  `.dp-game-root[data-dp-game-theme='ocean'], body[data-dp-game-theme='ocean'] { … }`  
  建议复制 **default** 或最接近的现有主题，再改色值；至少覆盖 default 里已有的一套 `--dp-*`（字体、背景、面板、文字、强调色、按钮、输入框、倒计时环、房主色、玩家卡片等）。  
- [ ] **若需浅色桌布与牌背**：在 `dp-game-themes.css` 该主题块内覆盖 `--dp-table-felt-*`、`--dp-card-back-*`、`--dp-muck-*` 等（`dp-game-shell.css` 里台呢会 `color-mix` 暗色，浅色主题需覆盖 `--dp-table-felt-depth` 等，避免桌面发灰黑——见根目录 README「对局主题」说明）。  
- [ ] **`--dp-panel-bg` / `--dp-player-card-bg`**：须为 **可被 `color-mix` 的纯色**，勿用渐变作主色。  

### C. Element UI 与 body

- [ ] 确认 **`dp-game-element-ui.css`** 使用通用 `body[data-dp-game-theme]` 变量即可；**通常不必为每个主题单独加选择器**，只要 `dp-game-themes.css` 里 **body** 与 **.dp-game-root** 双写变量。  
- [ ] **`game.vue`** 已 `syncBodyDpGameTheme` / `clearBodyDpGameTheme`，**一般无需改**。  

### D. 子组件代码

- [ ] **除 `GameTopBar` 外**，子组件 **通常不需要** 为 new 主题加 props 或 `class` 分支。  
- [ ] **`GameTopBar`** 通过 `themeOptions` 自动出现新选项，**无需改组件**（只要 `GAME_UI_THEMES` 已更新）。  

### E. 可选验证

- [ ] 切换主题后，对局页背景、顶栏、圆桌、操作区、弹窗、**原生全屏** 下界面是否一致。  
- [ ] 触发 `$message` / `$confirm`，样式是否与对局主题协调。  
- [ ] 打开 **节能模式**，确认无需为该主题单独修样式。  

---

## 5. 组件关系简图（主题数据流）

```
readGameTheme() / 顶栏下拉
        ↓
game.vue: gameUiTheme
        ↓
.dp-game-root[data-dp-game-theme]  +  watch → writeGameTheme()
        ↓                              syncBodyDpGameTheme()
        ↓                              document.body[data-dp-game-theme]
        ↓
dp-game-themes.css 中对应主题的 --dp-* 变量
        ↓
shell / modals / cards / community-cards / element-ui 覆盖层
```

---

## 6. 相关源码位置速查

| 用途 | 路径 |
|------|------|
| 主题列表常量 | `front/dp_game/src/constants/dpGameThemes.js` |
| localStorage 读写 | `front/dp_game/src/utils/dpGameTheme.js` |
| `body[data-dp-game-theme]` 统一同步 | `front/dp_game/src/main.js` + `front/dp_game/src/utils/dpBodyGameTheme.js` |
| 登录/注册页样式 | `front/dp_game/src/styles/dp-auth-shell.css`（`#app.app--auth`） |
| 顶栏主题下拉 | `front/dp_game/src/components/GameTopBar.vue` |
| 变量定义 | `front/dp_game/src/styles/dp-game-themes.css` |

---

## 7. 大厅与子页（`home` / `room` / 曲库 / 历史对局）与登录注册

与对局页共用 **`GAME_UI_THEMES`**、`readGameTheme` / `writeGameTheme`（经 Vuex `SET_GAME_UI_THEME`）及 **`dp-game-themes.css`** 中的 `--dp-*` 变量。

| 页面 / 组件 | 根节点 | body 同步 | 说明 |
|-------------|--------|-----------|------|
| 登录 / 注册（`App.vue` `app--auth`） | 无单独 `.dp-game-root`，靠 **`body[data-dp-game-theme]`** 继承变量 | 是（`dpBodyGameTheme` 含 `/login`、`/register`、`/`） | 顶栏主题下拉在 `App.vue`；样式 **`dp-auth-shell.css`**。 |
| `home.vue` | `.dp-game-root` + `:data-dp-game-theme` | 是（`dpLobbyThemeMixin`） | 顶栏含主题下拉；**快捷入口**一行含快速匹配、建房、历史对局、曲库上传及邮箱（未读角标）、好友；房间列表用 `dp-lobby-shell.css` 面板与按钮类。 |
| `room.vue` | 同上 | 是 | 创建/加入房间后的等待页；含主题下拉。 |
| `MusicUpload.vue` | 同上 | 是 | Element 覆盖见全局 `dp-game-element-ui.css`（`main.js` 引入），表单/表格/上传随 `--dp-*`。 |
| `HandHistory.vue` | 独立路由时包一层 `.dp-game-root`；`embedded` 为 true 时不包根、**不同步 body** | 仅独立路由 | 列表样式用 `.hand-history-page--dp` + `--dp-*`（与对局弹层内列表一致）。 |
| `HandHistoryDetail.vue` | 独立路由时包 `.dp-game-root`；嵌入时不同步 body | 仅独立路由 | 牌谱详情独立页含主题下拉；`--embedded` 主题块仍负责变量着色。 |

大厅子页仍用 **`front/dp_game/src/mixins/dpLobbyThemeMixin.js`** 映射 `gameUiTheme` / `onLobbyThemeChange`。**`document.body[data-dp-game-theme]`** 由 **`front/dp_game/src/main.js`** 里 `router.afterEach`、`router.onReady` 与 `store.subscribe('dpGame/SET_GAME_UI_THEME')` 调用 **`front/dp_game/src/utils/dpBodyGameTheme.js`** 统一设置；**勿**在组件 `beforeDestroy` 里 `removeAttribute`，否则 SPA 在大厅子页之间跳转会短暂丢失 body 主题并诱发半屏灰底、滚动异常。

大厅专用控件与布局见 **`front/dp_game/src/styles/dp-lobby-shell.css`**（含 `.dp-game-root` 根布局与 `#app.app--lobby` 重置，避免整份引入 `dp-game-shell.css`）；**`main.js` 已全局引入** `dp-game-themes.css` 与 `dp-lobby-shell.css`，保证首屏与路由切换时变量与布局一致。

---

*若项目根目录 `README.md` 中「对局主题」段落有更新，以该处与 `docs/DPGAME.md` 为准；本文专注对局页组件与样式绑定关系。*
