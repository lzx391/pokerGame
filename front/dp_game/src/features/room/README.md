# features/room

对局域：进房、WS/心跳、下注、房主工具、局内聊天 UI、Vuex `dpGame`。

- `pages/GamePage.vue` — 原 `components/game.vue`
- `pages/ButtonGuidePage.vue` — 原 `GameButtonGuidePage.vue`
- `components/` — Game* 对局子组件（社交/NPC 组件仍留 `components/`，Wave 5/6 再迁）
- `store/dpGame.js` — 对局 Vuex 模块
- `api/roomApi.js` — 房间 REST（原 `api.dpRoom.js`）
- `utils/`、`mixins/`、`constants/` — 对局专用工具与常量

旧路径均保留 re-export 兼容层。
