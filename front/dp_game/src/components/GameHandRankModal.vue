<template>
  <div v-if="visible" class="hand-rank-modal-mask" @click="$emit('close')">
    <div class="hand-rank-modal" @click.stop>
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
        <span style="font-size:18px; font-weight:bold;">牌型大小参考（从大到小）</span>
        <button
          type="button"
          style="background:#d9d9d9; border:none; width:28px; height:28px; border-radius:4px; cursor:pointer; font-size:16px; line-height:1;"
          @click="$emit('close')"
        >×</button>
      </div>
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
