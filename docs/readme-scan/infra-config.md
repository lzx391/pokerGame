> 扫描日期：2026-05-22 · 范围：AI/infra/DB · 覆盖重写

# 基础设施 / 配置 / 安全 / 数据迁移

## 技术栈与构建（`pom.xml`）

| 项 | 版本 / 说明 |
|----|-------------|
| Java | **17**（`java.version`） |
| Spring Boot | **3.5.11**（parent） |
| Flyway | **11.7.2**（`flyway-core` + `flyway-mysql`，与 Boot BOM 对齐） |
| MyBatis-Plus | **3.5.8**（`mybatis-plus-spring-boot3-starter`） |
| PageHelper | **2.1.0** |
| Druid | **1.2.27** |
| MySQL 驱动 | **8.0.33** |
| JJWT | **0.13.0** |
| dotenv-java | **3.0.0**（本机 `.env`） |
| sensitive-word | **0.19.0** |

**无 Spring profiles**：仅 `src/main/resources/application.yml`（无 `application-*.yml`）。

验证（2026-05-22）：`mvn -q compile -DskipTests` **PASS**；`mvn -q test` **PASS**。

---

## `application.yml` 结构摘要

| 块 | 要点 |
|----|------|
| `spring.application.name` | `MGDemoPlus` |
| `spring.flyway` | `classpath:db/migration`；`baseline-on-migrate: true` |
| `spring.datasource` | Druid；URL/账号/密码用 `${SPRING_DATASOURCE_*:默认值}` |
| `spring.data.redis` | `${SPRING_DATA_REDIS_*}`，默认 `127.0.0.1:6380`，密码 `mgdemo_redis` |
| `spring.mvc.async.request-timeout` | `-1`（SSE 长连接） |
| `mgdemoplus` | `time-zone`、`jwt.secret` / `jwt.expiration.time`（259200000 ms ≈ 3 天）、`cache.music-list-ttl-seconds`、大厅对齐/快匹/站点在线 TTL、图片与曲库目录 |
| `mybatis-plus` | `classpath*:mapper/**/*.xml`；SQL 日志 `Slf4jImpl` |
| `pagehelper` | `mysql` 方言 |
| `server.port` | **8088** |
| `dp.llm.ark` | 方舟 LLM（见 [ai-npc.md](ai-npc.md)） |

本机默认 JDBC：`jdbc:mysql://localhost:3307/school_db?...`（与 Compose 映射 **3307:3306** 一致）。

---

## 启动：`MgDemoPlusApplication` + `LocalDotenvLoader`

```text
main()
  └─ LocalDotenvLoader.load()     # dotenv-java；仅当 getenv(key) 为空时 setProperty
  └─ SpringApplication.run(...)
```

- **`@MapperScan`**（8 个包，无 `npc`/`room` mapper）：
  - `common.mapper`
  - `history.mapper`
  - `lobby.mapper`
  - `music.mapper`
  - `roomchat.mapper`
  - `social.mapper`
  - `user.mapper`
- **`@EnableScheduling`** — 大厅对齐、快匹清理等 `@Scheduled` Bean。

---

## `config/` 包

| 类 | 职责 |
|----|------|
| `SecurityConfig` | 无状态 JWT；`PERMIT_ALL` + 静态资源；`JwtAuthenticationFilter` 前置 |
| `WebConfig` | `/images/**`、`/music/**` 映射到 `mgdemoplus.images.file-location` / `music.file-location` |
| `WebSocketGameRoomConfig` | 原生 WebSocket 注册（对局房、快匹） |
| `MybatisPlusConfig` | 分页插件 `PaginationInnerInterceptor` |
| `CorsConfig` | 跨域 |
| `AppTimeZoneConfig` | JVM 默认时区 `mgdemoplus.time-zone`（默认 `Asia/Shanghai`） |
| `DpSensitiveWordConfig` | houbb `SensitiveWordBs` Bean（默认词表） |
| `LocalDotenvLoader` | 见上；Docker 通常不依赖磁盘 `.env` |

---

## `security/` 包

| 类 | 职责 |
|----|------|
| `JwtSecurityConstants` | `PERMIT_ALL` 白名单路径数组 |
| `JwtTokenService` | HMAC-SHA256 签发/校验；`subject`=昵称，`jti` 在 claim `id` |
| `JwtAuthenticationFilter` | Bearer / `?token=`；校验 Redis 中 jti；白名单可选带 token |
| `JwtAuthenticationEntryPoint` | 未认证 JSON 响应 |

### JWT 白名单（`PERMIT_ALL`）

- 登录注册：`/dpUser/loginProfile`、`/dpUser/registerUser`
- WebSocket：`/ws/**`
- 静态：`/images/**`、`/music/**`、`/`、`/index.html`、`/favicon.ico`、`/fonts/**`
- 大厅只读：`/dpRoom/getNowRoom`、`/dpRoom/getAllRooms2`
- 站点心跳配置：`/dp/presence/site-heartbeat/config`
- 曲库列表：`/dpMusic/list`
- 其余 `/dpRoom/**`、社交 SSE 等需认证（SSE 可在 filter 用 query token）

### Redis JTI 单会话

