/**
 * 圆桌椭圆座位与盖牌区、台呢标的几何（原 game.vue 内联逻辑，便于单测与复用）。
 */

/**
 * 开局发牌动画：从发牌位顺时针下一位起为 0，依次 1、2…（仅用于错开飞入时间）
 * @param {number} seatIndex players 数组下标
 * @param {{ dealer?: boolean }[]} players
 */
export function holeDealOrderFromDealer(seatIndex, players) {
  var list = players
  if (!list || !list.length) return 0
  var dealerIdx = -1
  for (var i = 0; i < list.length; i++) {
    if (list[i].dealer) {
      dealerIdx = i
      break
    }
  }
  if (dealerIdx < 0) return 0
  var start = (dealerIdx + 1) % list.length
  return (seatIndex - start + list.length) % list.length
}

export function roundTableSeatTheta(displayIdx, total, viewerSeatedAtTable) {
  if (!total) return 0
  if (viewerSeatedAtTable) {
    return Math.PI + (2 * Math.PI * displayIdx) / total
  }
  return -Math.PI / 2 + (2 * Math.PI * displayIdx) / total
}

export function seatChatBubbleSide(displayIdx, total, viewerSeatedAtTable) {
  if (!total) return 'top'
  if (!viewerSeatedAtTable) return 'top'
  var theta = roundTableSeatTheta(displayIdx, total, viewerSeatedAtTable)
  var rx = 46
  var cx = 50
  var x = cx + Math.sin(theta) * rx
  if (x < 38) return 'left'
  if (x > 62) return 'right'
  return 'top'
}

export function playerRoundTableStyle(displayIdx, total, viewerSeatedAtTable, stage) {
  if (!total) return {}
  var theta = roundTableSeatTheta(displayIdx, total, viewerSeatedAtTable)
  var rx = 46
  var ry = 41
  var cx = 50
  var cy = 44
  var x = cx + Math.sin(theta) * rx
  var y = cy - Math.cos(theta) * ry
  if (stage === 'showdown' || stage === 'settled') {
    var c = Math.cos(theta)
    if (c > 0.2) {
      y -= 9
    } else if (c > -0.15) {
      y -= 4
    } else if (c < -0.35) {
      y += 10
    }
  }
  if (typeof window !== 'undefined') {
    var w = window.innerWidth
    var cosT = Math.cos(theta)
    if (w <= 600) {
      if (cosT > 0.2) {
        y += 12
      } else if (cosT > 0) {
        y += 6
      }
    } else if (w <= 900) {
      if (cosT > 0.08) {
        y += 6
      }
    }
  }
  return {
    left: x + '%',
    top: y + '%'
  }
}

export function nudgeSeatTowardTableCenter(displayIdx, total, inward, viewerSeatedAtTable, stage) {
  var base = playerRoundTableStyle(displayIdx, total, viewerSeatedAtTable, stage)
  var x = parseFloat(base.left)
  var y = parseFloat(base.top)
  if (!isFinite(x) || !isFinite(y)) return {}
  var nx = x + (50 - x) * inward
  var ny = y + (44 - y) * inward
  return {
    left: nx + '%',
    top: ny + '%',
    transform: 'translate(-50%, -50%)'
  }
}

export function seatFeltMarkerRoundTableStyle(displayIdx, total, viewerSeatedAtTable, stage) {
  var inward = 0.34
  if (typeof window !== 'undefined') {
    var w = window.innerWidth
    if (w <= 600) inward = 0.16
    else if (w <= 900) inward = 0.24
  }
  return nudgeSeatTowardTableCenter(displayIdx, total, inward, viewerSeatedAtTable, stage)
}

/**
 * 桌面行动倒计时圆环：沿「桌心 ↔ 座位」射线，比台呢标（D/SB/BB）更靠桌心，避免压住标与玩家卡。
 * inward 须大于 seatFeltMarkerRoundTableStyle，使顺序为：桌心 → 计时器 → 标 → 玩家卡。
 */
export function actionTimerOrbitRoundTableStyle(displayIdx, total, viewerSeatedAtTable, stage) {
  var inward = 0.56
  if (typeof window !== 'undefined') {
    var w = window.innerWidth
    if (w <= 600) inward = 0.32
    else if (w <= 900) inward = 0.44
  }
  return nudgeSeatTowardTableCenter(displayIdx, total, inward, viewerSeatedAtTable, stage)
}

export function muckPileRoundTableStyle(stage, playersDisplayOrderLength, dealerDisplayIndex, viewerSeatedAtTable) {
  if (stage === 'showdown' || stage === 'settled') {
    return {
      left: '81%',
      top: '56%',
      transform: 'translate(-50%, -50%)'
    }
  }
  var n = playersDisplayOrderLength
  var d = dealerDisplayIndex
  if (n === 0 || d < 0) {
    return {
      left: '50%',
      top: '44%',
      transform: 'translate(-50%, -50%)'
    }
  }
  return nudgeSeatTowardTableCenter(d, n, 0.26, viewerSeatedAtTable, stage)
}
