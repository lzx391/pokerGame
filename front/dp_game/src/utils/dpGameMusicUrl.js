/**
 * 曲库音频 URL：与 MusicUpload.vue 的 audioSrc 一致（开发走 /dev-api 代理到后端 /music/**）。
 */
export function musicFileSrc (webPath) {
  if (!webPath) return ''
  var base = process.env.NODE_ENV === 'production' ? '' : '/dev-api'
  if (webPath.indexOf('http') === 0) return webPath
  return base + webPath
}
