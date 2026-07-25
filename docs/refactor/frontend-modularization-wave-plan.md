# 前端模块化重组波次计划（Wave 1–7）

## 完成状态（Wave 7 · 2026-06-15）

| 域 | 目录 | 状态 | 说明 |
|----|------|------|------|
| user | `features/user/` | ✅ 已迁移 | 登录/注册/OAuth/资料/头像（13 文件） |
| lobby | `features/lobby/` | ✅ 已迁移 | `LobbyPage`、`CreateRoomPage`、大厅 API（10 文件） |
| quickmatch | `features/quickmatch/` | ✅ 已迁移 | 快匹退出工具、`QuickMatchPixelCritters`（3 文件） |
| room | `features/room/` | ✅ 已迁移 | `GamePage`、Game* 子组件、`dpGame` store（55 文件） |
| social | `features/social/` | ✅ 已迁移 | 邮箱/好友/邀请/私信、SSE、`dpMailbox`（18 文件） |
| history | `features/history/` | ✅ 已迁移 | 牌谱页与回放组件（6 文件） |
| leaderboard | `features/leaderboard/` | ✅ 已迁移 | 周榜页与 API（2 文件） |
| achievement | `features/achievement/` | ✅ 已迁移 | Toast/成就墙、store（6 文件） |
| music | `features/music/` | ✅ 已迁移 | BGM 播放器、上传页（3 文件） |
| npc | `features/npc/` | ⚠️ 部分 | trace 面板已迁入；mood/自定义 NPC 控制台仍留 `components/` |
| download | `features/download/` | ✅ 已迁移 | 下载中心（原 admin 域，4 文件） |
| presence | `features/presence/` | ✅ 已迁移 | 站点心跳（1 文件） |
| shared | `shared/` | ⚠️ 部分 | `api/http.js`、`utils/dpApiResult.js`；Retro/Crt UI 未迁入 |
| compat | `src/components/` 等 | 🔄 保留 | **Wave 7 未删 re-export**；72 个 `.vue` 薄层 + 17 个待迁真实组件 |

**Wave 7 交付**：`front/dp_game/docs/modularization-done-summary.md`（用户总结）、`modularization-remaining.md`（剩余清单）、`npm run build` 绿。

---

> **文档类型**：可执行波次计划（供 PM 派 Agent、前端负责人使用）  
> **核对基准**：`front/dp_game/src/`（**213** 源文件、**90** 个 `.vue`）  
> **对齐后端域**：`room/`、`lobby/`、`social/`、`quickmatch/`、`history/`、`user/` 等（见 [CLAUDE.md](../../CLAUDE.md)）  
> **契约参考**：[DPGAME.md](../DPGAME.md)、[WEBSOCKET.md](../WEBSOCKET.md)、[JWT.md](../JWT.md)、[dp-quick-match-flow.md](../dp-quick-match-flow.md)、[dp_friend_mailbox_mvp.md](../dp_friend_mailbox_mvp.md)  
> **权威说明**：本文档为**前端模块化**唯一波次计划；若存在 `frontend-android-migration-wave-plan.md`，以本文档为准，Android 专章不在此维护。  
> **状态**：草案 — 推荐默认项标有「待负责人确认」

---

## 1. 背景

MGDemoPlus 前端为 Vue 2 单仓（`front/dp_game/`），与 Spring Boot 后端同域部署（端口 8088）。

当前 `src/` 下 **213 个源文件** 按「页面 + 巨型组件」**扁平堆放**：

- **90 个 `.vue`** 几乎全部堆在 `components/` 根目录，无功能域边界；
- **`game.vue`**（约 **3067** 行）、**`home.vue`**（约 **2420** 行）耦合 WS、REST、社交、BGM、快匹等跨域逻辑；
- **API 仅 3 个封装文件**（`api.dpRoom.js`、`api.dpSocial.js`、`api.dpLeaderboard.js`），**45+ 处**组件/工具直接调用 `$http`，契约散落 **30 个文件**；
- **Vuex 3 模块**（`dpGame.js` ~643 行、`dpMailbox.js`、`dpAchievement.js`）与 `store/` 平铺，未按域拆分；
- **65 个 utils**、**6 个 mixins**、**20+ 样式文件** 域边界模糊。

后端已按功能域分包（`room`、`lobby`、`social`、`quickmatch`、`history`、`user`、`npc`…），前端目录与之**不对齐**，导致：

- PM / Agent 难以按域派工、Review 范围失控；
- 改 `game.vue` 一行可能牵动对局全流程；
- 新功能不知应落在哪个目录，重复 import 与循环依赖风险上升。

本计划将前端重组为 `features/` 域驱动结构，分 **7 波**渐进搬迁，每波可独立验收、可回滚（兼容层 re-export）。

---

## 2. 目标与非目标

### 2.1 目标

