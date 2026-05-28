# 好友 presence 跟随失败兜底与 SSE 推送方案

> 状态：P0 已实现（本文档与代码同步）。  
> 范围：跟随进房失败时校正**被跟随好友**的房内态，并通过现有 `/dp/social/stream` 推送给**跟随者**，无需 F5。

---

## 1. 现状数据流

### 1.1 两层 presence

| 层 | 存储 | TTL | 写入方 | 读取方 |
|---|---|---|---|---|
| **房内态** `DpFriendPresenceService` | 单机 `ConcurrentHashMap<Integer, DpFriendPresenceState>`（`IDLE` / `IN_GAME`） | 无 TTL，进程内常驻 | `DpRoomServiceImpl` 进房/退房/快照清理；`DpFriendSocialService.followFriendToTheirRoom` 兜底 | `DpFriendSocialService.buildFriendListItems` → `GET /dp/friends` |
| **站点在线** `DpSitePresenceService` | 单机 `ConcurrentHashMap<Integer, lastSeenMillis>` | 默认 90s（`mgdemoplus.dp-site-presence-ttl-ms`） | JWT 过滤器 / `POST /dp/presence/site-heartbeat` | 与房内态合成列表展示态 |

**注意：好友房内态不使用 Redis**（与早期文档中「Redis key」表述不同；Redis 用于其他能力，本链路为 JVM 内存）。

### 1.2 列表展示态合成

```text
buildFriendListItems
  → friendPresenceService.getEffectiveMany(friendIds)   // 缺省 IDLE
  → resolveFriendListPresence(inRoom, sitePresenceService.isOnlineSite(fid))
       IN_GAME 优先
       否则在线 → IDLE（大厅空闲）
       否则 → OFFLINE
  → 字段 presence: "IDLE" | "IN_GAME" | "OFFLINE"
```

前端 `dpFriendPresence.js`：`IN_GAME` → 显示「游戏中」+ 跟随按钮。

### 1.3 社交 SSE（现有）

- 连接：`GET /dp/social/stream?token=`（仅大厅 `/home` 路由在前端维持连接）
- Hub：`SocialSseHub` 按 `userId` 注册最多 3 条 `SseEmitter`
- **原仅有** `event: notify`，载荷 `SocialNotifyPayload`：邮箱未读 + 好友私信未读（**不含 presence**）
- 推送入口：`SocialNotifyPublisher.notifyUser` ← `DpFriendSocialService.pushSocialNotify`

### 1.4 跟随进房主路径

`POST /dp/friends/follow-room` → `followFriendToTheirRoom`：

1. 校验互为好友
2. `dpRoomService.findRoomIdContainingNickname(friendNick)` 查内存房
3. 无房 → **已加** `markIdle(friendUserId, "followFriendToTheirRoom")`（修复前无 SSE）
4. 有房 → `joinRoomInviteAsSpectator`

房内 `IN_GAME` 由 `DpRoomServiceImpl.presenceMarkInGameHuman` 等在进房时写入；退房走 `presenceTryMarkIdleFullyLeft` / `presenceMarkIdleAllHumansOnRoomSnapshot`（幽灵房对齐等见 `lobby` reconcile，P1 可继续审计遗漏路径）。

---

## 2. 根因（修订，2025-05）

> **结论修正**：用户用**无 SSE 的旧逻辑**验证「刷新页面后好友仍显示游戏中」→ **主因在后端 JVM 内存 `ConcurrentHashMap` 未 `markIdle`，不是浏览器本地缓存。**  
> SSE/前端仅为「跟随失败瞬间列表不自动变」的**次要**问题（P1 体验层）。

### 2.1 根因 A（主因）：离开路径未可靠写入 `markIdle`

好友列表 `presence` 直接读 `DpFriendPresenceService.getEffectiveMany()`（无 Redis、无 TTL）。进房时 `markInGame` 写入 `IN_GAME` 后，若离开链路未调用 `markIdle`，**无论刷新多少次 `GET /dp/friends` 都会仍是 `IN_GAME`**（见 §2.3）。

