# Nginx 反向代理：本地多实例（HTTP only）

本仓库 **本地开发**：Nginx 对外 **8880**（映射容器 80），**直接托管** `front/dp_game/dist`；仅后端路径轮询转发到宿主机 **`host.docker.internal:8088`** 与 **`:8089`**（本机 `mvn spring-boot:run` 双实例）。构建前端见 [docs/DOCKER.md](DOCKER.md)。

## 1. 配置文件

| 文件 | 用途 |
|------|------|
| `docker/nginx/default.conf` | 唯一 Nginx 配置：纯 HTTP、双 JVM 轮询；compose 挂载此文件 |

浏览器访问 **`http://localhost:8880`**；对局 WebSocket 为 **`/ws/dp-game`**（与页面同主机、同端口）。

## 2. 启动步骤

```powershell
cd front/dp_game && npm install && npm run build && cd ../..
docker compose up -d          # Nginx + MinIO
# 宿主机另起 8088 / 8089 两个 JVM（见 docs/DOCKER.md）
```

- **经 Nginx（推荐）**：`http://localhost:8880`（hash 路由如 `/#/login`）。
- **直连 Spring Boot（调试）**：`http://localhost:8088`、`http://localhost:8089`。

若 **8880** 被占用，改 `docker-compose.yml` 中 nginx 的 `"8880:80"` 为其它端口。

## 3. 配置要点

- **静态**：`root /usr/share/nginx/html` ← compose 挂载 `./front/dp_game/dist`。
- **上游**：`upstream mgdemo_backend` 含 `host.docker.internal:8088`、`host.docker.internal:8089` — compose 为 nginx 配置 `extra_hosts`。
- **反代前缀**：`/dp/`、`/dpRoom/`、`/dpUser/`、`/dpHandHistory/`、`/dpMusic/`、`/dpDownload/`、`/oauth/`、`/images/`、`/music/`、`/files/`、`/ws/` 等（见 `default.conf`）。
- **WebSocket**：`location /ws/` 须含 `Upgrade`、`Connection`（`map $http_upgrade $connection_upgrade`），并拉长 `proxy_read_timeout`。
- **SSE（大厅）**：`location = /dp/social/stream` 须 **`proxy_buffering off`**、`gzip off`、长 `proxy_read_timeout`。
- **上传**：`client_max_body_size 50m`；可按需调大。

## 4. 重载 Nginx

改 `default.conf` 后：

```bash
docker compose exec nginx nginx -t && docker compose exec nginx nginx -s reload
```

或 `docker compose up -d --force-recreate nginx`。

## 5. 关闭对外 8088/8089（可选）

若只希望经 Nginx 访问，本机 JVM 可只监听 `127.0.0.1`（Spring Boot `server.address`），不对外暴露端口。
