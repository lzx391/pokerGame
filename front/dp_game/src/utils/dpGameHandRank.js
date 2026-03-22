/**
 * @param holeCards - 玩家手牌，如 ["hearts_A", "spades_K"]
 * @param communityCards - 公共牌
 * @returns 牌型名称，如 "皇家同花顺"、"同花顺" 等
 */
export function getHandRank(holeCards, communityCards) {
  if (!holeCards || holeCards.length < 2 || !communityCards || communityCards.length < 3) {
    return '牌不足'
  }

  var allCards = holeCards.concat(communityCards)

  var rankMap = {
    '2': 2,
    '3': 3,
    '4': 4,
    '5': 5,
    '6': 6,
    '7': 7,
    '8': 8,
    '9': 9,
    '10': 10,
    'J': 11,
    'Q': 12,
    'K': 13,
    'A': 14
  }
  var parsed = []
  for (var i = 0; i < allCards.length; i++) {
    var c = allCards[i]
    var parts = c.split('_')
    parsed.push({ suit: parts[0], rank: rankMap[parts[1]] || 0 })
  }

  var combos = getCombinations(parsed, 5)

  var bestScore = 0
  var bestName = ''

  for (var j = 0; j < combos.length; j++) {
    var result = evaluateHand(combos[j])
    if (result.score > bestScore) {
      bestScore = result.score
      bestName = result.name
    }
  }

  return bestName
}

function getCombinations(arr, k) {
  var result = []
  var combo = []

  function backtrack(start) {
    if (combo.length === k) {
      result.push(combo.slice())
      return
    }
    for (var i = start; i < arr.length; i++) {
      combo.push(arr[i])
      backtrack(i + 1)
      combo.pop()
    }
  }

  backtrack(0)
  return result
}

function evaluateHand(cards) {
  var cardsCopy = cards.slice()
  cardsCopy.sort(function (a, b) {
    return b.rank - a.rank
  })

  var ranks = []
  var suits = []
  for (var i = 0; i < cardsCopy.length; i++) {
    ranks.push(cardsCopy[i].rank)
    suits.push(cardsCopy[i].suit)
  }

  var isFlush = true
  for (var f = 1; f < suits.length; f++) {
    if (suits[f] !== suits[0]) {
      isFlush = false
      break
    }
  }

  var isStraight = false
  var uniqueRanks = []
  for (var u = 0; u < ranks.length; u++) {
    if (uniqueRanks.indexOf(ranks[u]) === -1) uniqueRanks.push(ranks[u])
  }
  if (ranks[0] - ranks[4] === 4 && uniqueRanks.length === 5) {
    isStraight = true
  }
  if (ranks[0] === 14 && ranks[1] === 5 && ranks[2] === 4 && ranks[3] === 3 && ranks[4] === 2) {
    isStraight = true
  }

  var rankCount = {}
  for (var r = 0; r < ranks.length; r++) {
    rankCount[ranks[r]] = (rankCount[ranks[r]] || 0) + 1
  }
  var counts = []
  for (var key in rankCount) {
    counts.push(rankCount[key])
  }
  counts.sort(function (a, b) {
    return b - a
  })

  if (isFlush && isStraight && ranks[0] === 14 && ranks[1] === 13) {
    return { score: 10, name: '皇家同花顺' }
  }

  if (isFlush && isStraight) {
    return { score: 9, name: '同花顺' }
  }

  if (counts[0] === 4) {
    return { score: 8, name: '四条' }
  }

  if (counts[0] === 3 && counts[1] === 2) {
    return { score: 7, name: '葫芦' }
  }

  if (isFlush) {
    return { score: 6, name: '同花' }
  }

  if (isStraight) {
    return { score: 5, name: '顺子' }
  }

  if (counts[0] === 3) {
    return { score: 4, name: '三条' }
  }

  if (counts[0] === 2 && counts[1] === 2) {
    return { score: 3, name: '两对' }
  }

  if (counts[0] === 2) {
    return { score: 2, name: '一对' }
  }

  return { score: 1, name: '高牌' }
}
