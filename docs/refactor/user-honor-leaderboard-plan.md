# 资料卡 honor 附带周榜位次 — 方案与验收

## 背景

局内资料卡 `GET /dpUser/stats/{userId}` 返回 `honor`（`DpPlayerHonorView`），此前仅有生涯牌型/倍数统计。周榜 REST 已对登录观众附带 `myRank` / `myMultiplier`，资料卡查看**任意玩家**时也需要同样语义的两个榜数据，响应时查 Redis 拼装，不改 `dp_user` / `dp_user_stats` 表。

## 两个榜单（以代码为准）

| 字段名 | board | REST | Redis key 模式 | 倍数含义 |
|--------|-------|------|----------------|----------|
| `leaderboardWeeklyHand` | `hand` | `GET /dp/leaderboard/weekly/hand` | `lb:w:{yyyyMMdd}:hand` | 本周单局最佳**手牌**净赢倍数 |
| `leaderboardWeeklyRoom` | `room` | `GET /dp/leaderboard/weekly/room` | `lb:w:{yyyyMMdd}:room` | 本周单局最佳**房间**净赢倍数 |

- `{yyyyMMdd}`：`DpLeaderboardWeekUtil.weekKeySuffix(当前自然周周一)`，时区 `mgdemoplus.time-zone`（默认 `Asia/Shanghai`）。
- ZSET member = `userId` 字符串；score = 倍数 × 100（`DpLeaderboardRedisRepository.toRedisScore`）。
- `rank`：Redis `ZREVERSE RANK` + 1（与榜单 API 的 `myRank` 一致）；未进 ZSET 为 `null`。
- `multiplier`：来自 `dp_leaderboard_weekly` 当周行（与 `myMultiplier` 一致）；无有效记录为 `null`。
- Redis 读 rank 失败时仅 `rank` 为 null，`multiplier` 仍可从 MySQL 降级。

## 实现要点

- 复用 `DpLeaderboardWeeklyReadService#lookupPlacement(board, userId)`，榜单接口 `attachMyStats` 与 `buildHonorView` 共用，避免重复 key/逻辑。
- 嵌套 VO：`DpWeeklyLeaderboardPlacementView`（`board`, `weekMonday`, `rank`, `multiplier`）。
- `DpUserServiceImpl.buildHonorView` 注入 `placementForHand` / `placementForRoom`。

## 安全

- `/dpUser/stats/{userId}` **不在** `JwtSecurityConstants.PERMIT_ALL`，需 JWT（与改前一致）。
- 周榜列表接口仍为 permitAll；仅「查看他人 honor」走登录。

## 前端（本次未改）

- `GamePlayerSocialSheet.vue` 已消费 `honor` 的生涯字段，**未**绑定 `leaderboardWeeklyHand` / `leaderboardWeeklyRoom`；后续 UI 可直接用同名 JSON 字段。

## 验收

### 编译

```bash
mvn -q -DskipTests compile
```

### 手工 curl（需有效 JWT）

```bash
# 替换 TOKEN、TARGET_USER_ID
curl -s -H "Authorization: Bearer TOKEN" \
  "http://localhost:8088/dpUser/stats/TARGET_USER_ID"
```

期望 `data.honor` 含：

```json
"leaderboardWeeklyHand": {
  "board": "hand",
  "weekMonday": "20260526",
  "rank": 3,
  "multiplier": 12.50
},
"leaderboardWeeklyRoom": {
  "board": "room",
  "weekMonday": "20260526",
  "rank": null,
  "multiplier": 8.00
}
```

未上榜 / 无本周记录：`rank` 与 `multiplier` 可为 `null`；接口仍 200。

### 对照周榜 API

同一登录用户看自己时，`/dp/leaderboard/weekly/hand` 的 `myRank`/`myMultiplier` 应与 honor 内 `leaderboardWeeklyHand` 一致（`room` 同理）。

## 变更文件

- `leaderboard/vo/DpWeeklyLeaderboardPlacementView.java`（新建）
- `leaderboard/impl/DpLeaderboardWeeklyReadService.java`
- `user/dto/DpPlayerHonorView.java`
- `user/impl/DpUserServiceImpl.java`
- `docs/refactor/user-honor-leaderboard-plan.md`（本文）
