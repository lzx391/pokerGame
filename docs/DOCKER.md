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

**和 Hub 的关系**：若你希望**服务器上不 clone 仓库、只拉镜像**，见下方 **「仅镜像部署」**（需先把 `dpgame-mysql`、`dpgame-nginx` 与应用镜像一并推到 Hub，并用 `docker-compose.hub.yml`）。

**示例（仓库名、分支以你实际为准）：**

```bash
git clone -b testUI https://gitee.com/SILVERSWOED/spring-boot-demo.git
cd spring-boot-demo
docker compose up -d --build
```

浏览器访问 `http://服务器公网IP`（若放行 **80** 且走 Nginx）或 **8088**（若映射了应用端口）；云厂商**安全组**需放行对应端口。

---

## 仅镜像部署（不 clone 仓库）

思路：**建表 SQL 打进 MySQL 镜像**（`Dockerfile.mysql`），**Nginx 配置打进 Nginx 镜像**（`Dockerfile.nginx`），应用仍用 **`Dockerfile` 构建的 `dpgame` 镜像**。编排用 **`docker-compose.hub.yml`**：只有 `image:`，无 `build:`，无 `./docker-data`、`./src/...` 等宿主机挂载。

**服务器上仍需有「这一份」YAML**（从 U 盘拷贝、浏览器下载、或 `wget` Gitee 原始文件均可），不是 Git 专属；**不需要**整份源码目录。

### 1. 在有源码的机器上构建并推送（版本号与 `IMAGE_TAG` 一致，例 `v1.0.0`）

```bash
cd /path/to/MGDemoPlus
export REG=1933886418
export TAG=v1.0.0

docker build -t $REG/dpgame:$TAG .
docker build -f Dockerfile.mysql -t $REG/dpgame-mysql:$TAG .
docker build -f Dockerfile.nginx -t $REG/dpgame-nginx:$TAG .

docker login
docker push $REG/dpgame:$TAG
docker push $REG/dpgame-mysql:$TAG
docker push $REG/dpgame-nginx:$TAG
```

**Windows（PowerShell）**：仓库根目录先执行一次 **`docker login`**，再运行 **`.\build-push-hub.ps1`**（等同上表三次 build + 三次 push）。可选改版本： **`.\build-push-hub.ps1 -Tag v1.0.1`**（须与 `docker-compose.hub.yml` 里 `IMAGE_TAG` 一致）。

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

若 `src/main/resources/db/` 下 SQL 有增改，需**重新构建并推送 `dpgame-mysql` 镜像**；已有 MySQL 数据卷不会自动重跑 init，需自行迁移或 `docker compose down -v` 清空 `mysql_data`（**会删库**）。

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
对开发环境配置文件连3307打包之后依然正常运行的解释
的道理，说白了就是打包镜像的时候看docker compose配置呗，不看yml的，虽然yml写的3307，但docker配置写的3306，而docker服务在本地开发的时候映射出来是3307，在容器内部是3306，所以反而能连上，开发运维两不耽误
---

### 小结：`docker-compose.yml` 与 `docker-compose.hub.yml`

- **开发常用 `docker-compose.yml`**：可本地 `build` 应用；MySQL 初始化脚本、上传目录等通过 **`./docker-data`、`./src/...`** 等绑定挂载引用仓库内文件，改起来直观。MySQL 等业务数据仍落在 Compose 声明的**命名卷**（如 `mysql_data`）里，并不等同于「所有数据都在 `docker-data` 文件夹」。
- **仅镜像部署用 `docker-compose.hub.yml`**：不在服务器上 `build`，只拉 Hub 上的 **`dpgame`、`dpgame-mysql`、`dpgame-nginx`**；MySQL、Redis、上传目录等用**命名卷**持久化，**数据在服务器本机**由 Docker 管理。镜像从 Hub 拉取；**卷内数据不会上传到 Hub**。
- **为何要三个镜像**：若只推送应用镜像，新机器上没有仓库里的建表 SQL 与 Nginx 配置；用 **`Dockerfile.mysql` / `Dockerfile.nginx`** 把二者打进镜像后，才能在**不 clone 仓库**的情况下 `pull` 并跑通整套服务。
---
build的时候，dockerignore说的不算，只有Dockerfile里写入的才真正打包到镜像里，当你看docker-compose.hub.yml文件时会发现每个XX前面都会有一个image配置，redis用的是官方image,而mysql和nginx是需要自己定制的，所以单开了两个镜像，这就是为啥要单门打包mysql和nginx才能完整的跑起来  
总结一下就是Dockerfile只是项目代码，不全，没有redis，mysql,nginx配置，redis不需要定制，mysql和nginx需要再补上配置，所以要单独再打包俩镜像