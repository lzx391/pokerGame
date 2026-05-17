# 审计 Agent 03：快匹与社交/邀请/观战业务流程

> **范围**：默认大厅快匹（建房、取消、配对协调、`JoinableQuickMatchRoomIndex`）、进房与 `DpRoomServiceImpl` 的衔接；好友申请/列表/邮箱；房间邀请、「跟随进房」与「跟随观战」路径差异。  
> **性质**：只读代码与既有文档的对照审计；不包含实现改动建议之外的交付物。  
> **与 Agent 2 交界**：快匹 WebSocket 的 URL、握手、JSON 字段与连接生命周期属 **`docs/business-flow/audit-agent-02-*.md`**（或团队约定的 Agent 2 对局/大厅 WS 审计）— **本文件只写业务语义与触发条件**，技术协议细节请交叉阅读 Agent 2。

---

## 1. 快匹：从用户操作到内存 / REST / WebSocket

### 1.1 用户侧顺序（大厅 `home.vue`）

| 步骤 | 用户操作 | 客户端行为 | 服务端可见入口 |
|------|-----------|-------------|----------------|
| 1 | 点击「快速匹配」 | 先 `connectQuickMatchWs()`（`nickname` + `token` 查询参数），再 `POST /dpRoom/quickMatch2` | WS：`/ws/dp-quick-match`；REST：`DpRoomController#quickMatch2` |
| 2 | 排队等待 | 保持快匹 WS；`onmessage` 里识别 `state` | 见 §3 推送点 |
| 3 | 匹配成功 | 收 `MATCHED` → 断开快匹 WS → `router.push('/game/:roomId')` | 对局页再起 `/ws/dp-game`（属 Agent 2 / 对局链路） |
| 4 | 排队中再点按钮 | `POST /dpRoom/quickMatchCancel2` + 断开快匹 WS | `DpRoomController#quickMatchCancel2` |
| 5 | 创建房间 / 加入房间 / 跟随好友进房（大厅其它入口） | `exitLobbyQuickMatchSilently`：可选复位 UI、断快匹 WS、`POST /dpRoom/quickMatchCancel2` | 降低「仍占快匹队列」与进房并发 |

### 1.2 服务端 `POST /dpRoom/quickMatch2` 主链（业务级）

实现入口：`DpRoomServiceImpl#quickMatchJoinQueueOrImmediate`。

1. **`defaultQmLock` 内**：修剪超时等待者（约 3 分钟，`DEFAULT_QM_WAIT_MS`）；若当前昵称尚不在 `defaultQmWaiters`，则 `addLast` 入 FIFO 队列。  
2. **锁外**：对刚被超时修剪的昵称批量发「空闲/超时」类通知（见 §3）。  
3. **`attemptQuickMatchPairing()`**：在 **`dpQuickMatchAssignmentLock`** 下调用 `DpQuickMatchPairingCoordinator#attemptPairing()`，一轮内循环：  
   - **冲公开桌**：`flushQueuedWaitersIntoJoinablePublicRooms` — 队列仅做 prune + 快照，**锁外**对仍留在队列中的每条目调用 `quickMatchJoinAndReady`（内部再抢assignment锁，与同线程可重入）；成功则从队列摘掉并 `MATCHED` 推送。  
   - **填已有可进索引桌**：`JoinableQuickMatchRoomIndex` 按「缺人越少越优先」找房；对单间 `synchronized(r)`，队内 `poll` 一批等待者，`joinAndReadyWhileRoomLocked`，逐个 `notifyQuickMatchMatched`。  
   - **无可用可进桌且队列 ≥2 且队头两名不同**：批量 `poll`（至多 `MAX_NEW_ROOM_BATCH`），`createDefaultQuickMatchRoomForPairing`（等价于默认参数 `createRoom`：小盲 5、大盲 10、50 倍初始、无密、9 座），房内依次 `joinAndReady`，`notifyQuickMatchMatched`，`refreshJoinableQuickMatchIndex`，`startGameForQuickMatchRoom`，再冲一次公开桌。  
