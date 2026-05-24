# 对局内与附属业务能力审计（Agent 04）

**范围**：单桌德州对局阶段机（盲注、行动、摊牌、结算、下一手）、规则型 NPC、可选 `BOT_LLM` 座位；观测牌谱内存构建、落库与 REST 查询；房间内 BGM 同步与对局的耦合关系。  
**方法**：只读代码与现有文档，未改业务代码；**不**执行 git commit / push。  
**主实现类**：`DpRoomServiceImpl`（状态与推进）、`DpGameRoomPushService`（WS 下行）、`DpHandHistoryObservedImpl` + `DpHandHistoryPersistServiceImpl`（牌谱）、`DpNpcEngine` + `DpLlmNpcDecisionService`（NPC）、`DpHandHistoryController` / `DpHandHistoryServiceImpl`（查询）。

**非主业务（一行）**：`controller.demo` 下的 Redis 实验、上传、`StudentController`、`MovieController` 等为示例/脚手架，与对局房、牌谱、WS 主链路无关。

---

## 1. 对局：阶段 / 事件与「广播 / 持久化」挂钩

### 1.1 阶段与推进（机要）

| `currentStage` / 相关 | 含义与典型触发 | 推进 / 出口 |
|----------------------|----------------|-------------|
| **开局前** `playing=false` | 建房、凑座、`toggleReady` 翻准备 | `startGame` → `playing=true`，首手见 `newHand` |
| **preflop** | `newHandWithoutLobbyUpsert`：洗牌、发洞牌、≥2 人下 SB/BB，`findFirstActorAfterDealer` 定首动 | `bet` / `fold` / NPC 行动 → `moveToNextValidActor` → `autoAdvanceIfRoundFinished` |
| **flop / turn / river** | `advanceStage` 从牌堆发公牌；`noFurtherBettingPossible` 时 `currentActorIndex=-1` 连推 | 同上；每进一条街 `observedHandService.recordBoardState` |
| **showdown** | `advanceStage` 在 river 后进入；`calculatePots` 后边池就绪 | 立即 `autoSettle`（非「等房主点」的慢摊牌） |
| **settled** | `autoSettle*` 末尾写入；`readyDeadline` 约 30s；全员准备 / 机器人自动准备 / 候补 / 超时 | `checkAndStartNextHandAfterSettleReturning` → `newHandWithoutLobbyUpsert` 或保持等待 |
| **下一手** | `newHandWithoutLobbyUpsert` 递增 `currentHandSeed`，重置手内状态 | 回到 **preflop** |

关键链（代码顺序）：`moveToNextValidActor` →（`currentActorIndex == -1` 时）`autoAdvanceIfRoundFinished` → `advanceStage` 循环直至出现新行动者或 **showdown→autoSettle→settled**。

### 1.2 事件级：持久化（观测牌谱）

| 事件 | 触发点（方法） | 说明 |
|------|----------------|------|
| 开手构建 | `newHandWithoutLobbyUpsert` → `observedHandService.beginHand(r)` | `Key = (roomId, currentHandSeed)` |
| 盲注与座位快照 | SB/BB 扣款后 `observedHandService.recordBlind`；随后 `markHandReadyAfterBlinds` | 与 `DpNpcStreetActionLog` 并行 |
| 行动 | `bet` / `fold` 内 `observedHandService.recordBetLikeAction` / `recordFold` | 含街、档位、底池前快照等 |
| 公牌 | `advanceStage` 发 flop/turn/river 后 `recordBoardState` | showdown/settled 不追加 board |
| 结算前池 | `autoSettleNormalPotShowdownPath` 清 `pots` 前 `capturePotsBeforeClear` | 供有效主池统计 |
| 归档与入库 | `autoSettle*` 末尾 `finalizeHand` → 非 null 则 `observedHandPersistService.save(rec, r)`；再 `clearHand` | `finalizeHand`：桌上 **≤1 名玩家** 时 **返回 null 不落库** |

### 1.3 事件级：WebSocket 广播（与 Agent 2 交叉引用）

**Agent 2（何时推 WS）** 对应仓库说明见 `docs/WEBSOCKET.md` 与实现 `DpGameRoomPushService`：

