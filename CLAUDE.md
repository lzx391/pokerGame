# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 编码前必须做的事

**先问清楚需求再动手，不要猜测。** 遇到模糊需求时，先复述你的理解、列出你将改动哪些文件、确认边界条件，得到用户确认后再开始写代码。

## 常用命令

```bash
# 构建（跳过测试）
mvn clean package -DskipTests

# 运行
mvn spring-boot:run

# 运行单个测试类
mvn test -Dtest=ClassName

# Docker Compose 全套部署
docker compose up -d --build
```

## 技术栈

- Java 17, Spring Boot 3.5.11, Maven
- MySQL 8.0 + Druid 连接池 + MyBatis-Plus 3.5.8 + PageHelper
- Flyway 11.7.2（数据库迁移）
- Redis（Lettuce 客户端）
- Spring Security + JWT（jjwt 0.13.0, HMAC-SHA256）
- Spring WebSocket（原生，非 STOMP）
- dotenv-java（本地 `.env` 加载）
- 敏感词过滤：houbb/sensitive-word（DFA 算法）
- 前端：Vue 2（`front/dp_game/`），后端 API 与前端同端口 8088

## Flyway 迁移规则（必须遵守）

1. **已应用过的迁移脚本绝对不可修改。** Flyway 通过校验和检测变更，修改旧脚本会导致启动失败。
2. 所有表结构变更必须新建版本化脚本：`src/main/resources/db/migration/V{序号}__{描述}.sql`
3. 序号递增，例如已有 V1～V4，下一个是 V5。

## 项目架构

### 分层约定

```
controller/          → REST 接口，只做参数校验和路由，不写业务逻辑
  {feature}/impl/    → Service 实现类
  {feature}/mapper/  → MyBatis-Plus Mapper（或 MyBatis XML Mapper）
  {feature}/entity/  → 数据库实体
  {feature}/bo/      → 业务对象/传输对象
  {feature}/vo/      → 视图对象（返回给前端）
```

### 包结构（按功能域）

| 包 | 职责 |
|---|---|
| `common` | 共享实体（`DpPlayer`, `DpRoom`, `DpPot`）、共享 Mapper（`DpUserMapper`） |
| `room` | 房间生命周期、玩家行动、结算 — 核心模块 |
| `lobby` | 大厅列表、房间搜索、幽灵房对齐 |
| `npc` | 规则型 AI（Fish/Maniac/TAG/Shark）和大模型 AI（BOT_LLM） |
| `history` | 牌谱持久化与查询 |
| `social` | 好友、私信、SSE 推送 |
| `quickmatch` | 快速匹配队列与配对 |
| `music` | 对局 BGM 曲库 |
| `user` | 注册/登录/资料、头像上传 |
| `roomchat` | 局内聊天归档 |
| `presence` | 站点在线心跳 |
| `security` | JWT 过滤器、常量、Token 服务 |
| `config` | Spring 配置类 |
| `websocket` | WebSocket Handler |
| `utils` | 工具类（牌力评估、加密、昵称生成等） |

### Service 模式

- 接口定义在功能域包根目录（如 `room/DpRoomService.java`）
- 实现类放在 `impl/` 子包（如 `room/impl/DpRoomServiceImpl.java`）
- Controller 通过 `@Autowired` 注入接口

### 响应格式

所有 REST 接口统一使用 `ResultUtil`：
- `ResultUtil.ok().data("key", value)` — 成功
- `ResultUtil.error()` — 失败
- 内置工厂方法：`ResultUtil.repeatUsername()`, `ResultUtil.sensitiveUsername()`, `ResultUtil.invalidNickname()`

## 关键设计决策与约定

### 房间状态
- 游戏房间在 **单机内存 `ConcurrentHashMap`** 中维护，不走 Redis
- `DpRoomLobbyReconcileScheduler` 定时对齐 `dp_room_lobby` 表与内存房间，清幽灵房
- WebSocket 也基于同进程内存连接注册表，无跨实例广播

### 安全
- `JwtSecurityConstants.PERMIT_ALL` 定义白名单路径（登录、注册、WebSocket、静态资源、大厅只读接口等）
- 新增公开接口需要在 `JwtSecurityConstants` 中加白名单
- 密码存储：MD5(UTF-8) 摘要，前端传明文 → 后端 `CryptoUtil.md5HexUtf8()` 处理后落库
- Controller 中获取当前用户：通过 `SecurityContextHolder.getContext().getAuthentication()` 取得昵称，再用 `DpUserMapper.selectByNickname()` 查用户

### 环境变量与配置
- `.env` 文件在 `MgDemoPlusApplication.main()` 中由 `LocalDotenvLoader` 在 Spring 启动前加载
- 如环境变量已存在则**不覆盖**（环境变量优先于 `.env`）
- Docker Compose 直接注入环境变量，不走 `LocalDotenvLoader`

### 数据库
- 数据库名：`school_db`
- MyBatis-Plus 分页需要 `MybatisPlusConfig` 中的 `PaginationInnerInterceptor`，否则 `BaseMapper#selectPage` 不生效
- Mapper XML 路径：`classpath*:mapper/**/*.xml`
- 表结构变更完全由 Flyway 管理，不要手动在数据库执行 DDL

### 依赖注入
- 配置类：构造器注入
- Controller/Service：`@Autowired` 字段注入
- 不要混用两种风格

### 命名前缀
- 所有项目专属类使用 `Dp` 前缀（`DpRoom`, `DpPlayer`, `DpNpcEngine` 等）

## 文档索引

- 完整专题文档地图：`docs/README.md`
- 中文长说明（环境变量/WebSocket/NPC/Docker）：`README.ch.md`
- Docker 部署：`docs/DOCKER.md`
- 游戏协议与流程：`docs/DPGAME.md`
- JWT 安全机制：`docs/JWT.md`
- NPC 引擎：`docs/ai/npc-engine/README.md`
