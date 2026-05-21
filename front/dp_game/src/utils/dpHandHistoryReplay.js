/**
 * 牌谱回放：按街拆分行动轮次、行动文案（与 dp_observed_hand_history.payload_json 对齐）。
 */

import { DP_GAME_STAGE_LABELS } from '../constants/dpCatThemeCopy'

export const STREET_ORDER = ['preflop', 'flop', 'turn', 'river']

export const STREET_TABS = [
  { key: 'preflop', label: DP_GAME_STAGE_LABELS.preflop },
  { key: 'flop', label: DP_GAME_STAGE_LABELS.flop },
  { key: 'turn', label: DP_GAME_STAGE_LABELS.turn },
  { key: 'river', label: DP_GAME_STAGE_LABELS.river },
  { key: 'settlement', label: DP_GAME_STAGE_LABELS.settled }
]

/**
 * 进入该街前仍在手、可参与本街行动的玩家（按起手座位顺序）。
 */
export function seatNicknamesOrdered(seatsAtStart) {
  if (!Array.isArray(seatsAtStart)) return []
  return seatsAtStart
    .slice()
    .sort((a, b) => (a.seatIndex || 0) - (b.seatIndex || 0))
    .map((s) => s.nickname)
    .filter(Boolean)
}

export function activePlayersBeforeStreet(allActions, street, seatsNicknames) {
  const still = new Set(seatsNicknames)
  for (const st of STREET_ORDER) {
    if (st === street) break
    for (const a of allActions) {
      if (a.stage !== st) continue
      if (a.type === 'FOLD') still.delete(a.actorNickname)
    }
  }
  return still
}

/**
 * 是否视为「加投/开池」：开局底分不算；ALL_IN 仅在抬升总投入（相对需匹配额）时算新一圈起点。
 */
export function isRaiseLikeForRoundSplit(a) {
  if (!a || !a.type) return false
  if (a.type === 'POST_BLIND_SB' || a.type === 'POST_BLIND_BB') return false
  if (a.type === 'RAISE' || a.type === 'BET') return true
  if (a.type === 'ALL_IN') {
    const btc = a.betToCallBefore != null ? Number(a.betToCallBefore) : 0
    const ab = a.actorBetBefore != null ? Number(a.actorBetBefore) : 0
    const amt = a.amount != null ? Number(a.amount) : 0
    return ab + amt > btc
  }
  return false
}

/**
 * 从「第一次加投/开池」起算第1圈；之后每次新的加投/开池开启下一圈；其前的开局底分、跟投、观望等为「前置」列。
 * 若本街没有任何加投类行动（例如全观望），则整街归入第1圈。
 */
export function splitRoundsByRaises(actionsForStreet) {
  const list = Array.isArray(actionsForStreet) ? actionsForStreet : []
  if (!list.length) {
    return { prefix: [], rounds: [] }
  }
  let firstAgg = -1
  for (let i = 0; i < list.length; i++) {
    if (isRaiseLikeForRoundSplit(list[i])) {
      firstAgg = i
      break
    }
  }
  if (firstAgg === -1) {
    return { prefix: [], rounds: [list.slice()] }
  }
  const prefix = list.slice(0, firstAgg)
  const rest = list.slice(firstAgg)
  const rounds = []
  let cur = []
  for (let i = 0; i < rest.length; i++) {
    const a = rest[i]
    if (cur.length > 0 && isRaiseLikeForRoundSplit(a)) {
      rounds.push(cur)
      cur = []
    }
    cur.push(a)
  }
  if (cur.length) rounds.push(cur)
  return { prefix, rounds }
}

/** 起手座位：发牌猫、小猫/大猫标记（与 seatsAtStart.blind 一致） */
export function playerRoleTagsByNickname(seatsAtStart, dealerNickname) {
  const map = {}
  if (!Array.isArray(seatsAtStart)) return map
  for (const s of seatsAtStart) {
    const n = s.nickname
    if (!n) continue
    const tags = []
    if (dealerNickname && n === dealerNickname) tags.push('发牌猫')
    const bl = s.blind != null ? Number(s.blind) : 0
    if (bl === 1) tags.push('SC')
    if (bl === 2) tags.push('BC')
    map[n] = tags
  }
  return map
}

