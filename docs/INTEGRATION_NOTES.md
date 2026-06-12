# 第三方接入笔记

> 通用从零接入指南，适用于任意 Spring Boot 项目。不依赖特定业务抽象。

---

## 一、MinIO 对象存储接入

### 1.1 MinIO 是什么

MinIO 是兼容 Amazon S3 API 的开源对象存储服务，适合存放图片、音视频、文档等**非结构化文件**。常见用途：

- 用户头像、附件上传
- 静态资源 CDN 源站
- 备份、日志归档

与「把文件存服务器磁盘」相比，MinIO 便于横向扩展、权限隔离，且 SDK 与 S3 生态通用。

### 1.2 Docker Compose 单机部署

在项目根目录新建 `docker-compose.yml`（或单独 `minio-compose.yml`）：

```yaml
services:
  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"   # S3 API
      - "9001:9001"   # Web 管理控制台
    environment:
      MINIO_ROOT_USER: minioadmin      # 访问密钥（生产务必改掉）
      MINIO_ROOT_PASSWORD: minioadmin  # 秘密密钥（生产务必改掉）
    volumes:
      - minio_data:/data

volumes:
  minio_data:
```

启动：

```bash
docker compose up minio -d
```

验证：

- API：`http://127.0.0.1:9000`
- 控制台：`http://127.0.0.1:9001`（用上面的账号密码登录）

### 1.3 创建 Bucket

两种方式任选其一：

**方式 A：Web 控制台**

登录 `http://127.0.0.1:9001` → Buckets → Create Bucket → 例如命名为 `my-app`。

**方式 B：应用启动时自动创建（推荐）**

```java
@Component
public class MinioBucketInitializer implements ApplicationRunner {

    private final MinioClient minioClient;

    @Value("${storage.minio.bucket}")
    private String bucket;

    public MinioBucketInitializer(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
```

### 1.4 Maven 依赖

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.17</version>
</dependency>
```

> 版本可与 [Maven Central](https://mvnrepository.com/artifact/io.minio/minio) 最新稳定版对齐。

### 1.5 Spring Boot 配置

**配置属性类：**

```java
@ConfigurationProperties(prefix = "storage.minio")
public class MinioProperties {
    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey;
    private String secretKey;
    private String bucket = "my-app";
    // getter / setter
}
```

**MinioClient Bean：**

```java
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties props) {
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }
}
```

### 1.6 常用操作示例

**上传**

```java
public void upload(String objectKey, InputStream data, long size, String contentType)
        throws Exception {
    minioClient.putObject(
            PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)   // 如 "avatars/user-1.png"
                    .stream(data, size, 10 * 1024 * 1024)  // 分片 10MB
                    .contentType(contentType)
                    .build());
}
```

**下载**

```java
public InputStream download(String objectKey) throws Exception {
    return minioClient.getObject(
            GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
}
```

**删除**

```java
public void delete(String objectKey) throws Exception {
    minioClient.removeObject(
            RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
}
```

**预签名 URL（临时直链，适合私有桶）**

```java
public String presignedGetUrl(String objectKey, int expireHours) throws Exception {
    return minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expireHours, TimeUnit.HOURS)
                    .build());
}
```

**对象 key 命名建议**

- 使用层级路径：`avatars/{userId}.webp`、`music/{trackId}.mp3`
- 避免中文与空格；统一小写扩展名
- 业务 URL 与存储 key 可分离（数据库存 key，对外拼访问路径）

### 1.7 安全注意

| 项 | 建议 |
|----|------|
| 密钥 | `accessKey` / `secretKey` 放环境变量，**不要**提交到 Git |
| 生产 | 修改默认 `minioadmin`，控制台不要公网裸奔 |
| 桶策略 | 公开读需显式 Bucket Policy；默认私有 + 预签名更安全 |
| HTTPS | 生产 endpoint 使用 `https://` |
| 端口 | 9000 为 API；9001 为管理界面，可仅内网开放 |

### 1.8 典型 application.yml

```yaml
storage:
  minio:
    endpoint: ${MINIO_ENDPOINT:http://127.0.0.1:9000}
    access-key: ${MINIO_ACCESS_KEY:}
    secret-key: ${MINIO_SECRET_KEY:}
    bucket: ${MINIO_BUCKET:my-app}
```

对应 `.env` 示例：

```env
MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=my-app
```

---

## 二、OAuth2 第三方授权登录（JustAuth）

### 2.1 JustAuth 是什么

