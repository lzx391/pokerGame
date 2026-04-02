<template>
  <div class="dp-game-table">
    <div class="dp-game-table__layout">
      <div class="dp-game-table__felt" aria-hidden="true" />
      <div class="dp-game-table__center">
        <div class="dp-game-table__center-stack">
          <game-table-action-timer
              v-if="showTableActionTimer"
              :time-left="timeLeft"
              :actor-name="timerActorName"
              :urgency="timerUrgency"
              :progress-pct="timerProgressPct"
              :eco-mode="ecoMode"
          />
          <game-community-cards
              :community-cards="communityCards"
              :flip-state="communityCardsFlipState"
          />
        </div>
      </div>
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
        <span
            v-if="!row.player.leftThisHand && (row.player.winStreak || 0) >= 2"
            class="win-streak-badge win-streak-badge--table"
            :title="'已连续赢下 ' + (row.player.winStreak || 0) + ' 手'"
        >
          <span class="win-streak-badge__emoji" aria-hidden="true">🔥</span>
          <span class="win-streak-badge__text">{{ row.player.winStreak }}连胜</span>
        </span>
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
  muckPileRoundTableStyle,
  playerRoundTableStyle,
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
