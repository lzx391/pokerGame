# 成就系统交付说明

> **核对日期**：2026-06-10  
> **Flyway**：V14（表 + `quad_nightmare`）→ V15（`twenty_seven_terminator`）→ V17（5 条新成就）→ V19（`sweep_all`）  
> **语义修订方案**（P0 进行中）：[refactor/achievement-semantics-plan.md](refactor/achievement-semantics-plan.md)

---

## 功能概览

- **检测入口**：每手结算异步持久化前，`DpSettlePersistenceDispatcher` 调用 `DpDetectAchievementImpl.detect(job)`。
- **成就数量**：共 **8** 条（见下表）；仅真人玩家可解锁（NPC 跳过）；`unlockIfAbsent` 已解锁不重复写。
- **成就墙 API**：登录后可查本人或他人成就列表（全部定义 + 解锁状态）。
- **实时通知**：SSE 事件 `achievement_unlocked`，载荷含 `code, title, description`（与 DB 一致）。
- **前端入口**：个人资料 / 玩家资料「成就墙」→ `DpAchievementWallModal` 或 `DpAchievementWallCrt`；局内解锁 Toast。

---

## 成就一览（8 项）

| sort | code | 标题 | 说明（DB 种子 / 待 V20 修订） | P0 语义修订 |
|------|------|------|------------------------------|-------------|
| 1 | `quad_nightmare` | 四条噩梦 | 摊牌时该玩家四条撞上了更大的同花顺 | **未动** |
| 2 | `twenty_seven_terminator` | 27终结者 | 以 2-7 杂色手牌赢下一局 | **≥6 人**才解锁 |
| 3 | `throne_usurper` | 王座更迭 | 摊牌时用更大的同花顺击败对手的同花顺 | 判定不变；**文案改火箭** |
| 4 | `table_clear` | 牌桌消消乐 | 一场对局内迫使所有其他玩家弃牌 | **≥6 人**全弃才解锁 |
| 5 | `draw_insulator` | 听牌绝缘体 | 翻后曾花顺双抽，但河牌一张听牌都未命中 | **未动** |
| 6 | `natural_disaster` | 天灾 | 翻牌领先却被对手转牌与河牌连续反超 | **2 对+ vs 高牌 + 转河连追** |
| 7 | `soul_reader` | 灵魂阅读者 | 以高牌或底对摊牌击败对手的诈唬 | **须净赢底池** |
| 8 | `sweep_all` | 横扫一切 | 摊牌时赢下底池，且所有对手筹码归零 | **仅摊牌对手清零** |

详细判定口径与 PM 验收表见 [achievement-semantics-plan.md](refactor/achievement-semantics-plan.md)。

---

## 数据库

| 表 | 说明 |
|---|---|
| `dp_achievement` | 成就定义：`id, code, title, description, sort_order, created_at` |
| `dp_user_achievement` | 用户解锁：`user_id, achievement_id, unlocked, unlocked_at`；主键 `(user_id, achievement_id)` |

迁移脚本：

| 版本 | 内容 |
|------|------|
| V14 | 建表 + `quad_nightmare` |
| V15 | `twenty_seven_terminator` |
| V17 | `throne_usurper`, `table_clear`, `draw_insulator`, `natural_disaster`, `soul_reader` |
| V19 | `sweep_all` |
| V20（预期） | 仅 `UPDATE` 文案；判定修订无 DDL |

---

## API

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dpUser/achievements` | 需 JWT | 当前用户成就墙 |
| GET | `/dpUser/achievements/{userId}` | 需 JWT | 他人公开成就墙 |

响应 `data.achievements` 为数组，字段：`id, code, title, description, sortOrder, unlocked, unlockedAt`。

未加入 `JwtSecurityConstants.PERMIT_ALL`（必须登录）。

---

## 检测逻辑摘要

**公共约束**

- 仅**真人**玩家（`DpNpcEngine.isBotPlayer` 为 false 且 `dpUserId > 0`）可解锁。
- **摊牌参与者**：`holeCardsAtEnd` 底牌非空且本局 actions 无 `FOLD`；不能仅凭 map 键存在判定。

**分成就要点**（现行代码；P0 修订后以后端 PR + 单测为准）

| code | 要点 |
|------|------|
| `quad_nightmare` | 摊牌者中有人 cat≥9（火箭）且有人 cat=8（四条）→ 四条真人解锁 |
| `twenty_seven_terminator` | 杂色 2+7 且净赢；**P0 加 ≥6 人** |
| `throne_usurper` | 摊牌净赢；hero 与至少一对手均为 cat≥9 且 hero 更大 |
| `table_clear` | 净赢且所有对手已弃牌；**P0 加 ≥6 人** |
| `draw_insulator` | 翻/转花顺双抽，河牌 cat&lt;5 |
| `natural_disaster` | 翻牌领先、转牌不输、河牌被摊牌对手反超且 hero 输池；**P0 细化成牌门槛** |
| `soul_reader` | 摊牌净赢；hero 高牌/底对；对手转/河有激进行动且牌力弱于 hero |
| `sweep_all` | ≥2 摊牌者；赢家净赢；其余**摊牌**对手 `chipsAtEnd==0` |

单测类：`DpDetectAchievementImplTest`。

---

## 前端

| 文件 | 职责 |
|---|---|
| `DpAchievementWallModal.vue` | 成就墙弹层（API 驱动 title/description） |
| `DpAchievementWallCrt.vue` | 复古 CRT 成就墙（同上） |
| `DpAchievementToastHost.vue` / `DpAchievementToast.vue` | SSE 解锁 Toast |
| `store/modules/dpAchievement.js` | 解锁 revision + Toast 队列 |
| `HomeProfileModal.vue` / `GamePlayerSocialSheet.vue` | 「成就墙」入口 |

**P0 语义修订：前端无需改**（无硬编码成就文案；见 plan §7）。

`el-dialog` 已在 `main.js` 注册。

---

## 构建与验证

```bash
# 后端（含 Flyway）
mvn clean package -DskipTests

# 成就单测
mvn test -Dtest=DpDetectAchievementImplTest

# 前端
cd front/dp_game && npm run build
```

### 手动验收建议

1. 启动应用，确认 Flyway V14～V19 成功（V20 合并后一并确认）。
2. 登录 → 个人资料 → 成就墙：应见 **8** 条，未解锁灰显。
3. 局内点击他人资料 → 成就墙，可查看对方解锁状态。
4. 按 [achievement-semantics-plan.md](refactor/achievement-semantics-plan.md) PM 表逐条构造或回放场景验收 P0 六项修订 + 两项未动。

---

## 包结构

```
achievement/
  DpAchievementService.java
  DpDetectAchievement.java
  entity/   DpAchievement, DpUserAchievement
  mapper/   DpAchievementMapper, DpUserAchievementMapper
  impl/     DpAchievementServiceImpl, DpDetectAchievementImpl
  vo/       DpAchievementWallItemVO
  notify/   AchievementNotifyPublisher
controller/DpUserController.java  （成就墙路由）
social/notify/AchievementUnlockNotifyPayload.java
```
