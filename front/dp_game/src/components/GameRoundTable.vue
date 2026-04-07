<template>
  <div class="dp-game-table">
    <div class="dp-game-table__layout">
      <div class="dp-game-table__felt" aria-hidden="true" />
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
        />
      </svg>
      <div class="dp-game-table__center">
        <div class="dp-game-table__center-stack">
          <game-table-action-timer
              v-if="showCenterActionTimer"
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
        </div>
      </div>
      <game-table-action-timer
          v-if="showOrbitActionTimer"
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
          title="弃牌堆（庄家侧）"
          aria-label="弃牌堆"
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
        >D</span>
        <span
            v-if="row.player.blind === 1"
            class="dp-player-card__badge dp-player-card__badge--sb"
        >SB</span>
        <span
            v-if="row.player.blind === 2"
            class="dp-player-card__badge dp-player-card__badge--bb"
        >BB</span>
        <!-- 连胜台呢标已停用：改由「场上筹码最多」玩家卡片光效表示；日后若要恢复连胜标可解开 -->
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
            'dp-game-table__seat--empty':
              viewerSeatedAtTable && displayIdx === 0 && heroHoleDealIntroDone
          }"
          :style="seatRoundStyle(displayIdx)"
      >
        <game-player-card
            v-if="!(viewerSeatedAtTable && displayIdx === 0) || showHeroSeatOnTable"
            :player="row.player"
            :field-chip-leader="!row.player.leftThisHand && chipLeaderNicknames.indexOf(row.player.nickname) !== -1"
            :seat-index="row.seatIndex"
            :box-style="getPlayerBoxStyle(row.player, row.seatIndex)"
            :act-index="actIndex"
            :stage="stage"
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
  muckPileRoundTableStyle,
  playerRoundTableStyle,
  roundTableSeatTheta,
  seatChatBubbleSide,
  seatFeltMarkerRoundTableStyle
} from '../utils/dpGameRoundTableLayout'

export default {
  name: 'GameRoundTable',
  components: {
    GameCommunityCards,
    GamePlayerCard,
    GameTableActionTimer
  },
  props: {
    playersDisplayOrder: { type: Array, required: true },
    showTableActionTimer: { type: Boolean, default: false },
    timeLeft: { type: Number, required: true },
    timerActorName: { type: String, default: '' },
    timerUrgency: { type: String, default: 'ok' },
    timerProgressPct: { type: [Number, String], default: 0 },
    ecoMode: { type: Boolean, default: false },
    /** 倒计时圆环随当前行动者移到台呢标（D/SB/BB）左侧，中央仅保留公共牌 */
    orbitActionTimer: { type: Boolean, default: true },
    communityCards: { type: Array, default: function () { return [] } },
    communityCardsFlipState: { type: Array, default: function () { return [] } },
    viewerSeatedAtTable: { type: Boolean, default: false },
    heroHoleDealIntroDone: { type: Boolean, default: false },
    showHeroSeatOnTable: { type: Boolean, default: false },
    actIndex: { type: Number, required: true },
    stage: { type: String, required: true },
    communityCardsFlipComplete: { type: Boolean, default: false },
    isOwner: { type: Boolean, default: false },
    ownerRevealAll: { type: Boolean, default: false },
    myNickname: { type: String, default: '' },
    currentHandSeed: { type: Number, default: 0 },
    holeDealPlayerCountForAnim: { type: Number, default: 1 },
    showdownHandLeaderNicknames: { type: Array, default: function () { return [] } },
    dealerDisplayIndex: { type: Number, default: -1 },
    /** 后端 autoSettle 写入的筹码并列最高昵称，未结算过为空数组 */
    chipLeaderNicknames: { type: Array, default: function () { return [] } },
    getPlayerBoxStyle: { type: Function, required: true },
    holeDealOrderFromDealer: { type: Function, required: true },
    seatChatTextFor: { type: Function, required: true }
  },
  computed: {
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
        this.stage
      )
      return Object.assign({}, base, { zIndex: 5 })
    }
  },
  methods: {
    feltMarkerStyle: function (displayIdx) {
      return seatFeltMarkerRoundTableStyle(
        displayIdx,
        this.playersDisplayOrder.length,
        this.viewerSeatedAtTable,
        this.stage
      )
    },
    seatRoundStyle: function (displayIdx) {
      return playerRoundTableStyle(
        displayIdx,
        this.playersDisplayOrder.length,
        this.viewerSeatedAtTable,
        this.stage
      )
    },
    /**
     * 分区线：内端落在「公共牌区」外沿（与中心椭圆同心的比例环），外端至台呢外沿，
     * 中央不留线，避免穿过公共牌。
     */
    seatRayInnerX: function (displayIdx) {
      var n = this.playersDisplayOrder.length
      if (!n) return 50
      var theta = roundTableSeatTheta(displayIdx, n, this.viewerSeatedAtTable)
      var innerFactor = 0.36
      return 50 + Math.sin(theta) * 46 * innerFactor
    },
    seatRayInnerY: function (displayIdx) {
      var n = this.playersDisplayOrder.length
      if (!n) return 44
      var theta = roundTableSeatTheta(displayIdx, n, this.viewerSeatedAtTable)
      var innerFactor = 0.36
      return 44 - Math.cos(theta) * 41 * innerFactor
    },
    /** 与座位椭圆布局相同的极角，射线延伸至台呢外沿（约 1.05× 座位半径） */
    seatRayEndX: function (displayIdx) {
      var n = this.playersDisplayOrder.length
      if (!n) return 50
      var theta = roundTableSeatTheta(displayIdx, n, this.viewerSeatedAtTable)
      var scale = 1.05
      return 50 + Math.sin(theta) * 46 * scale
    },
    seatRayEndY: function (displayIdx) {
      var n = this.playersDisplayOrder.length
      if (!n) return 44
      var theta = roundTableSeatTheta(displayIdx, n, this.viewerSeatedAtTable)
      var scale = 1.05
      return 44 - Math.cos(theta) * 41 * scale
    },
    seatChatSide: function (displayIdx) {
      return seatChatBubbleSide(
        displayIdx,
        this.playersDisplayOrder.length,
        this.viewerSeatedAtTable
      )
    }
  }
}
</script>
