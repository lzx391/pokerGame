牌谱模块：手牌历史查询、回放与导出。

## 目录

| 路径 | 说明 |
|---|---|
| `pages/HandHistoryPage.vue` | `/hand-history` 列表页 |
| `pages/HandHistoryDetailPage.vue` | `/hand-history/detail/:id` 详情页 |
| `components/DpHandHistoryViewer.vue` | retro8bit CRT 列表（大厅/对局内嵌） |
| `components/DpHandHistoryDetail.vue` | retro8bit CRT 详情（大厅/对局/成就墙） |
| `utils/dpHandHistoryReplay.js` | 按街拆分行动、洞牌展示等回放工具 |
| `api/historyApi.js` | `/dpHandHistory/*` REST 薄封装 |

旧路径 `components/HandHistory*.vue`、`utils/dpHandHistoryReplay.js` 保留 re-export。
