# 扫描：Service / 对局·大厅·快匹·WebSocket

> 扫描日期：2026-05-22 · 范围：对局/大厅/快匹/WS · 覆盖重写

## 概览

| 存储 | 内容 |
|------|------|
| **JVM 内存** | `roomMap`（`DpRoomRegistry`）、快匹 FIFO 队列、`JoinableQuickMatchRoomIndex`、对局/快匹 WS 会话表 |
| **MySQL** | `dp_room_lobby` 大厅摘要、`dp_room_chat_message` 聊天归档 |
| **Redis** | 大厅公开房列表/筛选分页缓存（rev + TTL） |

对局进行中的一切权威状态在 **当前进程** 的 `roomMap`；MySQL 仅作大厅展示与持久化辅助，**不**参与实时下注/发牌。

---

## `room/` — `DpRoomService` / `DpRoomServiceImpl`

### 接口与实现

| 类型 | 路径 |
|------|------|
| API | [`DpRoomService`](src/main/java/com/example/mgdemoplus/room/DpRoomService.java) |
| 实现 | [`DpRoomServiceImpl`](src/main/java/com/example/mgdemoplus/room/impl/DpRoomServiceImpl.java) |

Controller **禁止**自建 `roomMap`；所有读写经 Service。

### `roomMap` 与注册表

```text
DpRoomRegistry.roomMap  ←──  ConcurrentHashMap<String, DpRoomBO>
         ↑
DpRoomServiceImpl.roomMap（同一引用，测试用）
```

- **创建**：`createRoom` → `roomMap.put` → 刷新快匹索引 + `dpRoomHallService.upsertRoomSummary`。
- **销毁**：无活人/无观众时 `tryUnregisterEmptyRoomAssumeLocked` → `DpRoomLobbySync.finalizeHallAfterRoomRemoved`（flush 聊天、删大厅行、关 WS 订阅）。

### 锁与并发要点

| 锁/对象 | 用途 |
|---------|------|
| `synchronized(DpRoomBO r)` | 单房进退座、准备、下注、踢人、快匹 join 等写操作 |
| `defaultQmLock`（`DpRoomQuickMatchBridge`） | 默认快匹 FIFO `ArrayDeque<DpQuickMatchWaitEntry>` |
| `dpQuickMatchAssignmentLock`（`ReentrantLock`） | 包裹 `DpQuickMatchPairingCoordinator.attemptPairing()`，避免并发配对 |
| `JoinableQuickMatchRoomIndex.indexLock` | 索引桶读写；**禁止**在持 `synchronized(r)` 时再抢 `indexLock` |
| 协调器约定 | 同路径既要房锁又要队列锁时：**先房锁、后队列锁**（见 `DpQuickMatchPairingCoordinator` 类注释） |

进房写操作 `joinRoomMutateAssumeLocked` 要求调用方已持 `synchronized(room)`；释放房锁后再 `refreshJoinableQuickMatchIndexRoom` / 大厅 sync，避免死锁。

### `support/` 分工

| 类 | 职责 |
|----|------|
| [`DpRoomRegistry`](src/main/java/com/example/mgdemoplus/room/support/DpRoomRegistry.java) | `roomMap` CRUD、空房摘除 |
| [`DpRoomLobbySync`](src/main/java/com/example/mgdemoplus/room/support/DpRoomLobbySync.java) | 快匹索引维护、`upsertRoomSummary`、摘房收尾 |
| [`DpRoomQuickMatchBridge`](src/main/java/com/example/mgdemoplus/room/support/DpRoomQuickMatchBridge.java) | 队列、扫房 join、`quickMatchJoinQueueOrImmediate`、配对入口 |
| [`DpRoomQuickMatchPairingHost`](src/main/java/com/example/mgdemoplus/room/support/DpRoomQuickMatchPairingHost.java) | 实现 `DpQuickMatchPairingHost`，供协调器回调建房/join |
| [`DpRoomServiceCallbacks`](src/main/java/com/example/mgdemoplus/room/support/DpRoomServiceCallbacks.java) | 解耦 ServiceImpl 与 Bridge 的回调面 |
| [`DpRoomHeartbeatScheduler`](src/main/java/com/example/mgdemoplus/room/support/DpRoomHeartbeatScheduler.java) | 全局 1s tick：心跳踢人、空房、NPC/人类超时 |
| [`DpRoomSnapshotSupport`](src/main/java/com/example/mgdemoplus/room/support/DpRoomSnapshotSupport.java) | 观战者可见性裁剪快照 |
| [`DpRoomHumanCounts`](src/main/java/com/example/mgdemoplus/room/support/DpRoomHumanCounts.java) | 真人/观众计数 |

### 定时任务（本类）

| 方法 | 配置 | 行为 |
|------|------|------|
| `scheduledPruneDefaultQuickMatchQueue` | `mgdemoplus.dp-quick-match-prune-ms`（默认 30s） | 队列超时 3min 或已在任意房内 → 移除并 WS 通知 |

