<template>
  <div class="dp-game-table">
    <div class="dp-game-table__layout" :style="retroTableLayoutStyle">
      <div class="dp-game-table__surface" aria-hidden="true">
        <div class="dp-game-table__felt" />
        <dp-retro-glitch-monster
            v-if="showRetroDesktopFx && retroTableLayout"
            :layout="retroTableLayout"
            :animated="retroDesktopAnimated"
            :glitch-seq="retroGlitchSeq"
            @table-edge-alert="retroTableEdgeAlert = $event"
        />
        <dp-retro-table-fx
            v-if="showRetroDesktopFx && retroTableLayout"
            :layout="retroTableLayout"
            :animated="retroDesktopAnimated"
            :edge-alert="retroTableEdgeAlert"
        />
      </div>
      <svg
          v-if="playersDisplayOrder.length >= 2"
          class="dp-game-table__seat-rays"
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          aria-hidden="true"
      >
        <line
            v-for="(row, displayIdx) in playersDisplayOrder"
            :key="'seat-ray-' + displayIdx + '-' + (row.player.nickname || row.seatIndex)"
            :x1="String(seatRayInnerX(displayIdx))"
            :y1="String(seatRayInnerY(displayIdx))"
            :x2="String(seatRayEndX(displayIdx))"
            :y2="String(seatRayEndY(displayIdx))"
            :class="seatRayClass(displayIdx)"
            :data-urgency="seatRayUrgency(displayIdx)"
        />
      </svg>
      <div class="dp-game-table__center">
        <div class="dp-game-table__center-stack">
          <dp-retro-stack-leader-ticker
              v-if="showRetroDesktopFx"
              :animated="retroDesktopAnimated"
          />
          <game-table-action-timer
              v-if="showCenterActionTimer && gameUiTheme !== 'retro8bit'"
              :time-left="timeLeft"
              :actor-name="timerActorName"
              :urgency="timerUrgency"
              :progress-pct="timerProgressPct"
              :eco-mode="ecoMode"
              :ring-only="false"
          />
          <game-community-cards
              :community-cards="communityCards"
              :flip-state="communityCardsFlipState"
          />
          <dp-table-pot-display
              v-if="gameUiTheme === 'retro8bit'"
              :pot="pot"
          />
        </div>
      </div>
      <game-table-action-timer
          v-if="showOrbitActionTimer && gameUiTheme !== 'retro8bit'"
          class="dp-game-table-action-timer--orbit"
          :style="actionTimerOrbitStyle"
          :time-left="timeLeft"
          :actor-name="timerActorName"
          :urgency="timerUrgency"
          :progress-pct="timerProgressPct"
          :eco-mode="ecoMode"
          :ring-only="true"
      />
      <div
          class="dp-game-muck-pile dp-game-muck-pile--orbit"
          data-dp-muck-anchor="true"
          :style="muckStyle"
          title="盖牌区（发牌猫侧）"
          aria-label="盖牌区"
      />
      <div
          v-for="(row, displayIdx) in playersDisplayOrder"
          :key="'felt-' + (row.player.leftThisHand ? 'offline-' + row.seatIndex : row.player.nickname)"
          class="dp-game-table__felt-markers"
          :style="feltMarkerStyle(displayIdx)"
          aria-hidden="true"
      >
        <span
            v-if="row.player.dealer"
            class="dp-player-card__badge dp-player-card__badge--dealer"
            title="发牌猫"
        >{{ dealerBadgeChar }}</span>
        <span
            v-if="row.player.blind === 1"
            class="dp-player-card__badge dp-player-card__badge--sb"
            title="小猫（SC）"
        >{{ catCopy.smallBlindAbbr }}</span>
        <span
            v-if="row.player.blind === 2"
            class="dp-player-card__badge dp-player-card__badge--bb"
            title="大猫（BC）"
        >{{ catCopy.bigBlindAbbr }}</span>
        <!-- 连胜台呢标已停用：改由「场上积分最多」玩家卡片光效表示；日后若要恢复连胜标可解开 -->
        <!--
        <span
            v-if="!row.player.leftThisHand && (row.player.winStreak || 0) >= 2"
            class="win-streak-badge win-streak-badge--table"
            :title="'已连续赢下 ' + (row.player.winStreak || 0) + ' 手'"
        >
          <span class="win-streak-badge__emoji" aria-hidden="true">🔥</span>
          <span class="win-streak-badge__text">{{ row.player.winStreak }}连胜</span>
        </span>
        -->
      </div>
      <div
          v-for="(row, displayIdx) in playersDisplayOrder"
          :key="(row.player.leftThisHand ? 'offline-' + row.seatIndex : row.player.nickname)"
          class="dp-game-table__seat"
          :class="{
            'dp-game-table__seat--join-reveal': seatEnterRevealEnabled
              && joinRevealNicks[row.player.nickname]
              && !row.player.leftThisHand
          }"
          :style="seatRoundStyle(displayIdx)"
          @animationend="onSeatEnterRevealAnimationEnd($event, row.player.nickname)"
      >
        <game-player-card
            :player="row.player"
            :field-chip-leader="!row.player.leftThisHand && chipLeaderNicknames.indexOf(row.player.nickname) !== -1"
            :seat-index="row.seatIndex"
            :box-style="getPlayerBoxStyle(row.player, row.seatIndex)"
            :act-index="actIndex"
            :stage="playerCardStage"
            :actual-stage="stage"
            :retro-showdown-tv-pending="retroShowdownTvPending"
            :display-chips="playerEconomyForDisplay(row.player).chips"
            :display-bet="playerEconomyForDisplay(row.player).bet"
            :community-cards="communityCards"
            :community-cards-flip-complete="communityCardsFlipComplete"
            :is-owner="isOwner"
            :owner-reveal-all="ownerRevealAll"
            :my-nickname="myNickname"
            :hand-deal-key="currentHandSeed"
            :hole-deal-seat-order="holeDealOrderFromDealer(row.seatIndex)"
            :hole-deal-player-count="holeDealPlayerCountForAnim"
            :rival-mini="true"
            :showdown-hand-leaders="showdownHandLeaderNicknames"
            :seat-chat-text="seatChatTextFor(row.player.nickname)"
            :seat-chat-side="seatChatSide(displayIdx)"
            @hole-deal-intro-complete="$emit('hole-deal-intro-complete')"
            @card-click="$emit('card-click', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script>
