# 对局与房间：REST、内存模型与核心流程

> **核对日期**：2026-05-25  
> **权威来源**：`DpRoomServiceImpl`、`DpRoomRegistry`、`DpRoomController`、`DpRoomHallServiceImpl`  
> **Status**: maintained

---

## 1. 架构要点

| 概念 | 实现 | 说明 |
|------|------|------|
| 房间热状态 | `DpRoomRegistry.roomMap` → `ConcurrentHashMap<String, DpRoomBO>` | **单机 JVM** 唯一真相；**不在 Redis** |
| 写路径串行 | `synchronized (DpRoomBO r)` | 同房下注/进退/结算互斥 |
| 大厅摘要 | MySQL `dp_room_lobby` + Redis 版本缓存 | `DpRoomHallServiceImpl`；与内存对齐见 `DpRoomLobbyReconcileScheduler` |
| 推送 | `DpGameRoomPushService` | 进程内 WS 订阅；见 [WEBSOCKET.md](./WEBSOCKET.md) |
| 牌谱落库 | `DpHandHistoryPersistServiceImpl` | 结算后异步；见 [DP_PERSISTENCE_README.md](./DP_PERSISTENCE_README.md) |

**多实例**：`roomMap` 与 WS 会话表均不跨节点；生产多副本宜关闭 `mgdemoplus.dp-lobby-reconcile-enabled` 或引入分布式房间方案。

**改字段副作用**：见 [refactor/room-mutation-side-effects.md](./refactor/room-mutation-side-effects.md)。

---

## 2. REST 映射（`DpRoomController`，前缀 `/dpRoom`）

鉴权：除下表白名单路径外，需 JWT；`joinRoom2` / `quickMatch2` / `quickMatchCancel2` 要求 **token subject == `nickname` 参数**。

| 方法 | 路径 | 服务方法 | 备注 |
|------|------|----------|------|
| POST | `/createRoom` | `createRoom` | 建房；公开房末尾可 `attemptQuickMatchPairing` |
| POST | `/joinRoom` | `joinRoom` | 旧入房 |
| POST | `/joinRoom2` | `joinRoom` | 同上 + JWT 昵称校验 |
| POST | `/quickMatch2` | `quickMatchJoinQueueOrImmediate` | 快匹；见 [dp-quick-match-flow.md](./dp-quick-match-flow.md) |
| POST | `/quickMatchCancel2` | `cancelDefaultQuickMatchWait` | 取消默认队列 |
| POST | `/exitRoom` | `exitRoom` | 未开局删房/移交；进行中 `leftThisHand` 僵尸位 |
| POST | `/toggleReady` | `toggleReady` | 开局前准备 / 局后下一手 |
| POST | `/readyNextHand` | `readyNextHand` | 观众预约下一局 |
| POST | `/cancelReadyNextHand` | `cancelReadyNextHand` | |
| POST | `/startGame` | `startGame` | 首轮 → `newHand` |
| POST | `/newHand` | `newHand` | 拉 `waitNextHand`、洗牌、庄盲 |
| POST | `/bet` | `bet` | 跟注/加注/All-in |
| POST | `/fold` | `fold` | |
| POST | `/heartbeat` | 更新 `lastHeartBeat` / 观众 `spectatorLastPresenceMs` | **非** WS 断线退房 |
| POST | `/kickPlayer`、`/kickPlayersBatch` | 踢人 | 房主 |
| POST | `/transferOwner` | `giveOwner` | |
| POST | `/rebuy` | 补码 | |
| POST | `/add*Bot`、`/addRuleNpcBatch`、`/addCustomNpcBatch`、`/addLlmBot`、`/addLlmGlobalBot` | NPC/LLM 座位 | 见 `docs/ai/npc-engine/README.md` |
| GET | `/getNowRoom` | `getRoomSnapshotForViewer` | **permitAll**；按 `nickname` 过滤底牌 |
| GET | `/getAllRooms2` | 内存房间 id 列表 | **permitAll** |
| GET | `/publicRooms`、`/publicRooms/query` | `DpRoomHallService` | **需 JWT**；读 DB+缓存 |
| GET | `/{roomId}/chat/recent` | 局内聊天近期 | JWT |

白名单细节：[JWT.md](./JWT.md)。

---

## 3. 生命周期（方法链）

### 3.1 开局前

```text
createRoom → joinRoom / joinRoom2
         → toggleReady（桌上） / readyNextHand（观众预约）
         → startGame → newHand
```

