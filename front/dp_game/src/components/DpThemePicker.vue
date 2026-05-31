<template>
  <div class="dp-theme-picker">
    <select
      class="dp-game-theme-select"
      :aria-label="ariaLabel"
      :value="gameUiTheme"
      @change="$emit('input-theme', $event.target.value)"
    >
      <option v-for="t in themeOptions" :key="t.id" :value="t.id">{{ t.label }}</option>
    </select>
  </div>
</template>

<script>
export default {
  name: 'DpThemePicker',
  props: {
    gameUiTheme: { type: String, required: true },
    themeOptions: { type: Array, required: true },
    ariaLabel: { type: String, default: '选择界面主题' }
  }
}
</script>

<style scoped>
.dp-theme-picker {
  position: relative;
  min-width: 0;
  flex: 0 1 auto;
  max-width: 100%;
}
</style>
