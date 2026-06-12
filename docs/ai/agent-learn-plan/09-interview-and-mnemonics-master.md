# 09 · 面试速记 & 全系列口诀 master

> **用法**：面试前 15 分钟扫一遍；忘了细节回翻 01～08

---

## 1. 30 秒项目介绍（背这个）

> 扑克平台 Spring Boot。我做了 **AI 大厅助手**：**RAG** 检索项目文档答流程规则；**Function Calling** 查用户牌谱、跳转快匹；**JWT** 鉴权，Tool 不信任模型传的 userId。对局 **LLM NPC** 与助手分模块。模型 HTTP API，Java **Spring AI** 编排，可拆微服务。

---

## 2. 高频概念题

### Q：RAG 是什么？和数据库区别？

**A**：检索文档片段塞进 prompt 再生成，减少幻觉。管 **知识**；MySQL 管 **实时数据**。查牌谱走 Tool，不走 RAG。

### Q：Agent 和 Chatbot？

**A**：Agent 多步、会调 Tool；Chatbot 多是一问一答。ReAct：想→做→看 循环。

### Q：Tool 谁执行？

**A**：Java Service。模型只出 JSON；**userId 从 JWT 取**。

### Q：Java 还是 Python？

**A**：应用层 Java + Spring AI 够用；模型是 API；Python 作参考不必重学。

### Q：OAuth 和 JWT？（常和 AI 混在一起问）

**A**：OAuth **进门**（第三方登录）；JWT **进门后的工牌**（每次请求）。Agent API 也用 JWT。

---

## 3. RAG vs Tool vs Agent 一张表

| | 解决啥 | 口诀 |
|--|--------|------|
| RAG | 文档/FAQ | **开卷答知识** |
| Tool | 查库/跳转 | **动手查事实** |
| Agent | 多步目标 | **开卷 + 动手 + 循环** |

---

## 4. 安全三句话

1. API Key 只在服务端  
2. Tool 禁止模型传 userId  
3. navigate 路径白名单  

---

## 5. 全系列超级口诀（一页汇总）

### 01 LLM

> 大模型爱接话，API 就像发 POST；  
> system 定规矩，幻觉要提防。

### 02 Prompt

> System 立规矩，历史要带全；  
> 不知道就说，别编项目门。

### 03 RAG

> 文档先切片，变成小向量；  
> 知识开卷答，事实查库帮。

### 04 Tool

> 模型出主意，Java 动手脚；  
> JWT 定身份，跳转白名单。

### 05 Agent

> 想完做一做，结果看一看；  
> 意图加槽位，多步才叫 Agent。

### 06 Java 栈

> Spring AI 当先，不必换 Python。

### 07 本项目

> NPC 管出牌，Copilot 管办事。

### 08 学习节奏

> 一周一主题，第八串 Demo，第九面试练。

### 09 总诀

> **OAuth 进门 JWT 牌，RAG 讲知识 Tool 查；  
> Agent 多步走，Java 后端照样 AI 扛。**

---

## 6. 还可能问的

| 问题 | 简答 |
|------|------|
| 向量库选型 | Demo 内存 → 生产 Redis/pgvector |
| 流式输出 | SSE 打字机，Spring AI `stream` |
| 评估 Agent | 固定 20 问，看 Tool 选对率 |
| 多实例 | 助手无状态轮询；对局 Redis 见 refactor 计划 |

---

## 7. 文档索引

| 文件 | 主题 |
|------|------|
| [README](./README.md) | 总目录 |
| 01～08 | 逐章学习 |
| 本文 | 面试 + 口诀 |

---

**读完全系列后**：动手做 [07](./07-mgdemoplus-practice-map.md) 里的 Copilot MVP，比背十遍本文有用。
