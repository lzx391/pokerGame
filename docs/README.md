# MGDemoPlus：`docs/` 专题文档总索引

> **核对日期**：2026-05-25  
> **权威来源**：`src/main/java`、`src/main/resources/application.yml`、`db/migration/`（Flyway 最高 **V7**）  
> **Status**: maintained（索引骨架；表内「简介」待 Agent2/3/4 填充）  
> **治理**：[refactor/doc-overhaul-plan.md](refactor/doc-overhaul-plan.md) · 根 README：[README.md](../README.md) · [README.ch.md](../README.ch.md) · [README.en.md](../README.en.md)

---

## 根目录 README 怎么选

| 文档 | 内容侧重 | 负责 Agent |
|------|----------|------------|
| [README.md](../README.md) | 中英对照主入口：特性、术语、栈、快速开始 | 2 + 3（分节，见 overhaul 计划） |
| [README.ch.md](../README.ch.md) | 中文长说明：环境、WS、UI 迭代、NPC、牌谱… | 2 + 3 |
| [README.en.md](../README.en.md) | 英文长说明（与 ch 同结构） | 2 + 3 |

---

## 环境与配置

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [CONFIG_LAYERS.md](CONFIG_LAYERS.md) | *待 Agent2 填充* | 2 |
| [refactor/config-inventory.md](refactor/config-inventory.md) | *待 Agent2 填充* | 2 |
| [ENV_README.md](ENV_README.md) | *待 Agent2 填充* | 2 |

---

## 部署与网关

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [DOCKER.md](DOCKER.md) | *待 Agent2 填充* | 2 |
| [NGINX.md](NGINX.md) | *待 Agent2 填充* | 2 |
| [监控-Grafana使用说明.md](监控-Grafana使用说明.md) | *待 Agent2 填充* | 2 |

---

## 安全与用户

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [JWT.md](JWT.md) | *待 Agent2 填充* | 2 |
| [DpUserPassword.md](DpUserPassword.md) | *待 Agent2 填充* | 2 |

---

## DP 游戏：规则、接口与房间

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [DPGAME.md](DPGAME.md) | *待 Agent3 填充* | 3 |
| [RoomUi.md](RoomUi.md) | *待 Agent3 填充* | 3 |
| [WEBSOCKET.md](WEBSOCKET.md) | *待 Agent3 填充* | 3 |
| [dp-quick-match-flow.md](dp-quick-match-flow.md) | *待 Agent3 填充* | 3 |
| [dp-quick-match-concurrency.md](dp-quick-match-concurrency.md) | *待 Agent3 填充* | 3 |
| [dp_friend_mailbox_mvp.md](dp_friend_mailbox_mvp.md) | *待 Agent3 填充* | 3 |
| [refactor/room-mutation-side-effects.md](refactor/room-mutation-side-effects.md) | *待 Agent3 合并或升格为维护文档* | 3 |

---

## 数据持久化与牌谱

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [DP_PERSISTENCE_README.md](DP_PERSISTENCE_README.md) | *待 Agent3 填充* | 3 |

---

## Redis

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [Redis.md](Redis.md) | *待 Agent2 填充* | 2 |

---

## 曲库

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [DpMusicWebPath.md](DpMusicWebPath.md) | *待 Agent2 填充* | 2 |

---

## AI / NPC

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [ai/npc-engine/README.md](ai/npc-engine/README.md) | *待 Agent4 填充* | 4 |
| [ai/npc-preflop-unified-decision-flow.md](ai/npc-preflop-unified-decision-flow.md) | *待 Agent4 填充* | 4 |
| [ai/npc-engine/01_overview_and_entry.md](ai/npc-engine/01_overview_and_entry.md) | *待 Agent4 填充* | 4 |
| [ai/npc-engine/02_normal_npc_implementation.md](ai/npc-engine/02_normal_npc_implementation.md) | *待 Agent4 填充* | 4 |
| [ai/npc-engine/03_normal_npc_modules.md](ai/npc-engine/03_normal_npc_modules.md) | *待 Agent4 填充* | 4 |
| [ai/npc-engine/04_shark_legacy.md](ai/npc-engine/04_shark_legacy.md) | *待 Agent4 填充* | 4 |
| [ai/npc-engine/05_shark_modules_legacy.md](ai/npc-engine/05_shark_modules_legacy.md) | *待 Agent4 填充* | 4 |
| [ai/npc-llm/part01_类间关系以及调用.md](ai/npc-llm/part01_类间关系以及调用.md) | *待 Agent4 填充* | 4 |

---

## 学习备忘（非必读）

| 文档 | 说明 |
|------|------|
| [notes/README.md](notes/README.md) | 学习类文档索引；正文迁入 `docs/notes/` 后由 Agent4 维护 |

---

## 调度与专题（维护向）

| 文档 | 简介 | 负责 Agent |
|------|------|------------|
| [SpringScheduling.md](SpringScheduling.md) | *待 Agent2 填充*（`@Scheduled`、大厅对齐、多实例） | 2 |

---

## 治理与协作（Agent 专用）

| 文档 | 说明 |
|------|------|
| [refactor/doc-overhaul-plan.md](refactor/doc-overhaul-plan.md) | **Agent2/3/4 开工前必读**：处置总表、合并规则、README 分节 |
| [refactor/doc-overhaul-changelog-01.md](refactor/doc-overhaul-changelog-01.md) | Agent1 已删文件与骨架变更记录 |
| [refactor/doc-overhaul-changelog-04.md](refactor/doc-overhaul-changelog-04.md) | Agent4 NPC/notes/readme-scan 清理记录 |
| [refactor/doc-overhaul-index-draft.md](refactor/doc-overhaul-index-draft.md) | 建议版专题索引表（合并轮用） |

---

## 前端 DP 游戏专题（`front/dp_game/docs`）

对局页布局、主题、CSS 变量等见 [front/dp_game/docs/README.md](../front/dp_game/docs/README.md)（**不由本计划重写正文**，仅索引互链）。

---

## 文件顶部一级标题（`#`）说明

各维护文档重写后，表格内简介须与对应文件首行 `# …` 一致；文首统一块格式见 [doc-overhaul-plan.md](refactor/doc-overhaul-plan.md)。
