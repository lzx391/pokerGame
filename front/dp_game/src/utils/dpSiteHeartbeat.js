/**
 * 站点级心跳：与 POST /dpRoom/heartbeat 并行；无 sendBeacon，关页即依赖后端 TTL。
 * 在已登录且停留 DP 业务路由时由 router.afterEach 统一启停。
 */
import { dpResultData, dpResultSuccess } from './dpApiResult'

var timerId = null
var httpRef = null
/** @type {number} */
var intervalMs = 15000
var configPromise = null

var DEFAULT_INTERVAL_MS = 15000

function isLoggedIn() {
  try {
    var raw = localStorage.getItem('userInfo')
    if (!raw) return false
    var u = JSON.parse(raw)
    return !!(u && u.token)
  } catch (e) {
    return false
  }
}

/**
 * 登录/注册/根重定向不算「停留的业务页」。
 * @param {string} path
 */
function isDpDwellRoute(path) {
  if (!path) return false
  if (path === '/login' || path === '/register' || path === '/') return false
  return true
}

function postSiteHeartbeat() {
  if (!httpRef) return
  httpRef.post('/dp/presence/site-heartbeat', null).catch(function () {
    /* 网络抖动不抛；401 仍由全局拦截器处理 */
  })
}

function applyIntervalFromConfigBody(body) {
  if (!dpResultSuccess(body)) return null
  var d = dpResultData(body)
  var ms = d && d.suggestedIntervalMs
  var n = typeof ms === 'number' ? ms : Number(ms)
  if (isFinite(n) && n >= 5000) return n
  return null
}

function fetchSuggestedInterval(http) {
  if (configPromise) return configPromise
  configPromise = http
    .get('/dp/presence/site-heartbeat/config')
    .then(function (res) {
      return applyIntervalFromConfigBody(res && res.data)
    })
    .catch(function () {
      return null
    })
  return configPromise
}

/**
 * 停止站点心跳（登出、进入登录页或热更新时安全调用）。
 */
export function stopDpSiteHeartbeat() {
  if (timerId != null) {
    clearInterval(timerId)
    timerId = null
  }
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {import('vue-router').default} router
 */
export function syncDpSiteHeartbeat(http, router) {
  stopDpSiteHeartbeat()
  httpRef = http
  var routePath = router && router.currentRoute ? router.currentRoute.path : ''
  if (!http || !router || !isLoggedIn() || !isDpDwellRoute(routePath)) {
    return
  }
  intervalMs = DEFAULT_INTERVAL_MS
  timerId = setInterval(postSiteHeartbeat, intervalMs)
  postSiteHeartbeat()

  fetchSuggestedInterval(http).then(function (ms) {
    var p = router.currentRoute ? router.currentRoute.path : ''
    if (!isLoggedIn() || !isDpDwellRoute(p)) return
    if (ms != null && ms !== intervalMs) {
      intervalMs = ms
      stopDpSiteHeartbeat()
      timerId = setInterval(postSiteHeartbeat, intervalMs)
      postSiteHeartbeat()
    }
  })
}
