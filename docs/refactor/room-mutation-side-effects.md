# 房间副作用盘点 — 数据结构变更 + 开发清单

> **文档摘要（5 行）**  
> - **给谁看**：改房间/快匹/大厅相关业务的开发者与 Code Review 人。  
> - **解决什么问题**：改 `DpRoomBO` 一处字段后，别漏刷可进房索引、大厅摘要、观众席、presence、WS 等「副作用」。  
> - **第 1～2 章**：Agent A/B 整理的**现状**词典与行为矩阵（只描述当前代码）。  
> - **第 3～5 章**：Agent C 整理的**理想判定 + 自检清单 + 技术债分期**（开发时对着用，不是补丁）。  
> - **用法**：写完业务先过第 4 章 checklist；不确定刷什么时查第 3 章「如果…就…」；矩阵细节回第 2 章。  
>
> 只读源码整理，描述**当前实现**（2026-05-22 代码树）。与注释冲突处标注「实现 vs 注释」。

---

## 第 1 章 维护中的数据结构（词典）



---
下面都是「改了一处容易忘另一处」的结构。按**单机 JVM 内存房**模型写；多实例时各节点 `roomMap` 不一致，大厅对齐任务需关闭（见 `DpRoomLobbyReconcileScheduler` 注释）。

### 1.1 `DpRoomBO` — 桌上玩家 `players`

| 维度 | 说明 |
|------|------|
| **是什么** | `List<DpPlayer>`：昵称、筹码、本手弃牌/离座标记 `leftThisHand`、心跳 `lastHeartBeat`、Bot 标记等。占「人数上限」用 `seatedCountForSeatCap`：**本手未离座**的席位数（`leftThisHand` 仍占位）。 |
| **谁在读** | 对局逻辑（`bet`/`fold`/`newHand`）、快匹空位（`DpQuickMatchRoomSemantics.seatedNonOfflineCount`：非离座 + 真人未心跳超时）、大厅摘要 `playerSize`（`DpRoomHallServiceImpl.toSummary`：非 `leftThisHand` 计数）、WS 推送快照、好友跟随 `findRoomContainingNickname`。 |
| **易混点** | **僵尸位**：进行中 `exitRoom` 只设 `leftThisHand=true`，不立刻从 `players` 删；结算后 `removeLeftThisHandZombiesAfterHand` 才删。**大厅人数**不含观众、不含 `waitNextHand`，且与快匹「有效人数」口径不同。 |

### 1.2 `waitNextHand`

| 维度 | 说明 |
|------|------|
| **是什么** | `List<String>`：已预约下一局上桌的昵称（含 Bot 昵称）。 |
| **谁在读** | `readyNextHand` / `cancelReadyNextHand`、`newHand` → `getAllCanPlayer` 拉人上桌；快匹索引 **计入空位**（`eligibleOccupants = 桌上非离线座 + waitNextHand.size()`）。 |
| **易混点** | 进 **`joinRoom` 已开始局** 会先从 `waitNextHand` remove 再进观众席，但**普通 joinRoom 不刷新快匹索引**（见 2.3）。大厅 `playerSize` **不算**候补。 |

### 1.3 `spectators` + `spectatorLastPresenceMs`

| 维度 | 说明 |
|------|------|
| **是什么** | 观众席昵称列表；`spectatorLastPresenceMs`（`@JsonIgnore`）记录真人最后保活时间，由 HTTP `heartbeat` 与对局 WS 刷新。 |
| **谁在读** | 空房摘除：`tryUnregisterEmptyRoomAssumeLocked` 要求 **无观众** 才删 `roomMap`；1s 定时器踢观众：超时走 `exitRoom`；`isNicknameInRoom` / 好友跟随。 |
| **易混点** | **观众人数不进** `dp_room_lobby.player_count`。**快匹空位不算观众**（只算桌席 + wait）。观众 join **不**走 `refreshQmIndexAfterJoin`（`joinRoom` 注释）。 |

### 1.4 内存 `roomMap`（`DpRoomRegistry`）

| 维度 | 说明 |
|------|------|
| **是什么** | `ConcurrentHashMap<String, DpRoomBO>`，进程内房间唯一真相；`DpRoomServiceImpl` 与快匹 Host 共用 `registry.roomMap()`。 |
| **谁在读** | 全部房间 API、快匹索引 `rebuildAll`、大厅对齐 `getRoomIdsInMemory`、WS 订阅按 `roomId` 推送。 |
| **易混点** | `roomMap` 有条目 ≠ 大厅表一定有行（需 `syncLobby`）；摘房后索引/大厅/聊天 buffer 要靠 `finalizeHallAfterRoomRemoved` 收尾。 |

### 1.5 快匹可进房索引 `JoinableQuickMatchRoomIndex`

| 维度 | 说明 |
|------|------|
| **是什么** | 无密码、仍有快匹空位（`rawVacancy > 0`）的房间，按「缺几人」分桶 + `roomIdToShortage` 反查表。 |
| **谁在读** | `quickMatchJoinAndReady` 扫桶 + fallback 全表扫描；`DpQuickMatchPairingCoordinator` 填桌/建房后刷新。 |
| **易混点** | 与 `roomMap` 漂移时需 `rebuildAll`（`@PostConstruct` 会全量重建）。**poll 候选**会从索引摘掉，进房失败需 `addOrRefresh` 补回。 |

### 1.6 大厅房间摘要 `dp_room_lobby`（`DpRoomHallService`）

| 维度 | 说明 |
|------|------|
| **是什么** | DB 表 + Redis 缓存版本；`upsertRoomSummary` / `deleteRoomSummary`；`playerSize` = 桌上非 `leftThisHand` 人数（**不含观众、候补**）。 |
| **谁在读** | 大厅分页/搜索 API；`DpRoomLobbyReconcileScheduler` 定时删「DB 有、内存无」的幽灵行。 |
| **易混点** | `readyNextHand` **故意不** `syncLobby`（注释：大厅展示的是桌上人数）。观众进房若只刷索引不刷大厅，列表人数不变。 |

### 1.7 快匹等待队列 `defaultQmWaiters`（`DpRoomQuickMatchBridge`）

