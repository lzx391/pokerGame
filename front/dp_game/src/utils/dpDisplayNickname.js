/**
 * 对局界面展示用昵称（与服务端真实 nickname 区分，避免改后端常量）。
 * DEMO 机器人在后端仍为 BOT_Fish，仅前端显示为 BOT_Lag。
 */
export function dpDisplayNickname (nickname) {
  if (nickname === 'BOT_Fish') return 'BOT_Lag'
  return nickname
}

/** 与后端 DpNpcEngine.isBotNickname 一致，用于下拉列表过滤等 */
export function isDpBotNickname (nickname) {
  if (!nickname) return false
  return (
    nickname === 'BOT_Fish' ||
    nickname === 'BOT_Maniac' ||
    nickname === 'BOT_Shark' ||
    nickname === 'BOT_Tag' ||
    nickname === 'BOT_LLM'
  )
}
