import { avatarFileSrc, avatarThumbWebPath, avatarCacheBustFromUpdatedAt } from '@/utils/dpAvatarUrl'

function loadOneImage (src) {
  return new Promise(function (resolve) {
    if (!src) {
      resolve()
      return
    }
    var img = new Image()
    img.onload = function () { resolve() }
    img.onerror = function () { resolve() }
    img.src = src
  })
}

/**
 * @param {string|{ avatarUrl?: string, url?: string, avatarUpdatedAt?: number|string|null, cacheBust?: number|string }} entry
 * @param {number|string|undefined} defaultCacheBust
 * @returns {{ avatarUrl: string, cacheBust: number|string|undefined }|null}
 */
function normalizePrefetchEntry (entry, defaultCacheBust) {
  if (!entry) return null
  if (typeof entry === 'string') {
    return { avatarUrl: entry, cacheBust: defaultCacheBust }
  }
  if (typeof entry === 'object') {
    var avatarUrl = entry.avatarUrl || entry.url || ''
    if (!avatarUrl) return null
    var cacheBust = entry.cacheBust
    if (cacheBust == null || cacheBust === '') {
      cacheBust = avatarCacheBustFromUpdatedAt(entry.avatarUpdatedAt)
    }
    if (cacheBust == null || cacheBust === '') {
      cacheBust = defaultCacheBust
    }
    return { avatarUrl: avatarUrl, cacheBust: cacheBust }
  }
  return null
}

/**
 * 预加载头像（thumb 必取；原图可选）。去重、静默失败、并发限制。
 * @param {Array<string|{ avatarUrl?: string, url?: string, avatarUpdatedAt?: number|string|null, cacheBust?: number|string }>} urls API avatarUrl 或带 bust 的对象
 * @param {{ concurrency?: number, prefetchFull?: boolean, cacheBust?: number|string }} [options] 全局 cacheBust（字符串项或未指定 per-url bust 时生效）
 */
export function prefetchAvatarUrls (urls, options) {
  options = options || {}
  var concurrency = options.concurrency != null ? options.concurrency : 6
  var prefetchFull = !!options.prefetchFull
  var defaultCacheBust = options.cacheBust

  var srcSet = {}
  var list = Array.isArray(urls) ? urls : []
  for (var i = 0; i < list.length; i++) {
    var normalized = normalizePrefetchEntry(list[i], defaultCacheBust)
    if (!normalized) continue
    var avatarUrl = normalized.avatarUrl
    var cacheBust = normalized.cacheBust
    var thumbPath = avatarThumbWebPath(avatarUrl)
    if (thumbPath) {
      srcSet[avatarFileSrc(thumbPath, cacheBust)] = true
    }
    if (prefetchFull) {
      srcSet[avatarFileSrc(avatarUrl, cacheBust)] = true
    }
  }

  var queue = Object.keys(srcSet)
  if (!queue.length) return Promise.resolve()

  var index = 0
  function worker () {
    if (index >= queue.length) return Promise.resolve()
    var src = queue[index++]
    return loadOneImage(src).then(worker)
  }

  var workers = []
  var n = Math.min(concurrency, queue.length)
  for (var w = 0; w < n; w++) {
    workers.push(worker())
  }
  return Promise.all(workers)
}
