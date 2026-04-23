# WebSocket 简要说明与本项目「游戏对局」实现

面向想入门、又想知道「在我们工程里具体怎么接到游戏页上」的读者。不要求先会网络协议细节。

---

## 1. WebSocket 是什么？

平时网页用 **HTTP**：多半是「问一句、答一句」，一次请求结束连接就告一段落（短连接）。

**WebSocket** 是浏览器和服务器之间的一条 **长连接**：连上之后，**双方都可以主动发数据**，不用你不停地发新的 HTTP 请求。

可以把它想成：HTTP 像一次次发短信问「到了吗？」；WebSocket 像 **一直通着的电话**，有动静随时说。

---

## 2. 和「定时轮询」比，好在哪？

**轮询**：前端每隔一段时间调用 `getNowRoom` 之类的接口，问「有更新吗？」  
- 简单，但请求多；更新有「最快间隔」限制。

**WebSocket**：服务器在状态变化后（或按固定节奏）**把数据推给前端**。  
- 少很多无效 HTTP；多人同桌、状态频繁变时更合适。

本项目里：**游戏对局页**（含**仅观战的观众席**，与同桌共用 `game.vue`）用 WebSocket 收房间快照；**是否在房、超时踢人**统一以普通 HTTP（`POST /dpRoom/heartbeat`）与 `DpRoomServiceImpl` 每秒定时逻辑为准：**桌上玩家**更新 `lastHeartBeat`；**观众席**更新 `spectatorLastPresenceMs`（与桌上同一超时阈值）。WS 仅负责推送与聊天/BGM，**断线不触发** `exitRoom`。

---

## 3. 在游戏里是怎么实现的？（总览）

对局页在 **`front/dp_game`**，进桌后 **`game.vue`** 会建 WebSocket，后端用 Spring 原生 WebSocket（**不是** STOMP），整条链路可以记成：

```text
浏览器 new WebSocket(url)
    → Spring 握手匹配 /ws/dp-game
    → DpGameRoomWebSocketHandler（建连 / 断连 / 收文本）
    → DpGameRoomPushService（按 roomId 挂会话、发快照、定时广播、聊天与 BGM）
    → DpRoomServiceImpl（内存 roomMap + 每秒 Timer 里调用 broadcastIfSubscribed）
```

| 环节 | 做什么 |
|------|--------|
| **连接 URL** | **`ws(s)://当前站点主机/ws/dp-game?roomId=…&nickname=…`**。`roomId` **必填**；`nickname` 建议带（对局页会带当前用户昵称），用于 **按观看者过滤** 的 JSON（例如隐藏他人底牌）。开发环境见下文 **`/dp-ws`**。 |
| **首包** | 连上后立刻 **`sendInitialSnapshot`**：若房间不存在则发 `{"_ws":"roomClosed"}` 并关连接；否则发 **与 `GET /dpRoom/getNowRoom` 同结构** 的房间 JSON（经 `getRoomSnapshotForViewer` 按昵称过滤）。若该房有人播过 BGM，会 **再发一帧** `roomMusic`（与快照去重无关）。 |
| **下行（房间状态）** | **`DpRoomServiceImpl`** 里 **`Timer.scheduleAtFixedRate(..., 0, 1000)`** 每秒遍历 `roomMap`；每个房间在当次循环末尾（除非中间 `continue` 整段跳过，例如空房已删）调用 **`gameRoomPushService.broadcastIfSubscribed(roomId)`**。仅当该房 **至少有一个 WS 订阅者** 时才序列化并发送。 |
| **下行去重** | **`DpGameRoomPushService`** 对每个 **`WebSocketSession`** 记下 **`lastBroadcastPayloadBySession`**：若本次要发的 JSON 字符串与 **该连接上次成功下发的快照** 相同，则 **本连接不再发送**（减流量；挂机时状态不变则多为 skip）。注意：**不同客户端** 因 `nickname` 不同，JSON 可能不同，各自独立去重。 |
| **上行（同一条连接）** | 客户端可发文本 JSON：`{"_ws":"chatSend",...}` 房间聊天、`{"_ws":"roomMusicSync",...}` 曲库同步；服务端校验昵称属于该房 **玩家或观众** 后广播，详见第 6 节。 |
| **Redis** | **未使用**。`roomId → Set<WebSocketSession>` 在 **单机内存**；多实例需另行设计跨节点广播。 |

