# AI Agent 入门学习路线（MGDemoPlus 配套）

> **写给谁**：AI 零基础，但 **会 Java、会调 HTTP API**（例如写过 `@RestController`、用过 `HttpClient`）  
> **目标**：从零理解 LLM → Prompt → RAG → Tool → Agent，能在 MGDemoPlus 里 **看懂现有 LLM NPC 代码**，并为将来做「项目助手 Agent」打基础  
> **性质**：**纯学习文档**，不表示仓库已上线完整 Agent 产品功能

---

## 阅读顺序（建议按编号 01 → 09）

| 序号 | 文档 | 状态 | 一句话 |
|------|------|------|--------|
| 01 | [01-llm-and-chat-basics.md](./01-llm-and-chat-basics.md) | ✅ 本文档集 | LLM 是什么、Chat API、三种角色、token、幻觉 |
| 02 | [02-prompt-engineering-basics.md](./02-prompt-engineering-basics.md) | ✅ 本文档集 | System prompt、约束、JSON 输出、多轮对话 |
| 03 | [03-rag-retrieval-augmented-generation.md](./03-rag-retrieval-augmented-generation.md) | ✅ 已有 | 先查文档再回答，减少胡编 |
| 04 | [04-tool-function-calling.md](./04-tool-function-calling.md) | ✅ 已有 | 模型出主意，Java 查库、跳转 |
| 05 | [05-agent-orchestration.md](./05-agent-orchestration.md) | ✅ 已有 | Agent 编排：Chatbot vs Agent、ReAct 循环 |
| 06 | [06-java-stack-spring-ai.md](./06-java-stack-spring-ai.md) | ✅ 已有 | Java 技术栈：Spring AI、不必重学 Python |
| 07 | [07-mgdemoplus-practice-map.md](./07-mgdemoplus-practice-map.md) | ✅ 已有 | MGDemoPlus 实战地图：LLM NPC vs 大厅 Copilot |
| 08 | [08-weekly-learning-schedule.md](./08-weekly-learning-schedule.md) | ✅ 已有 | 9 周学习计划与每周验收 |
| 09 | [09-interview-and-mnemonics-master.md](./09-interview-and-mnemonics-master.md) | ✅ 已有 | 面试速记与全系列口诀汇总 |

> **说明**：03、04 在本任务开始前已存在；05～09 与 01、02 共同构成完整 9 篇路线。

---

## 和 MGDemoPlus 的关系（实践对照）

本路线 **不是** 空讲概念，而是对照本仓库已有能力：

| 你学到的 | 仓库里已有的例子 |
|----------|------------------|
| 调 Chat API | `OpenAiCompatibleChatClient`（JDK `HttpClient` 调 OpenAI 兼容接口） |
| System + User + JSON 输出 | `DpLlmNpcDecisionService`（`BOT_LLM` / `BOT_LLM_GLOBAL` 扑克 NPC） |
| 多轮历史 | `LlmNpcGlobalHandConversationStore`（同一手牌内多轮 user/assistant） |
| RAG / Tool / 完整 Agent | **学习向设计**，见 03～09；**尚未**作为独立「大厅助手」产品上线 |

读文档时建议 **打开对应 Java 文件对照**：NPC 决策是 **最小可运行的 LLM 集成样本**；03～09 描述的是 **如何把同样能力扩展成「帮玩家查文档、查牌谱、跳转快匹」的 Agent**。

相关专题文档：

- [docs/ai/npc-llm/part01_类间关系以及调用.md](../npc-llm/part01_类间关系以及调用.md) — LLM NPC 类图与调用链  
- [docs/JWT.md](../../JWT.md) — Agent 接入时 **用户身份只能从 JWT 取**  
- [docs/DPGAME.md](../../DPGAME.md) — 快匹、房间 REST，RAG/Tool 示例会引用  

---

## 学习-only 免责声明

1. **本文档集用于自学**，不构成对外 API 承诺；文中伪代码、类名（如 `DpAgentChatService`）可能是 **规划命名**，以实际代码为准。  
2. **不要把 API Key 写进仓库**；与 `DpLlmNpcDecisionService` 一样，密钥走环境变量 / `.env`（见 `docs/ENV_README.md`）。  
3. **Agent 能查数据 ≠ 能乱动数据**：涉及牌谱、好友、私信等 Tool，必须复用现有 Service 与 JWT 鉴权（见 04、08）。  
4. 房间对局状态仍在 **单机内存** `ConcurrentHashMap`，多实例 Agent 广播不在本路线 Phase 1 范围（见 `docs/refactor/multi-instance-redis-room-plan.md`）。

---

## 怎么学最省力

1. **每天一篇**：01～02 打基础，03～04 看「查资料」和「动手脚」，05～09 拼完整 Agent。  
2. **每篇末尾有「记忆小口诀」**：用来复习，不用背 API 字段名。  
3. **动手最小实验**：在 01 读完后，用 Postman 或一段 Java 调一次 Chat API；在 02 读完后，改一句 system prompt 看输出变化。  
4. **有问题先查仓库**：`grep DpLlmNpcDecisionService`、`grep OpenAiCompatibleChatClient`。

---

**从第一篇开始**：[01-llm-and-chat-basics.md](./01-llm-and-chat-basics.md)
