# 文档治理 Changelog · Agent4（NPC / notes / readme-scan / 清理）

> **核对日期**：2026-05-25  
> **权威来源**：`npc/` 源码、`db/migration/V7__leaderboard_weekly.sql`、`MgDemoPlusApplication`  
> **Status**: maintained（治理元数据）

未执行：`git commit` / `git push`。未改 Java/Vue 源码。未重写 `docs/README.md` 索引表「简介」列（见 [doc-overhaul-index-draft.md](./doc-overhaul-index-draft.md)）。

---

## 交付物

| 文件 | 动作 |
|------|------|
| `docs/ai/**` | 重写 / 校正（见下表） |
| `docs/notes/README.md` | 填入 6 篇索引 |
| `docs/refactor/doc-overhaul-changelog-04.md` | 本文件 |
| `docs/refactor/doc-overhaul-index-draft.md` | 建议版 `docs/README.md` 全表 |
| 根 `README.md` | **仅** 给代码评审 / AI / Flyway / 断链修复（与 Agent2/3 节不重叠） |
| `README.ch.md` / `README.en.md` | 学习类链接 → `docs/notes/` |

---

## Delete（磁盘已移除）

### 重复 NPC

| 路径 | 理由 |
|------|------|
| `docs/NPC_UNIFIED_PREFLOP_DATA_FLOW.md` | 与 `ai/npc-preflop-unified-decision-flow.md` 重复 |
| `docs/ai/npc-preflop-data-flow.md` | 同上 |

### `docs/readme-scan/`（整目录 10 篇）

| 路径 | 合并目标 |
|------|----------|
| `README.md` | `docs/README.md` |
| `ARCHITECTURE.md` | 根 `README.md` 包结构表 |
| `services.md` | `DPGAME.md`（Agent3） |
| `controllers.md` | `DPGAME.md`（Agent3） |
| `social-friend.md` | `dp_friend_mailbox_mvp.md`（Agent3） |
| `ai-npc.md` | `ai/npc-engine/README.md` |
| `infra-config.md` | `ENV_README.md` + `CONFIG_LAYERS.md`（Agent2） |
| `frontend-brief.md` | 根 `README.md` 前端节（Agent3） |
| `MIGRATION_MANIFEST.md` | —（历史验收） |
| `STYLE_GUIDE.md` | plan §1 体例 |

### `docs/business-flow/`（5 篇 audit，目录已删）

`audit-agent-01-room-lobby-lifecycle.md` · `audit-agent-02-realtime-ws-presence.md` · `audit-agent-03-quickmatch-social-invite.md` · `audit-agent-04-gameplay-npc-history-aux.md` · `P0-heartbeat-evict-vs-websocket-close.md`

### `docs/refactor/`（plan/audit/log，18 篇）

`caution.md` · `custom-npc-tuning-plan.md` · `db-io-audit.md` · `db-io-audit-part-misc.md` · `db-io-audit-part-room.md` · `db-io-audit-part-social.md` · `frontend-desensitize-audit.md` · `frontend-fluidity-audit-game.md` · `frontend-fluidity-audit-shell.md` · `frontend-fluidity-impl-log.md` · `frontend-fluidity-interaction-log.md` · `frontend-fluidity-interaction-plan.md` · `frontend-fluidity-plan.md` · `frontend-route-transition-plan.md` · `front-first-load-plan.md` · `npc-table-talk-plan.md` · `rule-npc-think-delay-plan.md` · `weekly-leaderboard-plan.md`

### Shark 分册更名（删旧文件名）

| 删除 | 保留 |
|------|------|
| `ai/npc-engine/04_shark_implementation.md` | `04_shark_legacy.md` |
| `ai/npc-engine/05_shark_modules.md` | `05_shark_modules_legacy.md` |

---

## Migrate → `docs/notes/`（删根目录源文件）

| 源（已删） | 目标（保留） |
|------------|--------------|
| `docs/自学.md` | `docs/notes/自学.md` |
| `docs/Spring依赖注入复习笔记.md` | `docs/notes/Spring依赖注入复习笔记.md` |
| `docs/SPRING_DI_NOTES.md` | `docs/notes/SPRING_DI_NOTES.md` |
| `docs/BACKEND_INTERVIEW_QUESTIONS.md` | `docs/notes/BACKEND_INTERVIEW_QUESTIONS.md` |
| `docs/Json-map-serialization.md` | `docs/notes/Json-map-serialization.md` |
| `docs/Java对象引用与浅拷贝深拷贝备忘.md` | `docs/notes/Java对象引用与浅拷贝深拷贝备忘.md` |

---

## Merge / Rewrite（保留路径）

| 路径 | 动作 |
|------|------|
| `docs/ai/npc-engine/README.md` | 导航 + `npc/` 包树 + rule-think 说明 |
| `docs/ai/npc-engine/01_overview_and_entry.md` | 文首块；调用链 |
| `docs/ai/npc-engine/02_normal_npc_implementation.md` | `FISH`/`TAG` 与统一翻前；删 Shark 专用策略叙述 |
| `docs/ai/npc-engine/03_normal_npc_modules.md` | `dp.npc.rule-think` 已实现 |
| `docs/ai/npc-engine/04_shark_legacy.md` | **新建** — `BOT_Shark`→TAG |
| `docs/ai/npc-engine/05_shark_modules_legacy.md` | **新建** — 表留存、无写路径 |
| `docs/ai/npc-preflop-unified-decision-flow.md` | 文首块 + canonical 翻前 |
| `docs/ai/npc-llm/part01_类间关系以及调用.md` | 路径校正；无 `serviceImpl/dp` |

---

## `docs/refactor/` 收尾后现存（6 篇）

`config-inventory.md` · `friend-dm-room-chat-sse-plan.md`（Agent3 merge 后删） · `room-mutation-side-effects.md` · `doc-overhaul-plan.md` · `doc-overhaul-changelog-01.md` · **`doc-overhaul-changelog-04.md`**

---

## 实现核对要点（文档已对齐）

| 项 | 结论 |
|----|------|
| 规则思考延时 | **`dp.npc.rule-think` 已上线**（`DpNpcRuleThinkSampler` + `decideActionIfReady`） |
| Shark 策略 | **无** 独立 `SHARK` 分支；`addSharkBot` = TAG |
| Flyway | **V7** 周榜 |
| `@MapperScan` | 含 **`leaderboard.mapper`** |
| `docs/**/*.md` 计数 | **38**（含本 changelog；不含 `front/dp_game/docs`） |

---

## 断链修复（grep 已删名）

- 根 `README.md`：`readme-scan/*` → `docs/README.md` / `ENV_README.md`；`friend-dm-room-chat-sse-plan` → `dp_friend_mailbox_mvp`；Flyway **V7**
- `README.ch.md` / `README.en.md`：学习链 → `docs/notes/*`
- `docs/SpringScheduling.md`：DI 链 → `docs/notes/*`（已指向 notes）
- `docs/README.md`：Shark 行链 → `04_shark_legacy` / `05_shark_modules_legacy`

---

## 待后续 Agent

| 项 | 负责 |
|----|------|
| `docs/README.md` 索引「简介」列全文 | Agent2/3/4 用 index-draft 粘贴 |
| `friend-dm-room-chat-sse-plan.md` merge 后 delete | Agent3 |
| `room-mutation-side-effects.md` merge 或独立维护 | Agent3 |
| `config-inventory.md` rewrite | Agent2 |
