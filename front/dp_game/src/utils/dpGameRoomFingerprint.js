/**
 * 对局房间快照指纹：用于在 WS/HTTP 高频推送且数据未变时跳过 Vuex APPLY_ROOM 与整块子树 reconcile。
 * 须覆盖 {@link APPLY_ROOM} 写入的全部字段（及影响牌面/座位的常用 player 字段），否则可能错误省略更新。
 */

function stableJsonSlice(x) {
  if (x == null) return ''
  try {
    return JSON.stringify(Array.isArray(x) ? x : [])
  } catch (e) {
    return ''
  }
}

/** @param {object} p @returns {string} */
function playerVisualFingerprint(p) {
  if (!p || !p.nickname) return '!'
  return [
    String(p.nickname),
    Number(p.chips) || 0,
    Number(p.bet) || 0,
    p.fold ? '1' : '0',
    p.dealer ? '1' : '0',
    p.ready ? '1' : '0',
    p.leftThisHand ? '1' : '0',
    Number(p.blind) || 0,
    p.allIn ? '1' : '0',
    p.acted ? '1' : '0',
    Number(p.winStreak) || 0,
    Number(p.totalBet) || 0,
    stableJsonSlice(p.holeCards),
    p.handRankName != null ? String(p.handRankName) : '',
    stableJsonSlice(p.bestHandCards)
  ].join('~')
}

/**
 * @param {object} room API / WS JSON 载荷（与 getNowRoom / 推送正文一致）
 * @returns {string}
 */
export function encodeRoomApplyFingerprint(room) {
  if (!room || typeof room !== 'object') return ''
  var pl = room.players || []
  var seatParts = []
  for (var i = 0; i < pl.length; i++) {
    seatParts.push(playerVisualFingerprint(pl[i]))
  }
  var parts = [
    String(room.owner || ''),
    room.playing ? '1' : '0',
    String(Number(room.smallBlindChips) || 0),
    String(Number(room.bigBlindChips) || 0),
    String(Number(room.startingStackBb) || 0),
    String(Number(room.currentHandSeed) || 0),
    String(room.currentStage || ''),
    stableJsonSlice(room.communityCards),
    String(Number(room.pot) || 0),
    stableJsonSlice(room.pots),
    String(Number(room.currentBetToCall) || 0),
    String(Number(room.lastRaiseIncrement) || 0),
    String(Number(room.currentActorIndex) || 0),
    stableJsonSlice(room.spectators),
    stableJsonSlice(room.waitNextHand),
    stableJsonSlice(room.chipLeaderNicknames),
    String(Number(room.myCarryInChips) || 0),
    seatParts.join(';')
  ]
  return parts.join('|')
}
