/**
 * 大厅社交 SSE（邮箱 + 好友私信未读同一通道）。
 */

export function dpHttpBaseUrl() {
  if (typeof window !== 'undefined' && window.dpElectron && window.dpElectron.serverUrl) {
    return window.dpElectron.serverUrl
  }
  return process.env.NODE_ENV === 'production' ? '' : '/dev-api'
}

/**
 * @param {string} token JWT（勿打日志）
 */
export function buildSocialStreamUrl(token) {
  var t = token != null ? String(token).trim() : ''
  if (!t) return ''
  return dpHttpBaseUrl() + '/dp/social/stream?token=' + encodeURIComponent(t)
}

/**
 * 解析 notify / notify-summary 载荷，兼容 Agent2 多种字段名。
 * @param {any} data
 * @returns {{ mailboxUnread: number, friendChatUnreadTotal: number, perFriend: Array<{userId:number,count:number}> }}
 */
export function parseSocialNotifyPayload(data) {
  var out = {
    mailboxUnread: 0,
    friendChatUnreadTotal: 0,
    perFriend: []
  }
  if (!data || typeof data !== 'object') return out
  var mailbox =
    data.mailboxUnread != null
      ? data.mailboxUnread
      : data.mailboxCount != null
        ? data.mailboxCount
        : data.unreadCount != null
          ? data.unreadCount
          : data.count
  var chatTotal =
    data.friendChatUnreadTotal != null
      ? data.friendChatUnreadTotal
      : data.totalUnread != null
        ? data.totalUnread
        : data.chatUnreadTotal
  var pf = data.perFriend
  if (Array.isArray(pf)) {
    out.perFriend = pf
      .map(function (row) {
        var uid = row && row.userId != null ? Number(row.userId) : 0
        var c = row && row.count != null ? Number(row.count) : 0
        if (!isFinite(uid) || uid <= 0) return null
        return { userId: uid, count: isFinite(c) && c > 0 ? Math.floor(c) : 0 }
      })
      .filter(Boolean)
  } else {
    var byFriend = data.friendChatUnreadByFriendUserId
    if (byFriend && typeof byFriend === 'object') {
      Object.keys(byFriend).forEach(function (key) {
        var uid = parseInt(String(key), 10)
        var c = parseInt(String(byFriend[key]), 10)
        if (isFinite(uid) && uid > 0 && isFinite(c) && c > 0) {
          out.perFriend.push({ userId: uid, count: Math.floor(c) })
        }
      })
    }
  }
  var mv = parseInt(String(mailbox), 10)
  var tv = parseInt(String(chatTotal), 10)
  out.mailboxUnread = isFinite(mv) && mv > 0 ? mv : 0
  out.friendChatUnreadTotal = isFinite(tv) && tv > 0 ? tv : 0
  return out
}

/**
 * 解析 SSE {@code friendPresence} 载荷（与 GET /dp/friends 的 presence 同口径）。
 * @param {any} data
 * @returns {{ friendUserId: number, presence: string, reason: string } | null}
 */
export function parseFriendPresencePayload(data) {
  if (!data || typeof data !== 'object') return null
  var uid =
    data.friendUserId != null
      ? data.friendUserId
      : data.userId != null
        ? data.userId
        : 0
  var id = parseInt(String(uid), 10)
  if (!isFinite(id) || id <= 0) return null
  var presence = data.presence != null ? String(data.presence).trim().toUpperCase() : ''
  if (!presence) return null
  var reason = data.reason != null ? String(data.reason) : ''
  return { friendUserId: id, presence: presence, reason: reason }
}
