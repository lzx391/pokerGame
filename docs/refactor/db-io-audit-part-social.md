# DB I/O 审计 — 社交与用户域（main only）

> **范围**：`social/**`、`presence/**`、`user/**`、`controller/DpUser*`、`controller/DpFriend*`、`controller/DpSocial*`、`controller/UploadController`、`security/Jwt*`（含 Redis 会话）。  
> **权重**：低于对局热路径；对局内/进房/结算写 `dp_user_stats` 等见 **`docs/refactor/db-io-audit-part-room.md`**（待写）交叉引用。  
> **约束**：只读代码审计，不改实现、不提交。

---

## 1. 总览

| 存储 | 本域用途 |
|------|----------|
| **MySQL** | `dp_user`、`dp_user_stats`、好友/私信/邮箱（`dp_friend_*`、`dp_room_invite`） |
| **Redis** | 登录会话 `jti`（`mgdemo:cache:login:{nickname}`），**无**用户资料缓存 |
| **进程内存** | `DpSitePresenceService`、`DpFriendPresenceService`、SSE 连接表 — **零 DB** |

**JWT 与 DB**：`JwtAuthenticationFilter` 每条受保护请求仅 **Redis GET** 校验 `jti`；**不查** `dp_user`。昵称 → `dp_user.id` 在多个 Controller 的 `requireCurrentUser()` 中 **每次** `selectByNickname`。

---

## 2. Mapper 清单（读写语义）

### 2.1 `common.mapper.DpUserMapper` → `dp_user`

| 方法 | SQL 类型 | 说明 |
|------|----------|------|
| `registerUser` | INSERT | 注册 |
| `selectById` | SELECT | 按 id |
| `selectByNickname` | SELECT | 按昵称（**高频**） |
| `updateNickname` | UPDATE | 改昵称 |
| `updatePasswordHash` | UPDATE | 改密码 |
| `updateUserInfo` | UPDATE | 注解存在，本域未调用 |

### 2.2 `user.mapper.DpUserStatsMapper` → `dp_user_stats`

| 方法 | SQL 类型 | 本域调用方 |
|------|----------|------------|
| `selectByUserId` | SELECT | `DpUserServiceImpl`（资料/荣誉读） |
| `upsertAfterHand` | INSERT…ON DUPLICATE KEY UPDATE | **仅** `DpRoomServiceImpl`（对局结算，见 §6） |
| `tryUpdateLargestRoomNet` | UPDATE | **仅** `DpRoomServiceImpl`（离座结算，见 §6） |

### 2.3 `user.mapper.UploadMapper` → `dp_user.avatar_url`

| 方法 | SQL 类型 |
|------|----------|
| `upload(id, url)` | UPDATE `avatar_url` |

### 2.4 `social.mapper.DpFriendRequestMapper` → `dp_friend_request`

| 方法 | 类型 | 说明 |
|------|------|------|
| `insert` / `selectById` / `selectOne` / `selectCount` / `update` | MP 通用 | 申请 CRUD |
| `listPendingFriendRequestsToUser` | SELECT + JOIN `dp_user` | 待处理入站列表 |

### 2.5 `social.mapper.DpFriendLinkMapper` → `dp_friend_link`

| 方法 | 类型 | 说明 |
|------|------|------|
| `insert` / `delete` / `selectCount` | MP 通用 | 好友边 |
| `listFriendsOfUser` | SELECT + **双 JOIN** `dp_user` | 好友列表含 `friendNickname` |

### 2.6 `social.mapper.DpFriendMessageMapper` → `dp_friend_message`

| 方法 | 类型 | 说明 |
|------|------|------|
| `insert` | INSERT | 发私信（**须及时**） |
| `listConversation` | SELECT | 分页历史 |
| `countConversation` | SELECT | 裁剪前计数 |
| `deleteOldestInConversation` | DELETE 子查询 | 每对 >500 条删最旧 |
| `countUnreadFromPeer` | SELECT + 子查询 `dp_friend_chat_read` | 未读数 |

### 2.7 `social.mapper.DpFriendChatReadMapper` → `dp_friend_chat_read`

| 方法 | 类型 |
|------|------|
| `upsertLastRead` | INSERT … ON DUPLICATE KEY UPDATE |

