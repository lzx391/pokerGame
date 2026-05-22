# NPC 牌桌气泡（Table Talk）— 实现方案

> **文档性质**：方案与预制资源交付（Agent A）；**不写 Java、不改前端、不 git commit/push**。  
> **实现方（Agent B）**：按本文与 `src/main/resources/npc-lines/*.yaml` 落地后端；前端若需区分「人机气泡」为 P1。  
> **冻结契约**：下文「§0 冻结契约」与 `docs/WEBSOCKET.md` §6 冲突时，以 **§0 + WEBSOCKET §6 载荷形态** 为准。

---

## 变更说明（本次交付）

| 项 | 内容 |
|----|------|
| 新增 | `docs/refactor/npc-table-talk-plan.md`（本文件） |
| 新增 | `src/main/resources/npc-lines/{fish,call,lag,maniac,tag,nit,custom}.yaml` |
| 修改代码 | 无 |

---

## 0. 冻结契约（实现方不得擅自改动）

| # | 约定 |
|---|------|
| C1 | **下行载荷** 复用房间聊天帧：`{"_ws":"chat","nickname","text","ttlMs",...}`（与 `DpGameRoomPushService#handleChatSend` 广播形态一致）；**禁止**新 `_ws` 类型（P1 可再加 `source` 等可选字段，须向后兼容）。 |
| C2 | **触发点**：`DpRoomServiceImpl#npcAction` 内 **执行完** `fold` / `bet`（即动作已落房间状态）**之后** 再尝试推送气泡。 |
| C3 | **规则 NPC**：两阶段随机 — ① `speak?`（概率）② 在动作分桶 `lines` 中 **均匀随机** 一条。 |
| C4 | **动作分桶**：`FOLD` \| `CALL_OR_CHECK` \| `RAISE` \| `ALL_IN` \| `GENERIC`；映射自 `DpNpcEngine.BotActionType`（`GENERIC` 仅作兜底池，见 §4.3）。 |
| C5 | **LLM**：模型 JSON 字段 `table_talk` — `false`（boolean）**不推送**；**非空 string** 经敏感词后推送；与 `brief_reason` **分工**（§5）。 |
| C6 | **不落库**：禁止 `roomChatBuffer.append` / 任何聊天归档表；仅 WS 广播。 |
| C7 | **无订阅者则 skip**：推送前 `DpGameRoomPushService` 判断该 `roomId` 无 open 的 WS 订阅者时 **直接 return**（与 `broadcastIfSubscribed` 前置条件一致，可复用 `hasSubscribers`）。 |
| C8 | **频控**：同房同 NPC 昵称两次气泡间隔 ≥ `dp.npc.table-talk.min-interval-ms`（默认建议 2500，见 §7）。 |
| C9 | **敏感词**：出口文本走 `DpSensitiveWordService#maskForChat`（与真人聊天广播一致）。 |
| C10 | **回滚**：删/注释 `application.yml` 中 table-talk 配置 + `dp.npc.table-talk.enabled=false` 即可关闭，无需 Flyway。 |

---

## 1. 目标 / 非目标 / 验收 / 回滚

### 1.1 目标（产品）

- 规则 NPC 与 LLM NPC 在 **做出可见动作后**，有机会在牌桌旁显示 **短中文气泡**（复用现有聊天 UI 通道）。
- 规则线：台词来自 YAML，语气符合 FISH / CALL / LAG / MANIAC / TAG / NIT；`BOT_CUSTOM_*` 按旋钮映射子池或 `custom.yaml`。
- LLM 线：可选一句「牌桌口语」，与教学向 `brief_reason` 分离。

### 1.2 非目标

- 不改造真人聊天协议（仍 `chatSend` → 可落库路径，与 NPC 气泡隔离）。
- 不做跨房间、跨实例广播（仍单机 `roomSessions`）。
- 不要求本迭代改前端样式（P1：可选 `source:"npc"` 角标）。
- 不为气泡单独建表、不提供历史查询 API。
- `BOT_Shark` 等 **遗留昵称** 若未映射到规则风格，本迭代 **可不说话** 或走 `GENERIC`（B 自行择一并在代码注释写明）。

### 1.3 验收标准

