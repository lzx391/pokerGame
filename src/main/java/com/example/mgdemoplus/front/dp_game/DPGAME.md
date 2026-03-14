# dp_game

# 本文件是自己对德扑游戏后端代码的理解便于后期维护，AI需经过开发者允许才能修改此文件
一些编程思想：
开新局和结束最好都要记得重置状态  
重复函数要学会封装  
要在action方法里设置判定，每轮最后一个完成action的人就会走这个判定从而推进游戏进程
# 游戏流程
首先大家注册并登录进入游戏，然后房主创建房间，其他人在房间列表进入房间页准备，然后游戏开局，后续进入玩家先去观众表里，想玩得准备，然后是对局，直到结束并重开下一局

# 代码分析
## 前情提要
目前要考虑的有观众列表，准备下一把加入的观众人员列表，正在对局的玩家列表
状态有 刚开局，已经开始对局了 因此每个服务都要周全的考虑刚开局，已经开始的在对局玩家，已经开始的准备下一把加入玩家，已经开始的观众玩家四种状态
getWaitNextHand这个方法是看游戏开始后，新进来的在观众列表里并打算下一局入场的人员，在游戏开始后在newHand会被及时清理掉  
giveOwner这个方法是移交房主操作
getAllCanPlayer这个方法是在newhand里拉取准备玩家，清理筹码不足玩家，更新观众列表，也就是说返回马上可以游戏的玩家列表
## 首先是游戏正式开局前的阶段
### /getAllRooms2接口
用于在主页获取所有房间id,房主，活人
## 创建房间
### createRoom
用于创建房间，生成房间id，玩家列表，加入房主，设置准备状态，以便于其他玩家的房间列表显示
## 退出房间
### exitRoom
这里分两种情况：一是游戏还没开局就退出，二是开局了退出

首先清掉下一把准备记录，观众厅记录  
如果是还没开局就退出，然后如果是房主，先顺位继承，如果没有就直接删掉房间，如果有就先移交再退掉，然后把自己也移出去对局玩家记录  
如果已经开局了，那么把他弃牌并设置为僵尸牌，继续推进进程


## 加入房间
这里加入房间之后包括直接开局和准备会有多条逻辑线  
首先如果是刚开局，那就是joinRoom,如果是游戏中，又分观众席的准备和对局中玩家的准备，观众席进入下一波准备列表是readyNextHand，正在进行的对局进入下一把是toggleReady
### joinRoom
负责加入房间，如果游戏正在进行则防御重复进入导致准备下一手列表没及时清理的问题，加入观众列表
### readyNextHand
报名的话就进入准备下一把的列表里
### toggleReady
如果是刚开始，则把当前玩家设置成准备状态，返回true表示准备成功；  
如果是后续游戏，那就是准备下一把的阶段了，如果筹码不足10不让准备，如果筹码充足，每一个准备的人最后都会检测是否满足直接开下一把的条件checkAndStartNextHandAfterSettle
#### checkAndStartNextHandAfterSettle
看场上筹码充足的人是否全部准备，人齐了就开
### startGame
刚开始的时候设置初始各个人员基本数据，这时候全白板，没有设置D，SB,BB，此时调用newHand
### newHand
首先用exists过滤掉已经在对局里的玩家，然后如果是观众台里等待的玩家就当成新人配置相关数据并拉入场，移除对应的观众信息 **然后清理掉等待下一把玩家列表**自此所有可玩玩家入场  
然后把筹码不足大盲的人清到观众席，保留可玩玩家，由getAllCanPlayer方法返回
然后开始设置本局的房间信息包含洗牌发牌，确认新的庄家位，这里用字段lastDealerIndex来记录每一局的庄家位，人数大于2才有大小盲位设置，翻前从大盲的下一个开始行动，以便于转回到大盲位最后行动

因此截止到目前，joinRoom是进来，然后startGame，readyNextHand，toggleReady->checkAndStartNextHandAfterSettle是三种开局状态的准备，newHand是开局时拉人并负责各数据的配置
## 然后是游戏正式开局后的阶段
关键方法有
### moveToNextValidActor
负责把索引推进到下一个行动玩家
### autoAdvanceIfRoundFinished
负责自动推进游戏进程  
由此可见代码有两种推进进程的方式，一个是准备方法里最后检测一下人齐没，人齐就下一阶段；另一种是后端定时器检测推进，不过比较被动，用于处理离线，超时行动玩家
bet和fold可能会引起进程推进  
具体的进程推进调用顺序有moveToNextValidActor->autoAdvanceIfRoundFinished->advanceStage->findFirstActorAfterDealer
先执行moveToNextValidActor，主要是确定行动下标，如果下标不是-1，就不进行autoAdvanceIfRoundFinished，当全部行动完毕后才走autoAdvanceIfRoundFinished里面的advanceStage，然后会检查all in和 fold survive 触发连推或确认新一轮的行动者

---

# 游戏规则介绍（面向使用与文档）

## 德州扑克简要规则

- **目标**：用 2 张手牌 + 5 张公共牌组成最好的 5 张牌型，比大小；或通过下注让对手弃牌，赢得底池。
- **牌型从大到小**：皇家同花顺 > 同花顺 > 四条 > 葫芦 > 同花 > 顺子 > 三条 > 两对 > 一对 > 高牌。
- **一局流程**：发手牌 → 翻前下注（Preflop）→ 发 3 张翻牌（Flop）→ 翻牌圈下注 → 发 1 张转牌（Turn）→ 转牌圈下注 → 发 1 张河牌（River）→ 河牌圈下注 → 摊牌比牌（Showdown）→ 结算底池。

