import { GAME_UI_THEME_IDS } from '../constants/dpGameThemes'

var STORAGE_KEY = 'dp_game_ui_theme'
var LEGACY_CUSTOM_THEME_STORAGE_KEY = 'dp_game_custom_theme'
var LEGACY_CUSTOM_THEME_ID = 'custom'
var DEFAULT_ID = 'default'

/** 移除已下架的「自定义」主题及其 localStorage 残留，回退为 default */
export function migrateLegacyCustomTheme() {
  try {
    var v = localStorage.getItem(STORAGE_KEY)
    if (v === LEGACY_CUSTOM_THEME_ID) {
      localStorage.setItem(STORAGE_KEY, DEFAULT_ID)
    }
    localStorage.removeItem(LEGACY_CUSTOM_THEME_STORAGE_KEY)
  } catch (e) { /* ignore */ }
}

export function readGameTheme() {
  migrateLegacyCustomTheme()
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
