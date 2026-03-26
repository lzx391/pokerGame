/**
 * 庄位发牌动画起点（视口坐标）：庄家头像/座位区中心。
 *
 * 本人为庄时锚点绑在「内联手牌区」的 GamePlayerCard 上；窄屏或全屏下该区域被 CSS 整行隐藏，
 * getBoundingClientRect 为 0，若仍用 (0,0) 会表现为从屏幕左上角飞牌。
 * 此时回退为本人视角：视口底部居中略偏上（贴近底栏/手牌侧）。
 */
export function getDealerAnchorViewportPoint() {
  if (typeof document === 'undefined') return null
  var el = document.querySelector('[data-dp-dealer-anchor="true"]')
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