| # | 目标 | 验收口径 |
|---|------|----------|
| G1 | `src/features/` 与后端功能域一一对应 | 每域含 `api/`、`components/`、`pages/`（按需）、`store/`、`utils/` |
| G2 | 消灭散落 `$http` | 域内 REST 经 `features/*/api/` 或 `shared/http/`；Wave 7 前保留 `compat/` re-export |
| G3 | 拆解 `game.vue` / `home.vue` | Wave 4/3 后主文件各 **< 800 行**（逻辑下沉子组件 + utils） |
| G4 | 零业务回归 | 每 Wave 末 `npm run build` + 冒烟：登录 → 大厅 → 建房/进房 → 下注 → 结算 |
| G5 | 可并行派工 | 域边界清晰后，不同 Wave 可由不同 Agent 串行/有限并行执行 |

### 2.2 非目标

- **不改**后端 API 路径、WS 帧格式、JWT 签发逻辑（除非另开 RFC）。
- **不重写** UI 主题 / Retro8bit 视觉（仅搬迁文件、调整 import）。
- **不在本计划内**完成 Electron 客户端目录重组。
- **不修改** `front/` 代码作为本文档交付物（实施由各 Wave Agent 执行）。

### 2.3 总验收（Program Done）

全部 Wave 1–7 完成后：

1. `front/dp_game/src/features/` 存在且 router / store 仅从 `features/` 或 `shared/` 引用。
2. 旧路径 `components/`、`api/`、`utils/` 根目录仅保留 **re-export 薄层**（Wave 7 删除）。
3. `$http` 直调仅剩 `shared/http/` 与测试桩；业务调用经域 API 模块。
4. [docs/README.md](../README.md) 与 `front/dp_game/docs/` 架构说明已更新。

---

## 3. 现状摘要

### 3.1 规模

| 指标 | 数值 | 说明 |
|------|------|------|
| `src/` 源文件 | **213** | `.vue` + `.js` + mixins/constants/styles 等 |
| `.vue` 组件 | **90** | 几乎全部在 `components/` 扁平目录 |
| `game.vue` | **~3067 行** | 对局：WS、心跳、下注、房主工具、聊天、BGM… |
| `home.vue` | **~2420 行** | 大厅：公开房列表、快匹、邮箱、好友、资料 |
| API 封装文件 | **3** | 大量 REST 路径未收录 |
| 散落 `$http` | **45+ 处 / 30 文件** | 集中在 `game.vue`(31)、`home.vue`(26) |
| Vuex 模块 | **3** | `dpGame.js`、`dpMailbox.js`、`dpAchievement.js` |
| utils | **65** | 域边界模糊 |
| mixins | **6** | 主要在 `mixins/` 根目录 |

### 3.2 路由一览（`router/index.js`）

| 路径 | 组件 | 目标域 |
|------|------|--------|
| `/login`、`/register`、`/oauth/callback` | login / register / OAuthCallback | user |
| `/home` | home.vue | lobby + social（壳） |
| `/create-room` | CreateRoom.vue | lobby → room |
| `/game/:roomId` | game.vue | room |
| `/hand-history`、`/hand-history/detail/:id` | HandHistory / HandHistoryDetail | history |
| `/leaderboard` | LeaderboardPage | leaderboard |
| `/guide` | GameButtonGuidePage | room |
| `/music-upload`、`/download-center`、`/image_upload` | 运维/工具页 | music / admin |

### 3.3 痛点结构（现状）

```text
components/（90 个 vue 扁平）
    ├── game.vue ────────── WS + REST + 30+ 子组件 import
    ├── home.vue ────────── 快匹 WS + 大厅 REST + 社交 UI
    └── Game* / Dp* ─────── 命名前缀不统一

api/（仅 3 文件）
utils/（65 文件，域边界模糊）
store/modules/dpGame.js ── 对局状态与 room 域混杂
```

---

## 4. Wave 1–7 波次计划

### 4.0 人天汇总

| Wave | 名称 | 人天 | 累计 |
|------|------|------|------|
| **1** | 基线盘点 + 脚手架 + 兼容层 | **3–4** | 3–4 |
| **2** | user 域 | **2–3** | 5–7 |
| **3** | lobby 域（含 home 拆壳） | **3–4** | 8–11 |
| **4** | room 域（game 拆壳）★ | **7–9** | 15–20 |
| **5** | social + SSE | **3–4** | 18–24 |
| **6** | history / leaderboard / achievement / music / npc | **2–3** | 20–27 |
| **7** | 清理兼容层 + 文档收口 | **2** | **22–29** |

> PM 对外承诺：**22–29 人天**（含 Wave 1 基线盘点）。

---

### Wave 1 — 基线盘点 + 脚手架 + 兼容层

| 项 | 内容 |
|----|------|
| **目标** | 建立 `features/`、`shared/`、`compat/` 空壳；产出 `$http` 全量清单；抽出 axios 拦截器 |
| **范围** | 新建目录骨架；`main.js` 瘦身 → `shared/http/axios.js`；**零业务行为变更**；ripgrep `\$http` 产出调用矩阵 |
| **交付物** | 目录骨架；`shared/http/axios.js`、`shared/http/result.js`；`compat/**` 一键 re-export 脚本；`$http` 路径→文件→域矩阵 |
| **验收 checklist** | ☐ `npm run build` 通过 ☐ 所有路由可访问 ☐ 旧 import 路径仍可用（compat） ☐ `$http` 矩阵覆盖 30 文件 ☐ CI 无新增 lint 错误 |
| **人天** | 3–4 |
| **风险** | webpack alias 遗漏 → 在 `vue.config.js` 预置 `@features`、`@shared` |
| **依赖** | 无 |

