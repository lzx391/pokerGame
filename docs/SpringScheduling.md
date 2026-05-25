# Spring 定时任务与房间内核周期逻辑

> **核对日期**：2026-05-25  
> **权威来源**：`MgDemoPlusApplication`（`@EnableScheduling`）、`lobby/DpRoomLobbyReconcileScheduler`、`room/impl/DpRoomServiceImpl`、`leaderboard/DpLeaderboardWeeklyRedisSyncScheduler`、`application.yml`  
> **Status**: maintained

---

## 1. Spring `@Scheduled`（全仓库仅 3 处方法）

| 类 | 路径 | 方法 | 配置键 | 默认 |
|----|------|------|--------|------|
| `DpRoomLobbyReconcileScheduler` | `lobby/DpRoomLobbyReconcileScheduler.java` | `scheduledReconcile` | `mgdemoplus.dp-lobby-reconcile-ms` | 60000（`fixedDelay` + 同值 `initialDelay`） |
| 同上 | 同上 | `ApplicationRunner#run` 启动即 `reconcile("startup")` | `mgdemoplus.dp-lobby-reconcile-enabled` | `true`（`matchIfMissing`） |
| `DpRoomServiceImpl` | `room/impl/DpRoomServiceImpl.java` | `scheduledPruneDefaultQuickMatchQueue` | `mgdemoplus.dp-quick-match-prune-ms` | 30000 |
| `DpLeaderboardWeeklyRedisSyncScheduler` | `leaderboard/...` | `syncCurrentWeekToRedis` | `mgdemoplus.leaderboard.sync-enabled`、`sync-interval-ms` | true / 60000 |

大厅对齐调用链：`reconcile` → `DpRoomHallService#reconcileLobbyWithRuntimeRoomIds(dpRoomService.getRoomIdsInMemory())`，删除 **DB 有、本机 `roomMap` 无** 的幽灵行。

**多实例**：各节点 `roomMap` 不一致时，应设 `mgdemoplus.dp-lobby-reconcile-enabled=false`，否则会误删其他节点仍在线房间对应的大厅行。见 [WEBSOCKET.md](./WEBSOCKET.md) § 单机模型。

---

## 2. 非 Spring 周期任务（对局热路径）

| 机制 | 类 | 间隔 | 作用 |
|------|-----|------|------|
| `java.util.Timer` | `DpRoomHeartbeatScheduler`（`room/support/`） | **1s** | 心跳超时、`exitRoom`、NPC/LLM  tick、空房摘除、`broadcastIfSubscribed` |
| `ScheduledExecutorService` | `SocialSseHub` | 30s | SSE 连接心跳 |

房间 **1 秒** 逻辑已从 `DpRoomServiceImpl` 构造器迁至 `DpRoomHeartbeatScheduler`，与 `@Scheduled` 分离。

---

## 3. 新增 `@Scheduled` 检查清单

1. 启动类已有 `@EnableScheduling`（`MgDemoPlusApplication`）。  
2. 独立 `@Component`，构造器注入 Service。  
3. 长任务用 `fixedDelayString`，避免重叠。  
4. 开关/间隔写入 `application.yml` 的 `mgdemoplus.*`。  
5. 多实例全局单跑需分布式锁（本仓库未内置）。

---

## 4. 延伸阅读

- 快匹队列修剪：[dp-quick-match-flow.md](./dp-quick-match-flow.md)  
- 房间副作用：[refactor/room-mutation-side-effects.md](./refactor/room-mutation-side-effects.md)  
- Spring 官方：[Task Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
