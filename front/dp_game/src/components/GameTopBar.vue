<template>
  <div
    style="background:#fff; padding:15px; border-radius:10px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 2px 5px rgba(0,0,0,0.1); margin-bottom:15px;"
  >
    <div>
      <div style="font-size:18px; font-weight:bold;">
        房间: {{ roomId }} | 阶段: <span style="color:#1890ff;">{{ stageLabel }}</span>
      </div>
      <div style="font-size:14px; color:#666;">
        底池: <span style="color:#f5222d; font-weight:bold;">{{ pot }}</span>
        | 当前跟注额: <span style="font-weight:bold;">{{ currentBetToCall }}</span>
      </div>
    </div>
    <div style="display:flex; gap:8px;">
      <button
        type="button"
        style="background:#1890ff; color:#fff; border:none; padding:8px 15px; border-radius:5px; cursor:pointer; font-size:13px;"
        @click="$emit('show-hand-rank')"
      >
        牌型说明
      </button>
      <button
        v-if="spectatorCount > 0"
        type="button"
        style="background:#13c2c2; color:#fff; border:none; padding:8px 10px; border-radius:5px; cursor:pointer; font-size:12px;"
        @click="$emit('show-spectators')"
      >
        观众席（{{ spectatorCount }}）
      </button>
      <button
        type="button"
        style="background:#ff4d4f; color:#fff; border:none; padding:8px 15px; border-radius:5px; cursor:pointer;"
        @click="$emit('exit')"
      >
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
