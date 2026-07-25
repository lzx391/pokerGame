/** 前端牌型展示脱敏：仅映射皇家同花顺 / 同花顺两档，服务端与历史 JSON 仍可能带旧名 */

var ROYAL_OLD = '皇家同花顺'
var ROYAL_NEW = '超级火箭'
var SF_OLD = '同花顺'
var SF_NEW = '火箭'

/**
 * @param {string|null|undefined} raw
 * @returns {string}
 */
export function displayHandRankName(raw) {
  if (raw == null) return ''
  var s = String(raw).trim()
  if (!s) return ''
  if (s === ROYAL_OLD) return ROYAL_NEW
  if (s === SF_OLD) return SF_NEW
  return s
}

/**
 * @param {string|null|undefined} raw
 * @returns {string}
 */
export function displayHandRankDetail(raw) {
  if (raw == null) return ''
  var s = String(raw).trim()
  if (!s) return ''
  if (s.indexOf(ROYAL_OLD) !== -1) {
    if (s.indexOf('10-J-Q-K-A') !== -1) return ROYAL_NEW + '（顶顺同花）'
    return s.replace(ROYAL_OLD, ROYAL_NEW)
  }
  if (s.indexOf(SF_OLD) === 0) {
    return s.replace(SF_OLD, SF_NEW)
  }
  return s
}
