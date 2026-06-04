/**
 * App-level social SSE client (mailbox notify + friend presence).
 * Persists across SPA routes while JWT is present; disconnect on logout / 401.
 */
import { buildSocialStreamUrl } from '@/utils/dpSocialStream'

/** @type {import('vuex').Store<any> | null} */
var storeRef = null
/** @type {import('axios').AxiosInstance | null} */
var httpRef = null

var eventSource = null
var session = 0
var reconnectTimer = null
var reconnectAttempt = 0
var activeToken = ''
var notifyHandler = null
var presenceHandler = null

function readTokenFromStorage() {
  try {
    var raw = localStorage.getItem('userInfo')
    if (!raw) return ''
    var u = JSON.parse(raw)
    return u && u.token ? String(u.token).trim() : ''
  } catch (e) {
    return ''
  }
}

function clearReconnectTimer() {
  if (reconnectTimer != null) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

function scheduleReconnect() {
  if (reconnectTimer != null) return
  if (!activeToken) return
  var delay = Math.min(30000, 1000 * Math.pow(2, reconnectAttempt))
  reconnectTimer = setTimeout(function () {
    reconnectTimer = null
    reconnectAttempt++
    connectInternal(true)
  }, delay)
}

function onNotify(raw) {
  if (!storeRef) return
  if (raw) {
    try {
      var parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
      if (process.env.NODE_ENV !== 'production') {
        console.info('[social-sse] notify received', parsed)
      }
      storeRef.dispatch('dpMailbox/applyNotifyPayload', parsed)
      return
    } catch (e) {
      if (process.env.NODE_ENV !== 'production') {
        console.warn('[social-sse] notify parse failed', raw, e)
      }
    }
  }
  if (httpRef) {
    storeRef.dispatch('dpMailbox/fetchNotifySummary', { http: httpRef }).catch(function () {})
  }
}

function onFriendPresence(raw) {
  if (!storeRef || !raw) return
  try {
    var parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (process.env.NODE_ENV !== 'production') {
      console.info('[social-sse] friendPresence received', parsed)
    }
    storeRef.dispatch('dpMailbox/applyFriendPresencePayload', parsed)
  } catch (e) {
    if (process.env.NODE_ENV !== 'production') {
      console.warn('[social-sse] friendPresence parse failed', raw, e)
    }
  }
}

function teardownEventSource() {
  session++
  clearReconnectTimer()
  var es = eventSource
  var onNotify = notifyHandler
  var onPresence = presenceHandler
  eventSource = null
  notifyHandler = null
  presenceHandler = null
  if (es) {
    es.onopen = null
    es.onerror = null
    es.onmessage = null
    if (onNotify) {
      try { es.removeEventListener('notify', onNotify) } catch (e) { /* ignore */ }
    }
    if (onPresence) {
      try { es.removeEventListener('friendPresence', onPresence) } catch (e) { /* ignore */ }
    }
    try { es.close() } catch (e) { /* ignore */ }
  }
}

function connectInternal(isReconnect) {
  if (!storeRef || !httpRef) return
  var token = activeToken || readTokenFromStorage()
  if (!token) return
  activeToken = token
  var url = buildSocialStreamUrl(token)
  if (!url) return

  teardownEventSource()
  var currentSession = ++session
  var es
  try {
    es = new EventSource(url)
  } catch (e) {
    scheduleReconnect()
    return
  }
  eventSource = es

  notifyHandler = function (ev) {
    if (session !== currentSession) return
    onNotify(ev && ev.data)
  }
  presenceHandler = function (ev) {
    if (session !== currentSession) return
    onFriendPresence(ev && ev.data)
  }

  es.addEventListener('notify', notifyHandler)
  es.addEventListener('friendPresence', presenceHandler)
  es.onmessage = notifyHandler
  es.onopen = function () {
    if (session !== currentSession) return
    reconnectAttempt = 0
    if (isReconnect && httpRef) {
      storeRef.dispatch('dpMailbox/fetchNotifySummary', { http: httpRef }).catch(function () {})
    }
  }
  es.onerror = function () {
    if (session !== currentSession) return
    try { es.close() } catch (err) { /* ignore */ }
    if (eventSource === es) eventSource = null
    scheduleReconnect()
  }
}

/**
 * @param {import('vuex').Store<any>} store
 * @param {import('axios').AxiosInstance} http
 */
export function initDpSocialStreamClient(store, http) {
  storeRef = store
  httpRef = http
}

/** Connect or keep alive when JWT present; disconnect when absent. */
export function syncDpSocialStreamConnection() {
  var token = readTokenFromStorage()
  if (!token) {
    disconnectDpSocialStream()
    return
  }
  if (token === activeToken && eventSource) return
  activeToken = token
  reconnectAttempt = 0
  connectInternal(false)
}

/** Force disconnect (logout / 401). */
export function disconnectDpSocialStream() {
  activeToken = ''
  reconnectAttempt = 0
  teardownEventSource()
}

/** @returns {boolean} */
export function isDpSocialStreamConnected() {
  return !!eventSource
}
