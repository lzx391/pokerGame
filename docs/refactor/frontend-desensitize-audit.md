# 前端「猫咪主题脱敏」审计报告

> **审计范围**：`front/dp_game/src/**` 中用户可见层（`.vue` template、绑定到 UI 的字符串、`constants` 展示 label、`aria-label`/`title`/表头/空状态/`$message`/`$alert`）。  
> **基准词表**：`front/dp_game/src/constants/dpCatThemeCopy.js`（`CAT_COPY`）+ 产品扩展表（见 CLAUDE.md / 本任务说明）。  
> **审计日期**：2026-05-22  
> **约束**：仅文档交付；未改业务源码。禁止改动项见 §6。

---

## 1. 摘要

| 指标 | 数值 |
|------|------|
| **P0 命中条数** | **27**（含同一文件多行；不含纯注释/CSS/变量名/API path） |
| **P0 涉及文件数** | **12** |
| **已接入 `CAT_COPY` 的文件** | 7（见 §5） |
| **含敏感展示词但未用 `CAT_COPY` 的文件** | 10（见 §3） |

### 高风险文件 Top10（按用户曝光 + 违规密度）

| 排序 | 文件 | 理由 |
|------|------|------|
| 1 | `constants/guideUiSteps.js` | 新手一分钟引导，硬编码「筹码/盲注/加注/弃牌」 |
| 2 | `components/game.vue` | 对局核心：`主池`/`边池` 确认文案、`dp_*` 错误提示 |
| 3 | `App.vue` | 登录/注册页大标题 **POKER GAME**（禁词「扑克」英文形态） |
| 4 | `components/HandHistory.vue` | 列表表头「主池」+ 空状态/错误暴露表名 |
| 5 | `components/HandHistoryDetail.vue` | 详情「开局注」「边池」 |
| 6 | `constants/dpCatThemeCopy.js` | 词表源头 `anteLine: '开局注'` 未对齐「开局鱼干」 |
| 7 | `components/GameOwnerPanel.vue` | 房主分池 UI「主池/边池」 |
| 8 | `constants/npcStylePresets.js` | 自定义 NPC 弹窗「加注/下注/跟注/弃牌」 |
| 9 | `constants/dpGameThemeColorTokens.js` | 主题调色板用户可见 label |
| 10 | `components/GameSettledPrepareBar.vue` | 准备条「大猫注」 |

**说明**：`GameActionPanel.vue` 行动区（跟投/加投/盖牌/全投）与 `GameTopBar.vue` 顶栏已大体合规；P0 集中在历史牌谱、房主结算、引导与词表缺口。

---

## 2. 命中表（P0）

> **建议替换** 以产品词表为准；实现时优先 `CAT_COPY.xxx`，避免散落硬编码。

| 路径 | 行号 | 原文（摘录） | 建议替换 | 建议 `CAT_COPY` 键 |
|------|------|--------------|----------|-------------------|
| `App.vue` | 24 | `POKER GAME` | `猫咪牌局` 或 `CAT PARTY`（禁「扑克/poker」） | `appAuthTitle`（新增） |
| `constants/dpCatThemeCopy.js` | 80 | `anteLine: '开局注'` | `开局鱼干` | `anteLine`（改值） |
| `constants/guideUiSteps.js` | 34 | `…还能用的筹码…有人加注…` | `…还能用的小鱼干…有人加投…` | `chips` + 文案拼接 |
| `constants/guideUiSteps.js` | 94 | `…发牌猫与盲注位…` | `…发牌猫与小猫/大猫位（SC/BC）…` | `smallBlind` / `bigBlind` / `dealer` |
| `constants/guideUiSteps.js` | 138 | `…本手算弃牌…` | `…本手算盖牌…` | `actionFold`（新增） |
| `constants/guideUiSteps.js` | 182 | `有人加注时…` | `有人加投时…` | — |
| `components/HandHistory.vue` | 51 | `<th>主池</th>` | `主鱼干池` | `mainPot`（新增） |
| `components/HandHistory.vue` | 38-39 | `dp_observed_hand_history` / `dp_observed_hand_participant` | 改为「牌谱主记录/参与者记录未同步」等用户语 | — |
| `components/HandHistory.vue` | 247 | `…已建表 dp_observed_hand_participant` | `…请稍后重试或联系管理员` | — |
| `components/HandHistoryDetail.vue` | 31 | `…盈亏与边池` | `…盈亏与边鱼干池` | `sidePot` |
| `components/HandHistoryDetail.vue` | 52 | `开局注` | `开局鱼干` | `anteLine` |
| `components/HandHistoryDetail.vue` | 235 | `<h2>边池</h2>` | `边鱼干池` | `sidePot` |
| `components/game.vue` | 1041 | `'第 ' + (i === 0 ? '主' : i) + ' 池还没选赢家'` | `第 N 个主鱼干池/边鱼干池…` | `mainPot` / `sidePotIndexed` |
| `components/game.vue` | 1051 | `'主池'` / `'边池 '` | `主鱼干池` / `边鱼干池 ` | `mainPot` / `sidePot` |
| `components/game.vue` | 680 | `…dp_music_track 已就绪` | `…曲库服务未就绪，请稍后重试` | — |
| `components/GameOwnerPanel.vue` | 43 | `'主池'` / `'边池 '` | `主鱼干池` / `边鱼干池 ` | `mainPot` / `sidePot` |
| `components/GameSettledPrepareBar.vue` | 44 | `小鱼干不足大猫注(` | `小鱼干不足大猫鱼干(` | `bigBlindFish`（新增） |
| `constants/dpGameThemeColorTokens.js` | 30 | `底池/筹码强调` | `小鱼干池/小鱼干强调` | `pot` / `chips` |
| `constants/dpGameThemeColorTokens.js` | 42 | `主按钮+加注` | `主按钮+加投` | `actionRaise`（新增） |
| `constants/dpGameThemeColorTokens.js` | 46 | `主按钮+加注` | `主按钮+加投` | `actionRaise` |
| `constants/dpGameThemeColorTokens.js` | 50 | `全押按钮` | `全投按钮` | `actionAllIn`（新增） |
| `constants/npcStylePresets.js` | 16 | `翻前爱不爱加注` | `翻前爱不爱加投` | — |
| `constants/npcStylePresets.js` | 17 | `翻后爱不爱继续下注` | `翻后爱不爱继续加投` | — |
| `constants/npcStylePresets.js` | 19 | `爱跟注不爱弃牌` | `爱跟投不爱盖牌` | — |
| `components/MusicUpload.vue` | 30 | `入库到 dp_music_track` | `保存到服务器曲库` | — |
| `components/MusicUpload.vue` | 194 | `…已建表 dp_music_track…` | `…曲库未就绪，请确认后端已启动` | — |

