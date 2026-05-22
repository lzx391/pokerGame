> 扫描日期：2026-05-22 · 范围：AI/infra/DB · 覆盖重写

# MGDemoPlus package migration manifest

## W1 acceptance (2026-05-18)

| Check | Result |
|-------|--------|
| **Overall** | **PASS** |
| `mvn -q compile` | PASS (exit 0) |
| `mvn -q test` | PASS (exit 0) |

### Module layout

| Module | Subpackages present | Files |
|--------|---------------------|-------|
| `music/` | `(root)`, `impl/`, `entity/`, `mapper/` | 4 |
| `history/` | `(root)`, `impl/`, `entity/`, `mapper/`, `vo/` | 14 |
| `roomchat/` | `(root)`, `buffer/`, `entity/`, `mapper/` | 7 |

`MgDemoPlusApplication` `@MapperScan` includes `history.mapper`, `music.mapper`, `roomchat.mapper`.

### Stale-import scan (deleted pre-W1 paths)

| Pattern | Leftovers |
|---------|-----------|
| `com.example.mgdemoplus.entity.DpMusicTrack` | none |
| `com.example.mgdemoplus.service.DpHandHistory*` | none |
| `com.example.mgdemoplus.room.RoomChatBuffer` | none |

All `RoomChatBuffer` / `RoomChatEntry` references use `com.example.mgdemoplus.roomchat.buffer.*`.

### Issues (non-blocking; not W1 defects)

- Observed-hand BOs (`DpObserved*BO`) and `DpObservedHandActionType` remain under flat `bo/` / `types/` — planned **W4**.
- `DpRedisListCacheService` (+ impl) remain under `service/` — music-only consumer; planned **W3**.
- Duplicate `target/classes` mapper artifacts from prior builds may exist locally; `src/main` has no duplicate history/music entity sources.

## W2 acceptance (2026-05-18)

Scope: `social/`, `lobby/`, `presence/` (+ `presence.cache`).

| Check | Result |
|-------|--------|
| **Overall** | **PASS** |
| `mvn -q compile` | PASS (exit 0) |
| `mvn -q test` | PASS (exit 0, **24** tests, **0** failures) |

### Module layout

| Module | Subpackages present | Files | Result |
|--------|---------------------|-------|--------|
| `social/` | `impl/`, `entity/`, `mapper/`, `notify/` | 14 | **PASS** |
| `lobby/` | `(root)`, `impl/`, `entity/`, `mapper/` | 6 | **PASS** |
| `presence/` | `(root)`, `cache/`, `cache/impl/` | 6 | **PASS** |

`MgDemoPlusApplication` `@MapperScan` includes `lobby.mapper`, `social.mapper` (with W1 `history.mapper`, `music.mapper`, `roomchat.mapper`).

### Stale-import scan (pre-W2 paths)

| Pattern | `src/` leftovers |
|---------|------------------|
| `service.serviceImpl.DpFriend*` | none |
| `service.DpRoomHall*` | none |
| `service/cache` (Redis cache impls) | none — moved to `presence.cache` / `presence.cache.impl` |
| `scheduler/DpRoomLobbyReconcile` | none in Java (`docs/SpringScheduling.md` still cites old path) |
| `entity` / `mapper.DpFriend*` | none |

### Issues (non-blocking; not W2 build defects)

- `DpFriendPresenceService` remains under `service/` (manifest W5); uses `presence.DpFriendPresenceState`.
- Lobby `bo`/`vo` (`DpRoomLobbySearchParamBO`, `DpRoomPublicRoomsPageVO`) remain flat `bo/` / `vo/` (manifest W6).
- Doc-only stale path: `docs/SpringScheduling.md` → `lobby/DpRoomLobbyReconcileScheduler.java`.

## W4 acceptance (2026-05-18)

Scope: `common/` (`entity`, `mapper`, `bo`) + `user/` (`DpUserService`, `UploadService`, `impl`, `user.mapper`).

| Check | Result |
|-------|--------|
| **Overall** | **PASS** |
| `mvn clean compile` | PASS (exit 0) |
| `mvn test` | PASS (exit 0, **24** tests, **0** failures) |

### Module layout

