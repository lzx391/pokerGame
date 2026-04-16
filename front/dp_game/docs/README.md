# DP 游戏前端专题文档（`front/dp_game`）

本目录存放 **对局页（`/game/:roomId`）** 相关的**前端专项说明**：布局调参、主题与 CSS 变量绑定等。后端规则、REST、WebSocket 协议仍以仓库根目录 **`docs/`** 为准，索引见 **[docs/README.md](../../../docs/README.md)**。

---

## 本目录文档一览

| 文档 | 简介 |
|------|------|
| **[GAME_LAYOUT_TUNING_README.md](GAME_LAYOUT_TUNING_README.md)** | 对局页**顶栏 / 圆桌 / 底栏**间距与 `dp-game-shell.css` 相关调参说明；宽屏与窄屏下主区滚动、圆桌垂直居中等布局约定。 |
| **[THEME_BINDING_README.md](THEME_BINDING_README.md)** | **`game.vue` 与各子组件**如何继承 `--dp-*` 主题变量；`data-dp-game-theme` 与 **`document.body` 同步**（Element UI 弹层）；**节能模式**与主题正交；**新增主题**时需在哪些文件登记与自检。 |

---

## 与后端文档的常见对应关系

| 你想了解 | 建议阅读 |
|----------|----------|
| WebSocket 地址、推送内容、房间聊天、音乐盒协议 | [docs/WEBSOCKET.md](../../../docs/WEBSOCKET.md) |
| NL 规则、最小加注、接口与房间逻辑维护笔记 | [docs/DPGAME.md](../../../docs/DPGAME.md) |
| 座位 UI 与「本机视角」旋转 | [docs/RoomUi.md](../../../docs/RoomUi.md) |
| 曲库路径与试听代理 | [docs/DpMusicWebPath.md](../../../docs/DpMusicWebPath.md) |
| 环境变量与本地开发 | [docs/ENV_README.md](../../../docs/ENV_README.md) |

---

## 源码中常与文档一起看的目录

- **`src/views/game.vue`**：对局页根组件，WebSocket、HTTP、`provide`、主题与 BGM。
- **`src/store/modules/dpGame.js`**：对局相关 Vuex 状态（命名空间 `dpGame`）。
- **`src/assets/css/`**：`dp-game-shell.css`、`dp-game-themes.css`、`dp-game-eco-mode.css`、`dp-game-element-ui.css` 等。
- **`src/utils/dpGameRoundTableLayout.js`**、**`dpGameDealerAnchor.js`**：圆桌几何与庄位/弃牌堆锚点。

更多变更级说明（组件拆分、倒计时轨道、Vuex 迁移等）分散在仓库根目录 **[README.md](../../../README.md)** 的「游戏对局 WebSocket」大节中。
