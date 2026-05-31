/** @returns {string[]} */
export function extractPlayerNicknames(players) {
  if (!players || !Array.isArray(players)) return []
  var out = []
  for (var i = 0; i < players.length; i++) {
    var p = players[i]
    if (!p) continue
    var nick = (p.nickname != null ? String(p.nickname) : '').trim()
    if (nick) out.push(nick)
  }
  return out
}

/** @returns {string[]} newly added nicknames */
export function diffNewSeatNicknames(prevSet, nextList) {
  var added = []
  if (!nextList || !nextList.length) return added
  var prev = prevSet instanceof Set ? prevSet : new Set(prevSet || [])
  for (var i = 0; i < nextList.length; i++) {
    var nick = nextList[i]
    if (nick && !prev.has(nick)) added.push(nick)
  }
  return added
}
