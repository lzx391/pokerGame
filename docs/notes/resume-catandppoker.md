> **一句话电梯 pitch**（求职信/自我介绍可粘贴）：  
> 独立全栈交付的实时多人策略对战平台——自研牌局引擎 + WebSocket 房间同步 + 6 种规则 AI / 可选 LLM 对手，从 OAuth 登录、RBAC 权限到 Docker + CI/CD 一键上线，演示站已跑通。

---

## 投递版（推荐）

多人在线策略对战 Web 应用　个人全栈，偏后端　2026.02–至今

**项目简介：**  
想做一款能真人对战、也能单机练手的策略牌局平台，于是从零用 Spring Boot 3 + Vue 2 搭了一整套实时联机系统——房间态 WebSocket 同步、牌局逻辑全在服务端跑通，并接入 6 种规则 AI 与可选 LLM 对手。个人独立交付，Docker 同源部署已上线演示站。

演示：https://catandppoker.asia  
代码：https://github.com/lzx391/pokerGame.git

**业绩：**

● **扛住多房实时同步**：WebSocket 按房维护会话，1s 心跳 + JSON 去重，状态不变不下发；前端断线指数退避重连，对局可自动恢复。

● **跑通牌局全流程**：自研规则校验、主池/边池结算与最小加注判定；牌谱关联参与者入库，REST 分页查询 + 局内/独立页回放。

● **落地快匹配对器**：队列优先灌入未满房、≥2 人批量开新房；房间锁→队列锁顺序 + 等待超时清理，核心配对路径有单测覆盖。

● **内置六套 AI 出牌**：规则引擎覆盖 Fish/TAG/LAG/NIT/Call/Maniac 六种打法 + 房主自定义 Bot；OpenAI 兼容 Client 接火山方舟 LLM Bot，异步调用失败本地兜底，NPC 决策追踪 REST 可复盘。

● **打通上线全链路**：JWT + JTI 单会话踢旧、BCrypt 口令、GitHub/Gitee/钉钉 OAuth、RBAC `@PreAuthorize` 接口鉴权；Flyway 28 版 schema 演进，GitHub Actions 构建推 Docker Hub 并 SSH 触发线上部署。

---

## 详细版

多人在线策略对战 Web 应用　个人全栈，偏后端　2026.02–至今

**项目简介：**  
面向策略牌局爱好者的实时多人在线平台：开发期前后端分离联调，生产环境 Docker 多阶段构建后同源部署（8088 同端口 REST / WebSocket / 静态资源）。核心对局态维护在单 JVM 内存房间，WebSocket 推送 + MySQL 牌谱持久化，覆盖大厅、快速匹配、好友社交与 AI 对战全流程。

演示：https://catandppoker.asia  
代码：https://github.com/lzx391/pokerGame.git

**技术栈（后端）：** Java 17、Spring Boot 3.5、Spring WebSocket、SSE、Spring Security、JWT、OAuth2 Client + JustAuth（钉钉）、MySQL 8、Druid、MyBatis-Plus、PageHelper、Redis 7、Flyway 11.7、Docker、Nginx、GitHub Actions

**业绩：**

● **扛住多房实时推送**：按房间维护 WebSocket 会话表，1s 心跳仅在有订阅者时序列化广播；连接级 JSON 去重跳过无效下发，前端对局/快匹 WS 指数退避重连（上限 30s）。

● **自研牌局引擎与牌谱**：全流程规则校验、主池/边池结算、操作合法性判定；牌谱 payload V2 关联参与者实体入库（Flyway V18），与前端渲染口径一致，支持 REST 分页与回放。

● **快匹配对协调器**：队列→已有可加入房间优先灌入 + 队列≥2 批量开新房；严格锁顺序（房间监视器→队列锁）配合等待超时与周期清理；单元测试覆盖索引与配对核心路径。

● **双轨 AI 与可观测性**：6 种规则 NPC 风格 + 房主自定义 Bot；自封装 OpenAI 兼容 Client 接入火山方舟，支持单轮/多轮 LLM Bot，异步 + 本地兜底；NPC 决策追踪 REST（RBAC 门控）便于复盘决策链路。

