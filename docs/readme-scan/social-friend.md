# 扫描：社交 / 好友 / 信箱
> 自动生成，供 README 综合使用

## 功能状态

| 能力 | 说明 |
|------|------|
| MVP 十接口 | 好友申请、邮箱、进房邀请 60s — 见 [dp_friend_mailbox_mvp.md](../dp_friend_mailbox_mvp.md) |
| 扩展已实现 | 私信、删好友、站点心跳、跟随观战、`GET /dp/social/stream` SSE |
| 限制 | SSE 仅本 JVM（`SocialSseHub`）；多实例需 Nginx `proxy_buffering off` |

## API 要点

- **REST**：`/dp/*`（JWT）；站点配置 `GET /dp/presence/site-heartbeat/config` 匿名。
- **SSE**：`GET /dp/social/stream`（Bearer 或 `?token=`）；`event: notify` 聚合邮箱与私信未读；`GET /dp/social/notify-summary` 兜底。
- **数据表**：V1 好友/邀请；V2 `dp_friend_message`、`dp_friend_chat_read`。

## 配置

- `spring.mvc.async.request-timeout: -1`（SSE）
- `mgdemoplus.dp-site-presence-ttl-ms`（默认 90s）
- 联调日志包：`com.example.mgdemoplus.social` 等（见 `application.yml` 注释块）
