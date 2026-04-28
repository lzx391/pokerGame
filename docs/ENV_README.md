# 环境变量、`application.properties` 与 `.env` 说明

本文说明本仓库里：**Spring 如何读 `application.properties`**、**根目录 `.env` 如何生效**、以及 **JWT**、**方舟 LLM（BOT_LLM）** 的配置方式。变量清单以仓库根目录 **`.env.example`** 为准。

---

## 1. 三类配置各管什么

| 来源 | 谁负责读 | 典型用途 |
|------|----------|----------|
| **`src/main/resources/application.properties`** | Spring Boot **启动时自动**从 classpath 加载 | 默认连接串、端口、`${变量:默认值}` 占位 |
| **根目录 `.env`** | **`LocalDotenvLoader`**：在 **`MgDemoPlusApplication.main`** 里、**`SpringApplication.run` 之前**调用，把条目写入 **`System.setProperty`**（仅当同名环境变量尚未设置时） | 本机开发覆盖默认值；键名与 `SPRING_*`、`ARK_*`、`JWT_SECRET` 等一致 |
| **系统 / IDE 环境变量** | Spring `Environment` 标准解析 | 优先于 `.env` 写入的系统属性 |
| **Docker** | 容器内**不依赖**磁盘上的 `.env` 文件 | 宿主机 `docker compose` 读 `.env`，通过 `environment:` 注入进程环境变量 |

---

## 2. `.env` 如何进入 Spring（无 `META-INF` 注册）

1. **`main`** 里最先执行 **`LocalDotenvLoader.load()`**（`dotenv-java`）。
2. 读取**当前工作目录**下的 `.env`（本机运行须把**工作目录**设为**仓库根目录**）。
3. 对每个键：若 **`System.getenv(键)` 为空**，则 **`System.setProperty(键, 值)`**。
4. 随后 **`SpringApplication.run`**，Spring 加载 **`application.properties`**，解析 **`${SPRING_DATASOURCE_PASSWORD:123456}`**、`${ARK_API_KEY:}`、`mgdemoplus.jwt.secret=${JWT_SECRET:...}` 等时，会从**系统属性**（含上一步）与环境变量中取值。

---

## 3. JWT

- **`application.properties`**：`mgdemoplus.jwt.secret=${JWT_SECRET:…}`。
- **`JwtTokenService`**（`@Component`）：**`@Value("${mgdemoplus.jwt.secret}")`** 注入密钥，负责签发 / 校验与 **Bearer** 解析。
- **`DpUserController`**、**`JwtAuthenticationFilter`** 注入 **`JwtTokenService`**。

---

## 4. 方舟 LLM（BOT_LLM）

- **`application.yml`**：`dp.llm.ark.*=${ARK_*:…}`（含 **`response-json-object`**，对应环境变量 **`ARK_RESPONSE_JSON_OBJECT`**，默认 `true`：请求体带 `response_format=json_object`；若接入点返回 400 可设为 `false`，仍由解析器容错提取动作）。
- **`DpLlmNpcDecisionService`** **`@Value`** 注入后构造 **`LlmNpc`**。  
  **`ARK_*`** 来自 Spring 解析后的配置（含 `.env` → `System.setProperty` → 占位符）。

---

## 5. 小结

| 场景 | `.env` 如何生效 |
|------|-----------------|
| 本机 `java` / IDEA | `LocalDotenvLoader` → `System.setProperty` → Spring 解析 `${…}` 与 `@Value` |
| Docker | 宿主机 compose 注入环境变量 |

**勿将含真实密钥的 `.env` 提交到 Git。**
