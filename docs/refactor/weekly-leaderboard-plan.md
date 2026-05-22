# 周榜排行榜 — 实现方案（P0）

> **文档性质**：只读方案，供后端 / 前端实现 Agent 开工前必读。  
> **禁止**：本任务内 `git commit` / `push`；实现方勿在未确认前改业务代码。  
> **对齐来源**：产品已确认需求表（§0）；倍数公式与现网 `DpRoomServiceImpl` 一致。

---

## 变更说明（本次交付）

| 项 | 内容 |
|----|------|
| 新增文件 | `docs/refactor/weekly-leaderboard-plan.md`（本文件） |
| 修改代码 | 无 |
| Flyway 下一版 | `V7__leaderboard_weekly.sql`（序号在 V6 之后） |

### 前端交付（大厅周榜 UI）

| 项 | 内容 |
|----|------|
| 新增 | `front/dp_game/src/api/api.dpLeaderboard.js` |
| 新增 | `front/dp_game/src/components/LeaderboardPage.vue` |
| 修改 | `home.vue`（快捷入口「排行榜」）、`router/index.js`（`/leaderboard`）、`App.vue`、`utils/dpBodyGameTheme.js` |
| API | `GET /dp/leaderboard/weekly/hand`、`GET /dp/leaderboard/weekly/room`（limit=50） |
| 验收 | 未登录可打开 Top50；登录底栏显示 myRank/myMultiplier；两 Tab 路径与后端一致；`npm run build` 通过 |

---

## 0. 已确认需求（勿改语义）

| # | 决策 | 落地含义 |
|---|------|----------|
| 1 | 两榜 | **单局倍数** `best_hand_multiplier`、**单房间倍数** `best_room_multiplier` |
| 2 | 公式与现网一致 | 单局 = 本手净赢 / `startingChips`；单房 = (离场筹码 − 累计带入) / `startingChips`（见 `DpRoomServiceImpl#computeHandNetWinMultiplier` / `#computeRoomNetWinMultiplier`） |
| 3 | 周期 | 每周一 **0:00** `Asia/Shanghai`（`mgdemoplus.time-zone` / `AppTimeZoneConfig`）换周 |
| 4 | 可见性 | 所有人可见；REST 走 `JwtSecurityConstants.PERMIT_ALL` |
| 5 | 展示 | Top **50**；竞赛排名 **1,1,3**（同分同 rank，下一名跳号） |
| 6 | 同分 | 破同分「达成先后」**P1 不做**（P0 同分仅共享 rank，ZSET 内次序不参与名次） |
| 7 | 房间 | **密码房计入**；无 `userId` 的机器人 **不进榜** |
| 8 | 前端范围 | **仅大厅 UI**（`home`）；不改个人资料表/页 |
| 9 | 读写分离 | 对局事件 **只写 MySQL 周表**；**读榜只读 Redis** |
| 10 | 同步策略 **A** | 仅 `@Scheduled` 每 **60s** 从 MySQL 刷入 Redis（**不在写库时 ZADD**）；文案「榜单约每分钟更新」 |
| 11 | 生涯并行 | `dp_user_stats.largest_pot_won` / `largest_room_net` **继续维护**，与周榜并行 |

---

## 开始前必读

### 后端 Agent

1. 先读 **§1～§8** 与文末 **必读源码路径**；倍数计算 **复用** `DpRoomServiceImpl` 私有方法逻辑（可抽 `DpLeaderboardMultiplierUtil` 包内静态方法，避免复制公式漂移）。
2. 写库挂钩点：**§3** 两处；方法名以仓库为准：`recordPlayerHonorStatsAfterSettle`（文档曾称 `recordPlayerHonorStatsAfterHand`）、`settleAndClearCarryInOnLeaveSeatLocked`。
3. **禁止**在写 MySQL 周表时 `ZADD`；Redis 仅由 `DpLeaderboardWeeklyRedisSyncScheduler` 写入。
4. 读 API **禁止**直查 MySQL 拼榜（降级见 §8）；`myRank` 用 `ZREVRANK`。
5. Flyway **只新增 V7**，勿改 V1～V6。
6. 新接口加入 `JwtSecurityConstants.PERMIT_ALL`（与 `/dpRoom/getAllRooms2` 同级）。

