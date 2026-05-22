> 扫描日期：2026-05-22 · 范围：社交/用户/牌谱 · 覆盖重写

# 社交 / 用户 / 牌谱 / 局内聊天

与代码不一致时以仓库实现为准。专题文档 [docs/JWT.md](../JWT.md)、[docs/dp_friend_mailbox_mvp.md](../dp_friend_mailbox_mvp.md) 若与下文冲突，见各节「可能滞后」标注。

---

## 1. 包职责与边界

| 包 | 职责 | 与 `room` 的边界 |
|----|------|------------------|
| `social/` | 好友申请/关系、邮箱（进房邀请）、好友私信、SSE 通知聚合与推送 | 进房/观战调用 `DpRoomService`（`joinRoomInviteAsSpectator` 等）；不写 `roomMap` |
| `presence/` | **站点在线**（`DpSitePresenceService`）与 **房内态**（`DpFriendPresenceService`，仅 IDLE/IN_GAME 内存） | 房内态由 `room` 进出房时 `markInGame` / `markIdle`；站点心跳与房内 WebSocket 心跳无关 |
| `user/` | 注册/登录资料、bcrypt 口令、Redis 单点 jti、头像上传落库 | 不持有对局状态；荣誉战绩读 `dp_user_stats`（V4） |
| `history/` | 牌谱 JSON 持久化与 REST 查询（参与者维度） | **写入**在 `room` 结算链路（`DpHandHistoryPersistService` / `DpHandHistoryObservedService`）；本包只读查询 |
| `roomchat/` | 局内聊天内存缓冲 + 摘房批量落库 | 消息在房内存活；`DpRoomLobbySync` / 摘房时 `flushRoomToDatabase`；无独立 REST |

**调用链（面试官速览）**

1. **好友私信**：`POST /dp/friends/{peer}/messages` → `DpFriendChatService` → MyBatis 写 `dp_friend_message` → `SocialNotifyPublisher.notifyUser` → `SocialSseHub.broadcastNotify`（同 JVM）。
2. **站点在线**：`POST /dp/presence/site-heartbeat` → `DpSitePresenceService.touchSiteHeartbeat` → 进程内 `ConcurrentHashMap`；好友列表由 `DpFriendSocialService.listFriends` 合成「站点在线 + 房内 IN_GAME」。
3. **牌谱查询**：`GET /dpHandHistory/list?userId=` → `DpHandHistoryServiceImpl` → `dp_observed_hand_participant` + PageHelper；写入发生在 `DpRoomServiceImpl` 一手结束归档时（`history` 包 persist 服务）。

---

## 2. REST 与认证

### 2.1 路由前缀

| 前缀 | Controller | 响应形态 |
|------|------------|----------|
| `/dp` | `DpFriendMailboxController` | `ResultUtil` JSON |
| `/dp/social` | `DpSocialController` | SSE：`text/event-stream`；摘要：`ResultUtil` |
| `/dpUser` | `DpUserController` | `ResultUtil` |
| `/dpHandHistory` | `DpHandHistoryController` | 直接返回 `DpHandHistoryPageVO` / `DpHandHistoryDetailVO`（非 `ResultUtil`） |
| `/upload` | `UploadController` | 纯字符串：成功为 `/images/{uuid}_原名`，失败为 `"上传失败"` |

### 2.2 JWT 与白名单

配置：`application.yml` → `mgdemoplus.jwt.*`；过滤器 `JwtAuthenticationFilter` + `JwtSecurityConstants.PERMIT_ALL`。

| 路径 | 是否需 JWT |
|------|------------|
| `GET/POST /dpUser/loginProfile`、`POST /dpUser/registerUser` | 否 |
| `GET /dp/presence/site-heartbeat/config` | 否 |
| 其余 `/dp/**`、`/dpUser/**`、`/dpHandHistory/**`、`POST /upload` | 是 |
| `GET /dp/social/stream` | 不在 `permitAll`；过滤器对 **仅此路径** 额外支持 `?token=`（EventSource 无法设 Header） |