---

### Wave 2 — user 域

| 项 | 内容 |
|----|------|
| **目标** | 登录/注册/OAuth/资料/头像上传迁入 `features/user/` |
| **范围** | `login.vue`、`register.vue`、`OAuthCallback.vue`、`DpAuthStage.vue`、`HomeProfileModal.vue`、`image_upload.vue`；`dpAuthEnterLobby.js`、`dpEnsureUserId.js`、`dpAvatarUrl.js`、`dpAvatarPrefetch.js`；`dpProfileGrayGlitchMixin.js` |
| **交付物** | `features/user/api/userApi.js`（`loginProfile`、`registerUser`、资料接口）；pages 替换 router 懒加载指向 |
| **验收 checklist** | ☐ 登录→token 写入→进大厅 ☐ 注册敏感词/重复名校验 ☐ OAuth callback ☐ 资料弹窗改昵称/头像 ☐ `/image_upload` 路由 ☐ compat re-export 有效 |
| **人天** | 2–3 |
| **风险** | OAuth 回调路径硬编码 → 保持 `/oauth/callback` 不变 |
| **依赖** | Wave 1 |

---

### Wave 3 — lobby 域（home.vue 拆壳）

| 项 | 内容 |
|----|------|
| **目标** | `home.vue` 拆为 `LobbyPage` + 子组件；收拢大厅 REST |
| **范围** | `home.vue`、`CreateRoom.vue`、`DpCreateRoomConsole.vue`、`LobbyRoomPasswordGate.vue`；`publicRooms`/`createRoom`/`joinRoom2` 等 `$http`；`dpLobbyEnterGame.js`、`dpCreateRoomSubmit.js`、`dpCreateRoomEnterGame.js`；`dpLobbyThemeMixin.js`；快匹 UI 组件暂留壳内 |
| **交付物** | `features/lobby/api/lobbyApi.js`；`features/lobby/pages/LobbyPage.vue` **< 800 行**；`features/quickmatch/` 预备目录（API/WS 占位） |
| **验收 checklist** | ☐ 公开房列表/搜索 ☐ 建房→进桌 ☐ 密码房 gate ☐ 从大厅进 `/game/:id` ☐ `/create-room` 路由 ☐ home 旧路径 compat |
| **人天** | 3–4 |
| **风险** | home 与社交 UI 交织 → 社交组件暂留 lobby 壳内，Wave 5 再迁 |
| **依赖** | Wave 2（需登录态） |

**Feature freeze（待负责人确认）**：Wave 3 启动后对 `home.vue` 新功能冻结 **2 周**，仅允许 bugfix。

---

### Wave 4 — room 域（game.vue 拆壳）★ 关键路径

| 项 | 内容 |
|----|------|
| **目标** | 对局页域化；`store/modules/dpGame.js` 迁入；WS/心跳/下注 REST 收拢 |
| **范围** | `game.vue`、全部 `Game*` 组件（除社交邀请/私信类）、`dpGame.js`、room mixins/utils、`api.dpRoom.js` 扩展；`GameButtonGuidePage.vue` |
| **交付物** | `features/room/pages/GamePage.vue` **< 800 行**；`roomApi.js` 覆盖 [DPGAME.md](../DPGAME.md) 全部前端用到的 POST；`room/websocket/gameRoomWs.js` |
| **验收 checklist** | ☐ 进房 WS 首包 ☐ 下注/弃牌/加注 ☐ 局后 toggleReady ☐ 房主踢人/开局 ☐ 观众席 ☐ HTTP heartbeat 与 WS 并存 ☐ 离房清理 ☐ `/guide` 按钮引导页 |
| **人天** | 7–9 |
| **风险** | 回归面最大 → **2–3 周** `game.vue` + `dpGame.js` feature freeze（待负责人确认）；建议双 Agent 串行（一人改、一人审） |
| **依赖** | Wave 3（进房路径）；[WEBSOCKET.md](../WEBSOCKET.md) 契约对照 |

**Feature freeze（待负责人确认）**：Wave 4 期间 NPC trace / 新 UI 特性延后到 Wave 6 或独立分支。

---

### Wave 5 — social 域 + SSE