### 前端 Agent

1. 仅改大厅：`home.vue` 入口 + 新路由/页面（建议 `/leaderboard`）；**不动**个人资料相关组件/API。
2. 榜单请求 **可不登录**；有 `localStorage.userInfo.token` 时 axios 仍会带 Bearer（`main.js` 拦截器），用于 `myRank` / `myMultiplier`。
3. 展示产品文案：**「榜单约每分钟更新」**（与 60s 同步一致）。
4. 名次 **不要**在前端重算竞赛排名；用后端返回的 `rank`。
5. 401 拦截器：permitAll 接口不应返回 401；若调试期误配白名单会导致未登录用户被踢回登录页，需与后端联调确认。

---

## 1. 背景与名词

### 1.1 倍数是什么（大白话）

牌局用 **初始筹码 `startingChips`**（创建房间时设定）当「1 倍」的尺子：

| 名词 | 含义 | 何时产生 |
|------|------|----------|
| **单局倍数** | 这一手你净赢了多少个「初始筹码」 | 每手结算后，仅 **净赢 > 0** 时才算 |
| **单房间倍数** | 你在房间里 **一整段带入** 净赢了多少个「初始筹码」 | **离座 / 退房** 结算带入段时，仅 **净赢 > 0** 时才算 |

公式（与现网代码一致，保留两位小数 `HALF_UP`）：

```
单局倍数 = netWonChips / startingChips
         其中 netWon = 结算后筹码 − 本手开始前筹码 − 本手总下注

单房倍数 = (离场筹码 − 累计带入) / startingChips
```

实现参考：

```1966:1981:src/main/java/com/example/mgdemoplus/room/impl/DpRoomServiceImpl.java
    /** 单房间净赢倍数 = (离场筹码 − 累计买入) / 初始积分，保留两位小数。 */
    private static BigDecimal computeRoomNetWinMultiplier(int chips, int totalCarryIn, int initialChips) { ... }

    /** 单局净赢倍数 = 本手净赢筹码 / 初始积分，保留两位小数。 */
    private static BigDecimal computeHandNetWinMultiplier(int netWonChips, int initialChips) { ... }
```

### 1.2 周榜 vs 生涯（`dp_user_stats`）

| 维度 | 周榜 `dp_leaderboard_weekly` | 生涯 `dp_user_stats` |
|------|------------------------------|----------------------|
| 周期 | 自然周（周一 0:00 上海时区起算） | 永久 |
| 字段 | `best_hand_multiplier` / `best_room_multiplier` | `largest_pot_won` / `largest_room_net` |
| 语义 | 本周内各自 **最高** 倍数 | 历史 **最高** 倍数 |
| 写入口 | 周表 UPSERT + **现有** 生涯 Mapper 不变 | 已实现：`upsertAfterHand` / `tryUpdateLargestRoomNet` |
| 读入口 | **Redis ZSET**（API 不直读表） | 个人资料等（本次不改 UI） |

两轨 **并行维护**：在周榜写库旁路调用之外，**不要删** `recordPlayerHonorStatsAfterSettle` / `settleAndClearCarryInOnLeaveSeatLocked` 里对 `DpUserStatsMapper` 的现有调用。

### 1.3 谁进榜

- **真人**：`DpPlayer#getDpUserId() != null` 且非 `DpNpcEngine.isBotPlayer(p)`。
- **机器人**：无 `userId`，跳过。
- **密码房 / 公开房**：不做过滤，只要产生有效倍数即计入。

---

## 2. 表设计 — Flyway `V7__leaderboard_weekly.sql`

### 2.1 表 `dp_leaderboard_weekly`

