# 前端路由转场方案（阶段 2.1 · 仅方案）

> **角色**：MGDemoPlus 前端「路由转场」方案 Agent  
> **日期**：2026-05-22  
> **输入**：`frontend-fluidity-plan.md`、`frontend-fluidity-audit-shell.md` §3、`App.vue`、`router/index.js`、`dpBodyFluidity.js`、`dpGameEcoMode.js`、`home.vue` 快匹链路  
> **约束**：**禁止** git commit / push；**不改后端**；**不改业务源码**（本文档为交付物）；**不新增 npm 依赖**（除非另批）  
> **产品口径（已锁定）**：仅 **标准档** `body[data-dp-fluidity='standard']` 启用路由转场；**节能档** 瞬时切换；优先场景为快匹 MATCHED、大厅进房、`room` 页进对局；浏览器标签标题 **`poker_demo` 不改**（`vue.config.js` `pages.index.title`）

---

## 0. 与总方案的关系

| 文档 | 关系 |
|---|---|
| `frontend-fluidity-plan.md` | 全站双档母方案；本文 **修订** 其 §3.1 / §3.3 中「禁止路由级 transition」条款（见 §10） |
| `frontend-fluidity-audit-shell.md` §3 | 根因与跳转链证据；本文落地 §3.4 P1「壳层切换是否动画」 |
| 本文 | 路由转场 **专册**；实现由后续开发 Agent 按 P0/P1 执行 |

---

## 1. 问题与根因

### 1.1 用户可见现象

| 现象 | 典型路径 | 用户描述 |
|---|---|---|
| **短空白 / 闪灰条** | 首次进入某懒加载页、大厅 → 对局 | 点进房间后「白一下」「底下露出一条灰的」 |
| **壳层突变感** | `/home` → `/game/:id` | 布局从大厅 flex 链突然变成对局全屏链，无缓冲 |
| **快匹 MATCHED 突兀** | WS `MATCHED` 或 HTTP 同步 `roomId` → `push('/game/…')` | 匹配成功瞬间「啪」一下切屏 |

### 1.2 技术根因（代码证据）

1. **无路由级 `<transition>`**  
   `App.vue` 两处均为裸 `<router-view>`，**无** `<keep-alive>`（`audit-shell` §3.1）。换路由 = 旧组件 **整树销毁** + 新 chunk 组件 **重建**。

2. **懒加载 chunk 空窗**  
   `router/index.js` 全部路由为 `() => import(/* webpackChunkName: "route-*" */ …)`。首次进入子页需拉 JS；旧页已卸载而新页 `created` 未跑完时，视口内无业务 DOM。

3. **默认灰底露底**  
   `#app` / `body` 仍硬编码 `#f5f7fa`（`App.vue` L114–123；`fluidity-plan` P1-12 拟 token 化）。`#app.app--lobby` 为 `transparent` 时依赖 `body[data-dp-game-theme]`；chunk 间隙或未挂 `app--lobby` 时易闪 **Element 默认灰**。

4. **壳类与主题同步同一 tick、无过渡**  
   `main.js` `router.afterEach` 仅 `syncDpBodyGameTheme` + `syncDpBodyFluidity`，与组件 mount 同帧（`audit-shell` §3.1）。`app--lobby` ↔ `app--dp-game` 与 `router-view` 内容切换 **无** 视觉插值。

5. **快匹 push 无预加载**  
   `home.vue`：`handleQuickMatchWsMessage` 在 `MATCHED` 后 **立即** `this.$router.push('/game/' + data.roomId)`（约 L1097–1101）；HTTP 同步命中 `data.roomId` 亦立即 push（约 L1145–1147）。**未** 等待 `route-game` chunk。

6. **进对局多入口、行为一致**  
   - 快匹：`/game/:id`（优先）  
   - 大厅：`joinRoom2` → `/room/:id` → `fetchRoomInfo` 若 `playing` → `/game/:id`（`room.vue` L152–153）  
   - 房主：`startGame` → `/game/:id`（`room.vue` L121）  
   - 好友跟房 / 邮箱邀请：直接 `/game/:id`（`home.vue` L802、L846）

### 1.3 根因小结

> **露底 = chunk 卸载空窗 + 默认灰底 + 无路由过渡遮罩**；**突兀 = 重壳切换无插值 + 未 prefetch 对局 chunk**。

