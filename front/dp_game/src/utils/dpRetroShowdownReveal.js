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

/** TV 播放期间：摊牌/结算 presentation 冻结（数据可进内存，UI 仍按决赛圈展示） */
export function isSettlePresentationFrozen(tvPending, actualStage) {
  return !!tvPending && isRetroRevealStage(actualStage)
}

export function liveTableChipLeaderNicksFromPlayers(players) {
  var max = -1
  var nicks = []
  var list = players || []
  for (var i = 0; i < list.length; i++) {
    var p = list[i]
    if (!p || p.leftThisHand) continue
    var c = Number(p.chips)
    if (!isFinite(c) || c < 0) c = 0
    if (c > max) {
      max = c
      nicks = [p.nickname]
    } else if (c === max && p.nickname) {
      nicks.push(p.nickname)
    }
  }
  return nicks
}

/** 每次下注街快照更新；进入摊牌/结算 TV 期间用此冻结积分展示 */
export function captureBettingEconomySnapshot(room) {
  if (!room) return null
  var players = room.players || []
  var byNick = {}
  for (var i = 0; i < players.length; i++) {
    var p = players[i]
    if (!p || !p.nickname) continue
    byNick[p.nickname] = {
      chips: p.chips != null ? p.chips : 0,
      bet: p.bet != null ? p.bet : 0
    }
  }
  return {
    byNick: byNick,
    pot: room.pot != null ? room.pot : 0,
    currentBetToCall: room.currentBetToCall != null ? room.currentBetToCall : 0,
    chipLeaderNicknames: liveTableChipLeaderNicksFromPlayers(players)
  }
}

export function resolvePlayerEconomyDisplay(player, snapshot, frozen) {
  if (!player) return { chips: 0, bet: 0 }
  if (!frozen || !snapshot || !snapshot.byNick) {
    return {
      chips: player.chips != null ? player.chips : 0,
      bet: player.bet != null ? player.bet : 0
    }
  }
  var entry = snapshot.byNick[player.nickname]
  if (!entry) {
    return {
      chips: player.chips != null ? player.chips : 0,
      bet: player.bet != null ? player.bet : 0
    }
  }
  return { chips: entry.chips, bet: entry.bet }
}

export function resolveFrozenTablePot(livePot, snapshot, frozen) {
  if (!frozen || !snapshot) return livePot
  return snapshot.pot != null ? snapshot.pot : livePot
}

export function resolveFrozenCurrentBetToCall(liveValue, snapshot, frozen) {
  if (!frozen || !snapshot) return liveValue
  return snapshot.currentBetToCall != null ? snapshot.currentBetToCall : liveValue
}

export function resolveFrozenChipLeaderNicknames(liveLeaders, snapshot, frozen) {
  if (!frozen || !snapshot) return liveLeaders || []
  return snapshot.chipLeaderNicknames || []
}
