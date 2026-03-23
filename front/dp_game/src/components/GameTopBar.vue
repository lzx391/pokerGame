<template>
  <div class="dp-top-bar">
    <div>
      <div class="dp-top-bar__title">
        房间: {{ roomId }} | 阶段: <span class="dp-top-bar__accent">{{ stageLabel }}</span>
      </div>
      <div class="dp-top-bar__sub">
        底池: <span class="dp-top-bar__pot">{{ pot }}</span>
        | 当前跟注额: <span style="font-weight:bold;">{{ currentBetToCall }}</span>
      </div>
    </div>
    <div class="dp-top-bar__actions">
      <button type="button" class="dp-btn dp-btn--primary" @click="$emit('show-hand-rank')">
        牌型说明
      </button>
      <button
        v-if="spectatorCount > 0"
        type="button"
        class="dp-btn dp-btn--cyan"
        style="padding:8px 10px; font-size:12px;"
        @click="$emit('show-spectators')"
      >
        观众席（{{ spectatorCount }}）
      </button>
      <button type="button" class="dp-btn dp-btn--danger" @click="$emit('exit')">
        退出对局
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GameTopBar',
  props: {
    roomId: { type: String, required: true },
    stageLabel: { type: String, required: true },
    pot: { type: Number, required: true },
    currentBetToCall: { type: Number, required: true },
    spectatorCount: { type: Number, default: 0 }
  }
}
</script>