| 场景 | 期望 |
|------|------|
| 开关关闭 | `enabled=false` 时任意 NPC 动作后 **无** `_ws:chat` 气泡。 |
| 无观众 WS | 房内无 `/ws/dp-game` 订阅者时，服务端 **不** 序列化/发送气泡（零额外 JSON 工作可观测）。 |
| 规则鱼牌 fold | `BOT_FISH_*` fold 后，小概率出现含 fold/弃牌语义的中文气泡；连续两次动作间隔 &lt; `min-interval-ms` 时第二次 **无** 气泡。 |
| LLM `table_talk:false` | 解析成功且仅 `brief_reason` 时，**无** 气泡；日志/教学仍可有 `brief_reason`。 |
| LLM `table_talk:"跟一手看看"` | 有订阅者时收到 `_ws:chat`，`nickname` 为 bot 昵称，`text` 为脱敏后文案。 |
| 敏感词 | 台词命中词库时广播文本为 `*` 替换版（与真人聊天一致）。 |
| 回滚 | 仅改配置即可关闭，重启后生效。 |

### 1.4 回滚

1. `dp.npc.table-talk.enabled: false`  
2. （可选）删除 `application.yml` 中 `dp.npc.table-talk` 整段与 `npc-lines` 引用  
3. 无需数据库迁移  

---

## 2. WebSocket 载荷与内部 API（给 B）

### 2.1 下行：复用 `chat`（对齐 WEBSOCKET.md §6）

```json
{
  "_ws": "chat",
  "nickname": "BOT_FISH_3",
  "text": "算了，这牌我 fold。",
  "serverTime": 1716000000123,
  "ttlMs": 15000
}
```

| 字段 | 规则 |
|------|------|
| `_ws` | 固定 `"chat"` |
| `nickname` | NPC 完整昵称（与座位一致） |
| `text` | ≤200 字；`\r`/`\n` 替空格；**已** `maskForChat` |
| `serverTime` | `System.currentTimeMillis()` |
| `ttlMs` | 建议复用 `CHAT_TTL_MS`（15000） |
| `id` / `senderUserId` | **NPC 气泡可不传**（前端按 nickname 去重展示，与 §6 一致） |

前端现状：`game.vue` 对 `_ws === 'chat'` 走 `pushRoomChatFromServer` — **无需改协议即可显示**。

### 2.2 内部 API 建议（`DpGameRoomPushService` 或同级）

```java
/**
 * NPC 牌桌气泡：不落库；无订阅者 skip；text 须为已脱敏展示文案。
 */
public void broadcastNpcTableTalk(String roomId, String nickname, String displayText);
```

实现要点：

1. `if (!enabled) return;`  
2. `if (!hasSubscribers(roomId)) return;`  
3. 空昵称/空文案 return；超长截断 200  
4. 组装 `ObjectNode` 同 `handleChatSend` 广播段（**不** 调 `roomChatBuffer`）  
5. `broadcastRawJsonToRoom(roomId, json)`（已存在私有方法，可提升可见性或包内新方法）

频控状态建议：`ConcurrentHashMap<String, Long> lastNpcTableTalkMsByRoomAndNick`，key = `roomId + "\0" + nickname`。

### 2.3 与真人聊天差异（实现方必读）

| 维度 | 真人 `chatSend` | NPC table talk |
|------|-----------------|----------------|
| 触发 | 客户端上行 | 服务端 `npcAction` 后 |
| 落库 | 当前实现 `roomChatBuffer.append` | **禁止** |
| 频控 | 按 **WebSocketSession** ~1.2s | 按 **房+NPC 昵称** `min-interval-ms` |
| 校验 | 昵称须在房 | 调用方保证为 bot |

---

## 3. 触发点与调用链

```text
DpRoomHeartbeatScheduler（或同类 tick）
  → callbacks.npcAction(room, player, action)
  → DpRoomServiceImpl#npcAction
       → fold / bet（动作生效）
       → 【此处之后】DpNpcTableTalkService.tryPushAfterAction(room, player, action)
            → 规则：两阶段随机 + 分桶取 line
            → LLM：读决策缓存中的 table_talk（见 §5）
            → pushService.broadcastNpcTableTalk(...)
```

**禁止**在 `fold`/`bet` **之前**推送（避免气泡与桌面状态不一致）。

LLM 异步决策：在 **future 完成并调用 `npcAction` 的同一调用栈末尾** 推送即可；勿在「发起 HTTP」时推送。

---

## 4. 规则 NPC：两阶段随机与分桶

### 4.1 风格 → YAML 文件

