# JWT 鉴权与白名单：`JwtTokenService`、`SecurityConfig`、前端 axios 带 token

## JwtTokenService

负责生成 token、从 `Authorization` 头剥离 Bearer、校验签名与过期时间；`subject` 为登录用户昵称（与 `DpUserController#loginProfile` 一致）。密钥来自配置项 **`mgdemoplus.jwt.secret`**（可由环境变量 **`JWT_SECRET`** 经 `application.properties` 映射）。

## 全局鉴权（Spring Security）

- **依赖**：`spring-boot-starter-security`。
- **配置**：`config/SecurityConfig.java` —— 无 Session（`STATELESS`）、关闭 CSRF（前后端分离 API）、白名单见 `security/JwtSecurityConstants`。
- **过滤器**：`security/JwtAuthenticationFilter.java` —— 解析 `Authorization: Bearer <jwt>`，校验成功后把 `subject` 写入 `SecurityContextHolder`（`UsernamePasswordAuthenticationToken`，`getName()` 即昵称）。
- **未登录**：`security/JwtAuthenticationEntryPoint.java` 返回 JSON：`success=false`，HTTP 401。
- **白名单**：登录/注册、静态资源、`/ws/**`、大厅/房间快照等只读接口（见常量类注释）；其余请求需合法 JWT。

业务代码里需要当前用户时：`SecurityContextHolder.getContext().getAuthentication().getName()`（与 token 一致时再与请求参数里的昵称比对）。

## 前端

`front/dp_game/src/main.js` 中 axios：

- **请求**拦截器：除 `loginProfile`、`registerUser` 外，自动从 `localStorage.userInfo.token` 带上 `Authorization: Bearer`。
- **响应**拦截器：收到 **HTTP 401** 时用 Element `Message.error` 展示后端 `message`（或默认「未登录或登录已失效」），清除 `localStorage.userInfo`，并 `router.replace('/login')`（已在登录页则不再跳转），与后端 JSON 约定一致。

## 迭代说明

口令存储为 **MD5 摘要**（`CryptoUtil`），与「Spring Security 表单登录 / BCrypt」不同路；当前为 **JWT + 过滤器链** 的全局应用。口令流程说明见 [DpUserPassword.md](./DpUserPassword.md)。
