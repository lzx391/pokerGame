# 大厅快速匹配（`quickMatch2`）

> **核对日期**：2026-05-25  
> **权威来源**：`DpQuickMatchPairingCoordinator`、`DpRoomQuickMatchBridge`、`DpRoomController#quickMatch2`  
> **Status**: maintained

本文档描述 **「快速匹配 2」** 相关业务：**先尝试进入已有公开房；若无房可进则进入内存 FIFO 队列，满两人自动按默认参数建新公开房并准备/开局**。覆盖 **HTTP 接口、WebSocket 推送、服务端核心方法、加锁顺序、前端交互** 与 **已知边界**。

---

## 1. 业务目标（产品语义）

1. 玩家在大厅点击「快速匹配」。
2. 服务端 **优先** 将其 **选入已有、未加密、且按当前规则仍有空位** 的公开房（与 `quickMatchJoinAndReady` 逻辑一致）。
3. 若 **没有任何符合条件的公开房**，则进入 **默认等待队列**（小盲 5、大盲 10、初始 50 倍、公开、最多 9 人桌；与 `createRoom` 默认快匹建房参数一致）。
4. 队列 **先进先出（FIFO）**，当 **至少两名不同玩家** 在队时，服务端 **自动创建新房间**：先入队者为房主并 `createRoom`，后入队者 `joinRoom`，并完成 `ensureLobbyReady`、`startGame` 等与产品一致的准备。
5. 匹配成功后，客户端应 **进入对局页**（`/game/{roomId}`）；大厅侧 **快匹专用 WebSocket** 应 **关闭**，对局页再建立 **房间 WebSocket**（`/ws/dp-game`），二者 **独立**。

---

## 2. 代码与入口一览

| 层级 | 路径 / 类 | 说明 |
|------|-----------|------|
| HTTP 控制器 | `DpRoomController` | `POST /dpRoom/quickMatch2`、`POST /dpRoom/quickMatchCancel2` |
| 核心服务 | `DpRoomServiceImpl` + `DpQuickMatchPairingCoordinator` | 公开房快匹、默认 FIFO 队列入队、`attemptPairing`/冲桌与批量建新桌、修剪队列、`pushQuickMatchLobbySnapshot`、推送触发 |
| WebSocket 配置 | `WebSocketGameRoomConfig` | 注册 `/ws/dp-quick-match` |
| WS 握手 | `DpQuickMatchWebSocketHandler` | 校验 query `nickname` + `token`（JWT subject 必等于昵称），注册会话并补发快照 |
| WS 推送 | `DpQuickMatchPushService` | `nickname → Set<WebSocketSession>`，JSON 推送 `WAITING` / `MATCHED` / `IDLE` |
| 前端大厅 | `front/dp_game/src/components/home.vue` | 先连快匹 WS，再 `POST quickMatch2`；排队中依 WS 推送跳转；断线重连；取消/销毁时断开 WS |
| 安全白名单 | `JwtSecurityConstants.PERMIT_ALL` 含 `/ws/**` | WS 握手 **不经** Spring Security JWT 过滤器；**昵称+token 在 Handler 内自检** |
| HTTP 快匹接口 | 需登录 | JWT 经 `SecurityContext` 与请求 param `nickname` 一致性校验（控制器内） |

---

## 3. 服务端常量与内存结构（`DpRoomServiceImpl`）

以下为 **默认快匹队列** 相关常量（名称以源码为准）：

| 常量 | 典型值 | 含义 |
|------|--------|------|
| `DEFAULT_QM_SB` | 5 | 小盲 |
| `DEFAULT_QM_BB` | 10 | 大盲 |
| `DEFAULT_QM_STARTING_BB` | 50 | 初始筹码 = 大盲 × 倍数（与 `createRoom` 内 clamp 一致） |
| `DEFAULT_QM_MAX_SEATS` | `DpRoomBO.MAX_SEAT_COUNT` | 最多座位（通常 9） |
| `DEFAULT_QM_WAIT_MS` | 3 分钟 | 队列内等待超时；超时从队列移除并可通过 WS 推送 `IDLE`（见修剪逻辑） |
| `MSG_NO_PUBLIC_ROOM` | 固定文案 | 仅当 **公开房快匹返回该错误** 时，才允许进入默认队列 |

**内存结构：**

