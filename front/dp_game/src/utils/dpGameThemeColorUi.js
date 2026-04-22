/**
 * 自定义主题调色板：CSS 颜色字符串 ↔ 取色器 / 透明度
 */

function clamp(n, lo, hi) {
  return Math.max(lo, Math.min(hi, n))
}

function round2(x) {
  return Math.round(x * 100) / 100
}

/**
 * 解析 #rgb #rrggbb、rgb()、rgba()；失败返回 null
 */
export function parseCssColor(str) {
  if (str == null) return null
  var s = String(str).trim()
  if (s === '' || s === 'none') return null
  var m
  m = /^#([0-9a-fA-F]{3})$/.exec(s)
  if (m) {
    var h = m[1]
    return {
      r: parseInt(h[0] + h[0], 16),
      g: parseInt(h[1] + h[1], 16),
      b: parseInt(h[2] + h[2], 16),
      a: 1
    }
  }
  m = /^#([0-9a-fA-F]{6})$/.exec(s)
  if (m) {
    var x = m[1]
    return {
      r: parseInt(x.slice(0, 2), 16),
      g: parseInt(x.slice(2, 4), 16),
      b: parseInt(x.slice(4, 6), 16),
      a: 1
    }
  }
  m = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/.exec(s)
  if (m) {
    return {
      r: clamp(Number(m[1]), 0, 255),
      g: clamp(Number(m[2]), 0, 255),
      b: clamp(Number(m[3]), 0, 255),
      a: m[4] !== undefined && m[4] !== '' ? clamp(Number(m[4]), 0, 1) : 1
    }
  }
  return null
}

export function rgbToHex6(r, g, b) {
  function x(n) {
    var v = Math.max(0, Math.min(255, Math.round(n)))
    var h = v.toString(16)
    return h.length === 1 ? '0' + h : h
  }
  return ('#' + x(r) + x(g) + x(b)).toLowerCase()
}

/** 写入 localStorage / CSS 变量：不透明用 #hex，否则 rgba */
export function formatCssColor(rgb, alpha) {
  var a = alpha != null ? clamp(Number(alpha), 0, 1) : 1
  if (a >= 0.999) return rgbToHex6(rgb.r, rgb.g, rgb.b)
  return (
    'rgba(' +
    Math.round(rgb.r) +
    ',' +
    Math.round(rgb.g) +
    ',' +
    Math.round(rgb.b) +
    ',' +
    round2(a) +
    ')'
  )
}

/** 用于 <input type="color">，忽略透明度 */
export function cssToHexInput(str) {
  var p = parseCssColor(str)
  if (!p) return '#888888'
  return rgbToHex6(p.r, p.g, p.b)
}

/** 0–100，用于 range */
export function cssToAlphaPercent(str) {
  var p = parseCssColor(str)
  if (!p) return 100
  return Math.round(p.a * 100)
}

export function colorsCssEquivalent(a, b) {
  var pa = parseCssColor(a)
  var pb = parseCssColor(b)
  if (!pa || !pb) return String(a || '').trim() === String(b || '').trim()
  return (
    Math.round(pa.r) === Math.round(pb.r) &&
    Math.round(pa.g) === Math.round(pb.g) &&
    Math.round(pa.b) === Math.round(pb.b) &&
    round2(pa.a) === round2(pb.a)
  )
}

/** 是否含渐变等，无法用取色器表示模板当前值 */
export function isNonParsableColorToken(str) {
  if (str == null) return true
  var s = String(str).trim()
  if (s === '') return true
  if (/gradient\(/i.test(s) || /^url\(/i.test(s)) return true
  return parseCssColor(s) == null
}
