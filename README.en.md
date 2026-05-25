## MGDemoPlus

### Documentation map (entry points)

| Entry | Description |
|------|-------------|
| **[docs/README.md](docs/README.md)** | **Full topic index for `docs/`**, grouped by environment / deploy / security / game / WebSocket / persistence / Redis / AI / notes; one-line intro per doc—**start here** when looking for a subject. |
| **[README.md](README.md)** | **Short English overview**: features, stack, quick start; good first read (English). |
| **[README.ch.md](README.ch.md)** | **Chinese long-form** maintenance notes (same structure as this file). |
| **[README.en.md](README.en.md)** | **This file** — English long-form (translation of README.ch). |
| **[front/dp_game/docs/README.md](front/dp_game/docs/README.md)** | **DP frontend topics**: table layout tuning, themes and `--dp-*` binding, mapping to backend `docs`. |

**This file (`README.en.md`)** is the **long-form run & maintenance** companion—environment variables, WebSocket and table UI iterations, NPC, hand history and users, music BGM, Redis, Docker, etc., in chronological or functional chunks; it **complements** topic docs under `docs/` (when details live in `docs/*.md`, this file cross-references them). **Chinese version:** [README.ch.md](README.ch.md).

### Overview

- Quick English intro: [README.md](README.md). **Full doc map:** [docs/README.md](docs/README.md). This file is the long English narrative.

### Environment variables (`.env`)

- Repo root has **`.env.example`**: common variables for Docker Compose and local runs (MySQL / Redis / JWT / Ark LLM, etc.). Copy to **`.env`** and fill in; **`.env` is gitignored**—do not commit real secrets.
- **`docker compose`** reads `.env` next to `docker-compose.yml` for image tags, passwords, and optional `JWT_SECRET`, `ARK_*`, etc. for app containers.
- **Running Spring Boot locally** (IDEA / `mvn spring-boot:run`): **`MgDemoPlusApplication.main`** calls **`LocalDotenvLoader.load()`** (**`dotenv-java`**) **before** **`SpringApplication.run`**, loading root **`.env`** into system properties; **working directory must be repo root**, or the file is not found. If the same keys exist in the OS or IDE env, env wins.
- **Defaults**: DB, Redis, upload paths, Ark, etc. are already in **`application.properties`** as `${ENV:default}`; you can start without `.env`. Override via root `.env` or IDE env; names are in **`.env.example`**.
- **Details** (`application.properties` vs `.env`, JWT, Ark LLM): [docs/ENV_README.md](docs/ENV_README.md).

### DP game doc links

**Full categories and one-line intros:** [docs/README.md](docs/README.md). Handy shortcuts:

- [Backend interview questions (aligned with this stack)](docs/notes/BACKEND_INTERVIEW_QUESTIONS.md)
- [JSON, Map, serialization / deserialization notes](docs/notes/Json-map-serialization.md)
- [DP game: rules, APIs, dev & ops](docs/DPGAME.md)
- [DP NPC engine (Fish/Maniac/TAG & Shark)](docs/ai/npc-engine/README.md)
- [Table layout tuning (top bar / round table / bottom, `dp-game-shell.css`)](front/dp_game/docs/GAME_LAYOUT_TUNING_README.md)
- [DP music library: `webPath`, disk paths, dev proxy](docs/DpMusicWebPath.md)
- [DP user passwords: front/back split, validation, recovery](docs/DpUserPassword.md)
- [Java: references, `roomMap`, shallow vs deep copy notes](docs/notes/Java对象引用与浅拷贝深拷贝备忘.md)
- [WebSocket: table page, handlers, `game.vue`](docs/WEBSOCKET.md)

### In-game WebSocket (no Redis)

