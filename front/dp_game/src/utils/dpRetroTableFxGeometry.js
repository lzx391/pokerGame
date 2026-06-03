/**
 * SVG path / glitch-monster anchors from buildRetroTableLayout vertices (viewBox 0–100).
 */

export function retroTableEdgePathD(vertices) {
  return retroTablePolygonPathD(vertices)
}

/** Closed SVG path for polygon vertices; optional radial offset from center (viewBox %). */
export function retroTablePolygonPathD(vertices, center, offsetPct) {
  if (!vertices || !vertices.length) return ''
  var parts = []
  for (var i = 0; i < vertices.length; i++) {
    var v = vertices[i]
    if (center && offsetPct) {
      v = offsetVertexFromCenter(v, center, offsetPct)
    }
    parts.push((i === 0 ? 'M' : 'L') + v.x.toFixed(2) + ' ' + v.y.toFixed(2))
  }
  return parts.join(' ') + ' Z'
}

/** Outer / inner decorative frame paths along retro table polygon edge. */
export function retroTableBorderPaths(layout) {
  if (!layout || !layout.vertices || !layout.center) {
    return { outer: '', inner: '' }
  }
  var verts = layout.vertices
  var center = layout.center
  return {
    outer: retroTablePolygonPathD(verts, center, 0.22),
    inner: retroTablePolygonPathD(verts, center, -0.38)
  }
}

function offsetVertexFromCenter(v, center, delta) {
  var dx = v.x - center.x
  var dy = v.y - center.y
  var len = Math.sqrt(dx * dx + dy * dy)
  if (len < 1e-6) return { x: v.x, y: v.y }
  var scale = (len + delta) / len
  return {
    x: center.x + dx * scale,
    y: center.y + dy * scale
  }
}

/**
 * Ephemeral glitch monster on open felt near table rim — away from center cards / pot.
 * @param {{ center: { x: number, y: number }, vertices?: Array<{ x: number, y: number }> }} layout
 */
export function retroGlitchMonsterFeltAnchor(layout) {
  return retroGlitchMonsterBurstAnchors(layout, 1)[0]
}

/** Radial lerp from center toward rim target (0 = center, 1 = on edge/vertex). */
var GLITCH_MONSTER_RIM_T_BASE = 0.86
var GLITCH_MONSTER_RIM_T_SPREAD = 0.05

function lerpTablePoint(from, to, t) {
  return {
    x: from.x + (to.x - from.x) * t,
    y: from.y + (to.y - from.y) * t
  }
}

function retroTableEdgeMidpoint(vertices, edgeIndex) {
  var n = vertices.length
  var i = ((edgeIndex % n) + n) % n
  var a = vertices[i]
  var b = vertices[(i + 1) % n]
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }
}

/** Evenly spaced edge indices around polygon, with random rotational offset. */
function glitchMonsterBurstEdgeIndices(sideCount, burstCount) {
  var n = Math.max(3, sideCount || 3)
  var k = Math.max(1, Math.min(4, burstCount || 1))
  var start = Math.floor(Math.random() * n)
  var step = n / k
  var indices = []
  for (var i = 0; i < k; i++) {
    indices.push(Math.floor(start + i * step) % n)
  }
  return indices
}

function retroGlitchMonsterRimAnchor(layout, edgeIndex) {
  var center = layout.center
  var verts = layout.vertices
  var mid = retroTableEdgeMidpoint(verts, edgeIndex)
  var t = GLITCH_MONSTER_RIM_T_BASE + (Math.random() - 0.5) * GLITCH_MONSTER_RIM_T_SPREAD * 2
  t = Math.max(0.78, Math.min(0.92, t))
  var jitter = burstCountJitter()
  var pt = lerpTablePoint(center, mid, t)
  pt.x += jitter.x
  pt.y += jitter.y
  return {
    left: pt.x.toFixed(2) + '%',
    top: pt.y.toFixed(2) + '%'
  }
}

function burstCountJitter() {
  var spread = 3
  return {
    x: (Math.random() - 0.5) * spread,
    y: (Math.random() - 0.5) * spread
  }
}

/** Default rim anchor when layout is missing (bottom edge, ~86% radial). */
function retroGlitchMonsterRimFallbackAnchor() {
  return { left: '50%', top: '91%' }
}

/**
 * Spread anchors along felt rim — radial ~78–92% from center toward edge midpoints.
 * @param {{ center: { x: number, y: number }, vertices?: Array<{ x: number, y: number }>, sides?: number }} layout
 * @param {number} count
 * @returns {Array<{ left: string, top: string }>}
 */
export function retroGlitchMonsterBurstAnchors(layout, count) {
  var burstCount = Math.max(1, Math.min(4, count || 1))
  var center = layout && layout.center
  var verts = layout && layout.vertices
  if (!center || !verts || !verts.length) {
    var fallback = []
    var fb = retroGlitchMonsterRimFallbackAnchor()
    for (var f = 0; f < burstCount; f++) fallback.push(fb)
    return fallback
  }
  var edgeIndices = glitchMonsterBurstEdgeIndices(layout.sides || verts.length, burstCount)
  var anchors = []
  for (var i = 0; i < burstCount; i++) {
    anchors.push(retroGlitchMonsterRimAnchor(layout, edgeIndices[i]))
  }
  return anchors
}