### 2.8 `roomchat.mapper.DpRoomInviteMapper` → `dp_room_invite`（邮箱/进房邀请）

社交服务强依赖；Mapper 在 `roomchat` 包。

| 方法 | 类型 | 说明 |
|------|------|------|
| `insert` / `selectById` / `delete` / `selectCount` / `update` | MP | 邀请生命周期 |
| `listPendingRoomInvitesToUser` | SELECT + JOIN `dp_user` | 邮箱列表 |
| `listInviteeUserIdsWithPendingExpired` | SELECT DISTINCT | SSE 心跳过期扫描 |

---

## 3. 功能调用链（HTTP → Service → Mapper）

图例：**对局线程** = 是否在对局 WebSocket/房间变更主路径上同步执行（通常 **否**）。

### 3.1 注册 / 登录

| API | 链 | DB 次数（典型） | 对局线程 |
|-----|-----|----------------|----------|
| `POST /dpUser/registerUser` | `DpUserController` → `DpUserServiceImpl.registerUser` | `selectByNickname` + `INSERT dp_user` | 否 |
| `POST /dpUser/loginProfile` | `loginUserOrNull` | `selectByNickname` | 否 |
| 登录/注册成功 | `JwtTokenService.generateToken` + `DpRedisLoginCacheService.setLoginJti` | **Redis SET**（TTL=JWT 过期秒） | 否 |

敏感词：`DpSensitiveWordService` — 内存 DFA，无 DB。

### 3.2 资料（当前用户）

| API | 链 | DB | 对局线程 |
|-----|-----|-----|----------|
| `GET /dpUser/profile` | `requireCurrentUser` → `buildProfileView` | `selectByNickname` + `selectByUserId(stats)` | 否 |
| `PUT /dpUser/profile` | `updateProfile` | `selectById` + 可能 `selectByNickname` + `updateNickname` / `updatePasswordHash`；改昵称时 Redis `remove`+`set` jti | 否 |

`requireCurrentUser` 在 `DpUserController` 内：**每个需登录接口 +1** `selectByNickname`。

### 3.3 公开荣誉（局内点头像）

| API | 链 | DB | 对局线程 |
|-----|-----|-----|----------|
| `GET /dpUser/stats/{userId}` | `buildHonorView` | `selectById` + `selectByUserId(stats)` | **否**（独立 REST；前端 `GamePlayerSocialSheet.vue` 在用户打开社交面板时请求，非每条 WS 消息） |

**交叉**：`dp_user_stats` **写入**发生在 `DpRoomServiceImpl` 每手/离座（对局线程）→ **part-room**。

### 3.4 头像上传

| API | 链 | DB | 对局线程 |
|-----|-----|-----|----------|
| `POST /upload` | `UploadController` → `UploadServiceImpl` | `UPDATE avatar_url` | 否 |

说明：接口用请求参数 `id`，**未**走 JWT `requireCurrentUser`（鉴权与 DB 身份解耦，属产品/安全范畴）。

### 3.5 JWT 过滤器（全站）

| 组件 | 存储 | 对局线程 |
|------|------|----------|
| `JwtAuthenticationFilter.setAuthenticationIfTokenValid` | JWT 解析（CPU）+ **Redis** `getLoginJti(subject)` | **是**（过滤所有非白名单 HTTP，含 `/dpRoom/**`） |
| `JwtTokenService` | 无 DB/Redis | — |
| `JwtAuthenticationEntryPoint` | 无 DB | — |

**结论**：JWT 校验 **不写 DB**；会话踢下线靠 Redis jti 与签发时不一致。

### 3.6 好友申请 / 列表 / 删除

| API | Service | 典型 DB | 对局线程 |
|-----|---------|---------|----------|
| `POST /dp/friends/requests` | `sendFriendRequest` | `touchExpire`(批量 UPDATE 邀请) + `selectCount` link + `selectOne` request ×2 + 可能 `insert`/`update` request | 否 |
| `GET …/requests/pending` | `listPendingFriendRequestsInbound` | expire + `listPendingFriendRequestsToUser` | 否 |
| `POST …/accept` / `reject` | 事务 | expire + `selectById` + `update` + 可能 `insert` link | 否 |
| `GET /dp/friends` | `listFriends` | expire + `listFriendsOfUser`(JOIN×2) + 内存 presence | 否 |
| `DELETE /dp/friends/{id}` | `removeFriend` | expire + `update` invites + `delete` link | 否 |

