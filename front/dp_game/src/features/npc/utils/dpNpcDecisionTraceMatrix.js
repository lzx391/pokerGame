/** Build street × NPC matrix from TAG decision trace action summaries. */

export var TRACE_STREET_COLUMNS = Object.freeze([
  { key: 'preflop', label: '翻前' },
  { key: 'flop', label: 'Flop' },
  { key: 'turn', label: 'Turn' },
  { key: 'river', label: 'River' }
])

/**
 * @param {string|undefined|null} street
 * @returns {'preflop'|'flop'|'turn'|'river'|null}
 */
export function normalizeTraceStreet(street) {
  var s = String(street || '').trim().toLowerCase()
  if (s === 'preflop' || s === 'pre') return 'preflop'
  if (s === 'flop') return 'flop'
  if (s === 'turn') return 'turn'
  if (s === 'river') return 'river'
  return null
}

/**
 * @param {{ type?: string, amount?: number }|null|undefined} finalAction
 * @returns {string}
 */
export function formatTraceActionShort(finalAction) {
  if (!finalAction || !finalAction.type) return '—'
  return String(finalAction.type).toUpperCase()
}

/**
 * @param {{ type?: string, amount?: number }|null|undefined} finalAction
 * @returns {string}
 */
export function formatTraceActionLabel(finalAction) {
  if (!finalAction || !finalAction.type) return '—'
  var t = String(finalAction.type).toUpperCase()
  if (finalAction.amount != null && finalAction.amount > 0) return t + ' ' + finalAction.amount
  return t
}

/**
 * @param {Array<{ actionId?: string, actionSeq?: number, actorNickname?: string, street?: string, finalAction?: object }>|null|undefined} actions
 * @returns {{
 *   npcs: string[],
 *   streets: typeof TRACE_STREET_COLUMNS,
 *   cells: Record<string, object[]>,
 *   getCellActions: (npc: string, streetKey: string) => object[]
 * }}
 */
export function buildTraceStreetMatrix(actions) {
  var list = Array.isArray(actions) ? actions.slice() : []
  list.sort(function (a, b) {
    return (a.actionSeq || 0) - (b.actionSeq || 0)
  })

  var npcs = []
  var npcSeen = Object.create(null)
  var cells = Object.create(null)

  for (var i = 0; i < list.length; i++) {
    var act = list[i]
    if (!act || !act.actorNickname) continue
    var streetKey = normalizeTraceStreet(act.street)
    if (!streetKey) continue
    var nick = act.actorNickname
    if (!npcSeen[nick]) {
      npcSeen[nick] = true
      npcs.push(nick)
    }
    var cellKey = nick + '|' + streetKey
    if (!cells[cellKey]) cells[cellKey] = []
    cells[cellKey].push(act)
  }

  Object.keys(cells).forEach(function (key) {
    cells[key].sort(function (a, b) {
      return (a.actionSeq || 0) - (b.actionSeq || 0)
    })
  })

  return {
    npcs: npcs,
    streets: TRACE_STREET_COLUMNS,
    cells: cells,
    getCellActions: function (npc, streetKey) {
      return cells[npc + '|' + streetKey] || []
    }
  }
}
