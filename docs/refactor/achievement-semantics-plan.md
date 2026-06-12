# 成就判定语义修订方案

| 项 | 值 |
|---|---|
| 方案版本 | 2026-06-10 |
| 状态 | **待后端实现**（判定逻辑 + Flyway 文案） |
| 权威来源 | `DpDetectAchievementImpl`、`dp_achievement`（Flyway V14/V15/V17/V19） |
| 关联交付 | [achievement-system-delivery.md](../achievement-system-delivery.md) |
| 并行 Agent | 后端判定实现 Agent（本文档不写实现代码） |

---

## 1. 目标 / 非目标 / P0 验收标准

### 1.1 目标

收紧 6 条成就的**解锁判定语义**，使「27 / 消消乐」等需 **≥6 人参与** 才计入；修正「天灾连追」「灵魂阅读者净赢」「横扫摊牌参与者」等边界；同步 DB 展示文案（含「同花顺 → 火箭」术语），保证成就墙 / SSE Toast 与产品口径一致。

### 1.2 非目标

| 范围 | 说明 |
|------|------|
| 已解锁记录回溯 | 历史 `dp_user_achievement.unlocked=1` **不撤销** |
| 前端成就组件改造 | 文案全来自 API/DB，见 §6 前端审计 |
| 成就墙 UI / SSE 通道 | 协议与组件保持不变 |
| `quad_nightmare`、`draw_insulator` 判定 | P0 **逻辑与文案均不改动** |
| 牌谱 JSON / 牌型评估器 | 展示轨 `DpUtilHandEvaluator` 不变；前端 `dpHandRankDisplay.js` 独立 |

### 1.3 P0 验收标准（8 项成就）

| # | code | 改动类型 | P0 验收要点 |
|---|------|----------|-------------|
| 1 | `quad_nightmare` | **未动** | 维持现状：摊牌四条撞上更大火箭（cat≥9） |
| 2 | `twenty_seven_terminator` | **判定收紧** | 5 人场杂色 72 净赢**不解锁**；**≥6 人**参与本手且杂色 72 净赢**解锁** |
| 3 | `throne_usurper` | **文案 only** | 判定不变（更大火箭击败对手火箭）；DB/UI 描述用「火箭 / 超级火箭」，不用「同花顺」 |
| 4 | `table_clear` | **判定收紧** | 2 人场对手弃牌赢池**不解锁**；**≥6 人**且全员弃牌仅赢家未弃**解锁** |
| 5 | `draw_insulator` | **未动** | 维持现状：花顺双抽河牌未成顺/同花或更强 |
| 6 | `natural_disaster` | **判定细化** | 翻后 hero **两对及以上** vs 对手**高牌**领先；转牌仍不输；河牌对手**连追**成花/顺/更高并反超 hero 输池 → 受害者解锁 |
| 7 | `soul_reader` | **判定收紧** | 须**摊牌净赢**（`netChipsChange > 0`）；三人摊牌中间名次击败诈唬但**输池不解锁** |
| 8 | `sweep_all` | **判定收紧** | 仅统计**摊牌参与者**终局筹码；未摊牌/已弃牌者筹码归零**不计入**「对手清零」 |

**PM 逐条验收表（与上表 code 对齐）：**

| code | 验收要点 |
|------|----------|
| `twenty_seven_terminator` | 5 人场杂色 72 赢局不解锁；≥6 人解锁 |
| `throne_usurper` | 判定不变；DB/UI 文案为火箭术语 |
| `table_clear` | 2 人场对手弃牌不解锁；≥6 人全弃解锁 |
| `natural_disaster` | 翻后 2 对+ vs 高牌，转河连追成花/顺/更高后反超 |
| `soul_reader` | 三人摊牌中间名次击败诈唬但输池不解锁 |
| `sweep_all` | 仅摊牌参与者筹码归零计入 |

---

## 2. 判定口径表（目标语义）

> **人数口径占位**：本手「参与人数」以 `archived.seatsAtStart` 中非空昵称席位数为准，还是含 NPC、是否排除离座者等——**待后端变更说明对齐**。下文「≥6 人」均指与后端实现一致的同一计数函数。

### 2.1 `twenty_seven_terminator`（27 终结者）

| 维度 | 现行（代码现状） | 目标 |
|------|------------------|------|
| 手牌 | 杂色 2+7，顺序无关 | 不变 |
| 结果 | 净赢 `net > 0` | 不变 |
| 人数 | **无下限** | **≥6 人**参与本手 |
| 文案 | 「以 2-7 杂色手牌赢下一局」 | 可补充「至少六人桌」 |

### 2.2 `throne_usurper`（王座更迭）

| 维度 | 现行 | 目标 |
|------|------|------|
| 判定 | 摊牌赢家火箭(cat≥9) 击败另一名摊牌火箭对手 | **不变** |
| 文案 | 「更大的同花顺击败对手的同花顺」 | 「用更大的**火箭**击败对手的**火箭**」（超级火箭表述可选） |

