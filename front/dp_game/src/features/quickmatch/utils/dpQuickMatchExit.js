/**
 * 服务端快匹队列出队。Token 沿用 axios 全局配置。
 * 幂等；失败仅打日志，不抛错。
 */
export function postQuickMatchCancel2(http) {
  if (!http) return Promise.resolve()
  return http.post('/dpRoom/quickMatchCancel2', null, { params: {} }).catch(function (e) {
    console.warn('quickMatchCancel2', e)
  })
}
