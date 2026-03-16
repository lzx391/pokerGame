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