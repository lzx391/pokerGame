<template>
  <div class="community-cards-row">
    <div
      v-for="(c, cIdx) in communityCards"
      :key="'cc-' + cIdx + '-' + c"
      class="card-flip-wrapper"
      :class="{ 'deal-fly-in': dealFlyActive(cIdx) }"
      :style="dealFlyStyle(cIdx)"
      @animationend="onDealFlyAnimationEnd($event, cIdx)"
    >
      <div class="card-flip-inner" :class="{ flipped: flipState[cIdx] }">
        <div class="card-face card-back card-base bg-gray">?</div>
        <div class="card-face card-front" :class="getCardClass(c)">
          {{ getCardDisplay(c) }}
        </div>
      </div>
    </div>
    <div v-for="i in emptySlots" :key="'e' + i" class="card-base bg-gray community-card-placeholder">?</div>
  </div>
</template>

<script>
import { getDealerAnchorViewportPoint } from '../utils/dpGameDealerAnchor'
import { getCardClass, getCardDisplay } from '../utils/dpGameCardVisual'
import { DP_DEAL_STAGGER_MS } from '../constants/dpGameDealTiming'

export default {
  name: 'GameCommunityCards',
  inject: {
    dpGameView: { default: null }
  },
  props: {
    communityCards: { type: Array, required: true },
    flipState: { type: Array, required: true }
  },
  data() {
    return {
      /** 仅本次新发的索引：{ 0: { delay: 0 }, 1: { delay: 350 } }，动画结束后清除 */
      dealFlyByIndex: {},
      /** 自庄位到各新发公共牌槽的位移(px)，测不到庄位时为 null 走 CSS 默认 */
      dealOriginByIndex: null
    }
  },
  computed: {
    emptySlots() {
      return Math.max(0, 5 - this.communityCards.length)
    }
  },
  watch: {
    communityCards: {
      handler(newArr, oldArr) {
        var oldLen = oldArr && oldArr.length ? oldArr.length : 0
        var newLen = newArr.length
        var next = {}
        if (newLen < oldLen) {
          this.dealFlyByIndex = next
          this.dealOriginByIndex = null
          return
        }
        if (newLen > oldLen) {
          if (this.prefersReducedMotion()) {
            this.dealFlyByIndex = {}
            this.dealOriginByIndex = null
            return
          }
          var self = this
          this.$nextTick(function () {
            self.dealOriginByIndex = self.computeDealOriginsFromDealer(oldLen, newLen)
            var n2 = {}
            for (var i = oldLen; i < newLen; i++) {
              n2[i] = { delay: DP_DEAL_STAGGER_MS * (i - oldLen) }
            }
            self.dealFlyByIndex = n2
          })
        }
      },
      immediate: true
    }
  },
  methods: {
    getCardClass,
    getCardDisplay,
    prefersReducedMotion() {
      if (this.dpGameView && this.dpGameView.ecoMode) return true
      if (typeof window === 'undefined' || !window.matchMedia) return false
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    dealFlyActive(cIdx) {
      return !!this.dealFlyByIndex[cIdx]
    },
    dealFlyStyle(cIdx) {
      var m = this.dealFlyByIndex[cIdx]
      if (!m) return {}
      var center = 2
      var slotShiftPx = (center - cIdx) * 30
      var rotDeg = (cIdx - center) * 3
      var origin = this.dealOriginByIndex && this.dealOriginByIndex[cIdx]
      if (origin) {
        return {
          animationDelay: (m.delay || 0) + 'ms',
          '--deal-from-x': origin.x + 'px',
          '--deal-from-y': origin.y + 'px',
          '--deal-from-rot': rotDeg + 'deg',
          zIndex: 10 + cIdx
        }
      }
      return {
        animationDelay: (m.delay || 0) + 'ms',
        '--deal-from-x': slotShiftPx + 'px',
        '--deal-from-y': '92px',
        '--deal-from-rot': rotDeg + 'deg',
        zIndex: 10 + cIdx
      }
    },
    /**
     * 以庄位（data-dp-dealer-anchor 可见时的卡片中心；不可见时见 dpGameDealerAnchor 回退）为发牌起点。
     */
    computeDealOriginsFromDealer(oldLen, newLen) {
      if (typeof document === 'undefined' || !this.$el) return null
      var wrappers = this.$el.querySelectorAll('.card-flip-wrapper')
      if (!wrappers || wrappers.length === 0) return null
      var anchor = getDealerAnchorViewportPoint()
      if (!anchor) return null
      var dcx = anchor.x
      var dcy = anchor.y
      var map = {}
      for (var i = oldLen; i < newLen; i++) {
        var el = wrappers[i]
        if (!el) continue
        var r = el.getBoundingClientRect()
        map[i] = {
          x: dcx - (r.left + r.width / 2),
          y: dcy - (r.top + r.height / 2)
        }
      }
      return Object.keys(map).length ? map : null
    },
    onDealFlyAnimationEnd(ev, cIdx) {
      if (!ev) return
      var name = ev.animationName || ''
      if (name.indexOf('community-deal-fly') === -1) return
      if (this.dealFlyByIndex[cIdx]) {
        this.$delete(this.dealFlyByIndex, cIdx)
      }
    }
  }
}
</script>

<style src="../styles/dp-poker-cards.css"></style>
<style src="../styles/dp-game-community-cards.css"></style>
