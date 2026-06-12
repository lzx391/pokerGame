# retro8bit 创建房间 — 游戏机菜单（Concept B）

> 状态：**已实现**  
> 路由：`/create-room` · 门控：`gameUiTheme === 'retro8bit'`  
> 取代：终端 CLI（`DpCreateRoomTerminal` / `set` / `load profile`）  
> 非目标：不改后端 API；经典 `CreateRoom.vue` 表单分支不变。

---

## 1. PM 决策（相对终端方案）

| # | 决策 |
|---|------|
| 1 | **去掉命令行** — 不再输入 `set` / `load profile` / `commit` |
| 2 | **FC/NES 式垂直菜单** — `▶` 光标、高亮反色/闪烁（`prefers-reduced-motion` 静态高亮） |
| 3 | **键盘** — ↑↓/WS 移动；←→/AD 改值；Enter 确认；Esc/B 返回 |
| 4 | **触控** — 像素 D-pad + A（确认/加字）+ B（返回），触控目标 ≥44px |
| 5 | **错误** — 红色状态条 + 像素对话框，**无**终端 `[ERR]` 滚动历史 |
| 6 | **保留** — `dpCreateRoomSubmit.js`、commit 后 boot overlay、retro 隐藏主题选择器 |

---

## 2. 菜单项

| 行 | 操作 |
|----|------|
| PROFILE | ←→ 循环 Casual / Standard / Deep（套用 `ROOM_PRESETS`） |
| SMALL BLIND | ←→ 调整 SC（步长 1）；同行显示推导 BC |
| STACK (BB) | ←→ 5–200，步长 5 |
| SEATS | ←→ 2–9 |
| PASSWORD | Enter 进入子模式；←→ 选字；A/Space 追加；UP 删字；Enter 完成；空=公开 |
| CREATE | Enter → 校验 → boot（可跳过）→ `dpCreateRoomAndStart` |
| BACK | Enter / Esc / B → 大厅 |

---

## 3. 文件清单

| 文件 | 变更 |
|------|------|
| `front/dp_game/src/components/DpCreateRoomConsole.vue` | **新建** |
| `front/dp_game/src/styles/dp-create-room-console.css` | **新建** |
| `front/dp_game/src/components/CreateRoom.vue` | 引用 Console |
| `front/dp_game/src/utils/dpCreateRoomSubmit.js` | 无改（沿用） |
| `DpCreateRoomTerminal.vue` / `dp-create-room-terminal.css` | **删除** |
| `docs/refactor/retro8bit-create-room-console-plan.md` | 本计划 |
| `docs/refactor/retro8bit-create-room-terminal-plan.md` | 保留历史，标记被取代 |

---

## 4. 验收清单

### 桌面键盘

- [ ] retro8bit 主题进入 `/create-room` 为游戏机菜单（非 Element 表单、非命令行）
- [ ] ↑↓ 移动光标；←→ 改 PROFILE/SC/STACK/SEATS
- [ ] PASSWORD：Enter 子模式 → ←→ 选字 → Space/A 追加 → Enter 保存 → 主菜单显示 `****` 或 PUBLIC
- [ ] CREATE：合法配置 → boot（非 eco/PRM）→ 进对局；非法 → 红条/对话框
- [ ] Esc / BACK 行 → 回大厅

### 移动触控

- [ ] D-pad 上下左右与 A/B 与键盘行为一致
- [ ] 按钮可点区域 ≥44px；粗指针设备隐藏底部键盘提示行
- [ ] 安全区 `safe-area-inset-bottom` 不裁切手柄

### 回归

- [ ] 非 retro8bit 主题仍为经典 `CreateRoom` 表单 + 主题选择器
- [ ] `npm run build` 通过

---

## 5. Boot 文案（与终端 P0 相同）

```
ROOM-CRT v1.0 BOOT
[  OK  ]  Validating table parameters...
[  OK  ]  Allocating seat map...
[  OK  ]  Opening room channel...
[  OK  ]  Starting first hand...
```

`ecoMode` / `prefers-reduced-motion` / `data-dp-fluidity=eco`：跳过动画。
