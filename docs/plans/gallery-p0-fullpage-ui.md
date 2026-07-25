# Gallery P0 — 全页画廊 + 介绍信编辑

> 状态：已确认，待实现  
> 确认人：用户 2026-06-21

## 已确认决策

| # | 项 | 结论 |
|---|-----|------|
| 权限 | 介绍信编辑 | 统一 `gallery:view`，有权限即可，非管理员专属 |
| 2 | 介绍信收起 | 当次访问（不持久化 sessionStorage） |
| 3 | 编辑入口 | 路由 param：`/gallery?mode=edit` 看自己可编辑 |
| 4 | 看别人 | `/gallery/users/:userId` |
| 5 | 文案 | **介绍信**（非手写信） |
| 6 | 权限码 | 继续单一 `gallery:view` 管读写 |
| 范围 | 分期 | **仅 P0** |

## 路由

| 路径 | 模式 | 说明 |
|------|------|------|
| `/gallery` | `?mode=edit` 可选 | 当前用户自己的画廊；有 `gallery:view` 且 edit 时可编辑介绍信/作品 |
| `/gallery/users/:userId` | 只读 | 查看他人画廊 |

- 注册 `isLobbyRoute`（`App.vue`、`dpRouteTransition.js`）
- 过渡动画对齐 `/hand-history`

## 入口改造（P0 必做）

| 原入口 | 改为 |
|--------|------|
| `HomeProfileModal`「我的画廊」 | `router.push({ path: '/gallery', query: { mode: 'edit' } })` |
| `GamePlayerSocialSheet`「查看画廊」 | `router.push('/gallery/users/' + userId)` |

关闭弹窗后再跳转（或先关 modal 再 push）。

## GalleryPage 结构

```
GalleryPage (100vh, 非 el-dialog)
├── header: 返回(router.back) + 标题(昵称的画廊)
├── DpGalleryLetterPanel (介绍信)
│   ├── 默认展开
│   ├── 点击标题/箭头收起
│   ├── 只读：他人 / 自己 view 模式
│   └── 编辑：自己 + mode=edit + hasPerm(gallery:view)
│       ├── textarea + 保存 / 清空
│       └── API: GET/POST/DELETE /dp/gallery/letter*
└── DpGalleryWall (画廊)
    ├── 窄屏 <768px: 纵向 scroll-snap
    └── 宽屏 ≥768px: 横向 scroll-snap
```

## 画框分档（客户端 img.onload）

| 档位 | ratio = w/h | CSS class |
|------|-------------|-----------|
| portrait | < 0.85 | `gallery-frame--portrait` |
| square | 0.85–1.15 | `gallery-frame--square` |
| landscape | > 1.15 | `gallery-frame--landscape` |

- `object-fit: contain` 入框，不统一 crop
- 画框：mat 留白 + 细边框 + 轻阴影；stagger 入场动画
- caption 在框下；lazy load

## 视觉方向

- 必读：`/.agents/skills/frontend-design/SKILL.md`
- 必读：`/.agents/skills/ui-ux-pro-max/SKILL.md`
- 艺术画廊/editorial 感，沿用 `--dp-*`，避免 generic AI slop
- a11y: 44px 触控、aria-label、prefers-reduced-motion

## API（现有，不改契约）

- 介绍信：`GET /dp/gallery/letter`, `POST /dp/gallery/writeLetter?letter=`, `DELETE /dp/gallery/letter`
- 自己的作品：`GET/POST/DELETE /dp/gallery/items`, `PUT /items/{id}`
- 他人：`GET /dp/gallery/users/{userId}/letter`, `GET .../items`

## 本期不做

- 拖拽 sort_order、换图、gallery:edit 拆分、游戏内 overlay、删旧弹窗组件（可保留 deprecated）

## 验收

1. 有权限用户：资料卡 → 全页画廊 → 编辑介绍信保存 → 刷新仍在
2. 社交 sheet 看别人 → `/gallery/users/:id` 全页，介绍信只读，可收起
3. 宽屏横向滚动画廊；窄屏纵向
4. 竖/横/方图不同画框
5. 无权限：403 友好提示 + 返回

## 测试

- 手动 UI 上述 5 条
- 可选 playwright 路由 smoke（非必须 P0 阻断）