- `DpQuickMatchWaitEntry`：`record (String nickname, Integer userId, long enqueuedMs)`，入队时间用于超时判断。
- `defaultQmWaiters`：`ArrayDeque<DpQuickMatchWaitEntry>`，**FIFO**。
- `defaultQmLock`：保护队列的 **所有** 与 `defaultQmWaiters` / `prune` 相关的临界区。
- `dpQuickMatchAssignmentLock`：串 **`quickMatchJoinAndReady`** 与 **`attemptQuickMatchPairing()`**（其对 `DpQuickMatchPairingCoordinator#attemptPairing` 的封装），与同线程重入相容；收窄「谁先写 `roomMap`」的窗口。
- `DpQuickMatchPairingCoordinator`：**单入口** `attemptPairing()` —— 每轮 **冲公开桌**（历史 `tryFlush`，仍在队锁内 `prune`+快照、锁外 `quickMatchJoinAndReady`）→ 有房则按索引 **填空位** → 必要时 **批量建新桌**。同路径若同时要单房监视器与 `defaultQmLock`：**先房后队**（见该类 JavaDoc）。
- **建房 / HTTP 大厅进房兜底**：`createRoom`、`joinRoom` 入口在主业前幂等 **`cancelDefaultQuickMatchWait(nickname)`**，避免邀友链路与大厅快匹 FIFO 并排占坑（不要求与邀友 WS 等业务互斥错误）。
---

## 4. 锁与并发（必读）

### 4.1 锁的分工（摘要）

| 锁 / 构件 | 用途 |
|-----------|------|
| `dpQuickMatchAssignmentLock` | `quickMatchJoinAndReady` 全程；`attemptQuickMatchPairing`（包装协调器整条循环）外层；可与 `quickMatchJoinAndReady` **重入** |
| `defaultQmLock` | **仅队列**：`prune`、入队、`poll` / 批量 `poll`、失败插回队头队尾 |
| `synchronized(DpRoomBO)` | 单间房内进房 / 准备 / 名额与 `waitNextHand` 一致性 |
| 协调器 **`pairingDepth`** | **仅协调器内部**：外层 `attemptPairing` 正常跑循环；从内层公开 `createRoom` 再次触发的配对只跑 **flush 冲公开桌**，避免递归批量建房与同线程锁序问题（取代旧 `ThreadLocal` tryDrain 深度） |

### 4.2 **先房后队**（`defaultQmLock` 与单间监视器同路径时）

协调器 **`fillHeadIntoJoinableRoom`** 等路径：在需同时触碰 **单间 `DpRoomBO` 监视器** 与 **`defaultQmLock`** 时，必须先 `synchronized(r)`，再在 **已持房监视器** 的前提下 `synchronized(defaultQmLock)` 做批量 `poll`（见 `DpQuickMatchPairingCoordinator` 类注释表格）。

### 4.3 冲公开桌与 HTTP 首轮快匹对齐

历史约束不变：**不在持有 `defaultQmLock` 时进房**。`flushQueuedWaitersIntoJoinablePublicRooms`（由 `DpQuickMatchPairingHost` 在 Service 侧实现）：队内 `prune`+快照；**锁外**按 FIFO 对快照逐项 `quickMatchJoinAndReady`。与 `quickMatchJoinQueueOrImmediate` 入队前先跑的那轮 `quickMatchJoinAndReady` 语义对齐。

### 4.4 释座后触发配对

手动创建 **无密码公开房** 的 `createRoom` **末尾**，以及 **公开房** `exitRoom` **在释放房间监视器之后**（且业务成功释放了快匹可读空位相关状态时），调用 **`attemptQuickMatchPairing()`**，使 FIFO 与新空桌/索引立刻被协调器消费。该调用外层会占 `dpQuickMatchAssignmentLock`，与 §4.1 一致。

### 4.5 典型死锁反例（如何避免）

与同路径 **先 assignment 还是先房**无关的死锁关注点仍是：**不要在持单间监视器时去改 `JoinableQuickMatchRoomIndex`**；索引与大厅 upsert 在 **释房监视器之后** 执行（参见 [docs/dp-quick-match-concurrency.md](docs/dp-quick-match-concurrency.md) §8）。
---

## 5. HTTP 接口契约（`DpRoomController`）

### 5.1 `POST /dpRoom/quickMatch2`

- **参数**：`nickname`（必填）、`userId`（可选，与 `joinRoom` / 校验逻辑一致）。
- **鉴权**：`Authentication#getName()` 必须等于 `nickname`。
- **返回**（`ResultUtil`，成功时 `data` 常见字段）：
  - **直接进入公开房**：`success=true`，`roomId`，`message` 等（来自 `quickMatchJoinAndReady`）。
  - **进入默认队列**：`success=true`，`queued=true`，`state=WAITING`，提示文案。
  - **失败**：如昵称无效、公开房阶段业务错误、token 不一致等。

