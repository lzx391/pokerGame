# DP 对局落库说明（牌谱 / 参与者 / Shark 记忆）

本文说明 **哪些数据会写入 MySQL、在什么时机写、与 `BOT_Shark` 的关系**，以及失败时的行为。实现类以 `com.example.mgdemoplus.service.serviceImpl.dp` 包为主。

---

## 1. 总览：三类持久化

| 数据 | 表名 | 是否依赖桌上有 Shark | 典型用途 |
|------|------|----------------------|----------|
| 完整牌谱快照 | `dp_observed_hand_history` | **否** | 牌谱回放、详情接口 `payload_json` |
| 牌谱 ↔ 真人参与者 | `dp_observed_hand_participant` | **否** | 按用户/昵称查「我参与过的对局」 |
| Shark 对手画像 | `dp_shark_opponent_profile` | **是**（仅此时写入/加载） | 跨房间恢复 `PlayerStats` + `LearningLab` 旋钮 |

**结论**：牌谱与参与者 **不要求** Shark 在场；**只有** Shark 相关长期记忆表 **要求** 桌上有 `BOT_Shark` 才会从库加载、并在结算后写回。

---

## 2. 牌谱：从内存到 `dp_observed_hand_history`

### 2.1 内存侧：谁在记？

类名是 `DpNpcSharkObservedHandHistory`，但 **开关与 Shark 无关**：

- `isEnabledForRoom(room)`：只要 `room.getPlayers()` 里存在非 `null` 玩家，本手即开始构建「观测牌谱」。
- 与 `DpNpcSharkHandActionLog` 不同：后者 **仅当** 桌上昵称含 `BOT_Shark` 才记逐街动作日志。

**一手内会记录的内容（摘要）**：

- 盲注后座位、盲注位、盲注后筹码（`markHandReadyAfterBlinds`）
- 各街公共牌快照（`recordBoardState`）
- 全员行动（含 check/call/bet/raise/fold/all-in 等，`recordBetLikeAction` / `recordFold` / `recordBlind`）
- 结算分配前主池/边池结构（`capturePotsBeforeClear`）
- 结束时：每位玩家洞牌（服务端全知）、相对盲注后的净盈亏 `netChipsChange`（`finalizeHand`）

完成后得到 `ObservedHandRecord`，并进入房间维度的内存环形归档（`ARCHIVE`，有上限，防内存膨胀）。

### 2.2 何时调用 `finalizeHand` + 落库？

在 `DpRoomServiceImpl#autoSettle` 中：

1. **无下注、无池的早退路径**（例如无人投入筹码的特殊结算）：  
   `finalizeHand` → 若有记录则 `observedHandPersistService.save(archivedEarly, r)`。
2. **正常分配筹码后的路径**：  
   先更新内存统计、情绪等 → 再 `finalizeHand` → `observedHandPersistService.save(archived, r)` → 随后才是 Shark 学习相关（见第 4 节）。

**注意**：源码里个别注释仍写「仅 Shark 在座时归档」，与 **实际代码不一致**；当前实现是 **只要本手成功构建 `ObservedHandRecord` 就会尝试入库**，不判断 Shark。

### 2.3 `DpNpcObservedHandHistoryPersistService#save` 做什么？

1. 把 `ObservedHandRecord` 转成内部 DTO `Payload`（字段名稳定），用 `ObjectMapper` 序列化为 **JSON 字符串**。
2. 写入 `dp_observed_hand_history`：`room_id`、`hand_seed`、时间、盲注、庄家昵称、结算前主池、`payload_version`、`payload_json`。
3. MyBatis `insert` 使用 `useGeneratedKeys` 取 **`id`**，供下一步参与者表使用。

**失败策略**：任意异常只打 **warn 日志**，**不抛出不中断游戏**。

### 2.4 表结构与约束

- 建表脚本：`src/main/resources/db/dp_observed_hand_history.sql`
- 唯一键：`UNIQUE (room_id, hand_seed)` —— 同一房间、同一手种子 **只能有一条**；重复插入会失败并被 catch 打日志。
- `payload_json`：MySQL `JSON` 类型；内容与内存 `ObservedHandRecord` 对齐（座位、街、行动、池、洞牌、盈亏）。

---

## 3. 参与者：`dp_observed_hand_participant`

### 3.1 何时插入？

仅在 `save` 成功插入主表且 `handHistoryId` 非空，且 **`room` 非 null** 时，对 **当前 `room.getPlayers()` 里每一名非机器人** 尝试插入一行。

### 3.2 每行写什么？