### 2.3 `table_clear`（牌桌消消乐 / 消消乐）

| 维度 | 现行 | 目标 |
|------|------|------|
| 赢法 | 净赢且所有对手已弃牌（无摊牌对手） | 不变 |
| 人数 | `netChipsChange.size() >= 2` 即可 | **≥6 人**参与本手 |
| 文案 | 「一场对局内迫使所有其他玩家弃牌」 | 可补充「至少六人桌」 |

### 2.4 `natural_disaster`（天灾）

| 维度 | 现行 | 目标 |
|------|------|------|
| 翻牌 | hero 领先对手（任意牌力差） | hero **两对及以上**（`rankCategory ≥ 3` 或成牌分类 ≥ BOTTOM_TWO_PAIR）且对手为**高牌**（未成对） |
| 转牌 | hero ≥ 对手 | 不变 |
| 河牌 | 对手 > hero，hero 输池 | 不变；对手河牌成牌须为**连追**（转牌未领先、河牌成花/顺/更高） |
| 解锁对象 | 输池受害者（真人） | 不变 |

**待后端变更说明对齐**：成牌分类与 `rankCategory` 阈值、「连追」是否要求转牌仍为高牌/听牌未完成等细节。

### 2.5 `soul_reader`（灵魂阅读者）

| 维度 | 现行 | 目标 |
|------|------|------|
| 成牌 | 高牌或底对 | 不变 |
| 对手 | 转/河有 BET/RAISE/ALL_IN 且摊牌牌力弱于 hero | 不变 |
| 结果 | 净赢 `net > 0`（代码已有） | **强调**：三人池中间名次「读对但输池」**不得**解锁 |
| 文案 | 「以高牌或底对摊牌击败对手的诈唬」 | 可补充「并赢下底池」 |

### 2.6 `sweep_all`（横扫一切）

| 维度 | 现行 | 目标 |
|------|------|------|
| 前提 | ≥2 名摊牌参与者 | 不变 |
| 清零范围 | `showdownParticipants` 内对手 `chipsAtEnd == 0` | **不变**（现行已仅扫摊牌者）；需回归：弃牌早退者筹码为 0 **不计** |
| 文案 | 「所有对手筹码归零」 | 「所有**摊牌对手**筹码归零」 |

### 2.7 未改动：`quad_nightmare` / `draw_insulator`

维持 V14/V17 种子语义与现有 `DpDetectAchievementImpl` 行为；Flyway **不 UPDATE** 这两条的 `title`/`description`（除非产品单独立项改文案）。

---

## 3. 数据库与 Flyway

### 3.1 新迁移（预期 V20）

- **仅 `UPDATE dp_achievement SET title=…, description=… WHERE code=…`**，不 `INSERT` 新 code、不改表结构。
- 重点 UPDATE：`throne_usurper`（火箭术语）、可选补充 `twenty_seven_terminator` / `table_clear` / `soul_reader` / `sweep_all` / `natural_disaster` 描述与 §2 一致。
- **禁止修改** V14/V15/V17/V19 已应用脚本（Flyway 校验和）。

### 3.2 文案草案（供 V20 参考，以后端 PR 为准）

| code | description（草案） |
|------|---------------------|
| `throne_usurper` | 摊牌时用更大的火箭击败对手的火箭 |
| `twenty_seven_terminator` | 至少六人桌，以杂色 2-7 手牌净赢一局 |
| `table_clear` | 至少六人桌，迫使所有其他玩家弃牌并赢下底池 |
| `natural_disaster` | 翻牌两对以上领先对手高牌，转牌仍不输，却被对手转河连追成花、顺或更大牌型反超 |
| `soul_reader` | 摊牌净赢，以高牌或底对抓下对手转河诈唬 |
| `sweep_all` | 摊牌赢下底池，且所有摊牌对手筹码归零 |

---

## 4. 风险

| 风险 | 说明 | 缓解 |
|------|------|------|
| **旧成就不回溯** | 修订前误触发的解锁保持 `unlocked=1` | PM 知悉；必要时运营公告，不做批量 DELETE |
| **Flyway 仅 UPDATE** | 新环境仍跑旧 INSERT 再 V20 UPDATE；勿改旧脚本 | 评审 V20 仅 UPDATE |
| **人数口径不一致** | 文档写 ≥6，实现若用 `netChipsChange.size()` 或含 NPC 会偏差 | **待后端变更说明对齐**；实现后本文档 §5 补全 |
| **天灾边界复杂** | 翻牌「2 对+ vs 高牌」与边缘成牌（底两对、公共牌两对） | 以后端单测场景为验收依据 |
| **SSE/墙缓存** | 仅改 DB 文案后，已打开成就墙需刷新或等 revision | 现有 SSE `achievement_unlocked` 带实时 title/description，无额外风险 |

---

## 5. 实现确认

> **已确认**（2026-06-10）：后端判定、`V20` 文案迁移与单测均已落地；前端仍无需改（§7）。

