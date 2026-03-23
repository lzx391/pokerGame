<template>
  <div
    class="dp-player-card"
    :class="{ 'player-card--win-streak': !player.leftThisHand && (player.winStreak || 0) >= 2 }"
    :style="boxStyle"
    v-bind="dealerAnchorAttrs"
    @click="onClick"
  >
    <div class="dp-player-card__badges">
      <span v-if="player.dealer" class="dp-player-card__badge dp-player-card__badge--dealer">D</span>
      <span v-if="player.blind === 1" class="dp-player-card__badge dp-player-card__badge--sb">SB</span>
      <span v-if="player.blind === 2" class="dp-player-card__badge dp-player-card__badge--bb">BB</span>
      <span
        v-if="!player.leftThisHand && (player.winStreak || 0) >= 2"
        class="win-streak-badge"
        :title="'已连续赢下 ' + (player.winStreak || 0) + ' 手'"
      >
        <span class="win-streak-badge__emoji" aria-hidden="true">🔥</span>
        <span class="win-streak-badge__text">{{ player.winStreak }}连胜</span>
      </span>
    </div>

    <template v-if="player.leftThisHand">
      <div class="dp-player-card__offline-title">该玩家已离线</div>
      <div class="dp-player-card__offline-hint">座位保留至本局结束，行动顺序不变</div>
    </template>

    <template v-else>
      <div class="dp-player-card__head">
        <div class="dp-player-card__name">
          {{ player.nickname }}
          <span v-if="isMe" class="dp-player-card__name-me">（我）</span>
        </div>
        <div
          class="dp-player-card__status"
          :class="{
            'dp-player-card__status--fold': player.fold,
            'dp-player-card__status--turn': !player.fold && actIndex === seatIndex,
            'dp-player-card__status--idle': !player.fold && actIndex !== seatIndex
          }"
        >
          {{ player.fold ? '已弃牌' : (actIndex === seatIndex ? '思考中…' : '进行中') }}
        </div>
      </div>

      <div
        class="dp-player-card__stats-row"
        :class="{ 'dp-player-card__stats-row--has-holes': player.holeCards && player.holeCards.length > 0 }"
      >
        <div class="dp-player-card__chips">
          <span class="dp-player-card__mini-label">后手</span>
          <span class="dp-player-card__chips-val">{{ player.chips }}</span>
        </div>
        <div class="dp-player-card__bet" aria-label="本轮已下积分">
          <span class="dp-player-card__mini-label">本轮</span>
          <span class="dp-player-card__bet-val">{{ player.bet }}</span>
        </div>
        <div
          ref="holeCardsRow"
          class="dp-player-card__hole-row"
          :aria-label="isMe ? '我的手牌' : '手牌'"
        >
          <template v-if="showHoleCardsRevealed">
            <div
              v-for="(c, ci) in player.holeCards"
              :key="'h' + ci + '-' + handDealKey"
              class="hole-card-fly-wrapper"
              :class="{ 'hole-deal-fly-in': holeDealFlyActive(ci) }"
              :style="holeDealFlyStyle(ci)"
              @animationend="onHoleDealFlyEnd($event, ci)"
            >
              <div
                :class="[getCardClass(c), 'hole-card-flip']"
                :style="holeCardInnerStyle(ci)"
              >
                {{ getCardDisplay(c) }}
              </div>
            </div>
          </template>
          <template v-else-if="player.holeCards && player.holeCards.length > 0">
            <div
              v-for="n in player.holeCards.length"
              :key="'hb' + (n - 1) + '-' + handDealKey"
              class="hole-card-fly-wrapper"
              :class="{ 'hole-deal-fly-in': holeDealFlyActive(n - 1) }"
              :style="holeDealFlyStyle(n - 1)"
              @animationend="onHoleDealFlyEnd($event, n - 1)"
            >
              <div
                class="card-base bg-gray"
                style="width:36px; height:52px; font-size:13px;"
              >?</div>
            </div>
          </template>
        </div>
      </div>

      <div v-if="showHandRankSection" class="dp-player-card__hand-rank">
        <span
          class="dp-player-card__rank-pill"
          :class="showHandRankAsOpen ? 'dp-player-card__rank-pill--open' : 'dp-player-card__rank-pill--showdown'"
        >
          {{ getHandRank(player.holeCards, communityCards) }}
        </span>
        <div
          v-if="player.bestHandCards && player.bestHandCards.length === 5"
          class="best-hand-cards dp-player-card__best-hand"
        >
          <div
            v-for="(c, ci) in player.bestHandCards"
            :key="'best' + ci"
            :class="[getCardClass(c), 'best-hand-card', 'best-hand-card-enter', 'dp-player-card__best-card']"
            :style="{ animationDelay: (ci * 0.07) + 's' }"
          >
            {{ getCardDisplay(c) }}
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import { getCardClass, getCardDisplay } from '../utils/dpGameCardVisual'
import { getHandRank } from '../utils/dpGameHandRank'

