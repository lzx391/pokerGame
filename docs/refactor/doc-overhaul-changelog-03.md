# 文档治理变更记录 · Agent3（对局 · 实时 · 社交）

> **执行日期**：2026-05-25  
> **范围**：对局流程、WebSocket、快匹、好友/私信/SSE、牌谱持久化、JWT/密码（bcrypt 对齐）、调度说明、根 README 对局/社交节  
> **禁止**：本任务内未执行 `git commit` / `git push`

---

## 1. 重写（`rewrite`，文首 §1 三块 + 实现映射）

| 文档 | 权威来源（Source of truth） |
|------|---------------------------|
| [docs/DPGAME.md](../DPGAME.md) | `DpRoomServiceImpl`、`DpRoomRegistry`、`DpRoomController` |
| [docs/WEBSOCKET.md](../WEBSOCKET.md) | `DpGameRoomWebSocketHandler`、`DpQuickMatchWebSocketHandler`、`DpGameRoomPushService`、`DpRoomHeartbeatScheduler` |
| [docs/RoomUi.md](../RoomUi.md) | `front/dp_game` 对局组件 |
| [docs/DpMusicWebPath.md](../DpMusicWebPath.md) | `DpMusicController`、`WebConfig` |
| [docs/dp-quick-match-flow.md](../dp-quick-match-flow.md) | `DpQuickMatchPairingCoordinator`、`DpRoomQuickMatchBridge` |
| [docs/dp-quick-match-concurrency.md](../dp-quick-match-concurrency.md) | 同上 + `JoinableQuickMatchRoomIndex` |
| [docs/dp_friend_mailbox_mvp.md](../dp_friend_mailbox_mvp.md) | `DpFriendMailboxController`、`DpFriendSocialService`、`SocialSseHub` |
| [docs/DP_PERSISTENCE_README.md](../DP_PERSISTENCE_README.md) | `DpHandHistoryPersistServiceImpl` |
| [docs/JWT.md](../JWT.md) | `JwtSecurityConstants`、`SecurityConfig` |
| [docs/DpUserPassword.md](../DpUserPassword.md) | `DpUserServiceImpl`、`CryptoUtil.bcrypt*` |
| [docs/SpringScheduling.md](../SpringScheduling.md) | `lobby/DpRoomLobbyReconcileScheduler` 等 |
| [docs/refactor/room-mutation-side-effects.md](./room-mutation-side-effects.md) | 文首更新；正文保留矩阵 |

---

## 2. 合并后删除

| 路径 | 并入 |
|------|------|
| `docs/refactor/friend-dm-room-chat-sse-plan.md` | `docs/dp_friend_mailbox_mvp.md`（私信、局内聊天、SSE、Flyway V2/V3） |

---

## 3. 已删或 Agent1 已删（本 Agent 复核无残留链接）

| 路径 | 说明 |
|------|------|
| `docs/business-flow/*`（5） | 一次性 audit；仓库内无入链；目录已空/已移除 |
| `docs/readme-scan/ARCHITECTURE.md` | 内容吸收至根 `README.md` 包结构表 |
| `docs/readme-scan/services.md` | 吸收至 `DPGAME.md` REST/服务映射 |
| `docs/readme-scan/controllers.md` | 吸收至 `DPGAME.md` |
| `docs/readme-scan/social-friend.md` | 吸收至 `dp_friend_mailbox_mvp.md` |
| `docs/readme-scan/frontend-brief.md` | 吸收至根 `README.md` 前端节 + `RoomUi.md` |

---

## 4. 根 README 调整（Agent3 节）

| 文件 | 变更 |
|------|------|
| `README.md` | 已无 `readme-scan` / `friend-dm-room-chat-sse-plan` 链（复核通过） |
| `README.ch.md` | 密码改为 **bcrypt**；WS 推送改为 `DpRoomHeartbeatScheduler` |
| `README.en.md` | 同上 |

**未改**：`docs/README.md` 索引正文表（按计划由 Agent2 填表）。

---

## 5. 实现一致性要点（验收贡献）

- [x] **单机内存**：`DpRoomRegistry.roomMap`、WS `roomSessions` 不写 Redis  
- [x] **多实例**：`mgdemoplus.dp-lobby-reconcile-enabled` 风险已写入 `WEBSOCKET.md` / `SpringScheduling.md`  
- [x] **密码**：`JWT.md`、`DpUserPassword.md`、长 README 社交相关节 — **bcrypt 为主路径**，移除 MD5 主流程描述  
- [x] **调度器路径**：`DpRoomLobbyReconcileScheduler` 在 **`lobby/`**（非 `scheduler/`）  
- [x] **快匹**：`dpQuickMatchAssignmentLock` + 协调器 `pairingDepth` 与专题文一致  
- [x] **JWT 白名单**：`publicRooms` 需 JWT；`getNowRoom` / `getAllRooms2` / `/ws/**` / 周榜 GET 为 permitAll  

---

## 6. 移除的过时内容

- `WEBSOCKET.md` 文末「自己的笔记」草稿块（实现已文档化于 §14）  
- `DPGAME.md` 旧版长篇叙事笔记（替换为维护向结构文档；历史见 git）  

---

## 7. 交叉引用（与 Agent2）

- `ENV_README.md`、`DOCKER.md`：本 Agent 未改正文；`JWT.md` / `WEBSOCKET.md` / `DPGAME.md` 与其无矛盾表述  
- Flyway **V7** 周榜：在 `DP_PERSISTENCE_README.md`、`JWT.md` 白名单表提及  

---

*Agent3 完成 · 对照 `doc-overhaul-plan.md` §2 Agent3 行*
