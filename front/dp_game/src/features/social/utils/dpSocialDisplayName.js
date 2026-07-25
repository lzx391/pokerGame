/**
 * 社交列表主文案：优先展示服务端昵称；缺省时用固定前缀 + id，避免出现「整条只有数字 id」作主标题。
 */
export function dpSocialDisplayNickname(nicknameRaw, idRaw, unknownLabel) {
  var n = nicknameRaw != null ? String(nicknameRaw).trim() : ''
  if (n) return n
  if (idRaw != null && idRaw !== '') return '用户 · ' + String(idRaw)
  return unknownLabel != null ? String(unknownLabel) : '未知'
}
