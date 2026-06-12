/** CJK unified ideographs — nickname font routing for retro8bit theme. */
var RE_CHINESE = /[\u4e00-\u9fff]/
/** Latin letters and ASCII digits. */
var RE_LATIN_DIGIT = /[a-zA-Z0-9]/

/**
 * retro8bit nickname font: English/digits only or mixed CJK+Latin → pixel; CJK only → terminal.
 * Empty, whitespace-only, or special-char-only nicknames → terminal (readable fallback).
 *
 * @param {string|null|undefined} nickname
 * @returns {boolean}
 */
export function shouldUsePixelFont (nickname) {
  var text = nickname != null ? String(nickname).trim() : ''
  if (!text) return false
  var hasChinese = RE_CHINESE.test(text)
  var hasLatinDigit = RE_LATIN_DIGIT.test(text)
  if (hasChinese && !hasLatinDigit) return false
  if (!hasChinese && hasLatinDigit) return true
  if (hasChinese && hasLatinDigit) return true
  return false
}

/**
 * CSS class for retro8bit nickname display (no-op outside retro8bit — bind only when theme matches).
 *
 * @param {string|null|undefined} nickname
 * @returns {'dp-nick-font--pixel'|'dp-nick-font--terminal'}
 */
export function getNicknameFontClass (nickname) {
  return shouldUsePixelFont(nickname) ? 'dp-nick-font--pixel' : 'dp-nick-font--terminal'
}