#### 2.1.1 离开路径审计表

| 场景 | 文件:方法 | 是否 markIdle | 条件 / 备注 |
|---|---|---|---|
| 主动 HTTP 退房 | `DpRoomServiceImpl:exitRoom` → `presenceTryMarkIdleFullyLeft`（`exit_room`） | ✅ | 非 `droppedEmpty` 时；空房走 `presenceMarkIdleAllHumansOnRoomSnapshot` |
| 空房 / 摘房 | `DpRoomServiceImpl:finalizeHallAfterRoomRemovedWithPresenceSnapshot` | ✅ | `room_map_remove`；遍历 corpse 内真人 players + spectators |
| 定时器摘空房 | `DpRoomHeartbeatScheduler:removeDesertedRoomInGlobalTickIfNoLiveHumans` → `removeRoom` | ✅ | 同上 |
| 房主易位致空房 | `DpRoomServiceImpl:giveOwner` → `finalizeHallAfterRoomRemovedWithPresenceSnapshot` | ✅ | `liveHumanTableCount==0` 且观众为空 |
| 心跳踢桌上真人 | `DpRoomHeartbeatScheduler:tickEvictStaleSeatedPlayersOnHeartbeat` | ✅（P0 加强） | 真人、`!leftThisHand`、超 `HEART_TIMEOUT`(20s)；**P0 新增** `presenceMarkIdleHuman(userId)` 直写 |
| 心跳踢观众 | `DpRoomHeartbeatScheduler:tickEvictStaleSpectatorsOnHeartbeat` → `exitRoom` | ✅ | 观众 WS/HTTP 保活超时 |
| 本手结算摘僵尸位 | `DpRoomServiceImpl:removeLeftThisHandZombiesAfterHand` → `presenceTryMarkIdleFullyLeft` | ✅ | `hand_settle_zombie_removed` |
| 跟随失败兜底 | `DpFriendSocialService:correctStaleFriendPresenceOnFollowMiss` | ✅ | **被动纠错**，非离开路径 |
| **WebSocket 断线** | `DpGameRoomWebSocketHandler:afterConnectionClosed` | ❌ | 仅 `pushService.removeSessionFromRoom`；**故意不调 exitRoom**（对局页 WS 会重连） |
| **前端组件销毁** | `game.vue` / `room.vue` `beforeDestroy` | ❌ | 停心跳/关 WS，**不 POST `/dpRoom/exitRoom`** |
| 踢到观众席 | `DpRoomServiceImpl:kickOnePlayerWithoutLobbySync` | ⏸ 不标 | 仍在 spectators，**应保持 IN_GAME** |
| 结算准备超时挪观众 | `DpRoomServiceImpl:pruneSettledTableForReadyTimeout` | ⏸ 不标 | 仍在 spectators |
| 大厅 DB 对齐 | `DpRoomLobbyReconcileScheduler:reconcile` | ⏸ 不涉及 | 只清 `dp_room_lobby` 幽灵行，**不碰** `roomMap` / presence |
| NPC | 各 presence 入口 | ⏭ 跳过 | `DpNpcEngine.isBotNickname` / `isBotPlayer` |

#### 2.1.2 异常退出的实际链路（用户反馈场景）

```text
关 tab / 强退 / 路由离开（未点「退出对局」）
  → 前端 beforeDestroy：停 heartbeatTimer，不 exitRoom
  → 后端 WS afterConnectionClosed：只摘推送订阅
  → 依赖 DpRoomHeartbeatScheduler（1s tick）
       → 20s 无 POST /dpRoom/heartbeat 后 tickEvictStale*
       → exitRoom 或 it.remove + markIdle
```

**缺口**：

