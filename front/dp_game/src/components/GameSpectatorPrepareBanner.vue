<template>
  <div class="dp-spectator-banner">
    <div>
      你当前正在<span class="dp-spectator-banner__accent">旁观本局</span>，不会参与下注和结算。
    </div>
    <button
      type="button"
      class="dp-spectator-banner__btn"
      :disabled="nextHandReady"
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
