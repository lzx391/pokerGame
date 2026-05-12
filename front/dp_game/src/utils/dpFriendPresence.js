/**
 * GET /dp/friends 返回的好友在线状态（可选，旧后端可能无此字段）。
 * 上游枚举名：IDLE / IN_GAME（见 DpFriendPresenceState）。
 *
 * @param {Record<string, unknown> | null | undefined} f
 * @returns {'idle' | 'in_game' | 'unknown'}
 */
export function dpFriendPresenceBucket(f) {
  if (!f) return 'unknown'
  var raw = f.presence
  if (raw == null || raw === '') return 'unknown'
  var s = String(raw).trim().toUpperCase()
  if (s === 'IDLE') return 'idle'
  if (s === 'IN_GAME') return 'in_game'
  return 'unknown'
}

/**
 * 用于列表行 class；无状态时不返回类名，沿用默认样式。
 * @param {Record<string, unknown> | null | undefined} f
 * @returns {string}
 */
export function dpFriendPresenceRowClass(f) {
  var b = dpFriendPresenceBucket(f)
  if (b === 'idle') return 'dp-friend-row--presence-idle'
  if (b === 'in_game') return 'dp-friend-row--presence-ingame'
  return ''
}

/**
 * @param {Record<string, unknown> | null | undefined} f
 * @returns {string}
 */
export function dpFriendPresenceStatusText(f) {
  var b = dpFriendPresenceBucket(f)
  if (b === 'idle') return '空闲'
  if (b === 'in_game') return '游戏中'
  return ''
}