### 非 P0、但需在词表注明（产品允许保留简称）

| 路径 | 行号 | 原文 | 说明 |
|------|------|------|------|
| `components/GameActionPanel.vue` | 13-25 | `⅓池` `½池` `¾池` `1×池` `1½池` | 可保留按钮文案；须在 `dpCatThemeCopy.js` 注释/键说明：**= 当前小鱼干池的比例** |
| `constants/guideUiSteps.js` | 164 | `⅓池、½池…` | 已用 `CAT_COPY.pot` 解释比例，合规 |

### 已扫描、未列入 P0（符合审计边界）

- `console.log`、纯 JS/CSS 注释、props/变量名、`@all-in` 事件名、`dp-poker-cards.css` 文件名
- `vue.config.js` / `public/index.html` 的 `poker_demo` 标题（产品明确要求保持）
- `utils/dpGameHandRank.js` 文件头注释「德州」（非用户可见）
- `styles/dp-game-shell.css` 等样式注释中的「扑克/庄/弃牌/筹码」（非 UI 文案）

---

## 3. 未使用 `CAT_COPY` 却含敏感展示词的文件

以下文件在 template 或用户可见常量/消息中出现产品表敏感词，且**未** `import { CAT_COPY }`（或未通过 `catCopy` 代理展示）：

1. `front/dp_game/src/App.vue`
2. `front/dp_game/src/constants/dpCatThemeCopy.js`（`anteLine` 值本身待改）
3. `front/dp_game/src/constants/guideUiSteps.js`（部分步骤已用 `CAT_COPY`，仍有硬编码敏感词）
4. `front/dp_game/src/constants/dpGameThemeColorTokens.js`
5. `front/dp_game/src/constants/npcStylePresets.js`
6. `front/dp_game/src/components/HandHistory.vue`
7. `front/dp_game/src/components/HandHistoryDetail.vue`
8. `front/dp_game/src/components/game.vue`（池名、曲库错误）
9. `front/dp_game/src/components/GameOwnerPanel.vue`
10. `front/dp_game/src/components/GameSettledPrepareBar.vue`
11. `front/dp_game/src/components/MusicUpload.vue`

**已用 `CAT_COPY` / `dpGameStageDisplay` 的参考**：`GamePlayerCard.vue`、`GameRoundTable.vue`、`guideUiSteps.js`（部分）、`dpHandHistoryReplay.js`（阶段名）。

---

## 4. 建议新增到 `dpCatThemeCopy.js` 的键

在现有 `CAT_COPY` 基础上建议扩展（命名供实现 Agent 选用）：

