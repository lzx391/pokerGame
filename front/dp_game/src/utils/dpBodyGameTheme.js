/**
 * 统一维护 document.body[data-dp-game-theme]，避免各页在 beforeDestroy 里清掉属性
 * 导致 SPA 跳转后主题变量与背景不同步、整页高度/滚动错乱。
 * 「自定义」主题时：属性值为**预设底** id，强调色用内联 --dp-* 覆盖（与 store 一致）。
 *
 * 大厅路由真源（与 App.vue isLobbyRoute 保持一致）：/home、/create-room、/hand-history*、
 * /leaderboard、/music-upload、/room/*。
 */
import {
  resolveEffectiveThemeId,
  applyCustomThemeToBody,
  clearCustomThemeFromBody
} from './dpGameCustomTheme'

export function syncDpBodyGameTheme(store, router) {
  try {
    var r = router && router.currentRoute
    var path = r ? r.path : ''
    var lobbyLike =
      path === '/home' ||
      path === '/create-room' ||
      path.startsWith('/hand-history') ||
      path === '/leaderboard' ||
      path === '/music-upload' ||
      path.startsWith('/room/')
    var gameLike = path.indexOf('/game') === 0
    var authLike =
      path === '/login' || path === '/register' || path === '/'
    var st = store.state.dpGame
    if (lobbyLike || gameLike || authLike) {
      var raw = (st && st.gameUiTheme) || 'default'
      var eff = resolveEffectiveThemeId(raw, st && st.customThemeBase)
      document.body.setAttribute('data-dp-game-theme', eff)
      if (raw === 'custom' && st) {
        applyCustomThemeToBody(st.customAccent, st.customThemeOverrides)
      } else {
        clearCustomThemeFromBody()
      }
    } else {
      document.body.removeAttribute('data-dp-game-theme')
      clearCustomThemeFromBody()
    }
  } catch (e) {
    /* ignore */
  }
}