| 维度 | 说明 |
|------|------|
| **是什么** | `ArrayDeque<DpQuickMatchWaitEntry>`，锁 `defaultQmLock`；3 分钟超时 prune；与 `dpQuickMatchAssignmentLock` 配合配对。 |
| **谁在读** | `quickMatchJoinQueueOrImmediate`、定时 `scheduledPruneDefaultQuickMatchQueue`、`DpQuickMatchPairingCoordinator` 出队进房/建房。 |
| **易混点** | `createRoom` / `joinRoom` 会 `cancelDefaultQuickMatchWait` 取消本人在队列中的条目；与「已在房内」是不同维度。 |

### 1.8 好友房内 presence（`DpFriendPresenceService`）

| 维度 | 说明 |
|------|------|
| **是什么** | 内存 `userId → IDLE \| IN_GAME`（不入库）；站点在线另见 `DpSitePresenceService`。 |
| **谁在读** | 好友列表 `DpFriendSocialService.listFriends` 展示；**不**驱动匹配逻辑。 |
| **易混点** | `presenceTryMarkIdleFullyLeft`：若昵称仍在别房或本房 `players` 非离座僵尸位，**不**置 IDLE（注释：只清观众和候补）。摘房时 `presenceMarkIdleAllHumansOnRoomSnapshot` 批量 IDLE。 |

### 1.9 WebSocket 对局房 / 快匹推送

| 维度 | 说明 |
|------|------|
| **是什么** | `DpGameRoomPushService`：按房间订阅推送 JSON；摘房 `shutdownSubscriptionsForRoom` 发 `roomClosed`。`DpQuickMatchPushService`：队列位次 / matched / idle。 |
| **谁在读** | 前端对局页、快匹页；**主动推送**目前主要在 1s 定时器 `broadcastIfSubscribed`（下注/弃牌后不立即推，靠下一秒 tick）。 |
| **易混点** | 内存房状态变了但客户端没订 WS → 只靠 HTTP 拉快照；摘房必须关订阅，否则脏连接。 |

### 1.10 好友跟随 / 观战进房

| 维度 | 说明 |
|------|------|
| **是什么** | `joinRoomInviteAsSpectator`：免密、优先观众席；`followFriendToTheirRoom` / `acceptRoomInvite` / `followFriendIntoRoomAsSpectator` 均委托此方法。 |
| **谁在读** | 社交 Controller；目标房来自 `findRoomIdContainingNickname`（观众+桌上，**不含**仅 `waitNextHand`）。 |
| **易混点** | 与 `joinRoom` 不同：邀请进房会 **`refreshJoinableQmIndexThenSyncLobby`**（即使只是观众）。 |

### 1.11 局内聊天 buffer（`RoomChatBuffer`）

| 维度 | 说明 |
|------|------|
| **是什么** | 房内消息内存队列；房间从 `roomMap` 移除时 `DpRoomLobbySync.finalizeHallAfterRoomRemoved` → `flushRoomToDatabase`。 |
| **谁在读** | WS 发言、`listRecentRoomChat`；摘房时 drain 写 `dp_room_chat_message`。 |
| **易混点** | 只摘内存不 `finalizeHall` → 聊天丢或滞留在 buffer。 |

### 1.12 其它房内附属（简述）

- `registeredDpUserIdByNickname`：进房/候补登记，供 `newHand` 拉候补上桌补 `dpUserId`。
- `carryInChips`：真人离座/退房结算净赢倍数后清除。
- `isPlaying` / `currentStage`（含 `settled`）：影响 join 走桌席还是观众、快匹「游戏已开始」分支。

---

## 第 2 章 行为 → 数据结构变更矩阵（现状）

### 2.1 行为清单（调用链入口）

产品语言 + 入口方法（括号内为 Java 定位，行号约指当前文件）。

| # | 业务行为 | 入口 |
|---|----------|------|
| A1 | 创建房间（房主上桌） | `DpRoomServiceImpl#createRoom` (~742) |
| A2 | 密码/公开 join（未开局上桌；已开局→观众） | `joinRoom` → `joinRoomMutateAssumeLocked` (~1081) |
| A3 | 邀请/跟随观众进房 | `joinRoomInviteAsSpectator` (~1205) |
| A4 | 退出房间 | `exitRoom` → `exitRoomApplyMutationWhileHoldingRoomLock` (~1771) |
| A5 | 房主踢人（离座→观众） | `kickPlayer` / `kickOnePlayerWithoutLobbySync` (~524) |
| A6 | 批量踢人 | `kickPlayersBatch` (~535) |
| A7 | 房主移交 | `transferOwner` (~376) |
| A8 | 房主易位/空房摘除（心跳踢人、退房） | `giveOwner` → `applyGiveOwnerWhileRoomLocked` (~505) |
| A9 | 显式摘房 / 空房定时清理 | `removeRoom` (~353)；`DpRoomHeartbeatScheduler#removeDesertedRoomInGlobalTickIfNoLiveHumans` (~74) |
| A10 | 预约下一局 | `readyNextHand` → `applyReadyNextHandWhileLocked` (~1285) |
| A11 | 取消预约 | `cancelReadyNextHand` (~1328) |
| A12 | 批量/单个加 Bot 到候补 | `addRuleNpcBatchToNextHand` 等 (~155) |
| A13 | 开局前点准备 | `toggleReady` (~1547) |
| A14 | 房主开始游戏 | `startGame` (~1947) |
| A15 | 开始新一手 | `newHand` / `newHandWithoutLobbyUpsert` (~1986) |
| A16 | settled 后自动/超时开下一手 | `checkAndStartNextHandAfterSettleReturning` (~1612)；定时器 `tickSettledCheckAndStartIfReady` |
| A17 | 准备超时 prune | `handleReadyTimeout` → `pruneSettledTableForReadyTimeout` (~3051) |
| A18 | 下注 / 弃牌 / 阶段推进 / 结算 | `bet`/`fold`/`autoAdvanceIfRoundFinished`/`autoSettle` (~2241+) |
| A19 | settled 补码 | `rebuy` (~3067) |
| A20 | HTTP 心跳 | `heartbeat` (~3098) |
| A21 | 1s 全局房间 tick | `DpRoomHeartbeatScheduler#runGlobalSecondTickForSingleRoom` (~52) |
| A22 | 快匹立即找房 | `DpRoomQuickMatchBridge#quickMatchJoinAndReady` (~100) |
| A23 | 快匹入队 | `quickMatchJoinQueueOrImmediate` (~203) |
| A24 | 取消快匹等待 | `cancelDefaultQuickMatchWait` (~243) |
| A25 | 快匹配对协调器 | `DpQuickMatchPairingCoordinator#attemptPairing` (~86) |
| A26 | 大厅 DB 与内存对齐 | `DpRoomLobbyReconcileScheduler#reconcile` (~59) |
| B1 | 好友跟随/接受邀请进房 | `DpFriendSocialService#followFriendToTheirRoom` 等 → `joinRoomInviteAsSpectator` |

