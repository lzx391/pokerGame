# 07 · MGDemoPlus 实战地图：代码在哪、Demo 怎么做

> **读完能做什么**：知道现有 LLM 用在哪、Copilot 放哪、Tool 接哪些 REST

---

## 1. 两条 LLM 线（别混）

| | **LLM NPC（已有）** | **大厅 Copilot（目标）** |
|--|---------------------|---------------------------|
| 入口 | 对局内 Bot 座位 | 大厅/任意页助手面板 |
| 输入 | 牌局状态 JSON | 用户自然语言 |
| 输出 | fold/call/raise | 文字 + Tool 结果 + 跳转 |
| 典型类 | `DpLlmNpcDecisionService` | 未来 `DpAssistantChatService` |
| 决策类型 | **同步/异步出牌** | **RAG + Tool + Agent** |

**简历一句话**：同一套模型通道；NPC 是 **决策型**；助手是 **Tool 型 Agent**。

---

## 2. 可复用的基础设施

| 已有 | Copilot 怎么用 |
|------|----------------|
| JWT + `SecurityConfig` | 助手 API 要登录；Tool 用当前用户 |
| `JwtSecurityConstants.PERMIT_ALL` | 助手接口 **不要** 误加白名单 |
| `.env` / 方舟或 OpenAI 兼容 URL | 模型 Key 配置方式相同 |
| `DpHandHistory*` REST | Tool：`search_hand_history(date)` |
| 快匹 `quickMatch2` + `/ws/dp-quick-match` | Tool：`navigate(/quickmatch)` + RAG 解释流程 |
| `docs/JWT.md` `DPGAME.md` | RAG 索引源 |

---

## 3. Tool 映射表（MVP 建议）

| Tool 名 | 调什么 | 参数 | 身份 |
|---------|--------|------|------|
| `search_hand_history` | `DpHandHistoryService` | `date` | userId 来自 JWT |
| `navigate` | 返回前端 JSON | `path` 白名单 | 不需 userId |
| `explain_topic` | 可选纯 RAG | 问题文本 | — |

**禁止**：Tool 参数里让模型传 `userId`（见 [04-tool](./04-tool-function-calling.md)）。

---

## 4. 建议包结构（实现时参考，本文不落地代码）

```text
assistant/
  DpAssistantController.java      # POST /dp/assistant/chat
  DpAssistantChatService.java     # ChatClient + Tool 循环
  DpAssistantToolConfig.java      # @Tool 方法
  rag/
    DpDocIndexService.java        # 索引 docs/*.md
```

---

## 5. 简历 / 面试 30 秒叙事

> MGDemoPlus 是 Spring Boot 扑克平台。我在单体里加了 **AI 大厅助手**：用 **RAG** 索引项目文档回答规则与流程；用 **Function Calling** 查用户牌谱、跳转快匹；**JWT 鉴权** 下 Tool 只查当前用户。对局 LLM NPC 与助手 **分模块**，模型配置复用。后续可拆 `assistant-service` 微服务。

---

## 6. 和多实例计划的关系

- 助手 REST **无状态**，多副本 **轮询** 即可  
- RAG 向量库需 **多实例共享**（Redis / pgvector）  
- 对局仍见 [multi-instance-redis-room-plan.md](../../refactor/multi-instance-redis-room-plan.md)

---

## 记忆小口诀

> NPC 管出牌，Copilot 管办事；  
> 牌谱 history 查，快匹 navigate 指；  
> JWT 定身份，模型不传 id；  
> 文档做 RAG，简历好讲故事。

---

**下一篇**：[08-weekly-learning-schedule.md](./08-weekly-learning-schedule.md)
