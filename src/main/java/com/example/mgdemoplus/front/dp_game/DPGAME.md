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
| bestHandCards | 当前阶段由服务端计算的本玩家「最大牌型」对应的 5 张牌（仅用于前端展示，翻牌圈及以后且公共牌≥3 时才有值） |
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

---

## AI / NPC 模块说明（德扑机器人）

> 详细设计文档已迁移到专门文件：  
> **`src/main/java/com/example/mgdemoplus/service/studentImpl/DP_NPC_README.md`**  
> 本节只保留整体思路，方便快速了解；深入调参与算法说明请看该 README。

### 一、当前实现概览（多风格 NPC 基础版）

当前版本实现了**统一 NPC 决策框架 + 多种示例风格**，在保持流程简单的前提下，让机器人行为更贴近真实玩家：

- **已支持的风格 / 类型（通过昵称识别）**：
  - `BOT_Fish`：简单鱼式玩家（原 BOT_Demo，范围较宽、偏被动，适合新手练习）；  
  - `BOT_Maniac`：疯子风格（明显更激进，经常加注甚至 all-in）；  
  - `BOT_Tag`：紧凶风格 TAG（Tight-Aggressive），选择进入的牌较少，但拿到不错牌力时更偏向价值下注；  
  - `BOT_Shark`：聪明型，会结合最近几手牌的行为和牌面做更“读牌”式的决策。
- **识别与决策入口现在集中在 `DpNpcEngine` 中**：
  - 判断是否为机器人：`DpNpcEngine.isBotPlayer(DpPlayer p)`  
  - 轮到机器人行动时，由 `DpNpcEngine.decideActionIfReady(DpRoom room, DpPlayer bot)` 计算动作，服务类再调用原有的 `bet` / `fold`。

房间服务 `DpRoomServiceImpl` 只负责：

- 在定时任务中识别当前行动者是否为 NPC；
- 如果是 NPC，则调用 `DpNpcEngine` 拿到一个 `BotAction`，再调用自身已有的下注 / 弃牌方法；
- 在结算后根据输赢调整机器人情绪 `mood`，其余 NPC 内部细节全部放在 `DpNpcEngine` 中维护。

- **整体思路**：
  - 前端房主在房主神器里点击按钮，请求在“下一局”加入一个 `BOT_Demo`；
  - 后端将该昵称加入 `DpRoom.waitNextHand`；
  - 下一局 `newHand` 时，`getAllCanPlayer` 会将 `BOT_Demo` 当作正常玩家加入牌桌（发筹码、发牌）；
  - 每次轮到他行动时，由后端 `autoActForBot` 自动帮他调用 `bet` / `fold`，无需前端按钮；
  - 结算后，在 `settled` 阶段自动补码、自动准备，避免被准备倒计时踢回观众席；
  - 房主可以随时通过“踢人到观众席”把机器人送走，下局不再自动回来，除非房主再次添加。

### 二、接口与调用链路

#### 1. 房主添加演示 NPC 到下一局

- **前端入口**：`game.vue` 中“房主神器”弹窗里的实验区块：

```77:115:src/main/java/com/example/mgdemoplus/front/dp_game/src/components/game.vue
    <!-- 房主神器弹窗 -->
    <div v-if="showOwnerToolModal" class="hand-rank-modal-mask" @click="closeOwnerToolPanel">
      <div class="hand-rank-modal" @click.stop>
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
          <span style="font-size:18px; font-weight:bold;">房主神器</span>
          <button @click="closeOwnerToolPanel"
                  style="background:#d9d9d9; border:none; width:28px; height:28px; border-radius:4px; cursor:pointer; font-size:16px; line-height:1;">×
          </button>
        </div>

        <div style="margin-bottom:12px; font-size:13px; color:#666;">
          仅显示当前在本局中的玩家（不含房主与僵尸位）。
        </div>

        <!-- 简单演示：添加一个机器人玩家 BOT_Demo 到下一局 -->
        <div style="margin-bottom:12px; padding:8px; border-radius:6px; background:#fff7e6; border:1px dashed #ffa940;">
          <div style="font-size:13px; font-weight:bold; color:#d46b08; margin-bottom:4px;">
            实验功能：加入演示 NPC
          </div>
          <div style="font-size:12px; color:#8c8c8c; margin-bottom:6px;">
            点击后，将在下一局自动加入一个演示用机器人玩家 <span style="font-weight:bold;">BOT_Demo</span>，用于验证 NPC 流程。
          </div>
          <button
              @click="addDemoBot"
              :disabled="demoBotAdding"
              style="padding:6px 10px; border:none; border-radius:4px; cursor:pointer; font-size:12px; font-weight:bold;
                     background:#faad14; color:#fff;">
            {{ demoBotAdding ? '正在添加 NPC...' : '添加演示 NPC 到下一局' }}
          </button>
          <div v-if="demoBotAddedTip"
               style="margin-top:4px; font-size:12px; color:#595959;">
            {{ demoBotAddedTip }}
          </div>
        </div>
```