`touchExpireRoomInvites`：`UPDATE dp_room_invite SET status=EXPIRED WHERE PENDING AND expires_at<=NOW()` — 在多数邮箱/好友接口入口调用。

### 3.7 好友私信

| API | Service | 典型 DB | 及时性 | 对局线程 |
|-----|---------|---------|--------|----------|
| `POST …/messages` | `sendMessage` @Transactional | `selectCount` 好友 + `selectById` 对方 + **`insert` message** + 可能 count/delete 裁剪 + `notifyUser`→摘要查询 | **insert 同步提交，满足及时落库** | 否 |
| `GET …/messages` | `listMessages` | `selectCount` 好友 + `listConversation` | 读可略滞后 | 否 |
| `POST …/read` | `markRead` | `upsertLastRead` + SSE 推送 | 读游标可 eventual | 否 |
| `GET …/chat-unread-summary` | `unreadSummary` | `listFriendsOfUser` + **每好友** `countUnreadFromPeer` | 否 | 否 |

推送：`SocialNotifyPublisher.notifyUser` → `SocialNotifySummaryService.buildForUser`（见 §3.9）。

### 3.8 邮箱（好友申请 + 进房邀请）

| API | 典型 DB | 对局线程 |
|-----|---------|----------|
| `GET /dp/mailbox` | expire + 列表 request + 列表 invite（均 JOIN user） | 否 |
| `GET /dp/mailbox/unread-count` | expire + 2× `selectCount` | 否 |
| `POST /dp/room-invites` | expire + 内存房间校验 + `selectById` invitee + link count + delete 旧 PENDING + **insert** invite | 否（房间存在性 **内存**） |
| `POST …/accept` / `reject` | select/update invite + **`joinRoomInviteAsSpectator`**（内存房间，**无**本域额外 DB） | 否 |

### 3.9 SSE / 通知摘要

| 入口 | DB 路径 | 对局线程 |
|------|---------|----------|
| `GET /dp/social/stream` | `requireCurrentUser` + `buildForUser`（初始 payload） | 否 |
| `GET /dp/social/notify-summary` | 同上 | 否 |
| `SocialSseHub` 每 **30s** 心跳 | 若有在线 SSE：`expireDueRoomInvitesAndNotify` → SELECT invitees + bulk UPDATE + **每被邀请人** `notifyUser` → 完整 `buildForUser` | 否（后台调度线程） |
| 任意社交写后 `pushSocialNotify` | `buildForUser` 一次 | 否 |

`buildForUser` 分解：

1. `mailboxUnreadCount`：`touchExpire` + `count` friend requests + `count` room invites  
2. `friendChatUnreadByFriendUserId`：`listFriendsOfUser` + **F 次** `countUnreadFromPeer`（F=好友数）

### 3.10 观战 / 跟随进房

| API | DB | 内存/对局 | 对局线程 |
|-----|-----|-----------|----------|
| `GET /dp/friends/{id}/spectate-room` | `selectById` friend + link `selectCount` | `findActiveRoomIdForNickname`、`canActiveMemberInviteFriends` | 否 |
| `POST /dp/room-follow/spectate` | 同上 + `joinRoomInviteAsSpectator` | 进房逻辑在 room | 否 |
| `POST /dp/friends/follow-room` | `selectById` + link + `findRoomIdContainingNickname` + join | 否 |

### 3.11 站点心跳 / 好友在线态

| API / 组件 | DB | 对局线程 |
|------------|-----|----------|
| `POST /dp/presence/site-heartbeat` | **无**（`ConcurrentHashMap`） | 否 |
| `GET …/site-heartbeat/config` | 无 | 否 |
| `DpFriendPresenceService` | **无**；由 `DpRoomServiceImpl` 在进/离房时 `markInGame`/`markIdle` | **写入触发在对局路径**（见 §6） |
| `listFriends` 展示态 | 读内存 presence + 内存 site TTL | 否 |

---

## 4. 及时性评估

