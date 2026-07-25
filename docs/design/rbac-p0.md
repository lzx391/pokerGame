# RBAC P0 详细设计 — 轻量权限 + 看牌

> 状态：已确认，可实施  
> 概要设计决策见对话记录；P0 仅 JWT 保护管理 API，密码仅挡前端入口。

## 1. 目标与验收

### 1.1 一句话验收

管理页给某用户赋予 `game:hole_cards:view` 权限后，该用户（非房主）可在对局中看到他人底牌；移除权限后不可见。房主看牌行为与改前一致。

### 1.2 非目标（P0）

- 管理 REST 的 ADMIN 角色鉴权（P1）
- ROOM_OWNER 角色、房主逻辑改造
- 实验排牌/下载中心去密码
- WS 聊天/音乐

## 2. 数据库（Flyway V26）

**脚本路径**：`src/main/resources/db/migration/V26__rbac_init.sql`

### 2.1 表结构

```sql
-- dp_role
id BIGINT PK AUTO_INCREMENT
code VARCHAR(32) NOT NULL UNIQUE  -- PLAYER, ADMIN
name VARCHAR(64) NOT NULL
created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)

-- dp_permission
id BIGINT PK AUTO_INCREMENT
code VARCHAR(64) NOT NULL UNIQUE   -- game:hole_cards:view
name VARCHAR(128) NOT NULL
created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)

-- dp_role_permission
role_id BIGINT NOT NULL
permission_id BIGINT NOT NULL
PRIMARY KEY (role_id, permission_id)
FK → dp_role, dp_permission

-- dp_user_role
user_id INT NOT NULL
role_id BIGINT NOT NULL
PRIMARY KEY (user_id, role_id)
FK → dp_user(id), dp_role
```

### 2.2 种子数据

| 角色 code | name |
|-----------|------|
| PLAYER | 普通用户 |
| ADMIN | 管理员 |

| 权限 code | name |
|-----------|------|
| game:hole_cards:view | 看牌 |

- 新用户注册时自动绑定 `PLAYER` 角色（在现有注册逻辑末尾插入，若无则仅文档说明由迁移给已有用户默认 PLAYER — **推荐注册流程自动绑 PLAYER**）。

## 3. 后端模块

**新包**：`com.example.mgdemoplus.rbac`

```
rbac/
  entity/     DpRole, DpPermission, DpUserRole, DpRolePermission
  mapper/     对应 Mapper（MyBatis-Plus BaseMapper）
  DpRbacService.java
  impl/DpRbacServiceImpl.java
  DpPermissionResolver.java          -- 按 userId 解析 permission codes
  impl/DpPermissionResolverImpl.java -- Redis 缓存，TTL 5min，变更时 evict
  support/DpPermissionCodes.java     -- 常量 game:hole_cards:view
  controller/DpAdminRbacController.java
  controller/DpAuthController.java   -- GET /dp/auth/permissions
  bo/ vo/  请求响应对象
```

### 3.1 权限加载

- **JWT 仍只含 sub(jti)**，不在 token 写 permissions
- `DpPermissionResolver.resolveByUserId(int userId)` → `Set<String>`
- `JwtAuthenticationFilter` 认证成功后：查 userId → 加载 permissions → `SimpleGrantedAuthority("perm:game:hole_cards:view")` 或自定义 `DpPermissionService.hasPermi(userId, code)`
- 推荐：**独立 `DpPermissionService.hasPermi(String nickname, String code)`**，内部 nickname→userId→resolver，快照层直接调 Service，Filter 可选加载 authorities

### 3.2 看牌改动（核心）

**文件**：`room/support/DpRoomSnapshotSupport.java`  
**方法**：`sanitizeHoleCardsForViewer`

原逻辑：`viewerNickname.equals(room.getOwner())` → 保留他人 holeCards

新逻辑：

```
canViewHoleCards = isOwner(viewer, room) 
    || permissionService.hasPermi(viewerNickname, "game:hole_cards:view")
```

- `DpRoomSnapshotSupport` 当前为 static 方法 → 需改为 Spring Bean 或注入 callback；**最小改动**：将 sanitize 逻辑移到 `DpRoomSnapshotService` Bean，或给 Support 传入 `BooleanSupplier canViewAllHoleCards`
- WS 推送与 `getNowRoom` 共用此路径（`DpRoomServiceImpl.getRoomSnapshotForViewer`）

### 3.3 REST 接口契约

统一响应：`ResultUtil.ok().data(...)` / `ResultUtil.error()`

#### POST `/dp/admin/verifyPassword`

