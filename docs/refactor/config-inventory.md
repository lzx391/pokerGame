# 配置全量参数表（inventory）

> 扫描基准：2026-05-24 · 来源：`application.yml`、Java `@Value` / `@ConfigurationProperties`、compose、注释块。  
> 分层定义见 [`../CONFIG_LAYERS.md`](../CONFIG_LAYERS.md)。

**图例**：层 = A 密钥 / B 运维可调 / C 部署路径 / D 产品框架默认 · **Rebuild** = Docker 生产改该项是否需 rebuild 镜像

---

## 1. `application.yml` 全键

### 1.1 `spring.*`

| yml 路径 | 层 | 当前值 / 占位 | 标准环境变量 | Rebuild | 备注 |
|----------|-----|---------------|--------------|---------|------|
| `spring.application.name` | D | `MGDemoPlus` | — | 是 | |
| `spring.flyway.locations` | D | `classpath:db/migration` | — | 是 | |
| `spring.flyway.baseline-on-migrate` | D | `true` | — | 是 | 已有表无 flyway 历史时 baseline |
| `spring.autoconfigure.exclude` | D | `UserDetailsServiceAutoConfiguration` | — | 是 | |
| `spring.datasource.type` | D | `DruidDataSource` | — | 是 | |
| `spring.datasource.driver-class-name` | D | `com.mysql.cj.jdbc.Driver` | — | 是 | |
| `spring.datasource.url` | C | `${SPRING_DATASOURCE_URL:localhost:3307/...}` | `SPRING_DATASOURCE_URL` | 否* | *env 覆盖不需 rebuild |
| `spring.datasource.username` | A/C | `${SPRING_DATASOURCE_USERNAME:root}` | `SPRING_DATASOURCE_USERNAME` | 否* | |
| `spring.datasource.password` | A | `${SPRING_DATASOURCE_PASSWORD:mgdemo_root}` | `SPRING_DATASOURCE_PASSWORD` | 否* | |
| `spring.datasource.druid.enable` | D | `true` | — | 是 | |
| `spring.devtools.restart.additional-paths` | D | `src/main/java` | — | 是 | 开发热重启 |
| `spring.servlet.multipart.max-file-size` | D | `80MB` | — | 是 | 头像/曲库上传 |
| `spring.servlet.multipart.max-request-size` | D | `80MB` | — | 是 | |
| `spring.data.redis.host` | C | `${SPRING_DATA_REDIS_HOST:127.0.0.1}` | `SPRING_DATA_REDIS_HOST` | 否* | |
| `spring.data.redis.port` | C | `${SPRING_DATA_REDIS_PORT:6380}` | `SPRING_DATA_REDIS_PORT` | 否* | 容器内应为 6379 |
| `spring.data.redis.password` | A | `${SPRING_DATA_REDIS_PASSWORD:mgdemo_redis}` | `SPRING_DATA_REDIS_PASSWORD` | 否* | |
| `spring.data.redis.database` | D | `0` | — | 是 | |
| `spring.data.redis.timeout` | D | `3000ms` | — | 是 | |
| `spring.mvc.async.request-timeout` | D | `-1` | — | 是 | SSE `/dp/social/stream` |

### 1.2 `mgdemoplus.*`