排队状态由 **`/ws/dp-quick-match`** 推送（`WAITING` / `MATCHED` / `IDLE`）；前端 **`home.vue`** 不再请求 HTTP 轮询接口。

### 5.2 `POST /dpRoom/quickMatchCancel2`

- **鉴权**：同上。
- **语义**：在 `defaultQmLock` 下从 `defaultQmWaiters` 移除该昵称；若确实移除则 `quickMatchPush.notifyIdle(nickname, "已取消匹配")`。
- **返回**：`success=true`，`cancelled`：`true/false` 表示是否曾经在队列中。

---

## 6. 核心服务端方法链条（`DpRoomServiceImpl`）

以下按 **调用顺序** 与 **职责** 说明（非全部私有方法 exhaustive，但覆盖快匹主链）。

### 6.1 `quickMatchJoinQueueOrImmediate(String nickname, Integer userId)`

**主入口**（由 `quickMatch2` 调用）。

1. **`quickMatchJoinAndReady(nickname, userId)`**  
 - 在 **`dpQuickMatchAssignmentLock`** 下扫描 `roomMap` 中 **未加密** 且 **`quickMatchVacancy > 0`** 的房间，按「越满越优先」等排序尝试 `joinRoom` + 准备。  
 - **成功** → 直接返回带 `roomId` 的 `ResultUtil`，**不入队**。  
 - **失败** 且 `message != MSG_NO_PUBLIC_ROOM` → **直接返回错误**，不入队（例如业务拒绝）。  
 - **失败** 且为「无公开房」→ 继续。

2. **`findRoomContainingNickname` 二次检查**（防止与步骤 1 竞态后已在房内）→ 若已在房则再调一次 `quickMatchJoinAndReady`。

3. **`synchronized (defaultQmLock)`**：`pruneDefaultQuickMatchQueueLockedWithoutReentry()`，若本昵称尚不在队则 **`addLast`**。

4. **`notifyQuickMatchTimedOut`**：处理本次 `prune` 返回的 **超时昵称**（发 WS `IDLE`）。

5. **`attemptQuickMatchPairing()`** → **`DpQuickMatchPairingCoordinator#attemptPairing()`**（外层持 `dpQuickMatchAssignmentLock`）：循环「冲公开桌 → 有可进索引则填空位 → 否则队中多于 1 人时批量建新桌并落座」，直到本轮无进展。

6. **再次 `defaultQmLock`**：计算本昵称的 **`queuePosition`（从 1 起）**。若仍在队列，则 **`quickMatchPush.notifyWaiting(nickname, queuePosition)`**。

7. 返回 `queued=true`、`state=WAITING`。

### 6.2 `attemptQuickMatchPairing()` / `DpQuickMatchPairingCoordinator#attemptPairing()`

- Service 私有方法 **`attemptQuickMatchPairing`** 包住协调器：**同一把** `dpQuickMatchAssignmentLock` 与 **`quickMatchJoinAndReady`** 可重入。  
- 协调器：**先 flush**（`DpQuickMatchPairingHost#flushQueuedWaitersIntoJoinablePublicRooms`，即历史 **`tryFlush` 快照 + 锁外 `quickMatchJoinAndReady`**）→ **有可进公开桌**：按空缺自队头 `poll`，在 **先房后队** 前提下 `joinAndReadyWhileRoomLocked` → **索引无桌且队列至少两人**：单次最多 `MAX_NEW_ROOM_BATCH` 条出队建新桌（先入队者为房主，`createRoom` 已占位；协调器再在 `synchronized(room)` 内并进余下玩家），`afterRoomMutationRefreshQuickMatchIndex`、`notifyMatched`、`startGame`、再 flush 一圈。  
- **嵌套调用**：从内层 **`createRoom`（公开）** 末尾再次 `attemptQuickMatchPairing()` 时，协调器 **`pairingDepth` &gt; 1** 的轮次只做 **flush**，不再进入批量建新桌路径（取代旧 **`ThreadLocal` tryDrain** 防抖）。

### 6.3 `createRoom` / `joinRoom` 与 FIFO 兜底

- **`createRoom`**、大厅 **`joinRoom`**：主业前 **`cancelDefaultQuickMatchWait(nickname)`**（幂等），减少邀友链路与 FIFO 双排。  
- **无密码 `createRoom` 末尾**、公开房 **`exitRoom` 成功后** → **`attemptQuickMatchPairing()`**（参见 §4.4）。

