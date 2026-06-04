/**
 * retro8bit：河牌→摊牌 TV 弹窗触发条件（纯 overlay，不门闸亮牌）。
 */

var BETTING_STAGES = Object.freeze(['preflop', 'flop', 'turn', 'river'])
var REVEAL_STAGES = Object.freeze(['showdown', 'settled'])

export function isRetroBettingStage(stage) {
  return BETTING_STAGES.indexOf(stage) !== -1
}

export function isRetroRevealStage(stage) {
  return REVEAL_STAGES.indexOf(stage) !== -1
}

/**
 * 是否应在 retro8bit 下插入 ALL-IN SHOWDOWN 电视弹窗。
 * @param {string|undefined} oldStage 上一次 stage（首帧 hydrate 常为 undefined）
 * @param {string} newStage
 * @param {boolean} navReady 已完成首帧 stage 同步，避免进房即 settled 误触
 */
export function shouldRetroShowdownTvSequence(oldStage, newStage, navReady) {
  if (!navReady) return false
  if (!oldStage || oldStage === newStage) return false
  if (!isRetroBettingStage(oldStage)) return false
  return isRetroRevealStage(newStage)
}
