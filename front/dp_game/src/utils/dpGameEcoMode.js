/**
 * 对局页「节能模式」：关闭华丽动画与模糊，减轻手机 GPU/主线程压力。
 * 存 localStorage 下次进入仍生效；未写入过时为默认关闭（标准档 / 全效果）。
 *
 * 【策略 B · 硬约束】仅用户手动切换可调用 writeEcoMode：
 * - 禁止 navigator.userAgent / 设备检测自动写入
 * - 禁止 matchMedia('(prefers-reduced-motion: reduce)') 自动写入 storage
 * - 系统 PRM 仅允许 CSS 联合选择器（见 dp-motion-tokens.css），不得替代「默认标准档」
 * 全站 body[data-dp-fluidity] 由 dpBodyFluidity.js 同步，勿在组件 created 里按 UA 写 eco。
 */

var STORAGE_KEY = 'dp_game_eco_mode'

export function readEcoMode() {
  try {
    var v = localStorage.getItem(STORAGE_KEY)
    if (v === null || v === '') return false
    return v === '1'
  } catch (e) {
    return false
  }
}

/** 仅顶栏/设置等用户显式操作调用；禁止 UA/PRM 自动调用。 */
export function writeEcoMode(on) {
  try {
    localStorage.setItem(STORAGE_KEY, on ? '1' : '0')
  } catch (e) { /* ignore */ }
}
