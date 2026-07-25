/**
 * 进房邀请 / 「好友」Tab 只展示已落地双向关系（服务端 friendship_status=ACCEPTED）。
 * null / 缺省 兼容旧客户端仅返回 friends 数组时的行为。
 * 状态比较忽略大小写与首尾空格，避免序列化或网关改写导致整条名单被滤空。
 *
 * @param {Array<{ userId?: unknown, friendship_status?: string }>} list
 * @returns {Array<Record<string, unknown>>}
 */
export function dpFriendsInviteEligible(list) {
  if (!Array.isArray(list)) return []
  return list.filter(function (f) {
    if (!f) return false
    if (f.friendship_status == null || f.friendship_status === '') return true
    var st = String(f.friendship_status).trim().toUpperCase()
    if (!st) return true
    return st === 'ACCEPTED'
  })
}
