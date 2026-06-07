# GitHub OAuth 2.0 业务流程设计

## 数据库改动

```sql
-- 关联表：一个用户可绑多个第三方渠道
CREATE TABLE dp_user_social_auth (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    provider    VARCHAR(32)  NOT NULL COMMENT 'github / wechat / qq / google / apple ...',
    open_id     VARCHAR(128) NOT NULL COMMENT '第三方返回的唯一标识',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uq_provider_open (provider, open_id),
    INDEX idx_user_id (user_id)
);

-- dp_user 表改动：password 改成可空
ALTER TABLE dp_user MODIFY password VARCHAR(255) NULL;
```

---

## 场景一：新用户首次 GitHub 登录

这是最复杂的场景，分步走：

```
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                  │
│  1. 点击 "GitHub 登录"按钮                                         │
│     ↓                                                            │
│  2. 前端 GET /oauth/github/authorize-url                         │
│     后端返回 { url: "https://github.com/login/oauth/authorize    │
│                    ?client_id=xxx&redirect_uri=xxx" }            │
│     ↓                                                            │
│  3. window.location.href = url   ← 整页跳转，不是 ajax            │
│     ↓                                                            │
│  4. 用户在 GitHub 页面授权                                         │
│     ↓                                                            │
│  5. GitHub 302 跳回：https://你的域名/oauth/github/callback?code=xxx│
│     ↓                                                            │
│  6. 前端页面加载（vue router 命中 /oauth/github/callback 路由）      │
│     从 URL 取出 code，调后端：                                      │
│     POST /oauth/github/callback  { code: "xxx" }                 │
└─────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────┐
│  后端 POST /oauth/github/callback 处理                             │
│                                                                  │
│  7. 拿 code 向 GitHub POST 换 access_token                        │
│     ↓                                                            │
│  8. 拿 access_token 调 GET https://api.github.com/user            │
│     得到：{ login: "zhangsan", avatar_url: "https://..." }       │
│     ↓                                                            │
│  9. 查 dp_user_social_auth WHERE provider='github' AND open_id=? │
│     → 没找到 → 新用户                                              │
│     ↓                                                            │
│  10. 用 GitHub login 做默认昵称 "zhangsan"                         │
│      校验：                                                        │
│      - 是否纯数字？                                                 │
│      - 是否超长（>10）？                                            │
│      - 是否含敏感词？                                               │
│      - 查 dp_user 表是否已被占用？                                   │
│      ↓                                                            │
│  11a. 如果全部通过：昵称 = "zhangsan"                               │
│  11b. 如果任一不通过：昵称 = "player_" + 随机6位（如 player_a3x9k2）  │
│       标记 needSetupNickname = true                                │
│       ↓                                                            │
│  12. INSERT dp_user (nickname="zhangsan", password=NULL)           │
│      INSERT dp_user_social_auth (user_id, 'github', 'zhangsan')   │
│      ↓                                                            │
│  13. 下载 GitHub 头像 → 存本地 /images/{userId}.png                 │
│      UPDATE dp_user SET avatar_url='/images/{userId}.png'         │
│      ↓                                                            │
│  14. 生成 JWT（subject = nickname），jti 写入 Redis                  │
│      ↓                                                            │
│  15. 返回前端：                                                     │
│      {                                                             │
│        token: "eyJ...",                                            │
│        nickname: "zhangsan",                                       │
│        isNewUser: true,                                            │
│        needSetupNickname: false   ← 11a 场景为 false               │
│      }                                                             │
│      或                                                             │
│      {                                                             │
│        token: "eyJ...",                                            │
│        nickname: "player_a3x9k2",                                  │
│        isNewUser: true,                                            │
│        needSetupNickname: true    ← 11b 场景为 true                │
│      }                                                             │
└─────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────┐
│  前端处理                                                          │
│                                                                  │
│  16. 存 token 到 localStorage                                     │
│  ↓                                                               │
│  17a. needSetupNickname = false → 直接进游戏 ✅                     │
│  17b. needSetupNickname = true  → 弹出改名弹窗（不可跳过）            │
│       输入昵称 → PUT /dpUser/profile { nickname: "新昵称" }        │
│       成功后拿到新 token（JWT subject 变了），替换 → 进游戏           │
└─────────────────────────────────────────────────────────────────┘
```

