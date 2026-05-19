# DP 用户密码说明：明文到服务端、`MD5(UTF-8)` 入库、校验与「找回密码」现状

面向：**不懂代码也能看懂**；技术细节见文末代码位置。

---

## 1. 是不是「前端先加密再发给后端」？

**不是。** 当前实现里：

- **前端**（`login.vue`、`register.vue` 等）把用户在输入框里打的字当作 **明文密码**，通过 HTTP 请求参数发给后端（例如 `GET /dpUser/loginProfile?nickname=…&password=…`）。
- **后端**收到明文后，在 **`CryptoUtil.md5HexUtf8`** 里做一次 **MD5(UTF-8)**，得到 **32 位小写十六进制字符串**，再写入数据库或拿去和库里比对。

所以：**「加密 / 摘要」发生在服务端**，不是浏览器里先算 MD5 再传。

> **为什么要这样设计？**  
> 常见做法是：**传输层用 HTTPS 保护明文口令**（路上别人看不到）；服务端再对口令做**单向摘要**存库。若在前端做 MD5 再传，口令仍以「固定摘要」形式出现在请求里，被截获后仍可被用于重放登录，**不能代替 HTTPS**。  
> 本机开发若用 `http://`，密码在局域网内可能被嗅探，仅适合自用调试。

---

## 2. 后端怎么「验证密码是否正确」？

可以想成两步（实际在一条 SQL 里完成）：

1. 用户输入 **明文密码** → 服务端用 **`CryptoUtil.md5HexUtf8(明文)`** 得到一串 **32 位小写 hex**（和注册时规则完全一样）。
2. 用 **昵称 + 这串 hex** 去数据库查：`nickname` 和 `password` 都匹配才算登录成功。

也就是说：**库里从不存明文**，只存 MD5 摘要；登录时**不「解密」**，而是对**同一规则再算一遍**，和库里存的比是否一致。

相关代码：

- 摘要：`com.example.mgdemoplus.utils.CryptoUtil#md5HexUtf8`
- 注册/登录/改密：`com.example.mgdemoplus.service.serviceImpl.DpUserServiceImpl`
- SQL 条件：`com.example.mgdemoplus.mapper.DpUserMapper`（`loginUser` 按昵称 + 密码摘要查询）

---

## 3. 「找回密码」现在怎么做？

**当前工程里没有「忘记密码 → 邮件/短信重置」这类功能**，也没有单独的接口。

若用户忘记密码，现实里常见做法包括（需产品决定后开发）：

| 方式 | 思路 |
|------|------|
| **邮箱 / 手机验证码** | 用户证明身份后，允许设新密码；后端对新密码再 `CryptoUtil.md5HexUtf8` 写入 `dp_user.password`。 |
| **管理员后台重置** | 运维或房主工具生成临时密码或强制改密。 |
| **仅开发环境** | 直接改数据库里该用户的 `password` 字段为某次注册得到的 MD5 值（不推荐生产）。 |

要做「可自助找回」，通常还要：**用户表增加邮箱/手机**、**发信/发短信服务**、**短时有效的重置令牌** 等，属于独立需求。

---

## 4. 和 JWT 的关系

- **密码校验**：只在 **登录**（如 `loginProfile`）时用一次，成功后服务端签发 **JWT**（见 `JwtTokenService`）。
- **之后请求**：浏览器带 **`Authorization: Bearer <token>`**，过滤器校验的是 **令牌签名与过期**，**不再每次传密码**。

详见：[JWT.md](./JWT.md)。

---

## 5. 已知限制与改进方向（了解即可）

- **MD5 作口令存储**偏旧（无盐、计算快），生产环境更常见 **bcrypt / Argon2** 等；若升级算法，需要**迁移策略**（老用户登录时重哈希等）。
- **前端 `localStorage` 里保存了明文密码**（见 `login.vue` 写入的 `userInfo`），设备上可被同浏览器脚本读取，敏感场景应改为仅存 token、必要时用更安全的存储策略。

---

## 6. 相关文件速查

| 内容 | 位置 |
|------|------|
| bcrypt 摘要工具 | `src/main/java/.../utils/CryptoUtil.java` |
| JWT 签发/校验 | `src/main/java/.../security/JwtTokenService.java` |
| 登录发 token | `DpUserController#loginProfile` |
| 全局鉴权 | `docs/JWT.md` |
