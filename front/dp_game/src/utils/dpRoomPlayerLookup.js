/**
 * 局内按昵称解析真人 userId / 好友头像（观众席仅昵称列表时的前端补全）。
 */

/**
 * @param {Array<{nickname?:string,userId?:number|string}>|null|undefined} players
 * @param {string} nickname
 * @returns {number}
 */
export function findPlayerUserIdByNickname (players, nickname) {
  if (!nickname || !players || !players.length) return 0
  for (var i = 0; i < players.length; i++) {
    var p = players[i]
    if (!p || p.nickname !== nickname) continue
    var uid = p.userId != null && p.userId !== '' ? Number(p.userId) : NaN
    if (uid > 0 && !isNaN(uid)) return uid
  }
  return 0
}

/**
 * @param {Array<{userId?:number|string,nickname?:string,avatarUrl?:string,avatarUpdatedAt?:string|number}>|null|undefined} friends
 * @param {string} [nickname]
 * @param {number} [userId]
 * @returns {object|null}
 */
export function findFriendMeta (friends, nickname, userId) {
  if (!friends || !friends.length) return null
  var wantId = userId != null && userId !== '' ? Number(userId) : 0
  for (var i = 0; i < friends.length; i++) {
    var f = friends[i]
    if (!f) continue
    if (wantId > 0 && !isNaN(wantId) && Number(f.userId) === wantId) return f
    if (nickname && f.nickname === nickname) return f
  }
  return null
}

/**
 * @param {{ players?: Array, friends?: Array, nickname: string, userId?: number }} ctx
 * @returns {{ userId: number, avatarUrl: string, avatarUpdatedAt: string|number|null }}
 */
export function resolveRoomPersonMeta (ctx) {
  var nickname = ctx && ctx.nickname
  var out = { userId: 0, avatarUrl: '', avatarUpdatedAt: null }
  if (!nickname) return out

  var fromPlayers = findPlayerUserIdByNickname(ctx.players, nickname)
  if (fromPlayers > 0) out.userId = fromPlayers

  var uid = ctx.userId != null && ctx.userId !== '' ? Number(ctx.userId) : 0
  if (uid > 0 && !isNaN(uid)) out.userId = uid

  var friend = findFriendMeta(ctx.friends, nickname, out.userId)
  if (friend) {
    if (!out.userId) {
      var fid = Number(friend.userId)
      if (fid > 0 && !isNaN(fid)) out.userId = fid
    }
    if (friend.avatarUrl) out.avatarUrl = friend.avatarUrl
    if (friend.avatarUpdatedAt != null) out.avatarUpdatedAt = friend.avatarUpdatedAt
  }
  return out
}
