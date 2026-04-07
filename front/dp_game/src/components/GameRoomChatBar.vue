<template>
  <div
      class="dp-game-room-chat__bar"
      :class="barModifierClass"
      aria-label="房间聊天"
  >
    <input
        :value="value"
        type="text"
        maxlength="200"
        placeholder="说一句…"
        class="dp-game-room-chat__input"
        aria-label="房间聊天输入"
        @input="$emit('input', $event.target.value)"
        @keydown.enter.prevent="$emit('send')"
    >
    <button
        type="button"
        class="dp-game-room-chat__send"
        @click="$emit('send')"
    >
      发送
    </button>
  </div>
</template>

<script>
export default {
  name: 'GameRoomChatBar',
  props: {
    /** v-model（Vue 2：value + input） */
    value: { type: String, default: '' },
    /** default：仅 `dp-game-room-chat__bar`；hero / mobile 对应原 modifier 类名 */
    variant: {
      type: String,
      default: 'default',
      validator: function (v) {
        return ['default', 'hero', 'mobile'].indexOf(v) !== -1
      }
    }
  },
  computed: {
    barModifierClass: function () {
      if (this.variant === 'hero') return 'dp-game-room-chat__bar--hero-row'
      if (this.variant === 'mobile') return 'dp-game-room-chat__bar--mobile-dock'
      return null
    }
  }
}
</script>
