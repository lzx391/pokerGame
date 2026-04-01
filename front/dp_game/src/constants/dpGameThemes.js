/** 对局页 UI 主题（与 localStorage key、data-dp-game-theme 取值一致） */
export const GAME_UI_THEMES = [
  { id: 'default', label: '默认明亮' },
  { id: 'scifi', label: '科幻霓虹' },
  { id: 'gothic', label: '暗黑哥特' },
  { id: 'macau', label: '澳门风云' },
  { id: 'midnight', label: '午夜蓝调' },
  { id: 'forest', label: '森荫秘境' },
  { id: 'sunset', label: '落日熔金' },
  { id: 'ink', label: '水墨宣纸' },
  { id: 'strawberry', label: '草莓软糖' },
  { id: 'cotton', label: '棉花糖云' }
]

export const GAME_UI_THEME_IDS = GAME_UI_THEMES.map(function (t) {
  return t.id
})