**`DpRoomLobbySync` 三个 public 方法及调用方：**

| 方法 | 作用 | 调用方（文件:约行） |
|------|------|-------------------|
| `refreshJoinableQuickMatchIndexRoom` | 单房刷新或 remove 索引 | `DpRoomServiceImpl` 包装 (~286)；`refreshJoinableQmIndexThenSyncLobby` 内部；`refreshQmIndexAfterJoinOutcomeOutsideRoomLock` (~1130)；`@PostConstruct rebuild` (~281) |
| `refreshJoinableQmIndexThenSyncLobby` | 上者 + `upsertRoomSummary` | `exitRoom` 非摘房且 lobbyTouch (~1783)；`kick`/`transferOwner`/`giveOwner`/`startGame`/`newHand`/`joinRoomInvite`；心跳 `lobbyDirty` (~231)、settled 开局 (~199)；快匹 bridge (~116,166) |
| `finalizeHallAfterRoomRemoved` | flush 聊天、remove 索引、delete 大厅行、WS shutdown | `removeRoom`/`exitRoom`/`giveOwner` 摘房路径经 `finalizeHallAfterRoomRemovedWithPresenceSnapshot` (~937) |

---

### 2.2 主表（核心交付）

图例：**变** / **不变** / **条件**；附 `类#方法(约行)`。

| 业务行为 | players | waitNextHand | spectators | roomMap | 可进房索引 | 大厅摘要 | presence | WS/推送 | 快匹队列/配对 | 其它 |
|----------|---------|--------------|------------|---------|------------|----------|----------|---------|---------------|------|
| **A1 createRoom** | 变：+房主 | 不变 | 不变 | 变：put | 变：refresh | 变：sync | 变：IN_GAME | 不变（无订） | 变：取消房主排队 | LLM 全局 store 无关 |
| **A2 joinRoom** | 条件：未开局+未满→+player；已开局→不变 | 条件：已开局 remove 昵称 | 条件：已开局 add | 不变 | **不变**（实现：注释掉 refresh，~1153） | **不变** | 变：IN_GAME（mutate 内） | 不变 | 变：cancel 本人队列 | vs A3：不刷索引/大厅 |
| **A3 joinRoomInvite** | 条件：未开局且已上桌→不变；否则观众 | 变：remove | 变：add+touch | 不变 | 变：refresh+sync | 变：sync | 变：IN_GAME（锁外） | 不变 | 变：cancel 队列 | |
| **A4 exitRoom** | 条件：未开局 remove；进行中 leftThisHand | 变：remove | 变：remove+presence map | 条件：空房→remove | 条件：未摘房 refresh；摘房 remove | 条件：lobbyTouch\|易主→sync | 条件：未摘房 try IDLE | 条件：摘房 shutdown | 条件：公开房 attemptPairing | carryIn 结算 |
| **A5 kick** | 变：leftThisHand+fold 路径 | 不变 | 变：+观众 | 不变 | 变：refresh+sync | 变：sync | 不变 | 不变 | 不变 | |
| **A6 batch kick** | 同 A5 | 不变 | 同 A5 | 不变 | 条件：有人成功→refresh+sync | 同左 | 不变 | 不变 | 不变 | |
| **A7 transferOwner** | 不变 | 不变 | 不变 | 不变 | 变：refresh+sync | 变：sync | 不变 | 不变 | 不变 | owner 字段变 |
| **A8 giveOwner** | 不变 | 不变 | 不变 | 条件：无活人→remove | 条件：易主 refresh+sync；摘房 finalize | 同左 | 条件：摘房批量 IDLE | 条件：摘房 shutdown | 不变 | |
| **A9 removeRoom / 空房 tick** | — | — | — | 变：remove | 变：finalize 内 remove | 变：delete | 变：批量 IDLE | 变：shutdown | 不变 | flush 聊天 |
| **A10 readyNextHand** | 不变 | 变：+nick | 不变 | 不变 | **变：仅 refresh**（~1320，不 sync） | **不变** | 不变 | 不变 | 不变 | |
| **A11 cancelReadyNextHand** | 不变 | 条件：remove | 不变 | 不变 | 条件：changed→refresh | 不变 | 不变 | 不变 | 返回值恒 true |
| **A12 addBot batch** | 不变 | 变：+bot | 不变 | 不变 | 变：refresh | 不变 | 不变 | 不变 | 不变 | 绕过 present 校验 |
| **A13 toggleReady** | 变：ready 位 | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | |
| **A14 startGame** | 变：发牌态 | 不变 | 不变 | 不变 | 变：refresh+sync | 变：sync | 不变 | 不变 | 变：attemptPairing（公开） | isPlaying=true |
| **A15 newHand** | 变：getAllCanPlayer 重组 | 变：拉人 remove | 变：移出/加入观众 | 不变 | 变：refresh+sync | 变：sync | 条件：僵尸移除 try IDLE | 不变 | 不变 | 牌局/日志清理 |
| **A16 settled 自动 newHand** | 同 A15 | 同 | 同 | 不变 | 变（tick 内，~199） | 变 | 同 | 不变 | 不变 | 持房锁内 newHand |
| **A17 handleReadyTimeout** | 变：prune→观众/清空 | 不变 | 变：+nick | 条件：桌上空→isPlaying false | **不变**（实现 vs 注释：注释写外层 upsert，方法内未调 refresh/sync，~3051） | **不变** | 不变 | 不变 | 不变 | 未调 checkAndStart |
| **A18 bet/fold/settle** | 变：筹码/阶段 | 不变 | 不变 | 不变 | 不变 | 不变 | 条件：僵尸 settle 后 IDLE | 不变（靠 1s broadcast） | 不变 | autoSettle→settled |
| **A19 rebuy** | 变：chips | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | carryIn merge |
| **A20 heartbeat** | 变：player HB | 不变 | 变：touch spectator | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | |
| **A21 1s tick** | 条件：超时 remove | 不变 | 条件：超时 exit | 条件：空房 remove | 条件：lobbyDirty 或 settled 开局 | 条件：同上 | 条件：踢人/exit IDLE | 变：broadcast | 不变 | giveOwner/exit 链 |
| **A22 quickMatchJoin** | 变：join+ready | 条件：+wait | 条件：观众 | 不变 | 变：多次 refresh/sync | 变 | 变 | 变：notify | 变：出队/配对 | |
| **A23 入队** | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | 变：notifyWaiting | 变：+queue；attemptPairing | |
| **A24 取消排队** | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | 不变 | 变：notifyIdle | 变：remove | |
| **A25 pairing** | 变：join/create/start | 变 | 变 | 条件：create→put | 变：afterRoomMutation | 条件：create 已 sync | 变 | 变：matched | 变：poll/flush 队列 | |
| **A26 lobby reconcile** | 不变 | 不变 | 不变 | 不变 | 不变 | 变：删幽灵行 | 不变 | 不变 | 不变 | 仅 DB⊖内存 |
| **B1 好友跟随** | 同 A3 | 同 A3 | 同 A3 | 不变 | 同 A3 | 同 A3 | 同 A3 | 不变 | 同 A3 | |

