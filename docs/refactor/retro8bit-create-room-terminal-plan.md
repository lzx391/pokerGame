# retro8bit 创建房间终端（Concept A P0）

> 状态：**已取代** — 见 [retro8bit-create-room-console-plan.md](./retro8bit-create-room-console-plan.md)（游戏机菜单，无 CLI）  
> 路由：`/create-room` · 门控：`gameUiTheme === 'retro8bit'`  
> 非目标：不改后端 `/dpRoom/createRoom` / `startGame` 契约；不为非 retro 主题改经典表单。

---

## 1. PM 确认决策

| # | 决策 |
|---|------|
| 1 | **移动端 hybrid** — 上方只读 `config.sys` 行 + 底部命令输入（非纯 REPL 占满屏） |
| 2 | **布局** — retro8bit 分支在 `CreateRoom.vue` **整页替换** 为终端子组件 |
| 3 | **主题选择器** — retro 创建分支 **隐藏**；经典分支保留 `dp-theme-picker` |
| 4 | **commit 后 boot** — **每次 commit** 播放短 POST boot；`ecoMode` 或 `prefers-reduced-motion` **跳过** 动画 |
| 5 | **密码回显** — 终端历史与 `show` 输出中密码以 `*` 掩码 |

---

## 2. 范围（P0）

### 包含

- `DpCreateRoomTerminal.vue` + `dp-create-room-terminal.css`
- `CreateRoom.vue`：`v-if gameUiTheme === 'retro8bit'` → 终端；`v-else` 经典模板不变
- `config.sys` 语义：`SET SC` / `STACK_BB` / `SEATS` / `PASS`；推导 `BC` + 带入小鱼干预览
- 预设：`load profile casual|standard|deep`（与经典 `ROOM_PRESETS` 同源）
- 校验失败 → `[ERR]` 历史行
- 命令：`set`、`load profile`、`show`、`commit`、`abort`/`back`、`help`
- `dpCreateRoomSubmit.js` — 抽取 `createRoom` → `startGame` → `router.replace`
- commit 短 boot overlay（对齐 `DpCrtBootSequence` 雪花 + 逐行，房间专用文案）
- a11y：键盘 Enter、aria-live 错误区、基础 focus trap、可见 focus ring

### 不包含（P1/P2）

- 命令历史 ↑↓、Tab 补全（对局 `DpTerminalCli` 级）
- `dpCreateRoomTerminalDevLog.js` 埋点
- 抽取共享 `DpCrtSnowInset.vue`
- retro 创建页主题切换（刻意隐藏 picker）
- Element UI 控件（P0 retro 路径零新增 Element 组件）

---

## 3. 命令规格

| 命令 | 行为 |
|------|------|
| `help` | 列出命令与 `config.sys` 字段说明 |
| `show` | 打印当前 config（PASS 掩码） |
| `set sc <n>` | 小猫 1+ 整数 |
| `set stack_bb <n>` | 带入倍数 5–200 |
| `set seats <n>` | 人数 2–9 |
| `set pass <text>` | 进房密码；`set pass` 清空 |
| `load profile casual\|standard\|deep` | 套用预设 |
| `commit` | 校验 → boot（可跳过）→ 提交 API |
| `abort` / `back` | `$emit('back')` → 大厅 |

别名：大小写不敏感；`SET SC 5` 与 `set sc 5` 等价。

---

## 4. 校验

| 字段 | 规则 | ERR 示例 |
|------|------|----------|
| SC | ≥1 整数 | `[ERR] SC must be >= 1` |
| STACK_BB | 5–200 | `[ERR] STACK_BB must be 5–200` |
| SEATS | 2–9 | `[ERR] SEATS must be 2–9` |
| user | 需 nickname | `[ERR] Not logged in` |

---

## 5. Boot 文案（commit 后）

```
ROOM-CRT v1.0 BOOT
[  OK  ]  Validating table parameters...
[  OK  ]  Allocating seat map...
[  OK  ]  Opening room channel...
[  OK  ]  Starting first hand...
```

eco / PRM：直接 `onDone()`，无 overlay。

---

## 6. 文件清单

| 文件 | 变更 |
|------|------|
| `docs/refactor/retro8bit-create-room-terminal-plan.md` | 本计划 |
| `front/dp_game/src/components/DpCreateRoomTerminal.vue` | **新建** |
| `front/dp_game/src/styles/dp-create-room-terminal.css` | **新建** |
| `front/dp_game/src/utils/dpCreateRoomSubmit.js` | **新建** |
| `front/dp_game/src/components/CreateRoom.vue` | retro 分支 + 经典 submit 走 util |

---

## 7. 验收

- [ ] `retro8bit`：整页终端；无主题 picker；经典主题表单与预设无回归
- [ ] `show` / `set` / `load profile` / `commit` / `back` 可用
- [ ] 密码在输出中为 `*`
- [ ] 校验错误为 `[ERR]` 且 aria-live 可读
- [ ] commit 成功进对局；失败 `[ERR]` + 可重试
- [ ] eco / PRM：无 boot 动画
- [ ] `npm run build` 通过

---

*版本：2026-06-02*
