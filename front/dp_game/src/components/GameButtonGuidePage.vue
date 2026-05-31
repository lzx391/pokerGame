<template>
  <div
    ref="gameRoot"
    class="dp-game-root dp-guide-page"
    :class="{
      'dp-game-root--pseudo-fs': pseudoFullscreen,
      'dp-game-root--layout-fs': layoutFullscreen,
      'dp-game-root--mobile-hero-dock': mobileHeroDockActive
    }"
    :data-dp-layout-tier="layoutTier"
    :data-dp-orientation="layoutOrientation"
    :data-dp-game-theme="effectiveThemeForCss"
    :data-dp-eco-mode="ecoMode ? 'true' : 'false'"
    :data-dp-stage="stage"
  >
    <div class="dp-game-layout">
      <header class="dp-game-layout__header">
        <game-top-bar
          ref="topBar"
          :room-id="mock.roomId"
          :stage-label="stageLabel"
          :pot="pot"
          :current-bet-to-call="currentBetToCall"
          :spectator-count="spectators.length"
          :wait-next-hand-count="waitNextHand.length"
          :is-fullscreen="layoutFullscreen"
          :is-owner="isOwner"
          :can-invite-friend="canInviteFriend"
          :show-spectator-prepare="false"
          :next-hand-ready="mock.nextHandReady"
          :game-ui-theme="gameUiTheme"
          :eco-mode="ecoMode"
          :theme-options="gameThemeOptions"
          exit-label="退出教程"
          :show-hero-economy="topBarShowHeroEconomy"
          :hero-my-chips="myChips"
          :hero-economy-secondary-label="topBarHeroEconomySecondaryLabel"
          :hero-economy-secondary-value="topBarHeroEconomySecondaryValue"
          :hero-carry-in-chips="mock.myCarryInChips"
          @update:gameUiTheme="$store.commit('dpGame/SET_GAME_UI_THEME', $event)"
          @update:ecoMode="$store.commit('dpGame/SET_ECO_MODE', $event)"
          @show-play-guide="onGuidePlayGuide"
          @show-spectators="noopGuideTip('观众席列表')"
          @show-wait-next-hand="noopGuideTip('等待名单')"
          @toggle-fullscreen="toggleDpFullscreen"
          @open-hand-history="noopGuideTip('历史对局')"
          @open-music-box="noopGuideTip('音乐盒')"
          @open-owner-hub="noopGuideTip('房主操作')"
          @open-invite-friend="noopGuideTip('邀请好友')"
          @exit="exitToLobby"
          @ready-next-hand="noopGuideTip('下一局报名')"
        />
      </header>

      <main
        ref="gameMain"
        class="dp-game-layout__main dp-game-layout__main--fit-table"
      >
        <p
          v-if="layoutTier === 'phone' && layoutOrientation === 'portrait'"
          class="dp-game-layout__portrait-hint"
          role="status"
        >
          横屏可获得更大牌桌视野，建议旋转手机并全屏游玩。
        </p>
        <div ref="guideTableWrap" class="dp-game-table-fit" :style="tableFitClipStyleObj">
          <div ref="tableFitInner" class="dp-game-table-fit__inner">
            <game-round-table
              :players-display-order="playersDisplayOrder"
              :show-table-action-timer="showTableActionTimer"
              :time-left="mock.timeLeft"
              :timer-actor-name="tableActionActorDisplayName"
              :timer-urgency="'ok'"
              :timer-progress-pct="72"
              :eco-mode="ecoMode"
              :community-cards="communityCards"
              :community-cards-flip-state="communityCardsFlipState"
              :viewer-seated-at-table="true"
              :act-index="actIndex"
              :stage="stage"
              :community-cards-flip-complete="communityCardsFlipComplete"
              :is-owner="isOwner"
              :owner-reveal-all="mock.ownerRevealAll"
              :my-nickname="heroNickname"
              :current-hand-seed="currentHandSeed"
              :hole-deal-player-count-for-anim="players.length"
              :showdown-hand-leader-nicknames="showdownHandLeaderNicknames"
              :dealer-display-index="dealerDisplayIndex"
              :chip-leader-nicknames="chipLeaderNicknames"
              :get-player-box-style="getPlayerBoxStyle"
              :hole-deal-order-from-dealer="holeDealOrderFromDealer"
              :seat-chat-text-for="seatChatTextFor"
              @card-click="noopGuideTip('点击玩家卡')"
            />
          </div>
        </div>
        <p class="dp-guide-page__hint" role="status">
          单机教程：进页会自动尝试全屏（与真对局相同）。点击空白处继续；不会连接服务器。
        </p>
      </main>

      <footer class="dp-game-layout__footer">
        <game-hero-dock-footer ref="heroFooter" />
      </footer>
    </div>

    <game-dp-game-sheets ref="guideSheets" />

    <button-guide-spotlight
      v-if="guideActive"
      :step-index="guideStep"
      :total-steps="guideSteps.length"
      :step-title="currentStep.title"
      :step-body="currentStep.body"
      :step-note="currentStep.note || ''"
      :is-complete-step="currentStep.complete"
      :is-last-highlight-step="guideStep === lastHighlightStepIndex"
      :spotlight-rect="spotlightRect"
      :spotlight-pad="guideSpotlightPad"
      @next="onGuideNext"
      @prev="onGuidePrev"
      @skip="exitToLobby"
      @finish="exitToLobby"
    />
  </div>
