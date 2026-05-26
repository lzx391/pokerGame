# JWT 鉴权与白名单

> **核对日期**：2026-05-25  
> **权威来源**：`SecurityConfig`、`JwtAuthenticationFilter`、`JwtTokenService`、`JwtSecurityConstants`  
> **Status**: maintained

---

## 1. 职责划分

| 类 | 包路径 | 作用 |
|----|--------|------|
| `JwtTokenService` | `security/JwtTokenService.java` | 签发/解析 JWT；`subject` = 登录昵称 |
| `JwtAuthenticationFilter` | `security/JwtAuthenticationFilter.java` | 从 `Authorization: Bearer` 或 SSE 的 `?token=` 写入 `SecurityContextHolder` |
| `JwtAuthenticationEntryPoint` | `security/JwtAuthenticationEntryPoint.java` | 未认证 → HTTP 401 + JSON `success=false` |
| `SecurityConfig` | `config/SecurityConfig.java` | `STATELESS`、无 CSRF、白名单 + 其余 `authenticated()` |
| `JwtSecurityConstants` | `security/JwtSecurityConstants.java` | `PERMIT_ALL` 路径数组 |

密钥与过期：`mgdemoplus.jwt.secret`（环境变量 `JWT_SECRET`）、`mgdemoplus.jwt.expiration.time`（默认约 3 天）。配置在 **`application.yml`**，无 `application.properties` 主配置。

---

## 2. 业务侧取当前用户

```java
SecurityContextHolder.getContext().getAuthentication().getName(); // 昵称
```

`DpRoomController` 的 `joinRoom2` / `quickMatch2` 等会校验 **JWT subject 与请求参数 `nickname` 一致**。口令校验仅在登录时一次，见 [DpUserPassword.md](./DpUserPassword.md)（**bcrypt**，非 MD5）。

---

## 3. `PERMIT_ALL`（与对局/实时/社交相关）

源码：`JwtSecurityConstants.PERMIT_ALL`。

| 路径 | 说明 |
|------|------|
| `/dpUser/loginProfile`、`/dpUser/registerUser` | 登录/注册 |
| `/ws/**` | WebSocket 握手不强制 JWT；**快匹**在 `DpQuickMatchWebSocketHandler` 内校验 `token`+`nickname` |
| `/dpRoom/getNowRoom`、`/dpRoom/getAllRooms2` | 房间快照/内存 id 列表（旁观、分享链接） |
| `/dp/presence/site-heartbeat/config` | 站点心跳公开参数 |
| `/dpMusic/list` | 曲库列表 |
| `/dp/leaderboard/weekly/hand`、`/dp/leaderboard/weekly/room` | 周榜只读（Flyway **V7**） |
| `/images/**`、`/music/**`、静态 `index.html` 等 | 资源 |

**需 JWT**（节选）：其余 `/dpRoom/**`（含 `publicRooms`）、`/dp/**` 好友与邮箱、`/dp/social/notify-summary`。  
**SSE**：`GET /dp/social/stream` **不在** `permitAll`；过滤器对 **该路径** 支持 `?token=` 写入上下文（与 Bearer 二选一）。详见 [dp_friend_mailbox_mvp.md](./dp_friend_mailbox_mvp.md)。

---

## 4. 登录后会话（Redis JTI）

`DpUserServiceImpl#loginProfile` 签发 JWT 后，`DpRedisLoginCacheService` 按用户存 **JTI**；新登录覆盖旧 JTI，旧 token 校验失败（单会话踢旧）。与房间 `roomMap` 无关。

---

## 5. 前端（`front/dp_game`）

`src/main.js` axios：

- **请求**：除 `loginProfile`、`registerUser` 外，从 `localStorage.userInfo.token` 附加 `Authorization: Bearer`。
- **响应 401**：提示、`localStorage` 清除、`router.replace('/login')`。

SSE：`dpSocialStream.js` 使用 `GET /dp/social/stream?token=...`。

---

## 6. 交叉引用

| 主题 | 文档 |
|------|------|
| 口令 bcrypt | [DpUserPassword.md](./DpUserPassword.md) |
| 对局 WS | [WEBSOCKET.md](./WEBSOCKET.md) |
| 环境变量 | [ENV_README.md](./ENV_README.md) |
| Nginx SSE | [NGINX.md](./NGINX.md) |
