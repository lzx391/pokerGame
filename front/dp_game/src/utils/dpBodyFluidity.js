/**
 * 全站双档：body[data-dp-fluidity] 与 localStorage dp_game_eco_mode 同步。
 * 仅反映用户手动选择，禁止 UA / prefers-reduced-motion 写入 storage。
 */
import { readEcoMode } from './dpGameEcoMode'

export function syncDpBodyFluidity(store) {
  try {
    var on =
      store && store.state && store.state.dpGame
        ? !!store.state.dpGame.ecoMode
        : readEcoMode()
    document.body.setAttribute('data-dp-fluidity', on ? 'eco' : 'standard')
  } catch (e) {
    /* ignore */
  }
}
