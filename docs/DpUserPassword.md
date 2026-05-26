# 用户口令：传输、bcrypt 存库与登录

> **核对日期**：2026-05-25  
> **权威来源**：`DpUserServiceImpl`、`CryptoUtil.bcryptEncode` / `bcryptMatches`  
> **Status**: maintained

---

## 1. 是不是「前端先加密再传」？

**不是。** 浏览器把输入框内容作为 **明文** 放在 HTTP 参数或 body（如 `GET /dpUser/loginProfile?nickname=…&password=…`）。  
**单向哈希在服务端**：`CryptoUtil.bcryptEncode(明文)` 写入 `dp_user.password`；登录用 `bcryptMatches(明文, 库中哈希)`。

传输安全依赖 **HTTPS**（生产/Nginx）；本地 `http://` 仅适合调试。

> `CryptoUtil.md5HexUtf8` 仍存在于工具类，供**非用户口令**的遗留/指纹场景；**用户注册/登录/改密不走 MD5**。

---

## 2. 实现映射

| 操作 | 类#方法 | 存储 |
|------|---------|------|
| 注册 | `DpUserServiceImpl#registerUser` → `bcryptEncode` | `dp_user.password` |
| 登录 | `DpUserServiceImpl#loginUser` / `loginProfile` → `bcryptMatches` | 比对 bcrypt |
| 改密 | `DpUserServiceImpl` 更新资料路径 → 校验旧口令 `bcryptMatches` 后 `bcryptEncode` 新口令 | 同上 |

`CryptoUtil`：`BCryptPasswordEncoder`，强度 **10**（`src/main/java/.../utils/CryptoUtil.java`）。

---

## 3. 与 JWT 的关系

- **登录成功一次**：校验 bcrypt → `JwtTokenService` 签发 JWT。  
- **后续请求**：只带 Bearer token，**不再传密码**。见 [JWT.md](./JWT.md)。

---

## 4. 找回密码

当前 **无** 邮件/短信自助重置。需产品扩展（邮箱字段、重置 token、发信服务）或运维改库（仅开发环境）。

---

## 5. 已知注意点

- 部分历史文档或 `CLAUDE.md` 仍写 MD5；**以本文件与 `DpUserServiceImpl` 为准**。  
- 前端 `login.vue` 可能在 `localStorage.userInfo` 缓存字段；敏感场景应仅存 **token**，避免长期存明文口令。

---

## 6. 相关文件

| 内容 | 路径 |
|------|------|
| 用户服务 | `user/impl/DpUserServiceImpl.java` |
| 登录 API | `controller/DpUserController.java` |
| JWT | `security/JwtTokenService.java` |
