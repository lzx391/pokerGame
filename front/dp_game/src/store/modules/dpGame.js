import Vue from 'vue'
import { HAND_RANK_REFERENCE } from '../../constants/dpGameHandRankReference'
import { pickShowdownLeaderNicknames } from '../../utils/dpGameHandRank'
import { dpDisplayNickname, isDpBotNickname } from '../../utils/dpDisplayNickname'
import { readGameTheme, writeGameTheme } from '../../utils/dpGameTheme'
import { readEcoMode, writeEcoMode } from '../../utils/dpGameEcoMode'
import {
  readCustomTheme,
  writeCustomTheme,
  normalizeAccentHex,
  mergeCustomThemeVars,
  normalizeOverrides
} from '../../utils/dpGameCustomTheme'
import { GAME_UI_THEMES, GAME_UI_THEME_IDS } from '../../constants/dpGameThemes'
import { dpGameStageDisplay } from '../../constants/dpCatThemeCopy'

function initialState() {
  var ct = readCustomTheme()
  return {
    gameUiTheme: readGameTheme(),
    ecoMode: readEcoMode(),
    customThemeBase: ct.baseId,
    customAccent: ct.accent,
    customThemeOverrides: ct.overrides || {},
    gameThemeOptions: GAME_UI_THEMES,
    roomId: '',
    user: null,
    currentHandSeed: 0,
    owner: '',
    players: [],
    playing: false,
    stage: 'preflop',
    communityCards: [],
    pot: 0,
    pots: [],
    currentBetToCall: 0,
    lastRaiseIncrement: 10,
    actIndex: -1,
    spectators: [],
    /** 已报名下一局上桌的昵称列表（与后端 waitNextHand 一致） */
    waitNextHand: [],
    raiseAmount: 0,
    smallBlindChips: 5,
    bigBlindChips: 10,
    startingStackBb: 50,
    selectedWinners: [],
    potWinners: {},
    nextHandReady: false,
    loading: false,
    communityCardsFlipState: [],
    communityCardsFlipComplete: false,
    seatChatTextByNick: {},
    chatInputDraft: '',
    showPlayGuideModal: false,
    playGuideTab: 'flow',
    showSpectatorModal: false,
    showWaitNextHandModal: false,
    showHandHistoryModal: false,
    showMusicBoxModal: false,
    musicTracks: [],
    musicTracksLoading: false,
    musicTracksError: '',
    roomMusicState: null,
    showOwnerHubSheet: false,
    ownerToolType: 'transfer',
    ownerActionTarget: '',
    demoBotAdding: false,
    demoBotAddedTip: '',
    maniacBotAdding: false,
    maniacBotAddedTip: '',
    tagBotAdding: false,
    tagBotAddedTip: '',
    lagBotAdding: false,
    lagBotAddedTip: '',
    nitBotAdding: false,
    nitBotAddedTip: '',
    callBotAdding: false,
    callBotAddedTip: '',
    llmBotAdding: false,
    llmBotAddedTip: '',
    ownerRevealAll: false,
    showMobileHandSheet: false,
    showMobileActionSheet: false,
    heroHoleDealIntroDone: false,
    /** 后端 autoSettle 后写入的「场上积分并列最高」昵称，未结算过为空 */
    chipLeaderNicknames: []
  }
}

