# 02 · Prompt 工程入门

> **写给谁**：已读过 [01-llm-and-chat-basics.md](./01-llm-and-chat-basics.md)，知道 system/user/assistant 是什么  
> **读完能做什么**：会写 system prompt、加约束、要 JSON 输出、组织多轮 history，并用扑克助手例子串起来

---

## 1. Prompt 是什么？

**Prompt** = 你发给模型的 **全部文字指令**（主要是 system + user，有时含历史 assistant）。

可以把它当成 **给临时员工的工单**：

- 工单写清楚 → 产出稳定  
- 工单模糊 → 员工按自己理解发挥（容易幻觉、格式乱）

**Prompt 工程** 不是玄学，而是：**用自然语言 + 少量结构，把任务、边界、输出格式写死**。

---

## 2. System Prompt：总说明书

**System prompt** 通常 **只设一次**（每请求都带上），用来定义：

| 写什么 | 例子 |
|--------|------|
| **身份** | 「你是无限注德州扑克教练，面向新手。」 |
| **能做什么 / 不能做什么** | 「只讨论规则与策略，不讨论赌博网站。」 |
| **事实来源** | 「若用户问项目接口，仅依据提供的文档片段回答。」 |
| **输出格式** | 「必须输出一行 JSON，字段 action、reason。」 |
| **语气** | 「简短、中文、不用 markdown。」 |

### 2.1 MGDemoPlus 里的真实 system（节选思路）

`DpLlmNpcDecisionService` 里的 system 非常长，因为它要同时管：

- 牌力字段含义（`made_en`、`equity_estimate`…）  
- 行动纪律（什么时候不能 fold）  
- **输出契约**：「仅一行 JSON，`{action,chips_to_add,brief_reason,table_talk}`」  

这说明：**生产级 system prompt 往往 = 产品规则 + 协议文档**。  
入门时可以先写 10 行，再随踩坑慢慢加长——但 **别和用户问题混在一条 user 里**，身份和格式应放 system。

### 2.2 扑克助手示例（学习用，短版）

```text
你是 MGDemoPlus 扑克学习助手，只帮助理解规则与基础策略。
规则：
1. 不提供真实货币赌博建议，不推广线下赌局。
2. 用户未提供手牌/公共牌时，先追问，不要编造具体牌面。
3. 回答不超过 200 字，用中文。
4. 若问题与扑克无关，礼貌拒绝并说明你的职责范围。
```

这段 **不包含具体一手牌**，所以适合反复用在每一轮请求的 system 里。

---

## 3. 约束（Constraints）：让模型少犯蠢

**约束** = 在 prompt 里写清的 **必须 / 禁止** 列表。

常用写法：

| 类型 | 示例句 |
|------|--------|
| **必须** | 「必须引用用户给出的 pot 数字，不要自己改。」 |
| **禁止** | 「禁止输出 markdown 代码块。」 |
| **条件分支** | 「若跟注额为 0，不得建议 fold。」 |
| **失败策略** | 「资料不足时回答：文档中未找到。」 |

**和校验的关系**：

- Prompt 约束 = **软约束**（模型可能仍违反）  
- Java 解析后校验 = **硬约束**（JSON 缺字段就重试或默认 fold）  

`DpLlmNpcDecisionService` 在解析 JSON 后还会检查 `action` 枚举、金额范围—— **Prompt + 代码双保险** 是正规做法。

---

## 4. 要求 JSON 输出

很多 Agent / NPC 场景需要 **机器可读** 回复，而不是散文。

### 4.1 在 system 里写清 schema

```text
【输出】仅输出一行 JSON 对象，不要 markdown，不要解释性前缀。
字段：
- action: 字符串，只能是 "fold" | "call" | "raise"
- amount: 整数，raise 时为加注额，否则为 0
- reason: 字符串，≤80 字中文
示例：{"action":"call","amount":0,"reason":"底池赔率合适，跟注看转牌。"}
```

### 4.2 API 层：`response_format`

`OpenAiCompatibleChatClient` 支持 `response_format: { "type": "json_object" }`（OpenAI 兼容）。  
开启后模型 **更倾向于** 只吐 JSON；但若厂商不支持，会 HTTP 400——NPC 配置里可用开关关闭（见类注释）。

### 4.3 Java 解析要点

```java
// 伪代码：和 DpLlmNpcDecisionService 同类思路
String raw = reply.content();
// 模型偶尔仍包 ```json ... ```，可用正则剥掉（仓库里 JSON_BLOCK Pattern）
JsonNode node = objectMapper.readTree(extractJson(raw));
String action = node.path("action").asText();
```

**记住**：永远 **try/catch + 默认值**，LLM 不是可靠编译器。

---

