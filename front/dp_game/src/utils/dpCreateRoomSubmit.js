import { exitLobbyQuickMatchSilently } from '@/utils/dpLobbyQuickMatchExit'
import { enterGameFromLobby } from '@/utils/dpLobbyEnterGame'

var THINK_TIME_MIN = 15
var THINK_TIME_MAX = 180
var THINK_TIME_DEFAULT = 30

/**
 * @param {*} raw
 * @returns {number} 15–180，非法则 30
 */
export function clampThinkTimeSeconds(raw) {
  var n = Math.round(Number(raw))
  if (!Number.isFinite(n)) return THINK_TIME_DEFAULT
  return Math.min(THINK_TIME_MAX, Math.max(THINK_TIME_MIN, n))
}

/**
 * 创建房间并开局（与经典 CreateRoom.submit 同契约）。
 * @param {object} opts
 * @param {import('vue').default['prototype']['$http']} opts.http
 * @param {import('vue-router').default} opts.router
 * @param {object} opts.user — localStorage userInfo
 * @param {object} opts.config — { smallBlind, startingStackBb, maxSeatCount, thinkTimeSeconds?, roomPassword? }
 * @param {function(string): void} [opts.onError] — 用户可见错误
 * @param {boolean} [opts.crtHandoff] — retro8bit：终端 boot + CRT 交接后进 /game
 * @param {{ play: (opts: object) => void } | null} [opts.bootRef] — DpCrtBootSequence（与大厅进房一致）
 * @returns {Promise<{ ok: boolean, roomId?: string, error?: string }>}
 */
export async function dpCreateRoomAndStart({
  http,
  router,
  user,
  config,
  onError,
  crtHandoff,
  bootRef
}) {
  var notify = typeof onError === 'function' ? onError : function () {}

  if (!user || !user.nickname) {
    notify('未登录，请先登录')
    return { ok: false, error: 'not_logged_in' }
  }

  try {
    await exitLobbyQuickMatchSilently(http, user, {})
    var sc = Math.max(1, Number(config.smallBlind) || 5)
    var cap = Math.round(Number(config.maxSeatCount) || 9)
    cap = Math.min(9, Math.max(2, cap))
    var params = {
      nickname: user.nickname,
      smallBlindChips: sc,
      bigBlindChips: sc * 2,
      startingStackBb: Math.max(5, Number(config.startingStackBb) || 50),
      maxSeatCount: cap,
      thinkTimeSeconds: clampThinkTimeSeconds(config.thinkTimeSeconds)
    }
    if (config.roomPassword) {
      params.roomPassword = config.roomPassword
    }
    if (user.userId != null && user.userId !== '') {
      params.userId = user.userId
    }

    var res = await http.post('/dpRoom/createRoom', null, { params: params })
    var roomId = res.data && res.data.roomId
    if (!roomId) {
      notify('创建失败：未返回房间号')
      return { ok: false, error: 'no_room_id' }
    }

    var startRes = await http.post('/dpRoom/startGame', null, {
      params: {
        roomId: roomId,
        ownerNickname: user.nickname
      }
    })
    if (startRes.data !== 'ok') {
      notify('房间已创建但开局未成功，请从大厅进入该房间重试')
      await goGame(router, roomId, crtHandoff, bootRef)
      return { ok: true, roomId: roomId, partial: true }
    }

    await goGame(router, roomId, crtHandoff, bootRef)
    return { ok: true, roomId: roomId }
  } catch (e) {
    console.error('dpCreateRoomAndStart', e)
    notify('创建失败，请检查网络或后端是否已启动')
    return { ok: false, error: 'network' }
  }
}

/**
 * @param {import('vue-router').default} router
 * @param {string} roomId
 * @param {boolean} [crtHandoff]
 * @param {{ play: (opts: object) => void } | null} [bootRef]
 */
async function goGame(router, roomId, crtHandoff, bootRef) {
  if (crtHandoff) {
    await enterGameFromLobby(router, roomId, {
      entryPath: 'create',
      bootRef: bootRef || null
    })
    return
  }
  await router.replace({ name: 'game', params: { roomId: roomId } })
}
