# 建议版 `docs/README.md` 专题索引表（供合并轮粘贴）

> **核对日期**：2026-05-25  
> **权威来源**：`doc-overhaul-plan.md` + Agent4 磁盘核验  
> **Status**: draft — **勿**直接替代现网 `docs/README.md` 除非用户/Agent1 确认

将下列各节表格**替换** `docs/README.md` 中对应「待填充」段落后，删除本 draft 或标为 archived。

---

## 环境与配置

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [CONFIG_LAYERS.md](../CONFIG_LAYERS.md) | `application.yml` 分层、占位符与 `dp.*` 前缀地图 | 2 |
| [refactor/config-inventory.md](config-inventory.md) | 配置键 ↔ 环境变量 ↔ Java 绑定类对照表 | 2 |
| [ENV_README.md](../ENV_README.md) | `.env` / `LocalDotenvLoader`、Compose 注入、ARK/JWT 等 | 2 |

---

## 部署与网关

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [DOCKER.md](../DOCKER.md) | `docker-compose.yml` / Hub 镜像、端口与依赖服务 | 2 |
| [NGINX.md](../NGINX.md) | `docker/nginx/default.conf`、SSE `proxy_buffering off` | 2 |
| [监控-Grafana使用说明.md](../监控-Grafana使用说明.md) | Prometheus/Grafana Compose 服务与面板入口 | 2 |

---

## 安全与用户

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [JWT.md](../JWT.md) | `SecurityConfig`、白名单、`JwtTokenService`、WS 验签分工 | 2 |
| [DpUserPassword.md](../DpUserPassword.md) | 注册登录、**bcrypt**（`CryptoUtil.bcrypt*`） | 2 |

---

## DP 游戏：规则、接口与房间

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [DPGAME.md](../DPGAME.md) | 房间生命周期、下注/结算、`roomMap`、REST 建房与 Bot | 3 |
| [RoomUi.md](../RoomUi.md) | 对局页 `seatIndex`、前端与 `getNowRoom` 对齐 | 3 |
| [WEBSOCKET.md](../WEBSOCKET.md) | `/ws/dp-game`、`/ws/dp-quick-match`、推送与握手 | 3 |
| [dp-quick-match-flow.md](../dp-quick-match-flow.md) | `quickMatch2`、FIFO、配对与 WS 状态机 | 3 |
| [dp-quick-match-concurrency.md](../dp-quick-match-concurrency.md) | `dpQuickMatchAssignmentLock`、`JoinableQuickMatchRoomIndex` | 3 |
| [dp_friend_mailbox_mvp.md](../dp_friend_mailbox_mvp.md) | 好友申请/私信/mailbox/SSE/跟随进房 | 3 |
| [refactor/room-mutation-side-effects.md](room-mutation-side-effects.md) | 房间写路径副作用矩阵（大厅/快匹/索引） | 3 |

---

## 数据持久化与牌谱

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [DP_PERSISTENCE_README.md](../DP_PERSISTENCE_README.md) | 牌谱观测、`DpHandHistoryPersistServiceImpl`、Flyway V1/V4 | 3 |

---

## Redis

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [Redis.md](../Redis.md) | 登录 JTI、大厅分页缓存、曲库列表；**非** `roomMap` | 2 |

---

## 曲库

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [DpMusicWebPath.md](../DpMusicWebPath.md) | `DpMusicController`、`mgdemoplus.music.file-location` | 2 |

---

## AI / NPC

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [ai/npc-engine/README.md](../ai/npc-engine/README.md) | 规则 Bot 分册导航、`dp.npc.*`、前缀与 LLM 分流 | 4 |
| [ai/npc-preflop-unified-decision-flow.md](../ai/npc-preflop-unified-decision-flow.md) | `DpNpcUnifiedPreflopStrategy`：`Spot` / `rangeLevel` / 13×13 | 4 |
| [ai/npc-engine/01_overview_and_entry.md](../ai/npc-engine/01_overview_and_entry.md) | 心跳 → `decideActionIfReady` → `decideBotAction` | 4 |
| [ai/npc-engine/02_normal_npc_implementation.md](../ai/npc-engine/02_normal_npc_implementation.md) | 翻后 `FISH`…`MANIAC` 分派 | 4 |
| [ai/npc-engine/03_normal_npc_modules.md](../ai/npc-engine/03_normal_npc_modules.md) | 牌力、`StyleProfile`、**rule-think**、桌边话 | 4 |
| [ai/npc-engine/04_shark_legacy.md](../ai/npc-engine/04_shark_legacy.md) | 遗留 `BOT_Shark` → **TAG**（REST 兼容） | 4 |
| [ai/npc-engine/05_shark_modules_legacy.md](../ai/npc-engine/05_shark_modules_legacy.md) | `dp_shark_opponent_profile` 表留存说明 | 4 |
| [ai/npc-llm/part01_类间关系以及调用.md](../ai/npc-llm/part01_类间关系以及调用.md) | `BOT_LLM` / `BOT_LLM_GLOBAL` 异步方舟链 | 4 |

---

## 学习备忘（非必读）

| 文档 | 说明 |
|------|------|
| [notes/README.md](../notes/README.md) | 学习类索引（6 篇） |

---

## 调度与专题（维护向）

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [SpringScheduling.md](../SpringScheduling.md) | `@Scheduled`、大厅对齐 `DpRoomLobbyReconcileScheduler` | 2 |

---

## 治理与协作（Agent 专用）

| 文档 | 说明 |
|------|------|
| [refactor/doc-overhaul-plan.md](doc-overhaul-plan.md) | 总计划 |
| [refactor/doc-overhaul-changelog-01.md](doc-overhaul-changelog-01.md) | Agent1 删除记录 |
| [refactor/doc-overhaul-changelog-04.md](doc-overhaul-changelog-04.md) | Agent4 删除/迁移记录 |

**待 Agent3 删除（合并前勿从表移除链接）**：`refactor/friend-dm-room-chat-sse-plan.md`

---

## 给代码评审速查（与根 README 表一致）

| 项 | 值 |
|----|-----|
| Flyway | **V1–V7**（`V7__leaderboard_weekly.sql`） |
| `@MapperScan` | `common`, `history`, `lobby`, `music`, `roomchat`, `social`, `user`, **`leaderboard`** |
| 无 Mapper 热路径 | `room`, `npc`, `quickmatch`, `websocket` |
| REST 控制器 | 8：`DpRoom`, `DpUser`, `DpFriendMailbox`, `DpSocial`, `DpHandHistory`, `DpMusic`, **`DpLeaderboard`**, `Upload` |
| 周榜（permitAll 读） | `GET /dp/leaderboard/weekly/hand`, `GET /dp/leaderboard/weekly/room` |
