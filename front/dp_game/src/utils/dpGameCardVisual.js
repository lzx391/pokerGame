/** 后端牌格式: "hearts_A", "spades_10" — 展示用 */

export function getCardDisplay(c) {
  if (!c || !c.includes('_')) return '?'
  return c.split('_')[1]
}

export function getCardClass(c) {
  if (!c || !c.includes('_')) return 'card-base bg-gray'
  var suit = c.split('_')[0]
  if (suit === 'hearts') return 'card-base bg-red'
  if (suit === 'diamonds') return 'card-base bg-blue'
  if (suit === 'clubs') return 'card-base bg-green'
  if (suit === 'spades') return 'card-base bg-black'
  return 'card-base bg-gray'
}