- **前端调用的方法**：

```715:746:src/main/java/com/example/mgdemoplus/front/dp_game/src/components/game.vue
    async addDemoBot() {
      if (!this.roomId) return
      this.demoBotAdding = true
      this.demoBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addDemoBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.demoBotAddedTip = '已请求在下一局加入 BOT_Demo，请等待本局结束后自动入座。'
        } else {
          this.demoBotAddedTip = '添加 NPC 失败：' + res.data
        }
      } catch (e) {
        this.demoBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.demoBotAdding = false
      }
    },
```

- **控制器接口**：

```104:112:src/main/java/com/example/mgdemoplus/controller/DpRoomController.java
    @PostMapping("/addDemoBot")
    public String addDemoBot(@RequestParam String roomId) {
        return dpRoomService.addDemoBotToNextHand(roomId) ? "ok" : "fail";
    }
```

- **服务层实现：把 BOT_Demo 丢进下一局等待列表**：

```374:383:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
    public boolean addDemoBotToNextHand(String roomId) {
        return readyNextHand(roomId, DEMO_BOT_NICKNAME);
    }
```

> 总结：房主点击“添加演示 NPC”，本质就是帮昵称为 `BOT_Demo` 的“虚拟观众”调用了一次 `readyNextHand`，让它进入下一局候场列表。

#### 2. 开局时将 NPC 加入玩家列表

这一块完全复用原有“观众报名下一局加入”的逻辑，不区分真人 / 机器人：

```601:637:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
    public List<DpPlayer> getAllCanPlayer(DpRoom r) {
        // 防 null
        List<String> spectators = getNewSpectators(r);
        r.setSpectators(spectators);
        // 防筹码不足
        List<DpPlayer> canPlay = new ArrayList<>();
        for (DpPlayer p : r.getPlayers()) {
            if (p.getChips() < DpRoom.getBBChips()) {
                if (!spectators.contains(p.getNickname())) {
                    spectators.add(p.getNickname());
                }
            } else {
                canPlay.add(p);
            }
        }
        // 拉取准备下一把的
        List<String> waiters = r.getWaitNextHand();
        if (waiters != null && !waiters.isEmpty()) {
            for (String name : waiters) {
                DpPlayer np = new DpPlayer();
                np.setNickname(name);
                // 新加入的玩家带着默认筹码参与新一局
                np.setChips(DpRoom.getChips());
                np.setReady(true);
                canPlay.add(np);
                // 这些人已经回到牌桌，不再属于观众席
                if (spectators != null) {
                    spectators.remove(name);
                }
            }
            waiters.clear();
        }
        return canPlay;
    }
```

> 对 `BOT_Demo` 来说，只要它的昵称在 `waitNextHand` 里，就会被当作一个普通玩家创建 `DpPlayer`，发筹码、发两张手牌，加入本局。

#### 3. 行动阶段：自动下注 / 弃牌

定时器中，除了处理心跳、超时弃牌，还会检查当前行动位是不是机器人，是的话直接自动行动：

```17:59:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (DpRoom room : roomMap.values()) {
                    Iterator<DpPlayer> it = room.getPlayers().iterator();
                    while (it.hasNext()) {
                        DpPlayer p = it.next();
                        // 演示用机器人不依赖前端心跳，直接视为始终在线
                        if (isBotPlayer(p)) {
                            p.setLastHeartBeat(System.currentTimeMillis());
                            continue;
                        }
                        // 本手已离线的“占位”玩家不因心跳踢出...
                        if (p.isLeftThisHand()) continue;
                        if (System.currentTimeMillis() - p.getLastHeartBeat() > DpRoom.getHeartTimeout()) {
                            ...
                            it.remove();
                        }
                    }
                    // 30秒超时弃牌；若当前行动位是已离线占位，直接跳过不等待
                    if (room.isPlaying() && room.getCurrentActorIndex() >= 0) {
                        DpPlayer p = room.getPlayers().get(room.getCurrentActorIndex());
                        // 如果当前行动者是 NPC，则直接由后端自动决策行动，不等待前端操作
                        if (isBotPlayer(p)) {
                            autoActForBot(room, p);
                        } else {
                            ...
                        }
                    }
                    ...
                }
            }
        }, 0, 2000);
```

机器人决策的核心入口仍然是 `autoActForBot`，但已经抽象为“根据类型分派 + 通用决策函数”的结构：