export default {
  namespaced: true,
  state: initialState(),
  getters: {
    handRankReference: function () {
      return HAND_RANK_REFERENCE
    },
    stageCN: function (state) {
      return dpGameStageDisplay(state.stage)
    },
    isOwner: function (state) {
      return state.user && state.owner === state.user.nickname
    },
    isMyTurn: function (state) {
      if (!state.user || state.actIndex < 0 || state.actIndex >= state.players.length) return false
      return state.players[state.actIndex].nickname === state.user.nickname
    },
    myPlayer: function (state) {
      if (!state.user) return null
      return state.players.find(function (p) {
        return p.nickname === state.user.nickname
      }) || null
    },
    showSpectatorPrepareBlock: function (state, getters) {
      if (!getters.myPlayer) return true
      return !!getters.myPlayer.leftThisHand
    },
    myReady: function (state, getters) {
      return getters.myPlayer ? getters.myPlayer.ready : false
    },
    myChips: function (state, getters) {
      return getters.myPlayer ? getters.myPlayer.chips : 0
    },
    myBet: function (state, getters) {
      return getters.myPlayer ? getters.myPlayer.bet : 0
    },
    callAmount: function (state, getters) {
      return Math.max(0, state.currentBetToCall - getters.myBet)
    },
    smallBlind: function (state) {
      var v = Number(state.smallBlindChips)
      return isFinite(v) && v >= 1 ? Math.floor(v) : 5
    },
    bigBlind: function (state) {
      var v = Number(state.bigBlindChips)
      return isFinite(v) && v >= 1 ? Math.floor(v) : 10
    },
    lastRaiseIncrementEffective: function (state, getters) {
      var v = Number(state.lastRaiseIncrement)
      if (!isFinite(v) || v < 1) return getters.bigBlind
      return Math.floor(v)
    },
    minTotalToRaise: function (state, getters) {
      return state.currentBetToCall + getters.lastRaiseIncrementEffective
    },
    minRaise: function (state, getters) {
      var call = getters.callAmount
      if (!isFinite(call) || call < 0) call = 0
      var inc = getters.lastRaiseIncrementEffective
      var need = call + inc
      var cap = Math.min(need, getters.myChips)
      return Math.max(1, cap)
    },
    allPotsHaveWinners: function (state) {
      if (state.pots.length === 0) return false
      for (var i = 0; i < state.pots.length; i++) {
        if (!state.potWinners[i] || state.potWinners[i].length === 0) return false
      }
      return true
    },
    inSettledStage: function (state, getters) {
      return state.stage === 'settled' && !!getters.myPlayer && !getters.myPlayer.leftThisHand
    },
    ownerActionPlayers: function (state) {
      if (state.ownerToolType === 'transfer') {
        var owner = state.owner
        var out = []
        var seen = {}
        var players = state.players || []
        for (var i = 0; i < players.length; i++) {
          var p = players[i]
          if (!p || p.leftThisHand || p.nickname === owner) continue
          if (isDpBotNickname(p.nickname)) continue
          out.push(p)
          seen[p.nickname] = true
        }
        var specs = state.spectators || []
        for (var j = 0; j < specs.length; j++) {
          var nick = specs[j]
          if (!nick || nick === owner || seen[nick]) continue
          if (isDpBotNickname(nick)) continue
          out.push({ nickname: nick })
          seen[nick] = true
        }
        return out
      }
      return state.players.filter(function (p) {
        return !p.leftThisHand && p.nickname !== state.owner
      })
    },
    playersDisplayOrder: function (state) {
      var list = state.players
      if (!list || list.length === 0) return []
      var myNick = state.user && state.user.nickname
      var start = 0
      if (myNick) {
        var idx = list.findIndex(function (p) {
          return p.nickname === myNick
        })
        if (idx >= 0) start = idx
      }
      var out = []
      for (var j = 0; j < list.length; j++) {
        var seatIndex = (start + j) % list.length
        out.push({ player: list[seatIndex], seatIndex: seatIndex })
      }
      return out
    },
    viewerSeatedAtTable: function (state) {
      var nick = state.user && state.user.nickname
      if (!nick || !state.players || !state.players.length) return false
      return state.players.some(function (p) {
        return p.nickname === nick
      })
    },
    /** 与后端 {@code canActiveMemberInviteFriends} 一致：未离座上桌真人或观众（非机器人）可发进房邀请 */
    canInviteFriend: function (state, getters) {
      var u = state.user
      var nick = u && u.nickname
      if (!nick) return false
      if (isDpBotNickname(nick)) return false
      var mp = getters.myPlayer
      if (mp && !mp.leftThisHand) return true
      var specs = state.spectators || []
      return specs.indexOf(nick) !== -1
    },
    holeDealPlayerCountForAnim: function (state) {
      if (!state.players || !state.players.length) return 1
      return state.players.length
    },
    heroDockRow: function (state, getters) {
      if (!getters.viewerSeatedAtTable) return null
      var order = getters.playersDisplayOrder
      if (!order || !order.length) return null
      return order[0]
    },
    dealerDisplayIndex: function (state, getters) {
      var order = getters.playersDisplayOrder
      if (!order || !order.length) return -1
      for (var i = 0; i < order.length; i++) {
        if (order[i].player && order[i].player.dealer) return i
      }
      return -1
    },
    showdownHandLeaderNicknames: function (state) {
      if (state.stage !== 'showdown' && state.stage !== 'settled') return []
      if (!state.players || !state.players.length) return []
      if (!state.communityCards || state.communityCards.length < 3) return []
      var boardReady =
        state.communityCardsFlipComplete
        || state.communityCards.length >= 5
        || state.stage === 'settled'
      if (!boardReady) return []
      return pickShowdownLeaderNicknames(state.players, state.communityCards)
    },
    spectatorSeatChatEntries: function (state) {
      var map = state.seatChatTextByNick
      if (!map || typeof map !== 'object') return []
      var seated = {}
      var players = state.players || []
      for (var i = 0; i < players.length; i++) {
        var n = players[i] && players[i].nickname
        if (n) seated[n] = true
      }
      var out = []
      for (var k in map) {
        if (!Object.prototype.hasOwnProperty.call(map, k)) continue
        if (seated[k]) continue
        out.push({ nickname: k, text: map[k] })
      }
      return out
    },
    tableActionActorDisplayName: function (state) {
      var i = state.actIndex
      var list = state.players
      if (i < 0 || !list || i >= list.length) return '—'
      var p = list[i]
      if (!p || !p.nickname) return '—'
      return dpDisplayNickname(p.nickname)
    },
    mobileHeroDockActive: function (state, getters) {
      return !!(getters.heroDockRow || getters.isMyTurn || getters.inSettledStage || getters.isOwner)
    },
    showHeroViewHandButton: function (state, getters) {
      if (!getters.heroDockRow) return false
      if (state.stage !== 'preflop') return true
      return state.heroHoleDealIntroDone
    },
    showHeroSeatOnTable: function (state, getters) {
      return (
        getters.viewerSeatedAtTable
        && state.stage === 'preflop'
        && !state.heroHoleDealIntroDone
      )
    },
    showBottomHeroDock: function (state, getters) {
      return getters.heroDockRow && state.stage !== 'preflop'
    },
    /** 供 data-dp-game-theme：自定义时沿用预设底的 CSS 变量块 */
    effectiveThemeForCss: function (state) {
      if (state.gameUiTheme === 'custom') {
        var b = state.customThemeBase
        if (b && GAME_UI_THEME_IDS.indexOf(b) !== -1 && b !== 'custom') {
          return b
        }
        return 'default'
      }
      return state.gameUiTheme || 'default'
    },
    /** 自定义时覆盖到 .dp-game-root 内联样式，与 body 上由 dpBodyGameTheme 同步的一致 */
    customThemeInlineStyle: function (state) {
      if (state.gameUiTheme !== 'custom') return {}
      return mergeCustomThemeVars(null, state.customThemeOverrides)
    }
  },
  mutations: {
    SET_SESSION: function (state, payload) {
      if (payload.roomId != null) state.roomId = payload.roomId
      if (payload.user !== undefined) state.user = payload.user
    },
    SET_GAME_UI_THEME: function (state, id) {
      state.gameUiTheme = id
      writeGameTheme(id)
    },
    SET_CUSTOM_THEME: function (state, payload) {
      payload = payload || {}
      if (payload.baseId != null) {
        var bid = String(payload.baseId)
        if (GAME_UI_THEME_IDS.indexOf(bid) !== -1 && bid !== 'custom') {
          state.customThemeBase = bid
        }
      }
      if (payload.accent != null) {
        var ax = normalizeAccentHex(payload.accent)
        if (ax) state.customAccent = ax
      }
      if (payload.overrides !== undefined) {
        state.customThemeOverrides = normalizeOverrides(payload.overrides)
      }
      writeCustomTheme({
        baseId: state.customThemeBase,
        accent: state.customAccent,
        overrides: state.customThemeOverrides
      })
    },
    SET_ECO_MODE: function (state, on) {
      state.ecoMode = !!on
      writeEcoMode(!!on)
    },
    APPLY_ROOM: function (state, room) {
      state.owner = room.owner
      state.players = room.players || []
      state.playing = room.playing
      if (room.smallBlindChips != null) state.smallBlindChips = room.smallBlindChips
      if (room.bigBlindChips != null) state.bigBlindChips = room.bigBlindChips
      if (room.startingStackBb != null) state.startingStackBb = room.startingStackBb
      state.currentHandSeed = room.currentHandSeed != null ? room.currentHandSeed : 0
      state.stage = room.currentStage
      state.communityCards = room.communityCards || []
      state.pot = room.pot
      state.pots = room.pots || []
      state.currentBetToCall = room.currentBetToCall
      state.lastRaiseIncrement =
        room.lastRaiseIncrement != null ? room.lastRaiseIncrement : 10
      state.actIndex = room.currentActorIndex
      state.spectators = room.spectators || []
      var list = room.waitNextHand || []
      state.waitNextHand = list.slice()
      var nick = state.user && state.user.nickname
      state.nextHandReady = !!(nick && list.indexOf(nick) !== -1)
      state.chipLeaderNicknames = room.chipLeaderNicknames || []
    },
    SET_LOADING: function (state, v) {
      state.loading = !!v
    },
    SET_NEXT_HAND_READY: function (state, v) {
      state.nextHandReady = !!v
    },
    SET_RAISE_AMOUNT: function (state, v) {
      state.raiseAmount = v
    },
    SET_CHAT_DRAFT: function (state, v) {
      state.chatInputDraft = v != null ? String(v) : ''
    },
    SET_SEAT_CHAT: function (state, payload) {
      Vue.set(state.seatChatTextByNick, payload.nick, payload.text)
    },
    DELETE_SEAT_CHAT: function (state, nick) {
      if (state.seatChatTextByNick[nick] !== undefined) {
        Vue.delete(state.seatChatTextByNick, nick)
      }
    },
    SET_COMMUNITY_FLIP_STATE: function (state, arr) {
      state.communityCardsFlipState = arr || []
    },
    SET_FLIP_AT: function (state, payload) {
      Vue.set(state.communityCardsFlipState, payload.index, payload.value)
    },
    SET_COMMUNITY_FLIP_COMPLETE: function (state, v) {
      state.communityCardsFlipComplete = !!v
    },
    SET_SELECTED_WINNERS: function (state, arr) {
      state.selectedWinners = arr || []
    },
    TOGGLE_SELECTED_WINNER: function (state, nickname) {
      var idx = state.selectedWinners.indexOf(nickname)
      if (idx > -1) {
        state.selectedWinners.splice(idx, 1)
      } else {
        state.selectedWinners.push(nickname)
      }
    },
    CLEAR_JUDGE_SELECTION: function (state) {
      state.potWinners = {}
      state.selectedWinners = []
    },
    SET_POT_WINNERS: function (state, obj) {
      state.potWinners = obj && typeof obj === 'object' ? obj : {}
    },
    SET_POT_WINNERS_AT: function (state, payload) {
      Vue.set(state.potWinners, payload.potIndex, payload.winners)
    },
    CLEAR_OWNER_REVEAL: function (state) {
      state.ownerRevealAll = false
    },
    SET_OWNER_REVEAL_ALL: function (state, v) {
      state.ownerRevealAll = !!v
    },
    SET_MODAL: function (state, payload) {
      if (payload.showPlayGuideModal !== undefined) state.showPlayGuideModal = payload.showPlayGuideModal
      if (payload.playGuideTab !== undefined) state.playGuideTab = payload.playGuideTab
      if (payload.showSpectatorModal !== undefined) state.showSpectatorModal = payload.showSpectatorModal
      if (payload.showWaitNextHandModal !== undefined) state.showWaitNextHandModal = payload.showWaitNextHandModal
      if (payload.showHandHistoryModal !== undefined) state.showHandHistoryModal = payload.showHandHistoryModal
      if (payload.showMusicBoxModal !== undefined) state.showMusicBoxModal = payload.showMusicBoxModal
    },
    SET_MUSIC_TRACKS: function (state, payload) {
      if (payload.tracks !== undefined) state.musicTracks = payload.tracks
      if (payload.loading !== undefined) state.musicTracksLoading = payload.loading
      if (payload.error !== undefined) state.musicTracksError = payload.error
    },
    SET_ROOM_MUSIC_STATE: function (state, data) {
      state.roomMusicState = data
    },
    CLEAR_ROOM_MUSIC_UI: function (state) {
      state.roomMusicState = null
    },
    OPEN_OWNER_HUB: function (state) {
      state.ownerToolType = 'transfer'
      state.ownerActionTarget = ''
      state.showOwnerHubSheet = true
      state.demoBotAddedTip = ''
      state.maniacBotAddedTip = ''
      state.tagBotAddedTip = ''
      state.lagBotAddedTip = ''
      state.nitBotAddedTip = ''
      state.callBotAddedTip = ''
      state.llmBotAddedTip = ''
    },
    CLOSE_OWNER_HUB: function (state) {
      state.showOwnerHubSheet = false
      state.ownerActionTarget = ''
    },
    SET_OWNER_TOOL: function (state, payload) {
      if (payload.ownerToolType !== undefined) {
        state.ownerToolType = payload.ownerToolType
        if (payload.ownerActionTarget === undefined) {
          state.ownerActionTarget = ''
        }
      }
      if (payload.ownerActionTarget !== undefined) state.ownerActionTarget = payload.ownerActionTarget
    },
    SET_BOT_STATE: function (state, payload) {
      if (payload.demoBotAdding !== undefined) state.demoBotAdding = payload.demoBotAdding
      if (payload.demoBotAddedTip !== undefined) state.demoBotAddedTip = payload.demoBotAddedTip
      if (payload.maniacBotAdding !== undefined) state.maniacBotAdding = payload.maniacBotAdding
      if (payload.maniacBotAddedTip !== undefined) state.maniacBotAddedTip = payload.maniacBotAddedTip
      if (payload.tagBotAdding !== undefined) state.tagBotAdding = payload.tagBotAdding
      if (payload.tagBotAddedTip !== undefined) state.tagBotAddedTip = payload.tagBotAddedTip
      if (payload.lagBotAdding !== undefined) state.lagBotAdding = payload.lagBotAdding
      if (payload.lagBotAddedTip !== undefined) state.lagBotAddedTip = payload.lagBotAddedTip
      if (payload.nitBotAdding !== undefined) state.nitBotAdding = payload.nitBotAdding
      if (payload.nitBotAddedTip !== undefined) state.nitBotAddedTip = payload.nitBotAddedTip
      if (payload.callBotAdding !== undefined) state.callBotAdding = payload.callBotAdding
      if (payload.callBotAddedTip !== undefined) state.callBotAddedTip = payload.callBotAddedTip
      if (payload.llmBotAdding !== undefined) state.llmBotAdding = payload.llmBotAdding
      if (payload.llmBotAddedTip !== undefined) state.llmBotAddedTip = payload.llmBotAddedTip
    },
    SET_MOBILE_SHEETS: function (state, payload) {
      if (payload.showMobileHandSheet !== undefined) state.showMobileHandSheet = payload.showMobileHandSheet
      if (payload.showMobileActionSheet !== undefined) state.showMobileActionSheet = payload.showMobileActionSheet
    },
    SET_HERO_HOLE_DEAL: function (state, v) {
      state.heroHoleDealIntroDone = !!v
    },
    RESET_ON_ROOM_CLOSED: function (state) {
      state.roomMusicState = null
    }
  }
}