---

### 回答你的疑问①：三方登录查不查密码？

```
OAuth 登录走 POST /oauth/github/callback
   → 调 DpUserServiceImpl.oauthLoginOrRegister(provider, openId, defaultNickname, avatarUrl)
   → 这个方法里从头到尾不碰 password 字段
   → 注册时 INSERT password=NULL
   → 登录时查的是 social_auth 表，不查密码

账号密码登录走 /dpUser/loginProfile
   → 调 DpUserServiceImpl.loginUserOrNull(nickname, password)
   → bcrypt 比对密码
   → OAuth 用户的 password 是 NULL，bcryptMatches("xxx", NULL) → false → 登录失败
   → 前端提示："此账号通过 GitHub 登录，请使用 GitHub 登录"
   → 或更友好：后端返回专用错误码，前端识别后显示 "GitHub 登录" 按钮

两条路完全隔离，不会串。
```

---

## 场景二：老用户再次 GitHub 登录

```
1-9 步同场景一。

9. 查 dp_user_social_auth WHERE provider='github' AND open_id='zhangsan'
   → 找到了！user_id = 5

10. SELECT * FROM dp_user WHERE id = 5
    → 拿到 nickname, avatar_url...

11. 生成 JWT（subject = nickname），jti 写入 Redis

12. 返回前端：
    {
      token: "eyJ...",
      nickname: "zhangsan",
      isNewUser: false,
      needSetupNickname: false
    }

13. 前端：直接进游戏 ✅
```

---

## 场景三：OAuth 用户修改资料（无密码）

```
当前 updateProfile 逻辑：
  requireCurrentUser() → 拿 JWT 里的 nickname → 查 dp_user 拿完整记录
  ↓
  验证 oldPassword：
    if (stored.password == NULL || stored.password 是空串) {
      → 跳过旧密码验证  ← 【改动点：加这个判断】
    } else {
      → bcryptMatches(oldPassword, stored.password) 失败则拒绝
    }
  ↓
  后续逻辑不变（验昵称、验敏感词、更新库、重签 JWT）
```

### 回答你的疑问②：没密码怎么验证身份？JWT 够不够？

**改昵称、玩游戏：JWT 够用。设密码：不够，必须重新验证。**

JWT 能证明"这个浏览器当前有一个有效会话"，但不能证明"屏幕前面坐的是本人"。如果用户忘记登出，别人坐到电脑前就能悄悄设个密码——之后 JWT 过期了，那人还能用密码登录进来。

**危险等级判断**：

| 操作 | 如果被冒用…… | 需要额外验证？ |
|------|-------------|---------------|
| 改昵称 | 可以改回来 | JWT 够 |
| 玩游戏 | 不影响账号归属 | JWT 够 |
| **首次设密码** | 开了永久后门，JWT 过期后还能进 | **必须重新三方授权** |
| 改密码（旧换新） | 已有旧密码把关 | 旧密码本身就是验证 |

---

## 场景四：OAuth 用户想设置密码（高危操作，需 GitHub 重新授权）

```
1. 用户点 "设置密码"
   ↓
2. 跳 GitHub 重新授权（带 state=set-password，后端加密存 userId）
   ↓
3. GitHub 回调 → 后端解析 state → 确认是"设密码模式" + userId
   ↓
4. 后端生成一个一次性 token（5 分钟有效，写入 Redis），
   前端拿到后跳转到设密码页面
   ↓
5. 前端表单：输入新密码（无需昵称，无需旧密码）
   POST /dpUser/setPassword { setupToken: "xxx", newPassword: "123456" }
   ↓
6. 后端：验 setupToken → 验密码长度 → bcrypt 写库
   ↓
7. 之后就能用昵称+密码登录了
```

**注意**：如果用户已经设过密码了，再想改密码就走正常的旧密码验证——不需要也不应该走这个 GitHub 重新授权的通道。

---

## 场景五：已有密码账号绑定 GitHub