**身份解析（社交/用户 Controller 共性）**：`SecurityContextHolder` → `auth.getName()` 为 **昵称** → `DpUserMapper.selectByNickname` → `dp_user.id`。请求体里的 `toUserId` / `inviteeUserId` 等仅作业务目标，**不得**替代当前用户 id。

**牌谱 API 注意**：`DpHandHistoryController` 仅校验 `userId` 存在且为参与者，**未**将 query 的 `userId` 与 JWT 当前用户绑定；客户端须传本人 id，服务端未防越权读他人牌谱（若需硬约束应在 Service 层补 JWT 对照）。

### 2.3 密码与 JWT（以源码为准）

| 项 | 实现 |
|----|------|
| 口令落库 | `CryptoUtil.bcryptEncode` / `bcryptMatches`（`DpUserServiceImpl`） |
| 前端传参 | 注册/登录/改密：**明文** POST，服务端 bcrypt 后存 `dp_user.password`（`varchar(64)` 足够） |
| JWT 签发 | `JwtTokenService.generateToken(nickname, jti)`；subject = 昵称 |
| 单点登录 | Redis `mgdemo:cache:login:{nickname}` 存 jti，TTL 与 token 一致（`mgdemoplus.jwt.expiration.time`，默认 259200000 ms ≈ 3 天） |
| 改昵称 | `PUT /dpUser/profile` 成功且昵称变更时返回新 `token`，并 `removeLoginJti` 旧昵称 |

> **专题文档可能滞后**：[docs/JWT.md](../JWT.md) 仍写「口令 MD5」；实现已改为 **bcrypt**。根 [README.md](../../README.md) 已注明以 yml + 源码为准。

---

## 3. `/dp` — 好友、邮箱、私信、站点心跳

`DpFriendMailboxController` → `@RequestMapping("/dp")`。

### 3.1 好友与邮箱（MVP + 扩展）

| 方法 | 路径 | Service | 说明 |
|------|------|---------|------|
| POST | `/friends/requests` | `DpFriendSocialService` | body: `toUserId` |
| GET | `/friends/requests/pending` | 同上 | 入站待处理申请 |
| POST | `/friends/requests/{id}/accept` \| `/reject` | 同上 | |
| GET | `/friends` | 同上 | 含站点在线 + `DpFriendPresenceService` 房内态 |
| DELETE | `/friends/{friendUserId}` | 同上 | 删好友 |
| GET | `/mailbox` | 同上 | 邮箱列表（申请 + 进房邀请） |
| GET | `/mailbox/unread-count` | 同上 | 未读数 |
| POST | `/room-invites` | 同上 | body: `roomId`, `inviteeUserId`；邀请 **60s** 过期（`expires_at`） |
| POST | `/room-invites/{id}/accept` \| `/reject` | 同上 | 接受可走 `DpRoomService` 进房 |

### 3.2 观战 / 跟随进房（跨 `room`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/friends/{friendUserId}/spectate-room` | 只读解析好友可跟随的房间号 |
| POST | `/friends/follow-room` | body: `friendUserId` |
| POST | `/room-follow/spectate` | body: `friendUserId`；内部 `joinRoomInviteAsSpectator` |

### 3.3 好友私信

| 方法 | 路径 | Service | 规则摘要 |
|------|------|---------|----------|
| POST | `/friends/{peerUserId}/messages` | `DpFriendChatService` | 须互为好友；敏感词；单聊最多 500 条；发送间隔 ≥ 1.2s |
| GET | `/friends/{peerUserId}/messages` | 同上 | `beforeId`、`limit` 分页 |
| POST | `/friends/{peerUserId}/messages/read` | 同上 | body: `lastReadMessageId` → `dp_friend_chat_read` |
| GET | `/friends/chat-unread-summary` | 同上 | per-friend 未读 |

写成功后 → `SocialNotifyPublisher.notifyUser(收件人)`。