| 列 | 类型 | 说明 |
|----|------|------|
| `week_monday` | `DATE NOT NULL` | 本周周一日期（上海时区日历），PK 之一 |
| `user_id` | `INT NOT NULL` | `dp_user.id`，PK 之一 |
| `best_hand_multiplier` | `DECIMAL(10,2) NOT NULL DEFAULT 0` | 本周单局最高倍数 |
| `best_room_multiplier` | `DECIMAL(10,2) NOT NULL DEFAULT 0` | 本周单房最高倍数 |
| `updated_at` | `TIMESTAMP` | 行最后更新时间 |

```sql
-- V7__leaderboard_weekly.sql（示意，实现时补全 COMMENT / ENGINE）
CREATE TABLE dp_leaderboard_weekly (
    week_monday            DATE           NOT NULL COMMENT '本周周一(Asia/Shanghai)',
    user_id                INT            NOT NULL COMMENT 'dp_user.id',
    best_hand_multiplier   DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    best_room_multiplier   DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    updated_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (week_monday, user_id),
    KEY idx_week_hand (week_monday, best_hand_multiplier DESC),
    KEY idx_week_room (week_monday, best_room_multiplier DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='周榜最高分（写库源；读榜走 Redis）';
```

### 2.2 周键 `week_monday` 计算

- 使用 `ZoneId.of(mgdemoplus.time-zone)`（默认 `Asia/Shanghai`），与 `AppTimeZoneConfig` 一致。
- `week_monday = LocalDate.now(zone).with(PreviousOrSame(MONDAY))`。
- 周一 0:00 之后的新对局写入 **新的** `week_monday` 行；无需删旧周数据（历史周保留，Redis TTL 自然过期）。

### 2.3 UPSERT 语义

**单局**（每手一条路径，仅 `handMultiplier > 0`）：

```sql
INSERT INTO dp_leaderboard_weekly (week_monday, user_id, best_hand_multiplier, best_room_multiplier)
VALUES (#{weekMonday}, #{userId}, #{handMultiplier}, 0)
ON DUPLICATE KEY UPDATE
  best_hand_multiplier = GREATEST(best_hand_multiplier, VALUES(best_hand_multiplier));
```

**单房**（离座/退房，仅 `roomMultiplier > 0`）：

```sql
INSERT ... VALUES (#{weekMonday}, #{userId}, 0, #{roomMultiplier})
ON DUPLICATE KEY UPDATE
  best_room_multiplier = GREATEST(best_room_multiplier, VALUES(best_room_multiplier));
```

- 同一用户同一周 **一行** 两列分别取 max。
- **0 或负数** 不触发写库（与生涯逻辑一致）。

### 2.4 Mapper 建议

- 包：`com.example.mgdemoplus.leaderboard.mapper.DpLeaderboardWeeklyMapper`
- 方法：`upsertBestHand(...)`、`upsertBestRoom(...)`、`selectAllForWeek(weekMonday)`（供同步任务全量拉取）

---

## 3. 写入口（MySQL 旁路）

在 `DpRoomServiceImpl` 内 **紧邻** 现有生涯写库之后增加周榜调用（同一守卫条件：有 `dpUserId`、非 Bot、倍数 > 0）。

### 3.1 单局 — `recordPlayerHonorStatsAfterSettle`

调用链：`autoSettle` → 牌谱 `archived != null` → `recordPlayerHonorStatsAfterSettle`。

```2677:2705:src/main/java/com/example/mgdemoplus/room/impl/DpRoomServiceImpl.java
        for (DpPlayer p : r.getPlayers()) {
            if (p.getDpUserId() == null) {
                continue;
            }
            ...
            if (netWon > 0) {
                handMultiplier = computeHandNetWinMultiplier(netWon, r.getStartingChips());
                ...
            }
            dpUserStatsMapper.upsertAfterHand(userId, royalInc, straightInc, fourInc, handMultiplier);
        }
```

**旁路**：在 `upsertAfterHand` 之后，若 `handMultiplier.signum() > 0`，调用 `dpLeaderboardWeeklyService.recordHandBest(userId, handMultiplier, clock)`。

### 3.2 单房 — `settleAndClearCarryInOnLeaveSeatLocked`