| yml 路径 | 层 | 当前值 / 占位 | 标准环境变量 | Rebuild | 消费方 |
|----------|-----|---------------|--------------|---------|--------|
| `mgdemoplus.time-zone` | C | `${MGDEMOPLUS_TIME_ZONE:Asia/Shanghai}` | `MGDEMOPLUS_TIME_ZONE` | 否* | `AppTimeZoneConfig`, 排行榜 |
| `mgdemoplus.jwt.secret` | A | `${JWT_SECRET:abcdefgh...}` | `JWT_SECRET` | 否* | `JwtTokenService` |
| `mgdemoplus.jwt.expiration.time` | D | `259200000` | — | 是 | `JwtTokenService`（≈3天） |
| `mgdemoplus.cache.music-list-ttl-seconds` | B | `${MGDEMOPLUS_CACHE_MUSIC_LIST_TTL_SECONDS:300}` | 同名 | 否* | `DpRedisListCacheServiceImpl` |
| `mgdemoplus.cache.dp-room-public-rooms-ttl-seconds` | B | **yml 缺失** | `MGDEMOPLUS_CACHE_DP_ROOM_PUBLIC_ROOMS_TTL_SECONDS` | 否* | `DpRoomHallServiceImpl` — **建议补 yml** |
| `mgdemoplus.dp-lobby-reconcile-enabled` | B | `true`（字面量） | `MGDEMOPLUS_DP_LOBBY_RECONCILE_ENABLED` | 改 yml 需 rebuild | `DpRoomLobbyReconcileScheduler` |
| `mgdemoplus.dp-lobby-reconcile-ms` | B | `60000` | `MGDEMOPLUS_DP_LOBBY_RECONCILE_MS` | 改 yml 需 rebuild | 同上 |
| `mgdemoplus.dp-quick-match-prune-ms` | B | `30000` | `MGDEMOPLUS_DP_QUICK_MATCH_PRUNE_MS` | 改 yml 需 rebuild | `DpRoomServiceImpl` |
| `mgdemoplus.dp-site-presence-ttl-ms` | B | `${MGDEMOPLUS_DP_SITE_PRESENCE_TTL_MS:90000}` | 同名 | 否* | `DpSitePresenceService` |
| `mgdemoplus.images.file-location` | C | `${MGDEMOPLUS_IMAGES_FILE_LOCATION:file:P:/...}` | 同名 | 否* | `WebConfig`, `UploadController` |
| `mgdemoplus.music.file-location` | C | `${MGDEMOPLUS_MUSIC_FILE_LOCATION:file:P:/.../music/}` | 同名 | 否* | `WebConfig`, `DpMusicController` |
| `mgdemoplus.leaderboard.sync-interval-ms` | B | `60000` | `MGDEMOPLUS_LEADERBOARD_SYNC_INTERVAL_MS` | 改 yml 需 rebuild | `DpLeaderboardWeeklyRedisSyncScheduler` |
| `mgdemoplus.leaderboard.sync-enabled` | B | `true` | `MGDEMOPLUS_LEADERBOARD_SYNC_ENABLED` | 改 yml 需 rebuild | 同上 |
| `mgdemoplus.leaderboard.redis-ttl-days` | B | `14` | `MGDEMOPLUS_LEADERBOARD_REDIS_TTL_DAYS` | 改 yml 需 rebuild | `DpLeaderboardRedisRepository` |
| `mgdemoplus.settle-persist.async-enabled` | B | `${MGDEMOPLUS_SETTLE_PERSIST_ASYNC_ENABLED:true}` | 同名 | 否* | `DpSettlePersistenceDispatcher` |
| `mgdemoplus.settle-persist.queue-capacity` | B | `${MGDEMOPLUS_SETTLE_PERSIST_QUEUE_CAPACITY:256}` | 同名 | 否* | `DpSettlePersistenceExecutorConfig` |

### 1.3 `mybatis-plus.*` / `pagehelper.*` / `server.*`

| yml 路径 | 层 | 当前值 | 标准环境变量 | Rebuild | 备注 |
|----------|-----|--------|--------------|---------|------|
| `mybatis-plus.mapper-locations` | D | `classpath*:mapper/**/*.xml` | — | 是 | |
| `mybatis-plus.configuration.log-impl` | D | `Slf4jImpl` | — | 是 | |
| `pagehelper.helper-dialect` | D | `mysql` | — | 是 | |
| `pagehelper.reasonable` | D | `true` | — | 是 | |
| `pagehelper.support-methods-arguments` | D | `true` | — | 是 | |
| `server.port` | D | `8088` | `SERVER_PORT` | 是 | Spring 也认 `SERVER_PORT` env |

### 1.4 `dp.npc.table-talk.*`（`DpNpcTableTalkProperties`）