### 3.4 站点心跳（`presence`）

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/presence/site-heartbeat/config` | 匿名 | 返回 `ttlMs`、`suggestedIntervalMs` |
| POST | `/presence/site-heartbeat` | JWT | 刷新 `DpSitePresenceService` 内存 `last_seen_site` |

配置：`mgdemoplus.dp-site-presence-ttl-ms`（默认 **90000**）；建议客户端间隔 ≈ **TTL/3**（代码 `max(5s, ttl/3)`）。与房内轮询心跳 **解耦**。

---

## 4. `/dp/social` — SSE（单 JVM）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/stream` | `SseEmitter`；`event: notify`；连接后立即发 initial 摘要 |
| GET | `/notify-summary` | REST 兜底；载荷与 SSE `notify` 一致 |

**`SocialSseHub`（进程内）**

- `ConcurrentHashMap<userId, Set<SseEmitter>>`；每用户最多 **3** 条连接，超出踢最旧。
- `timeout=0`（长连）；每 **30s** comment `heartbeat`；有心跳连接时顺带 `expireDueRoomInvitesAndNotify`。
- **无** Redis/消息队列扇出；多实例部署时 SSE 仅命中持有连接的节点。

**`SocialNotifyPayload` 字段**：`mailboxUnread`（待处理好友申请 + 未过期进房邀请）、`friendChatUnreadTotal`、`friendChatUnreadByFriendUserId`、`perFriend` 数组。

**配置**：`spring.mvc.async.request-timeout: -1`（避免默认 ~30s 掐断 SSE）。

---

## 5. `/dpUser` — 账户与资料

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/registerUser` | 否 | body: `DpUser`；敏感词/昵称长度；成功返回 `token` |
| GET | `/loginProfile` | 否 | query: `nickname`, `password` |
| GET | `/profile` | JWT | `DpUserProfileView`（含是否已设密、部分 stats） |
| PUT | `/profile` | JWT | 改昵称/密码需 `oldPassword`；昵称变更返回新 token |
| GET | `/stats/{userId}` | JWT | 公开荣誉 `DpPlayerHonorView`（局内点头像） |

实现：`user/impl/DpUserServiceImpl`；Mapper：`common/mapper/DpUserMapper`；统计：`user/mapper/DpUserStatsMapper` → `dp_user_stats`（V4）。

---

## 6. `/dpHandHistory` — 牌谱查询

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/list` | `userId` 必填；`page`/`pageSize`（最大 100）；参与者表分页 |
| GET | `/detail` | `handHistoryId` + `userId`；非参与者 404；payload 来自 `dp_observed_hand_history.payload_json` |
| GET | `/checkUserAndOtherPlayerHandHistoryList` | 两人共同参与的手牌分页 |

持久化（写）：`history/DpHandHistoryPersistService`、`DpHandHistoryObservedService`，由 `room` 一手结束触发，不在本 Controller 暴露。

---