| Module | Subpackages present | Notes |
|--------|---------------------|-------|
| `common/` | `entity/`, `mapper/`, `bo/` | `DpUser`, `DpPlayer`, `DpPlayerStats`, `DpPot`, `DpRoom`, `DpRoomBO`, `DpUserMapper` |
| `user/` | `(root)`, `impl/`, `mapper/` | `DpUserService`, `UploadService` |

`MgDemoPlusApplication` `@MapperScan` includes `common.mapper`, `user.mapper` (with W1–W2 `history.mapper`, `music.mapper`, `roomchat.mapper`, `lobby.mapper`, `social.mapper`).

### Build hygiene (A10/A2)

| Item | Result |
|------|--------|
| NPC cross-package visibility (`DpNpcEngine`, `RuleNpcConfig`, `MultiwayVillainInfo`, `DpHandHistoryObservedServiceImpl.isEnabledForRoom`) | **OK** — public where required by `npc.llm` / `npc.strategy` / `DpRoomServiceImpl` |
| UTF-8 / javadoc in `common/bo`, `common/entity`, controllers | **OK** — valid UTF-8 |
| Duplicate flat `entity/` vs `common/entity/` | **OK** — removed orphan `com.example.mgdemoplus.entity.*` copies |
| Duplicate flat `mapper/` vs module `mapper/` | **OK** — removed stale flat mappers not in `@MapperScan` |
| `DpNpcUnifiedPreflopStrategy` | **OK** — `npc.strategy` (not `service.serviceImpl`) |

### Stale-import scan (pre-W4 paths)

| Pattern | `src/` leftovers |
|---------|------------------|
| `com.example.mgdemoplus.bo.DpRoomBO` | none — use `common.bo.DpRoomBO` |
| `com.example.mgdemoplus.entity.DpUser` / `DpPlayer` / `DpRoom` | none — use `common.entity.*` |
| `service.DpUserService` / `service.UploadService` | none — use `user.*` |

---

> **Read-only scan** of `src/main/java/com/example/mgdemoplus` (123 `.java` files) as of 2026-05-18.  
> **Do not move files from this doc alone** — it is the plan-of-record for modular packaging.

## Target layout

