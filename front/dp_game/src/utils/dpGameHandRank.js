/**
 * 德州 7 选 5 牌型：名称、比牌键（同型带踢脚）、中文精确组合说明。
 */

var RANK_MAP = {
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

/** 用于界面展示的点数（10 显示为 10，非 T） */
export function rankLabel(rank) {
  if (rank === 14) return 'A'
  if (rank === 13) return 'K'
  if (rank === 12) return 'Q'
  if (rank === 11) return 'J'
  return String(rank)
}

/** 解析 7 张，保留原始牌串 id 供 UI 展示最佳五张 */
function parseHoleAndBoard(holeCards, communityCards) {
  if (!holeCards || holeCards.length < 2 || !communityCards || communityCards.length < 3) {
    return null
  }
  var allCards = holeCards.concat(communityCards)
  var parsed = []
  for (var i = 0; i < allCards.length; i++) {
    var c = allCards[i]
    var parts = c.split('_')
    parsed.push({
      suit: parts[0],
      rank: RANK_MAP[parts[1]] || 0,
      id: c
    })
  }
  return parsed
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

function cmpCompareKey(a, b) {
  var len = Math.max(a.length, b.length)
  for (var i = 0; i < len; i++) {
    var x = a[i] || 0
    var y = b[i] || 0
    if (x !== y) return x - y
  }
  return 0
}

/** 顺子顶张（含 A-2-3-4-5 视为 5 高） */
function straightTopRank(ranksDesc, isStraight) {
  if (!isStraight) return 0
  if (ranksDesc[0] === 14 && ranksDesc[1] === 5 && ranksDesc[2] === 4 && ranksDesc[3] === 3 && ranksDesc[4] === 2) {
    return 5
  }
  return ranksDesc[0]
}

/**
 * @param cards - 五张 { suit, rank }
 * @returns {{ score: number, name: string, compareKey: number[], detailText: string }}
 */
function evaluateFive(cards) {
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

  var uniqueRanks = []
  for (var u = 0; u < ranks.length; u++) {
    if (uniqueRanks.indexOf(ranks[u]) === -1) uniqueRanks.push(ranks[u])
  }
  var isStraight = false
  if (ranks[0] - ranks[4] === 4 && uniqueRanks.length === 5) {
    isStraight = true
  }
  if (ranks[0] === 14 && ranks[1] === 5 && ranks[2] === 4 && ranks[3] === 3 && ranks[4] === 2) {
    isStraight = true
  }

  var stHigh = straightTopRank(ranks, isStraight)

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

  var royal = isFlush && isStraight && ranks[0] === 14 && ranks[4] === 10
  if (royal) {
    return {
      score: 10,
      name: '皇家同花顺',
      compareKey: [10, 14, 0, 0, 0, 0],
      detailText: '皇家同花顺（10-J-Q-K-A 同花）'
    }
  }

  if (isFlush && isStraight) {
    return {
      score: 9,
      name: '同花顺',
      compareKey: [9, stHigh, 0, 0, 0, 0],
      detailText: '同花顺：' + rankLabel(stHigh) + ' 领顺'
    }
  }

  if (counts[0] === 4) {
    var quadR = 0
    var k4 = 0
    for (var k in rankCount) {
      var kr = parseInt(k, 10)
      if (rankCount[k] === 4) quadR = kr
      else k4 = kr
    }
    return {
      score: 8,
      name: '四条',
      compareKey: [8, quadR, k4, 0, 0, 0],
      detailText: '四条「' + rankLabel(quadR) + '」+ 踢脚 ' + rankLabel(k4)
    }
  }

  if (counts[0] === 3 && counts[1] === 2) {
    var tripR = 0
    var pairR = 0
    for (var k2 in rankCount) {
      var kr2 = parseInt(k2, 10)
      if (rankCount[k2] === 3) tripR = kr2
      else if (rankCount[k2] === 2) pairR = kr2
    }
    return {
      score: 7,
      name: '葫芦',
      compareKey: [7, tripR, pairR, 0, 0, 0],
      detailText: '葫芦：「' + rankLabel(tripR) + '」三条带「' + rankLabel(pairR) + '」一对'
    }
  }

  if (isFlush) {
    return {
      score: 6,
      name: '同花',
      compareKey: [6, ranks[0], ranks[1], ranks[2], ranks[3], ranks[4]],
      detailText:
        '同花：' +
        rankLabel(ranks[0]) +
        '-' +
        rankLabel(ranks[1]) +
        '-' +
        rankLabel(ranks[2]) +
        '-' +
        rankLabel(ranks[3]) +
        '-' +
        rankLabel(ranks[4])
    }
  }

  if (isStraight) {
    return {
      score: 5,
      name: '顺子',
      compareKey: [5, stHigh, 0, 0, 0, 0],
      detailText: '顺子：' + rankLabel(stHigh) + ' 领顺'
    }
  }

  if (counts[0] === 3) {
    var tr = 0
    var kick3 = []
    for (var k3 in rankCount) {
      var kr3 = parseInt(k3, 10)
      if (rankCount[k3] === 3) tr = kr3
      else kick3.push(kr3)
    }
    kick3.sort(function (a, b) {
      return b - a
    })
    return {
      score: 4,
      name: '三条',
      compareKey: [4, tr, kick3[0], kick3[1], 0, 0],
      detailText:
        '三条「' +
        rankLabel(tr) +
        '」；踢脚 ' +
        rankLabel(kick3[0]) +
        '、' +
        rankLabel(kick3[1])
    }
  }

  if (counts[0] === 2 && counts[1] === 2) {
    var pairs = []
    var k2p = 0
    for (var k4 in rankCount) {
      var kr4 = parseInt(k4, 10)
      if (rankCount[k4] === 2) pairs.push(kr4)
      else if (rankCount[k4] === 1) k2p = kr4
    }
    pairs.sort(function (a, b) {
      return b - a
    })
    return {
      score: 3,
      name: '两对',
      compareKey: [3, pairs[0], pairs[1], k2p, 0, 0],
      detailText:
        '两对：' + rankLabel(pairs[0]) + ' 与 ' + rankLabel(pairs[1]) + '；踢脚 ' + rankLabel(k2p)
    }
  }

  if (counts[0] === 2) {
    var pr = 0
    var kickp = []
    for (var k5 in rankCount) {
      var kr5 = parseInt(k5, 10)
      if (rankCount[k5] === 2) pr = kr5
      else kickp.push(kr5)
    }
    kickp.sort(function (a, b) {
      return b - a
    })
    return {
      score: 2,
      name: '一对',
      compareKey: [2, pr, kickp[0], kickp[1], kickp[2], 0],
      detailText:
        '一对「' +
        rankLabel(pr) +
        '」；踢脚 ' +
        rankLabel(kickp[0]) +
        '、' +
        rankLabel(kickp[1]) +
        '、' +
        rankLabel(kickp[2])
    }
  }

  return {
    score: 1,
    name: '高牌',
    compareKey: [1, ranks[0], ranks[1], ranks[2], ranks[3], ranks[4]],
    detailText:
      '高牌：' +
      rankLabel(ranks[0]) +
      '-' +
      rankLabel(ranks[1]) +
      '-' +
      rankLabel(ranks[2]) +
      '-' +
      rankLabel(ranks[3]) +
      '-' +
      rankLabel(ranks[4])
  }
}

function findBestFiveWithCombo(parsedSeven) {
  var combos = getCombinations(parsedSeven, 5)
  var bestCombo = null
  var bestEv = null
  for (var j = 0; j < combos.length; j++) {
    var combo = combos[j]
    var ev = evaluateFive(combo)
    if (!bestEv || cmpCompareKey(ev.compareKey, bestEv.compareKey) > 0) {
      bestEv = ev
      bestCombo = combo
    }
  }
  return { ev: bestEv, combo: bestCombo }
}

/**
 * @param holeCards - 玩家手牌
 * @param communityCards - 公共牌
 * @returns {{ name: string, detailText: string, compareKey: number[] } | null}
 */
export function getBestHandEvaluation(holeCards, communityCards) {
  var parsed = parseHoleAndBoard(holeCards, communityCards)
  if (!parsed) return null
  return findBestFiveWithCombo(parsed).ev
}

/**
 * 当前 7 张里成牌的最佳五张（原始牌串）。
 * 仅在后端未下发 bestHandCards 时使用；展示顺序以服务端
 * DpUtilHandEvaluator.sortCardsForDisplay 为准。
 * @returns {string[]}
 */
export function getBestFiveCardIds(holeCards, communityCards) {
  var parsed = parseHoleAndBoard(holeCards, communityCards)
  if (!parsed) return []
  var combo = findBestFiveWithCombo(parsed).combo
  if (!combo) return []
  return combo.map(function (x) {
    return x.id
  })
}

/**
 * @param holeCards - 玩家手牌，如 ["hearts_A", "spades_K"]
 * @param communityCards - 公共牌
 * @returns 牌型名称，如 "皇家同花顺"、"同花顺" 等
 */
export function getHandRank(holeCards, communityCards) {
  var ev = getBestHandEvaluation(holeCards, communityCards)
  return ev ? ev.name : '牌不足'
}

/**
 * 当前 7 张里成牌的最佳五张组合说明（中文）
 */
export function getHandRankDetail(holeCards, communityCards) {
  var ev = getBestHandEvaluation(holeCards, communityCards)
  return ev ? ev.detailText : ''
}

/**
 * 摊牌时牌力最高的所有玩家（含同型踢脚比较）；平局时全部返回，顺序与 players 一致
 * @param players - { nickname, fold?, leftThisHand?, holeCards? }[]
 * @returns {string[]}
 */
export function pickShowdownLeaderNicknames(players, communityCards) {
  if (!players || !communityCards || communityCards.length < 3) return []
  var bestKey = null
  var nicks = []
  for (var i = 0; i < players.length; i++) {
    var p = players[i]
    if (!p || p.fold || p.leftThisHand) continue
    if (!p.holeCards || p.holeCards.length < 2) continue
    var ev = getBestHandEvaluation(p.holeCards, communityCards)
    if (!ev) continue
    if (!bestKey) {
      bestKey = ev.compareKey
      nicks = [p.nickname]
    } else {
      var cmp = cmpCompareKey(ev.compareKey, bestKey)
      if (cmp > 0) {
        bestKey = ev.compareKey
        nicks = [p.nickname]
      } else if (cmp === 0) {
        nicks.push(p.nickname)
      }
    }
  }
  return nicks
}

/**
 * 摊牌时牌力最高的一位（含同型踢脚比较）；平局取 players 数组中更靠前的一位
 * @param players - { nickname, fold?, leftThisHand?, holeCards? }[]
 */
export function pickShowdownLeaderNickname(players, communityCards) {
  var arr = pickShowdownLeaderNicknames(players, communityCards)
  return arr.length ? arr[0] : ''
}
