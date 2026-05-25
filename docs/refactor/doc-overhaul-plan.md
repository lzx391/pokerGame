# MGDemoPlus 文档收尾 · 总计划（Agent1）

> **核对日期**：2026-05-25  
> **权威来源**：`src/main/java`、`src/main/resources/application.yml`、`src/main/resources/db/migration/`、`docker-compose*.yml`  
> **Status**: maintained  
> **读者**：文档重写 Agent2 / Agent3 / Agent4；**禁止**本任务内 `git commit` / `git push` / 改 Java·Vue 源码（可读源码核对）。

---

## 0. 目标与分工

| Agent | 职责域 | 产出 |
|-------|--------|------|
| **2** | 环境、配置分层、部署、网关、JWT、密码、Redis、曲库路径、监控、调度 | 重写 §2 表中 Agent2 行；刷新根 README **配置/部署/技术栈/Flyway** 相关节 |
| **3** | 对局流程、REST、WebSocket、快匹、大厅/房间副作用、社交邮箱、牌谱持久化 | 重写 §2 表中 Agent3 行；刷新根 README **对局/实时/社交/REST** 节 |
| **4** | NPC/LLM 分册、翻前统一策略、学习类迁入 `docs/notes/` | 重写 §2 表中 Agent4 行；迁移 §4 学习清单；刷新根 README **AI 与曲库** 节 |

**Flyway（必须与代码一致）**

| 版本 | 文件 | 要点 |
|------|------|------|
| V1 | `V1__init_schema.sql` | 基线：用户、大厅、牌谱、Shark 表等 |
| V2 | `V2__friend_dm_and_room_chat.sql` | 好友私信、局内聊天 |
| V3 | `V3__room_chat_composite_pk.sql` | 局内聊天主键 |
| V4 | `V4__user_stats.sql` | `dp_user_stats` |
| V5 | `V5__largest_room_net_multiplier.sql` | 单房净赢倍数 |
| V6 | `V6__largest_pot_won_multiplier.sql` | 单局倍数 |
| **V7** | **`V7__leaderboard_weekly.sql`** | **周榜** `dp_leaderboard_weekly` |

根 `README.md` / `README.ch.md` / `README.en.md` 中凡写 **「V1–V6」** 处，Agent2 **统一改为 V1–V7**，并补充周榜 REST：`GET /dp/leaderboard/weekly/hand|room`（`DpLeaderboardController`、`JwtSecurityConstants` 白名单）。

---

## 1. 文档文首规范（重写后每篇维护文档必备）

```markdown
> **核对日期**：YYYY-MM-DD
> **权威来源**：一句（如 `DpRoomServiceImpl` + `application.yml`）
> **Status**: maintained
```

- 一级标题 `#` 与 `docs/README.md` 索引表「简介」列一致。  
- 示例密钥一律 `your-jwt-secret`、`mgdemo_root` 等占位，**禁止**真实生产密钥。

---

## 2. Disposition 总表（`docs/**/*.md`）

**图例**：`delete` 已删或待删 · `rewrite` 按实现重写 · `migrate-notes` 迁入 `docs/notes/` · `merge-into-xxx` 合并后删源文件 · `skeleton` Agent1 已搭骨架待填

### 2.1 仍存在的文件（**48** 条，与 `Get-ChildItem docs -Recurse -Filter *.md` 一致）

> **校验命令**（2026-05-25）：`(Get-ChildItem docs -Recurse -Filter *.md).Count` → **48**

