<template>
  <div
    :class="{ 'player-card--win-streak': !player.leftThisHand && (player.winStreak || 0) >= 2 }"
    :style="boxStyle"
    v-bind="dealerAnchorAttrs"
    @click="onClick"
  >
    <div style="display:flex; flex-wrap:wrap; gap:5px; margin-bottom:5px; align-items:center;">
      <span
        v-if="player.dealer"
        style="background:#faad14; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;"
      >D</span>
      <span
        v-if="player.blind === 1"
        style="background:#722ed1; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;"
      >SB</span>
      <span
        v-if="player.blind === 2"
        style="background:#52c41a; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;"
      >BB</span>
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
      <div style="font-weight:bold; color:#8c8c8c; font-size:14px;">
        该玩家已离线
      </div>
      <div style="margin-top:8px; font-size:12px; color:#bfbfbf;">
        座位保留至本局结束，行动顺序不变
      </div>
    </template>

    <template v-else>
      <div style="font-weight:bold;">
        {{ player.nickname }}
        <span v-if="isMe" style="color:#1890ff;">(我)</span>
      </div>

      <div style="margin-top: 8px; display: flex; flex-direction: column; gap: 4px;">
        <div
          style="font-size: 13px; color: #555; display: flex; align-items: center; justify-content: center; background: #f8f9fa; border-radius: 4px; padding: 2px 0;"
        >
          <span style="color: #8c8c8c; margin-right: 4px;">剩余积分:</span>
          <span style="font-weight: 800; font-family: monospace; color: #2f3542;">{{ player.chips }}</span>
        </div>

        <div
          style="background: #fff2f0; border: 1px solid #ffccc7; border-radius: 6px; padding: 4px 0; text-align: center;"
        >
          <div
            style="font-size: 11px; color: #ff4d4f; text-transform: uppercase; font-weight: bold; letter-spacing: 0.5px;"
          >
            本轮积分
          </div>
          <div style="font-size: 16px; color: #cf1322; font-weight: 900; font-family: 'Arial Black', sans-serif;">
            {{ player.bet }}
          </div>
        </div>
      </div>

      <div
        ref="holeCardsRow"
        style="display:flex; gap:5px; margin:8px 0; justify-content:center;"
      >
        <template
          v-if="
            isMe
              || (isOwner && ownerRevealAll && player.holeCards && player.holeCards.length > 0)
              || ((stage === 'showdown' || stage === 'settled') && !player.fold)
          "
        >
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

      <div
        v-if="
          communityCards.length >= 3
            && communityCardsFlipComplete
            && (isMe
              || (isOwner && ownerRevealAll && player.holeCards && player.holeCards.length > 0)
              || ((stage === 'showdown' || stage === 'settled') && !player.fold))
        "
        style="margin-top:4px; text-align:center;"
      >
        <template v-if="isMe || (isOwner && ownerRevealAll && player.holeCards && player.holeCards.length > 0)">
          <span
            style="background:#e6f7ff; color:#1890ff; padding:3px 10px; border-radius:4px; font-weight:bold; font-size:12px; display:inline-block;"
          >
            {{ getHandRank(player.holeCards, communityCards) }}
          </span>
          <div
            v-if="player.bestHandCards && player.bestHandCards.length === 5"
            class="best-hand-cards"
            style="display:flex; gap:4px; justify-content:center; margin-top:6px; flex-wrap:wrap;"
          >
            <div
              v-for="(c, ci) in player.bestHandCards"
              :key="'best' + ci"
              :class="[getCardClass(c), 'best-hand-card', 'best-hand-card-enter']"
              :style="{
                width: '32px',
                height: '46px',
                fontSize: '11px',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: '4px',
                animationDelay: (ci * 0.07) + 's'
              }"
            >
              {{ getCardDisplay(c) }}
            </div>
          </div>
        </template>
        <template v-else-if="(stage === 'showdown' || stage === 'settled') && !player.fold">
          <span
            style="background:#f6ffed; color:#52c41a; padding:3px 10px; border-radius:4px; font-weight:bold; font-size:12px; display:inline-block;"
          >
            {{ getHandRank(player.holeCards, communityCards) }}
          </span>
          <div
            v-if="player.bestHandCards && player.bestHandCards.length === 5"
            class="best-hand-cards"
            style="display:flex; gap:4px; justify-content:center; margin-top:6px; flex-wrap:wrap;"
          >
            <div
              v-for="(c, ci) in player.bestHandCards"
              :key="'best' + ci"
              :class="[getCardClass(c), 'best-hand-card', 'best-hand-card-enter']"
              :style="{
                width: '32px',
                height: '46px',
                fontSize: '11px',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: '4px',
                animationDelay: (ci * 0.07) + 's'
              }"
            >
              {{ getCardDisplay(c) }}
            </div>
          </div>
        </template>
      </div>

      <div
        style="font-weight:bold; font-size:12px; margin-top:4px;"
        :style="{ color: player.fold ? '#ff4d4f' : (actIndex === seatIndex ? '#faad14' : '#52c41a') }"
      >
        {{ player.fold ? '已弃牌' : (actIndex === seatIndex ? '思考中...' : '进行中') }}
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
