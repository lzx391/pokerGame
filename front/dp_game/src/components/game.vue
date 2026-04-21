<template>
  <div
      ref="gameRoot"
      class="dp-game-root"
      :class="{
        'dp-game-root--pseudo-fs': pseudoFullscreen,
        'dp-game-root--layout-fs': layoutFullscreen,
        'dp-game-root--mobile-hero-dock': mobileHeroDockActive
      }"
      :data-dp-game-theme="effectiveThemeForCss"
      :style="customThemeInlineStyle"
      :data-dp-eco-mode="ecoMode ? 'true' : 'false'"
      :data-dp-stage="stage"
  >
    <!-- 新布局：顶栏 | 主区(仅此滚动圆桌) | 底栏(文档流占位，不再 position:fixed 遮挡桌面) -->
    <div class="dp-game-layout">
    <header class="dp-game-layout__header">
    <game-top-bar
        :room-id="roomId"
        :stage-label="stageCN"
        :pot="pot"
        :current-bet-to-call="currentBetToCall"
        :spectator-count="spectators.length"
        :is-fullscreen="layoutFullscreen"
        :show-spectator-prepare="showSpectatorPrepareBlock"
        :next-hand-ready="nextHandReady"
        :game-ui-theme="gameUiTheme"
        :custom-theme-base="customThemeBase"
        :custom-accent="customAccent"
        @update:gameUiTheme="$store.commit('dpGame/SET_GAME_UI_THEME', $event)"
        @update:customThemeBase="$store.commit('dpGame/SET_CUSTOM_THEME', { baseId: $event })"
        @update:customAccent="$store.commit('dpGame/SET_CUSTOM_THEME', { accent: $event })"
        :eco-mode="ecoMode"
        @update:ecoMode="$store.commit('dpGame/SET_ECO_MODE', $event)"
        :theme-options="gameThemeOptions"
        @show-hand-rank="$store.commit('dpGame/SET_MODAL', { showHandRankModal: true })"
        @show-spectators="$store.commit('dpGame/SET_MODAL', { showSpectatorModal: true })"
        @toggle-fullscreen="toggleDpFullscreen"
        @open-hand-history="openHandHistory"
        @open-music-box="$store.commit('dpGame/SET_MODAL', { showMusicBoxModal: true })"
        @exit="exitGame"
        @ready-next-hand="readyNextHand"
    />

    </header>

    <main class="dp-game-layout__main">
    <!-- <div v-if="playing" class="dp-game-hint">
      各人手牌与公共牌均由发牌位（D）发出
    </div> -->

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
        :hero-hole-deal-intro-done="heroHoleDealIntroDone"
        :show-hero-seat-on-table="showHeroSeatOnTable"
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
import { dpDisplayNickname } from '../utils/dpDisplayNickname'
import { playSettlementMusic, stopSettlementMusic } from '../utils/dpGameSettlementMusic'
import { musicFileSrc } from '../utils/dpGameMusicUrl'
import GameRoundTable from './GameRoundTable.vue'
import GameHeroDockFooter from './GameHeroDockFooter.vue'
import GameDpFloatingModals from './GameDpFloatingModals.vue'
import GameDpGameSheets from './GameDpGameSheets.vue'
import dpGameFullscreenMixin from '../mixins/dpGameFullscreenMixin'
import dpGameActionCountdownMixin from '../mixins/dpGameActionCountdownMixin'
import { dpGamePlayerBoxStyle } from '../utils/dpGamePlayerBoxStyle'
import { ensureDpUserIdInStorage } from '../utils/dpEnsureUserId'
import { mapState, mapGetters } from 'vuex'

