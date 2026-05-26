<template>
  <div class="dp-settled-prepare-bar">
    <!-- 与 GameTopBar「下一局加入对局」同款 dp-btn + success/ghost，不塞进行动大盘 -->
    <template v-if="!myReady">
      <p class="dp-settled-prepare-bar__hint">
        已进入结算阶段，请选择是否继续下一局（约 30 秒未操作将移至观众席）
      </p>
    </template>
    <div class="dp-settled-prepare-bar__actions">
      <button
          type="button"
          class="dp-btn dp-top-bar__btn dp-settled-prepare-bar__btn"
          :class="primaryBtnClass"
          :disabled="!myReady && myChips < bigBlind"
          @click="$emit('toggle-ready')"
      >
        {{ primaryLabel }}
      </button>
      <button
          v-if="!myReady && myChips < bigBlind"
          type="button"
          class="dp-btn dp-btn--rebuy dp-top-bar__btn dp-settled-prepare-bar__btn"
          @click="$emit('rebuy')"
      >
        补满至初始小鱼干
      </button>
    </div>
  </div>
</template>

<script>
import { CAT_COPY } from '../constants/dpCatThemeCopy'

export default {
  name: 'GameSettledPrepareBar',
  props: {
    myReady: { type: Boolean, default: false },
    readyTimeLeft: { type: Number, default: 0 },
    myChips: { type: Number, default: 0 },
    bigBlind: { type: Number, default: 10 }
  },
  computed: {
    primaryLabel() {
      var bb = Number(this.bigBlind)
      if (this.myChips < bb) {
        return '小鱼干不足' + CAT_COPY.bigBlindFish + '(' + bb + ')，无法准备'
      }
      if (this.myReady) return '取消准备'
      var t = Math.max(0, Math.floor(Number(this.readyTimeLeft)))
      return '准备 (' + t + 's)'
    },
    primaryBtnClass() {
      var low = !this.myReady && this.myChips < Number(this.bigBlind)
      var tone = this.myReady ? 'dp-btn--ghost' : 'dp-btn--success'
      return [tone, { 'dp-settled-prepare-bar__btn--dim': low }]
    }
  }
}
</script>
