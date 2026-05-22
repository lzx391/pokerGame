# 前端流畅度审计 · 壳层与非对局页（阶段 1）

> **角色**：前端体验审计（Shell Agent）  
> **范围**：`front/dp_game` 全站中 **非对局核心动效** 部分——路由壳、大厅子页、登录注册、弹层/抽屉、主题同步、列表 loading、节能档作用域。  
> **对局页扑克动效、顶栏 eco 开关、WebSocket 节奏** 见并行文档 `frontend-fluidity-audit-game.md`（由 Game Agent 产出）。  
> **约束**：本文档仅为审计结论，**不含实现补丁**；未改任何源码。

---

## 1. 审计方法与环境

| 项 | 说明 |
|---|---|
| 代码基线 | `front/dp_game`（Vue 2 + Vue Router 懒加载 + Vuex `dpGame` + Element UI 按需注册） |
| 全局样式入口 | `src/main.js`：`dp-game-themes.css`、`dp-lobby-shell.css`、`dp-auth-shell.css`、`dp-game-element-ui.css`、`dp-social-lists.css` 等 |
| 主题/节能存储 | `localStorage`：`dp_game_ui_theme`、`dp_game_eco_mode`（仅对局相关 CSS 读取后者） |
| body 主题 | `utils/dpBodyGameTheme.js`，由 `main.js` 的 `router.afterEach` / `onReady` / `store.subscribe(SET_GAME_UI_THEME)` 调用 |
| 运行口径 | 开发 `mvn spring-boot:run` 后前端 **8088 同端口直出**；路由 chunk 变更一般不需改 Nginx，部署注意静态资源缓存 |

---

## 2. 站点地图（壳层分类）

| 路由 | 组件 | `#app` 类（`App.vue`） | `.dp-game-root` | `body[data-dp-game-theme]` | `data-dp-eco-mode` | 备注 |
|---|---|---|---|---|---|---|
| `/`、`/login`、`/register` | `login` / `register` | `app--auth` | 无 | ✅ `dpBodyGameTheme` | ❌ | 主题条在 `App.vue`；样式 `dp-auth-shell.css` |
| `/home` | `home.vue` | `app--lobby` | ✅ | ✅ | ❌ | 邮箱/好友 `el-dialog`/`el-drawer`；房间列表轮询 |
| `/create-room` | `CreateRoom.vue` | **无 `app--lobby`** ⚠️ | ✅ | ✅ | ❌ | 见 §3.1 路径不一致 |
| `/hand-history`、`/hand-history/detail/:id` | `HandHistory` / `HandHistoryDetail` | `app--lobby` | ✅（详情根类名略异） | ✅ | ❌ | 详情页大量 scoped 本地 `--hd-*` |
| `/leaderboard` | `LeaderboardPage.vue` | `app--lobby` | ✅ | ✅ | ❌ | scoped 样式硬编码 Element 默认色 |
| `/music-upload` | `MusicUpload.vue` | `app--lobby` | ✅ | ✅ | ❌ | `el-form`/`el-upload`/`el-table` |
| `/room/:roomId` | `room.vue` | `app--lobby` | ✅ | ✅ | ❌ | 2s 轮询房间状态 |
| `/guide` | `GameButtonGuidePage.vue` | `app--dp-game` | ✅ | ✅ | ✅ | 复用对局 flex 链 + `dp-game-eco-mode.css` |
| `/game/:roomId` | `game.vue` | `app--dp-game` | ✅ | ✅ | ✅ | 归 **audit-game** |
| `/image_upload` | `image_upload.vue` | 无专用类 | 无 | ❌ 移除属性 | ❌ | 遗留页，无主题/布局体系 |

**壳层结论**：大厅族页面已统一 `.dp-game-root` + `dpLobbyThemeMixin`；登录族靠 `body` 变量；`/guide` 被当作对局壳（`app--dp-game`）而非大厅壳。

