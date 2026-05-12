# Docker Compose 部署：本地构建、`docker-compose.hub` 仅镜像部署与端口映射

## 代码上 Gitee 和 Docker 打包是两件事

- **`docker compose up --build`**：在你**自己电脑**上根据**当前目录里的代码**构建镜像并启动容器，**不会**自动把任何东西推到 Gitee。
- **给别人用最新代码**：你需要 **`git add` → `git commit` → `git push`** 推到 Gitee；对方 **`git pull`** 后再执行 `docker compose up --build`（有代码或 Dockerfile 变更时加 `--build`）。

镜像默认只存在本机；若要用「别人只拉镜像不拉代码」，需要另行使用镜像仓库（本说明不展开）。

## 云服务器和本机一样「全套」：用 Git 拉仓库（不是 Docker Hub）

**Docker Hub 只存镜像，不存完整代码仓库**（没有 `docker-compose.yml`、数据库初始化脚本、`docker/nginx` 等）。  
要「和本机一样」一次起 **MySQL + Redis + 应用 + Nginx**，正确做法是：

1. 用 **`git push`** 把**整个项目**推到 Gitee/GitHub。
2. 云服务器装好 Docker（含 `docker compose`）后，**`git clone`** 仓库到某个目录，进入**仓库根目录**，执行：  
   `docker compose up -d --build`

**和 Hub 的关系**：若你希望**服务器上不 clone 仓库、只拉镜像**，见下方 **「仅镜像部署」**（需先把 **`dpgame`、`dpgame-nginx`** 推到 Hub；MySQL 使用官方 **`mysql:8.0`**，表结构由应用内 **Flyway** 在启动时迁移，见 `src/main/resources/db/migration/`）。

**示例（仓库名、分支以你实际为准）：**

```bash
git clone -b testUI https://gitee.com/SILVERSWOED/spring-boot-demo.git
cd spring-boot-demo
docker compose up -d --build
```

浏览器访问 `http://服务器公网IP`（若放行 **80** 且走 Nginx）或 **8088**（若映射了应用端口）；云厂商**安全组**需放行对应端口。

---

## 仅镜像部署（不 clone 仓库）

思路：**Nginx 配置打进 Nginx 镜像**（`Dockerfile.nginx`），应用用 **`Dockerfile` 构建的 `dpgame` 镜像**（内含 Flyway 脚本）。**MySQL 使用官方 `mysql:8.0` 镜像**，仅通过环境变量创建空库 `school_db`；**建表、改表全部由应用启动时 Flyway 执行**（`classpath:db/migration`）。编排用 **`docker-compose.hub.yml`**：只有 `image:`，无 `build:`，无 `./docker-data`、`./src/...` 等宿主机挂载。

**服务器上仍需有「这一份」YAML**（从 U 盘拷贝、浏览器下载、或 `wget` Gitee 原始文件均可），不是 Git 专属；**不需要**整份源码目录。

### 1. 在有源码的机器上构建并推送（版本号与 `IMAGE_TAG` 一致，例 `v1.0.0`）

```bash
cd /path/to/MGDemoPlus
export REG=1933886418
export TAG=v1.0.0

docker build -t $REG/dpgame:$TAG .
docker build -f Dockerfile.nginx -t $REG/dpgame-nginx:$TAG .

docker login
docker push $REG/dpgame:$TAG
docker push $REG/dpgame-nginx:$TAG
```

**Windows（PowerShell）**：仓库根目录先执行一次 **`docker login`**，再运行 **`.\build-push-hub.ps1`**（等同上表两次 build + 两次 push）。可选改版本： **`.\build-push-hub.ps1 -Tag v1.0.1`**（须与业务上使用的 `IMAGE_TAG` 一致）。

### 2. 云服务器（只装 Docker / Compose）

将 **`docker-compose.hub.yml`** 放到任意目录（或从 Gitee Raw 下载同文件），然后：

```bash
docker login
docker compose -f docker-compose.hub.yml pull
docker compose -f docker-compose.hub.yml up -d
```

**不要**加 `--build`（镜像已在 Hub）。可选：同目录放 `.env` 设置 `DOCKER_REGISTRY`、`IMAGE_TAG`、`MYSQL_ROOT_PASSWORD`、`REDIS_PASSWORD` 等；变量说明见仓库根目录 **`.env.example`**（可复制为 `.env` 再改值）。  
**本机 Windows 若 `http://localhost/` 打不开**：常见为 **80 端口被 IIS 等占用**；在 `.env` 中加 **`NGINX_HTTP_PORT=8080`**，再 `compose up`，用 **`http://localhost:8080/`** 访问 Nginx。

### 3. 表结构变更后

