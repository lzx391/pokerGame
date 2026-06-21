# 时区策略（MGDemoPlus）

## 业务时区

- **统一业务时区：`Asia/Shanghai`（UTC+8）**
- 应用容器：`TZ`、`MGDEMOPLUS_TIME_ZONE`
- JDBC：`serverTimezone=Asia/Shanghai`
- Docker MySQL（prod / dev）：`TZ=Asia/Shanghai` + `--default-time-zone='+08:00'`

## 游戏与关键计时

- 局内逻辑、超时、倒计时等：**优先 epoch 毫秒（`long` / `BIGINT`）**
- 不依赖 MySQL `NOW()` 驱动实时对局节奏
- 内存房间状态以进程内时间为准

## 新表与新接口约定

| 场景 | 推荐 |
|------|------|
| 需人类可读、按日统计 | `DATETIME(3)`（语义为上海本地时刻） |
| 精确排序、跨服务对齐 | `BIGINT` 毫秒时间戳 |
| REST / WebSocket 对外 | 优先返回 **epoch millis**；字符串仅作展示 |

## 存量（Legacy）

- 历史表混用 `TIMESTAMP` 与 `DATETIME`，行为受会话时区影响
- **P0 不做 Flyway 迁移**，仅对齐容器/MySQL 默认时区
- 改表前对照本策略，避免新旧语义冲突

## P0 已做变更（2025-06）

- `docker-compose-prod.yml`、`docker-compose.yml` 的 `mysql` 服务增加：
  - `environment.TZ: Asia/Shanghai`
  - `command: --default-time-zone='+08:00'`
- 目的：prod 与 dev MySQL 默认时区一致，减少 `NOW()` / `TIMESTAMP` 与 JDBC 偏差

**重部署注意**：修改 MySQL `command` 后通常需 **重建 mysql 容器**（`docker compose up -d --force-recreate mysql`）。已有 `mysql_data` 卷一般可保留；若仍异常再排查数据卷初始化时机。

## P2 全球化（未来）

- 存储：**UTC**（或统一 epoch millis）
- 展示：按用户 locale / 时区转换
- 需单独设计 API 字段命名（如 `createdAtMs` vs `createdAtLocal`）

## 快速检查

```sql
SELECT @@global.time_zone, @@session.time_zone, NOW();
-- 期望：+08:00 / +08:00，NOW() 为上海本地时刻
```