| 项 | 内容 |
|----|------|
| **目标** | 好友/邮箱/私信/SSE 迁入 `features/social/`；快匹 WS 迁入 `features/quickmatch/` |
| **范围** | `DpMailboxConsole`、`FriendChatDialog`、`GameInvite*`、`GameFriendChat*`、`GamePlayerSocialSheet`；`api.dpSocial.js`；`dpSocialStream*`；`store/dpMailbox.js`；`QuickMatchPixelCritters.vue`；`dpQuickMatchExit.js`、`dpLobbyQuickMatchExit.js` |
| **交付物** | `socialApi.js`；`sse/socialStreamClient.js`；`quickmatch/websocket/`；大厅邮箱/快匹从 lobby 壳剥离 |
| **验收 checklist** | ☐ 好友申请/接受 ☐ 邮箱红点 ☐ SSE 连接/断线重连 ☐ 局内邀请好友 ☐ 私信收发 ☐ 快匹排队/匹配/取消 |
| **人天** | 3–4 |
| **风险** | SSE 与 Nginx 缓冲 → 引用 [NGINX.md](../NGINX.md) 配置 |
| **依赖** | Wave 3 大厅壳稳定；可与 Wave 4 **尾部并行**（不同文件集、不同 Agent） |

---

### Wave 6 — history / leaderboard / achievement / music / npc

| 项 | 内容 |
|----|------|
| **目标** | 剩余垂直域搬迁；NPC trace / mood UI 独立为 `features/npc/` |
| **范围** | `HandHistory*`、`LeaderboardPage`、`DpAchievement*`、`DpMusicPlayer`、`MusicUpload`、`GameNpcDecisionTrace*`、`CustomNpc*`、`DpOwnerNpcConsole`；`dpHandHistoryReplay.js`、`dpNpcDecisionTrace*.js`；`store/dpAchievement.js` |
| **交付物** | 各域 `api/` + `pages/`/`components/`；achievement store 迁入；admin 页（`DownloadCenter`、`DpTerminalCli`）→ `features/admin/` |
| **验收 checklist** | ☐ 牌谱列表/详情 ☐ 周榜页 ☐ 成就 toast / 成就墙 ☐ BGM 播放 ☐ NPC trace 面板 ☐ `/music-upload`、`/download-center` |
| **人天** | 2–3 |
| **风险** | 低；与 Wave 4 对局无硬耦合 |
| **依赖** | Wave 4 对局稳定 |

---

### Wave 7 — 清理兼容层 + 文档收口

| 项 | 内容 |
|----|------|
| **目标** | 删除 `compat/`；全局替换旧 import；更新文档索引 |
| **范围** | codemod 全仓 import；删除空 `components/`/`api/`/`utils/` 根目录（或保留 README 指向 features）；更新 [docs/README.md](../README.md)、`front/dp_game/docs/` |
| **交付物** | 无 compat 的干净树；域目录 README（每 feature 一句职责说明） |
| **验收 checklist** | ☐ 无 `compat/` 引用 ☐ grep `$http` 仅 `shared/http/` ☐ 全量冒烟 ☐ `npm run build` 通过 |
| **人天** | 2 |
| **风险** | 漏网 import → CI 加「禁止 import `@/components/` 业务组件」规则（dev 脚本） |
| **依赖** | Wave 1–6 全部完成 |

---

## 5. 目标目录结构

```text
front/dp_game/src/
├── app/                          # 壳：main、App.vue、router、全局 store 入口
│   ├── main.js
│   ├── App.vue
│   ├── router/
│   │   └── index.js              # 懒加载 → features/*/pages/
│   └── store/
│       └── index.js              # 聚合各 feature store
├── shared/                       # 跨域基础设施
│   ├── http/
│   │   ├── axios.js              # 从 main.js 抽出拦截器
│   │   └── result.js             # dpApiResult 等
│   ├── websocket/
│   │   └── dpWsClient.js         # 连接、重连、帧解析基类
│   ├── ui/                       # DpRetro* 通用壳、ThemePicker、Avatar
│   ├── styles/                   # 原 styles/ 迁入（token、主题）
│   └── constants/                # 仅真正跨域常量
├── features/
│   ├── user/                     # 对齐 user + oauth
│   │   ├── api/
│   │   ├── pages/                # LoginPage, RegisterPage, OAuthCallbackPage
│   │   ├── components/           # DpAuthStage, HomeProfileModal, ImageUpload
│   │   ├── mixins/
│   │   └── utils/                # dpAuthEnterLobby, dpEnsureUserId, dpAvatarUrl
│   ├── lobby/                    # 对齐 lobby
│   │   ├── api/
│   │   ├── pages/                # LobbyPage（原 home.vue 壳）
│   │   ├── components/           # DpCreateRoomConsole, CreateRoom, LobbyRoomPasswordGate
│   │   ├── mixins/
│   │   └── utils/                # dpLobbyEnterGame, dpCreateRoomSubmit
│   ├── quickmatch/               # 对齐 quickmatch
│   │   ├── api/
│   │   ├── websocket/
│   │   ├── components/           # QuickMatchPixelCritters
│   │   └── utils/                # dpQuickMatchExit, dpLobbyQuickMatchExit
│   ├── room/                     # 对齐 room + roomchat + websocket 消费
│   │   ├── api/                  # 扩展 api.dpRoom + 收拢 $http
│   │   ├── pages/                # GamePage（原 game.vue 壳）、ButtonGuidePage
│   │   ├── components/           # Game* 子组件、房主面板、行动面板
│   │   ├── store/                # 原 store/modules/dpGame.js
│   │   ├── websocket/            # 对局 WS 会话管理
│   │   ├── mixins/               # dpGame* mixins
│   │   ├── constants/
│   │   └── utils/                # 牌面、布局、心跳、指纹
│   ├── social/                   # 对齐 social + presence
│   │   ├── api/                  # 原 api.dpSocial.js
│   │   ├── components/           # DpMailboxConsole, FriendChat*, GameInvite*
│   │   ├── store/                # dpMailbox
│   │   ├── sse/                  # dpSocialStream*
│   │   └── utils/                # dpFriendPresence, dpCopySocialId
│   ├── history/                  # 对齐 history
│   │   ├── api/
│   │   ├── pages/                # HandHistoryPage, HandHistoryDetailPage
│   │   ├── components/           # DpHandHistory*
│   │   └── utils/                # dpHandHistoryReplay
│   ├── leaderboard/              # 对齐 leaderboard
│   │   ├── api/                  # api.dpLeaderboard
│   │   └── pages/
│   ├── achievement/              # 对齐 achievement
│   │   ├── store/                # dpAchievement
│   │   └── components/           # DpAchievement*
│   ├── music/                    # 对齐 music
│   │   ├── pages/                # MusicUploadPage
│   │   ├── components/           # DpMusicPlayer
│   │   └── utils/                # dpGameMusicUrl
│   ├── npc/                      # NPC 决策 trace / mood（对齐 npc）
│   │   ├── components/           # GameNpcDecisionTrace*, CustomNpc*
│   │   ├── constants/
│   │   └── utils/                # dpNpcDecisionTrace*
│   ├── presence/                 # 对齐 presence
│   │   └── utils/                # dpSiteHeartbeat
│   └── admin/                    # 运维/下载/终端（无后端域，前端工具）
│       ├── pages/                # DownloadCenterPage
│       └── components/           # DpTerminalCli, DownloadAdminPasswordGate
└── compat/                       # Wave 1–6 兼容 re-export（Wave 7 删除）
    ├── components/
    ├── api/
    └── utils/
```