4. **HTTP 返回**：当前实现**固定**携带 `queued=true`、`state=WAITING` 成功体（即使在本请求内已被冲桌/配对摘出队列 —— **「是否已落座」以 WS `MATCHED` 或快照为准**；前端若仅依赖 HTTP 的 `data.roomId` 分支，可能与现后端行为不一致）。  
5. **若仍在队列**：`notifyWaiting(nickname, queuePosition)`（队列位置 1 为队首）。

**可进房索引**：`JoinableQuickMatchRoomIndex` 在 `syncLobbyForRoomId` / 退房 / `refreshJoinableQmIndexThenSyncLobby` 等路径更新；`orderedQuickMatchJoinCandidates` 以索引桶序为主、全表扫描兜底修复漂移。空位口径见 `DpQuickMatchRoomSemantics`（与 `waitNextHand`、非僵尸座位等相关）。

**实现漂移说明**：`docs/dp-quick-match-flow.md` 仍描述「先 `quickMatchJoinAndReady`，仅当无公开房才入队」；**当前源码**为「先入队再整体 `attemptQuickMatchPairing`（内含冲桌里的 `quickMatchJoinAndReady`）」。业务效果上仍可通过首轮 **flush** 立刻进入公开房，但 **HTTP 语义与文档不完全一致**，测试与产品说明宜以代码为准。

### 1.3 取消匹配

- **REST**：`POST /dpRoom/quickMatchCancel2` → `cancelDefaultQuickMatchWait`：在 `defaultQmLock` 下 `removeIf`；若确曾移除则 `quickMatchPush.notifyIdle(..., "已取消匹配")`。  
- **副作用**：仅队列；**不会**撤销已通过快配对局路径写入 `roomMap` 的落座。  
- **其它入口**：`createRoom` / `joinRoom` / `joinRoomInviteAsSpectator` 在写房前均会 `cancelDefaultQuickMatchWait(nickname)`，避免同昵称双占。

### 1.4 定时修剪

- `@Scheduled(fixedDelayString = "${mgdemoplus.dp-quick-match-prune-ms:30000}")`：`scheduledPruneDefaultQuickMatchQueue` 周期 prune，超时者 `notifyIdle("匹配等待超时")`。

---

## 2. 与 Agent 1（房间创建 / 加入）衔接点

下表将**快匹关键步骤**映射到房间服务的**显式 API 或内部方法**（Agent 1 若审计「建房/进房/准备/开局」，可据此对齐覆盖）。

| 快匹业务步骤 | Agent 1 衔接 | 说明 |
|--------------|--------------|------|
| 无桌批量建房 | `POST /dpRoom/createRoom` 语义等价路径：`createDefaultQuickMatchRoomForPairing` → 内存 `createRoom(..., null 密码)` | 无密码公开房末尾 `attemptQuickMatchPairing()`，新房入索引后立即消费队列 |
| 进入已有公开房（冲桌 / 索引进房） | `quickMatchJoinAndReady` 内 `joinRoomMutateAssumeLocked` + 未开局 `ensureLobbyReady` / 已开局 `applyReadyNextHandWhileLocked` | 与大厅手动进房共用「单间锁内写玩家列表」的约束 |
| 配对成功后开局 | `startGameForQuickMatchRoom` → `startGame(roomId, ownerNickname)` | 与房主手动开局同一条服务方法 |
| 手动建房/进房与快匹互斥 | `createRoom` / `joinRoom` 前 `cancelDefaultQuickMatchWait` | 防止昵称同时出现在队列与房内写入路径 |
| 观众/邀请进房 | **不走** `joinRoom` 的密席主路径；走 `joinRoomInviteAsSpectator`（见 §4） | 快匹队列在邀请进房前被取消 |

---

## 3. 与 Agent 2 交界：快匹 WebSocket 推送（仅业务含义）

下列为 **`DpQuickMatchPushService` 下行消息的业务意图**；帧结构与握手表见 **Agent 2** / `docs/WEBSOCKET.md` 周边说明。