路由转场 **不能单独** 消灭 chunk 下载延迟，必须与 **prefetch / 静态壳占位**（`fluidity-plan` P2-2）组合；转场动画的作用是 **遮住空窗、降低壳层突变感**（标准档、GPU 友好）。

---

## 2. 技术选型对比

| 方案 | 做法 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| **A. `<transition>` 包裹 `router-view`** | `App.vue` 按 `meta.transition` 或路径选 `name`；CSS 仅 `opacity`/`transform` | 与 Vue Router 原生契合；可区分 enter-game / leave-game；eco 用 `transition: none` 一行关掉 | 旧组件 leave + 新组件 enter 并行时仍可能 **中间帧无内容**（若新 chunk 未就绪） | **推荐主方案**（标准档） |
| **B. 全屏 overlay（假 loading）** | `afterEach` 前显示 `#app` 级主题色遮罩，chunk `import()` resolve 后隐藏 | 对 chunk 延迟 **最稳**；不依赖双组件同时 mount | 多一层 DOM/状态机；易与真实 loading 重复；需严格 **eco 禁用动画** | **辅助**：作 P1「chunk 未命中」兜底，或嵌在 A 的 enter 阶段 |
| **C. Webpack prefetch `route-game`** | `import(/* webpackPrefetch: true */ …)` 或 `home` mounted / 快匹 WAITING 时手动 `import()` | **直接缩短** 空窗；无额外视觉层；零 npm | 浪费带宽（用户不进房）；需与 **hover 进房** 策略权衡 | **推荐必做**（P0 与 A 并联）；不替代 A |
| **D. `<keep-alive>` 缓存大厅** | 缓存 `home`/`room` 减少重建 | 返回大厅快 | 内存与 WS/轮询生命周期复杂；**不解决** 首次进 `game` chunk | **本期不做** |
| **E. 全局 `router.beforeEach` 阻塞至 chunk 就绪** | `await import(game)` 再 `next()` | 无闪屏 | 阻塞导航、需改多处 push；与快匹 WS 时序耦合 | **仅** 用于 MATCHED/同步 roomId 的 **可选** `await prefetchGameChunk()`，不全局阻塞 |

**推荐组合（标准档）**：**C（prefetch）+ A（短 crossfade）+ 可选 B（仅当 prefetch 未命中且超过 80ms）**  
**节能档**：**无 A/B 动画**；允许 C（prefetch 不属动效，可保留以减等待，或 eco 也 prefetch——与「少动效」不冲突）。

---

## 3. 推荐方案

### 3.1 原则

1. **档位**：仅 `body[data-dp-fluidity='standard']` 应用路由 transition CSS；`eco` **显式** `transition: none !important`（与 `dp-motion-tokens.css` 壳层规则一致）。
2. **动效类型**：仅 **`opacity` + 可选 `transform: translateY(4px)`**，禁止 `filter`/`backdrop-filter`/多 keyframes。
3. **时长**：引用 token，建议新增：
   - `--dp-motion-duration-route-min: 0.12s`（最短，避免「闪一下」）
   - `--dp-motion-duration-route-max: 0.38s`（最长 enter；leave 可取 `0.22s`）
   - eco：`--dp-motion-duration-route: 0s`
4. **webpack prefetch `route-game`**：**建议启用**（P0），不新增 npm，仅 magic comment + 一处工具函数。
5. **标题**：不修改 `vue.config.js` 的 `title: 'poker_demo'`；路由转场 **不改** `document.title`。
6. **Feature flag**（回滚）：`localStorage.dp_route_transition`（见 §9），默认 `'1'` 开启（仅影响标准档是否挂载 transition 类）。

### 3.2 `App.vue` 结构（实现指引，非本阶段改码）

```html
<!-- 非 auth 分支 full-page 内 -->
<router-view v-slot="{ Component, route }" v-if="useRouterViewSlot">
  <!-- Vue 2.6+ 若未升级，退化为单 router-view + :key="route.path" + 外层 transition -->
</router-view>
```

**Vue 2.7 / 2.6 兼容**：项目为 Vue 2，若无法用 `v-slot`，采用：

```html
<transition :name="routeTransitionName" mode="out-in">
  <router-view :key="$route.fullPath" />
</transition>
```