| yml 路径 | 层 | 当前值 | 标准环境变量 | Java 字段默认 | Rebuild | 备注 |
|----------|-----|--------|--------------|---------------|---------|------|
| `dp.npc.table-talk.enabled` | B | `true` | `DP_NPC_TABLE_TALK_ENABLED` | `true` | 改 yml 需 rebuild | 总开关 |
| `dp.npc.table-talk.speak-probability` | B | 注释掉 | `DP_NPC_TABLE_TALK_SPEAK_PROBABILITY` | null | — | 覆盖 npc-lines yaml |
| `dp.npc.table-talk.min-interval-ms` | B | `2500` | `DP_NPC_TABLE_TALK_MIN_INTERVAL_MS` | **8000** | 改 yml 需 rebuild | yml 覆盖 Java 默认 |
| `dp.npc.table-talk.settle-speak-probability` | B | **yml 缺失** | `DP_NPC_TABLE_TALK_SETTLE_SPEAK_PROBABILITY` | null | — | 仅 Java 字段 |

### 1.5 `dp.npc.rule-think.*`（`DpNpcRuleThinkProperties`）

| yml 路径 | 层 | 当前值 | 标准环境变量 | Rebuild | 备注 |
|----------|-----|--------|--------------|---------|------|
| `dp.npc.rule-think.enabled` | B | `true` | `DP_NPC_RULE_THINK_ENABLED` | 改 yml 需 rebuild | |
| `dp.npc.rule-think.snap-probability` | B | `0.18` | `DP_NPC_RULE_THINK_SNAP_PROBABILITY` | 改 yml 需 rebuild | 秒出概率 |
| `dp.npc.rule-think.max-ms` | B | `4000` | `DP_NPC_RULE_THINK_MAX_MS` | 改 yml 需 rebuild | |
| `dp.npc.rule-think.fast-min-ms` | B | `500` | `DP_NPC_RULE_THINK_FAST_MIN_MS` | 改 yml 需 rebuild | |
| `dp.npc.rule-think.fast-max-ms` | B | `2000` | `DP_NPC_RULE_THINK_FAST_MAX_MS` | 改 yml 需 rebuild | |
| `dp.npc.rule-think.fast-weight` | B | `0.70` | `DP_NPC_RULE_THINK_FAST_WEIGHT` | 改 yml 需 rebuild | |
| `dp.npc.rule-think.slow-min-ms` | B | `2000` | `DP_NPC_RULE_THINK_SLOW_MIN_MS` | 改 yml 需 rebuild | |
| `dp.npc.rule-think.slow-max-ms` | B | `4000` | `DP_NPC_RULE_THINK_SLOW_MAX_MS` | 改 yml 需 rebuild | |
| `dp.npc.rule-think.slow-weight` | B | `0.30` | `DP_NPC_RULE_THINK_SLOW_WEIGHT` | 改 yml 需 rebuild | |

### 1.6 `dp.llm.ark.*`（`DpLlmNpcDecisionService` @Value）

| yml 路径 | 层 | 当前值 | 标准环境变量 | Rebuild | 备注 |
|----------|-----|--------|--------------|---------|------|
| `dp.llm.ark.api-key` | A | `${ARK_API_KEY:}` | `ARK_API_KEY` | 否* | 空则 BOT_LLM 本地兜底 |
| `dp.llm.ark.endpoint-id` | A | `${ARK_ENDPOINT_ID:}` | `ARK_ENDPOINT_ID` | 否* | |
| `dp.llm.ark.base-url` | A | `${ARK_BASE_URL:}` | `ARK_BASE_URL` | 否* | |
| `dp.llm.ark.reasoning-effort` | B | `${ARK_REASONING_EFFORT:}` | `ARK_REASONING_EFFORT` | 否* | high/medium/low |
| `dp.llm.ark.thinking-type` | B | `${ARK_THINKING_TYPE:}` | `ARK_THINKING_TYPE` | 否* | disabled 时忽略 effort |
| `dp.llm.ark.response-json-object` | B | `${ARK_RESPONSE_JSON_OBJECT:true}` | `ARK_RESPONSE_JSON_OBJECT` | 否* | 400 时可 false |

### 1.7 注释块 `# logging:`（当前未生效）