- **房间快照**：`DpRoomServiceImpl` 构造器里 **每 1 秒** 对 `roomMap` 每个房间跑 `runGlobalSecondTickForSingleRoom`，在多数路径末尾调用 `gameRoomPushService.broadcastIfSubscribed(roomId)`。  
  - **重要**：`POST /dpRoom/bet`、`/fold` 等 **不** 在请求线程内直接调用 `broadcastIfSubscribed`；客户端依赖 **下一拍 1s tick**（或自身轮询）看到最新状态。  
  - **去重**：`broadcastIfSubscribed` 按 **每个 `WebSocketSession` 上次下发的完整 JSON 字符串** 比较，相同则跳过该连接。  
- **例外下行（与对局状态无强一致事务）**：  
  - 聊天 `chatSend` → `broadcastRawJsonToRoom`  
  - BGM `roomMusicSync` → 存 `lastRoomMusicJson` + `broadcastRawJsonToRoom`；新连接在 `sendInitialSnapshot` 后补一帧 `roomMusic`  
- **摘房 / 踢人关连**：`finalizeHallAfterRoomRemoved` → `shutdownSubscriptionsForRoom`；桌上玩家心跳踢出 → `shutdownSubscriptionsForNicknameInRoom`（发 `roomClosed`）。

### 1.4 房间何时结束（与 Agent 1 交叉引用）

**Agent 1（房间何时从「可玩实体」消失）** 在代码上对应：

- **空房摘表**：`tryUnregisterEmptyRoomAssumeLocked` — 无 **桌上活人** 且无 **观众** 时 `roomMap.remove`；随后 `finalizeHallAfterRoomRemovedWithPresenceSnapshot` / `finalizeHallAfterRoomRemoved`：摘快匹索引、删大厅摘要、**关断该房全部 WS**。  
- **触发源示例**：全局 tick 中 `removeDesertedRoomInGlobalTickIfNoLiveHumans`；`exitRoom` 等路径在持 `synchronized(r)` 下检测到空房。  
- **与对局阶段关系**：摘房与 `currentStage` 无单独「终局」事件；**人走光**即拆房，无论是否在 `settled` 或 `playing`。

---

## 2. 牌谱：写入、参与者表、查询 API 前置条件

### 2.1 落库触发

- **唯一业务触发**：`DpRoomServiceImpl.autoSettle` / `autoSettleZeroPotAndEnterSettledShortcut` / `autoSettleNormalPotShowdownPath` 在转入 **settled** 流程中调用 `observedHandPersistService.save`。  
- **失败策略**：`DpHandHistoryPersistServiceImpl.save` 捕获异常只打日志，**不**中断游戏。

### 2.2 表与参与者

- **`dp_observed_hand_history`**：`room_id`、`hand_seed`、`payload_json`（完整 replay 结构）、时间戳、盲注、主池统计等。  
- **`dp_observed_hand_participant`**：在 `save` 插入主表拿到 `handHistoryId` 后，对 **`room.getPlayers()` 中每个非机器人** 插一行：`user_id`（优先 `DpPlayer.dpUserId`，否则按昵称查 `dp_user`）、`nickname_snapshot`、座位、庄盲标记、**`net_chips`**。  
- **注意**：若真人 **无** `user_id` 且昵称也查不到用户，仍可能写 participant 行但 `user_id` 为空；则 **`listForUserId` 不会命中**（SQL 条件为 `p.user_id = #{userId}`）。

### 2.3 查询 API 前置条件

| API | 前置条件（服务端） |
|-----|-------------------|
| `GET /dpHandHistory/list?userId=&page=&pageSize=` | `userId` 非空且存在于 `dp_user`；分页 PageHelper；结果仅含 **参与者表上该 `user_id` 命中** 的牌谱。 |
| `GET /dpHandHistory/checkUserAndOtherPlayerHandHistoryList?userId=&otherUserId=&…` | 两个 id 均非空且均在 `dp_user`；SQL 要求 **同一 `hand_history_id` 上两条 participant**（双方各命中一次）。 |
| `GET /dpHandHistory/detail?handHistoryId=&userId=` | `userId` 有效用户；`countParticipantForHand(handHistoryId, userId) > 0`；主表存在且 `payload_json` 可解析为 Map。 |

---

## 3. NPC：规则 Bot 与 `BOT_LLM`

