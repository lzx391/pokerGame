<template>
  <div class="dp-theme-picker">
    <!-- 与 dp-game-themes.css 选择器一致，用于读取「基于」模板的默认色值 -->
    <div
      ref="themeSampler"
      class="dp-game-root dp-theme-picker__sampler"
      :data-dp-game-theme="customThemeBase"
      aria-hidden="true"
    />
    <select
      class="dp-game-theme-select"
      :aria-label="ariaLabel"
      :value="gameUiTheme"
      @change="$emit('input-theme', $event.target.value)"
    >
      <option v-for="t in themeOptions" :key="t.id" :value="t.id">{{ t.label }}</option>
    </select>
    <div v-if="gameUiTheme === 'custom'" class="dp-theme-picker__custom">
      <div class="dp-theme-picker__intro">
        <span class="dp-game-theme-row__label">基于模板</span>
        <select
          class="dp-game-theme-select dp-theme-picker__base"
          aria-label="自定义主题所基于的预设配色"
          :value="customThemeBase"
          @change="onBaseChange($event.target.value)"
        >
          <option v-for="t in baseThemeOptions" :key="t.id" :value="t.id">{{ t.label }}</option>
        </select>
        <button
          type="button"
          class="dp-theme-picker__collapse"
          :aria-expanded="customEditorExpanded ? 'true' : 'false'"
          aria-controls="dp-theme-picker-palette"
          @click="customEditorExpanded = !customEditorExpanded"
        >
          {{ customEditorExpanded ? '收起配色' : '展开配色' }}
        </button>
        <span v-show="customEditorExpanded" class="dp-theme-picker__intro-hint">
          未改动的项跟模板走。标有「统一」的条目会把多个变量设成同一颜色；预览取组内你改过或第一项。
        </span>
      </div>
      <div
        v-show="customEditorExpanded"
        id="dp-theme-picker-palette"
        class="dp-theme-picker__palette"
      >
        <section
          v-for="group in colorGroups"
          :key="group.id"
          class="dp-theme-picker__group"
        >
          <h4 class="dp-theme-picker__group-title">{{ group.label }}</h4>
          <div v-for="row in group.rows" :key="row.keys.join('|')" class="dp-theme-picker__row">
            <div class="dp-theme-picker__row-head">
              <span class="dp-theme-picker__row-label" :title="row.keys.join(', ')">{{ row.label }}</span>
              <button
                type="button"
                class="dp-theme-picker__reset"
                :disabled="!rowIsOverridden(row)"
                @click="resetRow(row)"
              >
                恢复模板
              </button>
            </div>
            <p v-if="rowSamplerNote(row)" class="dp-theme-picker__row-note">
              {{ rowSamplerNote(row) }}
            </p>
            <div class="dp-theme-picker__row-controls">
              <label class="dp-theme-picker__color-hit">
                <input
                  class="dp-theme-picker__color"
                  type="color"
                  :value="rowHex(row)"
                  :aria-label="row.label + ' 颜色'"
                  @input="onRowHex(row, $event.target.value)"
                />
              </label>
              <div class="dp-theme-picker__alpha-wrap">
                <span class="dp-theme-picker__alpha-label">透明</span>
                <input
                  class="dp-theme-picker__alpha"
                  type="range"
                  min="0"
                  max="100"
                  :value="rowAlphaPct(row)"
                  :aria-label="row.label + ' 透明度'"
                  @input="onRowAlpha(row, Number($event.target.value))"
                />
                <span class="dp-theme-picker__alpha-val">{{ rowAlphaPct(row) }}%</span>
              </div>
            </div>
          </div>
        </section>
      </div>
      <div v-show="customEditorExpanded" class="dp-theme-picker__footer">
        <button type="button" class="dp-theme-picker__clear-btn" @click="clearAllOverrides">
          清除全部自定义颜色
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import {
  DP_CUSTOM_THEME_PALETTE_GROUPS,
  dpCustomPaletteFlatKeys
} from '@/constants/dpGameThemeColorTokens'
import { normalizeOverrides } from '@/utils/dpGameCustomTheme'
import {
  parseCssColor,
  formatCssColor,
  cssToHexInput,
  cssToAlphaPercent,
  colorsCssEquivalent,
  isNonParsableColorToken
} from '@/utils/dpGameThemeColorUi'

