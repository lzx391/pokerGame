# 扫描：AI / Bot / NPC
> 自动生成，供 README 综合使用

## 概览

**1 秒房间 Timer**（`DpRoomServiceImpl`）驱动出手：`isLlmBotNickname` → `DpLlmNpcDecisionService`（异步）；否则 `DpNpcEngine`（同步）。

| 类型 | 昵称前缀 | 决策 |
|------|----------|------|
| 规则 NPC | `BOT_FISH_` / `BOT_TAG_` / … | 翻前统一表 + 翻后分策略 |
| LLM NPC | `BOT_LLM_*` | 单轮快照 → 方舟 Chat API |
| GLOBAL LLM | `BOT_LLM_GLOBAL_*` | 每手多轮 + `LlmNpcGlobalHandConversationStore` |

未配置 `ARK_API_KEY` / `ARK_ENDPOINT_ID` 时本地 fold/check 兜底。

## 配置

`dp.llm.ark.*` ← `ARK_*`；详见 [ENV_README.md](../ENV_README.md)。调试：`BotLlmReasoning`（思考链）、`DpLlmNpcDecisionService`（流程）。

## API

`POST /dpRoom/addLlmBot`、`addLlmGlobalBot`、`addRuleNpcBatch` 及各档 `add*Bot`。

## 文档

[docs/ai/npc-engine/README.md](../ai/npc-engine/README.md)、[docs/ai/npc-llm/](../ai/npc-llm/)
