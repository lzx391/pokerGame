# Social SSE Redis Pub/Sub — CHANGELOG

**Gameplay unchanged; social SSE notifications fan out across JVM instances via Redis pub/sub.**

## What changed

- **Redis channel**: `dp:social:events` — minimal JSON `{userId, kind}` (presence/achievement include extra fields).
- **Publisher**: `SocialEventPublisher` — `SocialNotifyPublisher` and `AchievementNotifyPublisher` publish instead of calling `SocialSseHub` directly (avoids double-push on the publishing JVM).
- **Subscriber**: `SocialEventSubscriber` — each instance rebuilds `notify` payloads from DB/services and pushes only when `SocialSseHub.hasLocalSubscribers(userId)`.
- **Config**: shared `RedisMessageListenerContainer` in `DpRoomRedisPubSubConfig` also subscribes to `dp:social:events`.

## What did NOT change

- Friend chat / mailbox business logic, frontend API contracts, poker gameplay.

## How to verify manually

1. Start **8088** + **8089** behind nginx **8880** (round-robin, no sticky session).
2. User A opens social SSE on **8880** (may land on **8089**).
3. User B sends a friend message via API hitting **8088**.
4. User A receives SSE `notify` event without waiting for poll/refresh.

## Tests

```bash
mvn test "-Dtest=SocialEventPublisherTest,SocialEventSubscriberTest,AchievementNotifyPublisherTest"
mvn -q -DskipTests compile
```

## Files changed

| File | Change |
|------|--------|
| `social/notify/SocialRedisKeys.java` | **New** — channel name |
| `social/notify/SocialEventPublisher.java` | **New** — Redis publish |
| `social/notify/SocialEventSubscriber.java` | **New** — local SSE fan-out |
| `config/SocialRedisPubSubConfig.java` | **New** — subscriber bean |
| `config/DpRoomRedisPubSubConfig.java` | Register social channel on shared container |
| `social/notify/SocialNotifyPublisher.java` | Publish via Redis instead of direct hub |
| `social/notify/SocialSseHub.java` | `hasLocalSubscribers`, comment update |
| `achievement/notify/AchievementNotifyPublisher.java` | Publish via Redis |
| `social/notify/SocialEventPublisherTest.java` | **New** |
| `social/notify/SocialEventSubscriberTest.java` | **New** |
| `achievement/notify/AchievementNotifyPublisherTest.java` | Updated mocks |
