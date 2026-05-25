# 文档治理 Changelog · Agent1（方案阶段）

> **核对日期**：2026-05-25  
> **权威来源**：仓库 `docs/` 全量 `.md` 扫描 + `db/migration/` + `README*.md`  
> **Status**: maintained（治理元数据，非产品专题）

---

## 本 Agent 交付物

| 文件 | 动作 |
|------|------|
| `docs/refactor/doc-overhaul-plan.md` | **新建** — Agent2/3/4 总计划 |
| `docs/refactor/doc-overhaul-changelog-01.md` | **新建** — 本文件 |
| `docs/notes/README.md` | **新建** — notes 索引骨架 |
| `docs/README.md` | **改写为骨架** — 仅占位表，无专题正文 |
| 下列「已删除」路径 | **删除** — 见下节 |

未改动：`README.md` / `README.ch.md` / `README.en.md` 正文、`CLAUDE.md`、全部 Java/Vue 源码。

---

## 已删除文件（25）

### `docs/business-flow/`（一次性 audit，整目录清空）

- `audit-agent-01-room-lobby-lifecycle.md`
- `audit-agent-02-realtime-ws-presence.md`
- `audit-agent-03-quickmatch-social-invite.md`
- `audit-agent-04-gameplay-npc-history-aux.md`
- `P0-heartbeat-evict-vs-websocket-close.md`

### 重复 / 废弃专题

- `docs/NPC_UNIFIED_PREFLOP_DATA_FLOW.md`（与 `docs/ai/npc-preflop-unified-decision-flow.md` 重复，且无仓库内链接）
- `docs/ai/npc-preflop-data-flow.md`（同上，内容重叠）

### `docs/refactor/`（已完成 AI 协作 plan/audit/log）

- `caution.md`
- `custom-npc-tuning-plan.md`
- `db-io-audit.md`
- `db-io-audit-part-misc.md`
- `db-io-audit-part-room.md`
- `db-io-audit-part-social.md`
- `frontend-desensitize-audit.md`
- `frontend-fluidity-audit-game.md`
- `frontend-fluidity-audit-shell.md`
- `frontend-fluidity-impl-log.md`
- `frontend-fluidity-interaction-log.md`
- `frontend-fluidity-interaction-plan.md`
- `frontend-fluidity-plan.md`
- `frontend-route-transition-plan.md`
- `front-first-load-plan.md`
- `npc-table-talk-plan.md`
- `rule-npc-think-delay-plan.md`
- `weekly-leaderboard-plan.md`（V7 周榜已实现，方案仅历史记录）

### 保留未删（待后续 Agent 处置）

- `docs/refactor/config-inventory.md` — 与 `application.yml` 对照后 **rewrite**（Agent2）
- `docs/refactor/friend-dm-room-chat-sse-plan.md` — **README.md 仍链接**，须 merge 后再删（Agent3）
- `docs/refactor/room-mutation-side-effects.md` — **rewrite / merge**（Agent3）

---

## 空目录

- `docs/business-flow/`：**目录已不存在**（5 篇 audit 删除后移除）。

---

## 核对项

| 项 | 结果 |
|----|------|
| Flyway 最高版本 | **V7**（`V7__leaderboard_weekly.sql` → `dp_leaderboard_weekly`） |
| 删除后 `docs/**/*.md` 数量 | **48**（2026-05-25 `Get-ChildItem docs -Recurse -Filter *.md` 复核） |
| 治理前合计 | **73**（48 现存 + 25 已删，见 plan §2.1–§2.2） |
| 根 README Flyway 表述 | 仍写 **V1–V6** — **Agent2 须改为 V1–V7** 并补周榜 `GET /dp/leaderboard/weekly/hand|room` |
| `docs/refactor/` 现存 | **5** 业务/治理 md + plan/changelog（见 plan §10.1） |
| `docs/readme-scan/` 现存 | **10** md（吸收后整目录 delete） |

---

## 下一步

1. 用户确认 [doc-overhaul-plan.md](./doc-overhaul-plan.md) 中「待决 delete」三项。  
2. Agent2 → 配置/部署/安全/根 README 配置节。  
3. Agent3 → 对局/WS/快匹/社交/持久化。  
4. Agent4 → NPC 分册 + `docs/notes/` 迁入。
