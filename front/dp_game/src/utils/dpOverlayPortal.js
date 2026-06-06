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
  /* 伪全屏：叠层须挂在对局根内，否则 body 上的 fixed 层可能被整页 stacking 挡住 */
  var pseudo = document.querySelector('.dp-game-root.dp-game-root--pseudo-fs')
  if (pseudo) return pseudo
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
 * Element UI 的 v-modal 与 el-dialog__wrapper 为 body 同级兄弟（非 wrapper 子节点）。
 * @param {HTMLElement|null|undefined} wrapper
 * @returns {HTMLElement|null}
 */
export function dpFindDialogPairedVModal(wrapper) {
  if (!wrapper) return null
  var prev = wrapper.previousElementSibling
  if (prev && prev.classList && prev.classList.contains('v-modal')) return prev
  return null
}

/**
 * 对齐 dialog wrapper 与同层 v-modal 的 z-index（wrapper = z，v-modal = z - 1）。
 * @param {HTMLElement|null|undefined} wrapper
 * @param {number} zIndex
 */
export function dpSyncDialogPairedVModal(wrapper, zIndex) {
  if (!wrapper || !isFinite(zIndex)) return
  wrapper.style.zIndex = String(zIndex)
  var modal = dpFindDialogPairedVModal(wrapper)
  if (modal) modal.style.zIndex = String(zIndex - 1)
}

/**
 * 移除 overlay 根上落在 [lo, hi) z-index 段内的残留 v-modal。
 * @param {number} lo
 * @param {number} hi
 */
export function dpPruneStrayVModalInRange(lo, hi) {
  if (typeof document === 'undefined') return
  var modals = document.getElementsByClassName('v-modal')
  if (!modals.length) return
  var portal = dpGetOverlayPortalRoot()
  var i = 0
  for (i = modals.length - 1; i >= 0; i--) {
    var node = modals[i]
    if (!node || node.style.display === 'none') continue
    var parent = node.parentNode
    if (parent !== document.body && parent !== portal) continue
    var z = parseInt(node.style.zIndex, 10)
    if (isFinite(z) && z >= lo && z < hi) {
      parent.removeChild(node)
    }
  }
}

/** 好友抽屉：清理好友层段内残留 v-modal */
export function dpPruneFriendDrawerStrayVModal() {
  dpPruneStrayVModalInRange(DP_Z_LAYER.friendList - 1, DP_Z_LAYER.playerSheet)
}

/** 成就墙：modal=false 时不应有 v-modal，清理成就层段内残留 */
export function dpPruneAchievementStrayVModal() {
  dpPruneStrayVModalInRange(DP_Z_LAYER.achievement - 1, DP_Z_LAYER.handHistory + 100)
}