**原则**：

- 新代码只写 `features/` 或 `shared/`。
- `compat/` 用一行 re-export 保持旧 import 可用，例如：

```js
// compat/components/game.vue
export { default } from '@/features/room/pages/GamePage.vue'
```

---

## 6. 主要文件 → 目标模块映射表

### 6.1 页面级 `.vue`（路由入口）

| 现路径 | 目标路径 | 域 |
|--------|----------|-----|
| `components/login.vue` | `features/user/pages/LoginPage.vue` | user |
| `components/register.vue` | `features/user/pages/RegisterPage.vue` | user |
| `components/OAuthCallback.vue` | `features/user/pages/OAuthCallbackPage.vue` | user |
| `components/home.vue` | `features/lobby/pages/LobbyPage.vue` | lobby |
| `components/CreateRoom.vue` | `features/lobby/pages/CreateRoomPage.vue` | lobby |
| `components/game.vue` | `features/room/pages/GamePage.vue` | room |
| `components/GameButtonGuidePage.vue` | `features/room/pages/ButtonGuidePage.vue` | room |
| `components/HandHistory.vue` | `features/history/pages/HandHistoryPage.vue` | history |
| `components/HandHistoryDetail.vue` | `features/history/pages/HandHistoryDetailPage.vue` | history |
| `components/LeaderboardPage.vue` | `features/leaderboard/pages/LeaderboardPage.vue` | leaderboard |
| `components/MusicUpload.vue` | `features/music/pages/MusicUploadPage.vue` | music |
| `components/DownloadCenter.vue` | `features/admin/pages/DownloadCenterPage.vue` | admin |
| `components/image_upload.vue` | `features/user/components/ImageUpload.vue` | user |

### 6.2 大厅 / 建房 / 用户组件

| 现路径 | 目标路径 | 域 |
|--------|----------|-----|
| `components/DpCreateRoomConsole.vue` | `features/lobby/components/DpCreateRoomConsole.vue` | lobby |
| `components/LobbyRoomPasswordGate.vue` | `features/lobby/components/LobbyRoomPasswordGate.vue` | lobby |
| `components/QuickMatchPixelCritters.vue` | `features/quickmatch/components/QuickMatchPixelCritters.vue` | quickmatch |
| `components/DpAuthStage.vue` | `features/user/components/DpAuthStage.vue` | user |
| `components/HomeProfileModal.vue` | `features/user/components/HomeProfileModal.vue` | user |

### 6.3 对局 `Game*` 组件 → `features/room/components/`