---

### 2.3 专门小节：「清理遗漏」风险

| 风险 | 说明 | 代码锚点 |
|------|------|----------|
| **观众 join 不刷快匹/大厅** | `joinRoom` 故意注释掉 `refreshQmIndexAfterJoinOutcomeOutsideRoomLock`（「观众不占位、大厅不显示观众」）。公开房仅加观众时，索引仍显示有空位，直到别的路径 refresh。 | `DpRoomServiceImpl#joinRoom` ~1153 |
| **候补只刷索引不刷大厅** | `readyNextHand` 注释：大厅 `playerSize` 不含候补，故只 `refreshJoinableQuickMatchIndexRoom`。快匹空位减少但列表人数不变——**符合当前设计**，重构时勿误以为漏 sync。 | ~1318-1320 |
| **大厅人数 vs 快匹人数** | 大厅：`非 leftThisHand` 桌席数；快匹：非离座且真人未超时 + wait。心跳离线桌席仍占大厅位直至被 tick 踢掉。 | `DpRoomHallServiceImpl#toSummary` ~377；`DpQuickMatchRoomSemantics` |
| **newHand 与 wait/spectators** | `getAllCanPlayer` 从 wait 拉人并 `spectators.remove`；**未**清空的 wait 条目保留；离座真人推进观众席。若 wait 含已不在房昵称，靠 `readyNextHand` 的 `nicknamePresentInRoom` 防 HTTP 刷名额。 | ~2114-2172 |
| **exitRoom 双路径刷大厅** | 非摘房：`refreshJoinable` 总是执行；`syncLobby` 仅 `lobbyTouch \|\| ownerFieldChanged`。仅观众退出可能 **lobbyTouch=false** → 只变索引不变 DB。 | ~1781-1784 |
| **进行中 exit 僵尸位** | 真人 exit 后仍占 `players` 直至 settle 后 `removeLeftThisHandZombiesAfterHand`；`liveHumanTableCount` 可能已为 0 但观众非空仍不摘房。 | ~1860-1877；`DpRoomHumanCounts` |
| **handleReadyTimeout 未刷新索引/大厅** | 注释称外层 upsert，实现只做 `pruneSettledTableForReadyTimeout` 并 return，**不**调 `checkAndStartNextHand` 或 lobby sync；依赖同秒 tick 里先前的 `tickSettledCheckAndStartIfReady` 或下一秒的 `lobbyDirty`。 | ~3045-3061 vs `DpRoomHeartbeatScheduler` ~215-231 |
| **摘房三件套必须成套** | 只 `roomMap.remove` 不调 `finalizeHall` → 索引/大厅/WS/聊天 buffer 泄漏。 | `DpRoomLobbySync#finalizeHallAfterRoomRemoved` ~58 |
| **presence IDLE 误判** | `presenceTryMarkIdleFullyLeft` 在 `findRoomContainingNickname!=null` 时仍可能因本房非离座 player 而 **不** IDLE；多房场景依赖「先 exit 再查」。 | ~851-866 |
| **索引 poll 与进房失败** | `pollBestCandidate` 移除 roomId；协调器进房失败应 `addOrRefresh`（注释约定）。 | `JoinableQuickMatchRoomIndex` ~95-120 |
| **实现 vs 注释：joinRoom 与 refreshQm** | `refreshQmIndexAfterJoinOutcomeOutsideRoomLock` 对「游戏已开始」也会 refresh 索引，但 **不同步大厅**（仅 `ok` sync）；与 `joinRoom` 完全不调该方法并存。 | ~1130-1138 |

---

### 2.4 附录 A：grep 索引

