/** REST client for NPC TAG decision trace (owner + experimental password). */

import { dpResultData, dpResultSuccess } from './dpApiResult'

/**
 * @param {import('axios').AxiosInstance} http
 * @param {string} roomId
 * @param {string} experimentalPassword
 */
export async function fetchTraceHands(http, roomId, experimentalPassword) {
  var res = await http.get('/dpRoom/npcDecisionTrace/hands', {
    params: {
      roomId: roomId,
      experimentalPassword: experimentalPassword
    }
  })
  var body = res.data
  if (!dpResultSuccess(body)) {
    return { ok: false, body: body, hands: [] }
  }
  var d = dpResultData(body) || {}
  return {
    ok: true,
    body: body,
    hands: Array.isArray(d.hands) ? d.hands : []
  }
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {string} roomId
 * @param {number|string} handSeed
 * @param {string} experimentalPassword
 */
export async function fetchTraceHand(http, roomId, handSeed, experimentalPassword) {
  var res = await http.get('/dpRoom/npcDecisionTrace/hand', {
    params: {
      roomId: roomId,
      handSeed: handSeed,
      experimentalPassword: experimentalPassword
    }
  })
  var body = res.data
  if (!dpResultSuccess(body)) {
    return { ok: false, body: body, bundle: null }
  }
  var d = dpResultData(body) || {}
  return {
    ok: true,
    body: body,
    bundle: d.bundle || null
  }
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {string} roomId
 * @param {number|string} handSeed
 * @param {string} actionId
 * @param {string} experimentalPassword
 */
export async function fetchTraceAction(http, roomId, handSeed, actionId, experimentalPassword) {
  var res = await http.get('/dpRoom/npcDecisionTrace/action', {
    params: {
      roomId: roomId,
      handSeed: handSeed,
      actionId: actionId,
      experimentalPassword: experimentalPassword
    }
  })
  var body = res.data
  if (!dpResultSuccess(body)) {
    return { ok: false, body: body, action: null }
  }
  var d = dpResultData(body) || {}
  return {
    ok: true,
    body: body,
    action: d.action || null
  }
}

/** Upsert hand bundle by handSeed; newer sealedAtMs wins; insert at head if new. */
export function mergeTraceHandBundle(hands, bundle) {
  if (!bundle || bundle.handSeed == null) return hands || []
  var list = Array.isArray(hands) ? hands.slice() : []
  var seed = bundle.handSeed
  var idx = -1
  for (var i = 0; i < list.length; i++) {
    if (list[i] && list[i].handSeed === seed) {
      idx = i
      break
    }
  }
  if (idx >= 0) {
    list.splice(idx, 1, bundle)
  } else {
    list.unshift(bundle)
  }
  list.sort(function (a, b) {
    var ai = a && a.handIndex != null ? a.handIndex : 0
    var bi = b && b.handIndex != null ? b.handIndex : 0
    return bi - ai
  })
  if (list.length > 100) list.length = 100
  return list
}