| 现路径 | 目标路径 |
|--------|----------|
| `components/GameActionPanel.vue` | `features/room/components/GameActionPanel.vue` |
| `components/GameTopBar.vue` | `features/room/components/GameTopBar.vue` |
| `components/GameRoundTable.vue` | `features/room/components/GameRoundTable.vue` |
| `components/GameCommunityCards.vue` | `features/room/components/GameCommunityCards.vue` |
| `components/GamePlayerCard.vue` | `features/room/components/GamePlayerCard.vue` |
| `components/GameRoomChatPanel.vue` | `features/room/components/GameRoomChatPanel.vue` |
| `components/GameRoomChatBar.vue` | `features/room/components/GameRoomChatBar.vue` |
| `components/GameOwnerPanel.vue` | `features/room/components/GameOwnerPanel.vue` |
| `components/GameOwnerHubPanel.vue` | `features/room/components/GameOwnerHubPanel.vue` |
| `components/GameOwnerHubContent.vue` | `features/room/components/GameOwnerHubContent.vue` |
| `components/GameOwnerToolModal.vue` | `features/room/components/GameOwnerToolModal.vue` |
| `components/GameOwnerTouchPanel.vue` | `features/room/components/GameOwnerTouchPanel.vue` |
| `components/GameDpFloatingModals.vue` | `features/room/components/GameDpFloatingModals.vue` |
| `components/GameDpGameSheets.vue` | `features/room/components/GameDpGameSheets.vue` |
| `components/GameHandHistoryModal.vue` | `features/room/components/GameHandHistoryModal.vue` |
| `components/GameMusicBoxModal.vue` | `features/room/components/GameMusicBoxModal.vue` |
| `components/GameSpectatorModal.vue` | `features/room/components/GameSpectatorModal.vue` |
| `components/GameWaitNextHandModal.vue` | `features/room/components/GameWaitNextHandModal.vue` |
| `components/GameDeckPresetDialog.vue` | `features/room/components/GameDeckPresetDialog.vue` |
| `components/GameDeckPresetPasswordGate.vue` | `features/room/components/GameDeckPresetPasswordGate.vue` |
| `components/GameHeroHandHologram.vue` | `features/room/components/GameHeroHandHologram.vue` |
| `components/GameHeroDockFooter.vue` | `features/room/components/GameHeroDockFooter.vue` |
| `components/GameSettledPrepareBar.vue` | `features/room/components/GameSettledPrepareBar.vue` |
| `components/GameTableActionTimer.vue` | `features/room/components/GameTableActionTimer.vue` |
| `components/GamePlayGuideModal.vue` | `features/room/components/GamePlayGuideModal.vue` |
| `components/GamePlayFlowContent.vue` | `features/room/components/GamePlayFlowContent.vue` |
| `components/GameHandRankModal.vue` | `features/room/components/GameHandRankModal.vue` |
| `components/GameBottomSheet.vue` | `features/room/components/GameBottomSheet.vue` |
| `components/ButtonGuideSpotlight.vue` | `features/room/components/ButtonGuideSpotlight.vue` |
| `components/DpTablePotDisplay.vue` | `features/room/components/DpTablePotDisplay.vue` |
| `components/CatTutorialDialog.vue` | `features/room/components/CatTutorialDialog.vue` |

### 6.4 社交组件 → `features/social/components/`

| 现路径 | 目标路径 |
|--------|----------|
| `components/DpMailboxConsole.vue` | `features/social/components/DpMailboxConsole.vue` |
| `components/FriendChatDialog.vue` | `features/social/components/FriendChatDialog.vue` |
| `components/GameInviteFriendPanel.vue` | `features/social/components/GameInviteFriendPanel.vue` |
| `components/GameInviteFriendSheet.vue` | `features/social/components/GameInviteFriendSheet.vue` |
| `components/GameInviteFriendContent.vue` | `features/social/components/GameInviteFriendContent.vue` |
| `components/GameFriendChatPanel.vue` | `features/social/components/GameFriendChatPanel.vue` |
| `components/GameFriendChatSheet.vue` | `features/social/components/GameFriendChatSheet.vue` |
| `components/GameFriendChatPickerContent.vue` | `features/social/components/GameFriendChatPickerContent.vue` |
| `components/GamePlayerSocialSheet.vue` | `features/social/components/GamePlayerSocialSheet.vue` |

### 6.5 牌谱 / NPC / 成就 / 共享 UI

| 现路径 | 目标路径 | 域 |
|--------|----------|-----|
| `components/DpHandHistoryViewer.vue` | `features/history/components/DpHandHistoryViewer.vue` | history |
| `components/DpHandHistoryDetail.vue` | `features/history/components/DpHandHistoryDetail.vue` | history |
| `components/GameNpcDecisionTrace*.vue` | `features/npc/components/` | npc |
| `components/GameNpcMoodSheet.vue` | `features/npc/components/GameNpcMoodSheet.vue` | npc |
| `components/CustomNpcStyleDialog.vue` | `features/npc/components/CustomNpcStyleDialog.vue` | npc |
| `components/DpCustomNpcConsole.vue` | `features/npc/components/DpCustomNpcConsole.vue` | npc |
| `components/DpOwnerNpcConsole.vue` | `features/npc/components/DpOwnerNpcConsole.vue` | npc |
| `components/DpAchievement*.vue` | `features/achievement/components/` | achievement |
| `components/DpMusicPlayer.vue` | `features/music/components/DpMusicPlayer.vue` | music |
| `components/DpRetro*.vue` | `shared/ui/retro/` | shared |
| `components/DpThemePicker.vue` | `shared/ui/DpThemePicker.vue` | shared |
| `components/DpUserAvatar.vue` | `shared/ui/DpUserAvatar.vue` | shared |
| `components/DpCrt*.vue` | `shared/ui/crt/` | shared |
| `components/DpFluidityToggle.vue` | `shared/ui/DpFluidityToggle.vue` | shared |
| `components/DpTerminalCli.vue` | `features/admin/components/DpTerminalCli.vue` | admin |
| `components/DownloadAdminPasswordGate.vue` | `features/admin/components/DownloadAdminPasswordGate.vue` | admin |

