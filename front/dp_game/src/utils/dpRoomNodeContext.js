/**
 * 跨节点房间：用 lookup 返回的 {@code wsRoute} 连 WebSocket；HTTP 经 Nginx 同源前缀
 * {@link #dpRoomAxiosPrefixFromWsRoute}（/dev-api 或 /b-api）调对应 JVM。
 * 存 sessionStorage，刷新同标签页仍有效。
 */
import { dpResultData, dpResultSuccess } from '@/utils/dpApiResult'

var SESSION_KEY = 'dpRoomNodeContext_v1'

/**
 * @param {string} wsRoute 后端 {@code dp.game.public-ws-url}，须与 Nginx 路径一致，如
 *   {@code ws://127.0.0.1:8880/dp-ws/dp-game} / {@code .../dp-ws-b/dp-game}
 * @returns {string} 如 http://127.0.0.1:8880
 */
export function wsRouteToHttpBase(wsRoute) {
  if (!wsRoute || typeof wsRoute !== 'string') return ''
  try {
    var u = new URL(wsRoute.trim())
    return (u.protocol === 'wss:' ? 'https:' : 'http:') + '//' + u.host
  } catch (e) {
    return ''
  }
}

/**
 * Nginx 开发示例中第二套 JVM 走 {@code /dp-ws-b}，对应 HTTP 前缀为 {@code /b-api}，否则 {@code /dev-api}。
 * 若直连 {@code ws://127.0.0.1:8089/...}（不含 path 里的 dp-ws-b），仍须走 {@code /b-api}，否则同域下会误打
 * 只转发到 8088 的 {@code /dev-api}，出现 joinRoom2「房间不存在」。
 * 开发环境未配 {@code VUE_APP_DEV_JAVA_PORT_B} 时默认按 8089 作为 B 实例端口（与常见双 JVM 一致）。
 * @param {string} wsRoute
 * @returns {string} "/dev-api" 或 "/b-api"
 */
export function dpRoomAxiosPrefixFromWsRoute(wsRoute) {
  if (!wsRoute || typeof wsRoute !== 'string') return '/dev-api'
  if (wsRoute.indexOf('dp-ws-b') >= 0) return '/b-api'
  if (process.env.NODE_ENV !== 'development') {
    return '/dev-api'
  }
  var portA = String(process.env.VUE_APP_DEV_JAVA_PORT || '8088').trim()
  var portB = String(process.env.VUE_APP_DEV_JAVA_PORT_B || '8089').trim()
  try {
    var u = new URL(wsRoute.trim())
    var p = String(u.port || '')
    if (portB && p === portB) return '/b-api'
    if (portA && p === portA) return '/dev-api'
  } catch (e) {
    /* ignore */
  }
  return '/dev-api'
}

export function getRoomNodeContext() {
  try {
    var raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    var o = JSON.parse(raw)
    if (!o || !o.apiBase || !o.wsRoute) return null
    return { apiBase: String(o.apiBase), wsRoute: String(o.wsRoute) }
  } catch (e) {
    return null
  }
}

export function setRoomNodeFromWsRoute(wsRoute) {
  var apiBase = wsRouteToHttpBase(wsRoute)
  if (!apiBase || !wsRoute) return
  sessionStorage.setItem(SESSION_KEY, JSON.stringify({ apiBase: apiBase, wsRoute: wsRoute.trim() }))
}

export function clearRoomNodeContext() {
  try {
    sessionStorage.removeItem(SESSION_KEY)
  } catch (e) {
    /* ignore */
  }
}

export function applyRoomNodeFromLookupBody(body) {
  if (!dpResultSuccess(body)) return false
  var d = dpResultData(body)
  if (!d || !d.wsRoute) return false
  setRoomNodeFromWsRoute(String(d.wsRoute))
  return true
}

/**
 * @param {{ maxAttempts?: number, delayMs?: number }} [opts] 集群写入注册表后偶发晚读，可短重试
 */
export async function fetchAndApplyRoomNodeLookup(http, roomId, opts) {
  var maxAttempts = (opts && opts.maxAttempts) || 3
  var delayMs = (opts && opts.delayMs) || 150
  for (var i = 0; i < maxAttempts; i++) {
    try {
      var res = await http.get('/dpRoom/lookupRoom', { params: { roomId: roomId } })
      if (applyRoomNodeFromLookupBody(res.data)) {
        return true
      }
    } catch (e) {
      if (i === maxAttempts - 1) {
        console.warn('fetchAndApplyRoomNodeLookup', e)
      }
    }
    if (i < maxAttempts - 1) {
      await new Promise(function (resolve) {
        setTimeout(resolve, delayMs)
      })
    }
  }
  return false
}

export async function ensureRoomNodeContextIfNeeded(http, roomId) {
  if (!roomId) return false
  if (getRoomNodeContext()) return true
  try {
    var ok = await fetchAndApplyRoomNodeLookup(http, roomId)
    return ok && !!getRoomNodeContext()
  } catch (e) {
    console.warn('ensureRoomNodeContextIfNeeded', e)
    return false
  }
}

export function buildGameWebSocketUrl(wsRoute, roomId, nickname, token) {
  var base = (wsRoute || '').trim()
  if (!base) return ''
  var sep = base.indexOf('?') >= 0 ? '&' : '?'
  var q =
    'roomId=' +
    encodeURIComponent(roomId) +
    '&nickname=' +
    encodeURIComponent(nickname || '')
  if (token) {
    q += '&token=' + encodeURIComponent(String(token))
  }
  return base + sep + q
}
