# 审计笔记 · 实时链路（对局房 WS / 快匹 WS / 站点心跳与在线态）

**范围**：对局房 `/ws/dp-game`、快匹 `/ws/dp-quick-match` 的建连/断连、订阅关系、广播与单播语义、`chat`/`music` 类上行；站点心跳与好友列表在线态相关 REST 与 TTL。**仅文档**，与实现不一致处以仓库当前 Java 为准。

---

## 1. 会话（WebSocketSession）与 `roomMap`（单实例）

| 概念 | 含义 |
|------|------|
| **`roomMap`** | `DpRoomServiceImpl` 内 **`ConcurrentHashMap`**：房间的权威业务热状态（玩家、阶段、观众等）。 |
| **`roomSessions`（对局 WS）** | `DpGameRoomPushService` 内 **`ConcurrentHashMap<String, Set<WebSocketSession>>`**：`roomId →` 该节点上**所有订阅推送的长连接**。与 HTTP Session、登录 Cookie **无关**。 |
| **`nicknameSessions`（快匹 WS）** | `DpQuickMatchPushService`：`nickname → Set<WebSocketSession>`，同一昵称可多连接（多标签页）。 |

**单实例下的关系**：订阅集合只表达「谁连着、推给谁」；**是否在房内**以 `roomMap` 与 HTTP **`POST /dpRoom/heartbeat`** 维护的心跳字段为准。项目约定：**WS 断线不触发退房**；桌上玩家心跳超时由定时逻辑从 `roomMap` 摘掉后再 **主动关断** 匹配昵称的 WS（见下表）。

**多实例风险（引用 README）**：根目录 [README.md](../../README.md) 写明 —— 房间与对局热状态在 **JVM 内存 `roomMap`**，默认可横向扩展**不与 Redis 同步**；多实例需自行设计**会话粘滞或共享状态**。同一结论见专题 [docs/WEBSOCKET.md](../../docs/WEBSOCKET.md)（对局推送 **`roomSessions` 单机内存、未用 Redis**）。因此：**用户落到不同节点时**，可能出现「REST 看到某一房间状态，但 WS 推送到另一节点为空」「快匹队列与 WS 会话不在同一 JVM」等分裂行为，需架构层补救（粘滞、Pub/Sub 等）。

---

## 2. 路径与安全要点（摘要）

| 链路 | URL | 握手鉴权 |
|------|-----|----------|
| 对局 | `/ws/dp-game?roomId=…`（可选 `nickname=`） | Spring Security **`/ws/**` 放行**（[`JwtSecurityConstants`](../../src/main/java/com/example/mgdemoplus/security/JwtSecurityConstants.java)）；服务端用 **昵称是否在房内** 校验聊天/BGM，快照按观看者过滤。 |
| 快匹 | `/ws/dp-quick-match?nickname=…&token=…`（均必填） | Handler 内 **`JwtTokenService.verifyToken`**，`sub` 须与 `nickname` 一致。 |

开发环境前端经 **`/dp-ws/...`** 代理到 `/ws/...`，避免与 Vue HMR 占用 `/ws`（见 [docs/WEBSOCKET.md](../../docs/WEBSOCKET.md)）。

---

## 3. 事件 → 会话动作 → 是否「全量快照」→ 关键类

说明：**全量快照**指与 `GET /dpRoom/getNowRoom` 同结构的房间 JSON（经 `getRoomSnapshotForViewer` / `snapshotForViewerFromLive` 按 `viewerNickname` 过滤）。定时广播路径上对**每条连接**若字符串与上次相同则 **跳过下发**（去重）。

