# 好友、邮箱、私信、SSE 与局内聊天

> **核对日期**：2026-05-25  
> **权威来源**：`DpFriendMailboxController`、`DpFriendSocialService`、`DpFriendChatService`、`SocialSseHub`、`DpSocialController`；Flyway **V1**（好友/邀请）、**V2/V3**（私信与局内聊天）  
> **Status**: maintained

**鉴权**：`/dp/**` 下接口需 JWT；当前用户由 token `subject`（昵称）映射 `dp_user.id`，不信任 body 中的 userId。

---

## 1. 数据表（Flyway）

| 表 | 版本 | 用途 |
|----|------|------|
| `dp_friend_request`、`dp_friend_link`、`dp_room_invite` | V1 | 好友申请、好友关系、进房邀请 |
| `dp_friend_message`、`dp_friend_chat_read` | V2 | 好友私信、已读游标 |
| `dp_room_chat_message` | V2；主键 **V3** `(room_id, id)` | 摘房后局内聊天批量落库 |

私信容量：每无序好友对双向合计最多 **500** 条，超限删最旧 `id`。

---

## 2. REST — 好友与邮箱（`DpFriendMailboxController`，`/dp`）

| 方法 | 路径 | 服务要点 |
|------|------|----------|
| POST | `/friends/requests` | `sendFriendRequest` |
| GET | `/friends/requests/pending` |  inbound PENDING |
| POST | `/friends/requests/{id}/accept` \| `/reject` | |
| GET | `/friends` | 列表 + `presence`（`OFFLINE`/`IDLE`/`IN_GAME`） |
| DELETE | `/friends/{peerUserId}` | `removeFriend` |
| POST | `/room-invites` | 房主邀请；60s 有效；重复同房替换 PENDING |
| GET | `/mailbox` | 待处理申请 + 未过期邀请 |
| GET | `/mailbox/unread-count` | 红点计数 |
| POST | `/room-invites/{id}/accept` | 观众进房 `joinRoomInviteAsSpectator` |
| POST | `/room-invites/{id}/reject` | |
| POST | `/friends/follow-room` | 跟随好友所在房 |
| GET | `/friends/{friendUserId}/spectate-room` | 观战上下文 |
| POST | `/room-follow/spectate` | 跟随进房为观众 |
| GET | `/presence/site-heartbeat/config` | **permitAll** |
| POST | `/presence/site-heartbeat` | JWT；TTL `mgdemoplus.dp-site-presence-ttl-ms`（默认 90s） |

**房主邀请**：默认仅房主可 `POST /room-invites`。  
**超员**：观众进房不因满座拦截（产品约定由前端提示）。

---

## 3. REST — 好友私信

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/friends/{peerUserId}/messages` | body `{"body":"..."}`；非好友拒绝；约 **1200ms** 频控 |
| GET | `/friends/{peerUserId}/messages?beforeId=&limit=` | 分页，默认 limit 50，上限 100 |
| POST | `/friends/{peerUserId}/messages/read` | `{"lastReadMessageId":42}` |
| GET | `/friends/chat-unread-summary` | `totalUnread` + `perFriend` |

实现：`social` 包 `DpFriendChatService`；写成功后 `SocialNotifyPublisher` 通知**接收方**。

---

## 4. REST — 局内聊天近期（`DpRoomController`）

`GET /dpRoom/{roomId}/chat/recent?limit=50` — JWT；须在房内（与 WS `chatSend` 同口径 `isNicknameInRoom`）。  
数据源：内存 `RoomChatBuffer`（非 DB）；房间不在 `roomMap` 返回 404。

---

## 5. 局内聊天：内存 + WebSocket

- **`RoomChatBuffer`**：`roomId →` 最多 **500** 条；单调 `id`。  
- **`DpGameRoomPushService#handleClientTextMessage`**：`_ws:"chatSend"` → 校验昵称在房 → `append` → 广播 `_ws:"chat"`（`ttlMs` 约 15000）。  
- **摘房**：`DpRoomServiceImpl#finalizeHallAfterRoomRemoved` → `DpRoomChatPersistenceService#flushRoomToDatabase` → `INSERT dp_room_chat_message`。  

协议详见 [WEBSOCKET.md](./WEBSOCKET.md) §6。

---

## 6. 大厅 SSE（`DpSocialController`）

| 方法 | 路径 | 鉴权 |
|------|------|------|
| GET | `/dp/social/stream` | JWT：`Authorization` 或 **`?token=`**（仅本路径；`EventSource` 无法带头） |
| GET | `/dp/social/notify-summary` | Bearer |

**不在** `JwtSecurityConstants.PERMIT_ALL`（身份由过滤器写上下文）。

### 事件 `notify`

```json
{
  "mailboxUnread": 2,
  "friendChatUnreadTotal": 3,
  "friendChatUnreadByFriendUserId": { "123": 1 }
}
```

- `SocialSseHub`：每用户最多 **3** 连接；**30s** 注释心跳；**单 JVM** 扇出（多副本需粘滞或 Redis Pub/Sub，未实现）。  
- Nginx：`proxy_buffering off`（[NGINX.md](./NGINX.md)）。  
- `spring.mvc.async.request-timeout: -1` 避免 SSE 被掐断。

### 推送触发（写成功后）

| 操作 | 通知对象 |
|------|----------|
| 私信 `sendMessage` | 接收方 |
| `markRead` | 读方（未读下降） |
| 好友申请/接受/拒绝 | 相关用户 |
| 进房邀请/接受/拒绝/过期 | 邀请方或被邀请方 |
| `removeFriend` | 双方 |

前端：`api.dpSocial.js`、`dpSocialStream.js`、`Vuex dpMailbox`。

---

## 7. 实现类索引

| 类 | 职责 |
|----|------|
| `DpFriendSocialService` | 好友、邀请、mailbox、跟随 |
| `DpFriendChatService` | 私信 CRUD、未读 |
| `SocialSseHub` / `SocialNotifyPublisher` | SSE |
| `RoomChatBuffer` / `DpRoomChatPersistenceService` | 局内聊天 |
| `DpGameRoomPushService` | WS 聊天 |

Mapper：`src/main/resources/mapper/dp/DpFriendSocialMapper.xml` 等。

---

## 8. 交叉引用

- 对局/进退房：[DPGAME.md](./DPGAME.md)  
- JWT / 白名单：[JWT.md](./JWT.md)  
- 房间副作用（摘房 flush）：[refactor/room-mutation-side-effects.md](./refactor/room-mutation-side-effects.md)