- 鉴权：JWT
- Body: `{ "password": "string" }`
- 校验：`DpExperimentalDeckPresetPasswordGuard` 同款 env
- 成功：`{ "verified": true }`；失败：error 消息「密码错误」
- **不写库**；前端 sessionStorage 解锁

#### GET `/dp/admin/roles`

- 鉴权：JWT
- 响应：`[{ "id", "code", "name", "permissionIds": [1] }]`

#### GET `/dp/admin/permissions`

- 鉴权：JWT
- 响应：`[{ "id", "code", "name" }]`

#### PUT `/dp/admin/roles/{roleId}/permissions`

- 鉴权：JWT
- Body: `{ "permissionIds": [1, 2] }`
- 全量替换该角色权限；evict 所有用户权限缓存（或按 role 关联 user evict）

#### GET `/dp/admin/users`

- 鉴权：JWT
- Query: `page=1&size=20&keyword=`（可选 nickname 模糊）
- 响应：分页 `{ "list": [{ "id", "nickname", "roleIds": [] }], "total" }`

#### PUT `/dp/admin/users/{userId}/roles`

- 鉴权：JWT
- Body: `{ "roleIds": [1] }`
- 全量替换；evict 该 user 权限缓存

#### GET `/dp/auth/permissions`

- 鉴权：JWT
- 响应：`{ "permissions": ["game:hole_cards:view", ...] }` — 当前用户全部 permission codes（角色展开）

### 3.4 白名单

`/dp/admin/**` 与 `/dp/auth/permissions` **不在** PERMIT_ALL，需 JWT。

### 3.5 单元测试

**类**：`DpRoomSnapshotSupportTest` 或 `DpRbacHoleCardsTest`

| Case | 预期 |
|------|------|
| 房主，无 RBAC 权限 | 他人 holeCards 保留 |
| 非房主，有 game:hole_cards:view | 保留 |
| 非房主，无权限 | holeCards 空/脱敏 |
| 赋权后 evict 再撤权 | 脱敏 |

Mock `DpPermissionService`。

## 4. 前端

**技术**：Vue 2 + Vuex + Element UI 按需注册

### 4.1 路由

| path | 组件 | 说明 |
|------|------|------|
| `/admin` | `AdminLayoutPage.vue` | 路由守卫：无 sessionStorage unlock → redirect `/home` |
| `/admin/roles` | `AdminRolesPage.vue` | 角色 + 权限勾选 |
| `/admin/users` | `AdminUsersPage.vue` | 用户列表 + 分配角色 |

`router/index.js` 注册；新增页面用到的 `el-table`, `el-checkbox`, `el-pagination` 等在 `main.js` 注册。

### 4.2 大厅入口

**文件**：`front/dp_game/src/features/lobby/pages/LobbyPage.vue`

- 按钮「进入管理员模式」
- 弹窗输入密码 → `POST /dp/admin/verifyPassword`
- 成功：`sessionStorage.setItem('dp_admin_unlock', '1')` → `$router.push('/admin')`
- 复用现有密码 gate 样式（如 `DpRetroPasswordGateShell` 或 `GameDeckPresetPasswordGate` 模式）

### 4.3 权限指令

**文件**：`front/dp_game/src/directives/hasPermi.js`

```javascript
// v-hasPermi="'game:hole_cards:view'"
// 从 Vuex auth/permissions 或 getter 判断
```

登录后 / App 初始化：`GET /dp/auth/permissions` 写入 Vuex。

### 4.4 看牌按钮

条件改为：`isOwner || hasPerm('game:hole_cards:view')`

**涉及文件**（至少）：

- `GameOwnerHubContent.vue`
- `GameOwnerToolModal.vue`
- `GameOwnerPanel.vue`
- `GameOwnerHubPanel.vue` / `GameOwnerTouchPanel.vue`
- `GamePage.vue` 中 reveal 相关 handler — 非 owner 有权限时也允许 toggle `ownerRevealAll`（或重命名为 `revealAll`）

### 4.5 Playwright

- 路径：`front/dp_game/e2e/rbac-hole-cards.spec.js`（或项目现有 e2e 目录）
- 覆盖：密码门闸、管理页赋权、看牌按钮显隐

## 5. 执行顺序

1. 后端：Flyway → Service → 看牌快照 → Admin API → 单测
2. 前端：可并行；依赖 API 契约 above
3. 联调：赋权/撤权验证

## 6. 交付约束

- 禁止 git commit / push
- 交付：变更说明 + `mvn test -Dtest=...` + 前端构建命令 + 手动验证步骤