```1947:1963:src/main/java/com/example/mgdemoplus/room/impl/DpRoomServiceImpl.java
        BigDecimal multiplier = computeRoomNetWinMultiplier(p.getChips(), totalCarryIn, initialChips);
        if (multiplier.compareTo(BigDecimal.ZERO) > 0 && p.getDpUserId() != null) {
            dpUserStatsMapper.tryUpdateLargestRoomNet(p.getDpUserId(), multiplier);
        }
```

**旁路**：在 `tryUpdateLargestRoomNet` 之后调用 `recordRoomBest(userId, multiplier, clock)`。

触发场景包括：主动离座、被踢、退房时桌上仍有该玩家等（凡走 `settleAndClearCarryInOnLeaveSeatLocked` 的路径）。

### 3.3 事务与失败

- P0：与现有 honor 写库相同 — **单条 UPSERT**，失败记 `warn` 日志，**不阻断** 对局主流程。
- 写库路径 **不** 触碰 Redis。

### 3.4 依赖注入

`DpRoomServiceImpl` 构造器注入 `DpLeaderboardWeeklyService`（或 Mapper，由 Service 封装周一切换）。

---

## 4. Redis 设计

### 4.1 Key 命名

| 榜 | Key |
|----|-----|
| 单局 | `lb:w:{weekMondayyyyyMMdd}:hand` |
| 单房 | `lb:w:{weekMondayyyyyMMdd}:room` |

- `{weekMondayyyyyMMdd}`：`week_monday` 格式化为 `yyyyMMdd`（如 `20260519`）。
- 前缀 `lb:w:` 表示 leaderboard weekly。

### 4.2 ZSET 结构

- **member**：`String.valueOf(userId)`
- **score**：`multiplier × 100` 取整（`long` 或 `double` 存 Redis；12.34 → `1234`）
  - 与 DB `DECIMAL(10,2)` 对齐；同步时用 `BigDecimal.movePointRight(2).longValue()`。
- **排序**：`ZREVRANGE` 取 Top；分数越高名次越前。

### 4.3 TTL 与周切换

- 每次同步 `EXPIRE key 1209600`（**14 天** = 14 × 86400）。
- 新周自动使用新 key；旧周 key 到期删除，无需主动 `DEL` 上周（可选 P1：周一 0:05 清理任务）。

### 4.4 同步范围

- 从 MySQL `selectAllForWeek(currentWeekMonday)` 拉取 **本周所有** `user_id` 行（两列可分别过滤 `> 0`）。
- **全量重建** 当前周 key（见 §5），保证 `ZREVRANK` 对未进 Top50 的用户仍有效。

### 4.5 实现参考

`StringRedisTemplate` 用法对齐 `DpRedisListCacheServiceImpl`：try/catch + `log.warn`，Redis 异常不抛到调度器外。

---

## 5. 定时任务 — `DpLeaderboardWeeklyRedisSyncScheduler`

### 5.1 职责

每 **60s** 将 **当前周** MySQL 数据刷入两个 ZSET（策略 **A**：唯一写 Redis 入口）。

### 5.2 调度配置（对齐大厅 reconcile 范例）

参考 `DpRoomLobbyReconcileScheduler`：

```46:50:src/main/java/com/example/mgdemoplus/lobby/DpRoomLobbyReconcileScheduler.java
    @Scheduled(
            fixedDelayString = "${mgdemoplus.dp-lobby-reconcile-ms:60000}",
            initialDelayString = "${mgdemoplus.dp-lobby-reconcile-ms:60000}")
```

建议新增配置项（可复用同一 key 或独立）：

```properties
mgdemoplus.leaderboard-redis-sync-ms=60000
```

```java
@Scheduled(
    fixedDelayString = "${mgdemoplus.leaderboard-redis-sync-ms:60000}",
    initialDelayString = "${mgdemoplus.leaderboard-redis-sync-ms:60000}")
public void syncCurrentWeekToRedis() { ... }
```

