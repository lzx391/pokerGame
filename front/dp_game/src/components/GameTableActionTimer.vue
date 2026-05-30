<template>
  <div
      class="dp-game-table-action-timer dp-game-table-action-timer--compact"
      :class="[
        'dp-game-table-action-timer--' + urgency,
        { 'dp-game-table-action-timer--rich': !ecoMode && !ringOnly },
        { 'dp-game-table-action-timer--ring-only': ringOnly }
      ]"
      role="status"
      aria-live="polite"
      :aria-label="'当前行动 ' + actorName + '，剩余 ' + timeLeft + ' 秒'"
  >
    <div
        class="dp-game-table-action-timer__inner"
        :class="{ 'dp-game-table-action-timer__inner--ring-only': ringOnly }"
    >
      <div
          class="dp-game-table-action-timer__ring"
          :style="{ '--dp-table-timer-pct': progressPct }"
      >
        <span class="dp-game-table-action-timer__sec">{{ timeLeft }}</span>
      </div>
      <div v-if="!ringOnly" class="dp-game-table-action-timer__meta">
        <span class="dp-game-table-action-timer__who">{{ actorName }}</span>
        <span class="dp-game-table-action-timer__hint">思考中</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GameTableActionTimer',
  props: {
    timeLeft: { type: Number, required: true },
    actorName: { type: String, default: '' },
    urgency: { type: String, default: 'normal' },
    /** CSS 变量 --dp-table-timer-pct，如 '0.73' 或 '73%' */
    progressPct: { type: [String, Number], default: '100%' },
    ecoMode: { type: Boolean, default: false },
    /** 仅圆环 + 秒数（无昵称/「思考中」），用于随座位轨道倒计时 */
    ringOnly: { type: Boolean, default: false }
  }
}
</script>
