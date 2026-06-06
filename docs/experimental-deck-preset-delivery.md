# 实验玩法 · 预设下局牌序 — 交付说明

## 功能概述

房主可在 **Owner Hub（房主终端）** 中打开「实验玩法 / 预设下局牌序」，按**发牌顺序**选定 1～52 张牌作为下一局牌堆前缀。**对局进行中也可随时提交或更新**；下一局 `newHand` 时：

```
deck = [房主定的有序前缀] + shuffle(剩余未出现的牌)
```

用一次后自动清空 preset；不向其他玩家广播，房间快照也不下发 prefix/deck。当前进行中的手牌不受影响。

## 访问密码门禁

打开排牌面板或调用 preset API **之前**需验证访问密码：

| 项 | 说明 |
|---|---|
| 环境变量 | `EXPERIMENTAL_DECK_PRESET_PASSWORD` |
| yml 键 | `mgdemoplus.experimental-deck-preset-password: ${EXPERIMENTAL_DECK_PRESET_PASSWORD:}` |
| 示例占位 | 见根目录 `.env.example`（**勿提交真实密码**） |
| 未配置 | 前后端均拒绝访问，提示「实验排牌功能未启用」 |
| 错误密码 | 提示「实验排牌访问密码错误」，**不泄露**配置值 |

### 前端解锁范围

- 验证通过后写入 `sessionStorage`：`dp_deck_preset_unlock_${roomId}`（值为密码，供后续 API 携带）
- 同一浏览器标签页、同一房间可复用，关闭标签页后需重输
- 绕过前端直接调 API 仍会被后端 `experimentalPassword` 校验拦截

### 后端 API

**POST** `/dpRoom/verifyExperimentalDeckPassword`

```json
{
  "roomId": "房间号",
  "requesterNickname": "房主昵称",
  "experimentalPassword": "访问密码"
}
```

- 仅房主；密码正确返回 `{ message: "访问密码验证通过" }`

**POST** `/dpRoom/setNextHandDeckPrefix`

```json
{
  "roomId": "房间号",
  "requesterNickname": "房主昵称",
  "experimentalPassword": "访问密码",
  "cards": ["hearts_A", "spades_K", "..."]
}
```

**GET** `/dpRoom/nextHandDeckPrefixStatus?roomId=&requesterNickname=&experimentalPassword=`

- 以上三个接口均校验 `experimentalPassword`（或 verify 通过后前端 session 内复用）

## 发牌顺序（UI 提示）

与后端 `getAllCanPlayer` 上桌顺序一致：

1. 各玩家底牌各 2 张（共 `2N` 张）
2. Flop 3 张
3. Turn 1 张
4. River 1 张

建议前缀长度：**前 `2N + 5` 张**（N = 当前桌上人数）。

## 后端改动

| 文件 | 说明 |
|------|------|
| `common/bo/DpRoomBO.java` | `@JsonIgnore List<String> nextHandDeckPrefix` |
| `utils/DpDeckUtil.java` | 52 张牌校验、`shuffleDeckWithPrefix` |
| `room/dto/SetNextHandDeckPrefixRequest.java` | POST 请求体（含 `experimentalPassword`） |
| `room/dto/VerifyExperimentalDeckPasswordRequest.java` | 密码验证请求体 |
| `room/support/DpExperimentalDeckPresetPasswordGuard.java` | 密码校验（常量时间比较） |
| `room/DpRoomService.java` | 接口声明 |
| `room/impl/DpRoomServiceImpl.java` | `buildDeckForNewHand` hook；set/get/verify 实现 |
| `controller/DpRoomController.java` | REST 端点 |
| `application.yml` | `${EXPERIMENTAL_DECK_PRESET_PASSWORD:}` |
| `.env.example` | 占位说明 |

### newHand 消费点

`DpRoomServiceImpl.newHandWithoutLobbyUpsert` 中 `setDeck(buildDeckForNewHand(r))`：有 prefix 则应用并 `setNextHandDeckPrefix(null)`，否则 `newDeck()` 纯随机。

## 前端改动

| 文件 | 说明 |
|------|------|
| `utils/dpDeckCards.js` | 52 张牌常量、`suggestedPrefixLength` |
| `utils/dpDeckPresetUnlock.js` | sessionStorage 解锁 helpers |
| `components/GameDeckPresetPasswordGate.vue` | 密码门禁（default / retro8bit 双形态） |
| `components/GameDeckPresetDialog.vue` | 排牌面板（retro8bit 终端文案） |
| `components/GameOwnerHubContent.vue` | 主菜单入口 |
| `components/GameDpGameSheets.vue` | 挂载 gate + dialog |
| `components/game.vue` | 解锁流程与 API 调用 |
| `styles/dp-game-themes.css` | retro8bit 排牌面板样式 |

入口路径：**房主终端 / 触控 Owner Hub →「实验玩法/预设下局牌序」**。

## 主题差异（default vs retro8bit）

| 区域 | default | retro8bit (`gameUiTheme === 'retro8bit'`) |
|------|---------|-------------------------------------------|
| 密码门禁 | 白底 `el-dialog`，中文提示 | CRT 终端层：绿字、`[ROOT] ACCESS:`、扫描线、`EXECUTE` / `ABORT` |
| 排牌面板 | 白底 dialog，中文标题与按钮 | 标题 `> DECK_PRESET // NEXT_HAND`；log 行已选区；像素绿框牌面；`CONFIRM_PRESET` / `ABORT` |
| 样式文件 | 组件 scoped 默认色 | `dp-game-themes.css` 中 `--retro8bit` 覆盖 |

## 手动验证

1. 在 `.env` 设置 `EXPERIMENTAL_DECK_PRESET_PASSWORD=你的测试密码`，重启后端。
2. 房主进房 → Owner Hub → 实验排牌：**应先弹出密码层**，错误密码有清晰提示且不泄露真值。
3. 密码正确后打开排牌面板；刷新同标签页可继续用（sessionStorage）；关标签重开需重输。
4. GET `nextHandDeckPrefixStatus` / POST `setNextHandDeckPrefix` 不带或带错密码应失败。
5. 不带 JWT 或非房主调用应失败（与原先一致）。
6. **retro8bit 主题**：密码层与排牌面板为终端/CRT 风格；切回 default 仍为白底 dialog。
7. 选牌确认 → 下一局发牌顺序与前缀一致 → 再开一手恢复随机。

## 构建

```bash
mvn clean package -DskipTests
cd front/dp_game && npm run build
```

## 约束遵守

- 无 Flyway 变更（纯内存房间字段）
- 无 git commit
- 密码仅 env，不进 Git
- 无实验房标签、无 WebSocket 广播