| yml 路径 | 层 | 注释内默认 | 标准环境变量 | Rebuild | 备注 |
|----------|-----|------------|--------------|---------|------|
| `logging.charset.file` | D | `UTF-8` | — | 启用块需 rebuild | |
| `logging.file.name` | C | `${LOG_FILE:logs/mgdemoplus.log}` | `LOG_FILE` | 启用块需 rebuild | 日志文件路径 |
| `logging.logback.rollingpolicy.max-file-size` | D | `50MB` | — | 启用块需 rebuild | |
| `logging.logback.rollingpolicy.max-history` | D | `14` | — | 启用块需 rebuild | |
| `logging.logback.rollingpolicy.total-size-cap` | D | `1GB` | — | 启用块需 rebuild | |
| `logging.level.root` | D | `WARN` | — | 启用块需 rebuild | |
| `logging.level.com.example.mgdemoplus.BotLlmReasoning` | D | `INFO` | — | 启用块需 rebuild | |
| `logging.level.com.example.mgdemoplus.service.serviceImpl.DpNpcEngine` | D | `INFO` | — | 启用块需 rebuild | **包名已过时**，应为 `npc.engine.DpNpcEngine` |
| `logging.level.com.example.mgdemoplus.service.serviceImpl.DpLlmNpcDecisionService` | D | `DEBUG` | — | 启用块需 rebuild | 应为 `npc.llm.DpLlmNpcDecisionService` |
| `logging.level.com.example.mgdemoplus.social` | D | `INFO` | — | 启用块需 rebuild | |
| `logging.level.com.example.mgdemoplus.controller.DpSocialController` | D | `INFO` | — | 启用块需 rebuild | |
| `logging.level.com.example.mgdemoplus.controller.DpFriendMailboxController` | D | `INFO` | — | 启用块需 rebuild | |
| `logging.level.com.example.mgdemoplus.security.JwtAuthenticationFilter` | D | `INFO` | — | 启用块需 rebuild | |
| `logging.level.com.example.mgdemoplus.social.impl.DpFriendSocialService` | D | `INFO` | — | 启用块需 rebuild | |
| `logging.level.org.springframework` | D | `WARN` | — | 启用块需 rebuild | |
| `logging.level.com.alibaba.druid` | D | `WARN` | — | 启用块需 rebuild | |

---

## 2. Java `@Value` 汇总（含 yml 未声明项）

| 属性键 | 层 | 代码默认 | yml | 类 |
|--------|-----|----------|-----|-----|
| `mgdemoplus.jwt.secret` | A | — | 有 | `JwtTokenService` |
| `mgdemoplus.jwt.expiration.time` | D | — | 有 | `JwtTokenService` |
| `mgdemoplus.time-zone` | C | `Asia/Shanghai` | 有 | `AppTimeZoneConfig`, 排行榜 *Service |
| `mgdemoplus.images.file-location` | C | `file:P:/...` | 有 | `WebConfig`, `UploadController` |
| `mgdemoplus.music.file-location` | C | `file:P:/.../music/` | 有 | `WebConfig`, `DpMusicController` |
| `mgdemoplus.cache.music-list-ttl-seconds` | B | `300` | 有 | `DpRedisListCacheServiceImpl` |
| `mgdemoplus.cache.dp-room-public-rooms-ttl-seconds` | B | `120` | **无** | `DpRoomHallServiceImpl` |
| `mgdemoplus.dp-site-presence-ttl-ms` | B | `90000` | 有 | `DpSitePresenceService` |
| `mgdemoplus.settle-persist.async-enabled` | B | `true` | 有 | `DpSettlePersistenceDispatcher` |
| `mgdemoplus.settle-persist.queue-capacity` | B | `256` | 有 | `DpSettlePersistenceExecutorConfig` |
| `mgdemoplus.leaderboard.redis-ttl-days` | B | `14` | 有 | `DpLeaderboardRedisRepository` |
| `dp.llm.ark.api-key` | A | 空 | 有 | `DpLlmNpcDecisionService` |
| `dp.llm.ark.endpoint-id` | A | 空 | 有 | 同上 |
| `dp.llm.ark.base-url` | A | 空 | 有 | 同上 |
| `dp.llm.ark.reasoning-effort` | B | 空 | 有 | 同上 |
| `dp.llm.ark.thinking-type` | B | 空 | 有 | 同上 |
| `dp.llm.ark.response-json-object` | B | `true` | 有 | 同上 |

