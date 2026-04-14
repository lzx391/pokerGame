/**
 * 统一维护 document.body[data-dp-game-theme]，避免各页在 beforeDestroy 里清掉属性
 * 导致 SPA 跳转后主题变量与背景不同步、整页高度/滚动错乱。
 */
export function syncDpBodyGameTheme(store, router) {
  try {
    var r = router && router.currentRoute
    var path = r ? r.path : ''
    var lobbyLike =
      path === '/home' ||
      path === '/create-room' ||
      path.startsWith('/hand-history') ||
      path === '/music-upload' ||
      path.startsWith('/room/')
    var gameLike = path.indexOf('/game') === 0
    var authLike =
      path === '/login' || path === '/register' || path === '/'
    if (lobbyLike || gameLike || authLike) {
      var id =
        (store.state.dpGame && store.state.dpGame.gameUiTheme) || 'default'
      document.body.setAttribute('data-dp-game-theme', id)
    } else {
      document.body.removeAttribute('data-dp-game-theme')
    }
  } catch (e) {
    /* ignore */
  }
}
