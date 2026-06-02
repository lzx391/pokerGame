/**
 * SVG path / glitch-monster anchors from buildRetroTableLayout vertices (viewBox 0–100).
 */

export function retroTableEdgePathD(vertices) {
  if (!vertices || !vertices.length) return ''
  var parts = []
  for (var i = 0; i < vertices.length; i++) {
    var v = vertices[i]
    parts.push((i === 0 ? 'M' : 'L') + v.x.toFixed(2) + ' ' + v.y.toFixed(2))
  }
  return parts.join(' ') + ' Z'
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
