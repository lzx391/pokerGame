import { dpResultSuccess, dpResultData } from './dpApiResult'

/**
 * 用本地缓存的昵称+密码请求 loginProfile，把 userId 写回 localStorage。
 * 解决旧版 userInfo 仅有昵称/密码、未带 userId 时牌谱等接口无法按账号 ID 查询的问题。
 *
 * @param {import('axios').AxiosInstance} http
 * @returns {Promise<object|null>} 合并 userId 后的 user 对象；无法恢复时返回 null
 */
export async function ensureDpUserIdInStorage(http) {
  if (!http || typeof http.get !== 'function') return null
  var raw
  try {
    raw = localStorage.getItem('userInfo')
  } catch (e) {
    return null
  }
  if (!raw) return null
  var user
  try {
    user = JSON.parse(raw)
  } catch (e) {
    return null
  }
  if (!user || !user.nickname || !user.password) return null

  var existing = user.userId
  if (existing != null && existing !== '') {
    var n = Number(existing)
    if (!isNaN(n) && n > 0) {
      user.userId = n
      if (!user.token) {
        try {
          var resTok = await http.get('/dpUser/loginProfile', {
            params: { nickname: user.nickname, password: user.password }
          })
          var bodyTok = resTok && resTok.data
          if (dpResultSuccess(bodyTok)) {
            var payTok = dpResultData(bodyTok)
            if (payTok && payTok.token) {
              user.token = payTok.token
              localStorage.setItem('userInfo', JSON.stringify(user))
            }
          }
        } catch (e) {
          console.error('ensureDpUserIdInStorage token', e)
        }
      }
      return user
    }
  }

  try {
    var res = await http.get('/dpUser/loginProfile', {
      params: { nickname: user.nickname, password: user.password }
    })
    var body = res && res.data
    if (dpResultSuccess(body)) {
      var payload = dpResultData(body)
      if (payload && payload.userId != null) {
        user.userId = Number(payload.userId)
        if (payload.token) user.token = payload.token
        localStorage.setItem('userInfo', JSON.stringify(user))
        return user
      }
    }
  } catch (e) {
    console.error('ensureDpUserIdInStorage', e)
  }
  return user
}
