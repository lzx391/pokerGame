# 前端交互 · 悬停浮起 + 弹层过渡（实施日志）

> **Agent**：前端交互 Agent  
> **日期**：2026-05-22  
> **计划**：`docs/refactor/frontend-fluidity-interaction-plan.md`

---

## 变更文件列表

| 路径 | 操作 |
|---|---|
| `front/dp_game/src/styles/dp-interactive-hover.css` | **新建**：PC 悬停浮起 + eco 弱化 + 触屏 `:active` |
| `front/dp_game/src/styles/dp-motion-tokens.css` | 修改：eco 覆盖 `transition-duration`；eco 下 overlay/sheet 无位移 |
| `front/dp_game/src/styles/dp-game-element-ui.css` | 修改：`dialog-fade` / `el-drawer-fade` / `v-modal` 标准档过渡 |
| `front/dp_game/src/styles/dp-game-modals.css` | 修改：`dp-overlay` / `dp-sheet` / `dp-overlay-none` |
| `front/dp_game/src/main.js` | 修改：引入 `dp-interactive-hover.css`、`dp-game-modals.css`（全局） |
| `front/dp_game/src/App.vue` | 修改：`nav-link` 悬停形变交由全局 CSS |
| `front/dp_game/src/components/GamePlayGuideModal.vue` | 修改：`<transition name="dp-overlay">` |
| `front/dp_game/src/components/GameHandRankModal.vue` | 同上 |
| `front/dp_game/src/components/GameHandHistoryModal.vue` | 同上 |
| `front/dp_game/src/components/GameMusicBoxModal.vue` | 同上 |
| `front/dp_game/src/components/GameSpectatorModal.vue` | 同上 |
| `front/dp_game/src/components/GameWaitNextHandModal.vue` | 同上 |
| `front/dp_game/src/components/GameOwnerToolModal.vue` | 修改：`dp-overlay` / `dp-overlay-none`（embedded） |
| `front/dp_game/src/components/GameDpGameSheets.vue` | 修改：三处 `<transition name="dp-sheet">` |
| `docs/refactor/frontend-fluidity-interaction-plan.md` | **新建** |
| `docs/refactor/frontend-fluidity-interaction-log.md` | **新建**（本文件） |

**未改**：后端、扑克发牌/翻牌 CSS、`card-base` hover、git 仓库提交。

---

## 验证：`npm run build`

```text
目录：front/dp_game
命令：npm run build
结果：exit 0 — DONE Build complete
时间：2026-05-22T08:48:30Z（约 40s）
Hash：2b8fa90739b1c634
```

---

## 实现要点摘要

1. **悬停**：仅 `@media (hover: hover) and (pointer: fine)` + `body[data-dp-fluidity='standard']`；`transform` + 单层 `box-shadow`；`will-change: transform`；无 `filter` 动画。
2. **弹层**：标准档 0.3s；面板 `scale(0.96)` + `translateY(8px)`（dialog/overlay）或 `translateY(100%)`（底栏 sheet）；遮罩 opacity 同步。
3. **节能档**：dialog token 0.08s；悬停无 translate/scale；overlay/sheet 进入态 `transform: none`。
4. **`:root`** 保持 `--dp-motion-duration-dialog: 0.3s`（修正「标准档勿 0.08s 闪屏」）。

---

## PC 自测勾选

- [ ] `/home` 主按钮、ghost 按钮 hover 浮起
- [ ] `/home` 打开邮箱 dialog：淡入，关闭淡出
- [ ] `/game` 底栏 fold/call/raise 等按钮 hover（PC）
- [ ] 节能档开启后：dialog 可更快，hover 可减弱（符合 eco）

*请在桌面 Chrome 标准档 / 节能档各测一轮后勾选。*

---

*禁止 git commit/push（任务约束）。*
