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
    <div v-if="gameUiTheme === 'custom'" class="dp-theme-picker__custom">
      <span class="dp-game-theme-row__label">基于</span>
      <select
        class="dp-game-theme-select dp-theme-picker__base"
        aria-label="自定义主题所基于的预设"
        :value="customThemeBase"
        @change="$emit('custom-base', $event.target.value)"
      >
        <option v-for="t in baseThemeOptions" :key="t.id" :value="t.id">{{ t.label }}</option>
      </select>
      <label class="dp-theme-picker__accent-wrap">
        <span class="dp-theme-picker__accent-label">强调色</span>
        <input
          class="dp-theme-picker__color"
          type="color"
          :value="accentForInput"
          @input="$emit('custom-accent', $event.target.value)"
        />
      </label>
    </div>
  </div>
</template>

<script>
import { normalizeAccentHex } from '@/utils/dpGameCustomTheme'

export default {
  name: 'DpThemePicker',
  props: {
    gameUiTheme: { type: String, required: true },
    themeOptions: { type: Array, required: true },
    customThemeBase: { type: String, default: 'default' },
    customAccent: { type: String, default: '#1890ff' },
    ariaLabel: { type: String, default: '选择界面主题' }
  },
  computed: {
    baseThemeOptions() {
      return this.themeOptions.filter(function (t) {
        return t.id !== 'custom'
      })
    },
    accentForInput() {
      return normalizeAccentHex(this.customAccent) || '#1890ff'
    }
  }
}
</script>

<style scoped>
.dp-theme-picker__custom {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  margin-top: 8px;
}
.dp-theme-picker__accent-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}
.dp-theme-picker__accent-label {
  font-size: 13px;
  color: var(--dp-text-secondary, #666);
}
.dp-theme-picker__color {
  width: 36px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--dp-input-border, #d9d9d9);
  border-radius: 4px;
  cursor: pointer;
  background: var(--dp-input-bg, #fff);
}
</style>