| 符号 | 位置 | 一句话 |
|------|------|--------|
| `refreshJoinableQuickMatchIndexRoom` | `DpRoomLobbySync.java:41` | 单房索引 add/remove |
| | `DpRoomServiceImpl.java:184,286,782,1135,1320,1346,1782` | 建房/候补/exit/包装 |
| | `DpRoomServiceImpl.java:1153` | joinRoom：**调用被注释掉** |
| `refreshJoinableQmIndexThenSyncLobby` | `DpRoomLobbySync.java:53` | 索引 + upsert 大厅 |
| | `DpRoomServiceImpl.java:291,409,517,527,568,1274,1978,2110` | 踢人/易主/开局/newHand/邀请 |
| | `DpRoomHeartbeatScheduler.java:199,231` | settled 开局；lobbyDirty |
| | `DpRoomQuickMatchBridge.java:116,166` | 快匹进房 |
| `syncLobbyForRoomId` | `DpRoomLobbySync.java:68` | 仅 upsert，无索引 |
| | `DpRoomServiceImpl.java:345,783,1137,1784` | 建房；join 外盒 ok；exit 条件 |
| `finalizeHallAfterRoomRemoved` | `DpRoomLobbySync.java:58` | 聊天 flush + 索引删 + 大厅删 + WS 关 |
| | `DpRoomServiceImpl.java:294,369,515,939,1779` | remove/exit/giveOwner 摘房 |
| `rebuildJoinableQuickMatchIndex` | `DpRoomLobbySync.java:37` | 全量 rebuild |
| | `DpRoomServiceImpl.java:276,281` | PostConstruct |
| `lobbyTouch` | `DpRoomServiceImpl.java:441,1804,1835,1877,1783` | exit 是否 sync 大厅 |
| `getSpectators` | `DpRoomBO.java:186` | 观众列表 |
| | `DpRoomServiceImpl.java` 多处 | 进出/踢人/newHand |
| | `DpRoomHeartbeatScheduler.java:76,117` | 空房判断；踢观众 |
| `waitNextHand` | `DpRoomBO.java:96` | 候补列表 |
| | `DpRoomServiceImpl.java:1088,1217,1296,2143` | join/remove/拉上桌 |
| | `DpQuickMatchRoomSemantics.java:95` | 快匹计数 |
| `touchSpectatorPresence` | `DpRoomBO.java:189` | 观众保活 |
| | `DpRoomServiceImpl.java:1101,1231,1262,1687,1723,2125,3111` | join/kick/prune/newHand/heartbeat |
| `removeSpectatorPresence` | `DpRoomBO.java:195` | exit 清理 |
| | `DpRoomServiceImpl.java:1816,2168` | exit；newHand 上桌 |
| `presenceMarkInGameHuman` | `DpRoomServiceImpl.java:840` | friendPresence IN_GAME |
| `presenceTryMarkIdleFullyLeft` | `DpRoomServiceImpl.java:851` | 条件 IDLE |
| `presenceMarkIdleAllHumansOnRoomSnapshot` | `DpRoomServiceImpl.java:880` | 摘房批量 IDLE |
| `broadcastIfSubscribed` | `DpRoomHeartbeatScheduler.java:229` | 1s WS 推送 |
| `shutdownSubscriptionsForRoom` | `DpRoomLobbySync.java:65` | 摘房关 WS |
| `flushRoomToDatabase` | `DpRoomLobbySync.java:62` | 摘房写聊天 |
| `reconcileLobbyWithRuntimeRoomIds` | `DpRoomLobbyReconcileScheduler.java:62` | 定时清幽灵房 |
| `attemptQuickMatchPairing` | `DpRoomQuickMatchBridge.java:80` | 配对入口 |
| | `DpRoomServiceImpl.java:258,785,1793` | 建房/exit 后 |
| `joinRoomMutateAssumeLocked` | `DpRoomServiceImpl.java:1081` | 进房写锁内本体 |
| `applyReadyNextHandWhileLocked` | `DpRoomServiceImpl.java:1285` | 候补加锁 |
| `checkAndStartNextHandAfterSettleReturning` | `DpRoomServiceImpl.java:1612` | settled 齐人 newHand |
| `newHandWithoutLobbyUpsert` | `DpRoomServiceImpl.java:1986` | 新一手（内不调大厅） |
| `tryUnregisterEmptyRoomAssumeLocked` | `DpRoomRegistry.java:44` | 无活人且无观众 remove |
| `findRoomContainingNickname` | `DpRoomServiceImpl.java:1395` | 观众+桌上查房 |
| `joinRoomInviteAsSpectator` | `DpRoomServiceImpl.java:1205` | 观众进房+refresh+sync |

---

## 第 3 章 建议的「核心判定」（大白话 + 如果…就…）

**怎么用**：业务改完房间快照后，先问「下面哪条判定从 false 变 true 了？」，再只做对应动作；不要在每个方法里复制一长串 if。

口径以第 2 章、第 1 章词典为准；快匹空位见 `DpQuickMatchRoomSemantics`，大厅 `playerSize` 见 `DpRoomHallServiceImpl#toSummary`。

---

### 3.1 可进房索引（快匹）

#### 建议核心判定（一句话）

**「会影响快匹空位口径的房间快照变了」** — 下面任一变化都应视为 true：

| 算进判定 | 不算进判定 |
|----------|------------|
| 桌上**非离座**席位数变（含 `leftThisHand` 仍占位、真人被 tick 踢掉） | 仅观众席人数变（观众不占快匹空位） |
| `waitNextHand` 人数变（候补占空位） | 仅筹码多少变、仅 ready 位变 |
| 房间从 `roomMap` 消失或新建 | 仅 `isPlaying`/阶段文字变但席位数不变 |
| 有密码 ↔ 无密码（无密码才进索引） | 大厅列表展示字段但**不改变**空位公式 |
| 满员 ↔ 尚有空位（`rawVacancy` 公式变） | 仅 rebuy / bet 后筹码变 |

空位公式（B 章语义）：`eligibleOccupants = 桌上非离线有效座 + waitNextHand.size()`，再与 `maxPlayers` 比得 `rawVacancy`；`rawVacancy > 0` 且无密码才留在索引。

#### 统一动作（大白话）

**刷新**这个 `roomId` 在可进房索引里的条目（`addOrRefresh` 或按 shortage 重算）；**房没了或不再可进**就从索引删掉。

#### 推荐调用时机（做完业务后）