在仓库中新增 **Flyway 版本脚本**（`src/main/resources/db/migration/V*__*.sql`），**重新构建并推送 `dpgame` 镜像**即可；MySQL 仅持久化数据，**不重跑镜像内 init**。若同一库已由旧方式建好表再跑 Flyway V1，可能冲突——开发机可对 `mysql_data` 执行 **`docker compose down -v`** 清空卷重来（**会删库**）；生产须有独立迁移预案。

### 4. 上传目录权限（持久方案）

应用进程以 **`spring` 用户**运行；命名卷挂到 **`/data/mgdemo-files`** 时默认属主常为 **root**，曲库上传会因无法创建 `music` 子目录而失败。根目录 **`Dockerfile`** 使用 **`docker/docker-entrypoint-app.sh`**：启动 Java 前以 root 执行 `mkdir -p /data/mgdemo-files/music` 与 `chown -R spring:spring /data/mgdemo-files`，再通过 **`gosu`** 降权启动 `java -jar`。更新应用镜像后需 **重新 build / push `dpgame`** 才会生效。

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

- **空库**：Compose 里 MySQL 使用命名卷 **`mysql_data`**（及官方镜像的 **`MYSQL_DATABASE=school_db`**）。**不再使用** `docker-entrypoint-initdb.d` / 宿主挂载初始化 SQL。**应用首次连库时**，**Flyway** 按 `spring.flyway.locations`（`classpath:db/migration`）执行脚本，生成业务表及 **`flyway_schema_history`**。
- **沿用旧卷的开发者**：若该卷内的 `school_db` **早已用历史 init 脚本建过表**，再启动带 **V1** 的新镜像，Flyway 可能报「对象已存在」等错误。处理方式：开发环境 **`docker compose down -v` 删卷重来**（**删除卷会清空数据库，勿用于生产无误删授权**）；或改用 baseline / 手工对齐 Flyway 历史（须单独约定）。**生产变更前务必备份。**

### 想清空数据卷、重新验证「从零建表」

在仓库根目录执行（会**删除该卷内所有 MySQL 数据**，包括 `school_db` 里已有数据）：

```bash
docker compose down -v
docker compose up --build
```

`-v` 会移除 compose 声明的卷（含 `mysql_data`）。下次 `up` 时 MySQL 初始化空库，**应用启动后 Flyway 再建表**。
本地开发只起 mysql 和 redis 的服务  
```bash
docker compose up -d mysql redis
```
若你**不想删卷**、只想清空某库里的表，可连 `localhost:3307` 手工处理——**那只是数据清理**；要与 Flyway 版本历史一致仍需参考 Flyway 文档或删卷重来（开发机）。

- **后续表结构变更**：在 **`src/main/resources/db/migration/`** 递增版本号文件（如 `V2__add_column.sql`），发版随应用 JAR/Docker 镜像发布即可。

更简要的入口说明见仓库根目录 `README.md` 中的「Docker 部署」小节。
--- 
对开发环境配置文件连3307打包之后依然正常运行的解释
的道理，说白了就是打包镜像的时候看docker compose配置呗，不看yml的，虽然yml写的3307，但docker配置写的3306，而docker服务在本地开发的时候映射出来是3307，在容器内部是3306，所以反而能连上，开发运维两不耽误
---

### 小结：`docker-compose.yml` 与 `docker-compose.hub.yml`

- **开发常用 `docker-compose.yml`**：可本地 `build` 应用；上传目录通过 **`./docker-data/uploads`** 等绑定挂载。MySQL 等业务数据落在 Compose **命名卷**（`mysql_data`），表结构仍由应用 **Flyway** 迁移。
- **仅镜像部署用 `docker-compose.hub.yml`**：不在服务器上 `build`，拉 Hub 上的 **`dpgame`、`dpgame-nginx`**，**MySQL 为官方 `mysql:8.0`**；Redis、上传目录等用**命名卷**持久化。**卷内数据不会上传到 Hub**。
- **为何要两个业务镜像**：`dpgame` 内置前后端与 Flyway；`dpgame-nginx` 含站点与 TLS 前置配置。**MySQL** 不再定制镜像，与应用职责分离。
---
build的时候，dockerignore说的不算，只有Dockerfile里写入的才真正打包到镜像里，当你看docker-compose.hub.yml文件时会发现每个服务前有 image：`redis` 用官方镜像；`mysql` 用官方 **`mysql:8.0`**；`app`/`nginx` 用 Hub 自建镜像。**表结构打在 `dpgame` JAR 的 `db/migration` 里，由 Flyway 在运行时应用。**

总结一下就是Dockerfile只是项目代码，不全，没有redis；mysql 用官方镜像空库；nginx 需自定义配置故单独镜像；Flyway 随应用镜像升级。