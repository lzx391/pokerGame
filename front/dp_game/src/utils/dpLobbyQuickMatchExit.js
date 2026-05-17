/**
 * 大厅：创建/加入/跟随 进房前静默退出快匹（与 CreateRoom 调用的取消接口一致，并可选断开 /ws/dp-quick-match）。
 * 幂等；取消失败仅日志，不阻塞后续导航。
 */
import { postQuickMatchCancel2 } from '@/utils/dpQuickMatchExit'

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ nickname?: string, userId?: string|number, token?: string }} user
 * @param {{
 *   resetQuickMatchUiFlags?: () => void,
 *   disconnectQuickMatchWs?: () => void
 * }} [hooks]
 */
export async function exitLobbyQuickMatchSilently(http, user, hooks) {
  if (hooks && typeof hooks.resetQuickMatchUiFlags === 'function') {
    hooks.resetQuickMatchUiFlags()
  }
  if (hooks && typeof hooks.disconnectQuickMatchWs === 'function') {
    hooks.disconnectQuickMatchWs()
  }
  await postQuickMatchCancel2(http, user)
}