| 路径 | 处置 | Agent | 核对依据 | 备注 |
|------|------|-------|----------|------|
| `docs/README.md` | skeleton → rewrite | 2 | 全目录索引 | Agent1 已搭空表；Agent2 填表内简介 |
| `docs/notes/README.md` | rewrite | 4 | — | 迁入完成后更新索引表 |
| `docs/refactor/doc-overhaul-plan.md` | maintained | — | — | 本文件 |
| `docs/refactor/doc-overhaul-changelog-01.md` | maintained | — | — | Agent1 变更记录 |
| `docs/CONFIG_LAYERS.md` | rewrite | 2 | `application.yml` 头注释、`docs/CONFIG_LAYERS.md` | 与 inventory 互链 |
| `docs/refactor/config-inventory.md` | rewrite | 2 | `application.yml`、各 `@Value`/`@ConfigurationProperties` | **勿盲目 delete**；扫描日 2026-05-24，需按 yml 逐键复核 |
| `docs/ENV_README.md` | rewrite | 2 | `LocalDotenvLoader`、`application.yml`、`MgDemoPlusApplication` | 删除对 `application.properties` 的过时表述 |
| `docs/DOCKER.md` | rewrite | 2 | `docker-compose.yml`、`docker-compose.hub.yml`、`Dockerfile` | |
| `docs/NGINX.md` | rewrite | 2 | `docker/nginx/default.conf` | SSE `proxy_buffering off` |
| `docs/监控-Grafana使用说明.md` | rewrite | 2 | `docker-compose.yml` prometheus/grafana 服务 | |
| `docs/JWT.md` | rewrite | 2 | `SecurityConfig`、`JwtAuthenticationFilter`、`JwtSecurityConstants` | |
| `docs/DpUserPassword.md` | rewrite | 2 | `DpUserServiceImpl`、`CryptoUtil.bcrypt*` | **纠正**：现网为 **bcrypt**，非 MD5（长 README 仍有 MD5 过时句） |
| `docs/Redis.md` | rewrite | 2 | `DpRedisLoginCacheService`、大厅缓存、曲库列表缓存 | roomMap **不在** Redis |
| `docs/DpMusicWebPath.md` | rewrite | 2 | `DpMusicController`、`mgdemoplus.music.file-location` | |
| `docs/SpringScheduling.md` | rewrite | 2 | `DpRoomLobbyReconcileScheduler`、`@Scheduled`、yml 开关 | |
| `docs/DPGAME.md` | rewrite | 3 | `DpRoomController`、`DpRoomServiceImpl` | 核心维护文档之一 |
| `docs/WEBSOCKET.md` | rewrite | 3 | `DpGameRoomWebSocketHandler`、`DpQuickMatchWebSocketHandler`、`DpGameRoomPushService` | 核心维护文档之一 |
| `docs/RoomUi.md` | rewrite | 3 | `front/dp_game` 对局页、`seatIndex` 约定 | |
| `docs/dp-quick-match-flow.md` | rewrite | 3 | `DpQuickMatchPairingCoordinator`、`quickMatch2` | |
| `docs/dp-quick-match-concurrency.md` | rewrite | 3 | `dpQuickMatchAssignmentLock`、`JoinableQuickMatchRoomIndex` | 代码优先于旧文档 |
| `docs/dp_friend_mailbox_mvp.md` | rewrite | 3 | `DpFriendMailboxController`、`DpFriendSocialService` | 吸收 friend-dm 方案后删 plan |
| `docs/DP_PERSISTENCE_README.md` | rewrite | 3 | `DpHandHistoryPersistServiceImpl`、Flyway V1/V4 | |
| `docs/refactor/room-mutation-side-effects.md` | merge-into-DPGAME 或 rewrite | 3 | `DpRoomBO`、大厅/快匹副作用矩阵 | **待决**：独立维护 vs 并入 `DPGAME.md` 附录（§7） |
| `docs/refactor/friend-dm-room-chat-sse-plan.md` | merge-into-dp_friend_mailbox_mvp | 3 | Flyway V2/V3、`SocialSseHub` | **README.md:114 仍链此文**；合并后 delete |
| `docs/ai/npc-engine/README.md` | rewrite | 4 | `DpNpcEngine` | 分册导航 |
| `docs/ai/npc-preflop-unified-decision-flow.md` | rewrite | 4 | `DpNpcUnifiedPreflopStrategy` | **canonical** 翻前文档 |
| `docs/ai/npc-engine/01_overview_and_entry.md` | rewrite | 4 | `DpNpcEngine#decideActionIfReady` | |
| `docs/ai/npc-engine/02_normal_npc_implementation.md` | rewrite | 4 | `npc/strategy/*` | |
| `docs/ai/npc-engine/03_normal_npc_modules.md` | rewrite | 4 | 模块拆分 | |
| `docs/ai/npc-engine/04_shark_implementation.md` | rewrite | 4 | Shark 策略类 | 与现网 Bot 前缀对照 |
| `docs/ai/npc-engine/05_shark_modules.md` | rewrite | 4 | Shark 模块 | `dp_shark_opponent_profile` 表在、服务是否仍写需核对 |
| `docs/ai/npc-llm/part01_类间关系以及调用.md` | rewrite | 4 | `DpLlmNpcDecisionService`、`OpenAiCompatibleChatClient` | |
| `docs/自学.md` | migrate-notes | 4 | — | → `docs/notes/自学.md` |
| `docs/Spring依赖注入复习笔记.md` | migrate-notes | 4 | — | → `docs/notes/` |
| `docs/SPRING_DI_NOTES.md` | migrate-notes | 4 | — | 与上重复主题，保留一篇或合并 |
| `docs/BACKEND_INTERVIEW_QUESTIONS.md` | migrate-notes | 4 | — | → `docs/notes/` |
| `docs/Json-map-serialization.md` | migrate-notes | 4 | — | → `docs/notes/` |
| `docs/Java对象引用与浅拷贝深拷贝备忘.md` | migrate-notes | 4 | — | → `docs/notes/`；`roomMap` 示例需标注「单机内存」 |
| `docs/readme-scan/README.md` | delete | 2 | — | Agent2/3 吸收后删 |
| `docs/readme-scan/ARCHITECTURE.md` | merge-into-README.md | 3 | 包结构 | 吸收后 delete |
| `docs/readme-scan/services.md` | merge-into-DPGAME | 3 | Service 列表 | 吸收后 delete |
| `docs/readme-scan/controllers.md` | merge-into-DPGAME | 3 | `DpRoomController` 等 | 吸收后 delete |
| `docs/readme-scan/social-friend.md` | merge-into-dp_friend_mailbox_mvp | 3 | social 包 | 吸收后 delete |
| `docs/readme-scan/ai-npc.md` | merge-into-ai/npc-engine/README | 4 | npc 包 | 吸收后 delete |
| `docs/readme-scan/infra-config.md` | merge-into-ENV_README | 2 | yml | 吸收后 delete |
| `docs/readme-scan/frontend-brief.md` | merge-into-README.md | 3 | `vue.config.js` | 吸收后 delete；细节仍指 `front/dp_game/docs` |
| `docs/readme-scan/MIGRATION_MANIFEST.md` | delete | — | — | 模块化迁移验收，历史-only |
| `docs/readme-scan/STYLE_GUIDE.md` | delete | 2 | — | 体例并入本 plan §1 |