| 事件 | 应对哪些 session 做什么 | 是否全量快照（或等价） | 关键类 / 方法 |
|------|-------------------------|-------------------------|----------------|
| **对局 WS 建连** | 将该 `WebSocketSession` 加入 `roomSessions[roomId]`；首包推送房间快照；若存在 **`lastRoomMusicJson`** 再发一帧 `roomMusic` | 首包：**是**（快照 + 可选音乐帧） | [`DpGameRoomWebSocketHandler`](../../src/main/java/com/example/mgdemoplus/websocket/DpGameRoomWebSocketHandler.java)，[`DpGameRoomPushService#register`](../../src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java)，[`sendInitialSnapshot`](../../src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java) |
| **对局 WS 断线 / 传输错误** | 仅从 `roomSessions` 移除该 session；**不改 `roomMap` 成员** | 否 | `afterConnectionClosed` / `handleTransportError` → [`removeSessionFromRoom`](../../src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java) |
| **每秒全局 tick（有订阅时）** | 对该 `roomId` 下**每条打开的连接**：按需生成「按观看者过滤」快照字符串；与 `lastBroadcastPayloadBySession` 相同则跳过；若人已不在房内或房间已无 live，则下发 `roomClosed` 并移除/关闭 | **有条件**：内容变了才发；等价全量结构但可能不发 | [`DpRoomServiceImpl#runGlobalSecondTickForSingleRoom`](../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpRoomServiceImpl.java) → [`broadcastIfSubscribed`](../../src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java) |
| **桌上玩家 `/dpRoom/heartbeat` 超时被移除** | **`viewerNickname` 匹配离场昵称** 的 session：`roomClosed` + `GOING_AWAY` | 否（控制帧） | [`tickEvictStaleSeatedPlayersOnHeartbeat`](../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpRoomServiceImpl.java) → [`shutdownSubscriptionsForNicknameInRoom`](../../src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java) |
| **观众心跳超时被移出** | 走 `exitRoom`；**未**调用 `shutdownSubscriptionsForNicknameInRoom`。后续 **`broadcastIfSubscribed`** 发现昵称已不在房内时对相关连接发 **`roomClosed`**（约 1s 粒度） | 否（先业务退房，再靠广播路径关 WS） | [`tickEvictStaleSpectatorsOnHeartbeat`](../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpRoomServiceImpl.java)，[`broadcastIfSubscribed`](../../src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java) |
| **房间从 `roomMap` 摘除（拆房收尾）** | 该 `roomId` **全部** WS：`roomClosed` + 关闭；清理 `lastRoomMusicJson` | 否 | [`finalizeHallAfterRoomRemoved`](../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpRoomServiceImpl.java) → [`shutdownSubscriptionsForRoom`](../../src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java) |
| **上行 `chatSend`** | 校验昵称属房内玩家或观众后，向该房 **所有订阅 session** 广播同一帧 JSON（不经「按观看者」改写聊天内容） | 否（`_ws":"chat"`） | [`handleClientTextMessage`](../../src/main/java/com/example/mgdemoplus/websocket/DpGameRoomPushService.java) → `broadcastRawJsonToRoom` |
| **上行 `roomMusicSync`** | 同上，广播 `_ws":"roomMusic"`；更新 `lastRoomMusicJson` | 否 | 同上 |
| **快匹 WS 建连** | 校验 JWT 后注册 `nicknameSessions`；调用 **`pushQuickMatchLobbySnapshot`**：可能 `WAITING`（队列位）或 `MATCHED`（已在房内） | 否（状态小包 JSON） | [`DpQuickMatchWebSocketHandler`](../../src/main/java/com/example/mgdemoplus/websocket/DpQuickMatchWebSocketHandler.java)，[`DpRoomServiceImpl#pushQuickMatchLobbySnapshot`](../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpRoomServiceImpl.java)，[`DpQuickMatchPushService`](../../src/main/java/com/example/mgdemoplus/websocket/DpQuickMatchPushService.java) |
| **快匹 WS 断线 / 传输错误** | `removeSession`：按 session 属性从对应昵称集合移除 | 否 | `DpQuickMatchWebSocketHandler` → [`DpQuickMatchPushService#removeSession`](../../src/main/java/com/example/mgdemoplus/websocket/DpQuickMatchPushService.java) |
| **快匹业务侧通知** | `notifyWaiting` / `notifyMatched` / `notifyIdle`：**该昵称下所有仍打开的 session** 各发一帧（多开则多条连接都收到） | 否 | [`DpQuickMatchPushService`](../../src/main/java/com/example/mgdemoplus/websocket/DpQuickMatchPushService.java)；调用链见 [`DpRoomServiceImpl`](../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpRoomServiceImpl.java)（如 `quickMatchJoinQueueOrImmediate`、`notifyQuickMatchTimedOut`） |

---

## 4. 站点心跳 / 在线态（REST，与 WS 解耦）

| 接口 | 鉴权 | 行为 |
|------|------|------|
| `GET /dp/presence/site-heartbeat/config` | 匿名（白名单） | 返回 `ttlMs`、`suggestedIntervalMs` |
| `POST /dp/presence/site-heartbeat` | JWT | [`DpSitePresenceService#touchSiteHeartbeat`](../../src/main/java/com/example/mgdemoplus/service/dp/DpSitePresenceService.java) 写入单机 **`ConcurrentHashMap<Integer, Long>`**（`last_seen_site`） |

**TTL**：`mgdemoplus.dp-site-presence-ttl-ms`，默认 **90000** ms（[`application.yml`](../../src/main/resources/application.yml)）；建议客户端间隔 **≥ max(5s, TTL/3)**（[`DpSitePresenceService#getSuggestedHeartbeatIntervalMs`](../../src/main/java/com/example/mgdemoplus/service/dp/DpSitePresenceService.java)）。

