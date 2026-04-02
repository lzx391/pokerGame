<template>
  <div
      class="dp-game-sheet-mask dp-game-sheet-mask--bottom"
      role="dialog"
      aria-modal="true"
      :aria-label="ariaLabelComputed"
      @click.self="$emit('close')"
  >
    <div
        class="dp-game-sheet"
        :class="{ 'dp-game-sheet--wide': wide }"
        @click.stop
    >
      <div class="dp-game-sheet__head">
        <span class="dp-game-sheet__title">{{ title }}</span>
        <button
            type="button"
            class="dp-game-sheet__close"
            aria-label="关闭"
            @click="$emit('close')"
        >
          ×
        </button>
      </div>
      <div
          class="dp-game-sheet__body"
          :class="bodyExtraClass"
      >
        <slot />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GameBottomSheet',
  props: {
    title: { type: String, required: true },
    /** 不传则用 title，供无障碍 */
    ariaLabel: { type: String, default: '' },
    wide: { type: Boolean, default: false },
    /** 追加到 body：`action` | `owner-hub` | 空 */
    bodyModifier: {
      type: String,
      default: '',
      validator: function (v) {
        return v === '' || v === 'action' || v === 'owner-hub'
      }
    }
  },
  computed: {
    ariaLabelComputed: function () {
      return this.ariaLabel || this.title
    },
    bodyExtraClass: function () {
      if (this.bodyModifier === 'action') return 'dp-game-sheet__body--action'
      if (this.bodyModifier === 'owner-hub') return 'dp-game-sheet__body--owner-hub'
      return null
    }
  }
}
</script>
