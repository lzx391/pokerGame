# `docs/notes/` — 学习备忘（非运维必读）

> **核对日期**：2026-05-25  
> **权威来源**：无运行时契约；代码示例以 `src/main/java` 为准  
> **Status**: maintained（索引页）

本目录存放**与 MGDemoPlus 运行无关**的自学、面试、语言基础类 Markdown。运维、部署、对局协议请以 **[docs/README.md](../README.md)** 与专题文档为准。

## 使用说明

| 项 | 说明 |
|----|------|
| 受众 | 学习者、面试复习；**不是**新同学跑通项目的必读路径 |
| 维护 | 可不随每个 Flyway/发版同步；代码引用以 `src/` 为准 |
| `roomMap` | 文中涉及房间对象时，均指 **单机 JVM** `DpRoomRegistry.roomMap`，非 Redis |

## 索引

| 文件 | 主题 |
|------|------|
| [自学.md](自学.md) | 作者自学路线备忘 |
| [Spring依赖注入复习笔记.md](Spring依赖注入复习笔记.md) | Spring DI 中文笔记 |
| [SPRING_DI_NOTES.md](SPRING_DI_NOTES.md) | Spring DI 英文/对照要点 |
| [BACKEND_INTERVIEW_QUESTIONS.md](BACKEND_INTERVIEW_QUESTIONS.md) | 结合本仓库的后端面试题 |
| [Json-map-serialization.md](Json-map-serialization.md) | JSON / Map / 序列化 |
| [Java对象引用与浅拷贝深拷贝备忘.md](Java对象引用与浅拷贝深拷贝备忘.md) | 引用与拷贝（含 `roomMap` 示例） |

## 与 `docs/` 根目录的关系

学习类正文**仅**保留在本目录；`docs/` 根下同名文件已删除。调度文档等维护向链接见 [SpringScheduling.md](../SpringScheduling.md) → 本目录 DI 笔记。
