# MGDemoPlus 后端面试题库（贴合本仓库技术栈）

本文档站在**面试官**角度，结合本项目的真实技术栈与实现方式整理问题，便于你准备 Java / Spring Boot 后端岗位面试。问题按主题分组，每题附带**可结合本项目的答法提示**（不要求背项目细节，但能把简历上的项目讲清楚会加分）。

**项目速览**：Spring Boot 3.5 + Java 17；Web + Security(JWT) + WebSocket；MySQL + MyBatis-Plus + Druid + PageHelper；Redis(Lettuce)；前端打包进 jar；Docker Compose 部署。DP 扑克对局核心状态在**单机内存** `ConcurrentHashMap`，与 WebSocket 推送同进程。

---

## 一、项目与架构

1. **请用 1～2 分钟介绍你负责或参与过的后端模块，技术难点是什么？**  
   - 提示：可拆成「HTTP 接口（用户、房间、牌谱）」「内存房间 + 定时驱动」「WebSocket 推送」「NPC/LLM 决策」「持久化与分页」。

2. **为什么选择把游戏房间状态放在内存而不是全程走数据库？优缺点是什么？**  
   - 提示：低延迟、高频状态变更；缺点是多实例无法共享、进程重启丢局、需自己保证并发安全。

3. **如果要把本系统水平扩展为多台应用服务器，你会改哪些地方？**  
   - 提示：`roomMap` 与 `DpGameRoomPushService` 的会话表都是进程内结构；需 Redis Pub/Sub 或消息队列广播房间事件、Sticky Session 或集中式会话注册；房间状态要么外置（Redis）要么_shard 到固定节点。

4. **单体 jar 里同时提供 REST、WebSocket、静态资源，部署上有什么利弊？**  
   - 提示：运维简单、同端口；前后端耦合发布；大流量时可拆静态到 CDN。

5. **README 里写 WebSocket「无 Redis」是什么意思？Redis 在仓库里还用在哪？**  
   - 提示：对局推送不依赖 Redis；`spring-boot-starter-data-redis` 用于通用缓存/实验接口 `RedisLabController`，与房间推送解耦。

---

## 二、Java 语言与并发

1. **`ConcurrentHashMap` 与 `Collections.synchronizedMap` 区别？本项目哪里用了 `ConcurrentHashMap`？**  
   - 提示：`DpRoomServiceImpl#roomMap`、`DpGameRoomPushService` 的 `roomSessions` / 去重缓存、`DpRoom` 内昵称映射等。

2. **为什么对 `WebSocketSession` 发送消息时可能要用 `synchronized(session)`？**  
   - 提示：规范上同一 Session 并发发送可能需要串行化；可结合 `DpGameRoomPushService` 中的同步块谈竞态与半包问题（若面试官追问底层）。

3. **`ConcurrentHashMap.newKeySet()` 和 `Collections.newSetFromMap` 使用场景？**  
   - 提示：`computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())` 维护每房间订阅者集合。

4. **浅拷贝 / 深拷贝在本项目里为什么值得注意？**  
   - 提示：见仓库 `docs/Java对象引用与浅拷贝深拷贝备忘.md`；房间、玩家对象若多处引用，修改一处可能影响别处。

5. **项目里用 `java.util.Timer` 做每秒任务，和 Spring `@Scheduled` 比各有什么优劣？**  
   - 提示：`DpRoomServiceImpl` 中单线程 `Timer` 与异常是否会导致调度终止；`@Scheduled` 线程池、可监控、与 Spring 生命周期一致。

6. **多线程下若同时修改同一 `DpRoom` 对象，可能出什么问题？如何缓解？**  
   - 提示：可见性、丢失更新；可谈单线程定时器串行推进、或对关键路径加锁/队列（结合你们实际是否全在定时器线程里改）。

---

## 三、Spring Boot 与 IoC

1. **Spring Boot 3 相对 2 的一大变化是什么？（Jakarta EE 命名空间）**  
   - 提示：`javax.*` → `jakarta.*`。

2. **`@Lazy` 注入在 `DpGameRoomPushService` 构造函数里解决什么问题？**  
   - 提示：与 `DpRoomServiceImpl` 的循环依赖：推送服务依赖房间服务，房间服务可能依赖推送，延迟初始化一方。