```1731:1751:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
    private void autoActForBot(DpRoom room, DpPlayer bot) {
        if (room == null || bot == null || !room.isPlaying()) {
            return;
        }
        int actorIndex = room.getCurrentActorIndex();
        if (actorIndex < 0 || actorIndex >= room.getPlayers().size()) {
            return;
        }
        // 防御：当前行动位必须就是这个 bot，且未弃牌/未 all-in
        DpPlayer current = room.getPlayers().get(actorIndex);
        if (current != bot) {
            return;
        }
        if (bot.isFold() || bot.isAllIn() || bot.isLeftThisHand()) {
            return;
        }
        // 通过昵称决定机器人类型
        BotType type = getBotTypeByNickname(bot.getNickname());
        if (type == null) {
            return;
        }

        BotAction action = decideBotAction(room, bot, type);
        if (action == null) {
            return;
        }

        String roomId = room.getRoomId();

        switch (action.getType()) {
            case FOLD:
                fold(roomId, bot.getNickname());
                break;
            case CALL_OR_CHECK: {
                int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
                bet(roomId, bot.getNickname(), callAmount);
                break;
            }
            case RAISE:
            case ALL_IN: {
                int amount = Math.max(0, action.getAmount());
                bet(roomId, bot.getNickname(), amount);
                break;
            }
            default:
                break;
        }
    }
```

> 总结：所有机器人行动最终都汇总到一个 `decideBotAction(room, bot, type)`，不同风格只是在这个函数内部走不同分支，方便后续继续扩展更多性格。

#### 4. 决策要素：牌力、牌面危险度与情绪（mood）

当前版本的机器人下注决策，不再是“只看筹码比例 + 随机数”，而是考虑了三个纵向因素：

- **粗粒度牌力判断 `SimpleStrength`**：  
  - 通过已有的牌型评估函数，将 2+公共牌的 7 张牌压缩为 `WEAK / MEDIUM / STRONG`；  
  - preflop 阶段用简单规则（口袋对、高张连牌）估计强弱；  
  - 翻牌后直接按牌型档位粗分（高牌/一对/两对/三条/顺子/同花/葫芦+）。

```1540:1670:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
    private enum SimpleStrength {
        WEAK,
        MEDIUM,
        STRONG
    }

    private SimpleStrength estimateCurrentStrength(DpRoom room, DpPlayer bot) {
        List<String> hole = bot.getHoleCards();
        List<String> community = room.getCommunityCards();
        if (hole == null || hole.size() < 2) {
            return SimpleStrength.WEAK;
        }
        List<String> all = new ArrayList<>(hole);
        if (community != null) {
            all.addAll(community);
        }
        if (all.size() < 5) {
            // 公共牌不足 3 张时，用简单 preflop 规则
            return toSimpleStrength(null, room.getCurrentStage(), hole, community);
        }
        HandStrength hs = evaluateBestHand(all);
        return toSimpleStrength(hs, room.getCurrentStage(), hole, community);
    }
```

- **公共牌危险度 `BoardDanger`**：  
  - 检查公共牌是否存在“同花可能”（某花色 ≥ 3 张）或“顺子结构”（至少 3 张连续点数）；  
  - 将牌面标记为 `DRY`（干燥，不太容易听牌）或 `WET`（湿，听牌很多），影响弱牌时的弃牌/跟注选择。

```1672:1725:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
    private enum BoardDanger {
        DRY,
        WET
    }

    private BoardDanger evaluateBoardDanger(List<String> communityCards) {
        if (communityCards == null || communityCards.size() < 3) {
            return BoardDanger.DRY;
        }
        Set<Integer> ranks = new HashSet<>();
        Map<String, Integer> suitCount = new HashMap<>();
        for (String c : communityCards) {
            ...
        }
        // 同花或顺子结构存在时视为 WET
        ...
    }
```

- **情绪值 `mood`（存在 `DpPlayer` 上，仅 NPC 使用）**：  
  - 机器人有一个 `double mood` 字段（范围约在 [-1, 1]），用于模拟“连赢更敢冲、连输更保守”；  
  - 每次自动结算 `autoSettle` 后，根据结算后筹码 vs 初始筹码的差值，轻微调节情绪：
    - 赢了钱 → `mood += 0.2`；  
    - 输了钱 → `mood -= 0.2`；  
    - 并做上/下限裁剪。

```1:60:src/main/java/com/example/mgdemoplus/entity/DpPlayer.java
public class DpPlayer {
    ...
    private List<String> bestHandCards = new ArrayList<>();

    /**
     * 机器人情绪值：范围建议在 [-1.0, 1.0] 内，
     * 用于简单模拟“连赢变得更放松，连输变得更保守”的状态。
     * 对真人玩家逻辑没有影响，只有在 NPC 决策时才会读取。
     */
    private double mood = 0.0;

    public double getMood() { return mood; }
    public void setMood(double mood) { this.mood = mood; }
}
```

```968:983:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
        r.setPot(0);
        r.setPots(new ArrayList<>());

        // 根据赢/输调整机器人情绪：赢到筹码的机器人情绪略微变高，输掉筹码的略微变低
        for (DpPlayer p : r.getPlayers()) {
            if (!isBotPlayer(p)) {
                continue;
            }
            int initialChips = DpRoom.getChips();
            int diff = p.getChips() - initialChips;
            double moodDelta;
            if (diff > 0) {
                moodDelta = 0.2;
            } else if (diff < 0) {
                moodDelta = -0.2;
            } else {
                continue;
            }
            double newMood = p.getMood() + moodDelta;
            if (newMood > 1.0) newMood = 1.0;
            if (newMood < -1.0) newMood = -1.0;
            p.setMood(newMood);
        }
```

