/** 对局页 UI 主题（id 与 localStorage、data-dp-game-theme 一致；label 为界面展示中文名） */
export const GAME_UI_THEMES = [
  { id: 'default', label: '明亮经典' },
  { id: 'scifi', label: '未来科幻' },
  { id: 'gothic', label: '哥特暗夜' },
  { id: 'macau', label: '澳门风云' },
  { id: 'midnight', label: '午夜幽蓝' },
  { id: 'forest', label: '森野秘境' },
  { id: 'sunset', label: '落日熔金' },
  { id: 'ink', label: '水墨丹青' },
  { id: 'strawberry', label: '草莓甜心' },
  { id: 'cotton', label: '绵云轻柔' },
  /** 基于某一预设 + 自定义强调色，见 dpGameCustomTheme.js */
  { id: 'custom', label: '自定义' }
]

export const GAME_UI_THEME_IDS = GAME_UI_THEMES.map(function (t) {
  return t.id
})
