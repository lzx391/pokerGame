/**
 * 进房邀请 / 「好友」Tab 只展示已落地双向关系（服务端 friendship_status=ACCEPTED）。
 * null 兼容旧客户端仅返回 friends 数组时的行为。
 *
 * @param {Array<{ userId?: unknown, friendship_status?: string }>} list
 * @returns {Array<Record<string, unknown>>}
 */
export function dpFriendsInviteEligible(list) {
  if (!Array.isArray(list)) return []
  return list.filter(function (f) {
    if (!f) return false
    var st = f.friendship_status
    if (st !== undefined && st !== null && st !== '' && st !== 'ACCEPTED') return false
    return true
  })
}