## 7. `/upload` — 头像

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/upload` | query: `id`（用户 id）、`file`；落盘 `mgdemoplus.images.file-location`；DB 存 `/images/{uuid}_{原名}`；`UploadService` → `UploadMapper` 更新 `dp_user.avatar_url` |

静态访问：`WebConfig` `/images/**` 与上传目录一致。**未**校验 JWT 用户 id 与参数 `id` 一致（需 JWT 但未绑定身份）。

---

## 8. 局内聊天（`roomchat/`，无 REST）

```
WebSocket/room 发言 → RoomChatBuffer.append（每房最多 500 条内存）
    → 摘房 / 大厅同步摘出 → DpRoomChatPersistenceService.flushRoomToDatabase
    → dp_room_chat_message 批量 insert
```

主键：`V3__room_chat_composite_pk.sql` 将 `(room_id, id)` 设为复合主键。

---

## 9. Flyway 本域相关表

| 版本 | 文件 | 表 |
|------|------|-----|
| V1 | `V1__init_schema.sql` | `dp_user`；`dp_observed_hand_history`；`dp_observed_hand_participant`；`dp_friend_request`；`dp_friend_link`；`dp_room_invite` |
| V2 | `V2__friend_dm_and_room_chat.sql` | `dp_friend_message`；`dp_friend_chat_read`；`dp_room_chat_message` |
| V3 | `V3__room_chat_composite_pk.sql` | `dp_room_chat_message` PK 调整 |
| V4 | `V4__user_stats.sql` | `dp_user_stats` |

（V5/V6 为房间统计倍率字段，属 `room`/用户荣誉扩展，非社交核心。）

---

## 10. 关键 Service / Controller 对照

| Controller | 主要 Service / 组件 |
|------------|---------------------|
| `DpFriendMailboxController` | `DpFriendSocialService`, `DpFriendChatService`, `DpSitePresenceService` |
| `DpSocialController` | `SocialSseHub`, `SocialNotifySummaryService` |
| `DpUserController` | `DpUserService`, `JwtTokenService`, `DpRedisLoginCacheService` |
| `DpHandHistoryController` | `DpHandHistoryService` → `DpHandHistoryServiceImpl` |
| `UploadController` | `UploadService` |

| Service | 包 | 依赖要点 |
|---------|-----|----------|
| `DpFriendSocialService` | `social/impl` | MyBatis 好友/邀请；`DpRoomService`；`SocialNotifyPublisher` |
| `DpFriendChatService` | `social/impl` | 私信 CRUD；敏感词；推送收件人 SSE |
| `SocialNotifySummaryService` | `social/notify` | 聚合邮箱 + 私信未读 |
| `SocialSseHub` / `SocialNotifyPublisher` | `social/notify` | 单 JVM SSE |
| `DpSitePresenceService` | `presence` | TTL 来自 `mgdemoplus.dp-site-presence-ttl-ms` |
| `DpFriendPresenceService` | `presence` | 房内 IDLE/IN_GAME，由 room 维护 |
| `DpUserServiceImpl` | `user/impl` | bcrypt；`DpSensitiveWordService` |
| `DpHandHistoryServiceImpl` | `history/impl` | PageHelper；参与者校验 |
| `DpRoomChatPersistenceService` | `roomchat` | 摘房 flush |

---

## 11. 配置摘录（`application.yml`）

```yaml
spring.mvc.async.request-timeout: -1   # SSE
mgdemoplus:
  jwt.secret / jwt.expiration.time: 259200000
  dp-site-presence-ttl-ms: 90000      # 可 env MGDEMOPLUS_DP_SITE_PRESENCE_TTL_MS
  images.file-location: ...           # 头像与 /upload、/images/**
```

时区：`mgdemoplus.time-zone`（默认 `Asia/Shanghai`），影响好友私信 `created_at` 等 `LocalDateTime`。

---

## README 建议修订点

面向后端结构说明（面试/评审）：

1. **分层**：社交与用户域按功能分包（`social` / `presence` / `user` / `history` / `roomchat`），Controller 集中在 `controller` 包；Service 接口在域根目录、实现在 `impl/`（与 `CLAUDE.md` 一致）。
2. **调用链示例**：「发私信」= Controller 解析 JWT 昵称 → id → `DpFriendChatService` 事务写库 → `SocialNotifyPublisher` → 本机 `SocialSseHub`；「牌谱列表」= REST 只读 `history`，写路径在 `room` 结算。
3. **与 `room` 边界**：内存 `roomMap`、WebSocket 广播属 `room` + `websocket`；社交只通过 `DpRoomService` 进房/观战；局内聊在房内存、摘房后 `roomchat` 落库；牌谱 JSON 在 `history` 表、由 room 归档写入。
4. **部署**：SSE、站点在线、好友房内态均为 **单进程内存**；多实例需产品层接受「通知仅本节点」或后续引入共享总线（当前 P0 未做）。
5. **安全文档**：同步更新 [docs/JWT.md](../JWT.md) 口令为 bcrypt；牌谱 API 建议文档化「须传当前用户 userId」或代码层强制 JWT 对照。

**根 README 可能过时项（勿直接粘贴 TODO 语气）**

- 仍写 `service.serviceImpl.npc` 等旧包路径 → 现为 `npc/`、`room/impl/` 等。
- [docs/JWT.md](../JWT.md) 写 MD5 → 实现为 **bcrypt**（根 README 第 91 行已部分纠正）。
- 旧路径 `POST /dp/siteHeartbeat` → 现为 `POST /dp/presence/site-heartbeat`。
- 若 README 未提 SSE `?token=`、`-1` async timeout、Redis jti 单点，可从本节补入文档索引。
