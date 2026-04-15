# 双 JVM 开发：Docker Nginx 同域

用 **一个浏览器入口（例如 `:8880`）** 同时访问 Vue、`/dev-api`（JVM A）、`/b-api`（JVM B）和两条 WebSocket 路径，避免跨域。  
房间状态仍在**各 JVM 内存**；跨节点靠 `**dp_room_registry` + Redis 路由** 与前端按 `lookupRoom` 返回的 `**wsRoute`** 选对 HTTP 前缀。

---

## Nginx 路由（与 `dp-dev-two-jvm.example.conf` 一致）


| 浏览器路径                | 转发到                         | 用途                                   |
| -------------------- | --------------------------- | ------------------------------------ |
| `/`                  | Vue devServer（默认宿主机 `8080`） | 页面                                   |
| `/dev-api/` → `/`    | **8088**                    | 大厅默认 API、**未绑定房间节点时**的 `/dpRoom/*`   |
| `/b-api/` → `/`      | **8089**                    | **绑定到 B 节点房间后**的 `/dpRoom/*`（除白名单接口） |
| `/dp-ws/` → `/ws/`   | **8088**                    | 游戏 WS，后端真实路径 `/ws/dp-game`           |
| `/dp-ws-b/` → `/ws/` | **8089**                    | 同上，第二套 JVM                           |


**要点：** `/dev-api` **永远只进 8088**；`/b-api` **永远只进 8089**。进房、`joinRoom2` 等必须打到**有房的那台**，否则内存里没有房间 → 「房间不存在」。

---

## 前置

1. **两个 Spring Boot**：例如 **8088**、**8089**；共用 **同一 MySQL、同一 Redis**（房间注册与路由缓存）。
2. **集群建房（可选）**：`dp.game.cluster.enabled=true` 时，在 `node-http-bases[0]`、`[1]` 上随机选房；各实例 `**dp.game.cluster.internal-token`** 一致。两台都要能互相访问对方 HTTP（本机即 `127.0.0.1:8088` / `8089`）。
3. **前端**：`front/dp_game` 执行 `npm run dev`（端口与 conf 里 `vue_dev` 一致，常见 **8080**）。
4. **浏览器**：通过 Nginx 打开（如 `**http://127.0.0.1:8880/`**），不要直接用 `:8080` 进前端，否则 API 不经 Nginx、易跨域或走错实例。

---

## 每实例环境变量（与 Nginx 对齐时推荐）

经 **8880** 访问时，`DP_GAME_PUBLIC_WS_URL` 应带 **Nginx 上的路径前缀**，便于前端识别走 `/b-api` 还是 `/dev-api`：


| 实例  | `SERVER_PORT` | `DP_GAME_SHARD_ID`（示例） | `DP_GAME_PUBLIC_WS_URL`（示例，与 `-p 8880:80` 一致） |
| --- | ------------- | ---------------------- | --------------------------------------------- |
| A   | `8088`        | `local-1`              | `ws://127.0.0.1:8880/dp-ws/dp-game`           |
| B   | `8089`        | `local-2`              | `ws://127.0.0.1:8880/dp-ws-b/dp-game`         |


**必须保证：`server.port` 与 URL 里「最终连到的那台」一致。** 不要出现「进程监听 8088，却把 `public-ws-url` 写成 `:8089`」——登记到库里的路由会错，客户端也会连错。

若暂时 **直连端口**（如 `ws://127.0.0.1:8089/ws/dp-game`），前端开发环境可用 `**front/dp_game/.env.development`** 里 `**VUE_APP_DEV_JAVA_PORT_B=8089**`，按端口选中 `/b-api`（见 `dpRoomNodeContext.js`）。

---

## 前端 axios 规则（简述）

- **`GET /dpRoom/publicRooms`**（大厅分页列表）：不经「房间节点」上下文；**未配置** `VUE_APP_LOBBY_API_BASES` 时与 **`/dpRoom/lookupRoom`、`/dpRoom/createRoom`** 一样走默认 `**/dev-api**`。若构建时设置 **`VUE_APP_LOBBY_API_BASES=/dev-api,/b-api`**（逗号分隔），则**仅**该请求在发出前随机选其中一个前缀，便于双 JVM 下观察两台日志。
- **`/dpRoom/lookupRoom`、`/dpRoom/createRoom`**：始终走默认 `**/dev-api**`（不经房间节点上下文）。
- **进房后** `sessionStorage` 里会记下 `lookupRoom` 返回的 `**wsRoute`**；之后 `**/dpRoom/***`（除上述白名单）会按 `wsRoute` 选 `**/dev-api` 或 `/b-api**`，与上表一致。

---

## 一条命令（PowerShell，仓库根目录）

路径按你的磁盘改；Linux 去掉 ``` 换行，用 `\` 续行。

```powershell
docker run --rm -d --name dp-nginx-dev -p 8880:80 `
  -v "${PWD}/docker/nginx/dp-dev-two-jvm.example.conf:/etc/nginx/conf.d/default.conf:ro" `
  --add-host=host.docker.internal:host-gateway `
  nginx:alpine
```

浏览器访问：**[http://127.0.0.1:8880/](http://127.0.0.1:8880/)**  

停止：`docker stop dp-nginx-dev`

**说明：** `-p 8880:80` 把容器 80 映到本机 8880；`host.docker.internal` 让容器访问宿主机上的 8088/8089/8080。Linux 需 `--add-host=host.docker.internal:host-gateway`；若解析失败可改 `upstream` 为 `172.17.0.1` 等。Vue 不是 **8080** 时，编辑 conf 里 `**upstream vue_dev`** 端口。

---

## 不用 Docker、只在本机跑 Nginx

把同一份 `dp-dev-two-jvm.example.conf` 放进本机 `conf.d`，或 `include` 到 `http { }`；`upstream` 里的 `host.docker.internal` 改为 `**127.0.0.1**`，重载 Nginx 即可。

---

## 排错


| 现象                                     | 常见原因                                                                                                                             |
| -------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `joinRoom2` 报「房间不存在」，但 `lookupRoom` 正常 | HTTP 仍走了 `**/dev-api**`，请求打到 8088，房在 8089。检查 `**wsRoute**` 是否含 `**dp-ws-b**`，或 dev 下 `**VUE_APP_DEV_JAVA_PORT_B**` 是否与 B 实例端口一致。 |
| WebSocket 连不上                          | `DP_GAME_PUBLIC_WS_URL` 与 Nginx `**/dp-ws` / `/dp-ws-b**` 不一致，或端口与 `**server.port**` 不一致。                                        |
| 大厅无房或 lookup 无数据                       | 未建 `**dp_room_registry` 表**、或 Redis/MySQL 与实例不一致。                                                                                |


更完整的 DP 多实例说明见仓库根目录 `**docs/DP_GAME_REDIS_CLUSTER.md`**、`**docs/WEBSOCKET.md**`。
起后端
```powershell
$env:SERVER_PORT = "8088"
$env:DP_GAME_SHARD_ID = "local-1"
$env:DP_GAME_PUBLIC_WS_URL = "ws://127.0.0.1:8088/ws/dp-game"
mvn spring-boot:run
```

