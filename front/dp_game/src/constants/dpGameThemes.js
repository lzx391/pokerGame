/** 对局页 UI 主题（id 与 localStorage、data-dp-game-theme 一致；label 为界面展示中文名） */
export const GAME_UI_THEMES = [
  { id: 'default', label: '明亮经典' },
  { id: 'retro8bit', label: '8bit 终端' },
  { id: 'gothic', label: '哥特暗夜' },
  { id: 'strawberry', label: '草莓甜心' },
  { id: 'halloween', label: '万圣惊魂' }
]

export const GAME_UI_THEME_IDS = GAME_UI_THEMES.map(function (t) {
  return t.id
})
