# 好友私信 + 局内聊天（P0 数据层 / REST / 内存）

> SSE、前端、管理员按 roomId 查库均为后续阶段；本文档为 P0 后端实现规格。

## 1. 数据库（Flyway `V2__friend_dm_and_room_chat.sql`）

### `dp_friend_message`

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT AUTO_INCREMENT | 消息 id |
| sender_user_id | INT NOT NULL | 发送方 `dp_user.id` |
| recipient_user_id | INT NOT NULL | 接收方 |
| body | VARCHAR(500) NOT NULL | 正文 |
| created_at | DATETIME(3) | 发送时间 |

索引：`(recipient_user_id, id)`、`(sender_user_id, recipient_user_id, id)`。

**容量**：每无序好友对双向合计最多保留 **500** 条；发送事务内 INSERT 后若超限则按 `id` 升序删最旧。

### `dp_friend_chat_read`

| 列 | 类型 | 说明 |
|----|------|------|
| user_id | INT | PK 之一，读侧用户 |
| peer_user_id | INT | PK 之一，会话对端 |
| last_read_message_id | BIGINT | 已读到的最大消息 id |
| updated_at | DATETIME(3) | 更新时间 |

### `dp_room_chat_message`

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT | 与内存缓冲单调 id 一致（摘房批量 INSERT 时显式写入） |
| room_id | VARCHAR(32) | 房间号 |
| sender_user_id | INT NULL | 可解析则填 |
| sender_nickname | VARCHAR(128) | 昵称快照 |
| body | VARCHAR(200) | 正文 |
| server_time_ms | BIGINT | 服务端时间戳 |
| created_at | DATETIME(3) | 入库时间 |

索引：`(room_id, id)`。P0 **无**对外按 roomId 查询 API。

---

## 2. REST API（均需 JWT，`/dp` 或 `/dpRoom`）

### 2.1 好友私信 — `DpFriendMailboxController`（`/dp`）

#### `POST /dp/friends/{peerUserId}/messages`

发送私信（同步落库，非定时）。

**请求**

```json
{ "body": "你好，来一局？" }
```

**成功 200**

```json
{
  "success": true,
  "code": 20000,
  "message": "成功",
  "data": {
    "messageId": 42,
    "createdAt": "2026-05-17T12:34:56.789"
  }
}
```

**失败**：非好友 `success:false` + 文案「非好友，无法发送私信」；body 超长；频控（与局内聊天同量级 **1200ms**/发送方）。

#### `GET /dp/friends/{peerUserId}/messages?beforeId=&limit=`

历史分页（仅好友）。`beforeId` 可选，缺省取最新；`limit` 默认 50，上限 100。

**响应**

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "messageId": 40,
        "senderUserId": 1,
        "recipientUserId": 2,
        "body": "hello",
        "createdAt": "2026-05-17T12:00:00.000",
        "mine": true
      }
    ]
  }
}
```

#### `POST /dp/friends/{peerUserId}/messages/read`

**请求**

```json
{ "lastReadMessageId": 42 }
```

**响应**：`{ "success": true, "data": { "message": "ok" } }`

#### `GET /dp/friends/chat-unread-summary`

**响应**

```json
{
  "success": true,
  "data": {
    "totalUnread": 3,
    "perFriend": [
      { "userId": 2, "count": 3 }
    ]
  }
}
```

### 2.2 局内聊天最近条 — `DpRoomController`（`/dpRoom`）

#### `GET /dpRoom/{roomId}/chat/recent?limit=50`

JWT 解析当前用户昵称 → 须在房内（与 WS `chatSend` 同口径 `isNicknameInRoom`）。

**响应**（房间不在 `roomMap`：404；在房无消息：空数组）

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "1",
        "nickname": "alice",
        "text": "glhf",
        "serverTime": 1715937296789,
        "senderUserId": 1
      }
    ]
  }
}
```

---

## 3. 局内聊天内存 + WS