---

## 3. 路由切换

### 3.1 机制现状

- **无** `<transition>` 包裹 `router-view`，**无** `<keep-alive>`：每次换路由整页销毁/重建对应 chunk 组件。
- 路由均为 **动态 import**（webpack chunk），**首次**进入子页需拉 JS，期间旧页已卸载 → 易出现 **短空白或默认 `#f5f7fa` 底**（`App.vue` 全局背景），直至新页 `created` + 样式生效。
- `router.afterEach` 同步 `syncDpBodyGameTheme`：主题变量与 body 背景切换与组件 mount **同一 tick**，无额外闪屏逻辑。

### 3.2 `App.vue` 与 `dpBodyGameTheme` 路径不一致（P0 壳层）

| 路径 | `dpBodyGameTheme` `lobbyLike` | `App.vue` `isLobbyRoute` |
|---|---|---|
| `/create-room` | ✅ | ❌ **未列入** |
| `/guide` | ❌（走 `gameLike`） | ✅ `isGameRoute` |
| `/image_upload` | ❌ | ❌ |

**影响（`/create-room`）**：`body` 仍有 `data-dp-game-theme`，但 `#app` **未**加 `app--lobby`，`dp-lobby-shell.css` 中针对 `#app.app--lobby` 的 `display/overflow/min-height` 重置 **不生效**。表现为：创建房间页可能沿用默认 `#app` 灰底 flex 行为，与 `/home` 跳转后「半屏露底 / 滚动异常」历史问题同类（文档 `THEME_BINDING_README.md` 已强调 lobby 链一致性）。

**建议（写入 plan，非本文实现）**：将 `/create-room` 纳入 `isLobbyRoute` 与 `lobbyLike` 单一真源（常量数组），避免双处手写漂移。

### 3.3 典型跳转链

| 场景 | 导航方式 | 壳层观感风险 |
|---|---|---|
| 登录 → `/home` | `router.push` | 首进 home chunk；auth 灰底 → 主题底，可接受 |
| 大厅 → `/game/:id` | `push` | **重壳切换**：`app--lobby` → `app--dp-game`，布局模型突变；对局 chunk + 可能 WS，见 game 审计 |
| 大厅 ↔ `/leaderboard` 等 | `push` | 同壳，chunk 冷启动空白为主 |
| 退出对局 → `/home` | 多为 `push`/`replace` | 对局销毁；若曾全屏需 mixin 清理（game 审计） |
| `/home` → `/guide` | `push` name `GameButtonGuide` | lobby → **game 壳**，eco/顶栏/布局 tier 全套载入 |

### 3.4 路由切换 · 审计结论

| 等级 | 发现 |
|---|---|
| P0 | `/create-room` 未进 `app--lobby`，与 body 主题同步列表不一致，布局/露底风险 |
| P1 | 无全局 route loading / skeleton，懒加载 chunk 首次进入可感知空白 |
| P1 | 大厅 ↔ 对局壳切换无过渡，依赖用户心智；双档方案需定义「壳层切换是否动画」（节能档应禁止） |
| P2 | `/image_upload` 未接入主题体系，若仍对外暴露需下线或重定向 |

---

## 4. 列表与异步 Loading

### 4.1 模式归纳

全站壳层几乎统一为 **文案态**：`<p>加载中…</p>` / 按钮 `:disabled` + 文案变化；**未使用** `v-loading`、`el-skeleton`、进度条。

