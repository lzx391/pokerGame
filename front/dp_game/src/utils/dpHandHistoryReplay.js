/**
 * 牌谱回放：按街拆分下注圈、行动文案（与 dp_observed_hand_history.payload_json 对齐）。
 */

export const STREET_ORDER = ['preflop', 'flop', 'turn', 'river']

export const STREET_TABS = [
  { key: 'preflop', label: '翻前' },
  { key: 'flop', label: '翻后' },
  { key: 'turn', label: '转' },
  { key: 'river', label: '河' },
  { key: 'settlement', label: '结算' }
]

/**
 * 进入该街前仍在手、可参与本街下注的玩家（按起手座位顺序）。
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

function allMatched(contrib, active, maxBet) {
  if (active.size === 0) return false
  if (maxBet <= 0) return false
  for (const p of active) {
    if ((contrib.get(p) || 0) !== maxBet) return false
  }
  return true
}

function isVoluntaryAggression(a) {
  if (!a) return false
  return a.type === 'RAISE' || a.type === 'BET' || a.type === 'ALL_IN'
}

/**
 * 将一条街内的行动拆成「第1圈、第2圈…」（每轮大家都平注后，若有人再加注则进入下一圈）。
 */
export function splitBettingRounds(actionsForStreet, playersAtStreetStart) {
  const list = Array.isArray(actionsForStreet) ? actionsForStreet : []
  const rounds = []
  let round = []
  const contrib = new Map()
  const active = new Set(playersAtStreetStart)
  for (const n of playersAtStreetStart) contrib.set(n, 0)
  let maxBet = 0

  const apply = (a) => {
    const n = a.actorNickname
    if (a.type === 'FOLD') {
      active.delete(n)
      return
    }
    if (a.type === 'POST_BLIND_SB' || a.type === 'POST_BLIND_BB') {
      const amt = a.amount || 0
      contrib.set(n, (contrib.get(n) || 0) + amt)
      maxBet = Math.max(maxBet, contrib.get(n) || 0)
      return
    }
    const amt = a.amount || 0
    contrib.set(n, (contrib.get(n) || 0) + amt)
    maxBet = Math.max(maxBet, contrib.get(n) || 0)
  }

  for (let i = 0; i < list.length; i++) {
    const a = list[i]
    if (round.length > 0 && allMatched(contrib, active, maxBet) && isVoluntaryAggression(a)) {
      rounds.push(round)
      round = []
    }
    round.push(a)
    apply(a)
  }
  if (round.length) rounds.push(round)
  return rounds
}

export function formatActionText(a) {
  if (!a) return '—'
  const t = a.type
  const amt = a.amount
  switch (t) {
    case 'POST_BLIND_SB':
      return '小盲' + (amt ? ' ' + amt : '')
    case 'POST_BLIND_BB':
      return '大盲' + (amt ? ' ' + amt : '')
    case 'CHECK':
      return '过牌'
    case 'CALL':
      return amt != null && amt > 0 ? '跟注 ' + amt : '跟注'
    case 'BET':
      return '下注 ' + (amt != null ? amt : '')
    case 'RAISE':
      return '加注 ' + (amt != null ? amt : '')
    case 'ALL_IN':
      return '全下' + (amt != null && amt > 0 ? ' ' + amt : '')
    case 'FOLD':
      return '弃牌'
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
      cells[n] = parts.length ? parts.join('；') : '—'
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

/**
 * 玩家第一次弃牌所在的街（无弃牌则 null）。
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
 * 在该街标签下是否展示底牌：在本街之前未弃牌（含本街才弃牌的，翻前仍展示）。
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
