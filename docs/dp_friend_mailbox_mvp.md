# DP 好友 / 邮箱邀请（REST MVP）

**鉴权**：全部接口需 JWT；当前用户仅从 token `subject`（昵称）映射 `dp_user.id`，不信任请求体的 userId。

**已知限制（本轮刻意不做）**：

- **超员**：观众进房不因座位满而拦截；与前端约定见人满时仍可进观众席或由错误提示收口（当前实现：**始终写入观众**，未开局仅占观众列表）。
- **反骚扰**：无频率限制、黑名单等。
- **仅大厅可见邮箱**：服务端不校验调用场景，仅用前端约束展示。

**房主进房邀约**：默认仅**房主**可 `POST /dp/room-invites`；座位上的非房主好友本轮不可代发邀约。

---

## 表结构

表结构参见 `src/main/resources/db/migration/V1__init_schema.sql`（Flyway；应用启动时自动执行）。

---

## 接口列表

JSON 时间与工程一致：**LocalDateTime 字符串**，无后缀时客户端按服务端时区理解即可。

### 1. POST `/dp/friends/requests`

发好友申请（方向：`from`=当前登录，`to`=body）。

请求：

```json
{ "toUserId": 2 }
```

响应示例：

```json
{
  "success": true,
  "message": "成功",
  "data": {
    "message": "已发送申请"
  }
}
```

---

### 2. GET `/dp/friends/requests/pending`

我收到的待处理好友申请。

响应示例：

```json
{
  "success": true,
  "message": "成功",
  "data": {
    "items": [
      {
        "id": 1,
        "fromUserId": 2,
        "toUserId": 3,
        "fromNickname": "alice",
        "status": "PENDING",
        "createdAt": "2026-05-11T12:00:00",
        "kind": "FRIEND_REQUEST"
      }
    ]
  }
}
```

---

### 3. POST `/dp/friends/requests/{id}/accept`

同意好友申请（仅 `to_user` 可操作）。

响应示例：`data.message` = `已同意并成为好友`（重复同意返回说明性成功/失败语义见实现）。

---

### 4. POST `/dp/friends/requests/{id}/reject`

拒绝好友申请。

---

### 5. GET `/dp/friends`

好友列表：`userId` + `nickname`（关联 `dp_user`）。

```json
{
  "success": true,
  "data": {
    "friends": [
      { "userId": 2, "nickname": "alice" }
    ]
  }
}
```

---

### 6. POST `/dp/room-invites`

房主向好友发起进房邀请（60 秒内有效；重复同三房会替换上一条 **PENDING**）。

请求：

```json
{
  "roomId": "a1b2c3d4",
  "inviteeUserId": 2
}
```

响应示例：

```json
{
  "success": true,
  "data": {
    "inviteId": 10,
    "roomId": "a1b2c3d4",
    "expiresAt": "2026-05-11T12:01:01.123456"
  }
}
```

---

### 7. GET `/dp/mailbox`

合并：**待处理好友申请** + **未过期进房邀约**。

```json
{
  "success": true,
  "data": {
    "friendRequests": [ /* 同 pending 条目 */ ],
    "roomInvites": [
      {
        "id": 10,
        "roomId": "a1b2c3d4",
        "inviterUserId": 1,
        "inviterNickname": "bob",
        "inviteeUserId": 2,
        "status": "PENDING",
        "createdAt": "...",
        "expiresAt": "...",
        "remainingSeconds": 45,
        "kind": "ROOM_INVITE"
      }
    ]
  }
}
```

进入列表前会对全局 `expires_at <= NOW(3)` 的 **PENDING** 邀约批量标为 **EXPIRED**。

---

### 8. GET `/dp/mailbox/unread-count`

红点：待处理好友申请数 + 未过期邀约数之和。

```json
{ "success": true, "data": { "count": 3 } }
```

---

### 9. POST `/dp/room-invites/{id}/accept`

受邀人同意；**不写密码**，以观众进房（`joinRoomInviteAsSpectator`）。幂等：`ACCEPTED` 不报错。

无需 body。

---

### 10. POST `/dp/room-invites/{id}/reject`

受邀人拒绝进房邀约。幂等对非 `PENDING`。

---

## MyBatis

`src/main/resources/mapper/dp/DpFriendSocialMapper.xml`，`application.yml` 已配置：

```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
```
