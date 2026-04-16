/**
 * 对局界面展示用昵称（与服务端真实 nickname 区分，避免改后端常量）。
 * DEMO 机器人在后端仍为 BOT_Fish，仅前端显示为 BOT_Lag。
 */
export function dpDisplayNickname (nickname) {
  if (nickname === 'BOT_Fish') return 'BOT_Lag'
  return nickname
}
