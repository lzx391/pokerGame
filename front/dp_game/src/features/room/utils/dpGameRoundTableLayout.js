/**
 * 圆桌椭圆座位与盖牌区、台呢标的几何（原 game.vue 内联逻辑，便于单测与复用）。
 */

import { dpTableLayoutDevLog } from '@features/room/utils/dpTableLayoutDevLog'

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

export function seatChatBubbleSide(displayIdx, total, viewerSeatedAtTable, gameUiTheme) {
  if (!total) return 'top'
  if (gameUiTheme === 'retro8bit') {
    var layout = buildRetroTableLayout(total, { viewerSeatedAtTable: viewerSeatedAtTable })
    var seat = layout.seatPositions[displayIdx]
    if (!seat) return 'top'
    var x = parseFloat(seat.left)
    if (!isFinite(x)) return 'top'
    if (x < 38) return 'left'
    if (x > 62) return 'right'
    return 'top'
  }
  if (!viewerSeatedAtTable) return 'top'
  var theta = roundTableSeatTheta(displayIdx, total, viewerSeatedAtTable)
  var rx = 46
  var cx = 50
  var xEll = cx + Math.sin(theta) * rx
  if (xEll < 38) return 'left'
  if (xEll > 62) return 'right'
  return 'top'
}

/** retro8bit 正多边形：桌心、外接圆半径（viewBox %） */
export var RETRO_TABLE_POLY_CX = 50
export var RETRO_TABLE_POLY_CY = 50
export var RETRO_TABLE_POLY_R = 48
/** 顶点 0 极角：π/2 → 屏幕下方尖角（与入座 display 0 对齐）；原 −π/2 为顶部尖角、底部是平边 */
export var RETRO_TABLE_POLY_THETA0 = Math.PI / 2

/** @param {number} playerCount 在座人数 */
export function retroTablePolygonSides(playerCount) {
  var n = playerCount || 0
  return Math.max(3, n)
}

/**
 * 单一正多边形几何源：桌面 clip、射线端点、座位锚点均由此派生。
 * @param {number} playerCount 在座 display 环人数
 * @param {{ viewerSeatedAtTable?: boolean, seatOutward?: number, logReason?: string }} [options]
 * @returns {{
 *   sides: number,
 *   center: { x: number, y: number },
 *   vertices: Array<{ x: number, y: number }>,
 *   clipPath: string,
 *   seatPositions: Array<{ left: string, top: string, vertexIndex: number }>
 * }}
 */
export function buildRetroTableLayout(playerCount, options) {
  options = options || {}
  var seated = playerCount || 0
  var n = retroTablePolygonSides(seated)
  var viewerSeatedAtTable = !!options.viewerSeatedAtTable
  var seatOutward = options.seatOutward != null ? options.seatOutward : 1
  var cx = RETRO_TABLE_POLY_CX
  var cy = RETRO_TABLE_POLY_CY
  var center = { x: cx, y: cy }
  var vertices = []
  var clipPts = []
  for (var vi = 0; vi < n; vi++) {
    var v = retroTablePolygonVertex(vi, n)
    vertices.push(v)
    clipPts.push(v.x.toFixed(1) + '% ' + v.y.toFixed(1) + '%')
  }
  var clipPath = 'polygon(' + clipPts.join(', ') + ')'
  var seatPositions = []
  for (var di = 0; di < seated; di++) {
    var vertexIdx = retroDisplayIndexToVertexIndex(di, n, viewerSeatedAtTable)
    var vtx = vertices[vertexIdx]
    var sx = cx + (vtx.x - cx) * seatOutward
    var sy = cy + (vtx.y - cy) * seatOutward
    seatPositions.push({
      left: sx.toFixed(2) + '%',
      top: sy.toFixed(2) + '%',
      vertexIndex: vertexIdx
    })
  }
  var layout = {
    sides: n,
    center: center,
    vertices: vertices,
    clipPath: clipPath,
    seatPositions: seatPositions
  }
  if (options.logReason && process.env.NODE_ENV === 'development') {
    var bottomSeat = null
    if (viewerSeatedAtTable && seated > 0) {
      var bottomVtxIdx = retroDisplayIndexToVertexIndex(0, n, true)
      var bottomVtx = vertices[bottomVtxIdx]
      bottomSeat = {
        displayIndex: 0,
        vertexIndex: bottomVtxIdx,
        vertex: bottomVtx,
        seat: seatPositions[0]
      }
    }
    dpTableLayoutDevLog(options.logReason, {
      seated: seated,
      sides: n,
      theta0: RETRO_TABLE_POLY_THETA0,
      center: center,
      vertices: vertices,
      bottomSeat: bottomSeat,
      seatMap: seatPositions.map(function (s, i) {
        return { displayIndex: i, vertexIndex: s.vertexIndex, left: s.left, top: s.top }
      })
    })
  }
  return layout
}

