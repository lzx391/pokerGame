import { galleryFileSrc } from '@features/gallery/utils/dpGalleryUrl'

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

function shouldSkipPrefetch () {
  if (typeof navigator === 'undefined') return false
  var conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection
  return !!(conn && conn.saveData)
}

/**
 * @param {string|{ previewUrl?: string, imageUrl?: string, url?: string, updatedAt?: number|string|null, cacheBust?: number|string|null }} entry
 * @returns {{ previewUrl: string, imageUrl: string, cacheBust: number|string|undefined }|null}
 */
function normalizePrefetchEntry (entry) {
  if (!entry) return null
  if (typeof entry === 'string') {
    return { previewUrl: entry, imageUrl: entry, cacheBust: undefined }
  }
  if (typeof entry === 'object') {
    var previewUrl = entry.previewUrl || entry.url || ''
    var imageUrl = entry.imageUrl || entry.url || ''
    if (!previewUrl && !imageUrl) return null
    var cacheBust = entry.cacheBust
    if (cacheBust == null || cacheBust === '') {
      cacheBust = entry.updatedAt
    }
    return {
      previewUrl: previewUrl || imageUrl,
      imageUrl: imageUrl || previewUrl,
      cacheBust: cacheBust
    }
  }
  return null
}

/**
 * 预加载画廊 previewUrl（_sm.webp）；可选 prefetchFull 拉原图。
 * @param {Array<string|object>} items API item 或 previewUrl/imageUrl
 * @param {{ concurrency?: number, prefetchFull?: boolean }} [options]
 */
export function prefetchGalleryUrls (items, options) {
  if (shouldSkipPrefetch()) return Promise.resolve()
  options = options || {}
  var concurrency = options.concurrency != null ? options.concurrency : 6
  var prefetchFull = !!options.prefetchFull

  var srcSet = {}
  var list = Array.isArray(items) ? items : []
  for (var i = 0; i < list.length; i++) {
    var normalized = normalizePrefetchEntry(list[i])
    if (!normalized) continue
    var preview = normalized.previewUrl
    if (preview) {
      srcSet[galleryFileSrc(preview, normalized.cacheBust)] = true
    }
    if (prefetchFull && normalized.imageUrl) {
      srcSet[galleryFileSrc(normalized.imageUrl, normalized.cacheBust)] = true
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

/**
 * 按滚动方向预取接下来 N 张 preview。
 * @param {Array<object>} items
 * @param {number} fromIndex 当前可见区近似索引
 * @param {number} [aheadCount=3]
 * @param {{ concurrency?: number }} [options]
 */
export function prefetchGalleryAhead (items, fromIndex, aheadCount, options) {
  if (shouldSkipPrefetch()) return Promise.resolve()
  if (!Array.isArray(items) || !items.length) return Promise.resolve()
  aheadCount = aheadCount != null ? aheadCount : 3
  fromIndex = fromIndex != null ? fromIndex : 0
  var slice = []
  for (var i = 1; i <= aheadCount; i++) {
    slice.push(items[(fromIndex + i) % items.length])
  }
  return prefetchGalleryUrls(slice, options)
}

/**
 * 悬停 / 打开详情前预热全尺寸 imageUrl。
 * @param {object|string} item
 */
export function prefetchGalleryFull (item) {
  if (shouldSkipPrefetch()) return Promise.resolve()
  var normalized = normalizePrefetchEntry(item)
  if (!normalized || !normalized.imageUrl) return Promise.resolve()
  return loadOneImage(galleryFileSrc(normalized.imageUrl, normalized.cacheBust))
}