**好友列表 `presence` 合成**：[`DpFriendSocialService#listFriends`](../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpFriendSocialService.java) —— **房内态 `IN_GAME` 优先**（[`DpFriendPresenceService`](../../src/main/java/com/example/mgdemoplus/service/dp/DpFriendPresenceService.java)，单机内存）；否则若站点心跳在 TTL 内为 **`IDLE`**，否则 **`OFFLINE`**。房内态由进房/退房/心跳踢人等路径驱动，与 WS 无直接绑定。

**房内存活**：`POST /dpRoom/heartbeat`（[`DpRoomController`](../../src/main/java/com/example/mgdemoplus/controller/dp/DpRoomController.java)）更新桌上 `lastHeartBeat` 或观众 `spectatorLastPresenceMs`（[`DpRoomServiceImpl#heartbeat`](../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpRoomServiceImpl.java)），与站点心跳 **并行、语义分离**（README 与 [docs/WEBSOCKET.md](../../docs/WEBSOCKET.md) 一致）。

**文档漂移备忘**：[README.md](../../README.md) 提及兼容旧路径 `POST /dp/siteHeartbeat`；当前 `src/main/java` 未见该映射，集成测试应以实际 Controller 为准。

---

## 5. 与房间状态机交界

房内阶段迁移、进退房、踢人、拆房等与 **`roomMap` / `synchronized(DpRoomBO)`** 相关的状态机与 REST 编排，见 **`docs/business-flow/audit-agent-01-room-lobby-lifecycle.md`**（本文只描述实时推送层如何挂靠在这些结果之上）。

---

## 6. 与快匹业务交界

默认队列、`attemptQuickMatchPairing`、大厅索引与消费者编排等，见 **`docs/business-flow/audit-agent-03-quickmatch-social.md`**。本文仅覆盖：**快匹 WS** 作为下行通知通道及 `pushQuickMatchLobbySnapshot` 与队列/房内查询的衔接。

---

## 7. WS 专项漏测清单（建议）

以下为手工/自动化时可逐项过关的检查项，用于补齐 README 已提到的「公共 WS 集成级用例较少」缺口。

1. **断线重连**：对局页指数退避重连后，是否收到首包快照 + 必要 `roomMusic` 补帧；`lastBroadcastPayloadBySession` 是否与重连后期望一致。
2. **双开 / 多标签**：同一 `nickname` 多条对局 WS 订阅同房或快匹 WS 同事务；快匹 `notify*` 是否对所有连接广播导致重复 UI。
3. **无效 / 过期 / 伪造 token（快匹）**：缺参、`sub` 与 `nickname` 不一致、签名错误时是否在握手阶段关闭且reason可读。
4. **对局 WS 无 JWT**：任意 `roomId` + 任意 `nickname` 连接是否仍可接受；冒名 `chatSend`/`roomMusicSync` 是否被 **`isNicknameInRoom`** 拒绝。
5. **`roomId` 缺失或房间不存在**：首包 `roomClosed` 与关闭行为；`broadcastIfSubscribed` 在 `live == null` 时的清理是否与订阅集合一致。
6. **踢人 / 主动退房 / 解散**：桌上心跳超时 vs 观众超时 vs `exitRoom` 路径差异——WS 是立即 `shutdownSubscriptionsForNicknameInRoom` 还是依赖下一轮 `broadcastIfSubscribed` 发 `roomClosed`。
7. **房主移交 / 批量踢人**：房间仍存在时订阅者应持续收到快照；被踢至观众席（未退房）时 WS 是否仍应保持并可收到广播。
8. **聊天节流**：同连接 1.2s 内重复 `chatSend`；超长文本截断；非 JSON 上行静默丢弃。
9. **BGM 节流与安全路径**：400ms 内重复 `roomMusicSync`；非法 `webPath`（非 `/music/`、含 `..` 等）拒绝。
10. **僵尸连接**：广播发送 IOException 后是否从 `roomSessions` 移除。
11. **多实例（若部署）**：两节点各连一客户端同房，验证推送分裂；站点心跳 map 不同步导致好友列表一端 IDLE 一端 OFFLINE。
12. **站点心跳 TTL 边界**：停止 `POST /dp/presence/site-heartbeat` 超过 TTL 后好友列表是否落 `OFFLINE`，进房后是否优先 `IN_GAME`。

---

## 8. 小结

- **对局 WS**：**订阅表** + **每秒 tick 驱动**的有条件快照广播；**上行**仅 `chatSend`、`roomMusicSync`；**强制关 WS** 集中在「房间拆除」「桌上心跳踢出」及部分广播路径发现的「已不在房内」。
- **快匹 WS**：**握手 JWT**；**仅服务端下行**（当前 Handler 未处理客户端文本）；按昵称维护多连接集合。
- **站点在线**：纯 REST + **单机 TTL map**，与 WS、**`roomMap`** 解耦；好友列表展示为三态合成。

*本文件由审计 Agent 产出；禁止依赖本文替代代码阅读。*
