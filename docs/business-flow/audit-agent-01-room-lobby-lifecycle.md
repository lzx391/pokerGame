# 业务流程审计 · 房间 / 大厅生命周期（agent-01）

**范围**：建房、进退房、准备与结算后准备超时、房主转让与踢人、观众席与普通座位、`waitNextHand`、房间拆除、大厅列表与摘要（MySQL `dp_room_lobby` + Redis 版本缓存）、单机 `roomMap` 与异步推送的边界。

**约束**：本文档仅审计说明，不修改业务代码；对照根目录 [README.md](../../README.md) 中单实例免责条款。

---

## 与 README「单实例房间模型」的对照

README 明确：**房间与对局热状态驻留在 JVM 内存 `roomMap`**，默认可横向扩展时**不与 Redis 同步**；多实例需会话粘滞或共享状态；并指出 **`mgdemoplus.dp-lobby-reconcile-enabled`** 在多实例、各节点 `roomMap` 不一致时**不宜强行用 DB 摘要对齐**单机内存。

本审计结论：**权威对局态 = `DpRoomServiceImpl` 内 `ConcurrentHashMap&lt;String, DpRoomBO&gt; roomMap` + 各 `DpRoomBO` 监视器上的串行写路径**；**`dp_room_lobby`** 为大厅展示用摘要；**Redis** 仅为分页/筛选结果的**版本号失效型缓存**，不承载房间真相；**WebSocket 订阅表**亦为进程本地 (`DpGameRoomPushService.roomSessions`)。调度任务 `DpRoomLobbyReconcileScheduler` 用当前进程 `roomMap.keySet()` 清理 DB 中「幽灵房」，与上述免责一致——**多实例误判风险**已由代码注释标明。

---

## 主表：事件 → 触发者 → 服务端状态 → 客户端预期 → 关键类/路径

