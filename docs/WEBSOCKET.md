# WebSocket 简要说明与本项目用法

面向想入门、又想知道「在我们工程里具体怎么用」的读者。不要求你先会网络协议细节。

---

## 1. WebSocket 是什么？

平时网页用 **HTTP**：多半是「问一句、答一句」，一次请求结束连接就告一段落（短连接）。

**WebSocket** 是浏览器和服务器之间的一条 **长连接**：连上之后，**双方都可以主动发数据**，不用你不停地发新的 HTTP 请求。

可以把它想成：HTTP 像一次次发短信问「到了吗？」；WebSocket 像 **一直通着的电话**，有动静随时说。

---

## 2. 和「定时轮询」比，好在哪？

**轮询**：前端每隔 1 秒调用一次 `getNowRoom` 之类的接口，问「有更新吗？」  
- 简单，但 **请求多**、略晚才能看到最新状态（取决于间隔）。

**WebSocket**：服务器在状态变化后（或按固定节奏）**把最新数据推给前端**。  
- 少很多无效 HTTP；多人同桌、状态频繁变时更合适。

本项目里：**游戏对局页**用 WebSocket 收房间快照；**心跳**仍用普通 HTTP（`POST /dpRoom/heartbeat`），两件事分工不同，不互相替代。

---

## 3. 在本项目里是怎么用的？

| 项目 | 说明 |
|------|------|
| **用途** | 仅 **DP 游戏对局页**（`front/dp_game` 里进桌后的界面）同步房间状态。大厅、房间内准备页仍主要用 HTTP。 |
| **后端路径** | Spring 注册在 **`/ws/dp-game`**，查询参数 **`roomId=房间号`**。 |
| **数据格式** | 与 **`GET /dpRoom/getNowRoom`** 返回的 JSON 一致，前端可直接复用同一套字段（底池、阶段、玩家列表等）。 |
| **推送节奏** | 服务端在 **原有 1 秒定时逻辑**里，若该房间 **有人连着 WebSocket**，就顺带序列化当前房间并广播；**未按「状态是否变化」做判断**（与「事件触发推送」不同，见下文第 7 节）。 |
| **Redis** | **未使用**。连接表存在单机内存（`ConcurrentHashMap` 一类结构），适合单实例部署；以后多机再考虑消息总线。 |

相关代码大致位置：

- 配置与注册：`WebSocketGameRoomConfig.java`
- 连接与首包：`DpGameRoomWebSocketHandler.java`、`DpGameRoomPushService.java`
- 定时里触发广播：`DpRoomServiceImpl` 中每秒任务末尾调用 `broadcastIfSubscribed`
- 前端：`game.vue` 里 `connectGameWs`、`applyRoomFromServer`；连上后减少每秒 `getNowRoom` 轮询，并保留低频 HTTP 兜底。

---

## 4. 后端调用链（从配置到广播）

1. **`WebSocketGameRoomConfig`**：`@EnableWebSocket`，把路径 **`/ws/dp-game`** 注册到 **`DpGameRoomWebSocketHandler`**。
2. **浏览器连上** → **`DpGameRoomWebSocketHandler.afterConnectionEstablished`**：从 URL 解析 **`roomId`**；缺则关连接；否则 **`DpGameRoomPushService.register`**，并 **`sendInitialSnapshot`**（立刻发一帧当前房间 JSON）。
3. **断开** → **`afterConnectionClosed`** → **`unregister`**，从该房间的会话集合里移除。
4. **`DpGameRoomPushService`**：用 **`roomId → Set<WebSocketSession>`** 维护订阅；**`broadcastIfSubscribed`** 先 **`hasSubscribers`**，无人连则 **不序列化、不发送**；有人连则 **`getAllRooms` + JSON**，发给该房间所有仍打开的 session。房间已不存在时发 **`{"_ws":"roomClosed"}`** 并清理。
5. **`DpRoomServiceImpl`**：构造器里 **`Timer.scheduleAtFixedRate(..., 0, 1000)`**，每秒遍历房间逻辑，**每个未在本次循环中被 `continue` 跳过的房间**，在循环末尾调用 **`gameRoomPushService.broadcastIfSubscribed(room.getRoomId())`**。  
   - 推送 **不依赖** 房间数据是否相对上一秒有变化；**挂机时多秒快照相同，仍会推**（内容可能几乎一样）。
6. **`DpGameRoomPushService` 注入 `DpRoomServiceImpl` 使用 `@Lazy`**，避免 Spring 循环依赖。

---

## 5. `WebSocketSession` 是什么？

在 Spring WebSocket 里，**`WebSocketSession`** 表示 **这一条 WebSocket 长连接**：

- 可 **`sendMessage`** 向该客户端推数据；
- 可 **`getAttributes()`** 存键值（本项目把 **`roomId`** 放进 attributes，断开时用来 **`unregister`**）；
- 可 **`isOpen()`** 判断连接是否还活着。

与「HTTP Session（登录态、Cookie 里的会话 ID）」概念相近：都是 **标识这一次交互上下文**；区别是 WebSocket 是 **一条长连接对应一个 session 对象**。

---

## 6. 前端 `game.vue`：轮询、心跳、和 DevTools 里为什么「不像每秒有请求」

