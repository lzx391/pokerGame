# 成就墙「查看回放」后端方案

| 项 | 值 |
|---|---|
| 版本 | 2026-06-10 |
| Flyway | V21 `achievement_hand_history_id` |
| 状态 | 已实现 |

---

## 1. 目标

成就墙已解锁项可跳转至**首次解锁该成就**那一手的牌谱回放；看别人成就墙时展示回放按钮，详情 API 仍做参与者鉴权。

## 2. 非目标

- 不回填历史已解锁记录的 `hand_history_id`（旧数据无 id → 不显示按钮）
- 不改前端 UI 实现（本迭代仅 API 字段）
- 不修改牌谱 detail 鉴权逻辑

## 3. P0 验收

| # | 要点 |
|---|------|
| 1 | 结算持久化：**先** `save` 牌谱拿 `handHistoryId`，**再** `detect(job, handHistoryId)` |
| 2 | 首次解锁写入 `dp_user_achievement.hand_history_id` |
| 3 | 已解锁再次触发：**不 UPDATE** `hand_history_id`（幂等） |
| 4 | 成就墙 API：仅 `unlocked=true` 且 `handHistoryId` 非空时返回 id；否则 null，前端不渲染按钮 |
| 5 | 看别人墙：同样返回 `handHistoryId`；点击 detail 无权则自然 403/失败 |

## 4. 变更摘要

| 层 | 变更 |
|---|---|
| DB | `dp_user_achievement.hand_history_id BIGINT UNSIGNED NULL` + index |
| 持久化 | `DpSettlePersistenceDispatcher#persistOnce` 调序 |
| 解锁 | `unlockIfAbsent(userId, code, handHistoryId)` + `tryUnlock` 首次写入 |
| 查询 | `DpAchievementWallItemVO.handHistoryId` + Mapper CASE 表达式 |

## 5. 回滚

1. 部署上一版本后端（忽略 V21 列不影响旧代码运行；若需干净回滚勿在新版环境继续跑 Flyway）
2. 或在 V21 已应用后：`ALTER TABLE dp_user_achievement DROP INDEX idx_dp_user_achievement_hand_history, DROP COLUMN hand_history_id`（仅紧急手工回滚，**勿改 V21 脚本本身**）
3. 前端忽略 `handHistoryId` 字段即可恢复无回放按钮

## 6. 验证命令

```bash
mvn test -Dtest=DpDetectAchievementImplTest,AchievementNotifyPublisherTest,DpAchievementServiceImplTest,DpSettlePersistenceDispatcherTest
mvn clean package -DskipTests
```
