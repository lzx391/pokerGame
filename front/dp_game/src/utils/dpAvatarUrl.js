/**
 * 用户头像 URL：与 dpGameMusicUrl.js 一致（开发走 /dev-api 代理到后端 /images/**）。
 * @param {string} webPath 库内路径，如 /images/12.jpg
 * @param {number|string} [cacheBust] 上传后追加 ?t= 避免同路径浏览器缓存
 */
export function avatarFileSrc (webPath, cacheBust) {
  if (!webPath) return ''
  var base = process.env.NODE_ENV === 'production' ? '' : '/dev-api'
  var url = webPath.indexOf('http') === 0 ? webPath : base + webPath
  if (cacheBust != null && cacheBust !== '') {
    url += (url.indexOf('?') >= 0 ? '&' : '?') + 't=' + encodeURIComponent(String(cacheBust))
  }
  return url
}

/** 无头像时展示昵称首字（与大厅/好友列表一致） */
export function avatarInitialFromNickname (nickname) {
  var n = (nickname || '').trim()
  if (!n) return '?'
  return n.slice(0, 1).toUpperCase()
}