- `mode="out-in"`：避免 lobby/game 双壳 **同时** 叠在 DOM（减轻露底）；代价是 leave 完成后再 enter，总时长 ≤ min+max，需 **控制 leave ≤220ms**。
- `routeTransitionName`：由 `computed` 读 `$route.meta.transition` 或路径矩阵（§4）；eco / flag off 时返回 `'dp-route-none'`（CSS 设 `transition: none`）。

### 3.3 Prefetch 策略

| 时机 | 动作 | 优先级 |
|---|---|---|
| 用户进入 `/home` 且已登录 | `prefetchGameChunk()`（动态 `import()` 同 `route-game`） | P0 |
| 快匹 `WAITING`（`quickMatchPolling === true`） | 同上 | P0 |
| 鼠标 hover 房间列表「进入」按钮 ≥200ms | 同上 | P1 |
| `room.vue` `created`（已在等待页） | 同上 | P0 |

**router 注释（实现时）**：

```js
component: () =>
  import(
    /* webpackChunkName: "route-game" */
    /* webpackPrefetch: true */
    '@/components/game.vue'
  )
```

**是否必须 webpackPrefetch**：**建议双轨**——magic comment（构建时插入 prefetch link）+ 运行时 `prefetchGameChunk()`（确保快匹 MATCHED 前 chunk 已在缓存）。仅 comment 不足以覆盖「冷启动直接快匹」。

### 3.4 快匹 / 进房时序（§6 详表）

**标准档 + flag on**：

```
MATCHED/roomId 已知
  → prefetchGameChunk()（await，超时 1.2s 仍 push）
  → router.push('/game/:id')
  → router-view enter 动画（0.12–0.38s）
```

**节能档**：无 enter 动画；仍 **建议** `await prefetchGameChunk()`（减空白，非「动效」）。

---

## 4. 路由矩阵（path → transition name）

`transition name` 对应 CSS 类前缀：`.dp-route-enter-game-enter-active` 等。

| 源路径（模式） | 目标路径（模式） | `meta.transition` / name | 标准档 | 节能档 | 备注 |
|---|---|---|---|---|---|
| `/home`、`/room/:id`、`/create-room`、邀请/跟房 | `/game/:id` | **`dp-route-enter-game`** | ✅ | 瞬时 | **P0 优先** |
| `/game/:id` | `/home` | **`dp-route-leave-game`** | ✅ 短淡出 | 瞬时 | 退出对局 |
| `/home` | `/room/:id` | `dp-route-lobby` | ✅ 极短（≤0.15s）或 none | 瞬时 | 二次跳转，弱于进对局 |
| `/room/:id` | `/game/:id` | **`dp-route-enter-game`** | ✅ | 瞬时 | 与大厅进房同级 |
| `/home` ↔ `/leaderboard`、`/hand-history`、`/music-upload` | 同壳 lobby | `dp-route-lobby` 或 **none** | 可选极短 | 瞬时 | 冷 chunk 为主因，prefetch 对应 chunk 即可 |
| `/login`、`/register` | `/home` | **none** | ❌ | 瞬时 | 首屏已载 auth chunk |
| `/game/:id` | `/guide` | none | ❌ | 瞬时 | 同属 game 壳，避免双过渡 |
| 任意 | 任意（eco） | **`dp-route-none`** | — | **强制** | `body[data-dp-fluidity='eco']` 覆盖 |
| flag off | 任意 | **`dp-route-none`** | 瞬时 | 瞬时 | 回滚 |

**实现建议**：在 `router/index.js` 为路由增加 `meta`：

```js
{ path: '/game/:roomId', meta: { transition: 'dp-route-enter-game' }, ... }
```

`App.vue` / 小工具 `resolveRouteTransitionName(to, from, store)` 处理 **成对** 方向（例如 `from` 为 `/game` 且 `to` 为 `/home` → `dp-route-leave-game`）。

---

## 5. CSS 选择器与 token

### 5.1 作用域（硬约束）

