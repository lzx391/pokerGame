/**
 * 对局全屏 mixin 等注册 reparent 回调，供 SSE / 叠层组件触发 Element 叠层挪入 gameRoot。
 * 支持多组件同时注册（Set），避免后挂载者覆盖先注册者。
 */

/** @type {Set<() => void>} */
var reparentHandlers = new Set()

/** @param {(() => void) | null | undefined} fn */
export function registerDpFullscreenOverlayReparent(fn) {
  if (typeof fn === 'function') reparentHandlers.add(fn)
}

/** @param {(() => void) | null | undefined} fn */
export function unregisterDpFullscreenOverlayReparent(fn) {
  if (typeof fn === 'function') reparentHandlers.delete(fn)
}

export function triggerDpFullscreenOverlayReparent() {
  reparentHandlers.forEach(function (fn) {
    try {
      fn()
    } catch (e) {
      /* ignore */
    }
  })
}