全局房间 tick 由 `DpRoomHeartbeatScheduler` 的 `Timer`（1s）驱动，非 Spring `@Scheduled`。

### 单实例与多实例

- **设计假设**：一台 JVM 持有完整 `roomMap` 与 WS 连接表。
- **多实例**：房间状态不共享；负载均衡无粘滞会导致「进房 404」、快匹配对分裂、大厅 reconcile **误删** 他节点仍在线的 `dp_room_lobby` 行。
- **建议**：单副本或会话粘滞；`mgdemoplus.dp-lobby-reconcile-enabled=false`。

---

## `lobby/` — `DpRoomHallService`

### 接口与实现

| 类型 | 路径 |
|------|------|
| API | [`DpRoomHallService`](src/main/java/com/example/mgdemoplus/lobby/DpRoomHallService.java) |
| 实现 | [`DpRoomHallServiceImpl`](src/main/java/com/example/mgdemoplus/lobby/impl/DpRoomHallServiceImpl.java) |

### 职责

| 方法 | 说明 |
|------|------|
| `upsertRoomSummary(DpRoomBO)` | 内存房变更后写 `dp_room_lobby`，并 bump Redis `rev` 失效缓存 |
| `deleteRoomSummary(roomId)` | 摘房时软删/删行 |
| `reconcileLobbyWithRuntimeRoomIds(Set<String>)` | DB 有、内存无的 roomId → 清理幽灵房 |
| `getPublicRoomsPage` | 分页公开房（缓存） |
| `queryPublicRoomsFromDb` | 筛选/精确房号，MyBatis-Plus + Redis 按参数指纹缓存 |

### `DpRoomLobbyReconcileScheduler`

- 条件：`mgdemoplus.dp-lobby-reconcile-enabled=true`（**默认开启**）。
- 启动 `ApplicationRunner` + `@Scheduled(fixedDelay = dp-lobby-reconcile-ms)` 调用 `reconcileLobbyWithRuntimeRoomIds(dpRoomService.getRoomIdsInMemory())`。
- 仅适用于 **单机内存房模型**。

### Mapper / 实体

- [`DpRoomLobby`](src/main/java/com/example/mgdemoplus/lobby/entity/DpRoomLobby.java) — 表 `dp_room_lobby`
- [`DpRoomLobbyMapper`](src/main/java/com/example/mgdemoplus/lobby/mapper/DpRoomLobbyMapper.java) — XML 摘要 upsert/delete
- [`DpRoomLobbyMpMapper`](src/main/java/com/example/mgdemoplus/lobby/mapper/DpRoomLobbyMpMapper.java) — MP 条件分页

---

## `quickmatch/` — 队列、索引、配对

### 语义与索引

| 类 | 说明 |
|----|------|
| [`DpQuickMatchRoomSemantics`](src/main/java/com/example/mgdemoplus/quickmatch/DpQuickMatchRoomSemantics.java) | 空位 = `maxSeatCount - (在座非离线 + waitNextHand)`；密码房不入索引 |
| [`JoinableQuickMatchRoomIndex`](src/main/java/com/example/mgdemoplus/quickmatch/JoinableQuickMatchRoomIndex.java) | `TreeMap<缺人数, Set<roomId>>` + 反向表；`firstVacancyBucket()` 越满越优先 |

索引生命周期：进退房、候补、心跳离线、改密、`maxSeatCount` 变更后 `addOrRefresh`；销毁 `remove`。Bean 就绪后 `rebuildJoinableQuickMatchIndex` 全量重建。

### 队列与 REST 入口

[`DpRoomQuickMatchBridge`](src/main/java/com/example/mgdemoplus/room/support/DpRoomQuickMatchBridge.java)：

| 流程 | 方法 | 要点 |
|------|------|------|
| 扫公开房并入 | `quickMatchJoinAndReady` | 持 `dpQuickMatchAssignmentLock`，按候选列表 `synchronized(r)` join + ready |
| 默认 FIFO | `quickMatchJoinQueueOrImmediate` | 入队 → `attemptQuickMatchPairing()` → 返回 `queued/WAITING` |
| 取消排队 | `cancelDefaultQuickMatchWait` | 移除队列项，`notifyIdle` |

默认参数：`DEFAULT_QM_SB=5`, `BB=10`, `STARTING_BB=50`, `MAX_SEATS=9`, 等待超时 **3 分钟**。

### `DpQuickMatchPairingCoordinator`

单入口 [`attemptPairing()`](src/main/java/com/example/mgdemoplus/quickmatch/pairing/DpQuickMatchPairingCoordinator.java)：

1. `flushQueuedWaitersIntoJoinablePublicRooms()` — 队列玩家灌入索引桶中的公开房。
2. 有桌：`fillHeadIntoJoinableRoom` — 队头 k 人 poll，**先 `synchronized(r)` 再 `defaultQmLock`**。
3. 无桌：`drainBatchNewRoomWhenNoJoinableTable` — 队列 ≥2 人时批量出队（至多 9 人）建新公开房并 `startGame`。
4. 嵌套重入：`pairingDepth > 1` 时仅 flush，避免建房递归死锁。