- **`RoomChatBuffer`**：`roomId → CopyOnWriteArrayList`，单房上限 **500**；单调 `id`（long，JSON 用字符串）。
- **`DpGameRoomPushService#handleChatSend`**：校验通过后 **先** `append` 内存，再广播 `_ws:chat`（含 `id`、`ttlMs`）。
- **房间存活期间不写** `dp_room_chat_message`。

### 摘房落库挂钩

凡从 `roomMap` 摘除且走收尾的路径，在 **`DpRoomServiceImpl#finalizeHallAfterRoomRemoved`** 开头调用：

`dpRoomChatPersistenceService.flushRoomToDatabase(roomId)`

→ `RoomChatBuffer.drain(roomId)` → 批量 `INSERT dp_room_chat_message` → 缓冲已清空。

触发链示例：`removeRoom` → `tryUnregisterEmptyRoomAssumeLocked` → `finalizeHallAfterRoomRemovedWithPresenceSnapshot` → `finalizeHallAfterRoomRemoved`。

---

## 4. P0 实现类清单

| 类 | 职责 |
|----|------|
| `V2__friend_dm_and_room_chat.sql` | 三表 DDL |
| `DpFriendMessageRow` / `DpFriendChatReadRow` / `DpRoomChatMessageRow` | 实体 |
| `DpFriendMessageMapper` / `DpFriendChatReadMapper` / `DpRoomChatMessageMapper` | MyBatis |
| `DpFriendChatService` | 私信发送/分页/已读/未读 |
| `RoomChatBuffer` / `RoomChatEntry` | 局内内存 |
| `DpRoomChatPersistenceService` | 摘房 flush |
| `DpFriendMailboxController` | 私信 REST |
| `DpRoomController` | `chat/recent` |
| `DpGameRoomPushService` | WS 聊天改内存先写 |
| `DpRoomServiceImpl` | `finalizeHallAfterRoomRemoved` 挂钩 |

---

## 6. 大厅社交通知 SSE（Agent2 / P0 内存扇出）

> **生产多副本**：当前 `userId → Set<SseEmitter>` 仅本机内存；多实例部署需 **Redis Pub/Sub（或 Stream）扇出** 到各节点再 `broadcastNotify`，本文档 P0 不实现。

### 6.1 端点

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/dp/social/stream` | JWT（见下） | `text/event-stream`，单用户最多 **3** 条连接，30s 注释心跳 |
| GET | `/dp/social/notify-summary` | `Authorization: Bearer` | 与 SSE 单次 `notify` 载荷字段一致（断线兜底） |

**不在** `JwtSecurityConstants.PERMIT_ALL`；除 query token 外仍需有效登录会话（Redis jti 与 Header 相同校验）。

#### EventSource 与 `?token=`

浏览器 `EventSource` 无法设置 Header，故 **仅** `GET /dp/social/stream` 支持：

```
GET /dp/social/stream?token=<JWT>
```

`JwtAuthenticationFilter#resolveToken`：优先 Bearer，否则上述路径读 `token` 参数；**禁止**把 token 写入 info 日志。

### 6.2 SSE 事件

| event | data（JSON） |
|-------|----------------|
| `notify` | 见下表 |

```json
{
  "mailboxUnread": 2,
  "friendChatUnreadTotal": 3,
  "friendChatUnreadByFriendUserId": { "123": 1, "456": 2 }
}
```

- `mailboxUnread`：与 `GET /dp/mailbox/unread-count` 的 `data.count` 一致（待处理好友申请 + 未过期进房邀请）。
- `friendChatUnreadTotal` / `friendChatUnreadByFriendUserId`：与 `GET /dp/friends/chat-unread-summary` 同源（`DpFriendChatService`）。

连接建立后立即推送一条 `notify`；之后由 `SocialNotifyPublisher` 在写操作成功后推送。

每 **30s** 发送 SSE 注释行心跳（`: heartbeat`）；若有活跃 SSE 连接，同周期执行进房邀请过期清理并通知被邀请人。

### 6.3 实现类