| 页面/模块 | 状态字段 | UI 反馈 | 备注 |
|---|---|---|---|
| `home.vue` 房间列表 | `roomsLoading` | 文案 + 刷新图标 **CSS spin** | 10s 定时 `getRooms`；手动刷新同 spin |
| `home.vue` 快速匹配 | `quickMatchLoading` / `quickMatchPolling` | 按钮文案 | — |
| `LeaderboardPage` | `loading` | 文案 | 30s tab 缓存 |
| `HandHistory` | `loading` | 文案 | 分页 |
| `HandHistoryDetail` | `loading` | 文案 + **spinner 动画** | scoped `@keyframes hand-detail-spin` |
| `MusicUpload` 列表 | `listLoading` | 文案 | 宽表/卡片响应式 |
| `room.vue` | 无独立 loading | 静默轮询 | 2s `fetchRoomInfo` |
| 邮箱/好友 | `mailboxLoading` / `friendsLoading` | `dp-social-sheet__hint` | drawer/dialog 内 |
| `HomeProfileModal` | `loading` | 对话框内文案 | `el-dialog` |
| `FriendChatDialog` | `loading` / `sending` | 文案 + `el-button :loading` | — |

### 4.2 与双档（节能/标准）关系

- 壳层 loading **无** `data-dp-eco-mode` 分支；**spin 动画在节能档下仍会运行**（若全站节能档扩展到大厅，需统一降级，见 §8）。
- `prefers-reduced-motion`：**未**在 lobby/auth/leaderboard 样式中使用；仅对局相关 CSS/组件有部分覆盖。

### 4.3 列表 Loading · 审计结论

| 等级 | 发现 |
|---|---|
| P1 | 加载态信息密度低，长列表（曲库、历史）易「白屏感」；应用色彩层次 + 静态占位而非动效 spin |
| P1 | `room.vue` 无 loading/error 提示，弱网时 UI 冻结感 |
| P2 | 刷新 spin（`home-room-list-spin`）与详情 spinner 未纳入全站 motion token |

---

## 5. Sheet / Modal / Drawer

### 5.1 技术栈分布

| 类型 | 壳层使用处 | 主题依赖 | 动效来源 |
|---|---|---|---|
| **Element `el-dialog`** | 个人资料、邮箱、好友聊天、`CatTutorialDialog`（若启用） | `body[data-dp-game-theme]` + `dp-game-element-ui.css` | Element 默认 fade + `v-modal` |
| **Element `el-drawer`** | `home.vue` 好友列表 `direction=rtl` | 同上 | 滑入过渡 |
| **自定义 mask**（`.hand-rank-modal-*`） | `GamePlayGuideModal`（大厅/对局共用） | `dp-game-modals.css`（组件内 import）+ `--dp-*` | mask + 面板静态；tab `transition: background 0.15s` |
| **对局 BottomSheet** | 仅 `game.vue` / `GameDpGameSheets` | — | 归 **audit-game** |

`main.js` 已注册：`Dialog`、`Drawer`、`Message`、`MessageBox` 等；**若方案新增** `el-loading`、`el-skeleton`、`Popover` 等，须在 `main.js` 按需 `import` + `Vue.use`（硬约束）。

### 5.2 壳层弹层清单

| 组件 | 挂载 | `append-to-body` | 风险点 |
|---|---|---|---|
| `HomeProfileModal` | `el-dialog` | 默认 true | 荣誉徽章局部样式；加载纯文案 |
| `GamePlayGuideModal` | 自定义 | — | 依赖 `dp-game-modals.css`；大厅首登自动弹出 |
| `home` 邮箱 | `el-dialog` `width=560px` | ✅ | 移动端宽度靠 `custom-class` + 全局 CSS |
| `home` 好友 | `el-drawer` `size=380px` | ✅ | 列表项多按钮，滚动在 drawer body |
| `FriendChatDialog` | `el-dialog` | ✅ | 消息区滚动；轮询/SSE 归 social 逻辑 |

### 5.3 Element 覆盖完整性

`dp-game-element-ui.css` 已覆盖：`v-modal`、`el-message-box`、`el-dialog`、`el-drawer`、表单项、表格等，选择器前缀 `body[data-dp-game-theme]`。

**缺口**：

