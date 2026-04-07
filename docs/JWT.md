# JWT 说明（MGDemoPlus）

## JwtUtil

负责生成 token、从 `Authorization` 头剥离 Bearer、校验签名与过期时间；`subject` 为登录用户昵称（与 `DpUserController#loginProfile` 一致）。

## 全局鉴权（Spring Security）

- **依赖**：`spring-boot-starter-security`。
- **配置**：`config/SecurityConfig.java` —— 无 Session（`STATELESS`）、关闭 CSRF（前后端分离 API）、白名单见 `security/JwtSecurityConstants`。
- **过滤器**：`security/JwtAuthenticationFilter.java` —— 解析 `Authorization: Bearer <jwt>`，校验成功后把 `subject` 写入 `SecurityContextHolder`（`UsernamePasswordAuthenticationToken`，`getName()` 即昵称）。
- **未登录**：`security/JwtAuthenticationEntryPoint.java` 返回 JSON：`success=false`，HTTP 401。
- **白名单**：登录/注册、静态资源、`/ws/**`、大厅/房间快照等只读接口（见常量类注释）；其余请求需合法 JWT。

业务代码里需要当前用户时：`SecurityContextHolder.getContext().getAuthentication().getName()`（与 token 一致时再与请求参数里的昵称比对）。

## 前端

`front/dp_game/src/main.js` 中 axios 请求拦截器：除 `loginProfile`、`registerUser` 外，自动从 `localStorage.userInfo.token` 带上 `Authorization: Bearer`。

## 迭代说明

密码哈希（BCrypt 等）与「整包 Spring Security 表单登录」属后续迭代；当前仅做 **JWT + 过滤器链** 的全局应用。
