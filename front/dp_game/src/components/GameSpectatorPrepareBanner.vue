<template>
  <div
    style="background:#fff; padding:10px 15px; border-radius:8px; margin-bottom:15px; text-align:center; font-size:13px;"
  >
    <div style="margin-bottom:8px;">
      你当前正在<span style="color:#1890ff;">旁观本局</span>，不会参与下注和结算。
    </div>
    <button
      type="button"
      :disabled="nextHandReady"
      style="padding:6px 14px; border:none; border-radius:4px; cursor:pointer; background:#52c41a; color:#fff; font-size:13px;"
      @click="$emit('ready-next-hand')"
    >
      {{ nextHandReady ? '已报名下一局，等待房主重新发牌' : '准备在下一局加入对局' }}
    </button>
  </div>
</template>

<script>
export default {
  name: 'GameSpectatorPrepareBanner',
  props: {
    nextHandReady: { type: Boolean, default: false }
  }
}
</script>
