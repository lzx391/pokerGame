# NPC 台词 v2 + Mood 统一 — Changelog

**日期**：2026-05-25  
**范围**：七种规则 NPC 共用 mood 三档台词；YAML 嵌套 `happy/calm/sad`；Loader 展平；删除 TAG 台词特例。

## 变更文件

| 类型 | 路径 |
|------|------|
| Loader | `DpNpcLinePoolLoader.java` |
| 服务 | `DpNpcTableTalkService.java`（删除 `isTagStyleForTableTalk`） |
| 文档 | `NpcMoodState.java`（注释） |
| 台词 | `src/main/resources/npc-lines/{tag,nit,fish,call,lag,maniac,custom}.yaml`（全量重写） |
| 生成/校验 | `scripts/gen_npc_lines_v2.py` |
| 测试 | `NpcLinesYamlContractTest.java`、`DpNpcLinePoolLoaderTest.java` |
| 本文档 | `docs/refactor/npc-lines-v2-changelog.md` |

**未改**：Flyway、NPC 决策/strategy（mood 不参与决策）、`applySoftNoise`。

## 展平 key 规则

YAML 顶层仅保留 `enabled`、`speakProbability`、`settleSpeakProbability` 与三档块：

| YAML 块 | 展平前缀 | `NpcMoodState` | 触发条件 |
|---------|----------|----------------|----------|
| `happy` | `MOOD_HIGH_` | `HIGH` | `mood >= highThreshold`（默认 0.35） |
| `calm` | `MOOD_NEUTRAL_` | `NEUTRAL` | 两阈值之间；**或** `dp.npc.mood.enabled=false` |
| `sad` | `MOOD_LOW_` | `LOW` | `mood <= lowThreshold`（默认 -0.35） |

示例：`happy.FOLD` → `MOOD_HIGH_FOLD`；`calm.RAISE` → `MOOD_NEUTRAL_RAISE`。

`DpNpcTableTalkService.linesForPoolKey`：只查展平 key，**不再** fallback 裸 `FOLD` / 旧 `pools`。

## 行动类型 → 池 key（勿改 `BotActionType` 枚举）

| 池 key | 含义 | 执行要点 |
|--------|------|----------|
| `FOLD` | 弃牌 | `fold()` |
| `CALL_OR_CHECK` | 跟或过（共一池） | `callAmount = 面对档 − 本街已下`；=0 过牌，>0 跟注 |
| `RAISE` | 加码 | `clampRaiseAmountForLegal` 后 `bet(amount)` |
| `ALL_IN` | 推光意图 | 直接 `bet(amount)`，通常 amount=后手 |
| `SETTLE_WIN` / `SETTLE_LOSE` | 本手结算后 | 结算路径单独触发，非四行动 |

- `RAISE` vs `ALL_IN`：看决策枚举，不看 `bet()` 后是否置 `allIn`；短码打光仍走 `RAISE` 池。
- `CHECK` 与 `CALL` 不拆池（本任务未改）。

## 七风格差异摘要（`happy.RAISE` 抽检一句）

| 文件 | 人设 | `happy.RAISE` 示例 |
|------|------|-------------------|
| `tag.yaml` | 紧凶理性、记录感 | 「价值线，加码。」 |
| `nit.yaml` | 极紧惜字 | 「…打。」 |
| `fish.yaml` | 娱乐鱼、手气 | 「手气来了，加点气氛！」 |
| `call.yaml` | 跟注站、亮牌 | 「跟习惯了，顺手加点。」 |
| `lag.yaml` | 松凶施压 | 「加压！跟不跟？」 |
| `maniac.yaml` | 疯子热闹 | 「炸场开始！跟不跟！」 |
| `custom.yaml` | 房主定制礼貌 | 「按设置，适度加码。」 |

## `speakProbability` / `settleSpeakProbability`（沿用原值）

| 文件 | speak | settle |
|------|-------|--------|
| tag | 0.22 | 0.26 |
| nit | 0.15 | 0.20 |
| fish | 0.32 | 0.48 |
| call | 0.22 | 0.44 |
| lag | 0.30 | 0.50 |
| maniac | 0.38 | 0.58 |
| custom | 0.24 | 0.40 |

## 条数抽检表（每键 >= 10）

脚本：`python scripts/gen_npc_lines_v2.py`（生成前校验）+ `NpcLinesYamlContractTest`（加载后断言）。

七文件 × 3 档 × 6 键 = 126 池，**全部 = 10**（节选）：

| 文件 | happy.FOLD | calm.RAISE | sad.SETTLE_LOSE |
|------|------------|------------|-----------------|
| tag | 10 | 10 | 10 |
| nit | 10 | 10 | 10 |
| fish | 10 | 10 | 10 |
| call | 10 | 10 | 10 |
| lag | 10 | 10 | 10 |
| maniac | 10 | 10 | 10 |
| custom | 10 | 10 | 10 |

完整枚举：`{tag,nit,fish,call,lag,maniac,custom}.{happy,calm,sad}.{FOLD,CALL_OR_CHECK,RAISE,ALL_IN,SETTLE_WIN,SETTLE_LOSE}` → **均为 10**。

## 敏感词自检

禁止子串（大小写）：`激进`、`筹码`、`全押`、`全下`、`all-in`、`all in`、`梭`、`推池`、`加注`、`下注`。

```bash
rg "激进|筹码|全押|全下|all-in|梭" src/main/resources/npc-lines/ -i
```

**命中：0**（2026-05-25 构建后）。

## 验证命令与结果

```bash
mvn -q compile          # 成功
mvn -q test             # 成功（exit 0）
rg "isTagStyleForTableTalk" src/   # 0 命中
```

新增测试：`NpcLinesYamlContractTest`、`DpNpcLinePoolLoaderTest`。

## Mood 结算（保持 v1，仅台词结构变更）

- 配置：`dp.npc.mood`（`enabled` 默认 true；`deltaWin`/`deltaLose`；`highThreshold`/`lowThreshold` 默认 ±0.35）。
- 结算：本手 `handDelta>0` 加分；本手输且 `chips < room.startingChips` 才减分；否则不变；clamp `[-1,1]`。
- 台词：`mood>=高` → happy；中间 → calm；`<=低` → sad。