1. **无同步 markIdle**：异常退出与内存态校正之间至少有 **~20s**（`DpRoomBO.HEART_TIMEOUT`）窗口；窗口内刷新列表仍 `IN_GAME`。
2. **`leftThisHand` 僵尸位不走心跳踢人**（`tickEvictStaleSeatedPlayersOnHeartbeat` 对 `isLeftThisHand()` `continue`），依赖 `exit_room` 或 `hand_settle_zombie_removed` 才 markIdle；若 **`resolvePresenceUserIdPreferHint` 返回 null** 则 **静默不写**（P0 已加 warn 日志）。
3. **WS 断线不能绑 exitRoom**：`game.vue` 断线后会 `scheduleWsReconnect`，在 handler 里 exitRoom 会误杀重连中对局。

#### 2.1.3 P0 已做代码加固（本次）

- `presenceTryMarkIdleFullyLeft`：改为 `isNicknameActivelyPresentInAnyRoom`（观众 **或** 未离座玩家才算在房；**leftThisHand 不算**），与 `exitRoom` 语义对齐；uid 解析失败打 warn。
- `tickEvictStaleSeatedPlayersOnHeartbeat`：踢人后对已知 `dpUserId` **直调** `presenceMarkIdleHuman`。

#### 2.1.4 P0 仍建议补的最小离开路径（未做 / 前端）

| 优先级 | 改动 | 理由 |
|---|---|---|
| P0 | `game.vue` / `room.vue` `beforeDestroy`：`fetch(..., { keepalive: true })` 或 `sendBeacon` 调 `POST /dpRoom/exitRoom` | 异常退出时**主动** markIdle，不依赖 20s |
| P0 | 可选：`presenceMarkIdleAllHumansOnRoomSnapshot` 同时扫 `waitNextHand` | 极端孤儿候补（当前 join 链路极少见） |
| P1 | 全路径 markIdle 后向好友扇出 SSE | 需好友反向索引 |

---

### 2.2 根因 B（次要）：SSE / 前端未即时 patch

| 现象 | 证据 | 结论 |
|---|---|---|
| 跟随失败瞬间列表不变 | 原 `notify` 不含 presence；需 `friendPresence` 事件 | **仅影响「不刷新 UI」**，不能解释「刷新仍 IN_GAME」 |
| 未连 SSE 时 | 下次 `fetchFriends` 应与服务端一致 | 若刷新仍错 → 回到根因 A |

**勿再写「刷新无用仍怪前端缓存」**——`buildFriendListItems` 每次请求都读服务端内存 Map。

---

### 2.3 为何「刷新 listFriends 仍 IN_GAME」（代码证明）

```387:401:src/main/java/com/example/mgdemoplus/social/impl/DpFriendSocialService.java
        Map<Integer, DpFriendPresenceState> presenceByFriend =
                friendPresenceService.getEffectiveMany(presenceEligibleFriendIds);
        ...
            DpFriendPresenceState inRoom = presenceByFriend.get(fid);
            ...
            DpFriendPresenceState display =
                    resolveFriendListPresence(inRoom, sitePresenceService.isOnlineSite(fid));
            m.put("presence", display.name());
```

```26:28:src/main/java/com/example/mgdemoplus/presence/DpFriendPresenceService.java
    public DpFriendPresenceState getEffective(int dpUserId) {
        return presenceByUserId.getOrDefault(dpUserId, DpFriendPresenceState.IDLE);
    }
```

```410:414:src/main/java/com/example/mgdemoplus/social/impl/DpFriendSocialService.java
        if (inRoomPresence == DpFriendPresenceState.IN_GAME) {
            return DpFriendPresenceState.IN_GAME;
        }
```

**推论**：Map 里仍是 `IN_GAME` → 刷新必仍为「游戏中」（站点在线只影响 IDLE vs OFFLINE，压不过 IN_GAME）。

---

### 2.4 `findRoomIdContainingNickname` 与 `IN_GAME` 何时不一致