```
前提：用户已用昵称+密码登录，持有有效 JWT

1. 设置页 → 点 "绑定 GitHub"
   ↓
2. GET /oauth/github/authorize-url?bind=true
   后端生成 state 参数（防 CSRF），state 里加密标识 "当前用户 userId + bind 模式"
   ↓
3. 跳 GitHub → 授权 → 回调 /oauth/github/callback?code=xxx&state=yyy
   ↓
4. 后端解析 state → 知道是"绑定模式"、userId=5
   ↓
5. 拿 code 换 token，拿 GitHub 信息，得到 github_login = "zhangsan"
   ↓
6. 检查 dp_user_social_auth：
   SELECT * WHERE provider='github' AND open_id='zhangsan'
   
   6a. 已存在且 user_id != 5 → 返回 error："该 GitHub 账号已被其他用户绑定" ❌
   6b. 已存在且 user_id == 5 → 返回 info："已绑定过，无需重复绑定" ℹ️
   6c. 不存在 → INSERT (user_id=5, provider='github', open_id='zhangsan') ✅
   ↓
7. 返回前端 { bindSuccess: true, provider: 'github', githubLogin: 'zhangsan' }
```

## 附录：表设计说明——为什么一个关联表就够了

```
dp_user  1 ──── N  dp_user_social_auth
                        ├─ provider  VARCHAR(32)   ← 这就是"渠道标识"，不是外键
                        ├─ open_id   VARCHAR(128)  ← 第三方返回的唯一 ID
                        └─ UNIQUE(provider, open_id)

不需要 dp_social_provider 表。
provider 总共就 5~10 个值（github/wechat/qq/google/apple），不会频繁变化。
拆成三表（provider 表 + 外键关联）每次登录都要多 JOIN 一次，零收益。
```

---

### 回答你的疑问③：绑定 GitHub 需不需要再验密码？

不需要。当前登录态（JWT）已经证明了你是谁。这和修改昵称一个道理——**你已经是合法的本人了，不需要重复证明**。

唯一需要额外验证的场景是：解绑前（比如解绑最后绑定的登录渠道），可以要求输密码或验证码，防止账号变成死号。

---

## 场景六：后来用 GitHub 登录进来想解绑（但没密码）

```
解绑逻辑：
  查 dp_user_social_auth 当前用户有几个绑定：
  
  - 还有别的渠道或设置了密码 → 可以直接解绑 ✅
  - 只剩这一个渠道且没密码 → 拒绝解绑 ❌
    提示："这是你当前唯一的登录方式，请先设置密码或绑定其他渠道"
    引导用户去设置密码
```

---

## 后端要做的事（清单）

| # | 改动 | 说明 |
|---|------|------|
| 1 | 建 `dp_user_social_auth` 表 | DDL + Mapper |
| 2 | `dp_user.password` 改成可空 | DDL |
| 3 | 加 `DpOAuthService` | GitHub 换 token、调 API、头像下载 |
| 4 | 加 `DpOAuthController` | `GET /oauth/github/authorize-url`、`POST /oauth/github/callback` |
| 5 | 加 `POST /dpUser/setPassword` | 凭 setupToken（Redis 临时凭证）设密码 |
| 6 | 改 `DpUserServiceImpl.updateProfile` | password 为 NULL 时跳过旧密码校验（改昵称场景） |
| 7 | 改 `DpUserServiceImpl.loginUserOrNull` | 密码为空时返回 null，前端提示"请用 GitHub 登录" |
| 8 | 配置文件 | `application.yml` 加 GitHub OAuth 配置段 |

---

## 前端要做的事（清单）

| # | 改动 | 说明 |
|---|------|------|
| 1 | 登录页加 "GitHub 登录" 按钮 | |
| 2 | 回调页 `/oauth/github/callback` | 取 URL 上 code，调后端，处理返回 |
| 3 | 昵称设置弹窗 | `needSetupNickname=true` 时弹出，不可跳过 |
| 4 | 设置页加 "绑定/解绑 GitHub" | 已绑定显示解绑，未绑定显示绑定 |
| 5 | 登录失败提示优化 | 区分"密码错误"和"请用 GitHub 登录" |