1. **如果**判定变 true **且**房还在 `roomMap` → 先算判定，变了再 `refreshJoinableQuickMatchIndexRoom`（或包装方法）。
2. 建房、加 Bot 候补、候补增减、踢人离座、开局、新一手、易主、非摘房退房 → 通常要刷。
3. **观众**经 `joinRoom` 进房 → 理想也应刷（空位对外口径可能仍显示可进）；现状不调，见对比表。
4. **邀请/跟随观众**进房 → 应刷；现状会刷。
5. 摘房 / `finalizeHall` → 索引删除，不要只 `roomMap.remove`。
6. 快匹 `poll` 进房失败 → 补回索引（协调器约定）。
7. 全进程漂移怀疑 → `rebuildJoinableQuickMatchIndex`（启动或运维级，非每笔业务）。
8. `readyNextHand` 只减空位、**不改**大厅人数时 → **仍要刷索引**，不要误以为「大厅没动就不用刷索引」。

#### 与现状对比

| 理想规则 | 当前是否一致 | 不一致时在哪 |
|----------|--------------|--------------|
| 判定变 true → 单刷索引 | 多数一致 | `readyNextHand` / `cancelReadyNextHand` / `addBot`：`refreshJoinableQuickMatchIndexRoom`（~1320, ~1346） |
| 判定变 true → 索引+大厅 | 部分路径只刷索引 | 上三者**故意不** `syncLobby`（~1318 注释）— 符合设计，勿当漏刷索引 |
| 观众 `joinRoom` 也要刷 | **不一致** | `joinRoom` ~1153：`refreshQmIndexAfterJoinOutcomeOutsideRoomLock` **被注释** |
| 准备超时 prune 桌席变空位 | **不一致** | `handleReadyTimeout` → `pruneSettledTableForReadyTimeout`（~3051）**未** refresh |
| 退房非摘房必刷索引 | 一致 | `exitRoom` ~1782 |
| 邀请观众进房刷索引 | 一致 | `joinRoomInviteAsSpectator` → `refreshJoinableQmIndexThenSyncLobby` (~1274) |

---

### 3.2 大厅房间摘要（DB + 缓存）

#### 建议核心判定（一句话）

**「大厅列表上会让玩家看到的房间信息变了」** — 玩家扫房/搜房时关心的展示字段变了才刷 `dp_room_lobby`。

| **算**（应 upsert） | **不算**（不必为大厅单独刷） |
|---------------------|------------------------------|
| 桌上人数 `playerSize`（非 `leftThisHand` 的席位数） | 仅 `waitNextHand` 增减（列表人数不含候补） |
| 观众进/出（若产品要在列表体现观战人数—**当前实现不算进 playerSize**） | 仅观众席变但**政策是不展示** → 当前可不算 |
| `waitNextHand` 变导致**桌上**人数变（newHand 拉人上桌） | 仅筹码、底池、阶段、ready 位 |
| 房主昵称变 | 仅 WS 推送内容 |
| 有密码 / 公开状态变 | 仅 presence（好友列表用，不进大厅行） |
| `isPlaying` / 是否开局（列表「进行中」） | 快匹队列位次 |
| 房间被摘 / 应从列表消失 | 索引 shortage 数字（大厅不展示「缺几人」） |

**对照 B 口径**：大厅 `player_count` = 桌上非离座席，**不含观众、不含候补**；与快匹空位公式不同。

#### 统一动作（大白话）

**更新** `dp_room_lobby` 这一条摘要（`upsertRoomSummary`）；房摘了则 **delete**（走 `finalizeHallAfterRoomRemoved`）。

#### 推荐调用时机

1. 踢人、易主、开局、新一手、邀请进房（含观众）→ 索引+大厅一并 `refreshJoinableQmIndexThenSyncLobby`。
2. 建房 → `syncLobbyForRoomId`（~783）。
3. 退房 → **仅当** `lobbyTouch` 或房主字段变才 `syncLobby`（~1783）；纯观众退出可能只刷索引。
4. 候补 `readyNextHand` → **理想**：不必刷大厅（人数不变）；与现状一致。
5. `joinRoom` 普通进房 → 理想：若上桌应刷；若仅观众理想看产品—**现状不刷**。
6. 准备超时 prune → 理想：桌席变空应刷；**现状不刷**（~3051）。
7. 定时 reconcile → 只删幽灵行，不 upsert 内存房（A26）。

#### 与现状对比

| 理想规则 | 当前是否一致 | 不一致时在哪 |
|----------|--------------|--------------|
| 桌席数/房主/开局态变 → upsert | 多数一致 | `kick`/`transferOwner`/`startGame`/`newHand`/`joinRoomInvite` |
| 仅候补变 → 不 upsert | 一致 | `applyReadyNextHandWhileLocked` ~1318 |
| 观众 `joinRoom` 不展示人数 → 可不 upsert | **一致（按当前产品）** | `joinRoom` 不调 sync（~1153） |
| 观众退出若不影响桌席 → 可不 upsert | **一致** | `exitRoom` `lobbyTouch=false` 时只索引 (~1783) |
| prune 后桌席空/人进观众 → 应 upsert | **不一致** | `handleReadyTimeout` ~3051 |
| 摘房 → delete 大厅行 | 一致 | `finalizeHallAfterRoomRemoved` |

---

### 3.3 观众席集合

#### 建议核心判定（一句话）

**「观众席名单或某观众的在席心跳状态变了」** — `spectators` 列表或 `spectatorLastPresenceMs` 需要增删改。

#### 应触发的情况（与 A 2.3 对齐）

| 情况 | 期望动作 |
|------|----------|
| 已开局 `joinRoom` | 从 `waitNextHand` remove；`spectators.add` + `touchSpectatorPresence` |
| 邀请/跟随进房 | 同上；未开局已上桌则可能不占观众 |
| `exitRoom`（观众或踢到观众后退出） | `spectators.remove` + `removeSpectatorPresence` |
| 踢人 `kick` | 离座后进观众 + touch |
| 1s tick 观众心跳超时 | `exitRoom` 链清理观众 |
| `newHand` | 从 wait 上桌的 `removeSpectatorPresence`；离座真人 `touchSpectatorPresence` 进观众 |
| 筹码不足 / prune | `pruneSettledTableForReadyTimeout` 把人挪观众 + touch |
| HTTP `heartbeat` | 若在观众列表则 `touchSpectatorPresence` |
| **不应**只靠改 `players` 就默认观众列表已对 | newHand 后 wait 残留、僵尸位与观众并存需各自规则 |

