# 08 · 9 周学习计划：每周干什么、怎么验收

> **配套文档**：01～07 概念 · 09 面试速记  
> **可压缩**：全职可压到 4～5 周；在职按 9 周稳一点

---

## 总览

| 周 | 主题 | 读 | 练 | 验收 |
|----|------|----|----|------|
| 1 | Chat 打底 | 01 | Postman/curl 调一次 Chat API | 能解释 system/user/assistant |
| 2 | Prompt | 02 | 写扑克助手 system，对比有无幻觉 | 多轮 history 能带上 |
| 3 | Spring AI Hello | 06 | Boot 项目 `ChatClient` 问一句 | 控制台有回复 |
| 4 | RAG 入门 | 03 | 索引 `JWT.md` 两页，问答 | 问白名单能答对 |
| 5 | RAG 加深 | 03 | 加 `DPGAME.md` + 快匹 doc | 问快匹流程基本对 |
| 6 | Tool 一个 | 04 | `search_hand_history` 接只读 API | Postman 测 Tool 循环 |
| 7 | Tool + 导航 | 04 | `navigate` 白名单 + 前端按钮 | 说「去快匹」能跳转 |
| 8 | Agent 串起来 | 05·07 | 多步：查牌谱 + 跳转 | Demo 视频 2 分钟 |
| 9 | 打磨 | 09 | 20 条测试问句 + 日志 | 面试稿能背 30 秒 |

---

## Phase 对照

| Phase | 周 | 文档 |
|-------|-----|------|
| 0 打底 | 1 | 01 |
| 1 Chat+Prompt | 1～2 | 01～02 |
| 2 RAG | 4～5 | 03 |
| 3 Tool | 6～7 | 04 |
| 4 Agent | 8 | 05·07 |
| 5 工程化 | 9+ | 06·09 |

---

## 常见误区（避坑）

| 误区 | 正解 |
|------|------|
| 先学 PyTorch | 先 Chat → RAG → Tool |
| RAG 替数据库 | 牌谱必须 Tool |
| 模型传 userId | JWT 在 Java 取 |
| 一次做 10 个 Tool | MVP 两个就够 |
| Copilot 改 roomMap | 对局仍走 room 服务 |

---

## 和 Java 后端学习并行

- **上午**：Spring / 本项目 Controller 阅读  
- **下午**：按上表 AI 练习 1～2 小时  
- **周末**：整链路 Demo + 更新个人笔记

---

## 验收清单（第 8 周末）

- [ ] 助手 API 需 JWT  
- [ ] RAG 能答 JWT/快匹文档问题  
- [ ] Tool 查牌谱用当前用户  
- [ ] navigate 仅白名单  
- [ ] 能画 ReAct 想-做-看 图  
- [ ] 能口述 RAG vs Tool 分工  

---

## 记忆小口诀

> 一周一主题，别贪多；  
> Chat 打地基，RAG Tool 往上摞；  
> 第六周查谱，第七周跳转；  
> 第八串 Agent，第九面试练。

---

**下一篇**：[09-interview-and-mnemonics-master.md](./09-interview-and-mnemonics-master.md)
