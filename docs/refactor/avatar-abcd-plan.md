# 头像静态资源：HTTP 缓存（A）与缩略图（D）

> 范围：后端 `WebConfig`、Nginx、`DpUserServiceImpl` 上传链路、磁盘约定；**不改前端**。  
> B/C 项（前端 `avatarFileSrc`、列表接口返回 `avatarThumbUrl`）见后续迭代。  
> 头像 cache bust 时间戳见 **§10**（Flyway `V9__user_avatar_updated_at.sql`）。

## 1. 缩略图命名约定

| 项 | 约定 |
|---|---|
| 原图 | `/images/{userId}.{ext}`，如 `/images/12.jpg` |
| 缩略图 | `/images/{userId}_sm.webp`，固定扩展名 **WebP** |
| 尺寸 | 最长边 ≤ **128px**（等比缩放，不裁切） |
| 编码 | WebP，压缩质量约 **0.8** |
| API 返回值 | `DpAvatarUploadResult.avatarUrl` 仍为**原图** web 路径；列表/大厅用缩略图时由前端或后续接口拼 `{userId}_sm.webp` |

**GIF 动图**：读取**首帧**生成缩略图；若 `ImageIO` 无法解码则跳过 `_sm` 生成（原图上传仍成功）。

## 2. HTTP 缓存策略（任务 A）

### 2.1 Spring（直连 `8088`）

- `WebConfig` 对 `/images/**` 设置 `cachePeriod = 31536000`（秒）。
- `ResourceHttpRequestHandler` 响应头为 `Cache-Control: public, max-age=31536000`，并保留 **Last-Modified / ETag**（同路径文件被覆盖后，客户端带 `If-None-Match` / `If-Modified-Since` 可拿到新内容）。
- **不使用** `immutable`：头像 URL 不变（`/images/12.jpg`），换图靠磁盘覆盖 + 前端 `?t=` cache bust。

### 2.2 Nginx（Docker 生产）

- 在 `location /` 之前增加 `location ^~ /images/`：
  - `proxy_pass http://app:8088`
  - `proxy_hide_header Cache-Control` 后 `add_header Cache-Control "public, max-age=31536000" always`
- 与 Spring 语义一致；换头像仍依赖**文件更新** + 客户端 **`?t=`** bust。

`/music/**`、`/files/**` 不在此任务加长缓存。

## 3. 删除逻辑

上传新头像时（`uploadAvatar`）顺序不变：删库内旧 web 路径文件 → `deleteUserAvatarFiles(userId)` → 写新原图 → 写新缩略图。

`deleteUserAvatarFiles` 删除：

1. 目录下所有 `{userId}.*`（原图，扩展名变更时清旧图）
2. 固定文件 `{userId}_sm.webp`

## 4. 老数据回填（P1，默认关闭）

配置项（`application.yml` / 环境变量）：

```yaml
mgdemoplus:
  images:
    backfill-thumbs: ${MGDEMOPLUS_IMAGES_BACKFILL_THUMBS:false}
```

- `false`（默认）：启动不扫描。
- `true`：启动时 `DpAvatarThumbBackfillRunner` 扫描图片目录，对每个 `{userId}.{jpg|jpeg|png|webp|gif}` 若不存在 `{userId}_sm.webp` 则生成。

**运维建议**：仅在维护窗口对单机执行一次；多实例部署时只在一台开启，避免并发写同一 NFS/目录。

**备选（未实现代码，可按需加）**：受 JWT 保护的一次性 `POST /dpUser/admin/backfill-avatar-thumbs` + `mgdemoplus.images.backfill-admin-enabled=true`，仅 dev/staging。

## 5. 验收清单

- [ ] 上传 jpg/png：磁盘同时存在 `12.jpg` 与 `12_sm.webp`
- [ ] 再次上传（改扩展名 png）：旧 `12.jpg`、旧 `12_sm.webp` 已删，仅保留 `12.png` + `12_sm.webp`
- [ ] `curl -I http://localhost:8088/images/12_sm.webp` → `Cache-Control` 含 `public` 与 `max-age=31536000`，且有 `Last-Modified` 或 `ETag`
- [ ] Docker：重建/重载 Nginx 后，`curl -I https://<host>/images/12_sm.webp` 缓存头正确
- [ ] `mgdemoplus.images.backfill-thumbs=true` 启动后，仅有原图无 `_sm` 的用户生成缩略图
- [ ] GIF：首帧缩略图或跳过（日志），原图上传成功

