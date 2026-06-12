# 06 · Java 技术栈：做 AI 应用不必重学 Python

> **读完能做什么**：知道 Java 里用啥框架、核心类名、和 Spring Boot 怎么搭

---

## 1. 还要重学 Python 吗？

| 方向 | 语言 |
|------|------|
| **AI 应用 / Agent / RAG** | ✅ **Java 完全够用** |
| 算法研究、Notebook 实验 | Python 多 |
| 读开源示例 | 会读 Python 更好，**非必须**

**企业里**：业务、安全、事务在 Java；模型走 **HTTP API**——语言不绑死。

---

## 2. 框架二选一（建议 Spring AI）

| | **Spring AI** | LangChain4j |
|--|---------------|---------------|
| 和 Spring Boot | 一家，集成顺 | 独立，也支持 Spring |
| 文档 | spring.io/spring-ai | langchain4j.dev |
| 本仓库建议 | ✅ **优先** | 备选 |

别两个同时深啃——搞懂一个即可。

---

## 3. Spring AI 核心概念（对照 Spring Boot）

| Spring AI | 你熟悉的 Spring | 干什么 |
|-----------|-----------------|--------|
| `ChatClient` | `RestTemplate` 升级版 | 发 messages，拿回复 |
| `ChatModel` | 底层 HTTP 客户端 | 对接 OpenAI / 通义 / Ollama |
| `EmbeddingModel` | — | 文本 → 向量（RAG 用） |
| `VectorStore` | 像 `RedisTemplate` | 存向量、做相似搜索 |
| `@Tool` / `FunctionCallback` | `@Service` 方法 | 注册给模型调用的 Java 方法 |
| `Advisor` | `Filter` 链 | 先 RAG 检索再 Chat |

---

## 4. 最小依赖思路（概念）

```xml
<!-- 概念示意，版本以官方文档为准 -->
spring-ai-openai-spring-boot-starter
<!-- 或 spring-ai-ollama / 国产兼容 starter -->
```

配置 `application.yml`：

```yaml
spring.ai.openai.api-key: ${OPENAI_API_KEY}
spring.ai.openai.chat.options.model: gpt-4o-mini
```

和现有 `mgdemoplus.*`、`.env` 分层一样：**Key 走环境变量**。

---

## 5. 本地玩：Ollama（可选）

- 本机跑小模型，**不烧云 API 费**  
- Spring AI 有 Ollama starter，`base-url: http://localhost:11434`  
- 适合 **Prompt / RAG 调试**；上线可用云模型

---

## 6. 和现有 MGDemoPlus LLM 代码

`DpLlmNpcDecisionService` 可能是 **手写 HTTP** 或已有客户端——迁移到 Spring AI 时：

- **复用**：模型 URL、Key、超时配置  
- **新建**：`assistant` 包，ChatClient + Tool，**不要**和 NPC 出牌逻辑耦在一个类里

---

## 7. 别走偏：应用岗 vs 训练岗

| 要学 | 可先不学 |
|------|----------|
| Chat / RAG / Tool / Agent | PyTorch 手写神经网络 |
| Spring AI 文档 | Transformer 论文推导 |
| 向量库入门 | 自己训 7B 模型 |

---

## 记忆小口诀

> Java 做 Agent，Spring AI 当先；  
> Chat 像调接口，Vector 存片段；  
> Tool 注解 Service，Advisor 链上串；  
> Python 作参考，不必换语言盘。

---

**下一篇**：[07-mgdemoplus-practice-map.md](./07-mgdemoplus-practice-map.md)