</template>

<script>
import { mapState, mapGetters } from 'vuex'
import '../styles/dp-game-themes.css'
import '../styles/dp-game-shell.css'
import '../styles/dp-game-eco-mode.css'
import '../styles/dp-game-guide.css'
import { dpGameStageDisplay } from '../constants/dpCatThemeCopy'
import { GUIDE_UI_STEPS, GUIDE_UI_COMPLETE_STEP } from '../constants/guideUiSteps'
import ButtonGuideSpotlight from './ButtonGuideSpotlight.vue'
import GameTopBar from './GameTopBar.vue'
import GameRoundTable from './GameRoundTable.vue'
import GameHeroDockFooter from './GameHeroDockFooter.vue'
import GameDpGameSheets from './GameDpGameSheets.vue'
import dpGameFullscreenMixin from '../mixins/dpGameFullscreenMixin'
import dpGameLayoutTierMixin from '../mixins/dpGameLayoutTierMixin'
import dpGameTableFitMixin from '../mixins/dpGameTableFitMixin'
import { dpGamePlayerBoxStyle } from '../utils/dpGamePlayerBoxStyle'
import {
  createGuideMockState,
  buildGuidePlayersDisplayOrder,
  guideDealerDisplayIndex,
  holeDealOrderFromDealer,
  GUIDE_HERO_NICKNAME
} from '../utils/guideMockTableData'