**安全**：`JwtSecurityConstants` 中 **`/ws/**` 放行**（握手不强制 JWT），与「分享链接可旁观、轮询 `getNowRoom`」一致；聊天/音乐仍按 **昵称是否在房间内** 校验。

---

## 4. 后端代码位置与调用顺序（便于搜）

1. **`WebSocketGameRoomConfig`**：`@EnableWebSocket`，注册 **`/ws/dp-game`** → **`DpGameRoomWebSocketHandler`**，**`setAllowedOriginPatterns("*")`**（跨域开发方便；生产若需收紧可再改）。
2. **`DpGameRoomWebSocketHandler`**（继承 **`TextWebSocketHandler`**）  
   - **`afterConnectionEstablished`**：从 URL 解析 **`roomId`**，缺则 **`BAD_DATA`** 关闭；可选解析 **`nickname`** 写入 **`session.getAttributes()`** 的 `viewerNickname`。然后 **`pushService.register(roomId, session)`**，再 **`sendInitialSnapshot(session, roomId)`**。  
   - **`handleTextMessage`**：转给 **`DpGameRoomPushService.handleClientTextMessage`**。  
   - **`afterConnectionClosed`** / **`handleTransportError`**：按 **`roomId`** 调用 **`removeSessionFromRoom`**，仅从推送订阅集合移除该会话；**不因 WS 断线修改房间成员**（由 **`/dpRoom/heartbeat`** 与定时任务处理）。广播时发现僵死连接亦走同一移除逻辑。
3. **`DpGameRoomPushService`**  
   - **`roomSessions`**：`ConcurrentHashMap<String, Set<WebSocketSession>>`，表示每间房有哪些连接。  
   - **`sendInitialSnapshot`** / **`broadcastIfSubscribed`**：通过 **`DpRoomServiceImpl.getRoomSnapshotForViewer`** / **`snapshotForViewerFromLive`** 生成 **按观看者视角** 的 `DpRoom`，再 **`ObjectMapper.writeValueAsString`**；广播路径上与上次字符串相同则 **跳过该 session**（见上表）。  
   - 房间已从 `roomMap` 消失时，对该房 session 发 **`roomClosed`** 并清理。  
   - **`@Lazy DpRoomServiceImpl`**：打破与 **`DpRoomServiceImpl`** 注入 **`DpGameRoomPushService`** 的循环依赖。
4. **`DpRoomServiceImpl`**：构造器里注册 **每秒** 定时任务，循环内末尾 **`broadcastIfSubscribed(room.getRoomId())`**。

---

## 5. `WebSocketSession` 是什么？

在 Spring WebSocket 里，**`WebSocketSession`** 表示 **这一条 WebSocket 长连接**：

- 可 **`sendMessage`** 向该客户端推数据；  
- 可 **`getAttributes()`** 存 **`roomId`、`viewerNickname`**、聊天/音乐节流时间戳等；  
- 可 **`isOpen()`** 判断连接是否仍存活。

与「HTTP Session（登录 Cookie）」不是同一个东西，只是名字都叫 session。

---

## 6. 房间聊天与 BGM（与快照共用连接）

| 方向 | 约定 |
|------|------|
| **聊天** | 客户端发 `{"_ws":"chatSend","nickname":"…","text":"…"}`（`text` 最长 200 字、同连接约 1.2 秒一条）；服务端校验昵称属于当前房间 **玩家或观众** 后广播 `{"_ws":"chat",…,"ttlMs":15000}`，**不写数据库**。前端按昵称 **只保留一条展示**（新发顶旧）。 |
| **曲库 BGM** | 客户端发 `{"_ws":"roomMusicSync","nickname":"…","action":"play"|"pause"|"stop","trackId":…,"webPath":"/music/…","displayName":"…"}`（`play` 须合法 `trackId` 与 `webPath`；约 400ms 内同连接节流；`webPath` 须 **`/music/`** 下安全字符）。服务端广播 `{"_ws":"roomMusic",...}`，并 **`lastRoomMusicJson`** 记下该房最后一帧；**新订阅者**在首包快照后再收该帧。**不参与**与房间快照相同的「按 session 字符串去重」逻辑。 |

---

## 7. 前端 `game.vue`：轮询、心跳、DevTools 怎么看

