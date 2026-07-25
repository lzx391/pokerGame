# 前端模块化完成总结（给用户）

> Wave 1–8 收口 · 2026-06-15

## 一句话

业务代码已按功能域拆到 `src/features/` 与 `src/shared/`；**旧 `@/components`、`@/utils` 等路径已移除**，新代码必须使用 `@features/...` 或 `@shared/...`。

---

## 模块在哪

| 功能 | 目录 | 典型页面/组件 |
|------|------|----------------|
| 登录注册 | `features/user/` | `LoginPage`、`RegisterPage`、`DpAuthStage` |
| 大厅建房 | `features/lobby/` | `LobbyPage`（原 home）、`CreateRoomPage` |
| 快速匹配 | `features/quickmatch/` | `QuickMatchPixelCritters`、退出工具 |
| 对局 | `features/room/` | `GamePage`（原 game）、全部 `Game*` 子组件、`dpGame` store |
| 好友邮箱 | `features/social/` | `DpMailboxConsole`、邀请/私信组件、`dpMailbox` store |
| 牌谱 | `features/history/` | `HandHistoryPage`、`DpHandHistoryViewer` |
| 周榜 | `features/leaderboard/` | `LeaderboardPage` |
| 成就 | `features/achievement/` | `DpAchievementToast`、`DpAchievementWallModal` |
| BGM | `features/music/` | `DpMusicPlayer`、`MusicUploadPage` |
| NPC 调试 | `features/npc/` | `GameNpcDecisionTrace*` 面板、自定义 NPC 控制台 |
| 下载中心 | `features/download/` | `DownloadCenterPage`、`DpTerminalCli` |
| 在线心跳 | `features/presence/` | `dpSiteHeartbeat` |
| 横切 Retro/CRT UI | `shared/components/` | `DpThemePicker`、`DpCrtBootSequence`、`DpRetroTableFx` 等 |
| 横切工具 | `shared/utils/` | `dpRouteTransition`、`dpOverlayPortal`、`dpDisplayNickname` |
| 横切文案常量 | `shared/constants/` | `dpCatThemeCopy` |
| 公共 HTTP | `shared/api/`、`shared/utils/dpApiResult` | axios 封装、统一 Result 解析 |

路由仍从 `src/router/index.js` 加载；入口均指向 `features/*/pages/`。

---

## 旧 import 还能用吗？

**不能。** Wave 8 已删除全部 compat re-export 空壳：

- ~~`@/components/game.vue`~~ → `@features/room/pages/GamePage.vue`
- ~~`@/utils/dpApiResult`~~ → `@shared/utils/dpApiResult`
- ~~`@/store/modules/dpGame`~~ → `@features/room/store/dpGame`

空目录 `src/components/`、`src/utils/`、`src/constants/`、`src/mixins/`、`src/api/`、`src/store/modules/` 仅保留 README 说明已废弃。

---

## 新代码怎么写

```js
// 推荐
import GamePage from '@features/room/pages/GamePage.vue'
import { joinRoom2 } from '@features/lobby/api/lobbyApi.js'
import { CAT_COPY } from '@shared/constants/dpCatThemeCopy'
import { dpResultSuccess } from '@shared/utils/dpApiResult'

// 禁止（Wave 8 后无 compat 层）
import game from '@/components/game.vue'
```

---

## Wave 8 交付摘要

| 项 | 数量 |
|----|------|
| 迁入 `shared/` / `features/` 的真实文件 | 47（17 组件 + 3 常量 + 27 utils） |
| 删除 compat 空壳 | 174 |
| 旧目录剩余源码文件 | 0（仅 README） |

---

## 验收

- `npm run build` 通过（exit 0）
- 冒烟：登录 → 大厅 → 建房/进房 → 下注 → 结算

波次计划：`docs/refactor/frontend-modularization-wave-plan.md`
