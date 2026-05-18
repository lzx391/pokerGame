# 扫描：Service / 业务层
> 自动生成，供 README 综合使用

## 概览

对局热状态在 **`DpRoomServiceImpl.roomMap`（内存）**；MySQL 持久化用户、大厅、牌谱、社交、曲库；Redis 用于登录 jti、大厅分页缓存、曲库列表。**NPC 双轨**：`DpNpcEngine`（规则同步）与 `DpLlmNpcDecisionService`（方舟兼容 API 异步）。

## 核心服务

| 服务 | 职责 |
|------|------|
| `DpRoomServiceImpl` | 房间、快匹、对局、Bot 出手、WebSocket 推送 |
| `DpRoomHallServiceImpl` | `dp_room_lobby` + Redis 缓存 |
| `DpFriendSocialService` | 好友、邀请邮箱、跟随进房 |
| `DpFriendChatService` | 好友私信 |
| `DpNpcEngine` / `DpLlmNpcDecisionService` | 规则 Bot / LLM Bot |
| `DpHandHistoryObservedImpl` → `DpHandHistoryPersistServiceImpl` | 牌谱观测与落库 |

## README 建议补充点

- 明确 roomMap 不在 Redis；多实例需粘滞或自建共享状态。
- 快匹三阶段：索引扫房 → 内存队列 → `DpQuickMatchPairingCoordinator`。
- `dp_shark_opponent_profile` 表保留但服务层不再读写。