| 事件（业务语义） | 谁触发 | 服务端状态（roomMap / DB） | 客户端应看到什么 | 关键类 / 路径 |
| ---------------- | ------ | --------------------------- | ----------------- | ------------- |
| **创建房间** | 玩家 `POST /dpRoom/createRoom` | `roomMap.put` 新房；房主已在 `players` 且 `ready=true`；无密码则 `attemptQuickMatchPairing()`；`JoinableQuickMatchRoomIndex` 更新；**`syncLobbyForRoomId` → upsert `dp_room_lobby` + Redis rev 递增** | 返回完整 `DpRoomBO`（含 roomId）；大厅后续分页可见摘要 | `DpRoomController#createRoom`、`DpRoomServiceImpl#createRoom` |
| **加入房间（未开局上桌）** | 玩家 `POST /dpRoom/joinRoom` 等 | `synchronized(room)` 内：`players` 增员、`maxSeatCount` 未满；presence `IN_GAME`；锁外 **`refreshQmIndex`** + **`syncLobbyForRoomId`**（outcome=`ok` 时 upsert DB） | HTTP 返回 `ok`；轮询/Ws 快照中本人出现在座位上 | `joinRoom`、`joinRoomMutateAssumeLocked`、`refreshQmIndexAfterJoinOutcomeOutsideRoomLock` |
| **加入房间（已开局旁观）** | 同上，`isPlaying()==true` | `spectators` 增员、`touchSpectatorPresence`；**锁外刷新快匹索引，但不调用 `syncLobbyForRoomId`**（outcome=`游戏已开始` 时仅 index，**DB 摘要不因纯观众进房而在此路径更新**） | 返回 `游戏已开始`；快照中在 `spectators`，不在本手 `players` 行动列表 | 同上 + `DpRoomBO#spectators` |
| **邮箱邀请进房（始终观众）** | `joinRoomInviteAsSpectator`（`/dp` 链路，非本表展开） | 未开局可仅观众不占座；已开局进 `spectators` | 与朋友邀请流程一致的前端导航 | `DpRoomServiceImpl#joinRoomInviteAsSpectator` |
| **房主点击开局** | `POST /dpRoom/startGame`（参数 `ownerNickname`） | `isPlaying=true`，`newHandWithoutLobbyUpsert`；锁外 **`refreshJoinableQmIndexThenSyncLobby`** | 进入 preflop；大厅人数/索引随摘要更新 | `startGame` |
| **切换准备（未开局 lobby）** | `POST /dpRoom/toggleReady` | 桌上真人 `ready` 取反 | UI 的准备勾选变化；齐人后由房主 `startGame` 或其它路径开局 | `toggleReady`、`toggleReadyAssumeLocked` |
| **切换准备（结算后 settled）** | 同上 | 筹码≥大盲才可切；若因此齐人 → `newHandWithoutLobbyUpsert`；若在锁外新开局则 **`refreshJoinableQmIndexThenSyncLobby`** | 准备状态变化或直接进入下一手 | `checkAndStartNextHandAfterSettleReturning` |
| **结算写入 settled + 30s 准备窗** | 游戏流程 `autoSettle`（非 HTTP） | `currentStage=settled`，`readyDeadline=now+30s`，全员 `ready=false` | 倒计时与准备按钮；超时行为见下行 | `autoSettle` 尾部、`DpRoomBO#readyDeadline` |
| **结算准备超时** | 构造函数内 **`Timer` 每秒** 节拍 `maybeInvokeReadyTimeoutWhenDeadlinePassed` | `pruneSettledTableForReadyTimeout`：**筹码&lt;大盲**或**仍 unready** 的桌上真人 → 移入 `spectators`；桌上空则 **`isPlaying=false`**；否则尝试 `newHand...`，成功则 **`refreshJoinableQmIndexThenSyncLobby`** | 未准备/短码者落观众席或直接回到「未开局」态 | `handleReadyTimeout`、`pruneSettledTableForReadyTimeout` |
| **「独苗」settled 优化** | 同上定时器 | 若 settled、截止已过、**仅 1 人且无 `waitNextHand`** → **跳过广播与大厅刷新**（减少空转） | 该情形下客户端可能不会收到每秒快照刷新，依赖后续 HTTP/Ws 自建连 | `skipBroadcastForLoneSettledPlayerWaitingNothing` |
| **退房** | `POST /dpRoom/exitRoom` | `synchronized`：移出 `spectators`/`waitNextHand`/桌上（游戏中为 `leftThisHand` + 弃牌链路）；房主走 `giveOwner` 语义；无人则 **`tryUnregisterEmptyRoomAssumeLocked`** → **`finalizeHallAfterRoomRemovedWithPresenceSnapshot`**（删 map、删 DB、`shutdownSubscriptionsForRoom`、`roomClosed`） | 本人离开；房内他人见列表变化；房间空则断开 WS、`roomClosed` | `exitRoom`、`exitRoomApplyMutationWhileHoldingRoomLock` |
| **桌上真人 HTTP 心跳丢失** | 定时器每秒 | 超 `DpRoomBO#getHeartTimeout`（默认 20s）：若房主先 `giveOwner`，再 **`remove`** 玩家、**`shutdownSubscriptionsForNicknameInRoom`**、presence Idle | WS 同名连接收 `roomClosed`；列表不再有该座位真人 | `tickEvictStaleSeatedPlayersOnHeartbeat` |
| **观众心跳丢失** | 定时器每秒 | `spectatorLastPresenceMs` 超阈值 → **`exitRoom`**（视为离开） | 观众被移出房间；可能触发大厅/拆房同上 | `tickEvictStaleSpectatorsOnHeartbeat` |
| **空房摘除（定时路径）** | 定时器 | `liveHumanTableCount==0` 且 **`spectators` 空** → `removeRoom` | 同上拆房 | `removeDesertedRoomInGlobalTickIfNoLiveHumans` |
| **房主主动移交** | `POST /dpRoom/transferOwner` | 校验 `from` 为当前房主；目标为**未离座真人**或**观众席真人**；`owner` 字段更新；**`refreshJoinableQmIndexThenSyncLobby`** | 房主标识切换 | `transferOwner` |
| **房主易位（被动）** | `giveOwner`（心跳踢房主、exit 等） | 优先桌上真人，再观众真人；若无任何活人则 **摘房** | 与移交类似或房间消失 | `applyGiveOwnerWhileRoomLocked`、`giveOwner` |
| **踢人到观众席** | `POST /dpRoom/kickPlayer`、`/kickPlayersBatch` | 目标仍在 `players`：弃牌/离手标记 + 加入 `spectators` + `touchSpectatorPresence`；单/批末 **`refreshJoinableQmIndexThenSyncLobby`**（**注意：服务端未校验调用者是否为房主**） | 被踢者本手不再行动，出现在观众席 | `kickOnePlayerWithoutLobbySync`、`kickPlayer`、`kickPlayersBatch` |
| **预约下一局上桌** | `POST /dpRoom/readyNextHand` / `cancelReadyNextHand` | `waitNextHand` 列表维护；受 `maxSeatCount` 与 `seatedCountForSeatCap` 约束 | 观战席看到候补状态；新一手 `getAllCanPlayer` 拉人上桌 | `readyNextHand`、`cancelReadyNextHand`、`newHandWithoutLobbyUpsert` |
| **大厅无条件分页** | `GET /dpRoom/publicRooms` | 读 Redis `rev` + 页缓存；miss 则 **查 `dp_room_lobby`** 并回写缓存；**非**直接扫 `roomMap` | 公开房列表与总数 | `DpRoomHallServiceImpl#getPublicRoomsPage` |
| **大厅筛选分页** | `GET /dpRoom/publicRooms/query` | 与上类似：MyBatis 条件查 `dp_room_lobby`，Redis 以 `rev`+参数哈希缓存（Controller 注释写「只走 MySQL」，实现仍带 Redis 结果缓存） | 筛选后的列表 | `queryPublicRoomsFromDb` |
| **内存房间列表（调试用）** | `GET /dpRoom/getAllRooms2` | **纯 `roomMap` 派生**，不落库 | 与 DB 大厅可能短暂不一致 | `getAllRooms2` |
| **房间摘要 DB 写入** | `syncLobbyForRoomId` 等显式调用链 | `DpRoomHallServiceImpl#upsertRoomSummary`：`toSummary` 中 **playerSize 只计 `!leftThisHand` 的桌上位**，**不含观众** | 大厅「人数」为桌上非僵尸席位数，不是「房内总人数」 | `toSummary`、`DpRoomLobbyMapper` |
| **大厅与内存对齐** | `DpRoomLobbyReconcileScheduler`（默认可用 `mgdemoplus.dp-lobby-reconcile-enabled`） | DB 中存在、**当前进程 `roomMap` 无**的 roomId → **delete** 摘要行 + bump rev | 用户可能仍见短暂幽灵房直至 rev 与缓存过期 | `reconcileLobbyWithRuntimeRoomIds` |

