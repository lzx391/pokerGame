/**
 * 下载中心文件 URL：与 DownloadCenter.vue 的 fileSrc 一致（开发走 /dev-api 代理到后端 /files/**）。
 */
export function downloadFileSrc (webPath) {
  if (!webPath) return ''
  var base = process.env.NODE_ENV === 'production' ? '' : '/dev-api'
  if (webPath.indexOf('http') === 0) return webPath
  return base + webPath
}
