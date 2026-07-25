/**
 * 统一维护 document.body[data-dp-game-theme]，避免各页在 beforeDestroy 里清掉属性
 * 导致 SPA 跳转后主题变量与背景不同步、整页高度/滚动错乱。
 *
 * 大厅路由真源（与 App.vue isLobbyRoute 保持一致）：/home、/create-room、/hand-history*、
 * /gallery*、/leaderboard、/music-upload、/download-center。
 * 新手一分钟 /guide 与对局页同样需 body[data-dp-game-theme]（retro8bit CRT 引导/弹层）。
 */
export function syncDpBodyGameTheme(store, router) {
  try {
    var r = router && router.currentRoute
    var path = r ? r.path : ''
    var lobbyLike =
      path === '/home' ||
      path === '/create-room' ||
      path.startsWith('/hand-history') ||
      path.startsWith('/gallery') ||
      path === '/leaderboard' ||
      path === '/music-upload' ||
      path === '/download-center' ||
      path === '/admin' ||
      path.indexOf('/admin/') === 0
    var gameLike = path.indexOf('/game') === 0
    var guideLike = path === '/guide'
    var authLike =
      path === '/login' || path === '/register' || path === '/'
    var st = store.state.dpGame
    if (lobbyLike || gameLike || guideLike || authLike) {
      var themeId = (st && st.gameUiTheme) || 'default'
      document.body.setAttribute('data-dp-game-theme', themeId)
    } else {
      document.body.removeAttribute('data-dp-game-theme')
    }
  } catch (e) {
    /* ignore */
  }
}