[JustAuth](https://github.com/justauth/JustAuth) 是 Java 侧**第三方登录**聚合库，封装了 GitHub、Gitee、QQ、微信、钉钉等数十家平台的：

- 拼装授权跳转 URL
- 用 `code` 换 `access_token`
- 拉取用户信息

它帮你省掉各平台 HTTP 细节差异；你仍需要自己在各平台注册应用，并实现「回调 → 本地用户」逻辑。

### 2.2 Maven 依赖

```xml
<dependency>
    <groupId>me.zhyd.oauth</groupId>
    <artifactId>JustAuth</artifactId>
    <version>1.16.7</version>
</dependency>
```

若项目已使用 Spring Security，通常还会配合：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

> 说明：JustAuth 可独立使用；Spring OAuth2 Client 适合标准 OAuth2 平台（如 GitHub）。二者可并存——标准平台走 Spring Client，特殊平台走 JustAuth。

### 2.3 在各平台注册应用

以常见平台为例（名称可能随平台改版略有不同）：

| 平台 | 注册入口 | 需要记录的信息 |
|------|----------|----------------|
| GitHub | Settings → Developer settings → OAuth Apps | Client ID、Client Secret |
| Gitee | 设置 → 私人令牌 / OAuth 应用 | Client ID、Client Secret |
| 钉钉 | 开放平台 → 应用 → 凭证与基础信息 | AppKey、AppSecret |
| QQ / 微信 | 对应开放平台 | AppID、AppSecret |

注册时**必须填写回调地址（Redirect URI / Callback URL）**，且须与后端实际接收地址**完全一致**（协议、域名、端口、路径）。

示例（本地开发，前端 dev-server 在 8080，后端在 8088）：

```
http://localhost:8080/oauth/github/callback
```

若浏览器访问的是前端端口，回调也应登记前端端口；由前端代理或 302 到后端处理 `code`。

### 2.4 回调地址（Callback URL）概念

OAuth2 **授权码模式**流程：

```
用户 → 点击「GitHub 登录」
    → 浏览器跳转到 GitHub 授权页
    → 用户同意
    → GitHub 302 到你的 callback，并带上 ?code=xxx&state=yyy
    → 你的后端用 code 向平台换 token，再拉用户信息
    → 创建/绑定本地账号，返回你自己的登录态（Session / JWT）
```

`redirect_uri` 在三处必须一致：

1. 平台开发者后台登记的回调地址
2. 发起授权时 URL 里的 `redirect_uri` 参数
3. 用 `code` 换 token 时 POST body 里的 `redirect_uri`

### 2.5 JustAuth 基础配置

**方式 A：代码内 AuthConfig（适合单平台试水）**

```java
AuthConfig config = AuthConfig.builder()
        .clientId("你的ClientId")
        .clientSecret("你的ClientSecret")
        .redirectUri("http://localhost:8080/oauth/github/callback")
        .build();

AuthRequest authRequest = new AuthGithubRequest(config);
```

**方式 B：application.yml + 工厂（适合多平台）**

```yaml
justauth:
  type:
    github:
      client-id: ${GITHUB_CLIENT_ID:}
      client-secret: ${GITHUB_CLIENT_SECRET:}
      redirect-uri: ${GITHUB_REDIRECT_URI:}
    gitee:
      client-id: ${GITEE_CLIENT_ID:}
      client-secret: ${GITEE_CLIENT_SECRET:}
      redirect-uri: ${GITEE_REDIRECT_URI:}
```

根据 `type` 字段实例化对应的 `AuthXxxRequest`。

### 2.6 Spring Boot 集成模式

推荐两个 HTTP 端点 + 一个服务类。

#### 步骤 1：发起授权

```java
@GetMapping("/oauth/{provider}/authorize")
public void authorize(@PathVariable String provider,
                      HttpServletResponse response) throws IOException {
    String state = UUID.randomUUID().toString();
    // 将 state 存入 Redis / Session，设置短 TTL（如 10 分钟）
    stateStore.save(state, provider);

    AuthRequest authRequest = authRequestFactory.get(provider);
    String url = authRequest.authorize(state);
    response.sendRedirect(url);
}
```

前端也可先调 API 拿到 URL 再 `window.location.href = url`，效果相同。

#### 步骤 2：回调处理

```java
@GetMapping("/oauth/{provider}/callback")
public void callback(@PathVariable String provider,
                     @RequestParam String code,
                     @RequestParam String state,
                     HttpServletResponse response) throws IOException {
    // 1. 校验 state（防 CSRF）
    if (!stateStore.consume(state, provider)) {
        response.sendRedirect("/login?error=invalid_state");
        return;
    }

    // 2. code → token → 用户信息
    AuthRequest authRequest = authRequestFactory.get(provider);
    AuthCallback callback = AuthCallback.builder().code(code).state(state).build();
    AuthResponse<AuthUser> authResponse = authRequest.login(callback);

    if (!authResponse.ok()) {
        response.sendRedirect("/login?error=oauth_failed");
        return;
    }

    AuthUser authUser = authResponse.getData();
    // 3. 绑定或创建本地用户
    AppUser user = userService.findOrCreateFromOAuth(provider, authUser);

    // 4. 签发本地登录态（JWT 或 Session）
    String token = jwtService.issue(user);
    response.sendRedirect(frontendUrl + "/oauth/success?token=" + token);
}
```

JustAuth 核心三行等价于：

```java
AuthToken token = authRequest.getAccessToken(callback);
AuthUser user = authRequest.getUserInfo(token);
```

`login(callback)` 是封装好的组合调用。

#### 步骤 3：state 存储

| 方案 | 说明 |
|------|------|
| Redis | 多实例、无 Session 的 REST 项目常用 |
| HttpSession | 单体、传统 MVC 可用 |
| 加密 Cookie | 无 Redis 时的轻量方案 |

校验逻辑：**回调收到的 state 必须能且仅能消费一次**。

### 2.7 用户信息映射与本地用户创建

建议在数据库增加「社交账号绑定表」：

```
social_auth
  id
  provider          -- github / gitee / dingtalk
  provider_uid      -- 平台唯一 ID（GitHub id、钉钉 unionId）
  access_token      -- 可选，若需调平台 API 再存
  user_id           -- 关联本地用户表
  created_at
```

**查找或创建逻辑（伪代码）：**

```
1. 用 (provider, provider_uid) 查 social_auth
2. 若存在 → 取关联 user_id，直接登录
3. 若不存在：
   a. 用邮箱查本地用户（若平台返回 email 且你信任）
   b. 找到则绑定；找不到则新建用户
   c. 昵称、头像从 AuthUser 映射；敏感词/唯一性自行校验
4. 写 social_auth 绑定记录
5. 返回本地 JWT / Session
```

JustAuth `AuthUser` 常用字段：

- `uuid` / `token`：平台用户唯一标识（各平台字段名不同，以文档为准）
- `username` / `nickname`：显示名
- `avatar`：头像 URL
- `email`：可能为空或需额外 scope

### 2.8 安全注意

| 风险 | 对策 |
|------|------|
| CSRF | 强制校验 `state`，一次性消费 |
| 回调劫持 | 生产全程 HTTPS；回调域名与登记一致 |
| 密钥泄露 | Client Secret 仅放服务端环境变量 |
| Token 泄露 | 不要把平台 access_token 下发给前端 |
| 开放重定向 | 登录成功后只跳转到白名单前端地址 |
| 权限最小化 | scope 只申请必要权限（如 `read:user`） |

**Spring Security**：将 `/oauth/**` 加入匿名访问白名单；OAuth 完成后仍用你自己的 JWT 过滤器保护业务 API。

### 2.9 典型 application.yml

```yaml
# 若使用 Spring OAuth2 Client（GitHub 等标准平台）
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${GITHUB_CLIENT_ID:}
            client-secret: ${GITHUB_CLIENT_SECRET:}
            redirect-uri: ${GITHUB_REDIRECT_URI:}
            scope: read:user
        provider:
          github:
            authorization-uri: https://github.com/login/oauth/authorize
            token-uri: https://github.com/login/oauth/access_token
            user-info-uri: https://api.github.com/user
            user-name-attribute: id

# 业务侧
app:
  oauth:
    frontend-base-url: ${FRONTEND_BASE_URL:http://localhost:8080}
```

`.env` 示例：

```env
GITHUB_CLIENT_ID=Ov23li...
GITHUB_CLIENT_SECRET=...
GITHUB_REDIRECT_URI=http://localhost:8080/oauth/github/callback
FRONTEND_BASE_URL=http://localhost:8080
```

### 2.10 端到端自检清单

- [ ] 平台后台 callback 与代码 `redirect_uri` 字符级一致
- [ ] 发起授权 URL 带 `state`，回调能校验
- [ ] 用 `code` 换 token 成功（看日志 / Postman）
- [ ] 能拉到用户唯一 ID
- [ ] 首次登录创建用户，再次登录同一账号
- [ ] 生产环境 HTTPS + 密钥不入库

---

## 附录：OAuth2 授权码模式时序

```
浏览器          你的后端          第三方平台
  |-- 点击登录 -->|                  |
  |<-- 302 授权页-|                  |
  |---------------- 跳转授权 -------->|
  |<--------------- 用户同意 --------|
  |-- callback?code&state -->|       |
  |                |-- code 换 token -->|
  |                |<-- access_token ----|
  |                |-- 拉用户信息 ------>|
  |                |<-- user profile ----|
  |<-- 302 登录成功 ---------|       |
```

---

*文档版本：通用接入笔记 v1*