### 6.6 Store

| 现路径 | 目标路径 |
|--------|----------|
| `store/index.js` | `app/store/index.js` |
| `store/modules/dpGame.js` | `features/room/store/dpGame.js` |
| `store/modules/dpMailbox.js` | `features/social/store/dpMailbox.js` |
| `store/modules/dpAchievement.js` | `features/achievement/store/dpAchievement.js` |

### 6.7 API

| 现路径 | 目标路径 | 扩展动作 |
|--------|----------|----------|
| `api/api.dpRoom.js` | `features/room/api/roomApi.js` | 收拢 `game.vue` 内 31 处 `$http` |
| `api/api.dpSocial.js` | `features/social/api/socialApi.js` | 已较完整 |
| `api/api.dpLeaderboard.js` | `features/leaderboard/api/leaderboardApi.js` | — |
| （散落） | `features/user/api/userApi.js` | login/register/资料 |
| （散落） | `features/lobby/api/lobbyApi.js` | publicRooms/createRoom/join |
| （散落） | `features/quickmatch/api/quickMatchApi.js` | quickMatch2/cancel |
| （散落） | `features/history/api/historyApi.js` | 牌谱 CRUD |

### 6.8 Utils（按域归类，节选）

| 现路径 | 目标路径 | 域 |
|--------|----------|-----|
| `utils/dpAuthEnterLobby.js` | `features/user/utils/` | user |
| `utils/dpEnsureUserId.js` | `features/user/utils/` | user |
| `utils/dpAvatarUrl.js` | `features/user/utils/` | user |
| `utils/dpLobbyEnterGame.js` | `features/lobby/utils/` | lobby |
| `utils/dpCreateRoomSubmit.js` | `features/lobby/utils/` | lobby |
| `utils/dpCreateRoomEnterGame.js` | `features/lobby/utils/` | lobby |
| `utils/dpQuickMatchExit.js` | `features/quickmatch/utils/` | quickmatch |
| `utils/dpLobbyQuickMatchExit.js` | `features/quickmatch/utils/` | quickmatch |
| `utils/dpSocialStream.js` | `features/social/sse/` | social |
| `utils/dpSocialStreamClient.js` | `features/social/sse/` | social |
| `utils/dpFriendPresence.js` | `features/social/utils/` | social |
| `utils/dpCopySocialId.js` | `features/social/utils/` | social |
| `utils/dpGameHandRank.js` | `features/room/utils/` | room |
| `utils/dpGameCardVisual.js` | `features/room/utils/` | room |
| `utils/dpGameRoomFingerprint.js` | `features/room/utils/` | room |
| `utils/dpRoomPlayerLookup.js` | `features/room/utils/` | room |
| `utils/dpHandHistoryReplay.js` | `features/history/utils/` | history |
| `utils/dpNpcDecisionTrace*.js` | `features/npc/utils/` | npc |
| `utils/dpSiteHeartbeat.js` | `features/presence/utils/` | presence |
| `utils/dpApiResult.js` | `shared/http/result.js` | shared |
| `utils/dpBodyGameTheme.js` | `shared/styles/` | shared |
| `utils/dpOverlayPortal.js` | `shared/ui/` | shared |
| `utils/dpRouteTransition*.js` | `shared/` | shared |

### 6.9 Mixins / Constants / Styles

| 现路径 | 目标路径 |
|--------|----------|
| `mixins/dpGame*.js` | `features/room/mixins/` |
| `mixins/dpLobbyThemeMixin.js` | `features/lobby/mixins/` |
| `mixins/dpProfileGrayGlitchMixin.js` | `features/user/mixins/` |
| `constants/dpGame*.js` | `features/room/constants/` 或 `shared/constants/` |
| `constants/npcStylePresets.js` | `features/npc/constants/` |
| `constants/dpNpcDecisionTraceUi.js` | `features/npc/constants/` |
| `constants/guideUiSteps.js` | `features/room/constants/` |
| `styles/*` | `shared/styles/`（可按域分子目录） |

### 6.10 后端域 ↔ 前端 feature 对照