## 5. 多轮 History：messages 数组怎么长

Chat API 的 **同一次请求** 里，`messages` 是从旧到新排列的 **整段对话**：

```text
system: （总规则，可选单独字段或 messages 第一条）
user: 我 BTN 拿 AKs，100bb，前面都 fold，怎么打？
assistant: 建议 open raise 到 2.5bb…
user: 如果 BB 3bet 到 9bb 呢？
assistant: （模型需要根据上一轮继续答）
user: （本轮新问题）
```

**为什么要带回 assistant？**  
模型 **无状态**——你不发历史，它不知道上一句说了什么。

### 5.1 两种常见策略

| 策略 | 做法 | 适用 |
|------|------|------|
| **全量历史** | 每轮把所有 user/assistant 都塞进 messages | 轮数少、单会话短 |
| **滑动窗口** | 只保留最近 N 轮或 N token | 聊天助手、省 token |
| **摘要压缩** | 旧对话让模型总结成一段 system 备注 | 长会话 |

### 5.2 仓库例子：`BOT_LLM_GLOBAL`

同一手牌内，服务端在 `LlmNpcGlobalHandConversationStore` 里存 **本轮之前的 user 快照 + assistant 的 JSON**，下次决策时一并发给 API。  
这样模型能参考上一轮的 `plan_next`（计划下一街怎么打）—— 这就是 **业务驱动的多轮 prompt**，不是闲聊。

---

## 6. 扑克助手完整示例（从用户问题到 messages）

### 6.1 场景

玩家在 MGDemoPlus 学习模式里问（纯教学，非真实对局引擎）：

> 「我在按钮位，手牌 A♠K♠，100bb，前面都 fold，该怎么打？」

### 6.2 推荐 messages 结构

**System**（固定）：

```text
你是扑克学习助手。只教原理，不编造对局数据。
输出：先给 2～4 句中文建议；若用户要求结构化，再附一行 JSON。
JSON 字段：action(fold|call|raise), amount, reason。
```

**Messages**（本轮仅一轮也可；若继续追问则追加）：

```json
[
  {
    "role": "user",
    "content": "位置：按钮(BTN)。手牌：As Ks。有效筹码：100bb。行动：前面全部 fold 到我。问题：翻前怎么打？"
  }
]
```

**期望 assistant**（模型生成，示例）：

```text
在 BTN 用 AKs 面对全 fold，标准做法是 open raise（约 2.2～2.5bb），利用位置和后手优势抢盲注。
{"action":"raise","amount":25,"reason":"BTN 宽范围 open，AKs 属于强牌应加注。"}
```

### 6.3 和 LLM NPC 的对比

| 维度 | 扑克学习助手（本例） | `DpLlmNpcDecisionService` |
|------|----------------------|---------------------------|
| user 内容 | 人类自然语言 | 服务端冻结快照（防模型改数字） |
| 多轮 | 可选，偏聊天 | GLOBAL 模式必须，用于 plan_next |
| 输出 | 建议 + 可选 JSON | **强制**单行 JSON + table_talk |
| 下游 | 展示文字 | 驱动真实 `BotAction` |

**启示**：同一套 Prompt 技巧，**用户输入是「人话」还是「结构化快照」** 取决于你要不要防幻觉、要不要和引擎状态严格对齐。

---

## 7. User Prompt 写作小技巧

1. **结构化优于散文**：关键事实分行写（位置、手牌、底池、跟注额）。  
2. **一次一事**：复杂问题拆成多轮，比一条超长 user 更稳。  
3. **显式标注不确定**：「以下数据来自服务端，请勿修改：call_amount=120」。  
4. **少示例多规则**：few-shot 示例有用，但 2～3 个足够；太多占 token。  
5. **与 system 分工**：格式、角色放 system；具体牌面放 user。

---

## 8. 常见坑

1. **把约束只写进 user**：下一轮 user 换了，约束被冲掉 → 应放 system。  
2. **没定义 JSON 失败怎么办**：解析失败应 log + 重试或规则 NPC fallback。  
3. **历史无限增长**：长聊天会超 token → 要截断或摘要（学习计划见 08）。  
4. **system 和用户输入矛盾**：以 **最新 user + 明确标注的服务端快照** 为准（GLOBAL prompt 里写了这条纪律）。

---

## 记忆小口诀

> **system 定身份，格式约束写在前；**  
> **user 给本轮局，历史 assistant 别丢件；**  
> **要 JSON 说 schema，解析失败有后备；**  
> **快照数字服务端，别让模型随手改。**

---

**下一篇**：[03-rag-retrieval-augmented-generation.md](./03-rag-retrieval-augmented-generation.md) — 文档太多塞不进 prompt 时，先检索再回答。
