<template>
  <div class="dp-game-root" :data-dp-game-theme="gameUiTheme">
    <div class="dp-game-theme-row">
      <span class="dp-game-theme-row__label">界面主题</span>
      <select v-model="gameUiTheme" class="dp-game-theme-select" aria-label="选择对局界面主题">
        <option v-for="t in gameThemeOptions" :key="t.id" :value="t.id">{{ t.label }}</option>
      </select>
    </div>

    <game-top-bar
        :room-id="roomId"
        :stage-label="stageCN"
        :pot="pot"
        :current-bet-to-call="currentBetToCall"
        :spectator-count="spectators.length"
        @show-hand-rank="showHandRankModal = true"
        @show-spectators="showSpectatorModal = true"
        @exit="exitGame"
    />

    <game-hand-rank-modal
        :visible="showHandRankModal"
        :items="handRankReference"
        @close="showHandRankModal = false"
    />

    <game-spectator-modal
        :visible="showSpectatorModal"
        :spectators="spectators"
        @close="showSpectatorModal = false"
    />

    <game-owner-tool-modal
        :visible="showOwnerToolModal"
        :owner-tool-type.sync="ownerToolType"
        :owner-action-target.sync="ownerActionTarget"
        :owner-action-players="ownerActionPlayers"
        :demo-bot-adding="demoBotAdding"
        :demo-bot-added-tip="demoBotAddedTip"
        :maniac-bot-adding="maniacBotAdding"
        :maniac-bot-added-tip="maniacBotAddedTip"
        :tag-bot-adding="tagBotAdding"
        :tag-bot-added-tip="tagBotAddedTip"
        :shark-bot-adding="sharkBotAdding"
        :shark-bot-added-tip="sharkBotAddedTip"
        :llm-bot-adding="llmBotAdding"
        :llm-bot-added-tip="llmBotAddedTip"
        @close="closeOwnerToolPanel"
        @add-demo-bot="addDemoBot"
        @add-maniac-bot="addManiacBot"
        @add-tag-bot="addTagBot"
        @add-shark-bot="addSharkBot"
        @add-llm-bot="addLlmBot"
        @transfer-owner="doTransferOwner"
        @kick-player="doKickPlayer"
    />

    <game-spectator-prepare-banner
        v-if="showSpectatorPrepareBlock"
        :next-hand-ready="nextHandReady"
        @ready-next-hand="readyNextHand"
    />

    <div v-if="playing" class="dp-game-hint">
      各人手牌与公共牌均由庄位（D）发出
    </div>
    <game-community-cards
        :community-cards="communityCards"
        :flip-state="communityCardsFlipState"
    />

    <!-- 玩家列表：本机视角将自己排到网格首行，其余座位相对顺序不变；seatIndex 仍为服务端座位下标 -->
    <div class="dp-game-players-grid">
      <game-player-card
          v-for="row in playersDisplayOrder"
          :key="(row.player.leftThisHand ? 'offline-' + row.seatIndex : row.player.nickname)"
          :player="row.player"
          :seat-index="row.seatIndex"
          :box-style="getPlayerBoxStyle(row.player, row.seatIndex)"
          :act-index="actIndex"
          :stage="stage"
          :community-cards="communityCards"
          :community-cards-flip-complete="communityCardsFlipComplete"
          :is-owner="isOwner"
          :owner-reveal-all="ownerRevealAll"
          :my-nickname="user ? user.nickname : ''"
          :hand-deal-key="currentHandSeed"
          @card-click="onPlayerCardClick"
      />
    </div>

    <game-settled-prepare-panel
        v-if="inSettledStage"
        :ready-time-left="readyTimeLeft"
        :my-chips="myChips"
        :big-blind="bigBlind"
        :my-ready="myReady"
        @toggle-ready="toggleReady"
        @rebuy="rebuy"
    />

    <game-action-panel
        v-if="isMyTurn"
        :time-left="timeLeft"
        :current-bet-to-call="currentBetToCall"
        :my-bet="myBet"
        :call-amount="callAmount"
        :small-blind="smallBlind"
        :big-blind="bigBlind"
        :min-raise="minRaise"
        :my-chips="myChips"
        :raise-amount.sync="raiseAmount"
        @call="doCall"
        @raise="doRaise"
        @all-in="doAllIn"
        @fold="doFold"
    />

    <game-owner-panel
        v-if="isOwner"
        :owner-reveal-all.sync="ownerRevealAll"
        :stage="stage"
        :pots="pots"
        :pot="pot"
        :pot-winners="potWinners"
        :selected-winners="selectedWinners"
        :all-pots-have-winners="allPotsHaveWinners"
        @open-owner-tools="openOwnerToolPanel"
        @toggle-pot-winner="onTogglePotWinnerPayload"
        @confirm-pot-judge="confirmPotJudge"
        @confirm-judge-win="confirmJudgeWin"
    />

  </div>