## 本游戏中的阶段

- **preflop**：翻前，只有手牌，大盲小盲已下。
- **flop**：翻牌，已发 3 张公共牌。
- **turn**：转牌，已发 4 张公共牌。
- **river**：河牌，5 张公共牌发完。
- **showdown**：摊牌，由房主选择赢家并按池结算（或系统自动比牌）。

## 下注与行动

- **跟注（Call）**：下到当前街的跟注额。
- **加注（Raise）**：在跟注额之上再加注。
- **弃牌（Fold）**：放弃本手牌，不再参与本局。
- **All-in**：全部筹码推进底池；不足跟注额时也可 All-in，形成边池。
- 当某条街所有人都跟齐或 All-in，或只剩一人未弃牌时，会自动进入下一阶段，直至摊牌。

## 庄位与盲注

- **庄家（D）**：每局轮换，庄家左侧为小盲（SB）、再左侧为大盲（BB）。
- 翻前从大盲下一位开始行动，顺时针依次行动。
- 人数 ≥ 2 时才有大小盲；单人或无人时不设盲注。

---

# 房间与对局核心概念

## 房间（DpRoom）

| 字段 / 概念 | 说明 |
|------------|------|
| roomId | 房间号 |
| owner | 房主昵称 |
| players | 当前局参与对局的玩家列表（DpPlayer） |
| playing | 是否正在进行一局牌 |
| currentStage | 当前阶段：preflop / flop / turn / river / showdown |
| communityCards | 公共牌 |
| pot / pots | 底池、主池与边池列表 |
| currentBetToCall / currentActorIndex | 当前跟注额、当前行动玩家下标 |
| waitNextHand | 已报名在下一局加入的玩家昵称列表（本局仅围观） |

## 玩家（DpPlayer）

| 字段 | 说明 |
|------|------|
| nickname | 昵称 |
| chips | 当前筹码 |
| holeCards | 手牌 |
| bet / totalBet | 本轮下注、本手牌累计下注 |
| fold / allIn | 是否弃牌 / 是否 All-in |
| dealer / blind | 是否庄家 / 盲注类型（如 SB、BB） |

---

# 观战与「下一局加入」

- 从房间列表进入**已开局的房间**时，会进入对局页；若不在本局 `players` 中，则视为**观众**：
  - 只能看公共牌和他人状态，不能下注、弃牌。
- 观众可点击「**准备在下一局加入对局**」：
  - 调用 `POST /dpRoom/readyNextHand?roomId=&nickname=`，将昵称加入该房间的 `waitNextHand`。
- 房主在本手牌结算后点击「重新发牌」时，`newHand` 会：
  - 将 `waitNextHand` 中的玩家加入牌桌（默认筹码 500），清空 `waitNextHand`，然后发新牌开始下一局。
- 因此：**中途进房的玩家可先观战，再在下一局自动上桌**。

---

# 接口文档（DpRoomController）

所有接口前缀：`/dpRoom`。除注明外，返回 `"ok"` 表示成功，`"fail"` 表示失败。

## 房间列表与单房间查询

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/getAllRooms2` | 获取所有房间摘要（房间 id、房主、在线人数等），用于主页房间列表。无参数。 |
| GET | `/getNowRoom` | 查询单个房间详情。参数：`roomId`。 |

## 房间与座位

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| POST | `/createRoom` | nickname | 创建房间并成为房主。 |
| POST | `/joinRoom` | roomId, nickname | 游戏未开始时加入为玩家；已开始时返回提示，前端仍可进房观战。 |
| POST | `/exitRoom` | roomId, nickname | 退出房间。未开局时可能移交或删除房间；已开局时该玩家弃牌并设为僵尸，进程继续。 |
| POST | `/transferOwner` | roomId, fromNickname, toNickname | 房主将房主移交给房间内另一玩家。 |

## 准备与开局

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| POST | `/toggleReady` | roomId, nickname | 未开局：准备/取消准备；已开局且在场：准备下一把。筹码不足可能无法准备。 |
| POST | `/startGame` | roomId, ownerNickname | 房主开始第一局。 |
| POST | `/newHand` | roomId, ownerNickname | 房主在结算后重新发牌，开始新一局；将 waitNextHand 中的玩家加入牌桌。 |
| POST | `/readyNextHand` | roomId, nickname | 观众或未在桌上的玩家报名在下一局加入对局。 |

## 对局行动

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| POST | `/bet` | roomId, nickname, bet | 当前行动玩家下注/跟注/加注/All-in。bet 为下注数额。 |
| POST | `/fold` | roomId, nickname | 当前行动玩家弃牌。 |
| POST | `/judgeWin` | roomId, potWinners | 摊牌后房主指定赢家并按池结算。potWinners 格式示例：`0:Alice;1:Bob,Charlie`（池索引:赢家昵称，多赢家用逗号分隔）。 |

## 其他

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| POST | `/heartbeat` | roomId, nickname | 心跳保活，用于剔除长时间离线玩家。无返回值。 |
| POST | `/rebuy` | roomId, nickname | 结算后筹码为 0 的玩家补码到初始筹码。 |

## 阶段自动推进

当本街无人需要行动时（所有人都跟齐、都 All-in，或只剩一人未弃牌），后端会**自动推进**到下一街，直至摊牌；一般无需房主手动点「下一阶段」。摊牌后由房主通过 `judgeWin` 指定赢家并结算。