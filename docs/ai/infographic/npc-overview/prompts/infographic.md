Create a professional infographic following these specifications:

## Image Specifications
- **Type**: Infographic
- **Layout**: structural-breakdown (exploded view variant)
- **Style**: technical-schematic (blueprint variant)
- **Aspect Ratio**: 16:9
- **Language**: zh (Chinese)

## Core Principles
- Follow the layout structure precisely for information architecture
- Apply style aesthetics consistently throughout
- Keep information concise, highlight keywords and core concepts
- Use ample whitespace for visual clarity
- Maintain clear visual hierarchy

## Style Guidelines
- Background: deep blue (#1E3A5F) with subtle grid pattern
- Primary elements: white lines and cyan (#06B6D4) highlights on deep blue
- Accent color: amber (#F59E0B) for callout dots and emphasis
- Geometric precision, clean vector shapes, consistent stroke weights
- All-caps or clear sans-serif labels
- Engineering blueprint feel

## Layout Structure

**Top banner**: Main title + subtitle
**Left side**: 7 archetypes exploded view (horizontal strip showing each bot type as a module)
**Center**: Call chain flow (Timer → HeartbeatScheduler → NpcEngine → BotAction)
**Right side**: LLM vs Rule split diagram
**Bottom**: Quick reference tables (nickname prefix map + config snippet)

## Content

### Main Title
MGDemoPlus NPC AI 体系全景图

### Subtitle
规则型 Bot × 大模型 Bot：7 种 Archetype 从心跳到出牌的完整架构

### Section 1: 七档规则 Archetype（左侧模块区）

**FISH** — 偏被动新手鱼，VPIP~50%
**CALL** — 跟注站，callStation 高
**LAG** — 松凶型，高 VPIP + 高 PFR
**TAG** — 紧凶型，低 VPIP + 高侵略（默认标杆）
**NIT** — 超紧型，仅打强牌
**MANIAC** — 疯狂型，全范围激进
**CUSTOM** — 自定义 YAML 调参档

Shared: 统一翻前策略 DpNpcUnifiedPreflopStrategy
Branch: 翻后按 BotType switch 分派不同 Strategy

### Section 2: 调用链（中央流程区）

```
1s Timer
  ↓
DpRoomHeartbeatScheduler
  ↓
DpNpcEngine.decideActionIfReady (sync)
  ├─ 校验: actor 索引 / 未 fold / 未 all-in / 未离桌
  ├─ LLM 昵称? → return null (走 LLM 路径)
  ├─ rule-think: nextBotActionTime 采样延时
  └─ decideBotAction → BotAction
       ↓
DpRoomServiceImpl.npcAction → fold / call / raise / all-in
```

### Section 3: LLM vs 规则分流（右侧）

**规则 NPC** (BOT_FISH / TAG / LAG / NIT / CALL / MANIAC / CUSTOM)
→ DpNpcEngine (sync) → BotAction

**大模型 NPC** (BOT_LLM / BOT_LLM_GLOBAL)
→ DpLlmNpcDecisionService (async) → API → JSON → BotAction
→ BOT_LLM: 单轮对话
→ BOT_LLM_GLOBAL: 多轮累积 handSeed 对话

### Section 4: 昵称前缀速查（底部左侧）

| 前缀 | BotType | REST API |
| BOT_FISH | FISH | addFishBot |
| BOT_CALL | CALL | addCallBot |
| BOT_LAG | LAG | addLagBot |
| BOT_TAG | TAG | addTagBot |
| BOT_NIT | NIT | addNitBot |
| BOT_MANIAC | MANIAC | addManiacBot |
| BOT_CUSTOM | CUSTOM | addCustomBot |
| BOT_Shark(旧) | → TAG | addSharkBot |

### Section 5: 全局开关（底部右侧）

NPC_MOOD_ENABLED = false
NPC_HAND_SEED_FOR_DECISIONS = true

## Text Labels (in Chinese)
- 七档规则 Archetype
- 统一翻前策略
- 翻后分派 switch(BotType)
- 心跳调度器
- 行动就绪校验
- 规则决策引擎
- LLM 大模型路径
- 异步 API 调用
- BotAction 落地
- 六旋钮 StyleProfile
- 思考延时 rule-think
- 昵称前缀映射
- 全局行为开关