### 2.2 Agent1 已 delete（**25** 条；§2.1+§2.2 = **73** 条，覆盖治理前 `docs/**/*.md` 全集）

> 下列路径**已从磁盘删除**；Disposition 仍列入总表供 Agent2/3/4 改链与验收对照。

| 路径 | 处置 | 理由 |
|------|------|------|
| `docs/business-flow/*`（5） | delete | 一次性 audit，默认不与线上一致维护 |
| `docs/NPC_UNIFIED_PREFLOP_DATA_FLOW.md` | delete | 与 `ai/npc-preflop-unified-decision-flow.md` 重复，无入链 |
| `docs/ai/npc-preflop-data-flow.md` | delete | 同上 |
| `docs/refactor/caution.md` | delete | 草稿级心跳/房间提纲，无引用 |
| `docs/refactor/custom-npc-tuning-plan.md` | delete | plan |
| `docs/refactor/db-io-audit*.md`（4） | delete | audit |
| `docs/refactor/frontend-*`（11） | delete | plan/audit/log |
| `docs/refactor/front-first-load-plan.md` | delete | plan（DONE 交付） |
| `docs/refactor/npc-table-talk-plan.md` | delete | plan |
| `docs/refactor/rule-npc-think-delay-plan.md` | delete | plan |
| `docs/refactor/weekly-leaderboard-plan.md` | delete | V7 已实现 |

