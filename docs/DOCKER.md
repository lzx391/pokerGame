# Docker Compose：本地开发（Nginx + MinIO）

本仓库根目录 **`docker-compose.yml`** 仅编排 **Nginx**（HTTP 入口）与 **MinIO**（对象存储）。**MySQL、Redis、Spring Boot 后端在宿主机运行**（`mvn spring-boot:run`）。

## 快速启动

```powershell
# 0. 构建前端（Nginx 直接托管 dist；mvn spring-boot:run 不会打入 static）
cd front/dp_game
npm install
npm run build
cd ../..

# 1. Docker：Nginx + MinIO
docker compose up -d

# 2. 宿主机：MySQL、Redis 已就绪后，起双 JVM（各开一个终端）
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8088 --mgdemoplus.instance-id=8088"
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8089 --mgdemoplus.instance-id=8089"
```

或使用 `scripts/run-multi-instance.ps1 -DepsOnly` 代替第 1 步。

- **经 Nginx（推荐）**：`http://localhost:8880`（hash 路由如 `/#/login`）
- **直连 JVM（调试）**：`http://localhost:8088`、`http://localhost:8089`
- **MinIO 控制台**：`http://localhost:9001`（`minioadmin` / `minioadmin`）

停止 Docker：`docker compose down`。仅删 MinIO 数据卷：`docker compose down -v`。

## 栈组成

| 服务 | 说明 |
|------|------|
| `nginx` | HTTP 反代，轮询宿主机 `8088` / `8089`；配置 `docker/nginx/default.conf` |
| `minio` | 对象存储 API **9000**、控制台 **9001** |

宿主机默认连接：

| 组件 | 默认地址 |
|------|----------|
| MySQL | `localhost:3306`，库 `school_db`（见 `application.yml`） |
| Redis | `localhost:6379` |
| MinIO | `http://127.0.0.1:9000`（`.env.example` / `application.yml` 已对齐） |

表结构由应用启动时 **Flyway** 执行（`src/main/resources/db/migration/`）。

## Nginx → 宿主机 JVM

Nginx 容器通过 **`host.docker.internal`** 访问宿主机上的 Spring Boot：

- `host.docker.internal:8088`
- `host.docker.internal:8089`

`docker-compose.yml` 为 nginx 配置了 `extra_hosts: host.docker.internal:host-gateway`（Linux 亦可用）。Windows / macOS Docker Desktop 原生支持该主机名。

静态前端由 **Nginx 挂载** `front/dp_game/dist`（须先 `npm run build`）。仅 API、WebSocket、对象存储读路径（`/dp/`、`/dpRoom/`、`/oauth/`、`/ws/`、`/images/` 等）反代到宿主机 JVM；页面与 `js`/`css` 由 Nginx 本地返回。

## 环境变量

- 复制 **`.env.example`** → **`.env`**（勿提交 Git）；本机 `mvn spring-boot:run` 由 `LocalDotenvLoader` 加载。
- MinIO 默认：`MGDEMOPLUS_MINIO_ENDPOINT=http://127.0.0.1:9000`。
- OAuth / 前端基址经 Nginx 时：`FRONTEND_BASE_URL=http://localhost:8880`。

映射说明见 [docs/ENV_README.md](ENV_README.md)、[docs/CONFIG_LAYERS.md](CONFIG_LAYERS.md)。

## Docker Compose 速查

| 项 | 说明 |
|----|------|
| 启动 | `docker compose up -d` |
| 查看日志 | `docker compose logs -f nginx minio` |
| 重载 Nginx 配置 | `docker compose exec nginx nginx -t && docker compose exec nginx nginx -s reload` |
| 停止 | `docker compose down` |

Nginx 细节见 [docs/NGINX.md](NGINX.md)。
