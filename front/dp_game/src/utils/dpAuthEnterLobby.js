/**
 * 登录/注册成功后：轻提示 + 全屏 CRT 雪花 → replace /home
 * 由 App.vue 挂载的 DpCrtFullscreenOverlay 执行动画；未绑定时仅延迟导航。
 */

/** @typedef {{ navigateAt: number, fadeDuration: number, total: number, fullSnow: boolean }} AuthLobbyTiming */

let overlayController = null

/**
 * @param {{ play: (timing: AuthLobbyTiming, onNavigate: () => void) => void } | null} controller
 */
export function bindAuthCrtOverlay(controller) {
  overlayController = controller
}

function isRetro8bitAuthTheme() {
  if (typeof document === 'undefined') return false
  return document.body.getAttribute('data-dp-game-theme') === 'retro8bit'
}

function isLiteTransition() {
  if (typeof window === 'undefined') return false
  if (document.body && document.body.getAttribute('data-dp-fluidity') === 'eco') {
    return true
  }
  if (window.matchMedia) {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return true
    if (window.matchMedia('(max-width: 640px)').matches) return true
  }
  return false
}

/** @returns {AuthLobbyTiming} */
export function resolveAuthLobbyTiming() {
  if (isLiteTransition()) {
    return {
      navigateAt: 200,
      fadeDuration: 300,
      total: 500,
      fullSnow: false
    }
  }
  return {
    navigateAt: 520,
    fadeDuration: 280,
    total: 800,
    fullSnow: true
  }
}

/**
 * @param {import('vue-router').default} router
 * @param {import('vue').default} vm 组件实例（用于 $message）
 * @param {{ message?: string, showMessage?: boolean }} [opts]
 * @returns {Promise<void>}
 */
export function enterLobbyAfterAuth(router, vm, opts) {
  opts = opts || {}
  var showMessage = opts.showMessage !== false
  if (showMessage && opts.message) {
    vm.$message.success({
      message: opts.message,
      duration: 2500
    })
  }

  var timing = resolveAuthLobbyTiming()

  return new Promise(function (resolve) {
    var navigated = false
    function navigate() {
      if (navigated) return
      navigated = true
      router.replace('/home').catch(function () {}).finally(resolve)
    }

    if (
      isRetro8bitAuthTheme() &&
      overlayController &&
      typeof overlayController.play === 'function'
    ) {
      overlayController.play(timing, navigate)
      return
    }
    setTimeout(navigate, isRetro8bitAuthTheme() ? timing.navigateAt : 180)
  })
}
