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

/** retro8bit 正多边形台呢几何（与 game.vue --dp-table-polygon 一致） */
export var RETRO_TABLE_POLY_CX = 50
export var RETRO_TABLE_POLY_CY = 50
export var RETRO_TABLE_POLY_R = 48

export function retroTablePolygonSides(playerCount) {
  var n = playerCount || 0
  if (n < 3) n = 6
  return n
}

/** @returns {{ x: number, y: number }} viewBox 百分比，与 clip-path 顶点同源 */
export function retroTablePolygonVertex(vertexIdx, sides) {
  var n = sides
  var a = (2 * Math.PI * vertexIdx / n) - (Math.PI / 2)
  return {
    x: RETRO_TABLE_POLY_CX + RETRO_TABLE_POLY_R * Math.cos(a),
    y: RETRO_TABLE_POLY_CY + RETRO_TABLE_POLY_R * Math.sin(a)
  }
}

/** @returns {string} CSS clip-path polygon(...) */
export function retroTablePolygonClipPath(playerCount) {
  var n = retroTablePolygonSides(playerCount)
  var pts = []
  for (var i = 0; i < n; i++) {
    var v = retroTablePolygonVertex(i, n)
    pts.push(v.x.toFixed(1) + '% ' + v.y.toFixed(1) + '%')
  }
  return 'polygon(' + pts.join(', ') + ')'
}

/** 从多边形中心沿极角 θ 到边的距离（viewBox 百分比单位） */
export function retroTablePolygonEdgeDist(theta, sides) {
  var n = sides
  var alpha = Math.PI / n
  var sectorAngle = (2 * Math.PI) / n
  var thetaFromTop = ((theta + Math.PI / 2) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI)
  var sectorIndex = Math.floor(thetaFromTop / sectorAngle)
  var edgeMidAngle = -Math.PI / 2 + (sectorIndex + 0.5) * sectorAngle
  var delta = theta - edgeMidAngle
  while (delta > Math.PI) delta -= 2 * Math.PI
  while (delta < -Math.PI) delta += 2 * Math.PI
  return RETRO_TABLE_POLY_R * Math.cos(alpha) / Math.cos(delta)
}

/**
 * retro8bit：桌心 → 正多边形角顶点（与 retroTablePolygonClipPath 同源，非椭圆 rx/ry）。
 * 入座时座位环相对台呢旋转 π，顶点下标同步偏移半圈以对准角。
 * @returns {{ x1: number, y1: number, x2: number, y2: number }}
 */
export function retroSeatRayLineEndpoints(displayIdx, total, viewerSeatedAtTable) {
  if (!total) {
    return { x1: RETRO_TABLE_POLY_CX, y1: RETRO_TABLE_POLY_CY, x2: RETRO_TABLE_POLY_CX, y2: RETRO_TABLE_POLY_CY }
  }
  var sides = retroTablePolygonSides(total)
  var vertexIdx = displayIdx
  if (viewerSeatedAtTable) {
    vertexIdx = (displayIdx + Math.floor(sides / 2)) % sides
  }
  var v = retroTablePolygonVertex(vertexIdx, sides)
  return {
    x1: RETRO_TABLE_POLY_CX,
    y1: RETRO_TABLE_POLY_CY,
    x2: v.x,
    y2: v.y
  }
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
