# MGDemoPlus 首页（首访域名）冷启动性能方案

**角色与范围**：仅调研、度量和方案说明；不代替项目负责人做 git 提交/推送/合并 PR。  
**问题陈述**：生产环境「第一次打开域名（首页）」冷启动偏慢（约 10s 级）；用户主要在国内，源站在香港；**dev 环境满意可忽略**。  
**显式不做**：CDN、对象存储边缘、新增中间件（如 RabbitMQ、额外 Redis 等）、复杂多服务 Docker 编排。  
**允许**：重建 `**app`** / `**dpgame-nginx**` 镜像；在现有 `**docker/nginx/default.conf**` 增加适量 **gzip**、对 **带 hash 的静态资源** 长缓存等；运维保持简单。

---

## 1. 现状（基于只读代码 + 一次本地 `npm run build`）

### 1.1 `npm run build` 后 `dist` 体积概览

**构建环境**：仓库 `front/dp_game`，命令 `npm run build`（Vue CLI / `vue-cli-service build`）。  
**构建输出摘录**（与终端一致）：


| 资产                         | 原始大小          | Gzip 后（CLI 报告） |
| -------------------------- | ------------- | -------------- |
| `js/chunk-vendors.*.js`    | **~1008 KiB** | ~277 KiB       |
| `js/index.*.js`（主业务 chunk） | **~258 KiB**  | ~68 KiB        |
| `css/chunk-vendors.*.css`  | **~206 KiB**  | ~34 KiB        |
| `css/index.*.css`          | **~188 KiB**  | ~32 KiB        |


- **入口 `index` 合计**（CLI 警告）：约 **1.62 MiB**（含上述 JS + CSS），超过 webpack 建议的 244 KiB 单资源/入口限制。
- **体积最大的三个「业务相关」文件**（不含 `.map`）：  
  1. `chunk-vendors.*.js`（~1.0 MiB）
  2. `index.*.js`（~258 KiB）
  3. `chunk-vendors.*.css`（~206 KiB）
  另有 Element 图标字体 `element-icons.*`（约数十 KiB 级）。

**Source map**：生产目录中含 `*.js.map`（体积较大，如 vendors map 约 4.5 MiB）。若 Spring Boot 静态目录原样包含 `.map`，需评估是否对外暴露及是否打入 JAR（体积与安全性）；**不改变业务逻辑前提下**，可作为独立清理项。

### 1.2 `main.js` 里 Element UI 引入方式

文件：`front/dp_game/src/main.js`。

- **全量 JS**：`import ElementUI, { Message } from 'element-ui'` + `Vue.use(ElementUI)`。  
- **全量样式**：`import 'element-ui/lib/theme-chalk/index.css'`。

结论：**非按需引入**，Vendors chunk 与主题 CSS 体积与 Element 全量打包强相关。

### 1.3 路由是否懒加载

文件：`front/dp_game/src/router/index.js`。

- 各页面均为 **静态** `import`，例如 `import Home from '@/components/home.vue'`、`import Game from '../components/game.vue'` 等。  
- **未使用** `import()` 动态导入。

结论：**无路由级代码分割**；首页路径即便只用到 `login`/`home` 等，**首包仍会带上**对局页、手牌历史、曲库等路由组件相关依赖（与 vendors + 主 chunk 体积一致）。

### 1.4 入口 HTML 与静态资源路径

- 实际模板：`front/dp_game/public/index.html`（仓库根目录 **无** `public/index.html`；以 Vue 工程为准）。  
- 构建后 `dist/index.html` 以 `**defer`** 加载 `/js/chunk-vendors.*.js`、`/js/index.*.js`，并串行加载两套 CSS。  
- 生产与 JAR 同域，`axios` 在 production 下 `baseURL` 为空（`main.js`）。

### 1.5 Nginx 与 Compose 现状（与 Hub 对比）