| 类 | 职责 |
|----|------|
| `DpSocialController` | `/dp/social/stream`、`/notify-summary` |
| `SocialSseHub` | 连接注册、心跳、单用户连接上限 |
| `SocialNotifySummaryService` | 聚合 mailbox + 私信未读 |
| `SocialNotifyPublisher` | `notifyUser(userId)` 统一扇出 |
| `SocialNotifyPayload` | 载荷 DTO |

### 6.4 推送触发点（写操作成功后）

| 触发 | 通知用户 |
|------|----------|
| `DpFriendChatService#sendMessage` INSERT | **recipient**（接收方） |
| `DpFriendChatService#markRead` | 当前用户（已读方未读下降） |
| `DpFriendSocialService#sendFriendRequest` 新建/重新 PENDING | `toUserId` |
| `acceptFriendRequest` / `rejectFriendRequest` | 处理人 `currentUserId` |
| `createRoomInvite` INSERT | `inviteeUserId` |
| `acceptRoomInvite` / `rejectRoomInvite` 成功 | `currentUserId`（被邀请人） |
| `removeFriend` 成功 | 双方 |
| `expireDueRoomInvitesAndNotify`（SSE 心跳周期，有过期行时） | 各 `invitee_user_id` |

发送方私信：**P0 不推**（仅接收方未读变化）。

### 6.5 手工验证（勿提交真实密钥）

```bash
# 1) 登录拿 TOKEN_B（接收方）
TOKEN_B="<jwt>"

# 2) 摘要（Header）
curl -s "http://localhost:8080/dp/social/notify-summary" \
  -H "Authorization: Bearer $TOKEN_B"

# 3) SSE（query token，保持连接）
curl -N "http://localhost:8080/dp/social/stream?token=$TOKEN_B"

# 4) 另一用户 TOKEN_A 发私信后，终端 3 应收到 event: notify 且 friendChatUnreadTotal 增加
curl -s -X POST "http://localhost:8080/dp/friends/<PEER_ID>/messages" \
  -H "Authorization: Bearer $TOKEN_A" -H "Content-Type: application/json" \
  -d '{"body":"hi from sse test"}'
```

浏览器：`new EventSource('/dp/social/stream?token=' + encodeURIComponent(jwt))`，监听 `addEventListener('notify', ...)`。

---

## 热修：SSE 白名单 + 局内聊天联合主键

| 项 | 说明 |
|----|------|
| SSE | `GET /dp/social/stream` 加入 `JwtSecurityConstants.PERMIT_ALL`，避免 Tomcat ASYNC 二次分派触发 `authenticated()` 导致 `AuthorizationDeniedException`；`?token=` 仍在 `JwtAuthenticationFilter` 解析并校验 Redis jti；无有效身份时 Controller 返回 401 JSON，不注册 `SseEmitter`。 |
| 载荷 | `SocialNotifyPayload#toDataMap()` 同时输出 `perFriend` 数组；前端 `parseSocialNotifyPayload` 兼容 `friendChatUnreadByFriendUserId` Map。 |
| 落库 | Flyway `V3__room_chat_composite_pk.sql`：`dp_room_chat_message` 主键改为 `(room_id, id)`；摘房 flush 失败仅打日志，不阻断 `exitRoom`。 |
| 多实例 | P0 SSE 仍单 JVM `SocialSseHub`；生产多副本扇出待 P1。 |

---

## 5. 验证

```bash
mvn -q compile
```

**好友私信**（两 JWT 用户已是好友）：

```bash
curl -s -X POST "http://localhost:8080/dp/friends/2/messages" \
  -H "Authorization: Bearer <TOKEN_A>" -H "Content-Type: application/json" \
  -d '{"body":"hi"}'
# MySQL: SELECT * FROM dp_friend_message;
```

**局内聊天落库**（开房、WS 发几条 chat、清空房间真人/观众触发摘房）：

```sql
SELECT room_id, COUNT(*) FROM dp_room_chat_message GROUP BY room_id;
```
