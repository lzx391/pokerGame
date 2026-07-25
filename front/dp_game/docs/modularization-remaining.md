# 模块化剩余项（Wave 7 盘点）

> 生成基准：`src/components/` 扫描 · 2026-06-15  
> 原则：旧路径 **re-export 兼容层保留**，新代码写入 `features/` 或 `shared/`。

---

## 1. `src/components/` — 仍含真实实现的 `.vue`（17 个）

### 1.1 横切 UI → 目标 `shared/components/`（12）

| 文件 | 用途 | 主要引用方 |
|------|------|------------|
| `DpCrtBootSequence.vue` | CRT 开机序列动画 | `LobbyPage`、`CreateRoomPage`、`GamePage` |
| `DpCrtEventPopup.vue` | CRT 事件弹层 | 多处 Retro 壳 |
| `DpCrtFullscreenOverlay.vue` | 全屏 CRT 遮罩 | 路由过渡 / 进房 |
| `DpFluidityToggle.vue` | 流畅度开关 | `CreateRoomPage` 等 |
| `DpRetroAmbientOverlay.vue` | 环境光效层 | 大厅 / 对局 |
| `DpRetroConfirmDialog.vue` | Retro 确认框 | `DownloadCenterPage` 等 |
| `DpRetroGlitchMonster.vue` | Glitch 怪物装饰 | 主题 / 特效 |
| `DpRetroPasswordGateShell.vue` | 密码门壳 | 建房 / 下载 gate |
| `DpRetroStackLeaderTicker.vue` | 栈顶滚动条 | 大厅 |
| `DpRetroTableFx.vue` | 牌桌特效 | 对局 |
| `DpRetroTv.vue` | Retro 电视框 | 成就墙等 |
| `DpThemePicker.vue` | 全局主题选择 | `main.js` 全局注册 |

### 1.2 NPC 域 → 目标 `features/npc/components/`（4）

| 文件 | 用途 |
|------|------|
| `CustomNpcStyleDialog.vue` | 自定义 NPC 风格弹窗 |
| `DpCustomNpcConsole.vue` | 自定义 NPC 控制台 |
| `DpOwnerNpcConsole.vue` | 房主 NPC 控制台 |
| `GameNpcMoodSheet.vue` | NPC 情绪面板（`GameDpGameSheets` 引用） |

### 1.3 下载 / 运维 → 目标 `features/download/components/`（1）

| 文件 | 用途 |
|------|------|
| `DpTerminalCli.vue` | 局内终端 CLI（`GamePage` 引用） |

---

## 2. `src/components/` — 已是 re-export 薄层（72 个）

含全部路由页（`login.vue`、`home.vue`、`game.vue`…）、对局 `Game*` 子组件、社交/牌谱/成就/音乐等已迁域组件。  
**Wave 7 未删除这些兼容层**，旧 `@/components/...` import 仍可用。

本波已补齐 re-export 的 NPC trace（5 个）：

- `GameNpcDecisionTracePanel.vue` → `@features/npc/components/...`
- `GameNpcDecisionTraceActionGrid.vue`
- `GameNpcDecisionTraceBody.vue`
- `GameNpcDecisionTraceDock.vue`
- `GameNpcDecisionTraceMatrixGrid.vue`

---

## 3. 其他旧目录剩余真实文件

### `src/api/`（1 个真实 + 2 个 re-export）

| 文件 | 状态 | 目标 |
|------|------|------|
| `api.dpRoom.js` | re-export | `features/room/api/roomApi.js` |
| `api.dpSocial.js` | re-export | `features/social/api/socialApi.js` |
| `api.dpLeaderboard.js` | **真实** | `features/leaderboard/api/leaderboardApi.js` |

### `src/utils/`（约 35 个真实 + 20 个 re-export）

仍含大量横切工具（`dpRetroBootLines.js`、`dpRouteTransition.js`、`dpOverlayPortal.js`…）及未收拢的 NPC/Retro 工具。  
已 re-export 的示例：`dpCopySocialId.js`、`dpFriendPresence.js`、`dpHandHistoryReplay.js` 等。

### `src/constants/`（2 个真实 + 4 个 re-export）

| 真实文件 | 建议目标 |
|----------|----------|
| `dpCatThemeCopy.js` | `shared/constants/` 或 `features/room/constants/` |
| `npcStylePresets.js` | `features/npc/constants/` |

### `src/mixins/`、`src/store/modules/`

**全部已为 re-export**（指向 `features/*/mixins` 与 `features/*/store`）。

---

## 4. 后续建议（非 Wave 7 范围）

1. 将 §1.1 横切 UI 迁入 `shared/components/`，旧路径留一行 re-export。
2. 将 §1.2–§1.3 迁入对应 `features/*/components/`。
3. 收拢 `src/utils/` 散落文件至域内或 `shared/`。
4. 新代码禁止再写入 `src/components/` 根目录（仅 compat re-export）。
