/**
 * 用户头像 URL：与 dpGameMusicUrl.js 一致（开发走 /dev-api 代理到后端 /images/**）。
 * 原图：/images/{userId}.{ext}（API avatarUrl）
 * 缩略图：/images/{userId}_sm.webp（由 avatarUrl 解析 userId 推导）
 */

/**
 * @param {string} avatarUrl 库内路径或绝对 URL
 * @returns {string|null} 数字 userId
 */
export function avatarUserIdFromUrl (avatarUrl) {
  if (!avatarUrl) return null
  var path = avatarUrl
  if (path.indexOf('http') === 0) {
    try {
      path = new URL(path).pathname
    } catch (e) {
      return null
    }
  }
  var m = path.match(/\/images\/(\d+)\.[a-zA-Z0-9]+$/i)
  return m ? m[1] : null
}

/**
 * @param {string} avatarUrl
 * @returns {string|null} 如 /images/12_sm.webp
 */
export function avatarThumbWebPath (avatarUrl) {
  var id = avatarUserIdFromUrl(avatarUrl)
  if (!id) return null
  return '/images/' + id + '_sm.webp'
}

/**
 * @param {string} webPath 库内路径，如 /images/12.jpg 或 /images/12_sm.webp
 * @param {number|string} [cacheBust] 上传后追加 ?t=
 * @param {{ variant?: 'thumb'|'full' }} [options] variant 仅作文档/调用方语义，URL 由 webPath 决定
 */
export function avatarFileSrc (webPath, cacheBust, options) {
  if (!webPath) return ''
  var base = process.env.NODE_ENV === 'production' ? '' : '/dev-api'
  var url = webPath.indexOf('http') === 0 ? webPath : base + webPath
  if (cacheBust != null && cacheBust !== '') {
    url += (url.indexOf('?') >= 0 ? '&' : '?') + 't=' + encodeURIComponent(String(cacheBust))
  }
  return url
}

/**
 * @param {string} avatarUrl
 * @param {'sm'|'md'|'lg'} size
 * @returns {string} 用于展示的库内 web 路径
 */
export function avatarDisplayWebPath (avatarUrl, size) {
  if (!avatarUrl) return ''
  if (size === 'lg') return avatarUrl
  return avatarThumbWebPath(avatarUrl) || avatarUrl
}

/**
 * API `avatarUpdatedAt`（epoch 毫秒）→ `?t=` 参数；无值时返回空串（与现网一致）。
 * @param {number|string|null|undefined} avatarUpdatedAt
 * @returns {string}
 */
export function avatarCacheBustFromUpdatedAt (avatarUpdatedAt) {
  if (avatarUpdatedAt == null) return ''
  return String(avatarUpdatedAt)
}

/** 无头像时展示昵称首字（与大厅/好友列表一致） */
export function avatarInitialFromNickname (nickname) {
  var n = (nickname || '').trim()
  if (!n) return '?'
  return n.slice(0, 1).toUpperCase()
}