```css
/* 新建：front/dp_game/src/styles/dp-route-transition.css */

/* 仅标准档 */
body[data-dp-fluidity='standard'] .dp-route-enter-game-enter-active,
body[data-dp-fluidity='standard'] .dp-route-enter-game-leave-active {
  transition: opacity var(--dp-motion-duration-route-max, 0.38s) ease,
              transform var(--dp-motion-duration-route-max, 0.38s) ease;
  will-change: opacity;
}

body[data-dp-fluidity='standard'] .dp-route-enter-game-enter {
  opacity: 0;
  transform: translateY(4px);
}
body[data-dp-fluidity='standard'] .dp-route-enter-game-enter-to {
  opacity: 1;
  transform: translateY(0);
}

/* 节能：显式关闭（含 PRM 联合） */
body[data-dp-fluidity='eco'] .dp-route-enter-game-enter-active,
body[data-dp-fluidity='eco'] .dp-route-enter-game-leave-active,
body[data-dp-fluidity='eco'] .dp-route-leave-game-enter-active,
body[data-dp-fluidity='eco'] .dp-route-leave-game-leave-active,
body[data-dp-fluidity='eco'] .dp-route-lobby-enter-active,
body[data-dp-fluidity='eco'] .dp-route-lobby-leave-active,
body[data-dp-route-transition='off'] .dp-route-enter-game-enter-active,
body[data-dp-route-transition='off'] .dp-route-leave-game-enter-active {
  transition: none !important;
  animation: none !important;
}

@media (prefers-reduced-motion: reduce) {
  .dp-route-enter-game-enter-active,
  .dp-route-leave-game-leave-active {
    transition-duration: 0.08s !important; /* 缩短，不替代 eco 瞬时 */
  }
}
```

### 5.2 与 `#app` 壳类协同

- enter-game 期间保持 **`body[data-dp-game-theme]`** 已切换（`afterEach` 现状）；过渡层建议挂在 **`#app > .full-page`** 内 `router-view` 子树，避免 auth 分支误动画。
- **禁止** 路由 transition 使用 `backdrop-filter` / `box-shadow` 动画。

### 5.3 `dp-motion-tokens.css` 增补（实现项）

```css
:root {
  --dp-motion-duration-route-min: 0.12s;
  --dp-motion-duration-route-max: 0.38s;
  --dp-motion-duration-route-leave: 0.22s;
}
body[data-dp-fluidity='eco'] {
  --dp-motion-duration-route-max: 0s;
  --dp-motion-duration-route-leave: 0s;
}
```

`main.js` 在 `dp-motion-tokens.css` 之后引入 `dp-route-transition.css`。

---

## 6. 与快匹时序

### 6.1 现状（`home.vue`）

| 通道 | 触发 | 当前行为 |
|---|---|---|
| WebSocket | `data.state === 'MATCHED' && data.roomId` | `disconnectQuickMatchWs()` → **立即** `push('/game/' + roomId)` |
| HTTP `quickMatch2` | 响应含 `data.roomId`（同步匹配成功） | **立即** `push` |
| HTTP | `queued && WAITING` | 保持 WS，**无** push |

### 6.2 推荐时序

| 档位 | WS MATCHED | HTTP 带 `roomId` |
|---|---|---|
| **标准** | ① `prefetchGameChunk()`（await，max 1.2s）② `push` ③ CSS enter | 同左 |
| **节能** | ① prefetch（建议保留）② `push`（无 transition） | 同左 |

**WAITING 阶段（P0）**：在 `quickMatchPolling = true` 后首次 tick 调用 `prefetchGameChunk()`，使 MATCHED 时 chunk **大概率已缓存**。

**勿** 在标准档用 >400ms 人工 delay 代替 prefetch（影响匹配体感）。

### 6.3 `room.vue` 对齐

- `fetchRoomInfo` 发现 `room.playing` → push game：标准档同样 **先 prefetch 再 push**（与快匹一致）。
- `start()` 房主开局：同上。

### 6.4 工具函数（建议新建）

`front/dp_game/src/utils/dpPrefetchGameRoute.js`：

```js
var gameChunkPromise = null
export function prefetchGameChunk() {
  if (!gameChunkPromise) {
    gameChunkPromise = import(
      /* webpackChunkName: "route-game" */
      /* webpackPrefetch: true */
      '@/components/game.vue'
    )
  }
  return gameChunkPromise
}
```

---

## 7. P0 / P1 任务表