/**
 * display 环下标 → 多边形顶点下标。
 * 入座：display 0 = 顶点 0（θ₀=π/2 的底尖角）；旁观：display 0 = 对顶顶点（桌顶）。
 */
export function retroDisplayIndexToVertexIndex(displayIdx, sides, viewerSeatedAtTable) {
  if (viewerSeatedAtTable) {
    return displayIdx % sides
  }
  return (displayIdx + Math.floor(sides / 2)) % sides
}

/** @returns {{ x: number, y: number }} viewBox 百分比 */
export function retroTablePolygonVertex(vertexIdx, sides) {
  var n = sides
  var a = (2 * Math.PI * vertexIdx / n) + RETRO_TABLE_POLY_THETA0
  return {
    x: RETRO_TABLE_POLY_CX + RETRO_TABLE_POLY_R * Math.cos(a),
    y: RETRO_TABLE_POLY_CY + RETRO_TABLE_POLY_R * Math.sin(a)
  }
}

/** @returns {string} CSS clip-path polygon(...) */
export function retroTablePolygonClipPath(playerCount) {
  return buildRetroTableLayout(playerCount || 0, {}).clipPath
}

/** 从多边形中心沿极角 θ 到边的距离（viewBox 百分比单位） */
export function retroTablePolygonEdgeDist(theta, sides) {
  var n = sides
  var alpha = Math.PI / n
  var sectorAngle = (2 * Math.PI) / n
  var thetaFromVertex0 = ((theta - RETRO_TABLE_POLY_THETA0) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI)
  var sectorIndex = Math.floor(thetaFromVertex0 / sectorAngle)
  var edgeMidAngle = RETRO_TABLE_POLY_THETA0 + (sectorIndex + 0.5) * sectorAngle
  var delta = theta - edgeMidAngle
  while (delta > Math.PI) delta -= 2 * Math.PI
  while (delta < -Math.PI) delta += 2 * Math.PI
  return RETRO_TABLE_POLY_R * Math.cos(alpha) / Math.cos(delta)
}

/**
 * retro8bit 座位：锚在角顶点（与 clip-path / 射线同源，不做椭圆环 y 偏移）。
 */
export function retroPlayerRoundTableStyle(displayIdx, total, viewerSeatedAtTable) {
  if (!total) return {}
  var layout = buildRetroTableLayout(total, { viewerSeatedAtTable: viewerSeatedAtTable })
  var seat = layout.seatPositions[displayIdx]
  if (!seat) return {}
  return {
    left: seat.left,
    top: seat.top,
    transform: 'translate(-50%, -50%)'
  }
}

/**
 * 圆桌座位 left/top；retro8bit 走正多边形顶点，其它主题走椭圆环。
 */