export default {
  name: 'GamePlayerCard',
  props: {
    player: { type: Object, required: true },
    seatIndex: { type: Number, required: true },
    boxStyle: { type: Object, required: true },
    actIndex: { type: Number, required: true },
    stage: { type: String, required: true },
    communityCards: { type: Array, required: true },
    communityCardsFlipComplete: { type: Boolean, required: true },
    isOwner: { type: Boolean, required: true },
    ownerRevealAll: { type: Boolean, required: true },
    myNickname: { type: String, default: '' },
    /** 与房间 currentHandSeed 同步，每新一手变化以触发展位发手牌动画 */
    handDealKey: { type: Number, default: 0 }
  },
  data() {
    return {
      holeDealFlyByIndex: {},
      holeDealOriginByIndex: null,
      /** 本手是否走庄位发牌；用于翻牌 delay，避免飞入结束改 style 导致翻牌动画重播 */
      holeDealChainFlip: false
    }
  },
  watch: {
    handDealKey: {
      immediate: true,
      handler() {
        this.holeDealFlyByIndex = {}
        this.holeDealOriginByIndex = null
        this.holeDealChainFlip = false
        if (!this.shouldRunHoleDealFromDealer()) return
        if (this.prefersReducedMotion()) return
        var self = this
        this.$nextTick(function () {
          self.startHoleDealFly()
        })
      }
    }
  },
  computed: {
    isMe() {
      return !!this.myNickname && this.player.nickname === this.myNickname
    },
    /** 是否展示真实手牌（本人 / 房主看穿 / 摊牌后） */
    showHoleCardsRevealed() {
      if (this.player.leftThisHand) return false
      return (
        this.isMe
        || (this.isOwner && this.ownerRevealAll && this.player.holeCards && this.player.holeCards.length > 0)
        || ((this.stage === 'showdown' || this.stage === 'settled') && !this.player.fold)
      )
    },
    /** Flop 后且成牌可读时展示牌型与最大五张 */
    showHandRankSection() {
      if (this.player.leftThisHand) return false
      if (this.communityCards.length < 3 || !this.communityCardsFlipComplete) return false
      return (
        this.isMe
        || (this.isOwner && this.ownerRevealAll && this.player.holeCards && this.player.holeCards.length > 0)
        || ((this.stage === 'showdown' || this.stage === 'settled') && !this.player.fold)
      )
    },
    /** 牌型标签用「己方可见」配色还是摊牌公开配色 */
    showHandRankAsOpen() {
      return this.isMe
        || (this.isOwner && this.ownerRevealAll && this.player.holeCards && this.player.holeCards.length > 0)
    },
    /** 供公共牌飞入动画定位庄位（D）发牌起点 */
    dealerAnchorAttrs() {
      if (this.player.leftThisHand || !this.player.dealer) return {}
      return { 'data-dp-dealer-anchor': 'true' }
    }
  },
  methods: {
    getCardClass,
    getCardDisplay,
    getHandRank,
    prefersReducedMotion() {
      if (typeof window === 'undefined' || !window.matchMedia) return false
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    shouldRunHoleDealFromDealer() {
      if (this.player.leftThisHand) return false
      var hc = this.player.holeCards
      if (!hc || hc.length === 0) return false
      if (this.stage === 'showdown' || this.stage === 'settled') return false
      return true
    },
    startHoleDealFly() {
      var self = this
      this.$nextTick(function () {
        var row = self.$refs.holeCardsRow
        var n = self.player.holeCards ? self.player.holeCards.length : 0
        if (!row || n === 0) return
        self.holeDealChainFlip = true
        self.holeDealOriginByIndex = self.computeHoleOriginsFromDealer(row, n)
        var next = {}
        for (var i = 0; i < n; i++) {
          next[i] = { delay: 95 * i }
        }
        self.holeDealFlyByIndex = next
      })
    },
    computeHoleOriginsFromDealer(row, n) {
      if (typeof document === 'undefined') return null
      var dealerEl = document.querySelector('[data-dp-dealer-anchor="true"]')
      if (!dealerEl || !row) return null
      var d = dealerEl.getBoundingClientRect()
      var dcx = d.left + d.width / 2
      var dcy = d.top + d.height / 2
      var wrappers = row.querySelectorAll('.hole-card-fly-wrapper')
      var map = {}
      for (var i = 0; i < n && i < wrappers.length; i++) {
        var el = wrappers[i]
        var r = el.getBoundingClientRect()
        map[i] = {
          x: dcx - (r.left + r.width / 2),
          y: dcy - (r.top + r.height / 2)
        }
      }
      return Object.keys(map).length ? map : null
    },
    holeDealFlyActive(ci) {
      return !!this.holeDealFlyByIndex[ci]
    },
    holeDealFlyStyle(ci) {
      var m = this.holeDealFlyByIndex[ci]
      if (!m) return {}
      var origin = this.holeDealOriginByIndex && this.holeDealOriginByIndex[ci]
      var rotDeg = ci === 0 ? -5 : 5
      var style = {
        animationDelay: (m.delay || 0) + 'ms',
        zIndex: 2 + ci,
        '--hole-from-rot': rotDeg + 'deg'
      }
      if (origin) {
        style['--hole-from-x'] = origin.x + 'px'
        style['--hole-from-y'] = origin.y + 'px'
      } else {
        style['--hole-from-x'] = '0px'
        style['--hole-from-y'] = '64px'
      }
      return style
    },
    /** 飞入后再翻开，与庄位发牌节奏衔接 */
    holeCardInnerStyle(ci) {
      var base = {
        width: '36px',
        height: '52px',
        fontSize: '13px'
      }
      if (this.holeDealChainFlip) {
        base.animationDelay = (0.4 + ci * 0.09) + 's'
      } else {
        base.animationDelay = (ci * 0.08) + 's'
      }
      return base
    },
    onHoleDealFlyEnd(ev, ci) {
      if (!ev) return
      var name = ev.animationName || ''
      if (name.indexOf('hole-deal-fly') === -1) return
      if (this.holeDealFlyByIndex[ci]) {
        this.$delete(this.holeDealFlyByIndex, ci)
      }
      if (Object.keys(this.holeDealFlyByIndex).length === 0) {
        this.holeDealOriginByIndex = null
      }
    },
    onClick() {
      if (!this.player.leftThisHand) {
        this.$emit('card-click', this.player.nickname)
      }
    }
  }
}
</script>

<style src="../styles/dp-poker-cards.css"></style>
