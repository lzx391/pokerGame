import { GAME_UI_THEME_IDS } from '../constants/dpGameThemes'

var STORAGE_KEY = 'dp_game_ui_theme'
var DEFAULT_ID = 'default'

export function readGameTheme() {
  try {
    var v = localStorage.getItem(STORAGE_KEY)
    if (v && GAME_UI_THEME_IDS.indexOf(v) !== -1) {
      return v
    }
  } catch (e) { /* ignore */ }
  return DEFAULT_ID
}

export function writeGameTheme(themeId) {
  try {
    if (GAME_UI_THEME_IDS.indexOf(themeId) !== -1) {
      localStorage.setItem(STORAGE_KEY, themeId)
    }
  } catch (e) { /* ignore */ }
}