export function formatActionText(a) {
  if (!a) return '—'
  const t = a.type
  const amt = a.amount
  switch (t) {
    case 'POST_BLIND_SB':
      return '小猫' + (amt ? ' ' + amt : '')
    case 'POST_BLIND_BB':
      return '大猫' + (amt ? ' ' + amt : '')
    case 'CHECK':
      return '观望'
    case 'CALL':
      return amt != null && amt > 0 ? '跟投 ' + amt : '跟投'
    case 'BET':
      return '投入 ' + (amt != null ? amt : '')
    case 'RAISE':
      return '加投 ' + (amt != null ? amt : '')
    case 'ALL_IN':
      return '全投' + (amt != null && amt > 0 ? ' ' + amt : '')
    case 'FOLD':
      return '盖牌'
    default:
      return t || '—'
  }
}

/**
 * 按玩家聚合某一圈内的行动（通常一行）。
 */
export function buildRoundGrid(rounds, nicknamesOrdered) {
  const cols = []
  for (let r = 0; r < rounds.length; r++) {
    const byPlayer = {}
    for (const n of nicknamesOrdered) byPlayer[n] = []
    for (const a of rounds[r]) {
      const n = a.actorNickname
      if (!byPlayer[n]) byPlayer[n] = []
      byPlayer[n].push(formatActionText(a))
    }
    const cells = {}
    for (const n of nicknamesOrdered) {
      const parts = byPlayer[n] || []
      cells[n] = parts.length ? parts.join('\n') : '—'
    }
    cols.push(cells)
  }
  return cols
}

export function boardForStreet(boardsByStreet, street) {
  if (!Array.isArray(boardsByStreet)) return []
  const b = boardsByStreet.find((x) => x && x.stage === street)
  return b && Array.isArray(b.communityCards) ? b.communityCards : []
}

/** 该街快照中的牌型展示文案（payload.boardsByStreet[].handRankNameByPlayer） */
export function handRankNameByStreet(boardsByStreet, street) {
  if (!Array.isArray(boardsByStreet) || !street) return {}
  const b = boardsByStreet.find((x) => x && x.stage === street)
  const map = b && b.handRankNameByPlayer
  return map && typeof map === 'object' ? map : {}
}

/** 河牌圈牌型（结算页用） */
export function finalHandRankNameByPlayer(boardsByStreet) {
  const river = handRankNameByStreet(boardsByStreet, 'river')
  if (Object.keys(river).length) return river
  return handRankNameByStreet(boardsByStreet, 'turn')
}

/**
 * 玩家第一次盖牌所在的街（无盖牌则 null）。
 */
export function firstFoldStage(actions, nickname) {
  if (!nickname || !Array.isArray(actions)) return null
  let best = null
  let bestIdx = 999
  for (const a of actions) {
    if (!a || a.type !== 'FOLD' || a.actorNickname !== nickname) continue
    const st = a.stage || ''
    const idx = STREET_ORDER.indexOf(st)
    if (idx < 0) continue
    if (idx < bestIdx) {
      bestIdx = idx
      best = st
    }
  }
  return best
}

/**
 * 在该街标签下是否展示底牌：在本街之前未盖牌（含本街才盖牌的，翻前仍展示）。
 */
export function shouldShowHoleCardsOnStreetTab(firstFoldStreet, tabStreet) {
  if (!tabStreet || tabStreet === 'settlement') return false
  if (!firstFoldStreet) return true
  const fi = STREET_ORDER.indexOf(firstFoldStreet)
  const ti = STREET_ORDER.indexOf(tabStreet)
  if (fi < 0) return true
  if (ti < 0) return false
  return fi > ti
}

/** 终局公共牌（取 boards 中张数最多的一档，一般为河牌 5 张） */
export function finalCommunityCards(boardsByStreet) {
  if (!Array.isArray(boardsByStreet) || !boardsByStreet.length) return []
  let best = []
  for (const b of boardsByStreet) {
    const cc = (b && b.communityCards) || []
    if (cc.length >= best.length) best = cc
  }
  return best
}