import GameCommunityCards from './GameCommunityCards.vue'
import GamePlayerCard from './GamePlayerCard.vue'
import GameTableActionTimer from './GameTableActionTimer.vue'
import {
  actionTimerOrbitRoundTableStyle,
  buildRetroTableLayout,
  muckPileRoundTableStyle,
  retroSeatRayLayoutDiagnostics,
  retroSeatRayLineEndpoints,
  roundTableSeatPosition,
  roundTableSeatTheta,
  seatChatBubbleSide,
  seatFeltMarkerRoundTableStyle
} from '../utils/dpGameRoundTableLayout'
import { dpTableLayoutDevLog } from '@features/room/utils/dpTableLayoutDevLog'
import { dpSeatRayDevLog } from '@features/room/utils/dpSeatRayDevLog'
import DpTablePotDisplay from './DpTablePotDisplay.vue'
import DpRetroTableFx from '@shared/components/DpRetroTableFx.vue'
import DpRetroStackLeaderTicker from '@shared/components/DpRetroStackLeaderTicker.vue'
import DpRetroGlitchMonster from '@shared/components/DpRetroGlitchMonster.vue'
import { CAT_COPY, DEALER_BADGE_CHAR } from '@shared/constants/dpCatThemeCopy'

export default {
  name: 'GameRoundTable',
  components: {
    GameCommunityCards,
    GamePlayerCard,
    GameTableActionTimer,
    DpTablePotDisplay,
    DpRetroTableFx,
    DpRetroStackLeaderTicker,
    DpRetroGlitchMonster
  },
  props: {
    playersDisplayOrder: { type: Array, required: true },
    showTableActionTimer: { type: Boolean, default: false },
    timeLeft: { type: Number, required: true },
    timerActorName: { type: String, default: '' },
    timerUrgency: { type: String, default: 'ok' },
    timerProgressPct: { type: [Number, String], default: 0 },
    ecoMode: { type: Boolean, default: false },
    /** 倒计时圆环随当前行动者移到台呢标（D/1/2）左侧，中央仅保留公共牌 */
    orbitActionTimer: { type: Boolean, default: true },
    communityCards: { type: Array, default: function () { return [] } },
    communityCardsFlipState: { type: Array, default: function () { return [] } },
    viewerSeatedAtTable: { type: Boolean, default: false },
    actIndex: { type: Number, required: true },
    stage: { type: String, required: true },
    /** retro8bit TV 期间回退到上一下注街，供 GamePlayerCard 紧凑展示 */
    cardDisplayStage: { type: String, default: '' },
    /** retro8bit：TV 播放中门闸，亮牌逻辑与 cardDisplayStage 解耦 */
    retroShowdownTvPending: { type: Boolean, default: false },
    communityCardsFlipComplete: { type: Boolean, default: false },
    isOwner: { type: Boolean, default: false },
    ownerRevealAll: { type: Boolean, default: false },
    myNickname: { type: String, default: '' },
    currentHandSeed: { type: Number, default: 0 },
    holeDealPlayerCountForAnim: { type: Number, default: 1 },
    showdownHandLeaderNicknames: { type: Array, default: function () { return [] } },
    dealerDisplayIndex: { type: Number, default: -1 },
    /** 后端 autoSettle 写入的积分并列最高昵称，未结算过为空数组 */
    chipLeaderNicknames: { type: Array, default: function () { return [] } },
    getPlayerBoxStyle: { type: Function, required: true },
    holeDealOrderFromDealer: { type: Function, required: true },
    seatChatTextFor: { type: Function, required: true },
    /** TV 冻结期间：按昵称返回展示用 chips/bet */
    playerEconomyForDisplay: {
      type: Function,
      default: function (player) {
        if (!player) return { chips: 0, bet: 0 }
        return { chips: player.chips, bet: player.bet }
      }
    },
    joinRevealNicks: { type: Object, default: function () { return {} } },
    seatEnterRevealEnabled: { type: Boolean, default: false },
    gameUiTheme: { type: String, default: 'default' },
    pot: { type: Number, default: 0 },
    showRetroDesktopFx: { type: Boolean, default: false },
    retroDesktopAnimated: { type: Boolean, default: false },
    retroGlitchSeq: { type: Number, default: 0 }
  },
  data() {
    return {
      catCopy: CAT_COPY,
      dealerBadgeChar: DEALER_BADGE_CHAR,
      retroTableEdgeAlert: false
    }
  },
  watch: {
    actingDisplayIndex: function () {
      this.logSeatRayState('actingDisplayIndex')
    },
    actIndex: function () {
      this.logSeatRayState('actIndex')
    },
    gameUiTheme: function () {
      this.logSeatRayState('gameUiTheme')
    },
    'playersDisplayOrder.length': function () {
      this.logRetroSeatRayLayoutCompare('players-count')
    },
    viewerSeatedAtTable: function () {
      this.logRetroSeatRayLayoutCompare('viewer-seated')
    }
  },
  mounted: function () {
    this.logSeatRayState('mounted')
    this.logRetroSeatRayLayoutCompare('mounted')
  },
  computed: {
    playerCardStage: function () {
      return this.cardDisplayStage || this.stage
    },
    muckStyle: function () {
      return muckPileRoundTableStyle(
        this.stage,
        this.playersDisplayOrder.length,
        this.dealerDisplayIndex,
        this.viewerSeatedAtTable
      )
    },
    /** 当前行动者在「本机视角座位环」上的 display 下标，与 felt 标一致 */
    actingDisplayIndex: function () {
      var ai = this.actIndex
      var list = this.playersDisplayOrder
      if (!list || !list.length || ai == null || ai < 0) return -1
      for (var i = 0; i < list.length; i++) {
        if (list[i].seatIndex === ai) return i
      }
      return -1
    },
    showOrbitActionTimer: function () {
      return (
        !!this.orbitActionTimer
        && this.showTableActionTimer
        && this.actingDisplayIndex >= 0
      )
    },
    showCenterActionTimer: function () {
      return this.showTableActionTimer && !this.showOrbitActionTimer
    },
    actionTimerOrbitStyle: function () {
      var idx = this.actingDisplayIndex
      if (idx < 0) return {}
      var base = actionTimerOrbitRoundTableStyle(
        idx,
        this.playersDisplayOrder.length,
        this.viewerSeatedAtTable,
        this.stage,
        this.gameUiTheme
      )
      return Object.assign({}, base, { zIndex: 5 })
    },
    retroTableLayout: function () {
      if (this.gameUiTheme !== 'retro8bit') return null
      return buildRetroTableLayout(this.playersDisplayOrder.length, {
        viewerSeatedAtTable: this.viewerSeatedAtTable,
        logReason: 'layout-computed'
      })
    },
    retroTableLayoutStyle: function () {
      if (!this.retroTableLayout || !this.retroTableLayout.clipPath) return {}
      return { '--dp-table-polygon': this.retroTableLayout.clipPath }
    }
  },
  methods: {
    seatRayClass: function (displayIdx) {
      var isActive = displayIdx === this.actingDisplayIndex
      var t = Number(this.timeLeft)
      return {
        'dp-game-table__seat-ray--active': isActive,
        'dp-game-table__seat-ray--active-breathe': isActive && !isNaN(t) && t > 20
      }
    },
    seatRayUrgency: function (displayIdx) {
      if (displayIdx !== this.actingDisplayIndex) return undefined
      return this.timerUrgency
    },
    logSeatRayState: function (reason) {
      if (this.gameUiTheme !== 'retro8bit') return
      var idx = this.actingDisplayIndex
      var row = idx >= 0 ? this.playersDisplayOrder[idx] : null
      var ep = idx >= 0 ? this.seatRayEndpoints(idx) : null
      dpSeatRayDevLog(reason, {
        pathType: 'line',
        actIndex: this.actIndex,
        actingDisplayIndex: idx,
        actingNickname: row && row.player ? row.player.nickname : null,
        actingSeatIndex: row ? row.seatIndex : null,
        timerUrgency: this.timerUrgency,
        activeRayClass: idx >= 0 ? 'dp-game-table__seat-ray--active' : null,
        centerToVertex: ep
          ? { center: { x: ep.x1, y: ep.y1 }, vertex: { x: ep.x2, y: ep.y2 } }
          : null
      })
    },
    logRetroSeatRayLayoutCompare: function (reason) {
      if (this.gameUiTheme !== 'retro8bit') return
      var n = this.playersDisplayOrder.length
      if (n < 2) return
      var self = this
      var rays = retroSeatRayLayoutDiagnostics(
        n,
        this.viewerSeatedAtTable,
        this.retroTableLayout,
        function (displayIdx) {
          return self.seatRoundStyle(displayIdx)
        }
      )
      dpTableLayoutDevLog('seat-rays-' + reason, { playerCount: n, rays: rays })
    },
    feltMarkerStyle: function (displayIdx) {
      return seatFeltMarkerRoundTableStyle(
        displayIdx,
        this.playersDisplayOrder.length,
        this.viewerSeatedAtTable,
        this.stage,
        this.gameUiTheme
      )
    },
    seatRoundStyle: function (displayIdx) {
      return roundTableSeatPosition(
        displayIdx,
        this.playersDisplayOrder.length,
        this.viewerSeatedAtTable,
        this.stage,
        this.gameUiTheme
      )
    },
    /** retro8bit：桌心→多边形顶点；其它主题：椭圆比例环内沿→外沿 */
    seatRayInnerX: function (displayIdx) {
      var ep = this.seatRayEndpoints(displayIdx)
      return ep.x1
    },
    seatRayInnerY: function (displayIdx) {
      var ep = this.seatRayEndpoints(displayIdx)
      return ep.y1
    },
    seatRayEndX: function (displayIdx) {
      var ep = this.seatRayEndpoints(displayIdx)
      return ep.x2
    },
    seatRayEndY: function (displayIdx) {
      var ep = this.seatRayEndpoints(displayIdx)
      return ep.y2
    },
    seatRayEndpoints: function (displayIdx) {
      var n = this.playersDisplayOrder.length
      if (this.gameUiTheme === 'retro8bit') {
        return retroSeatRayLineEndpoints(
          displayIdx,
          n,
          this.viewerSeatedAtTable,
          this.retroTableLayout
        )
      }
      if (!n) return { x1: 50, y1: 44, x2: 50, y2: 44 }
      var theta = roundTableSeatTheta(displayIdx, n, this.viewerSeatedAtTable)
      var innerFactor = 0.36
      var scale = 1.05
      return {
        x1: 50 + Math.sin(theta) * 46 * innerFactor,
        y1: 44 - Math.cos(theta) * 41 * innerFactor,
        x2: 50 + Math.sin(theta) * 46 * scale,
        y2: 44 - Math.cos(theta) * 41 * scale
      }
    },
    seatChatSide: function (displayIdx) {
      return seatChatBubbleSide(
        displayIdx,
        this.playersDisplayOrder.length,
        this.viewerSeatedAtTable,
        this.gameUiTheme
      )
    },
    onSeatEnterRevealAnimationEnd: function (event, nickname) {
      if (!event || !event.animationName) return
      if (event.animationName !== 'dp-retro-seat-enter-materialize') return
      this.$emit('seat-enter-reveal-done', nickname)
    }
  }
}
</script>
