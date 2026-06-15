<template>
  <div class="dp-owner-npc-console">
    <ul class="dp-console-menu" role="listbox" aria-label="选择 NPC 类型与数量">
      <li
        v-for="(row, i) in rows"
        :key="row.id"
        role="option"
        :aria-selected="i === selectedIndex ? 'true' : 'false'"
        class="dp-console-menu__row"
        :class="rowClass(i)"
        @click="onRowTap(i)"
      >
        <div class="dp-console-menu__left">
          <span class="dp-console-menu__cursor" aria-hidden="true">&gt;</span>
          <span class="dp-console-menu__label" :style="labelStyle(row, i)">{{ rowLabel(row) }}</span>
        </div>
        <div class="dp-console-menu__right">
          <span class="dp-console-menu__adj">
            <button
              type="button"
              class="dp-console-menu__chev"
              :aria-label="rowLabel(row) + ' 减少'"
              :disabled="disabled || rowCount(row) <= 0"
              @click.stop="onAdjust(row, -1)"
            >
              &lt;
            </button>
            <button
              type="button"
              class="dp-console-menu__value-btn"
              :aria-label="rowLabel(row) + ' 当前数量'"
              :disabled="disabled"
              @click.stop="onAdjust(row, 1)"
            >
              {{ rowCount(row) }}
            </button>
            <button
              type="button"
              class="dp-console-menu__chev"
              :aria-label="rowLabel(row) + ' 增加'"
              :disabled="disabled || rowCount(row) >= countMax"
              @click.stop="onAdjust(row, 1)"
            >
              &gt;
            </button>
          </span>
        </div>
      </li>
    </ul>
    <p v-if="tip" class="dp-owner-npc-console__tip" role="alert">{{ tip }}</p>
    <p class="dp-console-footer" aria-hidden="true">
      <span class="dp-console-footer__touch">tap row · tap &lt;&gt; · NEXT below</span>
    </p>
  </div>
</template>

<script>
import '@/styles/dp-create-room-console.css'
import { shouldSkipRetroEnterEffects } from '@shared/utils/dpRetroEnterGameHandoff'

export default {
  name: 'DpOwnerNpcConsole',
  props: {
    rows: {
      type: Array,
      default: function () {
        return []
      }
    },
    counts: {
      type: Object,
      default: function () {
        return {}
      }
    },
    selectedIndex: { type: Number, default: 0 },
    countMax: { type: Number, default: 9 },
    disabled: { type: Boolean, default: false },
    tip: { type: String, default: '' }
  },
  computed: {
    useRowBlink: function () {
      return !shouldSkipRetroEnterEffects()
    }
  },
  methods: {
    rowLabel: function (row) {
      if (row.archetype) return String(row.archetype)
      if (row.id === 'custom') return 'CUSTOM'
      if (row.id === 'llm') return 'LLM'
      if (row.id === 'llmGlobal') return 'LLM_GLOBAL'
      return String(row.label || row.id || '').toUpperCase()
    },
    labelStyle: function (row, i) {
      if (i === this.selectedIndex || !row.labelColor) return null
      return { color: row.labelColor }
    },
    rowCount: function (row) {
      var n = parseInt(this.counts[row.id], 10)
      if (isNaN(n)) return 0
      return Math.max(0, Math.min(this.countMax, n))
    },
    rowClass: function (i) {
      return {
        'dp-console-menu__row--selected': i === this.selectedIndex,
        'dp-console-menu__row--blink': i === this.selectedIndex && this.useRowBlink
      }
    },
    onRowTap: function (i) {
      if (this.disabled) return
      this.$emit('select', i)
    },
    onAdjust: function (row, delta) {
      if (this.disabled || !row) return
      this.$emit('adjust-count', { id: row.id, delta: delta })
    }
  }
}
</script>

<style scoped>
.dp-owner-npc-console {
  position: relative;
  z-index: 1;
}

.dp-owner-npc-console__tip {
  margin: 8px 0 0;
  padding: 6px 10px;
  border: 2px solid rgba(255, 102, 102, 0.35);
  background: rgba(255, 40, 40, 0.08);
  color: var(--dp-terminal-led-red, #ff6666);
  font-family: 'Courier New', ui-monospace, monospace;
  font-size: 11px;
  text-align: center;
  letter-spacing: 0.03em;
}

.dp-owner-npc-console .dp-console-footer {
  display: block;
}

.dp-owner-npc-console .dp-console-footer__kb {
  display: none;
}

.dp-owner-npc-console .dp-console-footer__touch {
  display: inline;
}

/* Unselected row: preserve archetype tint on label */
.dp-owner-npc-console .dp-console-menu__row:not(.dp-console-menu__row--selected) .dp-console-menu__label {
  color: inherit;
}
</style>
