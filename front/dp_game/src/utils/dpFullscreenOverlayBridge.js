/**
 * 对局全屏 mixin 注册 reparent 回调，供 SSE 等全局路径触发 Element 叠层挪入 gameRoot。
 */

/** @type {(() => void) | null} */
var reparentHandler = null

/** @param {(() => void) | null | undefined} fn */
export function registerDpFullscreenOverlayReparent(fn) {
  reparentHandler = typeof fn === 'function' ? fn : null
}

export function triggerDpFullscreenOverlayReparent() {
  if (reparentHandler) reparentHandler()
}
