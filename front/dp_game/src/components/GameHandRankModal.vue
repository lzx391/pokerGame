<template>
  <transition name="dp-overlay">
  <div v-if="visible" class="hand-rank-modal-mask" @click="$emit('close')">
    <div class="hand-rank-modal" role="dialog" aria-modal="true" aria-labelledby="dp-hand-rank-title" @click.stop>
      <div class="dp-game-dialog__head">
        <span id="dp-hand-rank-title" class="dp-game-dialog__title">牌型大小参考（从大到小）</span>
        <button
            type="button"
            class="dp-game-dialog__close"
            aria-label="关闭"
            @click="$emit('close')"
        >
          ×
        </button>
      </div>
      <div class="dp-game-dialog__body">
        <div class="hand-rank-list">
          <div v-for="(item, idx) in items" :key="idx" class="hand-rank-item">
            <span class="hand-rank-num">{{ idx + 1 }}</span>
            <span class="hand-rank-name">{{ item.name }}</span>
            <div class="hand-rank-cards">
              <div v-for="c in item.cards" :key="c" :class="getCardClass(c)" class="hand-rank-card">
                {{ getCardDisplay(c) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  </transition>
</template>

<script>
import { getCardClass, getCardDisplay } from '../utils/dpGameCardVisual'

export default {
  name: 'GameHandRankModal',
  props: {
    visible: { type: Boolean, default: false },
    items: { type: Array, required: true }
  },
  methods: {
    getCardClass,
    getCardDisplay
  }
}
</script>

<style src="../styles/dp-poker-cards.css"></style>
<style src="../styles/dp-game-modals.css"></style>
