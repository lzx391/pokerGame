# NPC 概率软噪声移除 · Changelog

**日期**：2026-05-25  
**范围**：删除已废弃的 `applySoftNoise` / `NPC_SOFT_NOISE_ENABLED` / `probNoiseDelta` 通路；保留 `NPC_MOOD_ENABLED` 与 `NpcRuleCoeffs` 其余字段。

## 代码变更

| 文件 | 变更 |
|------|------|
| `DpNpcEngine.java` | 删除 `NPC_SOFT_NOISE_ENABLED`、`applySoftNoise`；`NpcRuleCoeffs` / `RuleNpcConfig` 移除 `probNoiseDelta` / `PROB_NOISE_DELTA` |
| `DpNpcFishStrategy.java` | `applySoftNoise` → 就地 `Math.min/max` |
| `DpNpcCallStrategy.java` | 同上 |
| `DpNpcTagStrategy.java` | 同上 |
| `DpNpcNitStrategy.java` | 同上 |
| `DpNpcCustomStrategy.java` | `applySoftNoise` → 既有 `clamp01` / `baseFold` |

## 文档

- `README.ch.md`：决策概率抖动改为「已移除」
- `docs/ai/npc-engine/01_overview_and_entry.md`、`03_normal_npc_modules.md`：删除软噪声开关行
- `docs/ai/npc-preflop-unified-decision-flow.md`、infographic prompts：同步表述

## 行为等价性

删除前默认 `NPC_SOFT_NOISE_ENABLED=false`，`applySoftNoise` 仅做 `[0,1]` 裁剪、无 ±delta 抖动。策略类内保留原有 `Math.min/max` 或 `clamp01`，决策与删除前一致。

## 验证（2026-05-25）

| 检查 | 结果 |
|------|------|
| `grep` `src/`：`applySoftNoise\|PROB_NOISE_DELTA\|NPC_SOFT_NOISE\|probNoiseDelta` | **0 命中** |
| `mvn -q test -Dtest=DpNpc*` | **exit 0** |
| `mvn -q compile` | **exit 0** |
