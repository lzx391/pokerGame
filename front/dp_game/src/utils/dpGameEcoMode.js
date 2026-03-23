/** 对局页「节能模式」：关闭华丽动画与模糊，减轻手机 GPU/主线程压力；存 localStorage 下次进入仍生效 */

var STORAGE_KEY = 'dp_game_eco_mode'

export function readEcoMode() {
  try {
    var v = localStorage.getItem(STORAGE_KEY)
    if (v === null || v === '') return true
    return v === '1'
  } catch (e) {
    return true
  }
}

export function writeEcoMode(on) {
  try {
    localStorage.setItem(STORAGE_KEY, on ? '1' : '0')
  } catch (e) { /* ignore */ }
}