- 组件 **scoped 硬编码**（如 `LeaderboardPage` 的 `#409eff`）在弹层 **外** 区域不跟主题走。
- `el-upload` 拖拽区（曲库）部分仍用 theme-chalk 默认色，靠全局覆盖不完整时可能「浅色块」。

### 5.4 Sheet/Modal · 审计结论

| 等级 | 发现 |
|---|---|
| P1 | 双档下节能档应缩短/去掉 `el-drawer`/`el-dialog` 动画（CSS `transition-duration` 或 `data-dp-eco-mode` on body） |
| P1 | 自定义 `GamePlayGuideModal` 与 `el-dialog` 并存，质感不统一（圆角/阴影/层次） |
| P2 | `FriendChatDialog` 标题 slot 布局在窄屏需验收 safe-area |

---

## 6. 主题与 `body` 同步

### 6.1 数据流（现状良好）

```
Vuex gameUiTheme (+ custom)
  → 各页 .dp-game-root[data-dp-game-theme] + inline custom vars
  → syncDpBodyGameTheme → document.body[data-dp-game-theme]
  → dp-game-themes.css / dp-game-element-ui.css
```

- **禁止**在壳层组件 `beforeDestroy` 中 `removeAttribute('data-dp-game-theme')`（`dpLobbyThemeMixin` 注释已说明）；当前壳层 **遵守**。
- `game.vue` **不再**单独 sync/clear body（仅根节点属性），与 central 方案一致。

### 6.2 自定义主题

`resolveEffectiveThemeId` + `applyCustomThemeToBody`：body 属性为预设底 id，强调色 inline `--dp-*` 覆盖。登录/大厅/对局 **共用** store，切换路由不丢主题。

### 6.3 露底与背景

| 层 | 背景来源 |
|---|---|
| `body[data-dp-game-theme]` | `var(--dp-game-bg)` + optional image（`dp-lobby-shell.css`） |
| `.dp-game-root` | 同上 |
| `#app` 默认 | **`#f5f7fa` 硬编码**（`App.vue`） |
| `#app.app--lobby` | `transparent`（交给 body/root） |
| `#app.app--auth` | `transparent` |

**风险**：未覆盖 `app--lobby` 的页面（如 `/create-room`）或 chunk 加载间隙，仍可能闪 **默认灰底**。

### 6.4 主题 · 审计结论

| 等级 | 发现 |
|---|---|
| P0 | `/create-room` 的 `#app` 壳类与 body 同步列表不一致 |
| P1 | `#app` / `body` 默认色未完全 token 化（`#f5f7fa`） |
| P2 | `HandHistoryDetail` 默认 `--hd-*` 与 `[data-dp-game-theme]` 覆盖并存，维护成本高 |

---

## 7. 新页面 / 子页样式一致性

### 7.1 已对齐体系

- **布局/控件**：`dp-lobby-shell.css` 的 `.dp-lobby-inner`、`.dp-lobby-panel`、`.dp-btn--*`、`.dp-game-theme-row`。
- **主题变量**：`dp-game-themes.css` 的 `--dp-text-*`、`--dp-panel-*`、`--dp-accent` 等。
- **社交列表**：`dp-social-lists.css` 与大厅面板令牌一致。

### 7.2 漂移页面（硬编码 Element 色）

| 文件 | 现象 |
|---|---|
| `LeaderboardPage.vue` scoped | `#409eff`、`#909399`、`#fff`、`#f5f7fa` 等大量写死 |
| `HandHistory.vue` scoped | 部分 pager/table 硬编码，部分已 `var(--dp-accent)` |
| `HandHistoryDetail.vue` scoped | 独立 `--hd-*` 调色板 + 底部 `[data-dp-game-theme]` 映射 |
| `App.vue` | 登录导航 `#409eff`、`transform` hover |

**质感问题**：切换主题后，排行榜/历史页 **仍呈「Element 默认蓝」**，与 `--dp-accent` 脱节；标准档若再加动效，会放大「两套 UI」观感。