### 6.4 `cancelDefaultQuickMatchWait(String nickname)`

见 §5.2。

### 6.5 `pushQuickMatchLobbySnapshot(String nickname)`

**WebSocket 建连或重连后** 调用（`DpQuickMatchWebSocketHandler`）：补发当前状态，避免「服务端已推送但客户端当时未 register」的丢失。

顺序概要：

1. `defaultQmLock` 下 `prune`，查队列位置 → 有则 **`notifyWaiting`**；并 `notifyQuickMatchTimedOut`。  
2. 否则若 **`findRoomContainingNickname`** → **`notifyMatched`**（含已配对进房、或从其它入口进房的用户）。

### 6.6 `pruneDefaultQuickMatchQueueLockedWithoutReentry()`

**须在已持有 `defaultQmLock` 的前提下调用。**

- 若 `findRoomContainingNickname(e.nickname()) != null` → 从队列 **移除**（已在房者不应再等默认 FIFO）。  
- 若等待时间 **超过 `DEFAULT_QM_WAIT_MS`** → 移除，并把昵称加入 **返回值**（供调用方 **`notifyQuickMatchTimedOut`**，WS 文案为「匹配等待超时」）。

### 6.7 `scheduledPruneDefaultQuickMatchQueue()`

- Spring `@Scheduled`，间隔 `mgdemoplus.dp-quick-match-prune-ms`（默认 30000 ms）。  
- 在 `defaultQmLock` 下 `prune`，释放锁后对超时列表 **`notifyQuickMatchTimedOut`**。

### 6.8 辅助

- **`quickMatchResultDetailMessage(ResultUtil)`**：从 `ResultUtil` 抽出 `message`，用于判断是否 **仅**「无公开房」才入队。  
- **`notifyQuickMatchTimedOut(List<String>)`**：对每个昵称 **`quickMatchPush.notifyIdle(..., "匹配等待超时")`**。

---

## 7. WebSocket 快匹通道

### 7.1 URL 与代理

- **生产 / 直连后端**：`ws(s)://{host}/ws/dp-quick-match?nickname=...&token=...`  
- **本地 `front/dp_game` 开发**：通过 `vue.config.js` 将 **`/dp-ws` 前缀** 代理到后端 **`/ws`**，故使用：  
  `ws://{dev-host}/dp-ws/dp-quick-match?...`  
  （与同目录下对局页 `dp-game` 的写法一致。）

### 7.2 握手与鉴权（`DpQuickMatchWebSocketHandler`）

1. 读取 query：`nickname`、`token`（JWT 串，**非** `Bearer ` 前缀）。  
2. `JwtTokenService.verifyToken(token)`，**`Claims#getSubject()` 必须等于 `nickname`**。  
3. 会话 attribute：`qmNickname`；`DpQuickMatchPushService.register`。  
4. **`dpRoomService.pushQuickMatchLobbySnapshot(nickname)`**。

断连 / 错线：`removeSession` 从昵称集合摘除该 `WebSocketSession`。

### 7.3 推送 JSON 格式（`DpQuickMatchPushService`）

所有消息带 **`_ws: "quickMatch"`**，`state` 取值：

| `state` | 附加字段 | 含义 |
|---------|----------|------|
| `WAITING` | `queuePosition`（整数，从 1 起） | 仍在默认 FIFO 队列 |
| `MATCHED` | `roomId` | 应跳转对局页 |
| `IDLE` | `message` | 不在队列（取消、超时、或其它业务文案） |

同一昵称 **允许多 Session**（多标签）：推送时对该昵称下 **所有已连接 Session** 各发一条。

---

## 8. 前端流程（`home.vue`）

### 8.1 用户点击「快速匹配」

1. 校验已登录、`user.token` 存在。  
2. **`connectQuickMatchWs()`**：建立 WS，**Promise 在 `onopen` resolve**；握手失败则提示。  
3. **`POST /dpRoom/quickMatch2`**。  
4. 若响应带 **`roomId`**：**`disconnectQuickMatchWs()`**，`$router.push('/game/' + roomId)`。  
5. 若 **`queued` 且 `WAITING`**：**`quickMatchPolling = true`**，保持 WS，等待 `onmessage`。  
6. **`onmessage`**：  
   - `MATCHED` + `roomId` → 清状态、**断开快匹 WS**、跳转对局。  
   - `IDLE` → 清状态、断开、弹窗提示。  
