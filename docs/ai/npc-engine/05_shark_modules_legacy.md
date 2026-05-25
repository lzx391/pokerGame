# Shark 相关表与模块（遗留，归档）

> **核对日期**：2026-05-25  
> **权威来源**：`V1__init_schema.sql`、`npc/entity/DpSharkOpponentProfile.java`  
> **Status**: maintained（Schema 留存说明）

## 表与实体

| 对象 | 说明 |
|------|------|
| `dp_shark_opponent_profile` | V1 建表；实体 `DpSharkOpponentProfile` 仍在 `npc/entity/` |
| 运行时 | **无** Mapper 写路径；对局 Bot 画像走内存 `DpPlayerStats` |

## 曾规划、已下线的能力

历史方案中的 **Shark 学习旋钮**（`DpNpcSharkLearningLab`）、**剥削 HandPlan**（`DpNpcSharkExploitHandPlan`）、专用翻前模块等 **未** 出现在当前 `npc/strategy` 包。勿按旧分册假设 Shark 与 TAG 决策树分离。

## 兼容入口

仅 REST / 昵称层保留 Shark 品牌，逻辑见 [04_shark_legacy.md](04_shark_legacy.md)。
