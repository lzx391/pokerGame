/**
 * 大厅进对局：retro8bit 先终端 boot，再 CRT 全屏交接；其它主题保持 prefetch + push。
 */
import { prefetchWithTimeout } from '@shared/utils/dpPrefetchGameRoute'
import { getRetroBootLines } from '@shared/utils/dpRetroBootLines'
import {
  enterGameWithRetroHandoff,
  isRetro8bitTheme,
  shouldSkipRetroEnterEffects
} from '@shared/utils/dpRetroEnterGameHandoff'

/**
 * @param {import('vue-router').default} router
 * @param {string} roomId
 * @param {{
 *   entryPath?: 'join'|'quickmatch'|'create'|string,
 *   bootRef?: { play: (opts: object) => void } | null
 * }} [opts]
 * @returns {Promise<void>}
 */
export async function enterGameFromLobby(router, roomId, opts) {
  opts = opts || {}
  var rid = roomId != null ? String(roomId).trim() : ''
  if (!rid) return

  await prefetchWithTimeout()

  if (!isRetro8bitTheme() || shouldSkipRetroEnterEffects()) {
    await router.push({ name: 'game', params: { roomId: rid } }).catch(function () {})
    return
  }

  var lines = getRetroBootLines(opts.entryPath || 'join')
  var boot = opts.bootRef

  if (boot && typeof boot.play === 'function') {
    await new Promise(function (resolve) {
      boot.play({
        lines: lines,
        onDone: function () {
          enterGameWithRetroHandoff(router, rid, { navigation: 'push' }).then(resolve)
        }
      })
    })
    return
  }

  await enterGameWithRetroHandoff(router, rid, { navigation: 'push' })
}
