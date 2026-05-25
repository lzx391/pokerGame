# 牌谱持久化与 Shark 表

> **核对日期**：2026-05-25  
> **权威来源**：`DpHandHistoryPersistServiceImpl`、`DpHandHistoryObservedImpl`（`history` 包）、Flyway V1/V4+  
> **Status**: maintained

---

## 1. 三类持久化

| 数据 | 表 | 依赖 Shark |
|------|-----|------------|
| 完整牌谱 JSON | `dp_observed_hand_history` | **否** |
| 真人参与者 | `dp_observed_hand_participant` | **否**（跳过 Bot 昵称） |
| Shark 对手画像 | `dp_shark_opponent_profile` | **是**（桌上 `BOT_Shark` 等；服务层可能已不再写，表仍在 V1） |

查询 API：`DpHandHistoryController`（`/dpHandHistory`）；`userId` 须为参与者 id（服务端未强制等于 JWT 用户，客户端勿传他人 id）。

---

## 2. 写入时机

`DpRoomServiceImpl#autoSettle` 在分配筹码后：

1. `DpHandHistoryObservedImpl#finalizeHand` 构建 `DpObservedHandRecordBO`  
2. `DpSettlePersistenceDispatcher` 异步调用 `DpHandHistoryPersistServiceImpl#save`  
3. `INSERT dp_observed_hand_history`（`payload_json`，版本字段）→ `insertParticipants`  

**失败**：仅 warn 日志，**不中断对局**。  
配置：`mgdemoplus.settle-persist.async-enabled`、`settle-persist.queue-capacity`。

唯一键：`UNIQUE (room_id, hand_seed)`。

---

## 3. 内存观测（与 Shark 无关）

`DpHandHistoryObservedImpl`：只要房内有玩家即记录盲注、公共牌、行动、池结构、摊牌洞牌与 `netChipsChange`。  
与 `DpNpcSharkHandActionLog`（仅 Shark 逐街日志）分离。

---

## 4. Flyway

| 版本 | 内容 |
|------|------|
| V1 | `dp_observed_hand_history`、`dp_observed_hand_participant` |
| V4 | `dp_user_stats` 等统计 |
| V5/V6 | 单房净赢/单局倍数 |
| V7 | `dp_leaderboard_weekly`（周榜 REST，见 `DpLeaderboardController`） |

**勿修改**已应用的旧迁移脚本；新表用 `V8__*.sql`。

---

## 5. 延伸阅读

- 结算链入口：[DPGAME.md](./DPGAME.md) §3.3  
- Redis 不含 roomMap：[Redis.md](./Redis.md)