| 业务事件 | 推送对象 | 业务含义 |
|----------|----------|----------|
| `WAITING` | 昵称下已注册的快匹会话 | 在默认 FIFO 队列中的位置（含建连后 `pushQuickMatchLobbySnapshot` 补发） |
| `MATCHED` | 同上 | 应进入对局页：携带目标 `roomId`（来自公开房落座、索引填房或批量新房） |
| `IDLE` | 同上 | 用户离开队列：主动取消文案、超时文案、或 prune 驱逐 |

**注册与补发**：`DpQuickMatchWebSocketHandler#afterConnectionEstablished` JWT 校验通过后 `register` 并调用 `pushQuickMatchLobbySnapshot`（内联 prune + 若仍在队则 `WAITING`，否则若在房内则 `MATCHED`）。**若客户端先 POST 后建连**：中途产生的推送可能漏收，依赖建连快照纠偏。

---

## 4. 社交：邀请 / 好友 / 跟随 —— 对「能否进房、是否观战」的影响

### 4.1 好友（与进房能力）

| 能力 | 依赖 | REST | 行为摘要 |
|------|------|------|----------|
| 发好友申请 | JWT；未成好友 | `POST /dp/friends/requests` | DB `dp_friend_request` |
| 收件箱待处理 | JWT | `GET /dp/friends/requests/pending` | 与邮箱列表可合并展示 |
| 同意/拒绝 | JWT；仅被申请人 | `POST /dp/friends/requests/{id}/accept|reject` | 同意写入 `friend_link` |
| 好友列表 + 展示态 | JWT | `GET /dp/friends` | `presence`：房内 `IN_GAME` 优先，否则站点心跳 → `IDLE` / `OFFLINE`（`DpFriendPresenceService` / `site-heartbeat`） |
| 解除好友 | JWT | `DELETE /dp/friends/{friendUserId}` | 删 `friend_link`；**双方 Pending 进房邀请置 `CANCELLED`** |

**要点**：除「互为好友」外，**进房邀请 / 跟随观战**还依赖 **内存房间** 与 **好友在房内的身份**（见下）。

### 4.2 邮箱（`GET /dp/mailbox`、`unread-count`）

- 聚合 **待处理好友申请** + **Pending 进房邀请**（邀请行前先做 DB 过期置 `EXPIRED`）。  
- 进房邀请创建：`POST /dp/room-invites`，需：`roomId` 在内存存在；邀请人与被请人 **互为好友**；邀请人 **`canActiveMemberInviteFriends`**（真人非 Bot；**在座且未本手离座** 或 **在观众名单**）。邀请约 **60s** 过期。

### 4.3 接受 / 拒绝邀请

- `POST /dp/room-invites/{id}/accept`：校验好友关系仍成立、未过期、房间仍在内存 → **`joinRoomInviteAsSpectator`** → DB 标记 `ACCEPTED`。  
- `reject`：状态机拒绝，不写 `roomMap`。

### 4.4 「跟随」两条 REST 路径差异（易混）

| 项目 | `POST /dp/friends/follow-room` | `GET /dp/friends/{id}/spectate-room` + `POST /dp/room-follow/spectate` |
|------|--------------------------------|------------------------------------------------------------------------|
| 找房间 | `findRoomIdContainingNickname`（好友在 **玩家或观众** 任一列表即可） | `findActiveRoomIdForNickname` + **`canActiveMemberInviteFriends(roomId, 好友昵称)`** |
| 典型结果 | 好友只要在房里即可跟 | 好友须满足「**与发起进房邀请时相同的活跃成员身份**」（未离座真人玩家或观众） |
| 进房实现 | `joinRoomInviteAsSpectator` | 同上 |
| 观战语义 | **始终是观众进房**：未开局进观众席；已开局进观众席；**不占桌上密席**；**免密** | 同上 |

**与大厅 `joinRoom` 的差异**：普通 `POST /dpRoom/joinRoom` 在未开局且未满时 **占玩家席**；已开局则 **观战席**，且可能受密码约束。邀请/跟随链 **统一免密、观战席语义**（与 `joinRoomInviteAsSpectator` 注释一致）。

