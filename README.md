## MGDemoPlus 德州扑克房间功能说明

本项目包含一个简单的德州扑克对战与房间系统，前端使用 Vue（位于 `src/main/java/com/example/mgdemoplus/front/dp_game`），后端使用 Spring Boot。

### 房间与对局核心概念

- **房间（DpRoom）**
  - `roomId`：房间号
  - `owner`：房主昵称
  - `players`：当前局真正参与对局的玩家列表（`DpPlayer`）
  - `playing`：是否正在进行一局牌
  - `currentStage`：当前阶段（如 `preflop`、`flop` 等）
  - `communityCards`：公共牌
  - `pot` / `pots`：底池与主池/边池列表
  - `currentBetToCall` / `currentActorIndex`：当前应跟注额度与当前行动玩家
  - `waitNextHand`：**已报名在下一局加入的玩家昵称列表（本局仅围观）**

- **玩家（DpPlayer）**
  - `nickname`：昵称
  - `chips`：当前筹码
  - `holeCards`：手牌
  - `bet` / `totalBet`：本轮下注与整手牌累计下注
  - `fold` / `allIn`：是否弃牌 / 是否 all-in
  - `dealer` / `blind`：庄家与盲注标记

### 观战与“下一局加入”功能

当用户通过房间列表进入一个**已经开局的房间**时：

- 前端仍然可以进入 `/room/:roomId`，随后自动跳转到 `/game/:roomId`，用户会看到当前对局进度，但如果不在 `players` 列表中，则视为**观众**：
  - 无法下注、弃牌等；
  - 只看到公共牌和其他玩家的状态。

在 `game.vue` 中，如果当前用户不是 `players` 里的成员，会显示一个“**准备在下一局加入对局**”按钮：

- 点击后调用后端接口：
  - `POST /dpRoom/readyNextHand?roomId=&nickname=`
  - 后端在对应房间的 `waitNextHand` 列表中登记该昵称。
- 当房主在当前手牌结算后点击“重新发牌”时，`newHand` 会：
  - 读取房间的 `waitNextHand`；
  - 为这些昵称创建新的 `DpPlayer`，默认筹码为 500，并加入 `players`；
  - 清空 `waitNextHand`，发新牌并开始下一局。

这样，**本局中途进来的玩家可以先围观当前对局，再在下一局自动加入牌桌**。

### 主要接口（后端 `DpRoomController`）

- `POST /dpRoom/createRoom?nickname=`：创建房间并成为房主。
- `GET  /dpRoom/getAllRooms`：获取所有房间信息。
- `POST /dpRoom/joinRoom?roomId=&nickname=`：在游戏尚未开始时加入房间成为玩家；若游戏已开始，则返回“游戏已开始”，前端仍可进入房间进行观战。
- `POST /dpRoom/toggleReady?roomId=&nickname=`：房间未开始时，玩家准备/取消准备。
- `POST /dpRoom/startGame?roomId=&ownerNickname=`：房主开始第一局。
- `POST /dpRoom/newHand?roomId=&ownerNickname=`：房主在结算后重新发牌，开始新一局，同时把 `waitNextHand` 中的玩家加入桌。
- `POST /dpRoom/bet?roomId=&nickname=&bet=`：当前行动玩家下注/跟注/加注/All-in。
- `POST /dpRoom/fold?roomId=&nickname=`：当前行动玩家弃牌。
- `POST /dpRoom/nextStage?roomId=&ownerNickname=`：房主推进到下一街（翻牌/转牌/河牌/摊牌）。
- `POST /dpRoom/judgeWin?roomId=&potWinners=`：房主在摊牌后选择赢家并按池结算。
- `POST /dpRoom/heartbeat?roomId=&nickname=`：心跳保活，用于剔除长时间离线玩家。
- `POST /dpRoom/readyNextHand?roomId=&nickname=`：**观众或当前未在桌上的用户报名在下一局加入对局**。

后续如果你再加新功能（比如观众聊天、限制观众人数等），可以继续在本文件中补充说明，方便自己和他人理解与使用。