- **`getAllCanPlayer`**（`newHand` 内）：从 `waitNextHand` 拉人、剔除筹码不足、更新观众。  
- **`checkAndStartNextHandAfterSettle`**：`toggleReady` 局后路径，人齐开下一手。

### 3.2 进行中

```text
bet / fold → moveToNextValidActor → autoAdvanceIfRoundFinished
          → advanceStage → findFirstActorAfterDealer（新街）
```

- **推进**：业务路径主动推进 + `DpRoomHeartbeatScheduler`（1s）处理超时/离线/NPC。  
- **`exitRoom`（进行中）**：`leftThisHand=true`，不立刻从 `players` 删除；结算后 `removeLeftThisHandZombiesAfterHand`。

### 3.3 结算与持久化

- **`autoSettle`**：分配底池 → `DpHandHistoryObservedImpl` 归档 → `DpHandHistoryPersistServiceImpl#save`（异步队列，失败仅 warn）。  
- **周榜/统计**：Flyway V4–V7 相关表由结算链更新（周榜 REST 见 `DpLeaderboardController`）。

---

## 4. 核心 BO 字段（维护速查）

### `DpRoomBO`

| 字段 | 含义 |
|------|------|
| `players` | 本局桌上 `DpPlayer`；`leftThisHand` 占座不计入部分大厅统计 |
| `waitNextHand` | 下一局预约昵称列表；快匹空位计算 **含** 此列表 |
| `spectators` / `spectatorLastPresenceMs` | 观众席与保活 |
| `playing` / `currentStage` | 是否进行中；`preflop`…`showdown` |
| `currentActorIndex` / `currentBetToCall` / `lastRaiseIncrement` | 行动与 NL 最小加注规则 |
| `lastDealerIndex` | 庄位轮换 |

### `DpPlayer`（节选）

| 字段 | 含义 |
|------|------|
| `lastHeartBeat` | 桌上 HTTP 心跳 |
| `leftThisHand` | 本手离座/弃牌后僵尸标记 |
| `isBot` / 昵称前缀 | 规则 Bot / `BOT_LLM_*` |

---

## 5. 大厅与内存对齐

- **写摘要**：建房/进退/人数变化 → `DpRoomLobbySync` / `DpRoomHallService#upsertRoomSummary`。  
- **对齐任务**：`lobby/DpRoomLobbyReconcileScheduler`（`mgdemoplus.dp-lobby-reconcile-enabled`、`dp-lobby-reconcile-ms`）。  
- **公开房列表**：不读 `roomMap` 全表扫描，读 `dp_room_lobby` 分页。

---

## 6. 快匹（摘要）

- **协调器**：`quickmatch/pairing/DpQuickMatchPairingCoordinator#attemptPairing`。  
- **索引**：`JoinableQuickMatchRoomIndex`（公开、有空位，按缺人数分桶）。  
- **队列**：`DpRoomQuickMatchBridge` 的 `defaultQmWaiters` + `defaultQmLock`；配对外层 `dpQuickMatchAssignmentLock`。  

全文：[dp-quick-match-flow.md](./dp-quick-match-flow.md)、[dp-quick-match-concurrency.md](./dp-quick-match-concurrency.md)。

---

## 7. 德州规则（产品语义摘要）

- **牌型**：皇家同花顺 → 高牌（标准德州）。  
- **街**：preflop → flop → turn → river → showdown。  
- **行动**：call / raise / fold / all-in；街结束条件见 `autoAdvanceIfRoundFinished`。  
- **庄盲**：`lastDealerIndex` 轮换；≥2 人有 SB/BB；翻前从大盲下家起行动。

对局页 UI 与 `seatIndex`：[RoomUi.md](./RoomUi.md)。

---

## 8. 测试类（回归入口）

`DpRoomServiceImpl` 相关测试以 `src/test/java` 下 `*Room*`、`*QuickMatch*` 为准；改锁序或快匹后优先跑快匹与进退房用例。

---

## 9. 延伸阅读

| 文档 | 内容 |
|------|------|
| [WEBSOCKET.md](./WEBSOCKET.md) | `/ws/dp-game`、心跳与 WS 分工 |
| [dp_friend_mailbox_mvp.md](./dp_friend_mailbox_mvp.md) | 好友、邀请、私信、SSE |
| [DP_PERSISTENCE_README.md](./DP_PERSISTENCE_README.md) | `dp_observed_hand_*` |
| [refactor/room-mutation-side-effects.md](./refactor/room-mutation-side-effects.md) | 副作用矩阵 |
