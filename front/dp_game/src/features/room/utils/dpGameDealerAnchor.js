/**
 * 发牌动画起点（视口坐标）：优先为桌面 **盖牌区**（随发牌位绕桌，`data-dp-muck-anchor`），
 * 与「从发牌位一侧发牌」一致；若无则回退 `data-dp-dealer-anchor`；再回退视口安全位置。
 */
export function getDealerAnchorViewportPoint() {
  if (typeof document === 'undefined') return null
  var el = document.querySelector('[data-dp-muck-anchor="true"]')
  if (el) {
    var m = el.getBoundingClientRect()
    if (m.width >= 1 && m.height >= 1) {
      return { x: m.left + m.width / 2, y: m.top + m.height / 2 }
    }
  }
  el = document.querySelector('[data-dp-dealer-anchor="true"]')
  if (el) {
    var d = el.getBoundingClientRect()
    if (d.width >= 1 && d.height >= 1) {
      return { x: d.left + d.width / 2, y: d.top + d.height / 2 }
    }
  }
  if (typeof window === 'undefined') return null
  var w = window.innerWidth || 0
  var h = window.innerHeight || 0
  if (w < 1 || h < 1) return null
  var insetY = Math.min(120, Math.max(56, h * 0.1))
  return { x: w / 2, y: h - insetY }
}