这三个要素会在统一的 `decideBotAction` 内被综合使用，不同风格通过不同概率/倍数体现不同性格。

#### 5. 结算后：机器人自动补码 & 自动准备

为避免机器人在结算阶段因为“没补码 / 没准备”被倒计时逻辑踢到观众席，这里对 `settled` 阶段的机器人做了自动处理：

```1292:1368:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
                    // 结算后准备阶段：机器人自动补码并自动准备
                    if (room.isPlaying() && "settled".equals(room.getCurrentStage())) {
                        boolean botTouched = false;
                        for (DpPlayer p : room.getPlayers()) {
                            if (!isBotPlayer(p)) continue;
                            // 若筹码不足大盲，自动补码（内部已有阶段与筹码校验）
                            if (p.getChips() < DpRoom.getBBChips()) {
                                rebuy(room.getRoomId(), p.getNickname());
                            }
                            // 筹码充足时自动准备
                            if (p.getChips() >= DpRoom.getBBChips() && !p.isReady()) {
                                p.setReady(true);
                                botTouched = true;
                            }
                        }
                        // 只要有机器人准备状态被更新，就检查是否可以直接开下一局
                        if (botTouched) {
                            checkAndStartNextHandAfterSettle(room);
                        }
                    }
```

> 这样，机器人输光后会在结算阶段自动补码一次，并立刻进入 ready 状态。  
> 如果场上所有“有能力继续玩的人”（筹码 ≥ 大盲）都准备好了，`checkAndStartNextHandAfterSettle` 会直接开新一局。

#### 5. 踢人逻辑与机器人

`kickPlayer` 的实现对昵称没有特别区分，机器人和真人一视同仁：

```141:171:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
    public boolean kickPlayer(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        int idx = -1;
        List<DpPlayer> ps = r.getPlayers();
        for (int i = 0; i < ps.size(); i++) {
            DpPlayer p = ps.get(i);
            if (p.getNickname().equals(nickname)) {
                idx = i;
                break;
            }
        }
        if (idx != -1) {
            if (r.getCurrentActorIndex() == idx) {
                // fold 会将该玩家标记为弃牌并轮到下一个人
                fold(roomId, nickname);
            } else {
                // 非当前行动玩家，直接视为弃牌
                if (!ps.get(idx).isFold()) {
                    ps.get(idx).setFold(true);
                }
            }
            ps.get(idx).setReady(false);
            ps.get(idx).setLeftThisHand(true);
            List<String> spectators = getNewSpectators(r);
            if(!spectators.contains(nickname)){
                spectators.add(nickname);
            }
            return true;
        } else {
            return false;
        }
    }
```

> 这意味着：  
> - 房主可以像踢普通玩家一样踢出 `BOT_Demo`；  
> - 被踢后它会进入观众席，且不会自动重新上桌，必须房主再次通过房主神器添加 NPC。

### 三、AI 调参与未来扩展（紧凶 / 松弱 / 石头人 / 疯子）

当前版本只有一个 `BOT_Demo`，策略也故意写得很“肉眼可理解”，方便后续基于此扩展更多类型的 AI。可以参考如下方向规划：

- **AI 性格维度**（可以用枚举或配置表示）：
  - **紧/松（Tight / Loose）**：参与底池的频率（起手范围宽不宽）。
  - **凶/弱（Aggressive / Passive）**：倾向于加注还是只跟注。
  - **石头人（Rock）**：极紧极被动，只打超级好牌，其他几乎都弃牌。
  - **疯子（Maniac）**：极松极激进，经常在牌力一般甚至很差时疯狂加注 / all-in。

- **建议的参数设计（伪概念）**：
  - `participationRate`：参与底池概率（松 > 紧）。
  - `raiseRate`：在可跟注时选择加注的概率（凶 > 弱）。
  - `bluffRate`：在牌力较差时仍然选择下注/加注的概率（疯子 > 普通 > 石头人）。
  - `callThreshold`：面对相对筹码占比较大的下注时，仍然愿意跟注的最大比例。

> 未来如果要落地“紧凶、紧弱、松凶、松弱、石头人、疯子”六种类型，可以在 `autoActForBot` 外再加一层：
>
> - 用昵称或配置映射出 `BotType`（比如 `BOT_TightAggro` → 紧凶）。  
> - 拆出 `decideAction(room, bot, botType)`，内部根据 `botType` 与牌力评估（可复用现有牌型算法）决定下注策略。  
> - 当前的 `autoActForBot` 可以作为“松弱 / 普通人”基线，在此基础上增加或降低跟注、加注、诈唬的概率。