| 数据 | 要求 | 现状 | 建议（审计意见，非实现） |
|------|------|------|-------------------------|
| 私信 `insert` | 必须及时可见 | `@Transactional` 同步 insert 后 SSE | 保持；避免改异步队列除非有补偿 UI |
| 私信已读游标 | 可略滞后 | `upsertLastRead` 同步 | 可接受 |
| 邮箱未读数 | 近实时 | 写后 `notifyUser` 重算；SSE 30s 补过期 | 可接受 |
| `selectByNickname` | 非强一致实时 | **每请求 1 次**，JWT subject 已是昵称 | **可缓存** `nickname → {id, avatarUrl}`（Redis 或 Caffeine，改昵称/删号失效） |
| `selectById`（邀请/私信校验） | 低频 | 按次查询 | 可与 userId 缓存合并 |
| 荣誉 `stats` 读 | 展示型 | 独立 GET；写在对局结算 | 读侧可加短期缓存；写路径见 part-room |
| 登录 `jti` | 强一致踢下线 | Redis TTL 与 JWT 对齐 | 已合理 |

---

## 5. 与对局域交叉（→ part-room）

以下 **不在** 本域 Controller，但会打 `dp_user` / `dp_user_stats`，重构对局 DB 时需一并考虑：

| 触发 | 位置 | Mapper / 表 |
|------|------|-------------|
| 创建/加入房间校验 `userId`↔昵称 | `DpRoomServiceImpl.resolveAndValidateUserId` | `selectById` |
| Presence 兜底 | `resolvePresenceUserIdPreferHint` | 可能 `selectByNickname` |
| 房内解析 `dpUserId` | `resolveDpUserIdForNicknameInRoom` | 可能 `selectByNickname` |
| 每手结算荣誉 | `upsertAfterHand` | `dp_user_stats` **写** |
| 离座最高房间净赢 | `tryUpdateLargestRoomNet` | `dp_user_stats` **写** |
| 房间 Controller 鉴权 | `DpRoomController.requireCurrentUser` | `selectByNickname` |

**本域间接**：`POST /dp/room-invites`、`follow-room` 等调用 `DpRoomService` **内存** API，不因邀请本身增加对局表写入。

**荣誉 UI**：`GET /dpUser/stats/{id}` 为牌桌侧栏 REST，**不**占用 WS 广播线程，但仍可能被用户在局内频繁点开 → 读放大属 **社交读**，非对局核心 I/O。

---

## 6. Redis vs DB（JWT 专节）

```
登录/注册成功
  → JwtTokenService.generateToken(nickname, jti)   [无 DB]
  → DpRedisLoginCacheService.setLoginJti           [Redis SET, key=mgdemo:cache:login:{nickname}]

每条 HTTP（非白名单或白名单带 token）
  → JwtAuthenticationFilter
       → verifyToken (HMAC)
       → getLoginJti(nickname)                    [Redis GET]
       → SecurityContext principal = nickname      [无 DB]

业务 Controller
  → requireCurrentUser()
       → dpUserMapper.selectByNickname             [MySQL SELECT]  ← 与 JWT 重复身份解析
```

改昵称：`removeLoginJti(old)` + `setLoginJti(new)`，旧 token 立即失效（Redis），无需 DB 会话表。

---

## 附录 A — 全表操作索引

| # | 表 | 操作 | 入口（代表性） | 对局线程 |
|---|-----|------|----------------|----------|
| A1 | `dp_user` | SELECT nickname | 注册/登录/所有 `requireCurrentUser` | 否 |
| A2 | `dp_user` | SELECT id | 资料更新、邀请、私信、荣誉 | 否 |
| A3 | `dp_user` | INSERT | 注册 | 否 |
| A4 | `dp_user` | UPDATE nickname/password | 资料 | 否 |
| A5 | `dp_user` | UPDATE avatar_url | `/upload` | 否 |
| A6 | `dp_user_stats` | SELECT | profile、honor | 否 |
| A7 | `dp_user_stats` | UPSERT/UPDATE | **仅** `DpRoomServiceImpl` | **是** |
| A8 | `dp_friend_request` | CRUD + 列表 | `/dp/friends/requests*`、`/dp/mailbox` | 否 |
| A9 | `dp_friend_link` | CRUD + 列表 | 好友/私信/邀请校验 | 否 |
| A10 | `dp_friend_message` | INSERT | 发私信 | 否 |
| A11 | `dp_friend_message` | SELECT/DELETE | 列表、未读、裁剪 | 否 |
| A12 | `dp_friend_chat_read` | UPSERT | 已读 | 否 |
| A13 | `dp_room_invite` | CRUD + 过期批量 UPDATE | 邮箱、SSE 心跳 | 否（心跳为调度线程） |