| 类型 | 识别 | 决策入口 | 执行 |
|------|------|----------|------|
| 规则 NPC | `DpNpcEngine.isBotPlayer` 且非 LLM 昵称 | `DpNpcEngine.decideActionIfReady` | 全局 tick 1s；`nextBotActionTime` 随机思考窗（`dp.npc.rule-think`，默认 ≤4s + tick 余量）→ `NpcAction` → `bet` / `fold` |
| `BOT_LLM_*` | `DpNpcEngine.isLlmBotNickname` | `DpLlmNpcDecisionService.decideActionIfReady` | 异步 `CompletableFuture`（固定 4 线程池）+ `inflightByKey`；**票据校验** `handSeed/stage/actorIndex/betToCall/...` 失效则取消在途请求；超时 125s |

**座位加入**：`addLlmBotToNextHand` / 各 `add*Bot` → `readyNextHand`，昵称形如 `BOT_LLM_<序号>`（与 `DpNpcEngine` 约定一致）。

---

## 4. 房间内音乐同步与对局耦合

- **存储**：`DpGameRoomPushService.lastRoomMusicJson`（按 `roomId`），与房间快照 **独立**。  
- **校验**：仅校验发送者 **昵称属于该房**（玩家或观众），**不**校验是否房主、**不**写入 `DpRoomBO`。  
- **与对局关系**：**弱耦合** — BGM 不影响 `currentStage`、底池或牌谱；对局 JSON 广播仍按 1s tick。新建 WS 会先收房间快照再收最近一帧 `roomMusic`。

---

## 5. 风险与并发要点

1. **`bet` / `fold` 与 `synchronized(room)`**  
   - `toggleReady`、`readyNextHand`、`exitRoom` 等多处持 **`synchronized(r)`** 修改成员。  
   - **`bet`、`fold` 直接从 `roomMap.get` 后改状态，未持房锁**，与 **同进程内全局 1s `Timer`** 并发执行 `tickNpcTurnOrHumanActionTimeout`（也会调 `bet`/`fold`/`moveToNextValidActor`）及 LLM 回调落地 **存在数据竞争风险**（重复行动、错序、`currentActorIndex` 与 UI 不一致等）。  
2. **行动超时**  
   - 真人超时：`lastActionTime` 与 `DpRoomBO.getActionTimeout()`；由 **tick** 判超并 `fold`。若 HTTP 与 tick 交错，依赖上述锁缺口，极端情况可能出现竞态。  
3. **LLM 在途决策**  
   - 快照变更会 `cancel` 旧 Future；若取消/完成与 tick 下一轮交错，依赖 `ticket.stillValid`；仍建议在架构上视作 **Eventually 与房内状态对齐**。  
4. **牌谱一致性**  
   - 内存构建用 `ConcurrentHashMap`（`BUILDERS`），但 **房间对象本体非线程安全**；竞态下手牌记录与真实结算可能漂移。  
   - **`finalizeHand` ≤1 人不上盘**：短桌或异常人数时无 DB 记录。  
5. **WS 与其它节点**  
   - 会话表在 **单机内存**；多实例未在此审计范围内扩展，但属于部署级风险（见 `WEBSOCKET.md`）。

---

## 6. 代码锚点（便于复查）

| 主题 | 主要位置 |
|------|----------|
| 阶段推进 / 结算 | `DpRoomServiceImpl`：`advanceStage`、`autoAdvanceIfRoundFinished`、`autoSettle`、`newHandWithoutLobbyUpsert` |
| 全局 tick | `DpRoomServiceImpl` 构造函数 `Timer`、`runGlobalSecondTickForSingleRoom` |
| WS 推送 | `DpGameRoomPushService.broadcastIfSubscribed`、`sendInitialSnapshot` |
| 摘房 | `removeRoom`、`tryUnregisterEmptyRoomAssumeLocked`、`finalizeHallAfterRoomRemoved` |
| 牌谱内存 | `DpHandHistoryObservedImpl` |
| 牌谱入库 | `DpHandHistoryPersistServiceImpl#save`、`insertParticipants` |
| REST 查询 | `DpHandHistoryController`、`DpHandHistoryServiceImpl`、`DpHandHistoryQueryMapper` |
| LLM NPC | `DpLlmNpcDecisionService` |

---

*本文档为 Agent 04 审计产物；文中「Agent 1 / Agent 2」与兄弟审计文档对齐：Agent 1 侧重 **房间生命周期与摘房**，Agent 2 侧重 **WS 推送节奏与载荷**（仓库内请以 `docs/WEBSOCKET.md` 与 `DpGameRoomPushService` 为准）。*
