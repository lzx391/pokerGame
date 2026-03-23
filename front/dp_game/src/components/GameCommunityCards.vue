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
import { getCardClass, getCardDisplay } from '../utils/dpGameCardVisual'

/** 与 game.vue 里公共牌翻转的间隔一致，飞入结束后再翻牌观感更顺 */
var DEAL_STAGGER_MS = 350

export default {
  name: 'GameCommunityCards',
  props: {
    communityCards: { type: Array, required: true },
    flipState: { type: Array, required: true }
  },
  data() {
    return {
      /** 仅本次新发的索引：{ 0: { delay: 0 }, 1: { delay: 350 } }，动画结束后清除 */
      dealFlyByIndex: {}
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
          return
        }
        if (newLen > oldLen) {
          if (this.prefersReducedMotion()) {
            this.dealFlyByIndex = {}
            return
          }
          for (var i = oldLen; i < newLen; i++) {
            next[i] = { delay: DEAL_STAGGER_MS * (i - oldLen) }
          }
          this.dealFlyByIndex = next
        }
      },
      immediate: true
    }
  },
  methods: {
    getCardClass,
    getCardDisplay,
    prefersReducedMotion() {
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
      return {
        animationDelay: (m.delay || 0) + 'ms',
        '--deal-from-x': slotShiftPx + 'px',
        '--deal-from-rot': rotDeg + 'deg',
        zIndex: 10 + cIdx
      }
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