---

## 附录 B — HTTP 端点 → DB 估算（单次成功请求，量级）

| 端点 | 估算 SQL 次数 | 备注 |
|------|---------------|------|
| `registerUser` | 2 | |
| `loginProfile` | 1 | + Redis |
| `GET profile` | 2 | nickname + stats |
| `PUT profile` | 2–4 | |
| `GET stats/{id}` | 2 | |
| `POST /upload` | 1 | |
| `POST friend message` | 4–7 + 推送摘要 | 含 trim；+ `buildForUser` 若推送 |
| `GET friends` | 2 + F×0 | expire + list；presence 无 DB |
| `GET mailbox` | 3+ | expire + 两列表 |
| `GET social/stream` | 2 + F | 初始 `buildForUser` |
| `POST site-heartbeat` | **0** | |
| 任意需登录 `/dp/*` | **+1** | `selectByNickname` |

`F` = 好友数量。`buildForUser` ≈ `3 + 2F`（expire + 2 count + list friends + F unread counts）。

---

## 疑似浪费（本域）

1. **`selectByNickname` 重复解析身份**  
   JWT subject 已是昵称，却在 `DpUserController`、`DpFriendMailboxController`、`DpSocialController`（及 **part-room** 的 `DpRoomController`）每次请求再查 `dp_user`。高 QPS 心跳/轮询若带 JWT 会放大。  
   **建议**：缓存 `nickname → userId`（及展示用字段），或 JWT claims 携带 `userId`（改昵称需轮换策略）。

2. **SSE 30s 全局心跳 + 过期扫描**  
   `SocialSseHub.onHeartbeatTick`：只要存在 SSE 连接，每 30s 可能 `listInviteeUserIdsWithPendingExpired` + bulk UPDATE + 对每个过期被邀请人完整 `notifyUser`（含 `buildForUser`）。无过期时仍执行 UPDATE 型 `touchExpire` 的兄弟逻辑在其它入口已足够频繁。  
   **建议**：无 PENDING 过期行时跳过 NOTIFY；或降低扫描频率/仅在有 PENDING 时调度。

3. **`notifyUser` / `buildForUser` 的 N+1**  
   每次私信/邮箱变更对收件人执行：`listFriendsOfUser` + 每好友 `countUnreadFromPeer`。好友数 N 时 O(N) 查询。  
   **建议**：未读汇总表/Redis 计数器 + 增量维护；或至少合并为一条 GROUP BY SQL。

4. **`touchExpireRoomInvites` 调用过宽**  
   几乎每个 mailbox/friends 读写在先执行批量 UPDATE。读多写少场景下重复扫表。  
   **建议**：懒过期（仅 list/count 时 WHERE 过滤）+ 定时任务，替代每次请求 UPDATE。

5. **`listFriendsOfUser` 双 JOIN `dp_user`**  
   昵称已在 link 侧推导；若缓存用户展示名可减少 JOIN（或 denormalize snapshot）。

6. **发私信 `selectById(peer)`**  
   好友关系已 `selectCount` 校验；存在性可省略或合并一次批量校验。

7. **私信裁剪 `countConversation` + `deleteOldestInConversation`**  
   每条发送后可能额外 2 次 SQL；可改为 INSERT 后异步裁剪或触发器（低优先级）。

8. **`POST /upload` 无 JWT 绑定**  
   非 DB 浪费，但任意 `id` 可改头像 URL，与审计相关的身份一致性风险。

---

## 文档维护

- 对局热路径、`dp_room_*`、WebSocket 归档等：**`db-io-audit-part-room.md`**。  
- 表结构来源：Flyway `V1__init_schema.sql`、`V2__friend_dm_and_room_chat.sql`、`V4__user_stats.sql` 等。
