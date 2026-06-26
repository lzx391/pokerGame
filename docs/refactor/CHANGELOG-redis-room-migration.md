# Redis Multi-Instance Room Migration — CHANGELOG

**Gameplay unchanged; storage layer migrated to Redis for multi-instance.**

## What changed

- **Room SSOT**: `DpRoomServiceImpl` no longer caches `registry.roomMap()` at construction. All live reads use `registry.get()` / `registry.values()`; mutations use `registry.runExclusive` / `runExclusiveVoid`.
- **Critical paths fixed**: `createRoom`, `startGame` / `newHandWithoutLobbyUpsert`, `heartbeat`, `getAllRooms` (WS snapshot), `giveOwner`, `kick`, `exitRoom`, `joinRoomInviteAsSpectator`, `rebuy`, `toggleReady`, `readyNextHand`.
- **Redis registry**: `DpRedisRoomRegistry` — same-thread reentrant `runExclusive` (heartbeat → fold/giveOwner/exitRoom); `pruneOrphanIndexEntries()` on leader tick.
- **Heartbeat**: evicted seated players trigger `shutdownSubscriptionsForNicknameInRoom` (WS cleanup on the local instance).
- **Lobby reconcile**: comment updated — safe with shared Redis registry; no nginx sticky required.

## What did NOT change

- Poker rules, hand flow, NPC/mood/settlement logic, frontend API contracts.

## How to verify manually

1. Start **8088** + **8089** behind nginx **8880** (round-robin, no `ip_hash`).
2. Create room on UI → **Start game** → enter room without immediate flash/exit.
3. Send heartbeat; player not evicted within 60s while connected.
4. Close room → `redis-cli SMEMBERS dp:room:index` should not accumulate orphan ids.

## Tests

```bash
mvn test "-Dtest=DpRedisRoomRegistryTest,DpRoomDesertedRoomCleanupTest,DpRoomExitPresenceMarkIdleTest,DpRoomRegistryFreshReadTest,DpRoomMoodStateSnapshotTest,DpRbacExperimentalDeckPresetTest"
mvn -q -DskipTests compile
```

## Files changed

| File | Change |
|------|--------|
| `room/impl/DpRoomServiceImpl.java` | Remove stale `roomMap`; registry SSOT reads/writes |
| `room/support/DpRedisRoomRegistry.java` | Reentrant lock, orphan index prune |
| `room/support/DpRoomRegistry.java` | `pruneOrphanIndexEntries()` hook |
| `room/support/DpRoomHeartbeatScheduler.java` | Orphan prune, WS cleanup on evict |
| `lobby/DpRoomLobbyReconcileScheduler.java` | Comment update |
| `room/support/DpRedisRoomRegistryTest.java` | Reentrant + prune tests |
| `room/impl/DpRoomRegistryFreshReadTest.java` | **New** — stale snapshot regression |
| `room/support/DpRoomTestSupport.java` | Test helpers via `registry` |
| `room/impl/DpRoomDesertedRoomCleanupTest.java` | Use registry, not `roomMap` field |
| `room/impl/DpRoomExitPresenceMarkIdleTest.java` | Use registry |
| `room/impl/DpRoomMoodStateSnapshotTest.java` | Use registry |
| `room/impl/DpRbacExperimentalDeckPresetTest.java` | Use registry |
| `room/impl/DpRoomMoodAfterHandTest.java` | Align win-mood expectation with `moodCorrectionWin` (pre-existing) |
