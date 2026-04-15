/**
 * 跨节点房间：用 lookup 返回的 {@code wsRoute} 连 WebSocket；HTTP 经 Nginx 同源前缀
 * {@link #dpRoomAxiosPrefixFromWsRoute}（如 /dev-api、/b-api）调对应 JVM。
 * 开发环境映射在 .env 用 {@code VUE_APP_DP_DEV_WS_HTTP_MAP}（B/C 与端口都写在这一行）。
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
 * 开发环境：按 {@code wsRoute} 选与 Nginx 一致的 axios 前缀。
 * 基础：{@code dp-ws}→/dev-api、{@code VUE_APP_DEV_JAVA_PORT}（默认 8088）→/dev-api。
 * B/C 及更多：.env 里 {@code VUE_APP_DP_DEV_WS_HTTP_MAP}；未配置时内置与 8089 对称的第三套（{@code dp-ws-c}、{@code 8090}→/c-api）。只跑两台 JVM 时请用 env 覆盖为仅 B：{@code dp-ws-b:/b-api,8089:/b-api}。
 * path 片段最长优先匹配；纯数字 key 为端口。
 * 生产环境固定 {@code /dev-api}（由网关/部署约定）。
 * @param {string} wsRoute
 * @returns {string} 如 "/dev-api"、"/b-api"
 */
export function dpRoomAxiosPrefixFromWsRoute(wsRoute) {
  if (!wsRoute || typeof wsRoute !== 'string') return '/dev-api'
  if (process.env.NODE_ENV !== 'development') {
    return '/dev-api'
  }
  var routing = getDevWsHttpRouting()
  var s = wsRoute.trim()
  var pathKeys = routing.pathKeys
  for (var i = 0; i < pathKeys.length; i++) {
    var pk = pathKeys[i]
    if (s.indexOf(pk) >= 0) {
      return routing.pathRules.get(pk) || '/dev-api'
    }
  }
  try {
    var u = new URL(s)
    var p = String(u.port || '')
    if (p && routing.portRules.has(p)) {
      return routing.portRules.get(p) || '/dev-api'
    }
  } catch (e) {
    /* ignore */
  }
  return '/dev-api'
}

/**
 * 解析逗号分隔的 key:value；纯数字 key 记入端口表，否则记入路径表。
 * @param {string} raw
 * @returns {{ pathRules: Map<string,string>, portRules: Map<string,string> }}
 */
function parseDpDevWsHttpMapSegment(raw) {
  var pathRules = new Map()
  var portRules = new Map()
  if (!raw || typeof raw !== 'string') {
    return { pathRules: pathRules, portRules: portRules }
  }
  raw.split(',').forEach(function (part) {
    part = part.trim()
    if (!part) return
    var idx = part.indexOf(':')
    if (idx <= 0) return
    var key = part.slice(0, idx).trim()
    var val = part.slice(idx + 1).trim()
    if (!key || !val) return
    if (/^\d+$/.test(key)) {
      portRules.set(key, val)
    } else {
      pathRules.set(key, val)
    }
  })
  return { pathRules: pathRules, portRules: portRules }
}

var _devWsHttpRouting = null

/** 未配置 {@code VUE_APP_DP_DEV_WS_HTTP_MAP} 时：与 8089/B 对称带上 8090/C（path + 端口），与 Nginx dp-ws-b、dp-ws-c 及直连端口一致 */
var DEFAULT_DP_DEV_WS_HTTP_MAP = 'dp-ws-b:/b-api,8089:/b-api,dp-ws-c:/c-api,8090:/c-api'

/**
 * 基础规则 + {@code VUE_APP_DP_DEV_WS_HTTP_MAP}（未设则等于常量 DEFAULT_DP_DEV_WS_HTTP_MAP）。
 * path 匹配顺序：key 按长度降序。
 */
function getDevWsHttpRouting() {
  if (_devWsHttpRouting) {
    return _devWsHttpRouting
  }
  var pathRules = new Map()
  var portRules = new Map()
  pathRules.set('dp-ws', '/dev-api')
  var portA = String(process.env.VUE_APP_DEV_JAVA_PORT || '8088').trim()
  if (portA) portRules.set(portA, '/dev-api')

  var raw = process.env.VUE_APP_DP_DEV_WS_HTTP_MAP
  var mapStr =
    raw && typeof raw === 'string' && raw.trim()
      ? raw.trim()
      : DEFAULT_DP_DEV_WS_HTTP_MAP
  var parsed = parseDpDevWsHttpMapSegment(mapStr)
  parsed.pathRules.forEach(function (v, k) {
    pathRules.set(k, v)
  })
  parsed.portRules.forEach(function (v, k) {
    portRules.set(k, v)
  })

  var pathKeys = Array.from(pathRules.keys()).sort(function (a, b) {
    return b.length - a.length
  })
  _devWsHttpRouting = { pathRules: pathRules, portRules: portRules, pathKeys: pathKeys }
  return _devWsHttpRouting
}

/**
 * 开发环境：未配置 {@code VUE_APP_LOBBY_API_BASES} 时，从当前 ws→HTTP 映射表收集所有 axios 前缀（去重排序），
 * 使大厅 {@code /dpRoom/publicRooms} 能随机打到含 /c-api 在内的各实例。
 */
export function getDpDevLobbyApiBases() {
  if (process.env.NODE_ENV !== 'development') {
    return []
  }
  var r = getDevWsHttpRouting()
  var set = new Set()
  r.pathRules.forEach(function (v) {
    set.add(v)
  })
  r.portRules.forEach(function (v) {
    set.add(v)
  })
  return Array.from(set).sort(function (a, b) {
    return a.localeCompare(b)
  })
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