| 状态 | findRoom（内存房） | presence Map | 列表展示 | 跟随 |
|---|---|---|---|---|
| 正常在房 | 有 roomId | IN_GAME | 游戏中 | 成功 |
| **脏态 A**（用户现象） | **null** | **IN_GAME** | 游戏中 | 失败 → 兜底 markIdle |
| 脏态 B | 有 roomId（含 leftThisHand 僵尸） | IDLE | 空闲/离线 | 可能仍能跟随 |
| 仅观众/候补在房 | 有 roomId | IN_GAME | 游戏中 | 成功 |

**findRoom 口径**（`DpRoomServiceImpl:findRoomContainingNickname`）：扫 `spectators` + **全部** `players`（**含 leftThisHand 僵尸**）。

**markIdle 口径（修订后）**：仅当不在「观众 **或** 未离座玩家」集合时才 IDLE。

**只清房不清 presence**：理论上摘房路径都走 `presenceMarkIdleAllHumansOnRoomSnapshot`；若 uid 解析失败会漏。  
**只清 presence 不清房**：`exitRoom` 对局中先 `markIdle`，僵尸位暂留至本手结束（findRoom 仍可找到，属脏态 B）。

---

### 2.5 与 P0 兜底的关系

`followFriendToTheirRoom` → `correctStaleFriendPresenceOnFollowMiss` 是 **跟随者** 点击跟随时的**被动纠错**，不能替代离开路径主动 `markIdle`。  
用户要的是：**在 exit / 心跳踢人 / 摘房 / 前端 beforeDestroy 上写对内存态**；SSE 只解决「写对后 UI 何时更新」。

---

## 3. P0 / P1 清单

### P0（本次）

- [x] 方案文档（本文，§2 根因修订）
- [x] 抽取 `correctStaleFriendPresenceOnFollowMiss` + SSE `friendPresence`
- [x] `presenceTryMarkIdleFullyLeft` 改用 `isNicknameActivelyPresentInAnyRoom` + uid 失败 warn
- [x] 心跳踢人：`presenceMarkIdleHuman(userId)` 直写 IDLE
- [ ] `game.vue` / `room.vue` `beforeDestroy` 主动 `exitRoom`（keepalive，仍建议补）

### P1（后续）

- `game.vue` / `room.vue` `beforeDestroy` 主动 `POST /dpRoom/exitRoom`（keepalive）
- `followFriendIntoRoomAsSpectator` / `resolveFriendSpectateRoomTarget` 失败时复用同一校正 + SSE
- 游戏页 `GameInviteFriendSheet` 开 SSE 或失败时 `fetchFriends`
- 退房 / reconcile / WebSocket 断线全路径审计 + 向所有「把该用户当好友的在线用户」扇出 presence（需好友关系索引）
- `markIdle` 返回是否变更，无变更则跳过 SSE（微优化）
- 单测：`followFriendToTheirRoom` 脏态 + mock `notifyFriendPresence`；`presenceTryMarkIdleFullyLeft` leftThisHand / 心跳踢人

---

## 4. 兜底策略说明

### 何时 `markIdle`

| 场景 | 条件 | trigger |
|---|---|---|
| 跟随：不在房 | `findRoomIdContainingNickname` 为空 | `followFriendToTheirRoom:not_in_room` |
| 跟随：房已消失 | `roomId` 非空但 `!dpRoomExistsInMemory` | `followFriendToTheirRoom:room_gone` |

不处理：跟随者自身 presence；`joinRoomInviteAsSpectator` 失败（好友可能仍在房）。

### 幂等与边界

- `markIdle`：`put(IDLE)`，已为 IDLE 时无副作用（仅 debug 日志不打印 transition）
- `friendUserId <= 0`：不校正
- `watcherUserId <= 0`：不推 SSE
- 竞态：跟随请求期间好友又进另一房 → 随后退房路径会再次 `markIdle`/`markInGame`；跟随失败 SSE 可能短暂显示 IDLE，下次 `listFriends`/进房事件可纠正（可接受）