#### 统一动作（大白话）

按规则 **增删观众昵称**，真人要 **touch** 保活时间；退出要 **removeSpectatorPresence** 清 map。

**现状**：没有单一 `syncSpectators(roomId)` 入口，逻辑分散在：

- `joinRoomMutateAssumeLocked` / `joinRoomInviteAsSpectator`（~1081, ~1205）
- `exitRoomApplyMutationWhileHoldingRoomLock`（~1771）
- `kickOnePlayerWithoutLobbySync`（~524）
- `getAllCanPlayer` / `newHand`（~2114+）
- `pruneSettledTableForReadyTimeout`（~1663）
- `DpRoomHeartbeatScheduler` 踢观众（~117）
- `heartbeat`（~3098）

**建议以后**：封成「观众席变更」小方法，由第 3 章判定驱动调用；本期只记在 checklist。

#### 与现状对比

| 理想规则 | 当前是否一致 | 不一致时在哪 |
|----------|--------------|--------------|
| 进房已开局 → 观众+touch | 一致 | `joinRoomMutateAssumeLocked` |
| 邀请进房 → 观众+touch+刷索引大厅 | 一致 | `joinRoomInviteAsSpectator` |
| 普通 join 不刷索引 | 见 3.1 | ~1153 |
| newHand 拉 wait 清观众 map | 一致 | ~2168 `removeSpectatorPresence` |
| 准备超时挪观众 | 一致（观众变） | ~1663；但**副作用**索引/大厅见 3.1/3.2 |
| 空房摘除要求无观众 | 一致 | `tryUnregisterEmptyRoomAssumeLocked` |

---

### 3.4 其它维护面（从 B 2D / 第 1 章提炼）

每条一行：**什么变了 → 该做什么**。不必合成一个判定，避免重复写 20 个细 if。

| 维护面 | 什么变了 → 该做什么 |
|--------|---------------------|
| **内存 `roomMap`** | 建房 put；空房且无观众 remove；摘房必须成套 `finalizeHall`，不要只 remove。 |
| **好友 presence** | 真人进房 → `presenceMarkInGameHuman`；完全离开且不在别房非僵尸 → `presenceTryMarkIdleFullyLeft`；摘房 → `presenceMarkIdleAllHumansOnRoomSnapshot`。 |
| **对局 WS** | 摘房 → `shutdownSubscriptionsForRoom` + `roomClosed`；平时靠 1s `broadcastIfSubscribed`，下注后不立刻推。 |
| **快匹 WS** | 入队/出队/匹配 → `DpQuickMatchPushService` notify；与房间索引刷新分开判。 |
| **快匹等待队列** | `createRoom`/`joinRoom` → 取消本人 `cancelDefaultQuickMatchWait`；入队/配对在 `DpRoomQuickMatchBridge` + `DpQuickMatchPairingCoordinator`。 |
| **快匹配对尝试** | 公开房退房后、建房后、协调器 flush 后 → `attemptQuickMatchPairing`（持锁顺序见 bridge 注释）。 |
| **局内聊天 buffer** | 摘房 → `flushRoomToDatabase`（在 `finalizeHall` 内）；只删内存不 flush 会丢消息。 |
| **大厅幽灵行** | 定时 `reconcileLobbyWithRuntimeRoomIds`：DB 有、内存无 → delete；不替代单笔 upsert。 |
| **`registeredDpUserIdByNickname`** | 进房/候补登记；newHand 拉候补上桌补 id。 |
| **`carryInChips`** | 真人离座/退房结算后清除；rebuy 合并。 |
| **Bot/LLM 全局 store** | 与索引无关；createRoom 路径单独看 NPC 配置。 |

---

## 第 4 章 开发自检 Checklist（可打印）

用法：改完房间相关代码，逐项打勾。每条 ≤2 行；细节回第 2 章矩阵对应行。

### 桌上玩家 `players`

- [ ] **我改动了桌上玩家吗？**（加减人、离座、筹码、ready、踢人、发牌态）  
  → 大厅刷了吗？（`syncLobby` / `refreshJoinableQmIndexThenSyncLobby`）详见 **2.2** A4/A5/A14/A15/A18/A19  
  → 可进房索引刷了吗？详见 **2.2** 同上 + A10/A11/A12  
  → presence：进房 IN_GAME、完全离开 IDLE？详见 **2.2** A2/A4/A18

- [ ] **进行中让人 exit，人还在 `players` 里当僵尸位？**  
  → 大厅人数可能仍占座；settle 后 `removeLeftThisHandZombiesAfterHand` + IDLE。详见 **2.3** 僵尸位、**2.2** A4/A18

### `waitNextHand`

- [ ] **我改动了候补列表吗？**（ready/cancel/Bot 批量）  
  → **必须**刷可进房索引；**不必**刷大厅人数。详见 **2.2** A10/A11/A12、**2.3** 候补只刷索引

- [ ] **已开局 join 会把人挪出候补吗？**  
  → `waitNextHand.remove` 后再进观众。详见 **2.2** A2

### 观众席 `spectators`

- [ ] **我改动了观众席吗？**（进/出/踢到观众/touch/超时踢）  
  → 名单与 `spectatorLastPresenceMs` 成对维护了吗？详见 **2.2** A2/A3/A4/A5/A20/A21、**2.3** 观众 join 不刷索引

- [ ] **仅观众变化、桌席不变？**  
  → 快匹索引理想应刷（现状 `joinRoom` 可能漏）；大厅可不刷。详见 **3.1** 对比表、**2.2** A2

### 新一局 / `newHand`

- [ ] **我开了新一手 / `newHand` 吗？**  
  → `waitNextHand` 拉人上桌后条目删了吗？残留昵称靠 `readyNextHand` 防刷。详见 **2.3** newHand 与 wait

- [ ] **newHand 后观众该清的清了吗？**（上桌 `removeSpectatorPresence`；离座真人进观众 touch）  
  → 索引+大厅刷了？详见 **2.2** A15/A16

- [ ] **settled 自动开下一手？**  
  → tick 里 `refreshJoinableQmIndexThenSyncLobby`（~199）与 `checkAndStartNextHand` 顺序对吗？详见 **2.2** A16/A21