记录详见 [doc-overhaul-changelog-01.md](./doc-overhaul-changelog-01.md)。

---

## 3. `docs/refactor/` 删除规则（后续 Agent 适用）

1. **文件名**含 `plan`、`audit`、`log`、`impl-log` → 默认 **delete**（实现已完成且无外部链接）。  
2. **文内**标明 `PASS` / `已完成` / `DONE Build complete` 且 **无** 其他文档（含根 README）链接 → **delete**。  
3. **例外**：`config-inventory.md` — 与 `application.yml` 仍一致则 **rewrite**（Agent2），**勿盲目删**。  
4. **例外**：`room-mutation-side-effects.md` — 维护向副作用矩阵，**rewrite 或 merge**（Agent3）。  
5. **例外**：`friend-dm-room-chat-sse-plan.md` — 先 **merge-into** `dp_friend_mailbox_mvp.md`，更新 `README.md` 链接后 **delete**。

---

## 4. 迁 `docs/notes/` 清单

| 源路径 | 目标路径 | Agent |
|--------|----------|-------|
| `docs/自学.md` | `docs/notes/自学.md` | 4 |
| `docs/Spring依赖注入复习笔记.md` | `docs/notes/Spring依赖注入复习笔记.md` | 4 |
| `docs/SPRING_DI_NOTES.md` | `docs/notes/SPRING_DI_NOTES.md`（或与中文合并） | 4 |
| `docs/BACKEND_INTERVIEW_QUESTIONS.md` | `docs/notes/BACKEND_INTERVIEW_QUESTIONS.md` | 4 |
| `docs/Json-map-serialization.md` | `docs/notes/Json-map-serialization.md` | 4 |
| `docs/Java对象引用与浅拷贝深拷贝备忘.md` | `docs/notes/Java对象引用与浅拷贝深拷贝备忘.md` | 4 |

迁入后：删除 `docs/` 根目录原文件；更新 `docs/notes/README.md` 与 `docs/README.md` 链接。

---

## 5. 重复文档合并表

| 保留（canonical） | 删除/已删（源） | 操作 |
|------------------|-----------------|------|
| `docs/ai/npc-preflop-unified-decision-flow.md` | `docs/NPC_UNIFIED_PREFLOP_DATA_FLOW.md` | **已删**；Agent4 重写时吸收差异段落 |
| 同上 | `docs/ai/npc-preflop-data-flow.md` | **已删** |
| `docs/dp_friend_mailbox_mvp.md` | `docs/refactor/friend-dm-room-chat-sse-plan.md` | Agent3 merge 后 delete plan |
| `docs/ENV_README.md` + `CONFIG_LAYERS.md` | `docs/readme-scan/infra-config.md` | 吸收后 delete scan |
| `docs/ai/npc-engine/README.md` | `docs/readme-scan/ai-npc.md` | 吸收后 delete scan |
| `README.md` 包结构表 | `docs/readme-scan/ARCHITECTURE.md` | 吸收后 delete scan |
| `docs/SPRING_DI_NOTES.md` + 中文 DI 笔记 | — | Agent4 择一或合并为一篇 |

---

## 6. 根 README 章节归属（避免并行改同一节）

### `README.md`（中英对照主文件）