在实际扩展时，建议：

- 继续把**所有机器人决策逻辑集中在服务层一个区域**（类似目前的 `autoActForBot` + 若干辅助方法），避免散落到各个接口；  
- 所有可调参数（参与率、加注倍率、诈唬率等）统一放在一个静态配置或枚举上，方便以后微调“AI 难度”；  
- 保持对真人玩家流程零侵入：即使关掉机器人相关代码，真人照常能玩完整局。

### 四、机器人“思考时间”与节奏感（强牌长考、弱牌秒出）

为让机器人更像真人玩家，在 `DpRoomServiceImpl` 中对所有 NPC 新增了简单的“思考时间”机制：

- **字段**：在 `DpPlayer` 上增加 `nextBotActionTime`（仅 NPC 使用），表示“下一次允许自动行动的时间戳（毫秒）”。
- **入口**：定时任务中轮到机器人行动时，`autoActForBot(room, bot)` 会先检查：
  - 若 `nextBotActionTime` 尚未设置，则根据当前牌力 `estimateCurrentStrength`、需要跟注筹码比例、机器人类型与情绪，调用 `calculateBotThinkDelay` 生成一个延迟（毫秒），保存为 `bot.nextBotActionTime`，**本轮先不真正行动**；
  - 之后每 2 秒定时器再次触发时，只有当 `System.currentTimeMillis() >= nextBotActionTime` 时，才真正调用 `decideBotAction` + `bet/fold` 完成这次行动，并把 `nextBotActionTime` 清零。

#### 4.1 分开配置（按机器人类型独立调参）

现在思考时间**不是统一一套**，而是按 `BotType` **分开配置**（你想怎么调都行，不用动决策逻辑）：

- **配置位置**：`DpRoomServiceImpl` 的“机器人思考时间配置”区域里，三个配置对象：
  - `THINK_DEMO`：普通机器人
  - `THINK_MANIAC`：疯子（更快、更爱秒出手）
  - `THINK_SHARK`：聪明型（更爱“故作思考”，但也保留秒 call 的随机性）
- **你能配置什么**（单位都是毫秒）：
  - `weakMinMs/weakMaxMs`、`mediumMinMs/mediumMaxMs`、`strongMinMs/strongMaxMs`：三档牌力的思考时间区间
  - `cheapMinMs/cheapMaxMs` + `cheapSnapProb`：当 `callAmount == 0` 或 `callRatio <= 0.2` 时，走“秒 call / 秒过牌”区间的概率
  - `globalSnapProb`：无论是否 cheap、无论牌力，直接“秒出手”的概率（用来制造“突然秒 call/秒 raise”的真人感）
  - `maxCapMs`：单次思考的上限（防止拖慢节奏）

这样你就可以做到：

- `BOT_Maniac`：raise/call 很多时候直接秒出或 1 秒内出手
- `BOT_Shark`：强牌更可能长考，但也会偶尔秒 call 迷惑对手

#### 4.2 行为规则（只影响机器人，不影响真人超时逻辑）

整体规则：

- **强牌（`SimpleStrength.STRONG`）**：
  - 走 `strongMinMs~strongMaxMs`；
  - 效果：拿到葫芦、顺子、同花以上的牌时，经常会出现“明显多想一会再动”的感觉。
- **中等牌（`MEDIUM`）**：
  - 走 `mediumMinMs~mediumMaxMs`；
  - 效果：一对、两对、三条时节奏适中，看起来像在权衡要不要跟/加注。
- **弱牌（`WEAK`）**：
  - 常规走 `weakMinMs~weakMaxMs`；
  - 若当前需要跟注筹码极小（`callRatio <= 0.2`）或可以 free check（`callAmount == 0`），会按 `cheapSnapProb` 以较高概率走 `cheapMinMs~cheapMaxMs` 的“秒 call / 秒过牌”区间；
  - 整体感觉是：垃圾牌要么很快弃牌，要么在便宜的跟注/过牌场景瞬间做决定。
- **不同风格差异**：
  - 通过不同 `THINK_*` 配置直接体现；
  - 情绪值 `mood` 会微调思考时间：连赢变得更爽快出手，连输则犹豫更久。

限制与安全性：

- 机器人单次行动的思考时间有上限：**不超过约 12 秒**，远小于 30 秒超时自动弃牌的窗口，不会影响整局进程；
- 所有逻辑都包裹在 `autoActForBot` 里，对真人玩家和接口协议完全无侵入；
- 前端依旧通过 `room.currentActorIndex` 高亮“思考中...”的玩家，因此你会看到：
  - 有时机器人一到自己就立刻 call / all-in；
  - 有时则会在圈内“闪黄圈 + 思考中...”停留几秒，再突然做出一个明显是强牌的慢打或可疑的诈唬动作。

---

## 实时展示“自己最大手牌”功能说明

这一块可以理解为：**后端算出“哪 5 张牌是你当前能组成的最大牌型”，前端轮询房间状态时一并拿到，并在界面上实时展示**。实现分为三层：