---

## 5. SSE 设计

### 事件

- **名称**：`friendPresence`（与 `notify` 并列，同一 `SseEmitter`）
- **推送对象**：**跟随者** `currentUserId`（观看自己好友列表的一方）
- **不推**：被校正好友本人（除非其好友列表也展示他人，非本需求）

### Payload（JSON data）

```json
{
  "friendUserId": 42,
  "presence": "IDLE",
  "reason": "followFriendToTheirRoom:not_in_room"
}
```

- `presence`：与 `GET /dp/friends` 的 `presence` 字段同口径（已合成站点在线）
- `reason`：排障用，前端可忽略

### 与 `SocialNotifyPayload` 的关系

- **不扩展** `notify` 载荷，避免破坏现有未读解析；独立事件最小侵入
- 未读变化仍走 `notify`

---

## 6. 验收标准

1. 好友 A 列表显示 B 为「游戏中」（`IN_GAME`），但 B 已不在任何内存房（可手工 `markInGame` 或残留态模拟）
2. A 在大厅 `/home` 点击跟随 → 接口失败提示「好友不在房间内」
3. **无需 F5**：A 的好友列表中 B 变为「空闲」或「离线」（取决于 B 站点心跳），跟随按钮消失
4. 后端日志：`markIdle` + `[social-sse] friendPresence broadcast userId=A`

---

## 7. 手工验证步骤

1. 启动后端 `mvn spring-boot:run`，前端 `npm run dev`，两账号互为好友，均在大厅且 SSE 已连接（Network 可见 `text/event-stream`）
2. **模拟脏态**（开发环境）：对 B 执行进房再异常退房，或临时在调试中令 `friendPresence` 为 `IN_GAME` 且 `findRoomIdContainingNickname(B)` 为 null
3. A 打开好友抽屉 → 跟随 B → 应弹错误且列表行样式从 ingame 变 idle/offline
4. DevTools → EventStream：应看到 `event: friendPresence` 一条

---

## 8. 构建与测试命令

```bash
mvn -q -DskipTests compile
mvn test -Dtest=DpFriendSocialServiceListFriendsPresenceTest
# 可选：新增 follow 兜底单测后
# mvn test -Dtest=DpFriendSocialServiceFollowPresenceFallbackTest
```

---

## 9. 风险与回滚

| 风险 | 缓解 |
|---|---|
| SSE 未连接（不在 `/home`） | 仍依赖下次 `fetchFriends`；P1 可游戏页补连接 |
| 多标签页 | 每连接各收一条，前端 patch 幂等 |
| 单机内存 presence 多实例不一致 | 与现架构一致；非本次范围 |

**回滚**：移除 `friendPresence` 事件发送与前端监听；保留 `markIdle` 不影响正确性。

---

## 10. 变更文件映射（P0）

| 文件 | 作用 |
|---|---|
| `social/notify/FriendPresenceNotifyPayload.java` | SSE 载荷 |
| `social/notify/SocialSseHub.java` | `broadcastFriendPresence` |
| `social/notify/SocialNotifyPublisher.java` | `notifyFriendPresence` |
| `room/impl/DpRoomServiceImpl.java` | `isNicknameActivelyPresentInAnyRoom`、`presenceMarkIdleHuman`、tryMarkIdle 修订 |
| `room/support/DpRoomHeartbeatScheduler.java` | 心跳踢人直写 markIdle |
| `room/support/DpRoomServiceCallbacks.java` | `presenceMarkIdleHuman` 回调 |
| `social/impl/DpFriendSocialService.java` | 跟随失败兜底 + SSE 推送 |
| `front/.../dpSocialStream.js` | 解析 |
| `front/.../dpMailbox.js` | `PATCH_FRIEND_PRESENCE` |
| `front/.../home.vue` | SSE 监听 |