- **Scope**: **`front/dp_game`** table page only; server holds connections per `roomId` in memory with `ConcurrentHashMap` room data in-process—**Redis not required**.
- **Seat grid (2026-03-23)**: `game.vue` rotates the grid to **hero-first**—**your seat stays on the first row**; others keep relative order; `seatIndex` matches backend; action highlight and dealer animation unchanged. See `docs/RoomUi.md`.
- **Subcomponents (2026-04-02)**: Extracted from `game.vue`: **`GameRoomChatBar`**, **`GameBottomSheet`**, **`GameTableActionTimer`**, **`GameRoundTable`**; geometry helpers in **`front/dp_game/src/utils/dpGameRoundTableLayout.js`**, player box styles in **`dpGamePlayerBoxStyle.js`**.
- **Further split (2026-04-02)**: **`GameHeroDockFooter`** (wide/narrow dock + spectator chat) and **`GameDpFloatingModals`** / **`GameDpGameSheets`** use **`inject: ['dpGameView']`**; **`mixins/dpGameFullscreenMixin.js`**, **`mixins/dpGameActionCountdownMixin.js`**; `game.vue` keeps routing, WS, HTTP, `provide`, `<audio ref="roomBgm">`, core logic.
- **Vuex (2026-04-02)**: **`vuex@3`**; state in **`front/dp_game/src/store/modules/dpGame.js`** (`dpGame` namespace); `game.vue` uses `mapState` / `mapGetters`; child writes use **`$store.commit('dpGame/…')`** instead of mutating readonly computed.
- **Round table, cards, orbit timer (2026-04-02)**: Card size defaults in **`dp-game-shell.css`** **`--dp-card-base-*`**; **`GameRoundTable`** SVG rails from community zone to felt edge; **action timer** **`orbitActionTimer`** along dealer–seat ray; **`DpRoom.chipLeaderNicknames`** filled in **`DpRoomServiceImpl#autoSettle`**; front only compares nicknames. Streak felt badge still commented out.
- **Full-width table (2026-03-23)**: Login/register padding on `.app-container` only; `.dp-game-root` spans full width.
- **Native fullscreen + dialogs (2026-03-23)**: Fullscreen API subtree only; Element `$confirm` / `$message` moved under `.dp-game-root` when native fullscreen.
- **Themes (2026-03-29)**: `dpGameThemes.js`, `dp-game-themes.css`; see [front/dp_game/docs/THEME_BINDING_README.md](front/dp_game/docs/THEME_BINDING_README.md). Themes **`strawberry`**, **`cotton`**; eco mode orthogonal via `data-dp-eco-mode`. Element on `body` synced from `game.vue` for dialogs.

**Card backs / muck**: CSS variables on `.dp-game-root`; `--dp-panel-bg` / `--dp-player-card-bg` must be solid colors mixable with `color-mix`, not gradients.

- **Eco mode (2026-03-23)**: `localStorage` `dp_game_eco_mode`; skips heavy animations. **2026-03-26**: Cached hand evaluation; **`handRankName` / `handRankDetail`** from API when available.
- **Action timer (2026-03-25)**: 30s ring; shared `timeLeft` with `GameActionPanel`. Later tweaks: muck anchor position, dealer markers, hero deal target `data-dp-hero-deal-target`.
- **Card UI (2026-03-22–04-01)**: Fly-in, flip timing, modals, `dpGameDealerAnchor.js` fallbacks, felt-layer markers, muck orbit—see code comments in `game.vue` and CSS files.
- **Showdown / muck animation (2026-03-23)**: Simultaneous reveals; muck to `data-dp-muck-anchor`.
- **Layout (2026-04-02 / 03-23)**: `data-dp-stage`, scroll in main, hero row, chat inline, top bar safe-area fix in `dp-game-shell.css`, etc.
- **URL**: `ws://<host>:<port>/ws/dp-game?roomId=<id>` (dev often `ws://localhost:8088`).
- **In-game hand history (2026-03-31)**: `GameHandHistoryModal` embedded; lobby `/hand-history` unchanged light theme.
- **Payload**: Same JSON as `GET /dpRoom/getNowRoom`; closed room → `{"_ws":"roomClosed"}`.
- **Push cadence**: 1s tick; serialize only if ≥1 WS subscriber; skip if JSON unchanged (`DpGameRoomPushService`); `lastHeartBeat` excluded from JSON.
- **Table chat**: One bubble per seat; protocol in `docs/WEBSOCKET.md`.
- **Action panel (2026-03-25)**: Narrow two-row layout; wide merges rows.
- **NL min-raise (2026-03-25)**: `DpRoom.lastRaiseIncrement`, `DpRoomServiceImpl.bet`; `GameActionPanel` shows min total, pot fractions, slider. **2026-04-01**: Min open one big blind; snap to small-blind multiples.
- **Code**: `DpGameRoomPushService`, `DpGameRoomWebSocketHandler`, `WebSocketGameRoomConfig`; `DpRoomHeartbeatScheduler` (~1s) calls `broadcastIfSubscribed`.