export default {
  name: 'DpThemePicker',
  props: {
    gameUiTheme: { type: String, required: true },
    themeOptions: { type: Array, required: true },
    customThemeBase: { type: String, default: 'default' },
    customThemeOverrides: {
      type: Object,
      default: function () {
        return {}
      }
    },
    ariaLabel: { type: String, default: '选择界面主题' }
  },
  data: function () {
    return {
      colorGroups: DP_CUSTOM_THEME_PALETTE_GROUPS,
      samplerMap: {},
      /** 自定义模式下大块配色编辑区默认收起，需点「展开配色」 */
      customEditorExpanded: false
    }
  },
  computed: {
    baseThemeOptions() {
      return this.themeOptions.filter(function (t) {
        return t.id !== 'custom'
      })
    }
  },
  watch: {
    customThemeBase: function () {
      this.refreshSampler()
    },
    gameUiTheme: function (v) {
      if (v === 'custom') {
        this.customEditorExpanded = false
        this.refreshSampler()
      }
    }
  },
  mounted: function () {
    if (this.gameUiTheme === 'custom') this.refreshSampler()
  },
  methods: {
    onBaseChange: function (baseId) {
      this.$emit('custom-base', baseId)
      this.refreshSampler()
    },
    refreshSampler: function () {
      var self = this
      this.$nextTick(function () {
        if (self.gameUiTheme !== 'custom') return
        var el = self.$refs.themeSampler
        if (!el) return
        var cs = getComputedStyle(el)
        var keys = dpCustomPaletteFlatKeys()
        var map = {}
        for (var i = 0; i < keys.length; i++) {
          var k = keys[i]
          map[k] = cs.getPropertyValue(k).trim()
        }
        self.samplerMap = map
      })
    },
    effectiveCss: function (key) {
      var o = this.customThemeOverrides || {}
      if (o[key]) return o[key]
      return this.samplerMap[key] || ''
    },
    /** 合并行：优先用组内已有覆盖项决定预览，否则用第一项 */
    rowDisplayKey: function (row) {
      var o = this.customThemeOverrides || {}
      for (var i = 0; i < row.keys.length; i++) {
        if (o[row.keys[i]]) return row.keys[i]
      }
      return row.keys[0]
    },
    rowEffectiveCss: function (row) {
      return this.effectiveCss(this.rowDisplayKey(row))
    },
    rowHex: function (row) {
      return cssToHexInput(this.rowEffectiveCss(row))
    },
    rowAlphaPct: function (row) {
      return cssToAlphaPercent(this.rowEffectiveCss(row))
    },
    rowIsOverridden: function (row) {
      var o = this.customThemeOverrides || {}
      for (var i = 0; i < row.keys.length; i++) {
        if (o[row.keys[i]]) return true
      }
      return false
    },
    rowSamplerNote: function (row) {
      for (var i = 0; i < row.keys.length; i++) {
        var raw = this.samplerMap[row.keys[i]]
        if (raw && isNonParsableColorToken(raw)) {
          return '模板为非纯色，仍可覆盖'
        }
      }
      return ''
    },
    emitOverrides: function (next) {
      this.$emit('custom-overrides', normalizeOverrides(next))
    },
    onRowHex: function (row, hex) {
      var cur = parseCssColor(this.rowEffectiveCss(row))
      var a = cur ? cur.a : 1
      var rgb = parseCssColor(hex)
      if (!rgb) return
      var css = formatCssColor(rgb, a)
      this.applyRowCss(row, css)
    },
    onRowAlpha: function (row, pct) {
      var p = Number(pct)
      if (!isFinite(p)) return
      p = Math.max(0, Math.min(100, p))
      var hexIn = cssToHexInput(this.rowEffectiveCss(row))
      var rgb = parseCssColor(hexIn)
      if (!rgb) rgb = { r: 136, g: 136, b: 136 }
      var css = formatCssColor(rgb, p / 100)
      this.applyRowCss(row, css)
    },
    applyRowCss: function (row, css) {
      var self = this
      var ks = row.keys
      var next = Object.assign({}, this.customThemeOverrides || {})
      var allMatch = ks.every(function (k) {
        var sv = self.samplerMap[k]
        if (sv == null || String(sv).trim() === '') return false
        return colorsCssEquivalent(css, sv)
      })
      if (allMatch) {
        ks.forEach(function (k) {
          delete next[k]
        })
      } else {
        ks.forEach(function (k) {
          next[k] = css
        })
      }
      this.emitOverrides(next)
    },
    resetRow: function (row) {
      var next = Object.assign({}, this.customThemeOverrides || {})
      for (var i = 0; i < row.keys.length; i++) {
        delete next[row.keys[i]]
      }
      this.emitOverrides(next)
    },
    clearAllOverrides: function () {
      this.emitOverrides({})
    }
  }
}
</script>

