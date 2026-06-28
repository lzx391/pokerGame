# Quick Match Redis Migration (P0)

Multi-instance quick match: shared wait queue, global pairing lock, room mutations via `runExclusive`, and cross-instance WebSocket fan-out — aligned with the room Redis SSOT + pub/sub pattern.

## Configuration

Quick match is always enabled on every app instance in a multi-instance deployment (shared Redis wait queue and pairing lock). No feature flag is required.

Optional tuning: `mgdemoplus.dp-quick-match-prune-ms` / `MGDEMOPLUS_DP_QUICK_MATCH_PRUNE_MS` (default 30000) controls stale wait-entry cleanup on the scheduled prune task.

## P2: Joinable room index (Redis)

| Key | Purpose |
|-----|---------|
| `dp:qm:join:room2bucket` | HASH: roomId → shortage (1..maxSeatCount) |
| `dp:qm:join:bucket:{shortage}` | ZSET: roomIds in bucket (score=0, lex order) |
| `dp:qm:join:active_buckets` | ZSET: non-empty bucket keys (member=score=shortage) |

- **Write path**: `DpRedisRoomRegistry.saveAfterMutation` updates join index atomically (Lua) alongside room state; `DpRoomEventSubscriber` no longer refreshes index on pub/sub.
- **Read path**: `JoinableQuickMatchRoomIndex` reads Redis under `DpQuickMatchPairingLock` during pairing (same contract as wait queue).
- **Unit tests**: no-arg `JoinableQuickMatchRoomIndex()` uses in-memory delegate; production Spring bean uses Redis.

See [docs/notes/quickmatch-joinable-index-redis-design.md](../notes/quickmatch-joinable-index-redis-design.md).

## Redis keys / channels (P0 + P2)

| Key / channel | Purpose |
|---------------|---------|
| `dp:qm:wait` | FIFO wait queue (LIST of JSON wait entries) |
| `dp:lock:qm:pairing` | Global pairing + queue mutation lock (SET NX EX, 10s TTL) |
| `dp:qm:events` | Quick-match WS pub/sub (`WAITING` / `MATCHED` / `IDLE`) |

Room state continues to use existing `dp:room:*` keys and `dp:room:events`.

## Architecture notes

- **Wait queue**: `DpQuickMatchWaitQueue` replaces per-JVM `ArrayDeque`.
- **Pairing lock**: `DpQuickMatchPairingLock` replaces `defaultQmLock` + `dpQuickMatchAssignmentLock`.
- **Room mutations**: `DpQuickMatchPairingCoordinator` uses `registry.runExclusive(roomId, …)` instead of `synchronized(DpRoomBO)`.
- **WS push**: `QuickMatchEventPublisher` → Redis `dp:qm:events` → `QuickMatchEventSubscriber` → local `DpQuickMatchPushService` when the nickname has a session on that JVM.
- **Joinable index**: Redis `dp:qm:join:*` (P2); updated in `saveAfterMutation`, not via pub/sub subscriber.

Gameplay / pairing rules unchanged — infrastructure only.

## Manual verification (8088 + 8089 + nginx 8880)

### Prerequisites

- Redis reachable from both JVMs
- MySQL + Flyway migrated
- nginx round-robin to 8088 and 8089 **without** sticky sessions

Example `.env` per instance (only `MGDEMOPLUS_INSTANCE_ID` / port differs):

```env
MGDEMOPLUS_INSTANCE_ID=8088   # or 8089 on second JVM
SERVER_PORT=8088              # or 8089
```

### Steps

1. Start Redis, MySQL, nginx (`8880` → 8088/8089), and both app instances.
2. Register/login two users (A on browser tab hitting instance A, B on another device/tab — requests may land on either instance via nginx).
3. Both open quick-match WebSocket (`/ws/quickMatch` or product equivalent) and call join-queue REST.
4. **Expect**:
   - Both receive `WAITING` with queue position via WS (even if REST hit a different instance than WS).
   - Within a few seconds both receive `MATCHED` with the **same** `roomId`.
   - Both can enter the room and see each other seated / game starts per existing rules.
5. Optional: cancel match on one client → other instance eventually sees queue drain; idle push received locally.

### Redis sanity checks (optional)

```bash
redis-cli LLEN dp:qm:wait          # should be 0 after successful pairing
redis-cli GET dp:lock:qm:pairing   # should be empty when idle
redis-cli SMEMBERS dp:room:index   # contains matched room id
redis-cli HLEN dp:qm:join:room2bucket   # indexed joinable public rooms
redis-cli ZRANGE dp:qm:join:bucket:1 0 -1   # roomIds missing 1 player (example)
redis-cli ZRANGE dp:qm:join:active_buckets 0 -1 WITHSCORES
```

### Failure signals

| Symptom | Likely cause |
|---------|----------------|
| One player stuck on WAITING | WS not connected on that JVM, or Redis queue/lock unreachable |
| Different room ids | Pairing lock / queue not shared (Redis misconfig) |
| MATCHED but empty room | Room SSOT not shared (Redis room registry) |
| Index never fills | Room events pub/sub down; fallback scan may still work slowly |
| Join index empty but rooms have vacancies | `saveAfterMutation` path; check `HLEN dp:qm:join:room2bucket` |

## Tests

```bash
mvn test -Dtest=JoinableQuickMatchRoomIndexTest,DpQuickMatchPairingCoordinatorTest,DpQuickMatchWaitQueueTest,DpRoomCreateRoomQuickMatchPairingTest
mvn test -Dtest=DpQuickMatchWaitQueueTest,DpQuickMatchPairingCoordinatorTest,DpRedisRoomRegistryTest
mvn clean package -DskipTests
```