| 字段 | 来源 |
|------|------|
| `hand_history_id` | 上一步主表自增 `id` |
| `user_id` | 优先 `DpPlayer.dpUserId`；否则按 **昵称** 查 `dp_user` |
| `nickname_snapshot` | 当前昵称 |
| `seat_index` / `is_dealer` / `blind_pos` | 座位与庄盲 |
| `net_chips` | `ObservedHandRecord.netChipsChange` 中该昵称的值（缺省按 0） |

**机器人**：`DpNpcEngine.isBotPlayer(p)` 为 true 时 **跳过**，不占参与者行（与根目录 readme 中「机器人不占行」一致）。

### 3.3 约束与失败

- 唯一键：`(hand_history_id, nickname_snapshot)`（见 `dp_observed_hand_participant.sql`）。
- 每名参与者 **单独 try/catch**：单行失败只打 warn，不影响其他人或其它表。

脚本路径：`src/main/resources/db/dp_observed_hand_participant.sql`（**未建外键**，由应用保证 `hand_history_id` 合法）。

---

## 4. Shark 对手记忆：`dp_shark_opponent_profile`

### 4.1 表存什么？

- **`stats_json`**：`DpPlayerStats` 的持久化快照（总手数、入池/加注/摊牌计数、最近窗口手牌列表、`foldAdjustmentAgainstHero` 等）。
- **`learned_json`**：`DpNpcSharkLearningLab` 导出的旋钮与分桶样本（`exportLearnedSnapshot`）。
- 主键：**玩家昵称** `player_nickname` —— **跨房间**识别同一人。

建表：`src/main/resources/db/dp_shark_opponent_profile.sql`，写入为 **UPSERT**（`ON DUPLICATE KEY UPDATE`）。

### 4.2 何时从库读到内存（hydrate）？

**前提**：`DpNpcSharkObservedHandHistory.isSharkAtTable(room)` 为 true（桌上存在昵称 **`BOT_Shark`**）。

1. **`joinRoom` 成功把玩家加进 `players` 后**：  
   `sharkOpponentMemoryService.hydratePlayerIfNeeded(r, nickname)`  
   —— 若该昵称还不在 `playerStatsMap` 里，则尝试 `SELECT` 一行并恢复统计 + `importLearnedSnapshot`。

2. **每手 `newHand`、盲注与座位就绪后**：  
   `sharkOpponentMemoryService.hydrateAllOpponentsForNewHand(r)`  
   —— 对桌上每位玩家调用上面的 `hydratePlayerIfNeeded`（Shark 本人跳过）。

若桌上 **没有** Shark，上述两个入口 **直接 return**，**不访问数据库**。

### 4.3 何时写回数据库？

**仅在一手结算之后**：`DpRoomServiceImpl#autoSettle` 末尾（在 `DpNpcSharkLearningLab.onHandSettled` 之后）调用：

`sharkOpponentMemoryService.persistOpponentsAfterHand(r)`

内部同样先判断 `isSharkAtTable`；否则 **不写库**。  
对每个非 Shark 玩家：序列化 stats + learned → `upsert` 一行。

### 4.4 与内存学习 `DpNpcSharkLearningLab.onHandSettled` 的关系

- `onHandSettled(room)` 在结算流程里 **每手都会调用**（只要走到该分支），用于 **更新内存** 中的旋钮；**读 Shark 决策时**才用到这些值。
- **数据库**中的 `learned_json` 只在 **`persistOpponentsAfterHand`** 里更新，且 **仅当桌上有 Shark** 的那类对局结束后才会把当前内存状态刷进 `dp_shark_opponent_profile`。

因此：**无 Shark 的对局** 一般不会把 LearningLab 状态写入 `dp_shark_opponent_profile`；若你希望「内存学习也仅在 Shark 在场时更新」，需要在业务上再收紧（当前代码未做该限制）。

---

## 5. 端到端顺序（正常结算路径）

下面只列 **与落库强相关** 的顺序（中间省略发牌、下注等）：

1. 新一手：`DpNpcSharkObservedHandHistory.beginHand`；若有 Shark：`DpNpcSharkHandActionLog.beginHand`。
2. 盲注后：`markHandReadyAfterBlinds`；若有 Shark：`hydrateAllOpponentsForNewHand`。
3. 对局中：观测牌谱持续追加；若有 Shark：动作日志并行追加。
4. 进入结算：`capturePotsBeforeClear`（若有池）。
5. 更新 `PlayerStats` 等内存统计（整段循环依赖 `playerStatsMap`；逐街精细字段在有 Shark 时可利用 `DpNpcSharkHandActionLog.snapshot`）。
6. `finalizeHand` → **`observedHandPersistService.save`**（主表 + 参与者）。
7. `DpNpcSharkLearningLab.onHandSettled`（内存旋钮）。
8. **`sharkOpponentMemoryService.persistOpponentsAfterHand`**（仅 Shark 在桌时写 `dp_shark_opponent_profile`）。
9. `DpNpcSharkHandActionLog.clearHand`（清本手动作日志）。

