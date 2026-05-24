# 环境与配置：`application.yml`、根目录 `.env`、JWT、方舟 LLM（BOT_LLM）

本文说明本仓库里 **Spring 如何读 `application.yml`**、**根目录 `.env` 如何生效**、以及 **JWT**、**方舟 LLM（BOT_LLM）** 的配置方式。

**分层规范与全量参数表**（A/B/C/D 四层、读取顺序、rebuild 矩阵）见 **[CONFIG_LAYERS.md](CONFIG_LAYERS.md)** 与 **[refactor/config-inventory.md](refactor/config-inventory.md)**。  
**变量清单**以 **`src/main/resources/application.yml`** 与仓库根目录 **`.env.example`** 为准。

---

## 1. 配置来源与读取顺序

| 优先级 | 来源 | 谁负责读 | 典型用途 |
|--------|------|----------|----------|
| ① 最高 | **系统 / IDE / compose 环境变量** | Spring `Environment` | 生产密钥、Docker 内 JDBC/Redis 主机名 |
| ② | **根目录 `.env`** | **`LocalDotenvLoader`**：在 **`MgDemoPlusApplication.main`** 里、**`SpringApplication.run` 之前**调用，写入 **`System.setProperty`**（**仅当 ① 未设置同名键**） | 本机开发覆盖默认值 |
| ③ 最低 | **`src/main/resources/application.yml`** | Spring Boot 启动时从 classpath 加载 | D 层产品/框架默认；B/C 层 `${ENV:默认}` 占位 |

**Docker 生产**：容器内**不依赖**磁盘上的 `.env` 文件；宿主机 `docker compose` 读 `.env` 后通过 `environment:` 注入进程环境变量（与 LocalDotenvLoader 无关）。

---

## 2. `.env` 如何进入 Spring

1. **`main`** 里最先执行 **`LocalDotenvLoader.load()`**（`dotenv-java`）。
2. 读取**当前工作目录**下的 `.env`（本机运行须把**工作目录**设为**仓库根目录**）。
3. 对每个键：若 **`System.getenv(键)` 为空**，则 **`System.setProperty(键, 值)`**。
4. 随后 **`SpringApplication.run`**，Spring 加载 **`application.yml`**，解析 **`${SPRING_DATASOURCE_PASSWORD:mgdemo_root}`**、`${ARK_API_KEY:}`、`mgdemoplus.jwt.secret=${JWT_SECRET:...}` 等时，会从环境变量与系统属性中取值。

复制 **`.env.example`** 为 **`.env`** 后按需填写；**勿将含真实密钥的 `.env` 提交到 Git**。

---

## 3. JWT

| 项 | 说明 |
|----|------|
| yml 键 | `mgdemoplus.jwt.secret: ${JWT_SECRET:abcdefghhgfedcbaabcdefghhgfedcba}` |
| 环境变量 | `JWT_SECRET`（**A 层**） |
| 可不填？ | **可以**。未设时使用 yml 弱默认（与 `JwtTokenService` 内开发回退一致）。 |
| 公网生产 | **强烈建议**单独设置 `JWT_SECRET`（UTF-8 ≥32 随机字符），经 compose 或 `.env` 注入。 |
| 消费方 | `JwtTokenService`（`@Value("${mgdemoplus.jwt.secret}")`）、`JwtAuthenticationFilter`、`DpUserController` |
| 过期时长 | `mgdemoplus.jwt.expiration.time: 259200000`（**D 层**，约 3 天；改 yml 需 rebuild 镜像） |

---

## 4. 方舟 LLM（BOT_LLM）

- **`application.yml`**：`dp.llm.ark.*=${ARK_*:…}`（含 **`response-json-object`** → **`ARK_RESPONSE_JSON_OBJECT`**，默认 `true`）。
- **`DpLlmNpcDecisionService`** 通过 **`@Value`** 注入后构造 **`LlmNpc`**。
- **A 层**（必填才走云端）：`ARK_API_KEY`、`ARK_ENDPOINT_ID`、`ARK_BASE_URL`。
- **B 层**（可选）：`ARK_REASONING_EFFORT`、`ARK_THINKING_TYPE`、`ARK_RESPONSE_JSON_OBJECT`。

---

## 5. 改配置要不要 rebuild 镜像？

| 变更类型 | 本地 `mvn spring-boot:run` | Docker 生产 |
|----------|---------------------------|-------------|
| **A/B** 密钥或运维开关（env / compose） | 改 `.env` 或环境变量 → **重启进程** | 改 compose / 宿主机 `.env` → **`docker compose up -d` 重建 app**；**不需 rebuild** |
| **C** 路径 / JVM / TZ / 卷 | 改 env 或 compose → **重启** | 改 compose `environment` / `volumes` → **重启 app**；**不需 rebuild** |
| **D** yml 字面量（port、Flyway、JWT expiration 等） | 保存 yml → **重启** | yml 打进 jar → **必须 build 新镜像** 后部署 |

详见 **[CONFIG_LAYERS.md §1.3](CONFIG_LAYERS.md#13-改某类配置要不要-rebuild-镜像)**。

---

## 6. 小结

| 场景 | 配置如何生效 |
|------|----------------|
| 本机 `java` / IDEA | `.env` → `LocalDotenvLoader` → `System.setProperty` → Spring 解析 `${…}` 与 `@Value` |
| Docker | 宿主机 compose 读 `.env` 做 `${VAR}` 替换 → 注入 `environment:` → Spring 解析 |

**勿将含真实密钥的 `.env` 提交到 Git。**
