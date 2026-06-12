# 成就新增三项判定说明

| 项 | 值 |
|---|---|
| 方案版本 | 2026-06-10 |
| Flyway | V24 |
| 实现 | `DpDetectAchievementImpl` |

---

## 1. `one_street_heaven`（一街天堂）

**解锁对象**：摊牌输家（`netChipsChange < 0`），仅真人。

| 条件 | 口径 |
|------|------|
| 翻后领先 | `heroFlop` 对全体摊牌参与者 **严格最强**（`compareTo > 0`） |
| 转牌仍领先 | `heroTurn >= oppTurn` 对所有摊牌对手 |
| 决赛单张反超 | 存在对手满足 `oppTurn <= heroTurn` 且 `oppRiver > heroRiver` |
| 与天灾区分 | 不要求转河连追；转牌时反超者未领先即可 |

**文案**：翻后一度领先，却在决赛被一张牌反超

---

## 2. `final_oracle`（决赛神谕）

**解锁对象**：摊牌净赢家（`netChipsChange > 0`），仅真人。

| 条件 | 口径 |
|------|------|
| 转牌落后 | `heroTurn` **严格弱于** 至少一名摊牌对手（`heroTurn < oppTurn`） |
| 终局最强 | `heroRiver` 对全体摊牌参与者严格最强 |
| 牌力评估 | 展示轨 `DpUtilHandEvaluator`，不用蒙特卡洛 |
| 文案 | 用「决赛」「终局」，不写「河牌」 |

**文案**：决赛前落后，终局一张牌完成反杀赢池

---

## 3. `mirror_duel`（镜像对决）

**解锁对象**：净赢的真人赢家。

| 条件 | 口径 |
|------|------|
| 摊牌人数 | ≥ 2 |
| 赢家底牌 | 杂色（两 hole 不同花色） |
| 赢家河牌 | `rankCategory == 6`（同花） |
| 赢家转牌 | 未成同花（`heroTurn.rankCategory < 6`） |
| 镜像对手 | 另一名摊牌对手杂色底牌，**转牌街** `handRankName` 与赢家相同 |
| handRankName | 优先 `boardsByStreet` turn 段 `handRankNameByPlayer`；缺失则 eval + `rankCategoryNameZh` |

**文案**：与对手同局同型，杂色底牌下你凭同花胜出

---

## 4. 测试覆盖

| code | 正例 | 负例 |
|------|------|------|
| `one_street_heaven` | 翻后顶对、转牌仍领先、河牌成顺反超 | 转牌已成顺反超 |
| `final_oracle` | 转牌高牌落后、终局 Broadway 赢池 | 转牌已领先 |
| `mirror_duel` | 转牌双高牌、河牌杂色成同花 | 赢家同花底牌 |
