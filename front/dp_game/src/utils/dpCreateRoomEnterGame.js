/**
 * 创建房间成功后进入对局：全屏 CRT 雪花盖住路由切换，避免 create-room 页闪一下。
 * 由 App.vue 的 DpCrtFullscreenOverlay 执行；未绑定时直接 replace。
 */
import { resolveAuthLobbyTiming } from '@/utils/dpAuthEnterLobby'

let overlayController = null
let crtHandoffActive = false
let crtHandoffClearTimer = null

/**
 * @param {{ play: (timing: object, onNavigate: () => void) => void } | null} controller
 */
export function bindCreateRoomCrtOverlay(controller) {
  overlayController = controller
}

/** App.vue 路由转场：create-room → game 在 CRT 交接期间禁用 out-in，避免 router-view 空窗黑屏 */
export function isCreateRoomCrtHandoffActive() {
  return crtHandoffActive
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

function isRetro8bitTheme() {
  if (typeof document === 'undefined') return false
  return document.body.getAttribute('data-dp-game-theme') === 'retro8bit'
}

/** @returns {import('@/utils/dpAuthEnterLobby').AuthLobbyTiming} */
function resolveCreateGameHandoffTiming() {
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

/**
 * @param {import('vue-router').default} router
 * @param {string} roomId
 * @param {{ onOverlayVisible?: () => void }} [opts]
 * @returns {Promise<void>}
 */
export function enterGameAfterCreateRoom(router, roomId, opts) {
  opts = opts || {}
  var timing = resolveCreateGameHandoffTiming()

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
      router
        .replace({ name: 'game', params: { roomId: String(roomId) } })
        .catch(function () {})
        .finally(resolve)
    }

    if (
      isRetro8bitTheme() &&
      overlayController &&
      typeof overlayController.play === 'function'
    ) {
      hideLocalBootOverlay()
      beginCrtHandoff(timing)
      overlayController.play(timing, navigate)
      return
    }
    hideLocalBootOverlay()
    navigate()
  })
}