### NPC / AI (plain language)

#### Saloon-style Fish (`BOT_Fish`, 2026-03-25)

- **Rule NPCs**: No tiered difficulty noise on strength or pot odds—decisions use real strength and pot odds; style comes from `StyleProfile` and per-bot logic. **`mood`**: off by default (`DpNpcEngine.NPC_MOOD_ENABLED = false`); set `true` to restore post-hand mood updates and mood-weighted probabilities.

#### Shark preflop ranges

Since 2026-03-18, Shark preflop uses **`DpNpcSharkPreflopStrategy.decideForShark`** from **`DpNpcEngine`** when `SHARK` and `preflop`.

- **HandGroup** 13×13 tiers; **`rangeLevel` 1–8** from player count, position, depth, style, tilt.
- **Style (2026-03-21)**: Shark maps to **`LOOSE_AGGRO`** (`preflopTightness` ~0.30), not TAG-tight tables.
- Decisions: open sizing; vs open (call/3bet/fold); vs 3bet; vs 4bet+ (all-in/call/fold).

#### Files

- `DpNpcSharkPreflopStrategy.java`, `DpNpcEngine.java`
- [docs/ai/npc-engine/README.md](docs/ai/npc-engine/README.md)

#### Shark opponent memory across rooms (2026-03-21)

- Table **`dp_shark_opponent_profile`** from `dp_shark_opponent_profile.sql` (key: nickname).
- Stores `PlayerStats`, `LearningLab` knobs; **not** keyed by room ID.
- **Write**: After each settled hand, `DpNpcSharkOpponentMemoryService.persistOpponentsAfterHand`.
- **Read**: On `joinRoom` / `newHand` if stats missing—`hydrate` from DB.

#### Shark strategy v2 (2026-03-25)

- Fewer “good odds but fold”; exploit calling stations; **`SharkStrategyProfile.DEFAULT`**, `SharkConfig`, `DpNpcSharkStrategy`.
- **`DpNpcSharkExploitHandPlan.tuneAfterBasePlan`** uses villain nickname for built-in fish IDs.

#### Shark postflop exploit HandPlan (2026-03-21)

- **Always on** when `BOT_Shark` is seated—documentation, not a hidden flag.
- After flop plan (VALUE/BLUFF/POT_CONTROL/GIVE_UP, barrels), coarse scripts from **`PlayerStats` + LearningLab** adjust lines and barrels.

#### LLM NPC (`BOT_LLM`, Volcengine Ark / Doubao)

- Routed in **`DpRoomServiceImpl`** via **`DpLlmNpcDecisionService`**; **`POST /dpRoom/addLlmBot?roomId=...`** to queue seat.
- Code: `npc/LlmNpc.java`, `DpLlmNpcDecisionService.java`, `DpNpcEngine.buildLlmNpcGameSnapshot`.
- **Config** (file preferred): `dp.llm.ark.*` in **`application.properties`**, or env `ARK_API_KEY`, `ARK_ENDPOINT_ID`, etc. **Never commit real keys.**
- **Latency**: Ark docs: ① `thinking.type` (`enabled` / `disabled` / `auto`)—deep thinking / chain-of-thought; many endpoints default to `enabled` if omitted (slower). ② `reasoning_effort` (`minimal`–`high`). **Note:** some `reasoning_effort` + `thinking.type=disabled` pairs return HTTP 400 (e.g. `low + disabled`). `LlmNpc` omits `reasoning_effort` when `disabled` to avoid that; use `enabled`/`auto` or drop `thinking-type` to tune reasoning.
  - **Windows:** System → About → Advanced system settings → Environment variables.
  - **Current PowerShell session:** `$env:ARK_API_KEY="..."`, `$env:ARK_ENDPOINT_ID="ep-..."`.
  - **IntelliJ:** Run → Edit Configurations → your Spring Boot → Environment variables.
