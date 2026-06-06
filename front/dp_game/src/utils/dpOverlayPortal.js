/**
 * 将自定义 fixed 遮罩挂到 overlay 根节点，避免被对局 DOM 树内 stacking context 挡住。
 * 对局全屏时须挂到 .dp-game-root（fullscreen 元素），否则挂 body 的层不可见。
 * 与 dpModalZIndex 层级常量配合使用。
 */

import { dpNextZIndex, DP_Z_LAYER } from './dpModalZIndex'

/** @returns {HTMLElement|null} */
export function dpGetOverlayPortalRoot() {
  if (typeof document === 'undefined') return null
  var fs =
    document.fullscreenElement ||
    document.webkitFullscreenElement ||
    document.mozFullScreenElement
  if (fs && fs.classList && fs.classList.contains('dp-game-root')) {
    return fs
  }
  return document.body
}

/**
 * @param {HTMLElement} el
 * @param {{ parent: Node, next: Node|null }} anchor
 * @param {HTMLElement|null} [explicitRoot]
 */
export function dpPortalOverlayToBody(el, anchor, explicitRoot) {
  if (!el) return
  var target = explicitRoot || dpGetOverlayPortalRoot()
  if (!target || el.parentNode === target) return
  if (anchor) {
    anchor.parent = el.parentNode
    anchor.next = el.nextSibling
  }
  target.appendChild(el)
}

/** 对局全屏下把 el-dialog / 自定义叠层挪回 gameRoot（与 dpGameFullscreenMixin 协同） */
export function dpScheduleOverlayFullscreenReparent(gameView) {
  if (gameView && typeof gameView.scheduleReparentElementUiLayersIntoFullscreenRoot === 'function') {
    gameView.scheduleReparentElementUiLayersIntoFullscreenRoot()
  }
}

/**
 * @param {HTMLElement|null|undefined} el
 * @param {{ parent: Node, next: Node|null }|null} anchor
 */
export function dpRestoreOverlayFromPortal(el, anchor) {
  if (!el || !anchor || !anchor.parent) return
  var root = dpGetOverlayPortalRoot()
  if (el.parentNode === document.body || (root && el.parentNode === root)) {
    anchor.parent.insertBefore(el, anchor.next)
  }
}

/** @param {'handHistory'|'playerSheet'|'achievement'|'profileDialog'|'friendList'} layer */
export function dpOpenBodyOverlayZIndex(layer) {
  return dpNextZIndex(layer)
}

/**
 * 好友抽屉叠层恢复：移除落在好友层 z-index 段内的残留 v-modal（旧版 modal=true 或 PopupManager 未对齐时）。
 */
export function dpPruneFriendDrawerStrayVModal() {
  if (typeof document === 'undefined') return
  var modals = document.getElementsByClassName('v-modal')
  if (!modals.length) return
  var lo = DP_Z_LAYER.friendList - 1
  var hi = DP_Z_LAYER.playerSheet
  var i = 0
  for (i = modals.length - 1; i >= 0; i--) {
    var node = modals[i]
    if (!node || node.style.display === 'none' || node.parentNode !== document.body) continue
    var z = parseInt(node.style.zIndex, 10)
    if (isFinite(z) && z >= lo && z < hi) {
      node.parentNode.removeChild(node)
    }
  }
}
