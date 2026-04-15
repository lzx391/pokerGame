# DP 游戏多实例（精简说明）

此前实现的「跨机 Redis 房间快照 + Pub/Sub 驱动另一台 JVM 上的 WebSocket」已移除：同一房间只认**建房实例内存状态**，所有人应通过 `lookupRoom` 返回的 **`wsRoute` / `apiBase`** 连到同一台（见 `DpGameInstanceProperties`、`DpRoomRegistryService`、集群建房 `DpRoomClusterPlacementService`）。

多 JVM 开发时 HTTP/WebSocket 同域转发请用 **`docker/nginx/dp-dev-two-jvm.example.conf`**；前端 **`vue.config.js`** 不配代理（见 **`docker/nginx/README-dp-dev-two-jvm.md`**）。

Redis 仍用于：`dp:room:route:*`（房间路由）、`dp_room_registry`（MySQL）、以及项目其它已有缓存与业务；**不再**写入 `dp:room:state:*` 或推送频道。