| Area | Package pattern | Notes |
|------|-----------------|-------|
| Global | `controller/`, `config/`, `security/`, `websocket/`, `utils/`, `llm/` | Locations unchanged; fix imports after module moves |
| `common/` | `common.entity`, `common.mapper`, `common.bo`, `common.vo` | Types referenced from **≥2** feature modules (evidence in [Common inventory](#common-inventory)) |
| Feature modules | `room`, `lobby`, `quickmatch`, `npc`, `social`, `history`, `music`, `user`, `roomchat`, `presence` | Service **interface** at module root; `impl/`, `entity/`, `mapper/`, `bo/`, `vo/` as needed |

**Partial migration already in repo:** `music/*`, `roomchat/*`, `quickmatch/*` subpackages exist; most types still live under flat `entity/`, `mapper/`, `service/`.

## Migration waves

| Wave | Scope | Goal |
|------|--------|------|
| **W1** | `MgDemoPlusApplication`, `config/`, `security/`, `utils/`, `llm/`, all `controller/*` | Boot + cross-cutting; expand `@MapperScan` / component scan stubs |
| **W2** | `common/` kernel (`DpUser`, `DpPlayer*`, `DpPot`, `DpRoom`, `DpRoomBO`, `DpUserMapper`) | Shared types before feature moves |
| **W3** | `user`, `music` (+ upload, login Redis cache) | Low coupling REST |
| **W4** | `history` | Observed hand pipeline + query API |
| **W5** | `social`, `presence` | Friends, SSE, site/friend presence |
| **W6** | `lobby`, `quickmatch`, `roomchat`, scheduler | Hall, QM index/pairing, chat persistence (finish `roomchat/`) |
| **W7** | `room` (+ `DpRoomServiceImpl` split), `npc`, `websocket` import wiring | Hot path last |

---

## Class manifest

Target subpackage = final segment under `com.example.mgdemoplus.<module>.` (e.g. `impl`, `entity`, `mapper`, `bo`, `vo`, `buffer`, `pairing`).

| Class | Current package | Target module | Target subpackage | common? | Notes | Wave |
|-------|-----------------|---------------|-------------------|---------|-------|------|
| MgDemoPlusApplication | com.example.mgdemoplus | global | (root) | no | Add module `@MapperScan` entries | W1 |
| CorsConfig | com.example.mgdemoplus.config | global | config | no | | W1 |
| LocalDotenvLoader | com.example.mgdemoplus.config | global | config | no | | W1 |
| MybatisPlusConfig | com.example.mgdemoplus.config | global | config | no | | W1 |
| SecurityConfig | com.example.mgdemoplus.config | global | config | no | | W1 |
| WebConfig | com.example.mgdemoplus.config | global | config | no | | W1 |
| WebSocketGameRoomConfig | com.example.mgdemoplus.config | global | config | no | | W1 |
| JwtAuthenticationEntryPoint | com.example.mgdemoplus.security | global | security | no | | W1 |
| JwtAuthenticationFilter | com.example.mgdemoplus.security | global | security | no | Uses `DpRedisLoginCacheService` | W1 |
| JwtSecurityConstants | com.example.mgdemoplus.security | global | security | no | | W1 |
| JwtTokenService | com.example.mgdemoplus.security | global | security | no | | W1 |
| CryptoUtil | com.example.mgdemoplus.utils | global | utils | no | | W1 |
| ResultCode | com.example.mgdemoplus.utils | global | utils | no | | W1 |
| ResultUtil | com.example.mgdemoplus.utils | global | utils | no | Used by controllers + services | W1 |
| DpUtilHandEvaluator | com.example.mgdemoplus.utils | global | utils | no | room + npc | W1 |
| DpUtilNpcDisplayNickname | com.example.mgdemoplus.utils | global | utils | no | npc + `DpPlayer` | W1 |
| DpUtilSmartContext | com.example.mgdemoplus.utils | global | utils | no | npc + `DpPlayerStats` | W1 |
| OpenAiCompatibleChatClient | com.example.mgdemoplus.llm | global | llm | no | | W1 |
| DpFriendMailboxController | com.example.mgdemoplus.controller | global | controller | no | `/dp/*` | W1 |
| DpHandHistoryController | com.example.mgdemoplus.controller | global | controller | no | | W1 |
| DpMusicController | com.example.mgdemoplus.controller | global | controller | no | | W1 |
| DpRoomController | com.example.mgdemoplus.controller | global | controller | no | | W1 |
| DpSocialController | com.example.mgdemoplus.controller | global | controller | no | | W1 |
| DpUserController | com.example.mgdemoplus.controller | global | controller | no | | W1 |
| UploadController | com.example.mgdemoplus.controller | global | controller | no | | W1 |
| DpUser | com.example.mgdemoplus.entity | common | entity | **yes** | user, social, room, history controllers/services | W2 |
| DpUserMapper | com.example.mgdemoplus.mapper | common | mapper | **yes** | user + social mappers | W2 |
| DpPlayer | com.example.mgdemoplus.entity | common | entity | **yes** | `DpRoomBO`, room, npc, history, quickmatch, utils | W2 |
| DpPlayerStats | com.example.mgdemoplus.entity | common | entity | **yes** | `DpRoomBO`, npc strategies, `DpUtilSmartContext` | W2 |
| DpPot | com.example.mgdemoplus.entity | common | entity | **yes** | `DpRoomBO`, history observed/persist | W2 |
| DpRoom | com.example.mgdemoplus.entity | common | entity | **yes** | room list, lobby mapper, `DpRoomPublicRoomsPageVO` | W2 |
| DpRoomBO | com.example.mgdemoplus.bo | common | bo | **yes** | 22 importers: room, quickmatch, lobby, history, npc, websocket | W2 |
| DpUserService | com.example.mgdemoplus.service | user | (root) | no | | W3 |
| DpUserServiceImpl | com.example.mgdemoplus.service.serviceImpl | user | impl | no | | W3 |
| DpRedisLoginCacheService | com.example.mgdemoplus.service | user | (root) | no | JWT jti; security + `DpUserController` | W3 |
| DpRedisLoginCacheServiceImpl | com.example.mgdemoplus.service.cache | user | impl | no | | W3 |
| UploadService | com.example.mgdemoplus.service | user | (root) | no | | W3 |
| UploadServiceImpl | com.example.mgdemoplus.service.serviceImpl | user | impl | no | | W3 |
| UploadMapper | com.example.mgdemoplus.mapper | user | mapper | no | | W3 |
| DpMusicService | com.example.mgdemoplus.music | music | (root) | no | **Already** under `music/` | W3 |
| DpMusicServiceImpl | com.example.mgdemoplus.music.impl | music | impl | no | Rename `music.impl` → `impl` optional | W3 |
| DpMusicTrack | com.example.mgdemoplus.music.entity | music | entity | no | **Already** migrated | W3 |
| DpMusicTrackMapper | com.example.mgdemoplus.music.mapper | music | mapper | no | **Already** migrated | W3 |
| DpRedisListCacheService | com.example.mgdemoplus.service | music | (root) | no | Only `DpMusicServiceImpl` consumer today | W3 |
| DpRedisListCacheServiceImpl | com.example.mgdemoplus.service.cache | music | impl | no | | W3 |
| DpHandHistoryService | com.example.mgdemoplus.service | history | (root) | no | | W4 |
| DpHandHistoryServiceImpl | com.example.mgdemoplus.service.serviceImpl | history | impl | no | | W4 |
| DpHandHistoryObservedService | com.example.mgdemoplus.service | history | (root) | no | | W4 |
| DpHandHistoryObservedImpl | com.example.mgdemoplus.service.serviceImpl | history | impl | no | Called from room tick | W4 |
| DpHandHistoryPersistService | com.example.mgdemoplus.service | history | (root) | no | | W4 |
| DpHandHistoryPersistServiceImpl | com.example.mgdemoplus.service.serviceImpl | history | impl | no | | W4 |
| DpHandHistoryQueryMapper | com.example.mgdemoplus.mapper | history | mapper | no | | W4 |
| DpObservedHandHistory | com.example.mgdemoplus.entity | history | entity | no | | W4 |
| DpObservedHandParticipant | com.example.mgdemoplus.entity | history | entity | no | | W4 |
| DpObservedHandHistoryMapper | com.example.mgdemoplus.mapper | history | mapper | no | | W4 |
| DpObservedHandParticipantMapper | com.example.mgdemoplus.mapper | history | mapper | no | | W4 |
| DpObservedHandActionRecordBO | com.example.mgdemoplus.bo | history | bo | no | room → observed service | W4 |
| DpObservedHandRecordBO | com.example.mgdemoplus.bo | history | bo | no | | W4 |
| DpObservedPotSnapshotBO | com.example.mgdemoplus.bo | history | bo | no | | W4 |
| DpObservedSeatAtHandStartBO | com.example.mgdemoplus.bo | history | bo | no | | W4 |
| DpObservedStreetBoardBO | com.example.mgdemoplus.bo | history | bo | no | | W4 |
| DpObservedHandActionType | com.example.mgdemoplus.types | history | (types) | no | Move as `history` enum subpackage or keep global `types` | W4 |
| DpHandHistoryDetailVO | com.example.mgdemoplus.vo | history | vo | no | | W4 |
| DpHandHistoryListItemVO | com.example.mgdemoplus.vo | history | vo | no | | W4 |
| DpHandHistoryPageVO | com.example.mgdemoplus.vo | history | vo | no | | W4 |
| DpSharkOpponentProfile | com.example.mgdemoplus.entity | history | entity | no | **Dead** — no service reads (see services.md) | W4 |
| DpSharkOpponentProfileMapper | com.example.mgdemoplus.mapper | history | mapper | no | **Dead** mapper | W4 |
| DpFriendLinkRow | com.example.mgdemoplus.entity | social | entity | no | | W5 |
| DpFriendRequestRow | com.example.mgdemoplus.entity | social | entity | no | | W5 |
| DpFriendMessageRow | com.example.mgdemoplus.entity | social | entity | no | | W5 |
| DpFriendChatReadRow | com.example.mgdemoplus.entity | social | entity | no | | W5 |
| DpFriendLinkMapper | com.example.mgdemoplus.mapper | social | mapper | no | | W5 |
| DpFriendRequestMapper | com.example.mgdemoplus.mapper | social | mapper | no | | W5 |
| DpFriendMessageMapper | com.example.mgdemoplus.mapper | social | mapper | no | | W5 |
| DpFriendChatReadMapper | com.example.mgdemoplus.mapper | social | mapper | no | | W5 |
| DpFriendChatService | com.example.mgdemoplus.service.serviceImpl | social | impl | no | No interface today | W5 |
| DpFriendSocialService | com.example.mgdemoplus.service.serviceImpl | social | impl | no | Uses `roomchat` invite mapper | W5 |
| SocialNotifyPayload | com.example.mgdemoplus.social | social | (root) | no | | W5 |
| SocialNotifyPublisher | com.example.mgdemoplus.social | social | (root) | no | | W5 |
| SocialNotifySummaryService | com.example.mgdemoplus.social | social | (root) | no | | W5 |
| SocialSseHub | com.example.mgdemoplus.social | social | (root) | no | | W5 |
| DpFriendPresenceState | com.example.mgdemoplus.presence | presence | (enum) | no | **Already** under `presence/` | W5 |
| DpFriendPresenceService | com.example.mgdemoplus.service | presence | (root) | no | In-memory map | W5 |
| DpSitePresenceService | com.example.mgdemoplus.service | presence | (root) | no | Site heartbeat TTL | W5 |
| DpRoomLobby | com.example.mgdemoplus.entity | lobby | entity | no | | W6 |
| DpRoomLobbyMapper | com.example.mgdemoplus.mapper | lobby | mapper | no | | W6 |
| DpRoomLobbyMpMapper | com.example.mgdemoplus.mapper | lobby | mapper | no | | W6 |
| DpRoomHallService | com.example.mgdemoplus.service | lobby | (root) | no | | W6 |
| DpRoomHallServiceImpl | com.example.mgdemoplus.service.serviceImpl | lobby | impl | no | Inline Redis for public rooms | W6 |
| DpRoomLobbySearchParamBO | com.example.mgdemoplus.bo | lobby | bo | no | | W6 |
| DpRoomPublicRoomsPageVO | com.example.mgdemoplus.vo | lobby | vo | no | | W6 |
| DpRoomLobbyReconcileScheduler | com.example.mgdemoplus.scheduler | lobby | (root) | no | Move scheduler next to lobby | W6 |
| DpQuickMatchRoomSemantics | com.example.mgdemoplus.quickmatch | quickmatch | (root) | no | **Already** under `quickmatch/` | W6 |
| JoinableQuickMatchRoomIndex | com.example.mgdemoplus.quickmatch | quickmatch | (root) | no | **Already** | W6 |
| DpQuickMatchPairingCoordinator | com.example.mgdemoplus.quickmatch.pairing | quickmatch | pairing | no | **Already** | W6 |
| DpQuickMatchPairingHost | com.example.mgdemoplus.quickmatch.pairing | quickmatch | pairing | no | Implemented by `DpRoomServiceImpl` | W6 |
| DpQuickMatchWaitEntry | com.example.mgdemoplus.quickmatch.pairing | quickmatch | pairing | no | **Already** | W6 |
| RoomChatBuffer | com.example.mgdemoplus.roomchat.buffer | roomchat | buffer | no | **Already** | W6 |
| RoomChatEntry | com.example.mgdemoplus.roomchat.buffer | roomchat | buffer | no | **Already** | W6 |
| DpRoomChatPersistenceService | com.example.mgdemoplus.roomchat | roomchat | (root) | no | **Already** | W6 |
| DpRoomChatMessageRow | com.example.mgdemoplus.roomchat.entity | roomchat | entity | no | **Already** | W6 |
| DpRoomInviteRow | com.example.mgdemoplus.roomchat.entity | roomchat | entity | no | **Already**; social reads invites | W6 |
| DpRoomChatMessageMapper | com.example.mgdemoplus.roomchat.mapper | roomchat | mapper | no | **Already** | W6 |
| DpRoomInviteMapper | com.example.mgdemoplus.roomchat.mapper | roomchat | mapper | no | **Already** | W6 |
| DpRoomServiceImpl | com.example.mgdemoplus.service.serviceImpl | room | impl | no | **3571** lines; no `DpRoomService` interface | W7 |
| DpNpcEngine | com.example.mgdemoplus.service.serviceImpl | npc | impl | no | Rule bots; large | W7 |
| DpNpcUnifiedPreflopStrategy | com.example.mgdemoplus.service.serviceImpl | npc | impl | no | | W7 |
| DpNpcStreetActionLog | com.example.mgdemoplus.service.serviceImpl | npc | impl | no | | W7 |
| DpLlmNpcDecisionService | com.example.mgdemoplus.service.serviceImpl | npc | impl | no | Async LLM | W7 |
| LlmNpcGameContext | com.example.mgdemoplus.service.serviceImpl | npc | impl | no | | W7 |
| LlmNpcGlobalHandConversationStore | com.example.mgdemoplus.service.serviceImpl | npc | impl | no | | W7 |
| LlmNpcUserSnapshot | com.example.mgdemoplus.service.serviceImpl | npc | impl | no | | W7 |
| DpNpcCallStrategy | com.example.mgdemoplus.service.serviceImpl.npc | npc | impl | no | Flatten `serviceImpl.npc` → `npc.impl` | W7 |
| DpNpcFishStrategy | com.example.mgdemoplus.service.serviceImpl.npc | npc | impl | no | | W7 |
| DpNpcLagStrategy | com.example.mgdemoplus.service.serviceImpl.npc | npc | impl | no | | W7 |
| DpNpcManiacStrategy | com.example.mgdemoplus.service.serviceImpl.npc | npc | impl | no | | W7 |
| DpNpcNitStrategy | com.example.mgdemoplus.service.serviceImpl.npc | npc | impl | no | | W7 |
| DpNpcRuleDecisionParams | com.example.mgdemoplus.service.serviceImpl.npc | npc | impl | no | | W7 |
| DpNpcTagStrategy | com.example.mgdemoplus.service.serviceImpl.npc | npc | impl | no | | W7 |
| DpGameRoomPushService | com.example.mgdemoplus.websocket | global | websocket | no | Import fixes after room/roomchat | W7 |
| DpGameRoomWebSocketHandler | com.example.mgdemoplus.websocket | global | websocket | no | | W7 |
| DpQuickMatchPushService | com.example.mgdemoplus.websocket | global | websocket | no | | W7 |
| DpQuickMatchWebSocketHandler | com.example.mgdemoplus.websocket | global | websocket | no | | W7 |

---

## Common inventory

| Type | Evidence (≥2 modules) |
|------|------------------------|
| `DpUser` | **user** (`DpUserServiceImpl`), **social** (`DpFriendSocialService`, `DpFriendChatService`, mailbox/social controllers), **room** (`DpRoomServiceImpl`), **history** (`DpHandHistoryServiceImpl`, persist) |
| `DpUserMapper` | **user** + **social** (`DpFriendSocialService`, `DpFriendChatService`) |
| `DpPlayer` | **room** (`DpRoomServiceImpl`, `DpRoomBO`), **npc** (engine + strategies), **history** (observed/persist), **quickmatch** (`DpQuickMatchRoomSemantics`), **utils** (`DpUtilSmartContext`) |
| `DpPlayerStats` | **room** (`DpRoomBO`), **npc** (`DpNpcEngine`, `DpNpcUnifiedPreflopStrategy`), **utils** (`DpUtilSmartContext`) |
| `DpPot` | **room** (`DpRoomBO`, `DpRoomServiceImpl`), **history** (`DpHandHistoryObservedImpl`) |
| `DpRoom` | **room** (`DpRoomServiceImpl`, controller), **lobby** (`DpRoomHallServiceImpl`, `DpRoomLobbyMapper`), **lobby vo** (`DpRoomPublicRoomsPageVO`) |
| `DpRoomBO` | **room**, **quickmatch** (index, pairing, semantics), **lobby** (`DpRoomHallService`), **history** (observed/persist APIs), **npc** (LLM + rule), **websocket** (`DpGameRoomPushService`) |

**Not common (single-module or global):** observed-hand entities/BOs (history + room callback only — keep under **history**), friend rows, music, roomchat, Redis list cache (music-only), `ResultUtil` (global utils).

**common? column count:** **8** classes (7 types + `DpUserMapper`).

---

## `DpRoomServiceImpl` split plan (plan only)

| Metric | Value |
|--------|--------|
| Physical lines | **3571** |
| Public API surface | No `DpRoomService` interface; `DpRoomController` injects concrete class |
| Inner types | `KickPlayersBatchResult`, `DpQuickMatchPairingHost` anonymous impl |

### Suggested extract classes (room module)

| Suggested class | Responsibility | Origin (method groups) |
|-----------------|----------------|-------------------------|
| `DpRoomRegistry` | `roomMap`, create/remove/exists, `getAllRooms`, `getRoomIdsInMemory` | Registry + `removeRoom` / `finalizeHall*` |
| `DpRoomMembershipService` | `joinRoom*`, `exitRoom`, `kick*`, `transferOwner`, `giveOwner`, spectator invite | Join/exit/kick block ~1492–2500 |
| `DpRoomSnapshotService` | `getRoomSnapshotForViewer`, `snapshotForViewerFromLive`, hole-card sanitize, `listRecentRoomChat` | ~1384–1490 |
| `DpRoomHandLifecycleService` | `startGame`, `newHand`, `bet`, `fold`, `rebuy`, `toggleReady`, `readyNextHand*`, deck/pots/actor | ~2502–3550 |
| `DpRoomHeartbeatScheduler` | 1s global tick, `heartbeat`, stale seat/spectator evict, deserted room cleanup | ctor timer ~244–477, `heartbeat` ~3578 |
| `DpRoomQuickMatchBridge` | QM queue, `quickMatchJoin*`, pairing host methods, index refresh | ~1771–2030, ~3599–3726 |
| `DpRoomNpcSeatFacade` | `add*Bot*`, `NpcAction` bridge to `DpNpcEngine` | ~109–232, ~612 |
| `DpRoomLobbySync` | `syncLobbyForRoomId`, `refreshJoinableQm*`, `finalizeHallAfterRoomRemoved` | ~507–666 |
| `KickPlayersBatchResult` | DTO | Move to `room.vo` |

`DpRoomServiceImpl` becomes a thin **facade** delegating to the above (or extract interfaces in a later pass).

---

## `docs/readme-scan` path / content mismatches

| Doc | Issue | Actual / fix |
|-----|--------|----------------|
| *(scan set)* | **Partial modularization not documented** | `music/*` and `roomchat/*` already exist; manifest table reflects current packages |
| [services.md](services.md) | Implies monolithic `service.serviceImpl` for music | `DpMusicServiceImpl` is `com.example.mgdemoplus.music.impl` |
| [services.md](services.md) | “Redis … 曲库列表” without class | `DpRedisListCacheService` under `service` + `service.cache` (music-only consumer) |
| [ai-npc.md](ai-npc.md) | Lists `BotLlmReasoning` like a class | Logger name in `DpLlmNpcDecisionService` (`LoggerFactory.getLogger("…BotLlmReasoning")`) |
| [ai-npc.md](ai-npc.md) | Links `../ai/npc-llm/` directory | Valid; folder contains only `part01_类间关系以及调用.md` (not a README index) |
| [social-friend.md](social-friend.md) | `../dp_friend_mailbox_mvp.md` | **OK** — file exists at `docs/dp_friend_mailbox_mvp.md` |
| [ai-npc.md](ai-npc.md) | `../ENV_README.md` | **OK** — `docs/ENV_README.md` |
| [controllers.md](controllers.md) | Removed `/demo/*` | **OK** — no `RedisLabController` in `src/main/java` (still mentioned in root `README.md`, not readme-scan) |

---

## Wave summary counts

| Wave | Classes |
|------|---------|
| W1 | 26 |
| W2 | 8 |
| W3 | 14 |
| W4 | 23 |
| W5 | 18 |
| W6 | 21 |
| W7 | 20 |
| **Total** | **123** |

---

## Post-move checklist (for implementers)

1. Update `@MapperScan` in `MgDemoPlusApplication` per module `mapper` package.
2. Introduce `DpRoomService` interface when splitting `DpRoomServiceImpl`.
3. Keep `websocket/` global; fix imports to `room`, `roomchat`, `common` types.
4. Re-run compile + integration tests after each wave.

---

## MIGRATION_COMPLETE (2026-05-22 复核)

Agent 3 只读扫描复核（`P:/javaworkspace/MGDemoPlus`）。

| Check | Result |
|-------|--------|
| **Overall** | **PASS** |
| `mvn -q compile -DskipTests` | PASS (exit 0, 2026-05-22) |
| `mvn -q test` | PASS (exit 0, Spring Boot 3.5.11 上下文启动 + 单元测试) |
| `service.serviceImpl` in `src/**/*.java` | **0** 匹配 |
| `service.serviceImpl` 物理目录 `src/main/java/.../service/serviceImpl` | **不存在** |
| `@MapperScan` 包列表 | `common`, `history`, `lobby`, `music`, `roomchat`, `social`, `user`（见 `MgDemoPlusApplication`） |
| NPC / room 目标包 | `npc.engine` / `npc.strategy` / `npc.llm`；`room.impl` + `room.support.*` |
| Package tree doc | [ARCHITECTURE.md](ARCHITECTURE.md) |

**结论：`MIGRATION_COMPLETE = true`** — 业务源码已无 `service.serviceImpl` 包；残留仅文档、`application.yml` 注释日志示例、`scripts/refactor_flatten_packages.py` 等。

### 仍待文档对齐（非迁移阻塞）

| 位置 | 问题 |
|------|------|
| `README.md` L17 | 仍写 `service.serviceImpl.npc` → 应为 `npc.engine` / `npc.llm` |
| `CLAUDE.md` | 密码仍写 MD5 → 源码为 **bcrypt** |
| `docs/ai/npc-engine/*`、`docs/DP_PERSISTENCE_README.md` | 旧包 `service.serviceImpl.dp` |
| `docs/ENV_README.md` | 主配置宜标明 `application.yml` |
| `application.yml` 注释 | logger 类名 `service.serviceImpl.DpNpcEngine` |

---

## MIGRATION_COMPLETE (2026-05-18 初验)

Final wrap-up: `mvn clean compile` + `mvn test` on `P:/javaworkspace/MGDemoPlus`.

| Check | Result |
|-------|--------|
| **Overall** | **PASS** |
| `mvn clean compile` | PASS (exit 0) |
| `mvn test` | PASS (exit 0, **24** tests, **0** failures) |
| Stale Java imports (`service.serviceImpl`, flat `entity.DpUser`, flat `bo.DpRoomBO`) | **none** in `src/**/*.java` |
| Package tree doc | [ARCHITECTURE.md](ARCHITECTURE.md) |

### Wave status (implementation order)

| Wave | Scope (as executed) | Status | Notes |
|------|---------------------|--------|-------|
| **W1** | Boot + global + `music/`, `history/`, `roomchat/` | **DONE** | `@MapperScan` for history, music, roomchat |
| **W2** | `social/`, `lobby/`, `presence/` (+ `presence.cache`) | **DONE** | Friend rows/mappers under `social.*`; Redis login/list cache under `presence.cache` |
| **W3** | `common/` kernel + `user/` | **DONE** | `DpUser`, `DpRoomBO`, players/pot/room; user + upload |
| **W4** | `history/` completion | **DONE** | Observed-hand BOs, types, vo under `history.*` |
| **W5** | (merged into W2/W3 in practice) | **DONE** | — |
| **W6** | `lobby/`, `quickmatch/`, `roomchat/` finish | **DONE** | `DpRoomLobbyReconcileScheduler` in `lobby/` |
| **W7** | `room/`, `npc/` | **DONE** | `DpRoomService` + `room.support.*`; NPC under `npc.engine`, `npc.strategy`, `npc.llm` |

Flat legacy trees **`entity/`, `mapper/`, `bo/`, `vo/`, `types/`, `service/`, `service/serviceImpl/`** are removed from `src/main/java`. **127** compilation units under `mgdemoplus/`.

### Remaining tech debt (non-blocking)

| Item | Severity | Action |
|------|----------|--------|
| `DpRoomServiceImpl` still large | medium | Continue extracting into `room.support.*` (registry, hand lifecycle, QM bridge) |
| `DpSharkOpponentProfile` + mapper | low | Dead persistence; delete or wire when Shark DB is productized |
| Javadoc in `DpNpcEngine` cites `service.serviceImpl.DpLlmNpcDecisionService` | low | Point to `npc.llm.DpLlmNpcDecisionService` |
| Root `README.md`, `docs/DP_PERSISTENCE_README.md`, `docs/ai/npc-engine/*` cite old `service.serviceImpl` paths | low | Doc-only refresh |
| `docs/readme-scan/services.md` / manifest class table | low | Historical “current package” column; use [ARCHITECTURE.md](ARCHITECTURE.md) for truth |
| `docs/SpringScheduling.md` scheduler path | low | Update to `lobby/DpRoomLobbyReconcileScheduler` |
| Empty `scheduler/` directory on disk | cosmetic | Delete folder or add `.gitkeep` note |
| Intermittent first `mvn clean test` `NoClassDefFoundError` on Windows | low | Re-run; full clean build passed in wrap-up |
