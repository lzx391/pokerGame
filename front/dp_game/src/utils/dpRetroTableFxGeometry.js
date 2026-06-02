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
  var center = layout && layout.center
  if (!center) return { left: '50%', top: '62%' }
  var jitterX = (Math.random() - 0.5) * 14
  var distY = 10 + Math.random() * 12
  return {
    left: (center.x + jitterX).toFixed(2) + '%',
    top: (center.y + distY).toFixed(2) + '%'
  }
}
