<template>
  <div
      ref="gameRoot"
      class="dp-game-root"
      :data-dp-layout-tier="layoutTier"
      :class="{
        'dp-game-root--pseudo-fs': pseudoFullscreen,
        'dp-game-root--layout-fs': layoutFullscreen,
        'dp-game-root--mobile-hero-dock': mobileHeroDockActive
      }"
      :data-dp-game-theme="effectiveThemeForCss"
      :style="customThemeInlineStyle"
      :data-dp-eco-mode="ecoMode ? 'true' : 'false'"
      :data-dp-stage="stage"
      :data-dp-orientation="layoutOrientation"
  >
    <!-- 顶栏 | 主区(牌桌) | 底栏 —— 三块同级 flex，无额外嵌套 -->
    <div class="dp-game-layout">
    <header class="dp-game-layout__header">
    <game-top-bar
        :room-id="roomId"
        :stage-label="stageCN"
        :pot="pot"
        :current-bet-to-call="currentBetToCall"
        :spectator-count="spectators.length"
        :wait-next-hand-count="waitNextHand.length"
        :is-fullscreen="layoutFullscreen"
        :is-owner="isOwner"
        :can-invite-friend="canInviteFriend"
        :show-spectator-prepare="showSpectatorPrepareBlock"
        :next-hand-ready="nextHandReady"
        :game-ui-theme="gameUiTheme"
        :custom-theme-base="customThemeBase"
        :custom-theme-overrides="customThemeOverrides"
        @update:gameUiTheme="$store.commit('dpGame/SET_GAME_UI_THEME', $event)"
        @update:customThemeBase="$store.commit('dpGame/SET_CUSTOM_THEME', { baseId: $event })"
        @update:customThemeOverrides="$store.commit('dpGame/SET_CUSTOM_THEME', { overrides: $event })"
        :eco-mode="ecoMode"
        @update:ecoMode="$store.commit('dpGame/SET_ECO_MODE', $event)"
        :theme-options="gameThemeOptions"
        @show-play-guide="$store.commit('dpGame/SET_MODAL', { showPlayGuideModal: true, playGuideTab: 'flow' })"
        @show-spectators="$store.commit('dpGame/SET_MODAL', { showSpectatorModal: true })"
        @show-wait-next-hand="$store.commit('dpGame/SET_MODAL', { showWaitNextHandModal: true })"
        @toggle-fullscreen="toggleDpFullscreen"
        @open-hand-history="openHandHistory"
        @open-music-box="$store.commit('dpGame/SET_MODAL', { showMusicBoxModal: true })"
        @open-owner-hub="openOwnerHubSheet"
        @open-invite-friend="openInviteFriendSheet"
        @exit="exitGame"
        @ready-next-hand="readyNextHand"
        :show-hero-economy="topBarShowHeroEconomy"
        :hero-my-chips="myChips"
        :hero-economy-secondary-label="topBarHeroEconomySecondaryLabel"
        :hero-economy-secondary-value="topBarHeroEconomySecondaryValue"
        :hero-carry-in-chips="myCarryInChips"
    />

    </header>

    <main
        ref="gameMain"
        class="dp-game-layout__main dp-game-layout__main--fit-table"
        :class="{ 'dp-game-layout__main--settlement-scroll': stage === 'showdown' || stage === 'settled' }"
    >
    <p
        v-if="layoutTier === 'phone' && layoutOrientation === 'portrait'"
        class="dp-game-layout__portrait-hint"
        role="status"
    >
      横屏可获得更大牌桌视野，建议旋转手机并全屏游玩。
    </p>
    <div class="dp-game-table-fit" :style="tableFitClipStyleObj">
      <div ref="tableFitInner" class="dp-game-table-fit__inner">
          <game-round-table
              :chip-leader-nicknames="chipLeaderNicknames"
              :players-display-order="playersDisplayOrder"
              :show-table-action-timer="showTableActionTimer"
              :time-left="timeLeft"
              :timer-actor-name="tableActionActorDisplayName"
              :timer-urgency="tableActionTimerUrgency"
              :timer-progress-pct="actionTimerProgressPct"
              :eco-mode="ecoMode"
              :community-cards="communityCards"
              :community-cards-flip-state="communityCardsFlipState"
              :viewer-seated-at-table="viewerSeatedAtTable"
              :act-index="actIndex"
              :stage="stage"
              :community-cards-flip-complete="communityCardsFlipComplete"
              :is-owner="isOwner"
              :owner-reveal-all="ownerRevealAll"
              :my-nickname="user ? user.nickname : ''"
              :current-hand-seed="currentHandSeed"
              :hole-deal-player-count-for-anim="holeDealPlayerCountForAnim"
              :showdown-hand-leader-nicknames="showdownHandLeaderNicknames"
              :dealer-display-index="dealerDisplayIndex"
              :get-player-box-style="getPlayerBoxStyle"
              :hole-deal-order-from-dealer="holeDealOrderFromDealer"
              :seat-chat-text-for="seatChatTextFor"
              @hole-deal-intro-complete="$store.commit('dpGame/SET_HERO_HOLE_DEAL', true)"
              @card-click="onPlayerCardClick"
          />
        </div>
    </div>

    </main>

    <footer class="dp-game-layout__footer">
      <game-hero-dock-footer />
    </footer>
    </div>

    <game-dp-floating-modals />

    <audio
        ref="roomBgm"
        class="dp-room-bgm"
        preload="none"
        aria-hidden="true"
    />

    <game-dp-game-sheets />

  </div>
</template>

<script>
import '../styles/dp-game-themes.css'
import '../styles/dp-game-shell.css'
import '../styles/dp-game-modals.css'
import '../styles/dp-game-eco-mode.css'
import GameTopBar from './GameTopBar.vue'
import { holeDealOrderFromDealer as holeDealOrderFromDealerUtil } from '../utils/dpGameRoundTableLayout'
import { dpDisplayNickname, isDpBotNickname } from '../utils/dpDisplayNickname'
import { musicFileSrc } from '../utils/dpGameMusicUrl'
import GameRoundTable from './GameRoundTable.vue'
import GameHeroDockFooter from './GameHeroDockFooter.vue'
import GameDpFloatingModals from './GameDpFloatingModals.vue'
import GameDpGameSheets from './GameDpGameSheets.vue'
import dpGameFullscreenMixin from '../mixins/dpGameFullscreenMixin'
import dpGameTableFitMixin from '../mixins/dpGameTableFitMixin'
import dpGameActionCountdownMixin from '../mixins/dpGameActionCountdownMixin'
import dpGameLayoutTierMixin from '../mixins/dpGameLayoutTierMixin'
import { dpGamePlayerBoxStyle } from '../utils/dpGamePlayerBoxStyle'
import { ensureDpUserIdInStorage } from '../utils/dpEnsureUserId'
import { dpResultSuccess, dpResultData, dpResultMessage } from '../utils/dpApiResult'
import { dpRoomApi } from '@/api/api.dpRoom'
import { mapState, mapGetters } from 'vuex'
import { encodeRoomApplyFingerprint } from '../utils/dpGameRoomFingerprint'
import { CAT_COPY, dpPotDisplayLabel } from '../constants/dpCatThemeCopy'