- **Context**: `DpUtilSmartContext` → `LlmNpcGameContext`.

### Hand history & users (2026-03-30)

- **Design**: [docs/DP_PERSISTENCE_README.md](docs/DP_PERSISTENCE_README.md).
- **Table**: `dp_observed_hand_participant.sql` (many-to-many; **no FK** in DB). Run on DB that already has `dp_observed_hand_history`.
- **`user_id`**: Nullable; prefer in-memory `dpUserId`, else lookup by **nickname** in `dp_user`; else `nickname_snapshot`. Bots don’t get rows.
- **Front**: `loginProfile` / `registerUser` return `ResultUtil`; store `userId` in `localStorage`; optional `userId` on create/join/next-hand when consistent with `dp_user`.
- **JWT (2026-04-07)**: `SecurityConfig` + `JwtAuthenticationFilter`; whitelist in [docs/JWT.md](docs/JWT.md). Axios interceptors in `main.js`.
- **Passwords (2026-05-25 doc alignment)**: **`CryptoUtil.bcryptEncode` / `bcryptMatches`** in `DpUserServiceImpl` — see [docs/DpUserPassword.md](docs/DpUserPassword.md), [docs/JWT.md](docs/JWT.md).
- **Tokens**: `JwtTokenService`, HMAC-SHA256.
- **Join**: `POST /dpRoom/joinRoom2` checks nickname vs `SecurityContext`; `ensureDpUserIdInStorage` may call `loginProfile` for token.
- **History APIs**: List/detail by **`userId`** only; see DTO rules in code.
- **Hand history UI (2026-03-30)**: `HandHistoryDetail.vue` styling; zebra tables, felt zone.
- **Net chips fix (2026-03-30)**: Baseline **before blinds** for per-hand P&L.
- **Main pot stat (2026-04-01)**: `mainPotBeforeSettlement` uses **eligible** multi-way pots only.

### Music BGM (2026-04-01)

- **Table**: `dp_music_track` from Flyway `src/main/resources/db/migration/V1__init_schema.sql` in `school_db`.
- **Path**: Default `P:/javaworkspace/DPGameFiles/music/` (`mgdemoplus.music.file-location`); HTTP `/music/**`; Docker: `MGDEMOPLUS_MUSIC_FILE_LOCATION`.
- **APIs**: `POST /dpMusic/upload`; `GET /dpMusic/list`.
- **Front**: `/#/music-upload` after login.
- **In-game jukebox**: Top bar opens the track list; **play/pause/stop** broadcast on the **same room WebSocket** after the server checks the sender is a player or spectator; last state is replayed to new connections after the first room snapshot. Showdown/settlement pauses library BGM to avoid clashing with settlement stinger; resumes after leaving settlement if still `play`. Client uplink example: `{"_ws":"roomMusicSync","nickname":"…","action":"play|pause|stop","trackId":…,"webPath":"/music/…","displayName":"…"}` (`play` requires `trackId`/`webPath`; path must be a safe filename under `/music/`).

### Redis

- **Dependency**: `spring-boot-starter-data-redis` (**Lettuce**); `spring.data.redis.*` in **`application.properties`**.
- **Env**: `SPRING_DATA_REDIS_*`; Compose Redis at `redis:6379`, host **`localhost:6380`** ([docs/DOCKER.md](docs/DOCKER.md)).
- **Usage**: `StringRedisTemplate`, etc.
- **Lab**: `RedisLabController` **`/demo/redis/*`** (JWT whitelisted); key prefix `mgdemo:lab:`.
- **Music cache**: `DpRedisListCacheService` for `GET /dpMusic/list`; lobby rooms still from memory `roomMap`.
- **Note**: Table WS and rooms are **single-node memory**; multi-instance needs custom Redis design—[docs/WEBSOCKET.md](docs/WEBSOCKET.md).

### Docker