export default {
  name: 'GameButtonGuide',
  components: {
    ButtonGuideSpotlight,
    GameTopBar,
    GameRoundTable,
    GameHeroDockFooter,
    GameDpGameSheets
  },
  mixins: [dpGameFullscreenMixin, dpGameLayoutTierMixin, dpGameTableFitMixin],
  provide: function () {
    return { dpGameView: this }
  },
  data: function () {
    return {
      mock: createGuideMockState(),
      dpGuideMode: true,
      guideActive: true,
      guideStep: 0,
      spotlightRect: null,
      _resizeHandler: null,
      _spotlightRemeasureTimer: null,
      _simulateActionOpenTimer: null,
      windowNarrow: false,
      loading: false
    }
  },
  computed: {
    ...mapState('dpGame', [
      'gameUiTheme',
      'ecoMode',
      'gameThemeOptions',
      'chatInputDraft',
      'raiseAmount',
      'showMobileActionSheet'
    ]),
    ...mapGetters('dpGame', ['effectiveThemeForCss']),
    heroNickname: function () {
      return this.mock.heroNickname || GUIDE_HERO_NICKNAME
    },
    user: function () {
      return this.mock.user
    },
    stage: function () {
      return this.mock.stage
    },
    pot: function () {
      return this.mock.pot
    },
    currentBetToCall: function () {
      return this.mock.currentBetToCall
    },
    callAmount: function () {
      return this.mock.callAmount
    },
    myChips: function () {
      return this.mock.myChips
    },
    myBet: function () {
      return this.mock.myBet
    },
    actIndex: function () {
      return this.mock.actIndex
    },
    players: function () {
      return this.mock.players
    },
    spectators: function () {
      return this.mock.spectators || []
    },
    waitNextHand: function () {
      return this.mock.waitNextHand || []
    },
    communityCards: function () {
      return this.mock.communityCards
    },
    communityCardsFlipState: function () {
      return this.mock.communityCardsFlipState
    },
    communityCardsFlipComplete: function () {
      return this.mock.communityCardsFlipComplete
    },
    currentHandSeed: function () {
      return this.mock.currentHandSeed
    },
    chipLeaderNicknames: function () {
      return this.mock.chipLeaderNicknames || []
    },
    showdownHandLeaderNicknames: function () {
      return []
    },
    roomChatMessages: function () {
      return this.mock.roomChatMessages || []
    },
    seatChatTextByNick: function () {
      return this.mock.seatChatTextByNick || {}
    },
    isOwner: function () {
      return !!this.mock.isOwner
    },
    ownerRevealAll: function () {
      return !!this.mock.ownerRevealAll
    },
    canInviteFriend: function () {
      return true
    },
    stageLabel: function () {
      return dpGameStageDisplay(this.stage)
    },
    playersDisplayOrder: function () {
      return buildGuidePlayersDisplayOrder(this.players, this.heroNickname)
    },
    heroDockRow: function () {
      var order = this.playersDisplayOrder
      return order && order.length ? order[0] : null
    },
    dealerDisplayIndex: function () {
      return guideDealerDisplayIndex(this.playersDisplayOrder)
    },
    isMyTurn: function () {
      return !!this.mock.isMyTurn
    },
    inSettledStage: function () {
      return this.stage === 'settled' || this.stage === 'showdown'
    },
    viewerSeatedAtTable: function () {
      return true
    },
    mobileHeroDockActive: function () {
      return !!(this.heroDockRow || this.isMyTurn || this.inSettledStage || this.isOwner)
    },
    showBottomHeroDock: function () {
      return !!(this.heroDockRow && this.stage !== 'preflop')
    },
    showHeroViewHandButton: function () {
      return !!this.heroDockRow && this.stage !== 'preflop'
    },
    showTableActionTimer: function () {
      return this.isMyTurn && this.actIndex >= 0
    },
    tableActionActorDisplayName: function () {
      var order = this.playersDisplayOrder
      var idx = this.actIndex
      if (!order.length || idx < 0) return this.heroNickname
      for (var i = 0; i < order.length; i++) {
        if (order[i].seatIndex === idx && order[i].player) {
          return order[i].player.nickname
        }
      }
      return this.heroNickname
    },
    smallBlind: function () {
      return this.mock.smallBlind
    },
    bigBlind: function () {
      return this.mock.bigBlind
    },
    minRaise: function () {
      return this.mock.minRaise
    },
    minTotalToRaise: function () {
      return this.mock.minTotalToRaise
    },
    lastRaiseIncrementEffective: function () {
      return this.mock.lastRaiseIncrement
    },
    timeLeft: function () {
      return this.mock.timeLeft
    },
    readyTimeLeft: function () {
      return this.mock.readyTimeLeft
    },
    myReady: function () {
      return this.mock.myReady
    },
    topBarShowHeroEconomy: function () {
      return !!(this.viewerSeatedAtTable && this.heroDockRow)
    },
    topBarHeroEconomySecondaryLabel: function () {
      if (this.isMyTurn && !this.inSettledStage && (Number(this.callAmount) || 0) > 0) {
        return '还需补'
      }
      return '本轮'
    },
    topBarHeroEconomySecondaryValue: function () {
      if (this.isMyTurn && !this.inSettledStage && (Number(this.callAmount) || 0) > 0) {
        return Number(this.callAmount) || 0
      }
      return Number(this.myBet) || 0
    },
    guideSteps: function () {
      return GUIDE_UI_STEPS.concat([GUIDE_UI_COMPLETE_STEP])
    },
    currentStep: function () {
      return this.guideSteps[this.guideStep] || GUIDE_UI_COMPLETE_STEP
    },
    lastHighlightStepIndex: function () {
      return GUIDE_UI_STEPS.length - 1
    },
    guideSpotlightPad: function () {
      var step = this.currentStep
      if (!step || step.complete) return 6
      if (step.spotlightPad != null) return Number(step.spotlightPad) || 6
      return step.closeUp ? 12 : 6
    },
    guideUsesMobileDock: function () {
      if (this.layoutTier === 'phone') return true
      if (this.layoutFullscreen) return true
      return !!this.windowNarrow
    },
    guideUsesActionSheet: function () {
      return this.guideUsesMobileDock
    }
  },
  watch: {
    guideStep: function () {
      this.applyStepSideEffects()
      this.$nextTick(this.updateSpotlight)
    },
    layoutFullscreen: function () {
      var self = this
      this.$nextTick(function () {
        self.updateSpotlight()
        self.scheduleTableFitUpdate()
        setTimeout(function () { self.updateSpotlight() }, 80)
      })
    },
    'players.length': function () {
      this.scheduleTableFitUpdate()
    },
    isMyTurn: function (v) {
      if (v) {
        this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
      }
    },
    showMobileActionSheet: function () {
      var self = this
      this.$nextTick(function () {
        self.updateSpotlight()
        if (self._spotlightRemeasureTimer) clearTimeout(self._spotlightRemeasureTimer)
        self._spotlightRemeasureTimer = setTimeout(function () { self.updateSpotlight() }, 280)
      })
    }
  },
  mounted: function () {
    this.syncWindowNarrow()
    this.$store.commit('dpGame/SET_MOBILE_SHEETS', {
      showMobileActionSheet: false,
      showMobileHandSheet: false
    })
    this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.mock.minRaise)
    this.$store.commit('dpGame/SET_CHAT_DRAFT', '')
    this.applyStepSideEffects()
    this.$nextTick(() => {
      this.updateSpotlight()
      this.scheduleTableFitUpdate()
    })
    var self = this
    this._resizeHandler = function () {
      self.syncWindowNarrow()
      self.updateSpotlight()
      self.scheduleTableFitUpdate()
    }
    window.addEventListener('resize', this._resizeHandler)
    window.addEventListener('scroll', this._resizeHandler, true)
    setTimeout(function () {
      self.updateSpotlight()
      self.scheduleTableFitUpdate()
    }, 150)
    setTimeout(function () {
      self.scheduleTableFitUpdate()
    }, 450)
  },
  beforeDestroy: function () {
    if (this._spotlightRemeasureTimer) clearTimeout(this._spotlightRemeasureTimer)
    if (this._simulateActionOpenTimer) clearTimeout(this._simulateActionOpenTimer)
    this.closeGuideActionSheet()
    if (this._resizeHandler) {
      window.removeEventListener('resize', this._resizeHandler)
      window.removeEventListener('scroll', this._resizeHandler, true)
    }
    this.setGuideChatExpanded(false)
    var tb = this.$refs.topBar
    if (tb && tb.closeSettingsForGuide) tb.closeSettingsForGuide()
    this.mock = createGuideMockState()
    this.guideActive = false
  },
  methods: {
    seatChatTextFor: function (nick) {
      return this.seatChatTextByNick[nick] || ''
    },
    holeDealOrderFromDealer: function (seatIndex) {
      return holeDealOrderFromDealer(seatIndex, this.players)
    },
    getPlayerBoxStyle: function (p, seatIndex) {
      return dpGamePlayerBoxStyle(p, seatIndex, {
        actIndex: this.actIndex,
        stage: this.stage,
        isOwner: this.isOwner,
        selectedWinners: [],
        myNickname: this.heroNickname
      })
    },
    unwrapGuideDom: function (el) {
      if (!el) return null
      if (Array.isArray(el)) el = el[0]
      if (el && el.$el) el = el.$el
      return el
    },
    getHeroFooter: function () {
      return this.$refs.heroFooter || null
    },
    syncWindowNarrow: function () {
      this.windowNarrow = typeof window !== 'undefined' && window.innerWidth <= 600
    },
    getGuideSheets: function () {
      return this.$refs.guideSheets || null
    },
    getGuideActionPanel: function () {
      if (this.guideUsesActionSheet && this.showMobileActionSheet) {
        var gs = this.getGuideSheets()
        if (gs && gs.$refs && gs.$refs.guideSheetActionPanel) {
          return gs.$refs.guideSheetActionPanel
        }
      }
      var hf = this.getHeroFooter()
      if (hf && hf.$refs && hf.$refs.guideActionPanel) return hf.$refs.guideActionPanel
      return null
    },
    getGuideChatPanel: function () {
      var hf = this.getHeroFooter()
      if (!hf || !hf.$refs) return null
      if (this.guideUsesMobileDock && hf.$refs.guideMobileRoomChatPanel) {
        return hf.$refs.guideMobileRoomChatPanel
      }
      return hf.$refs.guideRoomChatPanel || null
    },
    openGuideActionSheet: function () {
      this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: true })
    },
    closeGuideActionSheet: function () {
      this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: false })
    },
    isGuideActionPanelStep: function (step) {
      step = step || this.currentStep
      if (!step) return false
      return !!(step.openActionSheet ||
        (step.scope === 'actionPanel' && step.ref && step.ref !== 'guideMobileActionBtn'))
    },
    mapGuideFooterRef: function (ref) {
      if (!this.guideUsesMobileDock) {
        if (ref === 'guideMobileChatCluster') return 'guideChatBar'
        if (ref === 'guideMobileLeaveSeat') return 'guideLeaveSeat'
        if (ref === 'guideMobileActionBtn') return 'guideInlineActionSlot'
        if (ref === 'guideChatBar') return 'guideRoomChatInput'
      } else {
        if (ref === 'guideChatBar') return 'guideMobileChatBar'
        if (ref === 'guideMobileLeaveSeat') return 'guideMobileLeaveSeat'
      }
      return ref
    },
    resolveGuideElement: function (step) {
      if (!step || !step.ref) return null
      var el = null
      var ref = step.ref
      if (step.scope === 'topBar') {
        var tb = this.$refs.topBar
        if (tb && tb.$refs) el = tb.$refs[ref]
      } else if (step.scope === 'table') {
        el = this.$refs[ref]
      } else if (step.scope === 'actionPanel') {
        if (ref === 'guideActionSheet') {
          if (this.guideUsesActionSheet) {
            var sheets = this.getGuideSheets()
            if (sheets && sheets.$refs && sheets.$refs.guideActionSheet) {
              var sheetVm = sheets.$refs.guideActionSheet
              el = (sheetVm.$refs && sheetVm.$refs.guideSheetPanel) || sheetVm
            }
          } else {
            var apHost = this.getGuideActionPanel()
            if (apHost && apHost.$refs) el = apHost.$refs.guideActionPanelHost
          }
        } else {
          var ap = this.getGuideActionPanel()
          if (ap && ap.$refs) el = ap.$refs[ref]
        }
      } else {
        var hf = this.getHeroFooter()
        if (step.scope === 'page' && step.notMyTurn && this.guideUsesMobileDock) {
          ref = 'guideMobileChatCluster'
        } else {
          ref = this.mapGuideFooterRef(ref)
        }
        if (hf && hf.$refs) el = hf.$refs[ref]
        if (!el && (ref === 'guideChatToggle' || ref === 'guideChatListWrap')) {
          var cp = this.getGuideChatPanel()
          if (cp && cp.$refs) el = cp.$refs[ref]
        }
        if (!el) el = this.$refs[ref]
      }
      return this.unwrapGuideDom(el)
    },
    setGuideChatExpanded: function (expanded) {
      var cp = this.getGuideChatPanel()
      if (!cp) return
      if (expanded && cp.openForGuide) cp.openForGuide()
      else if (!expanded && cp.closeForGuide) cp.closeForGuide()
    },
    applyStepSideEffects: function () {
      var self = this
      var step = this.currentStep
      var tb = this.$refs.topBar
      if (tb && tb.closeSettingsForGuide) tb.closeSettingsForGuide()
      if (step && step.openSettings && tb && tb.openSettingsForGuide) {
        tb.openSettingsForGuide()
      }
      if (this._simulateActionOpenTimer) {
        clearTimeout(this._simulateActionOpenTimer)
        this._simulateActionOpenTimer = null
      }
      if (step && step.expandChat) {
        this.setGuideChatExpanded(true)
      } else {
        this.setGuideChatExpanded(false)
      }
      if (step && step.closeActionSheet) {
        this.closeGuideActionSheet()
      } else if (step && (step.openActionSheet || (step.scope === 'actionPanel' && step.ref !== 'guideMobileActionBtn'))) {
        if (this.guideUsesActionSheet) this.openGuideActionSheet()
      } else if (!step || !step.simulateOpenActionSheet) {
        if (!this.isGuideActionPanelStep(step)) this.closeGuideActionSheet()
      }
      if (step && step.simulateOpenActionSheet && this.guideUsesActionSheet) {
        this.closeGuideActionSheet()
        this._simulateActionOpenTimer = setTimeout(function () {
          self.openGuideActionSheet()
          self.$nextTick(function () {
            self.updateSpotlight()
            setTimeout(function () { self.updateSpotlight() }, 320)
          })
        }, 480)
      }
      if (step && step.notMyTurn) {
        this.mock.isMyTurn = false
        this.mock.actIndex = 1
      } else if (!step || !step.complete) {
        this.mock.isMyTurn = true
        this.mock.actIndex = 0
        this.mock.callAmount = 20
        this.mock.currentBetToCall = 20
      }
      if (step && step.scope === 'topBar' && step.ref === 'guideTopStage') {
        this.mock.stage = 'flop'
      }
    },
    measureSpotlightRect: function (el) {
      if (!el || typeof el.getBoundingClientRect !== 'function') {
        this.spotlightRect = null
        return
      }
      var r = el.getBoundingClientRect()
      if (r.width <= 0 || r.height <= 0) {
        this.spotlightRect = null
        return
      }
      this.spotlightRect = {
        top: r.top,
        left: r.left,
        width: r.width,
        height: r.height
      }
    },
    updateSpotlight: function () {
      var self = this
      var step = this.currentStep
      if (!step || step.complete) {
        this.spotlightRect = null
        return
      }
      var el = this.resolveGuideElement(step)
      if (!el) {
        this.spotlightRect = null
        return
      }
      if (step.closeUp && el.scrollIntoView) {
        try {
          el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'auto' })
        } catch (e) {
          el.scrollIntoView(false)
        }
      }
      this.measureSpotlightRect(el)
      if (this._spotlightRemeasureTimer) clearTimeout(this._spotlightRemeasureTimer)
      var delay = 80
      if (step.expandChat) delay = 220
      if (step.simulateOpenActionSheet) delay = 520
      if (step.openActionSheet) delay = 300
      if (step.expandChat || step.closeUp || step.simulateOpenActionSheet || step.openActionSheet) {
        this._spotlightRemeasureTimer = setTimeout(function () {
          var again = self.resolveGuideElement(step)
          if (again) self.measureSpotlightRect(again)
        }, delay)
      }
    },
    onGuideNext: function () {
      if (this.guideStep >= this.guideSteps.length - 1) {
        this.exitToLobby()
        return
      }
      this.guideStep += 1
    },
    onGuidePrev: function () {
      if (this.guideStep > 0) this.guideStep -= 1
    },
    exitToLobby: function () {
      this.guideActive = false
      this.$router.push('/home')
    },
    noopGuideTip: function (label) {
      this.$message.info('教程演示：「' + (label || '该按钮') + '」在真对局里才会生效')
    },
    onGuidePlayGuide: function () {
      this.noopGuideTip('玩法说明（真对局会打开完整规则弹窗）')
    },
    sendRoomChat: function () {
      this.noopGuideTip('局内聊天')
    },
    doLeaveSeat: function () {
      this.noopGuideTip('主动离座')
    },
    doCall: function () {
      this.noopGuideTip('跟投')
    },
    doRaise: function () {
      this.noopGuideTip('加投')
    },
    doAllIn: function () {
      this.noopGuideTip('全投')
    },
    doFold: function () {
      this.noopGuideTip('盖牌')
    },
    toggleReady: function () {
      this.noopGuideTip('准备')
    },
    rebuy: function () {
      this.noopGuideTip('补码')
    },
    onPlayerCardClick: function () {
      this.noopGuideTip('玩家卡')
    }
  }
}
</script>
