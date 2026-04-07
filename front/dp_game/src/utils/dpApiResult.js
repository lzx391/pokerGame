/**
 * 解析后端 ResultUtil 风格 JSON：success、code、message、data。
 */

/** @param {any} body axios 的 res.data */
export function dpResultSuccess(body) {
  return body && body.success === true
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