`createRoom`（无密码公开房）末尾在 **未** 持 `defaultQmLock` 时调用 `attemptPairing()`，使新房立即消费队列。

### 相关测试

- [`JoinableQuickMatchRoomIndexTest`](src/test/java/com/example/mgdemoplus/quickmatch/JoinableQuickMatchRoomIndexTest.java)
- [`DpQuickMatchPairingCoordinatorTest`](src/test/java/com/example/mgdemoplus/quickmatch/pairing/DpQuickMatchPairingCoordinatorTest.java)
- [`DpRoomDesertedRoomCleanupTest`](src/test/java/com/example/mgdemoplus/room/impl/DpRoomDesertedRoomCleanupTest.java)

---

## `websocket/` — 职责分工

| 组件 | 订阅键 | 推送内容 | 依赖 Service |
|------|--------|----------|--------------|
| [`DpGameRoomWebSocketHandler`](src/main/java/com/example/mgdemoplus/websocket/DpGameRoomWebSocketHandler.java) + [`DpGameRoomPushService`](src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java) | `roomId` | 房间快照 JSON（同 `getNowRoom`）、聊天气泡、BGM、关房 `roomClosed` | `DpRoomService`（快照）、`RoomChatBuffer` |
| [`DpQuickMatchWebSocketHandler`](src/main/java/com/example/mgdemoplus/websocket/DpQuickMatchWebSocketHandler.java) + [`DpQuickMatchPushService`](src/main/java/com/example/mgdemoplus/websocket/DpQuickMatchPushService.java) | `nickname` | `WAITING`（含队列位）、`MATCHED`（含 roomId）、`IDLE` | `DpRoomService.pushQuickMatchLobbySnapshot` |

- `/ws/**` 在 `JwtSecurityConstants.PERMIT_ALL`；快匹 WS **握手**用 query `token` + `JwtTokenService.verifyToken`，subject 必须等于 `nickname`。
- 对局 WS 可选 `nickname` 写入 session，用于观战裁剪快照。
- 两者均为 **进程内** `ConcurrentHashMap`，无 Redis 广播。

### REST 与 WS 协作（快匹）

```text
POST /dpRoom/quickMatch2  ──► 入队 + attemptPairing
        │
        ▼
客户端应连接 /ws/dp-quick-match?nickname=&token=
        │
        ▼
DpQuickMatchPushService 推送 WAITING / MATCHED / IDLE
POST /dpRoom/quickMatchCancel2  ──► 出队 + notifyIdle
```

---

## Flyway（本域表）

| 表 | 迁移 |
|----|------|
| `dp_room_lobby` | V1 `__init_schema.sql` |
| `dp_room_invite` | V1 |
| `dp_room_chat_message` | V2 + V3（复合主键） |

---

## README 建议修订点

- 写明对局状态在 [`DpRoomServiceImpl`](src/main/java/com/example/mgdemoplus/room/impl/DpRoomServiceImpl.java) 的 **`roomMap`（单 JVM）**，与 Redis 无关；多实例需关闭 [`DpRoomLobbyReconcileScheduler`](src/main/java/com/example/mgdemoplus/lobby/DpRoomLobbyReconcileScheduler.java) 或改分布式设计。
- 快匹：**索引扫房**（[`JoinableQuickMatchRoomIndex`](src/main/java/com/example/mgdemoplus/quickmatch/JoinableQuickMatchRoomIndex.java)）→ **FIFO 队列**（[`DpRoomQuickMatchBridge`](src/main/java/com/example/mgdemoplus/room/support/DpRoomQuickMatchBridge.java)）→ **配对**（[`DpQuickMatchPairingCoordinator`](src/main/java/com/example/mgdemoplus/quickmatch/pairing/DpQuickMatchPairingCoordinator.java)）；状态推送走 [`DpQuickMatchPushService`](src/main/java/com/example/mgdemoplus/websocket/DpQuickMatchPushService.java)。
- 大厅列表读 **`dp_room_lobby`** + Redis 缓存，实现见 [`DpRoomHallServiceImpl`](src/main/java/com/example/mgdemoplus/lobby/impl/DpRoomHallServiceImpl.java)；配置 `mgdemoplus.dp-lobby-reconcile-*` 与 `cache.dp-room-public-rooms-ttl-seconds`。
- 对局实时推送：[`DpGameRoomPushService`](src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java) + `/ws/dp-game`。
- 单元测试可链到 [`JoinableQuickMatchRoomIndexTest`](src/test/java/com/example/mgdemoplus/quickmatch/JoinableQuickMatchRoomIndexTest.java)、[`DpQuickMatchPairingCoordinatorTest`](src/test/java/com/example/mgdemoplus/quickmatch/pairing/DpQuickMatchPairingCoordinatorTest.java)。