| ID | 任务 | 文件路径 | 依赖 |
|---|---|---|---|
| **RT-P0-1** | `router/index.js`：`route-game` 增加 `webpackPrefetch`；路由 `meta.transition` | `front/dp_game/src/router/index.js` | — |
| **RT-P0-2** | 新建 `dpPrefetchGameRoute.js`；`home` mounted + 快匹 WAITING + `room` created 调用 | `utils/dpPrefetchGameRoute.js`；`home.vue`；`room.vue` | RT-P0-1 |
| **RT-P0-3** | MATCHED / 同步 roomId / `room.playing` / `startGame`：**await prefetch 再 push** | `home.vue`；`room.vue` | RT-P0-2 |
| **RT-P0-4** | `App.vue`：`transition` + `:key` + `routeTransitionName`；eco/flag 返回 `dp-route-none` | `front/dp_game/src/App.vue` | RT-P0-5 |
| **RT-P0-5** | 新建 `dp-route-transition.css`；`main.js` 引入；token 增补 | `styles/dp-route-transition.css`；`styles/dp-motion-tokens.css`；`main.js` | — |
| **RT-P0-6** | `resolveRouteTransitionName`（from/to/meta + fluidity + flag） | **新建** `utils/dpRouteTransition.js`；`App.vue` | RT-P0-4 |
| **RT-P0-7** | Feature flag 读写 + `body[data-dp-route-transition]` 同步 | **新建** `utils/dpRouteTransitionFlag.js`；`main.js` afterEach | RT-P0-4 |
| **RT-P1-1** | chunk 未命中 >80ms 时 **静态主题壳**（`--dp-game-bg` 铺满，无 spin） | `App.vue` 或 `dp-lobby-shell.css`；衔接 `fluidity-plan` P2-2 | RT-P0-2 |
| **RT-P1-2** | 房间列表 hover prefetch | `home.vue` | RT-P0-2 |
| **RT-P1-3** | 大厅子页 chunk prefetch（leaderboard/hand-history） | `router/index.js` 或 home 导航 hover | 可选 |
| **RT-P1-4** | `#app`/`body` 默认底 token 化（减露 `#f5f7fa`） | `App.vue`；`dp-depth-tokens.css` | `fluidity-plan` P1-12 |
| **RT-P1-5** | 验收录屏 + DevTools Network 验证 prefetch | 文档 §8 | RT-P0-* |

---

## 8. 验收表

> 设备：**Android 中低端 Chrome**（两档各测）、桌面 Chrome（标准档）。  
> 主题：至少 `default` 一套。标签标题仍为 **poker_demo**。

### 8.1 标准档（`body[data-dp-fluidity='standard']`，flag on）

| ID | 步骤 | 通过标准 |
|---|---|---|
| S1 | 清缓存 → 登录 → `/home` 停留 2s → 快匹至 MATCHED | 进 `/game/:id` **无** 持续 >300ms 纯 `#f5f7fa` 全屏；有 **轻微** 淡入（≤380ms） |
| S2 | 大厅点房 → `/room/:id` → 房主开局或 `playing` 自动进局 | 进对局有过渡；**无** 双屏叠影 >1 帧 |
| S3 | 好友跟房 / 邮箱邀请直达 `/game` | 同 S1 级别，不明显「啪」切 |
| S4 | 对局退出 → `/home` | leave 淡出 ≤220ms，回大厅无卡死 |
| S5 | Network：MATCHED 前已有 `route-game.*.js`（prefetch 或缓存） | prefetch 命中；push 后 FMP 主观 ≤3s（与 `fluidity-plan` A2 一致） |

### 8.2 节能档（`body[data-dp-fluidity='eco']`）

| ID | 步骤 | 通过标准 |
|---|---|---|
| E1 | 大厅手动开 eco → 快匹 MATCHED | **瞬时** 切换，**无** route opacity/transform 动画 |
| E2 | eco 下大厅 → `/room` → `/game` | 同 E1 |
| E3 | Elements 检查 enter-active | `transition: none` 生效 |
| E4 | eco 下 `/home` ↔ `/leaderboard` | 无路由 fade；允许 prefetch 缩短空白 |
| E5 | eco 刷新后再进房 | 仍为 eco；行为与 E1 一致（storage 策略 B） |

---

## 9. 风险与回滚

| 风险 | 影响 | 缓解 |
|---|---|---|
| `mode="out-in"` 拉长导航 | 快匹体感变慢 | leave ≤220ms；prefetch 减 enter 等待 |
| 双 transition 与弹层 transition 叠加 | 卡顿 | 路由仅用 opacity；弹层仍走 `dp-overlay` |
| prefetch 浪费流量 | 未进房用户多下 chunk | 仅 home/WAITING/room；hover 为 P1 |
| Vue 2 `router-view` + `transition` 边界 bug | 白屏 | `:key="$route.fullPath"`；auth 分支不加 transition |
| Android 掉帧 | GPU 压力 | 禁 blur；标准档亦禁止长动画 |

