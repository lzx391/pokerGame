# 扫描：Controller / API 层
> 自动生成，供 README 综合使用

## 概览

共 **7** 个 `@RestController`：无独立 `dto` 包；牌谱使用 `vo`。统一 JWT（`Authorization: Bearer`）；`GET /dp/social/stream` 另支持 `?token=`。业务 JSON 多为 `ResultUtil`；房间写操作仍多返回 `"ok"` / `"fail"` 字符串。

## 模块清单

| 前缀 | 类 | 说明 |
|------|-----|------|
| `/dpUser` | `DpUserController` | 注册、登录、`loginProfile`、改资料 |
| `/dpRoom` | `DpRoomController` | 建房、进退房、快匹、对局、Bot、大厅列表 |
| `/dp` | `DpFriendMailboxController` | 好友、邮箱、邀请、私信、跟随观战、站点心跳 |
| `/dp/social` | `DpSocialController` | SSE 流、`notify-summary` |
| `/dpHandHistory` | `DpHandHistoryController` | 牌谱列表/详情 |
| `/dpMusic` | `DpMusicController` | 曲库上传/列表 |
| `/upload` | `UploadController` | 图片上传（根路径） |

**已移除（README 勿再写）**：`/demo/*`、`/student`、`/movie`；`POST /dp/siteHeartbeat`（现为 `POST /dp/presence/site-heartbeat`）。

## README 建议补充点

- 区分 `permitAll`、JWT、JWT + nickname 与 token subject 一致（`joinRoom2`、`quickMatch2`）。
- SSE 与 WebSocket 分章交叉引用。
- 牌谱 API 的 `userId` 须为当前登录用户 id。
