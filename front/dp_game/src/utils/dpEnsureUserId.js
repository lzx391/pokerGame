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
      return user
    }
  }

  try {
    var res = await http.get('/dpUser/loginProfile', {
      params: { nickname: user.nickname, password: user.password }
    })
    var d = res && res.data
    if (d && d.ok === true && d.userId != null) {
      user.userId = Number(d.userId)
      localStorage.setItem('userInfo', JSON.stringify(user))
      return user
    }
  } catch (e) {
    console.error('ensureDpUserIdInStorage', e)
  }
  return user
}