| 章节（中文 / English 同级） | Agent | 核对重点 |
|----------------------------|-------|----------|
| 给代码评审 / For reviewers | **3**（栈表 **2** 补 Flyway V7） | `DpRoomRegistry`、`Flyway V1–**V7**` |
| 怎么跑 / Shortest path | **2** | compose、Nginx、端口 |
| 一张图 / Architecture diagram | **2** | 不变或微调 |
| 对局与房间 / Rooms & gameplay | **3** | `DpRoomServiceImpl`、V7 周榜一句 |
| 主要 REST 前缀 | **3** | 七控制器 + leaderboard |
| 实时与匹配 | **3** | WS、快匹 |
| 账号与社交 | **3** | 删链 `friend-dm-room-chat-sse-plan`，改 mailbox 文档 |
| 前端要点 | **3** | `vue.config.js`、hash 路由 |
| AI 与曲库 | **4** | NPC 前缀、LLM、`dp.llm.ark.*` |
| 技术栈 | **2** | Flyway **V1–V7**、Boot 3.5.11 |
| 环境要求、配置要点 | **2** | yml、`.env` |
| 快速开始 | **2** | Docker、Grafana 链接 |
| 测试 | **3** | 现有测试类名 |
| 仓库结构、文档索引 | **2** | 链到 `docs/README.md` |
| 路线图、许可证 | **2** | `TODO.md` 可选 |

### `README.ch.md` / `README.en.md`（长说明）

| 块 | Agent |
|----|-------|
| 环境变量、Docker、Redis、监控 | **2** |
| WebSocket、对局 UI 时间线、快匹、牌谱、JWT 段落 | **3**（密码 bcrypt **2** 改） |
| NPC / LLM 长段落 | **4** |
| 文档链接列表 | **2** 更新索引指向；删过时链 |

**并行规则**：同一 PR/批次内 **禁止** 两人改同一 `##` 节；英文节与中文节由 **同一 Agent** 成对更新。

---

## 7. 待决 delete（需用户确认）

| # | 路径 | 建议 | 风险 |
|---|------|------|------|
| 1 | `docs/refactor/room-mutation-side-effects.md` | **A** merge 进 `DPGAME.md` 附录后 delete · **B** 保留独立 maintained | A 更短；B 便于 CR 查副作用 |
| 2 | `docs/readme-scan/` 整目录 | 各 Agent 吸收后 **delete 全部 9 篇** | 吸收前 README 仍链 `ARCHITECTURE.md` |
| 3 | `docs/refactor/friend-dm-room-chat-sse-plan.md` | Agent3 合并后 delete | 须先改 `README.md:114` |

---

## 8. 验收清单（给用户）

### 8.1 链接检查范围

- 仓库内所有 `.md` 的相对链接（`docs/`、`README*.md`、`front/dp_game/docs/`、`CLAUDE.md`）。  
- 重点：根 `README.md` 不再指向已删 `refactor/*plan*`、`business-flow/`、`NPC_UNIFIED_*`。  
- `docs/README.md` 每行链接目标存在且 Status 为 maintained（notes 除外）。

### 8.2 核心 5 篇（重写完成后须「实现一致」）

| # | 文档 | 负责 Agent |
|---|------|------------|
| 1 | [docs/README.md](../README.md) | 2（索引）+ 3/4 补表 |
| 2 | [docs/DPGAME.md](../DPGAME.md) | 3 |
| 3 | [docs/WEBSOCKET.md](../WEBSOCKET.md) | 3 |
| 4 | [docs/JWT.md](../JWT.md) | 2 |
| 5 | [docs/ENV_README.md](../ENV_README.md) | 2 |

### 8.3 全局一致性

- [ ] Flyway 表述：**V7** 存在于根 README、`ENV_README`、`DOCKER`、持久化文档。  
- [ ] 密码：**bcrypt**（`CryptoUtil.bcryptEncode`），无 MD5 主路径描述。  
- [ ] 配置：仅 `application.yml` + `.env`，无 `application.properties` 主配置叙述。  
- [ ] 学习类仅出现在 `docs/notes/`。  
- [ ] 维护文档均有 §1 文首三块。

### 8.4 验证命令（Agent 收尾自选）

```powershell
# 文档数量（应随 delete/migrate 变化，以 plan §2 为准）
(Get-ChildItem -Path docs -Recurse -Filter *.md).Count

# Flyway 文件列表
Get-ChildItem src/main/resources/db/migration/V*.sql | Sort-Object Name
```

