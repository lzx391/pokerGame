# retro8bit 大厅邮箱 — 游戏机 Console（FC 菜单）

> 状态：**已实现（P0）**  
> 入口：大厅 `home.vue` 快捷入口「邮箱」  
> 门控：`gameUiTheme === 'retro8bit'`  
> 非目标：不改后端 API；不改对局页；本阶段不改「好友」drawer / 私信

---

## 1. PM 决策（已确认）

| # | 决策 |
|---|------|
| 1 | **仅 retro8bit** — 其它主题保留现有 `el-dialog` |
| 2 | 入口 label **MAILBOX**，desc **APPLY & INVITE** |
| 3 | FC 一级菜单 **APPLY / INVITE / CLOSE** → Enter 进入列表 |
| 4 | **打开邮箱即停闪**（即使仍有未读） |
| 5 | 列表正文（昵称、时间）中文 UI 栈；标题/按钮像素英文 |

---

## 2. 现状 UX 审计

### 2.1 入口与触点

| 触点 | 文件 | 行为 |
|------|------|------|
| 快捷卡片 | `home.vue` L133–153 | `el-badge` + `unreadCount`；`@click="openMailbox"` |
| 弹层 | `home.vue` L535–623 | `el-dialog` title「消息」 |
| 对局页 | 无 | 邮箱仅大厅 |

### 2.2 数据与未读

| 项 | 来源 |
|----|------|
| `unreadCount` | Vuex `dpMailbox`（申请 + 邀请聚合） |
| 打开 | `fetchMailbox` + `startMailboxTick` |
| 实时 | SSE `/dp/social/stream` |

API 不变：`GET /dp/mailbox`、`POST .../accept|reject`。

---

## 3. 概念设计

### 3.1 用户旅程（retro8bit）

1. **未打开 + 有未读** → 快捷卡片 step-end **blink**
2. **点击 MAILBOX** → dim overlay + 居中 `dp-console-shell`
3. **一级 FC 菜单**：APPLY / INVITE / CLOSE
4. **Enter APPLY** → 申请列表；YES / NO
5. **Enter INVITE** → 邀请列表；TTL 倒计时；JOIN / NO
6. **Esc / CLOSE** → 逐级返回 / 关闭
7. **eco / PRM** → 无 blink、instant 打开

### 3.2 与 DpCreateRoomConsole 的关系

| 维度 | CreateRoom | Mailbox |
|------|------------|---------|
| 布局 | 全页 | **modal overlay** |
| Shell | `dp-console-shell` | 同壳，head `— MAILBOX —` |
| 导航 | 单级 + 密码子模式 | **两级**：菜单 → 列表 |
| 数据 | `dpCreateRoomSubmit` | **现有** `dpMailbox` actions |

---

## 4. P0 范围 vs Deferred

### P0（已实现）

- [x] `DpMailboxConsole.vue` + retro8bit 门控
- [x] 未读 blink（`home-quick-card--mailbox-alert`）
- [x] FC 菜单 APPLY / INVITE / CLOSE
- [x] 二级列表 + YES/NO、JOIN/NO
- [x] Vuex 接线 + 邀请 tick
- [x] 键盘 / 触控 + eco/PRM + a11y

### Deferred

- 共享 `dp-console-shared.css`
- CRT snow 加载态
- 分类型 unread API
- 替换 `alert()` 为像素 dialog
- 8-bit 音效

---

## 5. 文件清单

| 文件 | 变更 |
|------|------|
| `front/dp_game/src/components/DpMailboxConsole.vue` | **新建** |
| `front/dp_game/src/styles/dp-mailbox-console.css` | **新建** |
| `front/dp_game/src/components/home.vue` | retro8bit 门控 + blink |
| `front/dp_game/src/styles/dp-lobby-shell.css` | blink keyframes |
| `front/dp_game/src/styles/dp-game-eco-mode.css` | eco 禁用 blink |
| `docs/refactor/retro8bit-mailbox-console-plan.md` | 本文档 |

---

## 6. Pixel English 文案

| 场景 | Pixel EN |
|------|----------|
| 快捷入口 | **MAILBOX** / **APPLY & INVITE** |
| Shell 标题 | **— MAILBOX —** |
| Tab | **APPLY** / **INVITE** / **CLOSE** |
| 空态 | **NO PENDING APPLY** / **NO PENDING INVITE** |
| 加载 | **LOADING...** |
| 操作 | **YES** / **NO** / **JOIN** |
| 倒计时 | **TTL NN s** |
| 返回 | **BACK** |

---

## 7. 验收标准

- [ ] retro8bit + 未读 → 卡片 blink → 打开 → console modal
- [ ] APPLY/INVITE 列表可操作；JOIN 成功进房
- [ ] 打开后 blink 停止
- [ ] 非 retro8bit → 原 `el-dialog`
- [ ] eco → 无 blink
- [ ] `npm run build` 通过

---

## 8. 相关文档

- `docs/refactor/retro8bit-create-room-console-plan.md`
- `docs/refactor/retro8bit-invite-friends-panel-plan.md`