**`@Scheduled` / `@ConditionalOnProperty`（无 @Value，读 yml 键）**：

| 属性键 | 层 | 默认 | 类 |
|--------|-----|------|-----|
| `mgdemoplus.dp-lobby-reconcile-enabled` | B | true | `DpRoomLobbyReconcileScheduler` |
| `mgdemoplus.dp-lobby-reconcile-ms` | B | 60000 | 同上 |
| `mgdemoplus.dp-quick-match-prune-ms` | B | 30000 | `DpRoomServiceImpl` |
| `mgdemoplus.leaderboard.sync-enabled` | B | true | `DpLeaderboardWeeklyRedisSyncScheduler` |
| `mgdemoplus.leaderboard.sync-interval-ms` | B | 60000 | 同上 |

---

## 3. `@ConfigurationProperties` 全字段

### 3.1 `DpNpcTableTalkProperties` — prefix `dp.npc.table-talk`

| 字段 | 类型 | Java 默认 | yml | 标准环境变量 |
|------|------|-----------|-----|--------------|
| `enabled` | boolean | true | `enabled: true` | `DP_NPC_TABLE_TALK_ENABLED` |
| `speakProbability` | Double | null | 注释 | `DP_NPC_TABLE_TALK_SPEAK_PROBABILITY` |
| `settleSpeakProbability` | Double | null | 无 | `DP_NPC_TABLE_TALK_SETTLE_SPEAK_PROBABILITY` |
| `minIntervalMs` | long | 8000 | `2500` | `DP_NPC_TABLE_TALK_MIN_INTERVAL_MS` |

配置类：`src/main/java/com/example/mgdemoplus/npc/tabletalk/DpNpcTableTalkConfig.java`

### 3.2 `DpNpcRuleThinkProperties` — prefix `dp.npc.rule-think`

| 字段 | 类型 | Java 默认 | yml | 标准环境变量 |
|------|------|-----------|-----|--------------|
| `enabled` | boolean | true | `true` | `DP_NPC_RULE_THINK_ENABLED` |
| `snapProbability` | double | 0.18 | `0.18` | `DP_NPC_RULE_THINK_SNAP_PROBABILITY` |
| `maxMs` | int | 4000 | `4000` | `DP_NPC_RULE_THINK_MAX_MS` |
| `fastMinMs` | int | 500 | `500` | `DP_NPC_RULE_THINK_FAST_MIN_MS` |
| `fastMaxMs` | int | 2000 | `2000` | `DP_NPC_RULE_THINK_FAST_MAX_MS` |
| `fastWeight` | double | 0.70 | `0.70` | `DP_NPC_RULE_THINK_FAST_WEIGHT` |
| `slowMinMs` | int | 2000 | `2000` | `DP_NPC_RULE_THINK_SLOW_MIN_MS` |
| `slowMaxMs` | int | 4000 | `4000` | `DP_NPC_RULE_THINK_SLOW_MAX_MS` |
| `slowWeight` | double | 0.30 | `0.30` | `DP_NPC_RULE_THINK_SLOW_WEIGHT` |

配置类：`src/main/java/com/example/mgdemoplus/npc/rulethink/DpNpcRuleThinkConfig.java`

---

## 4. Docker Compose 环境变量（app 服务）