---

## 9. `docs/business-flow/` 与 `docs/refactor/`

- **`business-flow/`**：5 篇 audit 已删；**目录已移除**。新业务流程写入 `DPGAME` / `WEBSOCKET` / 专题文，**不再** 使用 `business-flow/`。  
- **`refactor/`** 收尾后仅保留：`config-inventory.md`、治理 plan/changelog、`friend-dm-room-chat-sse-plan.md`（合并前）、`room-mutation-side-effects.md`；**无** 长期 plan/audit/log。

---

## 10. 目录清单（用户要求的 `.md` 全列）

### 10.1 `docs/refactor/` — 收尾后**现存**（**5** 个 `.md`）

| 文件 | 处置 |
|------|------|
| `config-inventory.md` | rewrite · Agent2 |
| `friend-dm-room-chat-sse-plan.md` | merge-into `dp_friend_mailbox_mvp.md` · Agent3 |
| `room-mutation-side-effects.md` | rewrite 或 merge · Agent3（§7 待决） |
| `doc-overhaul-plan.md` | maintained |
| `doc-overhaul-changelog-01.md` | maintained |

### 10.2 `docs/refactor/` — Agent1 **已删**（18 个 `.md`）

`caution.md` · `custom-npc-tuning-plan.md` · `db-io-audit.md` · `db-io-audit-part-misc.md` · `db-io-audit-part-room.md` · `db-io-audit-part-social.md` · `frontend-desensitize-audit.md` · `frontend-fluidity-audit-game.md` · `frontend-fluidity-audit-shell.md` · `frontend-fluidity-impl-log.md` · `frontend-fluidity-interaction-log.md` · `frontend-fluidity-interaction-plan.md` · `frontend-fluidity-plan.md` · `frontend-route-transition-plan.md` · `front-first-load-plan.md` · `npc-table-talk-plan.md` · `rule-npc-think-delay-plan.md` · `weekly-leaderboard-plan.md`

### 10.3 `docs/business-flow/` — Agent1 **已删**（5 个 `.md`，目录已移除）

`audit-agent-01-room-lobby-lifecycle.md` · `audit-agent-02-realtime-ws-presence.md` · `audit-agent-03-quickmatch-social-invite.md` · `audit-agent-04-gameplay-npc-history-aux.md` · `P0-heartbeat-evict-vs-websocket-close.md`

### 10.4 `docs/readme-scan/` — **现存**（10 个 `.md`，吸收后整目录删除）

| 文件 | 合并目标 | Agent |
|------|----------|-------|
| `README.md` | 索引职责并入 `docs/README.md` | 2 |
| `ARCHITECTURE.md` | `README.md` 包结构表 | 3 |
| `services.md` | `DPGAME.md` | 3 |
| `controllers.md` | `DPGAME.md` | 3 |
| `social-friend.md` | `dp_friend_mailbox_mvp.md` | 3 |
| `frontend-brief.md` | 根 `README.md` 前端节 | 3 |
| `infra-config.md` | `ENV_README.md` + `CONFIG_LAYERS.md` | 2 |
| `ai-npc.md` | `ai/npc-engine/README.md` | 4 |
| `MIGRATION_MANIFEST.md` | —（历史验收） | delete · — |
| `STYLE_GUIDE.md` | 体例并入本 plan §1 | delete · 2 |

---

## 11. 范围外（本计划不 rewrite 正文）

- `front/dp_game/docs/**`（仅互链）  
- `CLAUDE.md`（不改）  
- Java / Vue 源码  
- 根目录 `README.md` / `README.ch.md` / `README.en.md`（**本 Agent 不改正文**；Flyway **V1–V7**、bcrypt、删过时链由 Agent2/3/4 按 §6 执行）

---

*Agent1 完成日期：2026-05-25 · 磁盘核验：48 现存 + 25 已删 = 73 · 变更记录：[doc-overhaul-changelog-01.md](./doc-overhaul-changelog-01.md)*
