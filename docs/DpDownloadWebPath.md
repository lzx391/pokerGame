# DP 下载中心 `webPath`：磁盘落盘与 `/files/**`

> **核对日期**：2026-05-26  
> **权威来源**：`DpDownloadController`、`WebConfig#addResourceHandlers`、`mgdemoplus.files.file-location`  
> **Status**: maintained

本文说明 **`dp_download_asset.web_path`** 与磁盘文件、大厅下载链接的关系。列表 JSON 见 `GET /dpDownload/list`（**permitAll**）。

---

## 1. 三个容易混在一起的「路径」

| 概念 | 含义 | 示例 |
|------|------|------|
| **物理目录** | 服务器上存安装包等的文件夹 | `P:/javaworkspace/DPGameFiles/other/`（默认，可配置） |
| **`web_path`（入库）** | 浏览器应请求的 **URL 路径**（不含域名） | `/files/xxxxxxxx-xxxx-....exe` |
| **开发环境前缀** | 仅本地 `npm run serve` 时，为走代理而加的前缀 | `/dev-api` |

数据库里存的是 **`/files/文件名`**，不是完整 URL。

---

## 2. 上传时：文件落盘 + 写入 `web_path`

入口：`DpDownloadController`（`/dpDownload/upload`，需 JWT）。

1. 配置项 `mgdemoplus.files.file-location` 解析为物理目录（支持 `file:` 前缀）。
2. 生成 `storedFilename`（UUID + 扩展名），`MultipartFile.transferTo` 写入该目录。
3. 拼 **`webPath = "/files/" + storedFilename`**，写入 `DpDownloadAsset.webPath` 并 `INSERT` 表 `dp_download_asset`。

因此：**磁盘文件名** 与 **URL 最后一段** 一致；**URL 路径前缀** 固定为 `/files/`。

相关代码：`src/main/java/com/example/mgdemoplus/controller/DpDownloadController.java`。

---

## 3. 下载：不是 `/dpDownload`，而是 `GET /files/...`

`<a href="...">` 会发起普通 **GET**，由 **`WebConfig#addResourceHandlers`** 注册：

- **URL 模式**：`/files/**`
- **资源位置**：与 `mgdemoplus.files.file-location` 相同的目录（`file:` 形式）

即：**`GET /files/xxx.exe` → 从该目录读取 `xxx.exe` 返回**。与上传 `transferTo` 的目录是同一套配置。

`JwtSecurityConstants` 中 **`/files/**`** 为 permitAll，未登录也可下载。

---

## 4. 前端如何把 `webPath` 变成可请求的地址

### 4.1 下载中心页 `DownloadCenter.vue`

方法 `fileSrc(webPath)` / 工具 `downloadFileSrc(webPath)`：

- 若 `webPath` 已是 `http` 开头，原样使用。
- 否则：
  - **开发**：`base = '/dev-api'`，最终 **`/dev-api` + `webPath`**，例如 `/dev-api/files/xxx.exe`。
  - **生产**：`base = ''`，最终 **`/files/xxx.exe`**（与站点同域）。

### 4.2 为何开发环境要 `/dev-api`

与曲库相同：`vue.config.js` 的 `devServer.proxy` 把 `/dev-api` 转发到 Spring 并去掉前缀。

---

## 5. 配置与部署

| 配置键 | 作用 |
|--------|------|
| `mgdemoplus.files.file-location` | 下载文件目录；`WebConfig` 与 `DpDownloadController` 共用 |

默认：`file:P:/javaworkspace/DPGameFiles/other/`。  
Docker 生产见 `docker-compose-prod.yml` 中的 `MGDEMOPLUS_FILES_FILE_LOCATION`。

建表脚本：`src/main/resources/db/migration/V8__dp_download_asset.sql`（Flyway）。

---

## 6. 相关文件索引

| 文件 | 说明 |
|------|------|
| `front/dp_game/src/components/DownloadCenter.vue` | 列表、上传、下载链接 |
| `front/dp_game/src/utils/dpDownloadFileUrl.js` | `downloadFileSrc` |
| `src/main/java/.../DpDownloadController.java` | 上传、入库、`webPath` 生成 |
| `src/main/java/.../config/WebConfig.java` | `/files/**` 静态映射 |
| `src/main/java/.../download/mapper/DpDownloadAssetMapper.java` | 列表查询 |