- `fixedDelay`：上次 **结束后** 再等 60s（避免重叠长跑）。
- `initialDelay`：启动后 **首次** 也延迟 60s（与 reconcile 一致；避免启动瞬间 Redis 空窗可通过 §8 文案兜底）。

可选：`ApplicationRunner` 启动时 **不** 同步（坚持策略 A 仅定时器写 Redis）。

### 5.3 单次 sync 算法（当前周）

```
week = resolveCurrentWeekMonday()
rows = mapper.selectAllForWeek(week)

handKey = lb:w:{week}:hand
roomKey = lb:w:{week}:room

pipeline / multi:
  DEL handKey, roomKey   // 或 RENAME 临时 key 再 SWAP（P0 用 DEL+批量 ZADD 即可）

for row in rows:
  if row.bestHand > 0:
    ZADD handKey score=hand*100 member=userId
  if row.bestRoom > 0:
    ZADD roomKey score=room*100 member=userId

EXPIRE handKey 14d
EXPIRE roomKey 14d
```

- 行级 `best_hand` / `best_room` 为 0 则不加入对应 ZSET。
- 失败：`log.warn("leaderboard redis sync failed", e)`，**不抛**；下次 60s 重试。

### 5.4 条件装配

- `@ConditionalOnProperty(name = "mgdemoplus.leaderboard-redis-sync-enabled", havingValue = "true", matchIfMissing = true)`
- 多实例部署时 **每个节点** 都会跑同一 sync（幂等全量覆盖）；读 Redis 无单点问题。若未来 Redis 写放大再改为单实例 leader。

---

## 6. API

### 6.1 路由

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dp/leaderboard/weekly/hand` | 单局周榜 Top50 |
| GET | `/dp/leaderboard/weekly/room` | 单房周榜 Top50 |

Query：

| 参数 | 默认 | 说明 |
|------|------|------|
| `limit` | 50 | 固定上限 **50**（服务端 `Math.min(limit, 50)`） |

### 6.2 鉴权与白名单

- **permitAll**：在 `JwtSecurityConstants.PERMIT_ALL` 增加：
  - `"/dp/leaderboard/weekly/hand"`
  - `"/dp/leaderboard/weekly/room"`
- **登录可选**：有 JWT 时解析 `userId`，附加 `myRank` / `myMultiplier`；无 token 仅返回公开榜。

用户解析：与现有 Controller 一致 — `SecurityContextHolder` → nickname → `DpUserMapper.selectByNickname()`。

### 6.3 读路径（只读 Redis）

```
handKey / roomKey from current week
raw = ZREVRANGE key 0 49 WITHSCORES
entries = build from raw (userId, multiplier=score/100)
ranks = CompetitionRank.assign(entries by multiplier)  // §7
nicknames = batch load dp_user by userIds
optional: myRank = ZREVRANK(key, myUserId), myMultiplier from DB or score
```

**禁止**：`SELECT ... ORDER BY` 直接对外（除 §8 降级讨论）。

### 6.4 响应体（`ResultUtil`）

成功示例：

```json
{
  "success": true,
  "code": 20000,
  "message": "成功",
  "data": {
    "weekMonday": "2026-05-19",
    "board": "hand",
    "staleHint": "榜单约每分钟更新",
    "items": [
      {
        "rank": 1,
        "userId": 12,
        "nickname": "玩家A",
        "multiplier": 15.25
      }
    ],
    "myRank": 8,
    "myMultiplier": 3.50
  }
}
```

| 字段 | 说明 |
|------|------|
| `weekMonday` | ISO 日期 `yyyy-MM-dd` |
| `board` | `hand` \| `room` |
| `staleHint` | 固定文案「榜单约每分钟更新」 |
| `items` | 已算竞赛 `rank` 的 Top50 |
| `myRank` / `myMultiplier` | 仅登录且本周有成绩时返回；否则省略或 `null` |

Redis 空榜：见 §8。

### 6.5 Controller 包

- `com.example.mgdemoplus.leaderboard.DpLeaderboardWeeklyController`
- 或 `controller/DpLeaderboardWeeklyController`（与现有 `DpFriendMailboxController` 风格二选一，项目内统一即可）。

---

## 7. 名次算法（竞赛排名 1,1,3）

在 Java 内对 Redis 取回的 Top50 **按倍数降序** 计算 `rank`（**不要**用 Redis 排名作为最终 `rank`）。

```java
/**
 * 竞赛排名：同分同 rank，下一项跳号。例：倍数 [10, 10, 9] → rank [1, 1, 3]
 */
