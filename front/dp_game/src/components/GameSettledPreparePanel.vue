<template>
  <div class="dp-settled-panel">
    <div class="dp-settled-panel__title">
      本局已结算，请准备下一局（约30秒后未准备的玩家将被移到观众席）
    </div>
    <div class="dp-settled-panel__count-row">
      <div class="dp-timer-ring dp-timer-ring--sm">
        <span class="dp-timer-ring__text">{{ readyTimeLeft }}</span>
      </div>
      <span class="dp-settled-panel__count-label">准备倒计时</span>
    </div>
    <div class="dp-settled-panel__chips">
      当前积分：<strong>{{ myChips }}</strong>
    </div>
    <div class="dp-settled-panel__actions">
      <button
        type="button"
        class="dp-btn--success"
        :disabled="myChips < bigBlind"
        :style="{ opacity: myChips < bigBlind ? 0.45 : 1 }"
        @click="$emit('toggle-ready')"
      >
        {{ myReady ? '取消准备' : (myChips >= bigBlind ? '准备下一局' : ('积分不足大盲(' + bigBlind + ')，无法准备')) }}
      </button>
      <button
        v-if="myChips < bigBlind"
        type="button"
        class="dp-btn--rebuy"
        @click="$emit('rebuy')"
      >
        补码到初始积分
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GameSettledPreparePanel',
  props: {
    readyTimeLeft: { type: Number, required: true },
    myChips: { type: Number, required: true },
    bigBlind: { type: Number, required: true },
    myReady: { type: Boolean, default: false }
  }
}
</script>
