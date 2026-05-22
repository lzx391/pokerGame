/**
 * 新手一分钟引导：牌桌假数据（形状与对局页 DpPlayer / 后端牌面一致）
 */

import { holeDealOrderFromDealer } from './dpGameRoundTableLayout'

export const GUIDE_HERO_NICKNAME = '教程猫咪'

function makePlayer (opts) {
  return {
    nickname: opts.nickname,
    chips: opts.chips != null ? opts.chips : 400,
    bet: opts.bet != null ? opts.bet : 0,
    holeCards: opts.holeCards ? opts.holeCards.slice() : [],
    fold: !!opts.fold,
    dealer: !!opts.dealer,
    blind: opts.blind || 0,
    ready: true,
    leftThisHand: false,
    allIn: false,
    acted: true,
    handRankName: opts.handRankName || '',
    handRankDetail: opts.handRankDetail || '',
    bestHandCards: opts.bestHandCards ? opts.bestHandCards.slice() : [],
    winStreak: 0,
    mood: 0,
    nextBotActionTime: 0,
    npcHandPlanType: null,
    npcHandPlanMaxBarrels: 0,
    npcHandPlanAggression: 0,
    npcHandPlanTargetVillain: null
  }
}

export function createGuideMockPlayers () {
  return [
    makePlayer({
      nickname: GUIDE_HERO_NICKNAME,
      chips: 480,
      bet: 0,
      holeCards: ['hearts_K', 'spades_K'],
      handRankName: '一对',
      handRankDetail: '对K'
    }),
    makePlayer({
      nickname: '狸花猫',
      chips: 310,
      bet: 20,
      blind: 2,
      holeCards: ['clubs_9', 'diamonds_9']
    }),
    makePlayer({
      nickname: '橘猫',
      chips: 420,
      bet: 20,
      dealer: true,
      holeCards: ['hearts_A', 'clubs_Q']
    }),
    makePlayer({
      nickname: '三花猫',
      chips: 265,
      bet: 0,
      blind: 1,
      holeCards: ['diamonds_J', 'spades_10']
    })
  ]
}

export function buildGuidePlayersDisplayOrder (players, heroNickname) {
  var list = players || []
  if (!list.length) return []
  var start = 0
  if (heroNickname) {
    var idx = -1
    for (var i = 0; i < list.length; i++) {
      if (list[i].nickname === heroNickname) {
        idx = i
        break
      }
    }
    if (idx >= 0) start = idx
  }
  var out = []
  for (var j = 0; j < list.length; j++) {
    var seatIndex = (start + j) % list.length
    out.push({ player: list[seatIndex], seatIndex: seatIndex })
  }
  return out
}

export function guideDealerDisplayIndex (playersDisplayOrder) {
  var order = playersDisplayOrder || []
  for (var i = 0; i < order.length; i++) {
    if (order[i].player && order[i].player.dealer) return i
  }
  return -1
}

export function createGuideMockState () {
  var players = createGuideMockPlayers()
  return {
    roomId: '教程演示',
    stage: 'flop',
    playing: true,
    pot: 120,
    currentBetToCall: 20,
    callAmount: 20,
    actIndex: 0,
    isMyTurn: true,
    myChips: 480,
    myBet: 0,
    myCarryInChips: 500,
    smallBlind: 5,
    bigBlind: 10,
    minRaise: 20,
    minTotalToRaise: 40,
    lastRaiseIncrement: 10,
    raiseAmount: 20,
    communityCards: ['spades_A', 'hearts_10', 'diamonds_7'],
    communityCardsFlipState: [true, true, true],
    communityCardsFlipComplete: true,
    currentHandSeed: 900001,
    heroNickname: GUIDE_HERO_NICKNAME,
    players: players,
    chipLeaderNicknames: [],
    isOwner: true,
    ownerRevealAll: false,
    spectators: ['观战喵'],
    waitNextHand: ['候补喵'],
    nextHandReady: false,
    roomChatMessages: [
      { nickname: '橘猫', text: '欢迎教程～', ts: Date.now() - 60000 }
    ],
    seatChatTextByNick: {},
    timeLeft: 24,
    readyTimeLeft: 30,
    myReady: false,
    user: { nickname: GUIDE_HERO_NICKNAME }
  }
}

export { holeDealOrderFromDealer }
