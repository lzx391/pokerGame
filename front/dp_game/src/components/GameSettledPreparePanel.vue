<template>
  <div
    style="margin-top:15px; background:#fff; padding:12px; border-radius:8px; box-shadow:0 1px 4px rgba(0,0,0,0.06);"
  >
    <div style="font-size:14px; font-weight:bold; text-align:center; margin-bottom:8px; color:#333;">
      本局已结算，请准备下一局（约30秒后未准备的玩家将被移到观众席）
    </div>
    <div style="display:flex; justify-content:center; align-items:center; gap:10px; margin-bottom:8px;">
      <div
        style="display:flex; align-items:center; justify-content:center;
               width:32px; height:32px;
               background:#ffffff;
               border:2px solid #000000;
               border-radius:50%;
               flex-shrink:0; box-sizing:border-box;"
      >
        <span
          style="color:#ff4d4f; font-size:14px; font-weight:900; font-family:'Arial Black', sans-serif; line-height:1;"
        >
          {{ readyTimeLeft }}
        </span>
      </div>
      <span style="font-size:12px; color:#999;">准备倒计时</span>
    </div>
    <div style="text-align:center; font-size:13px; color:#666; margin-bottom:8px;">
      当前积分：<span style="font-weight:bold; color:#1890ff;">{{ myChips }}</span>
    </div>
    <div style="display:flex; justify-content:center; gap:10px; flex-wrap:wrap;">
      <button
        type="button"
        :disabled="myChips < bigBlind"
        style="padding:8px 16px; border:none; border-radius:5px; cursor:pointer; font-weight:bold;
               background: #52c41a; color:#fff;"
        @click="$emit('toggle-ready')"
      >
        {{ myReady ? '取消准备' : (myChips >= bigBlind ? '准备下一局' : ('积分不足大盲(' + bigBlind + ')，无法准备')) }}
      </button>
      <button
        v-if="myChips < bigBlind"
        type="button"
        style="padding:8px 16px; border:none; border-radius:5px; cursor:pointer; font-weight:bold;
               background:#fa8c16; color:#fff;"
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