export default {
  mixins: [dpGameFullscreenMixin, dpGameTableFitMixin, dpGameActionCountdownMixin, dpGameLayoutTierMixin],
  provide() {
    return {
      dpGameView: this
    }
  },
  components: {
    GameTopBar,
    GameRoundTable,
    GameHeroDockFooter,
    GameDpFloatingModals,
    GameDpGameSheets
  },
  data() {
    return {
      communityCardsFlipCompleteTimer: null,
      /** WS/HTTP 房间快照指纹；与 {@link encodeRoomApplyFingerprint} 一致，未变则跳过 APPLY_ROOM */
      _lastRoomApplyFingerprint: '',
      gameWs: null,
      gameWsConnected: false,
      /** 每开一条新连接前自增，用于丢弃旧 socket 的 onclose/onopen，避免顶替连接时误触重连 */
      gameWsSession: 0,
      wsReconnectTimer: null,
      /** 已连续重连失败次数；成功 onopen 时清零 */
      wsReconnectAttempt: 0,
      /** 离房 / 解散 / 组件销毁后禁止再连 */
      wsNoReconnect: false,
      pollTimer: null,
      backupPollTimer: null,
      heartbeatTimer: null,
      _seatChatTimers: null,
      _dpRoomClosedHandled: false,
      _lastRoomBgmUrl: '',
      _lastRoomMusicWebPath: '',
      playerSocialOpen: false,
      playerSocialTarget: null,
      inviteFriendOpen: false
    }
  },

  computed: {
    ...mapState('dpGame', [
      'gameUiTheme', 'customThemeBase', 'customThemeOverrides', 'ecoMode', 'gameThemeOptions', 'roomId', 'user', 'currentHandSeed', 'owner', 'players', 'playing', 'stage', 'communityCards', 'pot', 'pots', 'currentBetToCall', 'lastRaiseIncrement', 'actIndex', 'spectators', 'waitNextHand', 'raiseAmount', 'selectedWinners', 'potWinners', 'nextHandReady', 'loading', 'communityCardsFlipState', 'communityCardsFlipComplete', 'seatChatTextByNick', 'roomChatMessages', 'chatInputDraft', 'showPlayGuideModal', 'playGuideTab', 'showSpectatorModal', 'showWaitNextHandModal', 'showHandHistoryModal', 'showOpponentHandHistoryModal', 'opponentHandHistoryOtherUserId', 'opponentHandHistoryDisplayName', 'showMusicBoxModal', 'musicTracks', 'musicTracksLoading', 'musicTracksError', 'roomMusicState', 'showOwnerHubSheet', 'showCustomNpcStyleDialog', 'customNpcPendingCount', 'ownerToolType', 'ownerActionTarget', 'demoBotAdding', 'demoBotAddedTip', 'maniacBotAdding', 'maniacBotAddedTip', 'tagBotAdding', 'tagBotAddedTip', 'lagBotAdding', 'lagBotAddedTip', 'nitBotAdding', 'nitBotAddedTip', 'callBotAdding', 'callBotAddedTip', 'llmBotAdding', 'llmBotAddedTip', 'llmGlobalBotAdding', 'llmGlobalBotAddedTip', 'customBotAdding', 'customBotAddedTip', 'ownerRevealAll', 'showMobileHandSheet', 'showMobileActionSheet', 'heroHoleDealIntroDone', 'chipLeaderNicknames', 'myCarryInChips'
    ]),
    ...mapGetters('dpGame', [
      'effectiveThemeForCss', 'customThemeInlineStyle', 'handRankReference', 'stageCN', 'isOwner', 'canInviteFriend', 'isMyTurn', 'myPlayer', 'showSpectatorPrepareBlock', 'myReady', 'myChips', 'myBet', 'callAmount', 'smallBlind', 'bigBlind', 'lastRaiseIncrementEffective', 'minTotalToRaise', 'minRaise', 'allPotsHaveWinners', 'inSettledStage', 'ownerActionPlayers', 'playersDisplayOrder', 'viewerSeatedAtTable', 'holeDealPlayerCountForAnim', 'heroDockRow', 'dealerDisplayIndex', 'showdownHandLeaderNicknames', 'spectatorSeatChatEntries', 'tableActionActorDisplayName', 'mobileHeroDockActive', 'showHeroViewHandButton', 'showBottomHeroDock'
    ]),
    actionTimerProgressPct() {
      var t = Number(this.timeLeft)
      if (isNaN(t) || t < 0) return 0
      return Math.min(1, t / 30)
    },
    tableActionTimerUrgency() {
      var t = Number(this.timeLeft)
      if (isNaN(t)) return 'ok'
      if (t > 10) return 'ok'
      if (t > 5) return 'warn'
      return 'danger'
    },
    showTableActionTimer() {
      return this.actionCountdownShouldRun()
    },
    /** 顶栏展示本人持有/本轮/还需补（与原底栏筹码条同期机一致，避免重复） */
    topBarShowHeroEconomy() {
      return !!(this.viewerSeatedAtTable && this.heroDockRow)
    },
    topBarHeroEconomySecondaryLabel() {
      if (this.isMyTurn && !this.inSettledStage && (Number(this.callAmount) || 0) > 0) {
        return '还需补'
      }
      return '本轮'
    },
    topBarHeroEconomySecondaryValue() {
      if (this.isMyTurn && !this.inSettledStage && (Number(this.callAmount) || 0) > 0) {
        return Number(this.callAmount) || 0
      }
      return Number(this.myBet) || 0
    }
  },

  watch: {
    isMyTurn: function (v) {
      if (v) this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
      else this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: false })
    },
    heroDockRow: function (row) {
      if (!row) this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileHandSheet: false })
    },
    minRaise: function () {
      if (this.isMyTurn && this.raiseAmount < this.minRaise) {
        this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
      }
    },
    actIndex() {
      this.syncActionCountdown()
    },
    playing() {
      this.syncActionCountdown()
    },
    currentHandSeed() {
      this.$store.commit('dpGame/SET_HERO_HOLE_DEAL', false)
      this.syncActionCountdown()
    },
    stage(newVal) {
      this.syncActionCountdown()
      if (newVal !== 'preflop') {
        this.$store.commit('dpGame/SET_HERO_HOLE_DEAL', true)
      }
      if (newVal === 'settled') {
        this.startReadyCountdown()
      } else {
        this.stopReadyCountdown()
        this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: false })
      }
      var self = this
      this.$nextTick(function () {
        self.syncRoomBgmAudio()
      })
    },
    isOwner(v) {
      if (!v) this.$store.commit('dpGame/CLOSE_OWNER_HUB')
    }
  },

  beforeRouteUpdate(to, from, next) {
    if (to.params.roomId !== from.params.roomId) {
      this.resetRoomChatUiForEnter()
      this.$store.commit('dpGame/SET_SESSION', { roomId: to.params.roomId })
      var self = this
      this.loadGame().then(function () {
        self.fetchRoomChatRecent()
        self.shutdownGameWsPermanently()
        self.connectGameWs()
      })
    }
    next()
  },

  created() {
    this._seatChatTimers = Object.create(null)
    this.resetRoomChatUiForEnter()
    this.$store.commit('dpGame/SET_SESSION', { roomId: this.$route.params.roomId })

    var self = this
    ;(async function () {
      var raw = localStorage.getItem('userInfo')
      if (!raw) {
        self.$message.error('登录信息丢失，请重新登录')
        self.$router.push('/login')
        return
      }
      var user = await ensureDpUserIdInStorage(self.$http)
      if (!user || !user.nickname) {
        self.$message.error('登录信息丢失，请重新登录')
        self.$router.push('/login')
        return
      }
      var uid = Number(user.userId)
      if (isNaN(uid) || uid <= 0) {
        self.$message.error('登录信息不完整，请重新登录以同步账号 ID')
        self.$router.push('/login')
        return
      }
      user.userId = uid
      self.$store.commit('dpGame/SET_SESSION', { user: user })

      // 先 HTTP 拉一次，再建立 WebSocket（推送与定时器同 1s 节奏）
      self.loadGame().then(function () {
        self.fetchRoomChatRecent()
        self.connectGameWs()
      })

      self.loadMusicTracks()

      // 未连上 WS 时 1 秒轮询兜底；握手过程中 readyState===CONNECTING 也要停掉，否则会连着打一串 getNowRoom
      self.pollTimer = setInterval(function () {
        if (self.loading) return
        if (self.gameWsConnected) return
        if (self.gameWs && self.gameWs.readyState === WebSocket.CONNECTING) return
        self.loadGame()
      }, 1000)

      // 已连上 WS 时低频 HTTP 兜底（防止长连异常而界面停滞）
      self.backupPollTimer = setInterval(function () {
        if (!self.loading && self.gameWsConnected) self.loadGame()
      }, 15000)

      // 5秒独立心跳（和 loadGame 解耦，loadGame 失败不影响心跳）
      self.sendHeartbeat()
      self.heartbeatTimer = setInterval(function () {
        self.sendHeartbeat()
      }, 5000)
    })()
  },

  beforeDestroy() {
    this.$store.commit('dpGame/SET_MODAL', { showCustomNpcStyleDialog: false })
    try {
      var bgm = this.$refs.roomBgm
      if (bgm) {
        bgm.pause()
        bgm.removeAttribute('src')
      }
    } catch (e) { /* ignore */ }
    this.shutdownGameWsPermanently()
    if (this.pollTimer) clearInterval(this.pollTimer)
    if (this.backupPollTimer) clearInterval(this.backupPollTimer)
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    if (this.actionTimer) clearInterval(this.actionTimer)
    if (this.readyTimer) clearInterval(this.readyTimer)
    if (this.communityCardsFlipCompleteTimer) clearTimeout(this.communityCardsFlipCompleteTimer)
    this._lastRoomApplyFingerprint = ''
    if (this._seatChatTimers) {
      var self = this
      Object.keys(this._seatChatTimers).forEach(function (k) {
        clearTimeout(self._seatChatTimers[k])
      })
      this._seatChatTimers = Object.create(null)
    }
  },

  methods: {
    /**
     * 离开房间时多处可能同时触发跳转（WS roomClosed + 轮询 getNowRoom 为空、热更新等）；
     * Vue Router 3 对重复 push 同一地址会抛 NavigationDuplicated，需吞掉或跳过。
     */
    navigateHomeIfNeeded() {
      if (this.$route.path === '/home') return Promise.resolve()
      return this.$router.push('/home').catch(function (err) {
        if (err && err.name === 'NavigationDuplicated') return
        throw err
      })
    },

    // ---- 心跳（独立，不依赖 loadGame） ----
    sendHeartbeat() {
      if (!this.user) return
      this.$http.post('/dpRoom/heartbeat', null, {
        params: {roomId: this.roomId, nickname: this.user.nickname}
      }).catch(function (e) {
        console.error('心跳失败', e)
      })
    },

    gameWsBaseUrl() {
      // Electron 桌面客户端：连到 config.json 配置的服务器地址
      if (typeof window !== 'undefined' && window.dpElectron && window.dpElectron.serverUrl) {
        var url = window.dpElectron.serverUrl.replace(/\/+$/, '')
        return url.replace(/^https?:/, url.indexOf('https:') === 0 ? 'wss:' : 'ws:')
      }
      // 与页面同源；开发时游戏 WS 走 vue.config.js 的 /dp-ws → 后端 /ws
      var secure = window.location.protocol === 'https:'
      return (secure ? 'wss:' : 'ws:') + '//' + window.location.host
    },

    clearWsReconnectTimer() {
      if (this.wsReconnectTimer != null) {
        clearTimeout(this.wsReconnectTimer)
        this.wsReconnectTimer = null
      }
    },

    /**
     * 永久关闭 WS（离房、解散、组件销毁）：取消重连并摘掉回调，避免 onclose 再 schedule。
     */
    shutdownGameWsPermanently() {
      this.wsNoReconnect = true
      this.clearWsReconnectTimer()
      this.gameWsSession++
      var w = this.gameWs
      this.gameWs = null
      this.gameWsConnected = false
      if (w) {
        w.onopen = null
        w.onclose = null
        w.onerror = null
        w.onmessage = null
        try {
          w.close()
        } catch (e) { /* ignore */ }
      }
    },

    /**
     * 主动离房：在 exitRoom 之前关掉推送/轮询，并标记已处理 roomClosed，避免弹「房间已解散」。
     */
    beginIntentionalLeave() {
      this._dpRoomClosedHandled = true
      this.shutdownGameWsPermanently()
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
      if (this.backupPollTimer) {
        clearInterval(this.backupPollTimer)
        this.backupPollTimer = null
      }
      if (this.heartbeatTimer) {
        clearInterval(this.heartbeatTimer)
        this.heartbeatTimer = null
      }
    },

    scheduleWsReconnect(sessionAtOpen) {
      var self = this
      if (self.wsNoReconnect) return
      if (self.gameWsSession !== sessionAtOpen) return
      var exp = Math.min(5, self.wsReconnectAttempt)
      var delay = Math.min(30000, 1000 * Math.pow(2, exp))
      var jitter = Math.floor(Math.random() * 400)
      self.wsReconnectAttempt++
      self.clearWsReconnectTimer()
      self.wsReconnectTimer = setTimeout(function () {
        self.wsReconnectTimer = null
        if (self.wsNoReconnect) return
        if (self.gameWsSession !== sessionAtOpen) return
        var g = self.gameWs
        if (g && (g.readyState === WebSocket.OPEN || g.readyState === WebSocket.CONNECTING)) return
        self.connectGameWs()
      }, delay + jitter)
    },

    connectGameWs() {
      var self = this
      if (self.wsNoReconnect) return

      self.clearWsReconnectTimer()

      self.gameWsSession++
      var sessionAtOpen = self.gameWsSession

      if (self.gameWs) {
        var old = self.gameWs
        self.gameWs = null
        old.onopen = null
        old.onclose = null
        old.onerror = null
        old.onmessage = null
        try {
          old.close()
        } catch (e) { /* ignore */ }
      }
      self.gameWsConnected = false

      // 开发服：走 /dp-ws → vue 代理转成后端 /ws（避免与 webpack HMR 的 /ws 冲突）
      var path = process.env.NODE_ENV === 'development' ? '/dp-ws/dp-game' : '/ws/dp-game'
      var url = self.gameWsBaseUrl() + path + '?roomId=' + encodeURIComponent(self.roomId)
        + '&nickname=' + encodeURIComponent(self.user.nickname)
      try {
        var ws = new WebSocket(url)
        self.gameWs = ws
        ws.onopen = function () {
          if (self.gameWsSession !== sessionAtOpen || self.wsNoReconnect) {
            try {
              ws.close()
            } catch (err) { /* ignore */ }
            return
          }
          self.gameWsConnected = true
          self.wsReconnectAttempt = 0
          self.fetchRoomChatRecent()
        }
        ws.onmessage = function (ev) {
          try {
            var data = JSON.parse(ev.data)
            if (data._ws === 'roomClosed') {
              self.handleRoomClosedFromServer()
              return
            }
            if (data._ws === 'chat') {
              self.pushRoomChatFromServer(data)
              return
            }
            if (data._ws === 'roomMusic') {
              self.applyRoomMusicMessage(data)
              return
            }
            self.applyRoomFromServer(data)
          } catch (e) {
            console.error('WebSocket 消息解析失败', e)
          }
        }
        ws.onclose = function () {
          if (self.gameWsSession !== sessionAtOpen) return
          self.gameWsConnected = false
          if (self.gameWs === ws) self.gameWs = null
          if (!self.wsNoReconnect) self.scheduleWsReconnect(sessionAtOpen)
        }
        ws.onerror = function (e) {
          console.warn('WebSocket 错误（将按退避重试）', e)
        }
      } catch (e) {
        console.error('WebSocket 连接失败', e)
        if (!self.wsNoReconnect && self.gameWsSession === sessionAtOpen) {
          self.scheduleWsReconnect(sessionAtOpen)
        }
      }
    },

    handleRoomClosedFromServer() {
      if (this._dpRoomClosedHandled) return
      this._dpRoomClosedHandled = true
      this.$store.commit('dpGame/RESET_ON_ROOM_CLOSED')
      this._lastRoomBgmUrl = ''
      this._lastRoomMusicWebPath = ''
      try {
        var bgm = this.$refs.roomBgm
        if (bgm) {
          bgm.pause()
          bgm.removeAttribute('src')
        }
      } catch (e) { /* ignore */ }
      var self = this
      this.shutdownGameWsPermanently()
      if (this.pollTimer) clearInterval(this.pollTimer)
      if (this.backupPollTimer) clearInterval(this.backupPollTimer)
      if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
      this.$alert('房间已解散或你已被移出', '提示', {
        confirmButtonText: '确定',
        type: 'warning'
      }).then(function () {
        return self.navigateHomeIfNeeded()
      }).catch(function () {
        return self.navigateHomeIfNeeded()
      })
    },

    formatChatNick(name) {
      return dpDisplayNickname(name || '')
    },

    seatChatTextFor(nickname) {
      if (!nickname) return ''
      var m = this.seatChatTextByNick
      return (m && m[nickname]) ? m[nickname] : ''
    },

    applyRoomMusicMessage(data) {
      if (!data || data._ws !== 'roomMusic') return
      this.$store.commit('dpGame/SET_ROOM_MUSIC_STATE', {
        action: data.action,
        trackId: data.trackId,
        webPath: data.webPath,
        displayName: data.displayName,
        byNickname: data.byNickname,
        serverTime: data.serverTime
      })
      var wp = data.webPath != null ? String(data.webPath).trim() : ''
      if (wp) this._lastRoomMusicWebPath = wp
      var self = this
      this.$nextTick(function () {
        self.syncRoomBgmAudio()
      })
    },

    /**
     * 根据 {@link #roomMusicState} 与当前阶段驱动隐藏 {@code <audio>}；摊牌/结算时暂停以免与结算 BGM 叠播。
     */
    syncRoomBgmAudio() {
      var el = this.$refs.roomBgm
      if (!el) return
      var st = this.stage
      if (st === 'showdown' || st === 'settled') {
        try {
          el.pause()
        } catch (e) { /* ignore */ }
        return
      }
      var m = this.roomMusicState
      if (!m || !m.action) return
      var a = m.action
      if (a === 'stop') {
        try {
          el.pause()
          el.currentTime = 0
          el.removeAttribute('src')
        } catch (e) { /* ignore */ }
        this._lastRoomBgmUrl = ''
        this._lastRoomMusicWebPath = ''
        return
      }
      if (a === 'pause') {
        try {
          el.pause()
        } catch (e) { /* ignore */ }
        return
      }
      if (a !== 'play' || !m.webPath) return
      var url = musicFileSrc(m.webPath)
      if (this._lastRoomBgmUrl !== url) {
        this._lastRoomBgmUrl = url
        el.src = url
      }
      var p = el.play()
      if (p && typeof p.then === 'function') {
        p.catch(function () { /* 未与页面交互时部分浏览器会拒绝自动播放 */ })
      }
    },

    sendRoomMusicSync(payload) {
      if (!this.user) return
      if (!this.gameWs || this.gameWs.readyState !== WebSocket.OPEN) {
        this.$message.warning('未连接房间推送，请稍候再试')
        return
      }
      var action = payload.action
      var webPath = (payload.webPath != null ? String(payload.webPath) : '').trim()
      var displayName = (payload.displayName != null ? String(payload.displayName) : '').trim()
      if ((action === 'pause' || action === 'stop') && !webPath) {
        var rm = this.roomMusicState
        webPath = (rm && rm.webPath) ? String(rm.webPath).trim() : ''
        if (!webPath) webPath = (this._lastRoomMusicWebPath || '').trim()
        if (!displayName && rm && rm.displayName) {
          displayName = String(rm.displayName).trim()
        }
      }
      if (action === 'play' && webPath) {
        this._lastRoomMusicWebPath = webPath
      }
      var body = {
        _ws: 'roomMusicSync',
        nickname: this.user.nickname,
        action: action,
        trackId: payload.trackId != null ? payload.trackId : 0,
        webPath: webPath,
        displayName: displayName
      }
      try {
        this.gameWs.send(JSON.stringify(body))
      } catch (e) {
        console.error('roomMusicSync', e)
        this.$message.error('发送失败')
      }
    },

    async loadMusicTracks() {
      this.$store.commit('dpGame/SET_MUSIC_TRACKS', { loading: true, error: '' })
      try {
        var res = await this.$http.get('/dpMusic/list')
        this.$store.commit('dpGame/SET_MUSIC_TRACKS', {
          tracks: Array.isArray(res.data) ? res.data : [],
          loading: false,
          error: ''
        })
      } catch (e) {
        console.error('dpMusic/list', e)
        this.$store.commit('dpGame/SET_MUSIC_TRACKS', {
          tracks: [],
          loading: false,
          error: CAT_COPY.musicListLoadFailed
        })
      }
    },

    normalizeRoomChatRow(data, nick, text) {
      var id = data.id != null ? String(data.id) : ''
      if (!id) id = String(Date.now()) + '-' + Math.random().toString(36).slice(2, 8)
      return {
        id: id,
        nickname: nick,
        text: text,
        serverTime: data.serverTime != null ? Number(data.serverTime) : Date.now(),
        senderUserId: data.senderUserId != null ? data.senderUserId : null
      }
    },

    /** 进房 / 换房：清空上一局的聊天列表与座位气泡（NPC 桌边话仅存前端，不落库） */
    resetRoomChatUiForEnter() {
      this.$store.commit('dpGame/CLEAR_ROOM_CHAT_MESSAGES')
      this.$store.commit('dpGame/CLEAR_ALL_SEAT_CHAT')
      if (this._seatChatTimers) {
        var self = this
        Object.keys(this._seatChatTimers).forEach(function (k) {
          clearTimeout(self._seatChatTimers[k])
        })
        this._seatChatTimers = Object.create(null)
      }
    },

    pushRoomChatFromServer(data) {
      var nick = (data.nickname || '').trim()
      var text = (data.text != null ? String(data.text) : '').trim()
      if (!nick || !text) return
      this.$store.commit('dpGame/APPEND_ROOM_CHAT_MESSAGE', this.normalizeRoomChatRow(data, nick, text))
      var ttl = typeof data.ttlMs === 'number' && data.ttlMs > 0 ? data.ttlMs : 15000
      var prev = this._seatChatTimers[nick]
      if (prev) {
        clearTimeout(prev)
        delete this._seatChatTimers[nick]
      }
      this.$store.commit('dpGame/SET_SEAT_CHAT', { nick: nick, text: text })
      var self = this
      var tid = setTimeout(function () {
        if (self.seatChatTextByNick[nick] === text) {
          self.$store.commit('dpGame/DELETE_SEAT_CHAT', nick)
        }
        delete self._seatChatTimers[nick]
      }, ttl)
      this._seatChatTimers[nick] = tid
    },

    async fetchRoomChatRecent() {
      if (!this.roomId || !this.$http) return
      var api = dpRoomApi(this.$http)
      try {
        var res = await api.recentChat(this.roomId, { limit: 50 })
        var body = res.data
        if (!dpResultSuccess(body)) return
        var d = dpResultData(body) || {}
        var items = Array.isArray(d.items) ? d.items : []
        var rows = items
          .map(function (row) {
            var nick = (row.nickname || '').trim()
            var text = row.text != null ? String(row.text).trim() : ''
            if (!nick || !text) return null
            return {
              id: row.id != null ? String(row.id) : '',
              nickname: nick,
              text: text,
              serverTime: row.serverTime != null ? Number(row.serverTime) : 0,
              senderUserId: row.senderUserId != null ? row.senderUserId : null
            }
          })
          .filter(Boolean)
        this.$store.commit('dpGame/REPLACE_ROOM_CHAT_MESSAGES', rows)
      } catch (e) {
        console.warn('fetchRoomChatRecent', e)
      }
    },

    sendRoomChat() {
      var t = (this.chatInputDraft || '').trim()
      if (!t) return
      if (!this.user) return
      if (!this.gameWs || this.gameWs.readyState !== WebSocket.OPEN) {
        this.$message.warning('未连接房间推送，请稍候再试')
        return
      }
      if (t.length > 200) {
        this.$message.warning('单条最多 200 字')
        return
      }
      try {
        this.gameWs.send(JSON.stringify({
          _ws: 'chatSend',
          nickname: this.user.nickname,
          text: t
        }))
        this.$store.commit('dpGame/SET_CHAT_DRAFT', '')
      } catch (e) {
        console.error('发送聊天失败', e)
        this.$message.error('发送失败')
      }
    },

    applyRoomFromServer(room) {
      var fp = encodeRoomApplyFingerprint(room)
      if (fp && fp === this._lastRoomApplyFingerprint) {
        this.$nextTick(function () {
          this.syncActionCountdown()
          if (this.isMyTurn && this.raiseAmount < this.minRaise) {
            this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
          }
        }.bind(this))
        return
      }
      this._lastRoomApplyFingerprint = fp
      this.$store.commit('dpGame/APPLY_ROOM', room)
      this.syncCommunityCardsFlipState(room.communityCards || [])
      this.$nextTick(function () {
        this.syncActionCountdown()
        if (this.isMyTurn && this.raiseAmount < this.minRaise) {
          this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
        }
      }.bind(this))
    },

    /** eco 或系统「减少动态效果」：公共牌翻面走短定时，与 CSS 瞬时翻转对齐 */
    prefersReducedMotionForFlip() {
      try {
        return window.matchMedia('(prefers-reduced-motion: reduce)').matches
      } catch (e) {
        return false
      }
    },

    // ---- 拉取房间状态 ----
    async loadGame() {
      this.$store.commit('dpGame/SET_LOADING', true)
      try {
        var res = await this.$http.get('/dpRoom/getNowRoom', {
          params: {roomId: this.roomId, nickname: this.user ? this.user.nickname : ''}
        })
        var room = res.data
        if (!room) {
          if (!this.wsNoReconnect) {
            this.handleRoomClosedFromServer()
          }
          return
        }
        this.applyRoomFromServer(room)
      } catch (err) {
        console.error('拉取状态失败', err)
      } finally {
        this.$store.commit('dpGame/SET_LOADING', false)
      }
    },

    // ---- 准备/取消准备（与 readyNextHand 一致：ok 才 commit 本地态、提示、loadGame）----
    async toggleReady() {
      if (!this.user) return
      var wasReady = this.myReady
      try {
        var res = await this.$http.post('/dpRoom/toggleReady', null, {
          params: { roomId: this.roomId, nickname: this.user.nickname }
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/PATCH_MY_PLAYER_READY', !wasReady)
          if (wasReady) {
            this.$message.success('已取消准备')
          } else {
            this.$message.success('已准备下一局')
          }
          await this.loadGame()
        } else {
          this.$message.error('操作失败：' + res.data)
        }
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },
    // 结算后积分归零时补满
    async rebuy() {
      try {
        var res = await this.$http.post('/dpRoom/rebuy', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') {
          this.$message.error('补满失败：' + res.data)
        } else {
          this.$message.success('补满成功，可在结算阶段准备下一局')
        }
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 跟投/观望 ----
    async doCall() {
      await this.submitBet(this.callAmount)
    },

    // ---- 加投 ----
    async doRaise() {
      if (this.raiseAmount < this.minRaise) {
        this.$message.warning(
          '加投不能低于 ' + this.minRaise + '（总投入至少到 ' + this.minTotalToRaise + '）'
        )
        return
      }
      if (this.raiseAmount > this.myChips) {
        this.$message.warning('小鱼干不足！')
        return
      }
      await this.submitBet(this.raiseAmount)
    },

    // ---- All-In ----
    async doAllIn() {
      await this.submitBet(this.myChips)
    },

    // ---- 统一提交本轮投入（接口字段名仍为 bet）----
    async submitBet(amount) {
      try {
        var res = await this.$http.post('/dpRoom/bet', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname, bet: amount}
        })
        if (res.data !== 'ok') this.$message.error('投入失败，请检查数额')
        this.$store.commit('dpGame/SET_RAISE_AMOUNT', 0)
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 盖牌 ----
    async doFold() {
      try {
        var res = await this.$http.post('/dpRoom/fold', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') this.$message.error('盖牌失败')
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 主动离座：将自己移到观众席 ----
    async doLeaveSeat() {
      if (!this.user || !this.user.nickname) return
      try {
        await this.dpConfirm('确定主动离座并进入观众席吗？', '主动离座', {
          confirmButtonText: '确认离座',
          cancelButtonText: '取消'
        })
      } catch (e) {
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/kickPlayer', null, {
          params: { roomId: this.roomId, nickname: this.user.nickname }
        })
        if (res.data !== 'ok') {
          this.$message.error('离座失败：' + res.data)
          return
        }
        this.$message.success('你已离座，当前为观众席状态')
        this.$store.commit('dpGame/SET_MOBILE_SHEETS', {
          showMobileHandSheet: false,
          showMobileActionSheet: false
        })
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 摊牌阶段：点击玩家卡片选/取消赢家（简单模式备用） ----
    handleJudgeClick(nickname) {
      if (!this.isOwner || this.stage !== 'showdown') return
      // 有边池数据时，不用这个旧的点击方式
      if (this.pots.length > 0) return

      this.$store.commit('dpGame/TOGGLE_SELECTED_WINNER', nickname)
    },

    openInviteFriendSheet() {
      if (!this.canInviteFriend) return
      this.inviteFriendOpen = true
      this.scheduleReparentElementUiLayersIntoFullscreenRoot()
    },
    closeInviteFriendSheet() {
      this.inviteFriendOpen = false
    },
    closePlayerSocialSheet() {
      this.playerSocialOpen = false
      this.playerSocialTarget = null
    },
    /**
     * 从玩家信息底栏打开「与 TA 的共同历史对局」（独立弹层，数据走 checkUserAndOtherPlayerHandHistoryList）
     * @param {{ userId: number, displayName: string }} payload
     */
    openOpponentHandHistoryFromSocial(payload) {
      if (!payload || payload.userId == null || payload.userId === '') return
      var uid = Number(payload.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return
      this.closePlayerSocialSheet()
      this.$store.commit('dpGame/SET_MODAL', {
        showOpponentHandHistoryModal: true,
        opponentHandHistoryOtherUserId: uid,
        opponentHandHistoryDisplayName: payload.displayName || ''
      })
    },
    /**
     * @param {string|{nickname:string,userId?:number}} payload
     */
    onPlayerCardClick(payload) {
      var nickname = typeof payload === 'string'
        ? payload
        : (payload && payload.nickname)
      if (!nickname) return

      if (this.user && nickname === this.user.nickname) {
        return
      }

      if (this.isOwner && this.stage === 'showdown' && (!this.pots || this.pots.length === 0)) {
        this.handleJudgeClick(nickname)
        return
      }

      if (isDpBotNickname(nickname)) {
        this.$message.info('机器人不支持该功能')
        return
      }

      var rawUid = typeof payload === 'object' && payload ? payload.userId : null
      var uid = rawUid != null && rawUid !== '' ? Number(rawUid) : NaN
      if (!uid || uid <= 0 || isNaN(uid)) {
        this.$message.warning('无法获取该玩家的账号信息，请对方使用已登录账号进房后再试')
        return
      }

      this.playerSocialTarget = { nickname: nickname, userId: uid }
      this.playerSocialOpen = true
    },

    // ---- 按池选赢家 ----
    togglePotWinner(potIndex, nickname) {
      var winners = (this.potWinners[potIndex] || []).slice()
      var idx = winners.indexOf(nickname)
      if (idx > -1) {
        winners.splice(idx, 1)
      } else {
        winners.push(nickname)
      }
      this.$store.commit('dpGame/SET_POT_WINNERS_AT', { potIndex: potIndex, winners: winners })
    },

    onTogglePotWinnerPayload(payload) {
      this.togglePotWinner(payload.potIndex, payload.nickname)
    },

    // ---- 按池确认结算 ----
    async confirmPotJudge() {
      // 拼接格式: "0:Alice,Bob;1:Charlie"
      var parts = []
      for (var i = 0; i < this.pots.length; i++) {
        var winners = this.potWinners[i] || []
        if (winners.length === 0) {
          this.$message.warning(dpPotDisplayLabel(i) + ' 还没选赢家')
          return
        }
        parts.push(i + ':' + winners.join(','))
      }
      var potWinnersStr = parts.join(';')

      // 组装确认信息（HTML 换行，避免原生 confirm 打断全屏）
      var lines = ['确认结算？']
      for (var j = 0; j < this.pots.length; j++) {
        var potName = dpPotDisplayLabel(j)
        lines.push(
          potName + '(' + this.pots[j].amount + ') -> '
          + (this.potWinners[j] || []).map(dpDisplayNickname).join(', ')
        )
      }
      var msgHtml = lines.join('<br/>')
      try {
        await this.dpConfirm(msgHtml, '确认结算', {
          confirmButtonText: '确定结算',
          dangerouslyUseHTMLString: true
        })
      } catch (e) {
        return
      }

      try {
        var res = await this.$http.post('/dpRoom/judgeWin', null, {
          params: {roomId: this.roomId, potWinners: potWinnersStr}
        })
        if (res.data !== 'ok') this.$message.error('结算失败')
        this.$store.commit('dpGame/CLEAR_JUDGE_SELECTION')
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 房主：确认结算 ----
    async confirmJudgeWin() {
      if (this.selectedWinners.length === 0) {
        this.$message.warning('请至少选择一位赢家')
        return
      }
      var names = this.selectedWinners.map(dpDisplayNickname).join(', ')
      try {
        await this.dpConfirm('确定由 [' + names + '] 平分小鱼干池 ' + this.pot + ' 吗？', '确认结算')
      } catch (e) {
        return
      }

      try {
        var res = await this.$http.post('/dpRoom/judgeWin', null, {
          params: {roomId: this.roomId, winnerNickname: this.selectedWinners.join(',')}
        })
        if (res.data !== 'ok') this.$message.error('结算失败')
        this.$store.commit('dpGame/SET_SELECTED_WINNERS', [])
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 房主神器：底栏入口与底部抽屉 ----
    openOwnerHubSheet() {
      this.$store.commit('dpGame/OPEN_OWNER_HUB')
    },

    closeOwnerHubPanel() {
      this.$store.commit('dpGame/CLOSE_OWNER_HUB')
    },

    // ---- 房主：移交房主（通过弹窗选择玩家） ----
    async doTransferOwner() {
      if (!this.ownerActionTarget) {
        this.$message.warning('请先选择要移交房主的玩家')
        return
      }
      if (this.ownerActionTarget === this.user.nickname) {
        this.$message.warning('不能把房主移交给自己')
        return
      }
      try {
        await this.dpConfirm(
          '确定将房主移交给 [' + dpDisplayNickname(this.ownerActionTarget) + '] 吗？',
          '移交房主'
        )
      } catch (e) {
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/transferOwner', null, {
          params: {
            roomId: this.roomId,
            fromNickname: this.user.nickname,
            toNickname: this.ownerActionTarget
          }
        })
        if (res.data !== 'ok') {
          this.$message.error('移交失败：' + res.data)
        } else {
          this.$message.success('已将房主移交给 ' + dpDisplayNickname(this.ownerActionTarget))
        }
        await this.loadGame()
        this.closeOwnerHubPanel()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 房主：踢人到观众席（可多选批量） ----
    async doKickPlayers (nicknames) {
      var raw = [].concat(nicknames || []).filter(Boolean)
      var seen = {}
      var list = []
      for (var i = 0; i < raw.length; i++) {
        var n = raw[i]
        if (seen[n]) continue
        seen[n] = true
        list.push(n)
      }
      if (!list.length) {
        this.$message.warning('请至少选择一名要踢出的玩家')
        return
      }
      var preview = list.slice(0, 8).map(function (n) {
        return dpDisplayNickname(n)
      }).join('、')
      if (list.length > 8) preview += ' …'
      try {
        await this.dpConfirm(
          '确定将以下 ' +
            list.length +
            ' 人踢出本局并移至观众席吗？\n\n' +
            preview,
          '批量踢出'
        )
      } catch (e) {
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/kickPlayersBatch', null, {
          params: { roomId: this.roomId, nicknames: list.join(',') }
        })
        var body = res.data
        if (!dpResultSuccess(body)) {
          var errData = body && body.data ? body.data : {}
          var fn = errData.failedNicknames || []
          var msg = dpResultMessage(body)
          if (fn.length) {
            msg +=
              '：' +
              fn
                .map(function (n) {
                  return dpDisplayNickname(n)
                })
                .join('、')
          }
          this.$message.error(msg)
        } else {
          var d = dpResultData(body) || {}
          var fc = d.failCount != null ? d.failCount : 0
          if (fc > 0) {
            var failedNicks = d.failedNicknames || []
            var detail = failedNicks
              .map(function (n) {
                return dpDisplayNickname(n)
              })
              .join('、')
            this.$message.warning(
              '已踢出 ' +
                (d.successCount != null ? d.successCount : list.length - fc) +
                ' 人，另有 ' +
                fc +
                ' 人未成功：' +
                detail
            )
          } else {
            var okn = d.successCount != null ? d.successCount : list.length
            this.$message.success('已将 ' + okn + ' 人踢至观众席')
          }
        }
        await this.loadGame()
        this.closeOwnerHubPanel()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    closeCustomNpcStyleDialog () {
      this.$store.commit('dpGame/SET_MODAL', { showCustomNpcStyleDialog: false })
    },

    /**
     * 自定义 NPC：弹窗确定后提交（本批共用一套 profile）。
     */
    async submitCustomNpcBatch (profile) {
      if (!this.roomId || !this.user || !this.user.nickname) {
        this.$message.warning('请先登录')
        return
      }
      var count = this.customNpcPendingCount
      this.$store.commit('dpGame/SET_MODAL', { showCustomNpcStyleDialog: false })
      this.$store.commit('dpGame/SET_BOT_STATE', {
        customBotAdding: true,
        customBotAddedTip: ''
      })
      try {
        var res = await this.$http.post('/dpRoom/addCustomNpcBatch', {
          roomId: this.roomId,
          count: count,
          requesterNickname: this.user.nickname,
          profile: profile
        })
        var msg
        if (res.data === 'ok') {
          msg =
            '已请求在下一局加入最多 ' +
            count +
            ' 个自定义 NPC（受空位限制；本批共用一套参数），请等待本局结束。'
        } else {
          msg = '添加失败：' + res.data
        }
        this.$store.commit('dpGame/SET_BOT_STATE', { customBotAddedTip: msg })
      } catch (e) {
        this.$store.commit('dpGame/SET_BOT_STATE', {
          customBotAddedTip: '网络错误：' + (e && e.message ? e.message : e)
        })
      } finally {
        this.$store.commit('dpGame/SET_BOT_STATE', { customBotAdding: false })
      }
    },

    /**
     * 房主神器：按数量将 NPC 加入下一局等待列表（规则档走 addRuleNpcBatch）。
     */
    async confirmAddOwnerNpcs (payload) {
      if (!this.roomId || !payload) return
      var type = payload.type
      var count = parseInt(payload.count, 10)
      if (isNaN(count) || count < 1) count = 1
      if (count > 9) count = 9

      if (type === 'custom') {
        this.$store.commit('dpGame/SET_MODAL', {
          customNpcPendingCount: count,
          showCustomNpcStyleDialog: true
        })
        return
      }

      var ruleStore = {
        FISH: { prefix: 'demoBot' },
        MANIAC: { prefix: 'maniacBot' },
        TAG: { prefix: 'tagBot' },
        LAG: { prefix: 'lagBot' },
        NIT: { prefix: 'nitBot' },
        CALL: { prefix: 'callBot' }
      }

      var adding = {}
      var tipEmpty = {}
      var tipPrefix = ''
      var run = null

      if (type === 'rule') {
        var arch = String(payload.archetype || 'FISH').toUpperCase().replace(/^BOT_/, '')
        var rs = ruleStore[arch]
        if (!rs) {
          this.$message.warning('不支持的机器人类型')
          return
        }
        tipPrefix = rs.prefix
        adding[tipPrefix + 'Adding'] = true
        tipEmpty[tipPrefix + 'AddedTip'] = ''
        run = async function () {
          var res = await this.$http.post('/dpRoom/addRuleNpcBatch', null, {
            params: { roomId: this.roomId, archetype: arch, count: count }
          })
          if (res.data === 'ok') {
            return '已请求在下一局加入最多 ' + count + ' 个 ' + arch + '（受空位限制；每人独立编号），请等待本局结束。'
          }
          return '添加失败：' + res.data
        }.bind(this)
      } else if (type === 'llm') {
        tipPrefix = 'llmBot'
        adding.llmBotAdding = true
        tipEmpty.llmBotAddedTip = ''
        run = async function () {
          var ok = 0
          var lastErr = ''
          for (var i = 0; i < count; i++) {
            var res = await this.$http.post('/dpRoom/addLlmBot', null, {
              params: { roomId: this.roomId }
            })
            if (res.data === 'ok') {
              ok++
            } else {
              lastErr = String(res.data)
              break
            }
          }
          if (ok === count) {
            return '已请求在下一局加入 ' + count + ' 个 BOT_LLM，请等待本局结束（需配置服务端方舟密钥）。'
          }
          if (ok > 0) {
            return '仅成功添加 ' + ok + '/' + count + ' 个：' + (lastErr || '席位可能已满')
          }
          return '添加大模型 NPC 失败：' + (lastErr || 'fail')
        }.bind(this)
      } else if (type === 'llmGlobal') {
        tipPrefix = 'llmGlobalBot'
        adding.llmGlobalBotAdding = true
        tipEmpty.llmGlobalBotAddedTip = ''
        run = async function () {
          var ok = 0
          var lastErr = ''
          for (var i = 0; i < count; i++) {
            var res = await this.$http.post('/dpRoom/addLlmGlobalBot', null, {
              params: { roomId: this.roomId }
            })
            if (res.data === 'ok') {
              ok++
            } else {
              lastErr = String(res.data)
              break
            }
          }
          if (ok === count) {
            return '已请求在下一局加入 ' + count + ' 个 BOT_LLM_GLOBAL（全局叙事多轮），请等待本局结束（需服务端方舟密钥）。'
          }
          if (ok > 0) {
            return '仅成功添加 ' + ok + '/' + count + ' 个：' + (lastErr || '席位可能已满')
          }
          return '添加 BOT_LLM_GLOBAL 失败：' + (lastErr || 'fail')
        }.bind(this)
      } else {
        return
      }

      this.$store.commit('dpGame/SET_BOT_STATE', Object.assign({}, adding, tipEmpty))
      try {
        var msg = await run()
        var tipPatch = {}
        tipPatch[tipPrefix + 'AddedTip'] = msg
        this.$store.commit('dpGame/SET_BOT_STATE', tipPatch)
      } catch (e) {
        var errPatch = {}
        errPatch[tipPrefix + 'AddedTip'] = '网络错误：' + (e && e.message ? e.message : e)
        this.$store.commit('dpGame/SET_BOT_STATE', errPatch)
      } finally {
        var idle = {}
        idle[tipPrefix + 'Adding'] = false
        this.$store.commit('dpGame/SET_BOT_STATE', idle)
      }
    },

    openHandHistory() {
      this.$store.commit('dpGame/SET_MODAL', { showHandHistoryModal: true })
    },

    // ---- 退出 ----
    async exitGame() {
      try {
        await this.dpConfirm('确定退出对局？', '退出对局', {
          confirmButtonText: '退出',
          cancelButtonText: '取消'
        })
      } catch (e) {
        return
      }
      this.$store.commit('dpGame/SET_MODAL', { showCustomNpcStyleDialog: false })
      this.beginIntentionalLeave()
      try {
        await this.$http.post('/dpRoom/exitRoom', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
      } catch (err) {
        console.error('退出失败', err)
      }
      this.navigateHomeIfNeeded()
    },

    // ---- 观众：报名 / 取消下一局加入（再点一次从候补列表移除）----
    async readyNextHand() {
      if (!this.user) return
      try {
        var rp = { roomId: this.roomId, nickname: this.user.nickname }
        if (this.user.userId != null && this.user.userId !== '') {
          rp.userId = this.user.userId
        }
        if (this.nextHandReady) {
          var cancelRes = await this.$http.post('/dpRoom/cancelReadyNextHand', null, {
            params: rp
          })
          if (cancelRes.data === 'ok') {
            this.$store.commit('dpGame/SET_NEXT_HAND_READY', false)
            this.$message.success('已取消下一局报名')
            await this.loadGame()
          } else {
            this.$message.error('取消失败：' + cancelRes.data)
          }
          return
        }
        var res = await this.$http.post('/dpRoom/readyNextHand', null, {
          params: rp
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/SET_NEXT_HAND_READY', true)
          this.$message.success('已报名下一局，将在下一局开局时自动加入对局')
          await this.loadGame()
        } else {
          this.$message.error('报名失败：' + res.data)
        }
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    /**
     * 同步公共牌翻转状态：新牌先背面，再依次翻转；翻完后再允许显示牌型
     *
     * 注意：房间状态会高频推送（约 1s）。若每次推送都清掉「翻完」定时器，而公共牌张数未变（numNew===0），
     * 将不会重新设定时器，导致 communityCardsFlipComplete 长期为 false，成牌牌型区可卡住数秒～十余秒。
     * 因此仅在公共牌变少（新一手）或新增公共牌（numNew>0）时取消并重设定时器。
     */
    syncCommunityCardsFlipState(newCards) {
      var instantFlip = this.ecoMode || this.prefersReducedMotionForFlip()
      var prevLen = this.communityCardsFlipState.length
      if (newCards.length < prevLen) {
        if (this.communityCardsFlipCompleteTimer) {
          clearTimeout(this.communityCardsFlipCompleteTimer)
          this.communityCardsFlipCompleteTimer = null
        }
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_STATE', [])
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', false)
        prevLen = 0
      }
      var numNew = newCards.length - prevLen
      if (numNew > 0) {
        if (this.communityCardsFlipCompleteTimer) {
          clearTimeout(this.communityCardsFlipCompleteTimer)
          this.communityCardsFlipCompleteTimer = null
        }
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', false)
      }
      var flip = this.communityCardsFlipState.slice()
      for (var i = flip.length; i < newCards.length; i++) {
        flip.push(instantFlip)
      }
      if (flip.length !== this.communityCardsFlipState.length) {
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_STATE', flip)
      }
      if (numNew > 0 && instantFlip) {
        for (var k = 0; k < newCards.length; k++) {
          flip[k] = true
        }
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_STATE', flip)
        var selfInstant = this
        this.communityCardsFlipCompleteTimer = setTimeout(function () {
          selfInstant.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', true)
          selfInstant.communityCardsFlipCompleteTimer = null
        }, 180)
      } else if (numNew > 0) {
        for (var j = prevLen; j < newCards.length; j++) {
          var self = this
          ;(function (capturedIdx, capturedDelay) {
            setTimeout(function () {
              if (self.communityCardsFlipState.length > capturedIdx) {
                self.$store.commit('dpGame/SET_FLIP_AT', { index: capturedIdx, value: true })
              }
            }, capturedDelay)
          })(j, 520 + 350 * (j - prevLen))
        }
        var flipDuration = 480
        var lastFlipStart = 520 + 350 * (numNew - 1)
        var selfDone = this
        this.communityCardsFlipCompleteTimer = setTimeout(function () {
          selfDone.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', true)
          selfDone.communityCardsFlipCompleteTimer = null
        }, lastFlipStart + flipDuration)
      } else if (newCards.length > 0 && this.communityCardsFlipState.every(function (x) {
        return x
      })) {
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', true)
      }
    },

    /**
     * 开局发牌动画：从发牌位顺时针下一位起为 0，依次 1、2…（与常见首圈发牌顺序一致，仅用于错开飞入时间）
     */
    holeDealOrderFromDealer(seatIndex) {
      return holeDealOrderFromDealerUtil(seatIndex, this.players)
    },

    getPlayerBoxStyle(p, i) {
      return dpGamePlayerBoxStyle(p, i, {
        actIndex: this.actIndex,
        stage: this.stage,
        isOwner: this.isOwner,
        selectedWinners: this.selectedWinners,
        myNickname: this.user && this.user.nickname
      })
    }
  }
}
</script>