---

## 观众席 vs 普通座位 vs 下一局候补（实现口径）

| 概念 | 存储位置 | 备注 |
| ---- | -------- | ---- |
| **普通座位（本手）** | `DpRoomBO.players` | `leftThisHand=true` 为「僵尸占位」，仍占本手流程；**不算**大厅 `playerSize` |
| **观众席** | `DpRoomBO.spectators` | 已开局进房、`kick`、准备超时下移入等；真人依赖 **HTTP heartbeat** 或 **WS 快照路径不刷新 `spectatorLastPresenceMs`**（仅 `heartbeat(roomId,nickname)` 与进房触摸时间戳）；**仅用 WS、从不打 HTTP heartbeat 的观众仍有被定时踢除风险** |
| **下一局上桌** | `DpRoomBO.waitNextHand` | `isNicknameInRoom` 视为房内成员（快照门禁） |

---

## 与 WebSocket 交界

以下仅列出与本生命周期相关的 **`_ws` 事件名字符串**；载荷、快照字段、门禁与重连语义 **明细见 [audit-agent-02-realtime-ws-presence.md](./audit-agent-02-realtime-ws-presence.md)**。

| 方向 | `_ws` 取值 |
| ---- | ----------- |
| 客户端 → 服务端 | `chatSend`、`roomMusicSync` |
| 服务端 → 客户端（控制类 JSON） | `roomClosed`（常量 `DpGameRoomPushService.ROOM_CLOSED`） |

