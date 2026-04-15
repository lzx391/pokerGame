# DP 游戏多实例（精简说明）

此前实现的「跨机 Redis 房间快照 + Pub/Sub 驱动另一台 JVM 上的 WebSocket」已移除：同一房间只认**建房实例内存状态**，所有人应通过 `lookupRoom` 返回的 **`wsRoute` / `apiBase`** 连到同一台（见 `DpGameInstanceProperties`、`DpRoomRegistryService`、集群建房 `DpRoomClusterPlacementService`）。

多 JVM 开发时 HTTP/WebSocket 同域转发请用 **`docker/nginx/dp-dev-two-jvm.example.conf`**；前端 **`vue.config.js`** 不配代理（见 **`docker/nginx/README-dp-dev-two-jvm.md`**）。

Redis 仍用于：`dp:room:route:*`（房间路由）、`dp_room_registry`（MySQL）、以及项目其它已有缓存与业务；**不再**写入 `dp:room:state:*` 或推送频道。

---

## 大厅列表缓存与 revision（版本号）失效

`GET /dpRoom/publicRooms` 按页读 `dp_room_registry`，并在 Redis 里缓存**整页 JSON**（含 `list`、`total`、`page`、`pageSize`），减轻 MySQL 压力。TTL 见 `application.properties` 中 **`mgdemoplus.cache.dp-room-public-rooms-ttl-seconds`**（环境变量 `MGDEMOPLUS_CACHE_DP_ROOM_PUBLIC_ROOMS_TTL_SECONDS`）。

若每变一次房就只删「某一页」的 key，就要枚举「缓存过哪些 `page` / `pageSize`」，容易漏。做法是引入 **revision（整表版本号）**：

1. 单独存一个计数键 **`mgdemo:cache:dpRoom:publicRooms:rev`**，注册表有变动时对其 **`INCR`**。
2. 分页缓存的真实 key 形如：  
   **`mgdemo:cache:dpRoom:publicRooms:data:{rev}:{page}:{pageSize}`**  
   读缓存时先取当前 `rev`，再拼 key；**rev 一变，所有旧 key 永远不会再被访问**，等价于**一次作废全部分页**，无需按页点名删除。
3. 实现上在 revision 递增后还会对 **`mgdemo:cache:dpRoom:publicRooms:data:*`** 做 **SCAN + 批量 DELETE**，让旧 key 立刻释放，不必只靠 TTL 过期。

失效触发集中在 `DpRoomRegistryService`：**`registerNewRoom`**、**`syncFromRoom`**（`UPDATE` 成功或补登记/需刷新场景）、**`removeFromRegistry`**。业务侧只通过 `DpRoomServiceImpl` 调这三处，无旁路写表。Redis 异常时读接口降级直查库，不报错。

---

## 相关代码与配置

| 内容 | 位置 |
|------|------|
| 分页 + 缓存 + revision / SCAN 删 data 键 | `DpRoomRegistryService` |
| 路由键 `dp:room:route:{roomId}` | 同上，`writeRedisRoute` |
| 大厅接口 | `DpRoomController#publicRooms` |
| 前端按 `wsRoute` 选 `/dev-api` 或 `/b-api` | `front/dp_game/src/utils/dpRoomNodeContext.js`、`main.js` |

更多接口与前端约定见根目录 **`readme.md`**、**`docker/nginx/README-dp-dev-two-jvm.md`**。
