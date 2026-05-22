# 前端交互 · 悬停浮起 + 弹层过渡（实施计划）

> **Agent**：前端交互 Agent  
> **日期**：2026-05-22  
> **依据**：`docs/refactor/frontend-fluidity-plan.md` §3 双档、`frontend-fluidity-audit-shell.md`  
> **约束**：仅 PC `@media (hover: hover) and (pointer: fine)`；Android 策略 B；不改后端；禁止 git commit/push；扑克发牌/翻牌不在范围

---

## 1. 目标

| 项 | 标准档 `body[data-dp-fluidity='standard']` | 节能档 `body[data-dp-fluidity='eco']` |
|---|---|---|
| 可点击按钮 | 悬停 `translateY(-2px) scale(1.02)` + 单层阴影增强 | 关闭/弱化形变（保留颜色类 hover） |
| Element dialog/drawer | 0.3s 淡入 + 面板 scale/位移 | `--dp-motion-duration-dialog: 0.08s`，面板无位移 |
| 自定义 mask 弹窗 | `dp-overlay` 与 Element 同节奏 | 同上 |
| 对局底栏 sheet | `dp-sheet` 遮罩淡入 + 面板上滑 | 缩短/无位移 |

---

## 2. 悬停覆盖按钮类型

| 类型 | 选择器 / 位置 |
|---|---|
| 全站 `.dp-btn` | `dp-lobby-shell.css`、`dp-game-shell.css`（大厅/对局） |
| `button.dp-*` | 如 `button.dp-social-list__idline`（好友/私信列表） |
| Element 按钮 | `.el-button`（含 `--primary` / `--default` / `--text`、MessageBox 内按钮） |
| 登录/注册导航 | `#app .nav-link`（`App.vue`，浮起并入全局 token） |
| 对局顶栏 | `.dp-top-bar__btn`（`GameTopBar.vue`） |
| 行动面板 | `.dp-action-panel .dp-btn`、`button[type='button']`（`GameActionPanel.vue` / 底栏 sheet 内嵌） |
| 英雄区/移动条 | `.dp-game-mobile-hero-bar__btn`、`.dp-game-hero-action-row__owner-btn`（`GameHeroDockFooter.vue`） |
| 弹窗内控件 | `.dp-game-dialog__close`、`.dp-play-guide__tab`、`.dp-play-guide__ok` |
| 排行榜 | `.lb-page__tab`（`LeaderboardPage.vue`） |

**明确排除**：`.card-base` 扑克牌（`dp-poker-cards.css` 已有更强 hover，避免叠加强化）。

**实现文件**：`front/dp_game/src/styles/dp-interactive-hover.css`（`main.js` 在 `dp-motion-tokens.css` 之后引入）。

---

## 3. 弹窗 / 抽屉清单

### 3.1 Element UI（`dp-game-element-ui.css`）

| 组件 | 场景 | 过渡类名 |
|---|---|---|
| `el-dialog` | 大厅邮箱「消息」、资料、好友私信、猫咪教程等 | `dialog-fade-*` |
| `el-drawer` | 大厅好友列表侧栏 | `el-drawer-fade-*` + 方向 `rtl/ltr/ttb/btt` |
| `.v-modal` 遮罩 | dialog/drawer 共用背景 | `v-modal-enter` / `v-modal-leave` + `@keyframes dp-v-modal-in/out` |

### 3.2 自定义 mask（`dp-game-modals.css` + Vue `<transition name="dp-overlay">`）

| 组件 | 说明 |
|---|---|
| `GamePlayGuideModal` | 玩法说明（大厅/对局） |
| `GameHandRankModal` | 牌型参考 |
| `GameHandHistoryModal` | 历史对局（宽屏） |
| `GameMusicBoxModal` | 音乐盒 |
| `GameSpectatorModal` | 观众席 |
| `GameWaitNextHandModal` | 等待上桌名单 |
| `GameOwnerToolModal` | 房主神器（非 embedded；embedded 用 `dp-overlay-none`） |

### 3.3 底栏 sheet（`dp-sheet` + `GameDpGameSheets.vue`）

| 用途 | 组件 |
|---|---|
| 移动「我的手牌」 | `GameBottomSheet` |
| 移动「本轮行动」 | 内嵌 `GameActionPanel` |
| 房主操作 hub | 内嵌 `GameOwnerToolModal` embedded |

---

## 4. Token 与双档

- `:root`：`--dp-motion-duration-dialog: 0.3s`（标准档，**勿**改为 0.08s）
- `body[data-dp-fluidity='eco']`：仅在此处覆盖 dialog 为 `0.08s`；`dp-motion-tokens.css` 同时缩短 `dp-overlay` / `dp-sheet` 的 `transition-duration`
- 悬停变量：`--dp-hover-lift-y`、`--dp-hover-lift-scale`、`--dp-hover-shadow`（`dp-interactive-hover.css`）

---

## 5. 禁止项（本任务）

- 恢复 `card-base::after` infinite 扫光
- `backdrop-filter` 弹层模糊
- `filter` / `blur` 作为过渡属性
- 四层 `box-shadow` stack 悬停
- 改后端 / git commit / push

---

## 6. 验收（PC）

见 `frontend-fluidity-interaction-log.md` 末尾勾选表。
