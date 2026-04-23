/**
 * 「自定义」界面主题：选一个预设作模板（data-dp-game-theme），再用调色板覆盖任意 --dp-*（localStorage）。
 */
import { GAME_UI_THEME_IDS } from '../constants/dpGameThemes'

var STORAGE_KEY = 'dp_game_custom_theme'

var DEFAULT_BASE = 'default'
var DEFAULT_ACCENT = '#1890ff'

/** 供 body 上清除内联覆盖时按 key 移除（与历史版本兼容；实际以 lastAppliedBodyVarKeys 为准） */
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

/** 上一次写到 body 上的自定义变量名，用于在下次应用或切回预设时完整清除 */
var lastAppliedBodyVarKeys = []

var DP_VAR_KEY_RE = /^--dp-[a-zA-Z0-9-]+$/

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

/**
 * 仅接受键名匹配 --dp-* 的条目，值为非空字符串（供 localStorage / 调试用 JSON）
 */
export function normalizeOverrides(raw) {
  var out = {}
  if (raw == null) return out
  if (typeof raw === 'string') {
    try {
      raw = JSON.parse(raw)
    } catch (e) {
      return out
    }
  }
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return out
  for (var k in raw) {
    if (!Object.prototype.hasOwnProperty.call(raw, k)) continue
    if (!DP_VAR_KEY_RE.test(k)) continue
    var val = raw[k]
    if (val == null) continue
    var s = String(val).trim()
    if (s === '') continue
    out[k] = s
  }
  return out
}

export function readCustomTheme() {
  var def = { baseId: DEFAULT_BASE, accent: DEFAULT_ACCENT, overrides: {} }
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
    var overrides = normalizeOverrides(o.overrides)
    /* 旧版只存强调色、无 overrides：迁入调色板并写回，避免刷新后无法真正清空自定义 */
    if (Object.keys(overrides).length === 0 && accent !== DEFAULT_ACCENT) {
      overrides = buildCustomThemeVars(accent)
      accent = DEFAULT_ACCENT
      try {
        localStorage.setItem(
          STORAGE_KEY,
          JSON.stringify({
            baseId: baseId,
            accent: accent,
            overrides: overrides
          })
        )
      } catch (e) {
        /* ignore */
      }
    }
    return { baseId: baseId, accent: accent, overrides: overrides }
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
        accent: data.accent,
        overrides: normalizeOverrides(data.overrides)
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

/**
 * 仅应用用户在调色板里显式覆盖的变量；其余由当前「基于」预设的 CSS 提供。
 * accentHex 保留参数以兼容旧调用，不再参与合并。
 */
export function mergeCustomThemeVars(accentHex, overrides) {
  return normalizeOverrides(overrides)
}

export function applyCustomThemeToBody(accentHex, overrides) {
  clearCustomThemeFromBody()
  var merged = mergeCustomThemeVars(accentHex, overrides)
  lastAppliedBodyVarKeys = Object.keys(merged)
  for (var i = 0; i < lastAppliedBodyVarKeys.length; i++) {
    var key = lastAppliedBodyVarKeys[i]
    document.body.style.setProperty(key, merged[key])
  }
}

export function clearCustomThemeFromBody() {
  var toClear = lastAppliedBodyVarKeys.slice()
  lastAppliedBodyVarKeys = []
  var i
  for (i = 0; i < toClear.length; i++) {
    document.body.style.removeProperty(toClear[i])
  }
  for (i = 0; i < CUSTOM_THEME_CSS_KEYS.length; i++) {
    document.body.style.removeProperty(CUSTOM_THEME_CSS_KEYS[i])
  }
}