- **后端服务层：计算并填充每个玩家当前最佳 5 张牌（`bestHandCards`）**
- **接口层：`/dpRoom/getNowRoom` 周期性返回包含 `bestHandCards` 在内的房间快照**
- **前端页面：`game.vue` 将 `bestHandCards` 和本地计算的牌型文字 `getHandRank` 一起展示出来**

### 一、后端：`DpRoomServiceImpl` 中的核心逻辑

#### 1. 房间查询入口 `getAllRooms(String roomId)`

```181:201:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
    public DpRoom getAllRooms(String roomId) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return null;
        // 当公共牌不少于 3 张时，为每位有手牌的玩家计算并填充「最大牌型的 5 张牌」，供前端展示
        List<String> community = r.getCommunityCards();
        if (community != null && community.size() >= 3) {
            for (DpPlayer p : r.getPlayers()) {
                if (p.getHoleCards() != null && p.getHoleCards().size() >= 2) {
                    List<String> all = new ArrayList<>(p.getHoleCards());
                    all.addAll(community);
                    p.setBestHandCards(getBestHandCards(all));
                } else {
                    p.setBestHandCards(Collections.emptyList());
                }
            }
        } else {
            for (DpPlayer p : r.getPlayers()) {
                p.setBestHandCards(Collections.emptyList());
            }
        }
        return r;
    }
```

- **调用时机**：前端轮询接口 `GET /dpRoom/getNowRoom?roomId=` 时，Controller 会调用这个方法，把当前房间 `DpRoom` 返回给前端。
- **逻辑要点**：
  - 只有当 **公共牌数量 ≥ 3**（翻牌圈及以后），才开始计算“最大手牌”，避免在只有手牌时做无意义运算。
  - 对每个玩家 `DpPlayer`：
    - 若手牌存在且有 2 张，合并「2 张手牌 + 公共牌」得到最多 7 张牌列表 `all`。
    - 调用 `getBestHandCards(all)` 得到当前能组成的**最佳 5 张牌**，赋值给 `p.bestHandCards`。
    - 否则（比如还没发手牌），`bestHandCards` 设为空列表。
  - 如果公共牌不足 3 张，则所有玩家的 `bestHandCards` 都清空，前端自然不展示。

#### 2. 选出“最佳 5 张牌”的算法 `getBestHandCards`

```804:873:src/main/java/com/example/mgdemoplus/service/studentImpl/DpRoomServiceImpl.java
    private List<String> getBestHandCards(List<String> cards) {
        List<int[]> parsed = new ArrayList<>();
        Map<String, Integer> rankMap = new HashMap<>();
        // rankMap: "2"~"A" -> 2~14
        ...
        for (String c : cards) {
            if (c == null) continue;
            String[] parts = c.split("_");
            ...
            parsed.add(new int[]{rank, suitCode});
        }
        if (parsed.size() < 5) {
            return Collections.emptyList();
        }
        int n = parsed.size();
        HandStrength best = null;
        List<String> bestCards = null;
        // 生成所有 C(n,5) 组合，逐一评价
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    for (int a = k + 1; a < n; a++) {
                        for (int b = a + 1; b < n; b++) {
                            List<int[]> hand = Arrays.asList(
                                    parsed.get(i), parsed.get(j), parsed.get(k), parsed.get(a), parsed.get(b)
                            );
                            HandStrength hs = evaluateFiveCardHand(hand);
                            if (best == null || hs.compareTo(best) > 0) {
                                best = hs;
                                bestCards = Arrays.asList(
                                        cards.get(i), cards.get(j), cards.get(k), cards.get(a), cards.get(b)
                                );
                            }
                        }
                    }
                }
            }
        }
        if (bestCards == null || best == null) {
            return Collections.emptyList();
        }
        return sortCardsForDisplay(new ArrayList<>(bestCards), best);
    }
```

- **输入**：长度 5~7 的牌字符串列表（格式 `"hearts_A"` 这种）。
- **处理步骤**：
  1. 先把字符串解析成 `rank`（2~14，对应 2~A）和 `suitCode`（红桃/方片/梅花/黑桃）。
  2. 枚举所有 `C(n, 5)` 个 5 张牌组合（德扑标准：7 选 5）。
  3. 对每个 5 张组合调用 `evaluateFiveCardHand(hand)` 计算牌型强度，得到一个 `HandStrength`：
     - `rankCategory`：牌型大类（如 10=皇家同花顺，9=同花顺，8=四条，…，1=高牌）。
     - `ranks`：用来打平的点数列表（比如四条 AAAA2 的 [A,2]）。
  4. 按 `HandStrength.compareTo` 挑出**牌力最大**的那一组 5 张牌，对应的原始字符串保存到 `bestCards`。
  5. 最后调用 `sortCardsForDisplay(bestCards, best)` 做一个「更好看」的顺序整理，比如：
     - 三条显示为 `J J J A 2`
     - 两对显示为 `A A K K 2`
