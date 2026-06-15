/**
 * retro8bit：大厅/创建页 → 对局 全屏 CRT 雪花交接 + 路由瞬时切换（避免黑屏）。
 * 终端 boot 序列由各入口页/组件在导航前播放；本模块只管 overlay 与 router。
 */
import { resolveAuthLobbyTiming } from '@features/user/utils/dpAuthEnterLobby'

let overlayController = null
let crtHandoffActive = false
let crtHandoffClearTimer = null

/**
 * @param {{ play: (timing: object, onNavigate: () => void) => void } | null} controller
 */
export function bindRetroEnterGameCrtOverlay(controller) {
  overlayController = controller
}

/** @deprecated 兼容旧名 */
export function bindCreateRoomCrtOverlay(controller) {
  bindRetroEnterGameCrtOverlay(controller)
}

/** App.vue 路由转场：lobby/create-room → game 在 CRT 交接期间禁用 out-in */
export function isRetroEnterGameHandoffActive() {
  return crtHandoffActive
}

/** @deprecated 兼容旧名 */
export function isCreateRoomCrtHandoffActive() {
  return isRetroEnterGameHandoffActive()
}

/** 非 Vue 上下文（路由交接等）读取 body 主题；组件内请用 store gameUiTheme */
export function isRetro8bitTheme() {
  if (typeof document === 'undefined') return false
  return document.body.getAttribute('data-dp-game-theme') === 'retro8bit'
}

/** eco / 减少动效：跳过终端 boot 与 CRT 全屏交接 */
export function shouldSkipRetroEnterEffects() {
  if (typeof document === 'undefined') return false
  if (document.body.getAttribute('data-dp-fluidity') === 'eco') return true
  if (typeof window !== 'undefined' && window.matchMedia) {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return true
  }
  return false
}

/** @returns {import('@features/user/utils/dpAuthEnterLobby').AuthLobbyTiming} */
export function resolveEnterGameHandoffTiming() {
  var base = resolveAuthLobbyTiming()
  if (!base.fullSnow) {
    return {
      navigateAt: 80,
      fadeDuration: base.fadeDuration,
      total: 360,
      fullSnow: false
    }
  }
  return {
    navigateAt: 100,
    fadeDuration: base.fadeDuration,
    total: 420,
    fullSnow: true
  }
}

/** @param {{ total: number }} timing */
function beginCrtHandoff(timing) {
  if (crtHandoffClearTimer) {
    clearTimeout(crtHandoffClearTimer)
    crtHandoffClearTimer = null
  }
  crtHandoffActive = true
  crtHandoffClearTimer = setTimeout(function () {
    crtHandoffActive = false
    crtHandoffClearTimer = null
  }, Math.max(120, (timing && timing.total) || 0) + 80)
}

/**
 * @param {import('vue-router').default} router
 * @param {string} roomId
 * @param {{
 *   onOverlayVisible?: () => void,
 *   navigation?: 'push' | 'replace',
 *   forceHandoff?: boolean
 * }} [opts]
 * @returns {Promise<void>}
 */
export function enterGameWithRetroHandoff(router, roomId, opts) {
  opts = opts || {}
  var rid = roomId != null ? String(roomId).trim() : ''
  if (!rid) return Promise.resolve()

  var timing = resolveEnterGameHandoffTiming()
  var navMode = opts.navigation === 'push' ? 'push' : 'replace'

  return new Promise(function (resolve) {
    var navigated = false
    function hideLocalBootOverlay() {
      if (typeof opts.onOverlayVisible === 'function') {
        opts.onOverlayVisible()
      }
    }
    function navigate() {
      if (navigated) return
      navigated = true
      var loc = { name: 'game', params: { roomId: rid } }
      var p =
        navMode === 'push'
          ? router.push(loc)
          : router.replace(loc)
      Promise.resolve(p)
        .catch(function () {})
        .finally(resolve)
    }

    var useHandoff =
      (opts.forceHandoff === true || !shouldSkipRetroEnterEffects()) &&
      isRetro8bitTheme() &&
      overlayController &&
      typeof overlayController.play === 'function'

    if (useHandoff) {
      hideLocalBootOverlay()
      beginCrtHandoff(timing)
      overlayController.play(timing, navigate)
      return
    }
    hideLocalBootOverlay()
    navigate()
  })
}

/**
 * @param {import('vue-router').default} router
 * @param {string} roomId
 * @param {{ onOverlayVisible?: () => void }} [opts]
 * @returns {Promise<void>}
 */
export function enterGameAfterCreateRoom(router, roomId, opts) {
  return enterGameWithRetroHandoff(router, roomId, {
    onOverlayVisible: opts && opts.onOverlayVisible,
    navigation: 'replace'
  })
}
