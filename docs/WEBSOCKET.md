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
| **推送节奏** | 服务端在 **原有 1 秒定时逻辑**里，若该房间 **有人连着 WebSocket**，就顺带序列化当前房间并广播；**未强制**在每一次下注/弃牌瞬间立刻推送（以后可再加「事件触发推送」优化延迟）。 |
| **Redis** | **未使用**。连接表存在单机内存（`ConcurrentHashMap` 一类结构），适合单实例部署；以后多机再考虑消息总线。 |

相关代码大致位置：

- 配置与注册：`WebSocketGameRoomConfig.java`
- 连接与首包：`DpGameRoomWebSocketHandler.java`、`DpGameRoomPushService.java`
- 定时里触发广播：`DpRoomServiceImpl` 中每秒任务末尾调用 `broadcastIfSubscribed`
- 前端：`game.vue` 里 `connectGameWs`、`applyRoomFromServer`；连上后减少每秒 `getNowRoom` 轮询，并保留低频 HTTP 兜底。

---

## 4. 开发环境为什么要 `/dp-ws` 代理？

用 `npm run serve` 时，Vue 开发服务器会为 **热更新（HMR）** 自己也开 WebSocket，配置里常挂在 **`/ws`**。

若游戏 WebSocket 也走 **`/ws/...`**，会和热更新 **抢同一路径**，表现为：游戏长连连不上或立刻断，前端只好继续 **每秒 HTTP 拉房间**。

因此在本项目中：

- **`vue.config.js`** 增加 **`/dp-ws`** 代理（`ws: true`），把路径改写到后端的 **`/ws`**。
- 前端开发环境连接 **`/dp-ws/dp-game`**，生产环境仍可直接 **`/ws/dp-game`**（与页面同源时）。

这是 **开发工具链** 与 **业务 WebSocket** 的路径规划问题，不是业务逻辑写错。

---

## 5. 学习可以往哪延伸？

- **MDN**：搜索「WebSocket」看浏览器端 API（`new WebSocket(url)`、`onmessage` 等）。
- **Spring**：`spring-boot-starter-websocket`、`WebSocketConfigurer`、`TextWebSocketHandler`。
- **进阶**：SockJS / STOMP（兼容旧环境、消息规范）；多实例时的 **Redis Pub/Sub** 或消息队列做跨节点广播。

先把「长连接 + 服务端推送 + 与 HTTP 分工」理解透，再深入协议帧、代理、负载均衡，会顺很多。

---

## 6. 小结

- WebSocket = **双向、长连接**，适合 **实时状态同步**。  
- 本项目用于 **对局页房间状态**，数据对齐 `getNowRoom`，单机内存维护连接，**不配 Redis**。  
- 开发时 **`/dp-ws` 是为了避开 Vue HMR 占用的 `/ws`**，属于工程经验，值得记住。

如有文档与实现不一致，以当前代码为准。