**早退路径**（无池直接 settled）：也会 `finalizeHand` + `save`，并 `clearHand` 动作日志，但不会执行第 7～8 步中「完整统计 + LearningLab」那一段（需对照 `autoSettle` 源码行号）。

---

## 6. 对外查询 API（与落库的关系）

根目录 [README.md](../README.md) 中已有摘要：

- 列表：`GET /dpHandHistory/list?userId=…` —— 参与者表按 `user_id` 命中。
- 详情：`GET /dpHandHistory/detail?handHistoryId=…&userId=…` —— 校验该 `user_id` 参与该手后读主表 `payload_json`。

实现类在 `DpHandHistoryService` / 相关 Controller，不在此赘述字段。

---

## 7. 运维与排错要点

1. **表未建**：插入失败仅日志，游戏照常；需在目标库执行 `src/main/resources/db/` 下对应 SQL。
2. **唯一键冲突**：同一 `(room_id, hand_seed)` 重复插入牌谱主表会失败；通常不应在同手结算里调用两次 `save` 除非逻辑变更。
3. **隐私**：`payload_json` 含 **全桌洞牌**（服务端视角）；详情接口对「他人弃牌」等做了展示限制，**库内仍为全量**，备份与权限需单独管控。
4. **昵称变更**：参与者存 `nickname_snapshot`；用户改名后旧牌谱仍按旧昵称关联，与 readme 说明一致。

---

## 8. 相关类与脚本索引

| 环节 | 类或文件 |
|------|-----------|
| 内存牌谱 | `DpNpcSharkObservedHandHistory` |
| Shark 逐街日志 | `DpNpcSharkHandActionLog` |
| 牌谱/参与者入库 | `DpNpcObservedHandHistoryPersistService` |
| Shark 记忆读写 | `DpNpcSharkOpponentMemoryService` |
| 学习旋钮（内存） | `DpNpcSharkLearningLab` |
| 结算编排 | `DpRoomServiceImpl#autoSettle`、`#newHand`、`joinRoom` |
| Mapper | `DpObservedHandHistoryMapper`、`DpObservedHandParticipantMapper`、`DpSharkOpponentProfileMapper` |
| SQL | `db/dp_observed_hand_history.sql`、`db/dp_observed_hand_participant.sql`、`db/dp_shark_opponent_profile.sql` |

---

*文档与代码同步日期：2026-03-30。若调整结算顺序或 Shark 条件，请同步更新本节。*

---
# 以下是自己的笔记
## 以下类负责前端查询返回的信息
- **DpHandHistoryController**->**DpHandHistoryService**->**DpHandHistoryQueryMapper**,**DpObservedHandHistoryMapper分页联查**  
里面用到的数据结构有**DpHandHistoryPageDTO（集成DpHandHistoryListItemDTO）**，**DpHandHistoryDetailDTO**  
## 以下类是负责后端信息入库的
- 由房间服务类的**autoSettle**方法开始->DpNpcSharkObservedHandHistory.finalizeHand处理->DpNpcSharkObservedHandHistory.ObservedHandRecord类->
**DpNpcObservedHandHistoryPersistService**负责对局信息落库和更新用户对局关联表，存对局信息的时候用的是json形式，先把DpNpcSharkObservedHandHistory.ObservedHandRecord类转化成playload类，然后playload->json格式->**用DpObservedHandParticipant**和**DpObservedHandHistory**作为最终落库的实体  
## 以下是对finalizeHand方法的剖析  
首先**DpNpcSharkObservedHandHistory**这个类整个就是负责监控各个行动并写入内存的，里面有很多方法分别在开始，翻前行动前，翻前行动后的bet fold 等方法插眼，最后finalizeHand打包信息，并挂到近几手的内存对局队列中。  
## 4月4日加入了分页插件  
分页插件用法，首先在pom文件引入插件依赖，在application配置文件配置，在服务类调用mapper层之前，把页码和页数输入插件方法，会自动计算偏移并代替limit的sql，最后PageInfo<DpHandHistoryListItemDTO> pageInfo = new PageInfo<>(records);是负责获取总数，页码，页中记录数等信息