| 风格 | 资源文件 | 昵称前缀 |
|------|----------|----------|
| FISH | `npc-lines/fish.yaml` | `BOT_FISH_*` |
| CALL | `npc-lines/call.yaml` | `BOT_CALL_*` |
| LAG | `npc-lines/lag.yaml` | `BOT_LAG_*` |
| MANIAC | `npc-lines/maniac.yaml` | `BOT_MANIAC_*` |
| TAG | `npc-lines/tag.yaml` | `BOT_TAG_*` |
| NIT | `npc-lines/nit.yaml` | `BOT_NIT_*` |
| CUSTOM 兜底 | `npc-lines/custom.yaml` | `BOT_CUSTOM_*` |

加载：启动时读入 `Map<String, NpcLinePack>`（key = `meta.style`），失败则 log error 且该风格静音。

### 4.2 两阶段随机

```text
if (!enabled) → stop
if !passMinInterval(roomId, nickname) → stop
draw u ~ Uniform(0,1)
if u >= speakProbabilityEffective → stop   // 阶段①
line = uniformRandom(lines[bucket])       // 阶段②
broadcastNpcTableTalk(roomId, nickname, mask(line))
```

`speakProbabilityEffective` 优先级（高 → 低）：

1. `application.yml` → `dp.npc.table-talk.speak-probability.<style>`（可选细调，B 实现时用 relaxed binding，如 `speak-probability.fish`）  
2. YAML 内 `speakProbability`  
3. 全局默认 `dp.npc.table-talk.speak-probability`（可选，建议 0.25）

### 4.3 动作 → 分桶映射

| `BotActionType` | 分桶 key |
|-----------------|----------|
| `FOLD` | `FOLD` |
| `CALL_OR_CHECK` | `CALL_OR_CHECK` |
| `RAISE` | `RAISE` |
| `ALL_IN` | `ALL_IN` |
| （未知 / null action） | `GENERIC` |

若目标分桶 **无条目或空数组**，降级顺序：`GENERIC` → **不推送**（勿抛异常）。

### 4.4 `BOT_CUSTOM_*` 映射（旋钮 → 子池）

在 `DpNpcCustomStrategy` 已有 6 维 profile 的前提下，建议 **入座时** 或 **首次发言前** 算一次 `EffectiveStyle`：

| 条件（示例，B 可微调阈值） | 借用子池 |
|---------------------------|----------|
| `callStation` 高且 `pfr` 低 | CALL 或 FISH |
| `pfr` 高且 `bluffFreq` 高 | MANIAC 或 LAG |
| `foldToPressure` 高且 `vpip` 低 | NIT |
| `vpip`/`pfr` 中等偏紧 | TAG |
| 无匹配 | `custom.yaml` 的 `GENERIC` 与各动作池 |

实现简单方案（P0）：**不合并子池**，仅用 `custom.yaml`；P1 再按上表 `lines` 引用他池 ID。

---

## 5. LLM：`table_talk` 与 `brief_reason`

### 5.1 分工

| 字段 | 受众 | 用途 |
|------|------|------|
| `brief_reason` | 日志、运维、可选教学面板 | 解释决策；**不**作为牌桌气泡默认文案 |
| `table_talk` | 牌桌旁观/UI 气泡 | 短口语，≤40 字建议；须 **点明动作或态度** |

### 5.2 解析规则（冻结）

在 `DpLlmNpcDecisionService` 解析 JSON 时扩展 `LlmParseResult`：

| `table_talk` 类型 | 行为 |
|-------------------|------|
| 缺失 | 不推送 |
| `boolean false` | 不推送 |
| `boolean true` | 不推送（视为未提供口语；**禁止** 用 brief_reason 顶替） |
| `string` 空白 | 不推送 |
| `string` 非空 | trim → 长度截断 40（可配置）→ `maskForChat` → 推送 |
| 数字 / 对象 / 数组 | 不推送，debug 日志 |

系统提示词增补（B 改 prompt）示例：

> 可选字段 `table_talk`：给牌桌观众的一句短中文（≤40 字）；不需要口语时填 `false`。`brief_reason` 写教学解释，勿与 `table_talk` 重复长文。

### 5.3 推送时机

与规则 NPC 相同：**`npcAction` 执行成功后**；若 `table_talk` 有值且通过频控/开关/订阅者检查则 `broadcastNpcTableTalk`。

---

## 6. 频控、敏感词、不落库、Skip 广播