- **首屏**：先 **`loadGame()`**（HTTP `getNowRoom`），再 **`connectGameWs()`**。
- **`pollTimer`（1 秒）**：仅当 **未连上 WS**（且非 `CONNECTING`）时才 **`loadGame()`**；一旦 **`gameWsConnected === true`**，**不再每秒 HTTP 拉房间**。
- **`backupPollTimer`（15 秒）**：在 **已连上 WS** 时低频 **`loadGame()`**，防止长连异常导致界面长期不更新。
- **`heartbeatTimer`（5 秒）**：只发 **`POST /dpRoom/heartbeat`**，**不会**顺带拉 `getNowRoom`。
- **下注 / 弃牌等**：接口成功后代码里会 **`await loadGame()`**，因此 Network 里常出现 **bet → getNowRoom** 等与操作绑定的 XHR，**不是心跳触发的拉取**。

**重要**：房间更新走 WebSocket 时，数据在 DevTools → Network → **WS** 里看 **Messages**；**XHR 列表**不会出现「每秒一条 getNowRoom」。把「没看到 HTTP」当成「后端没推」是误解。

---

## 7. 观感、流量、以及「改成事件推送」的思路

**为什么两个人都不操作时，界面好像很久才「动」？**  
后端仍在按秒推 **同一套（或几乎相同）房间 JSON**；前端 **`onmessage` 每次都会 `applyRoomFromServer`**，没有「无变化则跳过」。若数据没变，**屏幕上就看不出差别**，容易误以为「没推送」。有下注、阶段变化等，快照一变，观感上才明显。

**WebSocket 到底省不省流量？**  
- **省的主要部分**：连上 WS 后 **不再每秒一次完整 HTTP GET**（少掉重复的 **请求头 + 响应头** 等开销）。  
- **仍接近每秒一次的部分**：服务端广播的 **房间 JSON 体积** 与轮询时单次响应体 **量级相近**（仍是约 1Hz 的应用数据下行）。  
若要连 JSON 也省，需要改为 **仅在状态变化时广播** 或 **降频**（见下）。

**若改为在 `bet` / `fold` / `exitRoom` 等方法末尾调用 `broadcastIfSubscribed`？**  
那就是 **按状态变更推送**，可减少挂机时的重复 JSON；但要保证 **所有会改房间状态的路径**（含定时器里的机器人行动、超时弃牌、踢人、准备超时等）都 **不漏推**，否则客户端会旧态；工程上可先 **加事件推送**，**保留低频定时或 HTTP 兜底** 再逐步收紧。

---

## 8. 开发环境为什么要 `/dp-ws` 代理？

用 `npm run dev` 时，Vue 开发服务器会为 **热更新（HMR）** 自己也开 WebSocket，配置里常挂在 **`/ws`**。

若游戏 WebSocket 也走 **`/ws/...`**，会和热更新 **抢同一路径**，表现为：游戏长连连不上或立刻断，前端只好继续 **每秒 HTTP 拉房间**。

因此在本项目中：

- **`vue.config.js`** 增加 **`/dp-ws`** 代理（`ws: true`），把路径改写到后端的 **`/ws`**。
- 前端开发环境连接 **`/dp-ws/dp-game`**，生产环境仍可直接 **`/ws/dp-game`**（与页面同源时）。

这是 **开发工具链** 与 **业务 WebSocket** 的路径规划问题，不是业务逻辑写错。

---

## 9. 生产部署要点

- 前端与 API **同域**时，页面用 **`ws://` / `wss://` + 当前 host + `/ws/dp-game`**（`game.vue` 已按是否 HTTPS 切换 **`wss`**）。
- 若前面有 **Nginx 反代**：需为 **`/ws/`**（或具体路径）配置 **WebSocket 升级**（如 `proxy_http_version 1.1`、`Upgrade` / `Connection` 头、`proxy_read_timeout` 适当加长），否则长连接容易失败或频繁断开。
- **多实例**：当前连接与广播均在 **单机内存**；扩多台应用机时，需另行设计 **跨节点广播**（如 Redis Pub/Sub），否则同一房间用户连在不同机器上会收不到彼此的推送。

---

## 10. 学习可以往哪延伸？

- **MDN**：搜索「WebSocket」看浏览器端 API（`new WebSocket(url)`、`onmessage` 等）。
- **Spring**：`spring-boot-starter-websocket`、`WebSocketConfigurer`、`TextWebSocketHandler`。
- **进阶**：SockJS / STOMP（兼容旧环境、消息规范）；多实例时的 **Redis Pub/Sub** 或消息队列做跨节点广播。

先把「长连接 + 服务端推送 + 与 HTTP 分工」理解透，再深入协议帧、代理、负载均衡，会顺很多。

---

## 11. 小结

- WebSocket = **双向、长连接**，适合 **实时状态同步**。  
- 本项目用于 **对局页房间状态**，数据对齐 `getNowRoom`，单机内存维护连接，**不配 Redis**；**推送由 1 秒定时任务驱动**，**不是**「仅状态变化才推」。  
- 开发时 **`/dp-ws` 是为了避开 Vue HMR 占用的 `/ws`**，属于工程经验，值得记住。  
- 看是否在推数据，请用 Network 的 **WS Messages**；XHR 少是 **前端在连上 WS 后停掉了每秒 `getNowRoom`**。

如有文档与实现不一致，以当前代码为准。