- **输出**：已经排好顺序、长度固定为 5 的字符串列表，直接给前端渲染。

> 总结：服务端负责“从 7 张里挑 5 张 + 牌力评估 + 排序”，最终以 `DpPlayer.bestHandCards` 字段的形式挂在玩家对象上，随着房间状态一起返回前端。

### 二、接口层：前端如何拿到 `bestHandCards`

前端 `game.vue` 里有一个**每秒轮询**房间状态的方法 `loadGame`：

```603:637:src/main/java/com/example/mgdemoplus/front/dp_game/src/components/game.vue
    async loadGame() {
      this.loading = true
      try {
        var res = await this.$http.get('/dpRoom/getNowRoom', {
          params: {roomId: this.roomId}
        })
        var room = res.data
        if (!room) {
          ...
        }
        this.owner = room.owner
        this.players = room.players || []
        this.playing = room.playing
        this.stage = room.currentStage
        this.communityCards = room.communityCards || []
        this.syncCommunityCardsFlipState(room.communityCards || [])
        this.pot = room.pot
        this.pots = room.pots || []
        this.currentBetToCall = room.currentBetToCall
        this.actIndex = room.currentActorIndex
        this.spectators = room.spectators || []
        // 报名下一局状态以服务端为准
        var list = room.waitNextHand || []
        this.nextHandReady = !!(this.user && list.indexOf(this.user.nickname) !== -1)
      } finally {
        this.loading = false
      }
    },
```

- 这里的 `room.players` 实际上就是刚才 `getAllRooms` 返回的 `DpPlayer` 列表，**已经包含了 `bestHandCards` 字段**。
- 因为前端用的是 `this.players = room.players || []` 直接整包替换，所以每一次轮询，`players[i].bestHandCards` 都会被最新值覆盖，实现了“实时更新”。

> 调用关系（从前端往后看）：
>
> - `game.vue` 每秒调一次 `GET /dpRoom/getNowRoom`
> - Controller 内部调用 `DpRoomServiceImpl.getAllRooms(roomId)`
> - `getAllRooms` 根据当前公共牌和手牌计算每个玩家的 `bestHandCards`
> - 前端拿到新的 `room.players`，界面随之刷新。

### 三、前端展示：`game.vue` 中的显示逻辑

#### 1. 模板中展示文字牌型 + 5 张最佳牌

```179:210:src/main/java/com/example/mgdemoplus/front/dp_game/src/components/game.vue
          <!-- 牌型显示：文字 + 最大牌型的 5 张牌 -->
          <div v-if="communityCards.length >= 3 && communityCardsFlipComplete && (isMe(p.nickname) || ((stage === 'showdown' || stage === 'settled') && !p.fold))"
               style="margin-top:4px; text-align:center;">
            <template v-if="isMe(p.nickname)">
                <span
                    style="background:#e6f7ff; color:#1890ff; padding:3px 10px; border-radius:4px; font-weight:bold; font-size:12px; display:inline-block;">
                  {{ getHandRank(p.holeCards, communityCards) }}
                </span>
                <div v-if="p.bestHandCards && p.bestHandCards.length === 5" class="best-hand-cards"
                     style="display:flex; gap:4px; justify-content:center; margin-top:6px; flex-wrap:wrap;">
                  <div v-for="(c, ci) in p.bestHandCards" :key="'best'+ci"
                       :class="[getCardClass(c), 'best-hand-card']"
                       style="width:32px; height:46px; font-size:11px; display:inline-flex; align-items:center; justify-content:center; border-radius:4px;">
                    {{ getCardDisplay(c) }}
                  </div>
                </div>
            </template>
            <template v-else-if="(stage === 'showdown' || stage === 'settled') && !p.fold">
                <span
                    style="background:#f6ffed; color:#52c41a; padding:3px 10px; border-radius:4px; font-weight:bold; font-size:12px; display:inline-block;">
                  {{ getHandRank(p.holeCards, communityCards) }}
                </span>
                <div v-if="p.bestHandCards && p.bestHandCards.length === 5" class="best-hand-cards"
                     style="display:flex; gap:4px; justify-content:center; margin-top:6px; flex-wrap:wrap;">
                  <div v-for="(c, ci) in p.bestHandCards" :key="'best'+ci"
                       :class="[getCardClass(c), 'best-hand-card']"
                       style="width:32px; height:46px; font-size:11px; display:inline-flex; align-items:center; justify-content:center; border-radius:4px;">
                    {{ getCardDisplay(c) }}
                  </div>
                </div>
            </template>
          </div>
```

这里做了几件事：

- **显示条件**（最外层 `v-if`）：
  - 公共牌数量 ≥ 3 且公共牌翻牌动画已完成（`communityCardsFlipComplete` 为 true）。
  - 且满足以下其一：
    - 这是「我自己」的玩家卡片（`isMe(p.nickname)`），无论是否到摊牌都能看到；
    - 或者已经到了 `showdown/settled` 阶段且该玩家未弃牌（只在摊牌亮出未弃牌玩家的牌）。
