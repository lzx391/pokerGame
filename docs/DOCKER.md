# Docker 与本地配置说明

## 代码上 Gitee 和 Docker 打包是两件事

- **`docker compose up --build`**：在你**自己电脑**上根据**当前目录里的代码**构建镜像并启动容器，**不会**自动把任何东西推到 Gitee。
- **给别人用最新代码**：你需要 **`git add` → `git commit` → `git push`** 推到 Gitee；对方 **`git pull`** 后再执行 `docker compose up --build`（有代码或 Dockerfile 变更时加 `--build`）。

镜像默认只存在本机；若要用「别人只拉镜像不拉代码」，需要另行使用镜像仓库（本说明不展开）。

---

## 与本项目相关的配置改动说明

### 1. `WebConfig.java`：图片静态目录可配置

原先 `/images/**` 写死为本机路径 `file:P:/javaworkspace/MGDemoPlusFiles/`，在 Linux 或 Docker 容器里不可用。

- **配置项**：`mgdemoplus.images.file-location`
- **默认**：仍为 `file:P:/javaworkspace/MGDemoPlusFiles/`（本机 Windows 开发习惯不变）
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

### DBeaver / JDBC 连接 Docker 内 MySQL 示例

```
jdbc:mysql://localhost:3307/school_db?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8
```

若出现 `Public Key Retrieval is not allowed`，请保留 **`allowPublicKeyRetrieval=true`**。

---

## 数据库初始化与后续变更

- **首次**：`school_db` 可能无业务表，需自行执行 DDL/转储或把初始化 SQL 放入 `docker-data/mysql-init/`（仅**数据卷首次为空**时执行）。
- **后续表结构变更**：建议在仓库中维护**增量 SQL**（如 `db/migrations/`），成员 pull 后在本地对 Docker 库（3307）或本机库执行；勿依赖向 `mysql-init` 增文件来更新已有数据卷（不会再次自动执行）。

更简要的入口说明见仓库根目录 `README.md` 中的「Docker 部署」小节。