| 后端包 | 前端 feature | 说明 |
|--------|--------------|------|
| `user` | `features/user` | 登录、注册、资料 |
| `lobby` | `features/lobby` | 大厅列表、建房 |
| `quickmatch` | `features/quickmatch` | 快匹 WS/HTTP |
| `room` + `roomchat` | `features/room` | 对局页、局内聊天 |
| `social` | `features/social` | 好友、邮箱、SSE |
| `history` | `features/history` | 牌谱 |
| `leaderboard` | `features/leaderboard` | 周榜 |
| `achievement` | `features/achievement` | 成就 |
| `music` | `features/music` | BGM |
| `npc` | `features/npc` | NPC trace 展示 |
| `presence` | `features/presence` | 站点心跳 |
| `websocket` | `shared/websocket` + 各 feature 会话 | 基类 vs 域会话 |
| `security` | `shared/http` | JWT 拦截器 |
| — | `features/admin` | 纯前端运维页 |

---

## 7. Agent 执行约束

> **本节为硬性约束。违反即视为 Wave 未完成。**

### 7.1 禁止 Git Commit

- Agent **不得**执行 `git add`、`git commit`、`git push` 或任何写入 Git 历史的操作。
- 变更仅保留在工作区；由**人类负责人**审阅后自行提交。
- 若需记录进度，在 PR 描述或 Wave checklist 中注明，**不**自行开 commit。

### 7.2 单 Wave 边界

- 一次 Agent 任务 **只执行一个 Wave**（或 PM 明确指定的子集）。
- **不得**跨 Wave 提前删 `compat/` 或全局改 import（Wave 7 专属）。
- Wave 4 期间 **禁止**两个 Agent 同时改 `game.vue` / `dpGame.js`。

### 7.3 行为不变原则

- 除 import 路径与目录搬迁外，**不得**改业务逻辑、API 路径、WS 帧格式。
- 每 Wave 结束前必须：`cd front/dp_game && npm run build`。
- 冒烟路径：登录 → 大厅 → 建房/进房 → 下注 → 结算（Wave 4+ 必跑）。

### 7.4 兼容层

- Wave 1–6：凡搬迁文件，**必须**在 `compat/` 留 re-export。
- 新功能代码只写入 `features/` 或 `shared/`，**禁止**往旧 `components/` 根目录新增业务文件。

### 7.5 文档与沟通

- 完成 Wave 后输出：**改动文件列表**、**未搬文件清单**、**build 结果**、**冒烟 checklist 勾选**。
- 发现映射表错误或遗漏：更新本文档 §6，**不** silent 改域边界。

### 7.6 Feature Freeze 遵守

- Wave 3 期间不往 `home.vue` 加新功能（bugfix 除外）。
- Wave 4 期间不往 `game.vue` / `dpGame.js` 加新功能（bugfix 除外）。

---

## 8. PM 派工速查

| 指令模板 | Wave |
|----------|------|
| 「执行 Wave 1：盘点 $http + 搭 features/compat 脚手架」 | 1 |
| 「搬迁 user 域并收拢登录 API」 | 2 |
| 「拆 home.vue 为 LobbyPage，收拢大厅 API」 | 3 |
| 「拆 game.vue，迁入 room store 与 WS」 | 4 |
| 「搬迁 social/SSE + quickmatch，剥离大厅社交」 | 5 |
| 「搬迁 history/leaderboard/achievement/music/npc」 | 6 |
| 「删除 compat，全局改 import，更新文档」 | 7 |

**单 Wave 开工前检查**：上一 Wave checklist 已勾选 · feature freeze 是否生效 · `npm run build` 基线是否绿。

---

## 9. 风险登记册

| ID | 风险 | 影响 | 缓解 |
|----|------|------|------|
| R1 | `game.vue` 拆分校验不足 | 高 — 对局回归 | 2–3 周 freeze；Wave 4 专用冒烟 |
| R2 | compat 与 features 双份真相 | 中 — 改漏 | Wave 7 强制删除；禁止新 compat import |
| R3 | 社交与大厅耦合 | 中 — Wave 3/5 扯皮 | Wave 3 只迁壳；社交 Wave 5 再切 |
| R4 | webpack alias 遗漏 | 中 — build 失败 | Wave 1 预置 `@features` |
| R5 | `$http` 漏收拢 | 中 — API 仍散落 | Wave 1 矩阵 + 每 Wave grep 复核 |

---

## 10. 参考文档

| 文档 | 用途 |
|------|------|
| [CLAUDE.md](../../CLAUDE.md) | 后端功能域包结构 |
| [DPGAME.md](../DPGAME.md) | 房间 REST、生命周期 |
| [WEBSOCKET.md](../WEBSOCKET.md) | 对局 WS |
| [JWT.md](../JWT.md) | 鉴权、axios 拦截 |
| [dp-quick-match-flow.md](../dp-quick-match-flow.md) | 快匹 HTTP + WS |
| [dp_friend_mailbox_mvp.md](../dp_friend_mailbox_mvp.md) | 好友/邮箱/SSE |
| [refactor/oauth2-client-migration-plan.md](./oauth2-client-migration-plan.md) | OAuth 与 user 域 |
| [refactor/room-mutation-side-effects.md](./room-mutation-side-effects.md) | 房间字段副作用 |

---

*文档版本：v1.0 · 创建日期：2026-06-15 · 维护：前端模块化负责人 + PM*
