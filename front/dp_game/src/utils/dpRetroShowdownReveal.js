/**
 * retro8bit：河牌→摊牌 TV 弹窗 + 摊牌亮牌门闸（TV 播放期间保持下注圈紧凑 UI）。
 */

var BETTING_STAGES = Object.freeze(['preflop', 'flop', 'turn', 'river'])
var REVEAL_STAGES = Object.freeze(['showdown', 'settled'])
var DEFAULT_BETTING_STAGE_BEFORE_SHOWDOWN = 'river'

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

/**
 * TV 播放期间：玩家卡片仍按上一下注街渲染（紧凑/背面），避免 settled 快照一到就展开。
 * @param {string} actualStage 服务端 stage
 * @param {boolean} tvPending 摊牌 TV 是否进行中
 * @param {string|null|undefined} bettingStageBeforeShowdown 进入摊牌前的下注街（如 river）
 */
export function resolveCardDisplayStage(actualStage, tvPending, bettingStageBeforeShowdown) {
  if (!tvPending || !isRetroRevealStage(actualStage)) return actualStage
  if (bettingStageBeforeShowdown && isRetroBettingStage(bettingStageBeforeShowdown)) {
    return bettingStageBeforeShowdown
  }
  return DEFAULT_BETTING_STAGE_BEFORE_SHOWDOWN
}

/**
 * TV 播放期间暂不展示摊牌牌力领先者高亮，避免与紧凑 UI 冲突。
 */
export function resolveShowdownHandLeaders(actualStage, tvPending, leaders) {
  if (tvPending && isRetroRevealStage(actualStage)) return []
  return leaders || []
}

/**
 * 是否应展示他人摊牌亮牌（与 cardDisplayStage 解耦：TV 结束即亮，不依赖 display stage 回退）。
 */
export function shouldRevealHoleCardsAtShowdown(actualStage, tvPending) {
  if (tvPending) return false
  return isRetroRevealStage(actualStage)
}
