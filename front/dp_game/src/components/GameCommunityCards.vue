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

export default {
  name: 'GameCommunityCards',
  props: {
    communityCards: { type: Array, required: true },
    flipState: { type: Array, required: true }
  },
  computed: {
    emptySlots() {
      return Math.max(0, 5 - this.communityCards.length)
    }
  },
  methods: {
    getCardClass,
    getCardDisplay
  }
}
</script>

<style src="../styles/dp-poker-cards.css"></style>
<style src="../styles/dp-game-community-cards.css"></style>