| 项 | 状态 | 备注 |
|----|------|------|
| ≥6 人计数函数与字段 | ☑ 已确认 | `DpDetectAchievementImpl` 以 `DpObservedHandRecordBO#seatsAtStart` 人数为准，阈值常量 `MIN_PARTICIPANTS_FOR_SIX_MAX = 6`（盲注后、行动前在桌座位） |
| `twenty_seven_terminator` 5 人否 / 6 人是 | ☑ 已测 | `skipsTwentySevenTerminatorBelowSixPlayers` / `unlocksWhenWinnerHasOffsuitTwoSevenOnSixMax` |
| `table_clear` 2 人否 / 6 人全弃是 | ☑ 已测 | `skipsTableClearBelowSixPlayers` / `unlocksTableClearWhenAllOpponentsFoldOnSixMax` |
| `natural_disaster` 2 对+ vs 高牌 + 转河连追 | ☑ 已测 | `unlocksNaturalDisasterWhenTurnRiverStraightComeback`；负例含转牌单街反超、翻牌非两对+ |
| `soul_reader` 三人中间名次输池否 | ☑ 已测 | `skipsSoulReaderWhenNotShowdownWinner`（非净赢家不解锁） |
| `sweep_all` 弃牌者清零不计 | ☑ 已测 | `unlocksSweepAllWhenFoldedPlayerStillHasChips`（早退弃牌者筹码归零不计入） |
| Flyway V20 文案 UPDATE | ☑ 已合并 | `V20__achievement_semantics_update.sql` 已 UPDATE 6 条 `description`（`throne_usurper` 等） |
| `mvn test -Dtest=DpDetectAchievementImplTest` | ☑ 全绿 | 22 项用例全部通过 |
| 前端成就组件 | ☑ 无需改 | 文案仍由 API/DB + SSE 驱动，见 §7 |

---

## 6. 回滚要点

1. **代码**：回退 `DpDetectAchievementImpl` 至修订前 commit；重启服务即可恢复旧判定（已写入的 `dp_user_achievement` 不回滚）。
2. **Flyway**：V20 若已应用，需新迁移 V21 将 `description`/`title` 改回旧文案（或接受新文案仅展示、逻辑已回退的不一致——优先回退代码 + V21 文案）。
3. **前端**：无代码依赖，无需回滚前端构建。
4. **验收**：回滚后复跑 P0 负例（5 人 27、2 人消消乐）应恢复为「可解锁」的旧行为。

---

## 7. 前端审计结论

**结论：成就 title/description 前端无需改**（API + SSE 驱动）。

### 7.1 核对过的前端文件清单

| 文件 | 结论 |
|------|------|
| `front/dp_game/src/components/DpAchievementWallModal.vue` | `item.title` / `item.description` 来自 `GET /dpUser/achievements` |
| `front/dp_game/src/components/DpAchievementWallCrt.vue` | 同上 |
| `front/dp_game/src/store/modules/dpAchievement.js` | SSE 解锁写入 `title`/`description` |
| `front/dp_game/src/utils/dpSocialStream.js` | `parseAchievementUnlockPayload` 解析后端字段 |
| `front/dp_game/src/components/DpAchievementToast.vue` | 仅展示 props；固定标签「成就解锁！」 |
| `front/dp_game/src/components/DpAchievementToastHost.vue` | 从 store 透传 title/description |
| `front/dp_game/src/utils/dpAchievementFormat.js` | 仅解锁时间格式化 |
| `front/dp_game/src/components/HomeProfileModal.vue` | 入口文案「成就墙」，非成就定义 |
| `front/dp_game/src/components/GamePlayerSocialSheet.vue` | 同上 |
| `front/dp_game/src/utils/dpSocialStreamClient.js` | SSE 事件监听，无硬编码成就名 |
| `front/dp_game/src/App.vue` | 挂载 ToastHost |

**未发现**硬编码成就 `code` 或 `title`/`description`（如「四条噩梦」「27终结者」等）。

### 7.2 牌型展示（非成就域）

| 文件 | 说明 |
|------|------|
| `front/dp_game/src/utils/dpHandRankDisplay.js` | 牌谱/结算展示：`同花顺`→`火箭`，`皇家同花顺`→`超级火箭`；与成就 DB 文案独立 |

若 DB 已 UPDATE 为火箭术语，成就墙自动展示新文案；**无需**为成就单独改前端。

---

## 8. 相关文件索引

| 类型 | 路径 |
|------|------|
| 检测入口 | `room/support/DpSettlePersistenceDispatcher` → `DpDetectAchievementImpl#detect` |
| 单测 | `src/test/java/.../DpDetectAchievementImplTest.java` |
| 成就 API | `controller/DpUserController` `GET /dpUser/achievements` |
| SSE | `social/notify/AchievementUnlockNotifyPayload` |
| 迁移 | `V14__achievement.sql`、`V15__*`、`V17__*`、`V19__*`、（预期）`V20__*` |