| 变量 | 层 | docker-compose.yml | docker-compose-prod.yml | 对应 Spring 键 |
|------|-----|--------------------|-------------------------|----------------|
| `JAVA_OPTS` | C | `-Xms256m -Xmx800m` | 同左 | entrypoint，非 Spring |
| `TZ` | C | `Asia/Shanghai` | 同左 | 与 `MGDEMOPLUS_TIME_ZONE` 对齐 |
| `SPRING_DATASOURCE_URL` | C | `mysql:3306/school_db...` | 同左 | `spring.datasource.url` |
| `SPRING_DATASOURCE_USERNAME` | A/C | `root` | 同左 | |
| `SPRING_DATASOURCE_PASSWORD` | A | `mgdemo_root` | 同左 | |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | C | `com.mysql.cj.jdbc.Driver` | 同左 | yml 已有 driver，冗余安全 |
| `MGDEMOPLUS_IMAGES_FILE_LOCATION` | C | `file:/data/mgdemo-files/` | 同左 | |
| `MGDEMOPLUS_MUSIC_FILE_LOCATION` | C | `file:/data/mgdemo-files/music/` | 同左 | |
| `MGDEMOPLUS_TIME_ZONE` | C | `Asia/Shanghai` | 同左 | |
| `MGDEMOPLUS_CACHE_MUSIC_LIST_TTL_SECONDS` | B | `"300"` | 同左 | |
| `SPRING_DATA_REDIS_HOST` | C | `redis` | 同左 | |
| `SPRING_DATA_REDIS_PORT` | C | `"6379"` | 同左 | |
| `SPRING_DATA_REDIS_PASSWORD` | A | `mgdemo_redis` | 同左 | |
| `ARK_*`（6 项） | A/B | `${...:-}` 代入 | 同左 | `dp.llm.ark.*` |
| `JWT_SECRET` | A | **未注入** | **未注入** | 建议 prod 追加 |

### 4.1 基础设施服务（非 Spring app）

| 服务 | 变量 / 配置 | 层 | 当前值 |
|------|-------------|-----|--------|
| mysql | `MYSQL_ROOT_PASSWORD` | A | `mgdemo_root` |
| mysql | `MYSQL_DATABASE` | D | `school_db` |
| redis | `requirepass` in command | A | `mgdemo_redis` |
| grafana | `GF_SECURITY_ADMIN_USER` | D | `admin` |
| grafana | `GF_SECURITY_ADMIN_PASSWORD` | A | `admin` |

### 4.2 卷（C）

| 卷名 | 挂载点 | 用途 |
|------|--------|------|
| `mgdemo_uploads` | app `/data/mgdemo-files` | 头像 + 曲库 |
| `mysql_data` | mysql 数据目录 | |
| `redis_data` | redis AOF | |

---

## 5. `.env.example` 现状 vs 目标

| 键 | 现状 | 目标（Agent-2） |
|----|------|-----------------|
| `ARK_API_KEY` | 空 | A，保留 |
| `ARK_ENDPOINT_ID` | 空 | A，保留 |
| `ARK_BASE_URL` | 空 | A，保留 |
| `ARK_REASONING_EFFORT` | 空 | B，保留 |
| `ARK_THINKING_TYPE` | 空 | B，保留 |
| `JWT_SECRET` | 无 | A，新增空行 |
| `SPRING_DATASOURCE_PASSWORD` | 无 | A，可选示例 |
| `SPRING_DATA_REDIS_PASSWORD` | 无 | A，可选示例 |
| B/C 运维项 | 无 | 注释示例块 |

---

## 6. Agent-2 待办（由 inventory 推导）

1. yml：B 项字面量改为 `${ENV:default}`（reconcile、leaderboard、quick-match、npc.*）。
2. yml：**补** `mgdemoplus.cache.dp-room-public-rooms-ttl-seconds`。
3. `.env.example`：扩展 A/B 分组（见 CONFIG_LAYERS §2.2）。
4. compose：prod/dev 增加注释分组；prod 可选 `JWT_SECRET: ${JWT_SECRET:-}`。
5. 启用 `logging` 块时：修正 Logger 包名为 `npc.engine` / `npc.llm`。
6. 文档：`docs/ENV_README.md` 指向 `CONFIG_LAYERS.md`，application.properties → application.yml。

---

## 7. 统计

| 类别 | 数量 |
|------|------|
| application.yml 有效键（不含注释 logging 子键） | 52 |
| 注释 logging 子键 | 16 |
| @Value 注入点 | 17（含重复键多类） |
| @ConfigurationProperties 字段 | 13 |
| yml 缺失但代码使用 | 2（public-rooms TTL、settle-speak-probability） |
| compose app env 键 | 18 |
