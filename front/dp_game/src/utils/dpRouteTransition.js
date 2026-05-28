/**
 * 路由 path / meta → transition name（仅标准档 + flag on 时由 App.vue 挂载 <transition>）
 */
import { isRouteTransitionEnabled } from './dpRouteTransitionFlag'

function pathPattern(path) {
  if (!path) return ''
  if (path.startsWith('/game/')) return '/game/:id'
  if (path.startsWith('/hand-history/detail')) return '/hand-history/detail/:id'
  if (path.startsWith('/hand-history')) return '/hand-history'
  return path
}

var GAME = '/game/:id'
var LOBBY_SUB = ['/leaderboard', '/hand-history', '/music-upload', '/download-center', '/image_upload']
var AUTH = ['/login', '/register', '/']

function isLobbySub(p) {
  return LOBBY_SUB.indexOf(p) >= 0 || p === '/hand-history/detail/:id'
}

/** @param {import('vue-router').Route} route */
function metaTransition(route) {
  return route && route.meta && route.meta.transition
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

  if (metaTransition(to) === 'none' || metaTransition(from) === 'none') {
    return 'dp-route-none'
  }

  var toP = pathPattern(to && to.path)
  var fromP = pathPattern(from && from.path)

  if (fromP === GAME && toP === '/home') {
    return 'dp-route-leave-game'
  }

  /* home ↔ 创建页：右侧滑入 */
  if (
    (fromP === '/home' && toP === '/create-room') ||
    (fromP === '/create-room' && toP === '/home')
  ) {
    return 'dp-route-slide-from-right'
  }

  /* 进对局：轻微放大 + 淡入（创建页 / 大厅等 → game） */
  if (toP === GAME && fromP !== '/guide' && fromP !== GAME) {
    return 'dp-route-zoom-fade-in'
  }

  if (
    (fromP === '/home' && isLobbySub(toP)) ||
    (toP === '/home' && isLobbySub(fromP))
  ) {
    return 'dp-route-fade'
  }

  if (AUTH.indexOf(fromP) >= 0 && toP === '/home') {
    return 'dp-route-none'
  }

  if (fromP === GAME && toP === '/guide') {
    return 'dp-route-none'
  }

  var toMeta = metaTransition(to)
  if (toMeta === 'slide-from-right') return 'dp-route-slide-from-right'
  if (toMeta === 'zoom-fade-in') return 'dp-route-zoom-fade-in'
  if (toMeta === 'fade') return 'dp-route-fade'

  return 'dp-route-none'
}