### 房主 / 摘房

- [ ] **我改了房主或触发 `giveOwner` 吗？**  
  → 索引+大厅；若空房摘房走 `finalizeHall` 三件套。详见 **2.2** A7/A8

- [ ] **我摘房或空房定时清理了吗？**  
  → `finalizeHall`：聊天 flush、索引删、大厅删、WS 关、presence 批量 IDLE。详见 **2.2** A9、**2.3** 摘房三件套

### 开局 / 准备 / 超时

- [ ] **我 `startGame` 了吗？**  
  → 索引+大厅；公开房 `attemptPairing`？详见 **2.2** A14

- [ ] **我动了 `toggleReady` 吗？**  
  → 一般只变 ready 位，不必刷索引/大厅。详见 **2.2** A13

- [ ] **我改 `handleReadyTimeout` / prune 桌席了吗？**  
  → 观众席变了；**现状可能漏**索引/大厅—要手动补或等 tick `lobbyDirty`。详见 **2.2** A17、**2.3** handleReadyTimeout

### 快匹 / 匹配

- [ ] **我加快匹进房/入队/配对了吗？**  
  → `defaultQmLock` / `dpQuickMatchAssignmentLock` 持了吗？索引刷了？要 `attemptPairing` 吗？详见 **2.2** A22～A25

- [ ] **索引 `poll` 后进房失败了吗？**  
  → 补 `addOrRefresh`。详见 **2.3** 索引 poll

- [ ] **`createRoom` / 普通 `joinRoom` 取消本人排队了吗？**  
  → `cancelDefaultQuickMatchWait`。详见 **2.2** A1/A2

### 心跳 / 定时器

- [ ] **我只改 HTTP `heartbeat` 吗？**  
  → touch 观众；一般不刷索引/大厅。详见 **2.2** A20

- [ ] **我动 1s tick 逻辑吗？**  
  → 踢人/exit/空房/lobbyDirty/settled 开局/broadcast 分支对齐。详见 **2.2** A21

### 大厅对齐 / 社交进房

- [ ] **我只跑 lobby reconcile 任务？**  
  → 只删幽灵行，不代替业务 upsert。详见 **2.2** A26

- [ ] **好友跟随/邀请进房？**  
  → 同 A3：观众+索引+大厅+presence。详见 **2.2** B1

### 对局内操作（bet/fold/settle/rebuy）

- [ ] **我 bet/fold/settle/rebuy 了吗？**  
  → 通常不刷索引/大厅；settle 后僵尸 IDLE；WS 靠下一秒 broadcast。详见 **2.2** A18/A19

---

### 第 4 章覆盖核对（相对 2.2「变」格）

| 矩阵「变」涉及列 | Checklist 分组 |
|------------------|----------------|
| players | 桌上玩家；bet/settle/rebuy；newHand；handleReadyTimeout |
| waitNextHand | 候补；join 已开局；newHand |
| spectators | 观众席；newHand；超时 prune |
| roomMap | 摘房/空房 |
| 可进房索引 | 桌上/候补/快匹/摘房各节 |
| 大厅摘要 | 桌上/开局/摘房/invite vs join |
| presence | 桌上玩家；摘房 |
| WS/推送 | 摘房；快匹；bet 靠 tick |
| 快匹队列/配对 | 快匹专节；create/join 取消排队 |
| A1～A26、B1 行为行 | 上表各组均至少命中一次 |

---

## 第 5 章 技术债与分期建议（只说话，不写代码）

### 现状最该先统一的 scattered 调用（5～10 条）

1. **`refreshJoinableQuickMatchIndexRoom` vs `refreshJoinableQmIndexThenSyncLobby`** — 有的路径只刷索引、有的索引+大厅、有的两个都不刷（`joinRoom` ~1153、`handleReadyTimeout` ~3051）；新人最易 copy 错。
2. **`exitRoom` 的 `lobbyTouch` 门槛** — 索引总刷、大厅条件刷；与「观众不占大厅人数」政策绑在一起，文档外难记。
3. **观众进房三条路径** — `joinRoom` / `joinRoomInvite` / 快匹 `quickMatchJoin` 刷新策略不一致（2.3 已记风险）。
4. **观众席增删 touch** — 分散在 6+ 方法，无统一「观众变更」入口，review 时易漏 `removeSpectatorPresence`。
5. **`finalizeHall` 三件套** — 摘房路径多（remove/exit/giveOwner/tick），任何只 `roomMap.remove` 都是高危漏网。
6. **`presenceTryMarkIdleFullyLeft` vs 多房/僵尸位** — IDLE 时机与 exit/newHand/settle 交叉，靠注释理解。
7. **准备超时 `handleReadyTimeout`** — 注释写外层 upsert，实现不刷；与 tick `lobbyDirty` 隐式耦合。
8. **快匹索引 `poll` 失败回滚** — 约定在协调器，进房失败路径要人工对清单。
9. **WS 推送依赖 1s tick** — 改桌席不刷大厅但客户端靠 broadcast 对齐，测试窗口要空 1 秒。
10. **`DpRoomLobbyReconcileScheduler`** — 多实例必须关；与业务 upsert 职责边界要在运维文档写死。

### 本期不做代码重构；下阶段第一步建议

1. **先写死一张「副作用路由表」**（可继续用本文第 3 章），Code Review 只查表，不先动 Java。
2. **第二步只抽一个「薄门面」**（例如 `DpRoomLobbySync` 旁增加 `afterRoomMutation(roomId, flags)`），把 10 条 scattered 调用**搬地址不改行为** — 行为与现网一致，单测对照 2.2 矩阵回归。
3. **第三步再修已知不一致** — 优先：`joinRoom` 观众是否刷索引（产品确认）、`handleReadyTimeout` 后索引/大厅（与 tick 去重）。
4. **观众席第四** — 合并 touch/add/remove 入口，newHand/exit/kick 只调一处。
5. **最后才动 WS 即时推送** — 与索引/大厅解耦，避免大爆炸 refactor。

---

*Agent C 续写：第 3～5 章（2026-05-22）*