### 7.3 重复 import CSS

`home`、`room`、`CreateRoom`、`HandHistory`、`Leaderboard`、`MusicUpload`、`HandHistoryDetail` 均在组件内 `import dp-game-themes.css` + `dp-lobby-shell.css`（`main.js` 已全局引入 themes + lobby）。**功能无害**，但增加 chunk 重复与心智负担；plan 可建议「壳层页仅依赖 main 全局 import」。

### 7.4 样式一致性 · 审计结论

| 等级 | 发现 |
|---|---|
| P0（质感） | 排行榜等子页未完全接入 `--dp-*`，不符合「色彩层次统一」产品口径 |
| P1 | 建议新增 `dp-depth-tokens.css` / `dp-motion-tokens.css` 专供壳层（plan 阶段定稿） |
| P2 | 组件级重复 import 可收敛 |

---

## 8. 非对局页与节能档（`eco`）作用域

### 8.1 现状

| 项 | 行为 |
|---|---|
| 存储 | `readEcoMode()` / Vuex `ecoMode`，**全局**持久化 |
| CSS 作用域 | `dp-game-eco-mode.css` 选择器 **仅** `.dp-game-root[data-dp-eco-mode='true']` |
| 绑定 eco 的页面 | `game.vue`、`GameButtonGuidePage.vue`（`/guide`） |
| 大厅/登录/排行榜等 | **无** `data-dp-eco-mode`，**未** import `dp-game-eco-mode.css` |
| 顶栏 eco 开关 | 仅对局顶栏 `GameTopBar`（guide 页亦有） |

因此：**用户在大厅无法切换节能档**；在对局打开 eco 后回大厅，store 虽为 `true`，但 **壳层 CSS 不读取**，壳层动画 **不受影响**。

### 8.2 壳层仍存在的动效（与 eco 无关）

| 来源 | 动效 |
|---|---|
| `home.vue` | 刷新图标 `home-room-list-spin` infinite |
| `App.vue` | `.nav-link` `transition: all 0.3s` + `translateY` hover |
| `LeaderboardPage` | tab `transition` 0.2s |
| `HandHistoryDetail` | spinner rotate |
| `GamePlayGuideModal` | tab/按钮 0.15s transition |
| Element UI | dialog/drawer/message 默认过渡 |

### 8.3 与产品双档口径的差距

产品要求：**节能档 = 少动效、靠色彩层次**；**标准档 = 可有扑克动效 + 层次美学**。

当前实现将 eco **限定在对局 DOM 子树**，壳层等于「第三档：始终轻度 Element 动效 + 零星 CSS 动画」，**未**实现全站双档。

### 8.4 Eco 作用域 · 审计结论

| 等级 | 发现 |
|---|---|
| P0 | 非对局页 **缺少** `data-dp-eco-mode`（或 `body` 级）作用域，全站双档在壳层 **不成立** |
| P0 | 大厅无法设置节能档；Android 验收若从登录→大厅→进房，仅对局段节能不足 |
| P1 | 壳层 spin/hover 应在节能档禁用或改为静态层次反馈 |
| P1 | `/guide` 已绑 eco，但属 game 壳，与大厅策略分裂 |

**plan 应采纳（建议）**：`body[data-dp-fluidity='eco'|'standard']` 或扩展 `data-dp-eco-mode` 到 `body`，壳层 CSS 用同一属性降级；对局仍保留细粒度 `dp-game-eco-mode.css`。

---

## 9. 分模块速查表（壳层）

