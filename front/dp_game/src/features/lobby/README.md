# features/lobby

大厅模块：公开房列表、搜索筛选、建房、加入房间、密码房 gate。

## 目录

| 路径 | 说明 |
|------|------|
| `pages/LobbyPage.vue` | 大厅主页（原 `home.vue`） |
| `pages/CreateRoomPage.vue` | 建房页（原 `CreateRoom.vue`） |
| `components/` | `DpCreateRoomConsole`、`LobbyRoomPasswordGate` |
| `api/lobbyApi.js` | `publicRooms` / `joinRoom2` / `createRoom` 等 REST 薄封装 |
| `utils/` | 进房交接、建房提交、快匹静默退出 |
| `mixins/dpLobbyThemeMixin.js` | 大厅子页主题映射 |

快匹 WS 与 `QuickMatchPixelCritters` 暂留 `LobbyPage` 内，Wave 5 迁入 `features/quickmatch/`。

