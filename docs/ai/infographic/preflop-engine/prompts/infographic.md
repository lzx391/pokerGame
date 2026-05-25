Create a professional infographic following these specifications:

## Image Specifications
- **Type**: Infographic
- **Layout**: linear-progression (step-by-step pipeline variant)
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
- Accent color: amber (#F59E0B) for callout dots, key probabilities
- Engineering blueprint feel, clean vector shapes, consistent stroke weights

## Layout Structure

**Pipeline flows left to right** in 4 stages, top to bottom:
Stage 1: Input → Spot 判定
Stage 2: lateFactor → TablePosition
Stage 3: rangeLevel → 13×13 Matrix Lookup
Stage 4: Per-Spot Decision Flow (4 parallel branches)

## Content

### Main Title
规则 NPC 翻前统一决策引擎

### Subtitle
从 Spot 判定 → lateFactor → rangeLevel → 13×13 矩阵查表 → 最终出牌：完整流水线

### Stage 1: 输入与 Spot 判定

**输入**: room, hero, callAmount, callRatio, vpip/pfr/callStation/foldToPressure, mood, random, botType
**Spot 判定** (spotBy):
| raiseLevel=3+ → FACING_4BET
| raiseLevel=2 → FACING_3BET
| raiseLevel=1 & callAmount>0 → FACING_OPEN
| callAmount=0 → UNOPENED
| else → UNKNOWN → CALL_OR_CHECK

### Stage 2: 位置计算

computePreflopLateFactor(room, hero):
- UTG 起点 = (dealerIndex+3)%N
- rank = 1-based position among active players
- lateFactor = (rank-1)/(active-1) ∈ [0,1]

pseudoTablePosition(lateFactor, blind):
- blind=1/2 → BLINDS
- lateFactor<0.34 → EARLY
- <0.67 → MIDDLE
- else → LATE

### Stage 3: rangeLevel 与 13×13 矩阵

**computeRangeLevel**: base=4
| active≤3 +2 | ≤5 +1 | effStack≥50 +1 | ≤16 -1
| + round((vpip-0.3)×5) + round(callStation) - round(foldToPressure×0.8) + round(mood)
| + rangeLevelBonus(botType): TAG=0, Shark+1, Maniac+2
| clamp(1,8)

**13×13 矩阵** (位置×rangeLevel×局面):
4 slices per position×level (8×4=32 matrices):
- openAllow — UNOPENED 是否 open
- vsOpenContinueAllow — FACING_OPEN 是否继续
- vsOpen3BetValueAllow — 价值 3bet 资格
- vs3BetContinueAllow — FACING_3BET 是否继续
- vs3Bet4BetValueAllow — 价值 4bet 资格

查表: matrix[13][13], i>j=杂色, i<j=同色, i=j=对子

### Stage 4: 四场景决策流

**UNOPENED**: canOpen? → YES: openSizeBB(2-4BB+jitter) → RAISE | NO: CALL_OR_CHECK

**FACING_OPEN**: canContinue? → NO: FOLD | YES: can3Bet? → prob=0.62(val)/0.16(bluff) × pfrAggression → RAISE or CALL

**FACING_3BET**: canContinue? → NO: FOLD | YES: callRatio≥0.35高压支路 → FOLD/CALL | else: 4bet prob → RAISE/ALL_IN or CALL

**FACING_4BET**: callRatio + effStackBB + facing4Bet matrix → FOLD / CALL / ALL_IN

### Bottom: 关键数值速查
- baseOpenSizeBB: 2-4BB
- 3bet 价值概率基值: 0.62
- 3bet 诈唬概率基值: 0.16
- pfrAggressionScale: 0.35+0.65×pfr
- 全局开关: NPC_HAND_SEED_FOR_DECISIONS=true

## Text Labels (in Chinese)
- 翻前统一决策流水线
- Spot 判定
- 位置 lateFactor
- rangeLevel 握紧度
- 13×13 范围矩阵
- UNOPENED 开池
- FACING_OPEN 面对开池
- FACING_3BET 面对再加注
- FACING_4BET 面对三次加注
- 查表 matrixAllows
- openSizeBB 尺度
- pfrAggressionScale
- 价值/诈唬概率
- CALL_OR_CHECK
- FOLD
- RAISE
- ALL_IN
