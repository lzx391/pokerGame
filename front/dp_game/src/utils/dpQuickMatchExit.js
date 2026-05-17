/**
 * 服务端快匹队列出队（与 quickMatch2 同源 query：nickname/userId）。Token 沿用 axios 全局配置。
 * 幂等；失败仅打日志，不抛错。
 */
export function postQuickMatchCancel2(http, user) {
  if (!http || !user || !user.nickname) return Promise.resolve()
  const params = { nickname: user.nickname }
  if (user.userId != null && user.userId !== '') {
    params.userId = user.userId
  }
  return http.post('/dpRoom/quickMatchCancel2', null, { params }).catch(function (e) {
    console.warn('quickMatchCancel2', e)
  })
}
