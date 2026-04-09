# 接入 Nginx（反向代理）

本仓库在 **Docker Compose** 里增加了 **Nginx** 服务：对外只暴露 **80** 端口，把 HTTP 与 **WebSocket** 转发到同一网络内的 **`app:8088`**（Spring Boot 仍负责 API、静态资源、对局 WS）。

## 1. 做了什么

| 文件 | 作用 |
|------|------|
| `docker/nginx/default.conf` | Nginx 站点配置：`/` → 应用；`/ws/` → 同一后端（带 WebSocket 升级头） |
| `docker-compose.yml` 中的 `nginx` 服务 | 镜像 `nginx:alpine`，映射宿主机 **80:80**，挂载上述配置 |

浏览器访问 **`http://localhost`**（无需 `:8088`）即可打开站点；对局页 WebSocket 仍为 **`/ws/dp-game`**，经 Nginx 时自动升级为 `ws://localhost/ws/dp-game?...`（与页面同主机、同端口）。

## 2. 启动步骤

在仓库根目录：

```bash
docker compose up --build
```

- **经 Nginx**：浏览器打开 `http://localhost`（hash 路由如 `/#/login`）。
- **直连 Spring Boot**（可选）：仍可用 `http://localhost:8088`（compose 里保留了端口映射，便于调试）。

若本机 **80 端口已被占用**（如本机 IIS、其它 Nginx），请改 `docker-compose.yml` 里 nginx 的端口映射，例如 `"8080:80"`，然后访问 `http://localhost:8080`。

## 3. 配置要点（你改参数时看这里）

- **上游地址**：`proxy_pass http://app:8088;` — `app` 是 compose 里 Spring 服务名，**不要**写 `127.0.0.1`（那是容器自己，不是应用容器）。
- **WebSocket**：`location /ws/` 中必须包含 `Upgrade`、`Connection`（本配置用 `map $http_upgrade $connection_upgrade`），并建议拉长 `proxy_read_timeout`，避免长连接被过早断开。
- **上传**：已设 `client_max_body_size 50m`，与常见 multipart 上传一致；若不够可再调大。
- **HTTPS**：若以后要 **443**，需在 Nginx 上配置 `ssl_certificate` / `listen 443 ssl`，或前面再加一层云负载均衡终止 TLS；本仓库默认只提供 **HTTP 80** 示例。

## 4. 仅本机开发、不用 Docker 时

可以在 Windows 上单独装 Nginx，把 `proxy_pass` 指到 **`http://127.0.0.1:8088`**，逻辑与上面相同；或继续只用 Spring Boot 的 8088，不必强制上 Nginx。

## 5. 关闭对外 8088（可选）

若希望 **公网只暴露 80**，不暴露应用端口：在 `docker-compose.yml` 里删除或注释 `app` 下的 `ports: - "8088:8088"`，仅保留内部网络访问 `app`。本机调试时再临时加回端口映射。