| 模块 | 路由/入口 | 主题 | Loading | 弹层 | Eco | 主要风险 |
|---|---|---|---|---|---|---|
| 登录注册 | `/login` `/register` | body | 无 | 无 | ❌ | App 硬编码蓝；nav hover 动效 |
| 大厅 | `/home` | root+body | 房间/社交 | drawer+dialog+自定义 guide | ❌ | 轮询+spin；路径多 |
| 建房 | `/create-room` | root+body | 提交文案 | 无 | ❌ | **app--lobby 缺失** |
| 房间等待 | `/room/:id` | root+body | 无 | 无 | ❌ | 轮询无反馈 |
| 历史 | `/hand-history*` | root+body | 文案/spinner | 可嵌入对局 modal | ❌ | 样式双轨 `--hd-*` |
| 排行榜 | `/leaderboard` | root+body | 文案 | 无 | ❌ | 硬编码色 |
| 曲库 | `/music-upload` | root+body | 文案 | el-upload | ❌ | 宽表滚动 |
| 按钮教程 | `/guide` | game 壳 | — | 对局 modal 系 | ✅ | 壳层分类特殊 |
| 头像上传遗留 | `/image_upload` | 无 | — | el-upload | ❌ | 未维护 |

---

## 10. 汇总：壳层待办（供 plan.md 引用）

### P0

1. 统一 `/create-room` 与 `lobbyLike` / `app--lobby` 路由分类真源。  
2. 全站双档：为非对局页建立 **body 或 `#app` 级** eco/standard 作用域；壳层禁止项与对局清单对齐（game 审计补充扑克项）。  
3. 排行榜/历史等子页 **硬编码色 → `--dp-*`**，满足「质感靠层次不靠特效」。

### P1

1. 路由首次 chunk 加载的 **静态壳占位**（无新 npm，可用 CSS skeleton）。  
2. 统一 loading/spin：纳入 `dp-motion-tokens.css`；节能档关闭 infinite animation。  
3. Element 弹层过渡在 eco 下 `transition: none` / 缩短 duration。  
4. `room.vue` 增加 loading/error 态。  
5. 收敛壳层页面对 `dp-game-themes.css` 的重复 import。

### P2

1. 处理或下线 `/image_upload`。  
2. `HandHistoryDetail` `--hd-*` 与全局 token 合并策略。  
3. `prefers-reduced-motion` 壳层兜底（与 eco 并存）。

---

## 11. 与阶段 2（`frontend-fluidity-plan.md`）的衔接

| 依赖 | 状态 |
|---|---|
| 本文档 `frontend-fluidity-audit-shell.md` | ✅ 已完成 |
| `frontend-fluidity-audit-game.md` | ⏳ **待 Game Agent 完成** |
| `frontend-fluidity-plan.md` | 🔒 **禁止先于 audit-game 编写** |

**plan.md 必须合并**：

- 本文 §3–§8 的壳层 P0/P1；  
- audit-game 的对局动效、顶栏 eco、牌面动画、WS/计时器；  
- 产品验收双档表（Android 节能不卡 / iOS·PC 标准不回归）；  
- Android 默认节能策略（推荐 A）与 P0/P1 分界；  
- token 文件建议（`dp-motion-tokens.css`、`dp-depth-tokens.css`）；  
- 禁止/允许清单给实现 Agent。

---

## 12. 证据索引（关键文件）

| 主题 | 路径 |
|---|---|
| 路由表 | `front/dp_game/src/router/index.js` |
| App 壳类 | `front/dp_game/src/App.vue` |
| body 主题同步 | `front/dp_game/src/utils/dpBodyGameTheme.js`、`main.js` |
| 大厅布局/背景 | `front/dp_game/src/styles/dp-lobby-shell.css` |
| 登录主题 | `front/dp_game/src/styles/dp-auth-shell.css` |
| Element 主题覆盖 | `front/dp_game/src/styles/dp-game-element-ui.css` |
| Eco 读写 | `front/dp_game/src/utils/dpGameEcoMode.js`、`styles/dp-game-eco-mode.css` |
| 主题绑定说明 | `front/dp_game/docs/THEME_BINDING_README.md` |

---

*文档版本：阶段 1 · Shell 审计 · 2026-05-22*