```javascript
// 建议追加到 CAT_COPY（示例值）
{
  mainPot: '主鱼干池',
  sidePot: '边鱼干池',
  /** 带序号：边鱼干池 1 */
  sidePotLabel: (i) => (i === 0 ? '主鱼干池' : '边鱼干池 ' + i),

  anteLine: '开局鱼干',        // 替换原「开局注」
  bigBlindFish: '大猫鱼干',    // 替换「大猫注」

  actionFold: '盖牌',
  actionCall: '跟投',
  actionRaise: '加投',
  actionBet: '投入',
  actionCheck: '观望',
  actionAllIn: '全投',

  blindPositionHint: '小猫/大猫位',

  /** 快捷按钮：文案可保留，注释标明语义 */
  potPresetThird: '⅓池',   // = 当前小鱼干池 × 1/3
  potPresetHalf: '½池',
  potPresetThreeQuarter: '¾池',
  potPresetOneX: '1×池',
  potPresetOneHalf: '1½池',
  potPresetAriaNote: '按小鱼干池比例快捷加投',

  appAuthTitle: '猫咪牌局',

  themePotChipsLabel: '小鱼干池/小鱼干强调',
  themeRaiseBtnLabel: '主操作按钮（加投）',
  themeAllInBtnLabel: '全投按钮'
}
```

**实现提示**：

- `HandHistoryDetail.vue` 结算区列表项 `池 {{ i + 1 }}`（约 239 行）可改为 `主鱼干池` / `边鱼干池 n`。
- `game.vue` 1041 行勿再用 `'主' + '池'` 拆分规避检测。
- `guideUiSteps.js` 宜全部改为 `CAT_COPY` 插值，与 `GameActionPanel` 用语一致。

---

## 5. 已合规范例文件（实现对照）

| 文件 | 合规要点 |
|------|----------|
| `components/GameActionPanel.vue` | 跟投/观望/加投/全投/盖牌；`aria-label` 含「小鱼干池比例」 |
| `components/GameTopBar.vue` | `小鱼干池`、`需对齐`、`剩余小鱼干` |
| `components/GameRoundTable.vue` | `catCopy` + `DEALER_BADGE_CHAR`；title 发牌猫/小猫 SC/大猫 BC |
| `components/GamePlayFlowContent.vue` | 全流程猫咪叙事、小鱼干池、跟投/加投/盖牌/全投 |
| `components/GamePlayerCard.vue` | `catCopy.stackShort` / `roundShort`；「已盖牌」 |
| `components/room.vue` / `CreateRoom.vue` / `home.vue` | 小猫/大猫/小鱼干/大猫鱼干筛选文案 |
| `utils/dpHandHistoryReplay.js` | `formatActionText` 已猫化；`playerRoleTags` 发牌猫/SC/BC |
| `components/CatTutorialDialog.vue` | 标题「欢迎来到猫咪牌局」；嵌入 `GamePlayFlowContent` |

---

## 6. 禁止改动清单

实现阶段 **不得** 修改以下内容（除非产品另开需求）：

| 类别 | 路径/项 |
|------|---------|
| 构建/HTML 标题 | `front/dp_game/vue.config.js` 的 `title: 'poker_demo'` |
| | `front/dp_game/public/index.html` 页面 `<title>` |
| 后端 | 任意 Java/SQL/API |
| 分发产物 | `front/dp_game/dist/**` |
| 协议字段 | 请求/响应 JSON 字段名（如 `smallBlindChips`、`allIn`、`POST_BLIND_BB`） |
| WebSocket / REST path | 如 `/dpRoom/judgeWin` |
| Store state / props 名 | 如 `potWinners`、`myChips`、`all-in` 事件名 |
| CSS 类名 / 文件名 | 如 `dp-btn--allin`、`dp-poker-cards.css` |
| Git | 本审计 **不** 提交 commit、不 push、不代合并 PR |

---

## 7. 验证说明

- 本次 **未执行** `npm run build`；行号以仓库当前版本为准，改行后需重新 `rg` 校对。
- 建议实现后复扫命令（PowerShell 示例）：

```powershell
rg -n "筹码|盲注|小盲|大盲|庄家|底池|主池|边池|下注|跟注|加注|弃牌|积分|赌|扑克|德州|开局注|大猫注|POKER|poker|dp_[a-z_]+|MyBatis" front/dp_game/src --glob "*.{vue,js}" 
```

排除注释后，用户可见层应无 P0 残留。

---

## 附录：当前 `CAT_COPY` 基准（审计时）

```67:81:front/dp_game/src/constants/dpCatThemeCopy.js
export const CAT_COPY = Object.freeze({
  chips: '小鱼干',
  smallBlind: '小猫',
  bigBlind: '大猫',
  smallBlindAbbr: 'SC',
  bigBlindAbbr: 'BC',
  dealer: '发牌猫',
  pot: '小鱼干池',
  needMatch: '需对齐',
  stackShort: '小鱼干',
  roundShort: '本轮',
  anteLine: '开局注'
})
```

`anteLine` 与产品表「开局鱼干」不一致，列为 P0 词表源头问题，修复后下游 `HandHistoryDetail` 等应统一引用该键。
