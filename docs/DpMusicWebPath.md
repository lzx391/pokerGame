# DP 曲库 `webPath`：磁盘落盘、`GET /music/**` 试听与开发代理

本文说明 **曲库元数据表 `dp_music_track` 里的 `web_path`** 如何对应 **磁盘上的文件**，以及前端 `<audio>` / 对局 BGM 如何通过 HTTP 拿到音频。**不涉及** `GET /dpMusic/list` 的 JSON 字段细节；列表接口只返回「逻辑路径」，真正播放是另一条 **静态资源** 链路。

---

## 1. 三个容易混在一起的「路径」

| 概念 | 含义 | 示例 |
|------|------|------|
| **物理目录** | 服务器上存 mp3 等文件的文件夹 | `P:/javaworkspace/DPGameFiles/music/`（默认，可配置） |
| **`web_path`（入库）** | 浏览器应请求的 **URL 路径**（不含域名） | `/music/xxxxxxxx-xxxx-....mp3` |
| **开发环境前缀** | 仅本地 `npm run serve` 时，为走代理而加的前缀 | `/dev-api` |

数据库里存的是 **`/music/文件名`**，不是完整 URL。

---

## 2. 上传时：文件落盘 + 写入 `web_path`

入口：`DpMusicController`（`/dpMusic/upload`）。

1. 配置项 `mgdemoplus.music.file-location` 解析为物理目录（支持 `file:` 前缀）。
2. 生成 `storedFilename`（UUID + 扩展名），`MultipartFile.transferTo` 写入该目录。
3. 拼 **`webPath = "/music/" + storedFilename`**，写入 `DpMusicTrack.webPath` 并 `INSERT` 表 `dp_music_track`。

因此：**磁盘文件名** 与 **URL 最后一段** 一致；**URL 路径前缀** 固定为 `/music/`。

相关代码：`src/main/java/com/example/mgdemoplus/controller/dp/DpMusicController.java`。

---

## 3. 试听 / 播放：不是 `/dpMusic`，而是 `GET /music/...`

`<audio :src="...">` 会发起普通 **GET**，不会调用 `RestController` 里带 `/dpMusic` 前缀的接口。

Spring 在 **`WebConfig#addResourceHandlers`** 中注册：

- **URL 模式**：`/music/**`
- **资源位置**：与 `mgdemoplus.music.file-location` 相同的目录（`file:` 形式）

即：**`GET /music/xxx.mp3` → 从该目录读取 `xxx.mp3` 返回**。这与上传 `transferTo` 的目录是同一套配置。

相关代码：`src/main/java/com/example/mgdemoplus/config/WebConfig.java`。

---

## 4. 前端如何把 `webPath` 变成可请求的地址

### 4.1 曲库页 `MusicUpload.vue`

方法 `audioSrc(webPath)`：

- 若 `webPath` 已是 `http` 开头，原样使用（预留外链）。
- 否则：
  - **开发**：`base = '/dev-api'`，最终 **`/dev-api` + `webPath`**，例如 `/dev-api/music/xxx.mp3`。
  - **生产**：`base = ''`，最终 **`/music/xxx.mp3`**（与站点同域）。

### 4.2 为何开发环境要 `/dev-api`

本地前端跑在 **Vue devServer**（如 `localhost:8080`），后端在 **Spring**（如 `localhost:8088`）。  
`axios` 的 `baseURL` 设为 `/dev-api`，由 **`vue.config.js` 的 `devServer.proxy`** 把请求转到后端并 **去掉** `/dev-api` 前缀。

因此：

- 列表/上传：`GET/POST /dev-api/dpMusic/...` → 转发为 `http://后端:8088/dpMusic/...`
- 试听：`GET /dev-api/music/...` → 转发为 `http://后端:8088/music/...` → 命中 **ResourceHandler**

配置见：`front/dp_game/vue.config.js`、`front/dp_game/src/main.js`。

---

## 5. 端到端流程（开发环境示意）

```text
数据库 web_path: /music/a.mp3

MusicUpload.vue  audioSrc("/music/a.mp3")
    → 浏览器请求: http://localhost:8080/dev-api/music/a.mp3
    → Vue 代理去掉 /dev-api
    → http://localhost:8088/music/a.mp3
    → Spring ResourceHandler /music/** → 磁盘 .../music/a.mp3
    → 返回音频流，<audio> 播放
```

生产环境通常前后端同域，`<audio>` 直接请求 **`/music/a.mp3`**，无 `/dev-api`。

---

## 6. 配置与部署

| 配置键 | 作用 |
|--------|------|
| `mgdemoplus.music.file-location` | 曲库文件目录；`WebConfig` 与 `DpMusicController` 共用，需一致 |

默认：`file:P:/javaworkspace/DPGameFiles/music/`。  
Docker 等环境见 `docker-compose.yml` 中的 `MGDEMOPLUS_MUSIC_FILE_LOCATION`。

建表脚本：`src/main/resources/db/dp_music_track.sql`。

---

## 7. 与「对局记录时间字段」的对比（避免混淆）

- **曲库**：`created_at` / `updated_at` 由表默认值维护；若 Mapper 的 `SELECT` 未查这两列，接口 JSON 里会显示 `null`（需把列写进查询并 `AS` 成 Java 属性名）。
- **对局记录 `dp_observed_hand_history`**：业务上的「这一手何时开始/结束」是字段 **`started_at_ms` / `ended_at_ms`**，在 `INSERT` 里显式写入；若表上另有审计时间列且未在 Java/Mapper 中映射，属于另一套设计，与曲库 `webPath` 流程无关。

---

## 8. 相关文件索引

| 文件 | 说明 |
|------|------|
| `front/dp_game/src/components/MusicUpload.vue` | 列表、`audioSrc`、试听 |
| `front/dp_game/vue.config.js` | `/dev-api` 代理到后端 |
| `front/dp_game/src/main.js` | `axios.defaults.baseURL` |
| `src/main/java/.../DpMusicController.java` | 上传、入库、`webPath` 生成 |
| `src/main/java/.../config/WebConfig.java` | `/music/**` 静态映射 |
| `src/main/java/.../mapper/dp/DpMusicTrackMapper.java` | 列表查询等 |

---

*文档日期：2026-04-01*
--- 
# 自己的笔记
文件是怎么访问的：  
前端拼出访问路径，请求不是走控制器类的，而是在webconfig类里注册并映射到本地的文件路径，从而实现访问文件的功能