## 6. 回滚

1. **删缩略图文件**：在图片目录批量删除 `*_sm.webp`（不影响原图）。
2. **还原 Java**：移除 `DpAvatarThumbnailSupport`、`DpAvatarThumbBackfillRunner`；`WebConfig` 去掉 `/images/**` 的 `cachePeriod`；`DpUserServiceImpl` 去掉生成缩略图调用；`DpImageFileSupport` 恢复仅删 `{userId}.*`。
3. **还原 Nginx**：删除 `location ^~ /images/` 块，重建 nginx 镜像/重载配置。
4. **配置**：`backfill-thumbs` 保持 `false`。

## 7. WebP 编码依赖

JDK `ImageIO` 不内置 WebP **写出**。项目增加 **`com.github.gotson:webp-imageio`**（ImageIO SPI）；缩放仍用 `BufferedImage` + `Graphics2D`。

## 8. 相关代码

| 文件 | 职责 |
|---|---|
| `config/WebConfig.java` | `/images/**` cachePeriod |
| `utils/DpImageFileSupport.java` | 路径、删除、thumb 文件名 |
| `utils/DpAvatarThumbnailSupport.java` | 读原图 → 缩放 → 写 WebP |
| `user/impl/DpUserServiceImpl.java` | 上传后生成 thumb |
| `config/DpAvatarThumbBackfillRunner.java` | 可选启动扫描 |
| `docker/nginx/default.conf` | `/images/` 代理与缓存头 |

## 9. Docker 发布提醒

修改 `docker/nginx/default.conf` 后需 **重建 nginx 镜像或更新挂载并重载**（`docker compose up -d --build nginx` 或等价操作），否则生产仍走旧配置。

## 10. 头像时间戳 bust（`avatar_updated_at` / `avatarUpdatedAt`）

### 10.1 语义

| 层 | 说明 |
|---|---|
| 库列 | `dp_user.avatar_updated_at`：`DATETIME(3)`，头像**最后一次成功写库**时间（上传替换时更新） |
| API | `avatarUpdatedAt`：**epoch 毫秒**（`long`），无头像或未回填时为 JSON `null` |
| 迁移 V9 | 新增列；`avatar_url IS NOT NULL` 且时间为空 → `CURRENT_TIMESTAMP(3)`，保证老用户也有 bust 值 |

头像 **URL 路径不变**（仍为 `/images/{userId}.{ext}`），换图靠磁盘覆盖 + 客户端 cache bust。

### 10.2 JSON 示例

`GET /dpUser/profile` → `data.profile`：

```json
{
  "id": 12,
  "nickname": "Alice",
  "avatarUrl": "/images/12.jpg",
  "avatarUpdatedAt": 1748246400123
}
```

`POST /dpUser/avatar` 成功：

```json
{
  "success": true,
  "avatarUrl": "/images/12.png",
  "avatarUpdatedAt": 1748246500456
}
```

`GET /dp/friends` 每条好友含同名字段；周榜 `items[]`、`GET /dpUser/stats/{userId}` 的 `honor` 亦返回。

### 10.3 与前端 `?t=` 的关系

- 静态资源仍走 `/images/**` 长缓存（§2）；**不换 URL** 时浏览器可能命中旧图。
- 前端在 `<img src>` 上拼接 **`?t={avatarUpdatedAt}`**（或缩略图 `_sm.webp` 同源规则），使 URL 变化从而绕过 `max-age`。
- `avatarUpdatedAt` 在每次成功上传后递增；仅读接口、未换头像时不变。

### 10.4 回滚

1. 部署旧版 Java（不读写该列）。
2. 可选：`ALTER TABLE dp_user DROP COLUMN avatar_updated_at;`（需确认无 Flyway 降级流程；生产一般保留列即可）。
3. Flyway **勿改**已应用的 `V9__user_avatar_updated_at.sql`。
