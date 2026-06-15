/** 标准 52 张牌编码，与后端 {@code DpDeckUtil} 一致。 */

export var DP_SUITS = ['hearts', 'diamonds', 'clubs', 'spades']
export var DP_RANKS = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K', 'A']

export var DP_SUIT_LABELS = {
  hearts: '♥',
  diamonds: '♦',
  clubs: '♣',
  spades: '♠'
}

export function allDeckCardCodes() {
  var out = []
  for (var si = 0; si < DP_SUITS.length; si++) {
    var suit = DP_SUITS[si]
    for (var ri = 0; ri < DP_RANKS.length; ri++) {
      out.push(suit + '_' + DP_RANKS[ri])
    }
  }
  return out
}

/** 发牌顺序提示：各玩家底牌 2N 张 + flop3 + turn1 + river1 */
export function suggestedPrefixLength(playerCount) {
  var n = parseInt(playerCount, 10)
  if (isNaN(n) || n < 1) return 0
  return 2 * n + 5
}
