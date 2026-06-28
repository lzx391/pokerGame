/**
 * 对局房间快照指纹：用于在 WS/HTTP 高频推送且数据未变时跳过 Vuex APPLY_ROOM 与整块子树 reconcile。
 * 须覆盖 {@link APPLY_ROOM} 写入的全部字段（及影响牌面/座位的常用 player 字段），否则可能错误省略更新。
 */

/** @type {Readonly<Record<string, number>>} */
var STAGE_ORDER = Object.freeze({
  preflop: 0,
  flop: 1,
  turn: 2,
  river: 3,
  showdown: 4,
  settled: 5
})

function stageOrdinal(stage) {
  if (stage == null || stage === '') return -1
  var o = STAGE_ORDER[stage]
  return o == null ? -1 : o
}

/**
 * 同一手牌内 stage 是否回退（如 settled/showdown → river），通常表示乱序/过期 HTTP 快照。
 */
export function isRoomStageRegressionWithinHand(prevStage, nextStage, prevHandSeed, nextHandSeed) {
  if (Number(prevHandSeed) !== Number(nextHandSeed)) return false
  var prev = stageOrdinal(prevStage)
  var next = stageOrdinal(nextStage)
  if (prev < 0 || next < 0) return false
  return next < prev
}

/**
 * 是否应丢弃比当前 Vuex 更旧的房间快照（并发 loadGame / 多实例 WS 乱序）。
 * @param {object} incoming API / WS room JSON
 * @param {{ currentHandSeed?: number, stage?: string, lastActionTime?: number }} applied 当前已应用的态
 */
export function isRoomSnapshotStale(incoming, applied) {
  if (!incoming || typeof incoming !== 'object' || !applied) return false
  var inHand = Number(incoming.currentHandSeed) || 0
  var apHand = Number(applied.currentHandSeed) || 0
  if (inHand < apHand) return true
  if (inHand > apHand) return false

  var inStage = incoming.currentStage || ''
  var apStage = applied.stage || ''
  var inTime = Number(incoming.lastActionTime) || 0
  var apTime = Number(applied.lastActionTime) || 0
  if (apTime > 0 && inTime > 0 && inTime < apTime) return true
  return isRoomStageRegressionWithinHand(apStage, inStage, apHand, inHand)
}

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
    stableJsonSlice(p.bestHandCards),
    p.mood != null ? String(Number(p.mood)) : '',
    p.moodState != null ? String(p.moodState) : ''
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
    String(Number(room.thinkTimeSeconds) || 0),
    seatParts.join(';')
  ]
  return parts.join('|')
}
