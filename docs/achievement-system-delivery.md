# 成就系统 P0 交付说明

## 功能概览

- **检测入口**：每手结算异步持久化前，`DpSettlePersistenceDispatcher` 调用 `DpDetectAchievementImpl.detect(job)`。
- **首成就「四条噩梦」**（`quad_nightmare`）：摊牌时某真人玩家最佳牌型为四条(8)，且本局另有摊牌者打出同花顺(9)或皇家同花顺(10)，则为该四条玩家解锁成就（`unlocked` 0→1，已解锁不重复写）。
- **成就墙 API**：登录后可查本人或他人成就列表（全部定义 + 解锁状态）。
- **前端入口**：个人资料「编辑资料」右侧、玩家资料「历史对局」右侧均可打开成就墙弹层。

## 数据库（Flyway V14）

| 表 | 说明 |
|---|---|
| `dp_achievement` | 成就定义：`id, code, title, description, sort_order, created_at` |
| `dp_user_achievement` | 用户解锁：`user_id, achievement_id, unlocked, unlocked_at`；主键 `(user_id, achievement_id)` |

种子数据：`quad_nightmare` / 四条噩梦 / 指摊牌时该玩家四条撞上了更大的同花顺。

## API

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/dpUser/achievements` | 需 JWT | 当前用户成就墙 |
| GET | `/dpUser/achievements/{userId}` | 需 JWT | 他人公开成就墙 |

响应 `data.achievements` 为数组，字段：`id, code, title, description, sortOrder, unlocked, unlockedAt`。

未加入 `JwtSecurityConstants.PERMIT_ALL`（必须登录）。

## 检测逻辑（四条噩梦）

**硬约束：仅摊牌参与者可检测/解锁**；未摊牌或已弃牌玩家不得触发。

**摊牌者定义**：本局 `archived.holeCardsAtEnd` 中有底牌记录（非空列表）的玩家，且 `actions` 中无 `FOLD`（亮牌参与摊牌）。归档时弃牌者仍可能保留底牌副本，须结合弃牌记录排除，不能仅凭 map 键存在判定。

1. 从 `job.archived().boardsByStreet` 取公共牌最多的一街（通常河牌 5 张）；不足 5 张则跳过。
2. 遍历 `holeCardsAtEnd`，用上述规则筛出摊牌者集合。
3. 对每个摊牌者：`evaluateBestHand(底牌 + 最后一街公共牌)`。
4. 若摊牌者集合内无人 `rankCategory >= 9`（同花顺/皇家）**或**无人 `rankCategory == 8`（四条）→ 跳过。
5. 对摊牌者中拿四条的真人玩家，经 `roomSnapshotForParticipants` 映射 `nickname → userId`（跳过 NPC），调用 `unlockIfAbsent`（已解锁不重复写）。

## 前端

| 文件 | 变更 |
|---|---|
| `DpAchievementWallModal.vue` | 成就墙弹层（灰显未解锁 / 高亮已解锁） |
| `HomeProfileModal.vue` | 「成就墙」按钮 → 本人成就 |
| `GamePlayerSocialSheet.vue` | 「成就墙」按钮 → 该玩家成就 |

`el-dialog` 已在 `main.js` 注册，无需新增 Element 组件。

## 构建与验证

```bash
# 后端（含 Flyway V14）
mvn clean package -DskipTests

# 前端
cd front/dp_game && npm run build
```

### 手动验收建议

1. 启动应用，确认 Flyway V14 成功。
2. 登录后打开个人资料 → 成就墙，应看到「四条噩梦」灰显未解锁。
3. 局内点击其他玩家资料 → 成就墙，可查看对方解锁状态。
4. 构造或回放一手「摊牌四条输给同花顺」后，四条玩家再次打开成就墙应已高亮解锁。

## 包结构

```
achievement/
  DpAchievementService.java
  DpDetectAchievement.java
  entity/   DpAchievement, DpUserAchievement
  mapper/   DpAchievementMapper, DpUserAchievementMapper
  impl/     DpAchievementServiceImpl, DpDetectAchievementImpl
  vo/       DpAchievementWallItemVO
controller/DpUserController.java  （成就墙路由）
```