| 文件                          | 要点                                                                                                                                                          |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docker/nginx/default.conf` | **仅反向代理**到 `app:8088`；**未配置 gzip / 静态资源缓存 / `expires`**。                                                                                                    |
| `docker-compose.yml`        | `**nginx**` 使用 `nginx:alpine`，**bind mount**：`./docker/nginx/default.conf` → `/etc/nginx/conf.d/default.conf`。**改配置后通常 `docker compose restart nginx` 即可**。 |
| `docker-compose.hub.yml`    | `**nginx`** 使用镜像 `**${DOCKER_REGISTRY}/dpgame-nginx:${IMAGE_TAG}**`（由 `**Dockerfile.nginx**` 构建），**仅挂载证书目录**，**不挂载 `default.conf`**。                        |


**运维提示**：

- **本地 / 挂载 compose**：改 `docker/nginx/default.conf` → restart `nginx` 服务。  
- **Hub 仅拉镜像**：若需使用仓库内新版 Nginx 配置，需 `**docker build -f Dockerfile.nginx …` 重建 `dpgame-nginx` 并推送/更新 tag**，再 `**docker compose -f docker-compose.hub.yml pull` + `up -d`**。

---

## 2. 瓶颈假设（需线上/类生产验证）


| 假设                      | 说明                                                                                              | 建议验证方式                                                                                                                              |
| ----------------------- | ----------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| **首包过大**                | 单次首访需下载 **~1.6 MiB+** 未压缩资源（或 gzip 后仍数百 KiB×多文件），国内访问香港 RTT 与带宽下延迟显著。                           | Chrome DevTools → Network，**Disable cache**，看 `**chunk-vendors` / `index`** 的 **Transferred** 与 **Time**；对比 gzip 开关前后（见下节 Nginx）。   |
| **TLS + 跨境链路**          | 香港源站，大陆用户首连、握手及拥塞控制拉长 TTFB/整体加载。                                                                | 同一页面看 **Waiting (TTFB)**；与「仅下载静态资源直连测试」对比（仅作诊断，不要求改架构）。                                                                             |
| **「关浏览器再开仍慢」**          | 可能 **每次仍重新拉大包**（缓存未生效 / 缓存键变化），或 **并非缓存问题**而是服务端/HTML/TTFB 慢。                                   | 二次访问开 **Disable cache** 关/off 对比；看响应头是否 `**Cache-Control`** / **304**；若二次仍大流量下载 vendors，则当前 **Nginx 未配长缓存** 会导致每次重新验证或重新拉取（视浏览器行为）。 |
| **Spring 全量经 JVM 提供静态** | 静态走 `app:8088`，无边缘缓存；压力点在应用与链路，**不在本方案引入 CDN 的前提下**，通过 **gzip + 客户端强缓存 hashed 资源** 降低重复体积与请求成本。 |                                                                                                                                     |


---

## 3. 推荐方案组合（P0 / P1）

### P0（优先：低成本、运维简单、符合约束）


| 项                        | 内容                                                                                                                                                   | 预期收益                                                            | 风险                                                                                 | 不回退点（验收锚点）                                                                |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| **Nginx 启用 gzip / 压缩类型** | 对 `text/css`、`application/javascript` 等启用 `gzip`（或 `gzip_static` 若有预压缩文件；当前构建未生成预压缩，通常 `**gzip on`** 即可）。                                            | 传输字节下降（CLI 已给 gzip 近似：vendors JS 约 **1008 KiB → ~277 KiB 量级**）。 | CPU 略增（一般可忽略）；需确认 `proxy_set_header Accept-Encoding` 行为与后端是否重复压缩（通常仅 nginx 压缩响应体）。 | 生产 Network：**Transferred** 显著小于 **Size**；总下载时间与首屏有可测改善。                   |
| **带 hash 的静态资源长缓存**      | 对 `/js/*`、`/css/*`（文件名含 hash）设置 `**Cache-Control: public, max-age=31536000, immutable`** 或长 `max-age`；`**index.html` 保持短缓存或不缓存**（避免发版后仍指向旧 chunk 名）。 | 回访与「同版本二次打开」大幅减少重复传输。                                           | 若只加长缓存却未隔离 `index.html`，可能导致 **旧 HTML 引用已删 chunk**；需 **HTML 短缓存 + 资源 hash** 组合。    | 二次打开（不勾 Disable cache）主要 JS/CSS **from disk/memory cache**；发版后强刷或版本切换仍正确。 |


### P1（前端打包结构：需改代码与回归，收益通常大于「仅 gzip」）


| 项                   | 内容                                                      | 预期收益                                              | 风险                                | 不回退点                                                    |
| ------------------- | ------------------------------------------------------- | ------------------------------------------------- | --------------------------------- | ------------------------------------------------------- |
| **Element UI 按需引入** | `babel-plugin-component` 或等价方案，仅注册实际用到的组件；样式按需或精简主题。    | **vendors JS/CSS 明显下降**（全量 Element 是 vendors 大户）。 | 需全面回归 UI；遗漏组件会导致运行时异常。            | 构建报告 vendors chunk 体积下降；核心页面无组件缺失。                      |
| **路由懒加载**           | `component: () => import('@/components/...')` 拆分 chunk。 | **首包 JS 下降**；非首屏页面延迟加载。                           | 需处理加载失败/骨架；首屏仍可能加载 home/login 依赖。 | `index.*.js` 缩小；首访仅加载当前路由相关 chunk（Network 可见多个小 chunk）。 |


---

## 4. 验收标准（可操作建议）

**构建侧（每次发版可做基线）**：

- 在 CI 或本地保存 `npm run build` 输出的 **File sizes / Gzipped** 表，对比 **chunk-vendors**、**index** 行。

**浏览器侧（类生产或生产）**：

1. 打开首页，DevTools → **Network**，勾选 **Disable cache**（测冷启动）。
2. 记录：
  - `**index.html`**：TTFB、总时间。  
  - `**chunk-vendors.*.js` / `index.*.js` / 主 CSS**：**Transferred**、**Time**、**Content-Encoding**（是否为 gzip/br）。
3. 取消 **Disable cache**，刷新：主 JS/CSS 应以 **memory/disk cache** 命中为主（实施长缓存后）。

**Web Vitals（可选，若不便上 RUM，可本地 + 限速）**：

- **LCP / FCP**：Lighthouse 或 Performance 面板；对比优化前后同类网络环境（例如 DevTools 限速「Fast 3G」模拟跨境体感）。

**达标方向性描述**（由项目组根据业务定数字）：  

- 冷启动首屏 **FCP/LCP** 较基线下降 **X%**，或 `**chunk-vendors` Transferred** 在开启 gzip 后接近 CLI **Gzipped** 列量级；P1 后 **首包 JS 总 Transferred** 下降 **Y%**。

---

## 5. 实现 Agent 清单

### 5.1 可能要改/要关注的文件路径


| 用途              | 路径                                                  |
| --------------- | --------------------------------------------------- |
| Nginx gzip / 缓存 | `docker/nginx/default.conf`                         |
| Nginx 镜像定义（Hub） | `Dockerfile.nginx`                                  |
| 本地 Compose 挂载说明 | `docker-compose.yml`（nginx volumes）                 |
| Hub 仅镜像部署说明     | `docker-compose.hub.yml`                            |
| Element 引入      | `front/dp_game/src/main.js`                         |
| 路由分包            | `front/dp_game/src/router/index.js`                 |
| 构建链（按需插件）       | `front/dp_game/package.json`、`babel.config.js`（若新增） |
| 入口 HTML         | `front/dp_game/public/index.html`                   |


### 5.2 明确不要做（与约束一致）

- **禁止**：接入公共 CDN、对象存储边缘、新增 RabbitMQ/额外 Redis 服务、复杂多容器编排改造。  
- **禁止（本轮）**：为性能上「顺手」做大范围业务重构、与首包无关的代码整顿。

---

## 6. 附：本次度量的构建信息（可复现）

- **命令**：`cd front/dp_game && npm ci && npm run build`  
- **结果**：`DONE Build complete`；webpack 报告 **entrypoint `index` ~1.62 MiB**；**chunk-vendors** 与 **index** 尺寸见第 1.1 节。  
- **browserslist**：未做 legacy/modern 双包（CLI 提示 targets 均支持 ES module，未 differential loading）。

---

*文档版本：与仓库分析时 `main.js` / `router/index.js` / `vue.config.js` / `docker/nginx/default.conf` / Compose 文件一致；若后续实现改动配置或打包，请同步更新本节数据。*

---

## 7. Implementation notes（落地记录）

**状态**：已按第 3 节 P0 + P1 在仓库内实现；未执行 git commit / push。

### 7.1 变更摘要

| 区域 | 文件 | 内容 |
| ---- | ---- | ---- |
| Element 按需 | `front/dp_game/package.json` | 新增 devDependency `babel-plugin-component` |
| Babel | `front/dp_game/babel.config.js` | 注册 `component` 插件（`libraryName: element-ui`，`styleLibraryName: theme-chalk`） |
| 入口 | `front/dp_game/src/main.js` | 移除全量 `Vue.use(ElementUI)` 与 `theme-chalk/index.css`；按项目实际用到的组件注册 **Badge / Button / Dialog / Drawer / Form / FormItem / Input / InputNumber / Table / TableColumn / Tooltip / Upload**，**Message / MessageBox** 挂到 `Vue.prototype`（`$message` / `$confirm` / `$alert`）；另引入 **`icon.css`**（`el-icon-*` 字体类） |
| 路由 | `front/dp_game/src/router/index.js` | 全部页面改为 `() => import(...)` + `webpackChunkName`，首屏只拉当前路由分包 |
| Nginx | `docker/nginx/default.conf` | `gzip`（`gzip_proxied any` 等）；对 **`/js/*`、`/css/*` 且文件名含 ≥8 位 contenthash** 的资源 `Cache-Control: public, max-age=31536000, immutable`（并 `proxy_hide_header` 上游 Cache 相关头，避免与 Spring 矛盾）；**`location = /`** 与 **`location = /index.html`** 强制 **`no-cache`**；其余 `location /` 仍完全走 Spring，未加覆盖 |

`Dockerfile.nginx` 仍为 `COPY docker/nginx/default.conf`，**无需改 Dockerfile**；Hub 场景重建镜像即带上新配置。

### 7.2 构建体积对比（本地 `npm run build`，2026-05-15）

| 指标 | 方案文档 §1.1 基线 | 落地后（同命令） |
| ---- | ------------------ | ---------------- |
| **入口 index 合计（CLI）** | 约 **1.62 MiB** | 约 **681 KiB** |
| `chunk-vendors.*.js` | ~1008 KiB（gzip ~277 KiB） | ~**475 KiB**（gzip ~**138 KiB**） |
| `index.*.js`（主入口 chunk） | ~258 KiB（gzip ~68 KiB） | ~**49 KiB**（gzip ~**16 KiB**） |
| `chunk-vendors.*.css` | ~206 KiB（gzip ~34 KiB） | ~**80 KiB**（gzip ~**13 KiB**） |

**首访 `/login` 时**（Network）：除 `index.html`、`chunk-vendors.*`、`index.*`、主 CSS 外，仅多一次 **`route-login.*.js`** 等小分包；进入大厅、对局等再按需加载对应 `route-*` chunk。

### 7.3 如何验证

1. **构建**：`cd front/dp_game && npm ci && npm run build`，应 `DONE Build complete`。
2. **传输与压缩（P0）**：类生产环境 DevTools → Network，对 `chunk-vendors`、`index`、路由 chunk 的响应头应有 **`Content-Encoding: gzip`**（或由 CDN/中间层代压时等效）；**Transferred** 接近 CLI **Gzipped** 量级、明显小于 **Size**。
3. **缓存（P0）**：关闭 Disable cache 二次刷新，`/js/*`、`/css/*` 带 hash 的资源应 **from disk/memory cache**；强刷或新版本后 HTML 仍能指向新 chunk 名。
4. **UI 回归（P1）**：重点过一遍 **登录/大厅抽屉与弹窗、手牌历史、曲库表格与上传、对局内 `$message` / `$confirm` / `$alert`**。

### 7.4 部署后如何让 Nginx 配置生效

| 部署方式 | 操作 |
| -------- | ---- |
| **`docker-compose.yml`**（挂载 `./docker/nginx/default.conf`） | 修改保存后执行 **`docker compose restart nginx`**（或等价的 `docker compose up -d nginx`） |
| **`docker-compose.hub.yml`**（`dpgame-nginx` 镜像，配置打在镜像内） | 在仓库根用 **`Dockerfile.nginx` 重建镜像**并推送/更新 tag，目标机 **`docker compose -f docker-compose.hub.yml pull`** 后 **`up -d`** |

### 7.5 与 Spring 静态、缓存头的关系

- 仅 **hash 静态路径**与 **精确 `/`、`/index.html`** 在 Nginx 层覆盖缓存头；**API 与其它 HTML 路径**仍使用 **Spring 原样响应头**，避免与业务缓存策略打架。
- 若未来 Spring 对部分静态也开启 **gzip**，需在联调中确认不会 **双重压缩**（一般 Boot 默认不压静态；当前以 Nginx 压缩为主）。