</template>

<script>
import '../styles/dp-game-themes.css'
import '../styles/dp-game-shell.css'
import { GAME_UI_THEMES } from '../constants/dpGameThemes'
import { readGameTheme, writeGameTheme } from '../utils/dpGameTheme'
import GamePlayerCard from './GamePlayerCard.vue'
import GameTopBar from './GameTopBar.vue'
import GameHandRankModal from './GameHandRankModal.vue'
import GameSpectatorModal from './GameSpectatorModal.vue'
import GameOwnerToolModal from './GameOwnerToolModal.vue'
import GameSpectatorPrepareBanner from './GameSpectatorPrepareBanner.vue'
import GameCommunityCards from './GameCommunityCards.vue'
import GameSettledPreparePanel from './GameSettledPreparePanel.vue'
import GameActionPanel from './GameActionPanel.vue'
import GameOwnerPanel from './GameOwnerPanel.vue'
import { HAND_RANK_REFERENCE } from '../constants/dpGameHandRankReference'

export default {
  components: {
    GamePlayerCard,
    GameTopBar,
    GameHandRankModal,
    GameSpectatorModal,
    GameOwnerToolModal,
    GameSpectatorPrepareBanner,
    GameCommunityCards,
    GameSettledPreparePanel,
    GameActionPanel,
    GameOwnerPanel
  },
  data() {
    return {
      gameUiTheme: readGameTheme(),
      gameThemeOptions: GAME_UI_THEMES,
      roomId: '',
      user: null,

      // 房间数据（对应后端 DpRoom 字段）
      /** 每手牌唯一，用于前端手牌「庄位发牌」动画（与 newHand 时 currentHandSeed 一致） */
      currentHandSeed: 0,
      owner: '',
      players: [],
      playing: false,
      stage: 'preflop',
      communityCards: [],
      pot: 0,
      pots: [],             // 主池+边池列表 [{amount, eligiblePlayers}]
      currentBetToCall: 0,
      actIndex: -1,
      // 观众席名单（由后端 DpRoom.spectators 提供）
      spectators: [],

      // UI
      raiseAmount: 0,
      selectedWinners: [],   // 旧的简单模式备用
      potWinners: {},        // 按池选赢家 { 0: ['Alice'], 1: ['Bob','Charlie'] }
      nextHandReady: false,  // 是否已报名下一局加入
      loading: false,

      // 公共牌翻转动画：每个下标 true=已翻开，false=未翻开
      communityCardsFlipState: [],
      // 公共牌全部翻完后再显示牌型，增强沉浸感
      communityCardsFlipComplete: false,
      communityCardsFlipCompleteTimer: null,

      // 游戏对局 WebSocket（与后端 /ws/dp-game 同步，无 Redis）
      gameWs: null,
      gameWsConnected: false,

      // 定时器
      pollTimer: null,
      backupPollTimer: null,
      heartbeatTimer: null,
      //游戏计时器
      actionTimer: null,
      timeLeft: 30,
      // 结算后准备阶段倒计时
      readyTimer: null,
      readyTimeLeft: 30,

      // 牌型说明弹窗
      showHandRankModal: false,
      showSpectatorModal: false,
      // 房主踢人/移交房主弹窗
      showOwnerToolModal: false,
      ownerToolType: 'transfer',  // 'transfer' | 'kick'
      ownerActionTarget: '',      // 当前选择的目标玩家昵称
      // 演示用 NPC 状态（仅前端提示用）
      demoBotAdding: false,
      demoBotAddedTip: '',
      // 疯子型 NPC 状态
      maniacBotAdding: false,
      maniacBotAddedTip: '',
      // 紧凶型 NPC 状态
      tagBotAdding: false,
      tagBotAddedTip: '',
      // 聪明型 NPC 状态
      sharkBotAdding: false,
      sharkBotAddedTip: '',
      // 大模型 NPC 状态
      llmBotAdding: false,
      llmBotAddedTip: '',

      // 房主专用：一键看穿所有人底牌（仅本机显示，不影响后端和 NPC 决策）
      ownerRevealAll: false
    }
  },

  computed: {
    handRankReference() {
      return HAND_RANK_REFERENCE
    },
    stageCN() {
      var m = {preflop: '翻牌前', flop: '翻牌圈', turn: '转牌圈', river: '河牌圈', showdown: '摊牌结算', settled: '准备下一局'}
      return m[this.stage] || this.stage
    },
    isOwner() {
      return this.user && this.owner === this.user.nickname
    },
    isMyTurn() {
      if (!this.user || this.actIndex < 0 || this.actIndex >= this.players.length) return false
      return this.players[this.actIndex].nickname === this.user.nickname
    },
    myPlayer() {
      if (!this.user) return null
      return this.players.find(function (p) {
        return p.nickname === this.user.nickname
      }.bind(this)) || null
    },
    // 是否显示“准备在下一局加入对局”：纯观众 或 本手已退出的僵尸位（退了又进来）都显示，便于立刻报名下一局
    showSpectatorPrepareBlock() {
      if (!this.myPlayer) return true
      return !!this.myPlayer.leftThisHand
    },
    myReady() {
      return this.myPlayer ? this.myPlayer.ready : false
    },
    myChips() {
      return this.myPlayer ? this.myPlayer.chips : 0
    },
    myBet() {
      return this.myPlayer ? this.myPlayer.bet : 0
    },
    callAmount() {
      return Math.max(0, this.currentBetToCall - this.myBet)
    },
    smallBlind() {
      // 使用与后端一致的小盲配置，默认 5，后续可从服务端房间配置透传
      return 5
    },
    bigBlind() {
      // 使用与后端一致的大盲配置，默认 10，后续可从服务端房间配置透传
      return 10
    },
    minRaise() {
      return this.callAmount + this.bigBlind
    },
    allPotsHaveWinners() {
      if (this.pots.length === 0) return false
      for (var i = 0; i < this.pots.length; i++) {
        if (!this.potWinners[i] || this.potWinners[i].length === 0) return false
      }
      return true
    },
    // 当前是否处于“结算完成，等待准备下一局”阶段（僵尸位不算，他们走观众区的“准备在下一局加入对局”）
    inSettledStage() {
      return this.stage === 'settled' && !!this.myPlayer && !this.myPlayer.leftThisHand
    },
    // 房主神器中可操作的玩家列表：排除房主自己和僵尸位
    ownerActionPlayers() {
      return this.players.filter(function (p) {
        return !p.leftThisHand && p.nickname !== this.owner
      }.bind(this))
    },
    /**
     * 网格展示顺序：从「自己」起按座位顺时针展开（与 players 数组顺序一致，只是旋转起点）。
     * seatIndex 仍为原数组下标，供 actIndex、庄位发牌动画等与后端一致。
     */
    playersDisplayOrder() {
      var list = this.players
      if (!list || list.length === 0) return []
      var myNick = this.user && this.user.nickname
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
    }
  },

  watch: {
    gameUiTheme: function (id) {
      writeGameTheme(id)
    },
    isMyTurn: function (v) {
      if (v) this.raiseAmount = this.minRaise
    },
// 监听当前行动者的索引变化
    actIndex(newVal) {
      // 获取当前轮到的那个人
      const currentPlayer = this.players[newVal];

      // 如果这个人存在，且名字是我自己（守卫 user 未加载）
      if (this.user && currentPlayer && currentPlayer.nickname === this.user.nickname) {
        this.startCountdown();
      } else {
        this.stopCountdown();
      }
    },
    // 监听阶段变化，用于控制结算后准备阶段的倒计时
    stage(newVal) {
      if (newVal === 'settled') {
        this.startReadyCountdown()
      } else {
        this.stopReadyCountdown()
      }
    }
  },

  created() {
    this.roomId = this.$route.params.roomId

    var raw = localStorage.getItem('userInfo')
    if (!raw) {
      alert('登录信息丢失，请重新登录')
      this.$router.push('/login')
      return
    }
    this.user = JSON.parse(raw)

    // 先 HTTP 拉一次，再建立 WebSocket（推送与定时器同 1s 节奏）
    this.loadGame().then(function () {
      this.connectGameWs()
    }.bind(this))

    // 未连上 WS 时 1 秒轮询兜底；握手过程中 readyState===CONNECTING 也要停掉，否则会连着打一串 getNowRoom
    this.pollTimer = setInterval(function () {
      if (this.loading) return
      if (this.gameWsConnected) return
      if (this.gameWs && this.gameWs.readyState === WebSocket.CONNECTING) return
      this.loadGame()
    }.bind(this), 1000)

    // 已连上 WS 时低频 HTTP 兜底（防止长连异常而界面停滞）
    this.backupPollTimer = setInterval(function () {
      if (!this.loading && this.gameWsConnected) this.loadGame()
    }.bind(this), 15000)

    // 5秒独立心跳（和 loadGame 解耦，loadGame 失败不影响心跳）
    this.sendHeartbeat()
    this.heartbeatTimer = setInterval(function () {
      this.sendHeartbeat()
    }.bind(this), 5000)
  },

  beforeDestroy() {
    this.disconnectGameWs()
    if (this.pollTimer) clearInterval(this.pollTimer)
    if (this.backupPollTimer) clearInterval(this.backupPollTimer)
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    if (this.actionTimer) clearInterval(this.actionTimer)
    if (this.readyTimer) clearInterval(this.readyTimer)
    if (this.communityCardsFlipCompleteTimer) clearTimeout(this.communityCardsFlipCompleteTimer)
  },

  methods: {
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
      alert('房间已解散或你已被移出')
      this.disconnectGameWs()
      if (this.pollTimer) clearInterval(this.pollTimer)
      if (this.backupPollTimer) clearInterval(this.backupPollTimer)
      if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
      this.$router.push('/home')
    },

    applyRoomFromServer(room) {
      this.owner = room.owner
      this.players = room.players || []
      this.playing = room.playing
      this.currentHandSeed = room.currentHandSeed != null ? room.currentHandSeed : 0
      this.stage = room.currentStage
      this.communityCards = room.communityCards || []
      this.syncCommunityCardsFlipState(room.communityCards || [])
      this.pot = room.pot
      this.pots = room.pots || []
      this.currentBetToCall = room.currentBetToCall
      this.actIndex = room.currentActorIndex
      this.spectators = room.spectators || []
      var list = room.waitNextHand || []
      this.nextHandReady = !!(this.user && list.indexOf(this.user.nickname) !== -1)
    },

    // ---- 拉取房间状态 ----
    async loadGame() {
      this.loading = true
      try {
        var res = await this.$http.get('/dpRoom/getNowRoom', {
          params: {roomId: this.roomId}
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
        this.loading = false
      }
    },

    // ---- 准备/取消准备 ----
    async toggleReady() {
      try {
        var res = await this.$http.post('/dpRoom/toggleReady', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') alert('操作失败')
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },
    // 结算后筹码归零时补码
    async rebuy() {
      try {
        var res = await this.$http.post('/dpRoom/rebuy', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') {
          alert('补码失败：' + res.data)
        } else {
          alert('补码成功，可以准备下一局了')
        }
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 跟注/过牌 ----
    async doCall() {
      await this.submitBet(this.callAmount)
    },

    // ---- 加注 ----
    async doRaise() {
      if (this.raiseAmount < this.minRaise) {
        alert('加注额不能低于 ' + this.minRaise)
        return
      }
      if (this.raiseAmount > this.myChips) {
        alert('筹码不足！')
        return
      }
      await this.submitBet(this.raiseAmount)
    },

    // ---- All-In ----
    async doAllIn() {
      await this.submitBet(this.myChips)
    },

    // ---- 统一提交下注 ----
    async submitBet(amount) {
      try {
        var res = await this.$http.post('/dpRoom/bet', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname, bet: amount}
        })
        if (res.data !== 'ok') alert('下注失败，请检查金额')
        this.raiseAmount = 0
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 弃牌 ----
    async doFold() {
      try {
        var res = await this.$http.post('/dpRoom/fold', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') alert('弃牌失败')
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 摊牌阶段：点击玩家卡片选/取消赢家（简单模式备用） ----
    handleJudgeClick(nickname) {
      if (!this.isOwner || this.stage !== 'showdown') return
      // 有边池数据时，不用这个旧的点击方式
      if (this.pots.length > 0) return

      var idx = this.selectedWinners.indexOf(nickname)
      if (idx > -1) {
        this.selectedWinners.splice(idx, 1)
      } else {
        this.selectedWinners.push(nickname)
      }
    },

    // 统一的玩家卡片点击入口：仅用于摊牌选赢家（房主神器不再通过点卡片）
    onPlayerCardClick(nickname) {
      this.handleJudgeClick(nickname)
    },

    // ---- 按池选赢家 ----
    togglePotWinner(potIndex, nickname) {
      var winners = this.potWinners[potIndex] || []
      var idx = winners.indexOf(nickname)
      if (idx > -1) {
        winners.splice(idx, 1)
      } else {
        winners.push(nickname)
      }
      this.$set(this.potWinners, potIndex, winners)
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
          alert('第 ' + (i === 0 ? '主' : i) + ' 池还没选赢家')
          return
        }
        parts.push(i + ':' + winners.join(','))
      }
      var potWinnersStr = parts.join(';')

      // 组装确认信息
      var msg = '确认结算？\n'
      for (var j = 0; j < this.pots.length; j++) {
        var potName = j === 0 ? '主池' : '边池 ' + j
        msg += potName + '(' + this.pots[j].amount + ') -> ' + (this.potWinners[j] || []).join(', ') + '\n'
      }
      if (!confirm(msg)) return

      try {
        var res = await this.$http.post('/dpRoom/judgeWin', null, {
          params: {roomId: this.roomId, potWinners: potWinnersStr}
        })
        if (res.data !== 'ok') alert('结算失败')
        this.potWinners = {}
        this.selectedWinners = []
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 房主：确认结算 ----
    async confirmJudgeWin() {
      if (this.selectedWinners.length === 0) {
        alert('请至少选择一位赢家')
        return
      }
      var names = this.selectedWinners.join(', ')
      if (!confirm('确定由 [' + names + '] 平分底池 ' + this.pot + ' 吗？')) return

      try {
        var res = await this.$http.post('/dpRoom/judgeWin', null, {
          params: {roomId: this.roomId, winnerNickname: this.selectedWinners.join(',')}
        })
        if (res.data !== 'ok') alert('结算失败')
        this.selectedWinners = []
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 房主神器：打开/关闭 ----
    openOwnerToolPanel() {
      this.ownerToolType = 'transfer'
      this.ownerActionTarget = ''
      this.showOwnerToolModal = true
      this.demoBotAddedTip = ''
      this.maniacBotAddedTip = ''
      this.tagBotAddedTip = ''
      this.sharkBotAddedTip = ''
    },

    closeOwnerToolPanel() {
      this.showOwnerToolModal = false
      this.ownerActionTarget = ''
    },

    // ---- 房主：移交房主（通过弹窗选择玩家） ----
    async doTransferOwner() {
      if (!this.ownerActionTarget) {
        alert('请先选择要移交房主的玩家')
        return
      }
      if (this.ownerActionTarget === this.user.nickname) {
        alert('不能把房主移交给自己')
        return
      }
      var ok = confirm('确定将房主移交给 [' + this.ownerActionTarget + '] 吗？')
      if (!ok) {
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
          alert('移交失败：' + res.data)
        } else {
          alert('已将房主移交给 ' + this.ownerActionTarget)
        }
        await this.loadGame()
        this.closeOwnerToolPanel()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 房主：踢人到观众席（通过弹窗选择玩家） ----
    async doKickPlayer() {
      if (!this.ownerActionTarget) {
        alert('请先选择要踢出的玩家')
        return
      }
      if (!confirm('确定将 [' + this.ownerActionTarget + '] 踢出本局并移至观众席吗？')) {
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/kickPlayer', null, {
          params: {roomId: this.roomId, nickname: this.ownerActionTarget}
        })
        if (res.data !== 'ok') {
          alert('踢人失败：' + res.data)
        } else {
          alert('已将 [' + this.ownerActionTarget + '] 踢至观众席')
        }
        await this.loadGame()
        this.closeOwnerToolPanel()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    /**
     * 将简单鱼式 NPC（BOT_Fish，原 BOT_Demo）加入下一局等待列表。
     * 当前用于基础难度练习与流程验证。
     */
    async addDemoBot() {
      if (!this.roomId) return
      this.demoBotAdding = true
      this.demoBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addDemoBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.demoBotAddedTip = '已请求在下一局加入 BOT_Fish，请等待本局结束后自动入座。'
        } else {
          this.demoBotAddedTip = '添加 NPC 失败：' + res.data
        }
      } catch (e) {
        this.demoBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.demoBotAdding = false
      }
    },

    /**
     * 将疯子型 NPC（BOT_Maniac）加入下一局等待列表。
     */
    async addManiacBot() {
      if (!this.roomId) return
      this.maniacBotAdding = true
      this.maniacBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addManiacBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.maniacBotAddedTip = '已请求在下一局加入 BOT_Maniac，请等待本局结束后自动入座。'
        } else {
          this.maniacBotAddedTip = '添加疯子 NPC 失败：' + res.data
        }
      } catch (e) {
        this.maniacBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.maniacBotAdding = false
      }
    },

    /**
     * 将紧凶型 NPC（BOT_Tag）加入下一局等待列表。
     * 该机器人打得相对紧凶，但不会像 Shark 那样根据对手历史动态调整策略。
     */
    async addTagBot() {
      if (!this.roomId) return
      this.tagBotAdding = true
      this.tagBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addTagBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.tagBotAddedTip = '已请求在下一局加入 BOT_Tag，请等待本局结束后自动入座。'
        } else {
          this.tagBotAddedTip = '添加紧凶 NPC 失败：' + res.data
        }
      } catch (e) {
        this.tagBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.tagBotAdding = false
      }
    },

    /**
     * 将聪明型 NPC（BOT_Shark）加入下一局等待列表。
     * 该机器人会根据对手最近几手的行为粗略判断其风格，调整自己的弃牌/跟注/加注倾向。
     */
    async addSharkBot() {
      if (!this.roomId) return
      this.sharkBotAdding = true
      this.sharkBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addSharkBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.sharkBotAddedTip = '已请求在下一局加入 BOT_Shark，请等待本局结束后自动入座。'
        } else {
          this.sharkBotAddedTip = '添加聪明 NPC 失败：' + res.data
        }
      } catch (e) {
        this.sharkBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.sharkBotAdding = false
      }
    },

    /**
     * 将大模型 NPC（BOT_LLM）加入下一局等待列表（后端 /dpRoom/addLlmBot）。
     */
    async addLlmBot() {
      if (!this.roomId) return
      this.llmBotAdding = true
      this.llmBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addLlmBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.llmBotAddedTip = '已请求在下一局加入 BOT_LLM，请等待本局结束后自动入座（需配置服务端方舟密钥）。'
        } else {
          this.llmBotAddedTip = '添加大模型 NPC 失败：' + res.data
        }
      } catch (e) {
        this.llmBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.llmBotAdding = false
      }
    },

    // ---- 退出 ----
    async exitGame() {
      if (!confirm('确定退出对局？')) return
      try {
        await this.$http.post('/dpRoom/exitRoom', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
      } catch (err) {
        console.error('退出失败', err)
      }
      clearInterval(this.pollTimer)
      clearInterval(this.heartbeatTimer)
      this.$router.push('/home')
    },

    // ---- 观众：报名在下一局加入 ----
    async readyNextHand() {
      if (!this.user) return
      try {
        var res = await this.$http.post('/dpRoom/readyNextHand', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data === 'ok') {
          this.nextHandReady = true
          alert('已报名下一局，将在下一局开局时自动加入对局')
        } else {
          alert('报名失败：' + res.data)
        }
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    /**
     * 同步公共牌翻转状态：新牌先背面，再依次翻转；翻完后再允许显示牌型
     */
    syncCommunityCardsFlipState(newCards) {
      if (this.communityCardsFlipCompleteTimer) {
        clearTimeout(this.communityCardsFlipCompleteTimer)
        this.communityCardsFlipCompleteTimer = null
      }
      var prevLen = this.communityCardsFlipState.length
      if (newCards.length < prevLen) {
        this.communityCardsFlipState = []
        this.communityCardsFlipComplete = false
        prevLen = 0
      }
      var numNew = newCards.length - prevLen
      if (numNew > 0) {
        this.communityCardsFlipComplete = false
      }
      for (var i = this.communityCardsFlipState.length; i < newCards.length; i++) {
        this.communityCardsFlipState.push(false)
      }
      for (var j = prevLen; j < newCards.length; j++) {
        var self = this
        ;(function (capturedIdx, capturedDelay) {
          setTimeout(function () {
            if (self.communityCardsFlipState.length > capturedIdx) {
              self.$set(self.communityCardsFlipState, capturedIdx, true)
            }
          }, capturedDelay)
        })(j, 520 + 350 * (j - prevLen))
      }
      if (numNew > 0) {
        var flipDuration = 800
        /* 等对应公共牌飞入动画（约 0.48s）后再翻牌，与 GameCommunityCards 发牌间隔一致 */
        var lastFlipStart = 520 + 350 * (numNew - 1)
        var self = this
        this.communityCardsFlipCompleteTimer = setTimeout(function () {
          self.communityCardsFlipComplete = true
          self.communityCardsFlipCompleteTimer = null
        }, lastFlipStart + flipDuration)
      } else if (newCards.length > 0 && this.communityCardsFlipState.every(function (x) {
        return x
      })) {
        this.communityCardsFlipComplete = true
      }
    },

    // ---- 工具方法 ----
    isMe(nickname) {
      return this.user && this.user.nickname === nickname
    },

    getPlayerBoxStyle(p, i) {
      var s = {
        background: 'var(--dp-player-card-bg)',
        padding: '12px',
        borderRadius: '10px',
        border: '2px solid transparent',
        transition: 'all 0.2s'
      }

      // 离线位：灰显，不参与行动高亮
      if (p.leftThisHand) {
        s.background = 'var(--dp-player-card-offline-bg)'
        s.borderColor = 'var(--dp-player-card-offline-border)'
        s.opacity = '0.85'
        return s
      }

      // 当前行动者高亮边框
      if (this.actIndex === i) {
        s.borderColor = 'var(--dp-player-card-turn-border)'
        s.background = 'var(--dp-player-card-turn-bg)'
      }

      // 自己强调边框
      if (this.isMe(p.nickname)) {
        s.borderColor = 'var(--dp-player-border-me)'
      }

      // 弃牌变灰
      if (p.fold) {
        s.opacity = '0.5'
      }

      // 摊牌选中
      if (this.selectedWinners.includes(p.nickname)) {
        s.borderColor = 'var(--dp-player-card-winner-border)'
        s.borderWidth = '3px'
        s.background = 'var(--dp-player-card-winner-bg)'
        s.opacity = '1'
      } else if (this.stage === 'showdown' && this.isOwner) {
        s.cursor = 'pointer'
        s.borderStyle = 'dashed'
        s.borderColor = 'var(--dp-player-showdown-border)'
      }

      return s
    },

    startCountdown() {
      this.stopCountdown(); // 先清除旧的
      this.timeLeft = 30;
      this.actionTimer = setInterval(() => {
        if (this.timeLeft > 0) {
          this.timeLeft--;
        } else {
          this.stopCountdown();
          // 这里可以加个逻辑，比如时间到了自动弃牌：this.doFold();
        }
      }, 1000);
    },

    stopCountdown() {
      if (this.actionTimer) {
        clearInterval(this.actionTimer);
        this.actionTimer = null;
      }
    },

    // 结算后准备阶段倒计时（与行动计时风格一致）
    startReadyCountdown() {
      this.stopReadyCountdown()
      this.readyTimeLeft = 30
      this.readyTimer = setInterval(() => {
        if (this.readyTimeLeft > 0) {
          this.readyTimeLeft--
        } else {
          this.stopReadyCountdown()
        }
      }, 1000)
    },

    stopReadyCountdown() {
      if (this.readyTimer) {
        clearInterval(this.readyTimer)
        this.readyTimer = null
      }
    }
  }
}
</script>