- **Full server deploy (same as local dev)**: **`git clone`** the **entire** repo (includes `docker-compose.yml`), then `docker compose up -d --build`—**Docker Hub cannot replace Git** for source ([docs/DOCKER.md](docs/DOCKER.md)).
- **Images only (no clone)**: Use **`docker-compose.hub.yml`** with pre-pushed **`dpgame`**, **`dpgame-nginx`**; MySQL is official **`mysql:8.0`**, schema from app **Flyway** ([docs/DOCKER.md](docs/DOCKER.md) “image-only deploy”); on server: `docker compose -f docker-compose.hub.yml pull` and `up -d` (no `--build`).
- **Docker Hub cheat sheet**
  - **When to push**: After changing anything that goes into the image (root **`Dockerfile`**, **`Dockerfile.nginx`**, app/front code, `docker/nginx`, **`src/main/resources/db/migration/`** Flyway scripts, etc.) and you want another machine to **pull only**—rebuild and push **`dpgame`** + **`dpgame-nginx`** with the **same tag**.
  - **How**: `docker login`; on Windows from repo root **`.\build-push-hub.ps1`** (optional `-Tag v1.0.1`); Linux/macOS: see **`docker build` / `docker push`** in [docs/DOCKER.md](docs/DOCKER.md) for `REG`/`TAG`. **`IMAGE_TAG`** in deploy `.env` must match the pushed tag.
  - **When to use `docker-compose.hub.yml`**: Target has Docker + Compose only, **no** `git clone`—place the YAML anywhere (copy or download), optional **`.env`** for `DOCKER_REGISTRY`, `IMAGE_TAG`, `MYSQL_ROOT_PASSWORD`, `REDIS_PASSWORD`, then **`pull` + `up -d`**. Compose uses **`image:`** only, **no** **`build:`**—no source tree needed.
  - **vs default `docker-compose.yml`**: Default uses **`build:`** at repo root, mounts `./docker-data/uploads`; Hub flow bakes Nginx + app (DDL packaged for **Flyway**); MySQL is stock `mysql:8.0`.
- **More**: Git vs packaging, `WebConfig`, drivers, prod front URL, DBeaver—[docs/DOCKER.md](docs/DOCKER.md). **Nginx** (port 80, WS proxy): [docs/NGINX.md](docs/NGINX.md).
- **One-shot (MySQL + Redis + app + Nginx)**: From repo root `docker compose up --build`; open **`http://localhost`** (via Nginx) or **`http://localhost:8088`** (direct app; hash routes, e.g. `/#/login`). Default MySQL root password `mgdemo_root` (override `MYSQL_ROOT_PASSWORD`). Host MySQL **`localhost:3307`** (avoids 3306 conflict); app uses `mysql:3306` inside Docker network. **Redis** service `redis`, volume `redis_data`, default password **`mgdemo_redis`** (override **`REDIS_PASSWORD`**, must match `SPRING_DATA_REDIS_PASSWORD`). Host Redis **`localhost:6380`** (mapped from container 6379). Single `docker run` app container needs Redis or `SPRING_DATA_REDIS_*` yourself.
- **Backend image only**: `docker build -t mgdemoplus .`, example run:
  `docker run -p 8088:8088 -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/school_db?useSSL=false -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=<your-password> -e MGDEMOPLUS_IMAGES_FILE_LOCATION=file:/data/mgdemo-files/ -v <host-dir>:/data/mgdemo-files mgdemoplus`
- **Notes**: `Dockerfile` builds `front/dp_game` into the JAR `static` alongside API/WS; dev default uploads `P:/javaworkspace/DPGameFiles/` (`mgdemoplus.images.file-location`); `/images/**` in container defaults `/data/mgdemo-files` (compose maps `./docker-data/uploads`). **DB**: named volume stores data only; **`school_db` DDL/DML migrations run via Flyway** at startup (`classpath:db/migration`). If **`mysql_data` was populated by the old Docker init**, Flyway **V1** may fail on duplicate objects—in dev use `docker compose down -v` (**wipes DB**); production needs backup/migration planning. **Do not** bake real secrets into `application.properties` in images—inject LLM keys via `ARK_API_KEY`, `ARK_ENDPOINT_ID`, etc. in compose.