### 9.1 一键关闭（Feature flag）

| 项 | 建议 |
|---|---|
| 存储键 | `localStorage.dp_route_transition`：`''`/`'1'`=开（默认开），`'0'`=关 |
| DOM | `body[data-dp-route-transition='off']` → 强制 `dp-route-none` |
| 入口 | 仅开发/设置隐藏开关（**不**改 `poker_demo` 标题）；生产问题支持台本：用户执行 `localStorage.setItem('dp_route_transition','0');location.reload()` |
| 回滚发布 | 关 flag 或 revert `App.vue` + `dp-route-transition.css` 三文件 |

**不新增 npm**；prefetch 为 webpack 内置能力。

---

## 10. 修订 `frontend-fluidity-plan.md` 的条款编号

以下条款在 **路由转场实施完成后** 以本文为准 **修订**（开发 Agent 改 `frontend-fluidity-plan.md` 时对照）：

| 原位置 | 原条文摘要 | 修订后 |
|---|---|---|
| **§3.1** 表格「路由壳」行（约 L94） | 节能禁止：`<transition>` 路由切换；keep-alive 入场 | **保留** eco 禁止；补充「标准档允许受控 `router-view` transition，见 `frontend-route-transition-plan.md`」 |
| **§3.3** 表格「路由级 transition」行（约 L120） | 标准档 **明确禁止** 路由级 transition | **删除该行** 或改为：「标准档允许 GPU 友好路由淡入淡出（仅 `opacity`/`translateY`，≤0.38s）；细则见 `frontend-route-transition-plan.md`」 |
| **§3.2** 壳层允许项（约 L108） | Element 短过渡 | 增补：「标准档路由 enter-game / leave-game」 |
| **§4.2 P2-2** | 路由 chunk 静态壳占位 | 与本文 RT-P1-1 **合并实施**，不重复造轮子 |
| **`audit-shell` §3.4 P1** | 双档需定义壳层切换是否动画 | **已定义**：eco 无动画；standard 有（本文 §4–§5） |

**验收表 `fluidity-plan` A2**：标准档「壳切换无露 `#f5f7fa` 条」— 路由转场 + prefetch + P1-12 token 化 **共同满足**；节能档仍为瞬时切换、无 route transition。

---

## 11. 证据索引

| 主题 | 路径 |
|---|---|
| 路由表 / chunk 名 | `front/dp_game/src/router/index.js` |
| `router-view` | `front/dp_game/src/App.vue` |
| 双档 body | `front/dp_game/src/utils/dpBodyFluidity.js` |
| eco 存储 | `front/dp_game/src/utils/dpGameEcoMode.js` |
| 快匹 push | `front/dp_game/src/components/home.vue`（`handleQuickMatchWsMessage`、`quickMatch`） |
| 等待页进局 | `front/dp_game/src/components/room.vue` |
| 标题 | `front/dp_game/vue.config.js` → `poker_demo` |
| 动效 token | `front/dp_game/src/styles/dp-motion-tokens.css` |
| 壳层审计 | `docs/refactor/frontend-fluidity-audit-shell.md` §3 |
| 母方案 | `docs/refactor/frontend-fluidity-plan.md` |

---

## 12. 交接汇总

| 项 | 值 |
|---|---|
| **交付文件** | `docs/refactor/frontend-route-transition-plan.md` |
| **P0 任务** | RT-P0-1～RT-P0-7（7 项） |
| **P1 任务** | RT-P1-1～RT-P1-5（5 项） |
| **推荐主技术** | prefetch `route-game` + 标准档 `router-view` transition（`opacity`/`translateY`） |
| **时长** | min **0.12s**，max **0.38s**，leave **0.22s** |
| **修订母方案** | §3.1 L94、§3.3 L120（及 §3.2 / P2-2 / audit-shell §3.4 P1 衔接） |
| **源码变更** | 本阶段 **未执行** |

---

*实现由后续开发 Agent 执行；须遵守仓库 `CLAUDE.md`（先确认再改代码）。*