- 登录：`DpUserController` 生成 UUID `jti` → `JwtTokenService.generateToken` → `DpRedisLoginCacheService.setLoginJti(nickname, jti)`
- 实现：`user.cache.impl.DpRedisLoginCacheServiceImpl`
- Key：`mgdemo:cache:login:{nickname}`，TTL = `JwtTokenService.tokenTtlSeconds()`（与 JWT 过期一致）
- 过滤器：`JwtAuthenticationFilter` 校验 token 内 `jti` 与 Redis 一致，否则 401
- 踢下线：`removeLoginJti`

### 口令存储

- **bcrypt**：`DpUserServiceImpl` 注册/改密 `CryptoUtil.bcryptEncode`，登录 `bcryptMatches`
- `CryptoUtil.md5HexUtf8` 仍存在，**不用于**当前用户口令落库

---

## `music/` 包

| 路径 | 职责 |
|------|------|
| `DpMusicService` / `impl/DpMusicServiceImpl` | 曲库 CRUD、上架列表 |
| `entity/DpMusicTrack` | 表 `dp_music_track`（V1） |
| `mapper/DpMusicTrackMapper` | MyBatis |
| `cache/DpRedisListCacheService` | 列表 Redis 缓存；上传后 `evictMusicList` |

HTTP：`GET /dpMusic/list` 在白名单；音频文件由 `WebConfig` 暴露 `/music/**`。缓存 TTL：`mgdemoplus.cache.music-list-ttl-seconds`（默认 300）。

---

## `utils/` 包（全局）

| 类 | 用途 |
|----|------|
| `ResultUtil` / `ResultCode` | 统一 REST 响应 |
| `CryptoUtil` | bcrypt / MD5(兼容) / SHA / HMAC / Base64 / 随机 hex |
| `DpUtilHandEvaluator` | 牌力评估（room + npc） |
| `DpUtilSmartContext` | NPC 决策上下文 DTO |
| `DpUtilNpcDisplayNickname` | Bot 昵称 UI 短显 |

---

## Flyway 迁移（V1–V6）

脚本目录：`src/main/resources/db/migration/`。**已执行过的脚本不可改**；变更须新增 `V7__*.sql`。

| 版本 | 文件 | 一行摘要 |
|------|------|----------|
| **V1** | `V1__init_schema.sql` | 基线：`dp_user`、牌谱 `dp_observed_hand_*`、`dp_shark_opponent_profile`、`dp_music_track`、好友/邀约、`dp_room_lobby` 等 |
| **V2** | `V2__friend_dm_and_room_chat.sql` | 好友私信 `dp_friend_message`、已读游标、局内聊天 `dp_room_chat_message` |
| **V3** | `V3__room_chat_composite_pk.sql` | 局内聊天主键改为 `(room_id, id)`，避免多房 id 冲突 |
| **V4** | `V4__user_stats.sql` | 生涯荣誉表 `dp_user_stats`（与 `dp_user` 1:1） |
| **V5** | `V5__largest_room_net_multiplier.sql` | `largest_room_net` 由 BC 改为带入倍数 `DECIMAL(10,2)`，历史清零 |
| **V6** | `V6__largest_pot_won_multiplier.sql` | `largest_pot_won` 同上改为单局净赢倍数 |

数据库名默认：**`school_db`**。

---

## Docker Compose（后端相关事实）

文件：`docker-compose.yml`（本地常 `build: .`；线上可改 `image:`）。

| 服务 | 宿主端口 | 与应用相关的环境/卷 |
|------|----------|---------------------|
| **mysql** | 3307→3306 | `MYSQL_DATABASE=school_db`；root 密码 `mgdemo_root` |
| **redis** | 6380→6379 | `requirepass mgdemo_redis` |
| **app** | **8088→8088** | `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/school_db...`；`SPRING_DATA_REDIS_HOST=redis`；`MGDEMOPLUS_*_FILE_LOCATION`；`ARK_*` 五项；`TZ=Asia/Shanghai`；上传卷 `mgdemo_uploads` |

- 表结构由应用启动时 **Flyway** 应用，勿在 Compose 里手写 DDL。
- 宿主 `.env`：Compose 注入 `ARK_*`（模板 `.env.example`）；MySQL/Redis 口令多在 compose 默认值中。
- **不写** Nginx/SSL 细节：本地可注释 `nginx` 服务，直连 `app:8088`。

---

## README 建议修订点

| 项 | 代码事实 | 建议 |
|----|----------|------|
| 本机 DB/Redis 默认 | `application.yml` 默认密码 `mgdemo_root` / `mgdemo_redis`，MySQL **3307** | 勿写「本机 yml 默认 123456 / ruoyi123 / 3306」除非另备 dev 配置 |
| 密码算法 | bcrypt 落库 | 更新 `CLAUDE.md`；`docs/DpUserPassword.md` 包路径改为 `user.impl.DpUserServiceImpl` |
| Flyway 范围 | **V1–V6** | readme-scan 旧稿「仅 V1–V3」已过时 |
| 配置文件名 | 仅 **`application.yml`** | `docs/ENV_README.md` 可并列 yml |
| 包迁移 | 无 `src/.../service/serviceImpl` Java 目录 | 根 README 一句包览改为 `npc.*` / `room.impl` 等 |
| JWT 文档 | JTI 在 `user.cache`，非 `service.cache` | `docs/JWT.md` 若有旧类名需对齐 |
| `application.yml` 注释日志 | 仍引用 `service.serviceImpl.DpNpcEngine` | 改为 `npc.engine.DpNpcEngine` 等（注释块，可选） |