public static void applyCompetitionRanks(List<LeaderboardEntry> sorted) {
    int rank = 0;
    BigDecimal prev = null;
    for (int i = 0; i < sorted.size(); i++) {
        BigDecimal m = sorted.get(i).getMultiplier();
        if (prev == null || m.compareTo(prev) != 0) {
            rank = i + 1;
            prev = m;
        }
        sorted.get(i).setRank(rank);
    }
}
```

- P0 **不**使用 `updated_at` 破同分；同分条目顺序按 Redis `ZREVRANGE` 返回顺序即可（仅影响列表展示次序，不影响 `rank` 数字）。

---

## 8. 降级策略（推荐）

| 方案 | 行为 | 推荐 |
|------|------|------|
| **A（推荐）** | Redis key 不存在或 `ZCARD==0`：返回 `items: []` + `staleHint` + `message: "榜单约每分钟更新"` | **P0 采用** |
| B | Redis 空时 MySQL `ORDER BY ... LIMIT 50` 拼榜 | 否 — 违背「读榜只读 Redis」；仅可作运维应急 **P1** |

理由：策略 A 与 60s 同步一致；启动后首分钟内空榜可预期；实现简单，无读路径双源不一致。

---

## 9. 前端（仅大厅）

### 9.1 入口

- `home.vue` → 「快捷入口」区增加按钮 **「周榜」**（与「历史对局」同级 `dp-btn--ghost`）。
- 路由：`/leaderboard` → 新组件 `WeeklyLeaderboard.vue`（或 `LeaderboardWeeklyPage.vue`）。

### 9.2 页面结构

```
┌─────────────────────────────────────┐
│  周榜                    [返回大厅]   │
│  榜单约每分钟更新                     │
├─────────────────────────────────────┤
│  [ 单局倍数 ]  [ 单房间倍数 ]  Tab    │
├─────────────────────────────────────┤
│  rank │ 昵称 │ 倍数                  │
│   1   │ ...  │ 12.50x               │
│   1   │ ...  │ 12.50x               │
│   3   │ ...  │ 10.00x               │
├─────────────────────────────────────┤
│  我的：第 8 名 · 3.50x  （登录且有数据）│
└─────────────────────────────────────┘
```

- **两 Tab** 切换时分别请求 `/hand` 与 `/room`。
- **底栏「我的」**：展示 `myRank` / `myMultiplier`；未登录显示「登录后查看我的名次」。
- 倍数展示建议后缀 `x` 或文案「倍」。

### 9.3 API 封装

- 新建 `front/dp_game/src/api/api.dpLeaderboard.js`：
  - `fetchWeeklyHand(http, { limit: 50 })`
  - `fetchWeeklyRoom(http, ...)`
- 使用全局 `this.$http` / `axios` 即可（**无需**单独「公开 axios 实例」；permitAll + 无 token 时与 `getAllRooms2` 相同）。
- 解析：`dpApiResult` 既有 `success` / `data` 约定。

### 9.4 不在范围

- 个人资料页、表 `dp_user_stats` 展示块 **不改**。
- 局内 `game.vue` **不加** 入口。

---

## 10. 验收与迭代

### 10.1 P0 验收 checklist

- [ ] Flyway V7 启动成功，`dp_leaderboard_weekly` 表与索引存在
- [ ] 一手净赢为正：生涯 `largest_pot_won` 与周表 `best_hand_multiplier` **同时** 可能更新
- [ ] 离座净赢为正：生涯 `largest_room_net` 与周表 `best_room_multiplier` **同时** 可能更新
- [ ] 机器人对局不产生周表行
- [ ] 密码房对局产生周表行
- [ ] 写库后 **立即** 调 API：允许空榜 + 「榜单约每分钟更新」；**≤60s** 后 Redis 有数据
- [ ] Top50 条数上限；第 51 名不在 `items`
- [ ] 同倍数两人 `rank` 相同，下一名跳号（1,1,3）
- [ ] 未登录可访问；登录后响应含 `myRank`
- [ ] `JwtSecurityConstants` 白名单生效，未登录不 401
- [ ] 周一 0:00（上海）后新对局计入新 `week_monday`，旧周数据仍可查历史 key（14 天内）
- [ ] 大厅入口可打开周榜页，两 Tab 正常
- [ ] 生涯字段维护行为与改前一致（回归 `DpRoomServiceImpl` 路径）

### 10.2 P1（本次不做）

| 项 | 说明 |
|----|------|
| 写时 ZADD | 写 MySQL 后增量更新 Redis，缩短空窗 |
| 破同分时间 | 同分按 `updated_at` 先后细排或二级 score |
| Redis 昵称缓存 | 同步时写入 `lb:w:{week}:nick:{userId}` 或 HASH，减少读 API 打 MySQL |
| MySQL 降级读 | §8 方案 B，仅供 Redis 长时间故障 |

---

## 附录 A — 建议新增类一览

| 类 | 包 |
|----|-----|
| `DpLeaderboardWeeklyMapper` | `leaderboard.mapper` |
| `DpLeaderboardWeeklyService` | `leaderboard` / `leaderboard.impl` |
| `DpLeaderboardWeeklyRedisSyncScheduler` | `leaderboard` |
| `DpLeaderboardWeeklyController` | `leaderboard` 或 `controller` |
| `LeaderboardCompetitionRankUtil` | `leaderboard` 或 `utils` |
| `WeeklyLeaderboard.vue` | `front/dp_game/src/components/` |
| `api.dpLeaderboard.js` | `front/dp_game/src/api/` |

---

## 附录 B — 必读源码路径

| 主题 | 路径 |
|------|------|
| 倍数 / 写生涯 | `src/main/java/com/example/mgdemoplus/room/impl/DpRoomServiceImpl.java` — `computeHandNetWinMultiplier`、`computeRoomNetWinMultiplier`、`recordPlayerHonorStatsAfterSettle`、`settleAndClearCarryInOnLeaveSeatLocked` |
| 生涯 Mapper | `src/main/java/com/example/mgdemoplus/user/mapper/DpUserStatsMapper.java` |
| V5/V6 注释 | `src/main/resources/db/migration/V5__largest_room_net_multiplier.sql`、`V6__largest_pot_won_multiplier.sql` |
| 时区 | `src/main/java/com/example/mgdemoplus/config/AppTimeZoneConfig.java` |
| `@Scheduled` 范例 | `src/main/java/com/example/mgdemoplus/lobby/DpRoomLobbyReconcileScheduler.java` |
| Redis 范例 | `src/main/java/com/example/mgdemoplus/music/cache/impl/DpRedisListCacheServiceImpl.java` |
| JWT 白名单 | `src/main/java/com/example/mgdemoplus/security/JwtSecurityConstants.java` |
| 大厅 UI | `front/dp_game/src/components/home.vue`、`front/dp_game/src/router/index.js` |
| 调度开关 | `src/main/java/com/example/mgdemoplus/MgDemoPlusApplication.java`（`@EnableScheduling`） |

---

## 附录 C — `myRank` 与 Redis 一致性

- `ZREVRANK` 返回 0-based 名次；API 建议 `myRank = rank + 1`（无 member 时返回 `null`）。
- 用户本周仅有 **单房** 成绩时，手榜 `myRank` 应为空；反之亦然。
- `myMultiplier` 优先从 **MySQL 当前周行** 读取（与榜无关的精确值）；若坚持纯 Redis，可用 `ZSCORE/100`（需用户已在 ZSET 中）。

---

*文档版本：P0 方案初稿，对应仓库 Flyway 最新版本 V6。*
