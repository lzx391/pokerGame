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
 * Ephemeral glitch monster on lower felt — below community cards / pot, inside polygon.
 * @param {{ center: { x: number, y: number } }} layout
 */
export function retroGlitchMonsterFeltAnchor(layout) {
  return retroGlitchMonsterBurstAnchors(layout, 1)[0]
}

/** Horizontal spread slots (% offset from table center) for multi-monster bursts. */
var GLITCH_MONSTER_BURST_SLOTS = {
  1: [{ x: 0, y: 0 }],
  2: [{ x: -12, y: 2 }, { x: 12, y: -1 }],
  3: [{ x: -15, y: 1 }, { x: 0, y: 4 }, { x: 15, y: 0 }],
  4: [{ x: -17, y: 3 }, { x: -6, y: 6 }, { x: 7, y: 2 }, { x: 17, y: 5 }]
}

/**
 * Spread anchors across lower felt — avoids center stack overlap when count > 1.
 * @param {{ center: { x: number, y: number } }} layout
 * @param {number} count
 * @returns {Array<{ left: string, top: string }>}
 */
export function retroGlitchMonsterBurstAnchors(layout, count) {
  var n = Math.max(1, Math.min(4, count || 1))
  var center = layout && layout.center
  if (!center) {
    var fallback = []
    for (var f = 0; f < n; f++) fallback.push({ left: '50%', top: '62%' })
    return fallback
  }
  var slots = GLITCH_MONSTER_BURST_SLOTS[n] || GLITCH_MONSTER_BURST_SLOTS[1]
  var baseY = 10 + Math.random() * 10
  var anchors = []
  for (var i = 0; i < n; i++) {
    var slot = slots[i] || slots[0]
    var jitterX = (Math.random() - 0.5) * (n === 1 ? 14 : 6)
    var jitterY = (Math.random() - 0.5) * (n === 1 ? 8 : 5)
    anchors.push({
      left: (center.x + slot.x + jitterX).toFixed(2) + '%',
      top: (center.y + baseY + slot.y + jitterY).toFixed(2) + '%'
    })
  }
  return anchors
}
