## DpNpcEngine.java 拆分阅读版（给 Claude/LLM 看的）

你现在的 `DpNpcEngine.java` 约 **3456 行**，直接整文件发给模型不方便。
这里我按“模块”把关键代码片段复制出来，分成多个小文件，你可以只发你关心的那一块。

### 怎么选你要发哪几份？

- **只看 Shark（最推荐）**：
  - `part04_shark_postflop.md`（Shark 翻后决策主流程：fold/call/raise 规则与日志）
  - `part03_context_thinkdelay_entry.md`（入口、思考时间、SmartContext 构建）
  - `part02_profiles_strength_preflop.md`（牌力评估、牌面干湿、翻前分类/翻前决策）

- **想理解“数据怎么被收集成 SmartContext”**：
  - `part03_context_thinkdelay_entry.md`

- **想理解“翻前怎么开池/3bet/短码全下”**：
  - `part02_profiles_strength_preflop.md`

- **想对比 TAG vs SHARK**：
  - `part05_tag_strategy.md`
  - `part04_shark_postflop.md`

### 文件列表

- `part01_config_and_types.md`：配置与核心类型（SharkConfig、BotAction、位置/难度/风格等枚举、HandPlan 初始化片段）
- `part02_profiles_strength_preflop.md`：风格/难度参数表、牌力评估、牌面干湿判断、翻前手牌分类与翻前决策入口
- `part03_context_thinkdelay_entry.md`：对手画像/统计、SmartContext 构建、思考时间、`decideActionIfReady` 入口
- `part04_shark_postflop.md`：`case SHARK` 翻后决策主体（fold 概率合成、c-bet、call/raise、commit threshold、river overbet/block 等）
- `part05_tag_strategy.md`：`case TAG`（更紧的翻前、更简单的翻后）与文件结尾
- `part06_shark_learning_lab.md`：Shark 动态学习实验（旋钮参数）+ 逐街动作日志（只影响 BOT_Shark）