- **首屏**：先 **`loadGame()`**（HTTP `getNowRoom`），再 **`connectGameWs()`**。  
- **`pollTimer`（1 秒）**：仅当 **未连上 WS**（且非 `CONNECTING`）时才 **`loadGame()`**；一旦 **`gameWsConnected === true`**，**不再每秒 HTTP 拉房间**。  
- **`backupPollTimer`（15 秒）**：在 **已连上 WS** 时低频 **`loadGame()`**，防止长连异常导致界面长期不更新。  
- **`heartbeatTimer`（5 秒）**：只发 **`POST /dpRoom/heartbeat`**，**不会**顺带拉 `getNowRoom`。  
- **`onmessage`**：若 `_ws === 'roomClosed'` / `'chat'` / `'roomMusic'` 分别处理，否则 **`applyRoomFromServer`** 当房间快照。

**重要**：房间快照走 WebSocket 时，请在 DevTools → Network → **WS** → **Messages** 里看；XHR 里不一定每秒有 `getNowRoom`（连上 WS 后前端会停高频轮询）。

---

## 8. 观感与「为什么挂机时界面好像不动」

后端仍 **每秒** 尝试推送；但若 **整段 JSON 与上一帧对该连接相同**，服务端 **直接不发**（`lastBroadcastPayloadBySession`），因此 **挂机、状态不变时** 前端可能 **收不到新消息**，屏幕无变化是正常现象。有下注、阶段变化等，快照字符串变化，观感上才明显。

若将来改为 **仅在业务方法里事件推送**，可减少定时路径上的空转，但须保证 **所有改房间状态的路径** 都补推或使用兜底轮询，避免客户端旧态。

---

## 9. 开发环境为什么要 `/dp-ws` 代理？

用 `npm run dev` 时，Vue 开发服务器会为 **热更新（HMR）** 自己也开 WebSocket，配置里常挂在 **`/ws`**。

若游戏 WebSocket 也走 **`/ws/...`**，会和热更新 **抢路径**，表现为长连连不上或立刻断。

因此：

- **`front/dp_game/vue.config.js`** 增加 **`/dp-ws`** 代理（`ws: true`），`pathRewrite` 到后端的 **`/ws`**。  
- 前端开发环境连接 **`/dp-ws/dp-game`**，生产环境 **`/ws/dp-game`**（与页面同源）。

---

## 10. 生产部署要点

- 前端与 API **同域**时，页面用 **`ws://` / `wss://` + 当前 host + `/ws/dp-game`**（`game.vue` 已按是否 HTTPS 切换 **`wss`**）。  
- 若前面有 **Nginx 反代**：需为 WebSocket 配置 **Upgrade**（`proxy_http_version 1.1`、`Upgrade` / `Connection`、`proxy_read_timeout` 等），否则长连接易失败。  
- **多实例**：当前连接与广播均在 **单机内存**；扩多台应用机时需 **跨节点广播**（如 Redis Pub/Sub），否则同房间用户连在不同机器上会收不到推送。

---

## 11. 学习可以往哪延伸？

- **MDN**：浏览器 **`WebSocket`** API。  
- **Spring**：`spring-boot-starter-websocket`、`WebSocketConfigurer`、`TextWebSocketHandler`。  
- **进阶**：SockJS / STOMP；多实例 **Redis Pub/Sub** 或消息队列。

---

## 12. 小结

- WebSocket = **双向长连接**，本项目用于 **对局页房间状态**，JSON 形态与 **`getNowRoom`** 一致；**按 `nickname` 做观看者视角过滤**。  
- **推送由 1 秒定时任务驱动**；**每条连接上** 若快照 JSON 与上次相同则 **不再下发**，与「完全无脑每秒推」不同。  
- **聊天 / BGM** 与快照 **同连接**，协议字段 **`_ws`** 区分。  
- 开发用 **`/dp-ws`** 避开 HMR 占用 **`/ws`**。  

如有文档与实现不一致，以当前代码为准。
--- 
# 自己的笔记
首先用房间id和每个人的会话作为键值对存入roomSessions  
然后用lastBroadcastPayloadBySession这个Map存会话和应推送的裁剪后的房间状态json，以下是大致实现思路：首先深拷贝一份房间状态(不是简单赋值，简单复制改的还是原来的房间，而深拷贝出来的房间信息后续怎么改都不影响原来roomMap里的房间状态)然后按昵称去裁剪信息，把其他人的信息设置成空，然后挂在lastBroadcastPayloadBySession里，等推送的时候按照昵称分别推送   
getRoomSnapshotForViewer->通过房间id和昵称实现处理  
snapshotForViewerFromLive->通过房间和昵称实现处理
deepCopyRoomForSnapshot->深拷贝过程
sanitizeHoleCardsForViewer->具体裁剪过程

