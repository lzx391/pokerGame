# 前端极简索引（供根 README 引用）

> 扫描日期：2026-05-22 · 详细 UI/主题/布局见 **[`front/dp_game/docs/README.md`](../../front/dp_game/docs/README.md)**。

| 项 | 说明 |
|----|------|
| 栈 | Vue 2、Vue Router（**默认 hash**）、Vuex、Element UI、axios |
| 路由 | `/#/home`、`#/game/:roomId` 等；**无**全局 `beforeEach`，401 由 `main.js` axios 拦截跳转登录 |
| 开发 HTTP | `axios.defaults.baseURL = '/dev-api'`；[`vue.config.js`](../../front/dp_game/vue.config.js) 代理到后端 **8088** 并剥 `/dev-api` 前缀 |
| 开发 WS | **`/dp-ws/dp-game`**、**`/dp-ws/dp-quick-match`** → 后端 **`/ws/...`**（避免与 HMR 的 `/ws` 冲突） |
| 生产 WS | 同域 **`/ws/dp-game`**、**`/ws/dp-quick-match`** |
| Vuex | **`dpGame`**（对局状态，无 actions）、**`dpMailbox`**（好友/邮箱，用 `api.dpSocial.js`） |
| 大厅 SSE | [`dpSocialStream.js`](../../front/dp_game/src/utils/dpSocialStream.js)（`EventSource` + `?token=`） |
| 嵌入 JAR | [`Dockerfile`](../../Dockerfile) 将 `dist` 拷入 `src/main/resources/static`；纯 `mvn package` 未必含最新前端 |