3. **如何在不使用默认表单登录的情况下接入 Security？**  
   - 提示：`UserDetailsServiceAutoConfiguration` 排除、`SessionCreationPolicy.STATELESS`、JWT 过滤器。

---

## 四、Spring Security 与 JWT

1. **什么是无状态会话？为什么 REST API 常用 JWT？**  
   - 提示：服务端不存 Session；扩展性好；需处理吊销与过期。

2. **CSRF 在本项目为什么可以关闭？有什么前提？**  
   - 提示：无 Cookie Session、Bearer Token；若未来有 Cookie 登录需再评估。

3. **JWT 结构（Header/Payload/Signature）与签名算法本项目用的什么？**  
   - 提示：`JwtTokenService`、HMAC-SHA256；密钥长度与存放（配置/环境变量）。

4. **白名单路径为什么要单独维护？举几个本项目的 `permitAll` 例子并说明原因。**  
   - 提示：`JwtSecurityConstants.PERMIT_ALL`：登录注册、`/ws/**` 握手、部分房间只读接口方便分享链接与旁观等。

5. **WebSocket 握手阶段如何与 HTTP 安全策略配合？**  
   - 提示：若握手不在白名单则需带 Token（常见：Query 或 Header）；本项目 `/ws/**` 放行，实际业务里仍可能在连接后校验身份（需对照 `DpGameRoomWebSocketHandler`）。

6. **`joinRoom2` 要求 SecurityContext 中的昵称与参数一致，是在防什么攻击？**  
   - 提示：防止用别人的 token 冒充参数里的昵称进房。

---

## 五、WebSocket

1. **WebSocket 与 HTTP 长轮询的区别？**  
   - 提示：全双工、头开销、服务端推送。

2. **服务端如何按 `roomId` 广播？断线如何清理？**  
   - 提示：`roomSessions` 注册/反注册；`afterConnectionClosed`。

3. **为什么要做「与上次 JSON 相同则不再下发」？**  
   - 提示：`lastBroadcastPayloadBySession` 去重；减少流量；注意 `lastHeartBeat` 不参与 JSON 避免无意义刷新。

4. **同一房间多 Tab 打开，行为会怎样？**  
   - 提示：多个 `WebSocketSession` 都会注册；去重按 session 维度，各 Tab 独立。

---

## 六、数据库与 MyBatis

1. **MyBatis-Plus 为你省掉了哪些样板代码？**  
   - 提示：`BaseMapper`、条件构造、分页配合插件。

2. **PageHelper 分页原理（拦截 SQL）？`reasonable` 配置含义？**  
   - 提示：见 `application.properties` 中 PageHelper 与牌谱列表。

3. **Druid 连接池相比默认 Hikari 你在项目里为什么选 Druid？（若简历写了）**  
   - 提示：监控、统计；或历史原因——如实说。

4. **牌谱、参与者多对多表设计为什么要「不设数据库外键」？**  
   - 提示：见 `docs/DP_PERSISTENCE_README.md`；灵活迁移、性能、分库分表预留等常见理由。

5. **事务边界：哪些操作必须 `@Transactional`？**  
   - 提示：多表写入、先写后读一致性；结合你们 `DpHandHistoryPersistServiceImpl` 等实际用法准备例子。

---

## 七、Redis

1. **Lettuce 与 Jedis 区别？**  
   - 提示：Lettuce 基于 Netty、线程安全、异步；Spring Boot 默认常用 Lettuce。

2. **StringRedisTemplate 与 RedisTemplate 使用场景？**  
   - 提示：字符串 KV vs 序列化对象。

3. **本项目 Redis 与游戏房间的关系？**  
   - 提示：对局核心不依赖 Redis；扩展多实例时才需要会话/房间外置或 Pub/Sub。

---

## 八、HTTP API 设计与工程实践

1. **统一响应包装 `ResultUtil` 的好处？**  
   - 提示：前端统一解析、`code`/`message`/`data` 约定。

2. **文件上传大小限制配置在哪？**  
   - 提示：`spring.servlet.multipart.*`。

3. **静态资源 `/images/**`、`/music/**` 与磁盘目录如何映射？**  
   - 提示：`WebConfig` 资源处理器 + `mgdemoplus.images.file-location` / `music.file-location`。

4. **CORS 配置要注意什么？**  
   - 提示：`CorsConfig`；凭证与 Origin 白名单。

