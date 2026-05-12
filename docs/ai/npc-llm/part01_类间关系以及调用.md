# 大模型 NPC（`BOT_LLM`）：核心类分工与端到端调用链

**本篇说明**：偏「面试速讲」粒度；方舟（Ark）与 `ARK_*` 环境变量仍以根目录 `README` / [`ENV_README.md`](../../ENV_README.md) 为准。

## 1) 涉及核心类

- [DpNpcEngine.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpNpcEngine.java)
- [DpLlmNpcDecisionService.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpLlmNpcDecisionService.java)
- [DpLlmNpcContextMapper.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/DpLlmNpcContextMapper.java)
- [LlmNpcGameContext.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/npc/LlmNpcGameContext.java)
- [LlmNpc.java](../../../src/main/java/com/example/mgdemoplus/service/serviceImpl/dp/npc/LlmNpc.java)

## 2) 一句话职责

- `DpNpcEngine`：识别 `BOT_LLM` 并提供牌局快照原料。
- `DpLlmNpcDecisionService`：LLM 决策总控（异步请求、快照校验、解析、兜底、收敛）。
- `DpLlmNpcContextMapper`：把引擎上下文映射成扁平的 LLM 输入对象。
- `LlmNpcGameContext`：承载 `M/T/H/E` 关键字段并输出 prompt 块。
- `LlmNpc`：OpenAI 兼容 HTTP 客户端，负责调用方舟并返回结构化回复。

## 3) 端到端调用链

1. 游戏轮询到 BOT 行动点，进入 `DpLlmNpcDecisionService.decideActionIfReady(...)`。  
2. 通过 `DpNpcEngine.buildLlmNpcGameSnapshot(...)` 获取牌局摘要。  
3. `DpLlmNpcContextMapper.map(...)` 生成 `LlmNpcGameContext`。  
4. `LlmNpcUserSnapshot.formatUserPayload(room, bot, ctx)` 生成固定键表 + `----` + 值行的 user 正文；纪律在 `DpLlmNpcDecisionService` 的 `LLM_SYSTEM_PROMPT`。  
5. `LlmNpc.chatMessagesDetailed(...)` 请求方舟，返回正文 + reasoning。  
6. `DpLlmNpcDecisionService` 解析 JSON 动作并做 `normalizeAndClamp(...)`。  
7. 若超时/解析失败/快照过期，统一回退 `fallback(...)`，保证可执行。  

## 4) 面试可强调的工程点

- **一致性**：请求发起与回包阶段都做快照校验，防止“过期动作”落地。  
- **稳定性**：异步 + 超时 + 本地兜底，模型不可用时也不阻塞游戏流程。  
- **可观测性**：决策日志与 reasoning 日志分离，可单独调级排查问题。  
- **边界安全**：对 `raise/all-in/call` 做金额收敛，保证动作合法且可执行。  

