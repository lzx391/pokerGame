/**
 * 画廊图片 URL：与 dpAvatarUrl.js 一致（开发走 /dev-api 代理到后端 /images/**）。
 * @param {string} webPath API 返回的 imageUrl 或 previewUrl
 * @param {number|string} [cacheBust]
 */
export function galleryFileSrc (webPath, cacheBust) {
  if (!webPath) return ''
  var base = process.env.NODE_ENV === 'production' ? '' : '/dev-api'
  var url = webPath.indexOf('http') === 0 ? webPath : base + webPath
  if (cacheBust != null && cacheBust !== '') {
    url += (url.indexOf('?') >= 0 ? '&' : '?') + 't=' + encodeURIComponent(String(cacheBust))
  }
  return url
}
