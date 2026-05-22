/**
 * 预加载 route-game chunk；进对局前 await（最长 1.2s）再 router.push。
 */
var gameChunkPromise = null
var PREFETCH_TIMEOUT_MS = 1200

export function prefetchGameChunk() {
  if (!gameChunkPromise) {
    gameChunkPromise = import(
      /* webpackChunkName: "route-game" */
      /* webpackPrefetch: true */
      '@/components/game.vue'
    )
  }
  return gameChunkPromise
}

function prefetchWithTimeout() {
  return Promise.race([
    prefetchGameChunk().catch(function () {
      /* chunk 失败仍允许导航 */
    }),
    new Promise(function (resolve) {
      setTimeout(resolve, PREFETCH_TIMEOUT_MS)
    })
  ])
}

/** 先 prefetch 再 push /game/:id（标准/节能均可用，无路由动画） */
export async function navigateToGame(router, roomId) {
  var rid = roomId != null ? String(roomId).trim() : ''
  if (!rid) return
  await prefetchWithTimeout()
  router.push('/game/' + rid)
}