export function roundTableSeatPosition(displayIdx, total, viewerSeatedAtTable, stage, gameUiTheme) {
  if (gameUiTheme === 'retro8bit') {
    return retroPlayerRoundTableStyle(displayIdx, total, viewerSeatedAtTable)
  }
  return playerRoundTableStyle(displayIdx, total, viewerSeatedAtTable, stage)
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

export function nudgeSeatTowardTableCenter(displayIdx, total, inward, viewerSeatedAtTable, stage, gameUiTheme) {
  if (gameUiTheme === 'retro8bit') {
    return retroNudgeSeatTowardTableCenter(displayIdx, total, inward, viewerSeatedAtTable)
  }
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

function retroNudgeSeatTowardTableCenter(displayIdx, total, inward, viewerSeatedAtTable) {
  var base = retroPlayerRoundTableStyle(displayIdx, total, viewerSeatedAtTable)
  var x = parseFloat(base.left)
  var y = parseFloat(base.top)
  if (!isFinite(x) || !isFinite(y)) return {}
  var layout = buildRetroTableLayout(total, { viewerSeatedAtTable: viewerSeatedAtTable })
  var cx = layout.center.x
  var cy = layout.center.y
  var nx = x + (cx - x) * inward
  var ny = y + (cy - y) * inward
  return {
    left: nx + '%',
    top: ny + '%',
    transform: 'translate(-50%, -50%)'
  }
}

export function seatFeltMarkerRoundTableStyle(displayIdx, total, viewerSeatedAtTable, stage, gameUiTheme) {
  var inward = 0.34
  if (typeof window !== 'undefined') {
    var w = window.innerWidth
    if (w <= 600) inward = 0.16
    else if (w <= 900) inward = 0.24
  }
  return nudgeSeatTowardTableCenter(displayIdx, total, inward, viewerSeatedAtTable, stage, gameUiTheme)
}

/**
 * 桌面行动倒计时圆环：沿「桌心 ↔ 座位」射线，比台呢标（D/SB/BB）更靠桌心，避免压住标与玩家卡。
 * inward 须大于 seatFeltMarkerRoundTableStyle，使顺序为：桌心 → 计时器 → 标 → 玩家卡。
 */
export function actionTimerOrbitRoundTableStyle(displayIdx, total, viewerSeatedAtTable, stage, gameUiTheme) {
  var inward = 0.56
  if (typeof window !== 'undefined') {
    var w = window.innerWidth
    if (w <= 600) inward = 0.32
    else if (w <= 900) inward = 0.44
  }
  return nudgeSeatTowardTableCenter(displayIdx, total, inward, viewerSeatedAtTable, stage, gameUiTheme)
}

/**
 * retro8bit：桌心 → 正多边形角顶点（与 buildRetroTableLayout 同源）。
 * @returns {{ x1: number, y1: number, x2: number, y2: number }}
 */
export function retroSeatRayLineEndpoints(displayIdx, total, viewerSeatedAtTable, layout) {
  if (!total) {
    return { x1: RETRO_TABLE_POLY_CX, y1: RETRO_TABLE_POLY_CY, x2: RETRO_TABLE_POLY_CX, y2: RETRO_TABLE_POLY_CY }
  }
  var L = layout || buildRetroTableLayout(total, { viewerSeatedAtTable: viewerSeatedAtTable })
  var vertexIdx = retroDisplayIndexToVertexIndex(displayIdx, L.sides, viewerSeatedAtTable)
  var v = L.vertices[vertexIdx]
  return {
    x1: L.center.x,
    y1: L.center.y,
    x2: v.x,
    y2: v.y,
    vertexIndex: vertexIdx
  }
}

/** Dev：每条射线终点 vs 顶点 vs 座位 CSS（证明 x2,y2 来自 vertices 而非 seat） */
export function retroSeatRayLayoutDiagnostics(total, viewerSeatedAtTable, layout, seatStyleForDisplay) {
  if (!total) return []
  var L = layout || buildRetroTableLayout(total, { viewerSeatedAtTable: viewerSeatedAtTable })
  var rows = []
  for (var di = 0; di < total; di++) {
    var ep = retroSeatRayLineEndpoints(di, total, viewerSeatedAtTable, L)
    var vtxIdx = ep.vertexIndex
    var vtx = L.vertices[vtxIdx]
    var seatCss = typeof seatStyleForDisplay === 'function' ? seatStyleForDisplay(di) : null
    var seatLeft = seatCss && seatCss.left != null ? parseFloat(seatCss.left) : null
    var seatTop = seatCss && seatCss.top != null ? parseFloat(seatCss.top) : null
    rows.push({
      displayIndex: di,
      vertexIndex: vtxIdx,
      vertex: { x: vtx.x, y: vtx.y },
      rayEnd: { x2: ep.x2, y2: ep.y2 },
      rayMatchesVertex: ep.x2 === vtx.x && ep.y2 === vtx.y,
      seatCss: seatCss ? { left: seatCss.left, top: seatCss.top } : null,
      seatMatchesVertex: seatLeft === vtx.x && seatTop === vtx.y
    })
  }
  return rows
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
