/**
 * 「自定义」界面主题：选一个预设作底 + 强调色，派生若干 --dp-* 覆盖（localStorage，非数据库）。
 */
import { GAME_UI_THEME_IDS } from '../constants/dpGameThemes'

var STORAGE_KEY = 'dp_game_custom_theme'

var DEFAULT_BASE = 'default'
var DEFAULT_ACCENT = '#1890ff'

/** 供 body 上清除内联覆盖时按 key 移除 */
export var CUSTOM_THEME_CSS_KEYS = [
  '--dp-accent',
  '--dp-cyan',
  '--dp-btn-primary-bg',
  '--dp-btn-primary-fg',
  '--dp-btn-ghost-border',
  '--dp-btn-ghost-fg',
  '--dp-input-border',
  '--dp-panel-border',
  '--dp-subpanel-border',
  '--dp-player-border-me',
  '--dp-timer-ring'
]

export function normalizeAccentHex(v) {
  if (v == null || v === '') return ''
  var s = String(v).trim()
  if (/^#[0-9A-Fa-f]{6}$/.test(s)) return s.toLowerCase()
  if (/^#[0-9A-Fa-f]{3}$/.test(s)) {
    return (
      '#' +
      s[1] +
      s[1] +
      s[2] +
      s[2] +
      s[3] +
      s[3]
    ).toLowerCase()
  }
  return ''
}

function hexToRgb(hex) {
  var h = normalizeAccentHex(hex)
  if (!h) return null
  h = h.replace('#', '')
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16)
  }
}

function rgbToHex(rgb) {
  if (!rgb) return DEFAULT_ACCENT
  function x(n) {
    var s = Math.max(0, Math.min(255, n | 0)).toString(16)
    return s.length === 1 ? '0' + s : s
  }
  return '#' + x(rgb.r) + x(rgb.g) + x(rgb.b)
}

function relativeLuminance(rgb) {
  if (!rgb) return 0
  function lin(c) {
    c = c / 255
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  }
  var R = lin(rgb.r)
  var G = lin(rgb.g)
  var B = lin(rgb.b)
  return 0.2126 * R + 0.7152 * G + 0.0722 * B
}

function fgOnAccent(rgb) {
  return relativeLuminance(rgb) > 0.55 ? '#1a1a1a' : '#ffffff'
}

/**
 * 供 data-dp-game-theme 使用：自定义时套用预设底，否则用当前 id。
 */
export function resolveEffectiveThemeId(gameUiTheme, customThemeBase) {
  var t = gameUiTheme || 'default'
  if (t === 'custom') {
    var b = customThemeBase
    if (b && GAME_UI_THEME_IDS.indexOf(b) !== -1 && b !== 'custom') return b
    return DEFAULT_BASE
  }
  return t
}

export function readCustomTheme() {
  var def = { baseId: DEFAULT_BASE, accent: DEFAULT_ACCENT }
  try {
    var raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return def
    var o = JSON.parse(raw)
    if (!o || typeof o !== 'object') return def
    var baseId = o.baseId
    if (GAME_UI_THEME_IDS.indexOf(baseId) === -1 || baseId === 'custom') {
      baseId = DEFAULT_BASE
    }
    var accent = normalizeAccentHex(o.accent) || def.accent
    return { baseId: baseId, accent: accent }
  } catch (e) {
    return def
  }
}

export function writeCustomTheme(data) {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        baseId: data.baseId,
        accent: data.accent
      })
    )
  } catch (e) {
    /* ignore */
  }
}

/**
 * 由强调色生成覆盖在预设底上的 CSS 变量（对象键为 --dp-*）。
 */
export function buildCustomThemeVars(accentHex) {
  var rgb = hexToRgb(accentHex || DEFAULT_ACCENT)
  if (!rgb) rgb = hexToRgb(DEFAULT_ACCENT)
  if (!rgb) return {}
  var hex = rgbToHex(rgb)
  var fg = fgOnAccent(rgb)
  var r = rgb.r
  var g = rgb.g
  var b = rgb.b
  return {
    '--dp-accent': hex,
    '--dp-cyan': hex,
    '--dp-btn-primary-bg': hex,
    '--dp-btn-primary-fg': fg,
    '--dp-btn-ghost-border': hex,
    '--dp-btn-ghost-fg': hex,
    '--dp-input-border': 'rgba(' + r + ',' + g + ',' + b + ',0.42)',
    '--dp-panel-border': 'rgba(' + r + ',' + g + ',' + b + ',0.28)',
    '--dp-subpanel-border': 'rgba(' + r + ',' + g + ',' + b + ',0.2)',
    '--dp-player-border-me': hex,
    '--dp-timer-ring': hex
  }
}

export function applyCustomThemeToBody(accentHex) {
  clearCustomThemeFromBody()
  var vars = buildCustomThemeVars(accentHex)
  Object.keys(vars).forEach(function (k) {
    document.body.style.setProperty(k, vars[k])
  })
}

export function clearCustomThemeFromBody() {
  CUSTOM_THEME_CSS_KEYS.forEach(function (k) {
    document.body.style.removeProperty(k)
  })
}