● **账号安全与权限**：Spring Security + JJWT（HMAC-SHA256），JTI + Redis 单会话；BCrypt 口令存储；GitHub/Gitee/钉钉 OAuth 绑定本地用户；RBAC 角色权限表 + `@PreAuthorize` + Redis 权限缓存，覆盖看牌、实验排牌、NPC 追踪等能力。

● **工程化一键部署**：Docker 多阶段构建（Node 编前端→Maven 打 JAR→单镜像）；Compose 编排 MySQL + Redis + Nginx；Flyway V1–V28 管理 schema 演进；GitHub Actions push 触发构建推 Hub 并 SSH `docker compose pull && up -d`。

---

## 评估摘要

> 简历正文已按「投递版」原则重写（2026-06-20）；量化指标未写入终稿，见下表口径。

### 旧简历纠错清单

| 项 | 旧稿 | 代码事实 |
|---|---|---|
| Flyway 版本 | 未写或文档写 V1–V7 | 当前 **V1–V28**（成就 V14–25、OAuth V13/V16、RBAC V26–28 等） |
| 密码算法 | 旧稿写 BCrypt ✓ | **正确**；`DpUserServiceImpl` 使用 `CryptoUtil.bcryptEncode/bcryptMatches`；MD5 仅遗留工具方法，不用于口令 |
| 「前后端分离」 | 易被理解为永久分端口部署 | 更准确：**开发期分离联调** + **生产 Docker 同源 JAR 部署**（8088 同端口 REST/WS/静态资源） |
| RBAC | 旧稿未写 | **已实现 P0**：`dp_role/permission`、V26–28、`@PreAuthorize`、`/dp/admin/**` |
| OAuth | 旧稿未写 | **已实现**：GitHub/Gitee（Spring OAuth2 Client）+ 钉钉（JustAuth），`dp_social_auth` 表 |
| 成就/周榜 | 周榜旧稿有、成就无 | 周榜 **V7/V10**；成就 **8 项** + SSE 通知（V14–25） |
| NPC 风格数 | 6 种 ✓ | **准确**（FISH/TAG/LAG/NIT/CALL/MANIAC）；另有 BOT_CUSTOM 与遗留 Shark→TAG 映射 |
| Shark 跨房记忆 | 部分旧文档仍写 | **服务层已不再读写** `dp_shark_opponent_profile` |
| 控制器数量 | 旧 README 写 7–8 个 | 当前 **14+**（含 OAuth、Admin RBAC、NPC Trace、Download、Leaderboard 等） |

### 量化指标口径（建议）

| 旧稿表述 | 仓库证据 | 建议 |
|---|---|---|
| 无效广播减少 **30%~70%** | `DpGameRoomPushService` 有 per-session JSON 去重 + 仅订阅者广播；**无压测/benchmark 脚本** | 简历改为**定性**（去重 + 按需广播），或保留区间但标注「设计观测、未压测」 |
| DB 查询降低 **40%+** | `DpRoomHallServiceImpl` 有 rev+TTL Redis 缓存；**无 metrics 对比** | 简历改为**显著减少重复查询**等定性表述 |

### 建议用户手动确认项

1. **演示站** https://catandppoker.asia 是否当前可访问、版本是否为最新镜像（CI 分支 `desensitization-pre`，镜像 tag `v1.0.1`）。
2. **GitHub 仓库** https://github.com/lzx391/pokerGame.git 是否与本地 MGDemoPlus 同步（本地目录名不同）。
3. **量化指标**是否在终版简历中保留 30%~70% / 40%+（仓库内无基准测试支撑；**当前投递版/详细版已省略**）。
4. **Electron 桌面客户端**（`electron-client/`）是否作为投递亮点单独列出（可选，非 Web 主线）。
5. **大厅 Copilot Agent** 仅为规划（`docs/ai/agent-learn-plan/`），**勿写入已交付业绩**。
