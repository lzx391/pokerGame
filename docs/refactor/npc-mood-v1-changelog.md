# NPC Mood v1 — Changelog

**日期**：2026-05-25  
**范围**：规则 NPC mood 仅用于结算更新 + 桌边台词分桶；决策路径零 mood。

## 变更文件列表

| 类型 | 路径 |
|------|------|
| 新增 | `src/main/java/com/example/mgdemoplus/npc/mood/DpNpcMoodProperties.java` |
| 新增 | `src/main/java/com/example/mgdemoplus/npc/mood/DpNpcMoodConfig.java` |
| 新增 | `src/main/java/com/example/mgdemoplus/npc/mood/NpcMoodState.java` |
| 配置 | `src/main/resources/application.yml`（`dp.npc.mood` + env 占位） |
| 引擎 | `DpNpcEngine.java`（删除 `NPC_MOOD_ENABLED`，决策不传 mood） |
| 结算 | `DpRoomServiceImpl.java`（本手 `handDelta` 更新 mood） |
| 台词 | `DpNpcTableTalkService.java`（mood 分桶 + TAG 豁免） |
| 决策参数 | `DpNpcRuleDecisionParams.java`（移除 `mood` 字段） |
| 翻前/策略 | `DpNpcUnifiedPreflopStrategy.java`，`DpNpcFish/Call/Lag/Maniac/Tag/NitStrategy.java` |
| 台词池 | `npc-lines/fish.yaml`、`call.yaml`、`lag.yaml`、`maniac.yaml`、`nit.yaml`、`custom.yaml`（**未改** `tag.yaml`） |
| 测试 | `DpRoomDesertedRoomCleanupTest.java`（构造器注入 `DpNpcMoodProperties`） |
| 文档 | `README.ch.md`、`README.en.md`、`docs/ai/npc-engine/01_overview_and_entry.md` |

## 结算伪代码

```
if (!dp.npc.mood.enabled) return
for each rule bot (非 LLM):
  handDelta = chipsAfterSettle - chipsBeforeSettle[nick]  // 分池前快照
  if handDelta == 0: continue
  if handDelta > 0: mood += deltaWin
  else if chipsAfter < room.startingChips: mood += deltaLose
  else: continue  // 本手输但未低于进房初始，不扣 mood
  mood = clamp(mood, -1, 1)
```

## 台词池 key 约定

- 基础 key（现网）：`FOLD`、`CALL_OR_CHECK`、`RAISE`、`ALL_IN`、`SETTLE_WIN`、`SETTLE_LOSE`
- mood 桶：`MOOD_{HIGH|NEUTRAL|LOW}_{基础key}`，例如 `MOOD_HIGH_FOLD`、`MOOD_LOW_SETTLE_LOSE`
- `dp.npc.mood.enabled=false`：始终用基础 key
- `enabled=true` 且风格 **非 TAG**：先查 mood 桶，**空则 fallback** 基础 key
- **TAG**：始终基础 key，不读 mood 桶

分档：`mood >= highThreshold` → HIGH；`mood <= lowThreshold` → LOW；否则 NEUTRAL（仅台词，默认 ±0.35）。

## 敏感词自检（本任务新增 yaml 行）

对 `npc-lines/{fish,call,lag,maniac,nit,custom}.yaml` 中 **MOOD_*** 新增行扫描禁止子串：

| 禁止子串 | 新增行命中 |
|----------|------------|
| 激进 | 0 |
| 筹码 | 0 |
| 全押 | 0 |

并人工避开同类表述（全下、all-in、推、加注、下注、筹码量等）。**旧 yaml 未全量清洗，仅保证 mood 池合规。**

## 验证命令

```bash
mvn -q compile && mvn -q test
rg "NPC_MOOD_ENABLED|p\.mood" src/main/java/com/example/mgdemoplus/npc/
```

策略包 `rg` 预期：**0 命中**（`DpNpcTableTalkService` 可读 `bot.getMood()` 用于分桶）。

## 测试输出摘要

- `mvn -q compile`：成功
- `mvn -q test`：成功（exit 0，含 Spring Boot 上下文与单元测试）
- `rg "NPC_MOOD_ENABLED|p\.mood" src/main/java/com/example/mgdemoplus/npc/`：**0 命中**