export default {
  mixins: [dpGameFullscreenMixin, dpGameActionCountdownMixin],
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
      gameWs: null,
      gameWsConnected: false,
      pollTimer: null,
      backupPollTimer: null,
      heartbeatTimer: null,
      _seatChatTimers: null,
      _dpRoomClosedHandled: false,
      _lastRoomBgmUrl: '',
      _lastRoomMusicWebPath: '',
      _settlementMusicStartedForHand: null
    }
  },

  computed: {
    ...mapState('dpGame', [
      'gameUiTheme', 'customThemeBase', 'customAccent', 'ecoMode', 'gameThemeOptions', 'roomId', 'user', 'currentHandSeed', 'owner', 'players', 'playing', 'stage', 'communityCards', 'pot', 'pots', 'currentBetToCall', 'lastRaiseIncrement', 'actIndex', 'spectators', 'raiseAmount', 'selectedWinners', 'potWinners', 'nextHandReady', 'loading', 'communityCardsFlipState', 'communityCardsFlipComplete', 'seatChatTextByNick', 'chatInputDraft', 'showHandRankModal', 'showSpectatorModal', 'showHandHistoryModal', 'showMusicBoxModal', 'musicTracks', 'musicTracksLoading', 'musicTracksError', 'roomMusicState', 'showOwnerHubSheet', 'ownerToolType', 'ownerActionTarget', 'demoBotAdding', 'demoBotAddedTip', 'maniacBotAdding', 'maniacBotAddedTip', 'tagBotAdding', 'tagBotAddedTip', 'sharkBotAdding', 'sharkBotAddedTip', 'llmBotAdding', 'llmBotAddedTip', 'ownerRevealAll', 'showMobileHandSheet', 'showMobileActionSheet', 'heroHoleDealIntroDone', 'chipLeaderNicknames'
    ]),
    ...mapGetters('dpGame', [
      'effectiveThemeForCss', 'customThemeInlineStyle', 'handRankReference', 'stageCN', 'isOwner', 'isMyTurn', 'myPlayer', 'showSpectatorPrepareBlock', 'myReady', 'myChips', 'myBet', 'callAmount', 'smallBlind', 'bigBlind', 'lastRaiseIncrementEffective', 'minTotalToRaise', 'minRaise', 'allPotsHaveWinners', 'inSettledStage', 'ownerActionPlayers', 'playersDisplayOrder', 'viewerSeatedAtTable', 'holeDealPlayerCountForAnim', 'heroDockRow', 'dealerDisplayIndex', 'showdownHandLeaderNicknames', 'spectatorSeatChatEntries', 'tableActionActorDisplayName', 'mobileHeroDockActive', 'showHeroViewHandButton', 'showHeroSeatOnTable', 'showBottomHeroDock'
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

  created() {
    this._seatChatTimers = Object.create(null)
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
    try {
      var bgm = this.$refs.roomBgm
      if (bgm) {
        bgm.pause()
        bgm.removeAttribute('src')
      }
    } catch (e) { /* ignore */ }
    stopSettlementMusic()
    this.disconnectGameWs()
    if (this.pollTimer) clearInterval(this.pollTimer)
    if (this.backupPollTimer) clearInterval(this.backupPollTimer)
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    if (this.actionTimer) clearInterval(this.actionTimer)
    if (this.readyTimer) clearInterval(this.readyTimer)
    if (this.communityCardsFlipCompleteTimer) clearTimeout(this.communityCardsFlipCompleteTimer)
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
      // 与页面同源；开发时游戏 WS 走 vue.config.js 的 /dp-ws → 后端 /ws
      var secure = window.location.protocol === 'https:'
      return (secure ? 'wss:' : 'ws:') + '//' + window.location.host
    },

    connectGameWs() {
      this.disconnectGameWs()
      // 开发服：走 /dp-ws → vue 代理转成后端 /ws（避免与 webpack HMR 的 /ws 冲突）
      var path = process.env.NODE_ENV === 'development' ? '/dp-ws/dp-game' : '/ws/dp-game'
      var url = this.gameWsBaseUrl() + path + '?roomId=' + encodeURIComponent(this.roomId)
        + '&nickname=' + encodeURIComponent(this.user.nickname)
      try {
        var ws = new WebSocket(url)
        this.gameWs = ws
        var self = this
        ws.onopen = function () {
          self.gameWsConnected = true
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
          self.gameWsConnected = false
          if (self.gameWs === ws) self.gameWs = null
        }
        ws.onerror = function (e) {
          console.error('WebSocket 错误', e)
        }
      } catch (e) {
        console.error('WebSocket 连接失败', e)
      }
    },

    disconnectGameWs() {
      if (this.gameWs) {
        try {
          this.gameWs.close()
        } catch (e) { /* ignore */ }
        this.gameWs = null
      }
      this.gameWsConnected = false
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
      this.disconnectGameWs()
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
          error: '曲库列表加载失败，请确认后端与 dp_music_track 已就绪。'
        })
      }
    },

    pushRoomChatFromServer(data) {
      var nick = (data.nickname || '').trim()
      var text = (data.text != null ? String(data.text) : '').trim()
      if (!nick || !text) return
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
      this.$store.commit('dpGame/APPLY_ROOM', room)
      this.syncCommunityCardsFlipState(room.communityCards || [])
      this.$nextTick(function () {
        this.syncActionCountdown()
        if (this.isMyTurn && this.raiseAmount < this.minRaise) {
          this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
        }
      }.bind(this))
    },

    /**
     * 摊牌/准备下一局阶段播放结算 BGM；进入新一手（preflop）或非结算街时停止。
     */
    // syncSettlementMusic() {
    //   if (!this.playing) {
    //     stopSettlementMusic()
    //     this.syncRoomBgmAudio()
    //     return
    //   }
    //   var st = this.stage
    //   var seed = this.currentHandSeed
    //   if (st === 'showdown' || st === 'settled') {
    //     if (this._settlementMusicStartedForHand !== seed) {
    //       this._settlementMusicStartedForHand = seed
    //       playSettlementMusic()
    //     }
    //   } else {
    //     stopSettlementMusic()
    //   }
    //   this.syncRoomBgmAudio()
    // },

    // ---- 拉取房间状态 ----
    async loadGame() {
      this.$store.commit('dpGame/SET_LOADING', true)
      try {
        var res = await this.$http.get('/dpRoom/getNowRoom', {
          params: {roomId: this.roomId, nickname: this.user ? this.user.nickname : ''}
        })
        var room = res.data
        if (!room) {
          this.handleRoomClosedFromServer()
          return
        }
        this.applyRoomFromServer(room)
      } catch (err) {
        console.error('拉取状态失败', err)
      } finally {
        this.$store.commit('dpGame/SET_LOADING', false)
      }
    },

    // ---- 准备/取消准备 ----
    async toggleReady() {
      try {
        var res = await this.$http.post('/dpRoom/toggleReady', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') this.$message.error('操作失败')
        await this.loadGame()
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

    // ---- 摊牌阶段：点击玩家卡片选/取消赢家（简单模式备用） ----
    handleJudgeClick(nickname) {
      if (!this.isOwner || this.stage !== 'showdown') return
      // 有边池数据时，不用这个旧的点击方式
      if (this.pots.length > 0) return

      this.$store.commit('dpGame/TOGGLE_SELECTED_WINNER', nickname)
    },

    // 统一的玩家卡片点击入口：仅用于摊牌选赢家（房主神器不再通过点卡片）
    onPlayerCardClick(nickname) {
      this.handleJudgeClick(nickname)
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
          this.$message.warning('第 ' + (i === 0 ? '主' : i) + ' 池还没选赢家')
          return
        }
        parts.push(i + ':' + winners.join(','))
      }
      var potWinnersStr = parts.join(';')

      // 组装确认信息（HTML 换行，避免原生 confirm 打断全屏）
      var lines = ['确认结算？']
      for (var j = 0; j < this.pots.length; j++) {
        var potName = j === 0 ? '主池' : '边池 ' + j
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

    // ---- 房主：踢人到观众席（通过弹窗选择玩家） ----
    async doKickPlayer() {
      if (!this.ownerActionTarget) {
        this.$message.warning('请先选择要踢出的玩家')
        return
      }
      try {
        await this.dpConfirm(
          '确定将 [' + dpDisplayNickname(this.ownerActionTarget) + '] 踢出本局并移至观众席吗？',
          '踢出玩家'
        )
      } catch (e) {
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/kickPlayer', null, {
          params: {roomId: this.roomId, nickname: this.ownerActionTarget}
        })
        if (res.data !== 'ok') {
          this.$message.error('踢人失败：' + res.data)
        } else {
          this.$message.success('已将 [' + dpDisplayNickname(this.ownerActionTarget) + '] 踢至观众席')
        }
        await this.loadGame()
        this.closeOwnerHubPanel()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    /**
     * 将 DEMO 型 NPC（服务端昵称为 BOT_Fish，界面展示为 BOT_Lag）加入下一局等待列表。
     * 当前用于基础难度练习与流程验证。
     */
    async addDemoBot() {
      if (!this.roomId) return
      this.$store.commit('dpGame/SET_BOT_STATE', { demoBotAdding: true, demoBotAddedTip: '' })
      try {
        var res = await this.$http.post('/dpRoom/addDemoBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/SET_BOT_STATE', {
            demoBotAddedTip: '已请求在下一局加入 BOT_Lag，请等待本局结束后自动入座。'
          })
        } else {
          this.$store.commit('dpGame/SET_BOT_STATE', { demoBotAddedTip: '添加 NPC 失败：' + res.data })
        }
      } catch (e) {
        this.$store.commit('dpGame/SET_BOT_STATE', {
          demoBotAddedTip: '网络错误：' + (e && e.message ? e.message : e)
        })
      } finally {
        this.$store.commit('dpGame/SET_BOT_STATE', { demoBotAdding: false })
      }
    },

    /**
     * 将疯子型 NPC（BOT_Maniac）加入下一局等待列表。
     */
    async addManiacBot() {
      if (!this.roomId) return
      this.$store.commit('dpGame/SET_BOT_STATE', { maniacBotAdding: true, maniacBotAddedTip: '' })
      try {
        var res = await this.$http.post('/dpRoom/addManiacBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/SET_BOT_STATE', {
            maniacBotAddedTip: '已请求在下一局加入 BOT_Maniac，请等待本局结束后自动入座。'
          })
        } else {
          this.$store.commit('dpGame/SET_BOT_STATE', {
            maniacBotAddedTip: '添加疯子 NPC 失败：' + res.data
          })
        }
      } catch (e) {
        this.$store.commit('dpGame/SET_BOT_STATE', {
          maniacBotAddedTip: '网络错误：' + (e && e.message ? e.message : e)
        })
      } finally {
        this.$store.commit('dpGame/SET_BOT_STATE', { maniacBotAdding: false })
      }
    },

    /**
     * 将紧凶型 NPC（BOT_Tag）加入下一局等待列表。
     * 该机器人打得相对紧凶，但不会像 Shark 那样根据对手历史动态调整策略。
     */
    async addTagBot() {
      if (!this.roomId) return
      this.$store.commit('dpGame/SET_BOT_STATE', { tagBotAdding: true, tagBotAddedTip: '' })
      try {
        var res = await this.$http.post('/dpRoom/addTagBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/SET_BOT_STATE', {
            tagBotAddedTip: '已请求在下一局加入 BOT_Tag，请等待本局结束后自动入座。'
          })
        } else {
          this.$store.commit('dpGame/SET_BOT_STATE', {
            tagBotAddedTip: '添加紧凶 NPC 失败：' + res.data
          })
        }
      } catch (e) {
        this.$store.commit('dpGame/SET_BOT_STATE', {
          tagBotAddedTip: '网络错误：' + (e && e.message ? e.message : e)
        })
      } finally {
        this.$store.commit('dpGame/SET_BOT_STATE', { tagBotAdding: false })
      }
    },

    /**
     * 将聪明型 NPC（BOT_Shark）加入下一局等待列表。
     * 该机器人会根据对手最近几手的行为粗略判断其风格，调整自己的盖牌/跟投/加投倾向。
     */
    async addSharkBot() {
      if (!this.roomId) return
      this.$store.commit('dpGame/SET_BOT_STATE', { sharkBotAdding: true, sharkBotAddedTip: '' })
      try {
        var res = await this.$http.post('/dpRoom/addSharkBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/SET_BOT_STATE', {
            sharkBotAddedTip: '已请求在下一局加入 BOT_Shark，请等待本局结束后自动入座。'
          })
        } else {
          this.$store.commit('dpGame/SET_BOT_STATE', {
            sharkBotAddedTip: '添加聪明 NPC 失败：' + res.data
          })
        }
      } catch (e) {
        this.$store.commit('dpGame/SET_BOT_STATE', {
          sharkBotAddedTip: '网络错误：' + (e && e.message ? e.message : e)
        })
      } finally {
        this.$store.commit('dpGame/SET_BOT_STATE', { sharkBotAdding: false })
      }
    },

    /**
     * 将大模型 NPC（BOT_LLM）加入下一局等待列表（后端 /dpRoom/addLlmBot）。
     */
    async addLlmBot() {
      if (!this.roomId) return
      this.$store.commit('dpGame/SET_BOT_STATE', { llmBotAdding: true, llmBotAddedTip: '' })
      try {
        var res = await this.$http.post('/dpRoom/addLlmBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/SET_BOT_STATE', {
            llmBotAddedTip:
              '已请求在下一局加入 BOT_LLM，请等待本局结束后自动入座（需配置服务端方舟密钥）。'
          })
        } else {
          this.$store.commit('dpGame/SET_BOT_STATE', {
            llmBotAddedTip: '添加大模型 NPC 失败：' + res.data
          })
        }
      } catch (e) {
        this.$store.commit('dpGame/SET_BOT_STATE', {
          llmBotAddedTip: '网络错误：' + (e && e.message ? e.message : e)
        })
      } finally {
        this.$store.commit('dpGame/SET_BOT_STATE', { llmBotAdding: false })
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
      try {
        await this.$http.post('/dpRoom/exitRoom', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
      } catch (err) {
        console.error('退出失败', err)
      }
      clearInterval(this.pollTimer)
      clearInterval(this.heartbeatTimer)
      this.navigateHomeIfNeeded()
    },

    // ---- 观众：报名在下一局加入 ----
    async readyNextHand() {
      if (!this.user) return
      try {
        var rp = { roomId: this.roomId, nickname: this.user.nickname }
        if (this.user.userId != null && this.user.userId !== '') {
          rp.userId = this.user.userId
        }
        var res = await this.$http.post('/dpRoom/readyNextHand', null, {
          params: rp
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/SET_NEXT_HAND_READY', true)
          this.$message.success('已报名下一局，将在下一局开局时自动加入对局')
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
        flip.push(false)
      }
      if (flip.length !== this.communityCardsFlipState.length) {
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_STATE', flip)
      }
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
      if (numNew > 0) {
        var flipDuration = 800
        var lastFlipStart = 520 + 350 * (numNew - 1)
        var self = this
        this.communityCardsFlipCompleteTimer = setTimeout(function () {
          self.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', true)
          self.communityCardsFlipCompleteTimer = null
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