<style scoped>
.dp-theme-picker {
  position: relative;
  min-width: 0;
  /* 桌面：随内容宽；窄屏由 dp-lobby-shell 设为 width:100% 铺满 */
  flex: 0 1 auto;
  max-width: 100%;
}
.dp-theme-picker__sampler {
  position: absolute;
  width: 0;
  height: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
  pointer-events: none;
}
.dp-theme-picker__custom {
  flex: 1 1 100%;
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.dp-theme-picker__intro {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
}
.dp-theme-picker__intro-hint {
  flex: 1 1 100%;
  font-size: 12px;
  line-height: 1.4;
  color: var(--dp-text-muted, #888);
}
.dp-theme-picker__collapse {
  font-size: 12px;
  padding: 5px 10px;
  border-radius: 6px;
  border: 1px solid var(--dp-input-border, #ccc);
  background: var(--dp-panel-bg, #fff);
  color: var(--dp-accent, #1890ff);
  cursor: pointer;
  white-space: nowrap;
}
.dp-theme-picker__collapse:hover {
  border-color: var(--dp-accent, #1890ff);
}
.dp-theme-picker__palette {
  /* svh：移动端地址栏收起前后更稳；dvh：动态视口；clamp 保底高度避免「框太小划不动」 */
  max-height: min(
    clamp(220px, 58svh, 520px),
    min(58dvh, 520px)
  );
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  min-height: 0;
  padding: clamp(6px, 1.5vw, 10px) clamp(4px, 1.2vw, 8px) clamp(8px, 2vw, 12px) 0;
  border: 1px solid var(--dp-subpanel-border, rgba(0, 0, 0, 0.08));
  border-radius: clamp(8px, 2vw, 12px);
  background: var(--dp-subpanel-bg, rgba(0, 0, 0, 0.02));
}

@media (min-width: 768px) {
  .dp-theme-picker__palette {
    max-height: min(
      clamp(260px, 52svh, 560px),
      min(52dvh, 560px)
    );
  }
}
.dp-theme-picker__group {
  margin-bottom: 14px;
}
.dp-theme-picker__group:last-child {
  margin-bottom: 4px;
}
.dp-theme-picker__group-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--dp-text-secondary, #666);
}
.dp-theme-picker__row {
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--dp-subpanel-border, rgba(0, 0, 0, 0.06));
  font-size: 12px;
}
.dp-theme-picker__row:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}
.dp-theme-picker__row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.dp-theme-picker__row-label {
  color: var(--dp-text-primary, #333);
  font-weight: 500;
}
.dp-theme-picker__row-note {
  margin: 0 0 6px;
  font-size: 11px;
  color: var(--dp-warning, #b45309);
}
.dp-theme-picker__row-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
}
.dp-theme-picker__color-hit {
  display: flex;
  align-items: center;
}
.dp-theme-picker__color {
  width: 40px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--dp-input-border, #d9d9d9);
  border-radius: 6px;
  cursor: pointer;
  background: var(--dp-input-bg, #fff);
}
.dp-theme-picker__alpha-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.dp-theme-picker__alpha-label {
  font-size: 11px;
  color: var(--dp-text-muted, #888);
  flex-shrink: 0;
}
.dp-theme-picker__alpha {
  flex: 1;
  min-width: 56px;
  height: 6px;
  accent-color: var(--dp-accent, #1890ff);
}
.dp-theme-picker__alpha-val {
  font-size: 11px;
  color: var(--dp-text-muted, #888);
  width: 34px;
  text-align: right;
  flex-shrink: 0;
}
.dp-theme-picker__reset {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid var(--dp-input-border, #ccc);
  background: var(--dp-panel-bg, #fff);
  color: var(--dp-text-secondary, #666);
  cursor: pointer;
  white-space: nowrap;
}
.dp-theme-picker__reset:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-theme-picker__reset:not(:disabled):hover {
  border-color: var(--dp-accent, #1890ff);
  color: var(--dp-accent, #1890ff);
}
.dp-theme-picker__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.dp-theme-picker__clear-btn {
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 4px;
  border: 1px solid var(--dp-input-border, #d9d9d9);
  background: var(--dp-panel-bg, #fff);
  color: var(--dp-text-secondary, #666);
  cursor: pointer;
}
.dp-theme-picker__clear-btn:hover {
  border-color: var(--dp-danger, #c62828);
  color: var(--dp-danger, #c62828);
}
</style>
