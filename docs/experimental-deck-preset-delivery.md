# 实验玩法 · 预设下局牌序 — 交付说明

## 功能概述

房主可在 **Owner Hub（房主终端）** 中打开「实验玩法 / 预设下局牌序」，按**发牌顺序**选定 1～52 张牌作为下一局牌堆前缀。**对局进行中也可随时提交或更新**；下一局 `newHand` 时：

```
deck = [房主定的有序前缀] + shuffle(剩余未出现的牌)
```

用一次后自动清空 preset；不向其他玩家广播，房间快照也不下发 prefix/deck。当前进行中的手牌不受影响。

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
| `room/dto/SetNextHandDeckPrefixRequest.java` | POST 请求体 |
| `room/DpRoomService.java` | 接口声明 |
| `room/impl/DpRoomServiceImpl.java` | `buildDeckForNewHand` hook；set/get 实现 |
| `controller/DpRoomController.java` | REST 端点 |

### API

**POST** `/dpRoom/setNextHandDeckPrefix`

```json
{
  "roomId": "房间号",
  "requesterNickname": "房主昵称",
  "cards": ["hearts_A", "spades_K", "..."]
}
```

- 仅房主（`isRoomOwnerNickname`）
- **任意时刻**可设（含 preflop / flop 等对局进行中），仅影响下一局发牌
- 校验：`hearts_A` 格式、52 张合法编码、prefix 内无重复
- 空数组 = 清空预设

**GET** `/dpRoom/nextHandDeckPrefixStatus?roomId=&requesterNickname=`

- 仅房主可调用
- 返回 `{ presetCount, cards, canSet }`；`canSet` 恒为 `true`（房主通过校验后），`presetCount` 表示已预设张数
- **不**写入普通 `getNowRoom` 快照

### newHand 消费点

`DpRoomServiceImpl.newHandWithoutLobbyUpsert` 中 `setDeck(buildDeckForNewHand(r))`：有 prefix 则应用并 `setNextHandDeckPrefix(null)`，否则 `newDeck()` 纯随机。

## 前端改动

| 文件 | 说明 |
|------|------|
| `utils/dpDeckCards.js` | 52 张牌常量、`suggestedPrefixLength` |
| `components/GameDeckPresetDialog.vue` | el-dialog 排牌面板 |
| `components/GameOwnerHubContent.vue` | 主菜单新增入口 |
| `components/GameOwnerHubPanel.vue` | 事件透传 |
| `components/GameOwnerTouchPanel.vue` | 事件透传 |
| `components/GameDpGameSheets.vue` | 挂载 dialog |
| `components/game.vue` | API 调用与状态 |

入口路径：**房主终端 / 触控 Owner Hub →「实验玩法/预设下局牌序」**（retro8bit 宽屏终端与触控面板均可用）。

UI 提示：**随时可预设，下一局发牌时生效**；对局进行中不禁用点选与确认。

## 手动验证

1. 房主进房，打开 Owner Hub → 实验排牌，选若干张牌并确认。
2. GET `nextHandDeckPrefixStatus` 或 UI 应显示「已预设 N 张」。
3. **对局进行中**（如 preflop）再次打开面板：可点选并确认，API 应成功；当前手牌发牌顺序不变。
4. 下一局 `newHand` 开始：发牌顺序与前缀一致（可用固定 AA/KK 等易辨认组合）。
5. 再开一手：应恢复随机（preset 已消费）。
6. 非房主调用 API 应失败；`getNowRoom` 响应中无 `nextHandDeckPrefix` / `deck`。

## 构建

```bash
mvn clean package -DskipTests
cd front/dp_game && npm run build
```

## 约束遵守

- 无 Flyway 变更（纯内存房间字段）
- 无 git commit
- 无实验房标签、无 WebSocket 广播
