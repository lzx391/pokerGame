# MGDemoPlus

A **Spring Boot** web demo centered on **DP Texas Hold’em (NL)**—multi-room tables, live play, NPC bots, hand history, and optional Volcengine Ark LLM players. The **`front/dp_game`** UI is **Vue 2 + Vue Router + Vuex + Element UI**; production builds are embedded into the Spring Boot JAR by **`Dockerfile`**, served on the **same port** as REST and WebSocket.

**Long-form maintenance notes (env, WebSocket, NPC, hand history, Docker, etc.):** Chinese **[README.ch.md](README.ch.md)** · English **[README.en.md](README.en.md)** (same structure, translated).

---

## Features

### Gameplay

- **NLHE rules**: Blinds, streets, min-raise, side pots, etc., implemented server-side (e.g. `DpRoomServiceImpl`); the table UI uses polling plus WebSocket for state.
- **Rooms**: In-memory **`ConcurrentHashMap` (`roomMap`)**; see long docs for WebSocket and room lifecycle.
- **Push**: Game WebSocket path pattern **`/ws/dp-game?roomId=...`**; serializes only when subscribers exist and de-duplicates JSON vs last push.

### Accounts

- **Auth**: **Spring Security** + **JWT** (`Authorization: Bearer`); whitelist covers login/register, `/ws/**`, some read-only room APIs—full list in [docs/JWT.md](docs/JWT.md).
- **Passwords**: **MD5(UTF-8)** digest stored server-side—see [docs/DpUserPassword.md](docs/DpUserPassword.md).
- **Play money only**: No real-money gambling.

### NPC / AI

- **Rule-based bots**: Multiple styles (e.g. Fish, TAG, **Shark** with preflop/postflop logic and opponent memory)—overview in [docs/ai/npc-engine/README.md](docs/ai/npc-engine/README.md).
- **LLM seat (optional)**: `BOT_LLM` via Ark APIs; set `ARK_API_KEY`, `ARK_ENDPOINT_ID`, etc. ([docs/ENV_README.md](docs/ENV_README.md), LLM section in [README.en.md](README.en.md)).

### Other

- **Music library BGM**: Upload/list APIs; in-game playback state can sync over the room WebSocket.
- **Redis**: Used for music list cache, `/demo/redis/*` labs, etc.; **table WebSocket state is not Redis-backed by default** (scale-out notes in [docs/WEBSOCKET.md](docs/WEBSOCKET.md)).

---

## Stack

| Layer | Tech |
|--------|------|
| Backend | Java **17**, **Spring Boot 3.5**, Web / Security / WebSocket, **MyBatis-Plus**, **PageHelper**, **Druid**, MySQL driver, **Lettuce** (Redis), **JJWT** |
| Frontend (DP) | **Vue 2**, Vue Router, **Vuex**, **Element UI**, axios (`front/dp_game`) |
| Data | **MySQL 8** (default DB **`school_db`**), **Redis 7** |
| Build / Ops | **Maven**, **Docker** / **Compose**, **Nginx** |

---

## Requirements

- **JDK 17**, **Maven 3.6+**
- For DP frontend dev: **Node.js** compatible with Vue CLI 5 in `front/dp_game`
- Optional: **Docker 20+**, **Compose 2+**

---

## Quick start

### 1. Clone and env

```bash
git clone <your-repo-url> MGDemoPlus
cd MGDemoPlus
cp .env.example .env
# Edit .env — do not commit secrets
```

See [docs/ENV_README.md](docs/ENV_README.md) for variable mapping.

### 2. Docker (recommended)

From repo root:

```bash
docker compose up --build
```

- Via Nginx: **http://localhost** — [docs/NGINX.md](docs/NGINX.md)
- Direct app: **http://localhost:8088** — hash routes, e.g. **`/#/login`**

MySQL on host is typically **`localhost:3307`**, Redis **`localhost:6380`** ([docs/DOCKER.md](docs/DOCKER.md)).

### 3. Local dev (split)

Backend (run **from repo root** so `.env` loads):

```bash
mvn spring-boot:run
```

Frontend:

```bash
cd front/dp_game
npm install
npm run dev
```

Use `vue.config.js` dev proxy (often **`/dev-api`**) to hit the API.

---

## Repo layout (overview)

```
MGDemoPlus/
├── src/main/java/com/example/mgdemoplus/
├── front/dp_game/
├── docker/
├── docker-data/
├── docker-compose.yml
├── docker-compose.hub.yml
├── Dockerfile
├── pom.xml
├── README.md              # This file — English short overview
├── README.ch.md           # Chinese long-form maintenance notes
├── README.en.md           # English long-form (same as README.ch, translated)
└── docs/                  # Topic docs; see docs/README.md for the full map
```

---

## Documentation

- **[docs/README.md](docs/README.md)** — full index of `docs/` with short descriptions per file
- [docs/ENV_README.md](docs/ENV_README.md)
- [docs/DOCKER.md](docs/DOCKER.md)
- [docs/NGINX.md](docs/NGINX.md)
- [docs/JWT.md](docs/JWT.md)
- [docs/DPGAME.md](docs/DPGAME.md)
- [docs/WEBSOCKET.md](docs/WEBSOCKET.md)
- [front/dp_game/docs/README.md](front/dp_game/docs/README.md) — DP table UI (layout/theme) notes
- [README.ch.md](README.ch.md) — Chinese long-form maintenance notes
- [README.en.md](README.en.md) — English long-form maintenance notes

---

## Legal

Virtual chips only; **no real-money play**. License: see **LICENSE** if present.

If this short overview disagrees with code, trust **[README.ch.md](README.ch.md)** / **[README.en.md](README.en.md)** for operational detail and **[docs/README.md](docs/README.md)** / **docs/** for topic docs.