- **展示内容**：
  - `getHandRank(p.holeCards, communityCards)`：在前端本地再次根据“2+5”算出一个**中文牌型名字**（如“同花顺”、“两对”），只用来显示文字。
  - `p.bestHandCards`：直接用后端算好的那 5 张牌原始字符串，配合 `getCardClass` / `getCardDisplay` 渲染为小牌方块。

> 设计上的安全性：
>
> - **对自己**：从翻牌圈开始，只要有 ≥3 张公共牌，就能看到“自己当前最强 5 张牌”和牌型名称，方便判断走势。
> - **对别人**：在真正摊牌前，别人的手牌依然盖着，`bestHandCards` 也不会被显示出来，避免剧透。

#### 2. 前端的牌型评估（文字版）`getHandRank`

```1088:1135:src/main/java/com/example/mgdemoplus/front/dp_game/src/components/game.vue
    getHandRank(holeCards, communityCards) {
      if (!holeCards || holeCards.length < 2 || !communityCards || communityCards.length < 3) {
        return '牌不足'
      }
      // 合并所有牌
      var allCards = holeCards.concat(communityCards)
      // 解析 + 生成所有 5 张组合 + evaluateHand，返回最高分对应的 name
      ...
      return bestName
    },
```

```1158:1260:src/main/java/com/example/mgdemoplus/front/dp_game/src/components/game.vue
    evaluateHand(cards) {
      ...
      // 皇家同花顺
      if (isFlush && isStraight && ranks[0] === 14 && ranks[1] === 13) {
        return {score: 10, name: '皇家同花顺'}
      }
      // 同花顺
      ...
      // 高牌
      return {score: 1, name: '高牌'}
    }
```

- 这部分逻辑和后端的 `evaluateBestHand/evaluateFiveCardHand` 思路基本一致，但**只返回一个简单的 `{score, name}`**，用于展示。
- 文案是中文的：皇家同花顺 / 同花顺 / 四条 / 葫芦 / 同花 / 顺子 / 三条 / 两对 / 一对 / 高牌。

> 注意：**真正的底池结算完全依赖后端** 的 `HandStrength` 比较结果，前端这套纯展示，不影响输赢。

### 四、整体调用关系小结（给未来的你看）

1. **抽象目标**：让玩家在翻牌后随时看到“自己当前能拼出的最大 5 张牌是什么、牌型叫什么”，但又不暴露别人还没亮的底牌。
2. **数据流**：
   - 后端 `getAllRooms(roomId)` 在公共牌 ≥3 张时为每位玩家计算 `bestHandCards`（7 选 5 + 牌力评估）。
   - Controller 把带有 `players[*].bestHandCards` 的房间对象序列化成 JSON，返回给前端。
   - 前端 `game.vue` 每秒轮询 `/dpRoom/getNowRoom`，刷新 `this.players`。
3. **展示层控制**：
   - 只有自己，或者已到摊牌/结算阶段的未弃牌玩家，才展示对应的最大手牌和牌型名字。
   - 展示时用后端给的 `bestHandCards` + 前端自算的 `getHandRank` 组合起来。

如果后续要改这块逻辑（例如：想隐藏“实时最大牌型”，只在摊牌才显示），可以从这三个点入手：

- 在 `getAllRooms` 里改 `bestHandCards` 的填充时机；
- 或者在前端模板里放宽/收紧 `v-if` 条件；
- 或者干脆只保留后端结算逻辑，把前端 `getHandRank` 用作“牌型说明弹窗”的示例，而不显示在每个玩家卡片上。

---

## 代码瘦身记录（当前版本）

- **删除未使用后端方法**  
  - 移除 `DpRoomServiceImpl` 中旧的 `checkAndStartNextHandAfterSettleOrigin` 和未被调用的统计方法 `countActiveNotFolded`，实际流程统一使用新的 `checkAndStartNextHandAfterSettle` 与 `countPlayersStillInHand`。
- **删除未使用前端方法**  
  - 删除 `game.vue` 里未挂在任何按钮/逻辑上的 `startGame` 和 `doNewHand`，真正的开局逻辑集中在 `room.vue` 调用 `/dpRoom/startGame`，新一局发牌逻辑在服务端自动根据结算结果触发。
- **小范围封装与复用**  
  - `DpRoomServiceImpl` 中 `getBestHandCards` 现在复用统一的牌点数映射常量 `CARD_RANK_MAP`，不再在方法内部重复写一份 `"2"~"A"` 对应数值，行为不变、只是减少重复代码。

> 以上瘦身只清理了“完全不用的代码”或简单复用常量，没有改动任何接口地址、请求参数、游戏规则或结算逻辑，保证老版本前端和已有房间流程都能照常运行。