**快匹与社交交界**：任意 `joinRoomInviteAsSpectator` / `joinRoom` / `createRoom` 前会 **取消** 该昵称默认快匹队列，避免匹配与邀请并发占坑。

---

## 5. P0 / P1 漏测矩阵（建议用例）

| 优先级 | 场景 | 风险点 / 断言关注点 |
|--------|------|---------------------|
| **P0** | 排队中 **`quickMatchCancel2`**：请求在 `attemptQuickMatchPairing` 执行间隙到达 | 是否重复落座、取消后是否仍收到 `MATCHED`、队列与 `roomMap` 一致 |
| **P0** | 同一用户 **多标签页** 同时 `quickMatch2` | FIFO 去重与 `notifyWaiting` 位次、终局唯一 `roomId` |
| **P0** | **flush** 轮次中公开房最后一席 **双请求竞态** | `quickMatchJoinAndReady` + 单间锁与 assignment 锁是否仅一人成功 |
| **P0** | 配对 **`batch` 内 join 抛错** | `failedRequeue` 是否插回队头；索引与队列是否一致 |
| **P0** | 接受邀请时 **`removeFriend` / 邀请过期** 与时序交错 | DB 状态与内存 `joinRoomInviteAsSpectator` 错误文案 |
| **P1** | **HTTP `quickMatch2` 成功体恒为 `queued=true`** | 与前端 `data.roomId` 分支、与文档「先直进公开房」叙述的一致性 |
| **P1** | **先 POST 后建连** 快匹 WS | 是否仅靠快照恢复 `WAITING`/`MATCHED`；有无竞态窗口 |
| **P1** | **快匹 WS 断线重连**（`pushQuickMatchLobbySnapshot`）与队列 prune 同刻 | 位次与超时通知是否重复或遗漏 |
| **P1** | `follow-room` vs `room-follow/spectate` 在好友 **仅 spectators / 已离座** 等边界 | 一条成功另一条失败是否符合产品预期 |
| **P1** | **`findActiveRoomIdForNickname` 与 `findRoomIdContainingNickname`** 当前均委托 `findRoomContainingNickname` | JavaDoc 与实际是否一致；跟随/观战是否应区分「僵尸座」 |
| **P1** | 新房 `createRoom` 末尾 `attemptQuickMatchPairing` 与 **队锁 / 房锁** 顺序（协调器 `pairingDepth` 内层只 flush） | 死锁与「只冲桌不递归批量建房」约束 |
| **P1** | 定时 prune 与 **用户主动取消** 同昵称 | `IDLE` 文案是否双重、客户端是否误弹 |

---

## 6. 参考代码与文档（仓库内）

- `DpRoomController`：`quickMatch2` / `quickMatchCancel2` / `createRoom` / `joinRoom`  
- `DpRoomServiceImpl`：快匹队列、`quickMatchJoinAndReady`、`quickMatchJoinQueueOrImmediate`、`cancelDefaultQuickMatchWait`、`joinRoomInviteAsSpectator`、`createRoom`  
- `DpQuickMatchPairingCoordinator` / `DpQuickMatchPairingHost`（`QuickMatchPairingHost` 实现类于 `DpRoomServiceImpl` 内部）  
- `JoinableQuickMatchRoomIndex`、`DpQuickMatchRoomSemantics`  
- `DpFriendMailboxController` / `DpFriendSocialService`  
- `DpQuickMatchWebSocketHandler`、`DpQuickMatchPushService`  
- 既有专题：`docs/dp-quick-match-flow.md`、`docs/dp-quick-match-concurrency.md`（与源码不一致处以**源码**为准）  
- 对局 WS 总览：`docs/WEBSOCKET.md`（快匹专链细节建议以 Agent 2 审计为准）

---

*本文件为 Agent 03 业务流程审计交付物；禁止将本文档作为唯一协议来源而不核对 Java 实现。*