---

## 九、领域与业务（扑克 / 房间）

1. **NLHE 里「最小加注」与 `lastRaiseIncrement` 的关系？**  
   - 提示：README 与 `DpRoomServiceImpl.bet` 校验逻辑。

2. **边池（side pot）与「有效主池」统计为什么要区分？**  
   - 提示：短码全下后深码单边金额不应算进主池统计（README 牌谱主池说明）。

3. **NPC 难度与风格：Fish / Shark / TAG 等如何组织代码？**  
   - 提示：`DpNpcEngine`、策略类拆分；Shark 翻前 `DpNpcSharkPreflopStrategy`。

4. **Shark 对手画像为什么按「昵称」持久化而不是 userId？**  
   - 提示：随机房间、匿名桌；与 `dp_shark_opponent_profile` 设计一致（注意隐私与改名问题若面试官追问）。

---

## 十、LLM 与外部调用

1. **调用大模型接口超时、限流、重试如何设计？**  
   - 提示：`DpLlmNpcDecisionService`、`LlmNpc`；`inflightByKey` 防并发重复；失败降级（fold/check）。

2. **为什么 `thinking.type` 与 `reasoning_effort` 组合错误会 400？**  
   - 提示：README 方舟说明；代码里对 `disabled` 不传 `reasoning_effort` 的规避。

3. **不要把 API Key 提交到 Git，本地与 Docker 如何注入密钥？**  
   - 提示：环境变量、`application.properties` 不提交敏感内容。

---

## 十一、Docker 与运维

1. **`docker compose` 里 MySQL 端口映射为 3307 的原因？**  
   - 提示：避免与宿主机 3306 冲突（README）。

2. **数据库初始化脚本如何进入容器首次建表？**  
   - 提示：`docker-data/mysql-init` 与 `src/main/resources/db/*.sql`。

3. **健康检查与日志：生产环境你会加什么？**  
   - 提示：Actuator、集中日志、指标；本项目可按需扩展。

---

## 十二、安全与合规（加分）

1. **用户密码用 MD5 存储的问题？更推荐什么？**  
   - 提示：`docs/DpUserPassword.md`；加盐、慢哈希（BCrypt/Argon2）；历史项目可谈迁移方案。

2. **牌谱详情接口为什么必须校验参与者 `userId`？**  
   - 提示：防止越权读他人历史。

---

## 十三、场景题（综合）

1. **线上 CPU 飙高，如何排查？**  
   - 提示：线程栈、Profiler、定时器任务、JSON 序列化热点、WebSocket 广播量。

2. **某房间推送延迟大，可能原因？**  
   - 提示：定时器单线程阻塞、大对象序列化、慢 SQL（若触库）、网络。

3. **设计一个「观战人数上限」功能，改哪些类？**  
   - 提示：`register` 前计数、房间配置、超限拒绝连接或 HTTP 429。

---

## 十四、仓库内延伸阅读（答题时可引用）

| 文档 | 内容 |
|------|------|
| [README.md](../README.md) | 总览、维护长说明 |
| [docs/README.md](./README.md) | `docs/` 专题文档总索引 |
| [DPGAME.md](./DPGAME.md) | 规则与接口 |
| [WEBSOCKET.md](./WEBSOCKET.md) | WS 协议与多实例讨论 |
| [JWT.md](./JWT.md) | JWT 使用说明 |
| [DOCKER.md](./DOCKER.md) | 容器与数据库 |
| [DP_PERSISTENCE_README.md](./DP_PERSISTENCE_README.md) | 牌谱持久化 |
| [ai/npc-engine/README.md](./ai/npc-engine/README.md) | NPC 引擎 |

---

## 使用建议

- **初级**：优先掌握一、三、四、五、六中的基础概念，并能说出本项目「内存房间 + WebSocket + JWT」的整体数据流。  
- **中高级**：深入并发、扩展性、安全（密码与越权）、以及至少一条业务链（如下注校验或牌谱入库）。  
- **表达技巧**：用「问题 → 本项目做法 → 若规模变大如何演进」三段式，比纯背定义更贴近真实面试。

---

*文档生成依据：仓库 `pom.xml`、`README.md`、`application.properties` 及主要 Java 包结构；若代码变更，请以当前实现为准。*
