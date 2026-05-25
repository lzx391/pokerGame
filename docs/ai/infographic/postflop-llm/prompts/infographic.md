Create a professional infographic following these specifications:

## Image Specifications
- **Type**: Infographic
- **Layout**: structural-breakdown (cross-section + exploded variant)
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
- Accent color: amber (#F59E0B) for emphasis
- Engineering blueprint feel, clean vector shapes, consistent stroke weights

## Layout Structure

**Top**: Title + subtitle banner
**Left column**: 6 archetype post-flop dispatch (stacked cards)
**Center column**: Shared modules (SmartContext, HandStrength, rule-think timeline)
**Right column**: LLM Bot async pipeline
**Bottom**: DpNpcEngine public capability checklist

## Content

### Main Title
翻后策略分派 + 大模型 Bot + 共享模块

### Subtitle
6 种规则 Bot 翻后行为 × LLM 异步决策 × rule-think 思考延时 × 共用牌力评估

### Section 1: 翻前公共量（进入分支前已算好）

callAmount | callRatio | position (EARLY/MIDDLE/LATE/BLINDS)
strength (SimpleStrength: WEAK/MEDIUM/STRONG)
boardDanger (DRY/WET) | mood (默认=0)
StyleProfile: preflopTightness, aggression, bluffFrequency, callStation, stealBlindFrequency, checkRaiseFear

### Section 2: 六档翻后分派（左侧纵向卡片）

**FISH** — 偏被动: 弱牌+高callRatio+湿牌→弃牌; 大比CALL; 小额诈唬1/3pot
**CALL** — 跟注站: callStation极高; 极少fold; 对加注粘池
**LAG** — 松凶: 高频率加注+bluff; stealBlind; 宽范围施压
**TAG** — 紧凶(标杆): 含HandPlan框架; SmartContext全量; 价值导向+可控bluff
**NIT** — 超紧: 仅强牌入池; 高foldToPressure; 极少bluff
**MANIAC** — 疯狂: 引擎内联大段逻辑; 全范围激进; 无视牌力频繁加注

Each card shows: key behavior pattern + primary StyleProfile knobs

### Section 3: 共用模块（中央区）

**DpNpcEngine 公共能力**:
- isBotPlayer / getBotTypeByNickname (前缀+遗留昵称识别)
- decideActionIfReady (行动位校验+延时)
- decideBotAction (翻前→统一策略; 翻后→switch)
- estimateCurrentStrength → DpUtilHandEvaluator → SimpleStrength
- buildSmartContext (TAG/MANIAC翻后)
- STYLE_PROFILE_MAP (NpcStyle→StyleProfile 数值旋钮)

**rule-think 思考延时**:
enabled=true → 采样延时(快桶/慢桶)→ nextBotActionTime
enabled=false → 即时决策(0ms)
snap-probability: 0.18 (18%本tick秒出)
max-ms: 4000

**辅助模块**: DpUtilHandEvaluator(牌力) | table-talk(桌边话) | HandPlan框架(TAG)

### Section 4: LLM Bot 异步流水线（右侧）

**BOT_LLM** (单轮):
心跳→LLM 昵称识别→规则引擎return null→DpLlmNpcDecisionService
→ buildLlmNpcGameSnapshot→LlmNpcUserSnapshot.formatUserPayload
→ OpenAiCompatibleChatClient(方舟)→JSON解析→normalizeAndClamp→BotAction

**BOT_LLM_GLOBAL** (多轮):
同handSeed内累积user/assistant→LlmNpcGlobalHandConversationStore
system prompt含plan_next→输出含推理链+行动

**安全机制**:
- in-flight key 防重入
- 回包校验: 房间阶段/行动位/快照代数过期则丢弃
- 解析失败→fallback(fold/check)保证不卡死
- PRE_API_DELAY_MS = 0 (无额外拖延)

### Section 5: 全局开关（底部条）

NPC_MOOD_ENABLED = false | NPC_HAND_SEED_FOR_DECISIONS = true
rule-think.enabled = true | snap-probability = 0.18 | max-ms = 4000

## Text Labels (in Chinese)
- 翻后策略分派
- 公共前置计算
- FISH 被动鱼
- CALL 跟注站
- LAG 松凶
- TAG 紧凶标杆
- NIT 超紧
- MANIAC 疯狂
- SimpleStrength 牌力
- SmartContext 上下文
- rule-think 思考延时
- LLM 异步决策流水线
- BOT_LLM 单轮
- BOT_LLM_GLOBAL 多轮
- normalizeAndClamp
- fallback 兜底
- StyleProfile 风格旋钮
- HandPlan 框架
