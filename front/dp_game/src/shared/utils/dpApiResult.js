/**
 * 解析后端 ResultUtil 风格 JSON：success、code、message、data。
 * 成功响应通常为 success:true 且 code:20000；部分环境序列化或网关可能省略 success，
 * 仅返回 code/message/data，此时仍应按成功解析（见 GameInviteFriendSheet /dp/friends）。
 */
const DP_RESULT_CODE_OK = 20000

/** @param {any} body axios 的 res.data */
export function dpResultSuccess(body) {
  if (!body || typeof body !== 'object') return false
  if (body.success === false) return false
  if (body.success === true) return true
  var c = body.code
  return c === DP_RESULT_CODE_OK || c === String(DP_RESULT_CODE_OK)
}

/** 成功时 data 字段（可能为 null） */
export function dpResultData(body) {
  if (!dpResultSuccess(body)) return null
  var d = body.data
  return d && typeof d === 'object' ? d : {}
}

/** 失败或需提示时的文案：优先 data.message，其次顶层 message */
export function dpResultMessage(body) {
  if (!body) return '请求失败'
  if (body.data && body.data.message != null) return String(body.data.message)
  if (body.message != null) return String(body.message)
  return '请求失败'
}

/**
 * axios catch：优先 ResultUtil 文案；HTTP 409 等与好友关系相关的业务错误给固定中文兜底。
 * @param {any} err
 * @param {string} [conflictFallback]
 */
export function dpAxiosErrorMessage(err, conflictFallback) {
  var st = err && err.response && err.response.status
  var data = err && err.response && err.response.data
  if (data && typeof data === 'object') {
    var nested = data.data && data.data.message
    if (nested != null && String(nested).trim()) return String(nested)
    if (data.message != null && String(data.message).trim()) return String(data.message)
  }
  if (st === 409) {
    return conflictFallback || '与对方非好友关系或条件不满足，无法完成此操作'
  }
  return (err && err.message) ? String(err.message) : '网络错误'
}