| 机制 | 说明 |
|------|------|
| 全局开关 | `dp.npc.table-talk.enabled` |
| 间隔 | 同房同 bot：`min-interval-ms`（默认 2500） |
| 敏感词 | `maskForChat`；命中则仍推送星号版 |
| 不落库 | 不调用 `RoomChatBuffer` / Mapper |
| Skip | `!hasSubscribers(roomId)` 时 return |

可选 P1：每手牌每 bot 最多 N 条（防 LLM 话痨）。

---

## 7. 配置键（B 必须实现）

在 `application.yml`（或 `@ConfigurationProperties` 前缀 `dp.npc.table-talk`）：

```yaml
dp:
  npc:
    table-talk:
      enabled: true                    # 总开关，默认 true
      speak-probability: 0.25          # 全局默认（规则 NPC 阶段①）
      min-interval-ms: 2500            # 同房同 NPC 最小间隔
      # 可选 per-style 覆盖（示例）：
      # speak-probability:
      #   fish: 0.30
      #   nit: 0.12
      llm-table-talk-max-len: 40       # P1 可选
```

| 键 | 类型 | 默认 | 说明 |
|----|------|------|------|
| `dp.npc.table-talk.enabled` | boolean | `true` | `false` 关闭规则+LLM 气泡 |
| `dp.npc.table-talk.speak-probability` | double | `0.25` | 规则 NPC 说话概率；可被 YAML / per-style 覆盖 |
| `dp.npc.table-talk.min-interval-ms` | long | `2500` | 频控 |

YAML `speakProbability`：**低于** application 全局时，建议 B 实现为「文件提供基线，配置覆盖文件」（即 **application 优先**，与资源注释一致）。

---

## 8. 预制台词资源

- 目录：`src/main/resources/npc-lines/`  
- 结构见各文件；每动作 **3～6** 条中文，**点明动作**（弃牌/跟注/加注/全下）。  
- 禁止辱骂、政治、色情、真人侮辱类词汇。

---

## 9. P0 / P1  backlog

### P0（首版必做）

- [ ] `DpNpcTableTalkProperties` + `application.yml` 三键  
- [ ] 加载 `npc-lines/*.yaml`  
- [ ] `broadcastNpcTableTalk`（不落库、skip 无订阅者、敏感词）  
- [ ] `npcAction` 末尾挂钩（规则 + LLM）  
- [ ] 规则：两阶段随机 + 五动作分桶  
- [ ] LLM：`table_talk` 解析与 string 推送  
- [ ] 频控 `min-interval-ms`  

### P1（可后续）

- [ ] `table_talk` / `chat` 增加 `"source":"npc"` 供前端样式区分  
- [ ] CUSTOM 旋钮映射到他池 `lines`  
- [ ] per-hand 每 bot 上限条数  
- [ ] `speak-probability` 按房间人数/底池动态微调  
- [ ] 遗留 `BOT_Shark` 专用台词包  
- [ ] 单元测试：解析 `table_talk:false`、频控、空房 skip  

---

## 10. 必读代码路径

| 路径 | 原因 |
|------|------|
| `docs/WEBSOCKET.md` §6 | `chat` 载荷约定 |
| `websocket/DpGameRoomPushService.java` | 广播、`hasSubscribers`、`CHAT_TTL_MS` |
| `room/impl/DpRoomServiceImpl.java#npcAction` | 触发点 |
| `room/support/DpRoomHeartbeatScheduler.java` | `npcAction` 调用方 |
| `npc/engine/DpNpcEngine.java` | `BotActionType`、昵称前缀 |
| `npc/llm/DpLlmNpcDecisionService.java` | LLM JSON 解析扩展 |
| `moderation/DpSensitiveWordService.java` | 出口脱敏 |

---

## 11. YAML 自检（交付方已完成项）

- 7 个文件均含 `meta.style`、`speakProbability`、`lines` 下 **5 个 key** 齐全。  
- 每 key 至少 **2** 条（交付为 4 条/桶）。  
- 缩进：2 空格；字符串使用双引号。  
- 肉眼检查：中文须含动作语义（fold/跟注/加注/全下/过牌等）。

实现方合并前可用：

```powershell
Get-ChildItem src/main/resources/npc-lines/*.yaml | ForEach-Object { $_.Name; Get-Content $_.FullName | Select-String '^\s+(FOLD|CALL_OR_CHECK|RAISE|ALL_IN|GENERIC):' }
```

---

*文档版本：2026-05-22 · 仅方案与资源，不含 Java 实现。*