另：**房间完整状态快照**另由 **`broadcastIfSubscribed`** 周期性推送（与 REST `getNowRoom` 同类 viewer 快照），不按 `_ws` 分支命名；明细同样见 **audit-agent-02**。

---

## 与快匹交界

建房（无密码）、退房（原房间为公开房）、队列冲刷、建新桌并进房 **`JoinableQuickMatchRoomIndex`、`DpQuickMatchPairingCoordinator`**、锁 `dpQuickMatchAssignmentLock` 等与大厅入口 **`POST /dpRoom/quickMatch2`**、`/quickMatchCancel2` 的流程 **明细见 [audit-agent-03-quickmatch-social.md](./audit-agent-03-quickmatch-social.md)**。

---

## P0 漏测场景（建议优先补集成/并发用例）

1. **并发进房**：两名玩家同时对**同一未满未开局房间**调用 `joinRoom`，在 `maxSeatCount-1` 仅剩一席时是否总有一方得到「人数已满」且数据无重复座位/无负数空位表象。
2. **并发进退与拆房**：`exitRoom` 与定时器 **`removeRoom` / `tryUnregisterEmptyRoomAssumeLocked`** 交错时，`joinRoom` 对「房间不存在」与 map 一致性（注释中强调的竞态窗口）。
3. **踢人链路**：批量踢中与 **`fold`/结算** 并发（代码已注明 `idx` 失效风险）；以及 **REST 未校验房主** 时任意客户端调用 `/kickPlayer` 的滥用面（安全 + 一致性）。
4. **旁观者**：仅 WS、**不传 `nickname` query**（则 `viewerNickname` 为空）时 **`getRoomSnapshotForViewer`/广播**是否能隐藏不应见的底牌与是否误判 `roomClosed`；**仅从 WS 不看 HTTP heartbeat** 的 20s 驱逐。
5. **解散瞬间**：最后一人的 `exitRoom` / 全员心跳蒸发与 **`finalizeHallAfterRoomRemoved`**（DB 删除、Ws 广播 `roomClosed`、好友 presence IDLE）的顺序与幂等。
6. **大厅一致性**：进行中大量观众涌入时 **`joinRoom` outcome=`游戏已开始`** **不触发 `syncLobbyForRoomId`** 与 **`getPublicRoomsPage`/`getAllRooms2` 的差异**是否在业务上可接受（UI 误导「空桌」）。
7. **结算准备超时与外部 HTTP**：`handleReadyTimeout` 在每秒定时器上与 **`toggleReady` / `exitRoom` 同人同秒**交错时的就绪状态与是否误开一桌。
8. **多实例**（与设计声明一致）：Sticky off 时，用户落在 B 节点但大厅 DB 仍为 A 节点写入的旧摘要；**勿开启 reconcile** 或需分布式房注册的场景演练。

---

## 文档修订记录

- **2026-05-14**：agent-01 首版（对照当前 `DpRoomServiceImpl`、`DpRoomHallServiceImpl`、`DpRoomLobbyReconcileScheduler`、`DpGameRoomPushService`、`DpRoomController`、根 `README.md`）。
