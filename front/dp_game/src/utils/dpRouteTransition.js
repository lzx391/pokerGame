/**
 * 路由 path → transition name（仅标准档 + flag on 时由 App.vue 挂载 <transition>）
 */
import { isRouteTransitionEnabled } from './dpRouteTransitionFlag'

function pathPattern(path) {
  if (!path) return ''
  if (path.startsWith('/game/')) return '/game/:id'
  if (path.startsWith('/room/')) return '/room/:id'
  if (path.startsWith('/hand-history/detail')) return '/hand-history/detail/:id'
  if (path.startsWith('/hand-history')) return '/hand-history'
  return path
}

var GAME = '/game/:id'
var LOBBY_ENTER_GAME = ['/home', '/room/:id', '/create-room']
var LOBBY_SUB = ['/leaderboard', '/hand-history', '/music-upload', '/image_upload']
var AUTH = ['/login', '/register', '/']

function isLobbySub(p) {
  return LOBBY_SUB.indexOf(p) >= 0 || p === '/hand-history/detail/:id'
}

/**
 * @param {import('vue-router').Route} to
 * @param {import('vue-router').Route} from
 * @param {{ fluidity?: string, flagOn?: boolean }} [opts]
 */
export function resolveRouteTransitionName(to, from, opts) {
  opts = opts || {}
  var fluidity =
    opts.fluidity ||
    (typeof document !== 'undefined'
      ? document.body.getAttribute('data-dp-fluidity')
      : 'standard')
  var flagOn = opts.flagOn !== undefined ? opts.flagOn : isRouteTransitionEnabled()

  if (fluidity === 'eco' || !flagOn) {
    return 'dp-route-none'
  }

  var toP = pathPattern(to && to.path)
  var fromP = pathPattern(from && from.path)

  if (fromP === GAME && toP === '/home') {
    return 'dp-route-leave-game'
  }

  if (toP === GAME && fromP !== '/guide') {
    if (LOBBY_ENTER_GAME.indexOf(fromP) >= 0) {
      return 'dp-route-enter-game'
    }
    if (fromP !== GAME && fromP !== '/guide') {
      return 'dp-route-enter-game'
    }
  }

  if (fromP === '/home' && toP === '/room/:id') {
    return 'dp-route-lobby'
  }

  if (fromP === '/room/:id' && toP === GAME) {
    return 'dp-route-enter-game'
  }

  if (
    (fromP === '/home' && isLobbySub(toP)) ||
    (toP === '/home' && isLobbySub(fromP))
  ) {
    return 'dp-route-lobby'
  }

  if (AUTH.indexOf(fromP) >= 0 && toP === '/home') {
    return 'dp-route-none'
  }

  if (fromP === GAME && toP === '/guide') {
    return 'dp-route-none'
  }

  if (to && to.meta && to.meta.transition === 'none') {
    return 'dp-route-none'
  }

  return 'dp-route-none'
}
