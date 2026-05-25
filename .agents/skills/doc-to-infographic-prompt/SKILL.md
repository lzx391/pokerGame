---
name: doc-to-infographic-prompt
description: |
  [Manual] 把学习笔记/技术文档（.md 或 .txt）转成信息图生成 prompt。当你提到"笔记生成信息图"、"文档配图"、"生成信息图 prompt"、"markdown 转信息图"、"做个知识卡片"、或者给了一个 .md 文件想要可视化，就应该用这个 skill。输出一个 prompts/infographic.md 文件，用户复制到豆包/通义千问即可生图，不需要调付费 API。
---

# 文档转信息图 Prompt 生成器

把一篇 Markdown/纯文本笔记转成一份完整的信息图生成提示词（prompt），用户拿去豆包、通义千问等 AI 图片工具一键生图。

**核心理念**：只需要产出最后的 prompt 文件，不调任何付费图片 API。

## 输入

接受以下任一形式：
- 用户提供文件路径（`.md` / `.txt`）
- 用户直接把文字贴在对话里
- 用户说"把这个笔记转成信息图 prompt"

## 工作流

### Step 1: 读内容并判断类型

| 特征 | 分类 |
|------|------|
| 有 `#`、`##` 标题、`**加粗**`、列表、代码块 | Markdown |
| 以上都没有 | 纯文本 |

**纯文本的处理**：先帮用户自动做简单结构化——根据内容断出段落、提炼 3-6 个章节标题，然后继续后续流程。

### Step 2: 分析内容（快速总结）

读完后，用 3-5 句话总结：

- **核心主题**：这篇文章讲什么？
- **关键亮点**：哪些点值得在图中突出？（金句、数据、对比、流程）
- **适合的视觉隐喻**：是流程图？架构拆解？多宫格？对比图？

### Step 3: 推荐布局 × 风格组合

推荐 3-4 个组合，让用户选。参考以下库：

#### Layout 库（21 种）

| Layout | 适合场景 |
|--------|----------|
| `structural-breakdown` | 架构拆解、系统组件说明 |
| `bento-grid` | 多主题并列、知识概览 |
| `linear-progression` | 时间线、流程、教程步骤 |
| `hub-spoke` | 核心概念辐射到子项 |
| `binary-comparison` | A vs B 对比 |
| `circular-flow` | 循环流程、迭代过程 |
| `hierarchical-layers` | 层级金字塔、优先级 |
| `winding-roadmap` | 学习路线、里程碑 |
| `dashboard` | 数据指标、KPI |
| `tree-branching` | 分类、树状结构 |
| `funnel` | 转化漏斗、过滤流程 |
| `bridge` | 问题→解决方案 |
| `comparison-matrix` | 多维度对比 |
| `iceberg` | 表象 vs 深层 |
| `periodic-table` | 分类集合 |
| `comic-strip` | 叙事、故事序列 |
| `venn-diagram` | 概念重叠 |
| `jigsaw` | 拼图、相互关联 |
| `dense-modules` | 高密度信息、多模块数据 |
| `isometric-map` | 空间关系、立体展示 |
| `story-mountain` | 情节结构、张力弧线 |

#### Style 库（22 种）

| Style | 描述 |
|-------|------|
| `technical-schematic` | 蓝图工程风，深蓝底+白线+网格 |
| `craft-handmade` | 手绘纸艺风，温暖亲和 |
| `chalkboard` | 黑板粉笔风，教学感 |
| `ikea-manual` | 极简说明书画风 |
| `corporate-memphis` | 扁平矢量，商务活泼 |
| `storybook-watercolor` | 水彩柔和，童趣 |
| `cyberpunk-neon` | 霓虹未来风 |
| `aged-academia` | 复古学术风，泛黄稿纸 |
| `bold-graphic` | 漫画风，大胆配色 |
| `origami` | 折纸几何风 |
| `pixel-art` | 复古 8-bit 像素 |
| `ui-wireframe` | 灰度线框图 |
| `subway-map` | 地铁线路图 |
| `knolling` | 扁平排列，整洁 |
| `kawaii` | 日系可爱风 |
| `claymation` | 黏土 3D 风 |
| `lego-brick` | 乐高积木风 |
| `pop-laboratory` | 实验室蓝图网格风 |
| `morandi-journal` | 手绘涂鸦+莫兰迪色调 |
| `retro-pop-grid` | 70 年代复古波普 |
| `hand-drawn-edu` | 马克龙粉彩+手绘线条 |
| `retro-popup-pop` | 复古拼贴+粗轮廓 |

**推荐逻辑**：
- 技术文档 → `structural-breakdown` + `technical-schematic`
- 学习笔记 → `bento-grid` + `craft-handmade` 或 `chalkboard`
- 流程教程 → `linear-progression` + `ikea-manual` 或 `hand-drawn-edu`
- 对比分析 → `binary-comparison` + `corporate-memphis`
- 知识体系 → `hub-spoke` + `chalkboard`

用 `AskUserQuestion` 一次问完：**布局×风格** + **比例**（landscape 16:9 / portrait 9:16 / square 1:1）。

### Step 4: 生成结构化内容

基于选定的布局和风格，把文档内容转成可视化结构。遵循原则：

- **不添加新信息**，原始内容忠实保留
- **提取关键标签**：每个版块控制在 15-30 字的标签文字
- **确定视觉层次**：标题 → 版块标题 → 标签 → 说明文字
- **布局映射**：把文档的章节映射到布局的各个区域

### Step 5: 生成最终 Prompt 文件

输出路径：`{文档所在目录}/infographic/{文档标题-slug}/prompts/infographic.md`

如文档在 `docs/` 下，目录结构：
```
docs/infographic/{slug}/
├── analysis.md
├── prompts/
│   └── infographic.md      ← ⭐ 最终产物，发给豆包
```

Prompt 模板：
```
Create a professional infographic following these specifications:

## Image Specifications
- Type: Infographic
- Layout: {选定的 layout}
- Style: {选定的 style}
- Aspect Ratio: {选定的 aspect ratio}
- Language: zh (Chinese)

## Style Guidelines
{从 Step 3 的 Style 描述中提取颜色、质感、字体要点}

## Layout Structure
{布局各区域说明}

## Content
{结构化后的内容：标题、版块、标签文字、数据点}

## Text Labels (in Chinese)
{所有需要出现在图中的文字标签，一行一个}
```

### Step 6: 交付

报告：
- ✅ prompt 文件路径
- 📐 选定的 layout + style + 比例
- ⏭ 下一步：复制 prompt 文件内容到豆包/通义千问 → 生图

---

## 注意事项

- **别调图片 API**：我们只产 prompt 文件，不负责实际生图
- **保留原文**：结构化时不要改写、删减原内容
- **标签要短**：每个标签 15-30 字，适合信息图展示
- **中文优先**：所有标签用中文
- 如果用户同时装了 `baoyu-format-markdown`，可选：先生成 info 图 prompt，再建议用户跑格式化
