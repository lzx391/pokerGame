# Docker 与本地配置说明

## 代码上 Gitee 和 Docker 打包是两件事

- **`docker compose up --build`**：在你**自己电脑**上根据**当前目录里的代码**构建镜像并启动容器，**不会**自动把任何东西推到 Gitee。
- **给别人用最新代码**：你需要 **`git add` → `git commit` → `git push`** 推到 Gitee；对方 **`git pull`** 后再执行 `docker compose up --build`（有代码或 Dockerfile 变更时加 `--build`）。

镜像默认只存在本机；若要用「别人只拉镜像不拉代码」，需要另行使用镜像仓库（本说明不展开）。


---

## 与本项目相关的配置改动说明

### 1. `WebConfig.java`：图片静态目录可配置

原先 `/images/**` 写死为本机路径 `file:P:/javaworkspace/DPGameFiles/`（现由配置项统一），在 Linux 或 Docker 容器里不可用。

- **配置项**：`mgdemoplus.images.file-location`
- **默认**：仍为 `file:P:/javaworkspace/DPGameFiles/`（本机 Windows 开发）
- **Docker**：在 `docker-compose.yml` 里通过环境变量 **`MGDEMOPLUS_IMAGES_FILE_LOCATION=file:/data/mgdemo-files/`** 覆盖，并与卷 `./docker-data/uploads` 对应

未配置 `file:` 前缀时，代码会自动补上并保证末尾 `/`。

### 2. `application.properties`：MySQL 驱动类名

- **改为**：`spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver`
- **原因**：`com.mysql.jdbc.Driver` 为旧版类名；当前依赖为 MySQL Connector/J 8.x，与 **MySQL 8**、Spring Boot 3 搭配应使用 **`com.mysql.cj.jdbc.Driver`**。本机连 MySQL 8 与 Docker 内 MySQL 8 均适用。

### 3. `front/dp_game/src/main.js`：生产环境 API 根路径

- **开发**：`axios.defaults.baseURL` 仍为 `/dev-api`，由 `vue.config.js` 代理到后端。
- **生产 / Docker**：打包后由 Spring Boot 托管静态资源，与接口同域，故生产为 **`''`**（空字符串），请求直接发往当前站点根路径。

---

## Docker Compose 速查

| 项 | 说明 |
|----|------|
| 启动（含重建镜像） | 仓库根目录：`docker compose up --build` |
| 仅重启（代码未改） | `docker compose up` |
| 停止 | 终端 `Ctrl+C` 或 `docker compose down` |
| 应用访问 | 浏览器 `http://localhost:8088`（hash 路由如 `/#/login`） |
| 容器内 MySQL 映射到本机 | **`localhost:3307`**（避免与本机 3306 冲突） |
| 默认 root 密码 | **`mgdemo_root`**（与 `docker-compose.yml` 中 `MYSQL_ROOT_PASSWORD` 默认值一致；勿与 `application.properties` 里本机 `123456` 混淆） |
| Redis（Compose 内） | 应用经 **`redis:6379`** 连接；数据持久化卷 **`redis_data`**（AOF）。宿主机调试：**`localhost:6380`** → 容器 6379，避免占满本机常见端口 **6379** |
| Redis 口令 | 默认 **`mgdemo_redis`**；与本机 `application.properties` 里的 `ruoyi123` 不同。可用环境变量 **`REDIS_PASSWORD`** 覆盖（`redis` 服务与 `app` 的 `SPRING_DATA_REDIS_PASSWORD` 需一致） |

### Redis（Docker 与本地开发的区别）

- **Compose 启动时**：`app` 服务已设置 `SPRING_DATA_REDIS_HOST=redis`、`SPRING_DATA_REDIS_PORT=6379` 及口令，**无需**在容器里改 `application.properties`。
- **本机直接跑 Spring Boot**（不用 Compose）：仍连 **`127.0.0.1:6379`**，口令与 `application.properties` 中 **`spring.data.redis.password`** 一致（当前示例为 `ruoyi123`）；若本机未装 Redis，可只起 Compose 里的 **`redis`** 服务：`docker compose up -d redis`，再用 **`localhost:6380`** 需在 `application.properties` 里把端口改为 `6380`（或改用与 Compose 相同的口令并连 6380）。

### DBeaver / JDBC 连接 Docker 内 MySQL 示例

```
jdbc:mysql://localhost:3307/school_db?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8
```

若出现 `Public Key Retrieval is not allowed`，请保留 **`allowPublicKeyRetrieval=true`**。

---

## 数据库初始化与后续变更

- **首次**：Compose 里 MySQL 使用命名卷 `mysql_data`。若该卷**从未初始化过**，镜像会执行 `docker-data/mysql-init/00-school_db.sql`，并按顺序 `SOURCE` 仓库内 `src/main/resources/db/*.sql`，在 `school_db` 中建表。
- **`docker-entrypoint-initdb.d` 只跑一次**：仅在数据目录**空**（MySQL 第一次初始化）时执行。若你以前已经跑过 MySQL 容器，**删表、删库、DROP DATABASE** 都不会再触发 init；要复现「第一次能不能建表」，必须**删掉整个 MySQL 数据卷**，让下次启动从零初始化。

### 想清空数据卷、重新验证「首次建表」

在仓库根目录执行（会**删除该卷内所有 MySQL 数据**，包括 `school_db` 里已有数据）：

```bash
docker compose down -v
docker compose up --build
```

`-v` 会移除 compose 声明的卷（含 `mysql_data`）。下次 `up` 时 MySQL 会重新初始化并再次执行 init SQL。
本地开发只起mysql和redis的服务  
```bash
docker compose up -d mysql redis
```
若你**不想删卷**、只想清空某库里的表，可连 `localhost:3307` 手工 `DROP TABLE` / 重建库——**那只相当于清理数据**，**不会**再跑一遍 `mysql-init`；要验证 init 脚本，仍须 `down -v`。

- **后续表结构变更**：建议在仓库中维护**增量 SQL**（如 `db/migrations/`），成员 pull 后在本地对 Docker 库（3307）或本机库执行；勿依赖向 `mysql-init` 增文件来更新已有数据卷（不会再次自动执行）。

更简要的入口说明见仓库根目录 `README.md` 中的「Docker 部署」小节。
--- 
很简单的道理，说白了就是打包镜像的时候看docker compose配置呗，不看yml的，虽然yml写的3307，但docker配置写的3306，而docker服务在本地开发的时候映射出来是3307，在容器内部是3306，所以反而能连上，开发运维两不耽误