7. **`finally`**：若未进入「排队中」，结束 loading。

### 8.2 排队中断线重连

- 仅在 **`quickMatchPolling === true`** 且 **`quickMatchWsNoReconnect === false`** 时，`onclose` 经 **`scheduleQuickMatchWsReconnect`** 指数退避 **重连**。  
- 重连成功后 **`pushQuickMatchLobbySnapshot`** 会补发 `WAITING` / `MATCHED` / 已在房状态。

### 8.3 取消与离开大厅

- 点击「取消匹配」：**先 `disconnectQuickMatchWs()`**（`noReconnect`），再 **`POST quickMatchCancel2`**。  
- **`beforeDestroy`**：若仍在排队，**`cancelQuickMatchRemote` + `disconnectQuickMatchWs`**。

---

## 9. 端到端时序（两名玩家、无公开房）

```text
A：WS 连接 → register → snapshot WAITING(1) [若尚未 POST 则可能仍为后续状态]
A：POST quickMatch2 → 入队 → attemptPairing：队 &lt;2 仅能 flush → HTTP 返回 WAITING → notifyWaiting(A,1)

B：WS 连接 → …
B：POST quickMatch2 → 入队 → attemptPairing：建新桌或对两人分配落座 → notifyMatched(A), notifyMatched(B)

A/B：home.vue 收到 MATCHED → 断开快匹 WS → 进入 /game/{roomId}
对局页：再起 /ws/dp-game（与快匹 WS 无关）
```

**谁触发建房？**  
**后入队的那次请求**里触发的 **`attemptQuickMatchPairing()`**（协调器 **`attemptPairing`** 在多轮进度环中择机走「批量建新桌」分支）完成 **建房与推送**；**不是**「第一人 HTTP 返回后再由第二人单独建房通知第一人」的分离模型，而是在 **外层 `dpQuickMatchAssignmentLock` 包装下**的一段协调回合内落成。

### 9.1 仅一人在队 + 他人新建公开桌

若当时只有玩家 A 在默认 FIFO（**不足以建新桌或无第二参与者**），随后玩家 B **手动创建**无密码、且快匹可见空位规则下的公开桌，则在以下时机之一会先 **flush**（与首轮扫公开桌一致），使 A 入 B 的桌并收到 **`MATCHED`**：

- 任意 **`attemptQuickMatchPairing()`**（含他人 `POST quickMatch2` 入队末尾触发）；  
- **手动 `createRoom`（公开）完成后**；  
- **公开房 `exitRoom` 释监视器成功后**。

---

## 10. 已知边界与限制

1. **单机内存队列**：`defaultQmWaiters` 均在 **当前 JVM**。多实例负载均衡时，**除非**改为 Redis 等共享队列，否则 **不会出现「跨机拼桌」**。  
2. **修剪与已在房**：`prune` 会把 **已在任意房间**（含 spectator / player）的等待者移出队列，**不**等同于「匹配失败」，可能与其它入口进房交错。  
3. **协调器 prune**：`DpQuickMatchPairingHost#pruneQueueWhileLocked`（及冲桌快照前的 prune）会向超时者 **`notifyQuickMatchTimedOut`**；持锁粒度以源码为准。  
4. **安全**：`/ws/**` 在 Spring Security 中为 **permitAll**；**快匹 WS 的鉴权完全依赖 Handler 内 JWT** —— 若 token 泄露，等同账号维度风险，与 HTTP Bearer 一致。

---

## 11. 与「对局心跳 / 房间 WS」的关系

- **大厅快匹 WS** **不**替代 **桌上 HTTP 心跳**（`POST /dpRoom/heartbeat`）或 **观众席 WS 保活**逻辑。  
- **进房后** 生命周期由 **对局页 `game.vue` + `/ws/dp-game`** 与 **心跳** 接管；**快匹 WS 应在匹配成功或取消后关闭**。

---

## 12. 配置项

| 配置键 | 含义 |
|--------|------|
| `mgdemoplus.dp-quick-match-prune-ms` | 定时修剪默认队列的间隔（毫秒） |

JWT、端口等与全局应用配置一致，此处不展开。

---

## 13. 文档维护提示

- 若将队列 **迁移到 Redis**，需重写 §3、§4、§9，并区分 **分布式锁** 与 **推送通道**（可考虑仍用单机推送或引入消息中间件）。

---

*文档版本与仓库实现一致；以 `DpRoomServiceImpl` / `DpQuickMatchWebSocketHandler` / `home.vue` 源码为准。*
