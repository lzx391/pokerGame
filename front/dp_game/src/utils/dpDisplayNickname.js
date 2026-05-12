/**
 * UI 展示昵称：服务端仍为完整 nickname（下注 / WS 逻辑必须用全称）。
 * - BOT_*_&lt;长 uuid&gt; → 前缀 + 去横线后前 4 字符；BOT_*_&lt;序号&gt; 较短时原样
 */
const BOT_TRUNC_HEADS = [
  'BOT_MANIAC_',
  'BOT_CALL_',
  'BOT_LLM_',
  'BOT_LAG_',
  'BOT_TAG_',
  'BOT_NIT_',
  'BOT_FISH_'
]

function shortenBotUuidNickname (nickname) {
  if (!nickname) return nickname
  for (let i = 0; i < BOT_TRUNC_HEADS.length; i++) {
    const head = BOT_TRUNC_HEADS[i]
    if (!nickname.startsWith(head) || nickname.length <= head.length) continue
    const uuidPart = nickname.slice(head.length).replace(/-/g, '')
    if (uuidPart.length >= 4) return head + uuidPart.slice(0, 4)
    return nickname
  }
  return nickname
}

/**
 * @param {string|{nickname?: string, displayNickname?: string}} nicknameOrPlayer
 */
export function dpDisplayNickname (nicknameOrPlayer) {
  if (nicknameOrPlayer && typeof nicknameOrPlayer === 'object') {
    const dn = nicknameOrPlayer.displayNickname
    if (dn) return dn
    return shortenBotUuidNickname(nicknameOrPlayer.nickname || '')
  }
  const nickname =
    nicknameOrPlayer == null ? '' : String(nicknameOrPlayer)
  return shortenBotUuidNickname(nickname)
}

/** 与后端 DpNpcEngine.isBotNickname 对齐（含多实例 BOT_*_uuid） */
export function isDpBotNickname (nickname) {
  if (!nickname || typeof nickname !== 'string') return false
  if (!nickname.startsWith('BOT_')) return false
  if (nickname === 'BOT_LLM' || nickname.startsWith('BOT_LLM_')) return true
  const legacy = ['BOT_Fish', 'BOT_Maniac', 'BOT_Shark', 'BOT_Tag']
  if (legacy.indexOf(nickname) !== -1) return true
  const prefixBodies = ['MANIAC', 'CALL', 'LAG', 'TAG', 'NIT', 'FISH']
  for (let i = 0; i < prefixBodies.length; i++) {
    const px = 'BOT_' + prefixBodies[i]
    if (nickname === px || nickname.startsWith(px + '_')) return true
  }
  return false
}
